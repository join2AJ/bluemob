  // ---------- message anyone by BlueMob ID ----------
  // On phones, a message to someone out of range is handed (end-to-end encrypted) to phones nearby, who carry it
  // and pass it on as people move, until it reaches them. Here, the carriers and the journey are simulated.
  S.byId = store.get("byid", []);
  const normId = (s) => { const h = String(s || "").toLowerCase().replace(/^\s*bm/, "").replace(/[^0-9a-z]/g, ""); return /^[0-9a-f]{16}$/.test(h) ? h.toUpperCase() : null; };
  function byIdPerson(b) {
    P[b.id] = { id: b.id, name: b.name, avatar: "✉️", uid: b.uid, byid: true, presence: "offline", met: true, lastSeen: 0, dist: 3200, bearing: 40, link: "" };
    FRIEND[b.id] = { rules: [], fallback: ["Got your message through the mesh! 🙌 Two phones carried it to me.", "Yes! Message received, no signal here either 😄"] };
  }
  S.byId.forEach(byIdPerson);
  function startById(idText, name) {
    const uidHex = normId(idText);
    if (!uidHex) return toast("A BlueMob ID has 16 letters and numbers, like BM 3F9A 1C2B 7D4E 8A01");
    if (uidHex === MY_ID) return toast("That's your own ID");
    const known = Object.values(P).find((p) => p.uid === uidHex);
    if (known) { back(); open("chat", { chat: known.id }); return; }
    const b = { id: "id-" + uidHex.slice(0, 8).toLowerCase(), uid: uidHex, name: (name || "").trim().slice(0, 24) || "BM " + uidHex.slice(0, 4) + " " + uidHex.slice(4, 8) };
    S.byId.push(b); store.set("byid", S.byId); byIdPerson(b);
    audit("mesh", `Added ${b.name} by BlueMob ID ${uidHex}`);
    back(); open("chat", { chat: b.id });
  }
  /** The mesh-carry path: ask for their key, hand encrypted copies to phones nearby, delivered when a carrier meets them. */
  function carryViaMesh(m, id) {
    const p = P[id];
    const carriers = nearby().filter((x) => x.presence === "online").slice(0, 2);
    m.paths = { mesh: { state: "waiting" } };
    m.status = "pending";
    note(m, "Not in range. Looking for a way to reach them through phones nearby");
    if (!S.mesh || !carriers.length) { note(m, "No one nearby yet. It waits on your phone and goes with the next person you meet"); refresh(); return; }
    later(700, () => {
      if (!p.keyKnown) { note(m, `Asked phones nearby for ${p.name}'s key, so it can be encrypted. ${carriers[0].name} knew it`); p.keyKnown = true; }
      m.paths.mesh.state = "moving"; m.status = "sent";
      Object.assign(m, { route: ["You", ...carriers.map((c) => c.name), "Tara", p.name], hop: 0, hopTimes: [now()] });
      note(m, `Handed to ${carriers.map((c) => c.name).join(" and ")} to carry toward them. It's encrypted: carriers can't read it`);
      refresh();
      const step = () => { m.hop++; m.hopTimes.push(now()); if (m.hop >= m.route.length - 1) { delivered(m, id, "mesh", "the mesh"); return; } refresh(); later(2200, step); };
      later(2200, step);
    });
  }
  function newChatView() {
    const known = [...nearby(), ...S.byId.map((b) => P[b.id])].filter((p, i, a) => p && a.indexOf(p) === i);
    return subScreen("New message", `
      <div class="id-card">
        <span class="t-over" style="color:rgba(255,255,255,.8)">YOUR BLUEMOB ID</span>
        <div class="id-big">BM ${MY_ID.match(/.{4}/g).join(" ")}</div>
        <p style="font-size:14px;opacity:.9;margin-top:6px">Give it to anyone, at home or on the trail. They can message you with it: no phone number, no internet. It can't be copied by another phone.</p>
        <div class="row" style="gap:8px;padding:12px 0 0"><button class="btn" style="background:#fff;color:var(--pine)" data-act="copy-id">Copy</button>
          <button class="btn secondary" style="background:transparent;color:#fff;box-shadow:inset 0 0 0 1px rgba(255,255,255,.6)" data-act="share-id">Share</button></div>
      </div>
      <div class="group-label t-over">Message someone by their ID</div>
      <form class="group spec" id="byid-form">
        <div class="field"><label class="t-over" for="byid-id">BlueMob ID</label><input id="byid-id" maxlength="30" placeholder="BM 3F9A 1C2B 7D4E 8A01" autocomplete="off" style="font-family:ui-monospace,monospace;text-transform:uppercase"></div>
        <div class="field"><label class="t-over" for="byid-name">Their name (optional)</label><input id="byid-name" maxlength="24" placeholder="e.g. Kabir from the bus"></div>
        <button class="btn" type="submit">Start chat</button>
        <button class="btn text" type="button" data-act="byid-example">Try an example ID</button>
      </form>
      <p class="t-cap" style="margin:10px 4px 0">If they're not nearby, your message is handed, encrypted, to phones around you. They carry it and pass it on as people move, until it reaches them. The more BlueMob phones around, the faster it gets there.</p>
      <div class="group-label t-over">People you know (${known.length})</div>
      <div class="group">${known.map((p) => `<div class="set">${avatar(p.avatar, p.id, 40, p.presence)}
        <span class="main"><span class="t-strong" style="display:block">${esc(p.name)}</span><span class="t-cap">BM ${p.uid.match(/.{4}/g).join(" ")} · ${p.byid ? "Added by ID" : statusLine(p)}</span></span>
        <button class="btn text" data-act="open-chat" data-id="${p.id}">Message</button></div>`).join("")}</div>`);
  }
