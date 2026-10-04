#!/usr/bin/env python3
"""
Remove everything seed.py created — and nothing else. Only the seed's own usernames are touched
(seed_data.USERS); existing accounts such as testuser, testadmin or your own are left alone.

For each seed user that exists:
  1. DELETE /users/{userId} as that user — ms_user deletes the profile, vault and stats, and its
     user.deleted event makes ms_dictionary delete their dictionaries, words, tags and membership
     copy, and ms_marketplace delete their listings, imports and publisher row;
  2. delete the Keycloak account (ms_user may already have done it — that is fine).

    python3 scripts/dev-seed/reset.py
"""
import sys
import time

import common as c
from seed_data import PASSWORD, SEED_USERNAMES


def main():
    admin = c.admin_token()
    tokens = c.Tokens()
    removed = 0

    for username in SEED_USERNAMES:
        account = c.find_keycloak_user(admin, username)
        if not account:
            continue

        try:
            token = tokens.get(username, PASSWORD)
            profile = c.request("GET", f"{c.USER_URL}/users/me", token=token)
            c.request("DELETE", f"{c.USER_URL}/users/{profile['id']}", token=token)
            print(f"  {username:<16} profile deleted (cascade via user.deleted)")
        except c.HttpError as e:
            if e.status != 404:
                raise
            # No profile (a seed that stopped early): remove any dictionaries directly
            sub = c.subject(token)
            for dictionary in c.request("GET", f"{c.DICTIONARY_URL}/dictionaries/{sub}", token=token) or []:
                c.request("DELETE", f"{c.DICTIONARY_URL}/dictionaries/{dictionary['dictionaryId']}", token=token)
            print(f"  {username:<16} had no profile; dictionaries deleted directly")

        try:
            c.request("DELETE", f"{c.KEYCLOAK}/admin/realms/{c.REALM}/users/{account['id']}", token=admin)
        except c.HttpError as e:
            if e.status != 404:  # ms_user deletes the account itself when its admin client is configured
                raise
        removed += 1

    if not removed:
        print("Nothing to reset: no seed users found.")
        return

    # Let the user.deleted cascades finish in the other services before reporting done
    time.sleep(3)
    print(f"\nRemoved {removed} seed users and their data. Check the dead-letter queue is empty: "
          f"{c.RABBIT_URL} → Queues → verborum.dead-letter")


if __name__ == "__main__":
    sys.exit(main())
