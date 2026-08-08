# Canonical Dependency Set

Boot starters are **unversioned** — the `spring-boot-starter-parent` manages them. Only
non-managed artifacts carry a `<version>`.

| Dependency | Version | Scope | Notes |
|---|---|---|---|
| `spring-boot-starter-web` | managed | | |
| `spring-boot-starter-actuator` | managed | | |
| `spring-boot-starter-data-jpa` | managed | | omit for a gateway |
| `spring-boot-starter-validation` | managed | | |
| `spring-boot-starter-amqp` | managed | | any service that publishes or consumes events |
| `spring-boot-starter-security` | managed | | **from the first commit** |
| `spring-boot-starter-oauth2-resource-server` | managed | | **from the first commit** |
| `spring-boot-devtools` | managed | | |
| `org.postgresql:postgresql` | `42.6.2` via property | | pinned over the managed 42.6.0 for CVE-2024-1597 |
| `org.liquibase:liquibase-core` | `4.25.1` | | |
| `org.projectlombok:lombok` | `1.18.30` | `provided` | |
| `org.mapstruct:mapstruct` | `1.5.5.Final` | | |
| `org.mapstruct:mapstruct-processor` | `1.5.5.Final` | | |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | `2.2.0` | | Swagger UI + `/v3/api-docs` |
| `org.jetbrains:annotations` | `RELEASE` | | |
| `spring-boot-starter-test` | managed | `test` | JUnit 5 + Mockito + AssertJ + MockMvc |
| `spring-security-test` | managed | `test` | the `jwt()` MockMvc post-processor |
| `com.github.springtestdbunit:spring-test-dbunit` | `1.3.0` | `test` | declared, currently unused |
| `dbunit:dbunit` | `2.2` | `test` | declared, currently unused |

## ms_user only

```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>23.0.0</version>
</dependency>
```

It is the only service that talks to the Keycloak Admin API.

## Do not add these

**JUnit and Mockito with explicit versions.** They come transitively from
`spring-boot-starter-test`. Older project docs showed `junit-jupiter-engine:5.6.2` and
`mockito-junit-jupiter:2.23.0` as separate entries — adding those back downgrades both libraries
against Boot 3.2.2 and breaks the test suite in confusing ways.

## Plugins

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <id>default-prepare-agent</id>
                    <goals><goal>prepare-agent</goal></goals>
                </execution>
                <execution>
                    <id>default-report</id>
                    <phase>prepare-package</phase>
                    <goals><goal>report</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

No explicit `maven-compiler-plugin` with `annotationProcessorPaths` is configured. MapStruct's
processor resolves from the classpath, and that has been sufficient — add the explicit
configuration only if Lombok and MapStruct start disagreeing about processing order.
