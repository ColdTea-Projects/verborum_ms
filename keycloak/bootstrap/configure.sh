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
log "Setting realm loginTheme=verborum, emailTheme=verborum."
"$KCADM" update "realms/${KC_REALM}" -s loginTheme=verborum -s emailTheme=verborum

# --- Identity providers -------------------------------------------------------
# upsert_idp <alias> <providerId> <clientId> <clientSecret> <defaultScope>
# Creates the IdP if absent, otherwise updates its credentials. trustEmail=true so a provider that
# already vouches for the address does not trigger a second Keycloak email verification.
upsert_idp() {
  local alias="$1" provider_id="$2" client_id="$3" client_secret="$4" scope="$5" display_name="$6"

  if [[ -z "$client_id" || -z "$client_secret" ]]; then
    log "IdP '${alias}': credentials not set — skipping (this provider stays OFF)."
    return 0
  fi

  # displayName is the button label on the login page (the theme adds the brand icon via CSS).
  if "$KCADM" get "identity-provider/instances/${alias}" -r "$KC_REALM" >/dev/null 2>&1; then
    log "IdP '${alias}': exists — updating credentials."
    "$KCADM" update "identity-provider/instances/${alias}" -r "$KC_REALM" \
      -s enabled=true \
      -s trustEmail=true \
      -s "displayName=${display_name}" \
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
      -s "displayName=${display_name}" \
      -s "config.clientId=${client_id}" \
      -s "config.clientSecret=${client_secret}" \
      -s "config.useJwksUrl=true" \
      -s "config.defaultScope=${scope}"
  fi
  log "IdP '${alias}': done."
}

upsert_idp "google"   "google"   "${GOOGLE_CLIENT_ID:-}"     "${GOOGLE_CLIENT_SECRET:-}"     "openid profile email"  "Google"
upsert_idp "facebook" "facebook" "${FACEBOOK_CLIENT_ID:-}"   "${FACEBOOK_CLIENT_SECRET:-}"   "email public_profile"  "Facebook"

# --- Per-environment redirect URIs and web origins ----------------------------
# The realm import ships only the local values: the custom scheme (Android + iOS) and
# `http://localhost:*`. Anything deployed has an origin the import cannot know, and the web client
# is the one that needs it — the KMP web app authenticates as `verborum-app` with its own page as
# the redirect target (`https://<origin>/`), not as a custom scheme.
#
# So a deployed origin is added here rather than committed, exactly like the IdP secrets above: it
# is per-environment, and the import path would not substitute it anyway. No-op on a laptop.
#
#   APP_WEB_ORIGIN=https://app.verborum.coldtea.de
#
# Multiple origins: comma-separate them. Each contributes `<origin>/*` as a redirect URI. Only the
# redirect list is managed here — the client's `webOrigins` is `"+"`, which tells Keycloak to derive
# the CORS allowlist from these same redirect URIs, so it follows along on its own.
if [[ -n "${APP_WEB_ORIGIN:-}" ]]; then
  log "verborum-app: adding deployed web origin(s) to the redirect allowlist."

  CID=$("$KCADM" get clients -r "$KC_REALM" -q clientId=verborum-app --fields id --format csv | tr -d '"' | tail -n1)
  if [[ -z "${CID:-}" ]]; then
    log "verborum-app: client not found — cannot add redirect URIs. Is the realm imported?"
    exit 1
  fi

  # Start from what the import defines rather than appending to whatever a previous run left, so a
  # re-run with a changed APP_WEB_ORIGIN replaces the old origin instead of accumulating stale ones.
  REDIRECTS='"de.coldtea.verborum://oauth2redirect/*","http://localhost:*"'
  IFS=',' read -ra ORIGIN_LIST <<< "${APP_WEB_ORIGIN}"
  for origin in "${ORIGIN_LIST[@]}"; do
    origin="${origin%/}"                       # a trailing slash would make the pattern `…//*`
    [[ -z "$origin" ]] && continue
    REDIRECTS="${REDIRECTS},\"${origin}/*\""
  done

  "$KCADM" update "clients/${CID}" -r "$KC_REALM" -b "{\"redirectUris\":[${REDIRECTS}]}"
  log "verborum-app: redirect allowlist updated."
else
  log "verborum-app: no APP_WEB_ORIGIN set — keeping the local redirect URIs from the realm import."
fi

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

# --- API audience (SEC-05) ----------------------------------------------------
# The services accept only access tokens whose `aud` contains the API audience
# (spring.security.oauth2.resourceserver.jwt.audiences), so a token minted for any other client in the
# realm — the verborum-backend service account, an admin tool — is refused. The user-facing clients
# get an audience mapper that adds it. The realm import carries the same mapper; this step adds it to
# a realm that was imported before it existed. Idempotent; a client that is absent (verborum-dev-cli in
# a shared realm) is skipped.
API_AUDIENCE="${VERBORUM_JWT_AUDIENCE:-verborum-api}"
for client in verborum-app verborum-dev-cli; do
  cid=$("$KCADM" get clients -r "$KC_REALM" -q "clientId=${client}" --fields id --format csv --noquotes | head -n1 | tr -d '\r')
  if [[ -z "$cid" ]]; then
    log "Audience: client ${client} not in this realm — skipped."
    continue
  fi
  if "$KCADM" get "clients/${cid}/protocol-mappers/models" -r "$KC_REALM" --fields name --format csv --noquotes \
      | tr -d '\r' | grep -qx "verborum-api-audience"; then
    log "Audience: ${client} already adds '${API_AUDIENCE}'."
  else
    "$KCADM" create "clients/${cid}/protocol-mappers/models" -r "$KC_REALM" \
      -s name=verborum-api-audience -s protocol=openid-connect -s protocolMapper=oidc-audience-mapper \
      -s "config.\"included.custom.audience\"=${API_AUDIENCE}" \
      -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=false' >/dev/null
    log "Audience: added '${API_AUDIENCE}' to ${client}."
  fi
done

# --- Passwordless email-code browser flow ------------------------------------
# Needs the verborum-email-code SPI (baked into the custom Keycloak image). ON by default — the full
# choose-password-or-code flow is browser-verified end to end. Set EMAIL_CODE_ENABLED=false to fall
# back to the stock password-only browser flow.
if [[ "${EMAIL_CODE_ENABLED:-true}" == "true" ]]; then
  log "Configuring passwordless email-code browser flow."
  bash /bootstrap/configure-email-code-flow.sh
else
  log "EMAIL_CODE_ENABLED=false — leaving the stock browser flow (password only)."
fi

log "Bootstrap complete."
