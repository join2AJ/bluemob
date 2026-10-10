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
//   PUT  /v1/blob/<fid>      (signed by the sender)        a chat attachment, already encrypted on the phone
//   GET  /v1/blob/<fid>?id&at&sig  (signed by the recipient)  download it; POST /v1/blob/<fid>/done deletes it
//   PUT  /v1/backup          (signed by the phone)          BlueMob Cloud: an encrypted backup (password-locked on the phone)
//   GET  /v1/backups?id&at&sig  /v1/backups/<name>?…       list / download your own backups (newest 3 kept)
//   POST /v1/report          {text, category, sub, app, device, details, photos[]}  a problem report from the app
//                                                          (Diagnostics → Report a problem); up to 3 JPEG photos, base64
//   GET  /v1/reports?token=REPORTS_TOKEN[&category&sub]    read them, filtered, with counts per category / sub-category
//   GET  /v1/reports/<id>/<n>?token=REPORTS_TOKEN          one report's photo
//   POST /v1/phone  {pk, at, h, sig}                       "my verified number's fingerprint is h" (signed by the phone)
//   GET  /v1/phone?h&id&at&sig                             which BlueMob ID has that number (signed lookup, 30 a day)
//   POST /v1/email  {pk, at, email, sig}                   optional recovery email for this BlueMob ID ("" removes it);
//                                                          kept for account recovery and receipts, never shown to anyone
//   GET  /v1/live (WebSocket)                              real-time links for calls (see live.js)
//   GET  /health
"use strict";
const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const TTL_MS = 7 * 24 * 3600e3;
const MAX_BODY = 256 * 1024;
const MAX_REPORT_BODY = 3 * 1024 * 1024; // a report may carry up to 3 photos
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
    this.pushTokens = new Map(); // id -> Firebase device token, for waking a phone that isn't connected
    this.phones = new Map(); // fingerprint of a verified number -> BlueMob ID (the number itself is never sent)
    this.emails = new Map(); // BlueMob ID -> optional recovery email
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
    else if (r.op === "phone") { for (const [h, i] of this.phones) if (i === r.id) this.phones.delete(h); if (r.h) this.phones.set(r.h, r.id); }
    else if (r.op === "email") { if (r.email) this.emails.set(r.id, r.email); else this.emails.delete(r.id); }
    else if (r.op === "push") { if (r.token) this.pushTokens.set(r.id, r.token); else this.pushTokens.delete(r.id); }
    else if (r.op === "rate") { if (!this.ratings.has(r.subject)) this.ratings.set(r.subject, new Map()); this.ratings.get(r.subject).set(r.key, r.packet); }
    if (persist) this.log(r);
  }
  learnKey(id, pk) { if (this.keys.get(id) !== pk) this.apply({ op: "key", id, pk }); }
  setPushToken(id, token) { if ((this.pushTokens.get(id) || null) !== (token || null)) this.apply({ op: "push", id, token: token || null }); }

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
    if (this.onPut) this.onPut(b.to, { type: o.type, name: typeof b.name === "string" ? b.name.slice(0, 40) : "" }); // tell the phone: pull now
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

/**
 * Chat attachments for people who aren't nearby. Files arrive already encrypted with a key only the two phones have,
 * so the relay holds bytes it can't read. Only the phone the file is addressed to can download it. Kept 7 days at
 * most, deleted once the recipient has it.
 */
class Blobs {
  constructor(dir, { maxBytes = 26 * 1024 * 1024, totalBytes = 1024 * 1024 * 1024, perDevicePerDay = 300 * 1024 * 1024, ttlMs = 7 * 24 * 3600e3 } = {}) {
    this.dir = dir; this.maxBytes = maxBytes; this.totalBytes = totalBytes; this.perDevicePerDay = perDevicePerDay; this.ttlMs = ttlMs;
    this.meta = new Map(); // fid -> {to, from, size, at}
    this.used = new Map(); // device|day -> bytes
    if (dir) { fs.mkdirSync(dir, { recursive: true }); for (const f of fs.readdirSync(dir)) if (f.endsWith(".bin")) fs.rmSync(path.join(dir, f), { force: true }); }
  }
  total() { let n = 0; for (const m of this.meta.values()) n += m.size; return n; }
  file(fid) { return path.join(this.dir, fid + ".bin"); }
  allow(from, size) {
    const key = from + "|" + Math.floor(Date.now() / 86400e3);
    const used = (this.used.get(key) || 0) + size;
    if (used > this.perDevicePerDay || this.total() + size > this.totalBytes) return false;
    this.used.set(key, used);
    return true;
  }
  put(fid, meta, buf) { fs.writeFileSync(this.file(fid), buf); this.meta.set(fid, { ...meta, size: buf.length, at: Date.now() }); }
  get(fid) { const m = this.meta.get(fid); return m && fs.existsSync(this.file(fid)) ? { meta: m, buf: fs.readFileSync(this.file(fid)) } : null; }
  del(fid) { this.meta.delete(fid); fs.rmSync(this.file(fid), { force: true }); }
  prune(now = Date.now()) { for (const [fid, m] of this.meta) if (now - m.at > this.ttlMs) this.del(fid); }
}

const FID = /^f-[0-9a-z]{8,40}$/;

/**
 * BlueMob Cloud backups. Each is the same file a phone saves locally: compressed and encrypted with the user's backup
 * password, which never leaves the phone. Stored per BlueMob ID; only that ID's key can list or download them, which
 * after a lost phone means: restore the ID with the recovery code, then the backup with its password.
 * They need a disk that survives restarts (Render: a paid plan with a disk, and PERSISTENT_DISK=1).
 */
class Backups {
  constructor(dir, { maxBytes = 100 * 1024 * 1024, keep = 3, persistent = false } = {}) {
    this.dir = dir; this.maxBytes = maxBytes; this.keep = keep; this.persistent = persistent;
    if (dir) fs.mkdirSync(dir, { recursive: true });
  }
  folder(id) { return path.join(this.dir, id); }
  list(id) {
    const d = this.folder(id);
    if (!fs.existsSync(d)) return [];
    return fs.readdirSync(d).filter((f) => /^\d{13}\.bmbk$/.test(f)).map((f) => ({ name: f, at: Number(f.slice(0, 13)), size: fs.statSync(path.join(d, f)).size }))
      .sort((a, b) => b.at - a.at);
  }
  put(id, buf, at = Date.now()) {
    const d = this.folder(id);
    fs.mkdirSync(d, { recursive: true });
    const name = String(at).padStart(13, "0") + ".bmbk";
    fs.writeFileSync(path.join(d, name), buf);
    for (const old of this.list(id).slice(this.keep)) fs.rmSync(path.join(d, old.name), { force: true });
    return name;
  }
  read(id, name) { const f = path.join(this.folder(id), name); return /^\d{13}\.bmbk$/.test(name) && fs.existsSync(f) ? fs.readFileSync(f) : null; }
}

/** Checks a signature over [text] by the key [pk]; returns the signer's ID or null. */
function signer(pk, text, sig) {
  try {
    const der = Buffer.from(pk, "base64");
    const key = crypto.createPublicKey({ key: der, format: "der", type: "spki" });
    return crypto.verify("sha256", Buffer.from(text), key, Buffer.from(sig, "base64")) ? idFor(der) : null;
  } catch { return null; }
}

/** Problem reports from users. Kept as files (newest 500); readable only with the server's REPORTS_TOKEN. */
class Reports {
  constructor(dir, token = "") { this.dir = dir; this.token = token; if (dir) fs.mkdirSync(dir, { recursive: true }); }
  /** Saves a report and its photos (JPEG buffers). Keeps the newest 500. */
  add(r, photos = []) {
    const id = Date.now() + "-" + crypto.randomBytes(3).toString("hex");
    photos.forEach((p, i) => fs.writeFileSync(path.join(this.dir, `${id}-${i}.jpg`), p));
    fs.writeFileSync(path.join(this.dir, id + ".json"), JSON.stringify({ ...r, photos: photos.length }));
    const all = fs.readdirSync(this.dir).filter((f) => f.endsWith(".json")).sort();
    for (const old of all.slice(0, Math.max(0, all.length - 500))) {
      const base = old.slice(0, -5);
      for (const f of fs.readdirSync(this.dir)) if (f === old || f.startsWith(base + "-")) fs.rmSync(path.join(this.dir, f), { force: true });
    }
    return id + ".json";
  }
  all() { return fs.readdirSync(this.dir).filter((f) => f.endsWith(".json")).sort().reverse().map((f) => { try { return { id: f, ...JSON.parse(fs.readFileSync(path.join(this.dir, f), "utf8")) }; } catch { return null; } }).filter(Boolean); }
  /** Newest first, optionally only one category / sub-category; plus how many there are of each. */
  list(category = "", sub = "") {
    const all = this.all();
    const counts = {};
    for (const r of all) { const c = r.category || "Other"; const sc = r.sub || "Other"; counts[c] = counts[c] || {}; counts[c][sc] = (counts[c][sc] || 0) + 1; }
    const shown = all.filter((r) => (!category || (r.category || "Other") === category) && (!sub || (r.sub || "Other") === sub)).slice(0, 200);
    return { reports: shown, counts };
  }
  photo(id, n) {
    if (!/^[0-9]+-[0-9a-f]{6}\.json$/.test(id) || !/^[0-2]$/.test(n)) return null;
    const f = path.join(this.dir, `${id.slice(0, -5)}-${n}.jpg`);
    return fs.existsSync(f) ? fs.readFileSync(f) : null;
  }
}

function createServer(store, guides = loadGuides(), blobs = new Blobs(null), backups = new Backups(null), reports = new Reports(null)) {
  const hits = new Map(); // device or IP -> {hour, n}
  const lookups = new Map(); // device|day -> phone lookups
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
    if (req.method === "GET" && url.pathname === "/health") return send(res, 200, { ok: true, packets: store.packets.size, cloudBackups: backups.dir ? (backups.persistent ? "kept" : "temporary") : "off" });
    if (backups.dir && url.pathname === "/v1/backup" && req.method === "PUT") {
      const at = Number(req.headers["x-at"]), pk = String(req.headers["x-pk"] || ""), sig = String(req.headers["x-sig"] || "");
      const size = Number(req.headers["content-length"] || 0);
      if (!(size > 0) || size > backups.maxBytes) return send(res, 413, { error: "too large" });
      if (Math.abs(Date.now() - at) > 10 * 60e3) return send(res, 400, { error: "clock" });
      const chunks = []; let got = 0;
      req.on("data", (c) => { got += c.length; if (got > backups.maxBytes) { send(res, 413, { error: "too large" }); req.destroy(); } else chunks.push(c); });
      req.on("end", () => {
        if (res.writableEnded) return;
        const buf = Buffer.concat(chunks);
        const id = signer(pk, ["bluemob-backup", crypto.createHash("sha256").update(buf).digest("hex"), at].join("|"), sig);
        if (!id) return send(res, 401, { error: "bad signature" });
        if (limited("dev:" + id)) return send(res, 429, { error: "slow down" });
        store.learnKey(id, pk);
        const name = backups.put(id, buf);
        return send(res, 200, { ok: true, name, persistent: backups.persistent });
      });
      return;
    }
    const rp = url.pathname.match(/^\/v1\/reports(?:\/([^/]+)\/([0-9]))?$/);
    if (reports.dir && rp && req.method === "GET") {
      const given = Buffer.from(url.searchParams.get("token") || "");
      const want = Buffer.from(reports.token);
      if (!reports.token || given.length !== want.length || !crypto.timingSafeEqual(given, want)) return send(res, 403, { error: "no" });
      if (rp[1]) {
        const img = reports.photo(rp[1], rp[2]);
        if (!img) return send(res, 404, { error: "not found" });
        res.writeHead(200, { "content-type": "image/jpeg", "cache-control": "no-store" });
        return res.end(img);
      }
      return send(res, 200, reports.list(url.searchParams.get("category") || "", url.searchParams.get("sub") || ""));
    }
    const bk = url.pathname.match(/^\/v1\/backups(?:\/([^/]+))?$/);
    if (backups.dir && bk && req.method === "GET") {
      const id = url.searchParams.get("id") || "", at = Number(url.searchParams.get("at")), sig = url.searchParams.get("sig") || "";
      const name = bk[1] || "list";
      const pk = store.keys.get(id);
      if (!pk || Math.abs(Date.now() - at) > 10 * 60e3 || signer(pk, ["bluemob-backup-get", name, at].join("|"), sig) !== id) return send(res, 403, { error: "not yours" });
      if (!bk[1]) return send(res, 200, { backups: backups.list(id), persistent: backups.persistent });
      const buf = backups.read(id, name);
      if (!buf) return send(res, 404, { error: "no such backup" });
      res.writeHead(200, { "content-type": "application/octet-stream", "content-length": buf.length, "cache-control": "no-store" });
      return res.end(buf);
    }
    if (req.method === "GET" && url.pathname === "/v1/guides") {
      return send(res, 200, { packs: [...guides.values()].map((p) => ({ id: p.id, version: p.version || 1, emoji: p.emoji || "📘", title: p.title, about: p.about || "",
        articles: p.articles.length, bytes: Buffer.byteLength(JSON.stringify(p)) })) });
    }
    if (req.method === "GET" && url.pathname.startsWith("/v1/guides/")) {
      const p = guides.get(url.pathname.slice("/v1/guides/".length));
      return p ? send(res, 200, p) : send(res, 404, { error: "no such pack" });
    }
    if (req.method === "GET" && url.pathname === "/v1/phone") {
      const h = url.searchParams.get("h") || "", id = url.searchParams.get("id") || "", at = Number(url.searchParams.get("at")), sig = url.searchParams.get("sig") || "";
      const pk = store.keys.get(id);
      if (!/^[0-9a-f]{64}$/.test(h) || !pk || Math.abs(Date.now() - at) > 10 * 60e3 || signer(pk, ["bluemob-phone-get", h, at].join("|"), sig) !== id) return send(res, 403, { error: "not allowed" });
      // A few lookups a day per phone: enough to add SOS contacts, too few to scan numbers.
      const day = "phone:" + id + ":" + Math.floor(Date.now() / 86400e3);
      lookups.set(day, (lookups.get(day) || 0) + 1);
      if (lookups.get(day) > 30) return send(res, 429, { error: "too many lookups today" });
      const found = store.phones.get(h);
      return found ? send(res, 200, { id: found, pk: store.keys.get(found) || "" }) : send(res, 404, { error: "not on BlueMob" });
    }
    if (req.method === "GET" && url.pathname === "/v1/key") {
      const pk = store.keys.get(url.searchParams.get("id") || "");
      return pk ? send(res, 200, { pk }) : send(res, 404, { error: "unknown id" });
    }
    if (req.method === "GET" && url.pathname === "/v1/ratings") {
      const m = store.ratings.get(url.searchParams.get("subject") || "");
      return send(res, 200, { ratings: m ? [...m.values()] : [] });
    }
    const blob = url.pathname.match(/^\/v1\/blob\/([^/]+)(\/done)?$/);
    if (blob && blobs.dir) {
      const fid = blob[1];
      if (!FID.test(fid)) return send(res, 400, { error: "bad id" });
      if (req.method === "PUT") {
        // Signed by the sender over the file's hash, its ID, the recipient and the time.
        const to = String(req.headers["x-to"] || ""), at = Number(req.headers["x-at"]), pk = String(req.headers["x-pk"] || ""), sig = String(req.headers["x-sig"] || "");
        if (!/^[0-9a-f]{16}$/.test(to) || Math.abs(Date.now() - at) > 10 * 60e3) return send(res, 400, { error: "bad request" });
        const size = Number(req.headers["content-length"] || 0);
        if (!(size > 0) || size > blobs.maxBytes) return send(res, 413, { error: "too large" });
        const chunks = []; let got = 0;
        req.on("data", (c) => { got += c.length; if (got > blobs.maxBytes) { send(res, 413, { error: "too large" }); req.destroy(); } else chunks.push(c); });
        req.on("end", () => {
          if (res.writableEnded) return;
          const buf = Buffer.concat(chunks);
          const hash = crypto.createHash("sha256").update(buf).digest("hex");
          const from = signer(pk, ["bluemob-blob", fid, to, at, hash].join("|"), sig);
          if (!from) return send(res, 401, { error: "bad signature" });
          if (blobs.meta.has(fid)) return send(res, 200, { ok: true, duplicate: true });
          if (!blobs.allow(from, buf.length)) return send(res, 429, { error: "storage limit reached, try later" });
          blobs.put(fid, { to, from }, buf);
          store.learnKey(from, pk);
          return send(res, 200, { ok: true });
        });
        return;
      }
      // Downloading or deleting needs the recipient's signature over the file ID and the time.
      const id = url.searchParams.get("id") || "", at = Number(url.searchParams.get("at")), sig = url.searchParams.get("sig") || "";
      const m = blobs.meta.get(fid);
      if (!m) return send(res, 404, { error: "not here (expired, or not uploaded yet)" });
      const pk = store.keys.get(id);
      if (id !== m.to || !pk || Math.abs(Date.now() - at) > 10 * 60e3 || signer(pk, ["bluemob-blob-get", fid, at].join("|"), sig) !== id) return send(res, 403, { error: "not yours" });
      if (req.method === "GET" && !blob[2]) {
        const b = blobs.get(fid);
        if (!b) return send(res, 404, { error: "gone" });
        res.writeHead(200, { "content-type": "application/octet-stream", "content-length": b.buf.length, "cache-control": "no-store" });
        return res.end(b.buf);
      }
      if (req.method === "POST" && blob[2]) { blobs.del(fid); return send(res, 200, { ok: true }); }
      return send(res, 405, { error: "method" });
    }
    if (req.method !== "POST") return send(res, 404, { error: "not found" });
    let size = 0;
    const chunks = [];
    const maxBody = url.pathname === "/v1/report" ? MAX_REPORT_BODY : MAX_BODY;
    req.on("data", (c) => { size += c.length; if (size > maxBody) { send(res, 413, { error: "too large" }); req.destroy(); } else chunks.push(c); });
    req.on("end", () => {
      if (res.writableEnded) return;
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString("utf8")); } catch { return send(res, 400, { error: "bad json" }); }
      if (url.pathname === "/v1/report" && reports.dir) {
        if (limited("report:" + ip)) return send(res, 429, { error: "slow down" });
        const str = (v, n) => (typeof v === "string" ? v : "").slice(0, n);
        if (!str(body.text, 4000).trim()) return send(res, 400, { error: "empty" });
        // Up to 3 photos, each a real JPEG of at most 900 KB.
        const photos = (Array.isArray(body.photos) ? body.photos.slice(0, 3) : []).map((b) => { try { return Buffer.from(String(b), "base64"); } catch { return null; } })
          .filter((b) => b && b.length > 3 && b.length <= 900 * 1024 && b[0] === 0xff && b[1] === 0xd8);
        const id = reports.add({ at: Date.now(), text: str(body.text, 4000), category: str(body.category, 40), sub: str(body.sub, 60),
          app: str(body.app, 40), device: str(body.device, 300), details: str(body.details, 20000) }, photos);
        return send(res, 200, { ok: true, id });
      }
      if (url.pathname === "/v1/phone") {
        const h = String(body.h || ""), at = Number(body.at);
        if (!/^[0-9a-f]{64}$/.test(h) || Math.abs(Date.now() - at) > 10 * 60e3) return send(res, 400, { error: "bad request" });
        const id = signer(String(body.pk || ""), ["bluemob-phone", h, at].join("|"), String(body.sig || ""));
        if (!id) return send(res, 401, { error: "bad signature" });
        if (limited("dev:" + id)) return send(res, 429, { error: "slow down" });
        store.learnKey(id, body.pk);
        store.apply({ op: "phone", id, h });
        return send(res, 200, { ok: true });
      }
      if (url.pathname === "/v1/email") {
        const email = String(body.email || "").trim().toLowerCase(), at = Number(body.at);
        if ((email && !/^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/.test(email)) || Math.abs(Date.now() - at) > 10 * 60e3) return send(res, 400, { error: "bad request" });
        const id = signer(String(body.pk || ""), ["bluemob-email", email, at].join("|"), String(body.sig || ""));
        if (!id) return send(res, 401, { error: "bad signature" });
        if (limited("dev:" + id)) return send(res, 429, { error: "slow down" });
        store.learnKey(id, body.pk);
        store.apply({ op: "email", id, email });
        return send(res, 200, { ok: true });
      }
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

module.exports = { createServer, Store, Blobs, Backups, Reports, openEnvelope, idFor, loadGuides };

if (require.main === module) {
  const port = Number(process.env.PORT || 8080);
  const dataDir = process.env.DATA_DIR || path.join(__dirname, "data");
  fs.mkdirSync(dataDir, { recursive: true });
  const store = new Store(path.join(dataDir, "relay.jsonl"));
  setInterval(() => store.prune(), 3600e3).unref();
  const blobs = new Blobs(path.join(dataDir, "blobs"));
  setInterval(() => blobs.prune(), 3600e3).unref();
  const backups = new Backups(path.join(dataDir, "backups"), { persistent: process.env.PERSISTENT_DISK === "1" });
  const reports = new Reports(path.join(dataDir, "reports"), process.env.REPORTS_TOKEN || "");
  const server = createServer(store, loadGuides(), blobs, backups, reports);
  const { createPusher, loadAccount } = require("./push");
  const pusher = createPusher(loadAccount());
  if (pusher.configured) console.log("Firebase wake-ups on");
  require("./live").attachLive(server, { store, pusher });
  server.listen(port, () => console.log(`BlueMob relay listening on :${port}`));
}
