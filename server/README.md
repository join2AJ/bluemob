# BlueMob relay

The internet bridge for BlueMob. When any phone has internet, it uploads messages it's sending or carrying, and
downloads messages for itself and for the phones around it. That's how two people who met over Bluetooth can keep
talking from 1,000 km apart.

**What the relay can see:** encrypted messages, the ID each one is for, public keys, and star ratings (which are public
by design). **What it can't do:** read messages (they're end-to-end encrypted on the phones), or forge or change
anything (every packet is signed by the sending phone, and an ID is the hash of its key). There are no accounts and no
phone numbers.

## Run it

Node 18 or newer, no dependencies:

```
node server/relay.js            # listens on :8080, stores data in server/data/relay.jsonl
PORT=9000 DATA_DIR=/var/lib/bluemob node server/relay.js
node --test server/relay.test.js
```

## Deploy

Any host that runs a container and gives it HTTPS works. For example, Google Cloud Run:

```
cd server
gcloud run deploy bluemob-relay --source . --region asia-south1 --allow-unauthenticated
```

Or Fly.io, Render or Railway, using the `Dockerfile`. Mount a volume at `/data` so stored messages survive restarts.
Then, on each phone: **You → Internet bridge → Relay address**, and enter the `https://` address.

## API

| Call | What it does |
|---|---|
| `POST /v1/push {packets}` | Stores signed messages (`rmsg`), receipts (`rrcpt`) and ratings (`rate`). Bad signatures are refused. A delivery receipt deletes the message it confirms. |
| `POST /v1/pull {ids, since, proof}` | Packets for up to 50 IDs, after each ID's cursor. `proof` is a signature over `bluemob-pull|at|ids` (at most 5 minutes old); pulling also registers your key. |
| `GET /v1/key?id=` | A device's public key, so others can encrypt to it. |
| `GET /v1/ratings?subject=` | Signed ratings about a device. |
| `GET /health` | Status. |

Limits: 600 requests per device per hour, 256 KB per request, messages kept at most 7 days.

## Before a real launch

- Swap the append-only file for Postgres or Firestore once traffic grows; the `Store` class is the only part to change.
- Put it behind HTTPS only, and add monitoring and alerts.
- India's DPDP Act 2023: publish a privacy notice; the relay holds no names or numbers, but IDs and timing are metadata.
