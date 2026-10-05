#!/usr/bin/env python3
"""
Seed the local stack with dummy users, dictionaries and words — for the Library and the Forum.

Everything goes through the real APIs (Keycloak admin API, then ms_user, ms_dictionary and
ms_marketplace as each user), so every service's events fire and all databases end up consistent:
profiles, dictionaries, words, tags, Forum membership, listings, publishers, imports and vaults.

    python3 scripts/dev-seed/seed.py            # seed (refuses if seed users already exist)
    python3 scripts/dev-seed/reset.py           # remove everything the seed created

Needs: the docker-compose stack (Keycloak, Postgres x3, RabbitMQ) and ms_user, ms_dictionary and
ms_marketplace running with their RabbitMQ listeners on. Python 3.9+, no third-party packages.
"""
import json
import random
import sys
import uuid

import common as c
from seed_data import PASSWORD, SHOWCASES, TERMS_VERSION, plan
from vocab import build

RNG = random.Random(7)


def log(message=""):
    print(message, flush=True)


def preflight():
    log("Checking the stack ...")
    for name, url in [("ms_user", c.USER_URL), ("ms_dictionary", c.DICTIONARY_URL), ("ms_marketplace", c.MARKETPLACE_URL)]:
        try:
            c.request("GET", f"{url}/actuator/health")
        except Exception as e:
            sys.exit(f"  {name} is not reachable at {url}: {e}")
    # Joining shares dictionaries (ms_dictionary) and lists them (ms_marketplace) through these queues;
    # without a consumer the seed would silently produce an empty Forum
    for queue_name in ["dictionary.user.profile.updated", "marketplace.user.profile.updated",
                       "marketplace.dictionary.visibility.public", "user.dictionary.imported"]:
        try:
            consumers = c.queue(queue_name).get("consumers", 0)
        except c.HttpError as e:
            sys.exit(f"  RabbitMQ queue {queue_name} not found ({e.status}) — are the services on the current code?")
        if consumers < 1:
            sys.exit(f"  RabbitMQ queue {queue_name} has no consumer — start the service that listens on it")
    log("  services up, queues consumed")


def create_account(admin, user):
    first, last = user["display_name"].split(" ", 1)
    c.request("POST", f"{c.KEYCLOAK}/admin/realms/{c.REALM}/users", token=admin, body=dict(
        username=user["username"], email=user["email"], firstName=first, lastName=last,
        enabled=True, emailVerified=True, requiredActions=[],
        credentials=[dict(type="password", value=PASSWORD, temporary=False)]))


def word_entry(concept, word_lang, translation_lang):
    word, word_meta = build(concept, word_lang)
    translation, translation_meta = build(concept, translation_lang)
    entry = dict(wordId=str(uuid.uuid4()),
                 word=json.dumps(word, ensure_ascii=False), wordMeta=json.dumps(word_meta, ensure_ascii=False),
                 translation=json.dumps(translation, ensure_ascii=False),
                 translationMeta=json.dumps(translation_meta, ensure_ascii=False))
    # The owner's own progress on some words; left out on others like an older client would
    if RNG.random() < 0.7:
        entry["level"] = RNG.randint(0, 5)
    return entry


def dictionary_body(dictionary, sub, is_public):
    return dict(dictionaryId=dictionary["id"], userId=sub, name=dictionary["name"], isPublic=is_public,
                fromLang=dictionary["pair"][0].upper(), toLang=dictionary["pair"][1].upper())


def main():
    preflight()
    users = plan()
    tokens = c.Tokens()
    admin = c.admin_token()

    existing = [u["username"] for u in users if c.find_keycloak_user(admin, u["username"])]
    if existing:
        sys.exit(f"Seed users already exist ({', '.join(existing)}). Run reset.py first.")

    # 1. Accounts and profiles ------------------------------------------------------------------
    log(f"\n1. Creating {len(users)} accounts and profiles (password: {PASSWORD})")
    for user in users:
        create_account(admin, user)
        token = tokens.get(user["username"], PASSWORD)
        user["sub"] = c.subject(token)
        user["user_id"] = str(uuid.uuid4())
        c.request("POST", f"{c.USER_URL}/users/", token=token, body=dict(
            userId=user["user_id"], keycloakId=user["sub"], email=user["email"], displayName=user["display_name"]))
        log(f"  {user['display_name']:<16} {user['username']:<16} {'member' if user['member'] else 'not a member'}")

    # 2. Dictionaries, words and tags — all private for now; joining shares them -------------------
    log("\n2. Creating dictionaries, words and tags")
    for user in users:
        token = tokens.get(user["username"], PASSWORD)
        words_total = 0
        for dictionary in user["dictionaries"]:
            dictionary["id"] = str(uuid.uuid4())
            c.request("POST", f"{c.DICTIONARY_URL}/dictionaries/", token=token,
                      body=dictionary_body(dictionary, user["sub"], False))
            words = [word_entry(concept, *dictionary["pair"]) for concept in dictionary["concepts"]]
            c.request("POST", f"{c.DICTIONARY_URL}/words", token=token,
                      body=[dict(dictionaryId=dictionary["id"], words=words)])
            for tag in dictionary["tags"]:
                c.request("POST", f"{c.DICTIONARY_URL}/dictionaries/{dictionary['id']}/tags", token=token, body=dict(tag=tag))
            words_total += len(words)
        log(f"  {user['display_name']:<16} {len(user['dictionaries']):>2} dictionaries, {words_total:>4} words")

    # 3. Members join the Forum — the server shares all their dictionaries --------------------------
    members = [u for u in users if u["member"]]
    log(f"\n3. {len(members)} members join the Forum (all their dictionaries become public)")
    for user in members:
        token = tokens.get(user["username"], PASSWORD)
        c.request("PUT", f"{c.USER_URL}/users/me/profile-info", token=token, body=dict(
            marketplaceAgreementAccepted=True, marketplaceAgreementVersion=TERMS_VERSION))
    for user in members:
        token = tokens.get(user["username"], PASSWORD)
        expected = len(user["dictionaries"])
        c.wait_until(lambda: sum(1 for d in c.request("GET", f"{c.DICTIONARY_URL}/dictionaries/{user['sub']}", token=token)
                                 if d.get("isPublic")) == expected,
                     what=f"{user['username']}'s dictionaries to become public")
        c.wait_until(lambda: _listing_count(token, user["sub"]) == expected,
                     what=f"{user['username']}'s listings in the Forum")
        log(f"  {user['display_name']:<16} {expected:>2} shared")

    # 4. Some members hide a few dictionaries again (never a showcase, never the last shared one) ---
    log("\n4. Hiding a few dictionaries")
    for user in members:
        if not user["hide"]:
            continue
        token = tokens.get(user["username"], PASSWORD)
        candidates = [d for d in user["dictionaries"] if not d["showcase"]]
        for dictionary in candidates[-user["hide"]:]:
            c.request("PUT", f"{c.DICTIONARY_URL}/dictionaries/", token=token,
                      body=dictionary_body(dictionary, user["sub"], False))
            dictionary["hidden"] = True
        log(f"  {user['display_name']:<16} hid {user['hide']}")

    # 5. Imports — showcases by many members, everything else by a few ---------------------------
    log("\n5. Importing")
    for user in members:  # let the hides reach the Forum before anyone imports
        token = tokens.get(user["username"], PASSWORD)
        shared = sum(1 for d in user["dictionaries"] if not d.get("hidden"))
        c.wait_until(lambda: _listing_count(token, user["sub"]) == shared, what=f"{user['username']}'s hides")

    imports = 0
    imported = []  # (importer, dictionary) — step 6 rates from these
    for owner in members:
        showcase = SHOWCASES.get(owner["display_name"])
        if not showcase:
            continue
        dictionary = next(d for d in owner["dictionaries"] if d["showcase"])
        importers = RNG.sample([m for m in members if m is not owner], showcase["importers"])
        for importer in importers:
            _import(tokens, importer, dictionary)
            imported.append((importer, dictionary))
            imports += 1
        log(f"  showcase '{dictionary['name'][:50]}' ({showcase['case']}): {len(importers)} importers")

    for importer in members:
        others = [d for owner in members if owner is not importer
                  for d in owner["dictionaries"] if not d["showcase"] and not d.get("hidden")]
        for dictionary in RNG.sample(others, RNG.randint(1, 3)):
            _import(tokens, importer, dictionary)
            imported.append((importer, dictionary))
            imports += 1
    log(f"  {imports} imports in total")

    # 6. Ratings (P4-19..P4-22) — only importers rate, and most of them do --------------------------
    log("\n6. Rating")
    quality = {}  # dictionary id -> how good it is, so its raters roughly agree
    ratings = 0
    for importer, dictionary in imported:
        if RNG.random() < 0.2:  # some importers never rate — keeps the "rate it" path testable
            continue
        base = quality.setdefault(dictionary["id"], RNG.uniform(2.2, 4.9))
        stars = max(1, min(5, round(base + RNG.uniform(-1.2, 1.2))))
        token = tokens.get(importer["username"], PASSWORD)
        c.request("PUT", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{dictionary['id']}/rating", token=token,
                  body=dict(stars=stars))
        ratings += 1
    log(f"  {ratings} ratings on {len(quality)} dictionaries; the other imports are unrated")

    # 7. Summary --------------------------------------------------------------------------------
    viewer = members[0]
    token = tokens.get(viewer["username"], PASSWORD)
    top = max(s["importers"] for s in SHOWCASES.values())
    c.wait_until(lambda: c.request("GET", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/popular?size=1",
                                   token=token)["items"][0]["importCount"] >= top, what="import counts")
    popular = c.request("GET", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/popular?size=8", token=token)["items"]
    log("\nTop of 'popular':")
    for listing in popular:
        log(f"  {listing['importCount']:>2} imports  {listing['fromLang']}-{listing['toLang']}  "
            f"{listing['name'][:60]:<60}  by {listing['publisherName']}")

    top_rated = c.request("GET", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/top-rated?size=5", token=token)["items"]
    log("\nTop of 'top rated':")
    for listing in top_rated:
        log(f"  {listing['ratingAverage']:.1f} ({listing['ratingCount']:>2})  {listing['fromLang']}-{listing['toLang']}  "
            f"{listing['name'][:60]:<60}  by {listing['publisherName']}")

    total_dictionaries = sum(len(u["dictionaries"]) for u in users)
    log(f"\nDone: {len(users)} users ({len(members)} Forum members), {total_dictionaries} dictionaries. "
        f"Log in as any of them with password '{PASSWORD}', e.g. {users[0]['username']} / {users[0]['email']}.")


def _listing_count(token, sub):
    try:
        return len(c.request("GET", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/publisher/{sub}?size=100",
                             token=token)["items"])
    except c.HttpError as e:
        if e.status == 403:  # membership has not reached the Forum yet
            return -1
        raise


def _import(tokens, importer, dictionary):
    token = tokens.get(importer["username"], PASSWORD)
    c.request("POST", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{dictionary['id']}/import", token=token)


if __name__ == "__main__":
    main()
