# ms_notification — Service Guide

This file supplements the root `CLAUDE.md` and the `.claude/skills/`. It covers only what is
specific to this service. All conventions are inherited from the root — do not duplicate them.

## What this service does
Device tokens and push delivery. It consumes marketplace/dictionary events and sends **silent data
messages** (`SYNC`, `REFRESH`) through Firebase Cloud Messaging so apps stay up to date in the
background. Design, contract and order of work: `docs/agent/push-notifications.md`; tasks
`P4N-01`…`P4N-13` in `docs/agent/roadmap.md` (Phase 4N).

## Facts
- **Port:** 8088
- **DB:** `vdbnotification` (PostgreSQL) — docker-compose in this module (Postgres on 5435 + Adminer on
  8083), and `db_notification` in the root compose on the same host port. Run one or the other.
- **Base package:** `de.coldtea.verborum.msnotification`
- **Status:** Scaffolded (P4N-02), device registration (P4N-03), FCM sender (P4N-04), event consumers
  (P4N-07). **Needs RabbitMQ** — use the root compose; with no broker `/actuator/health` is DOWN.

## Entities
- `DeviceToken` (`device_tokens`) — `deviceId` (PK, client UUID of the app install), `userId`
  (`fk_user_id`, the JWT subject — no DB FK), `platform` (`ANDROID`|`IOS`, CHECK constraint),
  `token` (**UNIQUE**, never returned by the API, excluded from `toString`), `locale`, `appVersion`,
  `lastSeenAt` (set on every register), `createdAt`/`updatedAt`. Migration `2026/10/14-01-changelog.json`.

## API — DeviceTokenController (`/notifications/devices`)
- `PUT` register or refresh (201, idempotent upsert; the owner is the token subject).
  - Existing `deviceId` owned by someone else with a **different** token → **403**; with the **same**
    token it is the same phone and moves to the caller (a second account on one device).
  - A token already stored under another `deviceId` replaces that row (the UNIQUE token holds).
  - At most 20 devices per user; the least recently seen are evicted.
  - A concurrent double PUT can trip the UNIQUE token → **409** (`DataIntegrityViolationException`),
    never a 500; the client retry succeeds.
- `DELETE /{deviceId}` remove (200; a no-op when absent **or not yours**, matching the other deletes).
  Clients call it on logout.
- No `GET`: a token is a delivery address, not data a client needs back (405).
- `RequestBodyLimitFilter` caps a body at 16 KB (`MAX_REQUEST_BODY_BYTES`), 413 above it (SEC-07).

## Push sending (`push/`, P4N-04)
- `PushMessage(type, dictionaryId, collapseKey)` — a **silent data message**: no `notification` block, ids
  only. `PushSender` is the channel: `FirebasePushSender` (Admin SDK, `sendEach`) or `NoOpPushSender`.
  `FirebaseConfig` picks one: **no `FIREBASE_CREDENTIALS_FILE` = no-op with a WARN** (local dev works
  without a key); a path that is set but unreadable **fails the start-up**.
- `PushDispatchService.sendToUser(keycloakId, message, excludeDeviceId)` — the entry point for listeners.
  Loads the user's tokens, skips the writing device, sends in batches of ≤ 500, deletes dead tokens, then
  throws `PushDeliveryException` if anything failed transiently (so the broker redelivers). It is
  deliberately **not `@Transactional`**: a network call must not hold a database connection.
- FCM verdicts: `UNREGISTERED`/`SENDER_ID_MISMATCH` → token deleted; `QUOTA_EXCEEDED`/`UNAVAILABLE`/
  `INTERNAL`/`THIRD_PARTY_AUTH_ERROR` → retry; `INVALID_ARGUMENT` → neither (payload and token look alike).
- Android: normal priority + collapse key + 1 h TTL. iOS: `background` push (`content-available`), which
  Apple throttles — never relied on.
- `FIREBASE_DRY_RUN=true` validates every request with FCM and delivers nothing.
- `FirebasePushSenderLiveTest` is opt-in (runs only with `FIREBASE_CREDENTIALS_FILE` set, always dry run).
- **Netty is pinned to 4.1.138.Final** (SEC-08); `firebase-admin` brings 4.1.135 otherwise.

## Events (P4N-07) — consumes only, publishes nothing
| Routing key | Queue | From | Effect |
|---|---|---|---|
| `dictionary.changed` | `notification.dictionary.changed` | ms_dictionary | silent `SYNC` to the owner's **other** devices (the event's `deviceId` is skipped) |
| `dictionary.imported` | `notification.dictionary.imported` | ms_marketplace | silent `REFRESH` to `publisherId` |
| `dictionary.rated` | `notification.dictionary.rated` | ms_marketplace | silent `REFRESH` to `publisherId` |
| `user.deleted` | `notification.user.deleted` | ms_user | deletes the user's tokens — **match on `keycloakId`** |
| `vault.changed` | `notification.vault.changed` | ms_user | silent `SYNC` to the owner's **other** devices — a Forum download or unsubscribe (P4N-14); the event's `deviceId` is skipped |

- Listeners (`common/listener/`, one class per source service) delegate to `NotificationService` /
  `DeviceTokenService` and re-throw, so retry (3 attempts, backoff) and then the dead-letter queue take over.
  A `PushDeliveryException` (quota, outage) takes that path on purpose.
- An event with no recipient (no `keycloakId`, or an import from before `publisherId`) is **ignored with a
  WARN**, not thrown: it can never succeed, so retrying it would only fill the dead-letter queue.
- The actor is never notified: `SYNC` skips the writing device, `REFRESH` is not sent when the publisher
  is the actor. Collapse keys: `sync` and `refresh` — one pending message of each per user.
- No server-side debounce (decided, see roadmap `P4N-07`): the collapse key and the client's unique sync
  work already coalesce a burst, and a debounce either drops the last edit or needs state that does not
  survive a restart.
- The publisher's Forum membership is **not mirrored**: ms_marketplace only publishes these events for a
  listing whose publisher is a member.
- Event DTOs in `common/event/` are consumer-side copies; `RabbitMQConfigTest` pins the wire contract
  through the real converter (INFERRED type precedence, unknown fields ignored).

## What arrives next
- `P4N-08` docs + `security-auditor` run, then the clients (`P4N-09` Android; web needs no push). The
  service itself is feature-complete for the first release; preferences (`BL-06`, was `P4N-05`) are deferred.

## Quirks
- **A device row can be moved by anyone holding its FCM token** (audit P4N-08, accepted): the same token
  under a new account proves it is the same phone, which is what makes a second account on one device
  work. A leaked token is the only way to abuse it, the impact is a silent id-only push to the wrong
  account, and each move is logged (both subjects, never the token).
- **Fan-out is not rate-limited here** (audit P4N-08, accepted for now): every real write raises one
  `dictionary.changed`, so a user scripting writes at API speed makes FCM sends in step. The caller can only
  hurt their own devices and the shared FCM quota; per-user write limits belong at the gateway (`P5-04`).
  The shared `verborum.dead-letter` queue has no TTL or length cap and **cannot be given one from a
  service** — changing a live queue's arguments fails the declaration; do it with a new queue name.
- `RequestBodyLimitFilter` refuses an unknown-length body only when `Transfer-Encoding` is present, like
  ms_user's; Tomcat does not enable HTTP/2 here. Tighten in all services together, not in one.
- Silent data messages only: no `notification` block, no preferences table (`BL-06`, was `P4N-05`, is deferred).
- The Firebase service-account key is a credential. It lives outside every repo
  (`~/.config/verborum/`), is read through `FIREBASE_CREDENTIALS_FILE`, and `.gitignore` guards its
  usual file names.
