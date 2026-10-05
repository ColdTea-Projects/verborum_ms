# Client Findings — Android and KMP (2026-10-05)

**Audience:** the Claude agents working in `verborum_android` and `verborum_kmp`. Written in the
backend repo on purpose. The backend team does not edit the client repos; this file is the handoff.
Read it top to bottom, then work through the items for your repo in order. Where this file and the
backend code disagree, the code is right and this file is the bug. Say so in your report.

**Snapshots reviewed:**
- `verborum_android` `main` @ `c997e96` (2026-10-04)
- `verborum_kmp` `main` @ `106fab7` (2026-09-26)
- backend `verborum_ms` @ `a8807db`

Android was read but not built (no Android SDK on the reviewing machine); iOS cannot be built on
Windows. Backend behaviour quoted here was verified live against the seeded local stack
(`scripts/dev-seed/seed.py`: 30 users, password `test1234`, 26 Forum members, 4 non-members).

**Companion files in this repo:**
- `docs/integration/marketplace-client-guide.md` (2026-10-04, the current contract)
- `docs/integration/dictionary-sharing-client-guide.md`
- `docs/agent/security-audit-2026-10-05.md` (backend findings; the `SEC-xx` ids below point there)

---

## 0. First: refresh your copy of the contract

`verborum_android/docs/marketplace-client-guide.md` is the **2026-09-28** version. The backend
changed the marketplace contract on 2026-10-04 (P4-11 to P4-17). Copy these from this repo into
each client repo's `docs/` before any Forum work:
- `marketplace-client-guide.md`
- `dictionary-sharing-client-guide.md`
- `client-login-guide.md`
- `frontend-backend-integration.md`

Then update the client's own CLAUDE.md, skills and agents wherever they summarise the contract.

---

## 1. Backend changes that will reach you (plan for them now)

These are planned backend fixes from the security audit. Each says what the client must do. None is
deployed yet; the roadmap ("Security fixes (audit 2026-10-05)") tracks them.

| Backend change | What the client must do |
|---|---|
| **SEC-06 refresh-token rotation:** every refresh returns a *new* refresh token, and reusing an old one ends the session | Store the new refresh token from **every** refresh, atomically with the access token. Make refresh single-flight: read the refresh token *inside* the lock, after checking whether another caller already refreshed. Android's `TokenAuthenticator` reads `currentRefreshToken()` *before* `synchronized` (`core/.../auth/TokenAuthenticator.kt` ~line 37); move the read inside. KMP already reads the pair behind the session mutex; just confirm the new refresh token is persisted |
| **SEC-07 request size limits** (proposed: ≤ 500 words per bundle, ≤ 20 bundles per request, ≤ 100 ids per batch GET) | The upload sync must split word uploads into chunks. A 400 here is permanent for that payload, so retrying it unchanged is wrong (see §4.2) |
| **SEC-04 profile e-mail from the token:** `POST /users/` must send the token's own `email`, and the token must be e-mail-verified | Send the `email` claim of the current token, never one the user typed. Android does this already; KMP must do it when it adds profile creation (§3.1) |
| **SEC-09 `POST /users/{id}/vault` removed** | Nothing; neither client calls it. Vault entries come only from `POST /marketplace/dictionaries/{id}/import` |
| **SEC-13 account deletion needs a fresh login** (when built) | Before `DELETE /users/{id}`, start a login with `max_age=0` (or `prompt=login`), then delete with the new token |
| **SEC-05 audience check** | Nothing; the audience mapper sits on the `verborum-app` client. Tokens issued before the switch fail once and refresh |
| **SEC-10 reserved display names — live now** | `PUT /users/me/profile-info` and `POST`/`PUT /users/` answer 400 `displayName contains a reserved word …` for names with `Verborum`, `admin`, `moderator`, `official` (and `support`, `team`, `staff`, … as words). Show the message on the profile/Join screen and keep the input. Duplicate names are allowed by design |
| **`isPublic` handling** (being decided; see §4.1) | Wait for the backend decision before building the share toggle |

---

## 2. Security findings — Android (`verborum_android`)

What is already right (keep it): only the launcher activity is exported; the auth prefs are excluded
from cloud backup and device transfer; PKCE plus `state` through AppAuth; the insecure http
connection builder is gated on `BuildConfig.DEBUG`; auth failures are logged only in debug; logout
does a back-channel end-session; refresh clears the session only on 400/401.

| # | Severity | Finding | Fix |
|---|---|---|---|
| A-S1 | **High at release** | `app/src/main/res/xml/network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true">` for **all** builds, and `core/build.gradle.kts` `release` points at `http://192.168.0.241:*` | Move the cleartext permission into a `src/debug/res/xml/network_security_config.xml` override (and scope it to the dev hosts with `<domain-config>`); the main config should forbid cleartext. The release base URLs come from the go-live checklist (`docs/production-cutover.md`). Until then, a release build must fail to build rather than silently ship http, e.g. a Gradle check that release `buildConfigField`s start with `https://` |
| A-S2 | Medium | Debug `HttpLoggingInterceptor` at `BODY` (`core/.../di/NetworkModule.kt`) prints the `Authorization: Bearer …` header to logcat | `httpLoggingInterceptor.redactHeader("Authorization")`. Make sure the refresh client (`TokenAuthenticator.refreshClient`) never gets a body logger, because refresh responses contain tokens |
| A-S3 | Medium | Tokens are kept in `EncryptedSharedPreferences` with `MasterKeys` (`core/.../auth/AuthTokenStore.kt`). `androidx.security:security-crypto` is deprecated | Plan a migration: DataStore with values encrypted by an Android Keystore AES-GCM key (or Tink with an Android Keystore KEK). Migrate once on first start: read the old prefs, write the new store, delete the old. Keep the backup exclusions |
| A-S4 | Medium | Refresh-token rotation readiness (see §1, SEC-06): the refresh token is read outside the lock | Read it inside `synchronized`, after the "already refreshed?" check |
| A-S5 | Low | The OAuth redirect is the custom scheme `de.coldtea.verborum://`, so another app can register the same scheme | PKCE makes a stolen code useless, so this is acceptable. If you ever drop PKCE or add a confidential flow, switch to a verified App Link (`https://…/oauth2redirect`) |
| A-S6 | Low | Every non-2xx upload response is treated like "offline" (`bibliotheca/.../common/utils/ApiOutcome.kt` `succeededRemotely`). A 403 or 400 row is retried forever and silently | See §4.2; it is also a security hygiene issue, since repeated refused writes look like probing in server logs |

Not findings, but before release: confirm R8 is on for `app` release (it is: `app/build.gradle.kts`
`isMinifyEnabled = true`); and verify the keep rules don't keep more than the serialized DTOs.

## 3. Security findings — KMP (`verborum_kmp`)

What is already right: PKCE plus a fresh `state` per attempt; the web return route restores only a
`#fragment` onto the app's own origin, so it is not an open redirect; the iOS Keychain uses
`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`; iOS ATS allows only local networking; HTTP logging
is gated on a debug binary; the token pair is read behind a mutex.

| # | Severity | Finding | Fix |
|---|---|---|---|
| K-S1 | **High (web)** | The refresh token is in `localStorage` (`core/auth/.../TokenStorage.kt`, deliberate), **together with `offline_access`** (`AuthConfig.kt` scope). The backend currently allows refresh-token reuse and offline sessions never expire (SEC-06), so a single XSS gives an attacker a token that works indefinitely | (1) Web: do **not** request `offline_access`. A browser session can live on Keycloak's SSO session instead; make the scope platform-specific (`AuthConfig.web.kt`). (2) Add a strict Content-Security-Policy where the app is hosted, as a reverse-proxy header: `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'`. Adjust as the build needs and test it. `composeApp/src/webMain/resources/index.html` currently has none. (3) After SEC-06, rotation limits the damage further |
| K-S2 | Medium | KMP has no `/users` call at all, so no ms_user profile is created for iOS or web users. Account deletion and the `user.deleted` cascade are impossible for them | Add `UserApi`: after every login call `GET /users/me`, and on 404 only, `POST /users/` (body: new `userId` UUID, `keycloakId` = token `sub`, `email` = token `email` claim). See `marketplace-client-guide.md` §4 |
| K-S3 | Medium | Upload failures are swallowed by design (`UploadPendingChangesUseCase`: "Nothing is reported"). A permanent 400/403 is retried forever | See §4.2 |
| K-S4 | Low | The web has no local database (`createBibliothecaDatabase()` returns `null`), so offline edits live in memory and are lost when the tab closes | A product decision: either document it in the KMP README as intended, or persist pending writes (IndexedDB). It isn't a leak; it's data loss |
| K-S5 | Low | The iOS release base URL `https://api.verborum.coldtea.de` is a single origin, which only works once the backend gateway (Phase 5) exists | Nothing to fix; just don't ship iOS before Phase 5 |

---

## 4. Integration gaps (both clients unless marked)

### 4.1 Sharing (`isPublic`) vs. sync — wait for the backend decision
- Joining the Forum makes all of a user's dictionaries public **on the server**, in the background.
- Both clients let local unsynced edits win, so an edit made before the join is uploaded later with
  the old `isPublic: false`. That un-shares the dictionary, or is refused with 400
  `SharingRequiredException`.
- Both clients also always create with `isPublic: false`:
  - Android `bibliotheca/.../dictionary/domain/DictionaryService.kt` ~line 78
  - KMP `feature/bibliotheca/.../createdictionary/ui/CreateDictionaryViewModel.kt` ~line 156

  For a member with no shared dictionary, that create is refused with 400.

The backend will likely make `isPublic` optional on `PUT /dictionaries/` (absent = unchanged), or give
visibility its own endpoint. **Until that lands, don't build the share toggle.** When it lands:
- Send `isPublic` only when the user changed it.
- Create with the member default from `dictionary-sharing-client-guide.md` §5.

### 4.2 Tell "refused" apart from "offline" in the upload sync
Network error or 5xx → keep pending and retry. **4xx → stop retrying that row:**
- 401: refresh, then retry once.
- 403: mark the row "not allowed" and surface it. This also happens for an imported dictionary that
  must never be uploaded.
- 400 `SharingRequiredException`: re-fetch the user's dictionaries and take the server's `isPublic`.
- Any other 400: mark it failed and show it.

Add a `syncError` column (Room migration, with a `version` bump), so the UI can show a badge.

### 4.3 Profile and Forum membership (both)
Android calls `GET /users/{id}` and `POST /users/` only; KMP calls nothing. Both need:
- `GET /users/me` after every login
- `PUT /users/me/profile-info` for the display name, the marketplace agreement and leaving
- a **Join the Forum** screen

Non-members get **403** on every marketplace call. Seed users for testing:
- not members: `oliver.brown`, `nina.koch`, `mia.schulz`, `kerem.dogan`
- members: everyone else

### 4.4 Android Forum vs. the current marketplace contract
`forum/.../marketplace/data/MarketplaceRepository.kt` serves `MarketplaceDummyData`. When wiring the
real API:
- **Paging:** the envelope is `SliceResponse {items, page, size, hasNext}`, with no `totalElements`
  or `totalPages`. `MarketplacePageResponse.hasMore` computed from `totalPages` would be `false`
  forever, so only the first page would ever load. Model `hasNext` directly.
- **Filters:**
  - `pair=EN-TR`: repeatable, matches both directions, both codes required.
  - `tag`: repeatable.
  - `publisher`: part of a display name, at least the documented minimum length.

  The single-sided `fromLanguage`/`toLanguage` in `ForumDictionaryFilter` cannot be expressed, so
  change the search panel to pick pairs.
- **Fields that exist:** `publisherName` and `tags` are now on every listing; drop the per-listing tag
  fetch. **Fields that don't exist:** `rating` and `wordCount`. Ratings are not designed, so hide that
  UI or keep it behind a flag.
- Also available: `GET /marketplace/dictionaries/popular`, and `/publisher/{publisherId}` for "more
  from this publisher".
- **Base URL:** add `ROOT_URL_VERBORUM_MARKETPLACE_API` (`:8087`) to `core/build.gradle.kts`, and a
  `MarketplaceApi` Retrofit interface.
- The caller's own dictionaries never appear in browse (P4-17), so don't filter them client-side.

### 4.5 KMP: one base URL, one service
`ApiConfig` reaches only ms_dictionary:
- web: `/api` is proxied to `:8085` (`composeApp/webpack.config.d/devServerProxy.js`)
- iOS debug: `http://localhost:8085`

ms_user (`:8086`) and ms_marketplace (`:8087`) are unreachable. Either add per-service base URLs (web:
`/api/users` → 8086 and `/api/marketplace` → 8087 proxy entries with `pathRewrite`), or wait for the
backend gateway. The paths stay the same either way.

### 4.6 KMP Forum
`feature/forum/.../data/Listing.kt` is the original template placeholder (price, seller). Replace it
with the marketplace model when §4.3 and §4.5 are done, using Android's Forum as the reference.

### 4.7 Import and vault (both)
Not built in either client. Follow `marketplace-client-guide.md` §6.6–§6.9:
- Imported dictionaries are **read-only references**.
- They must stay out of the upload set entirely, or every write is a 403.
- Learning progress on imported words stays local.
- A vault entry whose dictionary is no longer readable shows as "No longer available".

### 4.8 Stale docs in the client repos
`verborum_android/docs/android-development.md` §2 and §7 still say authentication is "🔲 none" and
describe a language mismatch that has been fixed. Update them together with §0.

---

## 5. Suggested order

1. §0: refresh the contract docs.
2. §4.2: error classification in sync (unblocks diagnosing everything else).
3. A-S1, A-S2, A-S4 and K-S1: small, high value.
4. §4.3: profile and Join (Android first as the reference, then KMP with K-S2).
5. §4.4 (Android) and §4.5–§4.6 (KMP): the Forum on the real API.
6. §4.7: import and vault.
7. §4.1: sharing, once the backend `isPublic` change has landed.
8. A-S3: storage migration, before release.
