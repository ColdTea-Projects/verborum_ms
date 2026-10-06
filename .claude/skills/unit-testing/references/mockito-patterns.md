# Mockito Patterns

## Happy path

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
    DictionaryResponseDTO result = dictionaryService.saveDictionary(requestDTO, OWNER);

    // Assert
    assertEquals(responseDTO, result);
    verify(dictionaryMapper).toDictionary(requestDTO);
    verify(dictionaryRepository).saveAndFlush(dictionary);
}
```

## Exception path

```java
@Test
void saveDictionary_RepositoryThrows_ExceptionPropagates() {
    when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
    when(dictionaryRepository.saveAndFlush(dictionary)).thenThrow(new RuntimeException("DB error"));

    assertThrows(RuntimeException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
    verifyNoMoreInteractions(dictionaryMapper);
}
```

## Not-found path

```java
@Test
void deleteWordsByDictionaryId_DictionaryNotFound_ThrowsException() {
    when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

    assertThrows(RecordNotFoundException.class,
            () -> wordService.deleteWordsByDictionaryId(dictionaryId, OWNER));
    verify(dictionaryRepository).findById(dictionaryId);
    verifyNoInteractions(wordRepository);
}
```

## Ownership branches

```java
@Test
void getDictionaryById_NotTheOwner_ThrowsRecordNotFound() {
    when(dictionaryRepository.findById(ID)).thenReturn(Optional.of(dictionaryOwnedBy("someone-else")));

    // 404, not 403 — a 403 would confirm the id exists
    assertThrows(RecordNotFoundException.class, () -> dictionaryService.getDictionaryById(ID, OWNER));
}

@Test
void addTag_NotTheOwner_ThrowsForbidden() {
    when(dictionaryRepository.findById(ID)).thenReturn(Optional.of(dictionaryOwnedBy("someone-else")));

    assertThrows(ForbiddenOperationException.class,
            () -> dictionaryTagService.addTag(ID, tagRequest, OWNER));
}
```

Use the exact owner id, not `anyString()` — a stub that accepts any string proves nothing about
which id was used.

## Asserting raised events

A service raises a Spring application event and never touches `RabbitTemplate`, so mock
`ApplicationEventPublisher` and assert on the envelope:

```java
ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
verify(eventPublisher).publishEvent(captor.capture());
assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, captor.getValue().routingKey());
```

The negative case matters as much, because these events fire on change only:

```java
@Test
void saveDictionary_RenameOfPublicDictionary_PublishesNothing() {
    // a rename must not create a duplicate marketplace listing
    verifyNoInteractions(eventPublisher);
}
```

## Listener tests

A `@RabbitListener` class is a component with one dependency, so it is tested as a plain unit test —
`@Mock`, `@InjectMocks`, `openMocks`. Two cases matter:

```java
@Test
void handleUserDeleted_CascadesOnKeycloakId() {
    userEventListener.handleUserDeleted(event());

    // fk_user_id holds the JWT subject; cascading on the userId would match nothing
    // and quietly report success
    verify(dictionaryService).deleteAllByUserId(KEYCLOAK_ID);
    verify(dictionaryService, never()).deleteAllByUserId(USER_ID);
}

@Test
void handleUserDeleted_RethrowsSoTheMessageIsDeadLettered() {
    doThrow(new RuntimeException("db down")).when(dictionaryService).deleteAllByUserId(KEYCLOAK_ID);

    // swallowing would acknowledge a half-finished cascade
    assertThrows(RuntimeException.class, () -> userEventListener.handleUserDeleted(event()));
}
```

## Reference

```java
verify(repo).findById("id");                     // exactly once (default)
verify(mapper, times(3)).toResponseDTO(any());
verify(repo, never()).deleteById(any());
verifyNoInteractions(repo);
verifyNoMoreInteractions(mapper);

doThrow(new RuntimeException("error")).when(repo).deleteById(any());

when(repo.findById(any()))
    .thenReturn(Optional.of(entity))             // first call
    .thenThrow(new RuntimeException());          // second call

ArgumentCaptor<Word> captor = ArgumentCaptor.forClass(Word.class);
verify(repo).saveAndFlush(captor.capture());
assertEquals("expected", captor.getValue().getWord());
```

## When the class under test gains a dependency

`@InjectMocks` uses constructor injection and **silently passes `null`** for a constructor argument that has
no matching `@Mock`. The test compiles, then fails with a `NullPointerException` deep in the method, or
worse, passes because the branch is never reached. When a constructor gains a parameter (e.g.
`UserEventListener` gaining `DictionaryRatingService`), add the `@Mock` in every test class of that class.
Then add a test for what the new dependency is for; `InOrder` when the order matters:

```java
InOrder inOrder = inOrder(dictionaryRatingService, dictionaryStatsService);
inOrder.verify(dictionaryRatingService).deleteRatingsByUser(KEYCLOAK_ID);
inOrder.verify(dictionaryStatsService).deleteListingsByUser(KEYCLOAK_ID);
```

## Fixtures send what a real client sends

When a rule tightens (e.g. `isPublic` required on create), existing tests that built a bare
`new DictionaryRequestDTO()` start failing. Fix the fixture by setting the field the way a real client
does, with a short comment, rather than loosening the rule or deleting the test. The test then documents
the contract.

## Boxed numbers in assertions

`assertEquals(2, entity.getStars())` is ambiguous when `getStars()` returns `Short` or `Long`. Compare
`getStars().intValue()`, or use a typed literal such as `(short) 2`.
