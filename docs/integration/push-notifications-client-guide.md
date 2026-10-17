# Push notifications — client guide

For the Android and KMP (iOS, web) teams. The design and the backend rules are in
`docs/agent/push-notifications.md`; this file is the contract you build against. Service:
**ms_notification**, reached through the API gateway (`/notifications/**`; `http://localhost:8080` locally). Every endpoint needs the usual bearer
token; the owner is always the token subject.

## What a push is — and is not

- **Silent data messages only.** Nothing is shown to the user, so there is **no notification permission
  prompt, no channel, no badge**. They exist to keep the app current in the background.
- **A hint, never the source of truth.** A push can be dropped (force-quit app, Doze, Low Power Mode,
  Background App Refresh off; iOS throttles hardest). **Keep your full sync on opening the dictionary
  list.** If a push is lost, that sync repairs it.
- Payloads carry **ids only** — no names, words or e-mail. Fetch the data from the API as usual.

## Which platforms

| Platform | Push? | What to do |
|---|---|---|
| **Android** | Yes — FCM | Firebase Messaging; steps below |
| **Web** | **No** | No FCM. Refetch the open screen when the tab becomes visible (`visibilitychange`) and on a slow timer (about 60 s) only while visible. Chrome requires every web push to show a notification, which is not what this is for |
| **iOS** | Later | Needs the Apple Developer account first (roadmap `P4N-12`); same contract, `platform: IOS` |

## 1. Register the device — `PUT /notifications/devices`

```json
{ "deviceId": "<UUID, one per app install, persisted>",
  "token": "<FCM registration token>",
  "platform": "ANDROID",
  "locale": "de-DE",
  "appVersion": "1.4.0" }
```

`locale` and `appVersion` are optional. `platform` is `ANDROID` or `IOS` (anything else, including `WEB`,
is 400). Response: `Response` envelope, **201**, idempotent.

| Status | Meaning |
|---|---|
| 201 | registered or refreshed |
| 400 | validation: `deviceId` not a UUID, blank/oversized `token`, unknown `platform` |
| 401 | no / invalid token |
| 403 | that `deviceId` belongs to another account and the token differs — generate a new `deviceId` |
| 409 | two registrations raced; retry |
| 413 | body larger than 16 KB |

Rules that matter:

- **`deviceId` is stable per install.** Generate it once, persist it, send it on every call. Do not
  regenerate it on login or logout. A second account on the same phone is fine: holding the same token
  proves it is the same device, and it moves to that account.
- **Register after login** (the `PostLoginHook`), **again on every launch** (this refreshes `lastSeen`),
  and **whenever the token rotates** (`onNewToken`). Retry with WorkManager; it is idempotent.
- At most **20 devices per account**; the least recently seen are dropped. Unused tokens are cleaned up
  after about 30 days (a stale-token job arrives with `P4N-11`).
- There is no read endpoint: a token is a delivery address, not data you get back.

## 2. Remove the device — `DELETE /notifications/devices/{deviceId}`

Call it **on logout**, then `FirebaseMessaging.deleteToken()`. **200**, and also 200 when the id is absent
or not yours (nothing is revealed). A deleted account is cleaned up server-side (`user.deleted`).

## 3. Tell the server which device is writing — `X-Device-Id`

Send the same `deviceId` as a header on **every write** that changes the account's data on the server —
ms_dictionary (dictionaries, words, tags), ms_marketplace (a Forum **download**) and ms_user (removing a
download from the vault, i.e. an **unsubscribe**):

```
X-Device-Id: 3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10
```

It is optional and ignored if it is not a UUID. With it, the device that made the change does **not**
receive its own `SYNC`; without it that device just syncs once more. Uploading the whole local state on
every sync is safe: **an upload with no real change publishes nothing**, so devices cannot ping-pong.

## 4. Handle the messages

FCM **data** messages (no `notification` block), normal priority. In `onMessageReceived`:

| `data.type` | When | Do |
|---|---|---|
| `SYNC` | the same account changed a dictionary, its words or tags on another device, **downloaded or unsubscribed** a Forum dictionary there, or joined/left the Forum | enqueue the existing sync as **unique work** (coalesces a burst). Do not sync inline |
| `REFRESH` | someone imported or rated your published dictionary | invalidate the Forum cache so stats refresh on next view |

- `data.dictionaryId` is present for context; **treat it like any untrusted id** (the API still answers 404
  for something you cannot read).
- Ignore any message while logged out; ignore unknown `type`s (the schema evolves).
- Messages use the collapse keys `sync` and `refresh`, so a burst leaves one pending message of each.

## 5. Try it locally

- **Backend:** the root compose stack, then ms_dictionary, ms_notification and the gateway. Without a
  Firebase key ms_notification logs "Push not sent" and delivers nothing; with
  `FIREBASE_CREDENTIALS_FILE` set it sends for real. `FIREBASE_DRY_RUN=true` validates with FCM and
  delivers nothing.
- **Android:** an emulator image **with Google Play**, the `verborum-dev` Firebase project and its
  `google-services.json` in `app/` (kept out of git). Register the device, then edit a dictionary through
  another device or `curl`.
- **A silent test message** needs `curl` to the FCM HTTP v1 API with a `data` block — the Firebase
  console's "Send test message" always sends a *visible* notification and is not a valid test.

## Android — how this app does it (P4N-09)

`core/push/`: `PushRegistrationService` (register / unregister), `PushRegistrationWorker` via
`PushRegistrationScheduler`, `VerborumMessagingService`, `PushMessageRouter`, `DeviceIdInterceptor`,
`DeviceIdStore`. Features contribute a `PushMessageHandler` `@IntoSet`; bibliotheca's turns `SYNC` into a
debounced full sync. Logout runs a `PreLogoutHook` (the DELETE needs the bearer token) before the tokens
are cleared. `app/google-services.json` comes from the Firebase console and is git-ignored.

## Not built / open

- Preferences per category (`BL-06`, was `P4N-05`) — deferred; there is nothing user-visible to switch off.
- Push to everyone who imported a dictionary — cancelled; importers sync on open.
- iOS (`P4N-12`/`P4N-13`) — waits for the Apple Developer account.
