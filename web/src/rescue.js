  // ---------- rescue groups: everyone answering one SOS ----------
  // Tapping "I'm coming" adds you to the SOS's group: directions to the person, who else is coming, and a shared chat.
  // On phones the group travels the mesh like the SOS itself. Here, the other helpers are simulated.
  S.rescues = {};
  S.rescue = null;
  const HELPER_REPLIES = ["On my way 🏃", "Stay where you are", "Can you hear my whistle?", "Shine your light", "I see you!", "Need more people"];
  const VICTIM_REPLIES = ["I can hear you!", "I can see your light", "Please hurry", "I'm OK, take care", "I'm by the water", "Battery is low"];
  const walkMin = (m) => Math.max(1, Math.round(m / 1.1 / 60));
  const whoName = (id) => (id === "me" ? "You" : P[id] ? P[id].name : id);

  /** Re-renders the rescue screen without losing what the user is typing. */
  function renderKeepDraft(toEnd) {
    const d = document.getElementById("rescue-draft");
    const val = d ? d.value : "", focused = d && document.activeElement === d;
    const sc = document.getElementById("scroll"), atEnd = sc ? sc.scrollHeight - sc.scrollTop - sc.clientHeight < 60 : true, pos = sc ? sc.scrollTop : 0;
    render();
    const d2 = document.getElementById("rescue-draft");
    if (d2) { d2.value = val; if (focused) d2.focus(); }
    const sc2 = document.getElementById("scroll");
    if (sc2) sc2.scrollTop = atEnd || toEnd ? sc2.scrollHeight : pos;
  }

  function newRescue(victim, note) {
    const r = { id: "sos-" + uid(), victim, note, time: now(), helpers: [], chat: [], ended: false };
    S.rescues[r.id] = r;
    return r;
  }
  const rescueOf = (victim) => Object.values(S.rescues).find((r) => r.victim === victim && !r.ended);
  const coming = (r) => r.helpers.filter((h) => h.status !== "left");
  const inRoom = (r) => r.victim === "me" || r.helpers.some((h) => h.id === "me" && h.status !== "left");

  function rescuePost(r, from, kind, text) {
    r.chat.push({ id: uid(), from, kind, text, time: now() });
    if (kind === "text") audit("mesh", `Rescue group for ${whoName(r.victim)}: ${whoName(from)}: "${text.slice(0, 80)}"`);
    const here = S.screen === "rescue" && S.rescue === r.id;
    if (here) renderKeepDraft(from === "me");
    else if (from !== "me" && inRoom(r)) toast(kind === "join" ? `${whoName(from)} is coming to help` : kind === "arrived" ? `${whoName(from)} has arrived` : `🆘 ${whoName(from)}: ${text}`);
  }
  function rescueJoin(r, who, dist) {
    if (r.helpers.some((h) => h.id === who && h.status !== "left")) return;
    r.helpers.push({ id: who, status: "coming", dist, at: now() });
    rescuePost(r, who, "join", `I'm coming · ${fmtDist(dist)} away, about ${walkMin(dist)} min`);
    if (who === "me") audit("sos", `Joined the rescue for ${whoName(r.victim)}: "I'm coming"`);
  }
  // Helpers walk closer every few seconds, so the preview shows live distances.
  setInterval(() => {
    let changed = false;
    for (const r of Object.values(S.rescues)) {
      if (r.ended) continue;
      for (const h of r.helpers) {
        if (h.status !== "coming") continue;
        h.dist = Math.max(8, h.dist - (h.id === "me" ? 25 : 35)); h.at = now(); changed = true;
        if (h.id !== "me" && h.dist <= 8) { h.status = "arrived"; rescuePost(r, h.id, "arrived", "I'm here"); }
      }
    }
    if (changed && S.screen === "rescue") renderKeepDraft();
    if (changed && S.screen === "soshub") render();
  }, 5000);

  // Someone nearby sent an SOS and we tapped "I'm coming".
  function joinRescueFor(victim) {
    const p = P[victim];
    const r = rescueOf(victim) || newRescue(victim, p.sos ? p.sos.text : "");
    rescueJoin(r, "me", p.dist);
    open("rescue", { rescue: r.id });
    if (!r.scripted) {
      r.scripted = true;
      later(2500, () => rescuePost(r, victim, "text", "Thank you! I'm under the big pine by the stream 🙏"));
      later(5500, () => P.meera && rescueJoin(r, "meera", 600));
      later(9500, () => rescuePost(r, "meera", "text", `I have a first-aid kit. ${S.name ? S.name + ", can" : "Can"} you bring a torch?`));
    }
  }
  // Our own SOS: people nearby answer and join our group.
  function startOwnRescue(note) {
    const r = newRescue("me", note);
    const near = nearby().filter((p) => p.presence === "online");
    if (near[0]) later(2500, () => { rescueJoin(r, near[0].id, near[0].dist); later(1500, () => rescuePost(r, near[0].id, "text", "Stay where you are. Coming with water and a torch.")); });
    if (near[1]) later(7000, () => rescueJoin(r, near[1].id, near[1].dist));
    return r;
  }
  function endOwnRescue() {
    const r = rescueOf("me");
    if (!r) return;
    rescuePost(r, "me", "ended", "You're safe now. Thank you, everyone 💚");
    r.ended = true;
  }
  function rescueSend(text) {
    const r = S.rescues[S.rescue];
    const t = (text || "").trim().slice(0, 300);
    if (!r || !t || r.ended) return;
    const d = document.getElementById("rescue-draft");
    if (d) d.value = "";
    rescuePost(r, "me", "text", t);
    if (r.victim !== "me" && Math.random() < 0.7) {
      later(2000, () => rescuePost(r, r.victim, "text", /whistle/i.test(t) ? "I can hear you! Keep going, you're close" : /light|torch/i.test(t) ? "I can see your light! 🙏" : "OK, thank you 🙏"));
    } else if (r.victim === "me" && coming(r)[0]) {
      later(2000, () => rescuePost(r, coming(r)[0].id, "text", "Got it. Hold on, we're close."));
    }
  }

  function rescueView() {
    const r = S.rescues[S.rescue];
    if (!r) return subScreen("Rescue", '<p class="t-sub" style="padding:24px">This rescue has ended.</p>');
    const mine = r.victim === "me", v = P[r.victim], me = r.helpers.find((h) => h.id === "me");
    const people = [mine ? "you" : v.name, ...coming(r).map((h) => h.id === "me" ? "you" : whoName(h.id))];
    const tag = r.ended ? `<span class="tag">ENDED</span>` : me && me.status === "arrived" ? `<span class="tag" style="background:var(--pine);color:#fff">ARRIVED</span>`
      : me && me.status === "coming" ? `<span class="tag" style="background:var(--sky);color:#fff">ON THE WAY</span>` : `<span class="tag" style="background:var(--rose);color:#fff">SOS</span>`;
    const helperRow = (h) => `<div class="set">${avatar(h.id === "me" ? S.avatar : P[h.id].avatar, h.id, 38)}
      <span class="main"><span class="t-strong" style="display:block">${h.id === "me" ? "You" : esc(P[h.id].name)}</span>
      <span class="t-cap">${h.status === "arrived" ? "With " + (mine ? "you" : esc(v.name)) : h.status === "left" ? "Can't come"
        : `${fmtDist(h.dist)} from ${mine ? "you" : esc(v.name)} · about ${walkMin(h.dist)} min`} · updated ${ago(h.at)}</span></span>
      <span class="tag" style="${h.status === "coming" ? "background:var(--sky-tint);color:var(--sky)" : h.status === "arrived" ? "background:var(--pine-tint);color:var(--pine)" : ""}">${h.status === "coming" ? "On the way" : h.status === "arrived" ? "Arrived" : "Left"}</span></div>`;
    const top = mine ? `
      <div class="rescue-card ${coming(r).length ? "good" : "wait"}">
        <h2 class="t-title">${r.ended ? "You're safe" : !coming(r).length ? "Your SOS is out. Waiting for someone to answer" : coming(r).length === 1 ? esc(whoName(coming(r)[0].id)) + " is coming to help" : coming(r).length + " people are coming to help"}</h2>
        ${(() => { const here = r.helpers.filter((h) => h.status === "arrived"), way = r.helpers.filter((h) => h.status === "coming").sort((a, b) => a.dist - b.dist);
          return here.length ? `<p style="margin-top:4px">${here.map((h) => esc(whoName(h.id))).join(", ")} ${here.length > 1 ? "are" : "is"} with you${way[0] ? `. ${esc(whoName(way[0].id))} is ${fmtDist(way[0].dist)} away` : ""}</p>`
            : way[0] ? `<p style="margin-top:4px">Nearest: ${esc(whoName(way[0].id))}, ${fmtDist(way[0].dist)} away · about ${walkMin(way[0].dist)} min</p>` : ""; })()}
        <p class="t-sub" style="margin-top:8px">Stay where you are if you can. Keep warm and save battery. When they're close, use the SOS signal so they can find you.</p>
        ${r.ended ? "" : `<div class="row" style="gap:8px;padding:12px 0 0"><button class="btn" style="background:var(--rose);flex:1;font-size:15px;padding:0 12px;white-space:nowrap" data-act="sos-light">Light & sound signal</button><button class="btn secondary" style="flex:1;font-size:15px;padding:0 12px;white-space:nowrap" data-act="sos-safe">I'm safe now</button></div>`}
      </div>` : `
      <div class="rescue-card">
        <div class="row" style="padding:0;gap:12px">${avatar(v.avatar, v.id, 52)}<div><h2 class="t-title">${esc(v.name)} needs help</h2>
          <p class="t-cap">SOS ${ago(r.time)} · their battery ${v.sos ? v.sos.battery : 21}%</p></div></div>
        ${r.note ? `<div class="sos-quote" style="margin:12px 0 0">“${esc(r.note)}”</div>` : ""}
        <div class="row" style="padding:14px 0 0;gap:14px"><span class="rescue-arrow" style="transform:rotate(${Math.round(v.bearing)}deg)"><svg viewBox="0 0 24 24"><path d="M12 2l7 18-7-4-7 4z" fill="currentColor"/></svg></span>
          <div><div class="t-hero" style="font-size:30px">${fmtDist(me ? me.dist : v.dist)}</div>
          <p class="t-sub">${Math.round(v.bearing)}° ${cardinal(v.bearing)} · about ${walkMin(me ? me.dist : v.dist)} min walk</p></div></div>
        <div class="where"><span class="t-over" style="color:var(--sky)">WHERE THEY ARE · GPS</span>
          <p>GPS 1 min ago, accurate to 8 m: 30.0837 N, 78.2663 E. Facing 230° SW.</p></div>
        <div class="row" style="gap:8px;padding:12px 0 0;flex-wrap:wrap"><button class="btn" data-act="nav-to" data-v="${v.id}">${I.compass} Navigate</button>
          <button class="btn secondary" data-act="open-article" data-v="fracture">Broken bones and sprains</button></div>
        ${inRoom(r) && !r.ended ? `<div class="row" style="gap:4px;padding:6px 0 0">${me.status === "coming" ? `<button class="btn text" data-act="rescue-here">I'm here ✓</button>` : ""}<button class="btn text" style="color:var(--ink-3)" data-act="rescue-leave">I can't come</button></div>` : ""}
      </div>`;
    const chat = r.chat.map((m) => m.kind !== "text"
      ? `<p class="sysline">${m.kind === "join" ? `${esc(whoName(m.from))} joined: ${esc(m.text)}` : m.kind === "arrived" ? `${esc(whoName(m.from))} arrived` : m.kind === "left" ? `${esc(whoName(m.from))} can't come after all` : esc(m.text)}</p>`
      : `<div class="rmsg ${m.from === "me" ? "me" : m.from === r.victim ? "victim" : ""}">${m.from === "me" ? "" : `<span class="who">${esc(whoName(m.from))}${m.from === r.victim ? " · needs help" : ""}</span>`}
          <div class="bubble">${esc(m.text)}</div><span class="when">${ago(m.time)}</span></div>`).join("");
    const bottom = r.ended ? `<p class="t-sub" style="text-align:center;padding:16px">${mine ? "You're" : esc(v.name) + " is"} safe. This rescue has ended. Thank you, everyone 💚</p>`
      : !inRoom(r) ? `<div style="padding:12px 14px"><p class="t-cap">Join to tell ${esc(v.name)} you're coming. Your position is shared with this group.</p>
          <button class="btn" style="width:100%;margin-top:8px;background:var(--rose)" data-act="rescue-join">I'm coming</button></div>`
      : `<div class="filters" style="margin:0;padding:8px 12px">${(mine ? VICTIM_REPLIES : HELPER_REPLIES).map((t) => `<button class="chip" data-act="rescue-quick" data-v="${esc(t)}">${esc(t)}</button>`).join("")}</div>
         <form class="composer" id="rescue-form" style="padding:0 10px 12px"><div class="field-pill"><input id="rescue-draft" autocomplete="off" placeholder="Message the group" aria-label="Message the group"></div>
         <button class="send" type="submit" aria-label="Send">${I.send}</button></form>`;
    return `<div class="screen grouped ${S._anim === "push" ? "push-in" : ""}" style="display:flex;flex-direction:column">
      <header class="bar solid"><button class="icon-btn" data-act="back" aria-label="Back">${I.back}</button>
        <div class="bar-title" style="opacity:1;transform:none;line-height:1.15">${mine ? "Your rescue" : "Helping " + esc(v.name)}<br><span class="t-cap" style="font-weight:400">${people.length} in this group · ${people.map(esc).join(", ")}</span></div>${tag}</header>
      <div class="scroll" id="scroll" style="flex:1">
        ${top}
        <div class="group-label t-over">${mine ? "Who's coming" : "Who's helping"}</div>
        <div class="group">${r.helpers.length ? r.helpers.map(helperRow).join("") : `<div class="set"><span class="t-sub">${mine ? "No one has answered yet. Your SOS keeps going out to every phone that comes into range." : "No one yet. Be the first."}</span></div>`}</div>
        ${!mine && me && me.status === "coming" ? `<div class="group-label t-over">Before you set off</div><div class="group spec"><p style="font-size:15px;line-height:1.6">• Tell someone where you're going, or post it here<br>• Take water, a light, a warm layer and any first-aid kit<br>• Don't become a second casualty: check for danger before you get close<br>• Call out and use your whistle as you get near</p></div>` : ""}
        <div class="group-label t-over">Group chat</div>
        <div class="rchat">${chat || '<p class="t-cap">No messages yet. Everyone in this group sees what you write here.</p>'}</div>
        <p class="t-cap" style="text-align:center;margin:14px 0 4px">Web preview: other helpers are simulated and walk closer every few seconds.</p>
      </div>
      <div style="background:var(--surface);border-top:1px solid var(--line)">${bottom}</div></div>`;
  }
  function rescueGroupsHtml() {
    const rooms = Object.values(S.rescues).filter((r) => inRoom(r) || !r.ended);
    if (!rooms.length) return "";
    return `<div class="section-h" style="margin-top:20px"><span class="t-over">Rescue groups</span></div><div class="rows">${rooms.map((r) => {
      const last = r.chat[r.chat.length - 1];
      return `<button class="row press" data-act="open-rescue" data-v="${r.id}"><span class="avatar" style="width:54px;height:54px"><span class="face" style="background:${r.ended ? "var(--sand-2)" : "var(--rose)"};font-size:24px">🆘</span></span>
        <span class="main"><span class="line1"><span class="name ellipsis">${r.victim === "me" ? "Your rescue" : "Help " + esc(P[r.victim].name)}</span><span class="tag ${r.ended ? "" : "ember"}">${r.ended ? "ENDED" : coming(r).length + " COMING"}</span></span>
        <span class="preview ellipsis">${last ? esc(whoName(last.from)) + ": " + esc(last.text) : esc(r.note || "SOS")}</span></span></button>`;
    }).join("")}</div>`;
  }
