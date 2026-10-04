  const AVATARS = ["🌿", "🦅", "🌊", "⛰️", "🌻", "🦋", "🐬", "🔥", "🌙", "🍀", "🐺", "🌵"];
  const TINTS = ["#CFEBDC", "#D6E6F5", "#F8E2C4", "#F5D5D2", "#E0D8F4", "#CDECE8"];
  const tint = (seed) => TINTS[Math.abs([...seed].reduce((h, c) => (h * 31 + c.charCodeAt(0)) | 0, 7)) % TINTS.length];
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // ---------- storage (best effort) ----------
  const store = {
    get(k, d) { try { const v = localStorage.getItem("bluemob:" + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem("bluemob:" + k, JSON.stringify(v)); } catch { /* storage unavailable */ } },
  };

  // ---------- state ----------
  const now = () => Date.now();
  const S = {
    name: store.get("name", ""),
    avatar: store.get("avatar", "🦅"),
    onboarded: store.get("onboarded", false),
    shareLoc: store.get("shareLoc", false),
    slide: 0,
    tab: store.get("tab", "radar"),
    chat: null,
    mesh: false,
    showLog: false,
    log: [],
    convos: {},
    timers: [],
  };

  // Simulated people. In the Android app these come from real Bluetooth / Wi-Fi discovery.
  const PEOPLE = {
    asha:  { name: "Asha",  avatar: "🦋", uid: "7C21A9F04B3E5D18", dist: 42,   bearing: 60,  link: "fast Wi-Fi link", rtt: 18 },
    ravi:  { name: "Ravi",  avatar: "🐬", uid: "2F8E61C0D93A47B5", dist: 380,  bearing: 200, link: "Bluetooth link", rtt: 64 },
    meera: { name: "Meera", avatar: "🌵", uid: "B05D3E9A16F7C842", dist: 920,  bearing: 310, link: "good link", rtt: 41, internet: true },
    asha2: { name: "Asha",  avatar: "🌙", uid: "E4B09D2C71F3A856", dist: 1240, bearing: 20,  link: "Bluetooth link", rtt: 72 },
    tara:  { name: "Tara",  avatar: "🦉", uid: "3D6F0A82C5E1B947", dist: 640,  bearing: 285, link: "Bluetooth link", rtt: 58 },
    kabir: { name: "Kabir", avatar: "🐺", uid: "91AC5F2E08D4B763", dist: 1800, bearing: 130, link: "", rtt: 0, home: "Pune, about 1,000 km away" },
    dee:   { name: "Dee",   avatar: "🌻", uid: "5E7240BB9C1DA3F6", remote: true },
  };
  const P = {};
  for (const [id, p] of Object.entries(PEOPLE)) {
    P[id] = { id, ...p, presence: "offline", met: false, lastSeen: 0 };
  }
  // Kabir was met on an earlier trip; Dee is a friend far away, reachable only through the internet.
  Object.assign(P.kabir, { met: true, lastSeen: now() - 2 * 3600e3 });
  Object.assign(P.tara, { met: true, lastSeen: now() - 5 * 3600e3 });
  Object.assign(P.dee, { met: true, presence: "remote" });

  const SKY = { id: "sky", name: "Sky", avatar: "🌤️" };

  // ---------- Sky, the practice bot (same rules as the Android app) ----------
  const SKY_GREETING = [
    "Hey there! 👋 I'm Sky, your BlueMob buddy.",
    "I live inside this app on your phone. I don't use the internet, so I work with zero signal and nothing you ask me leaves your phone.\n\nAsk me how to use BlueMob, what's happening around you, or any survival question. Or tap a suggestion below.",
  ];
  const SKY_SUGGEST = ["Hi 👋", "How does BlueMob work?", "What can I do here?", "Tell me a joke"];
  const SKY_CHIPS = ["Who is nearby?", "Is there a bridge?", "How do I send an SOS?", "What do the ticks mean?", "How do I make water safe?", "What do I do for a burn?", "Where do you live?", "Tell me a joke"];
  const SKY_RULES = [
    [["hello", "hi", "hey", "hii", "namaste", "hola", "yo"], [
      "Hi! 😊 Great to hear from you. This is exactly how it feels when a friend nearby messages you, no towers needed.",
      "Hello hello! 🌿 You just sent a message the BlueMob way. With a real person it would hop over Bluetooth or Wi-Fi.",
      "Hey! 👋 How's your day out there?"]],
    [["how are you", "how r u", "wassup", "what's up", "sup"], [
      "I'm doing great, feeling free as the wind 🌬️. How about you?",
      "All good here! Just floating around in your phone ☁️. What about you?"]],
    [["good", "fine", "great", "awesome", "nice", "cool"], [
      "Love that! 🌻",
      "Awesome 🙌. Want to know how BlueMob reaches people without signal? Just ask \"how does it work\"."]],
    [["how does bluemob", "how bluemob works", "how it works", "how does it work", "how does this work", "how does the app", "mesh network", "without towers"], [
      "Here's the magic ✨: phones running BlueMob find each other over Bluetooth and Wi-Fi and link up directly. No SIM, no towers, no internet.\n\nEvery phone can pass messages along, so a message can hop A → B → C to reach someone out of your range. And if one phone nearby has internet, it can carry the group's messages to the wider world. Try messaging Dee once Meera is online to watch it happen!"]],
    [["what can", "feature", "help", "do here", "options"], [
      "Here's what you can do:\n📡 Radar: see who's around, how far and when they were last online\n💬 Chats: message anyone nearby\n🌍 Reach Dee, 2,000 km away, through a friend's internet\n📍 Share your location so friends see the distance\n🙋 Your profile: pick a name and an avatar"]],
    [["joke", "funny", "laugh"], [
      "Why did the phone go to the mountains? To get away from all the towers 🏔️😄",
      "I told my Wi-Fi a joke… it didn't get the connection 😅",
      "What do you call a group of phones with no signal? A BlueMob! 📱📱📱"]],
    [["who are you", "your name", "are you real", "bot", "robot", "human"], [
      "I'm Sky 🌤️, a little practice bot built into BlueMob. I'm not a real person, but I chat like one so you can get comfy before your friends join."]],
    [["location", "distance", "far", "radar", "gps"], [
      "The Radar tab shows everyone around you 📡. Turn on \"Share my location\" to see exactly how far apart you are. GPS works without internet!"]],
    [["call", "voice", "video"], ["Voice and video calls are coming soon 📞. They'll go straight over Wi-Fi between nearby phones."]],
    [["safe", "private", "privacy", "secure", "encrypt"], [
      "Privacy matters 🔒. Messages go directly phone to phone. End-to-end encryption is planned, so even phones relaying your message won't be able to read it."]],
    [["thank", "thx", "ty"], ["Anytime! 💚", "You're welcome! Happy exploring 🌍"]],
    [["bye", "see you", "goodbye", "good night", "gn"], ["Bye for now! 👋 I'll be right here whenever you want to chat.", "See you soon, explorer! 🌙"]],
    [["sad", "lonely", "alone", "bored"], ["You're not alone, I'm here 🤗. And once someone nearby opens BlueMob, they'll pop up on your Radar."]],
  ];
  const SKY_FALLBACK = [
    "I'm best at survival questions. Try \"How do I make water safe?\", \"What do I do for a burn?\" or \"How do I find north?\" 🌿",
    "Got it! 👍 Ask me anything about first aid, water, fire, shelter or finding your way. My answers come from the survival guide on your phone.",
  ];
  let turn = 0;
  const matches = (text, key) => key.length <= 3
    ? new RegExp("(^|\\W)" + key.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "(\\W|$)").test(text)
    : text.includes(key);
  function pickReply(rules, fallback, input) {
    const text = input.toLowerCase();
    turn++;
    const hit = rules.find(([keys]) => keys.some((k) => matches(text, k)));
    const options = hit ? hit[1] : fallback;
    return options[turn % options.length];
  }

  // Simulated friends answer with their own personality.
  const FRIEND = {
    asha: { rules: [[["hi", "hello", "hey"], ["Hey! 🦋 I can see you on my radar, about 40 m away!", "Hiii 👋 Wi-Fi link between us is super fast"]],
                    [["where", "location"], ["By the river, near the big rock 🪨"]]],
            fallback: ["Haha nice 😄", "Sounds good! Meet at camp at 6?", "Zero signal here but this works perfectly 🙌"] },
    ravi: { rules: [[["hi", "hello", "hey"], ["Hi! 🐬 Only Bluetooth between us, a bit slower but it works"]],
                    [["where", "location"], ["Up the trail, maybe 400 m from you"]]],
            fallback: ["👍", "Cool cool", "Can you pass a message to Asha? She's out of my range"] },
    tara: { rules: [[["hi", "hello", "hey"], ["Hi! 🦉 Just walked back into range and your message popped up. No signal up there at all"]]],
            fallback: ["Got it now! I was out of range on the ridge 😅", "Back at camp, see you soon"] },
    kabir: { rules: [[["hi", "hello", "hey"], ["Kabir here! 🐺 I'm back home in Pune, 1,000 km away, and your message still found me. No numbers needed 😄"]],
                     [["trip", "photos", "pics"], ["Best trip ever. Let's plan the next one!"]]],
             fallback: ["Got it! Reached me at home 🏠", "Haha, miss the mountains already", "Talk soon 👋"] },
    experts: { rules: [], fallback: ["Thanks. A volunteer from the expert network has your question and will reply here. If it's urgent, send an SOS from the Guide tab."] },
    asha2: { rules: [[["hi", "hello", "hey"], ["Hi! 🌙 Different Asha here, from the other camp. Our IDs keep us apart 😄"]]],
             fallback: ["Nice to meet you out here!", "👍", "Signal is zero here too"] },
    meera: { rules: [[["hi", "hello", "hey"], ["Hello! 🌵 I have one bar of internet up here, so I'm bridging messages out for everyone"]],
                     [["internet", "bridge"], ["Yep, my phone carries the group's messages to the internet and back 🌐"]]],
             fallback: ["Got it 🌵", "Haha true", "Let me know if you need to reach anyone outside, I'm your bridge"] },
    dee: { rules: [[["hi", "hello", "hey"], ["Wow, this reached me from the mountains? 😮 2,000 km away and it came through!"]],
                   [["safe", "ok", "fine"], ["So glad you're safe! ❤️ Say hi to everyone there"]]],
           fallback: ["Got it loud and clear from here 🌍", "Amazing that this works with no signal on your side!", "Message received 👍 Take care out there"] },
  };

  // ---------- conversations ----------
  const uid = () => Math.random().toString(36).slice(2, 10);
  function convo(id) {
    if (!S.convos[id]) S.convos[id] = { messages: [], unread: 0, typing: false, queue: [] };
    return S.convos[id];
  }
  convo("sky").messages = SKY_GREETING.map((t) => ({ id: uid(), me: false, text: t, time: now() }));
  convo("sky").unread = 1;

  // Live counters for the insights screen.
  const STATS = { sent: 0, delivered: 0, direct: 0, hopped: 0, bridge: 0, relayed: 0, hourNow: 0 };
  const later = (ms, fn) => { const t = setTimeout(fn, ms); S.timers.push(t); return t; };

  function incoming(id, text, actions) {
    const c = convo(id);
    c.messages.push({ id: uid(), me: false, text, time: now(), actions });
    if (S.chat !== id) c.unread++;
    refresh();
  }

  /** msg is a string or { text, actions }. Shows typing first, like a real person. */
  function reply(id, msg, opts = {}) {
    const { text, actions } = typeof msg === "string" ? { text: msg } : msg;
    const c = convo(id);
    later(opts.wait ?? 500, () => {
      c.typing = true; refresh();
      later(Math.min(700 + text.length * 14, 2800), () => {
        c.typing = false;
        incoming(id, text, actions);
      });
    });
  }
  const friendReply = (id, text) => (FRIEND[id] ? pickReply(FRIEND[id].rules, FRIEND[id].fallback, text) : null);

  // =====================================================================
  //  Delivery engine: store-and-forward over two paths, exactly-once delivery
  // ---------------------------------------------------------------------
  //  Every message has one unique ID. It can travel two ways:
  //   • direct:   Bluetooth / Wi-Fi, the moment the person is in range
  //   • internet: through a bridge (someone nearby with internet) to the relay,
  //               which hands it over once the person is reachable online
  //  The first path to deliver wins. The delivery receipt cancels the other path,
  //  the relay deletes its stored copy, and if a copy still arrives the receiver
  //  recognises the ID and discards it. So a message is shown exactly once.
  // =====================================================================
  const hasInternet = (p) => p.remote || !!p.home;   // far-away people are online where they are
  const PATH_LABEL = { direct: "Bluetooth / Wi-Fi", internet: "Internet" };

  function send(id, text, opts = {}) {
    text = text.trim();
    if (!text) return;
    const c = convo(id);
    const m = { id: "m-" + uid() + uid().slice(0, 4), me: true, text, time: now(), status: "sending", to: id,
      onDelivered: opts.onDelivered, receipts: [], paths: {}, events: [] };
    c.messages.push(m);
    STATS.sent++; STATS.hourNow++;
    refresh();

    if (id === "sky") {
      // Sky runs inside the app: nothing is sent anywhere, so no network ticks or receipts.
      m.status = "local";
      reply("sky", skyReply(text), { wait: 500 });
      return m;
    }
    const p = P[id];
    if (!p.remote) m.paths.direct = { state: p.presence === "online" ? "trying" : "waiting" };
    m.paths.internet = { state: "waiting" };
    note(m, "Written on your phone · ID " + m.id);
    if (p.presence === "online" && !p.remote) {
      m.paths.internet.state = "not-needed";
      deliverDirect(m, id);
    } else {
      m.status = "pending";
      note(m, p.remote ? "Waiting for a bridge to the internet" : `${p.name} isn't in range. Waiting for whichever comes first: ${p.name} in range, or a bridge`);
      if (bridgeOnline()) sendViaBridge(m, id); else { convo(id).queue.push(m); refresh(); }
    }
    return m;
  }
  function note(m, text) { m.events.push({ time: now(), text }); }

  function deliverDirect(m, id) {
    const p = P[id];
    m.paths.direct.state = "trying";
    Object.assign(m, { route: ["You", p.name], hop: 0, hopTimes: [now()] });
    note(m, `${p.name} is in range. Sending directly over ${p.link}`);
    later(150 + p.rtt, () => { if (m.status === "sending" || m.status === "pending") m.status = "sent"; refresh(); });
    later(400 + p.rtt * 3, () => { m.hop = 1; m.hopTimes.push(now()); delivered(m, id, "direct", p.link); });
  }

  /** Called when a copy reaches the person. Only the first copy counts. */
  function delivered(m, id, path, how) {
    const p = P[id];
    if (m.deliveredVia) {
      m.paths[path].state = "discarded";
      STATS.dupes = (STATS.dupes || 0) + 1;
      note(m, `A second copy arrived by ${PATH_LABEL[path]}. ${p ? p.name + "'s" : "Their"} phone recognised the ID and discarded it`);
      log("Duplicate of a message to " + (p ? p.name : id) + " discarded by ID");
      refresh();
      return;
    }
    m.deliveredVia = path;
    m.paths[path].state = "delivered";
    m.paths[path].time = now();
    m.status = "delivered";
    STATS.delivered++;
    if (path === "direct") STATS.direct++; else { STATS.bridge++; if (m.route && m.route.includes("Ravi")) STATS.hopped++; }
    const back = path === "direct" ? how : "the internet and the bridge";
    m.receipts.push({ kind: "delivered", time: now(), via: back });
    note(m, `Delivered by ${path === "direct" ? how : p && p.sms ? "text message" : "internet"}. Delivery receipt came back over ${back}`);
    // The receipt stops the other path.
    const other = path === "direct" ? "internet" : "direct";
    const o = m.paths[other];
    if (o) {
      if (o.state === "waiting") { o.state = "cancelled"; note(m, `${PATH_LABEL[other]} copy cancelled before it was sent`); }
      else if (o.state === "relay") { o.state = "cancelled"; note(m, "The relay deleted its stored copy after the delivery receipt"); }
      else if (o.state === "moving") note(m, `An internet copy is still travelling. If it arrives, it will be discarded`);
    }
    convo(id).queue = convo(id).queue.filter((x) => x !== m);
    if (m.onDelivered) m.onDelivered();
    refresh();
    // Read receipt: SMS has none; people read it a little later.
    if (id !== "sky" && !(p && p.sms)) later(1800 + Math.random() * 2500, () => {
      m.status = "read"; m.receipts.push({ kind: "read", time: now(), via: back }); note(m, "Read receipt received"); refresh();
    });
    if (id === "sky") later(600, () => { m.status = "read"; m.receipts.push({ kind: "read", time: now(), via: "this phone" }); refresh(); });
    const r = id !== "sky" && friendReply(id, m.text);
    if (r && Math.random() < 0.9) reply(id, r, { wait: 2200 });
  }

  // A → B → C (has internet) → internet → relay → D, played out hop by hop.
  const bridgeOnline = () => S.mesh && P.meera.presence === "online";
  function routeFor(p) {
    const hops = ["You"];
    if (P.ravi.presence === "online") hops.push("Ravi");
    hops.push("Meera 🌐", "Internet");
    if (p.sms) hops.push("SMS");
    hops.push(p.name);
    return hops;
  }
  function sendViaBridge(m, id) {
    const p = P[id];
    if (m.deliveredVia) { if (m.paths.internet.state === "waiting") m.paths.internet.state = "cancelled"; return; }
    if (!bridgeOnline()) { if (!convo(id).queue.includes(m)) convo(id).queue.push(m); m.paths.internet.state = "waiting"; refresh(); return; }
    m.paths.internet.state = "moving";
    if (m.status === "sending" || m.status === "pending") m.status = "sent";
    // Keep the direct route on screen if it already won; otherwise show the internet route.
    if (!m.route || m.route.length < 3) Object.assign(m, { route: routeFor(p), hop: 0, hopTimes: [now()] });
    note(m, "A bridge is nearby (Meera has internet). Internet copy on its way");
    refresh();
    const step = () => {
      m.hop++;
      m.hopTimes.push(now());
      const arrived = m.route[m.hop] === "Internet";
      if (arrived && !hasInternet(p)) {
        // The person has no internet either: the relay keeps it until they can be reached.
        if (m.deliveredVia) { m.paths.internet.state = "cancelled"; note(m, "Reached the relay after delivery. The relay dropped it"); refresh(); return; }
        m.paths.internet.state = "relay";
        m.status = "pending";
        note(m, `Stored safely at the relay. ${p.name} has no internet either, so it waits there`);
        refresh();
        return;
      }
      if (m.hop >= m.route.length - 1) { delivered(m, id, "internet", p.sms ? "text message" : "internet"); return; }
      refresh();
      later(900, step);
    };
    later(700, step);
  }
  function flushBridgeQueue() {
    let i = 0;
    for (const [id, c] of Object.entries(S.convos)) {
      c.queue.splice(0).forEach((m) => later(600 + i++ * 450, () => sendViaBridge(m, id)));
    }
    flushSos();
  }
  /** Someone came into range: hand over everything waiting for them, directly. */
  function flushDirect(id) {
    const c = S.convos[id];
    if (!c) return;
    c.messages.filter((m) => m.me && !m.deliveredVia && m.paths && m.paths.direct && m.paths.direct.state === "waiting")
      .forEach((m, i) => later(500 + i * 300, () => { if (!m.deliveredVia) deliverDirect(m, id); }));
  }

  // ---------- mesh simulation ----------
  function log(text) {
    const t = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
    S.log.unshift(t + "  " + text);
    S.log = S.log.slice(0, 120);
  }
  function setPresence(id, presence) {
    const p = P[id];
    p.presence = presence;
    p.met = true;
    p.lastSeen = now();
    if (presence === "online") {
      log("Connected to " + p.name + " (" + p.link + ")");
      flushDirect(id);
      if (id === "meera") { log("Meera has internet: she can bridge messages out"); flushBridgeQueue(); }
    } else if (presence === "range") {
      log("Found " + p.name);
    }
    refresh();
  }
  function startMesh() {
    if (S.mesh) return;
    S.mesh = true;
    log("Mesh on: visible to nearby phones, scanning…");
    later(1200, () => setPresence("asha", "range"));
    later(2300, () => setPresence("asha", "online"));
    later(3000, () => setPresence("ravi", "range"));
    later(4400, () => setPresence("ravi", "online"));
    later(5600, () => setPresence("meera", "range"));
    later(7200, () => setPresence("meera", "online"));
    later(9800, () => setPresence("asha2", "range"));
    later(11400, () => setPresence("asha2", "online"));
    later(16000, () => maybeInvite());
    later(48000, () => { if (!P.ravi.sos) receiveSos("ravi", "Twisted my ankle near the stream. Can't walk. Please bring a torch"); });
    later(30000, () => setPresence("tara", "range"));
    later(31500, () => setPresence("tara", "online"));
    later(9000, () => { if (S.mesh && convo("asha").messages.length === 0) incoming("asha", "Hey! Saw you pop up on my radar 👋"); });
    refresh();
  }
  function stopMesh() {
    if (!S.mesh) return;
    S.mesh = false;
    S.timers.forEach(clearTimeout);
    S.timers = [];
    for (const p of Object.values(P)) {
      if (p.presence === "online" || p.presence === "range") { p.presence = "offline"; p.lastSeen = now(); }
    }
    for (const c of Object.values(S.convos)) c.typing = false;
    log("Mesh off");
    refresh();
  }

  // People drift a little, so distances feel alive.
  setInterval(() => {
    if (!S.mesh) return;
    for (const id of ["asha", "ravi", "meera", "asha2", "tara"]) {
      const p = P[id];
      if (p.presence !== "online") continue;
      p.dist = Math.max(8, p.dist * (0.97 + Math.random() * 0.06));
      p.bearing = (p.bearing + (Math.random() - 0.5) * 6 + 360) % 360;
    }
    if (S.tab === "radar" && S.screen === "main" && S.onboarded) { placeBlips(); updatePeopleText(); }
  }, 4000);

