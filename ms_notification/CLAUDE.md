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
- **Status:** Scaffolded (P4N-02): an empty, running, secured shell — `SecurityConfig`, response
  envelope, `GlobalExceptionHandler`, empty Liquibase master. No entities, endpoints, RabbitMQ or
  Firebase code yet.

## What arrives next
- `P4N-03` `device_tokens` + `PUT`/`DELETE /notifications/devices` (owner from the JWT subject)
- `P4N-04` FCM sender (Firebase Admin SDK; adds the dependency and `FIREBASE_CREDENTIALS_FILE`)
- `P4N-06`/`P4N-07` events and consumers (adds `spring-boot-starter-amqp` and the RabbitMQ pins that
  were left out of the pom on purpose — the AMQP health indicator would turn `/actuator/health`
  DOWN with no broker)

## Quirks
- Silent data messages only: no `notification` block, no preferences table (`P4N-05` is deferred).
- The Firebase service-account key is a credential. It lives outside every repo
  (`~/.config/verborum/`), is read through `FIREBASE_CREDENTIALS_FILE`, and `.gitignore` guards its
  usual file names.
