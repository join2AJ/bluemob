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
node --test server/relay.test.js server/live.test.js
```

## Deploy (once, by the BlueMob team, not by users)

The relay address is **built into the app**: users never type it. Deploy the relay once, then put its address in
`gradle.properties` (`relayUrl=https://…`) and build; every phone then connects to it automatically.

**Easiest, free: Render** (no command line):
1. Sign in at https://render.com with the GitHub account that has this repository.
2. **New → Blueprint**, pick this repository. Render reads `render.yaml` and creates `bluemob-relay`.
3. When it's live, copy its address (like `https://bluemob-relay.onrender.com`) and check `…/health` shows `{"ok":true…}`.

The free plan sleeps after 15 minutes without traffic (the first sync after that takes up to a minute) and doesn't keep
files across restarts, so messages waiting on the relay can be lost when it restarts. That's fine for testing; for real
use pick a paid plan with a disk mounted at `/data`, or:

**Google Cloud Run** (`asia-south1` is Mumbai):

```
cd server
gcloud run deploy bluemob-relay --source . --region asia-south1 --allow-unauthenticated
```

Fly.io and Railway also work with the `Dockerfile`; mount a volume at `/data`.
Anyone can still point a phone at a different relay: **You → Internet bridge → Use a different relay (advanced)**.

## API

| Call | What it does |
|---|---|
| `POST /v1/push {packets}` | Stores signed messages (`rmsg`), receipts (`rrcpt`) and ratings (`rate`). Bad signatures are refused. A delivery receipt deletes the message it confirms. |
| `POST /v1/pull {ids, since, proof}` | Packets for up to 50 IDs, after each ID's cursor. `proof` is a signature over `bluemob-pull|at|ids` (at most 5 minutes old); pulling also registers your key. |
| `GET /v1/key?id=` | A device's public key, so others can encrypt to it. |
| `GET /v1/ratings?subject=` | Signed ratings about a device. |
| `GET /v1/live` (WebSocket) | Real-time link for calls (`live.js`). The phone signs in by signing a challenge with its device key; then call set-up (the app's signed packets) and voice/video frames (`[8-byte ID | data]`) pass between two signed-in phones. Nothing is stored. |
| `GET /health` | Status. |

Limits: 600 requests per device per hour, 256 KB per request, messages kept at most 7 days. Live link: 64 KB per
frame, 512 KB/s per phone, and frames are dropped (not queued) when the receiving phone falls behind.

**Internet calls** use the live link: about 64 kbit/s each way for voice, more with video. On Render's free plan the
relay sleeps after 15 minutes with no one connected; phones keep it awake while they're signed in.

## Before a real launch

- Swap the append-only file for Postgres or Firestore once traffic grows; the `Store` class is the only part to change.
- Put it behind HTTPS only, and add monitoring and alerts.
- India's DPDP Act 2023: publish a privacy notice; the relay holds no names or numbers, but IDs and timing are metadata.

## Optional settings (0.13)

| Environment variable | What it does |
|---|---|
| `FCM_SERVICE_ACCOUNT` | Firebase service account JSON. Turns on wake-ups: calls ring and messages arrive on phones whose BlueMob is closed. See `docs/firebase.md`. |
| `PERSISTENT_DISK=1` | Set when `DATA_DIR` is on a disk that survives restarts (Render: a paid plan with a disk mounted at `/data`). Phones then show BlueMob Cloud backups as kept, not temporary. |

Files sent in chats to people who aren't nearby are held under `DATA_DIR/blobs` (encrypted on the phone, deleted once
downloaded, 7 days at most). Cloud backups are under `DATA_DIR/backups` (encrypted with the user's password, newest 3).
