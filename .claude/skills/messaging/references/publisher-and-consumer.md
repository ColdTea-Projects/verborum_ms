# Publisher and Consumer Classes

## The outbound publisher — the only place RabbitTemplate is used

```java
// common/event/OutboundEvent.java — the envelope, internal to the service
public record OutboundEvent(String routingKey, Object payload) { }

// common/listener/OutboundEventPublisher.java
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
            // The transaction is already committed — failing the caller now would be a lie.
            // This log is the record that the event never went out.
            log.error("Failed to publish {} after commit", event.routingKey(), e);
        }
    }
}
```

Three things that are easy to get wrong:

- **`fallbackExecution = true` is deliberate.** By default a `@TransactionalEventListener` does
  nothing at all when no transaction is active — the event is silently dropped. Any publisher path
  that is not `@Transactional` would lose its events with no error anywhere. With the fallback, it
  sends immediately instead.
- **A failed send cannot fail the request.** The write is already committed. Log it; do not throw.
- **Unit tests verify `ApplicationEventPublisher`, not `RabbitTemplate`.** The send is covered once
  in the publisher's own test, and the rollback guarantee once in ms_user's
  `UserDeletedAfterCommitTest` — both services share the listener, so it is not repeated.

## The service side

```java
@Transactional
@Override
public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dto, String ownerId) {
    Dictionary saved = dictionaryRepository.saveAndFlush(dictionaryMapper.toDictionary(dto));
    eventPublisher.publishEvent(new OutboundEvent(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, payload));
    return dictionaryMapper.toDictionaryResponseDTO(saved);
}
```

The injected field is Spring's `ApplicationEventPublisher`. Raise the event only on an actual state
change — see rule 7's corollary in `seven-rules.md`.

## The consumer

```java
// common/listener/UserEventListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DictionaryService dictionaryService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_USER_DELETED)
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted for keycloakId: {}", event.getKeycloakId());
        try {
            dictionaryService.deleteAllByUserId(event.getKeycloakId());
        } catch (Exception e) {
            log.error("Failed to process user.deleted for {}", event.getKeycloakId(), e);
            throw e;   // re-throw so retries, then the dead-letter queue, take over
        }
    }
}
```

Rules for listeners:

- Live in `common/listener/`, one class per source service.
- Log on receipt, delegate to **one** service method, re-throw on failure. Swallowing the exception
  acknowledges a half-finished cascade.
- No business logic in the listener itself.
- **Cascade on `keycloakId`, not `userId`.**
- Event-driven service paths take no caller and are deliberately unguarded by ownership checks —
  their actor is another service, not a logged-in caller.

## Event DTO

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDeletedEvent {
    private String userId;         // correlation only
    private String keycloakId;     // what consumers must match on
    private OffsetDateTime eventTimestamp;
}
```

Defined in `common/event/` of the publishing service, and **copied** into each consuming service.
Only the JSON field names have to agree, because the type mapper infers the target type from the
listener's parameter — see `rabbitmq-config.md`.
