# BlueMob cryptography: design for review

This document describes exactly what the app does today (version 0.29), so a cryptographer can review it. Code
references point at `app/src/main/java/com/bluemob/app/`. Section 9 lists the weaknesses we already know about.
Section 10 lists the questions we want answered. Section 11 describes a possible move to Google Tink.

## 1. Goals and threat model

BlueMob carries messages phone to phone over Bluetooth and Wi-Fi (the "mesh"). Strangers' phones may relay them for
several hops, and the internet relay server may store and forward them.

**Must hold:**
- Only the two people in a chat (or the members of a group) can read its messages, files and calls.
- Relaying phones and the relay server can't read them.
- Nobody can forge an SOS, rescue-group message, rating or receipt in someone else's name.
- Data at rest on the phone can't be read by copying files off it.

**Out of scope:**
- A fully compromised (rooted) phone of a participant.
- Traffic analysis: who talks to whom and when. The relay sees IDs and timing.
- Anonymity: an SOS deliberately shows a name and position.

## 2. Identity keys (`crypto/Crypto.kt`, `identity/Identity.kt`, `identity/Recovery.kt`)

- **Key pair:** each phone has one long-term **P-256 (secp256r1)** key pair, used for both ECDSA and ECDH.
- **Created from a short seed:** new accounts use a random **15-byte seed** (120 bits).
  The private scalar is `d = SHA-256("bluemob-identity-v1" || seed || counter)`, retried until `0 < d < n`.
- **Recovery code:** the seed, in base32 with a 2-byte checksum, so the identity can be moved to a new phone.
  Older accounts have a full 32-byte key instead.
- **BlueMob ID:** the first **8 bytes (64 bits)** of `SHA-256(SubjectPublicKeyInfo DER)`, shown as 16 hex characters.
  A key is accepted for an ID only if it hashes to it (`KeyBook`), so keys can be learned from anyone.
- **Storage:** the private key is wrapped with AES-256-GCM by a non-exportable Android Keystore key. If the Keystore
  is broken on a phone, it falls back to an app-private key, and this is shown on the audit screen.

## 3. Signed packets (`mesh/Envelope.kt`)

Every packet that matters is an envelope `{t, b, pk, s, h}`:

| Field | Meaning |
|---|---|
| `t` | Type: sos, lost, room, app, rmsg, rrcpt, rate… |
| `b` | Body: a JSON string that includes `from` (the sender's ID) |
| `pk` | The sender's public key (base64 DER) |
| `s` | ECDSA-SHA256 signature over `t + "\n" + b` |
| `h` | Hop count. **Not signed**: relaying phones increment it, so it can be changed in transit |

To open one, the receiver checks that `idFor(pk) == b.from` and that the signature is valid. Duplicates are dropped
using per-type "seen" sets of message IDs. Messages expire through a body field `x` (expiry time), which the server
and phones enforce.

## 4. Messages, end-to-end (`mesh/MeshRouter.kt`)

- **Key:** `k = HKDF-SHA256(salt = 32 zero bytes, ikm = ECDH(my private key, their public key), info = "bluemob-msg-v1|" + sorted(idA, idB))`.
  It's static: one key per pair of people, the same for every message between them.
- **Encryption:** `AES-256-GCM(k, nonce = 12 random bytes, aad = messageId)` over `{"text", "att"}`.
- **Sending:** the ciphertext goes inside a signed `rmsg` envelope, together with the recipient's ID and the expiry.
- **Receipts:** `rrcpt` envelopes ("delivered" or "read") are signed but not encrypted. They reveal only the message ID.
- **Groups:** each group message is sent as one ordinary pairwise message per member. Group details and replies
  travel inside the encrypted text, marked with `⁣bm1` followed by JSON.
- **Rescue groups:** room packets are signed but not encrypted, because anyone who heard the SOS may join.
  Since 0.26 they also travel inside a hidden, encrypted pairwise chat message.

## 5. Files (`files/Attachment.kt`, `files/FileShare.kt`, `bridge/RelayFiles.kt`)

Each file gets its own random 32-byte key and is encrypted with AES-256-GCM (random 12-byte IV). That key travels
inside the encrypted message (`att`). The relay stores only the encrypted file. Uploads are signed by the sender;
downloads are signed by the recipient. Files are deleted after download, or after 7 days.

## 6. Calls (`mesh/MediaRelay.kt` `CallCipher`)

- **Key:** `HMAC-SHA256(k, "bluemob-call-v1|" + callId)`, where `k` is the pairwise message key and `callId` is chosen
  by the caller.
- **Frames:** encrypted with AES-256-GCM. The nonce is the first 4 bytes of the sender's ID followed by a 64-bit
  per-call counter. AAD is the marker plus the media kind.
- **Replay (since 0.29):** the counter is sent in clear. Each phone keeps a 64-frame window per sender, like IPsec's: a frame
  plays only once and frames older than the window are dropped. Only a frame that decrypts moves the window.
- **Signalling** (invite, accept, end) uses signed `app` envelopes.

## 7. Data at rest

| Data | Protection |
|---|---|
| Database (messages, calls, trips, audit) | SQLCipher (AES-256) with a random 32-byte key, sealed by an Android Keystore key (`data/DbKey.kt`) |
| Settings and profile | `SecurePrefs`: each value is AES-256-GCM sealed by a Keystore key; names are hashed (SHA-256, truncated) |
| App PIN | PBKDF2-HMAC-SHA256, 120,000 rounds, random salt; only the hash is kept. Waits grow after wrong tries |
| Backups (file, Drive, BlueMob Cloud) | AES-256-GCM with a key from PBKDF2-HMAC-SHA256, 150,000 rounds, random salt, from the user's backup password |
| Audit trail | Hash chain: each entry hashes the previous one and is signed with the identity key; a saved checkpoint detects removal of the newest entries |

## 8. Relay server (`server/`)

The relay only ever sees ciphertexts, signed envelopes, public keys, number **fingerprints**, optional emails, push
tokens and timing.
- **Requests that change something** (keys, phone fingerprint, email, backups, account deletion, personal news) are
  signed by the phone over a labelled string with a timestamp, e.g. `bluemob-delete|at`, valid for ±10 minutes.
- **Admin endpoints** need a token, compared in constant time.
- **Live link** (calls, presence): a WebSocket. The phone signs in by signing a challenge from the server.

## 9. Known weaknesses and planned fixes

| # | Issue | Risk | Plan |
|---|---|---|---|
| W1 | **No forward secrecy.** The pairwise key is static, so whoever steals a private key can read all past and future messages between those two people (if they recorded them) | High for a messaging app | Move to a ratchet (Signal Double Ratchet via libsignal), or at least ephemeral keys per session |
| W2 | One key pair used for both ECDSA and ECDH | Low in practice; bad hygiene | Separate signing and agreement keys, both certified by the identity key |
| W3 | 64-bit IDs. Making another key with the *same* ID costs about 2^64 work: hard, but within reach of a very well-funded attacker | Medium for the long term | Show the full fingerprint or a safety number when verifying a contact; consider 96- or 128-bit IDs |
| W4 | Identity from a 120-bit seed (P-256 itself gives about 128 bits) | Low | Accept, or move to 16 bytes and a slightly longer recovery code |
| W5 | Random 96-bit GCM nonces under a static pairwise key | Low unless one pair sends billions of messages | Goes away with W1; or use a derived per-message key |
| W6 | Calls: ~~no replay window~~ (fixed in 0.29: 64-frame window); the caller chooses `callId` | Low | Derive the key from both sides' random values |
| W7 | Hop count `h` and rescue-room content aren't confidential; receipts are unencrypted | By design (relaying needs it) | Document it; keep it |
| W8 | ~~No safety-number check~~ (0.29: "Verify with a QR code" and a 60-digit safety number, section 12) | Was medium | Done; verification is optional |
| W9 | Our own HKDF code (simple, but home-made) | Low | Use a library implementation (Tink or BouncyCastle) |
| W10 | Keystore fallback stores the wrapping key in app-private storage | Low (only on broken phones; shown to the user) | Keep it; warn more clearly |

## 10. Questions for the reviewer

1. Is the envelope construction (signature over `type\nbody`, ID bound to the key) sound against cross-type replay
   and key substitution?
2. Is the HKDF use correct (zero salt, info with both sorted IDs), and is a static pairwise key acceptable for a first
   public release if W1 is on the roadmap?
3. Are the PBKDF2 rounds (120k PIN, 150k backups) enough on today's phones? Should we move to Argon2id or scrypt?
4. Is anything missing from the call encryption (key derivation, nonce layout, replay)?
5. Are the signed server requests (labels plus a ±10 minute timestamp) enough, or do they need nonces to stop replays
   inside that window?
6. What is the minimum to do before a public launch, and what can wait?

## 11. Option: moving to Google Tink

Tink is Google's cryptography library, built to be hard to misuse. Moving to it replaces our hand-built pieces with
reviewed ones.

| Today | With Tink |
|---|---|
| ECDH + our HKDF + AES-GCM per pair | **HPKE** (`HybridEncrypt`, DHKEM P-256 + HKDF + AES-256-GCM) per message: a fresh ephemeral key per message gives partial forward secrecy for the sender |
| ECDSA with JCA | `PublicKeySign` / `PublicKeyVerify` (ECDSA P-256, DER), with key management by Tink |
| AES-GCM for files and storage | `Aead` (AES256_GCM); `StreamingAead` for big files |
| Keystore wrapping, SecurePrefs | Tink's `AndroidKeysetManager` (keysets wrapped by an Android Keystore master key) |
| PBKDF2 for backups | Keep PBKDF2 (or Argon2 via a separate library); Tink has no password hashing |

**Effort:** about 2–3 weeks, including a version flag in envelopes so old and new phones work together during the
change. Tink adds about 1 MB to the app.

**Recommendation:** have an expert review this document first. For real forward secrecy (W1), libsignal (the Signal
Protocol) is the stronger long-term choice. Tink is the quicker step that removes our home-made code.

## 12. Verifying a contact (`crypto/SafetyNumber.kt`, since 0.29)

- **QR code:** `bluemob:verify:1:<ID>:<public key, base64 DER>`. Scanning checks that the key hashes to the ID and that
  the ID is the chat's; the key is learned if it wasn't known yet.
- **Safety number:** for each person, `SHA-512` iterated 5,200 times over `0x0001 || key || ID` (then `|| key` each
  round), the first 30 bytes as six 5-digit groups; the two halves sorted by ID. 60 digits, the same on both phones.
- **Stored:** `ID → first 8 bytes of SHA-256(key)`. If the key for that ID ever changes, the contact shows as not
  verified again.
