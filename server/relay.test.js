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
  srv.close();
});
