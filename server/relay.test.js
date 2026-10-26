// node --test server/relay.test.js
"use strict";
const test = require("node:test");
const assert = require("node:assert");
const crypto = require("crypto");
const { Store, openEnvelope, idFor } = require("./relay");

function device() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
  const der = publicKey.export({ format: "der", type: "spki" });
  return { pk: der.toString("base64"), id: idFor(der), privateKey };
}
function seal(dev, t, body) {
  const b = JSON.stringify({ ...body, from: dev.id });
  return { t, b, pk: dev.pk, s: crypto.sign("sha256", Buffer.from(t + "\n" + b), dev.privateKey).toString("base64"), h: 0 };
}

test("stores a signed message for its recipient and serves it once per cursor", () => {
  const s = new Store(null), a = device(), c = device();
  assert.equal(s.put(seal(a, "rmsg", { id: "m1", to: c.id, at: 1, x: Date.now() + 1e6, c: "ciphertext" })), "ok");
  const first = s.pull([c.id], {});
  assert.equal(first.packets.length, 1);
  assert.equal(first.packets[0].net, 1);
  assert.equal(s.pull([c.id], first.next).packets.length, 0);
  // Another phone pulling for C (e.g. C just came into its range) still gets it: cursors are per ID.
  assert.equal(s.pull([c.id, a.id], { [a.id]: 99 }).packets.length, 1);
  assert.equal(s.keys.get(a.id), a.pk);
});

test("rejects forged and tampered packets", () => {
  const s = new Store(null), a = device(), evil = device(), c = device();
  const p = seal(evil, "rmsg", { id: "m2", to: c.id, at: 1, x: Date.now() + 1e6, c: "x" });
  const claimed = { ...p, b: p.b.replace(evil.id, a.id) };
  assert.equal(s.put(claimed), "bad-signature");
  assert.equal(openEnvelope({ ...p, pk: a.pk }), null);
});

test("a delivery receipt removes the stored message", () => {
  const s = new Store(null), a = device(), c = device();
  s.put(seal(a, "rmsg", { id: "m3", to: c.id, at: 1, x: Date.now() + 1e6, c: "x" }));
  s.put(seal(c, "rrcpt", { mid: "m3", to: a.id, k: "d", at: 2, x: Date.now() + 1e6 }));
  assert.equal(s.pull([c.id]).packets.length, 0);
  assert.equal(s.pull([a.id]).packets.length, 1);
});

test("ratings: one per rater, subject, kind and context; self-ratings refused", () => {
  const s = new Store(null), a = device(), b = device();
  s.put(seal(a, "rate", { subject: b.id, kind: "thanks", ctx: "sos1", remark: "Came quickly", at: 1 }));
  s.put(seal(a, "rate", { subject: b.id, kind: "thanks", ctx: "sos1", remark: "Came quickly!!", at: 2 }));
  assert.equal(s.ratings.get(b.id).size, 1);
  assert.equal(s.put(seal(a, "rate", { subject: a.id, kind: "thanks", at: 3 })), "bad-rating");
});

test("expired messages are refused and pruned", () => {
  const s = new Store(null), a = device(), c = device();
  assert.equal(s.put(seal(a, "rmsg", { id: "m4", to: c.id, at: 1, x: Date.now() - 1 })), "expired");
});

test("serves the guide packs for offline download", async () => {
  const { createServer, Store, loadGuides } = require("./relay");
  const guides = loadGuides();
  assert.ok(guides.size >= 4);
  for (const p of guides.values()) for (const a of p.articles) {
    assert.ok(a.id && a.title && a.intro && a.steps.length >= 2, p.id + "/" + a.id);
    assert.ok(["FIRST_AID", "WATER", "FIRE", "SHELTER", "NAVIGATION", "SIGNALS", "WEATHER", "DISASTERS", "BASICS"].includes(a.category), a.category);
  }
  const server = createServer(new Store(null), guides);
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const list = await (await fetch(base + "/v1/guides")).json();
  assert.ok(list.packs.some((p) => p.id === "mountains" && p.articles === 4));
  const pack = await (await fetch(base + "/v1/guides/mountains")).json();
  assert.equal(pack.articles[0].id, "altitude-sickness");
  assert.equal((await fetch(base + "/v1/guides/nope")).status, 404);
  server.close();
});

test("files: only the sender can upload, only the recipient can download, and it's deleted after", async () => {
  const os = require("os"), path = require("path"), fs = require("fs");
  const { createServer, Blobs } = require("./relay");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "blobs-"));
  const store = new Store(null);
  const srv = createServer(store, new Map(), new Blobs(dir, { maxBytes: 1024 * 1024 }));
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const a = device(), b = device(), evil = device();
  store.learnKey(b.id, b.pk); store.learnKey(evil.id, evil.pk);
  const body = crypto.randomBytes(5000), fid = "f-abc123def456";
  const put = (signerDev, data = body) => {
    const at = Date.now(), hash = crypto.createHash("sha256").update(data).digest("hex");
    const sig = crypto.sign("sha256", Buffer.from(["bluemob-blob", fid, b.id, at, hash].join("|")), signerDev.privateKey).toString("base64");
    return fetch(`${base}/v1/blob/${fid}`, { method: "PUT", body: data, headers: { "x-to": b.id, "x-at": String(at), "x-pk": a.pk, "x-sig": sig } });
  };
  assert.equal((await put(evil)).status, 401); // signed with another key than the one it claims
  assert.equal((await put(a)).status, 200);
  const get = (dev, method = "GET", suffix = "") => {
    const at = Date.now();
    const sig = crypto.sign("sha256", Buffer.from(["bluemob-blob-get", fid, at].join("|")), dev.privateKey).toString("base64");
    return fetch(`${base}/v1/blob/${fid}${suffix}?id=${dev.id}&at=${at}&sig=${encodeURIComponent(sig)}`, { method });
  };
  assert.equal((await get(evil)).status, 403);
  const r = await get(b);
  assert.equal(r.status, 200);
  assert.deepEqual(Buffer.from(await r.arrayBuffer()), body);
  assert.equal((await get(b, "POST", "/done")).status, 200);
  assert.equal((await get(b)).status, 404);
  srv.close();
});

test("cloud backups: only the owner can list and download, and the newest 3 are kept", async () => {
  const os = require("os"), path = require("path"), fs = require("fs");
  const { createServer, Backups, Blobs } = require("./relay");
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "backups-"));
  const store = new Store(null);
  const srv = createServer(store, new Map(), new Blobs(null), new Backups(dir, { persistent: true }));
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const me = device(), evil = device();
  store.learnKey(evil.id, evil.pk);
  const upload = (data) => {
    const at = Date.now(), hash = crypto.createHash("sha256").update(data).digest("hex");
    const sig = crypto.sign("sha256", Buffer.from(["bluemob-backup", hash, at].join("|")), me.privateKey).toString("base64");
    return fetch(`${base}/v1/backup`, { method: "PUT", body: data, headers: { "x-at": String(at), "x-pk": me.pk, "x-sig": sig } });
  };
  const signed = (dev, name, suffix) => {
    const at = Date.now();
    const sig = crypto.sign("sha256", Buffer.from(["bluemob-backup-get", name, at].join("|")), dev.privateKey).toString("base64");
    return fetch(`${base}/v1/backups${suffix}?id=${dev.id}&at=${at}&sig=${encodeURIComponent(sig)}`);
  };
  for (let i = 0; i < 4; i++) { assert.equal((await upload(Buffer.from("backup " + i))).status, 200); await new Promise((r) => setTimeout(r, 5)); }
  const list = await (await signed(me, "list", "")).json();
  assert.equal(list.backups.length, 3);
  assert.equal(list.persistent, true);
  // Someone else only ever sees their own (empty) list, and can't fetch ours by name.
  assert.deepEqual((await (await signed(evil, "list", "")).json()).backups, []);
  const newest = list.backups[0].name;
  assert.equal(await (await signed(me, newest, "/" + newest)).text(), "backup 3");
  assert.equal((await signed(evil, newest, "/" + newest)).status, 404);
  // A signature by the wrong key is refused.
  const at = Date.now(), forged = crypto.sign("sha256", Buffer.from(["bluemob-backup-get", "list", at].join("|")), evil.privateKey).toString("base64");
  assert.equal((await fetch(`${base}/v1/backups?id=${me.id}&at=${at}&sig=${encodeURIComponent(forged)}`)).status, 403);
  srv.close();
});

test("problem reports are kept and readable only with the token", async () => {
  const os = require("os"), path = require("path"), fs = require("fs");
  const { createServer, Reports, Blobs, Backups } = require("./relay");
  const srv = createServer(new Store(null), new Map(), new Blobs(null), new Backups(null), new Reports(fs.mkdtempSync(path.join(os.tmpdir(), "rep-")), "secret-token"));
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const r = await fetch(`${base}/v1/report`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ text: "Sky gave a wrong answer", app: "0.14.0", device: "motorola" }) });
  assert.equal(r.status, 200);
  assert.equal((await fetch(`${base}/v1/reports?token=wrong`)).status, 403);
  const list = await (await fetch(`${base}/v1/reports?token=secret-token`)).json();
  assert.equal(list.reports[0].text, "Sky gave a wrong answer");
  // With a category, a sub-category and a photo; filtering and counts.
  const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(100, 7)]);
  const r2 = await fetch(`${base}/v1/report`, { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ text: "No voice", category: "Calls", sub: "No voice", photos: [jpeg.toString("base64"), Buffer.from("not a jpeg").toString("base64")] }) });
  const id = (await r2.json()).id;
  const calls = await (await fetch(`${base}/v1/reports?token=secret-token&category=Calls`)).json();
  assert.equal(calls.reports.length, 1);
  assert.equal(calls.reports[0].photos, 1, "only the real JPEG is kept");
  assert.equal(calls.counts.Calls["No voice"], 1);
  const photo = await fetch(`${base}/v1/reports/${id}/0?token=secret-token`);
  assert.equal(photo.headers.get("content-type"), "image/jpeg");
  assert.deepEqual(Buffer.from(await photo.arrayBuffer()), jpeg);
  assert.equal((await fetch(`${base}/v1/reports/${id}/0?token=wrong`)).status, 403);
  srv.close();
});

test("phone directory: only fingerprints, signed registration and lookups", async () => {
  const { createServer } = require("./relay");
  const store = new Store(null);
  const srv = createServer(store, new Map());
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const me = device(), friend = device();
  const h = crypto.createHash("sha256").update("bluemob-phone-v1|+919876543210").digest("hex");
  const at = Date.now();
  const reg = { pk: friend.pk, at, h, sig: crypto.sign("sha256", Buffer.from(["bluemob-phone", h, at].join("|")), friend.privateKey).toString("base64") };
  assert.equal((await fetch(`${base}/v1/phone`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(reg) })).status, 200);
  store.learnKey(me.id, me.pk);
  const look = (dev, hh) => { const t = Date.now(); const sig = crypto.sign("sha256", Buffer.from(["bluemob-phone-get", hh, t].join("|")), dev.privateKey).toString("base64");
    return fetch(`${base}/v1/phone?h=${hh}&id=${dev.id}&at=${t}&sig=${encodeURIComponent(sig)}`); };
  const r = await look(me, h);
  assert.equal(r.status, 200);
  assert.equal((await r.json()).id, friend.id);
  assert.equal((await look(me, "0".repeat(64))).status, 404);
  srv.close();
});

test("recovery email: signed, checked, removable", async () => {
  const { createServer } = require("./relay");
  const store = new Store(null);
  const srv = createServer(store, new Map());
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const me = device(), evil = device();
  const post = (dev, email, signer = dev) => { const at = Date.now();
    const sig = crypto.sign("sha256", Buffer.from(["bluemob-email", email.trim().toLowerCase(), at].join("|")), signer.privateKey).toString("base64");
    return fetch(`${base}/v1/email`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ pk: dev.pk, at, email, sig }) }); };
  assert.equal((await post(me, "Asha@Example.com")).status, 200);
  assert.equal(store.emails.get(me.id), "asha@example.com");
  assert.equal((await post(me, "not-an-email")).status, 400);
  assert.equal((await post(me, "x@y.com", evil)).status, 401);
  assert.equal((await post(me, "")).status, 200);
  assert.equal(store.emails.has(me.id), false);
  srv.close();
});

test("news from the team, account deletion, automatic reports", async () => {
  const { createServer, Store, Announcements, Reports } = require("./relay");
  const os = require("os"), fsx = require("fs"), pathx = require("path");
  const dir = fsx.mkdtempSync(pathx.join(os.tmpdir(), "bm-"));
  const store = new Store(null);
  const news = new Announcements(null);
  const srv = createServer(store, new Map(), undefined, undefined, new Reports(dir, "rt"), { news, adminToken: "admin" });
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const post = (p, b) => fetch(base + p, { method: "POST", body: JSON.stringify(b) });
  assert.equal((await post("/v1/announce", { token: "nope", title: "x", text: "y" })).status, 401);
  const r = await (await post("/v1/announce", { token: "admin", title: "Monsoon alert", text: "Heavy rain in Kerala this week." })).json();
  assert.equal(r.item.title, "Monsoon alert");
  const list = await (await fetch(base + "/v1/announcements?since=0")).json();
  assert.equal(list.items.length, 1);
  assert.equal((await (await fetch(base + "/v1/announcements?since=" + (r.item.at + 1))).json()).items.length, 0);
  // Delete my account: signed by the phone; the relay forgets its key, number and email.
  const a = device();
  store.learnKey(a.id, a.pk); store.apply({ op: "email", id: a.id, email: "a@b.co" }); store.apply({ op: "phone", id: a.id, h: "f".repeat(64) });
  const at = Date.now();
  const sig = crypto.sign("sha256", Buffer.from("bluemob-delete|" + at), a.privateKey).toString("base64");
  assert.equal((await post("/v1/account/delete", { pk: a.pk, at, sig: "AAAA" })).status, 401);
  assert.equal((await post("/v1/account/delete", { pk: a.pk, at, sig })).status, 200);
  assert.equal(store.keys.has(a.id), false);
  assert.equal(store.emails.has(a.id), false);
  assert.equal(store.phones.size, 0);
  // An automatic error report is kept and marked.
  assert.equal((await post("/v1/report", { text: "Crash: NullPointerException", category: "The app", sub: "Crashed", auto: true })).status, 200);
  const reps = await (await fetch(base + "/v1/reports?token=rt")).json();
  assert.equal(reps.reports[0].auto, true);
  srv.close();
});

test("privacy policy and account deletion pages are served", async () => {
  const { createServer, Store } = require("./relay");
  const srv = createServer(new Store(null), new Map(), undefined, undefined, undefined, { contactEmail: "help@example.org" });
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  for (const p of ["/privacy", "/privacy/hi", "/delete-account"]) {
    const r = await fetch(base + p);
    assert.equal(r.status, 200);
    const html = await r.text();
    assert.ok(html.includes("help@example.org"));
    assert.ok(!html.includes("{{EMAIL}}"));
  }
  srv.close();
});

test("admin: stats, report status, admin page; wrong token refused", async () => {
  const { createServer, Store, Reports } = require("./relay");
  const os = require("os"), fsx = require("fs"), pathx = require("path");
  const dir = fsx.mkdtempSync(pathx.join(os.tmpdir(), "bm-"));
  const srv = createServer(new Store(null), new Map(), undefined, undefined, new Reports(dir, ""), { adminToken: "adm", liveOnline: () => 3 });
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  await fetch(base + "/v1/report", { method: "POST", body: JSON.stringify({ text: "Map froze", category: "Compass & map", sub: "Trail & trips" }) });
  assert.equal((await fetch(base + "/v1/admin/stats", { headers: { "x-admin-token": "nope" } })).status, 403);
  const s = await (await fetch(base + "/v1/admin/stats", { headers: { "x-admin-token": "adm" } })).json();
  assert.equal(s.onlineNow, 3);
  assert.equal(s.openReports, 1);
  const list = await (await fetch(base + "/v1/reports", { headers: { "x-admin-token": "adm" } })).json();
  const id = list.reports[0].id;
  assert.equal((await fetch(base + "/v1/reports/status", { method: "POST", body: JSON.stringify({ token: "adm", id, status: "resolved", note: "fixed in 0.26" }) })).status, 200);
  const after = await (await fetch(base + "/v1/reports?token=adm")).json();
  assert.equal(after.reports[0].status, "resolved");
  assert.equal(after.reports[0].note, "fixed in 0.26");
  const page = await fetch(base + "/admin");
  assert.equal(page.status, 200);
  assert.match(page.headers.get("content-security-policy"), /default-src 'self'/);
  srv.close();
});

test("news to particular BlueMob IDs reaches only them", async () => {
  const { createServer, Store, Announcements } = require("./relay");
  const store = new Store(null), news = new Announcements(null);
  const srv = createServer(store, new Map(), undefined, undefined, undefined, { news, adminToken: "adm" });
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;
  const a = device(), b = device();
  store.learnKey(a.id, a.pk); store.learnKey(b.id, b.pk);
  const shown = "BM " + a.id.toUpperCase().match(/.{4}/g).join(" ");
  await fetch(base + "/v1/announce", { method: "POST", body: JSON.stringify({ token: "adm", title: "For Asha", text: "Your report is fixed", to: shown }) });
  await fetch(base + "/v1/announce", { method: "POST", body: JSON.stringify({ token: "adm", title: "Everyone", text: "Hello" }) });
  const ask = async (dev, signed = true) => {
    const at = Date.now();
    const sig = crypto.sign("sha256", Buffer.from("bluemob-news|" + at), dev.privateKey).toString("base64");
    const q = signed ? `&id=${dev.id}&at=${at}&sig=${encodeURIComponent(sig)}` : `&id=${dev.id}`;
    return (await (await fetch(base + "/v1/announcements?since=0" + q)).json()).items.map((n) => n.title).sort();
  };
  assert.deepEqual(await ask(a), ["Everyone", "For Asha"]);
  assert.deepEqual(await ask(b), ["Everyone"]);
  assert.deepEqual(await ask(a, false), ["Everyone"], "asking for someone else's ID without their key shows nothing private");
  srv.close();
});

test("SQLite storage keeps everything across restarts and imports the old file", () => {
  const { Store } = require("./relay");
  const os = require("os"), fsx = require("fs"), pathx = require("path");
  const dir = fsx.mkdtempSync(pathx.join(os.tmpdir(), "bm-db-"));
  // An old relay.jsonl from before SQLite.
  const old = new Store(pathx.join(dir, "relay.jsonl")), a = device(), c = device();
  assert.equal(old.put(seal(a, "rmsg", { id: "m1", to: c.id, at: 1, x: Date.now() + 1e6, c: "x" })), "ok");
  old.apply({ op: "email", id: a.id, email: "a@b.co" });
  // First start on SQLite imports it.
  const s1 = new Store(pathx.join(dir, "relay.db"));
  assert.ok(s1.db);
  assert.equal(s1.pull([c.id], {}).packets.length, 1);
  assert.equal(s1.emails.get(a.id), "a@b.co");
  assert.ok(fsx.existsSync(pathx.join(dir, "relay.jsonl.imported")));
  s1.apply({ op: "phone", id: c.id, h: "a".repeat(64) });
  s1.put(seal(a, "rmsg", { id: "m2", to: c.id, at: 2, x: Date.now() + 1e6, c: "y" }));
  s1.db.close();
  // Restart: everything is still there, and sequence numbers carry on.
  const s2 = new Store(pathx.join(dir, "relay.db"));
  assert.equal(s2.pull([c.id], {}).packets.length, 2);
  assert.equal(s2.phones.get("a".repeat(64)), c.id);
  assert.equal(s2.keys.get(a.id), a.pk);
  const before = s2.seq;
  s2.put(seal(a, "rmsg", { id: "m3", to: c.id, at: 3, x: Date.now() + 1e6, c: "z" }));
  assert.equal(s2.seq, before + 1);
  // Deleting an account removes it from the database too.
  s2.apply({ op: "forget", id: c.id });
  s2.db.close();
  const s3 = new Store(pathx.join(dir, "relay.db"));
  assert.equal(s3.pull([c.id], {}).packets.length, 0);
  assert.equal(s3.phones.size, 0);
  s3.db.close();
});
