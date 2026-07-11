---
name: new-event
description: Step-by-step checklist for adding a complete RabbitMQ event across Verborum services. Use when a service needs to publish an event another service consumes. All steps are required — a half-wired event fails silently.
---

# Adding a New RabbitMQ Event

Full code patterns are in `docs/agent/rabbitmq.md`. Do every step below.

## 1. Event DTO (publisher side)
Create in `common/event/{Event}Event.java` of the PUBLISHING service:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DictionaryVisibilityEvent {
    private String dictionaryId;
    private String userId;
    // ... payload fields
    private LocalDateTime eventTimestamp;
}
```

## 2. Routing key
Pick a key in the `{domain}.{event}` / `{domain}.{sub}.{detail}` format. Add it as a constant
in `RabbitMQConfig` of both services. It must match (or extend) the routing key table in
`docs/agent/verborum.md`.

## 3. Exchange
Both services declare the shared topic exchange `verborum.events` (durable).

## 4. Consumer queue + binding
In the CONSUMER's `RabbitMQConfig`:
- Declare a durable queue with a DLQ argument (`x-dead-letter-exchange`).
- Bind it to `verborum.events` with the routing key pattern (`#` / `*` as needed).

## 5. Listener (consumer side)
Create `common/listener/{Source}EventListener.java`:
```java
@Component @RequiredArgsConstructor @Slf4j
public class UserEventListener {
    private final SomeService someService;

    @RabbitListener(queues = "my.queue.name")
    public void handle(SomeEvent event) {
        log.info("Received ... {}", event.getId());
        try {
            someService.doSomething(event);
        } catch (Exception e) {
            log.error("Failed to process ...", e);
            throw e;   // re-throw so the DLQ catches it
        }
    }
}
```

## 6. Publisher (publisher side)
Publish from the SERVICE LAYER (not the controller):
```java
rabbitTemplate.convertAndSend(EXCHANGE, "routing.key", event);
```
Do it inside the same transactional write method where the triggering change happens.

## 7. Dead Letter Queue
Ensure the consumer has a dead-letter exchange + queue + binding declared.

## 8. JSON converter
Both services configure `Jackson2JsonMessageConverter` on the `RabbitTemplate` and the
listener container factory.

## 9. Update the routing key table
Add or confirm the row in `docs/agent/verborum.md`'s RabbitMQ events table. Mandatory —
this table is the event source of truth.

## 10. Test
- Unit-test the listener with `@SpringBootTest` + `@MockBean` (see verborum-testing).
- Manually: trigger the publisher, confirm the message in the RabbitMQ Management UI
  (localhost:15672) and the listener log.

## Tip
For the whole thing in one shot, delegate to the `event-wirer` subagent.
