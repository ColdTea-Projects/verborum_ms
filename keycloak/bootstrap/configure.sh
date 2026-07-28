#!/usr/bin/env bash
#
# Post-import Keycloak configuration for the `verborum` realm.
#
# WHY THIS EXISTS
#   The realm is imported from keycloak/import/verborum-realm.json by `start-dev --import-realm`.
#   That native path does NOT reliably substitute ${ENV} placeholders inside the JSON
#   (Keycloak #12069 / #26275), so a secret written as "${GOOGLE_CLIENT_SECRET}" is stored as the
#   literal string and silently breaks login. Therefore every SECRET and every PER-ENVIRONMENT
#   value is applied here, AFTER the realm is live, through kcadm.sh reading environment variables.
#
# PROPERTIES
#   - Idempotent: safe to run on every `docker compose up`. Re-running updates in place.
#   - No-op locally: with an empty .env (no Google/Facebook/real-SMTP vars), it configures nothing
#     and exits 0. A developer laptop needs none of these.
#   - Never echoes secret values.
#
# Runs as the `keycloak-bootstrap` compose service once Keycloak reports healthy.

set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
KC_URL="${KC_URL:-http://keycloak:8080}"
KC_REALM="${KC_REALM:-verborum}"

log() { echo "[keycloak-bootstrap] $*"; }

log "Logging in to ${KC_URL} (realm master) as ${KEYCLOAK_ADMIN}"
"$KCADM" config credentials \
  --server "$KC_URL" \
  --realm master \
  --user "$KEYCLOAK_ADMIN" \
  --password "$KEYCLOAK_ADMIN_PASSWORD"

# --- Login theme --------------------------------------------------------------
# Not a secret, always desired. Set it here too (not just in the realm import) so the Verborum
# branding applies on an EXISTING Keycloak volume without a full re-import. The theme files are
# mounted at /opt/keycloak/themes/verborum.
log "Setting realm loginTheme=verborum."
"$KCADM" update "realms/${KC_REALM}" -s loginTheme=verborum

# --- Identity providers -------------------------------------------------------
# upsert_idp <alias> <providerId> <clientId> <clientSecret> <defaultScope>
# Creates the IdP if absent, otherwise updates its credentials. trustEmail=true so a provider that
# already vouches for the address does not trigger a second Keycloak email verification.
upsert_idp() {
  local alias="$1" provider_id="$2" client_id="$3" client_secret="$4" scope="$5"

  if [[ -z "$client_id" || -z "$client_secret" ]]; then
    log "IdP '${alias}': credentials not set — skipping (this provider stays OFF)."
    return 0
  fi

  if "$KCADM" get "identity-provider/instances/${alias}" -r "$KC_REALM" >/dev/null 2>&1; then
    log "IdP '${alias}': exists — updating credentials."
    "$KCADM" update "identity-provider/instances/${alias}" -r "$KC_REALM" \
      -s enabled=true \
      -s trustEmail=true \
      -s "config.clientId=${client_id}" \
      -s "config.clientSecret=${client_secret}" \
      -s "config.defaultScope=${scope}"
  else
    log "IdP '${alias}': creating."
    "$KCADM" create identity-provider/instances -r "$KC_REALM" \
      -s "alias=${alias}" \
      -s "providerId=${provider_id}" \
      -s enabled=true \
      -s trustEmail=true \
      -s "config.clientId=${client_id}" \
      -s "config.clientSecret=${client_secret}" \
      -s "config.useJwksUrl=true" \
      -s "config.defaultScope=${scope}"
  fi
  log "IdP '${alias}': done."
}

upsert_idp "google"   "google"   "${GOOGLE_CLIENT_ID:-}"     "${GOOGLE_CLIENT_SECRET:-}"     "openid profile email"
upsert_idp "facebook" "facebook" "${FACEBOOK_CLIENT_ID:-}"   "${FACEBOOK_CLIENT_SECRET:-}"   "email public_profile"

# --- Real SMTP override (staging/prod) ---------------------------------------
# The realm JSON already ships a working local smtpServer pointing at Mailpit. Only override it when
# a real SMTP host is provided — i.e. never on a laptop. Mailpit needs no auth; a real provider does.
if [[ -n "${SMTP_HOST:-}" ]]; then
  log "SMTP: overriding realm smtpServer with real provider host '${SMTP_HOST}'."
  "$KCADM" update "realms/${KC_REALM}" \
    -s "smtpServer.host=${SMTP_HOST}" \
    -s "smtpServer.port=${SMTP_PORT:-587}" \
    -s "smtpServer.from=${SMTP_FROM:-no-reply@verborum.app}" \
    -s "smtpServer.fromDisplayName=${SMTP_FROM_DISPLAY_NAME:-Verborum}" \
    -s "smtpServer.starttls=${SMTP_STARTTLS:-true}" \
    -s "smtpServer.auth=${SMTP_AUTH:-true}" \
    -s "smtpServer.user=${SMTP_USER:-}" \
    -s "smtpServer.password=${SMTP_PASSWORD:-}"
  log "SMTP: override applied."
else
  log "SMTP: no SMTP_HOST set — keeping the Mailpit defaults from the realm import."
fi

log "Bootstrap complete."
