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

## Real-database tests for what mocks cannot see

Mocked repositories never exercise the persistence context: flush order, `clearAutomatically`, cascades,
JPQL that only parses at startup. A bug there passes every unit test. Example: a rating `delete()` was
discarded unwritten by the aggregate UPDATE's `clearAutomatically` (see `persistence`).

Pattern (`DictionaryRatingPersistenceTest` in ms_marketplace):

```java
@SpringBootTest
@Transactional                      // rolled back at the end; needs the local stack, like contextLoads
class DictionaryRatingPersistenceTest {
    // arrange rows through the repositories, call the real service, then assert the row AND the aggregate
    // after every write (rate → change → remove)
}
```

**Prove the test catches the bug:** temporarily revert only the fix (one annotation, say), run the test and
see it fail on the expected line, then restore the fix and see it pass. Don't use `git stash` on the file
for this, because it reverts every change in it.

## Verifying against the running stack

- Full-context tests need Postgres up (`docker compose up -d`). Without it they fail with
  "Connection refused", which is an environment error, not a code failure. Report it as such.
- Run the dev seed (`scripts/dev-seed/seed.py`) for realistic data. Without a local Python, run it in a
  container with `host.docker.internal` URLs (`infra-ops` → verification-recipes).
- **Test network exposure from the LAN address,** not from a container: Docker Desktop routes
  `host.docker.internal` to the host's loopback, so a container reaches loopback-only ports that another
  machine cannot.
- After a live check that writes data, clean up (delete the probe accounts through `DELETE /users/{id}`) or
  reset and reseed.
