# Deletion check — account deletion across the four services

Proves that `DELETE /users/{id}` removes the data of one account from ms_user, ms_dictionary,
ms_marketplace and ms_notification, and the Keycloak account. Roadmap task `P5A-04`.

```bash
python3 scripts/deletion-check/check.py
```

Exit code 0 when all checks pass, 1 when one fails. It prints one line per check.

## What it does

1. Creates two throwaway users, `deletion.victim` and `deletion.importer` (password `deletion-check-1`).
2. Gives the victim data in every service: a dictionary with two words and a tag, Forum membership (listing and
   publisher name), a vault entry, an import and a rating of the importer's dictionary, a device token, and a
   statistics row. The importer imports and rates the victim's dictionary.
3. Counts the rows of the victim in the four databases. Every count must be at least 1.
4. Deletes the victim through the real API (the login is under 5 minutes old).
5. Waits up to 30 seconds (the cleanup is event-driven), then checks that every count is 0, the Keycloak account
   is gone, and the old password no longer signs in.
6. Checks the importer's side: the importer's own dictionary and listing are untouched, the victim's rating is
   gone from the importer's average, and the deleted dictionary cannot be read by id.
7. Removes the importer again (also after a failure).

## What it asserts on purpose

- **The importer keeps a vault entry** for the deleted dictionary. It is a reference, and the app shows "No longer
  available". This is the documented behavior (`docs/integration/account-deletion-guide.md` §2). If a backend
  change removes the entry, this check fails: update the guide, the lawyer list (`BL-07`) and this script together.
- **The statistics row is inserted by SQL.** Nothing in ms_user writes `user_stats` yet, so no API can fill it. The
  check still proves that the profile delete takes the row along (database cascade).

## Needs

The compose stack plus ms_user, ms_dictionary, ms_marketplace and ms_notification, with their RabbitMQ listeners
on. ms_user needs `KEYCLOAK_ADMIN_CLIENT_SECRET` (its default is the local realm secret); without it the Keycloak
check fails. Python 3.9+, no packages, and `docker exec` access to the four Postgres containers.

**Local only.** It uses the dev password-grant client, the Keycloak admin account and direct SQL, which do not
exist in a shared realm. It reuses `scripts/dev-seed/common.py`.
