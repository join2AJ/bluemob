# Field day: testing BlueMob with a real group

Before any feature work, find out what really happens on 20–30 phones out in the open. Everything below runs from the
app (You → Diagnostics → Field test, or a trip group → Field test with this group); no laptop needed on the day.

## Where and who

- One trek day (4–6 h on a trail) **or** one mela/stadium day (crowd, jammed network). Ideally both, on separate days.
- 20–30 people, mixed phones on purpose: Xiaomi/Redmi, Realme/Oppo, Vivo, Samsung, Motorola, at least one 3 GB RAM
  phone. Write each person's phone model next to their name.
- Two people agree to stay 300–500 m apart for part of the day, so messages have to hop.

## Before setting off (15 minutes, everyone together)

1. Install the same BlueMob version on every phone. Sign in, allow Bluetooth, location and notifications.
2. One person: Nearby → Trip group → Start → name it → show the code. Everyone else: Scan the code.
3. Roll call from the trip group screen: every member should get a ✓. Fix anyone who doesn't before leaving.
4. Everyone: Field test → pick the trip group → roll call every 10 min → Start.
5. Do **not** change battery settings on half of the phones (the "as-is" group). On the other half, follow
   You → Connections & power (battery saver off, auto-start on). This shows how much the settings matter.

## During the day

- Phones in pockets or bags, screens off, used normally (photos, WhatsApp) — like a real trip.
- Note on paper anything odd: "lost signal at the bridge", "phone overheated", with the time.

## At the end

On every phone: Field test → Stop the test → Share results (into one WhatsApp group). Use "Share raw data" for the
phones with bad numbers.

## What each phone reports

| Measure | Good | Worry |
|---|---|---|
| Delivered (roll calls that reached each member) | 90% or more | under 60% |
| Time to reach (typical) | under 30 s | over 5 min |
| Paths | some "1 phone" or "2 phones" when the group spread out | only "Direct": hopping never happened |
| Battery per hour (off the charger) | under 4% | over 8% |
| Stopped by phone | 0 | any; note the brand and whether its settings were fixed |
| Phones connected | 3+ on average | 0–1 most of the time |

## What to decide afterwards

- **Stops by brand:** which makers kill BlueMob even after the settings fix. Those need brand-specific steps in
  Connections & power, or a warning at sign-up.
- **Battery:** if over 8%/h, slow the scanning when nothing has changed for a while, before adding any feature.
- **Hops:** if messages rarely go through other phones, range and connection limits are the bottleneck, and that is
  the case for the LoRa radio plan (docs/lora.md).
