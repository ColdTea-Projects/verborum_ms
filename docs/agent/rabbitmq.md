# RabbitMQ — Messaging Patterns

## Decision
Verborum uses **RabbitMQ** for async inter-service communication.
Chosen over Kafka because: simpler ops, right-sized for Verborum's event volume,
excellent Spring AMQP integration, and no event replay requirement exists in this project.

---

## Maven Dependency

Add to any service that produces or consumes events:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

---

## Docker Compose — Add RabbitMQ

Add to the root or gateway `docker-compose.yml`:
```yaml
rabbitmq:
  image: rabbitmq:3-management
  ports:
    - "5672:5672"    # AMQP protocol
    - "15672:15672"  # Management UI (guest/guest)
  environment:
    RABBITMQ_DEFAULT_USER: verborum
    RABBITMQ_DEFAULT_PASS: verborum
```

---

## application.properties

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=verborum
spring.rabbitmq.password=verborum
```

---

## Exchange & Queue Design

**One topic exchange for all Verborum events:**
```
Exchange name: verborum.events
Exchange type: topic
Durable: true
```

**Routing key conventions:** `{domain}.{event}` or `{domain}.{sub}.{detail}`

| Routing Key | Publisher | Subscriber Queue | Purpose |
|---|---|---|---|
| `dictionary.visibility.public` | ms_dictionary | `marketplace.dictionary.public` | Dictionary made public |
| `dictionary.visibility.private` | ms_dictionary | `marketplace.dictionary.private` | Dictionary made private |
| `dictionary.deleted` | ms_dictionary | `marketplace.dictionary.deleted` | Dictionary deleted |
| `user.deleted` | ms_user | `dictionary.user.deleted` | Cascade cleanup in ms_dictionary |
| `user.deleted` | ms_user | `marketplace.user.deleted` | Cascade cleanup in ms_marketplace |
| `dictionary.imported` | ms_marketplace | `user.dictionary.imported` | Add to user vault |
| `word.created` | ms_dictionary | `autofil.word.created` (V2) | Feed Autofil suggestions |

---

## Configuration Class Pattern

Each service that uses RabbitMQ needs a `RabbitMQConfig` class:

```java
// common/config/RabbitMQConfig.java
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "verborum.events";

    // Declare the exchange (shared — all services declare the same exchange)
    @Bean
    public TopicExchange verborumExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    // --- Producer service: declare queues + bindings it writes to (optional but good practice) ---

    // --- Consumer service: declare queues + bindings it reads from ---
    @Bean
    public Queue myServiceQueue() {
        return QueueBuilder.durable("my.service.queue")
                .withArgument("x-dead-letter-exchange", EXCHANGE + ".dlx")
                .build();
    }

    @Bean
    public Binding myServiceBinding(Queue myServiceQueue, TopicExchange verborumExchange) {
        return BindingBuilder
                .bind(myServiceQueue)
                .to(verborumExchange)
                .with("dictionary.visibility.#");  // # matches zero or more words
    }

    // Dead Letter Queue for failed messages.
    // The DLX is a FANOUT, deliberately. RabbitMQ keeps a message's original routing key when it
    // dead-letters it (a failed `user.deleted` still carries `user.deleted`), so a direct DLX
    // would only catch it if the queue also set `x-dead-letter-routing-key` — forget that on one
    // queue and its failed messages are dropped as unroutable, silently. Fanout ignores the
    // routing key, so naming the DLX in `x-dead-letter-exchange` is always sufficient.
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(EXCHANGE + ".dlx", true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("verborum.dead-letter").build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange);   // fanout: no routing key
    }
}
```

---

## Message DTOs (Events)

Event payloads are plain Java objects serialized as JSON.
Define them in `common/event/` package of the **publishing** service.
If a consuming service needs the same type, duplicate or create a shared library.

```java
// common/event/DictionaryVisibilityEvent.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryVisibilityEvent {
    private String dictionaryId;
    private String userId;
    private Boolean isPublic;
    private String fromLang;
    private String toLang;
    private String dictionaryName;
    private OffsetDateTime updatedAt;      // ordering key for the projection - rule 4
    private OffsetDateTime eventTimestamp; // when the event was raised, not when the data changed
}

// common/event/DictionaryDeletedEvent.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryDeletedEvent {
    private String dictionaryId;
    private String userId;
    private LocalDateTime eventTimestamp;
}

// common/event/UserDeletedEvent.java
// Carries BOTH ids on purpose. ms_dictionary and ms_marketplace store the JWT subject in their
// fk_user_id columns, and that value is ms_user's keycloak_id — NOT its user_id. A consumer
// cascading in another service must match on keycloakId; matching on userId deletes nothing and
// reports success. userId is carried for correlation.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent {
    private String userId;
    private String keycloakId;
    private LocalDateTime eventTimestamp;
}

// common/event/DictionaryImportedEvent.java
// Identifies the user by keycloakId for the same reason as UserDeletedEvent: ms_marketplace only
// ever sees the JWT subject, and ms_user's own user_id is private to ms_user. The consumer
// resolves keycloakId -> user_id before writing the vault entry. Contract fixed by P2-09; P4-07
// must publish exactly these fields.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryImportedEvent {
    private String dictionaryId;
    private String keycloakId;
    private LocalDateTime eventTimestamp;
}

// common/event/WordCreatedEvent.java  (V2 — for Autofil)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordCreatedEvent {
    private String wordId;
    private String dictionaryId;
    private String userId;
    private String word;
    private String translation;
    private String fromLang;
    private String toLang;
    private LocalDateTime eventTimestamp;
}
```

---

## The Seven Rules

Standing conventions for anything event-driven in Verborum. They came out of the 2026-07-23 review;
each one exists because of a specific way this system can go wrong. Read them before designing an
event, not after.

**1. Publish after commit, never inside the transaction.**
A send inside a transaction can be followed by a rollback, and then you have announced something that
never happened — a *phantom event*. Publishing after commit can instead lose an event if the process
dies in the gap. These are not equally bad: a phantom `user.deleted` destroys live data in another
service and cannot be undone, while a lost one leaves orphaned rows that a re-publish or a
reconciliation sweep can clean up. **Always prefer the recoverable failure.** See the publisher
pattern below.

**2. An event carries what the consumer needs.**
A consumer should never have to call back into the publisher to act on an event. `DictionaryVisibilityEvent`
carries the full listing payload for exactly this reason. Callbacks reintroduce runtime coupling, they
race with the publisher's own transaction, and they turn a broker outage into a cascade.

**3. Every consumer is idempotent.**
Delivery is at-least-once. A redelivery must be a no-op, not a duplicate row or a double increment.
The established trick here is a UNIQUE constraint plus a find-or-create service method — one code
path then serves both the HTTP caller and the listener (`VaultService.addVaultEntry`,
`DictionaryTagService.addTag`).

**4. An event that updates a projection carries a version or timestamp, and the consumer drops stale
ones.**
Messages can arrive out of order. Two quick renames delivered in reverse leave the projection holding
the older name, permanently, with nothing to signal it. Carry the entity's `updatedAt` and have the
consumer ignore anything not newer than what it already holds.

**5. Denormalize for read models; do not reach across services at request time.**
If a service must filter, sort or paginate on a field, it has to store that field. Fetching it from
the owning service per request means you cannot page in the database, you inherit that service's
latency and downtime, and — in this codebase — you hit the P3-08 ownership filter, which returns
nothing to a service account. A marketplace listing is a read model; treat it as one.

**6. A reconciliation job is not optional once you have projections.**
Rule 1 admits a small window where an event can be lost, and rule 4 admits a stale projection if an
event is missed entirely. A periodic re-sync is the backstop for both, and it is the tool you will
want during an incident. Write it with the projection, not after the first drift is reported.

**7. External, non-transactional calls belong after commit too, best-effort, with a loud log.**
Keycloak, email, payments. They cannot be rolled back, so doing them inside a transaction has the
same phantom problem as rule 1 — worse, in Keycloak's case, because a deleted identity cannot be
recreated with the same subject. Do them after commit; on failure log at ERROR with the id, because
that log line is the only record that a manual cleanup is owed (`KeycloakUserService`).

---

## Publisher Pattern

Publish from the **service layer** (never the controller), and **after the transaction commits**
(rule 1). The service does not touch `RabbitTemplate` at all: it raises a Spring application event,
and one small listener per service does the actual send once the transaction is safely committed.

```java
// common/event/OutboundEvent.java — the envelope, internal to the service
public record OutboundEvent(String routingKey, Object payload) { }

// common/listener/OutboundEventPublisher.java — the only place RabbitTemplate is used
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboundEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(OutboundEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, event.routingKey(), event.payload());
        } catch (Exception e) {
            // The transaction is already committed - failing the caller now would be a lie.
            // This log is the record that the event never went out.
            log.error("Failed to publish {} after commit", event.routingKey(), e);
        }
    }
}

// the service just raises it, inside its transaction
@Transactional
@Override
public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dto, String ownerId) {
    Dictionary saved = dictionaryRepository.saveAndFlush(dictionaryMapper.toDictionary(dto));
    // ... only on an actual visibility flip ...
    eventPublisher.publishEvent(new OutboundEvent(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, payload));
    return dictionaryMapper.toDictionaryResponseDTO(saved);
}
```

Three things that are easy to get wrong:

- **`fallbackExecution = true` is deliberate.** By default a `@TransactionalEventListener` does
  nothing at all when no transaction is active — the event is silently dropped. Any publisher path
  that is not `@Transactional` would lose its events with no error anywhere. With the fallback, it
  sends immediately instead.
- **The send failing cannot fail the request.** The write is already committed. Log it; do not throw.
- **Unit tests verify `ApplicationEventPublisher`, not `RabbitTemplate`.** The send is covered once,
  in the publisher's own test, plus a transactional test that asserts a rollback publishes nothing.

---

## Consumer Pattern

Use `@RabbitListener` on a dedicated **listener class** in `common/listener/` package:

```java
// common/listener/UserEventListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DictionaryService dictionaryService;

    @RabbitListener(queues = "dictionary.user.deleted")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted event for userId: {}", event.getUserId());
        try {
            dictionaryService.deleteAllByUserId(event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process user.deleted event for userId: {}",
                    event.getUserId(), e);
            throw e;  // re-throw to trigger DLQ
        }
    }
}
```

---

## JSON Serialization Configuration

Configure `RabbitTemplate` to serialize messages as JSON:

```java
@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        // enhancedObjectMapper() registers JavaTimeModule but leaves WRITE_DATES_AS_TIMESTAMPS
        // ON, which renders an event's LocalDateTime as [2026,7,16,15,17,53,415040500].
        // Pin to ISO-8601 — readable in the Management UI, portable to non-Java consumers.
        ObjectMapper objectMapper = JacksonUtils.enhancedObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // ... rest of config
}
```

`JacksonUtils` lives in `org.springframework.amqp.support.converter` (not `...amqp.support`).

**Any service that CONSUMES must also set the type mapper to `INFERRED` precedence:**
```java
DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
typeMapper.setTrustedPackages("de.coldtea.verborum.*");
converter.setJavaTypeMapper(typeMapper);
```
`Jackson2JsonMessageConverter` stamps every outgoing message with a `__TypeId__` header holding the
publisher's fully-qualified class name, and by default the consumer trusts that header. Between
services that never works: the publisher's `…msmarketplace.common.event.DictionaryImportedEvent` does
not exist in the consumer, so every message fails with ClassNotFound — permanently, straight to the
DLQ, with an error that reads like a broker fault. `INFERRED` makes the `@RabbitListener` method's
own parameter type win, so each service deserializes into its own copy of the event and only the
JSON field names have to agree. Added in ms_user at P2-09; ms_dictionary needs it at P2-10.

**Every service must configure the converter this way.** Timestamp format is part of the wire
contract: a publisher writing ISO-8601 and a consumer expecting the array form (or vice versa)
disagree at the boundary.

**`eventTimestamp` is `OffsetDateTime`, not `LocalDateTime`** (changed 2026-07-23). The samples above
show `LocalDateTime` historically; every event now carries an offset, for the same reason P0-19/P0-20
made the entity timestamps zone-aware — a zoneless timestamp is ambiguous the moment publisher and
consumer run in containers with different default zones, which is exactly when you are reading the
DLQ trying to work out what happened. Do not swap in Boot's auto-configured `ObjectMapper` here either — it is
shared with the web layer, and event serialization should not shift when someone tunes REST JSON.

---

## Error Handling & Dead Letter Queue (DLQ)

- Every consumer queue is configured with a dead letter exchange (see config above)
- If a listener throws an exception, the message is routed to the DLQ after retry
- Log the error before re-throwing so it appears in service logs
- Monitor the DLQ via RabbitMQ Management UI (localhost:15672)
- `x-dead-letter-exchange` is all a consumer queue needs — the DLX is a fanout, so it catches the
  message whatever its original routing key. Do not "fix" the DLX to a direct exchange without
  also adding `x-dead-letter-routing-key` to every consumer queue; see the note in the config above.

Configure retry in `application.properties`:
```properties
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.initial-interval=1000
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.retry.multiplier=2.0
```

---

## Routing Key Wildcard Reference
- `*` matches exactly one word: `dictionary.*` matches `dictionary.deleted` but not `dictionary.visibility.public`
- `#` matches zero or more words: `dictionary.#` matches `dictionary.deleted` AND `dictionary.visibility.public`

---

## Checklist When Adding a New Event

0. Re-read "The Seven Rules" above. In particular: does the payload carry everything the consumer
   needs (2), is the consumer idempotent (3), and if it feeds a projection does it carry `updatedAt`
   so stale deliveries can be dropped (4)?

1. Define event DTO in `common/event/` of the publishing service
2. Add routing key constant to `RabbitMQConfig` in both services
3. Declare queue + binding in consuming service's `RabbitMQConfig`
4. Implement `@RabbitListener` in consuming service's `common/listener/`
5. Publish from service layer (not controller) in publishing service
6. Add DLQ binding for the new queue
7. Update `docs/agent/verborum.md` routing key table
