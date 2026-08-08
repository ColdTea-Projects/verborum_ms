# Wiring a New Event

Every step is required. A half-wired event fails **silently** — no error, no message, nothing in
the logs.

## 0. Design it against the seven rules

Before writing anything, answer out loud:

- Does the payload carry everything the consumer needs, with no callback? (rule 2)
- Is the consumer idempotent on redelivery? (rule 3)
- If it feeds a projection, does it carry `updatedAt` so stale deliveries can be dropped? (rule 4)
- If it identifies a user, does it carry `keycloakId`?

## 1. Event DTO — publishing service

`common/event/{Event}Event.java`, `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, with an
`OffsetDateTime eventTimestamp`. Copy the class into the consuming service.

## 2. Routing key constant — both services

On `RabbitMQConfig`, in the `{domain}.{event}` or `{domain}.{sub}.{detail}` format, matching the
naming already in the routing-key table.

## 3. Exchange — both services

Both declare the shared durable topic exchange `verborum.events`.

## 4. Consumer queue and binding

In the **consumer's** `RabbitMQConfig`: a durable queue with `x-dead-letter-exchange`, bound to the
exchange with the routing key or a wildcard pattern.

## 5. Listener — consumer side

`common/listener/{Source}EventListener.java`: log on receipt, delegate to one service method,
re-throw on failure. See `publisher-and-consumer.md`.

## 6. Publisher — publishing service

Raise an `OutboundEvent` from the **service layer**, inside the transaction. The send happens after
commit. Never from a controller, never `RabbitTemplate` directly. Fire only on an actual change,
not on a plain re-save.

## 7. Dead-letter infrastructure

Confirm the fanout dead-letter exchange, its queue and its binding exist in the consumer.

## 8. JSON converter

Both services configure it identically; the consumer must have `INFERRED` type precedence. See
`rabbitmq-config.md`.

## 9. Documentation

- The row in the routing-key table in `docs/agent/verborum.md` — **the event source of truth**
- The events section of **both** services' `CLAUDE.md`

## 10. Test and verify

Automated (`unit-testing`, `integration-testing`):

- The service raises the `OutboundEvent` with the right routing key — and does **not** raise it on
  a plain re-save.
- The listener delegates on `keycloakId` and re-throws on failure.

By hand, with the root compose stack up (`infra-ops`):

```bash
# observe an event the service publishes: bind a temp queue, trigger, read it
curl -s -u verborum:verborum -H "content-type: application/json" -X PUT \
  -d '{"durable":false}' "http://localhost:15672/api/queues/%2F/tmp.verify"
curl -s -u verborum:verborum -H "content-type: application/json" -X POST \
  -d '{"routing_key":"user.deleted"}' \
  "http://localhost:15672/api/bindings/%2F/e/verborum.events/q/tmp.verify"
# ... trigger the action ...
curl -s -u verborum:verborum -H "content-type: application/json" -X POST \
  -d '{"count":5,"ackmode":"ack_requeue_false","encoding":"auto"}' \
  "http://localhost:15672/api/queues/%2F/tmp.verify/get"
curl -s -u verborum:verborum -X DELETE "http://localhost:15672/api/queues/%2F/tmp.verify"
```

To test a **consumer**, publish into the exchange through the management API with the `__TypeId__`
header set to the *publisher's* class name — that header naming a class the consumer does not have
is exactly the cross-service case, and proves the `INFERRED` mapper works. A response of
`{"routed":false}` means no queue is bound to that key.

Then check the dead-letter queue is still empty.
