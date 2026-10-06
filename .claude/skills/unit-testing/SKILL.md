---
name: unit-testing
description: JUnit 5 and Mockito conventions for Verborum — the exact setUp boilerplate, test naming, what to cover, and how to assert raised events. Use when writing or reviewing an isolated test of a service implementation, a listener, or a pure helper.
---

# Unit Testing

Isolated tests: no Spring context, no database, no broker. Anything that loads a context is
`integration-testing`.

JUnit 5 and Mockito both come transitively from `spring-boot-starter-test` — do not declare them
separately with their own versions.

## Quick start

```java
class DictionaryServiceImplTest {

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private DictionaryMapper dictionaryMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DictionaryServiceImpl dictionaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void saveDictionary_Success() {
        // Arrange
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        // Act
        DictionaryResponseDTO result = dictionaryService.saveDictionary(requestDTO, OWNER);
        // Assert
        assertEquals(responseDTO, result);
        verify(dictionaryRepository).saveAndFlush(dictionary);
    }
}
```

## Fixed conventions

- **`MockitoAnnotations.openMocks(this)` in `@BeforeEach setUp()`.** Not
  `@ExtendWith(MockitoExtension.class)` — the whole suite uses this form, and the extension's
  strict stubbing would flag existing tests.
- **No full-context annotation in a unit test.**
- Mock every dependency with `@Mock`; inject with `@InjectMocks`.
- Location mirrors the source: `src/test/java/.../{domain}/service/impl/{Name}ImplTest.java`.
- Naming is `{methodUnderTest}_{Scenario}`: `saveDictionary_Success`,
  `deleteWords_DictionaryNotFound_ThrowsException`, `addTag_AlreadyExists_ReturnsExisting`.
- Bodies carry `// Arrange` / `// Act` / `// Assert` comments, in that order.

## What every public service method needs

1. **Happy path** — the result, plus `verify()` that each collaborator got the right arguments.
2. **Exception path** — `assertThrows`, plus `verifyNoInteractions` / `verifyNoMoreInteractions` on
   whatever must not have run.
3. **Boundaries** — empty list, absent `Optional`, a null optional field, a no-op delete.
4. **Ownership** — the owner succeeds; a non-owner gets `ForbiddenOperationException` on a write
   and `RecordNotFoundException` on a read by id; a list is filtered rather than refused. These are
   security behaviours, not edge cases.
5. **Events raised** — both the positive and the negative case.

Mockito idioms, event assertions and listener tests are in
[references/mockito-patterns.md](references/mockito-patterns.md).

## Do not unit test

Repository interfaces and MapStruct mappers (both generated), controllers in isolation (that is a
web slice), or private methods (reach them through their public callers).

## Running

```bash
./mvnw -pl ms_dictionary test
./mvnw test -Dtest=DictionaryServiceImplTest
./mvnw clean verify     # + JaCoCo report at target/site/jacoco/index.html
```

Target ≥ 80% line coverage on service implementation classes. Coverage is a floor, not the goal —
an untested ownership branch matters more than three more percent.

## Pitfalls

- `@ExtendWith(MockitoExtension.class)`
- Loading a Spring context in a unit test
- Testing generated code
- Verifying `RabbitTemplate` from a service test — services raise application events instead
- Only a happy path for a method that has an ownership check or a throw
- `any()` where the specific argument is what the test is about
- A new constructor dependency without a `@Mock` — `@InjectMocks` passes `null` silently (references)
