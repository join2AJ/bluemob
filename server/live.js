// BlueMob live channel: real-time links between phones over the internet, for calls when people are far apart.
//
//   GET /v1/live  (WebSocket)
//
// 1. The server sends {t:"challenge", n}. The phone answers {t:"auth", pk, sig} where sig signs "bluemob-live|n" with
//    its device key; its ID is the hash of pk, so no one can sign in as someone else's ID.
// 2. Text  {t:"send", to, data}   → delivered to `to` as {t:"msg", from, data}, or answered {t:"offline", to}.
//    Text  {t:"presence", ids}    → {t:"presence", online:[...]}
//    Binary [8-byte target ID | payload] → delivered as [8-byte sender ID | payload]   (call audio and video)
//
// Call set-up messages are the app's own signed packets, and the audio and video are already in the app's format; the
// server only passes bytes between two signed-in phones. Nothing is stored. Zero dependencies (RFC 6455 by hand).
"use strict";
const crypto = require("crypto");

const GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
const MAX_FRAME = 64 * 1024;
const MAX_TEXT = 32 * 1024;
const BYTES_PER_SEC = 512 * 1024; // per phone: plenty for voice and small video frames, but not a free file host
const IDLE_MS = 75e3;

function idFor(der) { return crypto.createHash("sha256").update(der).digest().subarray(0, 8).toString("hex"); }

/** One WebSocket connection. Calls back with whole text (string) and binary (Buffer) messages. */
class Socket {
  constructor(sock, onText, onBinary, onClose) {
    this.sock = sock; this.onText = onText; this.onBinary = onBinary; this.onClose = onClose;
    this.buf = Buffer.alloc(0); this.parts = []; this.partOp = 0; this.closed = false;
    this.lastSeen = Date.now();
    sock.on("data", (d) => this.data(d));
    sock.on("close", () => this.close());
    sock.on("error", () => this.close());
  }

  data(d) {
    this.lastSeen = Date.now();
    this.buf = Buffer.concat([this.buf, d]);
    while (true) {
      if (this.buf.length < 2) return;
      const b0 = this.buf[0], b1 = this.buf[1];
      const fin = (b0 & 0x80) !== 0, op = b0 & 0x0f, masked = (b1 & 0x80) !== 0;
      let len = b1 & 0x7f, off = 2;
      if (len === 126) { if (this.buf.length < 4) return; len = this.buf.readUInt16BE(2); off = 4; }
      else if (len === 127) { if (this.buf.length < 10) return; const big = this.buf.readBigUInt64BE(2); if (big > BigInt(MAX_FRAME)) return this.close(1009); len = Number(big); off = 10; }
      if (len > MAX_FRAME) return this.close(1009);
      if (!masked) return this.close(1002); // clients must mask
      if (this.buf.length < off + 4 + len) return;
      const mask = this.buf.subarray(off, off + 4);
      const payload = Buffer.from(this.buf.subarray(off + 4, off + 4 + len));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
      this.buf = this.buf.subarray(off + 4 + len);
      if (op === 8) return this.close(1000);
      if (op === 9) { this.frame(10, payload); continue; }
      if (op === 10) continue;
      if (op === 0) { this.parts.push(payload); } else { this.parts = [payload]; this.partOp = op; }
      if (this.parts.reduce((n, p) => n + p.length, 0) > MAX_FRAME) return this.close(1009);
      if (!fin) continue;
      const msg = Buffer.concat(this.parts); this.parts = [];
      if (this.partOp === 1) this.onText(msg.toString("utf8")); else if (this.partOp === 2) this.onBinary(msg);
    }
  }

  frame(op, payload) {
    if (this.closed) return;
    const len = payload.length;
    const head = len < 126 ? Buffer.from([0x80 | op, len])
      : len < 65536 ? Buffer.from([0x80 | op, 126, len >> 8, len & 255])
      : (() => { const h = Buffer.alloc(10); h[0] = 0x80 | op; h[1] = 127; h.writeBigUInt64BE(BigInt(len), 2); return h; })();
    this.sock.write(Buffer.concat([head, payload]));
  }

  text(obj) { this.frame(1, Buffer.from(JSON.stringify(obj))); }
  binary(buf) { this.frame(2, buf); }
  /** Bytes waiting to go out to this phone: if it can't keep up, call media is dropped rather than queued. */
  backlog() { return this.sock.writableLength; }

  close(code = 1000) {
    if (this.closed) return;
    try { this.frame(8, Buffer.from([code >> 8, code & 255])); } catch {}
    this.closed = true;
    this.sock.destroy();
    this.onClose();
  }
}

/** Adds the live channel to an http server. Returns the table of connected phones (for tests and /health). */
function attachLive(server, { now = () => Date.now() } = {}) {
  const online = new Map(); // id -> Socket
  server.on("upgrade", (req, sock) => {
    const url = new URL(req.url, "http://relay");
    const key = req.headers["sec-websocket-key"];
    if (url.pathname !== "/v1/live" || !key || (req.headers.upgrade || "").toLowerCase() !== "websocket") {
      sock.end("HTTP/1.1 400 Bad Request\r\n\r\n"); return;
    }
    const accept = crypto.createHash("sha1").update(key + GUID).digest("base64");
    sock.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n");
    sock.setNoDelay(true);
    const nonce = crypto.randomBytes(16).toString("base64");
    let id = null;
    let budget = { sec: 0, used: 0 };
    const allowed = (n) => {
      const s = Math.floor(now() / 1000);
      if (budget.sec !== s) budget = { sec: s, used: 0 };
      return (budget.used += n) <= BYTES_PER_SEC;
    };
    const ws = new Socket(sock, (txt) => {
      if (txt.length > MAX_TEXT) return;
      let m; try { m = JSON.parse(txt); } catch { return; }
      if (!id) {
        if (m.t !== "auth") return ws.close(1008);
        try {
          const der = Buffer.from(m.pk, "base64");
          const pub = crypto.createPublicKey({ key: der, format: "der", type: "spki" });
          if (!crypto.verify("sha256", Buffer.from("bluemob-live|" + nonce), pub, Buffer.from(m.sig, "base64"))) return ws.close(1008);
          id = idFor(der);
        } catch { return ws.close(1008); }
        const old = online.get(id);
        online.set(id, ws);
        if (old && old !== ws) old.close(4000); // the same phone reconnected: keep only the new link
        return ws.text({ t: "ok", id });
      }
      if (!allowed(txt.length)) return;
      if (m.t === "send" && /^[0-9a-f]{16}$/.test(m.to) && typeof m.data === "string") {
        const peer = online.get(m.to);
        if (peer) peer.text({ t: "msg", from: id, data: m.data }); else ws.text({ t: "offline", to: m.to });
      } else if (m.t === "presence" && Array.isArray(m.ids)) {
        ws.text({ t: "presence", online: m.ids.slice(0, 50).filter((x) => online.has(x)) });
      }
    }, (bin) => {
      if (!id || bin.length < 9 || !allowed(bin.length)) return;
      const to = bin.subarray(0, 8).toString("hex");
      const peer = online.get(to);
      if (!peer) return;
      if (peer.backlog() > 256 * 1024) return; // their link is behind: drop this frame instead of adding delay
      const out = Buffer.from(bin);
      Buffer.from(id, "hex").copy(out, 0);
      peer.binary(out);
    }, () => { if (id && online.get(id) === ws) online.delete(id); });
    ws.text({ t: "challenge", n: nonce });
  });
  // Close links that have gone quiet (phones send a ping every 25 s).
  const timer = setInterval(() => {
    for (const ws of online.values()) if (now() - ws.lastSeen > IDLE_MS) ws.close(1001);
  }, 15e3);
  timer.unref();
  return online;
}

module.exports = { attachLive, Socket };
