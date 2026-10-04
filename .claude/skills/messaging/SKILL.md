---
name: messaging
description: RabbitMQ event-driven communication in Verborum — the shared topic exchange, routing keys, after-commit publishing, listeners, and the dead-letter setup. Use when working on events, queues, publishers, listeners, or any inter-service communication.
---

# Messaging (RabbitMQ 3)

Async inter-service communication. Chosen over Kafka: simpler operations, right-sized volume,
first-class Spring AMQP support, no replay requirement.

The routing-key table in `docs/agent/verborum.md` is the **source of truth for events**. Only the
**root** compose file runs a broker — see `infra-ops`.

## Quick start — the publishing pattern

A service never touches `RabbitTemplate`. It raises a Spring application event inside its
transaction; one publisher per service sends it **after commit**.

```java
@Transactional
@Override
public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dto, String ownerId) {
    Dictionary saved = dictionaryRepository.saveAndFlush(dictionaryMapper.toDictionary(dto));
    // only on an actual visibility flip — never on a plain re-save
    eventPublisher.publishEvent(new OutboundEvent(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, payload));
    return dictionaryMapper.toDictionaryResponseDTO(saved);
}
```

Publisher and listener classes are in
[references/publisher-and-consumer.md](references/publisher-and-consumer.md).

## Exchange and routing keys

One durable **topic** exchange for everything: `verborum.events`. Keys are `{domain}.{event}` or
`{domain}.{sub}.{detail}`. Wildcards: `*` is one word, `#` is zero or more.

| Routing key | Published by | Consumer queue | Trigger |
|---|---|---|---|
| `dictionary.visibility.public` | ms_dictionary | `marketplace.dictionary.visibility.public` (live) | `is_public` set true |
| `dictionary.visibility.private` | ms_dictionary | `marketplace.dictionary.visibility.private` (live) | `is_public` set false |
| `dictionary.deleted` | ms_dictionary | `marketplace.dictionary.deleted` (live) | dictionary deleted |
| `dictionary.updated` | ms_dictionary | `marketplace.dictionary.updated` (live) | listed field of a public dictionary changed |
| `dictionary.snapshot` | ms_dictionary | `marketplace.dictionary.snapshot` (live) | nightly schedule — reconciliation (rule 6) |
| `word.created` | ms_dictionary | ms_autofil (V2) | new word added |
| `user.deleted` | ms_user | `dictionary.user.deleted`, `marketplace.user.deleted` (live) | account deleted |
| `user.profile.updated` | ms_user | `marketplace.user.profile.updated` (live) | display name set, changed or cleared |
| `dictionary.imported` | ms_marketplace | `user.dictionary.imported` (live) | listed dictionary imported (every successful call) |

Both built services declare the exchange and the dead-letter infrastructure; declarations are
idempotent, so whichever starts first creates them. Events with no bound queue are discarded by the
topic exchange — that is expected until the consumer exists.

## The rules that cause outages when broken

1. **Publish after commit, never inside the transaction.** A rollback after a send announces
   something that never happened, and a phantom `user.deleted` destroys live data in another
   service. A lost event only leaves recoverable orphans — always prefer the recoverable failure.
2. **Consumers are idempotent.** Delivery is at-least-once; a redelivery must be a no-op.
3. **User-identifying events carry `keycloakId`, and consumers cascade on it.** Cascading on
   ms_user's `userId` matches nothing and reports success.
4. **Every consumer sets `INFERRED` type precedence** on its message converter, or every
   cross-service message fails as ClassNotFound straight to the dead-letter queue.
5. **The dead-letter exchange is a fanout** — do not convert it to a direct exchange.

All seven standing rules, with the reasoning behind each, are in
[references/seven-rules.md](references/seven-rules.md). The `RabbitMQConfig` beans and the two
load-bearing converter settings are in
[references/rabbitmq-config.md](references/rabbitmq-config.md).

## Event DTOs

In `common/event/` of the **publishing** service, `@Data @Builder @NoArgsConstructor
@AllArgsConstructor`, carrying an `eventTimestamp`. A consuming service keeps its **own copy** of
the class — only the JSON field names have to agree.

`eventTimestamp`, and any entity timestamp carried on an event, is `OffsetDateTime`. A zoneless
timestamp is ambiguous exactly when you are reading the dead-letter queue trying to work out what
happened.

## Workflow: wiring a new event

Ten steps in [references/wire-an-event.md](references/wire-an-event.md), across both services.
Every step is required — **a half-wired event fails silently.**

## Pitfalls

- `rabbitTemplate.convertAndSend(...)` anywhere but `OutboundEventPublisher`
- Publishing inside the transaction, or dropping `fallbackExecution = true`
- A queue without `x-dead-letter-exchange`
- Publishing on a plain re-save rather than an actual change
- Shipping an event that never reaches the routing-key table
