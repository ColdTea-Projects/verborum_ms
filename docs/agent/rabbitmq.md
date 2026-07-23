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
    private LocalDateTime eventTimestamp;
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

## Publisher Pattern

Inject `RabbitTemplate` and publish from the **service layer** (not the controller):

```java
@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryMapper dictionaryMapper;
    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = RabbitMQConfig.EXCHANGE;

    @Transactional
    @Override
    public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dto) {
        Dictionary saved = dictionaryRepository.saveAndFlush(
                dictionaryMapper.toDictionary(dto));

        // Publish visibility event if dictionary is public
        if (Boolean.TRUE.equals(saved.getIsPublic())) {
            rabbitTemplate.convertAndSend(
                EXCHANGE,
                "dictionary.visibility.public",
                DictionaryVisibilityEvent.builder()
                    .dictionaryId(saved.getDictionaryId())
                    .userId(saved.getUserId())
                    .isPublic(true)
                    .fromLang(saved.getFromLang())
                    .toLang(saved.getToLang())
                    .dictionaryName(saved.getName())
                    .eventTimestamp(LocalDateTime.now())
                    .build()
            );
        }

        return dictionaryMapper.toDictionaryResponseDTO(saved);
    }
}
```

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
disagree at the boundary. Do not swap in Boot's auto-configured `ObjectMapper` here either — it is
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

1. Define event DTO in `common/event/` of the publishing service
2. Add routing key constant to `RabbitMQConfig` in both services
3. Declare queue + binding in consuming service's `RabbitMQConfig`
4. Implement `@RabbitListener` in consuming service's `common/listener/`
5. Publish from service layer (not controller) in publishing service
6. Add DLQ binding for the new queue
7. Update `docs/agent/verborum.md` routing key table
