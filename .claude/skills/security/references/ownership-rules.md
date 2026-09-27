# Ownership Rules

From roadmap P3-05 and P3-08. Authentication proves who is calling; authorization decides what they
may touch. These are separate, and the second was added later.

## The shape

The controller takes the caller from the token and passes it into the service as an explicit
argument. The service never reads the security context.

```java
// controller
@GetMapping("/dictionary/{dictionaryId}")
public ResponseEntity<DictionaryResponseDTO> getDictionaryById(@PathVariable String dictionaryId) {
    return new ResponseEntity<>(
            dictionaryService.getDictionaryById(dictionaryId, getCurrentUserId()), HttpStatus.OK);
}

// a path variable that names a user, kept for client compatibility
@GetMapping("/{userId}")
public ResponseEntity<List<DictionaryResponseDTO>> getAllDictionariesByUser(@PathVariable String userId) {
    requireSelf(userId);
    return new ResponseEntity<>(dictionaryService.getDictionariesByUser(userId), HttpStatus.OK);
}
```

Why an explicit argument rather than reading the context inside the service: the service stays
unit-testable without a security context, and the event-driven paths can call the same class
without one.

## The status table

| Situation | Result | Reasoning |
|---|---|---|
| Write naming another user | **403** | the client has a bug; silently rewriting the id would let it ship looking healthy |
| Read of another user's resource **by id** | **404** | a 403 confirms the id exists, so a caller could enumerate |
| Batch or list endpoint | **filter to the caller** | refusing would break legitimate mixed requests, and filtering leaks nothing |
| Event-driven path | **unguarded** | the actor is another service; `deleteAllByUserId` and the private delete helper are the deliberate exceptions |

## The two-helper split

`DictionaryTagServiceImpl` is the reference implementation. The same lookup, two different
outcomes:

```java
/** Writes: a dictionary owned by someone else is refused outright. */
private void requireOwnedDictionary(String dictionaryId, String ownerId) {
    Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
            .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));
    if (!ownerId.equals(dictionary.getUserId())) {
        throw new ForbiddenOperationException(NOT_THE_OWNER);
    }
}

/** Reads: someone else's dictionary is indistinguishable from one that does not exist. */
private void requireReadableDictionary(String dictionaryId, String ownerId) {
    Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
            .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));
    if (!ownerId.equals(dictionary.getUserId())) {
        throw new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId);
    }
}
```

A child resource inherits its parent's ownership: tags follow their dictionary, so a write on
someone else's dictionary's tags is 403 and a read is 404.

## `requireSelf` throws rather than substituting

```java
public static void requireSelf(String claimedUserId) {
    if (!getCurrentUserId().equals(claimedUserId)) {
        throw new ForbiddenOperationException(NOT_THE_OWNER);
    }
}
```

Quietly replacing the claimed id with the caller's would hide a client bug that then ships. A 403
surfaces it.

## The identity trap

`fk_user_id` holds the **JWT subject**. In ms_user, that value is the `keycloak_id` column, not its
`user_id`. An ownership check or a cascade written against ms_user's `user_id` matches nothing and
reports success — a silent no-op that looks like a clean run.

## Testing ownership

These are security behaviours, not edge cases. Every ownership-checked method needs:

- the owner succeeding
- a non-owner getting `ForbiddenOperationException` on a write
- a non-owner getting `RecordNotFoundException` on a read by id
- a list filtered rather than refused

Plus a web-slice case proving the status code that reaches the caller, since `requireSelf` runs in
the controller and a service-level test cannot see it. See `unit-testing` and
`integration-testing`.
