// node --test server/live.test.js   (needs Node 22+ for the built-in WebSocket client)
"use strict";
const test = require("node:test");
const assert = require("node:assert");
const crypto = require("crypto");
const http = require("http");
const { attachLive } = require("./live");
const { Store } = require("./relay");

function device() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
  const der = publicKey.export({ format: "der", type: "spki" });
  return { pk: der.toString("base64"), id: crypto.createHash("sha256").update(der).digest().subarray(0, 8).toString("hex"), privateKey };
}

async function server(store = null) {
  const s = http.createServer((q, r) => r.end("ok"));
  const online = attachLive(s, { store });
  await new Promise((r) => s.listen(0, r));
  return { s, online, store, url: `ws://127.0.0.1:${s.address().port}/v1/live` };
}

/** Connects and signs in; resolves with the socket and a queue of received messages. */
function connect(url, dev, { forge } = {}) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    ws.binaryType = "arraybuffer";
    const inbox = [];
    const waiters = [];
    ws.onmessage = (e) => {
      const m = typeof e.data === "string" ? JSON.parse(e.data) : Buffer.from(e.data);
      if (m.t === "challenge") {
        const signer = forge || dev;
        ws.send(JSON.stringify({ t: "auth", pk: dev.pk, sig: crypto.sign("sha256", Buffer.from("bluemob-live|" + m.n), signer.privateKey).toString("base64") }));
        return;
      }
      if (m.t === "ok") return resolve({ ws, next });
      const w = waiters.shift(); if (w) w(m); else inbox.push(m);
    };
    ws.onclose = () => reject(new Error("closed"));
    function next() { return inbox.length ? Promise.resolve(inbox.shift()) : new Promise((r) => waiters.push(r)); }
  });
}

test("two signed-in phones exchange call set-up and media", async () => {
  const { s, online, url } = await server();
  const a = device(), b = device();
  const A = await connect(url, a), B = await connect(url, b);
  assert.equal(online.size, 2);
  A.ws.send(JSON.stringify({ t: "send", to: b.id, data: "{\"invite\":1}" }));
  assert.deepEqual(await B.next(), { t: "msg", from: a.id, data: "{\"invite\":1}" });
  // Binary: [target id | payload] arrives as [sender id | payload].
  A.ws.send(Buffer.concat([Buffer.from(b.id, "hex"), Buffer.from("Avoice")]));
  const got = await B.next();
  assert.equal(got.subarray(0, 8).toString("hex"), a.id);
  assert.equal(got.subarray(8).toString(), "Avoice");
  // Someone not signed in: the sender is told.
  A.ws.send(JSON.stringify({ t: "send", to: "00112233aabbccdd", data: "x" }));
  assert.deepEqual(await A.next(), { t: "offline", to: "00112233aabbccdd" });
  A.ws.send(JSON.stringify({ t: "presence", ids: [b.id, "00112233aabbccdd"] }));
  assert.deepEqual(await A.next(), { t: "presence", online: [b.id], via: {} });
  A.ws.close(); B.ws.close(); s.close();
});

test("can't sign in as someone else's ID", async () => {
  const { s, online, url } = await server();
  const victim = device(), evil = device();
  await assert.rejects(connect(url, victim, { forge: evil }));
  assert.equal(online.size, 0);
  s.close();
});

test("a phone that reconnects replaces its old link", async () => {
  const { s, online, url } = await server();
  const a = device(), b = device();
  const first = await connect(url, a);
  const second = await connect(url, a);
  const B = await connect(url, b);
  B.ws.send(JSON.stringify({ t: "send", to: a.id, data: "hi" }));
  assert.deepEqual(await second.next(), { t: "msg", from: b.id, data: "hi" });
  assert.equal(online.size, 2);
  first.ws.close(); second.ws.close(); B.ws.close(); s.close();
});

/** A signed chat-message envelope from [dev] to [to], like the app's MeshRouter makes. */
function rmsg(dev, to, id) {
  const b = JSON.stringify({ id, to, at: Date.now(), x: Date.now() + 3600e3, c: "sealed", from: dev.id });
  return { t: "rmsg", b, pk: dev.pk, s: crypto.sign("sha256", Buffer.from("rmsg\n" + b), dev.privateKey).toString("base64"), h: 0 };
}

test("a gateway carries phones near it that have no internet", async () => {
  const { s, url } = await server();
  const far = device(), gw = device();
  const offlineFriend = "0011223344556677";
  const F = await connect(url, far), G = await connect(url, gw);
  G.ws.send(JSON.stringify({ t: "via", ids: [offlineFriend] }));
  await new Promise((r) => setTimeout(r, 50));
  F.ws.send(JSON.stringify({ t: "presence", ids: [offlineFriend] }));
  assert.deepEqual(await F.next(), { t: "presence", online: [], via: { [offlineFriend]: gw.id } });
  // Signals go to the gateway as they are; it hands them on over the mesh.
  F.ws.send(JSON.stringify({ t: "send", to: offlineFriend, data: "{\"t\":\"app\"}" }));
  assert.deepEqual(await G.next(), { t: "msg", from: far.id, data: "{\"t\":\"app\"}" });
  // Call audio is wrapped in a relay frame: R | ttl | dest | origin | payload.
  F.ws.send(Buffer.concat([Buffer.from(offlineFriend, "hex"), Buffer.from("Avoice")]));
  const got = await G.next();
  assert.equal(got.subarray(0, 8).toString("hex"), far.id);
  assert.equal(got[8], 0x52);
  assert.equal(got.subarray(10, 18).toString("hex"), offlineFriend);
  assert.equal(got.subarray(18, 26).toString("hex"), far.id);
  assert.equal(got.subarray(26).toString(), "Avoice");
  // The gateway goes offline: nobody carries the friend any more.
  G.ws.close();
  await new Promise((r) => setTimeout(r, 100));
  F.ws.send(JSON.stringify({ t: "send", to: offlineFriend, data: "x" }));
  assert.deepEqual(await F.next(), { t: "offline", to: offlineFriend });
  F.ws.close(); s.close();
});

test("messages for someone offline are kept, and they're poked when one is stored for them", async () => {
  const store = new Store(null);
  const { s, url } = await server(store);
  const a = device(), b = device();
  const A = await connect(url, a);
  A.ws.send(JSON.stringify({ t: "send", to: b.id, data: JSON.stringify(rmsg(a, b.id, "m-1")) }));
  assert.deepEqual(await A.next(), { t: "offline", to: b.id });
  assert.equal(store.pull([b.id]).packets.length, 1);
  // b signs in; a message pushed over HTTP for b now pokes b straight away.
  const B = await connect(url, b);
  assert.equal(store.put(rmsg(a, b.id, "m-2")), "ok");
  assert.deepEqual(await B.next(), { t: "poke" });
  A.ws.close(); B.ws.close(); s.close();
});
