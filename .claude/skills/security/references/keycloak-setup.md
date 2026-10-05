# Keycloak Setup and the Auth Contract

Keycloak 26, realm `verborum`, issuer `http://localhost:8180/realms/verborum` locally.

## Clients

| Client id | Type | Flow | Used by |
|---|---|---|---|
| `verborum-app` | public | Auth Code + PKCE | Android, iOS, **web** |
| `verborum-backend` | confidential | service account | ms_user → Keycloak Admin API |
| `verborum-dev-cli` | public | **direct access grants (password)** | **LOCAL DEV ONLY** |

**One client for every platform.** A separate `verborum-web` client existed and was removed: the
web app authenticates as `verborum-app` with its own page as the redirect target. If a web
backend-for-frontend is ever built, that is a *confidential* client and a new decision.

**`verborum-dev-cli` is not part of the contract.** It exists so a single curl can obtain a user
token instead of driving a browser through PKCE. It must never exist in a shared or production
realm — enabling password grant on `verborum-app` would have contradicted the PKCE-only spec, which
is why it is a separate throwaway client. Delete it from any realm export that leaves a developer
machine.

**Redirect URIs:** `verborum-app` → `de.coldtea.verborum://oauth2redirect/*` and
`http://localhost:*`. An unregistered URI is rejected before the login page renders. A deployed web
origin is not committed — it is applied after import from `APP_WEB_ORIGIN`. The client's
`webOrigins` stays `"+"`, deriving the CORS allowlist from the redirect URIs.

**PKCE is enforced.** The public client sets `pkce.code.challenge.method=S256`; a request without
`code_challenge` fails with `invalid_request`. Not advisory.

## Token policy

Access tokens 5 minutes, SSO idle 30 minutes, offline session idle 60 days so a device offline for
days resumes sync without re-login. Clients send `Authorization: Bearer <access>`, refresh once on
401, then surface login.

**Logout** uses the end-session endpoint with `client_id` + `refresh_token`, then deletes local
tokens. Skipping it leaves an SSO session that logs the user straight back in.

## Roles

Realm roles `user` (every registered account) and `admin`, mapped to `ROLE_user` / `ROLE_admin`.
Client roles under `resource_access` are unused.

## Realm import — the mechanics that surprise people

`keycloak/import/verborum-realm.json` is the source of truth: realm, clients, roles, local dev
users. Versioned in git, imported by `--import-realm`.

- The import runs **only on the first start of an empty data volume**:
  ```bash
  docker compose down
  docker volume rm verborum_ms_keycloak_data
  docker compose up -d keycloak
  ```
- Changes made in the admin console are **not** written back to the file.
- **`${ENV}` substitution does not work on the native import path** (Keycloak issues 12069 and
  26275). A placeholder is stored as a literal string and silently breaks login. Non-secret config
  lives in the JSON; secrets and per-environment overrides are applied afterwards by
  `keycloak/bootstrap/configure.sh` via `kcadm.sh` — idempotent, and a no-op locally.
- The image is custom: stock Keycloak 26 plus the Verborum email-code authenticator SPI, with
  themes bind-mounted. See `infra-ops`.

**Issuer pinning (`KC_HOSTNAME`, env `KEYCLOAK_HOSTNAME_URL`).** Keycloak stamps the issuer into every token; unpinned it
echoes the caller's Host header, so a phone on a LAN address gets tokens no service accepts — a 401
that reads like a bad token. For device testing set `KEYCLOAK_HOSTNAME_URL` plus every service's
`KEYCLOAK_ISSUER_URI` and `KEYCLOAK_JWK_SET_URI` to the same origin.

## Email

Email verification is required (`verifyEmail: true`): a newly registered account must confirm its
address before it can obtain tokens. Locally the realm's `smtpServer` points at the Mailpit
container, so mail is captured and readable at http://localhost:8025, never sent. Other
environments use a real provider injected after import.

**Passwordless email-code login** — signing in with an emailed one-time code after the address is
verified — needs a community SPI in the custom image plus a custom browser flow. Design, build
steps and rollback are in `keycloak/passwordless-email-code/README.md`.

## Account deletion

`verborum-backend`'s service account holds `realm-management` `manage-users` and `view-users`,
granted by the realm import. ms_user uses it to delete the Keycloak identity when a profile is
deleted — otherwise the account survives and can simply re-register through hosted sign-up. It
never creates identities. Without `KEYCLOAK_ADMIN_CLIENT_SECRET` the call is skipped with a WARN,
so local development still works.

## Social sign-in

Google and Facebook federate behind Keycloak — the client never touches a social SDK. The wiring
exists: `keycloak-bootstrap` creates each identity provider from environment variables. Both stay
**off** until real OAuth credentials exist. Each environment needs its **own** OAuth app, because
the redirect URI is `{issuer}/broker/{google|facebook}/endpoint`, which differs per environment.
Apple was considered and dropped.

## Local dev users

`testuser`/`testuser` with role `user`, `testadmin`/`testadmin` with `user` + `admin`.
Self-registration is open in this realm because sign-up is hosted — review that before any realm
that is not a developer laptop.
