# Local Development

How to actually run, test and verify this backend on a developer machine. Written 2026-07-23, after
Phases 0–3; the ports and credentials here are the local-dev ones baked into the compose file and the
realm import, and none of them are secrets.

The companion docs: `docs/ops/dockerization-and-environments.md` (the containerization *plan*),
`docs/agent/security.md` (the auth contract), `docs/integration/client-login-guide.md` (what clients
need).

---

## 1. Prerequisites

- **JDK 17.** The build is pinned to Java 17 and Boot 3.2.2.
- **Docker Desktop**, running. Everything except the services themselves lives in containers.

### JAVA_HOME is probably not set

On the current dev machine there is no `java` on `PATH` and `JAVA_HOME` is empty — the JDKs live
where IntelliJ put them (`~/.jdks/`). `mvnw` fails immediately with
*"The JAVA_HOME environment variable is not defined correctly"* until you set it per shell:

```powershell
# PowerShell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-17.0.18"
.\mvnw.cmd test
```
```bash
# Git Bash
export JAVA_HOME="$USERPROFILE/.jdks/ms-17.0.18"
./mvnw test
```

Check what you have with `ls ~/.jdks`. Setting `JAVA_HOME` permanently in your user environment is
the obvious fix; it is left unset here only because nobody has needed it outside the IDE.

---

## 2. Bring up the infrastructure

```bash
docker compose up -d          # from the repo root
docker compose ps             # all four should report healthy
```

| Service | Host port | Credentials | Notes |
|---|---|---|---|
| RabbitMQ | 5672 (AMQP), 15672 (UI) | `verborum` / `verborum` | Management UI at http://localhost:15672 |
| Postgres `vdbdictionary` | 5432 | `coldtea` / `qwerty` | ms_dictionary |
| Postgres `vdbprofile` | 5433 | `coldtea` / `qwerty` | ms_user |
| Keycloak | 8180 | `admin` / `admin` | realm `verborum`, imported from git |
| Adminer | 8080 | — | web DB client for both databases |

> **Run the root compose or a per-service compose, never both.** `ms_dictionary/` and `ms_user/`
> each have their own compose file for working on one service in isolation, and they bind the same
> host ports. The root file is the one to use for anything involving events or auth.

### Keycloak realm — imported, not clicked

`keycloak/import/verborum-realm.json` is the source of truth: realm, clients, roles and dev users.
`--import-realm` applies it **only on the first start of an empty data volume**, and changes you make
in the admin console are *not* written back to that file. After editing it:

```bash
docker compose down
docker volume rm verborum_ms_keycloak_data
docker compose up -d keycloak
```

---

## 3. Run a service

Services run on the host, not in containers (containerization is planned, not done — see the ops
doc).

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-17.0.18"
.\mvnw.cmd -pl ms_dictionary spring-boot:run     # :8085
.\mvnw.cmd -pl ms_user spring-boot:run           # :8086
```

Every host, port and credential in `application.properties` is `${ENV_VAR:default}`, so nothing needs
setting for a normal local run. Two exceptions worth knowing:

- **`KEYCLOAK_ADMIN_CLIENT_SECRET`** — without it, deleting a user profile leaves the Keycloak
  account alive and logs a WARN (see `ms_user/CLAUDE.md`). To exercise the real path:
  ```powershell
  $env:KEYCLOAK_ADMIN_CLIENT_SECRET = "local-dev-only-change-me"   # matches the realm import
  ```
- **`KEYCLOAK_HOSTNAME_URL` / `KEYCLOAK_ISSUER_URI` / `KEYCLOAK_JWK_SET_URI`** — only for testing
  from a phone. See §7.

---

## 4. Get a token

Every endpoint in both services requires a JWT. For curl and API testing, use the dev-only client:

```bash
curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli \
  -d username=testuser -d password=testuser -d grant_type=password
```

| User | Password | Realm roles |
|---|---|---|
| `testuser` | `testuser` | `user` |
| `testadmin` | `testadmin` | `user`, `admin` |

`verborum-dev-cli` exists **only** so a developer can skip the browser; it is not part of the auth
contract and must never appear in a shared realm. Real clients use `verborum-app` with
Authorization Code + PKCE.

Extract the token and the subject without jq or python:

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

# the subject — this is what every service stores as fk_user_id
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | sed 's/$/==/' | base64 -d 2>/dev/null \
  | sed -E 's/.*"sub":"([^"]+)".*/\1/'
```

Then: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/dictionaries/$SUB`

**Testing multi-user behaviour** (ownership rules, 403s) needs two tokens — get a second one as
`testadmin` and use them as "user A" and "user B".

---

## 5. Tests

```powershell
.\mvnw.cmd test                    # both modules
.\mvnw.cmd -pl ms_user test        # one module
.\mvnw.cmd -pl ms_user test "-Dtest=*ServiceImplTest" -DfailIfNoSpecifiedTests=false
```

**The `contextLoads` `@SpringBootTest` in each module needs the compose stack up** — it boots the
real application against the real Postgres. On a machine with no database it fails rather than
skipping. The pure unit tests do not need anything running.

Surefire reports are at `<module>/target/surefire-reports/`. Note that **stale reports linger** for
test classes that no longer exist or were not selected by a `-Dtest` filter — check the file
timestamps before trusting a green summary you did not just produce.

---

## 6. Verifying events by hand

Unit tests mock `RabbitTemplate`, so they prove the code calls it, not that anything reaches the
broker. Two techniques have been used for genuine end-to-end proof; both are worth knowing.

### Publishing an event into a service (testing a consumer)

The management API publishes without any application involvement. Note the `__TypeId__` header: set
it to the *publisher's* class name to prove the consumer's `INFERRED` type mapper works — that header
naming a class the consumer does not have is exactly the cross-service case.

```bash
cat > /tmp/event.json <<'EOF'
{"properties":{"content_type":"application/json","headers":{"__TypeId__":"de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent"}},
 "routing_key":"dictionary.imported",
 "payload":"{\"dictionaryId\":\"d-1\",\"keycloakId\":\"kc-1\",\"eventTimestamp\":\"2026-07-23T12:00:00\"}",
 "payload_encoding":"string"}
EOF

curl -s -u verborum:verborum -H "content-type: application/json" -X POST -d @/tmp/event.json \
  "http://localhost:15672/api/exchanges/%2F/verborum.events/publish"
# -> {"routed":true}   ("routed":false means no queue is bound to that routing key)
```

### Observing an event a service publishes

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

curl -s -u verborum:verborum -X DELETE "http://localhost:15672/api/queues/%2F/tmp.verify"   # clean up
```

Do **not** use an `autoDelete` queue for this — it disappears after the first read and the second
`get` returns a confusing 404.

If the action can only be triggered through a secured endpoint you cannot reach, a throwaway
`@SpringBootTest` in the module that autowires the real service and `RabbitAdmin` does the same job.
Write it, run it with `-Dtest=`, read the printed payload, then **delete the file** — these are
verification scaffolding, not tests to keep.

### Checking the dead-letter queue

A failing consumer retries three times and then dead-letters. After any negative test:

```bash
curl -s -u verborum:verborum "http://localhost:15672/api/queues/%2F/verborum.dead-letter" \
  | tr ',' '\n' | grep '"messages":'
curl -s -u verborum:verborum -X DELETE \
  "http://localhost:15672/api/queues/%2F/verborum.dead-letter/contents"    # purge
```

---

## 7. Testing from a phone or another machine

Keycloak stamps the issuer into every token. Unpinned it echoes back whatever `Host` the caller used,
so a device on a LAN address gets tokens the services reject — **a 401 that looks like a bad token
but is a configuration mismatch.** Set all three to the same origin and restart:

```
KEYCLOAK_HOSTNAME_URL=http://<lan-ip>:8180                                          # keycloak
KEYCLOAK_ISSUER_URI=http://<lan-ip>:8180/realms/verborum                            # each service
KEYCLOAK_JWK_SET_URI=http://<lan-ip>:8180/realms/verborum/protocol/openid-connect/certs
```

Find the LAN address with `ipconfig` (ignore the `172.x` WSL/Hyper-V ones). Full explanation in
`docs/integration/frontend-backend-integration.md` §6.2a.

---

## 8. Looking at the databases

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -c "\dt"
docker exec verborum-db-user      psql -U coldtea -d vdbprofile    -c "\d vault_entries"

# -tAc gives bare values, handy in scripts
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -tAc "SELECT count(*) FROM words;"
```

Or use Adminer at http://localhost:8080 (system *PostgreSQL*, server `db_dictionary` or `db_user` —
the compose service names, not `localhost`).

### Test-data hygiene

There is **no fixture or seed script**; manual verification has meant creating rows through the API
and deleting them afterwards. Get into the habit of ending a session with the counts at zero:

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -tAc \
  "SELECT 'dicts='||count(*) FROM dictionaries; SELECT 'words='||count(*) FROM words;"
docker exec verborum-db-user psql -U coldtea -d vdbprofile -tAc \
  "SELECT 'users='||count(*) FROM users;"
```

Deleting a user through `DELETE /users/{userId}` is the cleanest reset: it cascades to stats and
vault rows, publishes `user.deleted` (which clears that user's dictionaries and words in
ms_dictionary), and removes the Keycloak account if the admin secret is configured.

---

## 9. Troubleshooting

| Symptom | Cause |
|---|---|
| `mvnw`: "JAVA_HOME is not defined correctly" | Not set — §1 |
| `contextLoads` fails, everything else passes | Compose stack is not up |
| 401 with a token that looks fine | Issuer mismatch — §7 |
| 401 on every endpoint after a client update | Expected since P3-03: ms_dictionary requires a bearer token |
| 403 on your own data | The `userId` you sent is not the JWT `sub` (P3-05). Check for the guest UUID |
| Another user's dictionary reads as 404 | Deliberate — a 403 would confirm the id exists (P3-08) |
| `/actuator/env` 404s | Deliberate since P3-06; only `health` and `info` are exposed |
| Consumer fails with `ClassNotFoundException` | The `INFERRED` type mapper is missing from that service's `RabbitMQConfig` |
| Realm edits have no effect | The import only runs on an empty volume — §2 |
| Event published, nothing consumed | `"routed":false`, or no queue bound to the routing key |
| `Cannot invoke "…getUserId()"` on a cascade | The event's `keycloakId` was used where `userId` was expected, or vice versa |
