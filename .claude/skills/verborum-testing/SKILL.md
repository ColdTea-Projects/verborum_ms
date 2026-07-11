---
name: verborum-testing
description: Unit and integration testing conventions for Verborum. Use when writing or reviewing tests, or when a service implementation needs coverage. Covers JUnit 5 + Mockito patterns specific to this project.
---

# Verborum Testing

Full patterns and examples live in `docs/agent/testing.md`. Read it for the boilerplate and
Mockito snippets.

## Core rules

- JUnit 5 + Mockito.
- Unit tests use `MockitoAnnotations.openMocks(this)` in `@BeforeEach setUp()`.
  Do NOT use `@ExtendWith(MockitoExtension.class)`.
- `@SpringBootTest` only for RabbitMQ listener tests, not for plain unit tests.
- Mock with `@Mock`, inject with `@InjectMocks`.
- Test method naming: `{methodUnderTest}_{Scenario}` (e.g. `saveDictionary_Success`).
- Cover happy path, exception path, and boundary cases.
- Assert downstream calls with `verify()`; assert absence with `verifyNoInteractions()`.
- Do NOT test repository interfaces or MapStruct mappers — they are generated.

## RabbitMQ listeners

Use `@SpringBootTest` + `@MockBean` on the delegated service, call the listener directly,
verify the service was invoked. Pattern in `docs/agent/testing.md`.

## Coverage

JaCoCo is configured. Run `./mvnw clean verify`; report at `target/site/jacoco/index.html`.
Target ≥ 80% line coverage on service implementation classes.

## Delegation

For bulk test writing, delegate to the `test-writer` subagent.
