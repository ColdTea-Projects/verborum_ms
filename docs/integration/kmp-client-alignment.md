# KMP client (iOS + web) — backend alignment notes

**Written 2026-08-06** after reading `verborum_kmp` at `35a5191` against this backend. Companion to
`client-login-guide.md`, which stays the normative contract; this file only records where the KMP
client and the backend do not yet meet, and what the backend has ready for each.

**The KMP client is work in progress.** Nothing below is a defect report — every item is a backend
capability that exists and is unused, listed so it is not rediscovered later. Android already does
all four; it is the reference for each.

---

## What already lines up

No action needed on any of this:

- **Auth flow.** Authorization Code + PKCE against `verborum-app`, scope
  `openid profile email offline_access`, bearer stamped on every request, refresh on 401. Matches
  the contract exactly. `AuthConfig.web.kt` deriving the issuer from the browser origin
  (`{origin}/auth/realms/verborum`) keeps the token exchange same-origin — no CORS involved.
- **Ownership.** `SyncService` passes the signed-in `sub` as `userId`, which is what P3-05/P3-08
  require. The tightening does not break the client.
- **Wire format.** `DictionaryDto` / `WordDto` field names, the opaque `word`/`*Meta` blobs, the
  ISO-8601 UTC timestamps, and `MAX_LEVEL = 7` all agree with the DTOs here.
- **Web client id.** The web app authenticating as `verborum-app` rather than a separate web client
  is now the committed design — the unused `verborum-web` client was removed on 2026-08-06.

---

## 1. No ms_user profile is created

`ApiConfig` knows one base URL, `ms_dictionary` (`:8085` on iOS debug, `{origin}/api` on web). There
is no `UserApi` and no call to `/users`, so an iOS or web user logs in and works normally but has
**no row in ms_user**.

That is invisible today and stops being invisible the moment anything reads the profile:
`user_stats`, the vault, and account deletion all key off it — and `user.deleted` is what tells
ms_dictionary to drop that user's data, so a user with no profile row cannot be fully deleted.

Backend side is ready: `GET /users/{sub}` → on a clean **404** only, `POST /users/` with
`userId = keycloakId = sub`. A network error must *not* trigger the POST, or a flaky connection
creates duplicates. See `client-login-guide.md` §"After the first successful login".

## 2. `email_verified` is not checked

`JwtClaims` reads `sub`, `email` and `name`, but nothing reads `email_verified`, and there is no
equivalent of Android's `LoginOutcome.EmailNotVerified`.

Not a security hole — Keycloak will not issue tokens to an unverified account, so the backend is
never exposed. It is a dead end in the UX: after hosted sign-up Keycloak parks the browser on its
"verify your email" page and never redirects back, which to the client is indistinguishable from the
user dismissing the browser. Without the distinction the app returns silently to the login wall and
the user is told nothing.

## 3. No guest re-owning at login

`CreateDictionaryViewModel` writes `userId = activeUser().orEmpty()`, and nothing rewrites those rows
when the user later signs in. `SyncService` skips sync while signed out, so they sit locally with an
empty owner; on the first sync after login they fail `@ValidUUID` (400) or the ownership guard (403)
and never sync.

Android solves this with a `PostLoginHook` that re-stamps guest rows with the real `sub` and marks
them unsynced before the first sync runs. There is no backend endpoint for this and none is needed —
see `client-login-guide.md` §9 item 7.

## 4. Tags are local-only

Tags exist in the KMP UI (`DictionaryTag.kt`, the tag sections) but there is no tag API call, so they
never leave the device. The same account will show different tags on Android and on iOS/web.

The endpoints exist and Android uses them: `GET`/`POST /dictionaries/{id}/tags`,
`DELETE /dictionaries/{id}/tags/{tag}`. Tags are normalised (trimmed, lower-cased) on write and on
delete, and adding is idempotent, so a naive "push everything" sync is safe.

---

## Deployment notes for when the web app ships

- **Its origin must be registered.** Only `de.coldtea.verborum://oauth2redirect/*` and
  `http://localhost:*` are committed. A deployed origin is per-environment and is applied after
  realm import by `keycloak/bootstrap/configure.sh` from `APP_WEB_ORIGIN` — tell the backend the
  origin, it is not something the client can add.
- **Two issuer stories.** `AuthConfig.ios.kt` release points at `https://auth.verborum.coldtea.de`,
  while `AuthConfig.web.kt` derives `{origin}/auth`. Both can be true if a proxy fronts Keycloak on
  the web origin, but the deployment has to actually provide both paths. Worth settling before the
  first deploy rather than during it — a wrong issuer is a 401 on every API call that looks like a
  bad token (`client-login-guide.md` §7).
