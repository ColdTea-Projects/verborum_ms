#!/usr/bin/env python3
"""
Put one seed user back — for example after you deleted their data while testing.

    python3 scripts/dev-seed/restore.py "Chloé Bernard"     # display name, username or email

It does for that one user what seed.py does for all 30: the Keycloak account and the profile (only if
they are missing), the dictionaries with their words and tags, the Forum membership, the hidden
dictionaries, and then a few imports and ratings of other people's dictionaries. Nobody else is touched.

What it cannot bring back: the imports that OTHER users made of this user's old dictionaries. Those
dictionaries get new ids, so the old vault entries of the others stay "No longer available". Run
reset.py and seed.py for a fully consistent set.

Needs the same running stack as seed.py. Local only.
"""
import random
import sys
import uuid

import common as c
import seed as s
from seed_data import PASSWORD, TERMS_VERSION, plan


def find_user(users, wanted):
    wanted = wanted.strip().lower()
    for user in users:
        if wanted in (user["display_name"].lower(), user["username"], user["email"]):
            return user
    names = ", ".join(u["display_name"] for u in users)
    sys.exit(f"No seed user matches '{wanted}'. The seed users are: {names}")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    s.preflight()
    user = find_user(plan(), sys.argv[1])
    tokens = c.Tokens()
    admin = c.admin_token()
    name = user["display_name"]

    # 1. Account and profile — only what is missing --------------------------------------------------
    s.log(f"\n1. Account and profile of {name}")
    if c.find_keycloak_user(admin, user["username"]):
        s.log("  Keycloak account exists")
    else:
        s.create_account(admin, user)
        s.log("  Keycloak account created")
    token = tokens.get(user["username"], PASSWORD)
    user["sub"] = c.subject(token)
    try:
        profile = c.request("GET", f"{c.USER_URL}/users/me", token=token)
        s.log("  profile exists")
    except c.HttpError as e:
        if e.status != 404:
            raise
        user["user_id"] = str(uuid.uuid4())
        c.request("POST", f"{c.USER_URL}/users/", token=token, body=dict(
            userId=user["user_id"], keycloakId=user["sub"], email=user["email"], displayName=name))
        s.log("  profile created")

    # 2. Dictionaries, words and tags — only those that are not there ----------------------------------
    existing = {d["name"] for d in c.request("GET", f"{c.DICTIONARY_URL}/dictionaries/{user['sub']}", token=token)}
    s.log(f"\n2. Dictionaries ({len(existing)} already there)")
    created = words_total = 0
    for dictionary in user["dictionaries"]:
        if dictionary["name"] in existing:
            continue
        dictionary["id"] = str(uuid.uuid4())
        c.request("POST", f"{c.DICTIONARY_URL}/dictionaries/", token=token,
                  body=s.dictionary_body(dictionary, user["sub"], False))
        words = [s.word_entry(concept, *dictionary["pair"]) for concept in dictionary["concepts"]]
        c.request("POST", f"{c.DICTIONARY_URL}/words", token=token, body=[dict(dictionaryId=dictionary["id"], words=words)])
        for tag in dictionary["tags"]:
            c.request("POST", f"{c.DICTIONARY_URL}/dictionaries/{dictionary['id']}/tags", token=token, body=dict(tag=tag))
        created += 1
        words_total += len(words)
    s.log(f"  created {created} dictionaries, {words_total} words")

    # 3. Forum membership — the server shares every dictionary of a member --------------------------------
    total = len(c.request("GET", f"{c.DICTIONARY_URL}/dictionaries/{user['sub']}", token=token))
    if user["member"]:
        s.log("\n3. Joining the Forum")
        c.request("PUT", f"{c.USER_URL}/users/me/profile-info", token=token, body=dict(
            marketplaceAgreementAccepted=True, marketplaceAgreementVersion=TERMS_VERSION))
        c.wait_until(lambda: sum(1 for d in c.request("GET", f"{c.DICTIONARY_URL}/dictionaries/{user['sub']}", token=token)
                                 if d.get("isPublic")) == total, what="the dictionaries to become public")
        c.wait_until(lambda: s._listing_count(token, user["sub"]) == total, what="the listings in the Forum")
        s.log(f"  {total} shared")

        # 4. Hide the same number as the seed does (never a showcase) --------------------------------------
        if user["hide"] and created:
            s.log("\n4. Hiding a few dictionaries")
            hidden = [d for d in user["dictionaries"] if not d["showcase"] and "id" in d][-user["hide"]:]
            for dictionary in hidden:
                c.request("PUT", f"{c.DICTIONARY_URL}/dictionaries/", token=token,
                          body=s.dictionary_body(dictionary, user["sub"], False))
                dictionary["hidden"] = True
            c.wait_until(lambda: s._listing_count(token, user["sub"]) == total - len(hidden), what="the hides")
            s.log(f"  hid {len(hidden)}")

        # 5. A few imports and ratings of other people's dictionaries ---------------------------------------
        s.log("\n5. Imports and ratings")
        rng = random.Random(user["username"])
        listings = c.request("GET", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/popular?size=100", token=token)["items"]
        others = [l for l in listings if l.get("publisherId") != user["sub"]]
        for listing in rng.sample(others, min(rng.randint(1, 3), len(others))):
            c.request("POST", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{listing['dictionaryId']}/import", token=token)
            rated = rng.random() < 0.8
            if rated:
                c.request("PUT", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{listing['dictionaryId']}/rating",
                          token=token, body=dict(stars=rng.randint(2, 5)))
            s.log(f"  imported '{listing['name'][:50]}'{' and rated it' if rated else ''}")

    s.log(f"\nDone: {name} ({user['username']} / {PASSWORD}) has {total} dictionaries again.")


if __name__ == "__main__":
    main()
