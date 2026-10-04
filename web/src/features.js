
  // =====================================================================
  //  Features: identity, power, insights, compass, guide, SOS, games
  // =====================================================================

  // ---------- identity: every phone gets a permanent, unique ID on first launch ----------
  const MY_ID = store.get("id", null) || (() => {
    const bytes = new Uint8Array(8);
    (window.crypto || {}).getRandomValues ? crypto.getRandomValues(bytes) : bytes.forEach((_, i) => (bytes[i] = Math.random() * 256));
    const id = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("").toUpperCase();
    store.set("id", id);
    return id;
  })();
  const fmtId = (id) => id.match(/.{4}/g).join(" ");
  const shortId = (id) => "#" + id.slice(0, 4);
  // Two phones derive the same four symbols from their pair of IDs, so people can check in person
  // that they are really talking to each other (like Signal's safety numbers).
  const SAFETY = ["🌲", "🏔️", "🌊", "🔥", "🌙", "⭐", "🦅", "🐺", "🦋", "🌻", "🍀", "🐚", "🪨", "🌵", "🐢", "🦉"];
  function safetyCode(a, b) {
    const [x, y] = [a, b].sort();
    let h = 2166136261;
    for (const ch of x + y) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0; }
    return [0, 4, 8, 12].map((sh) => SAFETY[(h >>> sh) & 15]);
  }
  const dupName = (p) => Object.values(P).filter((q) => q.met && q.name === p.name).length > 1;
  const nameWithId = (p) => esc(p.name) + (dupName(p) ? ` <span class="uid">${shortId(p.uid)}</span>` : "");

  // ---------- power ----------
  Object.assign(S, {
    survival: store.get("survival", false),
    batteryPct: 64,
    steps: store.get("powerSteps", { saver: false, exempt: false }),
    navTarget: "camp", heading: 20, guideCat: null, bookmarks: store.get("bookmarks", []), gq: "",
    peopleFilter: "all", sort: "near", chartTable: false,
  });
  const hoursLeft = (survival) => Math.round((S.batteryPct / 100) * (survival ? 46 : 14));
  function applySurvival() {
    if (S.survival) document.documentElement.setAttribute("data-theme", "dark");
    else if (document.documentElement.getAttribute("data-theme") === "dark" && S._forcedDark) document.documentElement.removeAttribute("data-theme");
    S._forcedDark = S.survival;
  }
  applySurvival();

  function powerView() {
    const pct = S.batteryPct;
    const step = (key, n, title, body, btn) => `<div class="set" style="align-items:flex-start">
      <span class="tile" style="background:${S.steps[key] ? "var(--pine)" : "var(--ink-3)"}">${S.steps[key] ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10"/></svg>' : `<b>${n}</b>`}</span>
      <span class="main"><span class="t-strong" style="display:block">${title}</span><span class="t-cap">${body}</span></span>
      ${S.steps[key] ? '<span class="step-done">DONE</span>' : `<button class="btn small" data-act="power-step" data-v="${key}">${btn}</button>`}</div>`;
    return subScreen("Power", `
      <div class="large-title" style="margin-top:8px"><span class="tag ${S.survival ? "pine" : ""}">${S.survival ? "SURVIVAL POWER ON" : "NORMAL POWER"}</span>
        <h1 class="t-hero" style="margin-top:10px">Make the battery last</h1>
        <p class="t-sub" style="margin-top:6px">Keep BlueMob reachable for days, while the rest of the phone sleeps.</p></div>
      <div class="group spec">
        <div class="spec-row"><span class="t-strong">Battery</span><span class="num t-strong">${pct}%</span></div>
        <div class="gauge" role="meter" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100" aria-label="Battery"><span style="width:${pct}%"></span></div>
        <div class="hours">
          <div class="${S.survival ? "" : "win"}"><span class="t-cap">Normal use</span><b>${hoursLeft(false)} h</b></div>
          <div class="${S.survival ? "win" : ""}"><span class="t-cap">Survival power</span><b>${hoursLeft(true)} h</b></div>
        </div>
        <p class="t-cap">Estimate for a typical phone with the screen mostly off.</p>
      </div>
      <div class="group-label t-over">One switch</div>
      <div class="group">
        <div class="set"><span class="tile" style="background:var(--pine)">${I.leaf}</span>
          <span class="main"><span class="t-strong" style="display:block">Survival power mode</span>
          <span class="t-cap">Dark screens, slower scanning (every 30 s), location every 5 min, no animations</span></span>
          <button class="switch" role="switch" aria-checked="${S.survival}" aria-label="Survival power mode" data-act="survival"></button></div>
      </div>
      <div class="group-label t-over">Whole phone, except BlueMob</div>
      <div class="group">
        ${step("saver", 1, "Turn on phone Battery Saver", "Opens Android's Battery Saver. It slows every other app.", "Open")}
        ${step("exempt", 2, "Keep BlueMob running", "Lets BlueMob stay awake while Battery Saver is on, so messages still reach you.", "Allow")}
      </div>
      <div class="callout" style="margin-top:16px">Android doesn't allow any app to switch the whole phone into Battery Saver by itself. BlueMob takes you there in one tap and keeps itself awake, which together does the same job.</div>`);
  }

  // ---------- insights ----------
  // Example history from a 12-hour trip, plus whatever you do in this session (added to the last hour).
  const HOURLY = [3, 5, 2, 0, 0, 1, 4, 9, 12, 7, 6, 10];
  const BASE = { direct: 37, hopped: 13, bridge: 6, relayed: 21, sent: 59, delivered: 56 };
  function insightsView() {
    const sent = BASE.sent + STATS.sent, delivered = BASE.delivered + STATS.delivered;
    const parts = [
      { key: "direct", label: "Direct", color: "var(--c-direct)", v: BASE.direct + STATS.direct },
      { key: "hopped", label: "Hopped", color: "var(--c-hop)", v: BASE.hopped + STATS.hopped },
      { key: "bridge", label: "Via bridge", color: "var(--c-bridge)", v: BASE.bridge + STATS.bridge },
    ];
    const total = parts.reduce((n, x) => n + x.v, 0);
    const hourly = HOURLY.map((v, i) => (i === HOURLY.length - 1 ? v + STATS.hourNow : v));
    const hourLabel = (i) => { const d = new Date(now() - (HOURLY.length - 1 - i) * 3600e3); return d.getHours().toString().padStart(2, "0") + ":00"; };
    const peopleNow = nearby();
    const online = peopleNow.filter((p) => p.presence === "online");
    const quality = { "fast Wi-Fi link": 0.95, "good link": 0.7, "Bluetooth link": 0.42 };
    return subScreen("Mesh insights", `
      <div class="large-title" style="margin-top:8px"><h1 class="t-hero">Mesh insights</h1>
        <p class="t-sub" style="margin-top:6px">Last 12 hours. Example trip data plus what you do in this preview.</p></div>
      <div class="tiles">
        <div class="tile-stat"><span class="t-cap">Messages sent</span><b>${sent}</b><span class="delta">${STATS.sent ? "+" + STATS.sent + " this session" : "12 h"}</span></div>
        <div class="tile-stat"><span class="t-cap">Delivered</span><b>${Math.round((delivered / Math.max(1, sent)) * 100)}%</b><span class="delta">${delivered} of ${sent}</span></div>
        <div class="tile-stat"><span class="t-cap">Carried for others</span><b>${BASE.relayed + STATS.relayed}</b><span class="delta">your phone as a relay</span></div>
        <div class="tile-stat"><span class="t-cap">People online now</span><b>${online.length}</b><span class="delta">${peopleNow.length} met so far</span></div>
      </div>

      <div class="group-label t-over">How your messages travelled</div>
      <div class="group spec">
        <div class="spec-row"><span class="t-strong">${total} delivered messages</span><button class="btn text" data-act="chart-table">${S.chartTable ? "Show chart" : "Show table"}</button></div>
        ${S.chartTable ? `<table class="dtable"><thead><tr><th>Route</th><th>Messages</th></tr></thead><tbody>${parts.map((x) => `<tr><td>${x.label}</td><td>${x.v}</td></tr>`).join("")}
          ${hourly.map((v, i) => `<tr><td>${hourLabel(i)}</td><td>${v}</td></tr>`).join("")}</tbody></table>` : `
        <div class="stackbar" role="img" aria-label="${parts.map((x) => x.label + " " + x.v).join(", ")}">${parts.map((x) => `<span style="flex-grow:${x.v};background:${x.color}" title="${x.label}: ${x.v}"></span>`).join("")}</div>
        <div class="legend">${parts.map((x) => `<span><i style="background:${x.color}"></i>${x.label} <b>${x.v}</b> · ${Math.round((x.v / total) * 100)}%</span>`).join("")}</div>`}
      </div>

      ${S.chartTable ? "" : `<div class="group-label t-over">Messages per hour</div>
      <div class="group spec"><div class="chart" id="hourChart">${columnChart(hourly, hourLabel)}<div class="tip" id="tip"></div></div></div>`}

      <div class="group-label t-over">Link strength now</div>
      <div class="group">${online.length ? online.map((p) => `<div class="set">${avatar(p.avatar, p.id, 36)}
          <span class="main"><span class="t-strong" style="display:block">${nameWithId(p)}</span><span class="t-cap">${p.link} · ${p.rtt} ms</span></span>
          <span class="meter" style="max-width:110px" role="meter" aria-label="${esc(p.name)} link strength" aria-valuenow="${Math.round(quality[p.link] * 100)}" aria-valuemin="0" aria-valuemax="100"><span style="width:${quality[p.link] * 100}%"></span></span></div>`).join("")
        : '<div class="set"><span class="t-sub">No one connected right now. Switch the mesh on.</span></div>'}</div>`);
  }
  function columnChart(vals, labelFor) {
    const W = 340, H = 150, pad = { l: 24, r: 4, t: 10, b: 22 };
    const max = Math.max(4, Math.ceil(Math.max(...vals) / 4) * 4);
    const bw = (W - pad.l - pad.r) / vals.length, barW = Math.min(18, bw - 6);
    const y = (v) => pad.t + (H - pad.t - pad.b) * (1 - v / max);
    const grid = [0, max / 2, max].map((g) => `<line x1="${pad.l}" x2="${W - pad.r}" y1="${y(g)}" y2="${y(g)}" stroke="var(--line)" stroke-width="1"/>
      <text x="${pad.l - 6}" y="${y(g) + 4}" text-anchor="end" font-size="10" fill="var(--ink-3)">${g}</text>`).join("");
    const peak = vals.indexOf(Math.max(...vals));
    const bars = vals.map((v, i) => {
      const x = pad.l + i * bw + (bw - barW) / 2, top = y(v), h = Math.max(0, y(0) - top), r = Math.min(4, h);
      const path = h > 0 ? `M${x},${y(0)} V${top + r} Q${x},${top} ${x + r},${top} H${x + barW - r} Q${x + barW},${top} ${x + barW},${top + r} V${y(0)} Z` : "";
      return `<g class="col" data-i="${i}" data-v="${v}" data-l="${labelFor(i)}"><rect x="${pad.l + i * bw}" y="${pad.t}" width="${bw}" height="${H - pad.t}" fill="transparent"/>
        ${path ? `<path d="${path}" fill="var(--c-direct)" opacity="${i === vals.length - 1 ? 1 : .55}"/>` : ""}
        ${i % 3 === 2 || i === vals.length - 1 ? `<text x="${x + barW / 2}" y="${H - 6}" text-anchor="middle" font-size="10" fill="var(--ink-3)">${labelFor(i).slice(0, 2)}h</text>` : ""}
        ${i === peak ? `<text x="${x + barW / 2}" y="${top - 4}" text-anchor="middle" font-size="10" font-weight="700" fill="var(--ink-2)">${v}</text>` : ""}</g>`;
    }).join("");
    return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Messages per hour, last 12 hours, peak ${vals[peak]}">${grid}${bars}</svg>`;
  }
  app.addEventListener("pointermove", (e) => {
    const col = e.target.closest && e.target.closest(".col");
    const tip = document.getElementById("tip");
    if (!tip) return;
    if (!col) { tip.classList.remove("show"); return; }
    const box = document.getElementById("hourChart").getBoundingClientRect(), r = col.getBoundingClientRect();
    tip.textContent = `${col.dataset.l} · ${col.dataset.v} message${col.dataset.v === "1" ? "" : "s"}`;
    tip.style.left = r.left - box.left + r.width / 2 + "px";
    tip.style.top = "8px";
    tip.classList.add("show");
  });

  // ---------- compass & offline navigation ----------
  // Positions in metres on a local flat grid around where you started (x = east, y = north).
  const polar = (dist, bearing) => ({ x: dist * Math.sin((bearing * Math.PI) / 180), y: dist * Math.cos((bearing * Math.PI) / 180) });
  const TRAIL = [];
  { // a walk from base camp to here, so "back to camp" has a path to retrace
    let x = -420, y = -460;
    for (let i = 0; i < 46; i++) { TRAIL.push({ x, y }); x += 9 + Math.sin(i / 4) * 6; y += 10 + Math.cos(i / 5) * 5; }
  }
  const me = () => TRAIL[TRAIL.length - 1];
  const WAYPOINTS = store.get("waypoints", null) || [
    { id: "camp", name: "Base camp", emoji: "⛺", x: -420, y: -460 },
    { id: "water", name: "Stream", emoji: "💧", x: 260, y: 120 },
  ];
  function targets() {
    const m = me();
    const list = WAYPOINTS.map((w) => ({ ...w, kind: "place" }));
    nearby().filter((p) => p.presence === "online").forEach((p) => {
      const o = polar(p.dist, p.bearing);
      list.push({ id: p.id, name: p.name, emoji: p.avatar, x: m.x + o.x, y: m.y + o.y, kind: "friend" });
    });
    return list.map((t) => {
      const dx = t.x - m.x, dy = t.y - m.y;
      return { ...t, dist: Math.hypot(dx, dy), bearing: ((Math.atan2(dx, dy) * 180) / Math.PI + 360) % 360 };
    });
  }
  const CARD = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  const cardinal = (b) => CARD[Math.round(b / 45) % 8];
  // You wander a little while the preview runs, leaving a breadcrumb trail.
  setInterval(() => {
    const m = me(), a = ((S.heading + (Math.random() - .5) * 50) * Math.PI) / 180;
    TRAIL.push({ x: m.x + Math.sin(a) * 4, y: m.y + Math.cos(a) * 4 });
    if (TRAIL.length > 400) TRAIL.shift();
    if (S.mesh && Math.random() < .25) STATS.relayed++;
    if (S.onboarded && S.screen === "main" && S.tab === "compass") render();
  }, 5000);

  function compassView() {
    const ts = targets();
    const t = ts.find((x) => x.id === S.navTarget) || ts[0];
    const rel = ((t.bearing - S.heading) % 360 + 360) % 360;
    const mins = Math.max(1, Math.round(t.dist / 75)); // ~4.5 km/h walking
    const ticks = Array.from({ length: 72 }, (_, i) => {
      const a = i * 5, long = a % 30 === 0;
      return `<line x1="150" y1="${long ? 14 : 18}" x2="150" y2="26" stroke="${a === 0 ? "var(--rose)" : "var(--ink-3)"}" stroke-width="${long ? 2 : 1}" transform="rotate(${a} 150 150)"/>`;
    }).join("");
    const letters = [["N", 0], ["E", 90], ["S", 180], ["W", 270]].map(([l, a]) =>
      `<text x="150" y="48" text-anchor="middle" font-size="16" font-weight="800" fill="${l === "N" ? "var(--rose)" : "var(--ink-2)"}" transform="rotate(${a} 150 150)">${l}</text>`).join("");
    return `
      <div class="large-title"><div class="t-sub">Works with no signal · GPS + compass</div><h1 class="t-hero">Compass</h1></div>
      <div class="targets">${ts.map((x) => `<button class="chip" data-act="nav-target" data-v="${x.id}" aria-pressed="${x.id === t.id}">${x.emoji} ${esc(x.name)}</button>`).join("")}</div>
      <div class="compass" aria-label="Compass. ${esc(t.name)} is ${fmtDist(t.dist)} to the ${cardinal(t.bearing)}">
        <svg class="dial" viewBox="0 0 300 300" style="transform:rotate(${-S.heading}deg)" aria-hidden="true">
          <circle cx="150" cy="150" r="146" fill="var(--canvas)" stroke="var(--line)" stroke-width="2"/>${ticks}${letters}
        </svg>
        <div class="needle" style="transform:rotate(${rel}deg)" aria-hidden="true">
          <svg viewBox="0 0 300 300" width="100%" height="100%"><path d="M150 40 L170 104 L150 94 L130 104 Z" fill="var(--pine)"/></svg>
        </div>
        <div class="hub"><span style="font-size:22px">${t.emoji}</span><b>${fmtDist(t.dist)}</b><span class="t-cap">${cardinal(t.bearing)} · ${Math.round(t.bearing)}°</span></div>
      </div>
      <div class="turn">
        <button class="btn small secondary" data-act="turn" data-v="-30" aria-label="Turn left">↺ Turn left</button>
        <button class="btn small secondary" data-act="turn" data-v="30" aria-label="Turn right">Turn right ↻</button>
      </div>
      <p class="t-sub" style="text-align:center;margin-top:10px">${rel < 15 || rel > 345 ? "Straight ahead" : rel < 180 ? "Turn right " + Math.round(rel) + "°" : "Turn left " + Math.round(360 - rel) + "°"} · about ${mins} min walk</p>

      <div class="section-h"><h2 class="t-head">Your trail</h2><button class="btn text" data-act="save-spot">+ Save this spot</button></div>
      <div class="minimap">${miniMap(ts, t)}</div>

      <div class="section-h"><h2 class="t-head">How it works offline</h2></div>
      <div class="group">
        ${[["var(--pine)", I.pin, "GPS needs no internet", "Your phone hears satellites directly. Works in airplane mode."],
           ["var(--sky)", I.radar, "Compass from the phone's sensor", "The magnetometer gives your heading. No data needed."],
           ["var(--ember)", I.mesh, "Friends' positions over the mesh", "Nearby phones share where they are, so you can walk to each other."],
           ["#7C6BD6", I.globe, "Maps: download before you go", "Save an area's map (OpenStreetMap) at home. Then maps and routes work offline."]]
          .map(([c, ic, a, b]) => `<div class="set"><span class="tile" style="background:${c}">${ic}</span><span class="main"><span class="t-strong" style="display:block">${a}</span><span class="t-cap">${b}</span></span></div>`).join("")}
      </div>
      <p class="t-cap" style="text-align:center;margin-top:20px">Web preview: your heading and walk are simulated. Use Turn left / right to rotate.</p>`;
  }
  function miniMap(ts, target) {
    const pts = [...TRAIL, ...ts];
    const minX = Math.min(...pts.map((p) => p.x)) - 60, maxX = Math.max(...pts.map((p) => p.x)) + 60;
    const minY = Math.min(...pts.map((p) => p.y)) - 60, maxY = Math.max(...pts.map((p) => p.y)) + 60;
    const W = 340, H = 220, s = Math.min(W / (maxX - minX), H / (maxY - minY));
    const ox = (W - (maxX - minX) * s) / 2, oy = (H - (maxY - minY) * s) / 2;
    const X = (x) => ox + (x - minX) * s, Y = (y) => H - (oy + (y - minY) * s);
    const m = me();
    const scaleM = 200, scalePx = scaleM * s;
    const trail = TRAIL.map((p) => `${X(p.x).toFixed(1)},${Y(p.y).toFixed(1)}`).join(" ");
    return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Map of your trail and places, north up">
      <defs><pattern id="grid" width="24" height="24" patternUnits="userSpaceOnUse"><path d="M24 0H0V24" fill="none" stroke="var(--line)" stroke-width="1"/></pattern></defs>
      <rect width="${W}" height="${H}" fill="url(#grid)"/>
      <line x1="${X(m.x)}" y1="${Y(m.y)}" x2="${X(target.x)}" y2="${Y(target.y)}" stroke="var(--pine)" stroke-width="2" stroke-dasharray="4 5"/>
      <polyline points="${trail}" fill="none" stroke="var(--ember)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" opacity=".85"/>
      ${ts.map((t) => `<g><circle cx="${X(t.x)}" cy="${Y(t.y)}" r="${t.id === target.id ? 15 : 12}" fill="var(--canvas)" stroke="${t.id === target.id ? "var(--pine)" : "var(--line)"}" stroke-width="2"/>
        <text x="${X(t.x)}" y="${Y(t.y) + 5}" text-anchor="middle" font-size="${t.id === target.id ? 15 : 12}">${t.emoji}</text></g>`).join("")}
      <g transform="translate(${X(m.x)} ${Y(m.y)}) rotate(${S.heading})"><path d="M0 -20 L9 2 L-9 2 Z" fill="var(--sky)" opacity=".35"/></g>
      <circle cx="${X(m.x)}" cy="${Y(m.y)}" r="7" fill="var(--sky)" stroke="var(--canvas)" stroke-width="2.5"/>
      <text x="${W - 12}" y="20" text-anchor="end" font-size="12" font-weight="800" fill="var(--rose)">N ↑</text>
      <line x1="12" y1="${H - 14}" x2="${12 + scalePx}" y2="${H - 14}" stroke="var(--ink-2)" stroke-width="2"/>
      <text x="12" y="${H - 20}" font-size="10" fill="var(--ink-2)">${scaleM} m</text>
    </svg>`;
  }

  // ---------- survival guide (written for BlueMob; general guidance, not medical advice) ----------
  const CATS = [
    { id: "aid", name: "First aid", color: "var(--rose)", icon: I.plusBox },
    { id: "water", name: "Water", color: "var(--sky)", icon: I.drop },
    { id: "fire", name: "Fire", color: "var(--ember)", icon: I.flame },
    { id: "shelter", name: "Shelter", color: "var(--pine)", icon: I.tent },
    { id: "nav", name: "Navigation", color: "#7C6BD6", icon: I.compass },
    { id: "signal", name: "Signals", color: "#C2621A", icon: I.flag },
    { id: "weather", name: "Weather", color: "#3D8FD9", icon: I.bolt },
    { id: "disaster", name: "Disasters", color: "#8A5A44", icon: I.alert },
    { id: "basics", name: "Basics", color: "#3A4A44", icon: I.leaf },
  ];
  const A = (id, cat, title, mins, intro, steps, donts = [], note = "") => ({ id, cat, title, mins, intro, steps, donts, note });
  const ARTICLES = [
    A("cpr", "aid", "CPR for an adult", 3, "Use when someone is unresponsive and not breathing normally.", [
      "Check the area is safe. Tap their shoulders and shout. Look for normal breathing for no more than 10 seconds.",
      "Send someone to call emergency services and find a defibrillator (AED), or use BlueMob to reach someone with signal.",
      "Kneel beside them. Put the heel of one hand in the centre of the chest, other hand on top, arms straight.",
      "Push hard and fast: 5 to 6 cm deep, 100 to 120 pushes a minute. Let the chest rise fully between pushes.",
      "If you are trained, give 2 rescue breaths after every 30 pushes. If not, keep pushing without stopping.",
      "Keep going until help takes over, an AED tells you to stop, or they start breathing normally."],
      ["Don't stop to check for a pulse unless they start breathing.", "Don't give up early. Swap with someone every 2 minutes if you can."]),
    A("bleed", "aid", "Severe bleeding", 2, "Fast action matters most. Pressure stops most bleeding.", [
      "Protect yourself with gloves or a plastic bag if you can.",
      "Press firmly on the wound with a clean cloth or your hand. Keep pressing.",
      "If blood soaks through, add more cloth on top. Keep the first layer in place.",
      "Wrap a bandage tightly over the pad to hold pressure.",
      "For life-threatening bleeding from an arm or leg that won't stop, tie a tourniquet 5 to 7 cm above the wound, not on a joint. Tighten until bleeding stops and note the time.",
      "Keep them warm and lying down. Get medical help."],
      ["Don't remove soaked dressings.", "Don't loosen a tourniquet once it is on."]),
    A("burns", "aid", "Burns", 2, "Cool the burn quickly and for long enough.", [
      "Move away from the heat source.",
      "Cool the burn under cool running water for 20 minutes.",
      "Remove rings, watches or clothing near the burn, unless stuck to the skin.",
      "Cover loosely with cling film or a clean, non-fluffy cloth.",
      "Get medical help for burns larger than their hand, or on the face, hands, feet or groin, and for chemical or electrical burns."],
      ["No ice, butter, oil or toothpaste.", "Don't burst blisters."]),
    A("choke", "aid", "Choking adult", 2, "If they can cough, encourage them to keep coughing.", [
      "If they can't cough, speak or breathe, stand behind them and lean them forward.",
      "Give up to 5 firm back blows between the shoulder blades with the heel of your hand.",
      "If that fails, give up to 5 abdominal thrusts: fist above the belly button, pull sharply in and up.",
      "Repeat 5 back blows and 5 thrusts until the object comes out.",
      "If they become unresponsive, start CPR and get help."]),
    A("hypo", "aid", "Hypothermia (too cold)", 2, "Signs: strong shivering, confusion, slurred speech, clumsiness, drowsiness.", [
      "Get them out of wind and wet, into shelter.",
      "Replace wet clothes with dry ones. Insulate them from the ground.",
      "Warm the body core first (chest, neck, groin) with blankets, a sleeping bag or skin-to-skin contact.",
      "If they are alert and can swallow, give warm, sweet drinks.",
      "Handle them gently and get medical help."],
      ["No alcohol.", "Don't rub their arms and legs.", "Don't put them in a hot bath."]),
    A("heat", "aid", "Heat stroke", 2, "An emergency. Signs: hot skin, confusion, fast pulse, sometimes no sweating.", [
      "Move them to shade and get help.",
      "Cool them fast: wet their skin and fan them.",
      "Put cold packs or wet cloths on the neck, armpits and groin.",
      "If they are awake, give small sips of water.",
      "Keep cooling until help arrives or they feel normal."]),
    A("snake", "aid", "Snake bite", 2, "Most bites are not fatal. Calm and stillness help.", [
      "Move away from the snake. Keep the person calm and still.",
      "Keep the bitten limb still and remove rings, watches and tight clothing.",
      "Note the time and what the snake looked like. Don't try to catch it.",
      "Get to medical help as soon as possible. Carry them if you can."],
      ["Don't cut the wound or try to suck out venom.", "Don't use ice or a tight tourniquet."]),
    A("fracture", "aid", "Broken bones and sprains", 2, "Support the injury in the position you found it.", [
      "Keep the injured part still. Don't try to straighten it.",
      "Splint it with padding, using sticks or a rolled mat, covering the joint above and below.",
      "Check the skin beyond the injury stays warm and pink.",
      "For sprains: rest, a cold pack wrapped in cloth for 20 minutes, light compression, and raise it.",
      "Get medical help for suspected breaks."]),
    A("find-water", "water", "Finding water", 2, "You can last about 3 days without water. Find it early.", [
      "Walk downhill: valleys and low ground often hold streams.",
      "Look for green plants, insects and animal tracks. Birds often fly to water at dawn and dusk.",
      "Collect rain with any sheet or container.",
      "At dawn, wipe dew off grass with a cloth and wring it out.",
      "Treat all water before drinking."],
      ["Don't drink seawater or urine.", "Avoid still, smelly water if anything better exists."]),
    A("purify", "water", "Making water safe", 2, "Boiling is the most reliable method.", [
      "If water is cloudy, let it settle, then pour it through a cloth.",
      "Bring it to a rolling boil for 1 minute (3 minutes above 2,000 m).",
      "Or use purification tablets, following the packet instructions.",
      "No fuel? Fill clear plastic bottles and leave them in full sun for 6 hours (2 days if cloudy).",
      "Store treated water in clean, covered containers."]),
    A("fire", "fire", "Lighting a fire safely", 3, "A fire needs three things: heat, fuel and air.", [
      "Pick a spot out of the wind, away from trees and tents. Clear 3 m of ground down to soil.",
      "Gather everything first: tinder (dry grass, bark), kindling (pencil-thick sticks) and fuel (wrist-thick wood).",
      "Make a loose tinder nest. Lean kindling over it like a tent, leaving gaps for air.",
      "Light the tinder from the windward side and blow gently at the base.",
      "Add bigger wood slowly as the flames grow.",
      "To put it out: drown it, stir it, and drown again until it's cold to the touch."],
      ["Never leave a fire alone.", "Don't use petrol to start a fire."]),
    A("shelter", "shelter", "Emergency shelter", 3, "In bad weather, cold can harm you in hours. Shelter comes before food.", [
      "Choose dry, flat ground out of the wind. Avoid dry riverbeds and dead trees overhead.",
      "Insulate yourself from the ground first: you lose most heat there. Use branches, leaves or a mat.",
      "Keep it small, so your body heat warms it.",
      "Lean-to: prop a long branch against a tree and layer sticks, then leaves, on the windward side.",
      "Stay dry. Wet clothes lose most of their warmth."]),
    A("north", "nav", "Find north without a compass", 3, "Simple ways to find your direction.", [
      "Shadow stick: put a stick upright and mark the tip of its shadow. Wait 15 minutes and mark again. A line from the first mark to the second points roughly east.",
      "The sun rises in the east and sets in the west. At midday it is due south in the northern hemisphere, due north in the southern.",
      "At night (north): find the Big Dipper. Its two end stars point to Polaris, the North Star.",
      "At night (south): extend the long axis of the Southern Cross about 4.5 times to find roughly south."]),
    A("lost", "nav", "If you are lost", 2, "Remember STOP: Stop, Think, Observe, Plan.", [
      "Stop moving. Sit down, drink water and calm down.",
      "Think about how you got here and the last place you were sure of.",
      "Observe: landmarks, the sun, streams, your trail on the BlueMob compass.",
      "Plan: if people know your route, stay put and make yourself visible. Searchers find people who stay still.",
      "Use BlueMob to reach anyone nearby, and save your battery with Survival power."]),
    A("signals", "signal", "Signalling for rescue", 2, "Three of anything means 'help'.", [
      "Three fires, three whistle blasts or three flashes in a triangle or row signal distress.",
      "SOS in Morse code: three short, three long, three short ( ··· ––– ··· ). BlueMob's SOS light flashes it for you.",
      "Make ground signals big (3 m or more) and in contrast with the ground: V means 'need help', X means 'need medical help'.",
      "Flash a mirror or phone screen at aircraft or distant people.",
      "Wear or spread bright clothing in open ground."]),
    A("help-sos", "signal", "Someone sent an SOS: how to help", 3, "You may be the fastest help they have. Stay safe yourself first.", [
      "Reply at once so they know someone is coming. BlueMob's \"I'm coming\" button does this.",
      "BlueMob passes their SOS on to anyone with internet automatically. Keep the mesh on so it can.",
      "Check it's safe for you to go: weather, light, terrain. Don't become a second person who needs rescue.",
      "Tell someone near you where you're going, and go in pairs if you can.",
      "Take water, a warm layer, a light, and a first-aid kit if you have one.",
      "Follow the compass to them. BlueMob shows their direction and distance.",
      "When you reach them: check for danger, then whether they respond and are breathing. Use the first-aid guides.",
      "Keep them warm and still. Don't move someone who may have a neck or back injury unless they are in danger.",
      "Stay with them and send updates to the group until help arrives."],
      ["Don't rush in alone at night or in a storm.", "Don't give them food or drink if they may need surgery or are barely conscious."]),
    A("lightning", "weather", "Lightning safety", 2, "If you hear thunder, you are close enough to be struck.", [
      "30-30 rule: if thunder follows the flash within 30 seconds, take shelter. Wait 30 minutes after the last thunder.",
      "Go to a building or a hard-topped vehicle if you can.",
      "Outdoors: leave summits, ridges, open fields and water. Keep away from lone trees and metal fences.",
      "If caught in the open, crouch low on the balls of your feet, feet together, head tucked.",
      "In a group, spread out at least 15 m apart."]),
    A("quake", "disaster", "Earthquake", 2, "Drop, Cover, Hold on.", [
      "Drop to your hands and knees.",
      "Cover your head and neck under a sturdy table, or next to an inside wall.",
      "Hold on until the shaking stops.",
      "If outside, move to open ground away from buildings, trees and power lines.",
      "After: expect aftershocks, check for gas smells, and avoid lifts and damaged buildings."]),
    A("flood", "disaster", "Floods", 2, "Moving water is far more powerful than it looks.", [
      "Move to higher ground straight away.",
      "Never walk, swim or drive through flood water. 15 cm of moving water can knock you down, 30 cm can sweep a car away.",
      "Stay away from rivers, streams and drains.",
      "If trapped in a building, go to the highest floor, not a closed attic.",
      "Use BlueMob to tell others where you are."]),
    A("threes", "basics", "The rule of threes", 1, "What to deal with first. Roughly, you can survive:", [
      "3 minutes without air.",
      "3 hours without shelter in harsh weather.",
      "3 days without water.",
      "3 weeks without food.",
      "So: breathing and bleeding first, then shelter and warmth, then water, then food."],
      ["Don't eat plants or mushrooms you can't identify with certainty."]),
  ];
  const catOf = (id) => CATS.find((c) => c.id === id);

  function guideView() {
    const q = S.gq.trim().toLowerCase();
    const saved = S.guideCat === "saved";
    const list = ARTICLES.filter((a) => (saved ? S.bookmarks.includes(a.id) : !S.guideCat || a.cat === S.guideCat) &&
      (!q || (a.title + " " + a.intro + " " + a.steps.join(" ")).toLowerCase().includes(q)));
    return `
      <div class="large-title"><div class="t-sub">${ARTICLES.length} guides · stored on your phone</div><h1 class="t-hero">Survival guide</h1></div>
      <div class="sos-strip">
        <button class="sos-btn press" style="background:var(--rose);color:#fff" data-act="sos">${I.alert}<span><b style="display:block">SOS</b><span style="font-size:12px;opacity:.9">Nearby now · family by bridge</span></span></button>
        <button class="sos-btn press" style="background:var(--ember-tint);color:var(--ink)" data-act="open-article" data-v="lost">${I.compass}<span><b style="display:block">I'm lost</b><span class="t-cap">Stop, think, plan</span></span></button>
      </div>
      <label class="search" style="margin-top:14px">${I.search}<input id="gq" type="search" placeholder="Search bleeding, water, fire…" value="${esc(S.gq)}" aria-label="Search the survival guide"></label>
      <div class="filters" role="group" aria-label="Topics">
        <button class="chip" data-act="guide-cat" data-v="" aria-pressed="${!S.guideCat}">All</button>
        <button class="chip" data-act="guide-cat" data-v="saved" aria-pressed="${saved}">★ Saved ${S.bookmarks.length ? S.bookmarks.length : ""}</button>
      </div>
      ${q || saved ? "" : `<div class="cats">${CATS.map((c) => `<button class="cat press" data-act="guide-cat" data-v="${c.id}" aria-pressed="${S.guideCat === c.id}">
        <span class="tile" style="background:${c.color}">${c.icon}</span><span class="t-strong ellipsis" style="max-width:100%;font-size:14px">${c.name}</span></button>`).join("")}</div>`}
      <div class="section-h"><h2 class="t-head">${saved ? "Saved" : S.guideCat ? catOf(S.guideCat).name : q ? "Results" : "All guides"}</h2><span class="t-cap">${list.length}</span></div>
      <div class="rows">${list.map((a) => `<button class="row press" data-act="open-article" data-v="${a.id}">
          <span class="tile" style="background:${catOf(a.cat).color};width:44px;height:44px;border-radius:13px">${catOf(a.cat).icon}</span>
          <span class="main"><span class="name ellipsis">${a.title}</span><span class="preview ellipsis">${a.intro}</span></span>
          <span class="side"><span class="time">${a.mins} min</span>${S.bookmarks.includes(a.id) ? '<span style="color:var(--ember)">★</span>' : ""}</span></button>`).join("")
        || '<p class="t-sub" style="padding:24px 16px;text-align:center">Nothing found. Try a simpler word.</p>'}</div>
      <p class="t-cap" style="text-align:center;margin-top:20px">General guidance, not a substitute for trained medical help. Reach emergency services whenever you can.</p>`;
  }
  function articleView() {
    const a = ARTICLES.find((x) => x.id === S.article);
    const c = catOf(a.cat), saved = S.bookmarks.includes(a.id);
    return subScreen(a.title, `
      <article class="article">
        <span class="tag" style="background:${c.color};color:#fff">${c.name.toUpperCase()}</span>
        <h1 class="t-title">${a.title}</h1>
        <p class="t-sub" style="margin-top:8px">${a.intro}</p>
        <div class="group spec" style="margin-top:16px"><ol class="steps">${a.steps.map((s) => `<li><span>${s}</span></li>`).join("")}</ol></div>
        ${a.donts.length ? `<div class="group-label t-over">Avoid</div><div class="group spec"><ul class="donts">${a.donts.map((d) => `<li><span>${d}</span></li>`).join("")}</ul></div>` : ""}
        <div class="callout" style="margin-top:16px">General guidance, not medical advice. Reach emergency services when you can. BlueMob can carry an SOS message to anyone nearby.</div>
      </article>`, `<button class="icon-btn" data-act="bookmark" data-v="${a.id}" aria-label="${saved ? "Remove from saved" : "Save"}" aria-pressed="${saved}" style="color:${saved ? "var(--ember)" : "inherit"};font-size:22px">${saved ? "★" : "☆"}</button>`);
  }

  // ---------- SOS signal: screen, flashlight, sound, or all, in the ··· ––– ··· rhythm ----------
  const SIGNAL_MODES = [
    { id: "screen", label: "Screen", em: "📱", tip: "Flashes the whole screen white" },
    { id: "torch", label: "Flashlight", em: "🔦", tip: "Blinks the camera flash. Brightest, best at night" },
    { id: "sound", label: "Sound", em: "📢", tip: "Loud whistle-pitch beeps. Carries in fog and forest" },
    { id: "all", label: "All", em: "🚨", tip: "Screen, flashlight and sound together" },
  ];
  S.signalDefault = store.get("signalDefault", "screen");
  S.signalMode = S.signalDefault;
  let sosTimer = null, audioCtx = null;
  function beep(ms) {
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      const o = audioCtx.createOscillator(), g = audioCtx.createGain();
      o.type = "square"; o.frequency.value = 2800; // close to a rescue whistle
      g.gain.setValueAtTime(0.0001, audioCtx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.25, audioCtx.currentTime + 0.01);
      g.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + ms / 1000);
      o.connect(g).connect(audioCtx.destination); o.start(); o.stop(audioCtx.currentTime + ms / 1000 + 0.02);
    } catch { /* sound not available */ }
  }
  function sosPattern() {
    const dot = 300, pattern = [];
    const sym = (on) => { pattern.push([true, on]); pattern.push([false, dot]); };
    [1, 1, 1].forEach(() => sym(dot)); pattern[pattern.length - 1][1] = dot * 3;
    [1, 1, 1].forEach(() => sym(dot * 3)); pattern[pattern.length - 1][1] = dot * 3;
    [1, 1, 1].forEach(() => sym(dot)); pattern[pattern.length - 1][1] = dot * 7;
    return pattern;
  }
  function startSos() {
    const pattern = sosPattern();
    const mode = S.signalMode;
    const screen = mode === "screen" || mode === "all", torch = mode === "torch" || mode === "all", sound = mode === "sound" || mode === "all";
    let i = 0;
    const step = () => {
      const el = app.querySelector(".sos-screen");
      if (!el) return;
      const [on, ms] = pattern[i % pattern.length];
      el.classList.toggle("flash", on && screen);
      el.querySelector(".torch")?.classList.toggle("on", on && torch);
      if (on && sound) beep(ms);
      i++;
      sosTimer = setTimeout(step, ms);
    };
    step();
  }
  function sosView() {
    const m = SIGNAL_MODES.find((x) => x.id === S.signalMode);
    return `<div class="sos-screen" role="dialog" aria-label="SOS signal">
      <div style="width:100%"><div class="t-over" style="color:inherit;opacity:.7">SOS SIGNAL · ${m.label.toUpperCase()}</div>
        <div class="morse" style="margin-top:14px">··· ––– ···</div>
        <div class="sig-modes" role="group" aria-label="Signal type">${SIGNAL_MODES.map((x) =>
          `<button class="sig-mode" data-act="signal-mode" data-v="${x.id}" aria-pressed="${x.id === S.signalMode}">${x.em}<span>${x.label}</span></button>`).join("")}</div>
      </div>
      ${S.signalMode === "torch" || S.signalMode === "all" ? `<div class="torch" aria-hidden="true">🔦<span>Flashlight</span></div>` : ""}
      <div>
        <p style="max-width:30ch;opacity:.85;margin:0 auto">${m.tip}. ${S.signalMode === "screen" ? "Turn brightness up and point the screen at rescuers." : "Repeats until you stop it."}</p>
        ${S.signalMode !== S.signalDefault ? `<button class="btn text" style="color:inherit;margin-top:8px" data-act="signal-default" data-v="${S.signalMode}">Make ${m.label} my default</button>`
          : '<p class="t-cap" style="color:inherit;opacity:.6;margin-top:8px">This is your default</p>'}
        <p class="t-cap" style="color:inherit;opacity:.55;margin-top:6px">Web preview: the flashlight is shown on screen. On Android it blinks the real flash.</p>
      </div>
      <button class="btn" style="background:var(--rose);color:#fff;height:56px;padding:0 40px" data-act="sos-stop">Stop</button></div>`;
  }

  // ---------- games ----------
  // Tic-tac-toe with someone nearby. Each move is a tiny message over the mesh.
  const G = { board: Array(9).fill(""), turn: "x", over: null, line: null, you: 0, them: 0, opp: "asha", last: -1 };
  const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
  const winner = (b) => { for (const l of LINES) if (b[l[0]] && b[l[0]] === b[l[1]] && b[l[1]] === b[l[2]]) return { who: b[l[0]], line: l }; return b.every(Boolean) ? { who: "draw", line: [] } : null; };
  function aiMove(b) {
    const free = b.map((v, i) => (v ? null : i)).filter((i) => i !== null);
    const tryWin = (mark) => free.find((i) => { const c = b.slice(); c[i] = mark; return winner(c)?.who === mark; });
    let m = tryWin("o"); if (m !== undefined) return m;
    m = tryWin("x"); if (m !== undefined && Math.random() < .85) return m;
    if (!b[4]) return 4;
    const corners = [0, 2, 6, 8].filter((i) => !b[i]);
    return corners.length && Math.random() < .7 ? corners[Math.floor(Math.random() * corners.length)] : free[Math.floor(Math.random() * free.length)];
  }
  function finishCheck() {
    const w = winner(G.board);
    if (!w) return false;
    G.over = w.who; G.line = w.line;
    if (w.who === "x") G.you++; else if (w.who === "o") G.them++;
    return true;
  }
  function tttView() {
    const opp = P[G.opp];
    const status = G.over ? (G.over === "x" ? "You win! 🎉" : G.over === "o" ? opp.name + " wins" : "It's a draw")
      : G.turn === "x" ? "Your move" : opp.name + " is thinking…";
    return subScreen("Tic-tac-toe", `
      <div class="vs" style="margin:16px 0 8px">
        <div style="text-align:center">${avatar(S.avatar, "me", 52)}<div class="t-cap" style="margin-top:4px">You · X</div></div>
        <div class="score num">${G.you} – ${G.them}</div>
        <div style="text-align:center">${avatar(opp.avatar, opp.id, 52, opp.presence)}<div class="t-cap" style="margin-top:4px">${esc(opp.name)} · O</div></div>
      </div>
      <p class="t-head" style="text-align:center;margin:10px 0 16px" aria-live="polite">${status}</p>
      <div class="board" role="grid" aria-label="Board">${G.board.map((v, i) => `<button class="cell ${v} ${G.line && G.line.includes(i) ? "win" : ""} ${i === G.last ? "pop" : ""}"
        data-act="ttt" data-v="${i}" ${v || G.over || G.turn !== "x" ? "disabled" : ""} aria-label="Square ${i + 1}${v ? ", " + v.toUpperCase() : ""}">${v ? v.toUpperCase() : ""}</button>`).join("")}</div>
      <div style="display:flex;justify-content:center;margin-top:20px">${G.over ? '<button class="btn" data-act="ttt-new">Play again</button>' : ""}</div>
      <p class="t-cap" style="text-align:center;margin-top:16px">Each move is a 40-byte message, light enough for a Bluetooth link.<br>Web preview: ${esc(opp.name)} is simulated.</p>`);
  }
  function tttPlay(i) {
    if (G.board[i] || G.over || G.turn !== "x") return;
    G.board[i] = "x"; G.last = i; STATS.sent++; STATS.hourNow++;
    if (finishCheck()) { render(); return; }
    G.turn = "o"; render();
    later(700 + Math.random() * 600, () => {
      if (S.screen !== "ttt") return;
      const m = aiMove(G.board);
      G.board[m] = "o"; G.last = m; G.turn = "x";
      finishCheck(); render();
    });
  }

  // Survival quiz, played against someone nearby. Questions come from the guide.
  const QUIZ = [
    ["How long should you cool a burn under running water?", ["2 minutes", "5 minutes", "20 minutes", "1 hour"], 2, "burns"],
    ["Three of anything (fires, whistles, flashes) means…", ["All clear", "Help / distress", "Come here", "Danger, stay away"], 1, "signals"],
    ["Boil water for at least how long at low altitude?", ["10 seconds", "1 minute", "10 minutes", "Until it smells clean"], 1, "purify"],
    ["Chest compressions in adult CPR should be about…", ["1 to 2 cm deep", "5 to 6 cm deep", "10 cm deep", "As soft as possible"], 1, "cpr"],
    ["The 30-30 rule is about…", ["Water", "Lightning", "Fire", "Food"], 1, "lightning"],
    ["What do you do first in an earthquake?", ["Run outside", "Stand in a lift", "Drop, cover, hold on", "Open the windows"], 2, "quake"],
    ["What does 'S' in STOP stand for when you're lost?", ["Search", "Sprint", "Stop", "Shout"], 2, "lost"],
    ["Most body heat in a shelter is lost to…", ["The sky", "The ground", "The wind only", "Your clothes"], 1, "shelter"],
  ];
  const Q = { i: 0, you: 0, them: 0, picked: null, theirPick: null, opp: "ravi", done: false };
  function quizView() {
    const opp = P[Q.opp];
    if (Q.done) return subScreen("Survival quiz", `
      <div style="text-align:center;margin-top:30px"><div style="font-size:54px">${Q.you > Q.them ? "🏆" : Q.you === Q.them ? "🤝" : "🌱"}</div>
      <h1 class="t-title" style="margin-top:10px">${Q.you > Q.them ? "You won!" : Q.you === Q.them ? "A draw" : esc(opp.name) + " won this time"}</h1>
      <p class="t-sub" style="margin-top:6px">You ${Q.you} · ${esc(opp.name)} ${Q.them} · out of ${QUIZ.length}</p>
      <div style="display:flex;gap:10px;justify-content:center;margin-top:20px"><button class="btn" data-act="quiz-new">Play again</button><button class="btn secondary" data-act="tab-guide">Read the guide</button></div></div>`);
    const [q, opts, right, art] = QUIZ[Q.i];
    const answered = Q.picked !== null;
    return subScreen("Survival quiz", `
      <div class="vs" style="margin:12px 0">
        <div style="text-align:center">${avatar(S.avatar, "me", 44)}<div class="t-cap">You</div></div>
        <div class="score num">${Q.you} – ${Q.them}</div>
        <div style="text-align:center">${avatar(opp.avatar, opp.id, 44, opp.presence)}<div class="t-cap">${esc(opp.name)}</div></div>
      </div>
      <div class="t-over" style="text-align:center">Question ${Q.i + 1} of ${QUIZ.length}</div>
      <h2 class="t-head" style="text-align:center;margin:10px 0 18px">${q}</h2>
      <div class="answers">${opts.map((o, i) => `<button class="answer ${answered && i === right ? "right" : answered && i === Q.picked ? "wrong" : ""}" data-act="quiz" data-v="${i}" ${answered ? "disabled" : ""}>
        ${o}${answered && i === Q.theirPick ? ` <span class="t-cap">· ${esc(opp.name)}</span>` : ""}</button>`).join("")}</div>
      ${answered ? `<div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px">
        <button class="btn text" data-act="open-article" data-v="${art}">Why? Read the guide</button>
        <button class="btn" data-act="quiz-next">${Q.i + 1 < QUIZ.length ? "Next" : "See result"}</button></div>` : ""}`);
  }
  function quizAnswer(i) {
    if (Q.picked !== null) return;
    const right = QUIZ[Q.i][2];
    Q.picked = i;
    Q.theirPick = Math.random() < .7 ? right : (right + 1 + Math.floor(Math.random() * 3)) % 4;
    if (i === right) Q.you++;
    if (Q.theirPick === right) Q.them++;
    render();
  }

  function gamesIdeasView() {
    const idea = (em, t, b, tag) => `<div class="set idea"><span class="em">${em}</span><span class="main"><span class="t-strong">${t}</span> ${tag}<span class="t-cap" style="display:block">${b}</span></span></div>`;
    const good = '<span class="tag pine">BLUETOOTH OK</span>', wifi = '<span class="tag sky">NEEDS WI-FI LINK</span>', gps = '<span class="tag ember">USES GPS</span>';
    return subScreen("Games offline", `
      <div class="large-title" style="margin-top:8px"><h1 class="t-hero">Which games work offline?</h1>
        <p class="t-sub" style="margin-top:6px">Turn-based games send tiny messages, so they work even over slow Bluetooth and through several hops. Fast action games need a direct Wi-Fi link.</p></div>
      <div class="group-label t-over">Great over the mesh</div>
      <div class="group">
        ${idea("⭕", "Tic-tac-toe, Connect 4", "Two players, a few bytes per move.", good)}
        ${idea("♟️", "Chess, checkers, Ludo", "Turn-based board games, even across hops.", good)}
        ${idea("🧠", "Survival quiz", "Learn the guide by playing. Works for a whole group.", good)}
        ${idea("🐺", "Werewolf / Mafia", "Group storytelling game, roles sent secretly to each phone.", good)}
        ${idea("🔤", "Word chain, 20 questions", "Pass-and-reply word games.", good)}
      </div>
      <div class="group-label t-over">Outdoor, using location</div>
      <div class="group">
        ${idea("🗺️", "Treasure hunt", "Hide a waypoint; others follow the compass to find it.", gps)}
        ${idea("🏳️", "Capture the flag", "Teams see each other on the radar, not the other team.", gps)}
      </div>
      <div class="group-label t-over">Needs a close Wi-Fi link</div>
      <div class="group">
        ${idea("🎨", "Draw and guess", "Live drawing streams more data.", wifi)}
        ${idea("🏎️", "Real-time racing or shooters", "Need low delay, so only phones right next to each other.", wifi)}
      </div>`);
  }

  // ---------- person: who someone is, by their unique ID ----------
  function personView() {
    const p = P[S.person];
    const code = safetyCode(MY_ID, p.uid);
    return subScreen(p.name, `
      <div class="intro" style="padding-top:8px">${avatar(p.avatar, p.id, 96, p.presence)}
        <h1 class="t-title" style="margin-top:10px">${esc(p.name)}</h1><span class="t-sub">${statusLine(p)}</span></div>
      <div class="group-label t-over">BlueMob ID</div>
      <div class="group" style="padding:14px"><div class="id-card"><span class="t-cap">Given automatically to their phone. No two phones share one.</span>
        <span class="big">BM · ${fmtId(p.uid)}</span></div>
        ${dupName(p) ? `<p class="t-cap" style="margin-top:10px">Two people nearby are called ${esc(p.name)}. Their IDs tell them apart: this is ${shortId(p.uid)}.</p>` : ""}</div>
      <div class="group-label t-over">Verify in person</div>
      <div class="group spec" style="text-align:center"><div class="safety" aria-label="Safety symbols">${code.join("")}</div>
        <p class="t-cap">If ${esc(p.name)}'s screen shows the same four symbols for you, you're really talking to each other.</p></div>
      <div class="group-label t-over">Stay in touch</div>
      <div class="group spec"><p class="t-sub">${p.presence === "online" ? "You're connected directly right now." : `You met ${esc(p.name)} on this trip${p.home ? ", and they're now in " + esc(p.home) : ""}.`}
        You can keep talking anywhere, by BlueMob ID. No phone numbers are shared. When either of you has no signal, messages wait for a bridge.</p></div>
      <div style="display:flex;gap:10px;justify-content:center;margin-top:18px">
        <button class="btn" data-act="open-chat" data-id="${p.id}">${I.chat} Message</button>
        ${p.presence === "online" ? `<button class="btn secondary" data-act="nav-to" data-v="${p.id}">${I.compass} Walk to</button>` : ""}
      </div>`);
  }

  // ---------- shared sub-screen frame (pushed screens with a back button) ----------
  function subScreen(title, body, actions = "") {
    return `<div class="screen grouped ${S._anim === "push" ? "push-in" : ""}">
      <header class="bar solid"><button class="icon-btn" data-act="back" aria-label="Back">${I.back}</button>
        <div class="bar-title" style="opacity:1;transform:none">${esc(title)}</div>${actions}</header>
      <div class="scroll" id="scroll">${body}</div></div>`;
  }
