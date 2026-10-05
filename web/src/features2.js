
  // =====================================================================
  //  Round 2: Sky answers from the guide, experts, SOS contacts, loved ones,
  //  message routes, trip money, game lobby + more games
  // =====================================================================

  // ---------- reach anyone by ID, even after the trip ----------
  P.experts = { id: "experts", name: "Expert network", avatar: "🩺", uid: "E0C4A71B3D9F2265", remote: true, presence: "remote", met: true,
    about: "Volunteer medics, rangers and rescuers" };

  // Loved ones and SOS contacts are phone numbers. They don't need BlueMob: they get a normal text (SMS).
  S.contacts = store.get("contacts", [{ id: "c-mom", name: "Mom", phone: "+91 98765 43210", sos: true, example: true }]);
  function contactToPerson(c) {
    P[c.id] = { id: c.id, name: c.name, avatar: "💌", uid: c.phone.replace(/\D/g, "").padEnd(16, "0").slice(0, 16), remote: true, sms: true,
      phone: c.phone, presence: "remote", met: true };
    FRIEND[c.id] = { rules: [[["sos", "help", "emergency"], ["(SMS reply) We got your SOS and called the rescue team. Stay where you are, we love you ❤️"]]],
      fallback: ["(SMS reply) Thank god you're okay! ❤️ Call when you can.", "(SMS reply) Got it. Stay warm and safe 🙏"] };
  }
  S.contacts.forEach(contactToPerson);
  const saveContacts = () => store.set("contacts", S.contacts.map(({ id, name, phone, sos, example }) => ({ id, name, phone, sos, example })));

  // ---------- Sky answers survival questions from the built-in guide ----------
  const SKY_KEYS = {
    cpr: ["cpr", "unconscious", "not breathing", "stopped breathing", "heart attack", "cardiac", "collapsed", "resuscitat", "no pulse"],
    bleed: ["bleed", "blood", "deep cut", "wound", "tourniquet", "gash", "cut+knife", "cut+deep"],
    burns: ["burn", "scald", "hot water+skin"],
    choke: ["chok", "can't breathe food", "swallowed"],
    hypo: ["hypotherm", "freezing", "shiver", "too cold", "very cold", "frostbite"],
    heat: ["heat stroke", "heatstroke", "sunstroke", "overheat", "too hot", "heat exhaustion"],
    snake: ["snake", "venom", "bitten"],
    fracture: ["broken", "fracture", "sprain", "bone", "ankle", "twisted", "splint"],
    "find-water": ["find water", "no water", "thirst", "dehydrat", "where to get water", "out of water", "water+find", "water+where", "water+running out", "water+collect"],
    purify: ["purif", "boil water", "clean water", "safe water", "dirty water", "drink water", "drinking water", "safe to drink", "water+safe", "water+clean", "water+boil", "water+treat", "water+filter", "water+drinkable", "water+germs", "stream+drink", "river+drink"],
    fire: ["fire", "campfire", "matches", "lighter", "tinder", "keep+warm+wood"],
    shelter: ["shelter", "sleep outside", "sleep in the open", "stay dry", "build a hut", "sleep+night+outside", "rain+sleep"],
    "help-sos": ["someone sent an sos", "someone sent sos", "received an sos", "got an sos", "sos from", "help someone", "someone needs help", "friend needs help"],
    north: ["north", "direction", "which way", "without a compass", "navigate", "stars", "shadow stick", "shadow", "polaris", "north star", "southern cross", "sunrise", "sunset"],
    lost: ["lost", "can't find my way", "cant find my way", "stranded", "where am i", "way+back"],
    signals: ["rescue", "signal", "helicopter", "whistle", "get found", "be found", "attract attention"],
    lightning: ["lightning", "thunder", "storm"],
    quake: ["earthquake", "quake", "tremor"],
    flood: ["flood", "river rising", "water rising"],
    "battery-low": ["low battery", "battery low", "battery critical", "critical battery", "battery dying", "battery is dying", "phone dying", "phone is dying",
      "battery discharg", "battery+die", "battery+dead", "battery+%", "battery+save", "battery+last", "battery+empty", "battery+drain", "phone+switch off"],
    recharge: ["recharge", "charge my phone", "charge the phone", "charging", "power bank", "powerbank", "solar", "no charger", "regain battery",
      "battery back", "get power", "charge+without"],
    threes: ["priorit", "first thing", "how long can", "survive without", "hungry", "food"],
  };
  const URGENT = /\b(hurt|pain|sick|injur|emergency|bitten|stung|sting|allerg|fever|poison|vomit|faint|seizure|pregnan|unwell|dizzy|bee|scorpion|spider)/;
  function skyReply(text) {
    const t = text.toLowerCase();
    S.lastQ = text;
    // Questions about the app itself, and live insights from this phone, come first.
    const appAnswer = skyAppAnswer(t);
    if (appAnswer) return appAnswer;
    // Free time: suggestions, not an emergency reply.
    if (/\b(free time|spare time|something to do|what (can|should) i do (now|here)|pass (the )?time|kill time|entertain|bored)\b/.test(t)) {
      return { text: "Some ideas for free time out here 🌿\n• Play a game with someone nearby, or against the computer if no one's around\n• Take the survival quiz and learn a guide or two\n• Check who's around on the radar and say hi",
        actions: [{ label: "Play a game", act: "play" }, { label: "Survival quiz", act: "lobby", v: "quiz" }, { label: "Browse the guide", act: "tab-guide" }] };
    }
    let best = null, score = 0;
    for (const [id, keys] of Object.entries(SKY_KEYS)) {
      // "a+b" means every part must appear, in any order: "water+safe" matches "make water safe".
      const sc = keys.reduce((n, k) => n + (k.split("+").every((part) => t.includes(part)) ? k.length : 0), 0);
      if (sc > score) { score = sc; best = id; }
    }
    if (best) {
      const a = ARTICLES.find((x) => x.id === best);
      const steps = a.steps.slice(0, 4).map((s, i) => `${i + 1}. ${s}`).join("\n");
      const more = a.steps.length > 4 ? `\n…plus ${a.steps.length - 4} more step${a.steps.length - 4 > 1 ? "s" : ""} in the guide.` : "";
      const avoid = a.donts.length ? `\n\nAvoid: ${a.donts.join(" ")}` : "";
      const actions = [{ label: "Open full guide", act: "open-article", v: a.id }];
      if (a.cat === "aid") actions.push({ label: "🆘 SOS", act: "sos" });
      actions.push({ label: "Ask an expert", act: "ask-expert" });
      // Battery: speak to the number they gave, or to the real level.
      const pct = (t.match(/(\d{1,3})\s*%/) || [])[1];
      const lead = a.id === "battery-low" ? (pct ? `At ${pct}%, act now. ` : `Your battery is ${S.batteryPct}% right now. `) + "\n\n" : "";
      if (a.id === "battery-low") actions.splice(1, 0, { label: "How to recharge", act: "open-article", v: "recharge" }, { label: "Survival power", act: "power" });
      return { text: `${lead}From your survival guide: ${a.title}\n${a.intro}\n\n${steps}${more}${avoid}`, actions };
    }
    const unknownHelp = {
      text: "I don't have a guide for that on your phone. Here's what I can do:\n\n" +
        "• Ask an expert: I'll send your question to volunteer medics and rangers as soon as someone nearby has internet (a bridge). You'll be notified when they reply.\n" +
        "• SOS: reaches everyone nearby right away, and texts your SOS contacts as soon as a bridge appears.\n" +
        "• Message your loved ones: it goes out the moment a bridge is available.",
      actions: [{ label: "Ask an expert", act: "ask-expert" }, { label: "🆘 SOS", act: "sos" }, { label: "Message loved ones", act: "contacts" }, { label: "Browse the guide", act: "tab-guide" }],
    };
    if (URGENT.test(t)) return unknownHelp;
    if (SKY_RULES.some(([keys]) => keys.some((k) => matches(t, k)))) return pickReply(SKY_RULES, SKY_FALLBACK, text);
    if (/\?|\b(how|what|why|where|when|can i|should i|is it)\b/.test(t)) return {
      text: "I don't have an answer for that yet. I'm best with first aid, water, fire, shelter, finding your way, signals, weather, disasters, phone battery, and how BlueMob works.\n\nIf an expert should answer it, I can send your question as soon as someone nearby has internet.",
      actions: [{ label: "Browse the guide", act: "tab-guide" }, { label: "Ask an expert", act: "ask-expert" }] };
    return pickReply(SKY_RULES, SKY_FALLBACK, text);
  }
  function askExpert() {
    const q = (S.lastQ || "").trim();
    open("chat", { chat: "experts" });
    if (q) later(250, () => send("experts", "Question from the field: " + q));
  }

  // ---------- SOS: nearby now, marked contacts as soon as a bridge appears ----------
  S.sos = null;
  S.sosArmed = false;
  function flushSos() { refresh(); }
  function sosText() {
    const note = S.sosNote.trim();
    return `SOS from ${S.name} (BlueMob ${shortId(MY_ID)}). I need help.${note ? " " + note + "." : ""} Last position 30.0869 N, 78.2676 E, near base camp. Battery ${S.batteryPct}%. Sent with BlueMob.`;
  }
  function sendSos() {
    const near = nearby().filter((p) => p.presence === "online");
    const text = sosText();
    S.sos = { time: now(), near: near.map((p) => p.id), contacts: S.contacts.filter((c) => c.sos).map((c) => ({ id: c.id, status: "waiting", time: null })) };
    log("SOS sent to " + near.length + " people nearby");
    S.sos.contacts.forEach((sc) => { sc.msg = send(sc.id, text, { onDelivered: () => { sc.status = "sent"; sc.time = now(); refresh(); } }); });
    // People nearby answer first: they are the fastest help.
    near.slice(0, 1).forEach((p) => later(2500, () => incoming(p.id, `🆘 I got your SOS! I'm ${fmtDist(p.dist)} from you and coming now. Stay where you are.`)));
    render();
  }
  function sosHubView() {
    const s = S.sos;
    const contacts = S.contacts.filter((c) => c.sos);
    const statusRow = (icon, color, title, sub) => `<div class="set"><span class="tile" style="background:${color}">${icon}</span>
      <span class="main"><span class="t-strong" style="display:block">${title}</span><span class="t-cap">${sub}</span></span></div>`;
    const body = !s ? `
      <div class="sos-hero">
        <button class="sos-big ${S.sosArmed ? "armed" : ""}" data-act="sos-send" aria-label="${S.sosArmed ? "Tap again to send SOS" : "Send SOS"}">
          <b>SOS</b><span>${S.sosArmed ? "Tap again to send" : "Tap to send"}</span></button>
        <p class="t-sub" style="max-width:32ch">Goes to everyone nearby right away, and texts your SOS contacts as soon as someone nearby has internet.</p>
      </div>
      <div class="group-label t-over">What's happening? (optional)</div>
      <div class="group spec">
        <div class="filters" style="margin:0;padding:0">${SOS_REASONS.map((r) => `<button class="chip" data-act="sos-reason" data-v="${r}" aria-pressed="${S.sosNote.includes(r)}">${r}</button>`).join("")}</div>
        <textarea id="sos-note" class="note-field" rows="2" maxlength="160" placeholder="e.g. Twisted ankle near the stream, can't walk">${esc(S.sosNote)}</textarea>
        <p class="t-cap">You can send without a note. Every second counts.</p>
      </div>
      <div class="group-label t-over">What will be sent</div>
      <div class="group spec"><p style="font-size:15px" id="sos-preview">${esc(sosText())}</p></div>`
      : `
      <div class="large-title" style="margin-top:8px"><span class="tag" style="background:var(--rose);color:#fff">SOS ACTIVE · ${clock(s.time)}</span>
        <h1 class="t-hero" style="margin-top:10px">Help is being called</h1></div>
      <div class="group">
        ${statusRow(I.mesh, "var(--pine)", `Delivered to ${s.near.length} people nearby`, s.near.length ? s.near.map((id) => nameWithId(P[id])).join(", ") + " · straight away, over the mesh" : "No one connected right now. It will go out as people appear.")}
        ${s.contacts.map((sc) => { const c = P[sc.id]; return statusRow(I.phone, sc.status === "sent" ? "var(--pine)" : "var(--ember)",
          `${esc(c.name)} · ${esc(c.phone)}`, sc.status === "sent" ? `Texted at ${clock(sc.time)} through Meera's internet`
            : sc.msg && sc.msg.status === "sent" ? "On its way through Meera's internet…"
            : "Waiting for a bridge. Sends automatically when someone nearby has internet."); }).join("")}
      </div>
      <div style="display:flex;gap:10px;justify-content:center;margin-top:18px"><button class="btn" data-act="sos-safe">I'm safe now</button></div>`;
    return subScreen("SOS", `${body}
      <div class="group-label t-over">More</div>
      <div class="group">
        <button class="set" data-act="sos-light"><span class="tile" style="background:var(--rose)">${I.flash}</span><span class="main"><span class="t-strong" style="display:block">SOS signal</span><span class="t-cap">Screen, flashlight, sound or all · ··· ––– ···</span></span><span class="chev">${I.chevron}</span></button>
        <div class="set" style="flex-wrap:wrap"><span class="tile" style="background:var(--ember)">${I.bolt}</span><span class="main"><span class="t-strong" style="display:block">Default signal</span><span class="t-cap">Used when you open the SOS signal</span></span>
          <div class="filters" style="flex-basis:100%;margin:8px 0 0 48px;padding:0">${SIGNAL_MODES.map((x) => `<button class="chip" data-act="signal-default" data-v="${x.id}" aria-pressed="${S.signalDefault === x.id}">${x.em} ${x.label}</button>`).join("")}</div></div>
        <button class="set" data-act="contacts"><span class="tile" style="background:var(--sky)">${I.phone}</span><span class="main"><span class="t-strong" style="display:block">SOS contacts</span><span class="t-cap">${contacts.length ? contacts.map((c) => esc(c.name)).join(", ") : "None yet. Add a number"}</span></span><span class="chev">${I.chevron}</span></button>
        <button class="set" data-act="open-article" data-v="help-sos"><span class="tile" style="background:var(--pine)">${I.book}</span><span class="main"><span class="t-strong" style="display:block">If you receive an SOS</span><span class="t-cap">How to help someone safely</span></span><span class="chev">${I.chevron}</span></button>
        <button class="set" data-act="sos-test"><span class="tile" style="background:var(--ink-3)">${I.alert}</span><span class="main"><span class="t-strong" style="display:block">Preview: receive an SOS</span><span class="t-cap">See what happens when someone nearby asks for help</span></span><span class="chev">${I.chevron}</span></button>
      </div>`);
  }

  // ---------- loved ones & SOS contacts (phone numbers) ----------
  function contactsView() {
    return subScreen("Loved ones", `
      <div class="large-title" style="margin-top:8px"><h1 class="t-hero">Loved ones</h1>
        <p class="t-sub" style="margin-top:6px">Message anyone by phone number. They don't need BlueMob: your message reaches them as a normal text the moment someone nearby has internet.</p></div>
      <div class="group">${S.contacts.length ? S.contacts.map((c) => `<div class="set">
          <span class="avatar" style="width:40px;height:40px"><span class="face" style="background:${tint(c.id)};font-size:20px">💌</span></span>
          <span class="main"><span class="t-strong" style="display:block">${esc(c.name)} ${c.example ? '<span class="tag">EXAMPLE</span>' : ""}</span><span class="t-cap num">${esc(c.phone)}</span></span>
          <button class="chip" data-act="contact-sos" data-v="${c.id}" aria-pressed="${!!c.sos}" aria-label="Use for SOS">SOS</button>
          <button class="icon-btn" data-act="open-chat" data-id="${c.id}" aria-label="Message ${esc(c.name)}">${I.chat}</button>
          <button class="icon-btn" data-act="contact-del" data-v="${c.id}" aria-label="Remove ${esc(c.name)}">${I.trash}</button></div>`).join("")
        : '<div class="set"><span class="t-sub">No one yet. Add someone below.</span></div>'}</div>
      <div class="group-label t-over">Add someone</div>
      <form class="group spec" id="contact-form">
        <div class="field"><label class="t-over" for="ct-name">Name</label><input id="ct-name" maxlength="30" placeholder="e.g. Papa" autocomplete="name"></div>
        <div class="field"><label class="t-over" for="ct-phone">Phone number</label><input id="ct-phone" inputmode="tel" maxlength="20" placeholder="+91 …" autocomplete="tel"></div>
        <label class="row" style="padding:0;gap:10px;cursor:pointer"><input type="checkbox" id="ct-sos" checked style="width:20px;height:20px;accent-color:var(--pine)"> <span>Text them when I send an SOS</span></label>
        <button class="btn" type="submit">Add</button>
      </form>
      <p class="t-cap" style="text-align:center;margin-top:16px">Numbers stay on your phone. They're only used when you send a message or an SOS.</p>`);
  }

  // ---------- trip money: who owes whom, settled later through UPI ----------
  const RUPEE = (n) => "₹" + Math.round(n).toLocaleString("en-IN");
  S.ledger = store.get("ledger", [
    { id: "e1", payer: "asha", amount: 900, note: "Dinner at the dhaba", split: ["me", "asha", "ravi"], time: now() - 5 * 3600e3, example: true },
    { id: "e2", payer: "me", amount: 600, note: "Fuel", split: ["me", "ravi"], time: now() - 3 * 3600e3, example: true },
    { id: "e3", payer: "meera", amount: 450, note: "Firewood and tea", split: ["me", "meera", "asha"], time: now() - 3600e3, example: true },
  ]);
  S.settled = store.get("settled", {});
  S.payOpen = null;
  const nameOf = (id) => (id === "me" ? "You" : P[id] ? P[id].name : id);
  function balances() {
    const b = {};
    for (const e of S.ledger) {
      const share = e.amount / e.split.length;
      if (e.payer === "me") e.split.filter((x) => x !== "me").forEach((x) => (b[x] = (b[x] || 0) + share));
      else if (e.split.includes("me")) b[e.payer] = (b[e.payer] || 0) - share;
    }
    for (const [id, v] of Object.entries(S.settled)) if (b[id] != null) b[id] -= v;
    return Object.entries(b).filter(([, v]) => Math.abs(v) >= 1);
  }
  function moneyView() {
    const bal = balances();
    const owe = bal.filter(([, v]) => v < 0).reduce((n, [, v]) => n - v, 0);
    const owed = bal.filter(([, v]) => v > 0).reduce((n, [, v]) => n + v, 0);
    const online = nearby().filter((p) => p.presence === "online");
    return subScreen("Trip money", `
      <div class="large-title" style="margin-top:8px"><h1 class="t-hero">Trip money</h1>
        <p class="t-sub" style="margin-top:6px">Split costs with no signal. Settle up through UPI when you're back online.</p></div>
      <div class="tiles">
        <div class="tile-stat"><span class="t-cap">You owe</span><b style="color:var(--ember)">${RUPEE(owe)}</b></div>
        <div class="tile-stat"><span class="t-cap">You're owed</span><b style="color:var(--pine)">${RUPEE(owed)}</b></div>
      </div>
      <div class="group-label t-over">Settle up</div>
      <div class="group">${bal.length ? bal.map(([id, v]) => {
        const p = P[id];
        const iOwe = v < 0;
        return `<div class="set" style="flex-wrap:wrap">${avatar(p.avatar, id, 40, p.presence)}
          <span class="main"><span class="t-strong" style="display:block">${nameWithId(p)}</span><span class="t-cap">${iOwe ? "You owe " + RUPEE(-v) : "Owes you " + RUPEE(v)}</span></span>
          <button class="btn small ${iOwe ? "" : "secondary"}" data-act="${iOwe ? "pay-open" : "pay-request"}" data-v="${id}">${iOwe ? "Pay " + RUPEE(-v) : "Request"}</button>
          ${S.payOpen === id ? `<div class="pay-options">
            <button class="btn small" data-act="pay-upi" data-v="${id}">Pay with UPI app</button>
            <button class="btn small secondary" data-act="pay-cash" data-v="${id}">Paid in cash</button>
            <p class="t-cap">UPI needs your phone's own internet. With no signal, use UPI Lite X tap-to-pay in your bank app, or pay cash and mark it here.</p></div>` : ""}</div>`;
      }).join("") : '<div class="set"><span class="t-sub">All settled up 🎉</span></div>'}</div>

      <div class="group-label t-over">Add an expense</div>
      <form class="group spec" id="expense-form">
        <div style="display:grid;grid-template-columns:1fr 2fr;gap:10px">
          <div class="field"><label class="t-over" for="ex-amt">Amount ₹</label><input id="ex-amt" inputmode="numeric" placeholder="300"></div>
          <div class="field"><label class="t-over" for="ex-note">For</label><input id="ex-note" maxlength="40" placeholder="Snacks"></div>
        </div>
        <div><span class="t-over">Split equally with</span><div class="filters" style="margin:8px 0 0;padding:0">${online.length ? online.map((p) =>
          `<button type="button" class="chip" data-act="ex-toggle" data-v="${p.id}" aria-pressed="${(S.exSplit || []).includes(p.id)}">${p.avatar} ${nameWithId(p)}</button>`).join("") : '<span class="t-cap">Switch on the mesh to split with people nearby.</span>'}</div></div>
        <button class="btn" type="submit">Add and tell them</button>
      </form>

      <div class="group-label t-over">Expenses</div>
      <div class="group">${S.ledger.slice().reverse().map((e) => `<div class="set">
        <span class="main"><span class="t-strong" style="display:block">${esc(e.note)} ${e.example ? '<span class="tag">EXAMPLE</span>' : ""}</span>
        <span class="t-cap">${nameOf(e.payer)} paid · split ${e.split.length} ways · ${RUPEE(e.amount / e.split.length)} each</span></span>
        <span class="t-strong num">${RUPEE(e.amount)}</span></div>`).join("")}</div>

      <div class="group-label t-over">How payments work without internet</div>
      <div class="group">
        ${[["var(--pine)", I.mesh, "A shared record, not a wallet", "BlueMob never holds money. It keeps a record of who paid what, shared with the people involved."],
           ["var(--sky)", I.phone, "Pay through your UPI app", "When you're online, one tap opens GPay, PhonePe or BHIM with the amount and person filled in."],
           ["var(--ember)", I.flash, "No signal? Tap to pay", "UPI Lite X in bank apps can pay phone-to-phone over NFC, fully offline, for small amounts."],
           ["#7C6BD6", I.globe, "Requests travel by bridge", "Ask someone far away to pay you. The request reaches them as soon as anyone nearby has internet."]]
          .map(([c, ic, a, b]) => `<div class="set"><span class="tile" style="background:${c}">${ic}</span><span class="main"><span class="t-strong" style="display:block">${a}</span><span class="t-cap">${b}</span></span></div>`).join("")}
      </div>`);
  }

  // ---------- games: catalogue, lobby, invites ----------
  const GAMES = [
    { id: "ttt", name: "Tic-tac-toe", em: "⭕", players: "2 players", play: true, grad: "linear-gradient(150deg,#11694E,#0B3D2E)" },
    { id: "c4", name: "Connect 4", em: "🔴", players: "2 players", play: true, grad: "linear-gradient(150deg,#C2621A,#7A3A0E)" },
    { id: "quiz", name: "Survival quiz", em: "🧠", players: "2+ players", play: true, grad: "linear-gradient(150deg,#2F7BC0,#173E5C)" },
    { id: "words", name: "Word chain", em: "🔤", players: "2+ players", play: true, grad: "linear-gradient(150deg,#7C6BD6,#43348F)" },
    { id: "hunt", name: "Treasure hunt", em: "🗺️", players: "2+ · outdoors", play: true, grad: "linear-gradient(150deg,#8A5A44,#4A2C1F)" },
    { id: "chess", name: "Chess", em: "♟️", players: "2 players" },
    { id: "checkers", name: "Checkers", em: "⚫", players: "2 players" },
    { id: "ludo", name: "Ludo", em: "🎲", players: "2–4 players" },
    { id: "wolf", name: "Werewolf", em: "🐺", players: "5+ players" },
    { id: "twenty", name: "20 questions", em: "❓", players: "2+ players" },
    { id: "draw", name: "Draw and guess", em: "🎨", players: "3+ · Wi-Fi link" },
    { id: "flag", name: "Capture the flag", em: "🏳️", players: "Teams · outdoors" },
  ];
  const gameOf = (id) => GAMES.find((g) => g.id === id);
  // The computer: always available, for when no one nearby wants to play.
  P.cpu = { id: "cpu", name: "Computer", avatar: "🤖", uid: "00000000000000C0", presence: "online", met: false, cpu: true, dist: 0, bearing: 0, link: "this phone", rtt: 0 };
  S.muteInvites = store.get("muteInvites", false);
  S.lobby = null;
  S.invite = null;
  // How simulated nearby people answer an invite.
  function inviteAnswer(pid, game) {
    if (pid === "asha2") return { state: "muted" };
    if (pid === "meera") return { state: "busy", say: "Busy bridging everyone's messages. Maybe later!" };
    if (pid === "asha") return ["quiz", "words", "twenty"].includes(game) ? { state: "suggest", suggest: "c4", say: "Not this one. Connect 4?" } : { state: "joined" };
    if (pid === "ravi") return ["quiz", "words", "twenty", "wolf", "chess"].includes(game) ? { state: "joined" } : { state: "suggest", suggest: "quiz", say: "Not this. Survival quiz?" };
    return { state: "joined" };
  }
  function openLobby(game, preJoined) {
    const people = nearby().filter((p) => p.presence === "online");
    S.lobby = { game, responses: Object.fromEntries(people.map((p) => [p.id, { state: "asking" }])) };
    if (preJoined) S.lobby.responses[preJoined] = { state: "joined" };
    people.forEach((p, i) => {
      if (p.id === preJoined) return;
      later(900 + i * 700, () => {
        if (!S.lobby || S.lobby.game !== game) return;
        S.lobby.responses[p.id] = inviteAnswer(p.id, game);
        if (S.screen === "lobby") render();
      });
    });
    S.screen === "lobby" ? render() : open("lobby");
  }
  function gamesHubView() {
    return subScreen("Play", `
      <div class="large-title" style="margin-top:8px"><h1 class="t-hero">Play together</h1>
        <p class="t-sub" style="margin-top:6px">Pick a game and BlueMob asks people nearby if they'd like to join. They can say yes, suggest another game, or mute invites.</p></div>
      <div class="games-grid">${GAMES.map((g) => `<button class="game-tile press" data-act="lobby" data-v="${g.id}" style="${g.play ? `background:${g.grad};color:#fff` : ""}">
        <span class="em">${g.em}</span><b>${g.name}</b><span class="t-cap" style="color:inherit;opacity:.8">${g.players}</span>
        ${g.play ? "" : '<span class="tag">ANDROID APP</span>'}</button>`).join("")}</div>
      <div class="group" style="margin-top:20px"><div class="set"><span class="tile" style="background:var(--ink-3)">${I.game}</span>
        <span class="main"><span class="t-strong" style="display:block">Game invites from others</span><span class="t-cap">${S.muteInvites ? "Muted. Nobody can invite you." : "On. You'll see a banner when someone nearby invites you."}</span></span>
        <button class="switch" role="switch" aria-checked="${!S.muteInvites}" aria-label="Game invites" data-act="mute-invites"></button></div></div>
      <button class="btn text" style="margin:12px auto 0;display:flex" data-act="games">Which games work offline, and why?</button>`);
  }
  function lobbyView() {
    const g = gameOf(S.lobby.game);
    const R = S.lobby.responses;
    const ids = Object.keys(R);
    const joined = ids.filter((id) => R[id].state === "joined");
    const pill = (r) => ({
      asking: '<span class="tag">ASKING…</span>', joined: '<span class="tag pine">JOINED</span>', busy: '<span class="tag ember">BUSY</span>',
      muted: '<span class="tag">MUTED INVITES</span>', suggest: '<span class="tag sky">SUGGESTS ANOTHER</span>' }[r.state]);
    return subScreen(g.name, `
      <div class="intro" style="padding-top:6px"><span style="font-size:56px">${g.em}</span><h1 class="t-title">${g.name}</h1><span class="t-sub">${g.players}</span></div>
      <div class="group-label t-over">Asked ${ids.length} people nearby</div>
      <div class="group">${ids.length ? ids.map((id) => { const p = P[id], r = R[id]; return `<div class="set">${avatar(p.avatar, id, 40, p.presence)}
          <span class="main"><span class="t-strong" style="display:block">${nameWithId(p)}</span>
          <span class="t-cap">${r.say ? "“" + esc(r.say) + "”" : r.state === "muted" ? "Has game invites turned off" : r.state === "asking" ? "Invite sent over the mesh…" : "Ready to play"}</span></span>
          ${r.state === "suggest" ? `<button class="btn small secondary" data-act="lobby-switch" data-v="${r.suggest}" data-p="${id}">Play ${gameOf(r.suggest).name}</button>` : pill(r)}</div>`; }).join("")
        : '<div class="set"><span class="t-sub">No one online nearby. Switch the mesh on and wait for people to appear.</span></div>'}</div>
      <div style="display:flex;flex-direction:column;align-items:center;gap:10px;margin-top:20px">
        ${g.play ? `<button class="btn" data-act="lobby-start" ${joined.length ? "" : "disabled"}>${joined.length ? "Start with " + joined.map((id) => esc(P[id].name)).join(", ") : ids.length ? "Waiting for someone to join…" : "No one nearby yet"}</button>
          <button class="btn ${joined.length ? "text" : "secondary"}" data-act="lobby-cpu">🤖 Play against the computer</button>`
          : '<p class="t-sub" style="text-align:center;max-width:32ch">This game is coming in the Android app. Try one of the coloured games for now.</p>'}
      </div>`);
  }
  function startGame(game, opp) {
    S.lobby = null;
    // Leave the lobby behind, so "back" from the game returns to where the invite started.
    if (S.screen === "lobby") Object.assign(S, S.stack.pop() || { screen: "main" });
    if (game === "ttt") { Object.assign(G, { board: Array(9).fill(""), turn: "x", over: null, line: null, last: -1, opp }); open("ttt"); }
    if (game === "quiz") { Object.assign(Q, { i: 0, you: 0, them: 0, picked: null, theirPick: null, done: false, opp }); open("quiz"); }
    if (game === "c4") { Object.assign(C4, { b: Array(42).fill(""), turn: "x", over: null, line: [], last: -1, opp }); open("c4"); }
    if (game === "words") { Object.assign(W, { words: [], over: null, opp, msg: "" }); open("words"); }
    if (game === "hunt") { huntNew(opp); open("hunt"); }
  }
  // Someone nearby invites you, unless invites are muted.
  function maybeInvite() {
    if (S.muteInvites || S.invite || !S.mesh || P.ravi.presence !== "online" || !S.onboarded) return;
    S.invite = { from: "ravi", game: "quiz", choosing: false };
    refresh();
  }
  function inviteBanner() {
    const iv = S.invite;
    if (!iv || S.screen !== "main") return "";
    const p = P[iv.from], g = gameOf(iv.game);
    return `<div class="invite" role="alert">
      <div class="row" style="padding:0;gap:12px">${avatar(p.avatar, p.id, 40, p.presence)}
        <span class="main"><span class="t-strong" style="display:block">${esc(p.name)} invites you to ${g.em} ${g.name}</span><span class="t-cap">${fmtDist(p.dist)} away · nearby</span></span></div>
      ${iv.choosing ? `<div class="filters" style="margin:10px 0 0;padding:0">${GAMES.filter((x) => x.play && x.id !== iv.game).map((x) =>
          `<button class="chip" data-act="invite-suggest" data-v="${x.id}">${x.em} ${x.name}</button>`).join("")}</div>`
        : `<div class="invite-actions"><button class="btn small" data-act="invite-join">Join</button>
          <button class="btn small secondary" data-act="invite-other">Not this one</button>
          <button class="btn small secondary" data-act="invite-mute">Mute invites</button></div>`}
    </div>`;
  }

  // Connect 4
  const C4 = { b: Array(42).fill(""), turn: "x", over: null, line: [], you: 0, them: 0, opp: "asha", last: -1 };
  const c4At = (b, r, c) => (r < 0 || r > 5 || c < 0 || c > 6 ? null : b[r * 7 + c]);
  function c4Drop(b, col, mark) { for (let r = 5; r >= 0; r--) if (!b[r * 7 + col]) { b[r * 7 + col] = mark; return r * 7 + col; } return -1; }
  function c4Win(b) {
    for (let r = 0; r < 6; r++) for (let c = 0; c < 7; c++) {
      const m = b[r * 7 + c]; if (!m) continue;
      for (const [dr, dc] of [[0, 1], [1, 0], [1, 1], [1, -1]]) {
        const cells = [0, 1, 2, 3].map((k) => [r + dr * k, c + dc * k]);
        if (cells.every(([rr, cc]) => c4At(b, rr, cc) === m)) return { who: m, line: cells.map(([rr, cc]) => rr * 7 + cc) };
      }
    }
    return b.every(Boolean) ? { who: "draw", line: [] } : null;
  }
  function c4Ai(b) {
    const cols = [3, 2, 4, 1, 5, 0, 6].filter((c) => !b[c]);
    for (const mark of ["o", "x"]) for (const c of cols) { const t = b.slice(); c4Drop(t, c, mark); if (c4Win(t)?.who === mark) return c; }
    const safe = cols.filter((c) => { const t = b.slice(); c4Drop(t, c, "o"); return !cols.some((c2) => { const u = t.slice(); return c4Drop(u, c2, "x") >= 0 && c4Win(u)?.who === "x"; }); });
    const pool = safe.length ? safe : cols;
    return Math.random() < .6 ? pool[0] : pool[Math.floor(Math.random() * pool.length)];
  }
  function c4View() {
    const opp = P[C4.opp];
    const status = C4.over ? (C4.over === "x" ? "You win! 🎉" : C4.over === "o" ? opp.name + " wins" : "It's a draw") : C4.turn === "x" ? "Your move: tap a column" : opp.name + " is thinking…";
    return subScreen("Connect 4", `
      ${vsHeader(opp, C4.you, C4.them, "Green", "Orange")}
      <p class="t-head" style="text-align:center;margin:8px 0 14px" aria-live="polite">${status}</p>
      <div class="c4" role="grid" aria-label="Connect 4 board">${C4.b.map((v, i) => `<button class="c4-cell ${v} ${C4.line.includes(i) ? "win" : ""} ${i === C4.last ? "drop" : ""}"
        data-act="c4" data-v="${i % 7}" ${C4.over || C4.turn !== "x" || C4.b[i % 7] ? "disabled" : ""} aria-label="Column ${(i % 7) + 1}"><i></i></button>`).join("")}</div>
      <div style="display:flex;justify-content:center;margin-top:18px">${C4.over ? '<button class="btn" data-act="c4-new">Play again</button>' : ""}</div>
      <p class="t-cap" style="text-align:center;margin-top:14px">${opp.cpu ? "Playing against the computer, right on this phone." : "Web preview: " + esc(opp.name) + " is simulated."}</p>`);
  }
  function c4Play(col) {
    if (C4.over || C4.turn !== "x") return;
    const at = c4Drop(C4.b, col, "x"); if (at < 0) return;
    C4.last = at; STATS.sent++; STATS.hourNow++;
    const w = c4Win(C4.b);
    if (w) { C4.over = w.who; C4.line = w.line; if (w.who === "x") C4.you++; render(); return; }
    C4.turn = "o"; render();
    later(800 + Math.random() * 600, () => {
      if (S.screen !== "c4" || C4.over) return;
      C4.last = c4Drop(C4.b, c4Ai(C4.b), "o"); C4.turn = "x";
      const w2 = c4Win(C4.b);
      if (w2) { C4.over = w2.who; C4.line = w2.line; if (w2.who === "o") C4.them++; }
      render();
    });
  }
  function vsHeader(opp, a, b, la = "", lb = "") {
    return `<div class="vs" style="margin:14px 0 4px">
      <div style="text-align:center">${avatar(S.avatar, "me", 48)}<div class="t-cap" style="margin-top:4px">You${la ? " · " + la : ""}</div></div>
      <div class="score num">${a} – ${b}</div>
      <div style="text-align:center">${avatar(opp.avatar, opp.id, 48, opp.presence)}<div class="t-cap" style="margin-top:4px">${esc(opp.name)}${lb ? " · " + lb : ""}</div></div></div>`;
  }

  // Word chain: each word starts with the last letter of the one before. Nature words only.
  const NATURE = ("acorn alder algae alpine amber ant antelope apple ash aspen aster aurora avalanche badger bamboo bark basil bay beach bear beaver bee beech beetle berry birch bison blossom boulder bramble brook buffalo bush butterfly cactus camel canyon cardinal cave cedar cheetah cherry chestnut cliff cloud clover cobra coconut condor coral cougar coyote crane creek crocodile crow cypress daisy deer delta desert dew dolphin dove dragonfly dune eagle earth eel egret elephant elk elm ember estuary falcon fern ferret field finch fir firefly fjord flamingo flower fog forest fox frog frost gale gazelle gecko geyser ginger giraffe glacier goat goose gorge grass grove gull hail hare hawk hazel heather hedgehog heron hill hippo holly honey horizon hornet ibex ibis iceberg iguana iris island ivy jackal jaguar jasmine jay jungle juniper kangaroo kelp kestrel kingfisher kite kiwi koala lagoon lake larch lark laurel lava leaf lemon leopard lichen lily lime lion lizard llama lotus lynx magnolia mango mangrove maple marsh meadow mist mole monsoon moose moss moth mountain mushroom nectar nest nettle newt nightingale oak oasis ocean octopus olive orchid osprey otter owl palm panda panther parrot peak pebble pelican pine plateau plum pond poplar poppy prairie puffin python quail quartz rabbit rain rainbow raven reed reef river robin rock rose sage salmon sand savanna seal sequoia shark shell shore sky sloth snail snow sparrow spruce squirrel stone stork stream sun swan swamp thistle thunder tide tiger toad tortoise tree tulip tundra turtle valley violet volcano vulture walnut walrus wasp waterfall wave whale wheat willow wind wolf woodpecker wren yak yew zebra").split(" ");
  const W = { words: [], over: null, opp: "ravi", msg: "" };
  function wordsView() {
    const opp = P[W.opp];
    const last = W.words.at(-1);
    const need = last ? last.w.slice(-1).toUpperCase() : null;
    return subScreen("Word chain", `
      ${vsHeader(opp, W.words.filter((x) => x.by === "me").length, W.words.filter((x) => x.by !== "me").length)}
      <p class="t-sub" style="text-align:center;margin:6px 0 14px">Nature words only. Each word starts with the last letter of the one before.</p>
      <div class="chain">${W.words.length ? W.words.map((x) => `<span class="link ${x.by === "me" ? "mine" : ""}">${esc(x.w)}</span>`).join('<span class="t-cap">→</span>') : '<span class="t-sub">You start. Any nature word.</span>'}</div>
      ${W.over ? `<div style="text-align:center;margin-top:18px"><h2 class="t-title">${W.over === "me" ? "You win! 🎉" : esc(opp.name) + " wins"}</h2>
          <p class="t-sub" style="margin-top:4px">${esc(W.msg)}</p><button class="btn" style="margin-top:14px" data-act="words-new">Play again</button></div>`
        : W.turn === "them" ? `<p class="t-head" style="text-align:center;margin-top:18px">${esc(opp.name)} is thinking…</p>`
        : `<form class="composer" id="word-form" style="margin-top:18px">
            <div class="field-pill"><input id="word-in" autocomplete="off" placeholder="${need ? "A word starting with " + need : "e.g. river"}" aria-label="Your word"></div>
            <button class="send" type="submit" aria-label="Play word">${I.send}</button></form>
          ${W.msg ? `<p class="t-cap" style="text-align:center;margin-top:8px;color:var(--rose)">${esc(W.msg)}</p>` : ""}
          <div style="text-align:center;margin-top:10px"><button class="btn text" data-act="words-giveup">I give up</button></div>`}`);
  }
  function wordsPlay(raw) {
    const w = raw.trim().toLowerCase();
    const last = W.words.at(-1);
    if (!/^[a-z]{2,}$/.test(w)) { W.msg = "One word, letters only."; render(); return; }
    if (last && w[0] !== last.w.slice(-1)) { W.msg = `It has to start with "${last.w.slice(-1).toUpperCase()}".`; render(); return; }
    if (W.words.some((x) => x.w === w)) { W.msg = "That word was already used."; render(); return; }
    if (!NATURE.includes(w)) { W.msg = `"${w}" isn't in the nature word list. Try another.`; render(); return; }
    W.msg = ""; W.words.push({ w, by: "me" }); W.turn = "them"; STATS.sent++; STATS.hourNow++; render();
    later(1100 + Math.random() * 900, () => {
      if (S.screen !== "words" || W.over) return;
      const used = new Set(W.words.map((x) => x.w));
      const options = NATURE.filter((x) => x[0] === w.slice(-1) && !used.has(x));
      if (!options.length || (W.words.length > 8 && Math.random() < .25)) {
        W.over = "me"; W.msg = `${P[W.opp].name} couldn't think of a word starting with "${w.slice(-1).toUpperCase()}".`;
      } else W.words.push({ w: options[Math.floor(Math.random() * options.length)], by: W.opp });
      W.turn = "me"; render();
    });
  }

  // Treasure hunt: someone hid a spot nearby; follow "warmer / colder" using the compass.
  const H = {};
  function huntNew(opp) {
    const d = 120 + Math.random() * 100, b = Math.random() * 360;
    Object.assign(H, { opp, tx: d * Math.sin((b * Math.PI) / 180), ty: d * Math.cos((b * Math.PI) / 180), x: 0, y: 0, heading: 0, prev: d, hint: "Start walking. I'll tell you if you're getting warmer.", found: false, steps: 0 });
  }
  const huntDist = () => Math.hypot(H.tx - H.x, H.ty - H.y);
  function huntView() {
    const opp = P[H.opp] || P.asha;
    const d = huntDist();
    const heat = Math.max(0, Math.min(1, 1 - d / 240));
    const band = d < 15 ? "Found it!" : d < 40 ? "Very close" : d < 90 ? "Getting close" : d < 160 ? "Warm-ish" : "Far";
    return subScreen("Treasure hunt", `
      <div class="intro" style="padding-top:6px"><span style="font-size:52px">${H.found ? "🎁" : "🗺️"}</span>
        <h1 class="t-title">${H.found ? "You found it! 🎉" : esc(opp.name) + " hid a treasure"}</h1>
        <span class="t-sub">${H.found ? `In ${H.steps} moves. ${esc(opp.name)} has been notified.` : "Somewhere within about 250 m. Walk and listen for warmer or colder."}</span></div>
      <div class="group spec">
        <div class="spec-row"><span class="t-strong">${band}</span><span class="t-cap">${H.found ? "" : "Heading " + Math.round(H.heading) + "° " + cardinal(H.heading)}</span></div>
        <div class="heat" role="meter" aria-label="How close you are" aria-valuenow="${Math.round(heat * 100)}" aria-valuemin="0" aria-valuemax="100"><span style="width:${Math.max(4, heat * 100)}%"></span></div>
        <p class="t-head" style="text-align:center" aria-live="polite">${esc(H.hint)}</p>
      </div>
      ${H.found ? '<div style="display:flex;justify-content:center;margin-top:18px"><button class="btn" data-act="hunt-new">Hunt again</button></div>' : `
      <div class="turn" style="margin-top:18px">
        <button class="btn small secondary" data-act="hunt-turn" data-v="-45">↺ Turn</button>
        <button class="btn" data-act="hunt-walk">Walk 20 m</button>
        <button class="btn small secondary" data-act="hunt-turn" data-v="45">Turn ↻</button></div>`}
      <p class="t-cap" style="text-align:center;margin-top:16px">Outdoors, the real compass and GPS drive this game. Web preview: your walking is simulated.</p>`);
  }
  function huntWalk() {
    const a = (H.heading * Math.PI) / 180;
    H.x += 20 * Math.sin(a); H.y += 20 * Math.cos(a); H.steps++;
    const d = huntDist();
    if (d < 15) { H.found = true; H.hint = "Treasure found!"; STATS.sent++; }
    else H.hint = d < H.prev - 2 ? (d < 50 ? "Hot! 🔥 Almost there" : "Warmer 🌤️") : d > H.prev + 2 ? "Colder 🧊 Try turning" : "About the same";
    H.prev = d; render();
  }
