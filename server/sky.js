// Smart Sky: lets a phone with internet ask Claude anything.
//
// Off unless ANTHROPIC_API_KEY is set. Questions are signed by the phone's BlueMob key, and each
// device gets SKY_PER_DAY questions a day (default 30), so a leaked URL can't run up the bill.
// SKY_MODEL picks the model (default claude-opus-5-5; claude-sonnet-5-5 or claude-haiku-5-5 cost less).

const SYSTEM = `You are Sky, the assistant inside BlueMob, an app that lets people message, call and send SOS alerts over Bluetooth, Wi-Fi and the internet, even with no mobile network.
Help with anything the user asks: everyday questions, writing, planning, maths, learning, travel, health basics, first aid, survival and outdoor skills, and how to use BlueMob.
Answer on a phone screen: get to the point, use short paragraphs or short lists, and plain words. Reply in the user's language.
If someone may be in danger right now, first tell them to call local emergency services if they can, and to use BlueMob's SOS button (it reaches nearby phones even without signal and their SOS contacts over the internet). Then give clear, practical steps.
For medical, legal or money matters, give useful general information and say when a professional should check.
BlueMob basics: Chats (messages, groups, calls, files), Nearby (radar of phones around you), Compass (trail, back to base, sun, offline map), Guide (survival guides, quizzes, Sky), You (profile, SOS contacts, backup, settings). SOS is always free.`;

const DEFAULT_MODEL = "claude-opus-5-5";

/** Makes the function that asks Claude, or null when there is no API key. */
function claudeAsker(env = process.env) {
  if (!env.ANTHROPIC_API_KEY) return null;
  const { Anthropic } = require("@anthropic-ai/sdk");
  const client = new Anthropic({ maxRetries: 2, timeout: 60e3 });
  const model = env.SKY_MODEL || DEFAULT_MODEL;
  // Server-side fallbacks retry a declined request on the model Anthropic recommends for it.
  const fallbacks = /^claude-(opus|sonnet|fable)-5/.test(model);
  return async (messages) => {
    const req = { model, max_tokens: 4000, system: SYSTEM, messages };
    const r = fallbacks
      ? await client.beta.messages.create({ ...req, betas: ["server-side-fallback-2026-07-01"], fallbacks: "default" })
      : await client.messages.create(req);
    if (r.stop_reason === "refusal") return { answer: "", refused: true };
    const text = r.content.filter((b) => b.type === "text").map((b) => b.text).join("\n").trim();
    return { answer: text, cut: r.stop_reason === "max_tokens" };
  };
}

/** The last few turns of the phone's Sky chat, cleaned up into valid alternating messages. */
function toMessages(history, q) {
  const out = [];
  for (const h of (Array.isArray(history) ? history : []).slice(-12)) {
    const role = h && h.role === "assistant" ? "assistant" : "user";
    const text = String((h && h.text) || "").slice(0, 4000).trim();
    if (!text) continue;
    if (out.length && out[out.length - 1].role === role) out[out.length - 1].content += "\n\n" + text;
    else out.push({ role, content: text });
  }
  while (out.length && out[0].role !== "user") out.shift();
  if (out.length && out[out.length - 1].role === "user") out.pop(); // the new question takes its place
  out.push({ role: "user", content: q });
  return out;
}

/** Per-device daily allowance. */
class SkyLimits {
  constructor(perDay = 30) { this.perDay = perDay; this.used = new Map(); }
  take(id, now = Date.now()) {
    const day = Math.floor(now / 86400e3);
    const u = this.used.get(id);
    if (!u || u.day !== day) { this.used.set(id, { day, n: 1 }); return this.perDay - 1; }
    if (u.n >= this.perDay) return -1;
    u.n++;
    return this.perDay - u.n;
  }
}

module.exports = { claudeAsker, toMessages, SkyLimits, SYSTEM };
