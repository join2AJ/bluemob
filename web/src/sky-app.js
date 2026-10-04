
  // =====================================================================
  //  Sky knows the app: how to use each feature, plus live insights read
  //  from this phone's own state. Everything runs inside BlueMob on the
  //  phone. Nothing Sky is asked leaves the device.
  // =====================================================================
  const go = (tab, label) => ({ label, act: "go-tab", v: tab });
  const SKY_APP = [
    { keys: ["where do you live", "are you online", "do you need internet", "are you on the internet", "is sky online", "where is my data", "privacy", "who can see", "are you ai", "are you chatgpt", "server"],
      text: "I live inside the BlueMob app on your phone. I'm not on the internet and I don't call any server, so I work with zero signal, and nothing you ask me leaves your phone.\n\nI know the survival guide and how every part of BlueMob works, and I can read what's happening on your phone right now: who's nearby, whether there's a bridge, your battery and waiting messages." },
    { keys: ["send sos", "send an sos", "use sos", "sos button", "how does sos", "sos work", "ask for help", "call for help"],
      text: "Tap the red SOS button at the top of any tab, then tap the big SOS circle twice (twice so it can't go off by accident).\n\nYou can add a note like \"Injured\" or \"Lost\", but you don't have to. It reaches everyone nearby straight away, and texts your SOS contacts as soon as someone nearby has internet.",
      actions: [{ label: "Open SOS", act: "sos" }, { label: "SOS contacts", act: "contacts" }] },
    { keys: ["sos signal", "flashlight", "torch", "siren", "change signal", "default signal", "flash light", "sound signal"],
      text: "The SOS signal blinks ··· ––– ··· using your screen, your flashlight, a loud whistle-pitch sound, or all three. Pick one each time, or set your default in SOS → Default signal.",
      actions: [{ label: "Open SOS signal", act: "sos-light" }] },
    { keys: ["receive an sos", "get an sos", "someone sends sos", "someone sends an sos", "when i get an sos"],
      text: "When someone nearby sends an SOS, BlueMob opens a full-screen alert with who it is, how far away they are and their message. Tap \"I'm coming\" so they know, \"Show me the way\" to follow the compass to them, or \"How to help\" for the guide. Their SOS is also passed on to anyone with internet, automatically.",
      actions: [{ label: "How to help guide", act: "open-article", v: "help-sos" }] },
    { keys: ["what is a bridge", "what's a bridge", "whats a bridge", "bridge mean", "explain bridge"],
      text: "A bridge is any phone nearby that has internet. BlueMob uses it to carry everyone's messages out to the wider world, and replies back in. You don't need to do anything: when a bridge appears, waiting messages go out on their own." },
    { keys: ["tick", "receipt", "read receipt", "delivered mean", "circles", "was it delivered", "did they get", "did it reach"],
      text: "Under each message you send:\n• dotted circle: sending\n• clock: waiting (out of range, no bridge yet)\n• one circle: sent\n• two circles: delivered\n• two filled circles: read\n\nTap any message you sent to see its receipts, which path it took and every hop with times." },
    { keys: ["out of range", "not in range", "message waiting", "why waiting", "stuck", "not delivered", "pending", "store and forward"],
      text: "You can message anyone, even if they're out of range. The message waits on your phone and goes the first way possible: directly over Bluetooth or Wi-Fi when they come into range, or through the internet once someone nearby is a bridge.\n\nIt's delivered exactly once: the other copy is cancelled or discarded by its message ID." },
    { keys: ["after the trip", "stay in touch", "without number", "keep in touch", "without exchanging", "far away friend"],
      text: "Everyone you meet stays in your Chats. After the trip, even 1,000 km apart, your messages reach them by their BlueMob ID through the internet. No phone numbers are shared." },
    { keys: ["loved ones", "message family", "message my mom", "message my mother", "message home", "add contact", "phone number", "sms", "text message"],
      text: "Go to You → Loved ones and add a name and phone number. They don't need BlueMob: your message reaches them as a normal text message once someone nearby has internet. Tick \"SOS\" to have them texted when you send an SOS.",
      actions: [{ label: "Open Loved ones", act: "contacts" }] },
    { keys: ["split", "money", "upi", "expense", "owe", "payment", "pay someone"],
      text: "Trip money lets you split costs with no signal. Add an expense and choose who to split it with; they're told over the mesh. Settle later with your UPI app when you're online, or pay cash and mark it as paid. BlueMob never holds money.",
      actions: [{ label: "Open Trip money", act: "money" }] },
    { keys: ["mute", "stop invites", "turn off invites", "no invites", "game invites"],
      text: "To stop game invites, turn off You → Game invites, or tap \"Mute invites\" on any invite banner.",
      actions: [go("you", "Open You")] },
    { keys: ["game", "play", "bored"],
      text: "Pick a game and BlueMob asks people nearby. They can join, suggest another game, or mute invites. Playable now: Tic-tac-toe, Connect 4, Survival quiz, Word chain and Treasure hunt.",
      actions: [{ label: "Open games", act: "play" }] },
    { keys: ["navigate to", "walk to", "find my friend", "compass tab", "use the compass", "my trail", "save this spot", "waypoint", "back to camp", "find camp"],
      text: "The Compass tab works offline: pick a target (camp, a saved spot or a friend who's online) and the arrow shows the way, with distance and walking time. \"Save this spot\" remembers where you are, and your trail shows where you've walked.",
      actions: [go("compass", "Open Compass")] },
    { keys: ["battery saver", "save battery", "survival power", "battery last", "low battery", "power mode"],
      text: "Turn on Survival power for dark screens, slower scanning and no animations. Then follow the two steps there to put the rest of the phone into Battery Saver while BlueMob keeps running.",
      actions: [{ label: "Open Power", act: "power" }] },
    { keys: ["share my location", "share location", "distance to", "how far is", "by distance"],
      text: "Turn on You → Share my location (or By distance on the Nearby tab). People you're connected to then see how far away you are, and you see them. It uses GPS, which needs no internet." },
    { keys: ["radar", "find people", "see people", "nearby tab"],
      text: "The Nearby tab's radar shows everyone around you: green = online, orange = in range, grey = seen before. Filter by Online, Has internet or Under 500 m.",
      actions: [go("radar", "Open Nearby")] },
    { keys: ["survival guide", "guide tab", "offline guide", "first aid guide"],
      text: "The Guide tab has short survival guides stored on your phone: first aid, water, fire, shelter, navigation, signals, weather and disasters. Or just ask me, like \"what do I do for a burn?\"",
      actions: [go("guide", "Open Guide")] },
    { keys: ["insights", "statistics", "stats", "analytics"],
      text: "Mesh insights shows how many messages you sent and delivered, how they travelled (direct, hopped or through a bridge), and link strength.",
      actions: [{ label: "Open insights", act: "insights" }] },
  ];

  // Live answers, read from the app's own state on this phone.
  const SKY_LIVE = [
    { keys: ["who is nearby", "who's nearby", "whos nearby", "who is around", "anyone nearby", "who's online", "who is online", "anyone around"],
      answer: () => {
        const on = nearby().filter((p) => p.presence === "online");
        if (!S.mesh) return { text: "The mesh is off, so I can't see anyone. Switch it on in the Nearby tab.", actions: [go("radar", "Open Nearby")] };
        if (!on.length) return "No one is connected yet. People appear as they open BlueMob near you.";
        return { text: `${on.length} ${on.length === 1 ? "person is" : "people are"} online near you:\n` +
          on.map((p) => `• ${p.name}${dupName(p) ? " " + shortId(p.uid) : ""}: ${S.shareLoc ? fmtDist(p.dist) + " away, " : ""}${p.link}${p.internet ? ", has internet" : ""}`).join("\n"),
          actions: [go("radar", "Show radar")] };
      } },
    { keys: ["is there a bridge", "any bridge", "bridge available", "is there internet", "anyone with internet", "can i reach", "internet nearby", "any network"],
      answer: () => bridgeOnline()
        ? "Yes. Meera is a bridge right now: she has internet, so your messages to faraway people go out straight away."
        : "Not right now. No one nearby has internet. Messages to faraway people wait safely and go out as soon as a bridge appears." },
    { keys: ["my id", "what is my id", "bluemob id", "my number"],
      answer: () => `Your BlueMob ID is BM · ${fmtId(MY_ID)}. It was given to this phone automatically and no other phone has it. People see it next to your name, ${S.name}.` },
    { keys: ["battery", "how much power", "charge"],
      answer: () => ({ text: `Battery ${S.batteryPct}%. That's about ${hoursLeft(S.survival)} hours ${S.survival ? "in Survival power" : "at normal use, or about " + hoursLeft(true) + " hours with Survival power on"}.`, actions: [{ label: "Open Power", act: "power" }] }) },
    { keys: ["my messages", "how many messages", "waiting messages", "undelivered", "unread"],
      answer: () => {
        const mine = Object.entries(S.convos).filter(([id]) => id !== "sky").flatMap(([id, c]) => c.messages.filter((m) => m.me).map((m) => ({ ...m, id })));
        const waiting = mine.filter((m) => m.status === "pending" || m.status === "sent" || m.status === "sending");
        const unread = Object.values(S.convos).reduce((n, c) => n + c.unread, 0);
        return `You've sent ${mine.length} message${mine.length === 1 ? "" : "s"} in this session. ${waiting.length ? waiting.length + " still on the way or waiting for someone to come in range or a bridge." : "All of them have been delivered."} ${unread ? "You have " + unread + " unread." : ""}`.trim();
      } },
    { keys: ["is my sos", "sos status", "did my sos"],
      answer: () => S.sos ? `Your SOS is active since ${clock(S.sos.time)}. It reached ${S.sos.near.length} people nearby. ${S.sos.contacts.filter((c) => c.status === "sent").length} of ${S.sos.contacts.length} SOS contacts have been texted.` : "You haven't sent an SOS. If you need help, tap the red SOS button at the top of any tab." },
  ];

  // Keys match at the start of a word, so "tick" doesn't fire inside "stick".
  const atWord = (t, k) => new RegExp("(^|[^a-z])" + k.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).test(t);
  function skyAppAnswer(t) {
    for (const r of SKY_LIVE) if (r.keys.some((k) => atWord(t, k))) return r.answer();
    for (const r of SKY_APP) if (r.keys.some((k) => atWord(t, k))) return { text: r.text, actions: r.actions };
    return null;
  }
