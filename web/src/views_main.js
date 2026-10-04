
  // ---------- extra UI state ----------
  Object.assign(S, { screen: "main", query: "", filter: "all", prevTab: S.tab, seen: new Set(), stack: [] });
  if (!["radar", "chats", "compass", "guide", "you"].includes(S.tab)) S.tab = "radar";

  // ---------- formatting ----------
  function fmtDist(m) {
    if (m < 10) return "a few m";
    if (m < 1000) return Math.round(m / 5) * 5 + " m";
    if (m < 10000) return (m / 1000).toFixed(1) + " km";
    return Math.round(m / 1000).toLocaleString() + " km";
  }
  function ago(t) {
    const s = Math.max(0, (now() - t) / 1000);
    if (s < 60) return "just now";
    if (s < 3600) return Math.floor(s / 60) + " min ago";
    if (s < 86400) return Math.floor(s / 3600) + " h ago";
    return Math.floor(s / 86400) + " d ago";
  }
  const clock = (t) => new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const stamp = (t) => (now() - t < 60e3 ? "now" : clock(t));
  const PCOLOR = { online: "var(--online)", range: "var(--away)", offline: "var(--offline)", remote: "var(--sky)" };
  const ORDER = { online: 0, range: 1, offline: 2, remote: 3 };

  function statusLine(p) {
    if (p.sms) return esc(p.phone) + " · text message " + (bridgeOnline() ? "via Meera's internet" : "when a bridge appears");
    if (p.id === "experts") return "Volunteer medics and rangers · " + (bridgeOnline() ? "via Meera's internet" : "when a bridge appears");
    if (p.remote) return bridgeOnline() ? "2,000 km away · via Meera's internet" : "2,000 km away · waiting for a bridge";
    if (p.presence === "online") return ["Online", S.shareLoc ? fmtDist(p.dist) + " away" : null, p.link].filter(Boolean).join(" · ");
    if (p.presence === "range") return "In range · connecting…";
    return "Seen " + ago(p.lastSeen) + (p.home ? " · now in " + p.home.split(",")[0] : S.shareLoc ? " · was " + fmtDist(p.dist) + " away" : "") + " · reach by ID";
  }

  // Avatar: emoji on a soft tint. Online people get a ring, others a small status badge.
  function avatar(emoji, seed, size, presence) {
    const ring = presence === "online";
    const badge = presence && !ring ? `<span class="badge" style="background:${PCOLOR[presence]}"></span>` : "";
    return `<span class="avatar${ring ? " ring" : ""}" style="width:${size}px;height:${size}px">
      <span class="face" style="background:${tint(seed)};font-size:${Math.round(size * (ring ? .44 : .5))}px">${esc(emoji)}</span>${badge}</span>`;
  }

  // ---------- rendering core ----------
  const app = document.getElementById("app");
  let toastTimer;
  function toast(text) {
    let el = app.querySelector(".toast");
    if (!el) { el = document.createElement("div"); el.className = "toast"; el.setAttribute("role", "status"); app.appendChild(el); }
    el.textContent = text;
    el.classList.toggle("high", S.screen === "chat");
    requestAnimationFrame(() => el.classList.add("show"));
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2400);
  }

  function refresh() {
    if (!S.onboarded) return;
    if (S.screen === "chat") { renderChatParts(); return; }
    if (["main", "insights", "person", "soshub", "info", "lobby", "money"].includes(S.screen)) render();
  }

  // Screen stack: open() pushes, back() pops.
  function open(screen, params = {}) {
    S.stack.push({ screen: S.screen, chat: S.chat, article: S.article, person: S.person });
    Object.assign(S, { screen }, params);
    render("push");
  }
  function back() {
    clearTimeout(sosTimer);
    const prev = S.stack.pop() || { screen: "main" };
    Object.assign(S, prev);
    render("fade");
  }

  /** anim: "fade" when switching tabs, "push" when opening a screen, none for live updates. */
  function render(anim) {
    const active = document.activeElement;
    const focusId = active && active.id, caret = active && active.selectionStart;
    const keepScroll = !anim ? app.querySelector("#scroll")?.scrollTop ?? 0 : 0;
    const toastEl = app.querySelector(".toast");

    if (!S.onboarded) { app.innerHTML = onboardingView(); afterOnboardingRender(); return; }
    S._anim = anim;
    const SCREENS = { design: designView, power: powerView, insights: insightsView, article: articleView, ttt: tttView,
      quiz: quizView, games: gamesIdeasView, person: personView, soshub: sosHubView, contacts: contactsView, info: messageInfoView,
      money: moneyView, play: gamesHubView, lobby: lobbyView, c4: c4View, words: wordsView, hunt: huntView };
    if (S.screen === "chat") { app.innerHTML = chatView(anim); renderChatParts(true); }
    else if (S.screen === "sos") { app.innerHTML = sosView(); clearTimeout(sosTimer); startSos(); }
    else if (SCREENS[S.screen]) app.innerHTML = SCREENS[S.screen](anim);
    else app.innerHTML = mainView(anim);

    if (S.alertOpen) app.insertAdjacentHTML("beforeend", sosAlertView());
    if (toastEl) app.appendChild(toastEl);
    const sc = app.querySelector("#scroll");
    if (sc) { sc.scrollTop = keepScroll; syncBar(sc); }
    if (S.screen === "main" && S.tab === "radar") placeBlips();
    if (focusId) {
      const el = document.getElementById(focusId);
      if (el) { el.focus(); if (caret != null && el.setSelectionRange) el.setSelectionRange(caret, caret); }
    }
    if (S.screen === "main") requestAnimationFrame(() => {
      const ind = app.querySelector(".tab-ind");
      if (ind) ind.style.transform = `translateX(${TABS.findIndex((t) => t.id === S.tab) * 100}%)`;
    });
  }
  function syncBar(sc) {
    const bar = app.querySelector(".bar");
    if (bar && !bar.classList.contains("solid")) bar.classList.toggle("scrolled", sc.scrollTop > 36);
  }
  app.addEventListener("scroll", (e) => { if (e.target.id === "scroll") syncBar(e.target); }, true);

  // ---------- onboarding ----------
  const SLIDES = [
    { art: "offgrid", title: "Talk freely, anywhere", body: "No towers. No internet. BlueMob links phones directly over Bluetooth and Wi-Fi: in the mountains, at sea, or when the network goes down." },
    { art: "hops", title: "Every phone is a path", body: "Messages hop from friend to friend until they reach the right person. The more people around, the further your voice travels." },
    { art: "bridge", title: "One signal frees everyone", body: "If just one person nearby has internet, the whole group can reach the world through them." },
    { art: "radar", title: "See who's around", body: "A live radar shows who's online, how far away they are, and when you last saw them." },
    { art: "buddy", title: "Never alone", body: "Chat with Sky, your built-in buddy, to feel what talking off-grid is like before friends join." },
  ];
  const node = (emoji, x, y, size, seed) =>
    `<span class="node" style="left:${x}%;top:${y}%;width:${size}px;height:${size}px;margin:${-size / 2}px;font-size:${size / 2}px;background:${tint(seed)}">${emoji}</span>`;
  function heroArt(kind) {
    if (kind === "offgrid") return `<div class="hero"><span class="ripple"></span><span class="ripple"></span><span class="ripple"></span>
      ${node("📱", 50, 50, 92, "me")}<span class="hero-badge">${I.signalOff}</span></div>`;
    if (kind === "hops") return `<div class="hero"><svg viewBox="0 0 300 300" width="100%" height="100%" aria-hidden="true">
      <path id="hopPath" d="M45 210 L150 90 L255 210" fill="none" stroke="var(--line)" stroke-width="4" stroke-dasharray="2 10" stroke-linecap="round"/>
      <circle r="16" fill="var(--ember)" opacity=".22"><animateMotion dur="2.8s" repeatCount="indefinite"><mpath href="#hopPath"/></animateMotion></circle>
      <circle r="7" fill="var(--ember)"><animateMotion dur="2.8s" repeatCount="indefinite"><mpath href="#hopPath"/></animateMotion></circle></svg>
      ${node("🦅", 15, 70, 64, "a")}${node("🌊", 50, 30, 64, "b")}${node("⛰️", 85, 70, 64, "c")}</div>`;
    if (kind === "bridge") return `<div class="hero"><svg viewBox="0 0 300 300" width="100%" height="100%" aria-hidden="true">
      <path d="M54 234 L135 258 L234 222" fill="none" stroke="var(--line)" stroke-width="4" stroke-linecap="round"/>
      <line x1="234" y1="222" x2="150" y2="60" stroke="var(--pine)" stroke-width="4" stroke-dasharray="2 10" stroke-linecap="round">
        <animate attributeName="stroke-dashoffset" from="48" to="0" dur="1.2s" repeatCount="indefinite"/></line></svg>
      ${node("🌻", 18, 78, 56, "f0")}${node("🍀", 45, 86, 56, "f1")}${node("📶", 78, 74, 56, "f2")}${node("🌍", 50, 20, 88, "earth")}</div>`;
    if (kind === "radar") return `<div class="hero"><div class="night" style="width:100%;height:100%;border-radius:50%"><div class="radar-wrap" data-demo="1"><canvas></canvas></div></div></div>`;
    return `<div class="hero"><div class="bubble-demo"><div class="b me">Hi Sky! 👋</div><div class="b them">Hey! 🌤️ Ready to explore?</div><div class="b me">Let's go! 🚀</div></div></div>`;
  }
  function onboardingView() {
    const last = S.slide === SLIDES.length;
    const s = SLIDES[S.slide];
    const content = last ? `
      <div class="slide">
        <span class="me-badge" id="ob-avatar">${esc(S.avatar)}</span>
        <h1 class="t-title" style="margin-top:8px">Who are you out there?</h1>
        <p>Pick a name and a spirit. People nearby will see these, next to your unique ID.</p>
        <span class="idline" style="background:var(--sand)">BM · ${fmtId(MY_ID)}</span>
        <div class="field" style="width:100%;margin-top:6px"><label class="t-over" for="ob-name">Your name</label>
          <input id="ob-name" maxlength="24" autocomplete="nickname" placeholder="e.g. Arjun" value="${esc(S.name)}"></div>
        <div class="picker" role="group" aria-label="Choose an avatar">${AVATARS.map((a) =>
          `<button data-act="pick-avatar" data-v="${a}" aria-pressed="${a === S.avatar}">${a}</button>`).join("")}</div>
      </div>` : `
      <div class="slide">
        ${heroArt(s.art)}
        ${s.art === "hops" || s.art === "bridge" ? '<span class="tag ember">COMING TO ANDROID · TRY IT IN THIS PREVIEW</span>' : ""}
        <h1 class="t-hero" style="margin-top:6px">${s.title}</h1>
        <p>${s.body}</p>
      </div>`;
    return `<div class="onboard">
      <div class="onboard-top"><span class="wordmark"><i></i>BlueMob</span>
        ${last ? "" : '<button class="btn text" data-act="skip">Skip</button>'}</div>
      ${content}
      <div class="onboard-bottom">
        <div class="dots" aria-hidden="true">${Array.from({ length: SLIDES.length + 1 }, (_, i) => `<span class="${i === S.slide ? "on" : ""}"></span>`).join("")}</div>
        <button class="btn" data-act="${last ? "finish" : "next"}" id="ob-next" ${last && !S.name.trim() ? "disabled" : ""}>
          ${last ? "Start exploring" : "Continue"} ${I.send}</button>
      </div></div>`;
  }
  function afterOnboardingRender() {
    const input = document.getElementById("ob-name");
    if (!input) return;
    input.addEventListener("input", () => {
      S.name = input.value.replace(/\|/g, " ").slice(0, 24);
      document.getElementById("ob-next").disabled = !S.name.trim();
    });
    input.addEventListener("keydown", (e) => { if (e.key === "Enter" && S.name.trim()) finishOnboarding(); });
  }
  function finishOnboarding() {
    S.name = S.name.trim() || "Explorer";
    S.onboarded = true;
    store.set("name", S.name); store.set("avatar", S.avatar); store.set("onboarded", true);
    S.tab = "radar"; S.screen = "main";
    render("fade");
    later(500, startMesh);
  }

  // ---------- main shell ----------
  const TABS = [
    { id: "radar", label: "Nearby", icon: I.radar },
    { id: "chats", label: "Chats", icon: I.chat },
    { id: "compass", label: "Compass", icon: I.compass },
    { id: "guide", label: "Guide", icon: I.book },
    { id: "you", label: "You", icon: I.person },
  ];
  function mainView(anim) {
    const title = { radar: "Nearby", chats: "Chats", compass: "Compass", guide: "Survival guide", you: "You" }[S.tab];
    const body = { radar: radarView, chats: chatsView, compass: compassView, guide: guideView, you: profileView }[S.tab]();
    const sosPill = `<button class="sos-pill" data-act="sos" aria-label="SOS: ask for help">SOS</button>`;
    const actions = S.tab !== "radar" ? `<span class="spacer"></span>${sosPill}` : `<span class="spacer"></span>${sosPill}<button class="battery-chip ${S.survival ? "on" : ""}" data-act="power" aria-label="Power settings, battery ${S.batteryPct}%">${I.battery}<span class="num">${S.batteryPct}%</span>${S.survival ? " · Survival" : ""}</button>
         <button class="icon-btn" data-act="tab" data-v="you" aria-label="Your profile">${avatar(S.avatar, "me", 32)}</button>`;
    return `<div class="screen ${S.tab === "you" ? "grouped" : ""} ${anim === "fade" ? "fade-in" : anim === "push" ? "push-in" : ""}">
      <header class="bar"><div class="bar-title">${title}</div>${actions}</header>
      <div class="scroll" id="scroll">${body}</div>
      ${tabbar()}${inviteBanner()}</div>`;
  }
  function tabbar() {
    const unread = Object.values(S.convos).reduce((n, c) => n + c.unread, 0);
    const from = Math.max(0, TABS.findIndex((t) => t.id === S.prevTab));
    return `<nav class="tabbar" role="tablist"><span class="tab-ind" style="transform:translateX(${from * 100}%)"></span>${TABS.map((t) =>
      `<button class="tab" role="tab" aria-selected="${S.tab === t.id}" data-act="tab" data-v="${t.id}">${t.icon}${t.label}${
        t.id === "chats" && unread ? `<span class="count">${unread}</span>` : ""}</button>`).join("")}</nav>`;
  }

  // ---------- Nearby (radar) ----------
  function greeting() {
    const h = new Date().getHours();
    return h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 17 ? "Good afternoon" : h >= 17 && h < 22 ? "Good evening" : "Hello, night owl";
  }
  const nearby = () => Object.values(P).filter((p) => !p.remote && p.met);
  function radarView() {
    const list = nearby().sort((a, b) => ORDER[a.presence] - ORDER[b.presence] || b.lastSeen - a.lastSeen);
    const PF = { all: () => true, online: (p) => p.presence === "online", internet: (p) => p.internet && p.presence === "online",
      close: (p) => p.presence === "online" && p.dist < 500, wifi: (p) => p.presence === "online" && p.link !== "Bluetooth link" };
    const SORTS = { near: ["Nearest", (a, b) => ORDER[a.presence] - ORDER[b.presence] || a.dist - b.dist],
      recent: ["Last seen", (a, b) => b.lastSeen - a.lastSeen], name: ["Name", (a, b) => a.name.localeCompare(b.name)] };
    const shown = list.filter(PF[S.peopleFilter]).sort(SORTS[S.sort][1]);
    const online = list.filter((p) => p.presence === "online").length;
    const range = list.filter((p) => p.presence === "range").length;
    const bridge = bridgeOnline();
    return `
      <div class="large-title"><div class="t-sub">${greeting()}, ${esc(S.name)}</div><h1 class="t-hero">Nearby</h1></div>

      <section class="night">
        <div class="night-top">
          <span class="live ${S.mesh ? "" : "off"}"><span class="pulse"></span>${S.mesh ? "MESH ON" : "MESH OFF"}</span>
          <button class="switch" role="switch" aria-checked="${S.mesh}" aria-label="Mesh" data-act="toggle-mesh"></button>
        </div>
        <div class="radar-wrap" id="radar"><canvas></canvas>${list.map((p) => {
          const { x, y } = blipPos(p);
          return `<button class="blip" data-act="open-chat" data-id="${p.id}" data-blip="${p.id}" aria-label="${esc(p.name)}, ${statusLine(p)}"
            style="left:${x}%;top:${y}%;opacity:${p.presence === "offline" ? .45 : 1};--glow:${p.presence === "online" ? "rgba(95,211,163,.55)" : "transparent"}">
            <span class="face" style="background:${tint(p.id)}">${p.avatar}</span><span class="badge" style="background:${p.sos ? "var(--rose)" : PCOLOR[p.presence]}"></span>${p.sos ? '<span class="sos-ring"></span>' : ""}</button>`;
        }).join("")}</div>
        <div class="night-foot">
          <div class="stat-line num"><div><b>${online}</b><span>online</span></div><div><b>${range}</b><span>in range</span></div><div><b>${list.length}</b><span>met</span></div></div>
          <div class="scale">${S.shareLoc ? "10 m · 300 m · 10 km" : "inner ring = online"}</div>
        </div>
      </section>

      <div class="seg ${S.shareLoc ? "right" : ""}" role="group" aria-label="Radar view" style="margin-top:14px">
        <span class="knob"></span>
        <button data-act="seg" data-v="status" aria-pressed="${!S.shareLoc}">By status</button>
        <button data-act="seg" data-v="distance" aria-pressed="${S.shareLoc}">By distance</button>
      </div>

      <div class="section-h"><h2 class="t-head">Around you</h2><button class="btn text" data-act="sort" aria-label="Change sort order">Sort: ${SORTS[S.sort][0]} ⇅</button></div>
      <div class="filters" role="group" aria-label="Filter people" style="margin-top:0">${[["all", "All " + list.length], ["online", "Online"], ["internet", "Has internet"], ["close", "Under 500 m"], ["wifi", "Wi-Fi link"]].map(([v, l]) =>
        `<button class="chip" data-act="people-filter" data-v="${v}" aria-pressed="${S.peopleFilter === v}">${l}</button>`).join("")}</div>
      ${list.length && !shown.length ? '<p class="t-sub" style="padding:12px 4px">No one matches this filter right now.</p>' : ""}
      ${shown.length ? `<div class="hscroll">${shown.map(personCard).join("")}</div>`
        : `<div class="card" style="padding:20px;text-align:center"><div style="font-size:34px">🏕️</div>
            <div class="t-strong" style="margin-top:6px">Quiet out here</div>
            <div class="t-sub">Switch the mesh on. People nearby appear as they open BlueMob.</div></div>`}

      <div class="section-h"><h2 class="t-head">Reach further</h2></div>
      <div class="stack" style="gap:10px">
        <button class="card feature press" data-act="open-chat" data-id="dee">
          <span class="tile" style="background:${bridge ? "var(--pine)" : "var(--ember)"}">${bridge ? I.globe : I.cloudOff}</span>
          <span style="flex:1;min-width:0"><span class="t-strong" style="display:block">${bridge ? "Meera is your bridge to the world" : "You're off-grid"}</span>
            <span class="t-sub">${bridge ? "She has internet. Message Dee, 2,000 km away, and watch it travel." : "No internet nearby yet. Messages to faraway friends will wait for a bridge."}</span></span>
          <span class="chev">${I.chevron}</span></button>
        <button class="card feature press" data-act="money">
          <span class="tile" style="background:#3A9A5B">${I.wallet}</span>
          <span style="flex:1;min-width:0"><span class="t-strong" style="display:block">Trip money</span>
            <span class="t-sub">Split costs offline, settle with UPI later.</span></span>
          <span class="chev">${I.chevron}</span></button>
        <button class="card feature press" data-act="insights">
          <span class="tile" style="background:var(--c-hop)">${I.chart}</span>
          <span style="flex:1;min-width:0"><span class="t-strong" style="display:block">Mesh insights</span>
            <span class="t-sub">Messages, routes, relays and link strength.</span></span>
          <span class="chev">${I.chevron}</span></button>
        <button class="card feature press" data-act="open-chat" data-id="sky">
          <span class="tile" style="background:var(--sky)">${I.bot}</span>
          <span style="flex:1;min-width:0"><span class="t-strong" style="display:block">Practise with Sky</span>
            <span class="t-sub">A friendly bot that shows how off-grid chats feel.</span></span>
          <span class="chev">${I.chevron}</span></button>
      </div>
      <div class="section-h"><h2 class="t-head">Play together</h2><button class="btn text" data-act="play">All 12 games</button></div>
      <div class="hscroll">
        <button class="gcard press" style="background:linear-gradient(150deg, var(--pine), #0B3D2E)" data-act="lobby" data-v="ttt"><span class="em">⭕</span><b>Tic-tac-toe</b><span style="font-size:13px;opacity:.85">Ask people nearby</span></button>
        <button class="gcard press" style="background:linear-gradient(150deg, #C2621A, #7A3A0E)" data-act="lobby" data-v="c4"><span class="em">🔴</span><b>Connect 4</b><span style="font-size:13px;opacity:.85">Ask people nearby</span></button>
        <button class="gcard press" style="background:linear-gradient(150deg, #2F7BC0, #173E5C)" data-act="play"><span class="em">🎲</span><b>All games</b><span style="font-size:13px;opacity:.85">Quiz, word chain, treasure hunt and more</span></button>
      </div>
      <p class="t-cap" style="text-align:center;margin-top:24px">Web preview · nearby people are simulated. The Android app finds real phones over Bluetooth and Wi-Fi.</p>`;
  }
  function personCard(p) {
    const label = { online: "ONLINE", range: "IN RANGE", offline: "AWAY" }[p.presence];
    const sub = p.presence === "online" ? (S.shareLoc ? fmtDist(p.dist) + " · " : "") + p.link
      : p.presence === "range" ? "Connecting…" : "Seen " + ago(p.lastSeen);
    return `<button class="pcard press" data-act="open-chat" data-id="${p.id}">
      <span class="art" style="background:${tint(p.id)}"><span class="tag"><span class="dot" style="background:${p.sos ? "var(--rose)" : PCOLOR[p.presence]}"></span>${p.sos ? "SOS" : label}</span>${p.avatar}</span>
      <span class="body"><span class="t-strong ellipsis">${nameWithId(p)}</span><span class="t-cap ellipsis" data-status-card="${p.id}">${sub}</span></span></button>`;
  }
  function updatePeopleText() {
    document.querySelectorAll("[data-status-card]").forEach((el) => {
      const p = P[el.dataset.statusCard];
      if (p.presence === "online") el.textContent = (S.shareLoc ? fmtDist(p.dist) + " · " : "") + p.link;
    });
  }

  // Placement: with location, real bearing on a log scale (rings at 10 m, ~300 m, 10 km);
  // without, a stable direction and a ring that reflects how reachable they are.
  function blipPos(p) {
    const r = S.shareLoc
      ? Math.min(.9, Math.max(.14, (Math.log10(Math.max(1, p.dist)) + .5) / 4.5))
      : { online: .36, range: .6, offline: .84 }[p.presence];
    const a = (p.bearing * Math.PI) / 180;
    return { x: 50 + 50 * r * Math.sin(a), y: 50 - 50 * r * Math.cos(a) };
  }
  function placeBlips() {
    document.querySelectorAll("[data-blip]").forEach((el) => {
      const p = P[el.dataset.blip];
      const { x, y } = blipPos(p);
      el.style.left = x + "%"; el.style.top = y + "%";
    });
  }

  // The radar is always drawn on a night sky, in both themes.
  const STARS = Array.from({ length: 46 }, () => [Math.random(), Math.random(), Math.random() * 1.2 + .3, Math.random()]);
  let sweep = 0;
  function drawRadars() {
    document.querySelectorAll(".radar-wrap canvas").forEach((cv) => {
      const box = cv.getBoundingClientRect();
      if (!box.width) return;
      const dpr = window.devicePixelRatio || 1;
      const W = Math.round(box.width * dpr);
      if (cv.width !== W) { cv.width = W; cv.height = W; }
      const ctx = cv.getContext("2d");
      const c = W / 2, r = c * .92;
      ctx.clearRect(0, 0, W, W);
      STARS.forEach(([x, y, s, tw]) => {
        ctx.fillStyle = `rgba(234,245,240,${.15 + .35 * Math.abs(Math.sin(sweep / 40 + tw * 6))})`;
        ctx.beginPath(); ctx.arc(x * W, y * W, s * dpr, 0, Math.PI * 2); ctx.fill();
      });
      ctx.strokeStyle = "rgba(234,245,240,.10)"; ctx.lineWidth = dpr;
      for (let i = 1; i <= 3; i++) { ctx.beginPath(); ctx.arc(c, c, (r * i) / 3, 0, Math.PI * 2); ctx.stroke(); }
      ctx.setLineDash([2 * dpr, 6 * dpr]);
      ctx.beginPath(); ctx.moveTo(c - r, c); ctx.lineTo(c + r, c); ctx.moveTo(c, c - r); ctx.lineTo(c, c + r); ctx.stroke();
      ctx.setLineDash([]);
      const demo = cv.parentElement.dataset.demo;
      if ((S.mesh || demo) && ctx.createConicGradient) {
        const a = (sweep * Math.PI) / 180;
        const g = ctx.createConicGradient(a, c, c);
        g.addColorStop(0, "rgba(95,211,163,.55)"); g.addColorStop(.18, "rgba(95,211,163,0)"); g.addColorStop(1, "rgba(95,211,163,0)");
        ctx.save(); ctx.scale(1, 1);
        ctx.fillStyle = g; ctx.beginPath(); ctx.arc(c, c, r, 0, Math.PI * 2); ctx.fill();
        ctx.strokeStyle = "rgba(95,211,163,.8)"; ctx.lineWidth = 1.5 * dpr;
        ctx.beginPath(); ctx.moveTo(c, c); ctx.lineTo(c + r * Math.cos(a), c + r * Math.sin(a)); ctx.stroke();
        ctx.restore();
      }
      if (demo) {
        [[40, .35, "#3FC897"], [150, .62, "#3FC897"], [250, .84, "#E9A23B"]].forEach(([b, rr, col]) => {
          const ang = (b * Math.PI) / 180, x = c + r * rr * Math.sin(ang), y = c - r * rr * Math.cos(ang);
          ctx.fillStyle = col; ctx.globalAlpha = .25; ctx.beginPath(); ctx.arc(x, y, 14 * dpr, 0, Math.PI * 2); ctx.fill();
          ctx.globalAlpha = 1; ctx.beginPath(); ctx.arc(x, y, 7 * dpr, 0, Math.PI * 2); ctx.fill();
        });
      }
      const pulse = .5 + .5 * Math.abs(Math.sin(sweep / 25));
      ctx.fillStyle = "rgba(125,183,234,.28)"; ctx.beginPath(); ctx.arc(c, c, (10 + 12 * pulse) * dpr, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = "#7DB7EA"; ctx.beginPath(); ctx.arc(c, c, 6.5 * dpr, 0, Math.PI * 2); ctx.fill();
      ctx.strokeStyle = "#fff"; ctx.lineWidth = 2 * dpr; ctx.stroke();
    });
    if (!reduceMotion && !S.survival) sweep = (sweep + 1.6) % 360;
    requestAnimationFrame(drawRadars);
  }

  // ---------- Chats ----------
  function chatEntries() {
    const entries = [
      { id: "sky", name: SKY.name, emoji: SKY.avatar, presence: "online", kind: "bot", status: "Your practice buddy" },
      ...["dee", "experts", ...S.contacts.map((c) => c.id)].map((id) => ({ id, name: P[id].name, emoji: P[id].avatar, presence: "remote",
        kind: P[id].sms ? "sms" : id === "experts" ? "expert" : "far", status: statusLine(P[id]) })),
      ...nearby().map((p) => ({ id: p.id, name: p.name, emoji: p.avatar, presence: p.presence, kind: p.presence === "offline" ? "far" : "near", status: statusLine(p) })),
    ];
    const lastTime = (e) => S.convos[e.id]?.messages.at(-1)?.time ?? 0;
    return entries.sort((a, b) => (a.id === "sky" ? -1 : b.id === "sky" ? 1 : 0) || lastTime(b) - lastTime(a) || ORDER[a.presence] - ORDER[b.presence]);
  }
  function chatsView() {
    const q = S.query.trim().toLowerCase();
    const all = chatEntries();
    const list = all.filter((e) => (!q || e.name.toLowerCase().includes(q)) &&
      (S.filter === "all" || (S.filter === "nearby" && e.kind === "near") || (S.filter === "far" && ["far", "sms", "expert"].includes(e.kind)) ||
       (S.filter === "unread" && S.convos[e.id]?.unread) || (S.filter === "online" && e.presence === "online")));
    const onlineNow = nearby().filter((p) => p.presence === "online");
    return `
      <h1 class="t-hero large-title">Chats</h1>
      <label class="search">${I.search}<input id="q" type="search" placeholder="Search" value="${esc(S.query)}" aria-label="Search chats"></label>
      ${onlineNow.length && !q ? `<div class="section-h" style="margin-top:20px"><span class="t-over">Online nearby</span></div>
        <div class="stories">${onlineNow.map((p) => `<button class="story press" data-act="open-chat" data-id="${p.id}">${avatar(p.avatar, p.id, 60, "online")}<span class="ellipsis" style="max-width:64px">${esc(p.name)}</span></button>`).join("")}</div>` : ""}
      <div class="filters" role="group" aria-label="Filter chats">${[["all", "All"], ["unread", "Unread"], ["online", "Online"], ["nearby", "Nearby"], ["far", "Far away"]].map(([v, l]) =>
        `<button class="chip" data-act="filter" data-v="${v}" aria-pressed="${S.filter === v}">${l}</button>`).join("")}</div>
      <div class="rows">${list.map(chatRow).join("") || '<p class="t-sub" style="padding:24px 16px;text-align:center">No chats match.</p>'}</div>`;
  }
  function chatRow(e) {
    const c = S.convos[e.id];
    const last = c?.messages.at(-1);
    const unread = c?.unread || 0;
    const tag = { bot: '<span class="tag sky">BOT</span>', far: '<span class="tag ember">VIA BRIDGE</span>', sms: '<span class="tag ember">SMS</span>',
      expert: '<span class="tag pine">EXPERTS</span>' }[e.kind] || "";
    const preview = c?.typing ? '<span class="typing-text">typing…</span>'
      : last ? (last.me ? "You: " : "") + esc(last.text.split("\n")[0]) : esc(e.status);
    return `<button class="row press ${unread ? "unread" : ""}" data-act="open-chat" data-id="${e.id}">
      ${avatar(e.emoji, e.id, 54, e.presence)}
      <span class="main"><span class="line1"><span class="name ellipsis">${P[e.id] ? nameWithId(P[e.id]) : esc(e.name)}</span>${tag}</span>
        <span class="preview ellipsis">${preview}</span></span>
      <span class="side">${last ? `<span class="time num">${stamp(last.time)}</span>` : ""}${unread ? `<span class="count">${unread}</span>` : ""}</span></button>`;
  }

  // ---------- Chat ----------
  function chatInfo(id) {
    if (id === "sky") return { name: SKY.name, emoji: SKY.avatar, presence: "online", bot: true };
    const p = P[id];
    return { name: p.name, emoji: p.avatar, presence: p.presence, remote: p.remote, far: p.remote || p.presence === "offline", sms: p.sms };
  }
  // You can always write: people out of range are reached through the internet once a bridge is nearby.
  function canSend(id) { return true; }
  function chatView(anim) {
    const id = S.chat, info = chatInfo(id);
    const live = !info.bot && !info.far;
    return `<div class="screen chat-screen ${anim === "push" ? "push-in" : ""}">
      <header class="bar solid chat-bar">
        <button class="icon-btn" data-act="back" aria-label="Back">${I.back}</button>
        <button class="who" ${info.bot ? "" : `data-act="person" data-v="${id}" aria-label="About ${esc(info.name)}"`}><span id="chat-av">${avatar(info.emoji, id, 38, info.presence)}</span><span style="min-width:0"><b class="ellipsis">${esc(info.name)}</b><span class="t-cap ellipsis" id="chat-sub" style="display:block"></span></span></button>
        ${live ? `<button class="icon-btn" data-act="ping" id="ping-btn" aria-label="Check link speed">${I.speed}</button>` : ""}
        <button class="icon-btn" data-act="soon" data-v="Voice calls" aria-label="Voice call">${I.phone}</button>
        <button class="icon-btn" data-act="soon" data-v="Video calls" aria-label="Video call">${I.video}</button>
      </header>
      ${info.bot ? "" : `<button class="route-strip" id="route-strip" data-act="route-strip"></button>`}
      <div class="messages ${info.bot ? "" : "with-strip"}" id="messages"></div>
      <div class="suggest" id="chips"></div>
      <div class="composer-wrap">
        <div class="composer-note" id="note" hidden></div>
        <form class="composer" id="composer">
          <div class="field-pill">
            <button type="button" class="icon-btn" data-act="soon" data-v="Emoji" aria-label="Emoji">${I.smile}</button>
            <input id="draft" autocomplete="off" placeholder="Message" aria-label="Message">
            <button type="button" class="icon-btn" data-act="soon" data-v="Photos and files" aria-label="Attach">${I.plus}</button>
          </div>
          <button class="send mic" type="submit" id="send-btn" aria-label="Send">${I.mic}</button>
        </form>
      </div></div>`;
  }
  function introHtml(id, info) {
    const p = P[id];
    const sub = info.bot ? "Built into BlueMob, on your phone. No internet, no server: what you ask stays here. I know the app, the survival guide, and what's happening around you."
      : id === "experts" ? "Volunteer medics, rangers and rescuers. Your questions go out as soon as someone nearby has internet, and you're notified when they reply."
      : info.sms ? `${esc(p.phone)} · They don't need BlueMob. Your message reaches them as a normal text once someone nearby has internet.`
      : info.remote ? "2,000 km away. Messages travel through a nearby friend who has internet."
      : p.presence === "offline" ? `You met on the trip. ${p.home ? "Now in " + esc(p.home) + ". " : ""}You can still talk, by BlueMob ID, with no phone numbers shared.`
      : "Met nearby over " + (p.link || "Bluetooth") + ". Messages go phone to phone.";
    const tag = info.bot ? '<span class="tag sky">ON THIS PHONE · OFFLINE</span>' : info.sms ? '<span class="tag ember">BY TEXT MESSAGE</span>'
      : info.far ? '<span class="tag ember">VIA BRIDGE</span>' : '<span class="tag pine">DIRECT</span>';
    return `<div class="intro">${avatar(info.emoji, id, 80)}<h2 class="t-title" style="margin-top:8px">${esc(info.name)}</h2>${tag}<p class="t-sub" style="margin-top:4px">${sub}</p></div>`;
  }
  function journeyHtml(m) {
    const pn = pendingNote(m);
    if (m.status === "pending" || !m.route || m.route.length < 3) return pn ? `<div class="journey-note">${pn}</div>` : "";
    const done = m.status === "delivered";
    const steps = m.route.map((h, i) => `<li class="${done || i < m.hop ? "done" : i === m.hop ? "now" : ""}"><i></i><span class="ellipsis" style="max-width:100%">${esc(h.replace(" 🌐", ""))}</span></li>`).join("");
    const note = done ? "Delivered through Meera's internet · tap for details" : m.hop === 0 ? "Leaving your phone…" : "Passing through " + m.route[m.hop].replace(" 🌐", "") + "…";
    return `<ol class="journey" aria-label="Route">${steps}</ol><div class="journey-note">${note}</div>`;
  }
  function messagesHtml(id, info, c) {
    const out = [introHtml(id, info), '<span class="tag day">TODAY</span>'];
    const ms = c.messages;
    const same = (a, b) => a && b && a.me === b.me && Math.abs(b.time - a.time) < 120e3 && !a.route && !b.route && a.status !== "queued" && b.status !== "queued";
    ms.forEach((m, i) => {
      const withPrev = same(ms[i - 1], m), withNext = same(m, ms[i + 1]);
      const pos = withPrev && withNext ? "mid" : withNext ? "first" : withPrev ? "last" : "";
      const isNew = !S.seen.has(m.id);
      S.seen.add(m.id);
      const showMeta = !withNext || m.route || m.status === "failed";
      const tick = m.me && m.status === "local" ? '<span class="t-cap">on this phone</span>' : m.me ? (m.status === "failed" ? '<span class="fail">Not sent · out of range</span>'
        : `<span class="${m.status === "delivered" || m.status === "read" ? "ok" : ""}" style="display:inline-flex">${TICK[m.status] || ""}</span>`) : "";
      const tap = m.me && m.status !== "local";
      out.push(`<div class="msg ${m.me ? "me" : "them"} ${tap ? "tappable" : ""} ${m.route && m.route.length > 2 ? "routed" : ""} ${pos} ${withPrev ? "" : "gap"} ${isNew ? "new" : ""}" data-mid="${m.id}" ${tap ? `data-act="msg-info" data-v="${m.id}"` : ""}>
        <div class="bubble">${esc(m.text)}</div>${m.me ? journeyHtml(m) : ""}
        ${m.actions ? `<div class="bot-actions">${m.actions.map((a) => `<button class="chip" data-act="${a.act}" data-v="${a.v || ""}">${esc(a.label)}</button>`).join("")}</div>` : ""}
        ${showMeta ? `<div class="meta">${clock(m.time)}${tick}</div>` : ""}</div>`);
    });
    if (c.typing) out.push('<div class="typing" aria-label="typing"><i></i><i></i><i></i></div>');
    return out.join("");
  }
  function renderChatParts(first) {
    const id = S.chat, info = chatInfo(id), c = convo(id);
    c.unread = 0;
    const sub = document.getElementById("chat-sub");
    if (!sub) return;
    if (first) S.convos[id].messages.forEach((m) => S.seen.add(m.id));
    sub.innerHTML = c.typing ? '<span class="typing-text">typing…</span>'
      : info.bot ? "Lives on your phone · works offline"
      : info.far ? statusLine(P[id])
      : info.presence === "online" ? '<span style="color:var(--pine)">Online nearby</span>'
      : info.presence === "range" ? "In range · connecting…" : "Seen " + ago(P[id].lastSeen);
    const av = document.getElementById("chat-av");
    if (av) av.innerHTML = avatar(info.emoji, id, 38, info.presence);
    const ping = document.getElementById("ping-btn");
    if (ping) ping.hidden = info.presence !== "online";

    const strip = document.getElementById("route-strip");
    if (strip) {
      const lastMine = c.messages.filter((m) => m.me).at(-1);
      const route = lastMine && lastMine.route;
      strip.innerHTML = lastMine
        ? `<span>Last message: <b>${route ? route.map(esc).join(" → ") : "You → " + esc(info.name)}</b> · ${lastMine.status === "read" ? "read " + clock(lastMine.receipts.at(-1).time) : lastMine.status === "delivered" ? "delivered " + clock(lastMine.receipts[0].time) : lastMine.status === "pending" ? "waiting: in range or bridge, whichever comes first" : "on its way"}</span><span class="chev">${I.chevron}</span>`
        : `<span><b>${esc(S.name)} ${shortId(MY_ID)}</b> → <b>${esc(info.name)} ${P[id].sms ? esc(P[id].phone) : shortId(P[id].uid)}</b> · ${info.far ? "through a bridge to the internet" : "direct, phone to phone"}</span>`;
      strip.dataset.v = lastMine ? lastMine.id : "";
    }
    const box = document.getElementById("messages");
    const nearBottom = first || box.scrollHeight - box.scrollTop - box.clientHeight < 120;
    box.innerHTML = messagesHtml(id, info, c);
    if (nearBottom) box.scrollTo({ top: box.scrollHeight, behavior: first ? "auto" : "smooth" });

    document.getElementById("chips").innerHTML = info.bot && !c.typing
      ? SKY_CHIPS.map((s) => `<button class="chip" data-act="suggest" data-v="${esc(s)}">${esc(s)}</button>`).join("") : "";
    const ok = canSend(id);
    const note = document.getElementById("note");
    const noteText = info.far && !bridgeOnline() ? "No one nearby has internet yet. Your message waits safely and goes out the moment a bridge appears." : "";
    note.textContent = noteText; note.hidden = !noteText;
    const draft = document.getElementById("draft");
    draft.disabled = !ok;
    draft.placeholder = ok ? "Message" : "Out of range";
    syncSend();
  }
  function syncSend() {
    const draft = document.getElementById("draft"), btn = document.getElementById("send-btn");
    if (!draft || !btn) return;
    const has = !!draft.value.trim();
    btn.classList.toggle("mic", !has);
    btn.innerHTML = has ? I.send : I.mic;
    btn.setAttribute("aria-label", has ? "Send" : "Record voice note");
    btn.disabled = draft.disabled;
  }

  // ---------- You ----------
  function profileView() {
    const set = (tileColor, icon, title, sub, right, act = "", v = "") => {
      const tagName = act ? "button" : "div";
      return `<${tagName} class="set" ${act ? `data-act="${act}" data-v="${v}"` : ""}>
        <span class="tile" style="background:${tileColor}">${icon}</span>
        <span class="main"><span class="t-strong" style="display:block">${title}</span>${sub ? `<span class="t-cap">${sub}</span>` : ""}</span>${right}</${tagName}>`;
    };
    const chev = `<span class="chev">${I.chevron}</span>`;
    return `
      <div class="me-head">
        <span class="me-badge">${esc(S.avatar)}</span>
        <h1 class="t-title" style="margin-top:10px">${esc(S.name)}</h1>
        <button class="idline" data-act="copy-id" aria-label="Copy your BlueMob ID">BM · ${fmtId(MY_ID)} ${I.copy}</button>
        <span class="t-cap">Your unique ID, given automatically to this phone</span>
      </div>

      <div class="group-label t-over">Profile</div>
      <div class="group" style="padding:14px;display:flex;flex-direction:column;gap:14px">
        <div class="field"><label class="t-over" for="pf-name">Name</label><input id="pf-name" maxlength="24" value="${esc(S.name)}"></div>
        <div class="picker" role="group" aria-label="Choose an avatar">${AVATARS.map((a) =>
          `<button data-act="pick-avatar" data-v="${a}" aria-pressed="${a === S.avatar}">${a}</button>`).join("")}</div>
      </div>

      <div class="group-label t-over">Connections</div>
      <div class="group">
        ${set("var(--pine)", I.mesh, "Mesh", "Find and be found by nearby phones",
          `<button class="switch" role="switch" aria-checked="${S.mesh}" aria-label="Mesh" data-act="toggle-mesh"></button>`)}
        ${set("var(--sky)", I.pin, "Share my location", "Friends see how far you are. GPS, no internet",
          `<button class="switch" role="switch" aria-checked="${S.shareLoc}" aria-label="Share my location" data-act="toggle-loc"></button>`)}
        ${set("#3A9A5B", I.battery, "Power", S.survival ? "Survival power on · about " + hoursLeft(true) + " h left" : "Battery " + S.batteryPct + "% · make it last for days", chev, "power")}
        ${set("var(--c-hop)", I.chart, "Mesh insights", "Messages, routes and links", chev, "insights")}
      </div>

      <div class="group-label t-over">Safety and people</div>
      <div class="group">
        ${set("var(--rose)", I.alert, "SOS", S.sos ? "Active since " + clock(S.sos.time) : "Alert people nearby and text your SOS contacts", chev, "sos")}
        ${set("var(--sky)", I.phone, "Loved ones", S.contacts.length + " saved · message by phone number", chev, "contacts")}
        ${set("#3A9A5B", I.wallet, "Trip money", "Split costs, settle with UPI later", chev, "money")}
        ${set("#7C6BD6", I.game, "Game invites", S.muteInvites ? "Muted" : "People nearby can invite you",
          `<button class="switch" role="switch" aria-checked="${!S.muteInvites}" aria-label="Game invites" data-act="mute-invites"></button>`)}
      </div>

      <div class="group-label t-over">BlueMob</div>
      <div class="group">
        ${set("var(--ember)", I.palette, "Design system", "Colours, type, shape and motion", chev, "design")}
        ${set("#7C6BD6", I.sparkle, "Replay the intro", "", chev, "replay")}
        ${set("var(--rose)", I.trash, "Forget people I've met", "Clears last-seen history", "", "forget")}
      </div>

      <div class="group-label t-over">For testers</div>
      <div class="group">
        ${set("#3A4A44", I.terminal, "Mesh activity log", "What the mesh is doing, step by step",
          `<span class="chev" style="transform:rotate(${S.showLog ? 90 : 0}deg);transition:transform var(--d-2)">${I.chevron}</span>`, "toggle-log")}
        ${S.showLog ? `<div class="log">${S.log.length ? S.log.map((l) => `<div>${esc(l)}</div>`).join("") : "Nothing yet"}</div>` : ""}
      </div>
      <p class="t-cap" style="text-align:center;margin-top:24px">BlueMob web preview 0.4 · made for the open sky</p>`;
  }

  // ---------- Design system (style guide) ----------
  function designView(anim) {
    const sw = (name, token, note) => `<div class="swatch"><i style="background:var(${token})"></i><b>${name}</b><span class="t-cap">${note}</span></div>`;
    return `<div class="screen grouped ${anim === "push" ? "push-in" : ""}">
      <header class="bar solid"><button class="icon-btn" data-act="back" aria-label="Back">${I.back}</button><div class="bar-title" style="opacity:1;transform:none">Design system</div></header>
      <div class="scroll" id="scroll">
        <div class="large-title" style="margin-top:12px"><span class="tag ember">PINE &amp; SAND · v0.4</span>
          <h1 class="t-hero" style="margin-top:10px">Calm like Signal, warm like Airbnb.</h1>
          <p class="t-sub" style="margin-top:8px;max-width:36ch">One accent, warm neutrals, soft depth and motion that settles like a breath. Everything below is live and follows your light or dark setting.</p></div>

        <div class="group-label t-over">Colour</div>
        <div class="group" style="padding:14px"><div class="swatches">
          ${sw("Pine", "--pine", "Brand, actions")}${sw("Pine tint", "--pine-tint", "Selected")}${sw("Mint", "--mint", "Live, radar")}
          ${sw("Ember", "--ember", "Internet bridge")}${sw("Sky", "--sky", "Info, bot")}${sw("Rose", "--rose", "Errors")}
          ${sw("Ink", "--ink", "Text")}${sw("Ink 2", "--ink-2", "Secondary")}${sw("Sand", "--sand", "Fields")}
          ${sw("Canvas", "--canvas", "Surfaces")}${sw("Night", "--night-2", "Radar sky")}${sw("Line", "--line", "Dividers")}
        </div></div>

        <div class="group-label t-over">Type</div>
        <div class="group spec">
          <div class="spec-row"><span class="t-hero">Off-grid</span><span class="t-cap num">Hero 34</span></div>
          <div class="spec-row"><span class="t-title">Nearby</span><span class="t-cap num">Title 28</span></div>
          <div class="spec-row"><span class="t-head">Around you</span><span class="t-cap num">Head 20</span></div>
          <div class="spec-row"><span>Messages hop from friend to friend.</span><span class="t-cap num">Body 16</span></div>
          <div class="spec-row"><span class="t-sub">Seen 2 h ago · 1.8 km</span><span class="t-cap num">Sub 14</span></div>
          <div class="spec-row"><span class="t-over">Online nearby</span><span class="t-cap num">Over 11</span></div>
          <p class="t-cap">Display: Bricolage Grotesque, tight tracking. Text: Figtree.</p>
        </div>

        <div class="group-label t-over">Shape &amp; depth</div>
        <div class="group spec">
          <div class="radii">${[["8", "--r-xs"], ["12", "--r-sm"], ["16", "--r-md"], ["22", "--r-lg"], ["28", "--r-xl"], ["pill", "--r-pill"]].map(([l, t]) =>
            `<div style="border-radius:var(${t})">${l}</div>`).join("")}</div>
          <div style="display:flex;gap:12px">
            <div class="card" style="flex:1;padding:14px"><b>Level 1</b><div class="t-cap">Cards and rows</div></div>
            <div style="flex:1;padding:14px;border-radius:var(--r-lg);background:var(--canvas);box-shadow:var(--shadow-2)"><b>Level 2</b><div class="t-cap">Floating bars</div></div>
          </div>
        </div>

        <div class="group-label t-over">Motion</div>
        <div class="group spec">
          <div class="curve"><span class="t-cap" style="width:70px">Ease out</span><div class="track"><span style="animation-timing-function:var(--ease)"></span></div></div>
          <div class="curve"><span class="t-cap" style="width:70px">Spring</span><div class="track"><span style="animation-timing-function:var(--spring)"></span></div></div>
          <p class="t-cap">140 ms for taps, 240 ms for fades, 420 ms for screens. Springs only on things you touch.</p>
        </div>

        <div class="group-label t-over">Components</div>
        <div class="group spec">
          <div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn small" data-act="noop">Primary</button><button class="btn small secondary" data-act="noop">Secondary</button><button class="btn text" data-act="noop">Text</button></div>
          <div style="display:flex;gap:8px;flex-wrap:wrap"><span class="chip" aria-pressed="true">All</span><span class="chip">Nearby</span><span class="tag pine">DIRECT</span><span class="tag ember">VIA BRIDGE</span><span class="tag sky">BOT</span></div>
          <div style="display:flex;gap:14px;align-items:center">${avatar("🦋", "asha", 48, "online")}${avatar("🌵", "meera", 48, "range")}${avatar("🐺", "kabir", 48, "offline")}${avatar("🌻", "dee", 48, "remote")}
            <button class="switch" role="switch" aria-checked="true" aria-label="Example switch" data-act="noop"></button></div>
          <div style="display:flex;flex-direction:column">
            <div class="msg them first"><div class="bubble">Saw you on my radar 👋</div></div>
            <div class="msg them last"><div class="bubble">Meet at camp?</div></div>
            <div class="msg me gap"><div class="bubble">On my way!</div><div class="meta">18:42<span class="ok" style="display:inline-flex">${TICK.delivered}</span></div></div>
          </div>
          <div class="t-cap" style="display:flex;gap:14px;align-items:center;flex-wrap:wrap">
            <span style="display:inline-flex;gap:4px;align-items:center">${TICK.sending.replace("<svg", '<svg width="15" height="15"')} Sending</span>
            <span style="display:inline-flex;gap:4px;align-items:center">${TICK.sent.replace("<svg", '<svg width="15" height="15"')} Sent</span>
            <span class="ok" style="display:inline-flex;gap:4px;align-items:center;color:var(--pine)">${TICK.delivered.replace("<svg", '<svg height="15"')} Delivered</span>
            <span style="display:inline-flex;gap:4px;align-items:center">${TICK.queued.replace("<svg", '<svg width="15" height="15"')} Waiting</span>
          </div>
          <div class="msg me" style="max-width:100%">${journeyHtml({ status: "sent", route: ["You", "Ravi", "Meera 🌐", "Internet", "Dee"], hop: 2 })}</div>
        </div>
        <p class="t-cap" style="text-align:center;margin-top:24px">The Android app uses the same tokens in Jetpack Compose.</p>
      </div></div>`;
  }

  // ---------- events ----------
  app.addEventListener("click", (e) => {
    const el = e.target.closest("[data-act]");
    if (!el) return;
    const act = el.dataset.act, v = el.dataset.v;
    switch (act) {
      case "next": S.slide++; render(); break;
      case "skip": S.slide = SLIDES.length; render(); break;
      case "finish": finishOnboarding(); break;
      case "pick-avatar":
        S.avatar = v; store.set("avatar", v);
        if (!S.onboarded) {
          document.getElementById("ob-avatar").textContent = v;
          document.querySelectorAll('[data-act="pick-avatar"]').forEach((b) => b.setAttribute("aria-pressed", b.dataset.v === v));
        } else render();
        break;
      case "toggle-mesh": S.mesh ? stopMesh() : startMesh(); break;
      case "seg":
        S.shareLoc = v === "distance"; store.set("shareLoc", S.shareLoc);
        if (S.shareLoc) toast("Sharing your location with people nearby");
        render(); break;
      case "toggle-loc":
        S.shareLoc = !S.shareLoc; store.set("shareLoc", S.shareLoc);
        if (S.shareLoc) toast("Sharing your location with people nearby");
        render(); break;
      case "tab":
        if (S.tab === v) { app.querySelector("#scroll")?.scrollTo({ top: 0, behavior: "smooth" }); break; }
        S.prevTab = S.tab; S.tab = v; store.set("tab", v); render("fade"); S.prevTab = v; break;
      case "filter": S.filter = v; render(); break;
      case "open-chat": open("chat", { chat: el.dataset.id }); break;
      case "design": case "power": case "insights": case "games": case "contacts": case "money": case "play": open(act); break;
      case "sos": S.sosArmed = false; open("soshub"); break;
      case "go-tab": S.stack = []; S.chat = null; S.screen = "main"; S.prevTab = S.tab; S.tab = v; render("fade"); S.prevTab = v; break;
      case "sos-light": S.signalMode = S.signalDefault; open("sos"); break;
      case "signal-mode": S.signalMode = v; clearTimeout(sosTimer); render(); break;
      case "signal-default": S.signalDefault = v; S.signalMode = v; store.set("signalDefault", v); toast(SIGNAL_MODES.find((x) => x.id === v).label + " is now your default SOS signal"); if (S.screen === "sos") { clearTimeout(sosTimer); } render(); break;
      case "sos-reason": S.sosNote = S.sosNote.includes(v) ? S.sosNote.replace(v, "").replace(/^[,\s]+|[,\s]+$/g, "").replace(/,\s*,/g, ",") : (S.sosNote ? S.sosNote + ", " : "") + v; render(); break;
      case "sos-test": back(); later(300, () => { if (P.ravi.presence !== "online") { toast("Switch the mesh on first, so someone nearby can send one"); return; } receiveSos("ravi", "Twisted my ankle near the stream. Can't walk. Please bring a torch"); }); break;
      case "sos-coming": S.alertOpen = false; send(v, "I'm coming! Stay where you are. I'll be there soon 🙏"); toast("Told " + P[v].name + " you're coming"); render(); break;
      case "sos-way": S.alertOpen = false; S.navTarget = v; S.stack = []; S.screen = "main"; S.prevTab = S.tab; S.tab = "compass"; render("fade"); S.prevTab = "compass"; break;
      case "sos-howto": S.alertOpen = false; open("article", { article: "help-sos" }); break;
      case "sos-dismiss": S.alertOpen = false; render(); break;
      case "sos-send":
        if (!S.sosArmed) { S.sosArmed = true; render(); later(4000, () => { if (S.sosArmed) { S.sosArmed = false; if (S.screen === "soshub") render(); } }); }
        else { S.sosArmed = false; sendSos(); toast("SOS sent to everyone nearby"); }
        break;
      case "sos-safe":
        S.sos.contacts.forEach((sc) => send(sc.id, "I'm safe now. Thank you. " + S.name));
        S.sos.near.forEach((id) => send(id, "I'm safe now, thank you! 🙏"));
        S.sos = null; toast("Told everyone you're safe"); render(); break;
      case "ask-expert": askExpert(); break;
      case "msg-info": open("info", { info: { chat: S.chat, mid: v } }); break;
      case "route-strip": if (el.dataset.v) open("info", { info: { chat: S.chat, mid: el.dataset.v } }); else open("person", { person: S.chat }); break;
      case "contact-sos": { const c = S.contacts.find((x) => x.id === v); c.sos = !c.sos; saveContacts(); render(); break; }
      case "contact-del": S.contacts = S.contacts.filter((x) => x.id !== v); saveContacts(); toast("Removed"); render(); break;
      case "pay-open": S.payOpen = S.payOpen === v ? null : v; render(); break;
      case "pay-upi": toast("On Android this opens your UPI app with " + RUPEE(-Object.fromEntries(balances())[v]) + " to " + P[v].name + " filled in"); S.payOpen = null; render(); break;
      case "pay-cash": S.settled[v] = (S.settled[v] || 0) + Object.fromEntries(balances())[v]; store.set("settled", S.settled); S.payOpen = null;
        send(v, "💸 Paid you in cash. Marked as settled in Trip money."); toast("Marked as paid. " + P[v].name + " is notified"); render(); break;
      case "pay-request": send(v, "💸 Payment request: " + RUPEE(Object.fromEntries(balances())[v]) + " for the trip. Pay me through UPI when you're online, or in cash."); toast("Request sent to " + P[v].name); break;
      case "ex-toggle": S.exSplit = (S.exSplit || []).includes(v) ? S.exSplit.filter((x) => x !== v) : [...(S.exSplit || []), v]; render(); break;
      case "lobby": openLobby(v); break;
      case "lobby-switch": openLobby(v, el.dataset.p); break;
      case "lobby-start": { const R = S.lobby.responses; startGame(S.lobby.game, Object.keys(R).find((id) => R[id].state === "joined")); break; }
      case "mute-invites": S.muteInvites = !S.muteInvites; store.set("muteInvites", S.muteInvites); if (S.muteInvites) S.invite = null;
        toast(S.muteInvites ? "Game invites muted" : "Game invites on"); render(); break;
      case "invite-join": { const iv = S.invite; S.invite = null; startGame(iv.game, iv.from); break; }
      case "invite-other": S.invite.choosing = true; render(); break;
      case "invite-suggest": { const iv = S.invite; S.invite = null; toast(`Asked ${P[iv.from].name}: “How about ${gameOf(v).name}?”`); render();
        later(1500, () => { toast(P[iv.from].name + " said yes!"); later(600, () => { if (S.screen === "main") openLobby(v, iv.from); }); }); break; }
      case "invite-mute": S.invite = null; S.muteInvites = true; store.set("muteInvites", true); toast("Game invites muted. Turn them back on in You"); render(); break;
      case "c4": c4Play(Number(v)); break;
      case "c4-new": Object.assign(C4, { b: Array(42).fill(""), turn: "x", over: null, line: [], last: -1 }); render(); break;
      case "words-giveup": W.over = W.opp; W.msg = "You gave up. Good game!"; render(); break;
      case "words-new": Object.assign(W, { words: [], over: null, msg: "", turn: "me" }); render(); break;
      case "hunt-turn": H.heading = (H.heading + Number(v) + 360) % 360; render(); break;
      case "hunt-walk": huntWalk(); break;
      case "hunt-new": huntNew(H.opp); render(); break;
      case "back": back(); break;
      case "person": open("person", { person: v }); break;
      case "survival": S.survival = !S.survival; store.set("survival", S.survival); applySurvival();
        toast(S.survival ? "Survival power on: about " + hoursLeft(true) + " h left" : "Back to normal power"); render(); break;
      case "power-step": S.steps[v] = true; store.set("powerSteps", S.steps);
        toast(v === "saver" ? "On Android this opens Battery Saver settings" : "On Android this asks to keep BlueMob running"); render(); break;
      case "chart-table": S.chartTable = !S.chartTable; render(); break;
      case "people-filter": S.peopleFilter = v; render(); break;
      case "sort": S.sort = { near: "recent", recent: "name", name: "near" }[S.sort]; render(); break;
      case "nav-target": S.navTarget = v; render(); break;
      case "nav-to": S.navTarget = v; S.stack = []; S.screen = "main"; S.prevTab = S.tab; S.tab = "compass"; render("fade"); S.prevTab = "compass"; break;
      case "turn": S.heading = (S.heading + Number(v) + 360) % 360; render(); break;
      case "save-spot": {
        const m = me(), n = WAYPOINTS.filter((w) => w.id.startsWith("spot")).length + 1;
        WAYPOINTS.push({ id: "spot" + n, name: "Spot " + n, emoji: "📍", x: m.x, y: m.y });
        store.set("waypoints", WAYPOINTS); toast("Saved Spot " + n + ". It stays on your phone"); render(); break;
      }
      case "guide-cat": S.guideCat = S.guideCat === v ? null : v || null; render(); break;
      case "open-article": open("article", { article: v }); break;
      case "bookmark": S.bookmarks = S.bookmarks.includes(v) ? S.bookmarks.filter((b) => b !== v) : [...S.bookmarks, v];
        store.set("bookmarks", S.bookmarks); toast(S.bookmarks.includes(v) ? "Saved for offline reading" : "Removed from saved"); render(); break;
      case "sos": open("sos"); break;
      case "sos-stop": back(); break;
      case "play-ttt": Object.assign(G, { board: Array(9).fill(""), turn: "x", over: null, line: null, last: -1 }); open("ttt"); break;
      case "ttt": tttPlay(Number(v)); break;
      case "ttt-new": Object.assign(G, { board: Array(9).fill(""), turn: "x", over: null, line: null, last: -1 }); render(); break;
      case "play-quiz": Object.assign(Q, { i: 0, you: 0, them: 0, picked: null, theirPick: null, done: false }); open("quiz"); break;
      case "quiz": quizAnswer(Number(v)); break;
      case "quiz-next": if (Q.i + 1 < QUIZ.length) Object.assign(Q, { i: Q.i + 1, picked: null, theirPick: null }); else Q.done = true; render(); break;
      case "quiz-new": Object.assign(Q, { i: 0, you: 0, them: 0, picked: null, theirPick: null, done: false }); render(); break;
      case "tab-guide": S.stack = []; S.screen = "main"; S.prevTab = S.tab; S.tab = "guide"; render("fade"); S.prevTab = "guide"; break;
      case "suggest": send("sky", v); break;
      case "soon": toast(v + (v.endsWith("s") ? " are" : " is") + " coming soon"); break;
      case "copy-id":
        try { navigator.clipboard.writeText("BM-" + MY_ID).then(() => toast("ID copied"), () => toast("Your ID: BM · " + fmtId(MY_ID))); }
        catch { toast("Your ID: BM · " + fmtId(MY_ID)); }
        break;
      case "ping": {
        const p = P[S.chat];
        const rtt = Math.round(p.rtt * (0.8 + Math.random() * 0.5));
        log("Ping " + p.name + ": " + rtt + " ms");
        toast("Link to " + p.name + ": " + rtt + " ms round trip");
        break;
      }
      case "toggle-log": S.showLog = !S.showLog; render(); break;
      case "replay": S.onboarded = false; S.slide = 0; store.set("onboarded", false); render(); break;
      case "forget":
        for (const p of Object.values(P)) if (!p.remote && p.presence === "offline") p.met = false;
        toast("Forgot everyone who isn't nearby");
        render(); break;
    }
  });
  app.addEventListener("submit", (e) => {
    if (e.target.id === "contact-form") {
      e.preventDefault();
      const name = document.getElementById("ct-name").value.trim(), phone = document.getElementById("ct-phone").value.trim();
      if (!name || phone.replace(/\D/g, "").length < 6) { toast("Add a name and a full phone number"); return; }
      const c = { id: "c-" + uid(), name, phone, sos: document.getElementById("ct-sos").checked };
      S.contacts.push(c); contactToPerson(c); saveContacts(); toast(name + " added"); render(); return;
    }
    if (e.target.id === "expense-form") {
      e.preventDefault();
      const amt = Number(document.getElementById("ex-amt").value.replace(/[^\d.]/g, "")), note = document.getElementById("ex-note").value.trim() || "Shared cost";
      const split = ["me", ...(S.exSplit || [])];
      if (!amt || split.length < 2) { toast(!amt ? "Enter an amount" : "Pick at least one person to split with"); return; }
      S.ledger.push({ id: uid(), payer: "me", amount: amt, note, split, time: now() }); store.set("ledger", S.ledger);
      S.exSplit.forEach((id) => send(id, `🧾 I paid ${RUPEE(amt)} for ${note}. Your share is ${RUPEE(amt / split.length)}. It's in Trip money.`));
      S.exSplit = []; toast("Added and shared with " + (split.length - 1) + " people"); render(); return;
    }
    if (e.target.id === "word-form") { e.preventDefault(); wordsPlay(document.getElementById("word-in").value); return; }
    if (e.target.id !== "composer") return;
    e.preventDefault();
    const input = document.getElementById("draft");
    if (!canSend(S.chat)) return;
    if (!input.value.trim()) { toast("Voice notes are coming soon"); return; }
    send(S.chat, input.value);
    input.value = "";
    syncSend();
    input.focus();
  });
  app.addEventListener("input", (e) => {
    if (e.target.id === "draft") syncSend();
    if (e.target.id === "q") { S.query = e.target.value; render(); }
    if (e.target.id === "gq") { S.gq = e.target.value; render(); }
    if (e.target.id === "sos-note") { S.sosNote = e.target.value.slice(0, 160); const pv = document.getElementById("sos-preview"); if (pv) pv.textContent = sosText(); }
    if (e.target.id === "pf-name") {
      const v = e.target.value.replace(/\|/g, " ").slice(0, 24);
      if (v.trim()) { S.name = v.trim(); store.set("name", S.name); app.querySelector(".me-head h1").textContent = S.name; }
    }
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && S.screen !== "main" && S.onboarded) back();
  });

