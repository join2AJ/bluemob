// node --test server/push.test.js
"use strict";
const test = require("node:test");
const assert = require("node:assert");
const crypto = require("crypto");
const { createPusher } = require("./push");

test("signs in with the service account and sends a high-priority data message", async () => {
  const { privateKey, publicKey } = crypto.generateKeyPairSync("rsa", { modulusLength: 2048 });
  const account = { client_email: "relay@x.iam.gserviceaccount.com", private_key: privateKey.export({ type: "pkcs8", format: "pem" }), project_id: "bluemob-test" };
  const calls = [];
  const fetchImpl = async (url, opts) => {
    calls.push({ url, opts });
    if (url.includes("oauth2")) {
      // The JWT must verify with the account's public key.
      const jwt = decodeURIComponent(opts.body.split("assertion=")[1]);
      const [h, c, sig] = jwt.split(".");
      const ok = crypto.verify("RSA-SHA256", Buffer.from(h + "." + c), publicKey, Buffer.from(sig.replace(/-/g, "+").replace(/_/g, "/"), "base64"));
      assert.ok(ok);
      assert.equal(JSON.parse(Buffer.from(c, "base64")).iss, account.client_email);
      return { ok: true, json: async () => ({ access_token: "at-1", expires_in: 3600 }) };
    }
    return { ok: true, status: 200 };
  };
  const p = createPusher(account, { fetchImpl });
  assert.equal(await p.send("device-token", { k: "call" }), "ok");
  assert.equal(await p.send("device-token", { k: "msg" }), "ok");
  // One sign-in, two sends.
  assert.equal(calls.filter((c) => c.url.includes("oauth2")).length, 1);
  const msg = JSON.parse(calls[1].opts.body).message;
  assert.equal(calls[1].url, "https://fcm.googleapis.com/v1/projects/bluemob-test/messages:send");
  assert.equal(calls[1].opts.headers.authorization, "Bearer at-1");
  assert.deepEqual(msg, { token: "device-token", data: { k: "call" }, android: { priority: "high", ttl: "60s" } });
});

test("without a service account nothing is sent", async () => {
  const p = createPusher(null);
  assert.equal(p.configured, false);
  assert.equal(await p.send("t", {}), "off");
});
