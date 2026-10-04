# Marketplace — Client Implementation Guide (Android, iOS, Web)

**Audience:** the Claude sessions that implement the Verborum clients — native Android
(`verborum_android`) and the Compose Multiplatform project for web and iOS (`verborum_kmp`). Written
to be followed literally. Where this file and the code in the backend repo disagree, the code is right
and this file is the bug — say so rather than working around it.

**Backend state this describes:** roadmap Phase 4 complete, verified against a running stack on
**2026-09-27** (tasks P4-01 … P4-10), plus the language-pair filter and slice paging of **P4-11** and
the tag filter of **P4-12** (2026-10-04). Nothing here is planned-only unless it says so.

**Read first, and keep open:**
- `docs/integration/frontend-backend-integration.md` — the normative contract (envelope, error shape,
  ids, word meta schema §4.2, auth §6). This guide adds the marketplace on top of it.
- `docs/integration/client-login-guide.md` — how to obtain and refresh the token every call below needs.

---

## 0. Heads-up for the client teams (roadmap P3-03a)

Send this as-is — to the humans owning each client repo, and paste it into each client repo's own
Claude context. It collects every backend change since the clients last synced, most of which will
otherwise surface as unexplained 401 / 400 / 403 / 404 responses.

> **Verborum backend — changes you must adapt to (as of 2026-10-04)**
>
> 1. **ms_dictionary (`:8085`) requires a token on every call — since 2026-07-23.** Send
>    `Authorization: Bearer <access token>` on dictionaries, words, tags and batch calls, or you get
>    **401** everywhere. Only `/actuator/health` and Swagger stay open. If sync suddenly fails across
>    the board, this is the first thing to check.
> 2. **The owner comes from the token, not from your request.** On `POST`/`PUT /dictionaries/`, send
>    `userId` = your JWT `sub` (anything else → **403**). Writing to or deleting someone else's
>    dictionary → **403**. `GET /dictionaries/{userId}` and `GET /words/user/{userId}` must name
>    *you* → otherwise **403**.
> 3. **Ids and language codes are validated — since 2026-09-27.** Every id you send (`dictionaryId`,
>    `wordId`, `userId`, `keycloakId`) must be a canonical UUID (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
>    hex). Every language code must be one of the 19 supported codes (any case). Violations are
>    **400** with the field named, e.g. `bundles.words[0].wordId: must be a valid UUID`. These were
>    silently accepted before — if you start seeing 400s, you have been sending bad data.
> 4. **Public dictionaries are readable by everyone logged in — since 2026-09-27.** You can now read
>    another user's *public* dictionary, its words and its tags. A private one is still **404**, and
>    you can never write to someone else's dictionary (**403**). On words from someone else's
>    dictionary, `level` is always **`null`** — it is the owner's progress, not yours.
> 5. **The marketplace is live (`ms_marketplace`, `:8087`).** Browse public dictionaries, filter by
>    language pairs, tags or publisher, and import them into your vault. Browse pages carry `hasNext`
>    (infinite scroll), not totals. See `docs/integration/marketplace-client-guide.md` in the backend repo.
> 6. **Word save/update messages changed.** `POST`/`PUT /words` now reply "Saved/Updated successfully
>    into dictionary `<dictionaryId>`" instead of listing the words. Do not parse `message` — it is for
>    humans and logs.
> 7. **Web app only:** the backend sends **no CORS headers yet** (they arrive with the API gateway,
>    backend Phase 5). A browser app on its own origin cannot call the services directly — use a
>    same-origin dev proxy for now (§7.3 of the marketplace guide).

---

## 1. What the marketplace is

- Any user can mark one of their own dictionaries **public** (`isPublic: true` on
  `PUT /dictionaries/`). Within about a second it appears in the marketplace as a **listing**.
- Other users **browse** listings (newest, most popular, by language pair, by publisher) and
  **import** one. Importing adds a **reference** to the dictionary to their **vault** (ms_user). It
  does **not** copy the dictionary.
- The importer then **reads** the dictionary and its words straight from ms_dictionary, like their
  own — but **read-only**, and always the owner's current version. If the owner renames it or adds
  words, the importer sees that next time they fetch.
- If the owner makes it private again or deletes it, it **disappears** for importers too: the listing
  vanishes and reads return 404. The vault entry stays behind (see §5.4).

There is no rating, no view count, no publisher display name, and no "make my own editable copy" yet
(§9).

---

## 2. Where to call — base URLs

| Service | Local base URL | Paths this guide uses |
|---|---|---|
| ms_marketplace | `http://localhost:8087` | `/marketplace/dictionaries/**` |
| ms_dictionary | `http://localhost:8085` | `/dictionaries/**`, `/words/**` |
| ms_user | `http://localhost:8086` | `/users/**` |
| Keycloak | `http://localhost:8180/realms/verborum` | token issuer (login guide) |

- **Keep one configurable base URL per service.** When the API gateway lands (backend Phase 5) all
  three collapse into one origin with **the same paths** (`/marketplace/**` → ms_marketplace, and so
  on), so a per-service base URL becomes one value — no path changes.
- **Physical device testing:** replace `localhost` with the machine's LAN IP *and* read
  `client-login-guide.md` §7 first — the token issuer must match or every call is a 401.
- Local stack is plain `http`. Production will be `https` behind the gateway; never hard-code `http`.

---

## 3. Authentication and the security rules clients must follow

Every marketplace endpoint requires `Authorization: Bearer <access token>` — browsing included.
There is no anonymous marketplace. Token acquisition, refresh and storage are exactly as in
`client-login-guide.md` §2–§6; nothing marketplace-specific changes there.

**Rules — each one prevents a concrete failure:**

1. **Send the token on every call; refresh once on 401, then send the user to login.** Access tokens
   live 5 minutes.
2. **Never send anyone's id as "who I am".** The import endpoint takes no user id at all — the server
   uses the token's `sub`. Do not add one.
3. **Know your own `sub`.** Read it from the access/ID token after login and keep it in memory. You
   need it to recognise your own listings (§5.3) and as the `userId` on your own dictionaries.
4. **Never import your own dictionary.** The server answers **400** (`SelfImportException`). Compare
   `listing.publisherId` with your `sub` and hide or disable the import action on your own listings.
5. **Guard the import button against double taps.** Disable it while the request is in flight. Two
   simultaneous imports by the same user can make the second one fail with a **500** (a unique
   constraint race) — nothing is corrupted, but the user sees an error. Retrying succeeds.
6. **Treat every string from a listing or someone else's dictionary as untrusted user input.**
   `name`, words, translations and tags are written by other people. Render them as plain text,
   never as HTML or markdown — this matters most in the **web** app (XSS). Never use them to build
   URLs, file paths or queries without encoding.
7. **Validate before sending.** Language codes against the supported list (§4.4), ids as canonical
   UUIDs, `size` within 1–100. The server enforces all of it with a 400, but a 400 from your own
   client is a bug.
8. **Do not log tokens**, and do not log full request/response bodies that contain other users'
   content in production builds.
9. **Token storage per platform** (from the login guide): Android — EncryptedSharedPreferences /
   DataStore backed by Keystore; iOS — Keychain; web — **never `localStorage`**: memory only, or a
   BFF holding tokens server-side (see §7.3).
10. **There is no server-side rate limiting yet** (it lands at the gateway, backend P5-04). Be a good
    citizen: debounce filter changes (≥ 300 ms), do not poll listings in a loop, page on scroll rather
    than prefetching everything.

---

## 4. Endpoints

### 4.1 Browse (ms_marketplace, all `GET`, all paginated)

| Endpoint | Returns | Order |
|---|---|---|
| `GET /marketplace/dictionaries?pair=EN-TR&pair=FR-DE` | listings, optionally filtered | newest first |
| `GET /marketplace/dictionaries/popular?pair=...` | listings, same filters | most imported first; newest first among equals |
| `GET /marketplace/dictionaries/publisher/{publisherId}` | one publisher's listings | newest first |

**Paging parameters** on all three: `page` (zero-based, default `0`, must be ≥ 0) and `size`
(default `20`, 1–100). Out-of-range → **400**. The order is stable (ties are broken by
`dictionaryId`), so paging does not repeat or skip items *unless* listings are added or removed while
the user scrolls — acceptable for browsing; de-duplicate by `dictionaryId` when appending pages.

**Language-pair filter (`pair`):** optional; leave it out for every language.
- One pair is two codes joined by a hyphen: `EN-TR`. Any case (`en-tr` works).
- **Direction does not matter.** `pair=EN-TR` returns English→Turkish **and** Turkish→English
  dictionaries; `pair=TR-EN` returns exactly the same.
- Several pairs: repeat the parameter (`pair=EN-TR&pair=FR-DE`) or comma-separate them
  (`pair=EN-TR,FR-DE`). The result is every listing in **any** of the pairs — here EN→TR, TR→EN,
  FR→DE and DE→FR, nothing else.
- At most **10** pairs. An unsupported code, the same code twice (`EN-EN`) or a malformed value
  (`ENTR`, `EN-`) → **400**.
- Recommended default when the marketplace opens: the pairs of the user's own dictionaries, e.g. a
  user with DE→TR and EN→TR dictionaries sends `pair=DE-TR&pair=EN-TR`. Let them edit the filter.
- Listings always come back with **uppercase** codes and their real direction in `fromLang`/`toLang`.

`GET /marketplace/dictionaries/language?from=&to=` **was removed on 2026-10-04** (it now returns
404) — use `pair`.

**Publisher filter:** an unknown `publisherId` returns an **empty page**, not a 404.

**Tag filter (`tag`):** optional, since 2026-10-04.
- `tag=food&tag=travel` returns listings with **any** of the tags. Any case (`Food` matches `food`).
- Repeat the parameter for several tags; at most **10**, each non-blank and at most 100 characters,
  otherwise **400**. Do not comma-join tags into one value — a tag containing a comma cannot be
  searched for (the server splits on commas).
- Combines with `pair` by AND: `pair=EN-TR&tag=food` is English–Turkish dictionaries tagged food.
- Every listing now carries `tags` (lowercase, sorted, `[]` when untagged) — render them as chips and
  let a tap add that tag to the filter.
- A tag added or removed in the app reaches the marketplace within seconds, like a rename.

**Planned, not built yet:** a publisher display-name filter with `publisherName` on every listing
(roadmap P4-13). Listings whose publisher has no display name will then stop appearing — require a
display name before a user publishes to the marketplace.

Only **listed** (public) dictionaries are ever returned. You will never see a private or deleted one
here.

### 4.2 The page envelope and the listing

```json
{
  "items": [
    {
      "dictionaryId": "00000006-0000-4000-8000-000000000000",
      "publisherId":  "765a81ed-2612-4dcc-bf7a-3d5fecf3d0d6",
      "name":         "Polish → Ukrainian",
      "fromLang":     "PL",
      "toLang":       "UK",
      "tags":         ["grammar", "travel"],
      "importCount":  7,
      "publishedAt":  "2026-07-19T17:01:21.303971Z"
    }
  ],
  "page": 0,
  "size": 20,
  "hasNext": true
}
```

| Field | Meaning |
|---|---|
| `items` | the listings on this page (may be empty) |
| `page` / `size` | echo of what you asked for (defaults applied) |
| `hasNext` | whether another page exists — request `page + 1` only while it is `true`. There are no totals: the marketplace is infinite scroll, and a count would cost the server a second query on every filter change |
| `dictionaryId` | the dictionary's id in ms_dictionary — use it to import and to read it |
| `publisherId` | the owner's `sub`. Pass it to the publisher endpoint for "more from this publisher"; compare it with your own `sub` to detect your own listings. **Not a display name** — do not show the raw UUID to users |
| `name`, `fromLang`, `toLang` | copies of the dictionary's fields, kept current by the backend within seconds |
| `tags` | the dictionary's tags, lowercase and sorted; `[]` when untagged. Kept current like `name` |
| `importCount` | number of **distinct users** who imported it (a user importing twice counts once) |
| `publishedAt` | when it (most recently) became public, ISO-8601 UTC. Making it private and public again resets it |

This `SliceResponse` shape is Verborum's own contract, not Spring's `Slice` — map exactly these four
fields and ignore unknown ones (fields may be added, never removed or renamed). It replaced
`PageResponse` (`totalElements`, `totalPages`) on 2026-10-04.

### 4.3 Import (ms_marketplace)

```
POST /marketplace/dictionaries/{dictionaryId}/import
Authorization: Bearer <token>
(no body)
```

| Response | When | Client action |
|---|---|---|
| **201** `{"status":201,"message":"Imported successfully dictionary <id>", …}` | recorded (first time or repeat) | show success; add to the local vault view optimistically (§5.2) |
| **400** `SelfImportException` | it is your own dictionary | should never happen — you hid the button (§3 rule 4) |
| **404** `RecordNotFoundException` | unknown id, **or** made private / deleted since you loaded the list | remove it from the visible list; tell the user it is no longer available |
| **401** | token expired | refresh once and retry |
| **500** | double-tap race (§3 rule 5) | retry once; it succeeds |

**Idempotent:** importing the same dictionary again returns 201, does not raise `importCount`, and
re-sends the vault update. So "import" is also the repair action if a vault entry is missing.

**Asynchronous:** 201 means the import is recorded. The **vault entry is created a moment later**
by ms_user (via an internal event). Do not immediately re-fetch the vault and conclude it failed.

### 4.4 Supported language codes

`EN, DE, FR, ES, IT, PT, NL, TR, AZ, LT, PL, UK, AR, FA, JA, ZH, KO, EL, RU` — one list for every
service and every client (`frontend-backend-integration.md` §4.1). Uppercase with a
locale-independent conversion (`Locale.ROOT` / invariant culture) — Turkish locale uppercasing turns
`i` into `İ`.

### 4.5 Related endpoints you need around the marketplace

| Service | Endpoint | Use |
|---|---|---|
| ms_user | `GET /users/{userId}/vault` | the caller's vault entries: `[{vaultEntryId, userId, dictionaryId, importedAt}]`. `{userId}` here is **ms_user's own profile id**, not the `sub` (login guide §4) |
| ms_user | `DELETE /users/{userId}/vault/{dictionaryId}` | remove an imported dictionary from the vault (200, also when absent) |
| ms_dictionary | `GET /dictionaries/dictionary/{id}` | the imported dictionary's details (404 if it became private / was deleted) |
| ms_dictionary | `GET /dictionaries/batch?ids=a,b,c` | several at once — unreadable ids are **silently dropped** |
| ms_dictionary | `GET /words/dictionary/{id}` | its words; **`level` is `null`** on another user's words; `[]` if it became private |
| ms_dictionary | `GET /dictionaries/{id}/tags` | its tags (404 if not readable) |
| ms_dictionary | `PUT /dictionaries/` with `isPublic` | publish / unpublish your own dictionary |

**Precondition for importing:** the user must have an **ms_user profile** (created with
`POST /users/` after first login — login guide §3). Without one, the vault update cannot be applied and
the imported dictionary never appears in the vault. Keep the existing rule: call `POST /users/`
whenever `GET /users/{userId}` returns 404, before offering the marketplace.

---

## 5. Flows to implement

### 5.1 Browse screen
1. `GET /marketplace/dictionaries?page=0&size=20` (or `/popular`) → render `items`.
2. On scroll near the end and `hasNext` → request `page + 1`, append, de-duplicate by
   `dictionaryId`.
3. Language-pair and tag filters → `?pair=..&pair=..&tag=..` on the same endpoint. Pre-fill it from the user's own
   dictionaries. Re-query on every change of the chips, reset to page 0, and **cancel the request
   still in flight** (`collectLatest`/`flatMapLatest`, `switchMap`) so a slow, older response can never
   overwrite a newer one.
4. Tap a publisher → `/publisher/{publisherId}` ("More from this publisher").
5. For each item where `publisherId == mySub`: show "Yours" and no import action.
6. Optional preview before importing: `GET /dictionaries/dictionary/{id}` and
   `GET /words/dictionary/{id}` work for any listed dictionary.

### 5.2 Import
1. Disable the button; `POST …/{dictionaryId}/import`.
2. On 201: mark the listing as imported locally and add a pending vault entry `{dictionaryId}` to the
   local vault view. Re-enable the button as "Imported" (a repeat tap is harmless but pointless).
3. Reconcile with the server later: next `GET /users/{userId}/vault` will contain it. If it still does
   not after a reasonable time (e.g. next app start), call import again — it is idempotent and
   repairs a lost vault update.

### 5.3 Your own dictionaries
- Publish: `PUT /dictionaries/` with `isPublic: true` (same body as any update, `userId` = your
  `sub`). It appears in the marketplace within about a second.
- Rename / change languages while public: listings follow within seconds.
- Unpublish: `isPublic: false` → it leaves the marketplace; everyone who imported it loses access.
  **Warn the user before unpublishing or deleting** if `importCount > 0`.

### 5.4 Opening an imported dictionary (vault)
1. `GET /users/{userId}/vault` → `dictionaryId`s.
2. `GET /dictionaries/batch?ids=…` → dictionaries still readable. **Any id missing from the result
   became private or was deleted.**
3. For each unavailable one: show it as "No longer available" with a **Remove** action →
   `DELETE /users/{userId}/vault/{dictionaryId}`. The backend does **not** remove these vault entries
   by itself.
4. `GET /words/dictionary/{id}` for the words. Render read-only: no add/edit/delete of words, tags or
   the dictionary (all **403**).

### 5.5 Learning progress on imported words
`level` from the server is `null` for words you do not own — the owner's mastery is not yours. Keep
the user's own progress for imported words **locally only**, keyed by `wordId` (and your `sub`).
There is no server storage for progress on someone else's words, and you cannot write `level` into
their dictionary (403).

### 5.6 Offline behaviour
- The marketplace itself is **online-only**; show a clear offline state instead of stale listings.
- Imported dictionaries may be **cached** for offline reading, but they are read-only references:
  refresh them when online and drop the cache when the dictionary becomes unavailable (§5.4).
  **Never upload an imported dictionary or its words through your normal sync** — they are not
  yours and every write is a 403. Keep them out of the local "unsynced/dirty" set entirely.

---

## 6. Error handling reference

Error body (all services):
```json
{ "status": 400, "error": "HandlerMethodValidationException",
  "errorDetail": "pair: must be two different supported language codes, e.g. EN-TR", "path": "/marketplace/dictionaries",
  "timestamp": "…" }
```

| Status | `error` values you may see here | Meaning |
|---|---|---|
| 400 | `HandlerMethodValidationException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`, `MethodArgumentNotValidException`, `SelfImportException` | bad parameter / missing parameter / wrong type / invalid body field / own dictionary. `errorDetail` names the field — log it, it is a client bug |
| 401 | — | token missing, expired or wrong issuer (login guide §7) |
| 403 | `ForbiddenOperationException` | a write on a dictionary that is not yours |
| 404 | `RecordNotFoundException`, `NoResourceFoundException` | unknown/unavailable dictionary, or a wrong URL |
| 500 | `Exception` (`errorDetail` is always `"Internal server error"`) | server fault — retry once, then show a generic error |

Branch on the **status code**, not on `message`/`errorDetail` text — texts are for logs and may change.

---

## 7. Platform notes

### 7.1 Android (native Kotlin, `verborum_android`)
- Reuse the existing authenticated HTTP stack (OkHttp `Authenticator` for refresh-once-on-401). Add
  ms_marketplace as another base URL in the same configuration that holds ms_dictionary / ms_user.
- Paging: map `SliceResponse` to Paging 3 (`PagingSource` keyed by page number; `nextKey = page + 1`
  while `hasNext`, else `null`).
- Local persistence for vault/imported content goes in its **own** tables, separate from owned
  dictionaries, so the sync engine can never pick them up for upload.
- The Android client is the reference implementation for the login edge cases (email verification,
  cancelled login) — follow it; the marketplace changes none of that.

### 7.2 iOS (Compose Multiplatform, `verborum_kmp`)
- Everything in §4–§6 belongs in `commonMain`: DTOs, the Ktor client, paging, the import use case,
  vault reconciliation. Only token storage (Keychain) and the login launcher
  (`ASWebAuthenticationSession`) are iOS-specific.
- `publishedAt` is ISO-8601 with `Z`; parse with `kotlinx-datetime` `Instant.parse`.

### 7.3 Web (Compose Multiplatform Wasm, `verborum_kmp`)
- Same `commonMain` code as iOS — only the HTTP engine, token storage and login redirect differ.
- **CORS is not available yet.** The services do not emit CORS headers (planned at the gateway,
  backend Phase 5, `frontend-backend-integration.md` §7). A browser page on `http://localhost:3000`
  calling `http://localhost:8087` directly is **blocked by the browser**, even though the same call
  works from curl or Android. Until the gateway exists, serve the app through a **same-origin dev
  proxy** that forwards `/marketplace/**` → `:8087`, `/dictionaries/**` and `/words/**` → `:8085`,
  `/users/**` → `:8086` — the same routing the gateway will do, so the app's paths do not change later.
  Do **not** ask the backend to add per-service CORS as a shortcut.
- **Token storage:** memory only, or a BFF (decision pending, §6.3 of the integration doc). Never
  `localStorage` / `sessionStorage`.
- **Render all listing and dictionary text as text** (Compose `Text` does this; if any HTML interop
  is used, escape it) — other users write it (§3 rule 6).

---

## 8. Verifying locally

Backend stack: `docker compose up -d` in the backend repo, services running (see
`docs/ops/local-development.md`). Dev users `testuser` / `testuser` and `testadmin` / `testadmin`.

```bash
# a token without a login UI (local-dev-only client — never use it in the app)
TOKEN=$(curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8087/marketplace/dictionaries?size=5"
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8087/marketplace/dictionaries?pair=de-en&pair=EN-TR&tag=travel"
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8087/marketplace/dictionaries/<dictionaryId>/import"
```

To see a full publish → browse → import round trip, publish a dictionary as `testadmin`
(`PUT /dictionaries/` with `isPublic: true`) and import it as `testuser`. `testuser` needs an ms_user
profile first (`POST /users/`).

---

## 9. Not available yet — do not build UI that depends on these

| Missing | Status |
|---|---|
| Publisher display name ("by Anna") | backend `BL-04` — show "More from this publisher" without a name for now |
| Ratings, view counts | not designed |
| Search by text / by tag | not built (tags are readable per dictionary, not filterable in the marketplace) |
| "Make my own editable copy" of an imported dictionary | not built — imports are references |
| Automatic vault cleanup when a dictionary becomes unavailable | not built — clients handle it (§5.4) |
| CORS / single gateway origin | backend Phase 5 |
| Server-side rate limiting, HTTPS | backend `P5-04` |
