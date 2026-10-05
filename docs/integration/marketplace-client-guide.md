# Marketplace (Forum) — Client Implementation Guide (Android, iOS, Web)

**Audience:** the Claude sessions that implement the Verborum clients — native Android
(`verborum_android`) and the Compose Multiplatform project for web and iOS (`verborum_kmp`). Written
to be followed literally. Where this file and the code in the backend repo disagree, the code is right
and this file is the bug — say so rather than working around it.

**Naming:** the backend calls it the **marketplace**; the apps call it the **Forum**. Same thing.

**Backend state this describes:** roadmap Phase 4 complete and verified against a running stack —
P4-01 … P4-10 on 2026-09-27; the language-pair filter and slice paging (P4-11), the tag filter
(P4-12), publisher display names (P4-13) and the profile endpoints with the marketplace agreement
(P4-14) on 2026-10-04, the Forum gate with clean conflict answers (P4-15) and "joining shares
everything" (P4-16) the same day. Nothing here is planned-only unless it says so.

**Sharing, hiding and deleting dictionaries** — joining/leaving, the per-dictionary toggle, the
"keep one shared" rule and the sync caveat — are explained end to end in
`docs/integration/dictionary-sharing-client-guide.md`.

**Read first, and keep open:**
- `docs/integration/frontend-backend-integration.md` — the normative contract (envelope, error shape,
  ids, word meta schema §4.2, auth §6). This guide adds the profile and the marketplace on top of it.
- `docs/integration/client-login-guide.md` — how to obtain and refresh the token every call below needs.

**Build order this guide is written for:**
1. **Profile page** — the user enters a display name and accepts the marketplace terms (§4, §6.1–§6.2).
2. **Sharing** — the user publishes their own dictionaries to the Forum (§6.4).
3. **Browsing and importing** — the Forum itself (§5, §6.5–§6.8).

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
> 5. **New profile endpoints — since 2026-10-04.** After every login call **`GET /users/me`**: it
>    returns `{id, email, displayName, marketplaceAgreementAccepted, marketplaceAgreementVersion}` for
>    the logged-in user, no stored id needed. **404 = no profile yet** → create it with `POST /users/`.
>    Edit the name and the marketplace agreement with **`PUT /users/me/profile-info`** (partial update).
>    `displayName` is now limited to 255 characters (400 otherwise). Creating a second profile for the
>    same account, or using an email another profile has, is now **409** (it was a 500).
> 6. **`PUT /users/` no longer clears `displayName` when you leave it out** — an omitted name is kept.
>    Send `""` to remove it. It never changes the marketplace agreement.
> 7. **The marketplace / Forum is live (`ms_marketplace`, `:8087`).** Browse public dictionaries,
>    filter by language pairs, tags or publisher name, and import them into your vault. Browse pages
>    are infinite scroll: they carry `hasNext`, not totals. **The Forum is for members only:** browsing
>    and importing need a display name and accepted marketplace terms, otherwise **403**; and a user's
>    public dictionaries appear only while that user is a member (§4).
> 8. **Joining the Forum shares all of the user's dictionaries; leaving makes them all private** —
>    the server changes `isPublic` itself. **Re-fetch the user's dictionaries after a join or leave
>    before your next upload**, or your sync will push the old `isPublic` back. A member who has
>    dictionaries must keep at least one shared: making the last shared one private (or deleting it
>    while private ones remain) is **400** `SharingRequiredException`.
> 9. **Word save/update messages changed.** `POST`/`PUT /words` now reply "Saved/Updated successfully
>    into dictionary `<dictionaryId>`" instead of listing the words. Do not parse `message` — it is for
>    humans and logs.
> 10. **Web app only:** the backend sends **no CORS headers yet** (they arrive with the API gateway,
>    backend Phase 5). A browser app on its own origin cannot call the services directly — use a
>    same-origin dev proxy for now (§8.3 of the marketplace guide).
>
> Full details: `docs/integration/marketplace-client-guide.md` in the backend repo.

---

## 1. What the Forum is

- **Membership is opt-in.** A user joins by having a **display name** and **accepting the marketplace
  terms** on their profile (§4). Only members can browse and import (**403** otherwise), and only
  members' dictionaries are listed.
- **Joining shares everything.** At the moment a user joins, **all** of their dictionaries are made
  public by the server and appear in the Forum within about a second, under their display name. They
  can hide individual ones afterwards (`isPublic: false` on `PUT /dictionaries/`), but a member who has
  dictionaries must keep **at least one** shared. Every re-join shares all of them again.
- Other users **browse** listings (newest, most popular, filtered by language pairs, tags or publisher
  name) and **import** one. Importing adds a **reference** to the dictionary to their **vault**
  (ms_user). It does **not** copy the dictionary.
- The importer then **reads** the dictionary and its words straight from ms_dictionary, like their
  own — but **read-only**, and always the owner's current version. If the owner renames it or adds
  words, the importer sees that next time they fetch.
- If the owner makes it private again or deletes it, it **disappears** for importers too: the listing
  vanishes and reads return 404. The vault entry stays behind (§6.7).
- **Leaving makes everything private.** When a member leaves, the server makes **all** of their
  dictionaries private: they leave the Forum, can no longer be imported, and users who imported them
  **lose access**. Nothing is deleted; re-joining shares all of them again. Dictionaries are only
  deleted with the account.

There is no rating, no view count, and no "make my own editable copy" (§10).

---

## 2. Where to call — base URLs

| Service | Local base URL | Paths this guide uses |
|---|---|---|
| ms_user | `http://localhost:8086` | `/users/**` |
| ms_marketplace | `http://localhost:8087` | `/marketplace/dictionaries/**` |
| ms_dictionary | `http://localhost:8085` | `/dictionaries/**`, `/words/**` |
| Keycloak | `http://localhost:8180/realms/verborum` | token issuer (login guide) |

- **Keep one configurable base URL per service.** When the API gateway lands (backend Phase 5) all
  three collapse into one origin with **the same paths** (`/marketplace/**` → ms_marketplace, and so
  on), so a per-service base URL becomes one value — no path changes.
- **Physical device testing:** replace `localhost` with the machine's LAN IP *and* read
  `client-login-guide.md` §7 first — the token issuer must match or every call is a 401.
- Local stack is plain `http`. Production will be `https` behind the gateway; never hard-code `http`.

---

## 3. Authentication and the security rules clients must follow

Every endpoint in this guide requires `Authorization: Bearer <access token>` — browsing included.
There is no anonymous Forum. Token acquisition, refresh and storage are exactly as in
`client-login-guide.md` §2–§6.

**Rules — each one prevents a concrete failure:**

1. **Send the token on every call; refresh once on 401, then send the user to login.** Access tokens
   live 5 minutes.
2. **Never send anyone's id as "who I am".** `/users/me`, the profile update and the import endpoint
   take no user id at all — the server uses the token's `sub`. Do not add one.
3. **Know your two ids** (login guide §4): the token's **`sub`** — what every service stores as the
   owner, and what listings call `publisherId` — and ms_user's **`userId`**, returned as `id` by
   `GET /users/me` and used only in `/users/{userId}/…` paths. Keep both in memory after login.
4. **Never import your own dictionary.** The server answers **400** (`SelfImportException`). Compare
   `listing.publisherId` with your `sub` and hide or disable the import action on your own listings.
5. **Guard the import button against double taps.** Disable it while the request is in flight. Two
   simultaneous imports by the same user can make the second one fail with a **500** (a unique
   constraint race) — nothing is corrupted, but the user sees an error. Retrying succeeds.
6. **Treat every string from a listing or someone else's dictionary as untrusted user input.**
   `publisherName`, `name`, words, translations and tags are written by other people. Render them as
   plain text, never as HTML or markdown — this matters most in the **web** app (XSS). Never use them
   to build URLs, file paths or queries without encoding.
7. **Validate before sending.** Language codes against the supported list (§5.4), ids as canonical
   UUIDs, `size` within 1–100, `displayName` ≤ 255. The server enforces all of it with a 400, but a
   400 from your own client is a bug.
8. **Do not log tokens**, and do not log full request/response bodies that contain other users'
   content in production builds.
9. **Token storage per platform** (from the login guide): Android — EncryptedSharedPreferences /
   DataStore backed by Keystore; iOS — Keychain; web — **never `localStorage`**: memory only, or a
   BFF holding tokens server-side (§8.3).
10. **There is no server-side rate limiting yet** (it lands at the gateway, backend P5-04). Be a good
    citizen: cancel superseded requests (§6.5), run the name search on tap rather than per keystroke,
    do not poll listings in a loop, page on scroll rather than prefetching everything.

---

## 4. Profile and marketplace membership (ms_user) — build this first

### 4.1 The four states a user can be in

| State | `GET /users/me` | What the app shows |
|---|---|---|
| **No profile** | **404** | create the profile (§4.3) — happens once per account, after first login |
| **Profile, not a member** | `marketplaceAgreementAccepted: false` | own dictionaries work as always; the Forum shows a "Join" screen (name + terms). Forum calls answer **403** |
| **Member** | `true`, `displayName` set | full Forum; their public dictionaries are listed under their name; at least one stays shared |
| **Withdrawn** | `false`, version still set | like "not a member"; all their dictionaries are private, nothing deleted; can re-join |

Only a **member** can use the Forum and has listings in it. The backend guarantees a member always has
a display name.

### 4.2 `GET /users/me` — after every login

```
GET /users/me                       (ms_user :8086)
Authorization: Bearer <token>
```

```json
200
{
  "id": "4140c0de-0000-4000-8000-0000000000bb",
  "email": "anna@example.com",
  "displayName": "Anna Bauer",
  "marketplaceAgreementAccepted": true,
  "marketplaceAgreementVersion": "2026-10-01"
}
```

| Field | Meaning |
|---|---|
| `id` | ms_user's `userId` — the id for `/users/{userId}/vault` and `DELETE /users/{userId}`. Not the `sub` |
| `email` | the profile's email |
| `displayName` | the public name shown on listings; `null` when not set |
| `marketplaceAgreementAccepted` | `true` = member (see §4.1) |
| `marketplaceAgreementVersion` | which terms text was last accepted; kept after a withdrawal; `null` if never accepted |

**404** (`RecordNotFoundException`) = this account has no profile yet → §4.3.

### 4.3 `POST /users/` — create the profile (once, only after a 404)

```json
POST /users/
{
  "userId":      "<new random UUID, generated by the client>",
  "keycloakId":  "<the token's sub>",
  "email":       "<email from the ID token>",
  "displayName": "<optional — e.g. the name from the ID token>"
}
→ 201 { "status": 201, "message": "Saved successfully user <userId>", ... }
```

- **Only call it after `GET /users/me` returned 404.** An account has exactly one profile. A second
  `POST` with a new `userId` for the same account is **409** `ProfileConflictException` ("This account
  already has a profile; load it with GET /users/me…") — the usual cause is a reinstall that lost the
  `userId`; recover with `GET /users/me`. An email another profile already uses is also **409**
  ("This email is already used by another profile").
- `email` must be the token's own `email` claim (any case), or **400** `email must be the signed-in account's own
  verified email`. The token must carry a verified e-mail (`email_verified: true`), or **403** (since 2026-10-05, SEC-04).
- `keycloakId` must equal your token's `sub` (**403** otherwise). `userId` and `keycloakId` must be
  canonical UUIDs, `email` a valid address (**400** otherwise).
- A new profile is **not** a member: `marketplaceAgreementAccepted` starts `false`, whatever you send.
- Then call `GET /users/me` again and keep its `id`.

### 4.4 `PUT /users/me/profile-info` — the profile page

```json
PUT /users/me/profile-info
{
  "displayName": "Anna Bauer",
  "marketplaceAgreementAccepted": true,
  "marketplaceAgreementVersion": "2026-10-01"
}
→ 201 { "status": 201, "message": "Updated successfully user <userId>", ... }
```

**Every field is optional, and a field you leave out is not changed.** `"displayName": ""` removes
the name; `null`/absent leaves it alone. Read the result back with `GET /users/me`.

| Field | Rules |
|---|---|
| `displayName` | ≤ 255 characters; leading/trailing spaces are trimmed; blank counts as no name. Need not be unique, but must not contain a reserved word (see below) |
| `marketplaceAgreementAccepted` | `true` = join (or confirm a new terms version); `false` = withdraw |
| `marketplaceAgreementVersion` | the version of the terms text the user was shown, ≤ 50 chars (e.g. `"2026-10-01"`). **Required whenever you send `true`** for a user who is not a member yet |

**The rules — a break is 400 `InvalidProfileException`, with the rule in `errorDetail`:**

| You send | Result |
|---|---|
| `true` + version, and a name is set (now or already) | **joined.** The backend records the version and the acceptance time, and **makes all of the user's dictionaries public** |
| `true` without a version, user not a member yet | 400 `marketplaceAgreementVersion is required to accept the marketplace agreement` |
| `true` + version, but no name anywhere | 400 `displayName cannot be empty while the marketplace agreement is accepted; …` |
| `"displayName": ""` while a member | 400 — the same message. **A member cannot remove their name**; withdraw first |
| `{"marketplaceAgreementAccepted": false, "displayName": ""}` | allowed — withdraw and remove the name in one request |
| `false` | **withdrawn.** **All of the user's dictionaries become private** within seconds — out of the Forum, and importers lose access; the last accepted version and time are kept as the record; nothing is deleted |
| `true` + a **new** version while a member | the new version and a new acceptance time are recorded; stays a member |
| a new `displayName` while a member | renamed; the Forum shows the new name within seconds |
| a `displayName` containing a reserved word (`Verborum`, `admin`, `moderator`, `official`, `coldtea` anywhere; `support`, `staff`, `team`, `system`, `mod`, `root`, `security`, `help`, `helpdesk`, `bot` as a word; case, accents and look-alikes like `4dm1n` don't help) | 400 `displayName contains a reserved word …`. Show the message and keep the input; the same applies to `POST`/`PUT /users/` (since 2026-10-05) |
| `{}` | nothing changes (201) |

**404** = no profile (§4.3). **401** = token missing/expired.

The terms version is **yours to define** — the backend stores it as given. Use something that changes
whenever the terms text changes (a date works). Compare it with `marketplaceAgreementVersion` from
`/me` to decide whether to ask a member to accept updated terms; the backend does not do this for you.

### 4.5 `PUT /users/` — the full profile (avoid on the profile page)

The original full-profile update still works (`{userId, keycloakId, email, displayName}`, all but
`displayName` required), with two changes since 2026-10-04: an **omitted `displayName` is kept**, not
cleared, and it **never changes the agreement**. The member rule applies here too (`""` while a
member → 400). Prefer `profile-info` for anything on the profile page.

### 4.6 What the backend enforces, and what the client must

- **The Forum gate is enforced by the server.** Every browse endpoint and import answer **403**
  `ForbiddenOperationException` ("Join the marketplace first: …") to a caller who is not a member.
  Still route non-members to the Join screen yourself (§6.3) rather than letting them hit the 403.
- **Membership reaches the Forum by event, within about a second.** Right after a successful join,
  a Forum call can still see the old state and answer 403 for a moment. After joining, wait for the
  201 and retry a 403 once after ~1 s before showing an error.
- **Joining and leaving change `isPublic` on the server** (all dictionaries public / all private).
  The app's local copies still hold the old values: **re-fetch the user's dictionaries
  (`GET /dictionaries/{sub}`) after a successful join or leave, and before the next upload**, and take
  `isPublic` from the server. Otherwise the next sync pushes the stale value and silently un-shares a
  dictionary (or re-shares one after leaving).
- **A member who has dictionaries keeps at least one shared** (ms_dictionary, 400
  `SharingRequiredException`): making the last shared one private; deleting the last shared one while
  private ones remain; creating a private dictionary while none is shared. Deleting the user's last
  dictionary outright is allowed, and joining with no dictionaries is fine. Block these in the UI too.
- **A non-member can still set `isPublic: true`** on a dictionary; it is then readable by id but **not
  listed** in the Forum.

---

## 5. Forum endpoints (ms_marketplace)

### 5.1 Browse (all `GET`, all paginated)

| Endpoint | Returns | Order |
|---|---|---|
| `GET /marketplace/dictionaries?pair=EN-TR&tag=food&publisher=nna` | listings, optionally filtered | newest first |
| `GET /marketplace/dictionaries/popular?…same filters…` | listings, same filters | most imported first; newest first among equals |
| `GET /marketplace/dictionaries/publisher/{publisherId}` | one publisher's listings | newest first |

**Never your own.** `GET /marketplace/dictionaries` and `/popular` never return the caller's own
listings (since 2026-10-04) — the Forum shows other people's dictionaries. Only
`/publisher/{publisherId}` with your own `sub` returns yours.

**Members only, both ways.** The caller must be a member (**403** otherwise, §4.6), and only members'
listings are ever returned — on every endpoint, filtered or not, including `/publisher/{publisherId}`:
the dictionary is public *and* its publisher has a display name *and* has accepted the terms. Private
or deleted dictionaries never appear.

**Paging** on all three: `page` (zero-based, default `0`, ≥ 0) and `size` (default `20`, 1–100).
Out-of-range → **400**. The order is stable (ties are broken by `dictionaryId`), so paging does not
repeat or skip items *unless* listings change while the user scrolls — de-duplicate by `dictionaryId`
when appending pages.

All three filters are optional and combine by **AND**; values of one filter combine by **OR**.

**Language pairs (`pair`)**
- One pair is two codes joined by a hyphen: `EN-TR`. Any case (`en-tr` works).
- **Direction does not matter.** `pair=EN-TR` returns English→Turkish **and** Turkish→English
  dictionaries; `pair=TR-EN` returns exactly the same.
- Several pairs: repeat the parameter (`pair=EN-TR&pair=FR-DE`) or comma-separate them
  (`pair=EN-TR,FR-DE`) → EN→TR, TR→EN, FR→DE and DE→FR, nothing else.
- At most **10** pairs. An unsupported code, the same code twice (`EN-EN`) or a malformed value
  (`ENTR`, `EN-`) → **400**.
- Recommended default when the Forum opens: the pairs of the user's own dictionaries, e.g. a user with
  DE→TR and EN→TR dictionaries sends `pair=DE-TR&pair=EN-TR`. Let them edit it.
- `GET /marketplace/dictionaries/language?from=&to=` was **removed** on 2026-10-04 (now 404).

**Tags (`tag`)**
- `tag=food&tag=travel` returns listings with **any** of the tags. Any case (`Food` matches `food`).
- **Repeat** the parameter for several tags — do not comma-join them: the server splits on commas, so
  a tag that itself contains a comma cannot be searched for.
- At most **10**, each non-blank and ≤ 100 characters, otherwise **400**.

**Publisher name (`publisher`)**
- Part of the publisher's display name, any case: `publisher=nna` finds "Anna Bauer", and so do `ANN`
  and `a b`. Wildcards are literal — `%` matches only a `%`.
- 3 to 255 characters, otherwise **400**. Run it when the user taps Search, not on every keystroke.
- Display names are not unique; use `publisherId` to tell two "Anna"s apart.

**Publisher endpoint:** an unknown `publisherId`, or one whose user is not a member, returns an
**empty page**, not a 404.

### 5.2 The page envelope and the listing

```json
{
  "items": [
    {
      "dictionaryId":  "00000006-0000-4000-8000-000000000000",
      "publisherId":   "765a81ed-2612-4dcc-bf7a-3d5fecf3d0d6",
      "publisherName": "Anna Bauer",
      "name":          "Polish → Ukrainian",
      "fromLang":      "PL",
      "toLang":        "UK",
      "tags":          ["grammar", "travel"],
      "importCount":   7,
      "publishedAt":   "2026-07-19T17:01:21.303971Z"
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
| `hasNext` | whether another page exists — request `page + 1` only while it is `true`. There are no totals: the Forum is infinite scroll |
| `dictionaryId` | the dictionary's id in ms_dictionary — use it to import and to read it |
| `publisherName` | the publisher's display name — show this ("by Anna Bauer"). Not unique |
| `publisherId` | the publisher's `sub`. Pass it to the publisher endpoint for "more from this publisher"; compare it with your own `sub` to spot your own listings. **Never show it to users** |
| `name`, `fromLang`, `toLang` | copies of the dictionary's fields, kept current within seconds; codes uppercase, real direction |
| `tags` | the dictionary's tags, lowercase and sorted; `[]` when untagged. Kept current within seconds |
| `importCount` | number of **distinct users** who imported it (a user importing twice counts once) |
| `publishedAt` | when it (most recently) became public, ISO-8601 UTC. Private-then-public again resets it |

This `SliceResponse` shape is Verborum's own contract, not Spring's `Slice` — map exactly these four
envelope fields and ignore unknown ones (fields may be added, never removed or renamed).

### 5.3 Import

```
POST /marketplace/dictionaries/{dictionaryId}/import
Authorization: Bearer <token>
(no body)
```

| Response | When | Client action |
|---|---|---|
| **201** `{"status":201,"message":"Imported successfully dictionary <id>", …}` | recorded (first time or repeat) | show success; add to the local vault view optimistically (§6.6) |
| **400** `SelfImportException` | it is your own dictionary | should never happen — you hid the button (§3 rule 4) |
| **403** `ForbiddenOperationException` | you are not a member (§4.6) | send the user to the Join screen |
| **404** `RecordNotFoundException` | unknown id, **or** made private / deleted, **or** its publisher left the Forum since you loaded the list | remove it from the visible list; tell the user it is no longer available |
| **401** | token expired | refresh once and retry |
| **500** | double-tap race (§3 rule 5) | retry once; it succeeds |

**Idempotent:** importing the same dictionary again returns 201, does not raise `importCount`, and
re-sends the vault update. So "import" is also the repair action if a vault entry is missing.

**Asynchronous:** 201 means the import is recorded. The **vault entry is created a moment later**
by ms_user (via an internal event). Do not immediately re-fetch the vault and conclude it failed.

**Precondition:** the importer must be a member (§4) — which also means they have the ms_user
profile the vault update is written to.

### 5.4 Supported language codes

`EN, DE, FR, ES, IT, PT, NL, TR, AZ, LT, PL, UK, AR, FA, JA, ZH, KO, EL, RU` — one list for every
service and every client (`frontend-backend-integration.md` §4.1). Uppercase with a
locale-independent conversion (`Locale.ROOT` / invariant culture) — Turkish locale uppercasing turns
`i` into `İ`.

### 5.5 Related endpoints you need around the Forum

| Service | Endpoint | Use |
|---|---|---|
| ms_dictionary | `PUT /dictionaries/` with `isPublic` | share / unshare your own dictionary (§6.4) |
| ms_dictionary | `POST /dictionaries/{id}/tags` `{"tag": "..."}`, `DELETE /dictionaries/{id}/tags/{tag}` | tag your dictionary; tags are what the Forum's tag filter searches |
| ms_dictionary | `GET /dictionaries/dictionary/{id}` | a dictionary's details (404 if it became private / was deleted) |
| ms_dictionary | `GET /dictionaries/batch?ids=a,b,c` | several at once — unreadable ids are **silently dropped** |
| ms_dictionary | `GET /words/dictionary/{id}` | its words; **`level` is `null`** on another user's words; `[]` if it became private |
| ms_dictionary | `GET /dictionaries/{id}/tags` | its tags (404 if not readable) |
| ms_user | `GET /users/{userId}/vault` | the caller's vault: `[{vaultEntryId, userId, dictionaryId, importedAt}]` — `{userId}` is `id` from `/me`, **not** the `sub` |
| ms_user | `DELETE /users/{userId}/vault/{dictionaryId}` | remove an imported dictionary from the vault (200, also when absent) |

---

## 6. Flows to implement

### 6.1 After every login (bootstrap)
1. `GET /users/me`.
2. **404** → `POST /users/` (§4.3) → `GET /users/me` again.
3. Keep `id` (the `userId`), the token's `sub`, `displayName`, `marketplaceAgreementAccepted` and
   `marketplaceAgreementVersion` in your session state; the profile page and the Forum gate read them.
4. If the user is a member but `marketplaceAgreementVersion` differs from your current terms version,
   you may ask them to accept the new terms (§6.2) — your product decision.

### 6.2 Profile page
- **Show:** email (read-only), display name, and the membership state from §4.1.
- **Join the Forum** (not a member): one screen with a display-name field (pre-filled from `/me`, or
  from the ID token's name), the terms text with an "I agree" control, and a clear statement that
  **all of their dictionaries will be shared** (they can hide some later). On submit send
  `{"displayName": "<name>", "marketplaceAgreementAccepted": true, "marketplaceAgreementVersion": "<your terms version>"}`
  in **one** request, then `GET /users/me` and **re-fetch the user's dictionaries** (§4.6). Disable
  submit while the name is blank.
- **Rename** (member or not): `{"displayName": "<new name>"}`. Block an empty name in the UI while a
  member, and explain why ("leave the Forum first") — the server would answer 400 anyway.
- **Leave the Forum** (member): confirm first — "all your dictionaries will become private; people who
  imported them will lose access; nothing is deleted" — then `{"marketplaceAgreementAccepted": false}`,
  then **re-fetch the user's dictionaries** (§4.6). If the user also wants
  their name gone, send `{"marketplaceAgreementAccepted": false, "displayName": ""}`.
- **Accept updated terms** (member, old version): `{"marketplaceAgreementAccepted": true, "marketplaceAgreementVersion": "<new>"}`.
- On **400 `InvalidProfileException`**, show `errorDetail` as a validation message next to the
  relevant field — this one is written for people. Branch your logic on the status code, not the text.

### 6.3 Forum entry gate
- Opening the Forum tab with **no profile** → bootstrap (§6.1). **Not a member** → the Join screen
  (§6.2), with a "Not now" that returns to the app. **Member** → the Forum.
- The server enforces the same gate (403, §4.6). If a Forum call answers 403 anyway — the user left on
  another device, or the join is a second old — refresh `GET /users/me` and route by its answer;
  right after joining, retry once after ~1 s first.

### 6.4 Sharing your own dictionaries (members)
- **On joining, everything is already shared** (§4.4). The per-dictionary toggle is for what comes
  after: `PUT /dictionaries/` with `isPublic` (same body as any update, `userId` = your `sub`).
- **Hiding:** allowed while at least one other dictionary stays shared. Disable the toggle on the last
  shared one and explain why ("leave the Forum to make everything private"); the server answers 400
  `SharingRequiredException` otherwise. The same rule blocks deleting the last shared dictionary while
  private ones remain (deleting the last dictionary of all is fine).
- **New dictionaries of a member:** you choose `isPublic`. If the member has no shared dictionary yet
  (e.g. they joined with none), the new one must be public — 400 otherwise.
- **Tags** make it findable: `POST /dictionaries/{id}/tags`. Changes reach the listing within seconds.
- **Rename / change languages while shared:** the listing follows within seconds.
- **Unshare:** `isPublic: false` → it leaves the Forum and **everyone who imported it loses access**.
  Warn the user first if `importCount > 0` (from the listing).
- For a non-member, offer "Join the Forum" (§6.2) instead of per-dictionary sharing — a public
  dictionary of a non-member is not listed (§4.6).

### 6.5 Browse screen
1. `GET /marketplace/dictionaries?pair=…&page=0&size=20` (or `/popular`) → render `items`, each with
   `publisherName`, languages, tags as chips and `importCount`.
2. On scroll near the end and `hasNext` → request `page + 1`, append, de-duplicate by `dictionaryId`.
3. Filters → `?pair=..&tag=..&publisher=..` on the same endpoint. Pre-fill `pair` from the user's own
   dictionaries. Re-query on every change of the chips, reset to page 0, and **cancel the request
   still in flight** (`collectLatest`/`flatMapLatest`, `switchMap`) so a slower, older response can
   never overwrite a newer one. The publisher-name search runs on tap. A tap on a tag chip may add
   that tag to the filter.
4. Tap a publisher → `/publisher/{publisherId}` ("More from Anna Bauer").
5. Your own dictionaries never appear in these lists — no "Yours" handling needed. (They do appear on
   `/publisher/{yourSub}`; there, hide the import action.)
6. Optional preview before importing: `GET /dictionaries/dictionary/{id}` and
   `GET /words/dictionary/{id}` work for any listed dictionary.

### 6.6 Import
1. Disable the button; `POST …/{dictionaryId}/import`.
2. On 201: mark the listing as imported locally and add a pending vault entry `{dictionaryId}` to the
   local vault view. Re-enable the button as "Imported" (a repeat tap is harmless but pointless).
3. Reconcile later: the next `GET /users/{userId}/vault` will contain it. If it still does not after a
   reasonable time (e.g. next app start), call import again — it is idempotent and repairs a lost
   vault update.

### 6.7 Opening an imported dictionary (vault)
1. `GET /users/{userId}/vault` → `dictionaryId`s.
2. `GET /dictionaries/batch?ids=…` → dictionaries still readable. **Any id missing from the result
   became private or was deleted.**
3. For each unavailable one: show it as "No longer available" with a **Remove** action →
   `DELETE /users/{userId}/vault/{dictionaryId}`. The backend does **not** remove these vault entries
   by itself.
4. `GET /words/dictionary/{id}` for the words. Render read-only: no add/edit/delete of words, tags or
   the dictionary (all **403**).

### 6.8 Learning progress on imported words
`level` from the server is `null` for words you do not own — the owner's mastery is not yours. Keep
the user's own progress for imported words **locally only**, keyed by `wordId` (and your `sub`).
There is no server storage for progress on someone else's words, and you cannot write `level` into
their dictionary (403).

### 6.9 Offline behaviour
- The Forum and the profile update are **online-only**; show a clear offline state instead of stale
  listings, and do not queue profile changes for later.
- Imported dictionaries may be **cached** for offline reading, but they are read-only references:
  refresh them when online and drop the cache when the dictionary becomes unavailable (§6.7).
  **Never upload an imported dictionary or its words through your normal sync** — they are not
  yours and every write is a 403. Keep them out of the local "unsynced/dirty" set entirely.

---

## 7. Error handling reference

Error body (all services):
```json
{ "status": 400, "error": "InvalidProfileException",
  "errorDetail": "marketplaceAgreementVersion is required to accept the marketplace agreement",
  "path": "/users/me/profile-info", "timestamp": "…" }
```

| Status | `error` values you may see here | Meaning |
|---|---|---|
| 400 | `InvalidProfileException` | a profile rule (§4.4) — `errorDetail` is user-readable |
| 400 | `HandlerMethodValidationException`, `MethodArgumentNotValidException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` | bad parameter or body field — `errorDetail` names it. A client bug: log it |
| 400 | `SelfImportException` | importing your own dictionary |
| 400 | `SharingRequiredException` | a member would be left with dictionaries but none shared (§4.6) — `errorDetail` is user-readable |
| 401 | — | token missing, expired or wrong issuer (login guide §7) |
| 403 | `ForbiddenOperationException` | not a Forum member (§4.6) — or a write on something that is not yours (e.g. `keycloakId` ≠ your `sub`) |
| 404 | `RecordNotFoundException`, `NoResourceFoundException` | no profile yet (`/users/me`), unknown/unavailable dictionary or listing, or a wrong URL |
| 409 | `ProfileConflictException`, `DataIntegrityViolationException` | a second profile for the same account, or an email another profile uses (§4.3). Load the existing profile with `GET /users/me` |
| 500 | `Exception` (`errorDetail` is always `"Internal server error"`) | server fault — retry once, then show a generic error |

Branch on the **status code**, not on `message`/`errorDetail` text — texts are for people and logs
and may change.

---

## 8. Platform notes

### 8.1 Android (native Kotlin, `verborum_android`)
- Reuse the existing authenticated HTTP stack (OkHttp `Authenticator` for refresh-once-on-401). Add
  ms_marketplace as another base URL beside ms_dictionary / ms_user.
- Hold the `/me` result in a session-scoped repository that the profile screen and the Forum gate
  observe; refresh it after every profile update.
- Paging: map `SliceResponse` to Paging 3 (`PagingSource` keyed by page number; `nextKey = page + 1`
  while `hasNext`, else `null`).
- Local persistence for vault/imported content goes in its **own** tables, separate from owned
  dictionaries, so the sync engine can never pick them up for upload.
- The Android client is the reference implementation for the login edge cases (email verification,
  cancelled login) — follow it; nothing here changes them.

### 8.2 iOS (Compose Multiplatform, `verborum_kmp`)
- Everything in §4–§7 belongs in `commonMain`: DTOs, the Ktor client, the profile repository, paging,
  the import use case, vault reconciliation. Only token storage (Keychain) and the login launcher
  (`ASWebAuthenticationSession`) are iOS-specific.
- `publishedAt` is ISO-8601 with `Z`; parse with `kotlinx-datetime` `Instant.parse`.

### 8.3 Web (Compose Multiplatform Wasm, `verborum_kmp`)
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

## 9. Verifying locally

Backend stack: `docker compose up -d` in the backend repo, services running (see
`docs/ops/local-development.md`). Dev users `testuser` / `testuser` and `testadmin` / `testadmin`.

```bash
# a token without a login UI (local-dev-only client — never use it in the app)
TOKEN=$(curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
H="Authorization: Bearer $TOKEN"

# profile: 404 the first time -> create it with POST /users/ (sub and email from the token)
curl -s -H "$H" http://localhost:8086/users/me
# join the Forum
curl -s -H "$H" -H 'content-type: application/json' -X PUT http://localhost:8086/users/me/profile-info \
  -d '{"displayName":"Anna Bauer","marketplaceAgreementAccepted":true,"marketplaceAgreementVersion":"2026-10-01"}'

# browse
curl -s -H "$H" "http://localhost:8087/marketplace/dictionaries?size=5"
curl -s -H "$H" "http://localhost:8087/marketplace/dictionaries?pair=de-en&pair=EN-TR&tag=travel&publisher=ann"
curl -s -X POST -H "$H" "http://localhost:8087/marketplace/dictionaries/<dictionaryId>/import"
```

Full round trip: as `testadmin`, create a dictionary, create a profile and join the Forum — the
dictionary is shared by the join; as `testuser`, create a profile, join, browse and import it.
Browsing before joining is a 403, and a public dictionary of a user who has not joined does **not**
show up — both are the rules, not bugs.

---

## 10. Not available yet — do not build UI that depends on these

| Missing | Status |
|---|---|
| Ratings, view counts | not designed |
| Search by dictionary name or word text | not planned (filters are language pair, tag and publisher name) |
| "Make my own editable copy" of an imported dictionary | not built — imports are references |
| Automatic vault cleanup when a dictionary becomes unavailable | not built — clients handle it (§6.7) |
| CORS / single gateway origin | backend Phase 5 |
| Server-side rate limiting, HTTPS | backend `P5-04` |
