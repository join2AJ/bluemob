# Plan: kilometres of range with a LoRa radio (later)

**Not built yet.** This is the plan for when field days show that phone-to-phone range is the bottleneck.

## Why

Bluetooth and Wi-Fi Direct reach tens of metres. A small LoRa radio (Meshtastic-compatible, about ₹1,500–2,500)
reaches 2–5 km on a trail and further from a ridge, using very little battery, and other radios repeat messages.

## How it would fit

- The phone pairs with the radio over Bluetooth LE, using the open Meshtastic protocol.
- BlueMob treats the radio as one more path, next to Bluetooth, Wi-Fi and the internet: the message router hands a
  packet to whichever paths are up; receipts come back the same way. Nothing else in the app changes.
- LoRa carries little data (a few hundred bytes a packet, a few packets a minute), so only small things go over it:
  SOS (already compact and signed), roll calls and their answers, check-ins, short texts, positions. No photos, voice
  or calls.
- Packets stay end-to-end encrypted and signed by BlueMob, so radios carry them without being able to read them.

## Steps

1. Field day results show low delivery when members spread out (docs/field-day.md).
2. Buy 3 radios; prototype the BLE link and send an SOS radio-to-radio.
3. Add the radio as a path in the router, behind a switch in `Features` until it's proven.
4. Field day again, with one radio per 5–10 people.
