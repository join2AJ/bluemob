
  // =====================================================================
  //  Round 3: receipts & delivery paths, SOS note, incoming SOS alert
  // =====================================================================

  // ---------- what the two delivery paths are doing, in plain words ----------
  function pathText(m, path) {
    const p = P[m.to], o = m.paths[path], name = p ? p.name : "them";
    if (!o) return path === "direct" ? `Not possible: ${esc(name)} is far away` : "Not used";
    const t = o.time ? " · " + clock(o.time) : "";
    return {
      direct: { waiting: `Waiting for ${esc(name)} to come in range`, trying: "Sending now", delivered: "Delivered" + t,
        cancelled: "Not needed: delivered another way", discarded: "Arrived second. Discarded by message ID" },
      internet: { waiting: bridgeOnline() ? "Starting…" : "Waiting for someone nearby with internet (a bridge)", moving: "Travelling through Meera's internet",
        relay: `Stored at the relay until ${esc(name)} can be reached`, delivered: "Delivered" + t, cancelled: "Cancelled: delivered another way",
        discarded: "Arrived second. Discarded by message ID", "not-needed": "Not needed: delivered directly" },
    }[path][o.state] || o.state;
  }
  const pathDone = (s) => s === "delivered";
  const pathOff = (s) => ["cancelled", "discarded", "not-needed"].includes(s);

  /** One line under a message that is still waiting, or that won the race. */
  function pendingNote(m) {
    const p = P[m.to];
    if (!p || !m.paths) return "";
    const d = m.paths.direct, n = m.paths.internet;
    if (m.status === "pending") {
      if (n && n.state === "relay") return `⏳ Stored at the relay. Also goes by Bluetooth the moment ${esc(p.name)} is in range`;
      if (d && d.state === "waiting") return `⏳ Will go the first way possible: Bluetooth when ${esc(p.name)} is in range, or internet through a bridge`;
      return "⏳ Waiting for a bridge to the internet";
    }
    if (m.deliveredVia === "direct" && n && (n.state === "cancelled" || n.state === "discarded")) return "Delivered directly · internet copy " + (n.state === "cancelled" ? "cancelled" : "discarded");
    if (m.deliveredVia === "internet" && d && (d.state === "cancelled" || d.state === "discarded")) return "Delivered by internet · direct copy " + (d.state === "cancelled" ? "cancelled" : "discarded");
    return "";
  }

  // ---------- message info ----------
  const HOP_TEXT2 = {
    Ravi: "Hopped to Ravi's phone over Bluetooth",
    "Meera 🌐": "Reached Meera's phone, which has internet (the bridge)",
    Internet: "Crossed the internet to the BlueMob relay",
    SMS: "Turned into a normal text message (SMS)",
  };
  const secs = (t) => new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  function messageInfoView() {
    const c = convo(S.info.chat), m = c.messages.find((x) => x.id === S.info.mid);
    const to = S.info.chat === "sky" ? { name: "Sky", uid: null } : P[S.info.chat];
    const route = m.route || ["You", to.name];
    const statusWord = { read: "Read", delivered: "Delivered", pending: "Waiting to be delivered", sent: "On its way", sending: "Sending" }[m.status] || m.status;
    const viaWord = m.deliveredVia === "direct" ? (m.receipts[0]?.via || "Bluetooth / Wi-Fi") : m.deliveredVia === "internet" ? (to.sms ? "Text message through a bridge" : "Internet through a bridge") : "";
    const who = (title, name, id, sub) => `<div class="party"><span class="t-over">${title}</span><b>${name}</b><span class="uid">${id}</span>${sub ? `<span class="t-cap">${sub}</span>` : ""}</div>`;
    const pathRow = (path, icon, color) => {
      const st = m.paths[path] ? m.paths[path].state : "none";
      return `<div class="set"><span class="tile" style="background:${pathDone(st) ? "var(--pine)" : pathOff(st) || st === "none" ? "var(--ink-3)" : color}">${icon}</span>
        <span class="main"><span class="t-strong" style="display:block">${PATH_LABEL[path]}${m.deliveredVia === path ? ' <span class="tag pine">USED</span>' : ""}</span>
        <span class="t-cap">${pathText(m, path)}</span></span></div>`;
    };
    const timeline = route.map((h, i) => {
      const t = m.hopTimes && m.hopTimes[i];
      const label = i === 0 ? "Left your phone" : i === route.length - 1 ? `Delivered to ${esc(to.name)}` : HOP_TEXT2[h] || "Passed through " + esc(h);
      return `<li class="${t != null ? "done" : ""}"><i></i><span class="main"><span class="t-strong" style="display:block">${label}</span>
        <span class="t-cap num">${t != null ? secs(t) : m.status === "pending" && i === route.length - 1 ? "Waiting" : "Not yet"}</span></span></li>`;
    }).join("");
    return subScreen("Message info", `
      <div class="parties">
        ${who("From", esc(S.name), "BM · " + fmtId(MY_ID), "This phone")}
        <span class="arrow">${I.send}</span>
        ${who("To", esc(to.name), to.sms ? esc(to.phone) : to.uid ? "BM · " + fmtId(to.uid) : "On this phone", to.sms ? "By text message" : to.remote || to.presence === "offline" ? "Out of range" : "Nearby")}
      </div>
      <div class="msg me" style="max-width:100%;align-items:flex-end;margin:16px 0"><div class="bubble">${esc(m.text)}</div></div>

      <div class="status-card ${m.status === "read" || m.status === "delivered" ? "ok" : "wait"}">
        <span class="big-tick">${TICK[m.status] || ""}</span>
        <span><b style="display:block;font-size:18px">${statusWord}</b><span class="t-cap">${viaWord ? "by " + esc(viaWord) : pendingNote(m).replace("⏳ ", "") || "Sending"}</span></span>
      </div>

      <div class="group-label t-over">Receipts</div>
      <div class="group">${m.receipts.length ? m.receipts.map((r) => `<div class="set"><span class="tile" style="background:var(--pine)">${r.kind === "read" ? I.eye : I.check}</span>
          <span class="main"><span class="t-strong" style="display:block">${r.kind === "read" ? "Read" : "Delivered"} · ${secs(r.time)}</span><span class="t-cap">Receipt came back over ${esc(r.via)}</span></span></div>`).join("")
        : `<div class="set"><span class="main"><span class="t-sub">No receipt yet. You'll get one the moment it's delivered${to.sms ? "" : ", and another when it's read"}.</span></span></div>`}
        ${to.sms ? '<div class="set"><span class="main"><span class="t-cap">Text messages give a delivery receipt from the phone network, but no read receipt.</span></span></div>' : ""}</div>

      <div class="group-label t-over">Delivery paths · first one wins</div>
      <div class="group">${pathRow("direct", I.mesh, "var(--sky)")}${pathRow("internet", I.globe, "var(--ember)")}</div>

      <div class="group-label t-over">Journey</div>
      <div class="group spec"><ol class="timeline">${timeline}</ol></div>

      <div class="group-label t-over">History</div>
      <div class="group spec"><ol class="timeline">${(m.events || []).map((e) => `<li class="done"><i></i><span class="main"><span style="display:block">${esc(e.text)}</span><span class="t-cap num">${secs(e.time)}</span></span></li>`).join("")}</ol></div>

      <div class="group-label t-over">Duplicate protection</div>
      <div class="group spec"><p class="t-sub">This message's ID is <span class="uid" style="font-size:12px">${m.id}</span>. Every copy carries it. ${esc(to.name)}'s phone keeps the IDs it has seen and discards any second copy, and the relay deletes its stored copy once a delivery receipt arrives. So it's shown exactly once, however many ways it travels.</p></div>`);
  }

  // ---------- SOS note (optional) ----------
  S.sosNote = "";
  const SOS_REASONS = ["Injured", "Lost", "Medical", "Need water", "Stuck", "Cold", "Animal"];

  // ---------- receiving an SOS ----------
  S.alert = null;
  S.alertOpen = false;
  function receiveSos(fromId, text) {
    const p = P[fromId];
    if (!p || p.presence !== "online") return;
    p.sos = { time: now(), text, battery: 21 };
    S.alert = { from: fromId };
    S.alertOpen = true;
    convo(fromId).messages.push({ id: "m-" + uid(), me: false, text: "🆘 SOS: " + text, time: now() });
    if (S.chat !== fromId) convo(fromId).unread++;
    log("SOS received from " + p.name + ". Passed on to the bridge automatically");
    [0, 450, 900].forEach((d) => setTimeout(() => beep(260), d));
    render();
  }
  function sosAlertView() {
    if (!S.alertOpen || !S.alert) return "";
    const p = P[S.alert.from], s = p.sos;
    return `<div class="sos-alert" role="alertdialog" aria-label="SOS from ${esc(p.name)}">
      <div class="sos-alert-head"><span class="pulse"></span><span class="t-over" style="color:#fff">SOS RECEIVED · ${clock(s.time)}</span></div>
      <div class="sos-alert-body">
        ${avatar(p.avatar, p.id, 84)}
        <h1 class="t-title" style="margin-top:10px">${nameWithId(p)} needs help</h1>
        <p class="t-sub">${fmtDist(p.dist)} away to the ${cardinal(p.bearing)} · their battery ${s.battery}%</p>
        <div class="sos-quote">“${esc(s.text)}”</div>
        ${(() => { const r = rescueOf(p.id); return r && coming(r).length ? `<p class="t-strong" style="color:var(--pine);margin-top:10px">Already coming: ${coming(r).map((h) => esc(whoName(h.id))).join(", ")}</p>` : ""; })()}
        <p class="t-cap">Already passed on to anyone with internet, automatically.</p>
      </div>
      <div class="sos-alert-actions">
        <button class="btn" data-act="sos-coming" data-v="${p.id}" style="flex-direction:column;height:auto;padding:10px 0;line-height:1.2">I'm coming<span style="font-size:12px;font-weight:500;opacity:.85">Join the rescue group: directions and chat</span></button>
        <button class="btn secondary" data-act="sos-way" data-v="${p.id}">${I.compass} Show me the way</button>
        <button class="btn secondary" data-act="sos-howto">${I.book} How to help</button>
        <button class="btn text" data-act="sos-dismiss">Close</button>
      </div></div>`;
  }
