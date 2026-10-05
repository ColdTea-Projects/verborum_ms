# Security Audit — Backend, Keycloak and Local Infrastructure (2026-10-05)

**Scope:** `ms_dictionary`, `ms_user`, `ms_marketplace` at `a8807db`, the Keycloak realm and the
`verborum-email-code` SPI, the root `docker-compose.yml`. Client apps are covered separately in
`docs/integration/client-findings-2026-10-05.md`.

**Method:** a static read of every controller, the ownership checks behind it, the three
`SecurityConfig`s, the realm JSON and bootstrap scripts, the email-code authenticator and the compose
file; then **live probes** against the running stack loaded with `scripts/dev-seed/seed.py` data.
The probes ran from a separate Docker container, which stands in for "another machine on the same
network". Every finding marked **verified** was reproduced live; the rest come from reading the code
or config.

**How to use this file:** each finding has an id (`SEC-xx`), a severity, the evidence, the fix and
how to prove the fix. The roadmap tracks them under "Security fixes (audit 2026-10-05)". Fix in id
order unless a dependency says otherwise. A fix is done when its **Verify** step passes and a
regression test exists where one is named.

| Id | Severity | Area | One line |
|---|---|---|---|
| SEC-01 | **Critical** — **fixed** | ms_dictionary | Any user could overwrite and take over anyone's word by `wordId` |
| SEC-02 | **High** — **fixed** | compose | All infrastructure listened on every network interface with default credentials |
| SEC-03 | **High** (before prod) | RabbitMQ | Every service shares one broker user; whoever holds it can forge `user.deleted` |
| SEC-04 | Medium — **fixed** | ms_user | The profile e-mail came from the request body, so it could be squatted |
| SEC-05 | Medium | all services | No audience check: a token from any realm client is accepted |
| SEC-06 | Medium | Keycloak | Refresh tokens can be reused, and offline sessions never expire |
| SEC-07 | Medium — **fixed** | ms_dictionary | No limit on request collection sizes, and no per-user quotas |
| SEC-08 | Medium | build | Spring Boot 3.2.2 and Keycloak 23.0.0 are past end of support |
| SEC-09 | Low–Medium — **fixed** | ms_user | `POST /users/{id}/vault` bypassed the marketplace and the Forum gate |
| SEC-10 | Low — **done** | ms_user | Reserved words (Verborum, admin, …) blocked in display names; names stay non-unique by design |
| SEC-11 | Low (prod blocker) | Keycloak | Realm defaults that must not reach a shared realm |
| SEC-12 | Low | all services | Swagger is open in every environment; errors expose exception names |
| SEC-13 | Low | ms_user | Account deletion accepts any valid access token, with no fresh login |
| SEC-14 | Info | ms_dictionary / ms_user | Responses that break the ownership-status table |

---

## SEC-01 — Word takeover through `POST`/`PUT /words` (Critical, verified) — **FIXED 2026-10-05**

**Status:** fixed in `WordServiceImpl.saveWords`. A `wordId` that already exists under a different
dictionary is refused with 403 `WORD_IN_ANOTHER_DICTIONARY` before anything is saved. That includes
moving a word between two of the caller's own dictionaries, which no client does. Two regression
tests are in `WordServiceImplTest`, and the 403 mapping is covered by the web slice. **Verified live:** the probe
below now gets 403, and the victim's word count is unchanged.

**What:** `WordServiceImpl.saveWords` (`ms_dictionary/.../word/service/impl/WordServiceImpl.java`,
around line 49) checks that the caller owns the **target** dictionary named in each bundle. It never
checks who owns an **existing** word whose `wordId` the request reuses. `saveAllAndFlush` then
upserts the row: the stranger's word is overwritten with the caller's content and moved into the
caller's dictionary. It disappears from its real owner's dictionary.

**Why it is critical:** since P4-10 every logged-in user can read the words of every public
dictionary, `wordId` included. One script can therefore empty and vandalise every dictionary in the
Forum. A private dictionary is only protected by its word ids being unguessable.

**Evidence (live):** `nina.koch` sent a bundle for her own dictionary containing a `wordId` from
`mia.schulz`'s private dictionary → `200`. Mia's dictionary went from 10 words to 9, and the word now
sits in Nina's dictionary with the text `["PWNED"]`.

**Fix:**
1. In `saveWords`, load the already-stored words (the `findAllById` call is already there) and resolve
   each one's dictionary owner, in one batched dictionary query.
2. If any existing word belongs to a dictionary the caller does not own → `ForbiddenOperationException`
   (403). That is the write rule in `.claude/skills/security/references/ownership-rules.md`.
3. Also refuse an existing word whose `dictionaryId` would change, even between two of the caller's
   own dictionaries, unless moving words is a deliberate feature. Today nothing announces a move.
4. Do the check before `saveAllAndFlush`, so nothing is written on refusal.

**Verify:**
- Unit test in `WordServiceImplTest`: a bundle containing another owner's existing `wordId` → 403 and
  `saveAllAndFlush` is never called.
- Web-slice test: the same request → 403.
- Live: repeat the probe above; the victim's word count must not change.

---

## SEC-02 — Local infrastructure is reachable from the network with default credentials (High, verified)

**Status — FIXED 2026-10-05.** Every port in the root and per-service compose files is now
`127.0.0.1:…`. `docker-compose.lan.yml` (opt-in) exposes Keycloak alone, via `ports: !override`, and
refuses to start without `KEYCLOAK_ADMIN_PASSWORD` and `KEYCLOAK_HOSTNAME_URL`. **Verified from the
LAN address** (`192.168.100.16`): 5432–5434, 5672, 15672, 1025, 8025, 8080 and 8180 are all closed;
with the override only 8180 opens, and the issuer becomes the LAN origin. The services on 8085–8087
stay on all interfaces by design (JWT-protected). Note: containers on the same machine still reach
loopback ports through Docker Desktop's `host.docker.internal`. That is not network exposure, but it
means the original container-based probe below was a stand-in for "another machine". Docs:
`docs/ops/local-development.md` §7 and `infra-ops` → `references/local-stack.md`. Still open from the
fix list: point 3 only matters with the override, and is enforced there.

**What:** every `ports:` entry in the root `docker-compose.yml` uses the `"host:container"` form,
which binds to `0.0.0.0`. On any shared Wi-Fi, anyone can reach:

| Port | Service | Default credentials | What an attacker can do (verified from another container) |
|---|---|---|---|
| 8180 | Keycloak (admin console + master realm) | `admin` / `admin` | Got a master-realm admin token: full control of every account |
| 15672 / 5672 | RabbitMQ | `verborum` / `verborum` | Published a forged `user.deleted`: the victim's dictionaries were deleted (see SEC-03) |
| 8025 | Mailpit UI | none | Read every e-mail, including **passwordless login codes**, so can log in as any user |
| 5432–5434 | PostgreSQL | `coldtea` / `qwerty` | Read and write every database |
| 8080 | Adminer | — | Web UI for the above |

The Android release build already points at a LAN address (`192.168.0.241`), so the stack does get
run reachable from the LAN.

**Fix:**
1. Bind every infrastructure port to loopback by default: `"127.0.0.1:5432:5432"` and so on, for the
   Postgres, RabbitMQ, Adminer and Mailpit ports.
2. Add an opt-in `docker-compose.lan.yml` override for physical-device testing. It may publish only
   Keycloak's `8180`. The three services run on the host and already listen on all interfaces;
   document that the Windows firewall decides who reaches them.
3. Make `KEYCLOAK_ADMIN_PASSWORD` and the RabbitMQ password come from `.env` with no
   `admin`/`verborum` fallback when the LAN override is used. Add placeholders to `.env.example`.
4. Update `docs/ops/local-development.md` (port map and the phone-testing section) and the
   `infra-ops` skill.

**Verify:** with the default compose up, `curl http://<LAN-IP>:15672` from another device fails;
with the override, only 8180 answers.

---

## SEC-03 — One shared broker identity; message senders are not authenticated (High before any shared environment, verified)

**What:** all three services connect to RabbitMQ as the same user, and listeners trust any message
on `verborum.events`. Anyone holding that one credential can publish `user.deleted`,
`dictionary.imported`, `dictionary.visibility.*` and so on, and each service acts on it.

**Evidence (live):** a `user.deleted` message published through the management API with
`verborum`/`verborum`, carrying only a target's `keycloakId` → `{"routed": true}`, and three seconds
later the target's dictionaries were gone.

**Good already:** the converter uses `TypePrecedence.INFERRED`, so the `__TypeId__` header cannot pick
a class to deserialise into. There is no deserialisation-gadget risk.

**Fix (needed before any non-local environment; locally SEC-02 is enough):**
1. One RabbitMQ user per service. Use topic permissions so each user can **write** only its own
   routing keys:
   - ms_user: `user.*`
   - ms_dictionary: `dictionary.*` and `word.*`
   - ms_marketplace: `dictionary.imported`

   and **read** only its own queues. Configure them via `definitions.json` or a bootstrap script, with
   passwords from env vars.
2. Document it in `docs/agent/rabbitmq.md` and the `messaging` skill as a new rule.

**Verify:** publishing `user.deleted` with ms_dictionary's credentials is refused (`access_refused`).

---

## SEC-04 — The profile e-mail is not bound to the token: e-mail squatting (Medium, verified)

**Status — FIXED 2026-10-05.** `SecurityUtils.getVerifiedEmail()` reads the token's `email` and
`email_verified` claims and returns 403 `EMAIL_NOT_VERIFIED` when either is missing or false (a
service-account token has none). `saveUser` takes it as an explicit argument, returns 400
`EMAIL_NOT_THE_TOKENS` for a body e-mail that differs (case-insensitive), and stores the token's form.
140/140 ms_user tests. **Verified live:** squatting → 400, own e-mail in different case → 201,
service-account token → 403.

**What:** `POST`/`PUT /users/` takes `email` from the request body (`UserRequestDTO.email`), and
`UserServiceImpl.requireNoConflictingProfile` enforces it as unique. The check that `keycloakId`
equals the token's `sub` exists; nothing ties `email` to the token.

**Evidence (live):** user A created a profile with an address that wasn't theirs → accepted (2xx). When the
real owner of that address later created their profile → `409 ProfileConflictException: This email
is already used by another profile`. Any user can block anyone else's sign-up this way, and a
profile's e-mail is not trustworthy for anything (support contact, future notifications).

**Fix:**
1. Take the e-mail from the JWT `email` claim and ignore the body field. If you keep the body field
   for compatibility, return `400` when it differs from the claim.
2. Require `email_verified == true` in the token for profile creation; otherwise `403`.
3. Add `getCurrentEmail()` to ms_user's `SecurityUtils`, and pass it into the service explicitly like
   the subject.

**Client impact:** none, as long as clients already send the token's own e-mail, which Android does.
Record this in the client findings file.

**Verify:** unit and web-slice tests for "body e-mail ≠ token e-mail → 400" and
"unverified → 403". Live: rerun the squatting probe.

---

## SEC-05 — No audience (`aud`/`azp`) validation (Medium, verified)

**What:** all three resource servers accept any token signed by the realm. Access tokens from the
app carry `aud=account`. A **client-credentials token of `verborum-backend`** (`aud=realm-management`)
was accepted by ms_user as an authenticated user (`GET /users/me` → 404, which means it got past
authentication). Any client added to the realm later, such as an admin tool or an integration, would
also get API access.

**Fix:**
1. Realm: add an audience mapper (`oidc-audience-mapper`, included audience `verborum-api`) to the
   `verborum-app` client, and to `verborum-dev-cli` for local use. Do it in the realm JSON *and* in
   `keycloak/bootstrap/configure.sh`, because existing realms are not re-imported.
2. Each service: a `JwtDecoder` bean with
   `JwtValidators.createDefaultWithIssuer(issuer)` plus a `JwtClaimValidator` requiring `verborum-api`
   in `aud`. Put the audience in a property (`VERBORUM_JWT_AUDIENCE`, default `verborum-api`).
3. Update `.claude/skills/security/references/resource-server-config.md` and
   `docs/agent/security.md`.

**Client impact:** none if the mapper sits on the client. Tokens issued before the change fail until
refreshed, which happens within 5 minutes.

**Verify:** a `verborum-backend` client-credentials token → 401 on every service, and an app token
still → 200. Add this to each `SecurityConfigTest`.

---

## SEC-06 — Refresh-token reuse allowed; offline sessions never expire (Medium, verified)

**What:** in `verborum-realm.json`, `revokeRefreshToken` is unset (false),
`offlineSessionMaxLifespanEnabled` is unset (false) and `offlineSessionIdleTimeout` is 60 days. All
clients request `offline_access`.
- **Live:** the same refresh token was exchanged **twice** successfully.
- The offline token has no `exp`. A stolen refresh token (for example from web `localStorage`, see the
  client file) works forever as long as it is used once every 60 days, and the owner never finds out.

**Fix:**
1. Realm: `revokeRefreshToken: true` and `refreshTokenMaxReuse: 0`, so reusing a rotated token kills
   the session. Also `offlineSessionMaxLifespanEnabled: true` with
   `offlineSessionMaxLifespan: 15552000` (180 days); confirm the number with the product owner.
2. Apply it in `configure.sh` for existing realms too.
3. **Coordinate with the clients first:** with rotation on, every refresh returns a new refresh token
   that must be stored atomically. Two refreshes racing with the old token log the user out. Android
   and KMP are close to correct already; the client findings file says exactly what to check.

**Verify:** the second use of a refresh token → `400 invalid_grant`, and the session is revoked.

---

## SEC-07 — No size limits on collections or totals (Medium, verified)

**Status — FIXED 2026-10-05.** Both clients upload one word per request and never call the batch
reads, so the limits are tight without touching them:
- **Collections:** at most 5 bundles per request, 500 words per bundle, 100 ids per batch read. Each is a
  400 that names the field.
- **Quotas:** 1,000 dictionaries per account and 5,000 words per dictionary, counted on new rows only.
  Over the quota is 400 `QuotaExceededException`.
- **Body size:** `RequestBodyLimitFilter`, in ms_dictionary and ms_user, refuses a body over
  `verborum.request.max-body-bytes` (`MAX_REQUEST_BODY_BYTES`, default 2 MB) with 413 before Jackson
  reads it, and a chunked body with no `Content-Length` with 411.

Suites: ms_dictionary 155, ms_user 139. **Verified live:** 501 words → 400; 2.2 MB → 413 in 17 ms;
chunked → 411; one word → 201. Rate limiting is still open; that is `P5-04` at the gateway.

**What:** fields have `@Size` limits (P3-07), but the lists have none: `POST /words` (bundles × words),
`GET /dictionaries/batch` and `GET /words/batch` (a batch of 300 ids was rejected by Tomcat's URL
length limit, not by design). There are also no per-user caps on dictionaries or words, and no rate
limiting (that is `P5-04`).

**Evidence (live):** one `POST /words` with 5,000 words → `200` after **14.1 s** on one request
thread. A handful of parallel requests ties up the service and grows the database without bound.

**Fix (the limits below are proposals; confirm them with the product owner):**
1. `@Size(max = 500)` on `words` in `WordBundleRequestDTO`, and `@Size(max = 20)` on the bundle list in
   `WordController` (method validation, as `MarketplaceController` already does it).
2. `@Size(max = 100)` on the `ids` list of both batch endpoints.
3. A per-user quota checked on create: for example 1,000 dictionaries and 5,000 words per dictionary,
   as properties.
4. Add a small request-body size limit (for example a 2 MB filter, plus `server.tomcat.max-swallow-size`)
   in each service.

**Client impact:** sync must split word uploads into chunks of at most the limit. It goes in the
client file.

**Verify:** a 501-word bundle → 400 with the field named. Web-slice tests for each limit.

---

## SEC-08 — End-of-support framework versions (Medium)

**What:** Spring Boot **3.2.2** (January 2024; open-source support for 3.2 ended late 2024) brings in
Spring Framework, Spring Security and Tomcat versions with published CVEs fixed in later releases.
Keycloak **23.0.0** (November 2023) has since had several security releases. Among them are fixes to
redirect-URI validation and session handling, both of which this project depends on.

**Fix:**
1. Add an OWASP dependency check (`org.owasp:dependency-check-maven`) or `mvn versions:display-dependency-updates`
   to the build. Record the actual CVE list it reports; this audit did not run one.
2. Plan an upgrade to a supported Boot line (3.4/3.5) and Keycloak 26. Keycloak is the bigger job:
   the SPI against the new APIs, the theme templates, `KC_HOSTNAME` v2 options, and `kcadm` script
   flags.
3. Add a roadmap task. This is a planned upgrade, not a hot fix.

**Verify:** the dependency check runs in the build with no high or critical findings, or with
documented suppressions.

---

## SEC-09 — Direct vault writes bypass the marketplace (Low–Medium, verified)

**Status — FIXED 2026-10-05.** The `POST` endpoint and the public `VaultService.addVaultEntry` are gone;
the find-or-create helper stays private behind `importDictionary`. Removing it exposed a second bug: in
**all three services** a wrong HTTP method fell through to the catch-all as a **500** with a stack trace.
Each `GlobalExceptionHandler` now maps `HttpRequestMethodNotSupportedException` → **405**
(`METHOD_NOT_ALLOWED`). Tests: `VaultControllerWebTest` (new) plus a 405 test in the ms_dictionary and
ms_marketplace web slices. Suites: 143 + 139 + 148. **Verified live:** direct `POST` → 405, `PATCH
/dictionaries/` → 405, and a marketplace import still adds the vault entry.

**What:** `POST /users/{userId}/vault` (`VaultController`) checks only that the path user is the
caller. It accepts **any** `dictionaryId`. Verified live:
- A member added another user's **private** dictionary id → accepted (2xx).
- A **non-member** added a dictionary directly → accepted (2xx).

That skips the Forum gate, the import count and the "not your own" rule. Reading is still protected,
because ms_dictionary returns 404 for private dictionaries. The impact is polluted vault data and
skipped business rules, not a leak.

**Fix:** imports already reach the vault through `dictionary.imported` (`VaultServiceImpl.importDictionary`).
Remove the `POST` endpoint, or restrict it to an internal role. Neither client calls it; the
marketplace guide tells clients to use `POST /marketplace/dictionaries/{id}/import`. Keep `GET` and
`DELETE`.

**Verify:** `POST /users/{id}/vault` → 405 (removed) or 403. The import path still adds the entry.

---

## SEC-10 — Reserved display names (Low) — **DONE 2026-10-05**

**Decision (product owner):** display names stay **non-unique on purpose**, so two users can both be
"Anna Bauer". What is blocked is a name that reads as the platform speaking.

**Built:** `ms_user/.../common/utils/DisplayNameUtils.isReserved`, checked in both write paths
(`POST`/`PUT /users/` and `PUT /users/me/profile-info`) → 400 `InvalidProfileException` with
`DISPLAY_NAME_RESERVED`.
- Blocked **anywhere in the name**, separators ignored: `verborum`, `coldtea`, `admin`, `moderator`,
  `official`.
- Blocked **as a whole word** only, because they are common inside real names: `support`, `staff`,
  `team`, `system`, `mod`, `root`, `security`, `help`, `helpdesk`, `bot`.
- Matching folds case (`Locale.ROOT`), accents (NFKD), look-alike characters (`4dm1n`, `$upport`) and
  separators (`V e r b o r u m`).
- Known trade-off: "admin" also catches "Badminton".
- A stored name that predates the rule is kept, so an unrelated `PUT` re-sending it doesn't fail.
  Only a changed name is judged.

**Verified:** `DisplayNameUtilsTest` (50 cases) and 5 `UserServiceImplTest` cases. Live:
- "Verborum Team", "4dm1n" and "Anna Support" → 400.
- A second "Lukas Schmidt" → 201.

---

## SEC-11 — Realm settings that must not reach a shared or production realm (Low locally, blocks prod)

These are known in part from `BL-03`; collected here so one task can close them all.

| Setting (`keycloak/import/verborum-realm.json`) | Now | Required outside local |
|---|---|---|
| `sslRequired` | `none` | `external` (or `all`) |
| `verborum-app` redirect URIs | `de.coldtea.verborum://oauth2redirect/*`, **`http://localhost:*`** (live: an arbitrary localhost port was accepted) | the app scheme plus the exact HTTPS web origin path; no `localhost` |
| Users `testuser`/`testuser`, `testadmin`/`testadmin` (role `admin`) | in the import | must not exist; the `admin` role is unused by any service, so drop it or give it a meaning |
| `verborum-dev-cli` (password grant) | in the import | absent (already `BL-03`) |
| `verborum-backend` secret | `local-dev-only-change-me` committed; the service account has `manage-users` | a rotated secret from env (already `BL-03`). `manage-users` is the narrowest built-in role that can delete users, so guard the secret accordingly |
| `eventsEnabled` / `adminEventsEnabled` | off | on, with an expiry, so logins, failures and admin changes can be audited |
| `passwordPolicy` | `length(8) and notEmail(undefined)` | add `notUsername` and `passwordHistory`, plus complexity, per `BL-03` |

**Fix:** create a separate realm import for non-local environments, or apply these values in a
bootstrap step keyed on an `ENVIRONMENT` variable. Never put secrets in the JSON (see
`docs/agent/security.md` and the realm-import rule).

---

## SEC-12 — Swagger open everywhere; errors show internals (Low)

- `/v3/api-docs` and `/swagger-ui/**` are `permitAll` in all three services, in every environment.
  Fix: `springdoc.api-docs.enabled=${SWAGGER_ENABLED:true}` and the same for `swagger-ui.enabled`;
  production sets `false`.
- Error bodies expose exception class names and parser text. For example
  `"error":"HttpMessageNotReadableException","errorDetail":"JSON parse error: Unexpected end-of-input…"`.
  The catch-all already hides the message, which is good. Consider mapping `error` to a stable code
  (`BadRequest`, `ValidationFailed`) rather than the class name. **Clients match on `error` today**
  (`SharingRequiredException`, `ProfileConflictException`), so keep those exact strings or announce
  the change. Low priority.

## SEC-13 — Account deletion needs no fresh login (Low)

`DELETE /users/{userId}` cascades a permanent deletion, Keycloak identity included, on any valid
5-minute access token. A stolen token or an unlocked phone is enough. **Fix:** require a recent login
for this one endpoint. Check the token's `auth_time` is within, say, 5 minutes, otherwise 401 with a
"reauthenticate" error. Clients then start a login with `max_age=0` before deleting.

## SEC-14 — Status codes that break the ownership table (Info)

None of these leak data; they make client behaviour and tests ambiguous.

| Request (caller ≠ owner) | Now | Table says |
|---|---|---|
| `GET /words/dictionary/{privateId}` | `200 []` | 404, like the dictionary itself |
| `DELETE /words/{othersWordId}` | `200 "Deleted successfully"`, but nothing deleted | 404 (the id-addressed read/write rule), or a body that says 0 deleted |
| `GET /users/{othersUserId}` | 403 | 404 (by-id read) |

---

## What was tested and holds

Kept here so nobody re-tests it, and so a regression shows up as a change.

- Requests with no token → 401 on all three services. A token with `alg=none`, or a swapped payload
  keeping the original signature → 401.
- Reading another user's private dictionary or its tags by id → 404. The dictionary and word batch
  endpoints filter to the caller. Listing someone else's dictionaries or words → 403.
- Ownership hijack of a dictionary through `PUT`/`POST /dictionaries/` with your own `userId` → 403,
  and the owner is unchanged. Writing words into, deleting, or tagging someone else's dictionary → 403.
- ms_user: someone else's profile, vault, delete, and overwriting via `PUT /users/` → 403.
- Marketplace: a non-member browsing → 403; importing a private dictionary → 404.
- `/actuator/env` → 404, and only health/info are exposed. Cross-origin preflight → 403 (no CORS
  configured; correct until the web client needs it in Phase 5).
- Keycloak password brute force: after 6 wrong passwords the right one is refused (temporary
  lockout).
- Email-code SPI: SecureRandom 6-digit codes, a 5-minute expiry, 3 attempts per code, and resend
  throttled to 3 per session with a 30-second cooldown. **Across sessions**, the realm's brute-force
  protection locked the account after 3 failed sessions (about 9 guesses), which also limits how many
  code e-mails an attacker can trigger. The flip side: anyone who knows a username can lock that
  account temporarily. That is the standard Keycloak trade-off, so keep `permanentLockout` off.
- Exception handler catch-alls don't echo `ex.getMessage()`. RabbitMQ deserialisation uses inferred
  types.
- No secret was found in git history; `.env` is ignored. (`local-dev-only-change-me` is committed by
  design; see SEC-11.)

## Re-running the probes

1. Start the stack and the services (`docs/ops/local-development.md`), then run
   `scripts/dev-seed/seed.py`.
2. The probes used the seed users `anna.bauer` (member), `oliver.brown`, `mia.schulz` and `nina.koch`
   (non-members, all private), with password `test1234`.
3. On Windows with Docker Desktop, run Python in a container with `host.docker.internal` URLs. Docker
   Desktop sometimes drops container→host connections before they reach Tomcat (verified: all server
   threads idle). A test harness should retry *connection* errors, never HTTP responses.
4. Run `reset.py` afterwards.
