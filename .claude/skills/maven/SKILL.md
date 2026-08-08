---
name: maven
description: The Maven build for Verborum — aggregator pom and module registration, the pinned dependency set, JaCoCo, and the build, test and run commands. Use when adding a dependency, creating a module, or running a build.
---

# Maven

Build layout and commands. What goes inside a service is `spring-boot-app-architecture`.

**Always use the wrapper `./mvnw` from the repo root, never a system `mvn`.**

## Quick start

```bash
./mvnw clean install                     # whole aggregator
./mvnw -pl ms_dictionary compile         # one module
./mvnw -pl ms_dictionary test            # one module's tests
./mvnw test -Dtest=DictionaryServiceImplTest
./mvnw clean verify                      # tests + JaCoCo report
./mvnw -pl ms_dictionary spring-boot:run
```

JaCoCo report lands at `ms_{name}/target/site/jacoco/index.html`. Target is ≥ 80% line coverage on
service implementation classes.

Running a service also needs its infrastructure up — see `infra-ops`. The root compose file and the
per-module compose files bind the same host ports: run one or the other, never both.

## Aggregator

`verborum_ms/pom.xml` is packaging `pom`, groupId `de.coldtea.verborum`, artifactId `verborum-ms`.
**Every new service module must be added to `<modules>`** — a module that is not listed is
invisible to a root build and will silently rot.

```xml
<modules>
    <module>ms_dictionary</module>
    <module>ms_user</module>
</modules>
```

It declares no dependencies and no dependency management: each module inherits from
`spring-boot-starter-parent` directly.

## Module pom

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.2</version>
    <relativePath/>
</parent>
<groupId>de.coldtea.verborum</groupId>
<artifactId>ms{name}</artifactId>          <!-- no underscore: msdictionary, msuser -->
<version>0.0.1-SNAPSHOT</version>
<properties>
    <java.version>17</java.version>
    <!-- pinned above the version the parent manages, for CVE-2024-1597 -->
    <postgresql.version>42.6.2</postgresql.version>
</properties>
```

Plugins: `spring-boot-maven-plugin` and `jacoco-maven-plugin` 0.8.11 (`prepare-agent` plus a
`report` execution bound to `prepare-package`). MapStruct's processor is picked up from the
classpath; no explicit compiler-plugin configuration is needed.

The full dependency table with versions and scopes is in
[references/dependency-set.md](references/dependency-set.md).

## Workflow: adding a dependency

1. Check whether the Boot parent already manages it. If so, add it with **no** `<version>`.
2. Add it to **every** module that needs it, at the same version. Divergence between services is a
   defect, and there is no shared BOM to catch it.
3. If it is pinned above the managed version for a CVE, say so in a comment next to the property —
   as `postgresql.version` does.

## Pitfalls

- Forgetting to register a new module in the aggregator `<modules>`
- Pinning a version on a Boot-managed starter
- Re-declaring JUnit or Mockito on top of `spring-boot-starter-test`, which downgrades them
- Different versions of the same library in ms_dictionary and ms_user
- Using a system `mvn` instead of `./mvnw`
