#!/usr/bin/env python3
"""
Account deletion, end to end across the four services (roadmap P5A-04).

    python3 scripts/deletion-check/check.py

It creates two throwaway users, gives the first one data in every service, deletes that user through the
real API and then looks at the database of each service and at Keycloak:

  victim    profile, vault entry, statistics (ms_user) | dictionary, words, tag, Forum membership (ms_dictionary)
            listing, publisher name, an import and a rating of someone else's dictionary (ms_marketplace)
            a device token (ms_notification) | a Keycloak account
  importer  imports and rates the victim's dictionary; owns a dictionary the victim imports and rates

Expected after DELETE /users/{id} (docs/integration/account-deletion-guide.md §2):
  - every row of the victim is gone, in all four databases, and so is the Keycloak account
  - the importer's rating average is recomputed without the victim's rating
  - the importer keeps a vault entry for the victim's dictionary: it is a reference, and it shows
    "No longer available" (guide §2, "What stays"). If this ever stops being true, the guide must change.

Exit code 1 when a check fails. Everything it created is removed again, also after a failure.

Local only: it uses the dev password-grant client, the Keycloak admin account and `docker exec psql`.
Needs the compose stack plus ms_user, ms_dictionary, ms_marketplace and ms_notification running, with
their RabbitMQ listeners on. Python 3.9+, no packages.
"""
import os
import subprocess
import sys
import uuid

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dev-seed"))
import common as c  # noqa: E402

PASSWORD = "deletion-check-1"
TERMS_VERSION = "2026-10-01"
NOTIFICATION_URL = os.environ.get("SEED_NOTIFICATION_URL", "http://localhost:8088")
DB = {  # service -> (container, database); user is `coldtea`, as in docker-compose.yml
    "ms_user": ("verborum-db-user", "vdbprofile"),
    "ms_dictionary": ("verborum-db-dictionary", "vdbdictionary"),
    "ms_marketplace": ("verborum-db-market", "vdbmarket"),
    "ms_notification": ("verborum-db-notification", "vdbnotification"),
}

results = []  # (ok, text)


def log(message=""):
    print(message, flush=True)


def check(ok, text):
    results.append((ok, text))
    log(f"  {'ok  ' if ok else 'FAIL'} {text}")
    return ok


def sql(service, query):
    container, database = DB[service]
    out = subprocess.run(["docker", "exec", container, "psql", "-U", "coldtea", "-d", database, "-tAc", query],
                         capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"psql failed in {container}: {out.stderr.strip()}")
    return int(out.stdout.strip() or 0)


def run_sql(service, statement):
    container, database = DB[service]
    out = subprocess.run(["docker", "exec", container, "psql", "-U", "coldtea", "-d", database, "-c", statement],
                         capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"psql failed in {container}: {out.stderr.strip()}")


class Actor:
    def __init__(self, label):
        self.label = label
        self.username = f"deletion.{label}"
        self.email = f"{self.username}@example.com"
        self.sub = None
        self.user_id = None
        self.dictionary_id = str(uuid.uuid4())
        self.dictionary_name = f"Deletion check ({label})"


def preflight():
    log("Checking the stack ...")
    urls = [("ms_user", c.USER_URL), ("ms_dictionary", c.DICTIONARY_URL),
            ("ms_marketplace", c.MARKETPLACE_URL), ("ms_notification", NOTIFICATION_URL)]
    for name, url in urls:
        try:
            c.request("GET", f"{url}/actuator/health")
        except Exception as e:
            sys.exit(f"  {name} is not reachable at {url}: {e}")
    for queue_name in ["dictionary.user.deleted", "marketplace.user.deleted", "notification.user.deleted",
                       "dictionary.user.profile.updated", "marketplace.user.profile.updated",
                       "marketplace.dictionary.visibility.public", "user.dictionary.imported"]:
        try:
            consumers = c.queue(queue_name).get("consumers", 0)
        except c.HttpError as e:
            sys.exit(f"  RabbitMQ queue {queue_name} not found ({e.status}) — is the service on the current code?")
        if consumers < 1:
            sys.exit(f"  RabbitMQ queue {queue_name} has no consumer — start the service that listens on it")
    for name, (container, _) in DB.items():
        if subprocess.run(["docker", "inspect", container], capture_output=True).returncode != 0:
            sys.exit(f"  container {container} ({name}) is not running")
    log("  services up, queues consumed")


def remove_leftovers(admin, tokens, actor):
    """A crashed earlier run must not block this one."""
    existing = c.find_keycloak_user(admin, actor.username)
    if not existing:
        return
    log(f"  removing a leftover {actor.username}")
    try:
        token = tokens.get(actor.username, PASSWORD)
        me = c.request("GET", f"{c.USER_URL}/users/me", token=token)
        c.request("DELETE", f"{c.USER_URL}/users/{me['id']}", token=token)
    except Exception:
        pass
    c.wait_until(lambda: not c.find_keycloak_user(admin, actor.username), timeout=20, what="the leftover to go")


def create_actor(admin, tokens, actor):
    first, last = actor.label.title(), "Check"
    c.request("POST", f"{c.KEYCLOAK}/admin/realms/{c.REALM}/users", token=admin, body=dict(
        username=actor.username, email=actor.email, firstName=first, lastName=last, enabled=True,
        emailVerified=True, requiredActions=[], credentials=[dict(type="password", value=PASSWORD, temporary=False)]))
    token = tokens.get(actor.username, PASSWORD)
    actor.sub = c.subject(token)
    actor.user_id = str(uuid.uuid4())
    c.request("POST", f"{c.USER_URL}/users/", token=token, body=dict(
        userId=actor.user_id, keycloakId=actor.sub, email=actor.email, displayName=f"{first} {last}"))
    return token


def create_dictionary(token, actor):
    c.request("POST", f"{c.DICTIONARY_URL}/dictionaries/", token=token, body=dict(
        dictionaryId=actor.dictionary_id, userId=actor.sub, name=actor.dictionary_name, isPublic=False,
        fromLang="EN", toLang="DE"))
    words = [dict(wordId=str(uuid.uuid4()), word='["house"]', wordMeta="{}", translation='["Haus"]',
                  translationMeta="{}", level=1),
             dict(wordId=str(uuid.uuid4()), word='["tree"]', wordMeta="{}", translation='["Baum"]',
                  translationMeta="{}", level=2)]
    c.request("POST", f"{c.DICTIONARY_URL}/words", token=token, body=[dict(dictionaryId=actor.dictionary_id, words=words)])
    c.request("POST", f"{c.DICTIONARY_URL}/dictionaries/{actor.dictionary_id}/tags", token=token, body=dict(tag="deletion-check"))


def join_forum(token, actor):
    c.request("PUT", f"{c.USER_URL}/users/me/profile-info", token=token, body=dict(
        marketplaceAgreementAccepted=True, marketplaceAgreementVersion=TERMS_VERSION))
    c.wait_until(lambda: sql("ms_marketplace", f"select count(*) from dictionary_stats where fk_user_id='{actor.sub}' and is_listed") == 1,
                 what=f"{actor.label}'s listing in the Forum")


def victim_counts(v):
    """Every place the victim's data lives. Name, service, query."""
    s, u, d = v.sub, v.user_id, v.dictionary_id
    return [
        ("ms_user      profile", "ms_user", f"select count(*) from users where keycloak_id='{s}'"),
        ("ms_user      statistics", "ms_user", f"select count(*) from user_stats where user_id='{u}'"),
        ("ms_user      vault entries", "ms_user", f"select count(*) from vault_entries where fk_user_id='{u}'"),
        ("ms_dictionary dictionaries", "ms_dictionary", f"select count(*) from dictionaries where fk_user_id='{s}'"),
        ("ms_dictionary words", "ms_dictionary",
         f"select count(*) from words where fk_dictionary_id in (select dictionary_id from dictionaries where fk_user_id='{s}')"),
        ("ms_dictionary tags", "ms_dictionary",
         f"select count(*) from dictionary_tags where fk_dictionary_id in (select dictionary_id from dictionaries where fk_user_id='{s}')"),
        ("ms_dictionary Forum membership", "ms_dictionary", f"select count(*) from marketplace_members where keycloak_id='{s}'"),
        ("ms_marketplace listings", "ms_marketplace", f"select count(*) from dictionary_stats where fk_user_id='{s}'"),
        ("ms_marketplace publisher name", "ms_marketplace", f"select count(*) from publishers where keycloak_id='{s}'"),
        ("ms_marketplace imports made", "ms_marketplace", f"select count(*) from dictionary_imports where fk_user_id='{s}'"),
        ("ms_marketplace ratings given", "ms_marketplace", f"select count(*) from dictionary_ratings where fk_user_id='{s}'"),
        ("ms_marketplace imports of the dictionary", "ms_marketplace", f"select count(*) from dictionary_imports where fk_dictionary_id='{d}'"),
        ("ms_marketplace ratings of the dictionary", "ms_marketplace", f"select count(*) from dictionary_ratings where fk_dictionary_id='{d}'"),
        ("ms_notification device tokens", "ms_notification", f"select count(*) from device_tokens where fk_user_id='{s}'"),
    ]


def main():
    preflight()
    tokens = c.Tokens()
    admin = c.admin_token()
    victim, importer = Actor("victim"), Actor("importer")
    cleanup = []

    try:
        log("\n1. Two throwaway users")
        for actor in (victim, importer):
            remove_leftovers(admin, tokens, actor)
        t_victim = create_actor(admin, tokens, victim)
        t_importer = create_actor(admin, tokens, importer)
        cleanup.append(importer)
        log(f"  {victim.username} and {importer.username} (password {PASSWORD})")

        log("\n2. Data in every service")
        create_dictionary(t_victim, victim)
        create_dictionary(t_importer, importer)
        join_forum(t_victim, victim)
        join_forum(t_importer, importer)
        # The importer imports and rates the victim's dictionary; the victim does the same with the importer's
        c.request("POST", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{victim.dictionary_id}/import", token=t_importer)
        c.request("PUT", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{victim.dictionary_id}/rating", token=t_importer, body=dict(stars=4))
        c.request("POST", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{importer.dictionary_id}/import", token=t_victim)
        c.request("PUT", f"{c.MARKETPLACE_URL}/marketplace/dictionaries/{importer.dictionary_id}/rating", token=t_victim, body=dict(stars=5))
        c.request("PUT", f"{NOTIFICATION_URL}/notifications/devices",
                  token=t_victim, body=dict(deviceId=str(uuid.uuid4()), token="deletion-check-fcm-token",
                                            platform="ANDROID", locale="en", appVersion="1"))
        # Nothing in ms_user writes statistics yet (the table is empty everywhere), so there is no API to call.
        # The row is inserted directly: what is under test is that deleting the profile takes it along.
        run_sql("ms_user", f"insert into user_stats (user_id, total_words, total_dictionaries, update_dt) "
                           f"values ('{victim.user_id}', 2, 1, now())")
        # The vault entries are written a moment later, through an event
        c.wait_until(lambda: sql("ms_user", f"select count(*) from vault_entries where fk_user_id='{victim.user_id}'") == 1
                     and sql("ms_user", f"select count(*) from vault_entries where fk_user_id='{importer.user_id}'") == 1,
                     what="the vault entries")
        c.wait_until(lambda: sql("ms_marketplace", f"select rating_count from dictionary_stats where dictionary_id='{importer.dictionary_id}'") == 1,
                     what="the importer's rating count")

        log("\n3. Before the deletion every place holds something")
        for name, service, query in victim_counts(victim):
            n = sql(service, query)
            check(n >= 1, f"{name}: {n}")
        check(c.find_keycloak_user(admin, victim.username) is not None, "Keycloak account exists")

        log("\n4. DELETE /users/{id} as the victim (a login from a minute ago, so no re-sign-in is asked)")
        c.request("DELETE", f"{c.USER_URL}/users/{victim.user_id}", token=t_victim)
        log("  200")

        log("\n5. After the deletion (events are asynchronous; waiting up to 30 seconds)")
        try:
            c.wait_until(lambda: all(sql(service, query) == 0 for _, service, query in victim_counts(victim)),
                         timeout=30, what="every row of the victim to go")
        except TimeoutError:
            pass
        for name, service, query in victim_counts(victim):
            n = sql(service, query)
            check(n == 0, f"{name}: {n}")
        try:
            c.wait_until(lambda: c.find_keycloak_user(admin, victim.username) is None, timeout=15, what="the Keycloak account to go")
            check(True, "Keycloak account gone")
        except TimeoutError:
            check(False, "Keycloak account gone (ms_user needs KEYCLOAK_ADMIN_CLIENT_SECRET — see docs/ops/local-development.md)")
        try:
            c.request("POST", f"{c.KEYCLOAK}/realms/{c.REALM}/protocol/openid-connect/token",
                      form=dict(client_id=c.DEV_CLIENT, username=victim.username, password=PASSWORD, grant_type="password"))
            check(False, "signing in with the old password is refused")
        except c.HttpError as e:
            check(e.status in (400, 401), f"signing in with the old password is refused (HTTP {e.status})")

        log("\n6. The importer's side")
        check(sql("ms_dictionary", f"select count(*) from dictionaries where fk_user_id='{importer.sub}'") == 1,
              "the importer's own dictionary is untouched")
        check(sql("ms_marketplace", f"select count(*) from dictionary_stats where dictionary_id='{importer.dictionary_id}' and is_listed") == 1,
              "the importer's listing is untouched")
        check(sql("ms_marketplace", f"select rating_count from dictionary_stats where dictionary_id='{importer.dictionary_id}'") == 0,
              "the victim's rating was removed from the importer's average")
        check(sql("ms_user", f"select count(*) from vault_entries where fk_user_id='{importer.user_id}' and fk_dictionary_id='{victim.dictionary_id}'") == 1,
              "the importer keeps a vault entry for the deleted dictionary (a reference; shows \"No longer available\")")
        batch = c.request("GET", f"{c.DICTIONARY_URL}/dictionaries/batch?ids={victim.dictionary_id}", token=t_importer)
        check(not batch, "the deleted dictionary is no longer readable by id")

    finally:
        log("\n7. Clean up")
        fresh = c.Tokens()  # the cached token may be older than the 5 minutes a deletion accepts
        for actor in cleanup:
            try:
                token = fresh.get(actor.username, PASSWORD)
                c.request("DELETE", f"{c.USER_URL}/users/{actor.user_id}", token=token)
                c.wait_until(lambda: c.find_keycloak_user(admin, actor.username) is None, timeout=20, what="cleanup")
                log(f"  {actor.username} removed")
            except Exception as e:
                log(f"  could not remove {actor.username}: {e} — delete it by hand (Keycloak admin, realm {c.REALM})")

    failed = [text for ok, text in results if not ok]
    log(f"\n{len(results) - len(failed)} of {len(results)} checks passed.")
    for text in failed:
        log(f"  FAILED: {text}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
