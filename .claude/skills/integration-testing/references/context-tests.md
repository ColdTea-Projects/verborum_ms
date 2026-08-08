# Full-Context Tests

The most expensive tier. They boot the real application against the real database, so the compose
stack must be up — on a machine with no database they fail rather than skipping.

Use them only when a transaction boundary or the whole wiring is the thing under test.

## contextLoads

```java
@SpringBootTest
class MsDictionaryApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Cheap insurance that every bean still resolves — it catches a missing `@Bean`, a circular
dependency, or a configuration property that no longer parses. One per module.

## The after-commit test

`UserDeletedAfterCommitTest` in ms_user is the test that justifies the after-commit publisher. It
drives a real transaction and asserts that a **rollback publishes nothing** while a commit publishes
exactly once.

```java
@SpringBootTest
class UserDeletedAfterCommitTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean private RabbitTemplate rabbitTemplate;
    @MockBean private KeycloakUserService keycloakUserService;

    private String userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void commit_PublishesOnce() {
        User user = givenAUser();
        transactionTemplate.executeWithoutResult(status ->
                userService.deleteUser(userId, user.getKeycloakId()));
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY_USER_DELETED), any(Object.class));
    }

    @Test
    void rollback_PublishesNothing() {
        // ... same setup, then status.setRollbackOnly()
        verifyNoInteractions(rabbitTemplate);
    }
}
```

Why it matters: under the previous pattern — sending as the last statement inside the transaction —
the rollback case would have emitted `user.deleted` for a user who still exists, and ms_dictionary
reacts to that event by deleting the user's dictionaries and words. **That is the regression which
must never come back.**

`RabbitTemplate` and `KeycloakUserService` are mocked, so nothing leaves the process.

## Why it is not duplicated in ms_dictionary

Both services share the same `OutboundEventPublisher` implementation, so the guarantee is proven
once. Duplicating it would double the slowest test in the suite to re-prove identical code. If the
two implementations ever diverge, that reasoning stops holding and the second test becomes
necessary.

## Rules for full-context tests

- **Clean up what you insert.** These run against the development database; an `@AfterEach` that
  deletes by id is the minimum.
- **Mock anything that leaves the process** — the broker, the Keycloak admin client, any outbound
  HTTP.
- **Generate unique data per run** (`UUID.randomUUID()` for ids and emails) so a leftover row from a
  failed run does not break the next one.
- **Do not use one as a substitute for a unit test.** If mocking the collaborators would answer the
  same question, that is the cheaper tier.
- **Throwaway verification scaffolding is not a test.** A full-context class written only to print
  an event payload should be run with `-Dtest=` and then deleted.
