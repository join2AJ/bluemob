# Incident and data-breach procedure

For anything that could affect BlueMob users' safety, data or access: a server compromise, a leaked admin token or
upload key, a bug that sends data to the wrong person, a long relay outage, abuse at scale.

Owner: the grievance officer (`GRIEVANCE_OFFICER` / `CONTACT_EMAIL` on the relay). Keep this page short and current.

## 1. Notice (anyone, any time)

How it comes in:
- an `ALERT_WEBHOOK` alert;
- a spike of reports in `/admin`;
- a user email;
- Play Console vitals;
- an outage.

Open an incident note right away with:
- the time;
- what was seen;
- who saw it.

Don't delete anything.

## 2. Contain (first hour)

| Situation | Do |
|---|---|
| Admin or reports token leaked | Change `ADMIN_TOKEN` / `REPORTS_TOKEN` on the server and redeploy |
| Firebase service account leaked | Delete the key in Google Cloud, create a new one, update `FCM_SERVICE_ACCOUNT` |
| Server compromised | Take it offline (phones keep working phone to phone), rebuild from the repository on a clean machine, rotate every secret |
| Play upload key lost or leaked | Ask Google Play support to reset the upload key (Play App Signing keeps the app key safe) |
| Bug exposing data | Stop the feature on the server if possible; ship a fixed version; post News to tell users what to do |
| Fake SOS or abuse wave | Post News warning users and asking them to block and report; collect the reported IDs from `/admin` |

## 3. Assess (within 24 hours)

- What data was involved?
  - Messages and files stay end-to-end encrypted.
  - The server holds: public keys, number fingerprints, emails, push tokens, encrypted backups and reports.
- How many people were affected, and from when to when?
- Is there a risk of harm? Treat health details, positions and phone numbers as high risk.

## 4. Notify

- **India (DPDP Act 2023):** tell the Data Protection Board of India and each affected user without delay, in the
  form the DPDP Rules set: what happened, when, likely impact, what we're doing, and what users should do. Check the
  current Rules for the exact deadline and format.
- **EU users (GDPR):** tell the lead supervisory authority within 72 hours of becoming aware, and affected users
  without undue delay if the risk is high.
- **Users:** a News from BlueMob item (urgent if action is needed), plus email to those who gave one.
- **Google Play:** if the app or account is affected, through Play Console.

Templates:

> **Subject: Important: a security problem at BlueMob**
> On [date] we found [what happened]. It affected [what data] of [how many / which] users between [from] and [to].
> Your messages and calls were not readable: they are end-to-end encrypted. We have [what we did]. Please [what to do,
> e.g. update the app / set a new backup password]. Questions: [CONTACT_EMAIL].

## 5. Recover and learn (within 2 weeks)

- Restore the service; confirm with `/admin` (reports, online count) and a test SOS between two phones.
- Write a short review: cause, timeline, what worked, what to change. Add the risk to `docs/compliance.md`.
- Close the incident note.

## Practise

Once a year, walk through one scenario above (for example "admin token leaked") with whoever runs BlueMob.
