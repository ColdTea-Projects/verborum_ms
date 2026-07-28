#!/usr/bin/env bash
#
# Builds and binds the Verborum passwordless browser flow (password OR emailed code).
#
# Called by configure.sh when EMAIL_CODE_ENABLED != false. Idempotent: it unbinds + deletes any
# existing copy, then rebuilds from scratch and rebinds — so the flow is config-as-code, reproduced
# identically on every clean `docker compose up`. The email-code step is the hand-written SPI
# (provider id verborum-email-code), baked into the custom Keycloak image.
#
# Flow shape:
#   verborum-browser (bound as realm browserFlow)
#     Cookie                     ALTERNATIVE
#     Identity Provider Redirect ALTERNATIVE   (Google/Facebook)
#     verborum-forms             ALTERNATIVE
#       Username Form            REQUIRED       (sets the user)
#       verborum-first-factor    REQUIRED
#         Password Form          ALTERNATIVE    \ user picks one; "Try another way" switches.
#         Verborum Email Code    ALTERNATIVE    / code path self-gates on a verified email.

set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
KC_URL="${KC_URL:-http://keycloak:8080}"
R="${KC_REALM:-verborum}"
FLOW="verborum-browser"

log() { echo "[keycloak-bootstrap] $*"; }

"$KCADM" config credentials --server "$KC_URL" --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

# Reset to the built-in flow first so the old copy is deletable, then rebuild.
# Delete by ID, not alias: alias-based delete is unreliable and silently no-ops, which breaks
# idempotency (a re-run then fails with "flow already exists").
"$KCADM" update "realms/${R}" -s browserFlow=browser >/dev/null
FID=$("$KCADM" get authentication/flows -r "$R" --fields id,alias --format csv | grep "\"${FLOW}\"" | cut -d, -f1 | tr -d '"' || true)
if [[ -n "${FID:-}" ]]; then
  "$KCADM" delete "authentication/flows/${FID}" -r "$R"
fi

log "Creating browser flow '${FLOW}'."
"$KCADM" create authentication/flows -r "$R" \
  -s alias="${FLOW}" -s providerId=basic-flow -s topLevel=true -s builtIn=false \
  -s "description=Verborum: sign in with a password or an emailed code" >/dev/null

"$KCADM" create "authentication/flows/${FLOW}/executions/execution" -r "$R" -s provider=auth-cookie >/dev/null
"$KCADM" create "authentication/flows/${FLOW}/executions/execution" -r "$R" -s provider=identity-provider-redirector >/dev/null
"$KCADM" create "authentication/flows/${FLOW}/executions/flow" -r "$R" \
  -s alias=verborum-forms -s type=basic-flow -s "description=forms" >/dev/null

"$KCADM" create "authentication/flows/verborum-forms/executions/execution" -r "$R" -s provider=auth-username-form >/dev/null
"$KCADM" create "authentication/flows/verborum-forms/executions/flow" -r "$R" \
  -s alias=verborum-first-factor -s type=basic-flow -s "description=first factor" >/dev/null

"$KCADM" create "authentication/flows/verborum-first-factor/executions/execution" -r "$R" -s provider=auth-password-form >/dev/null
"$KCADM" create "authentication/flows/verborum-first-factor/executions/execution" -r "$R" -s provider=verborum-email-code >/dev/null

log "Setting execution requirements."
declare -A REQ=(
  ["Cookie"]="ALTERNATIVE"
  ["Identity Provider Redirector"]="ALTERNATIVE"
  ["verborum-forms"]="ALTERNATIVE"
  ["Username Form"]="REQUIRED"
  ["verborum-first-factor"]="REQUIRED"
  ["Password Form"]="ALTERNATIVE"
  ["Verborum Email Code"]="ALTERNATIVE"
)
while IFS=, read -r id name req; do
  id=$(echo "$id" | tr -d '"'); name=$(echo "$name" | tr -d '"'); req=$(echo "$req" | tr -d '"')
  want=${REQ[$name]:-}
  if [[ -n "$want" && "$want" != "$req" ]]; then
    "$KCADM" update "authentication/flows/${FLOW}/executions" -r "$R" -b "{\"id\":\"$id\",\"requirement\":\"$want\"}"
  fi
done < <("$KCADM" get "authentication/flows/${FLOW}/executions" -r "$R" --fields id,displayName,requirement --format csv)

"$KCADM" update "realms/${R}" -s browserFlow="${FLOW}" >/dev/null
log "Bound '${FLOW}' as the realm browser flow. Passwordless email-code login is live."
