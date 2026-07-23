# Client Login Guide — Android, iOS, Web

Practical companion to `frontend-backend-integration.md` §6, which stays normative. §6 says *what*
the contract is; this file says *how to build against it* and records the answers to questions the
client teams actually asked. If the two ever disagree, §6 wins and this file is the bug.

Last verified against a running stack: **2026-07-23** (backend roadmap P3-01/P3-02/P3-07 done).

---

## 1. What exists today

| | Status |
|---|---|
| Keycloak | Running at `http://localhost:8180`, realm `verborum` |
| Realm config | Code, not console clicks — `keycloak/import/verborum-realm.json` |
| `verborum-app` (mobile) | Public client, Authorization Code + **PKCE S256 enforced** |
| `verborum-web` | Public client, PKCE S256, redirect `http://localhost:3000/*` |
| Hosted sign-up | **Enabled** — clients build no registration form |
| Password reset | Enabled (hosted "Forgot Password"); **no SMTP in local dev**, so mail does not send |
| ms_user (`:8086`) | Secured. All endpoints require a valid JWT |
| ms_dictionary (`:8085`) | **Not secured yet** (backend P3-03). Open, and trusts a client-supplied `userId` |
| Google sign-in | **Not configured** — needs real Google OAuth2 credentials |
| API gateway | Not built (backend Phase 5). Talk to services directly for now |

---

## 2. Client registration values

```
issuer          http://localhost:8180/realms/verborum
discovery       {issuer}/.well-known/openid-configuration
client_id       verborum-app          (Android + iOS)
redirect_uri    de.coldtea.verborum://oauth2redirect/<path>
                http://localhost:*    (emulator/loopback only)
scopes          openid profile email offline_access
```

Android manifest placeholder: `manifestPlaceholders = [appAuthRedirectScheme: "de.coldtea.verborum"]`.
iOS: register `de.coldtea.verborum` as a URL type.

Adding a redirect URI means editing `keycloak/import/verborum-realm.json` in the backend repo — an
unregistered URI is rejected before the login page renders.

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

Safe rule: call it whenever `GET /users/{userId}` returns 404. `email` must be unique — one profile
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

1. **ms_dictionary is unsecured** (backend P3-03) and trusts a client-supplied `userId`. During
   development you will have a logged-in app talking to an open service; that is not working auth.
2. **`userId` moves to the JWT subject** at backend P3-05 — a coordinated breaking change on both
   sides.
3. **Google sign-in is not configured.** Federated-behind-Keycloak is still the design (never
   integrate Google SDK directly), but the button cannot work until real credentials exist.
4. **No API gateway** until backend Phase 5. Clients address services directly and must carry per-
   service base URLs.
5. **No SMTP**, so hosted password reset and email verification do not deliver mail locally.
6. **Roles are not enforced on any endpoint yet.** Realm roles map correctly to
   `ROLE_user` / `ROLE_admin`, but no endpoint requires one, so do not build UI that depends on
   role-based 403s.
7. **Guest-data migration is client-side** (§6.4): rewrite the local owner id from the guest UUID to
   the JWT `sub`, mark rows unsynced, run the normal upload. No backend endpoint exists or is
   needed. The guest UUID must never reach the server after login.
