---
name: verborum-messaging
description: RabbitMQ messaging patterns for Verborum. Use when working with events, queues, listeners, publishers, or any inter-service communication. Covers the exchange design, routing keys, and the full event-wiring checklist.
---

# Verborum Messaging (RabbitMQ)

Full patterns and code live in `docs/agent/rabbitmq.md`. The event source of truth (routing
key table) is in `docs/agent/verborum.md`. Read those for details.

## Core facts

- Broker: **RabbitMQ**. One topic exchange for everything: `verborum.events`.
- Routing key format: `{domain}.{event}` or `{domain}.{sub}.{detail}`
  (e.g. `dictionary.visibility.public`, `user.deleted`).
- Wildcards: `*` = one word, `#` = zero or more words.
- Every consumer queue is durable and has a dead-letter binding.

## Standing rules

- **Publish from the service layer**, never the controller.
- **Never skip the DLQ binding** — failed messages must not vanish.
- **Always update the routing key table** in `docs/agent/verborum.md` when adding an event.
- Serialize messages as JSON via `Jackson2JsonMessageConverter`.
- Event DTOs live in `common/event/` of the publishing service and carry an `eventTimestamp`.

## Adding a new event

For the complete step-by-step, invoke the `new-event` skill or delegate to the `event-wirer`
subagent. The seven-part checklist (DTO → routing key → exchange → queue+binding → listener →
publisher → DLQ → update table) must all be done or the event is only half-wired.
