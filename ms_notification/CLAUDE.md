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
- **Status:** Scaffolded (P4N-02), device registration (P4N-03). No RabbitMQ or Firebase code yet.

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

## What arrives next
- `P4N-04` FCM sender (Firebase Admin SDK; adds the dependency and `FIREBASE_CREDENTIALS_FILE`)
- `P4N-06`/`P4N-07` events and consumers (adds `spring-boot-starter-amqp` and the RabbitMQ pins that
  were left out of the pom on purpose — the AMQP health indicator would turn `/actuator/health`
  DOWN with no broker)

## Quirks
- Silent data messages only: no `notification` block, no preferences table (`P4N-05` is deferred).
- The Firebase service-account key is a credential. It lives outside every repo
  (`~/.config/verborum/`), is read through `FIREBASE_CREDENTIALS_FILE`, and `.gitignore` guards its
  usual file names.
