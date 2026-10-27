# Local Development

How to actually run, test and verify this backend on a developer machine. Written 2026-07-23, after
Phases 0–3; the ports and credentials here are the local-dev ones baked into the compose file and the
realm import, and none of them are secrets.

The companion docs: `docs/ops/dockerization-and-environments.md` (the containerization *plan*),
`docs/agent/security.md` (the auth contract), `docs/integration/client-login-guide.md` (what clients
need).

---

## 1. Prerequisites

- **JDK 17.** The build is pinned to Java 17 and Boot 3.5.16.
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
| Adminer | 8090 | — | web DB client for the databases (8080 is `ms_gateway`'s) |

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

### Upgrading from Keycloak 23 (2026-10-05, SEC-08)

The stack now runs **Keycloak 26.8.0**. After pulling, rebuild the custom image once, then start as usual:

```bash
docker compose up -d --build
```

An existing `keycloak_data` volume is migrated in place on first start, realms and users included
(watch for `migrated realm verborum` in `docker logs verborum-keycloak`). The compose file pins the
old H2 credentials (`KC_DB_USERNAME`/`KC_DB_PASSWORD`), because Keycloak 25+ changed the defaults and
would otherwise refuse the volume with "Wrong user name or password". `keycloak-bootstrap` then
re-applies themes, token policy, the audience mapper and the email-code flow. If the migration fails
for any reason, recreate the volume with the commands above and re-run `scripts/dev-seed/seed.py`.

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

- **`KEYCLOAK_ADMIN_CLIENT_SECRET`** — the default is the local realm secret, so deleting a user profile also
  deletes the Keycloak account with no setup. Set the variable only for a shared realm. An empty value
  switches the identity delete off: the profile and the data go, but the person can still sign in (WARN in
  the log; see `ms_user/CLAUDE.md`).
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

**Every container port is published on `127.0.0.1` only (SEC-02).** The defaults (`admin`/`admin`,
`verborum`/`verborum`, `qwerty`) are well known, and on all interfaces anyone on the same Wi-Fi could
take over Keycloak, forge RabbitMQ events, read login codes in Mailpit and open the databases. The
emulator is unaffected: `10.0.2.2` and `adb reverse` both land on the host's loopback.

A **physical device** needs Keycloak on the LAN. Use the opt-in override, which exposes Keycloak
alone and refuses to start without a real admin password and the LAN issuer in `.env`:

```bash
# .env:  KEYCLOAK_ADMIN_PASSWORD=<not admin>   KEYCLOAK_HOSTNAME_URL=http://<lan-ip>:8180
docker compose -f docker-compose.yml -f docker-compose.lan.yml up -d
```

`KEYCLOAK_ADMIN_PASSWORD` only takes effect on a fresh `keycloak_data` volume. On an existing one,
change the admin password in the console (master realm → Users → admin → Credentials) first. The
three services run on the host and already listen on all interfaces; they require a JWT for
everything but health and Swagger. Go back to loopback-only with a plain `docker compose up -d`.

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

## 7a. The local edge: HTTPS, the gateway and the login rate limit (P5-04)

Caddy sits in front of the gateway and Keycloak. It does two jobs: TLS and a brute-force limit on the login and
token endpoints. It is **opt-in**, because it changes the token issuer.

| Name | Goes to | Notes |
|---|---|---|
| `https://api.verborum.localhost` | `ms_gateway` on the host, `:8080` | the one origin for every client |
| `https://auth.verborum.localhost` | Keycloak | only `/realms/*` and `/resources/*`; the admin console and the master realm answer 404 |

`*.localhost` resolves to 127.0.0.1 in a browser without a hosts-file entry. Change the names with
`CADDY_API_HOST` and `CADDY_AUTH_HOST` in `.env`.

**1. Start Keycloak and Caddy** (this recreates the Keycloak container with the HTTPS issuer):

```bash
docker compose -f docker-compose.yml -f docker-compose.edge.yml up -d --build caddy keycloak
```

**2. Trust the local CA once.** Caddy makes its own certificate authority. Without trust, every client says "invalid
certificate". Export the root and add it to the system store (macOS shown; this one step needs your password):

```bash
docker cp verborum-caddy:/data/caddy/pki/authorities/local/root.crt ./caddy-local-root.crt
sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain ./caddy-local-root.crt
```

Without trusting it, `curl --cacert ./caddy-local-root.crt ...` works. Delete the certificate again when you stop
using the edge. The Android emulator and the iOS simulator need the same root installed in their own stores.

**3. Start the gateway and every service with the HTTPS issuer.** Tokens now carry
`iss=https://auth.verborum.localhost/realms/verborum`, and a service that expects `http://localhost:8180` answers 401.
Only the issuer changes. Keep the JWK set on plain `localhost:8180`, so no JVM has to trust the local CA:

```
KEYCLOAK_ISSUER_URI=https://auth.verborum.localhost/realms/verborum
# KEYCLOAK_JWK_SET_URI stays http://localhost:8180/realms/verborum/protocol/openid-connect/certs
```

**4. Check it.**

```bash
TOKEN=$(curl -s --cacert ./caddy-local-root.crt -X POST https://auth.verborum.localhost/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
curl --cacert ./caddy-local-root.crt -H "Authorization: Bearer $TOKEN" https://api.verborum.localhost/users/me
```

**The rate limit.** `AUTH_RATE_EVENTS` (default 30) per `AUTH_RATE_WINDOW` (default `1m`) per client address, on
`POST` to the token endpoint and the login form. Over the limit the answer is `429` with `Retry-After`. Lower it
to see it work: `AUTH_RATE_EVENTS=5` in `.env`, then recreate Caddy.

**Back to the plain setup:** `docker compose up -d keycloak` (without the edge file) and start the services with
the default issuer again. The browser keeps nothing; only the trusted certificate remains until you delete it.

## 7b. End-to-end pass through the gateway (P5-13)

Run this after any change to the gateway, the edge, a route, or the clients' API configuration. A script proves what
a machine can prove; the apps are checked by hand.

**1. Start everything.** The compose stack, the four services, and the gateway (`./mvnw -pl ms_gateway spring-boot:run`,
port 8080). For the HTTPS edge (§7a) start Caddy too; the services then need the HTTPS issuer.

**2. Run the script.**

Plain: the gateway on `:8080` and Keycloak on `:8180`. Expect every check to PASS.

```bash
python3 scripts/edge-smoke/smoke.py --writes
```

Through Caddy (§7a), with the exported local CA:

```bash
python3 scripts/edge-smoke/smoke.py --edge --cacert caddy-local-root.crt --writes --rate-limit
```

Copy only the command, not any text after it. An interactive zsh does not treat `#` as a comment, so a pasted trailing
comment becomes arguments and the script stops with "unrecognized arguments".

It checks the token and the refusals (no token, tampered token, token in the query string), the CORS answers for the web
origin, that every route reaches its service, that a service's own errors pass through, that a forged `X-User-Id` changes
nothing, and a write with a body. `--rate-limit` bursts the token endpoint until Caddy answers 429 (wait a minute
afterwards). Exit code 1 on any failure. It uses the dev account `testuser` and a probe device it removes again.

**3. Check the apps by hand.** Every app must do the same six things through the gateway:

| # | Flow | How to see it |
|---|---|---|
| 1 | Login | sign in with `testuser`; the library opens |
| 2 | Sync | create a dictionary on one app, it appears on another after a refresh |
| 3 | Forum | browse, open a listing, download one, rate it |
| 4 | Silent push | edit a dictionary on app A; app B updates within seconds without opening the Forum (needs `ms_notification` with `FIREBASE_CREDENTIALS_FILE`) |
| 5 | Unsubscribe | remove a downloaded dictionary on A; B drops it too |
| 6 | Sign out | the login wall returns, the device is unregistered |

| App | How to run it against the gateway |
|---|---|
| Android emulator | debug build. API `http://10.0.2.2:8080/`; Keycloak needs `adb reverse tcp:8180 tcp:8180` |
| Android phone | a release build with `-PverborumApiUrl=http://<lan-ip>:8080/`. The release `KEYCLOAK_ISSUER` in `core/build.gradle.kts` must name the same LAN origin. Keycloak, the gateway and the services use the LAN issuer (§7); the gateway listens on every interface, so allow port 8080 in the firewall |
| Web | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (port 8280). In the browser's Network tab every API call shows an `OPTIONS` 200 first and no CORS error in the console. Only `/auth` goes through the dev-server proxy |
| iOS simulator | run `iosApp` from Xcode (debug binary). API `http://localhost:8080`, Keycloak `http://localhost:8180` (the `NSAllowsLocalNetworking` exception covers both) |

**4. Record the cost.** Server sizing (`P5R-01`) uses real numbers. With everything above running and the apps idle:

```bash
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
footprint -p $(pgrep -f MsGatewayApplication | head -1) | grep Footprint:
```

`footprint` is macOS only. Repeat it for each service (`MsDictionaryApplication`, `MsUserApplication`, …). `ps` under-reports JVMs on macOS (compressed memory); use `footprint`. Latest baseline:

| When | Component | Memory |
|---|---|---|
| 2026-10-17 | `ms_dictionary`, `ms_user`, `ms_marketplace`, `ms_notification` (host JVMs from the IDE, C1-only JIT, idle) | 305 / 314 / 329 / 308 MB |
| | `ms_gateway` (after a smoke run) | 205 MB |
| | Keycloak 23.0.0 (the running container) / Keycloak 26.8.0 (edge test project) | 563 MiB / 626 MiB |
| | RabbitMQ | 197 MiB |
| | four Postgres containers | 32–38 MiB each |
| | Caddy (edge test project) | 19 MiB |
| | Adminer, Mailpit | 19 / 38 MiB |
| | **Total** | **about 2.5 GB** (JVMs 1.46 GB, containers 0.96 GB, plus Keycloak 26's extra 0.06 GB and Caddy 0.02 GB) |

These are development numbers: the services have no `-Xmx`, the data is small, and nothing was under load. A server runs
the same JVMs with explicit limits, and one Postgres can replace four containers — see
`docs/ops/dockerization-and-environments.md` §11 and §12.

## 8. Looking at the databases

```bash
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -c "\dt"
docker exec verborum-db-user      psql -U coldtea -d vdbprofile    -c "\d vault_entries"

# -tAc gives bare values, handy in scripts
docker exec verborum-db-dictionary psql -U coldtea -d vdbdictionary -tAc "SELECT count(*) FROM words;"
```

Or use Adminer at http://localhost:8090 (system *PostgreSQL*, server `db_dictionary` or `db_user` —
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
