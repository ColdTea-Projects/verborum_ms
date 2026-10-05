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
| `org.postgresql:postgresql` | `42.7.13` via property | | pinned over the managed 42.7.11 (see "Security pins") |
| `org.liquibase:liquibase-core` | managed (4.31.1) | | |
| `org.projectlombok:lombok` | managed (1.18.46) | `provided` | |
| `org.mapstruct:mapstruct` | `1.6.3` | | |
| `org.mapstruct:mapstruct-processor` | `1.6.3` | | |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | `2.8.17` | | Swagger UI + `/v3/api-docs` |
| `org.jetbrains:annotations` | `26.1.0` | | never `RELEASE` — a floating version breaks reproducible builds and dependency scanning |
| `spring-boot-starter-test` | managed | `test` | JUnit 5 + Mockito + AssertJ + MockMvc |
| `spring-security-test` | managed | `test` | the `jwt()` MockMvc post-processor |

## ms_user only

```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>26.0.12</version>   <!-- the admin client is versioned separately from the server; works with Keycloak 26 -->
</dependency>
```

It is the only service that talks to the Keycloak Admin API.

## Do not add these

**JUnit and Mockito with explicit versions.** They come transitively from
`spring-boot-starter-test`. Older project docs showed `junit-jupiter-engine:5.6.2` and
`mockito-junit-jupiter:2.23.0` as separate entries — adding those back downgrades both libraries
against Boot 3.5.16 and breaks the test suite in confusing ways.

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
            <version>0.8.15</version>
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

## Security pins and the dependency scan (SEC-08)

Each module pom pins a few Boot-managed libraries above the parent's version, one property each, with
the advisory named in a comment: `tomcat.version`, `rabbit-amqp-client.version`, `postgresql.version`,
`jackson-bom.version`, `commons-lang3.version`, `log4j2.version`, `netty.version`. Remove a pin once
the parent manages that version or a newer one. Keep the three modules identical.

Scan before and after any dependency change. The scan resolves the full transitive tree and needs
every version to be concrete:

```bash
docker run --rm -v "$(pwd -W):/src:ro" ghcr.io/google/osv-scanner:latest scan source --format table \
  /src/ms_dictionary/pom.xml /src/ms_user/pom.xml /src/ms_marketplace/pom.xml
```

The upgrade to Boot 3.5.16 took the result from 228 known vulnerabilities to 0 (2026-10-05).
