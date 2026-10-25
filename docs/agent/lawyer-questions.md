# Questions for a Lawyer — and the User Data We Hold

**Status:** list started 2026-10-25. It feeds `P5L-02` (book the lawyer) and backlog item `BL-07`.
**This is not legal advice.** It is a list of what a lawyer for IT and data-protection law must answer
before the first public release. Earlier discussion: `legal-requirements-2026-10-15.pdf` and
`legal-partner-data-access-2026-10-19.txt`.

How to use this file: take it to the first meeting. Part A tells the lawyer exactly what we store. Part B
holds the questions. Write each answer in Part B, with the date and the name of the lawyer.

---

## Part A — What user data Verborum holds (verified in the code, 2026-10-25)

| Where | Data | Linked to the person by | Deleted by "Delete account"? |
|---|---|---|---|
| Keycloak | email, name, password hash, login history, linked Google or Facebook identity, sessions | the sign-in identity (`sub`) | **Yes**, but a failure is silent (see B-7) |
| ms_user `users` | email, display name, Forum agreement (accepted, version, time), created and updated time | `keycloak_id` | Yes |
| ms_user `vault_entries`, `user_stats` | list of imported dictionaries, learning statistics | `user_id` | Yes (database cascade) |
| ms_dictionary | dictionaries, words, translations, tags. The user types the content. It can hold anything, also personal data of other people | `fk_user_id` = `sub` | Yes (event `user.deleted`) |
| ms_marketplace | listings with the publisher display name, ratings (stars), import records | `fk_user_id` = `sub` | Yes (ratings, import records, listings, display name) |
| ms_notification | push token, device id, platform, app language, app version, last seen | `fk_user_id` = `sub` | Yes |
| Other users' vaults | a **reference** to a deleted dictionary | the other user | **No.** It shows "No longer available" |
| RabbitMQ | events with the `sub` and, for some, the device id | `sub` | Messages are short-lived |
| Server logs | user ids, IP addresses, error text | `sub`, IP address | **No.** No retention rule exists yet |
| Backups | none exist yet | — | Not applicable yet |
| Firebase (Google) | the push token, and the content of the silent push | token | Token removed with the user. Google's own retention is not ours |
| Mail provider (not chosen yet) | email address, the text of verification mails | email address | Provider rules apply |
| Google and Facebook (sign-in) | They know that the person signs in to Verborum | their own account | Not ours to delete |

Not stored, to the best of our knowledge: payment data, precise location, contacts, advertising ids,
analytics. `[CHECK again before release: crash reporting or analytics added later change this table]`

---

## Part B — Questions

### B-1 Role and liability
1. Is the owner, as a private person, the controller under the GDPR? Is the personal liability limited in any
   way? Does a later company change the answer?
2. The contributor in Azerbaijan: which role (processor, joint controller, none)? Is test data only enough to
   stay outside Chapter V? What must the contract say? (See `legal-partner-data-access-2026-10-19.txt`.)
3. Is a cyber or IT liability policy for a private person useful? What must it cover?

### B-2 Documents
4. Review of the draft privacy policy and the terms of use (`P5L-03`). German and English are the legal
   versions. Is a translation into the other 17 languages only a convenience copy?
5. Minimum age: the German age of consent is 16. Do we need a technical age check or is a statement in the
   terms enough? What do Apple and Google need?
6. Does an Impressum apply (Sec. 5 DDG) for a free, non-commercial, open-source app? What happens if a
   donation button is added later? May the postal address be an address-forwarding service?

### B-3 User content and the Forum
7. Dictionaries are user content, and other users download them. Which license text must the terms contain so
   that a download is lawful? What if a user publishes copyrighted material (for example a textbook word list)?
8. DSA: is an individual a provider of a hosting service with notice-and-action duties (Art. 16 and 17)? Does
   Art. 19 (small enterprise exemption) apply to a person? What is the minimum process? (`P5L-05`)
9. Takedown: what must the owner of a removed listing receive (reason, appeal)? How long do we keep the
   report?

### B-4 Erasure and retention
10. Is the scope of "Delete account" enough for GDPR Art. 17? Is the warning text in
    `account-deletion-guide.md` §6 correct and sufficient?
11. Other users' vault entries stay as a reference to a deleted dictionary. Is this personal data of the
    deleted person? Does it change the answer if we remove them?
12. Logs and backups: what retention periods are allowed? May we keep a deleted person's id in a log for
    security reasons? How long? What do we say about backups when they exist?
13. The Forum agreement record (version, time) is deleted with the account. Do we need to keep proof of
    consent for a longer time?
14. Ratings: after deletion the stars are removed and the averages recalculated. Is this the right choice
    against keeping an anonymous count?

### B-5 Third parties and transfers
15. Firebase Cloud Messaging (Google, USA): which transfer mechanism applies, and is the Data Privacy
    Framework enough? Is the push token personal data? Does Sec. 25 TDDDG need consent for the token?
16. Google and Facebook sign-in through Keycloak: who is the controller for which step? What must the privacy
    policy say?
17. Hosting provider and mail provider: which agreements (Art. 28) must we keep? Is the standard online
    agreement enough?
18. Turkey: we expect users there. Does the KVKK (Turkish law) apply to us, and what does it require? Other
    countries with local rules for apps (for example Brazil, India)?

### B-6 Rights procedures
19. Access and export requests (Art. 15 and 20): is a manual process within one month enough, or must we
    build an export in the app?
20. Breach: the 72-hour runbook (Art. 33). Which authority? In which cases must the users be told?
21. Records of processing (Art. 30): does the exemption for small organizations apply to us? We plan to keep
    the record in any case.

### B-7 Open technical gaps to explain to the lawyer
These are known and not fixed. The lawyer must say how serious each one is.
- **Silent Keycloak failure.** If the identity delete fails, the profile is gone but the identity stays, and
  the person can sign in again. We only write an ERROR log line. Roadmap fix: decide in `P5A` (guide §10).
- **Orphan vault references** in other users' accounts (question 11).
- **No log retention rule and no backup concept yet** (question 12).
- **Test data:** the repository folder `sql_dumps/` holds a 2024 dump. Check that it holds no real data.

### B-8 Business choices that change the answers
- Donations, ads, sponsorship or paid features make the owner a trader. Decide in `P5L-01`, before any such
  button exists. Apple also asks for trader status in the EU.
- Project license and contributor terms (DCO or CLA).

---

## Answers (fill in)

| Question | Answer | Lawyer, date |
|---|---|---|
| | | |
