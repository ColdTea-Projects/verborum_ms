# Account Deletion — Client Guide

**Audience:** the three clients (Android, iOS, web). This guide gives one flow, one set of error rules and
one warning text for "Delete my account".

**Backend state:** roadmap `P5A-01`, 2026-10-25. The backend is ready. No client calls it yet.
**Status of the warning text:** approved by the owner on 2026-10-25 as the working text. The text is not legal
advice. A lawyer reviews it later (`BL-07`, question 10).

The login detail is in `client-login-guide.md` §5a. The vault rules are in `marketplace-client-guide.md` §6.7.
This guide does not repeat them. It joins them into one flow.

---

## 1. Why the app must do this

- **Apple** requires an in-app way to delete an account when the app lets people create one (App Store
  Review Guideline 5.1.1(v)).
- **Google Play** requires the same in the app, plus a web page where a person can ask for deletion
  without the app (Phase 5R, Stage 6, builds that page). `[CHECK the current Play policy before release]`
- **GDPR Article 17** gives the person the right to erasure. The in-app button is how we do it.
- The Keycloak account page does **not** replace this. It deletes only the sign-in identity. The
  dictionaries and the profile stay.

---

## 2. What the backend does

One call: `DELETE /users/{userId}`. `{userId}` is the `id` from `GET /users/me`. It is not the token `sub`.

| Step | Where | What happens |
|---|---|---|
| 1 | ms_user | Checks that the login is recent (§4). Checks that the profile belongs to the caller. |
| 2 | ms_user | Deletes the profile. The vault entries and the statistics go with it (database cascade). |
| 3 | ms_user | After the commit: sends the event `user.deleted`, and asks Keycloak to delete the identity. |
| 4 | ms_dictionary | Deletes all dictionaries, words and tags of the user. |
| 5 | ms_marketplace | Deletes the ratings the user gave. Recalculates the averages. Deletes the user's import records, listings and display name. |
| 6 | ms_notification | Deletes all device tokens of the user. |

Rules for the client:

- The answer is **200** when the profile is deleted. Steps 4 to 6 finish a few seconds later. They are
  asynchronous. Do not wait for them.
- A repeat call is **200** and does nothing. An unknown id is also **200**. Do not treat 200 as proof that the
  profile existed.
- A different user's `{userId}` is **403**. This is a client bug, not a flow.
- The Keycloak identity is deleted after the commit, and the call does not report a failure of that step.
  The backend writes an ERROR log line with the id. The owner then deletes the identity by hand.

### What stays

| Item | Why |
|---|---|
| Vault entries of **other users** that point to the deleted dictionaries | A vault entry is a reference, not a copy. It shows "No longer available" and the other user removes it. The backend does not remove it (`marketplace-client-guide.md` §6.7). |
| Server logs and backups | Logs are short-lived. There are no backups yet. The retention rules come with Phase 5L and 5R. |
| Anything the person copied to their own device from the Forum | The app keeps no copies of other people's dictionaries. |

Say the first row in the warning text. Do not promise that "everything" is erased from every system.

---

## 3. The flow

```
Profile page
  │  tap "Delete account"
  ▼
[A] Warning screen (§6) ── checkbox "I understand" ── Cancel ──► Profile page
  │  tap "Continue"
  ▼
[B] Sign in again: authorization request with max_age=0   ── cancelled or failed ──► Profile page
  │  new tokens
  ▼
[C] Check: the new token has the same sub as the old one  ── different ──► stop, show §5 "other account"
  ▼
[D] Final confirmation dialog: "Delete my account permanently?"  ── Cancel ──► Profile page
  │  tap "Delete"
  ▼
[E] DELETE /users/{userId}  (the new access token)
  │  200
  ▼
[F] Wipe local data (§7), then show the login screen
```

Rules:

1. **Two confirmations.** The checkbox on screen [A] and the dialog [D]. We do not ask the person to type a
   word, because the word would need 19 translations and a keyboard for each.
2. **The sign-in at [B] is a real login.** Send `max_age=0` on the authorization request. The person must
   enter the password or the code again. A refresh does **not** work, because a refresh keeps the old
   `auth_time` (SEC-13).
3. **Do not call `DELETE` before [B] finishes.** Use the **new** access token. Do not reuse the old one.
4. **Do not log the person out before [E].** If [B] or [D] is cancelled, the person stays signed in and goes
   back to the profile page.
5. **Do not call `DELETE` again after [F].** The tokens are gone.

---

## 4. The recent-login rule (SEC-13)

The backend accepts the call only if the token's login time is **under 5 minutes old**.

- Login time is the claim `auth_time`. A refresh does not change it.
- If the person takes longer than 5 minutes between [B] and [E], the call fails. See §5.

Per platform:

| Platform | How to send `max_age=0` |
|---|---|
| Android (AppAuth) | `AuthorizationRequest.Builder.setAdditionalParameters(mapOf("max_age" to "0"))` |
| KMP iOS and web | Add `max_age=0` to the authorization URL. |

Do not send `max_age=0` on the normal login. It would force a password prompt each time.

---

## 5. Errors

The error body has the fields `status`, `error`, `errorDetail`, `path` and `timestamp`.

| Answer | `error` | Meaning | What the client does |
|---|---|---|---|
| **200** | — | Deleted. | Go to [F]. |
| **401** | — | The access token is not valid. | Refresh once. If it still fails, go to [B]. Do not delete local data. |
| **403** | `ReauthenticationRequiredException` | The login is older than 5 minutes. | Go back to [B]. Show a short text: "Sign in again to continue." **Never retry automatically.** Do not treat it as a refresh case. A refresh cannot fix it, and a retry loop would result. |
| **403** | `ForbiddenOperationException` | `{userId}` is not the caller's profile. | Client bug. Show a general error. Do not delete local data. |
| **5xx**, timeout, no network | — | The result is unknown. | See §8. |

Other account at [C]: the person signed in as a different account at [B]. Do not call `DELETE`. Show
"This is a different account. Nothing was deleted." Go back to the profile page. Clear the second session.

---

## 6. The warning text (approved 2026-10-25, lawyer review pending)

Write it once in English here. Each client localizes it into the 19 app languages. Keep the meaning, the
order and the list. Use short sentences.

**Screen [A] — title:** `Delete your account`

**Screen [A] — body:**

> You are about to delete your Verborum account.
>
> **We delete this:**
> - Your profile and your display name
> - All your dictionaries, words and tags
> - Your vault and your learning statistics
> - Your ratings
> - The devices that receive notifications from us
> - Your sign-in account
>
> **This cannot be undone.** We cannot get your data back. You cannot get it back.
>
> **Other people:** If you shared dictionaries in the Forum, they disappear for everyone who added them. These
> people see "No longer available".
>
> **Before you continue:** We ask you to sign in again. This protects your account.
>
> If you sign up again later, you get a new, empty account.

**Screen [A] — checkbox:** `I understand that this cannot be undone.`

**Screen [A] — buttons:** `Continue` (destructive style) and `Cancel`

**Screen [B] — before the browser opens (one line):** `Sign in again to confirm that this is you.`

**Dialog [D] — title:** `Delete your account permanently?`

**Dialog [D] — body:** `All your data will be deleted now.`

**Dialog [D] — buttons:** `Delete` (destructive style) and `Cancel`

**Progress text:** `Deleting your account…`

**Success text (login screen):** `Your account was deleted.`

**Error texts:**

| Case | Text |
|---|---|
| Re-login needed (403) | `Sign in again to continue.` |
| Other account | `This is a different account. Nothing was deleted.` |
| Unknown result (§8) | `We could not confirm the deletion. Check your connection and try again.` |
| Sign-in failed at [B] (added by the Android build) | `We could not sign you in. Nothing was deleted.` |

### Wording rules for the clients (from the Android build, 2026-10-25)

- Say **"downloaded dictionaries"**, not "vault". People do not know the word "vault". The meaning is the same.
- The line for screen [B] ("Sign in again to confirm that this is you.") is optional. The warning already says
  it, and the browser opens straight after Continue, so the Android app shows no extra screen.
- Keep the proper noun **Forum** as it is in every language.
- The keys are in `core/src/main/res/values*/strings.xml` (`deleteAccount*`, `profileDeleteAccount*`) in the
  Android repo. The 19 translations are machine drafts. Native-speaker review is `P5R-19`.

### Web: the sign-in is a page navigation (KMP)

On web the browser leg replaces the page, so the flow cannot wait for an answer. Three rules follow:

- The pending authorization stores the **expected subject** beside the PKCE verifier (`sessionStorage`).
- The redirect returns to the same fragment (`#profile`), where the screen reads the answer when it is created
  and goes straight to the final dialog [D] (or shows the "different account" notice).
- The warning's checkbox is not restored after the reload. The person already confirmed it, so [D] opens directly.

### Open points for the owner

1. Is the list of deleted items correct and complete for your legal view? (Phase 5L, `P5L-04` and `P5L-05`
   cover the privacy policy and the records.)
2. Do you want to add a line about logs and backups? Today there are no backups.
3. Do you want a typed word instead of the checkbox? This guide says no (§3, rule 1).

---

## 7. What the client wipes at [F]

Use the existing `PostLogoutHook`s on Android and the same hooks on KMP. Wipe:

- Access and refresh tokens (encrypted storage). On web, also the browser storage keys of the session.
- The local database: dictionaries, words, tags, vault, statistics, ratings, sync state.
- The cached profile, the `X-Device-Id` registration state, the FCM token cache.
- Settings that belong to the person (for example, the Forum agreement flag). The language setting may stay.

Do **not** call the logout endpoint of Keycloak with the old refresh token and wait for it. The session
is already gone. A failed call must not block the wipe. A fire-and-forget call is allowed.

Then show the login screen with the success text.

---

## 8. No network, or an unknown result

- **Before [E]:** the call is not sent. Stay on the profile page. Show the error text. The person is still
  signed in. Nothing changed.
- **During [E]** (timeout, 5xx, lost connection): the result is unknown. The delete may have happened.
  - Do **not** wipe local data. Do **not** show the login screen.
  - Show the unknown-result text with a "Try again" button.
  - A repeat call is safe. It answers 200 and does nothing if the profile is already gone.
  - If a refresh is then refused with `invalid_grant`, Keycloak has removed the session. The deletion
    most likely worked. Run [F] and show the success text. A refresh that fails because of the network is
    **not** this case.
- There is **no offline queue**. A deletion needs the network and a fresh login. The person must be online.

---

## 9. What each client builds

| Client | Roadmap task | Where |
|---|---|---|
| Android | `P5A-02` (done 2026-10-25) | `DeleteAccountSection` on the profile screen, `AccountDeletionService`, strings in all 19 `values-XX` locales. |
| KMP iOS and web | `P5A-03` (code done 2026-10-27) | `feature:options` (`DeleteAccountSection`, `DeleteAccountViewModel`), `AuthService.reauthenticate()` and `completeAccountDeletion()` in `core:auth`, `AccountDeletionService` in `core:profile`. The 19 languages come from `core:localization`. |

Tests each client needs:

- The authorization request carries `max_age=0` on this flow and **not** on the normal login.
- A 403 `ReauthenticationRequiredException` goes back to [B] and is not retried.
- A different `sub` at [C] does not call `DELETE`.
- A 200 runs the wipe and shows the login screen.
- A timeout does not wipe local data.

The cross-service check (what the backend really removes) is `P5A-04`.

---

## 10. Known gaps (backend)

1. **Orphan vault entries.** Other users keep a vault entry for a deleted dictionary. The client shows "No
   longer available" and the person removes it. The roadmap text of `P5A-04` assumed that the entry goes. It
   does not. The check in `P5A-04` must test the real behavior, or the backend must change. The owner decides.
2. **A failed Keycloak delete is silent.** A profile can be gone while the identity stays. The person can
   still sign in, and the app then creates a new profile for the same identity. A repeat `DELETE` does not
   fix this, because it ends at "unknown id". `[open: decide a repair path, for example a reconciliation
   check, before release]`
