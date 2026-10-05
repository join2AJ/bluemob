
  // =====================================================================
  //  Audit trail: append-only and tamper-evident.
  //  Each entry stores the SHA-256 of (previous hash + its own content).
  //  Nothing can edit or delete entries; if anything changed, the chain breaks.
  // =====================================================================
  function sha256(str) {
    const K = [0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
      0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
      0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
      0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
      0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
      0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2];
    const bytes = new TextEncoder().encode(str);
    const len = bytes.length, words = [];
    for (let i = 0; i < len; i++) words[i >> 2] |= bytes[i] << (24 - (i % 4) * 8);
    words[len >> 2] |= 0x80 << (24 - (len % 4) * 8);
    const total = (((len + 8) >> 6) + 1) * 16;
    for (let i = words.length; i < total; i++) words[i] = words[i] || 0;
    words[total - 1] = len * 8;
    let h = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
    const r = (x, n) => (x >>> n) | (x << (32 - n));
    for (let i = 0; i < total; i += 16) {
      const w = words.slice(i, i + 16).map((x) => x | 0);
      for (let t = 16; t < 64; t++) {
        const s0 = r(w[t - 15], 7) ^ r(w[t - 15], 18) ^ (w[t - 15] >>> 3), s1 = r(w[t - 2], 17) ^ r(w[t - 2], 19) ^ (w[t - 2] >>> 10);
        w[t] = (w[t - 16] + s0 + w[t - 7] + s1) | 0;
      }
      let [a, b, c, d, e, f, g, hh] = h;
      for (let t = 0; t < 64; t++) {
        const t1 = (hh + (r(e, 6) ^ r(e, 11) ^ r(e, 25)) + ((e & f) ^ (~e & g)) + K[t] + w[t]) | 0;
        const t2 = ((r(a, 2) ^ r(a, 13) ^ r(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) | 0;
        hh = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
      }
      h = h.map((x, k) => (x + [a, b, c, d, e, f, g, hh][k]) | 0);
    }
    return h.map((x) => (x >>> 0).toString(16).padStart(8, "0")).join("");
  }

  const AUDIT = [];
  const auditBody = (e) => `${e.seq}|${e.time}|${e.kind}|${e.text}`;
  function audit(kind, text) {
    const prev = AUDIT.length ? AUDIT[AUDIT.length - 1].hash : "0".repeat(64);
    const e = { seq: AUDIT.length + 1, time: now(), kind, text, prev };
    e.hash = sha256(prev + auditBody(e));
    AUDIT.push(Object.freeze(e));
  }
  /** Re-computes every fingerprint. Returns the number of the first broken entry, or 0 if the chain is intact. */
  function verifyAudit() {
    let prev = "0".repeat(64);
    for (const e of AUDIT) {
      if (e.prev !== prev || sha256(prev + auditBody(e)) !== e.hash) return e.seq;
      prev = e.hash;
    }
    return 0;
  }
  const AUDIT_KIND = { sos: ["SOS", "var(--rose)"], message: ["Message", "var(--pine)"], receipt: ["Receipt", "var(--pine)"], mesh: ["Mesh", "var(--sky)"],
    position: ["Position", "var(--ember)"], app: ["App", "var(--ink-3)"] };
  function auditView() {
    const broken = verifyAudit();
    return subScreen("Audit trail", `
      <div class="large-title" style="margin-top:8px"><h1 class="t-hero">Audit trail</h1>
        <p class="t-sub" style="margin-top:6px">Everything important that happened on this phone, in order. Entries can't be edited or deleted.</p></div>
      <div class="status-card ${broken ? "wait" : "ok"}"><span class="big-tick" style="font-size:26px">${broken ? "⚠️" : "🔒"}</span>
        <span><b style="display:block;font-size:17px">${broken ? "Entry " + broken + " was changed" : "Verified · " + AUDIT.length + " entries, chain intact"}</b>
        <span class="t-cap">Each entry holds a fingerprint (SHA-256) of the one before it. Changing or removing any entry breaks every fingerprint after it.</span></span></div>
      <div class="group-label t-over">Newest first</div>
      <div class="group">${AUDIT.slice().reverse().map((e) => `<div class="set" style="align-items:flex-start">
          <span class="tag" style="background:${AUDIT_KIND[e.kind][1]};color:#fff;margin-top:2px">${AUDIT_KIND[e.kind][0]}</span>
          <span class="main"><span style="display:block">${esc(e.text)}</span>
          <span class="t-cap num">#${e.seq} · ${new Date(e.time).toLocaleString([], { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", second: "2-digit" })} · <span class="uid">${e.hash.slice(0, 12)}…</span></span></span></div>`).join("")
        || '<div class="set"><span class="t-sub">Nothing yet.</span></div>'}</div>
      <div style="display:flex;justify-content:center;margin-top:16px"><button class="btn secondary" data-act="audit-copy">Copy the full trail</button></div>`);
  }
  audit("app", "BlueMob started · ID BM " + fmtId(MY_ID));
