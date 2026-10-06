// node --test server/live.test.js   (needs Node 22+ for the built-in WebSocket client)
"use strict";
const test = require("node:test");
const assert = require("node:assert");
const crypto = require("crypto");
const http = require("http");
const { attachLive } = require("./live");

function device() {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
  const der = publicKey.export({ format: "der", type: "spki" });
  return { pk: der.toString("base64"), id: crypto.createHash("sha256").update(der).digest().subarray(0, 8).toString("hex"), privateKey };
}

async function server() {
  const s = http.createServer((q, r) => r.end("ok"));
  const online = attachLive(s);
  await new Promise((r) => s.listen(0, r));
  return { s, online, url: `ws://127.0.0.1:${s.address().port}/v1/live` };
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
  assert.deepEqual(await A.next(), { t: "presence", online: [b.id] });
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
