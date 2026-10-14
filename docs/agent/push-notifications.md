# Verborum — Push Notifications Guideline

The normative design for push notifications across the backend and all clients. Roadmap tasks are
`P4N-01`…`P4N-13` in `roadmap.md` (Phase 4N). Research, cost and options considered:
`docs/ops/push-notifications-guideline.pdf`. Written 2026-10-14; the status of each piece is in the
roadmap, not here.

## 1. Decisions (owner, 2026-10-14)

| Question | Decision |
|---|---|
| Provider | **FCM direct** (Firebase Cloud Messaging, HTTP v1 through the Firebase Admin SDK) |
| Where the code lives | **New service `ms_notification`** — consumes RabbitMQ events, owns device tokens and preferences, holds the Firebase credentials |
| iOS | **Firebase iOS SDK**; one sender (FCM) for all platforms |
| Message kind | **Silent data messages only.** No visible alert is planned; the goal is background freshness |
| First category | **Own-device `SYNC`** — a change on one device nudges the user's other devices |
| Later, silent | `REFRESH` to a publisher on import received / rating received |
| Not built | Push to every importer of a dictionary (hundreds of subscribers, read-only, full sync on open anyway) |
| Web | **No FCM.** Refetch on tab focus plus a slow timer while visible (`P4N-10`, decided 2026-10-14). SSE is backlog `BL-05` |
| Apple Developer account | **Not yet.** iOS is built and released last |

**Order of work:** backend + Android on a local stack → web on a local stack → Android + web release →
iOS build and release.

## 2. How it fits together

```
 Android / Web / iOS app ──(1) PUT /notifications/devices {token}──► ms_notification ──► vdbnotification
                                                                          ▲
 ms_marketplace ─ dictionary.imported / dictionary.rated ──► RabbitMQ ┤ (verborum.events)
 ms_dictionary  ─ dictionary.changed ─────────────────────────────────────┘
                                                                          │ (2) FirebaseMessaging.send
                                                                          ▼
                                                              FCM ──► Google (Android, web) / APNs (iOS)
```

- A push **wakes the app. It is not the data.** The payload carries ids; the app fetches the content
  from the API with its own token. Private data stays out of Google's and Apple's servers.
- Clients call `ms_notification` directly (no gateway yet), like every other service.
- Ownership always comes from the JWT subject. A client can never register a token for someone else.

## 3. Message types and payloads

Every message is **data-only** (no `notification` block), so nothing is shown and no permission prompt
is needed.

| `data.type` | Event | Recipient | Purpose | Status |
|---|---|---|---|---|
| `SYNC` | `dictionary.changed` (new) | the user's **other** devices | start a sync now | first |
| `REFRESH` | `dictionary.imported` (+`publisherId`), `dictionary.rated` (new) | the publisher | refresh Forum stats | second |

`data` keys: `type` (required), `dictionaryId` (every type), `eventId` (optional). **Ids only.**

- **A push is a hint, never a correctness mechanism.** The apps keep their full sync on opening the
  dictionary list. A force-quit app, Doze, Low Power Mode or Background App Refresh off can drop a push;
  iOS throttles silent pushes hardest. If a push is lost, the next sync repairs it.
- **Skip the originator.** `dictionary.changed` carries the optional `deviceId` of the writing device;
  that device gets nothing. Otherwise it receives and ignores its own nudge.
- **Coalesce bursts.** Collapse key `sync` per user, normal priority, plus a short server-side debounce,
  so ten quick edits leave one pending message. One bulk request is one event, not one per word.
- **Scale.** `SYNC` goes to one user's few devices. There is no fan-out, so no batching problem.
- Forum-related `REFRESH` goes only to a Forum member; never to the actor.

## 4. Backend (`ms_notification`)

Mirror `ms_marketplace`. `SecurityConfig` ships in the first commit. Skills: `spring-boot-app-architecture`
(+ `scaffold-a-service.md`), `persistence`, `messaging`, `security`, `web-api`.

**Tables** (Liquibase JSON, one changeset per change, UUID string ids, `timestamptz`):

```
device_tokens            device_id PK (client UUID) · fk_user_id (JWT subject) · platform ANDROID|IOS
                         token UNIQUE · locale · app_version · last_seen_at · creation_dt · update_dt
notification_preferences  DEFERRED — silent pushes have no user-visible category to switch off
```

**API** — owner from the token; no `userId` in the path or body:

| Method | Path | Body | Result |
|---|---|---|---|
| PUT | `/notifications/devices` | `{deviceId, token, platform, locale?, appVersion?}` | 201, idempotent upsert. **403** if `deviceId` belongs to another user and the token differs; the same token moves the device to the caller. A token already stored under another `deviceId` replaces that row. At most 20 devices per user (oldest evicted). `platform` is `ANDROID` or `IOS` (web is 400) |
| DELETE | `/notifications/devices/{deviceId}` | — | 200, no-op when absent or not yours |

**Events.** New: `dictionary.rated` (ms_marketplace), `dictionary.changed`
(ms_dictionary, any change to a user's dictionary or words — `dictionary.updated` only covers public
listing fields). Changed: `dictionary.imported` gains `publisherId`. Every event is published after
commit, fires on change only, and identifies users by `keycloakId`. Add each to the routing-key table
in `verborum.md` and `messaging`.

**Delivery rules** (the seven rules in `rabbitmq.md` apply unchanged):

- One dead-lettered queue per routing key; `INFERRED` type precedence on the converter.
- Listeners are idempotent. A redelivery may send a second push; collapse keys make that harmless.
- FCM `UNREGISTERED` (404) → delete the token, do not retry. 429 / 5xx → throw, so retry and the DLQ work.
- Never send from a request thread. (Batching of 500 tokens only matters if a fan-out category is ever added.)
- `user.deleted` removes every token of that user, matched on **`keycloakId`**.
- A token not seen for 30 days is removed by a scheduled job (`last_seen_at`; clients re-PUT on each
  launch and on `onNewToken`).

**Secrets.** The service-account JSON is a credential. It is read from `FIREBASE_CREDENTIALS_FILE`
(a path) with `FIREBASE_PROJECT_ID`; both are `${VAR:}` in `application.properties` with placeholders in
`.env.example`. Never in Java, an image, the realm JSON or git — `.gitignore` guards the usual file
names. With no file configured the sender logs a WARN and skips, so local development works without it.

## 5. Android (`verborum_android`)

Skills there: `android-dev`, `android-app-architecture`, `gradle-toolchain`, `android-app-sec`.

1. `google-services` Gradle plugin + Firebase BoM + `firebase-messaging` in `gradle/libs.versions.toml`;
   `google-services.json` from the `verborum-dev` project in `app/` (separate debug/release configs
   when prod exists).
2. `VerborumMessagingService : FirebaseMessagingService` (Hilt-aware, in `app` or a `notifications`
   slice, not in `core`). `onNewToken` → enqueue a WorkManager job (network constraint) that PUTs the
   token. `onMessageReceived` handles `SYNC` and `REFRESH`.
3. Register through a **`PostLoginHook`**, and again on every launch (updates `last_seen_at`).
4. **Logout:** `DELETE /notifications/devices/{deviceId}`, then `FirebaseMessaging.deleteToken()`.
5. **No permission prompt and no notification channel.** Data messages show nothing, so
   `POST_NOTIFICATIONS` is not needed. Add both only when a visible alert is planned.
6. A user who force-stops the app receives nothing until they open it; that is expected.
7. `SYNC` and `REFRESH` messages are ignored if the user is logged out.
8. `SYNC` enqueues the existing sync worker; `REFRESH` invalidates the Forum cache. Do not sync inside
   `onMessageReceived`.

## 6. Web (`verborum_kmp`, `js` and `wasmJs`)

**Decided: no FCM on web.** Refetch when the tab becomes visible (`visibilitychange`) and
on a slow interval while open. That covers "edited on Android, the open web tab is stale" for free, with
no service worker, VAPID key, token or CSP change. A truly silent web push is also not possible: Chrome
requires every push to show a visible notification, so a push arriving while no tab is focused would
display a generic one. The web app still **causes** `SYNC` pushes, because any API write emits
`dictionary.changed`.

If web push is ever wanted: `firebase-messaging-sw.js` at the web root; permission only after a user
click; `getToken` with the VAPID key over a small JS bridge; a `PushTokenRegistrar` interface in
`commonMain` bound in Koin; CSP opened for Firebase after reading `webapp-security`; serve from HTTPS or
`localhost`. Add no Android code (the project has no Android target). Safari on iOS needs a Home Screen
install and is out of scope.

## 7. iOS (last — needs an Apple Developer Program membership)

The membership (99 USD per year) is the same account used to publish; the Push capability is included.
Then: Push Notifications capability + Background Modes "Remote notifications" in Xcode (creates the
entitlements file with `aps-environment`; silent pushes need no user prompt but are throttled and
unreliable, so the app must never depend on them), an APNs auth key (`.p8`) uploaded to both Firebase projects,
Firebase iOS SDK via Swift Package Manager, and a thin Swift bridge that hands the FCM token and the tap
payload to Kotlin through `PushTokenRegistrar`. Keep Swift thin; Kotlin owns the logic. Read
`ios-security` first.

## 8. Testing locally

There is **no FCM emulator**; the Firebase Local Emulator Suite has no Messaging. The backend, the
emulators and the simulators run locally; only FCM itself is external and it is free.

- **Android emulator:** use a virtual device with a **Google Play** image. The app gets a real FCM token.
- **Send by hand** (no backend needed):

```bash
TOKEN=$(gcloud auth print-access-token)          # or any OAuth token for the dev service account
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  https://fcm.googleapis.com/v1/projects/PROJECT_ID/messages:send \
  -d '{"message":{"token":"DEVICE_TOKEN",
       "android":{"priority":"NORMAL","collapse_key":"sync"},
       "data":{"type":"SYNC","dictionaryId":"UUID"}}}'
```

  The Firebase console's "Send test message" always sends a visible notification, so use curl to test
  silent data messages. Watch `onMessageReceived` in Logcat.
- **Web:** serve from `localhost` (a secure context), register the service worker, ask permission.
- **iOS simulator (later):** `xcrun simctl push booted BUNDLE_ID payload.apns` for a fake push with no
  network; a real remote push needs the Push capability, Apple silicon or a T2 Mac, macOS 13+, and the
  APNs sandbox.
- **End to end on the local stack:** two dev tokens (`testuser`, `testadmin`), device A imports a
  dictionary of device B's user; watch `ms_notification` log the send and the other device sync.
- Use **two Firebase projects**, `verborum-dev` and `verborum-prod`, with separate config files and
  service accounts, so a test message never reaches a real user.

## 9. Security and privacy checklist

- Device record belongs to the JWT subject; another user's `deviceId` is a no-op on DELETE.
- Logout deletes the token, so a shared phone stops syncing the previous user's data.
- Payload: ids only; nothing private on the lock screen.
- Service account holds only the **Firebase Cloud Messaging API Admin** role.
- The privacy text names Google (and later Apple) as processors of the device token.
- Run the `security-auditor` agent on `ms_notification` before it is exposed.

## 10. Verification checklist

1. One device registers once; a second PUT creates no new row.
2. Logout removes the token and the phone receives nothing for that user.
3. An `UNREGISTERED` reply removes the token.
4. An edit on device A makes device B sync with its app in the background, and A receives nothing.
5. Ten quick edits deliver at most a few messages.
6. With the app force-stopped or the push lost, opening the dictionary list still syncs everything.
7. `user.deleted` removes every token of that user.
8. A redelivered event does no harm.

## 11. Not verified when this was written

The 500-token multicast limit of the Admin SDK, the iOS Safari web push version floor (16 or 16.4 —
sources differ), and `xcrun simctl push` working without a paid account. Confirm each against the vendor
documentation during `P4N-04`, `P4N-10` and `P4N-12`.

## 12. Firebase setup (`P4N-01`, owner console work, Android only)

Web does not use FCM and iOS comes later, so only the Android app and a server key are needed now.

1. **Create the project.** [console.firebase.google.com](https://console.firebase.google.com) → Add
   project → name `verborum-dev`. Turn Google Analytics **off** (not needed). Note the **Project ID**
   (Project settings → General); it may differ from the name.
2. **Check the messaging API.** Project settings → Cloud Messaging. **Firebase Cloud Messaging API (V1)**
   must be enabled. If it shows disabled, use the menu there to open it in Google Cloud and enable it.
   Ignore the "Cloud Messaging API (Legacy)" entry; it is not used.
3. **Register the Android app.** Project overview → Add app → Android. Package name
   `de.coldtea.verborum.app` (debug and release share it). Leave the nickname and SHA-1 empty; FCM does
   not need a SHA-1. Download `google-services.json` and place it in `verborum_android/app/`. Do **not**
   commit it yet; the repo's policy is decided in `P4N-09`.
4. **Create the server key.** Project settings → Service accounts → Generate new private key. Save the
   file as `~/.config/verborum/firebase-dev-adminsdk.json`, then `chmod 600` it. Keep it **outside every
   repo** and never paste its contents anywhere. For production use a dedicated service account with only
   the **Firebase Cloud Messaging API Admin** role; the default one is acceptable for dev.
5. **Skip for now:** the Web app and VAPID key (no web push), the iOS app and APNs key (no Apple
   account yet), and the `verborum-prod` project (created in `P4N-11`).

**Verify the server key without a device** (a dry run, nothing is delivered). The check needs an OAuth
access token for the service account; this Mac has no `gcloud`, so mint it with a short script from the
key file. Then:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  https://fcm.googleapis.com/v1/projects/PROJECT_ID/messages:send \
  -d '{"validate_only":true,"message":{"topic":"verify","data":{"type":"SYNC"}}}'
```

HTTP 200 proves the project, the API and the key are correct. A real delivery test needs a device token,
which the app only gets after `P4N-09` adds the Firebase SDK.

**Hand back:** the Project ID, a confirmation that `google-services.json` is in `app/` and the key file is
saved. Never the key contents.
