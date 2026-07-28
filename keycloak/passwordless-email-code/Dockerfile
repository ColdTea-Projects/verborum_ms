# Custom Keycloak image — adds the email-code (passwordless) authenticator SPI.
#
# NOT YET REFERENCED BY docker-compose.yml. Wiring this in is roadmap P3B-06 (see README.md in this
# folder), staged last because a mis-bound auth flow risks locking users out.
#
# To activate: drop the chosen SPI jar next to this file, uncomment the COPY below, then point the
# `keycloak` compose service at `build: ./keycloak/passwordless-email-code` instead of `image:`.

FROM quay.io/keycloak/keycloak:23.0.0

# The email-code Authenticator provider. Pin an exact, Keycloak-23-compatible jar — never a floating
# tag. Record the source in README.md before committing the jar.
# COPY providers/keycloak-email-otp.jar /opt/keycloak/providers/

# Bake providers into an optimized image so startup does not re-augment on every boot.
RUN /opt/keycloak/bin/kc.sh build

# The compose `command:` (start-dev --import-realm) and env still apply — this only adds the SPI.
