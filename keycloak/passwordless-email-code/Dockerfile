# Custom Keycloak image = stock Keycloak 23 + the hand-written Verborum email-code authenticator SPI.
#
# Stage 1 builds the provider jar from the Maven module in this folder. Stage 2 drops it into
# /opt/keycloak/providers and runs `kc.sh build` so the provider is baked in (no per-boot augment).
#
# Wired into docker-compose.yml: the `keycloak` service uses `build: ./keycloak/passwordless-email-code`
# instead of `image:`. The login/email themes are still bind-mounted for fast iteration in dev.

# ---- Stage 1: build the SPI jar ---------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /spi
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

# ---- Stage 2: Keycloak + provider -------------------------------------------
FROM quay.io/keycloak/keycloak:23.0.0
COPY --from=build /spi/target/verborum-email-code-authenticator.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
