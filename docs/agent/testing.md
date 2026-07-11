# Testing Standards

## Stack
- JUnit 5 (`junit-jupiter-engine`)
- Mockito (`mockito-junit-jupiter`)
- Spring Boot Test (`spring-boot-starter-test`)

---

## Unit Test Structure

Every service implementation must have a corresponding unit test in:
`src/test/java/.../service/impl/{ServiceName}ImplTest.java`

### Boilerplate
```java
class DictionaryServiceImplTest {

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private DictionaryMapper dictionaryMapper;

    @InjectMocks
    private DictionaryServiceImpl dictionaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
}
```

**Rules:**
- Do NOT use `@ExtendWith(MockitoExtension.class)` — use `MockitoAnnotations.openMocks(this)` in `@BeforeEach` (existing project pattern)
- Do NOT use `@SpringBootTest` for unit tests — it loads the full context and is slow
- Mock all dependencies with `@Mock`
- Inject mocks with `@InjectMocks`

---

## Test Method Naming

Pattern: `{methodUnderTest}_{scenario}`

```java
@Test
void saveDictionary_Success() { }

@Test
void saveDictionary_Failure() { }

@Test
void deleteDictionary_Success() { }

@Test
void getDictionariesByUser_Success() { }

@Test
void getDictionariesByUser_EmptyList() { }

@Test
void deleteWordsByDictionaryId_DictionaryNotFound_ThrowsException() { }
```

---

## Unit Test Pattern

### Happy Path
```java
@Test
void saveDictionary_Success() {
    // Arrange
    DictionaryRequestDTO requestDTO = new DictionaryRequestDTO();
    Dictionary dictionary = new Dictionary();
    DictionaryResponseDTO responseDTO = new DictionaryResponseDTO();

    when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
    when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
    when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(responseDTO);

    // Act
    DictionaryResponseDTO result = dictionaryService.saveDictionary(requestDTO);

    // Assert
    assertEquals(responseDTO, result);
    verify(dictionaryMapper).toDictionary(requestDTO);
    verify(dictionaryRepository).saveAndFlush(dictionary);
    verify(dictionaryMapper).toDictionaryResponseDTO(dictionary);
}
```

### Exception Path
```java
@Test
void saveDictionary_RepositoryThrows_ExceptionPropagates() {
    // Arrange
    DictionaryRequestDTO requestDTO = new DictionaryRequestDTO();
    Dictionary dictionary = new Dictionary();

    when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
    when(dictionaryRepository.saveAndFlush(dictionary))
            .thenThrow(new RuntimeException("DB error"));

    // Act & Assert
    assertThrows(RuntimeException.class, () -> dictionaryService.saveDictionary(requestDTO));
    verify(dictionaryMapper).toDictionary(requestDTO);
    verify(dictionaryRepository).saveAndFlush(dictionary);
    verifyNoMoreInteractions(dictionaryMapper);
}
```

### RecordNotFoundException
```java
@Test
void deleteWordsByDictionaryId_DictionaryNotFound_ThrowsException() {
    // Arrange
    String dictionaryId = "non-existent-uuid";
    when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(RecordNotFoundException.class,
            () -> wordService.deleteWordsByDictionaryId(dictionaryId));
    verify(dictionaryRepository).findById(dictionaryId);
    verifyNoInteractions(wordRepository);
}
```

---

## What to Test

### Always test:
- Happy path for every public service method
- Exception path for methods that throw domain exceptions
- Boundary cases (empty list, null optional, etc.)
- That downstream dependencies are called with correct arguments (`verify()`)
- That downstream dependencies are NOT called when they shouldn't be (`verifyNoInteractions()`)

### Don't unit test:
- Repository interfaces (Spring Data generates the implementation)
- MapStruct mapper interfaces (MapStruct generates the implementation)
- Controllers in isolation (covered by integration tests or Swagger manual testing)
- Private methods (test them through their public callers)

---

## Testing RabbitMQ Listeners

Use `@MockBean` on the service the listener delegates to,
and call the listener method directly — no need to involve actual RabbitMQ:

```java
@SpringBootTest
class UserEventListenerTest {

    @MockBean
    private DictionaryService dictionaryService;

    @Autowired
    private UserEventListener userEventListener;

    @Test
    void handleUserDeleted_CallsDeleteAllByUserId() {
        // Arrange
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId("test-user-uuid")
                .eventTimestamp(LocalDateTime.now())
                .build();

        // Act
        userEventListener.handleUserDeleted(event);

        // Assert
        verify(dictionaryService).deleteAllByUserId("test-user-uuid");
    }

    @Test
    void handleUserDeleted_ServiceThrows_ExceptionPropagates() {
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId("test-user-uuid")
                .build();
        doThrow(new RuntimeException("Service error"))
                .when(dictionaryService).deleteAllByUserId(any());

        assertThrows(RuntimeException.class,
                () -> userEventListener.handleUserDeleted(event));
    }
}
```

---

## Useful Mockito Snippets

```java
// Verify called exactly once (default)
verify(myRepository).findById("some-id");

// Verify called N times
verify(myMapper, times(3)).toResponseDTO(any(MyEntity.class));

// Verify never called
verify(myRepository, never()).deleteById(any());

// Verify no interactions at all
verifyNoInteractions(myRepository);

// Verify no more interactions after what was already verified
verifyNoMoreInteractions(myMapper);

// Stub void method to throw
doThrow(new RuntimeException("error")).when(myRepository).deleteById(any());

// Stub to return different values on successive calls
when(myRepository.findById(any()))
    .thenReturn(Optional.of(entity))   // first call
    .thenThrow(new RuntimeException()); // second call

// Capture argument
ArgumentCaptor<MyEntity> captor = ArgumentCaptor.forClass(MyEntity.class);
verify(myRepository).saveAndFlush(captor.capture());
assertEquals("expected-value", captor.getValue().getSomeField());
```

---

## Test Coverage

JaCoCo is configured in all services. Run with:
```bash
./mvnw clean verify
```
Coverage report at: `target/site/jacoco/index.html`

Target coverage: **≥ 80% line coverage** on service implementation classes.
