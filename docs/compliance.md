# BlueMob and security, continuity and privacy frameworks

This maps what BlueMob already does to the frameworks people ask about, lists the gaps, and suggests the order to
close them. It is a working document, not a certification: certification (ISO 27001, ISO 22301) needs an
organisation with an implemented management system and an external audit.

**Two different questions:**

1. **Is BlueMob, the service, run securely and lawfully?** Covered by ISO 27001, NIST CSF, DPDP and GDPR.
2. **Can BlueMob help *other* organisations meet their own obligations?** Mainly ISO 22301 (business continuity) and
   ISO 31000 (risk). This is a sales point for trek operators, schools, NGOs, construction sites, mines and companies.

**About CompTIA Security+, CISA and CISM:** these are certifications for *people*, not frameworks for products.
- Security+ is general security practice.
- CISA (ISACA) is about auditing.
- CISM (ISACA) is about security management.

They help whoever runs BlueMob's security, audits it, or sells it to companies. CISA and CISM map to ISACA's COBIT,
which overlaps with ISO 27001's management clauses.

---

## 1. ISO/IEC 27001:2022 (information security)

| Annex A control | What BlueMob does today | Gap |
|---|---|---|
| 5.1 Policies | Privacy policy (`/privacy`) | Written information security policy, acceptable use, scope of the management system |
| 5.7 Threat intelligence | — | Follow Android, Node and dependency advisories |
| 5.9 Asset inventory | Code, relay, keys, Firebase, Render, Play Console listed in `docs/play-store.md` | One asset register with owners |
| 5.15–5.18 Access control | Admin endpoints need `ADMIN_TOKEN` / `REPORTS_TOKEN`; device actions are signed by the phone's key | Per-person admin accounts, MFA and audit of admin actions (comes with the admin web app) |
| 5.19–5.23 Suppliers and cloud | Render (hosting), Google Firebase (push, SMS), Hugging Face (offline AI models, verified by SHA-256) | Supplier list with data-processing terms; choose an Indian region for hosting |
| 5.24–5.28 Incident management | Automatic error reports, problem reports with categories, `ALERT_WEBHOOK` alerts the team, tamper-evident audit trail on each phone | Written incident response plan; breach-notification steps (see DPDP/GDPR) |
| 5.29–5.30 Continuity | Phones keep working with no server (mesh); cloud backups; relay can run anywhere (Docker) | Second relay with automatic failover; backup of relay data |
| 5.34 Privacy and PII | Data minimisation: number stored only as a fingerprint; health data only inside an SOS; E2E encryption | Records of processing; data protection impact assessment for health data |
| 6.3 Awareness | In-app guides, safety notes | Staff training once there is a team |
| 8.5 Authentication | Phone OTP (Firebase), optional PIN and biometric lock, signed requests with device keys | — |
| 8.7 Malware | Android sandbox; no code downloaded at runtime (AI models are data, hash-checked) | — |
| 8.8 Vulnerabilities | Dependencies pinned | Automated dependency scanning (Dependabot / OSV) and a yearly penetration test |
| 8.12 Data leakage | Server never sees message content, numbers or positions outside an SOS | — |
| 8.13 Backup | Encrypted user backups (password-locked), newest 3 kept | Back up the relay's own data (`DATA_DIR`) |
| 8.15–8.17 Logging and monitoring | On-device tamper-evident audit trail with hash chain; relay health check | Central server logs with retention; uptime monitoring and alerts |
| 8.20–8.22 Network security | HTTPS to the relay; signed and encrypted packets on the mesh | Rate limits exist; add a firewall and DDoS protection (e.g. Cloudflare) in front of the relay |
| 8.24 Cryptography | AES-GCM on device with Android Keystore; ECDH + AES for messages and calls; ECDSA signatures | Key rotation and a written crypto policy |
| 8.25–8.29 Secure development | Unit tests, screenshot tests, lint on every build | CI on every push, code review, security tests for the relay |
| 8.32 Change management | Versioned releases, git history | Release checklist, staged roll-outs in Play |

## 2. ISO 22301:2019 (business continuity)

**BlueMob as a continuity tool for organisations** (worth building on):

| ISO 22301 need | BlueMob feature |
|---|---|
| Communication when networks fail (8.4.3) | Mesh messaging and calls over Bluetooth/Wi-Fi, no SIM or internet needed |
| Warning and communication (8.4.3) | News from BlueMob (mass notification); a per-organisation version can come with the admin web app |
| Roll call / accounting for people | "Check on everyone" (I'm OK / I need help, with battery levels) |
| Incident response | SOS with position, rescue groups with live positions, helper signals |
| Exercises and testing (8.5) | SOS preview mode; Check on everyone as a drill |
| Records (7.5) | Tamper-evident audit trail, exportable trips (CSV/GPX) |

**For BlueMob the service:**
- **Gaps:**
  - Business impact analysis.
  - Recovery targets: for example RTO 1 hour and RPO 24 hours for the relay.
  - A continuity plan: relay failover and restoring from backup.
  - A yearly exercise.
- **Already true:** losing the relay doesn't stop SOS or mesh messaging between nearby phones.

## 3. ISO 31000:2018 (risk management)

A first risk register (likelihood and impact from 1 to 5):

| Risk | L | I | Treatment |
|---|---|---|---|
| SOS not received because nobody is nearby and there's no internet | 3 | 5 | Clear disclaimer; message re-sent to every phone that comes into range; SOS contacts over the internet |
| Relay down (single server) | 3 | 3 | Second relay and failover in the app; monitoring |
| Fake SOS or abuse | 3 | 3 | Ratings, block and report, signed identities |
| Leak of health data | 2 | 5 | Kept on the phone; sent only inside an SOS; DPIA |
| Lost Play upload key | 2 | 4 | Play App Signing; two offline copies |
| Firebase SMS cost spike or fraud | 2 | 3 | Firebase quota limits and abuse protection |
| Phone makers killing background apps | 4 | 3 | Background switch, battery prompt, Firebase push |
| Offline AI gives wrong first-aid advice | 3 | 4 | Guide used as the source; "can be wrong" note; SOS and 112 always shown |
| Legal: DPDP non-compliance | 2 | 4 | Items in section 5 |

Review it every quarter and after any incident.

## 4. NIST Cybersecurity Framework 2.0

| Function | Today | Next |
|---|---|---|
| **Govern** | Privacy policy, Play checklist, this document | Named security owner, policies, supplier list |
| **Identify** | Assets and data flows (privacy policy table), risk register above | Asset register, DPIA |
| **Protect** | E2E encryption, on-device encryption, signed requests, PIN/biometric, block and report, data minimisation | CI security checks, MFA for admins, key rotation |
| **Detect** | Automatic error reports, webhook alerts, audit trail, relay rate limits | Server monitoring and uptime alerts, anomaly alerts on reports |
| **Respond** | Report categories, admin news to all users, account deletion | Incident response plan, breach-notification templates |
| **Recover** | User backups, mesh keeps working without the relay | Relay backups and failover, tested restore |

## 5. India DPDP Act 2023 and EU GDPR

| Requirement | Status |
|---|---|
| Notice and consent | Sign-up explains what's shared; optional health fields; privacy policy. *Gap:* a consent screen that links the policy, and lets users withdraw consent for each purpose |
| Purpose limitation and minimisation | Number fingerprint only; health data only in an SOS; location sharing off by default |
| Right to erasure | **Done:** delete account in the app and on the web |
| Right to access and correction | Correction: Account screen. *Gap:* "Download my data" export |
| Grievance officer (DPDP) / contact (GDPR) | `CONTACT_EMAIL` on `/privacy`. *Gap:* name a person and publish the response time |
| Children (DPDP: under 18 needs verifiable parental consent) | Policy says 13+ and parental consent for under-18s. *Gap:* age gate at sign-up using the date of birth |
| Breach notification (DPDP: Board and users; GDPR: 72 hours) | *Gap:* written procedure and templates; News from BlueMob can tell users |
| Data location | `render.yaml` puts the relay in Singapore. DPDP allows transfers except to countries the government restricts; moving to an Indian region or your own server in India is simpler |
| Health data (GDPR special category) | Explicit, optional, and only shared inside an SOS: matches "vital interests" and consent |
| Processors (Firebase, Render, Hugging Face) | *Gap:* data processing terms on file |

## 6. Suggested order

1. **Before Play launch:**
   - consent screen with the policy link;
   - age gate (under-18 parental consent);
   - "Download my data";
   - named grievance officer;
   - incident and breach procedure (1–2 pages).
2. **First month:** relay monitoring; relay backups; second relay with failover; CI with dependency scanning.
3. **With the admin web app:** per-admin accounts with MFA; admin audit log; per-organisation news and roll call. That
   makes BlueMob an ISO 22301 tool for customers.
4. **When there's a team or a big customer:** ISO 27001 management system (policies, risk process, internal audit),
   then external certification.
