# Verborum — Frontend–Backend Integration

**Target location of this file:** backend repo `verborum_ms/docs/integration/frontend-backend-integration.md`
(referenced from the Android and future KMP repos — single copy, no duplicates).
**Related:** Verborum Android Development (`verborum_android/docs/android-development.md`),
backend agent docs (`verborum_ms/docs/agent/`).

---

## 1. The Platform Picture

One backend, three client applications in **three separate repositories**:

| Client | Technology | Repo | Status |
|---|---|---|---|
| Android | **Native** Kotlin, Jetpack Compose | `verborum_android` | Working (pre-auth) |
| WebApp | **KMP**, Compose Multiplatform | (to be created) | Planned — next client |
| iOS | **KMP**, Compose Multiplatform | (to be created) | Planned — after web |

Android stays native permanently. The two KMP projects are independent of the Android codebase —
**nothing is shared between clients except the contracts in this document.** That is precisely why
this document exists: with three independent implementations, whatever is not specified here will be
invented three times, differently.

**Delivery policy:** work streams run in parallel and must not block each other, but **nothing is
released until Android + Backend are ready as a pair.** Web-required infrastructure (CORS, browser
token handling) is built on the backend at its natural phase even if Android releases first —
readiness on the BE side is decoupled from client release order.

---

## 2. Backend Topology — a Client's View

```
Client (Android / Web / iOS)
    │ HTTPS + Authorization: Bearer <JWT>
    ▼
API Gateway (Spring Cloud Gateway, single public origin)   ← BE Phase 5
    ├── /dictionaries/**, /words/**  → ms_dictionary (8085)
    ├── /users/**                    → ms_user       (8086)  ← BE Phase 2
    └── /marketplace/**              → ms_marketplace (8087)  ← BE Phase 4
Keycloak (auth server, Google SSO federated)               ← BE Phase 3
RabbitMQ (verborum.events)  — INTERNAL ONLY, never client-visible
```

Rules for every client:
- Clients talk to **the gateway and Keycloak only**. Individual service ports exist only in local development.
- RabbitMQ is backend-internal. Clients see its *effects* (§9), never the broker.
- Google sign-in is federated **behind Keycloak** — no client ever integrates Google directly.

---

## 3. API Contract

REST + JSON. Two response shapes:

**Mutations** return the standard envelope:
```json
{ "status": 201, "message": "Dictionary saved successfully: <id>", "path": "/dictionaries/", "timestamp": "…" }
```

**Errors** (all services, all cases — validation, not-found, malformed body):
```json
{ "status": 404, "error": "RecordNotFoundException", "errorDetail": "Dictionary not found for ID: …", "path": "/words", "timestamp": "…" }
```

**Reads** return the DTO or DTO list directly, no envelope.

Semantics every client must code against: `400` validation/malformed body · `401` missing/invalid
token (post-Phase-3) · `404` unknown record · **empty list (not 404)** for batch/list endpoints with
no matches.

### 3.1 Identity of records
All IDs are **client-generated UUID strings**. The backend never generates IDs. This is what makes
offline creation possible and is non-negotiable for every client. After auth (§6), the *owner* of a
record is always the JWT subject — any client-supplied `userId` is ignored by the backend (BE task
P3-05).

### 3.2 Endpoint catalog (client-relevant)

**ms_dictionary — live today**

| Method & path | Purpose |
|---|---|
| `POST /dictionaries/` · `PUT /dictionaries/` | Create / update a dictionary |
| `DELETE /dictionaries/{dictionaryId}` | Delete (words cascade server-side) |
| `GET /dictionaries/{userId}` | All dictionaries of a user |
| `GET /dictionaries/dictionary/{dictionaryId}` | Single fetch |
| `GET /dictionaries/batch?ids=a,b,c` | Batch fetch |
| `POST /words` · `PUT /words` | Save words as bundles: `[{"dictionaryId":"...","words":[…]}]` |
| `DELETE /words/{wordId}` · `DELETE /words/dictionary/{dictionaryId}` | Deletes |
| `GET /words/dictionary/{id}` · `GET /words/user/{userId}` · `GET /words/batch?ids=…` | Reads |

**ms_user — BE Phase 2:** `POST/PUT /users/`, `GET/DELETE /users/{userId}`, vault: `GET/POST /users/{userId}/vault`, `DELETE /users/{userId}/vault/{dictionaryId}`.

**ms_marketplace — BE Phase 4:** `GET /marketplace/dictionaries` (paginated), `…/popular`, `…/language?from=EN&to=DE`, `POST /marketplace/dictionaries/{id}/import`.

**ms_autofil — BE Phase 6 (V2):** `GET /autofil?word=…&from=…&to=…` → ranked community translations.

---

## 4. Data Contracts

### 4.1 Supported languages — one list, three clients, one backend
The backend property `supported.languages` is the **single source of truth**; every client's language
enum must be a subset of it. Target list (19):

```
EN, DE, FR, ES, IT, PT, NL, TR, AZ, LT, PL, UK, AR, FA, JA, ZH, KO, EL, RU
```

Chinese is Simplified only (`zh`). Update procedure: backend property first (accepting a superset is
harmless), then clients.

> **Status (backend):** RESOLVED. Both `ms_dictionary` and `ms_user` `application.properties`
> already carry all 19 codes. The earlier "backend still validates the original 8, pt/nl uploads
> fail" gap is closed — clients may ship the 19-language set whenever ready.

### 4.1a Dictionary tags (backend P4-08, added 2026-07-23)

A dictionary can carry many tags. They are **not part of the dictionary payload** — tagging uses its
own endpoints, so a client never has to re-send (or race with) the whole dictionary to add one:

| Method | Path | Body |
|---|---|---|
| `GET` | `/dictionaries/{dictionaryId}/tags` | — |
| `POST` | `/dictionaries/{dictionaryId}/tags` | `{"tag": "travel"}` |
| `DELETE` | `/dictionaries/{dictionaryId}/tags/{tag}` | — |

Rules a client must know:
- **Tags are normalised server-side**: trimmed and lower-cased. Send `"Travel"`, read back `"travel"`.
  They are grouping keys for marketplace browse and the later AI word-prediction work, not display
  text — render your own label if you want capitalisation. Delete normalises too, so
  `DELETE .../tags/Travel` removes `travel`.
- **Max 50 characters**, non-blank; otherwise 400.
- **Adding is idempotent** — re-adding an existing tag returns the existing one, no duplicate, no
  error. Safe to retry.
- **Deleting a dictionary deletes its tags** (database-level cascade). No client cleanup needed.
- Ownership follows the dictionary: another user's dictionary gives 403 on write, 404 on read.
- `GET /dictionaries/...` still returns **no** tags — fetching them is a second call per dictionary.
  Say so if that hurts your sync and the backend can reconsider.

### 4.2 Word content — the canonical meta schema
Defined by the Android implementation and documented normatively in the Android doc §4. Summary of
what web/iOS must reproduce exactly:

- **`word` / `translation` columns:** JSON array of per-meaning surfaces — `["kaufen","erwerben"]`.
- **`word_meta` / `translation_meta`:** one JSON object `{"lang","type"?,"genders"?,"fields"?}` with
  all lists index-aligned to the surfaces array; keys empty everywhere are omitted; **unknown keys
  are ignored** (the evolution rule).
- **Field keys currently:** `reading, plural, feminine, comparative, superlative, present, past,
  past3, participle, aux, aspect, root, stem, measure, class, polite` (the last seven arrive with the
  19-language expansion).

> **Status (backend):** RESOLVED in docs. `Word.java`, `docs/agent/verborum.md`, and
> `ms_dictionary/CLAUDE.md` previously documented a placeholder shape (`partOfSpeech`/`example`/
> `notes`) written before the client schema was known. The backend stores the column opaquely so
> nothing broke at runtime, but the docs now describe the schema above. The `word`/`translation`
> columns were also widened `VARCHAR(255)` → `TEXT` so multi-meaning entries cannot be truncated.

### 4.3 Word `level` (per-user mastery)
A word carries an integer **`level`** — the user's practice/mastery of that word, mirroring the
mobile client's local `level`. It rides on the word row (`POST`/`PUT /words`, returned on reads).
**Optional and nullable** on upload: a client that does not yet send it keeps working, and the
backend stores `null` — a client should treat `null` as `0`. Not carried on the `word.created`
event. (Backend column added 2026-07-21.)

### 4.4 Timestamps — names, zone, and format (contract)
Read DTOs (`DictionaryResponseDTO`, `WordResponseDTO`) expose two timestamps with the JSON keys
**`createdAt`** and **`updatedAt`**. Both are **zone-aware ISO-8601, normalized to UTC** with a `Z`
suffix, e.g. `"2026-07-21T09:34:42.622774Z"`.
- These are **server-authoritative**: Hibernate sets them; clients do **not** send them on upload
  (request DTOs have no timestamp fields), and any client-supplied value would be ignored.
- Parse them as an instant (UTC). Do **not** assume local time and do **not** map by the older names
  `creationTimestamp`/`updateTimestamp` — those were renamed to `createdAt`/`updatedAt`
  (2026-07-21) and are gone.
- Separate field, do not confuse: the mutation/error **envelope** (`Response`/`ErrorResponse`) has
  its own `timestamp` field that carries the server's **local offset** (e.g. `+04:00`). Only the
  read-DTO `createdAt`/`updatedAt` are the record's timestamps.

---

## 5. Sync & Offline Model

Sync internals are **client-internal** — the backend imposes no sync protocol — but any
offline-capable client must respect the shared contract: client-generated UUIDs; deletes are real
`DELETE` calls (remote-first, local hard-delete after confirmation); updates are full upserts via
`PUT`.

- **Android** is offline-first (Room + `isSynced` flags + tombstones, upload-then-download,
  local-unsynced-wins). Documented in the Android doc §5 and recommended as the reference for iOS if
  it goes offline-capable.
- **Web** is expected online-only (no durable local store; browser storage is not trusted).
- **At release Android is online + login required.** Offline-first remains as the resilience layer
  under a logged-in session — it is not an anonymous mode.

> **Backend note (deferred optimization):** reads today (`GET /dictionaries/{userId}`,
> `GET /words/user/{userId}`) return the user's entire corpus, and the client re-fetches it on every
> sync. This is fine at personal-vocabulary scale. When corpus size or traffic warrants, add a
> delta endpoint — `GET /words/user/{userId}?since=<ISO-8601>` returning only rows changed since the
> client's last sync. The `createdAt`/`updatedAt` fields are already on every response, so this is a
> purely additive change. See the backend roadmap Deferred/Backlog section.

---

## 6. Authentication Contract (normative for all three clients)

Keycloak realm `verborum`; Google federated behind it. **Authorization Code + PKCE for every
platform.** The first client to implement auth implements *this spec* — not a precedent of its own
making.

### 6.1 Flow & clients

| | Android | iOS | Web |
|---|---|---|---|
| Keycloak client | `verborum-app` (public) | `verborum-app` (public) | `verborum-web` (public) or BFF confidential — §6.3 |
| Login UI | AppAuth / Custom Tabs | ASWebAuthenticationSession | Browser redirect |
| Token storage | EncryptedSharedPreferences / DataStore + Keystore | Keychain | §6.3 |
| Refresh scope | `offline_access` | `offline_access` | none (short sessions) |

**Redirect URI (decided BE P3-02, 2026-07-23).** `verborum-app` accepts:
- `de.coldtea.verborum://oauth2redirect/*` — the mobile scheme. Android sets
  `manifestPlaceholders = [appAuthRedirectScheme: "de.coldtea.verborum"]`; iOS registers the same
  scheme as a URL type.
- `http://localhost:*` — emulator/loopback only.

`verborum-web` accepts `http://localhost:3000/*`. Any additional URI must be added to
`keycloak/import/verborum-realm.json` in the backend repo — an unregistered URI is rejected before
the login page renders.

**PKCE is enforced, not optional.** `verborum-app` and `verborum-web` set
`pkce.code.challenge.method = S256`. An authorization request without `code_challenge` is rejected
with `invalid_request: Missing parameter: code_challenge_method` — verified live. AppAuth and
ASWebAuthenticationSession send it automatically; a hand-rolled client will not.

> **Implementing this?** `docs/integration/client-login-guide.md` is the build-oriented companion —
> exact client registration values, the sign-up call, token handling, logout, device-testing
> pitfalls, and the current list of gaps. This section stays normative; that one explains how.

### 6.1a Sign-up, password reset and logout (decided 2026-07-23)

**Sign-up uses Keycloak's hosted registration page — clients do NOT build a registration form.**
Send the user to the same authorization endpoint with `/registrations` instead of `/auth`, with
identical PKCE parameters:
```
{issuer}/protocol/openid-connect/registrations?client_id=verborum-app&response_type=code
    &scope=openid&redirect_uri={redirect}&code_challenge={challenge}&code_challenge_method=S256
```
It returns the account-creation form and completes with the same code exchange as login, so a client
needs one auth flow, not two. AppAuth: reuse the `AuthorizationRequest` and swap the endpoint.

*Why not a native form:* `POST /users/` does **not** create a Keycloak identity — it creates the
profile row keyed on `keycloakId`. Creating the identity itself needs ms_user's Keycloak Admin API
path (BE P3-04), which is not built. Hosted registration keeps Keycloak the single identity
authority and needs no backend work.

**After the first successful login, the client calls `POST /users/` once** with `keycloakId` = the
JWT `sub`, plus `email` and `displayName` from the ID token, to create the profile. It is safe to
call whenever a `GET /users/{userId}` 404s. (`userId` is still client-supplied until BE P3-05.)

**Password reset:** the hosted login page carries a "Forgot Password" link
(`/protocol/openid-connect/reset-credentials`). No client screen needed. Note: the realm has **no
SMTP configured**, so reset mails do not send in local dev, and `verifyEmail` is off.

**Logout** (previously unspecified — the gap the Android side flagged):
1. Call the end-session endpoint with the refresh token —
   `{issuer}/protocol/openid-connect/logout`, form-encoded `client_id` + `refresh_token`. This kills
   the Keycloak session; skipping it leaves an SSO session that silently logs the user straight back
   in.
2. Optionally revoke at `{issuer}/protocol/openid-connect/revoke`.
3. Delete both tokens from encrypted storage.
4. Decide per client what happens to local data. Recommended: keep synced rows, clear nothing on
   logout, and treat a *different* user logging in on the same device as a wipe-and-resync — the
   local store is keyed by owner id, so mixing two users' rows is the failure to avoid.

### 6.2 Token policy (configured per Keycloak client — design them together, BE P3-02)
Mobile: short access tokens (≈5 min), long offline-capable refresh tokens — a device offline for days
must resume sync without re-login. Web: short everything; re-authentication is acceptable in a
browser. All clients: send `Authorization: Bearer <access>`; refresh on 401 once, then surface login.

### 6.2a Testing on a physical device — pin the issuer or every token is rejected

Keycloak stamps the issuer into every token. Left unpinned it echoes back whatever host the caller
used, so a phone hitting `http://192.168.0.x:8180` receives `iss: http://192.168.0.x:8180/...` while
the services validate `iss: http://localhost:8180/...` — **every API call then fails with a 401 that
looks like a broken token rather than a config mismatch.**

The root compose pins it via `KC_HOSTNAME_URL` (default `http://localhost:8180`). For device
testing, set all of these to the *same* LAN origin and restart:
```
KEYCLOAK_HOSTNAME_URL=http://<lan-ip>:8180     # keycloak container
KEYCLOAK_ISSUER_URI=http://<lan-ip>:8180/realms/verborum          # each service
KEYCLOAK_JWK_SET_URI=http://<lan-ip>:8180/realms/verborum/protocol/openid-connect/certs
```
Verified 2026-07-23: with the hostname pinned, tokens carry that issuer no matter which host
requested them — including `localhost`. One issuer everywhere is the property you want; a token that
validates on the emulator but not the phone means these three disagree.

### 6.3 Web token storage — decision pending, backend must stay compatible with both
Browsers have no secure token storage. Two options:
1. **BFF (recommended):** a thin server component of the web app holds tokens; the browser gets an
   httpOnly session cookie. Most secure; means the KMP web repo ships with a small server — this
   shapes that repo's structure and should be decided before it is created.
2. **Pure SPA:** tokens in memory only (never localStorage), silent re-auth on reload.

The gateway must therefore accept standard `Authorization: Bearer` (mobile, BFF-to-gateway)
regardless of which option the web app picks — which it does by design.

### 6.4 Guest-data migration (decide once — implement per client with local data)
A client that allowed local data creation before login (Android today) migrates on first login
**client-side**: rewrite the local owner id from the guest UUID
(`00000000-0000-0000-0000-000000000000`) to the JWT subject, mark all rows unsynced, run the normal
upload. No special backend endpoint exists or is needed — client-generated UUIDs make the records
globally valid already. The guest UUID must never reach the server post-auth.

---

## 7. Web-Specific Backend Readiness (build at the right time, block nobody)

Two things only browsers need — both land with their natural backend phases regardless of whether
Android releases first:
- **CORS** (BE Phase 5, gateway): allowed origins per environment for the web app's domains;
  `Authorization` + `Content-Type` headers; all verbs. Keycloak realm clients get matching *Web
  Origins*. Mobile clients are unaffected.
- **Cookie/session tolerance:** only relevant if BFF is chosen — and then it is the BFF's concern,
  not the gateway's; the gateway keeps seeing bearer tokens (§6.3).

---

## 8. Environments

| Environment | API origin | Keycloak | Transport |
|---|---|---|---|
| dev | `http://localhost:8085–8087` or LAN (per-service, no gateway) | `http://localhost:8180` | cleartext allowed |
| staging | gateway origin, HTTPS | staging realm | HTTPS only |
| prod | gateway origin, HTTPS | prod realm | HTTPS only |

Per-platform mechanics: Android build types / product flavors (`BuildConfig`); KMP projects use
build-time configuration per target. The current Android hardcoded LAN URL (`192.168.0.241:8085`,
cleartext in release) is a dev artifact scheduled for removal (Android roadmap A3).

---

## 9. Backend Events as Client-Visible Behavior

Clients never see RabbitMQ, but they rely on the behavior it produces:

| Client action | Backend event chain | Visible effect |
|---|---|---|
| Set dictionary `isPublic = true/false` | `dictionary.visibility.*` → ms_marketplace | Listing appears/disappears in marketplace |
| Delete account | `user.deleted` → ms_dictionary, ms_marketplace | All server data of the user gone |
| Import from marketplace | `dictionary.imported` → ms_user vault | Dictionary arrives via normal sync |
| Create words | `word.created` → ms_autofil (V2) | Community suggestion counts grow |

**Consequence for clients:** these effects are **eventually consistent** — an imported dictionary or
a new listing may take a moment. Do not design UI that assumes synchronous propagation.

---

## 10. Compatibility Rules

1. **Additive-only** API evolution: new fields, new endpoints; never repurpose or remove while any
   client version is live. Narrow exception: a field may be renamed only if **no client build has
   ever correctly consumed the old name** (i.e. removing it breaks nothing). The one exercised
   precedent is the `creationTimestamp`/`updateTimestamp` → `createdAt`/`updatedAt` rename in §4.4.
2. Meta schema evolves by **adding keys**; parsers ignore unknown keys (already true on Android;
   mandatory for web/iOS).
3. Language list grows backend-first (§4.1).
4. Envelope shapes (§3) are frozen; new information means new fields, not changed ones.

---

## 11. Milestone Mapping

| Backend milestone | Unblocks |
|---|---|
| Language property → 19 codes (small task, do early) | Android A1 release; fixes live pt/nl gap — **done** |
| Phase 2 (ms_user) + Phase 3 (Keycloak/JWT) | Android A2 login; any client auth |
| Phase 4 (ms_marketplace) | Android A4 forum tab; web marketplace pages |
| Phase 5 (gateway + CORS) | Android A3 final networking; **web development start** |
| Phase 6 (ms_autofil, V2) | Suggestion features on all clients |

Recommended additions to the backend roadmap: the language-list task, the meta-doc correction
(§4.2), and an auth-contract section in `security.md` mirroring §6 of this document.
