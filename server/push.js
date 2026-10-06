// Wake-ups for phones that aren't connected: a call is ringing for them, or a message is waiting.
//
// Sent through Firebase Cloud Messaging (HTTP v1). The server signs in with a Google service account (a JWT signed
// with its key, swapped for an access token) and sends "data" messages that carry no message content: just
// "someone is calling" or "a message is waiting", so the phone wakes up and fetches it over the relay itself.
//
// Set FCM_SERVICE_ACCOUNT to the service account JSON (or FCM_SERVICE_ACCOUNT_FILE to its path). Without it, nothing
// is sent and everything else works as before. Zero dependencies.
"use strict";
const crypto = require("crypto");

const b64url = (buf) => Buffer.from(buf).toString("base64").replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");

function createPusher(account, { fetchImpl = globalThis.fetch, now = () => Date.now() } = {}) {
  if (!account || !account.client_email || !account.private_key || !account.project_id) return { configured: false, send: async () => "off" };
  let token = null, tokenUntil = 0;

  async function accessToken() {
    if (token && now() < tokenUntil - 60e3) return token;
    const iat = Math.floor(now() / 1000);
    const head = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const claims = b64url(JSON.stringify({
      iss: account.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: account.token_uri || "https://oauth2.googleapis.com/token", iat, exp: iat + 3600,
    }));
    const sig = b64url(crypto.sign("RSA-SHA256", Buffer.from(head + "." + claims), account.private_key));
    const r = await fetchImpl(account.token_uri || "https://oauth2.googleapis.com/token", {
      method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" },
      body: "grant_type=" + encodeURIComponent("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + head + "." + claims + "." + sig,
    });
    if (!r.ok) throw new Error("token " + r.status);
    const j = await r.json();
    token = j.access_token; tokenUntil = now() + (j.expires_in || 3600) * 1000;
    return token;
  }

  /** Sends [data] (string values only) to a device token. Resolves "ok", "gone" (token no longer valid) or "error". */
  async function send(deviceToken, data, ttlSeconds = 60) {
    try {
      const r = await fetchImpl(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: "Bearer " + await accessToken() },
        body: JSON.stringify({ message: { token: deviceToken, data, android: { priority: "high", ttl: ttlSeconds + "s" } } }),
      });
      if (r.ok) return "ok";
      if (r.status === 404 || r.status === 400) return "gone";
      return "error";
    } catch { return "error"; }
  }

  return { configured: true, send };
}

function loadAccount(env = process.env) {
  try {
    if (env.FCM_SERVICE_ACCOUNT) return JSON.parse(env.FCM_SERVICE_ACCOUNT);
    if (env.FCM_SERVICE_ACCOUNT_FILE) return JSON.parse(require("fs").readFileSync(env.FCM_SERVICE_ACCOUNT_FILE, "utf8"));
  } catch (e) { console.error("FCM service account unreadable:", e.message); }
  return null;
}

module.exports = { createPusher, loadAccount };
