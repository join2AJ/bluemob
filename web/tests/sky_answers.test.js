// Checks that Sky answers 26 typical questions correctly. Run: node web/tests/sky_answers.test.js (needs Playwright).
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addLocatorHandler(p.locator('.sos-alert'), async () => { await p.click('[data-act=sos-dismiss]'); });
  // Wraps the preview in a full HTML page, as the artifact host does.
  const fs = require('fs'), path = require('path'), os = require('os');
  const page = path.join(os.tmpdir(), 'bluemob-preview.html');
  fs.writeFileSync(page, '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}</style>' + fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8'));
  await p.goto('file://' + page);
  await p.click('[data-act=skip]'); await p.fill('#ob-name', 'Arjun'); await p.click('[data-act=finish]');
  await p.waitForTimeout(8500);
  await p.click('[data-act=tab][data-v=chats]'); await p.click('.row[data-id=sky]');
  // [question, text the right answer must contain]
  const cases = [
    ["Who is nearby?", "online near you"], ["Is there a bridge?", "bridge"], ["How do I send an SOS?", "red SOS button"],
    ["What do the ticks mean?", "dotted circle"], ["How do I make water safe?", "Making water safe"], ["What do I do for a burn?", "Burns"],
    ["Where do you live?", "inside the BlueMob app"], ["Tell me a joke", ""], ["is this stream water safe to drink", "Making water safe"],
    ["how do i boil water", "Making water safe"], ["where can I find water", "Finding water"], ["how do I start a fire", "Lighting a fire"],
    ["how do I build a shelter", "Emergency shelter"], ["I'm lost", "If you are lost"], ["how do I find north", "Find north"],
    ["make a shadow stick", "Find north"], ["someone is not breathing", "CPR"], ["my friend is bleeding a lot", "Severe bleeding"],
    ["how does bluemob work", "Here's the magic"], ["how do I split money", "Trip money"], ["how do I walk to my friend", "Compass tab"],
    ["what should I do in an earthquake", "Earthquake"], ["lightning storm coming", "Lightning"], ["snake bit me", "Snake"],
    ["my friend got stung by a scorpion", "I don't have a guide"], ["how do I fix a car engine?", "I don't have a guide"],
  ];
  let fail = 0;
  for (const [q, want] of cases) {
    const n = await p.$$eval('.msg.them', (x) => x.length);
    await p.fill('#draft', q); await p.click('#send-btn');
    await p.waitForFunction((n) => document.querySelectorAll('.msg.them').length > n, n, { timeout: 8000 });
    const a = await p.$$eval('.msg.them .bubble', (x) => x.at(-1).textContent);
    const ok = !want || a.includes(want);
    if (!ok) fail++;
    console.log((ok ? "PASS " : "FAIL ") + q + "  →  " + a.slice(0, 70).replace(/\n/g, " "));
  }
  console.log(`\n${cases.length - fail}/${cases.length} passed · page errors: ${JSON.stringify(errs)}`);
  await b.close();
  process.exit(fail ? 1 : 0);
})();
