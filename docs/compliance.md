# BlueMob compliance audit: DPDP, GDPR, HIPAA, ISO 27001, ISO 22301, ISO 31000, NIST CSF

Last reviewed: 30 October 2026 (app 0.36, relay with admin sign-in and admin log).

This is a self-assessment of the app and its relay, written so a lawyer or an auditor can check it quickly. It is not a
certificate: ISO 27001 and ISO 22301 certification need an organisation running a management system and an external
audit. Legal points are what we understand of the law; have a lawyer confirm them before relying on them.

**Two questions:**
1. Is BlueMob, the service, run securely and lawfully? (DPDP, GDPR, HIPAA, ISO 27001, NIST CSF)
2. Can BlueMob help *other* organisations meet their duties? (ISO 22301, ISO 31000; a sales point for trek operators,
   schools, NGOs and companies)

---

## 0. What this audit found and fixed in 0.36

| # | Finding | Law or control | Fixed by |
|---|---|---|---|
| 1 | Trip groups (0.33–0.35) share roll-call answers, check-ins and "last heard" with the admin and captains, but members weren't told before joining | DPDP s.5 (notice before consent), GDPR Art. 13 | A "What the group sees" notice on the Join screen and in the passcode dialog, and in the group for members |
| 2 | Trip check-ins and field-test results were kept on phones forever | DPDP s.8(7) (erase when the purpose is served), GDPR Art. 5(1)(e) | When a trip ends, tracking stops and check-ins are erased 30 days later; field-test results 30 days after the test. The group and its chat stay (a purpose people choose: keeping in touch), until each person leaves |
| 3 | "Download my data" left out trip groups and field tests | DPDP s.11, GDPR Art. 15 and 20 | Export now has trip groups (role, plan, your check-ins) and the field-test summary |
| 4 | The privacy policy didn't mention trip groups, join codes, showing a group nearby, or the chat-background code | DPDP s.5, GDPR Art. 13 | Policy updated in English and Hindi, with retention |
| 5 | The 4-digit passcode was limited per phone only; many fake IDs could guess it | ISO 27001 8.5, NIST PR.AA | After 20 wrong passcodes in an hour from any phones, the group stops taking passcodes and tells the admin; turning it on again changes the passcode |
| 6 | One shared admin password, no second factor | ISO 27001 5.17, 8.5; NIST PR.AA | Optional TOTP code (`ADMIN_TOTP_SECRET`) and 8-hour sessions; with it set, the password alone opens nothing |
| 7 | No record of what admins looked at (screenshot and trip-code look-ups identify people) | DPDP Rules (logs kept a year), GDPR Art. 5(2) and 30, ISO 27001 8.15 | Admin log: every sign-in and admin action, allowed or refused, kept a year; "Admin log" tab |
| 8 | Chat showed "Today" above every message, whatever its date (a reported bug) | Integrity of records (ISO 27001 8.15) | Day labels: Today, Yesterday, weekday, or date |

**Still open (yours to decide or do):** sections 7 and 8.

---

## 1. Personal data BlueMob handles (record of processing, GDPR Art. 30)

| Data | Where | Why (purpose) | Lawful basis (GDPR / DPDP) | Kept |
|---|---|---|---|---|
| Name, emoji, BlueMob ID, public key | Phone; nearby phones; relay (key, ID) | Talking, delivery, encryption | Contract / consent at sign-up | Until account deleted |
| Mobile number | Phone; relay has only a scrambled fingerprint; Firebase at sign-up | Verify the person; find SOS contacts by number | Contract / consent | Until deleted |
| Date of birth, age | Phone | Age check (children's rules) | Legal obligation / consent | Until deleted |
| **Health: blood group, allergies, conditions, sex** | Phone; inside an SOS to phones nearby and SOS contacts; on the lock-screen card only if turned on (off by default) | Emergency care | **Explicit consent** (GDPR Art. 9(2)(a)); vital interests in an emergency (Art. 9(2)(c)) | Until deleted |
| Position | Phone; shared only while sharing is on, or in an SOS or rescue | Finding people | Consent; vital interests in an SOS | Trips until deleted by the user |
| Messages, files, calls | Phones; relay holds sealed copies until delivered | Communication | Contract | Relay: 7 days at most |
| Trip group: roll-call answers, check-ins (not positions), last heard | Phones of the group | Safety of a group trip | Consent (shown before joining) | Tracking stops when the trip ends; check-ins erased 30 days later |
| Trip group chat | Phones of the group | Keeping in touch | Consent (joining; anyone can leave) | Until each person leaves or deletes it |
| Join code: who made the group and the code, when, phone model, app version | Inside the code, sealed to the BlueMob admin key | Investigating misuse | Legitimate interests (GDPR Art. 6(1)(f)); DPDP s.7 "legitimate uses" for safety is arguable: get legal advice | As long as the code exists; looked up only on a report |
| Chat-background code (ID, hourly) | In screenshots | Tracing abusive screenshots | Legitimate interests; disclosed in the policy | Not stored |
| Reports and error reports | Relay | Fixing problems | Consent (automatic reports can be turned off) | Newest 500 |
| Admin log (sign-ins, look-ups, IP) | Relay | Accountability, detecting misuse | Legal obligation / legitimate interests | 1 year |
| Push token | Firebase | Waking the app | Contract | Until deleted |

**Processors:** Google Firebase (authentication, push), Render (hosting, Singapore), Hugging Face (download of the
offline AI model; nothing personal is sent).

---

## 2. India: DPDP Act 2023 and DPDP Rules 2025

The Rules were notified in November 2025. Most duties apply 18 months later (around May 2027). Check the final Rules
and any later changes with a lawyer.

| Requirement | Status |
|---|---|
| Notice in clear language, in English and Indian languages (s.5; Rules: notice must stand alone and list purposes) | **Done:** policy in English and Hindi; consent at sign-up with a link; trip-group notice before joining (0.36) |
| Consent: free, specific, withdrawable as easily as given (s.6) | **Done:** each optional thing has its own switch (location, background, error reports, emergency card, helper signals, fall detection, power-button SOS); leaving a trip group withdraws from it |
| Purpose limitation and minimisation (s.6, s.8) | **Done:** number kept only as a fingerprint; positions never leave the phone for check-ins; health only in an SOS |
| Erasure when the purpose is served (s.8(7); Rules: tell the person 48 h before erasing, for large platforms) | **Done:** relay messages 7 days; trip check-ins 30 days after the trip ends (the chat stays for keeping in touch, until people leave); field tests 30 days. *Gap:* the 48-hour warning applies only to classes of large platforms; not needed now |
| Security safeguards, including logs kept a year (s.8(5); Rules) | **Done:** encryption at rest and end to end; admin log kept a year (0.36). *Gap:* relay request logs at the host (Render) aren't kept a year; turn on log export, or keep them on your own server |
| Breach: tell the Board and each person without delay; detailed report to the Board within 72 hours (Rules) | **Done:** `docs/incident-response.md`, updated to the Rules |
| Rights: access, correction, erasure, grievance, nominee (s.11–14) | **Done:** Download my data, correction in Account, delete account, grievance officer. *Gap:* nominating someone to act after death or incapacity (s.14): add a "nominee" contact in Account |
| Children: verifiable consent of a parent for under-18s (s.9; Rules) | **Partly:** under 13 blocked; 13–17 need a parent's agreement in the app. *Gap:* the Rules expect verifiable consent (for example a parent who is a known adult, or a DigiLocker token). Decide before marketing to schools |
| Grievance officer, published contact | **Done** once `GRIEVANCE_OFFICER` and `CONTACT_EMAIL` are set on Render |
| Significant Data Fiduciary duties (DPO in India, audits, DPIA) | Not now: applies only if the government notifies BlueMob as one |
| Data outside India (s.16) | Allowed except to restricted countries. Relay in Singapore; moving to an Indian region is simpler for customers |

---

## 3. EU: GDPR (only if you offer BlueMob to people in the EU)

| Article | Status |
|---|---|
| Art. 5 principles, Art. 25 privacy by design | **Done:** end-to-end encryption, data on the phone, defaults off, retention limits |
| Art. 6 and 9 lawful basis, explicit consent for health | **Done:** see section 1. Health details are optional; the lock-screen card is off by default |
| Art. 12–22 rights | **Done:** access and portability (JSON export), correction, erasure, objection by turning features off |
| Art. 28 processors | *Gap:* keep signed data processing terms from Google (Firebase) and Render |
| Art. 30 record of processing | **Done:** section 1 of this document |
| Art. 32 security | **Done:** see ISO 27001 below |
| Art. 33–34 breach (72 h to the authority) | **Done:** incident procedure |
| Art. 35 impact assessment (health data; tracking in groups) | **Done (short form):** section 6 |
| Art. 27 EU representative | *Gap, only if you target the EU:* appoint one |
| Art. 44–49 transfers | *Gap, only for the EU:* Singapore has no adequacy decision; use standard contractual clauses with Render, or host in the EU |

---

## 4. US: HIPAA (and the FTC Health Breach Notification Rule)

**HIPAA doesn't apply to BlueMob today.** It covers health plans, clearing houses and healthcare providers that bill
electronically ("covered entities"), and their "business associates". BlueMob is a consumer app that people choose to
use; it doesn't handle health information for a provider or an insurer.

**When it would apply:** if a hospital, ambulance service or clinic used BlueMob to send patient information on
its behalf, BlueMob would become a business associate. It would then need a Business Associate Agreement and the
full HIPAA Security Rule. **Don't offer BlueMob to healthcare providers for patient data until that is in place.**

**What does apply in the US:** the FTC Health Breach Notification Rule covers non-HIPAA apps that hold health
information, such as blood group and conditions. After a breach of that data, the FTC and the affected people must be
told. The incident procedure covers it.

**HIPAA Security Rule safeguards already met in practice**, useful if a provider ever asks:

| Safeguard | BlueMob |
|---|---|
| Access control, unique user IDs | Key-based IDs; PIN and biometric lock |
| Audit controls | Tamper-evident audit trail on each phone; admin log on the relay |
| Integrity | Signed packets; hash-chained audit trail |
| Authentication | Phone OTP, device keys; admin: password plus TOTP code |
| Transmission security | End-to-end AES-GCM; HTTPS to the relay |
| Encryption at rest | Android Keystore-backed AES-GCM |

---

## 5. ISO/IEC 27001:2022 (Annex A)

| Control | Today | Gap |
|---|---|---|
| 5.1 Policies | Privacy policy; this document | A one-page information security policy and acceptable use |
| 5.9 Asset inventory | Code, relay, keys, Firebase, Render, Play Console | One register with owners (include `ADMIN_SEAL_KEY`, `ADMIN_TOTP_SECRET`, Play upload key) |
| 5.15–5.18 Access control | **0.36:** admin sign-in with TOTP and 8-hour sessions; every admin action logged | Separate accounts per admin (today one token); review the admin log monthly |
| 5.19–5.23 Suppliers | Firebase, Render, Hugging Face | Signed data processing terms on file |
| 5.24–5.28 Incidents | Incident procedure; alerts to `ALERT_WEBHOOK`; audit trails | One tabletop exercise a year |
| 5.29–5.30 Continuity | Mesh works without the relay; user backups | Relay data backup (`DATA_DIR`), second relay |
| 5.34 Privacy | Sections 1–3 | — |
| 8.5 Authentication | OTP, device keys, PIN/biometric, admin TOTP, passcode limits (0.36) | — |
| 8.8 Vulnerabilities | Pinned dependencies; tests | Dependency scanning in CI (OSV or Dependabot); yearly penetration test |
| 8.10 Deletion | Account deletion; retention (0.36) | — |
| 8.12 Data leakage | Server never sees content; screenshot code; sealed join codes | — |
| 8.15–8.16 Logging, monitoring | Audit trail on phones; admin log (0.36) | Uptime monitoring and alerts for the relay |
| 8.24 Cryptography | AES-GCM, ECDH P-256, ECDSA, HKDF; sealed codes to an admin public key | Written key policy: where `ADMIN_SEAL_KEY` lives, who can see it, how to rotate it (see README 0.34) |
| 8.25–8.29 Secure development | Unit, screenshot and server tests; lint | CI on every push |

---

## 6. Impact assessment, short form (GDPR Art. 35, ISO 27001 5.34)

| Processing | Risk to people | Measures | Remaining risk |
|---|---|---|---|
| Health details in an SOS and on the lock screen | Strangers learn a condition | Optional; sent only in an SOS; lock-screen card off by default with a preview of what shows | Low, accepted for emergency care |
| Trip groups: check-ins, last heard, roll calls | An admin monitors people too closely (for example an employer) | Notice before joining; no positions sent; leave any time; tracking stops when the trip ends and check-ins are erased 30 days later | Low |
| Trip groups: health cards and altitude checks (0.38) | Sensitive health data seen by more people than needed, or kept after the trip | Off by default; each person switches it on; goes phone to phone only to the admin and their own captain (never the relay); taken back at any time; erased from their phones when the trip ends (`ForgetCare`); never in the trip report PDF | Low |
| Join codes and screenshot codes identify people | Staff misuse | Sealed to one key on the server; every look-up logged for a year; used only on a report | Low, if the admin log is reviewed |
| Showing a group nearby | Strangers learn a group's name and its admin's name | Admin turns it on; only phones connected straight to the admin's phone; passcode never sent; guessing limits | Low |

---

## 7. ISO 22301:2019 (business continuity)

**BlueMob as a continuity tool for customers:**

| ISO 22301 need | BlueMob |
|---|---|
| Communication when networks fail (8.4.3) | Mesh messages and calls; trip groups |
| Accounting for people | Roll calls (manual and automatic), check-ins, "last heard", alerts when a phone can't be reached |
| Plans people can see | Itinerary with checkpoints |
| Exercises (8.5) | Field test; SOS preview; Check on everyone |
| Records (7.5) | Audit trail; trip exports; field-test data |

**For BlueMob the service. Gaps:**
- Business impact analysis. Suggested targets: relay RTO 1 hour, RPO 24 hours.
- Relay backups and a second relay.
- A yearly restore test.

**Already true:** losing the relay doesn't stop SOS, mesh messages, roll calls or check-ins between phones in reach.

---

## 8. ISO 31000:2018 (risk register, likelihood and impact from 1 to 5)

| Risk | L | I | Treatment | Owner |
|---|---|---|---|---|
| SOS not received (nobody near, no internet) | 3 | 5 | Disclaimer; re-sent to every phone in range; SOS contacts; LoRa plan (`docs/lora.md`) | Product |
| Phone makers stop BlueMob in the background | 4 | 3 | Battery-saver guidance; field test measures it | Product |
| Relay down | 3 | 3 | Mesh keeps working; add a second relay and monitoring | Ops |
| Admin token leaked | 2 | 4 | **0.36:** TOTP second factor, sessions, admin log | Ops |
| `ADMIN_SEAL_KEY` leaked: join codes readable | 2 | 3 | Only on Render; rotate with a new app version | Ops |
| Passcode guessed by fake IDs | 2 | 3 | **0.36:** 5 tries a phone, 20 a group, then paused | Product |
| Trip groups used to monitor people too closely | 2 | 3 | **0.36:** notice before joining; no positions; tracking ends with the trip; check-ins erased after 30 days | Product |
| Health data leak | 2 | 5 | On the phone; only in an SOS; encryption | Product |
| Offline AI gives wrong first aid | 3 | 4 | Guide is the source; warnings; SOS and 112 always shown | Product |
| Non-compliance (DPDP Rules from 2027) | 2 | 4 | Section 2 gaps, lawyer review | Founder |

Review it every quarter and after any incident.

---

## 9. NIST Cybersecurity Framework 2.0

| Function | Today | Next |
|---|---|---|
| **Govern** | This document, privacy policy (EN/HI), risk register with owners | Security policy page; named security owner; supplier terms |
| **Identify** | Record of processing (section 1), impact assessment (section 6), asset list | Asset register with owners |
| **Protect** | E2E and at-rest encryption, signed packets, admin TOTP, passcode limits, retention, minimisation | CI with dependency scanning; separate admin accounts |
| **Detect** | Error reports, webhook alerts, audit trails, admin log, refused admin sign-ins logged | Relay uptime and anomaly alerts |
| **Respond** | Incident procedure (DPDP Rules, GDPR, FTC), News to all users, account deletion | A yearly tabletop exercise |
| **Recover** | User backups; mesh without the relay | Relay backups and a tested restore |

---

## 10. What to do next, in order

1. **Now:**
   - Set `GRIEVANCE_OFFICER`, `CONTACT_EMAIL`, `ADMIN_SEAL_KEY` and `ADMIN_TOTP_SECRET` on Render.
   - Post a News item: the privacy policy changed (trip groups, join codes, screenshot code). The policy promises this.
2. **Before Play launch:**
   - Signed data processing terms with Google and Render.
   - Relay backups.
   - Dependency scanning in CI.
3. **Before selling to schools:** verifiable parental consent (DPDP Rules), then a lawyer's review.
4. **Before EU users:** an EU representative, and standard contractual clauses or EU hosting.
5. **Never without a BAA:** healthcare providers sending patient data.
6. **With a team or a large customer:** an ISO 27001 management system, then external certification.
