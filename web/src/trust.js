  // ---------- star ratings: 0 to 5, from signed ratings people give each other ----------
  // Same rules as the app: everyone starts at 4; thanks +½, confirmed real SOS +¼, bad language −¾, fake SOS −1½;
  // one person counts at most twice per kind; result stays between 0 and 5. Here the other people's ratings are examples.
  const RKIND = {
    thanks: { label: "Appreciated their help", delta: 0.5, good: true, title: "👏 Appreciate their help", hint: "Adds to their stars. Say what they did, if you like" },
    genuine_sos: { label: "Their SOS was real", delta: 0.25, good: true, title: "✅ Their SOS was real", hint: "They really needed help" },
    bad_language: { label: "Bad or abusive language", delta: -0.75, good: false, title: "🚩 Report bad language", hint: "Abusive or offensive messages" },
    fake_sos: { label: "Fake or prank SOS", delta: -1.5, good: false, title: "⚠ Their SOS was fake or a prank", hint: "Only if you're sure. It costs them 1½ stars" },
  };
  const DAY = 864e5;
  const RATINGS = [
    { rater: "meera", subject: "ravi", kind: "genuine_sos", ctx: "s-old1", remark: "Really twisted his ankle, we carried him down", at: now() - 9 * DAY },
    { rater: "tara", subject: "ravi", kind: "fake_sos", ctx: "s-old2", remark: "Nobody was there, they laughed about it", at: now() - 2 * DAY },
    { rater: "ravi", subject: "asha", kind: "thanks", ctx: "general", remark: "Shared her water filter with our whole group", at: now() - 3 * DAY },
    { rater: "kabir", subject: "asha", kind: "thanks", ctx: "general", remark: "Guided us back to the trail at night", at: now() - 6 * DAY },
    { rater: "asha", subject: "meera", kind: "thanks", ctx: "general", remark: "Her internet got my message home", at: now() - DAY },
    { rater: "asha2", subject: "kabir", kind: "bad_language", ctx: "general", remark: "Abusive messages in the group", at: now() - 4 * DAY },
    ...store.get("myratings", []),
  ];
  function scoreOf(id) {
    const latest = new Map();
    for (const r of RATINGS) if (r.subject === id && r.rater !== id) {
      const k = r.rater + "|" + r.kind + "|" + r.ctx;
      if (!latest.has(k) || latest.get(k).at < r.at) latest.set(k, r);
    }
    const about = [...latest.values()];
    let stars = 4;
    const perRater = new Map();
    about.sort((a, b) => b.at - a.at).forEach((r) => {
      const k = r.rater + "|" + r.kind;
      const n = (perRater.get(k) || 0) + 1;
      perRater.set(k, n);
      if (n <= 2) stars += RKIND[r.kind].delta;
    });
    stars = Math.max(0, Math.min(5, stars));
    const count = (kind) => new Set(about.filter((r) => r.kind === kind).map((r) => r.rater)).size;
    return { stars, n: about.length, thanks: count("thanks"), bad: count("bad_language"), fake: count("fake_sos"), real: count("genuine_sos"), recent: about };
  }
  const starsLabel = (s) => (Math.round(s.stars * 10) / 10).toFixed(1);
  const starsHtml = (s, size = 16) => `<span class="stars" style="--p:${(Math.round(s.stars * 2) / 2 / 5) * 100}%;font-size:${size}px" aria-label="${starsLabel(s)} out of 5 stars">★★★★★</span>`;
  const starChip = (id) => { const s = scoreOf(id); return `<span class="star-chip">★ ${s.n ? starsLabel(s) : "New"}</span>`; };
  function rate(subject, kind, ctx, remark) {
    const mine = store.get("myratings", []).filter((r) => !(r.subject === subject && r.kind === kind && r.ctx === ctx));
    const r = { rater: "me", subject, kind, ctx, remark: (remark || "").trim().slice(0, 140), at: now() };
    mine.push(r); store.set("myratings", mine);
    for (let i = RATINGS.length - 1; i >= 0; i--) if (RATINGS[i].rater === "me" && RATINGS[i].subject === subject && RATINGS[i].kind === kind && RATINGS[i].ctx === ctx) RATINGS.splice(i, 1);
    RATINGS.push(r);
    audit("trust", `You rated ${P[subject] ? P[subject].name : subject}: ${RKIND[kind].label}${r.remark ? ' · "' + r.remark + '"' : ""}`);
    toast(RKIND[kind].good ? "Thanks sent. It adds to their stars" : "Reported. It's signed by your phone");
  }
  function trustSection(p) {
    const s = scoreOf(p.id);
    const sosCtx = Object.values(S.rescues || {}).find((r) => r.victim === p.id);
    const options = [["thanks", "general"], ...(sosCtx ? [["genuine_sos", sosCtx.id], ["fake_sos", sosCtx.id]] : []), ["bad_language", "general"]];
    const raterName = (id) => (id === "me" ? "You" : P[id] ? P[id].name : "Someone");
    return `
      <div class="trust-head">${starsHtml(s, 28)}<b>${s.n ? starsLabel(s) + " out of 5 · " + s.n + " rating" + (s.n === 1 ? "" : "s") : "New · no ratings yet"}</b>
        <span class="trust-tags">${s.thanks ? `<span class="tag pine">👏 ${s.thanks} thanked them</span>` : ""}${s.real ? `<span class="tag pine">✅ ${s.real} confirmed a real SOS</span>` : ""}${s.bad ? `<span class="tag ember">🚩 ${s.bad} language flag${s.bad > 1 ? "s" : ""}</span>` : ""}${s.fake ? `<span class="tag" style="background:var(--rose-tint,#FBE4E4);color:var(--rose)">⚠ ${s.fake} fake SOS report${s.fake > 1 ? "s" : ""}</span>` : ""}</span></div>
      <div class="group-label t-over">Rate ${esc(p.name)}</div>
      <div class="group">${options.map(([k, ctx]) => {
        const mine = RATINGS.find((r) => r.rater === "me" && r.subject === p.id && r.kind === k && r.ctx === ctx);
        return `<div class="set" style="flex-wrap:wrap"><span class="main"><span class="t-strong" style="display:block">${RKIND[k].title}</span><span class="t-cap">${mine ? "You did this " + ago(mine.at) + (mine.remark ? ': "' + esc(mine.remark) + '"' : "") : RKIND[k].hint}</span></span>
          <button class="btn text" style="color:${RKIND[k].good ? "var(--pine)" : "var(--rose)"}" data-act="rate-open" data-v="${k}|${ctx}">${mine ? "Change" : "Rate"}</button>
          ${S.rating === k + "|" + ctx ? `<form class="rate-form" id="rate-form" data-v="${k}|${ctx}"><input id="rate-remark" maxlength="140" placeholder="Add a remark (optional)" value="${esc(mine ? mine.remark : "")}"><button class="btn" type="submit" style="${RKIND[k].good ? "" : "background:var(--rose)"}">${RKIND[k].good ? "Send" : "Report"}</button></form>` : ""}</div>`;
      }).join("")}</div>
      <div class="group-label t-over">Remarks (${s.recent.length})</div>
      <div class="group">${s.recent.length ? s.recent.map((r) => `<div class="set"><span class="main"><span class="t-strong" style="display:block">${esc(raterName(r.rater))} <span class="t-cap">· ${ago(r.at)}</span></span>
        <span class="t-cap" style="color:${RKIND[r.kind].good ? "var(--pine)" : "var(--rose)"}">${RKIND[r.kind].label}</span>${r.remark ? `<span style="display:block;font-size:15px">“${esc(r.remark)}”</span>` : ""}</span></div>`).join("")
        : '<div class="set"><span class="t-sub">No ratings yet.</span></div>'}</div>
      <p class="t-cap" style="margin:12px 4px 0">Everyone starts at 4 stars, and the most is 5. Thanks adds ½, a confirmed real SOS ¼. A bad-language flag takes ¾, a fake SOS report 1½. One person counts at most twice per kind. Ratings are signed by the phone that gave them, so they can't be faked.</p>`;
  }
