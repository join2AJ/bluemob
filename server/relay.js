// BlueMob relay: carries messages between phones over the internet, for when people are far apart.
//
// It stores only what the mesh already carries: signed, end-to-end encrypted packets. It can't read messages, and
// it can't forge them (every packet is signed by the sender's device key, and a device's ID is the hash of its key).
// No accounts, no phone numbers. Zero dependencies: Node 18+.
//
//   POST /v1/push    {packets:[...]}                       store messages, receipts and ratings
//   POST /v1/pull    {ids:[...], since:{id:seq}, proof}       fetch packets addressed to these IDs, after each ID's cursor
//   GET  /v1/key?id=ID                                      a device's public key, so others can encrypt to it
//   GET  /v1/ratings?subject=ID                             signed ratings about a device
//   GET  /v1/guides  /v1/guides/<id>                      survival-guide packs phones can download for offline use
//   GET  /v1/live (WebSocket)                              real-time links for calls (see live.js)
//   GET  /health
"use strict";
const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const TTL_MS = 7 * 24 * 3600e3;
const MAX_BODY = 256 * 1024;
const MAX_IDS = 50;
const PER_DEVICE_PER_HOUR = 600;

/** The ID for a public key: first 8 bytes of SHA-256 of the X.509 (SPKI DER) encoding, hex. Same as the app. */
function idFor(der) { return crypto.createHash("sha256").update(der).digest().subarray(0, 8).toString("hex"); }

/** Checks a signed packet {t, b, pk, s}: the key must match body.from, and the signature must cover "t\nb". */
function openEnvelope(p) {
  try {
    if (!p || typeof p.t !== "string" || typeof p.b !== "string" || p.b.length > 16000) return null;
    const der = Buffer.from(p.pk, "base64");
    const key = crypto.createPublicKey({ key: der, format: "der", type: "spki" });
    const body = JSON.parse(p.b);
    if (body.from !== idFor(der)) return null;
    if (!crypto.verify("sha256", Buffer.from(p.t + "\n" + p.b), key, Buffer.from(p.s, "base64"))) return null;
    return { type: p.t, body, from: body.from, pk: p.pk };
  } catch { return null; }
}

class Store {
  constructor(file) {
    this.file = file;
    this.seq = 0;
    this.packets = new Map(); // key -> {seq, key, to, packet, expiresAt}
    this.keys = new Map();    // id -> public key (base64)
    this.ratings = new Map(); // subject -> Map(ratingKey -> packet)
    if (file) this.load();
  }
  load() {
    if (!fs.existsSync(this.file)) return;
    for (const line of fs.readFileSync(this.file, "utf8").split("\n")) {
      if (!line) continue;
      try { const r = JSON.parse(line); this.apply(r, false); } catch { /* skip a torn line */ }
    }
  }
  log(r) { if (this.file) fs.appendFileSync(this.file, JSON.stringify(r) + "\n"); }
  apply(r, persist = true) {
    if (r.op === "put") { this.seq = Math.max(this.seq, r.seq); this.packets.set(r.key, r); }
    else if (r.op === "del") this.packets.delete(r.key);
    else if (r.op === "key") this.keys.set(r.id, r.pk);
    else if (r.op === "rate") { if (!this.ratings.has(r.subject)) this.ratings.set(r.subject, new Map()); this.ratings.get(r.subject).set(r.key, r.packet); }
    if (persist) this.log(r);
  }
  learnKey(id, pk) { if (this.keys.get(id) !== pk) this.apply({ op: "key", id, pk }); }

  /** Stores a verified packet. Returns false if it's a duplicate or not storable. */
  put(p, now = Date.now()) {
    const o = openEnvelope(p);
    if (!o) return "bad-signature";
    this.learnKey(o.from, o.pk);
    const b = o.body;
    if (o.type === "rate") {
      if (typeof b.subject !== "string" || !/^[0-9a-f]{16}$/.test(b.subject) || b.subject === o.from) return "bad-rating";
      // One rating per rater, subject, kind and context: a newer one replaces the older.
      const key = [o.from, b.subject, b.kind, b.ctx || ""].join("|");
      this.apply({ op: "rate", subject: b.subject, key, packet: p });
      return "ok";
    }
    if (o.type !== "rmsg" && o.type !== "rrcpt") return "unsupported";
    if (!(b.x > now)) return "expired";
    const key = o.type + ":" + (o.type === "rmsg" ? b.id : b.mid + b.k);
    if (this.packets.has(key)) return "duplicate";
    // A delivery receipt means the message arrived: the relay drops its copy.
    if (o.type === "rrcpt" && b.k === "d") { const mk = "rmsg:" + b.mid; if (this.packets.has(mk)) this.apply({ op: "del", key: mk }); }
    const clean = { t: p.t, b: p.b, pk: p.pk, s: p.s, h: Number(p.h) || 0, net: 1 };
    this.apply({ op: "put", seq: ++this.seq, key, to: b.to, packet: clean, expiresAt: Math.min(b.x, now + TTL_MS) });
    if (this.onPut) this.onPut(b.to); // tell the phone (or its gateway) over the live channel: pull now
    return "ok";
  }
  /** Packets for each ID after that ID's cursor (a phone pulls for itself and the phones around it). */
  pull(ids, since = {}, now = Date.now()) {
    const after = (id) => Number((since && since[id]) || 0);
    const want = new Set(ids);
    const out = [];
    for (const r of this.packets.values()) if (want.has(r.to) && r.seq > after(r.to) && r.expiresAt > now) out.push(r);
    out.sort((a, b) => a.seq - b.seq);
    const page = out.slice(0, 500);
    const next = {};
    for (const id of ids) next[id] = after(id);
    for (const r of page) next[r.to] = Math.max(next[r.to], r.seq);
    return { packets: page.map((r) => r.packet), next };
  }
  prune(now = Date.now()) { for (const [k, r] of this.packets) if (r.expiresAt <= now) this.apply({ op: "del", key: k }); }
}

/** Proof that a request comes from the device holding pk: a signature over "bluemob-pull|at|ids", at most 5 minutes old. */
function checkProof(proof, ids, now = Date.now()) {
  try {
    if (!proof || Math.abs(now - Number(proof.at)) > 5 * 60e3) return null;
    const der = Buffer.from(proof.pk, "base64");
    const key = crypto.createPublicKey({ key: der, format: "der", type: "spki" });
    const ok = crypto.verify("sha256", Buffer.from("bluemob-pull|" + proof.at + "|" + ids.join(",")), key, Buffer.from(proof.sig, "base64"));
    return ok ? idFor(der) : null;
  } catch { return null; }
}

/** Guide packs from ./guides/*.json, loaded once. */
function loadGuides(dir = path.join(__dirname, "guides")) {
  const packs = new Map();
  try {
    for (const f of fs.readdirSync(dir)) {
      if (!f.endsWith(".json")) continue;
      const p = JSON.parse(fs.readFileSync(path.join(dir, f), "utf8"));
      if (p.id && Array.isArray(p.articles)) packs.set(p.id, p);
    }
  } catch {}
  return packs;
}

function createServer(store, guides = loadGuides()) {
  const hits = new Map(); // device or IP -> {hour, n}
  const limited = (who) => {
    const hour = Math.floor(Date.now() / 3600e3);
    const h = hits.get(who);
    if (!h || h.hour !== hour) { hits.set(who, { hour, n: 1 }); return false; }
    return ++h.n > PER_DEVICE_PER_HOUR;
  };
  const send = (res, code, obj) => { res.writeHead(code, { "content-type": "application/json", "cache-control": "no-store" }); res.end(JSON.stringify(obj)); };
  return http.createServer((req, res) => {
    const url = new URL(req.url, "http://relay");
    const ip = req.socket.remoteAddress || "?";
    if (req.method === "GET" && url.pathname === "/health") return send(res, 200, { ok: true, packets: store.packets.size });
    if (req.method === "GET" && url.pathname === "/v1/guides") {
      return send(res, 200, { packs: [...guides.values()].map((p) => ({ id: p.id, version: p.version || 1, emoji: p.emoji || "📘", title: p.title, about: p.about || "",
        articles: p.articles.length, bytes: Buffer.byteLength(JSON.stringify(p)) })) });
    }
    if (req.method === "GET" && url.pathname.startsWith("/v1/guides/")) {
      const p = guides.get(url.pathname.slice("/v1/guides/".length));
      return p ? send(res, 200, p) : send(res, 404, { error: "no such pack" });
    }
    if (req.method === "GET" && url.pathname === "/v1/key") {
      const pk = store.keys.get(url.searchParams.get("id") || "");
      return pk ? send(res, 200, { pk }) : send(res, 404, { error: "unknown id" });
    }
    if (req.method === "GET" && url.pathname === "/v1/ratings") {
      const m = store.ratings.get(url.searchParams.get("subject") || "");
      return send(res, 200, { ratings: m ? [...m.values()] : [] });
    }
    if (req.method !== "POST") return send(res, 404, { error: "not found" });
    let size = 0;
    const chunks = [];
    req.on("data", (c) => { size += c.length; if (size > MAX_BODY) { send(res, 413, { error: "too large" }); req.destroy(); } else chunks.push(c); });
    req.on("end", () => {
      if (res.writableEnded) return;
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString("utf8")); } catch { return send(res, 400, { error: "bad json" }); }
      if (url.pathname === "/v1/push") {
        if (limited("ip:" + ip)) return send(res, 429, { error: "slow down" });
        const results = (Array.isArray(body.packets) ? body.packets.slice(0, 200) : []).map((p) => {
          const o = openEnvelope(p);
          if (o && limited("dev:" + o.from)) return "rate-limited";
          return store.put(p);
        });
        return send(res, 200, { results });
      }
      if (url.pathname === "/v1/pull") {
        const ids = (Array.isArray(body.ids) ? body.ids : []).filter((x) => /^[0-9a-f]{16}$/.test(x)).slice(0, MAX_IDS);
        const who = checkProof(body.proof, ids);
        if (!who) return send(res, 401, { error: "bad proof" });
        if (limited("dev:" + who)) return send(res, 429, { error: "slow down" });
        store.learnKey(who, body.proof.pk); // pulling registers your key, so people can find you by ID
        return send(res, 200, store.pull(ids, typeof body.since === "object" && body.since ? body.since : {}));
      }
      send(res, 404, { error: "not found" });
    });
  });
}

module.exports = { createServer, Store, openEnvelope, idFor, loadGuides };

if (require.main === module) {
  const port = Number(process.env.PORT || 8080);
  const dataDir = process.env.DATA_DIR || path.join(__dirname, "data");
  fs.mkdirSync(dataDir, { recursive: true });
  const store = new Store(path.join(dataDir, "relay.jsonl"));
  setInterval(() => store.prune(), 3600e3).unref();
  const server = createServer(store);
  require("./live").attachLive(server, { store });
  server.listen(port, () => console.log(`BlueMob relay listening on :${port}`));
}
