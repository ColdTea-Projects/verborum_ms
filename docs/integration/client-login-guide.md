# Client Login Guide — Android, iOS, Web

Practical companion to `frontend-backend-integration.md` §6, which stays normative. §6 says *what*
the contract is; this file says *how to build against it* and records the answers to questions the
client teams actually asked. If the two ever disagree, §6 wins and this file is the bug.

Last verified against a running stack: **2026-08-06** (Phase 3 done; P3B-01…P3B-07 done and
browser-verified). Reconciled on the same date against the Android client's own copy of this file,
which had been amended ahead of it — where the two differed, the Android copy was right.

---

## 1. What exists today

| | Status |
|---|---|
| Keycloak | Running at `http://localhost:8180`, realm `verborum` |
| Realm config | Code, not console clicks — `keycloak/import/verborum-realm.json` |
| `verborum-app` | Public client, Authorization Code + **PKCE S256 enforced**. **Every** platform uses it — Android, iOS *and* web. The separate `verborum-web` client was removed 2026-08-06; it was locked to `localhost:3000` and nothing used it |
| Hosted sign-up | **Enabled** — clients build no registration form |
| Email verification | **Required** (`verifyEmail: true`) — a new account must confirm its address before it can obtain tokens |
| Password reset | Enabled (hosted "Forgot Password") |
| SMTP (local) | **Configured via Mailpit** — verification/reset mail is captured, readable at http://localhost:8025 (never actually sent) |
| Google sign-in | **Live** (P3B-04, browser-verified 2026-07-28) — federated behind Keycloak, button on the hosted page. Needs that environment's `.env` credentials, and a tester's Google account must be allow-listed |
| Facebook sign-in | **Live** (P3B-05, browser-verified 2026-07-28) — same shape as Google. Public access still gated on Meta App Review; testers must be added as Testers on the Meta app |
| Passwordless email-code login | **Live** (P3B-06, browser-verified 2026-07-28) — "Try another way" → "Email me a sign-in code", hand-written Keycloak SPI baked into the custom image. No client code |
| Login page branding | **Themed** — hosted login/registration/verify-email carry the Verborum design (crimson/gold, light + dark). Nothing to build client-side; the page AppAuth opens is simply branded |
| ms_user (`:8086`) | Secured. All endpoints require a valid JWT |
| ms_dictionary (`:8085`) | **Secured (P3-03)**, and since **P3-05/P3-08 the owner comes from the token** — a client-supplied `userId` is no longer trusted. See §9 item 2 for the exact status codes |
| Field limits | Enforced server-side as of 2026-08-06: `level` must be 0..7, and `word`/`translation`/`*Meta` are length-bounded. A violation is a 400 naming the field, not a silent accept |
| API gateway | Not built (backend Phase 5). Talk to services directly for now |

---

## 2. Client registration values

```
issuer          http://localhost:8180/realms/verborum
discovery       {issuer}/.well-known/openid-configuration
client_id       verborum-app          (Android, iOS AND web — there is no separate web client)
redirect_uri    de.coldtea.verborum://oauth2redirect/<path>   (Android, iOS)
                http://localhost:*    (emulator/loopback, and the local web dev server)
scopes          openid profile email offline_access
```

Android manifest placeholder: `manifestPlaceholders = [appAuthRedirectScheme: "de.coldtea.verborum"]`.
iOS: register `de.coldtea.verborum` as a URL type.
Web: the redirect target is the app's own page (`https://<origin>/`), consumed and stripped from the
URL on the next start — no custom scheme involved.

Adding a **local** redirect URI means editing `keycloak/import/verborum-realm.json` in the backend
repo. A **deployed** web origin is not committed: tell the backend the origin and it is applied by
`keycloak/bootstrap/configure.sh` from `APP_WEB_ORIGIN` (per-environment, like the social-login
secrets). Either way an unregistered URI is rejected before the login page renders.

**PKCE is enforced, not advisory.** An authorization request without `code_challenge` fails with
`invalid_request: Missing parameter: code_challenge_method`. AppAuth and
`ASWebAuthenticationSession` send it for you; anything hand-rolled must.

**`offline_access` is an optional scope** — request it explicitly or you get no long-lived refresh
token, and a device offline for days will force a re-login.

---

## 3. Sign-up: hosted, not a native form

Send the user to the **same** authorization endpoint with `/registrations` instead of `/auth`, with
identical PKCE parameters:

```
{issuer}/protocol/openid-connect/registrations
    ?client_id=verborum-app
    &response_type=code
    &scope=openid%20profile%20email%20offline_access
    &redirect_uri=de.coldtea.verborum://oauth2redirect/cb
    &code_challenge={challenge}
    &code_challenge_method=S256
```

It returns Keycloak's account-creation form and finishes with the same code exchange as login — one
auth flow in the client, not two. With AppAuth, reuse the `AuthorizationRequest` and swap the
endpoint.

**Why not a native registration screen:** `POST /users/` does **not** create a Keycloak identity. It
creates the *profile row* keyed on `keycloakId`. Creating the identity needs ms_user's Keycloak Admin
API path (backend P3-04), which is not built. Hosted registration also keeps Keycloak the single
identity authority, which is the design in §6.

### After the first successful login

Create the profile once:

```
POST /users/            (ms_user, requires the bearer token)
{
  "userId":     "<client-generated UUID>",
  "keycloakId": "<JWT sub>",
  "email":      "<from the ID token>",
  "displayName":"<from the ID token>"
}
```

**After every login, call `GET /users/me`** (since 2026-10-04): it returns
`{id, email, displayName, marketplaceAgreementAccepted, marketplaceAgreementVersion}` for the token's
own user — no stored id needed — and builds the profile page. **404 means there is no profile yet:**
create it with the `POST /users/` above, then call `/me` again. Change the name or the marketplace
agreement with `PUT /users/me/profile-info` (marketplace guide §0).

Safe rule: call it whenever `GET /users/me` (or `GET /users/{userId}`) returns 404. `email` must be unique — one profile
per email is a product rule enforced in the database.

`userId` is still client-supplied. Backend P3-05 will switch it to the JWT subject; that is a
coordinated breaking change and will be announced.

---

## 4. Identity — which id goes where

This trips people up, so be precise:

- **`sub` (the JWT subject)** is what every service stores as `fk_user_id`. Dictionaries, words and
  vault entries all belong to a `sub`. In ms_user's own table this same value is the `keycloakId`
  column.
- **`userId`** is ms_user's *own* primary key. It exists only inside ms_user. Do not send it to
  ms_dictionary and do not expect other services to know it.

So: dictionaries and words you upload carry `userId` = **the JWT `sub`**, not ms_user's `userId`.

---

## 5. Token handling

- Send `Authorization: Bearer <access>` on every call.
- Access tokens last **5 minutes**. Refresh tokens are long-lived with `offline_access` (offline
  session idle 60 days).
- On `401`: refresh **once**, retry, and if that fails surface the login screen. An OkHttp
  `Authenticator` is the natural place on Android.
- Store tokens in EncryptedSharedPreferences / DataStore backed by Keystore (Android) or Keychain
  (iOS). Never in plain preferences, never in web `localStorage`.

---

## 5a. Account deletion

`DELETE /users/{userId}` now removes **everything**: the profile, its vault and stats, the user's
dictionaries and words in ms_dictionary (via the `user.deleted` event), and **the Keycloak account
itself**. After it the person cannot log in — their tokens are for an identity that no longer
exists — and signing up again produces a genuinely new account.

Clients should treat it as irreversible, confirm it explicitly, and afterwards clear local data and
return to the login screen rather than attempting a refresh (which will fail).

## 6. Logout

1. `POST {issuer}/protocol/openid-connect/logout`, form-encoded `client_id` + `refresh_token`.
2. Optionally revoke at `{issuer}/protocol/openid-connect/revoke`.
3. Delete both tokens from encrypted storage.

**Step 1 is not optional.** Dropping local tokens without ending the Keycloak session leaves an SSO
session behind, and the next login silently signs the same user back in with no prompt — the classic
"logout doesn't work" bug.

Local data: keeping synced rows after logout is fine. What must not happen is two users' rows mixing
in one local store — the store is keyed by owner id, so treat *a different user* logging in on the
same device as wipe-and-resync.

---

## 7. Testing on a physical device — read before you debug a 401

Keycloak stamps the issuer into every token. Unpinned, it echoes back whatever `Host` the caller
used: a phone hitting `http://192.168.0.x:8180` gets `iss: http://192.168.0.x:8180/...`, while the
services validate `iss: http://localhost:8180/...`. **Every API call then fails with a 401 that looks
like a broken token rather than a config mismatch.**

The backend compose pins the issuer via `KC_HOSTNAME_URL` (default `http://localhost:8180`). For
device testing, all three must name the same origin:

```
KEYCLOAK_HOSTNAME_URL=http://<lan-ip>:8180                                         # keycloak
KEYCLOAK_ISSUER_URI=http://<lan-ip>:8180/realms/verborum                           # each service
KEYCLOAK_JWK_SET_URI=http://<lan-ip>:8180/realms/verborum/protocol/openid-connect/certs
```

Verified: with the hostname pinned, tokens carry that issuer no matter which host requested them,
including `localhost`. A token that works on the emulator but not on the phone means these three
disagree.

---

## 8. Getting a token without a login UI

For testing an API layer before the login screen exists:

```bash
curl -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli \
  -d username=testuser -d password=testuser -d grant_type=password
```

`verborum-dev-cli` is a **local-dev-only** client with password grant enabled. It is deliberately
**not part of the auth contract** and must never exist in a shared or production realm — the real
clients are PKCE-only. Dev users: `testuser`/`testuser` (role `user`),
`testadmin`/`testadmin` (`user` + `admin`).

---

## 9. Known gaps — plan around these

1. **ms_dictionary now requires a token (breaking change, 2026-07-23).** It was open until backend
   P3-03; any client calling `:8085` without `Authorization: Bearer <access>` gets **401** on every
   endpoint — dictionaries, words, batch fetches, the lot. If your sync suddenly fails everywhere,
   this is why. `/actuator/health` and Swagger stay open.
2. **The server now enforces ownership (breaking change, 2026-07-23, backend P3-05 + P3-08).**
   Identity comes from the token, not from what you send:
   - Sending a `userId` that is not your JWT `sub` on `POST`/`PUT /dictionaries/` → **403**. Send
     your own `sub`, or the request fails. (The stored owner is the token's subject either way.)
   - `GET /dictionaries/{userId}` and `GET /words/user/{userId}` must name **you** → otherwise 403.
   - Writing words into a dictionary you do not own → **403**.
   - Deleting a dictionary or its words when you are not the owner → **403**.
   - Reading someone else's dictionary by id → **404** (deliberately indistinguishable from a
     missing one). Batch and list endpoints silently return only your own rows, so
     `/dictionaries/batch`, `/words/batch`, `/words/dictionary/{id}` and `/words/language/...` may
     come back shorter than you asked for — that is not an error.

   Practical upshot for sync: keep uploading your own `sub` as `userId` and nothing changes. If you
   see 403s after this lands, you are sending the wrong owner id — most likely the guest UUID
   (`00000000-...`) that §6.4 says must be rewritten at first login.
3. ~~**Google & Facebook sign-in: credentials pending.**~~ **Resolved 2026-07-28** — both are live
   and browser-verified. They remain federated behind Keycloak (never integrate a social SDK
   directly) and render as buttons on the hosted login page the client already opens, so **no client
   code exists for them**. What each environment still needs is its own `.env` credentials, plus
   per-tester allow-listing while the Google app is in Testing and the Meta app in Development. The
   step-by-step for that went to the client teams as `verborum_instructions28_08_26.md` §5–§6 (it
   lives in the client repos, not this one). A social login
   yields a normal `sub` and arrives pre-verified; the profile-creation dance (`POST /users/` on a
   404) is unchanged. Apple was dropped.
4. **No API gateway** until backend Phase 5. Clients address services directly and must carry per-
   service base URLs.
5. **Email verification is required, and SMTP works locally via Mailpit.** After hosted sign-up the
   account must confirm its address before it can obtain tokens. Locally the mail lands in the
   Mailpit UI (http://localhost:8025), not a real inbox. Social logins (Google/Facebook) arrive
   pre-verified and skip this step.

   This is not just a UX note — **it changes what a cancelled login looks like.** Keycloak parks the
   browser on its "verify your email" page and never redirects back, which to the client is
   indistinguishable from the user dismissing the browser. Remember which endpoint you launched
   (`/auth` vs `/registrations`) and, on a null result from a sign-up, show "check your inbox"
   rather than silently returning to the login wall. Also treat an `email_verified: false` claim on
   the id token as a failed login and store no tokens. The Android client does both
   (`LoginViewModel`, `LoginOutcome.EmailNotVerified`) and is the reference for this.

5a. **Passwordless email-code sign-in is live** (2026-07-28) and needs no client code: it is a step
   inside the hosted page, reached via "Try another way" → "Email me a sign-in code". Locally the
   code arrives in Mailpit. It yields the same tokens and the same `sub` as any other login.
6. **Roles are not enforced on any endpoint yet.** Realm roles map correctly to
   `ROLE_user` / `ROLE_admin`, but no endpoint requires one, so do not build UI that depends on
   role-based 403s.
7. **Guest-data migration is client-side** (§6.4): rewrite the local owner id from the guest UUID to
   the JWT `sub`, mark rows unsynced, run the normal upload. No backend endpoint exists or is
   needed. The guest UUID must never reach the server after login.
8. **Field limits are enforced server-side as of 2026-08-06.** Previously the clients were the only
   thing bounding these, which meant a bug or a third-party client could store a value the clients
   then refuse to read — `level: 99` is the concrete case, and both clients already "heal" it by
   resetting to 0 and re-uploading. The server now refuses it up front:

   | Field | Limit | On violation |
   |---|---|---|
   | `level` | integer 0–7, or absent | 400 |
   | `word`, `translation` | 2000 chars | 400 |
   | `wordMeta`, `translationMeta` | 4000 chars | 400 |
   | dictionary `name` | 255 chars | 400 (was a 500 from Postgres) |

   The text limits bound the **serialised** value, not one typed surface: `word` is a JSON array of
   per-meaning surfaces and the meta is the structure describing them, so 2000/4000 leaves room for
   far more meanings than a person writes by hand. No client that respects its own 40/150-character
   typing caps can hit them. If you do see a 400 here, it is a serialisation bug, not a user typing
   too much — treat it as one and do not silently drop the row.
