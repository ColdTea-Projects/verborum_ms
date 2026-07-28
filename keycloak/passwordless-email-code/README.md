# Passwordless email-code login — design & build notes

**Status:** scoped, not yet wired into the running stack. This is the one piece of the
oauth2-hardening work that modifies the Keycloak image, so it is deliberately staged last —
a mis-bound authentication flow can lock every user out.

## Requirement (agreed 2026-07-28)

A user may sign in with **either** their password **or** a one-time **code emailed to them** — the
code is an *alternative* first factor, not a second factor on top of the password. It is only
offered once the account's **email is verified** (`verifyEmail: true`, realm-wide). No magic link.

## Why this needs more than realm config

Keycloak 23 ships **no native email-OTP authenticator**. (Native email OTP arrives in later
Keycloak.) So the emailed-code step is a community **Authenticator SPI** that must be baked into a
custom Keycloak image, then referenced by a custom **browser authentication flow**.

Two moving parts, both required — a half-wired flow fails at the login screen:

1. **The SPI jar** — an email-code authenticator provider dropped into
   `/opt/keycloak/providers/`, built into the image (see `Dockerfile`).
2. **A custom browser flow** — replaces the default so the login page offers
   "password" *or* "email me a code" as alternative executions, the email-code path gated on
   `emailVerified == true`.

## Target flow shape

```
Verborum Browser Flow
└─ forms (subflow)
   ├─ Username/Email form        [REQUIRED]
   └─ First-factor (subflow)     [REQUIRED]
      ├─ Password                 [ALTERNATIVE]
      └─ Email-code (subflow)     [ALTERNATIVE]
         ├─ Condition: user configured & emailVerified   [CONDITIONAL]
         └─ Email OTP authenticator (SPI)                 [REQUIRED]
```

`ALTERNATIVE` on both means the user picks one. The condition ensures the code path is hidden until
the address is verified — matching the rule "email-code only after email is verified".

## Build steps (the concrete remaining work — roadmap P3B-06)

1. **Choose & pin an SPI** compatible with Keycloak 23. Candidates to evaluate (verify the exact
   version support before pinning — do not trust a floating tag):
   - a maintained `keycloak-email-otp` / passwordless-email authenticator, or
   - fork/build a minimal authenticator (it is ~2 small classes: an `Authenticator` that generates a
     code, mails it via Keycloak's `EmailSenderProvider`, and validates the input; plus a
     `AuthenticatorFactory`).
   Record the choice and its provenance here before committing a jar/URL.
2. **`Dockerfile`** (skeleton in this folder): `FROM quay.io/keycloak/keycloak:23.0.0`, copy the jar
   into `/opt/keycloak/providers/`, `RUN /opt/keycloak/bin/kc.sh build`.
3. **docker-compose:** switch the `keycloak` service from `image:` to `build: ./keycloak/passwordless-email-code`.
4. **Custom flow:** define it as data. Prefer adding it to `keycloak/bootstrap/configure.sh` via
   `kcadm.sh` (create flow → executions → set the realm's `browserFlow`) so it is env-driven and
   reversible, rather than hand-editing the realm JSON's `authenticationFlows` (large and brittle).
5. **Verify before trusting:** with the flow bound, confirm on a throwaway realm/user that BOTH
   password login AND email-code login succeed, the code path is hidden for an unverified email, and
   `testuser`/`testadmin` can still log in. Only then point the shared realm at the new flow.

## Rollback

The custom flow is additive until step 4 binds it as the realm `browserFlow`. Rollback = set
`browserFlow` back to `browser` (the built-in) via kcadm and restart. Keep that one command handy
during rollout.
