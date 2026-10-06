# Account and Session Rules

The rules about *who* a caller is, how long their session counts, and which actions need more than a
valid token. Ownership (who may touch which row) is in [ownership-rules.md](ownership-rules.md).

## Identity data comes from the token, verified

A field that identifies a person, and is unique across accounts, must come from the token and not from
the request body. Otherwise one user can claim another's value and block their sign-up.

```java
// ms_user SecurityUtils (SEC-04)
public static String getVerifiedEmail() {
    Jwt jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
    Object email = jwt.getClaims().get("email");
    Object verified = jwt.getClaims().get("email_verified");
    boolean isVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
    if (!(email instanceof String address) || address.isBlank() || !isVerified) {
        throw new ForbiddenOperationException(EMAIL_NOT_VERIFIED);          // 403
    }
    return address.trim();
}
```

- The controller passes it into the service as an explicit argument, like the subject.
- If the body still carries the field for compatibility, a different value is **400** (the client has a bug),
  never a silent substitution. Compare case-insensitively and store the token's form.
- A service-account token has no `email`, so the same check also refuses non-user callers.

## Irreversible actions need a recent login

Account deletion (it removes the Keycloak identity and every dictionary) must not work with just any valid
access token. `RecentLoginGuard` (ms_user) runs before `DELETE /users/{id}`:

- The login time is the token's **`auth_time`**, which a refresh carries over unchanged, so refreshing never
  makes a login recent. The limit is `verborum.account-deletion.max-login-age-seconds` (300).
- A stale login is **403 `ReauthenticationRequiredException`**, deliberately not 401: both clients refresh
  and retry on 401, and a refresh cannot fix this, so a 401 would loop. The client signs in again with
  `max_age=0`, then retries.
- `auth_time` comes from Keycloak 25+'s `basic` client scope and is present on **browser (Authorization
  Code) logins**. A **direct password grant** (`verborum-dev-cli`) has none, so the guard falls back to
  `iat`, because each such token is itself a fresh password login. The dev client must not exist in a
  shared realm (SEC-11).
- The guard is a `@Component` with a `@Value` constructor. Putting the check in `UserServiceImpl` would
  break its `@InjectMocks` tests, so the controller calls it, like `requireSelf`.

## Token policy (realm)

| Setting | Value | Why |
|---|---|---|
| `revokeRefreshToken` / `refreshTokenMaxReuse` | `true` / `0` | Every refresh issues a new refresh token; replaying a used one **revokes the session** (reuse detection) |
| `offlineSessionIdleTimeout` | 60 days | A device offline for weeks resumes sync |
| `offlineSessionMaxLifespanEnabled` / `offlineSessionMaxLifespan` | `true` / 180 days | Hard ceiling, however often the session is used |
| `accessTokenLifespan` | 5 min | |
| `aud` | must contain `verborum-api` | Set by an audience mapper on the user-facing clients; every service checks it ([resource-server-config.md](resource-server-config.md)) |

Realm settings live in **two places**: the import JSON for fresh realms, and an idempotent step in
`keycloak/bootstrap/configure.sh` for realms that already exist. Change both. Clients must store every
rotated refresh token atomically and refresh single-flight (the client handoff docs say so).

## Pitfalls

- A unique identity field (email, …) accepted from the body
- An irreversible endpoint protected only by "authenticated"
- Answering a stale login with 401, which the clients would refresh-and-retry forever
- Assuming every token has `auth_time`: password-grant tokens don't
- Changing a realm setting in the import JSON only; existing realms never re-import it
