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
