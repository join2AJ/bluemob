// BlueMob live channel: real-time links between phones over the internet, for calls when people are far apart.
//
//   GET /v1/live  (WebSocket)
//
// 1. The server sends {t:"challenge", n}. The phone answers {t:"auth", pk, sig} where sig signs "bluemob-live|n" with
//    its device key; its ID is the hash of pk, so no one can sign in as someone else's ID.
// 2. Text  {t:"send", to, data}   → delivered to `to` as {t:"msg", from, data}, or answered {t:"offline", to}.
//    Text  {t:"presence", ids}    → {t:"presence", online:[...], via:{id: gatewayId}}
//    Text  {t:"via", ids}         → this phone carries these IDs (phones near it with no internet of their own)
//    Binary [8-byte target ID | payload] → delivered as [8-byte sender ID | payload]   (call audio and video)
//    Server → phone {t:"poke"}    → a message for you (or a phone you carry) was stored: pull now
//
// Gateways: a phone with internet tells the server which nearby phones it can reach ("via"). Anything for one of those
// phones, while it isn't signed in itself, goes to the gateway, which hands it on over Bluetooth / Wi-Fi. Binary frames
// for them are wrapped as relay frames ['R' | ttl | dest(8) | origin(8) | payload] so the gateway knows where they go.
//
// Call set-up messages are the app's own signed packets, chat messages are signed and end-to-end encrypted, and calls
// from 0.12 on are end-to-end encrypted too; the server only passes bytes along. Messages sent here for someone who
// isn't reachable right now are stored (like /v1/push) so they're delivered when that phone next pulls.
// Zero dependencies (RFC 6455 by hand).
"use strict";
const crypto = require("crypto");

const GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
const MAX_FRAME = 64 * 1024;
const MAX_TEXT = 32 * 1024;
const BYTES_PER_SEC = 512 * 1024; // per phone: plenty for voice and small video frames, but not a free file host
const IDLE_MS = 75e3;
const MAX_VIA = 50;
/** Receiver's queue above which video frames are dropped (voice keeps going until the bigger limit). */
const VIDEO_BACKLOG = 96 * 1024;
const AUDIO_BACKLOG = 256 * 1024;
const RELAY = 0x52; // 'R'
const VIDEO = 0x56; // 'V'
const ENCRYPTED = 0x45; // 'E' | kind ('A' or 'V') | counter | ciphertext: an end-to-end encrypted call frame

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
function attachLive(server, { now = () => Date.now(), store = null } = {}) {
  const online = new Map(); // id -> Socket
  const carriers = new Map(); // phone without internet -> gateway id that carries it
  const carried = new Map(); // gateway id -> Set of ids it carries
  const forget = (gw) => {
    for (const x of carried.get(gw) || []) if (carriers.get(x) === gw) carriers.delete(x);
    carried.delete(gw);
  };
  /** Who to hand something for [to] to: the phone itself, or the gateway carrying it. */
  const route = (to) => online.get(to) || (carriers.has(to) ? online.get(carriers.get(to)) : undefined);
  const poke = (to) => { const ws = route(to); if (ws) ws.text({ t: "poke" }); };
  if (store) store.onPut = poke;
  online.poke = poke;
  online.carriers = carriers;
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
        const direct = online.get(m.to);
        const peer = route(m.to);
        if (peer) peer.text({ t: "msg", from: id, data: m.data });
        // Chat messages and receipts are kept for later unless they reached the phone itself.
        if (!direct && store) {
          let p = null; try { p = JSON.parse(m.data); } catch {}
          if (p && (p.t === "rmsg" || p.t === "rrcpt")) { const saved = store.onPut; store.onPut = null; try { store.put(p); } finally { store.onPut = saved; } }
        }
        if (!peer) ws.text({ t: "offline", to: m.to });
      } else if (m.t === "presence" && Array.isArray(m.ids)) {
        const ids = m.ids.slice(0, 50);
        const via = {};
        for (const x of ids) if (!online.has(x) && carriers.has(x) && online.has(carriers.get(x))) via[x] = carriers.get(x);
        ws.text({ t: "presence", online: ids.filter((x) => online.has(x)), via });
      } else if (m.t === "via" && Array.isArray(m.ids)) {
        forget(id);
        const set = new Set(m.ids.filter((x) => typeof x === "string" && /^[0-9a-f]{16}$/.test(x) && x !== id).slice(0, MAX_VIA));
        carried.set(id, set);
        for (const x of set) carriers.set(x, id);
      }
    }, (bin) => {
      if (!id || bin.length < 9 || !allowed(bin.length)) return;
      const to = bin.subarray(0, 8).toString("hex");
      const peer = route(to);
      if (!peer) return;
      // Their link is behind: drop this frame instead of adding delay. Video goes first, so voice keeps flowing.
      const kind = bin[8];
      const video = kind === VIDEO || (kind === ENCRYPTED && bin[9] === VIDEO) || (kind === RELAY && bin.length > 2048);
      if (peer.backlog() > (video ? VIDEO_BACKLOG : AUDIO_BACKLOG)) return;
      let out;
      if (peer === online.get(to) || kind === RELAY) {
        out = Buffer.from(bin);
        Buffer.from(id, "hex").copy(out, 0);
      } else {
        // For a phone a gateway carries: wrap it, so the gateway knows where it goes and who it's from.
        out = Buffer.concat([Buffer.from(id, "hex"), Buffer.from([RELAY, 4]), Buffer.from(to, "hex"), Buffer.from(id, "hex"), bin.subarray(8)]);
      }
      peer.binary(out);
    }, () => { if (id && online.get(id) === ws) { online.delete(id); forget(id); } });
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
