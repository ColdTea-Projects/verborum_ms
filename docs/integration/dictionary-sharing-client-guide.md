# Sharing, Hiding and Deleting Dictionaries — Client Guide

**Audience:** the client implementations (Android, iOS, web). This file explains one thing end to end:
how a user's dictionaries become visible to other people in the **Forum** (the backend calls it the
*marketplace*), how they are hidden again, and what deleting does. It is the detail behind
`marketplace-client-guide.md` §4.4, §4.6 and §6.4 — read that guide for the profile page, browsing and
importing.

**Backend state:** roadmap P4-16, 2026-10-04.

---

## 1. The two switches

Two independent settings decide whether a dictionary is visible to others:

| Switch | Where | Who changes it |
|---|---|---|
| **Forum membership** — "I accept the marketplace terms" | the user's profile (ms_user): `marketplaceAgreementAccepted` | the user, on the profile page (`PUT /users/me/profile-info`) |
| **`isPublic`** — "this dictionary is shared" | each dictionary (ms_dictionary) | the user, per dictionary (`PUT /dictionaries/`) — **and the server, when the user joins or leaves** |

A dictionary appears in the Forum only when **both** are on: its owner is a member **and** it is public.

| Owner | `isPublic` | In the Forum? | Others can open it by id? | Importers keep access? |
|---|---|---|---|---|
| member | `true` | **yes** | yes | yes |
| member | `false` | no | no (404) | **no** |
| not a member | `false` | no | no (404) | no |
| not a member | `true` | no | yes, by id | — (cannot be imported) |

The last row only happens if a client sets `isPublic: true` for a non-member. Avoid it: for a
non-member, offer "Join the Forum" instead of a share toggle.

---

## 2. Joining the Forum shares everything

When the user joins (accepts the terms on the profile page):

```
PUT /users/me/profile-info                         (ms_user :8086)
{ "displayName": "Anna Bauer", "marketplaceAgreementAccepted": true, "marketplaceAgreementVersion": "2026-10-01" }
→ 201
```

then **within about a second, the server makes every one of the user's dictionaries public.** Each one
appears in the Forum under the user's display name. No call per dictionary is needed — and none
should be made.

- **This happens on every join**, including a re-join after leaving. Dictionaries the user had hidden
  before are shared again; they can hide them again afterwards (§4).
- **A user with no dictionaries can join.** Nothing is shared until they create one (§5).
- **Say it on the join screen:** "All your dictionaries will be shared in the Forum. You can hide
  individual ones later."

---

## 3. Leaving the Forum makes everything private

```
PUT /users/me/profile-info
{ "marketplaceAgreementAccepted": false }
→ 201
```

then **within about a second, the server makes every one of the user's dictionaries private:**

- they leave the Forum and can no longer be imported;
- **everyone who imported them loses access** — in those users' vaults they show as "No longer
  available" (`marketplace-client-guide.md` §6.7);
- **nothing is deleted** — the dictionaries, words and tags stay, private, and a re-join shares them
  all again.

**Confirm before leaving:** "All your dictionaries will become private. People who imported them will
lose access. Nothing is deleted."

---

## 4. Hiding and showing one dictionary (members)

After joining, the user can hide or show individual dictionaries with the normal update — the same
body as any dictionary update, with `isPublic` set:

```
PUT /dictionaries/                                 (ms_dictionary :8085)
{
  "dictionaryId": "4160c0de-0000-4000-8000-000000000001",
  "userId":       "<the token's sub>",
  "name":         "Kitchen words",
  "isPublic":     false,
  "fromLang":     "DE",
  "toLang":       "TR"
}
→ 201 { "status": 201, "message": "Updated successfully into dictionary <id>", ... }
```

- **Show** (`true`): it appears in the Forum within about a second.
- **Hide** (`false`): it leaves the Forum and **everyone who imported it loses access**. If the listing
  has `importCount > 0`, warn the user first.
- Every field is required on this `PUT` (`isPublic` included) — send the whole dictionary as you have
  it, with only `isPublic` changed.

### The one rule: a member keeps at least one dictionary shared

A member who has dictionaries must have **at least one** of them public. The server checks the
*result* of every change and refuses with **400** `SharingRequiredException`:

```json
{ "status": 400, "error": "SharingRequiredException",
  "errorDetail": "A marketplace member must keep at least one dictionary shared; leave the marketplace to make all of them private",
  "path": "/dictionaries/", "timestamp": "…" }
```

| A member… | Result |
|---|---|
| hides a dictionary while at least one other stays shared | ✅ allowed |
| hides their **last shared** dictionary | ❌ 400 |
| shows a hidden dictionary | ✅ allowed |
| edits a dictionary that stays hidden (rename, languages) | ✅ allowed — nothing shared is taken away |
| wants everything private | ✅ leave the Forum (§3) — that is the only way |

**In the UI:** disable the toggle on the last shared dictionary and explain why: "At least one of your
dictionaries stays shared while you are in the Forum. Leave the Forum to make all of them private."
The server's 400 is the safety net, not the primary check.

---

## 5. Creating a dictionary (members)

The client always sends `isPublic` on create (`POST /dictionaries/`, same body as above). The server
does not choose it for you, with one exception from the rule in §4:

| A member creates… | Result |
|---|---|
| a **public** dictionary | ✅ appears in the Forum within about a second |
| a **private** dictionary while they already have a shared one | ✅ allowed |
| a **private** dictionary while **none** of theirs is shared (e.g. they joined with no dictionaries) | ❌ 400 `SharingRequiredException` — their first dictionary as a member must be public |

**In the UI:** for a member, default the "Share in Forum" toggle to **on** for a new dictionary, and
lock it on when they have no shared dictionary yet.

Non-members create dictionaries as always; send `isPublic: false`.

---

## 6. Deleting a dictionary

```
DELETE /dictionaries/{dictionaryId}                (ms_dictionary :8085)
→ 200 { "status": 200, "message": "Deleted successfully <id>", ... }
```

Deleting removes the dictionary **with its words and tags**, for good. If it was public it leaves the
Forum and **everyone who imported it loses access**.

| A member deletes… | Result |
|---|---|
| a private dictionary | ✅ allowed |
| a shared dictionary while another stays shared | ✅ allowed |
| their **last dictionary of all** | ✅ allowed — they then have none, which is fine |
| their **last shared** dictionary while **private ones remain** | ❌ 400 `SharingRequiredException` — the rest would all be hidden. Share another one first, or leave the Forum |

Other answers: **403** when the dictionary is not yours, **200** (no-op) for an id that does not exist.

Non-members can delete anything of theirs.

**Confirm before deleting** a dictionary with `importCount > 0`: "N people imported this dictionary
and will lose it."

---

## 7. Keeping the app's local copy in sync — important

Joining and leaving change `isPublic` **on the server**, for all of the user's dictionaries. The app's
local copies still hold the old values. If the sync engine then uploads a dictionary with its stale
`isPublic`, it **silently undoes the join or leave** for that dictionary (un-shares it after a join, or
re-shares it after a leave).

**Rule:** after a successful join or leave —

1. wait for the `201` from `profile-info`;
2. wait about a second (the server applies it asynchronously);
3. **re-fetch the user's dictionaries** — `GET /dictionaries/{sub}` — and overwrite the local
   `isPublic` with the server's value;
4. only then let the sync engine upload again.

Treat `isPublic` from the server as authoritative whenever the two disagree after a join or leave.
If an upload still hits 400 `SharingRequiredException`, the local copy is stale: re-fetch and retry.

---

## 8. Timing — what "within about a second" means

Joining, leaving, showing, hiding and deleting all reach the Forum through internal events, normally
within a second. In practice:

- Right after a join, a Forum call can still answer **403** for a moment (the Forum does not know yet
  that the user is a member) — retry once after ~1 s (`marketplace-client-guide.md` §4.6).
- Right after a join, `GET /dictionaries/{sub}` can still show the old `isPublic` for a moment — that
  is why §7 waits before re-fetching. If the values have not changed yet, fetch once more.
- A listing can lag its dictionary by a moment in both directions; the Forum is never more than a
  few seconds behind.

---

## 9. Quick reference

| User action | Request | What changes | Others |
|---|---|---|---|
| Join the Forum | `PUT /users/me/profile-info` `accepted: true` + version (+ name) | **all** dictionaries → public | all appear in the Forum |
| Leave the Forum | `PUT /users/me/profile-info` `accepted: false` | **all** dictionaries → private | all vanish; importers lose access |
| Share one (member) | `PUT /dictionaries/` `isPublic: true` | that dictionary → public | it appears |
| Hide one (member) | `PUT /dictionaries/` `isPublic: false` | that dictionary → private | it vanishes; its importers lose access. **400 if it is the last shared one** |
| Create (member) | `POST /dictionaries/` with `isPublic` | new dictionary | public → appears. **Private is 400 if none is shared** |
| Delete (member) | `DELETE /dictionaries/{id}` | dictionary, words, tags gone | it vanishes; its importers lose access. **400 if it is the last shared one and private ones remain** |

| Error | Meaning | What to show |
|---|---|---|
| 400 `SharingRequiredException` | the change would leave a member with dictionaries but none shared | `errorDetail` (written for people), or your own text from §4 |
| 403 `ForbiddenOperationException` | not your dictionary | generic error — a client bug |
| 401 | token expired | refresh once and retry |

---

## 10. Trying it locally

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
SUB=<the token's sub>
H="Authorization: Bearer $TOKEN"

# two private dictionaries
for n in 1 2; do curl -s -H "$H" -H 'content-type: application/json' -X POST http://localhost:8085/dictionaries/ \
  -d "{\"dictionaryId\":\"4160c0de-0000-4000-8000-00000000000$n\",\"userId\":\"$SUB\",\"name\":\"Test $n\",\"isPublic\":false,\"fromLang\":\"de\",\"toLang\":\"tr\"}"; done

# join (needs a profile first: GET /users/me, POST /users/ on 404) -> both become public
curl -s -H "$H" -H 'content-type: application/json' -X PUT http://localhost:8086/users/me/profile-info \
  -d '{"displayName":"Anna Bauer","marketplaceAgreementAccepted":true,"marketplaceAgreementVersion":"2026-10-01"}'
sleep 2; curl -s -H "$H" http://localhost:8085/dictionaries/$SUB     # isPublic: true on both

# hide one -> 201; hide the other (the last shared) -> 400 SharingRequiredException
```
