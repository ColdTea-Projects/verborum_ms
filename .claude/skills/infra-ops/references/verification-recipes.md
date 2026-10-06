# Manual Verification Recipes

Automated tests stop at the process boundary. These prove the real broker, the real database and
the real identity server behave as expected.

## Token and subject

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# the subject — this is what every service stores as fk_user_id
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | sed 's/$/==/' | base64 -d 2>/dev/null \
  | sed -E 's/.*"sub":"([^"]+)".*/\1/'

curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/dictionaries/$SUB
```

Ownership rules need **two** tokens — take a second as `testadmin` and use them as user A and
user B.

## Publishing an event into a service (testing a consumer)

The management API publishes with no application involvement. Set `__TypeId__` to the *publisher's*
class name — a class the consumer does not have is exactly the cross-service case, and this proves
the `INFERRED` type mapper works.

```bash
cat > /tmp/event.json <<'EOF'
{"properties":{"content_type":"application/json","headers":{"__TypeId__":"de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent"}},
 "routing_key":"dictionary.imported",
 "payload":"{\"dictionaryId\":\"d-1\",\"keycloakId\":\"kc-1\",\"eventTimestamp\":\"2026-07-23T12:00:00Z\"}",
 "payload_encoding":"string"}
EOF

curl -s -u verborum:verborum -H "content-type: application/json" -X POST -d @/tmp/event.json \
  "http://localhost:15672/api/exchanges/%2F/verborum.events/publish"
# -> {"routed":true}   ("routed":false means no queue is bound to that routing key)
```

## Observing an event a service publishes

Bind a temporary queue, trigger the action, then read the queue:

```bash
curl -s -u verborum:verborum -H "content-type: application/json" -X PUT \
  -d '{"durable":false}' "http://localhost:15672/api/queues/%2F/tmp.verify"
curl -s -u verborum:verborum -H "content-type: application/json" -X POST \
  -d '{"routing_key":"user.deleted"}' \
  "http://localhost:15672/api/bindings/%2F/e/verborum.events/q/tmp.verify"

# ... trigger the action ...

curl -s -u verborum:verborum -H "content-type: application/json" -X POST \
  -d '{"count":5,"ackmode":"ack_requeue_false","encoding":"auto"}' \
  "http://localhost:15672/api/queues/%2F/tmp.verify/get"

curl -s -u verborum:verborum -X DELETE "http://localhost:15672/api/queues/%2F/tmp.verify"
```

Do **not** use an `autoDelete` queue — it disappears after the first read and the second `get`
returns a confusing 404.

If the action can only be triggered through a secured endpoint you cannot reach, a throwaway
full-context test that autowires the real service and `RabbitAdmin` does the same job. Run it with
`-Dtest=`, read the printed payload, then **delete the file** — that is verification scaffolding,
not a test to keep.

## The dead-letter queue

A failing consumer retries three times and then dead-letters. Check it after any negative test:

```bash
curl -s -u verborum:verborum "http://localhost:15672/api/queues/%2F/verborum.dead-letter" \
  | tr ',' '\n' | grep '"messages":'

curl -s -u verborum:verborum -X DELETE \
  "http://localhost:15672/api/queues/%2F/verborum.dead-letter/contents"      # purge
```

## Databases

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -c "\dt"
docker exec verborum-db-user      psql -U coldtea -d vdbprofile    -c "\d vault_entries"

# -tAc gives bare values, handy in scripts
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -tAc "SELECT count(*) FROM words;"
```

Or Adminer at http://localhost:8080 — system *PostgreSQL*, server `db_dictionary` or `db_user`,
which are the compose service names, not `localhost`.

## Cleaning up after a session

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -tAc \
  "SELECT 'dicts='||count(*) FROM dictionaries; SELECT 'words='||count(*) FROM words;"
docker exec verborum-db-user psql -U coldtea -d vdbprofile -tAc \
  "SELECT 'users='||count(*) FROM users;"
```

`DELETE /users/{userId}` is the cleanest reset: it cascades to stats and vault rows, publishes
`user.deleted` (clearing that user's dictionaries and words in ms_dictionary), and removes the
Keycloak account if the admin secret is configured.

## Running the dev seed and Python probes without a local Python

The seed and reset scripts are plain Python 3 with no packages. Run them in a throwaway container that
reaches the host through `host.docker.internal` (Git Bash; `MSYS_NO_PATHCONV=1` keeps the paths intact):

```bash
H=host.docker.internal
MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W)/scripts/dev-seed:/seed" \
  -e SEED_KEYCLOAK_URL=http://$H:8180 -e SEED_USER_URL=http://$H:8086 \
  -e SEED_DICTIONARY_URL=http://$H:8085 -e SEED_MARKETPLACE_URL=http://$H:8087 \
  -e SEED_RABBIT_URL=http://$H:15672 python:3.12-slim python -u /seed/seed.py    # or reset.py
```

Keycloak keeps issuing tokens with `iss=http://localhost:8180/...` (the hostname is pinned), so tokens
obtained this way are accepted by the services. If container→host connections drop
(troubleshooting.md), mount a `sitecustomize.py` on `PYTHONPATH` that wraps `urllib.request.urlopen` to
retry `URLError`/timeouts only.

## Re-running the security probes

`docs/agent/security-audit-2026-10-05.md` lists each finding's Verify step. After a change in auth,
ownership, compose ports or the realm, re-run the probes that touch it:
- exposure checks against the **LAN IP**
- ownership checks with two seed users (e.g. a member and a non-member with private dictionaries)
- token checks with a service-account token (must be 401)
