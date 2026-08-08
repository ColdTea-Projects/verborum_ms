# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `mvnw`: "JAVA_HOME is not defined correctly" | Not set for this shell; the JDKs live in `~/.jdks/` | Export `JAVA_HOME` per shell, or set it permanently |
| `contextLoads` fails, unit tests pass | The compose stack is down — a full-context test boots against the real Postgres | `docker compose up -d` |
| Port already in use on 5432 or 8080 | The root and a per-service compose are both running | Stop one |
| 401 with a token that looks fine | Issuer mismatch | Pin `KC_HOSTNAME_URL` and both service URIs to the same origin |
| 401 on every endpoint after a client update | Expected — every endpoint requires a bearer token | Attach the token |
| 403 on your own data | The `userId` sent is not the JWT `sub` | Check for a leftover guest UUID in the client |
| Another user's resource reads as 404 | Deliberate — a 403 would confirm the id exists | Not a bug |
| `/actuator/env` returns 404 | Deliberate — only `health` and `info` are exposed | Not a bug; do not restore `*` |
| Consumer fails with `ClassNotFoundException` | The `INFERRED` type mapper is missing from that service's `RabbitMQConfig` | Add it — see `messaging` |
| Realm edits have no effect | The import only runs on an empty volume | `down`, remove `keycloak_data`, `up` |
| Event published, nothing consumed | `"routed":false`, or no queue bound to that routing key | Check the binding in the Management UI |
| Growing `verborum.dead-letter` | A consumer is throwing | Read the message, fix the listener, purge |
| `Cannot invoke "…getUserId()"` on a cascade | The event's `keycloakId` was used where `userId` was expected, or the reverse | Cascade on `keycloakId` |
| Stale green test summary | Surefire reports linger for classes not selected by a `-Dtest` filter | Check the file timestamps in `target/surefire-reports/` |
| Keycloak container unhealthy at startup | The realm is still importing | Wait — the health check has a 30s start period and 20 retries |
| Adminer cannot reach the database | `localhost` was used as the server | Use the compose service name, `db_dictionary` or `db_user` |

## Diagnostic order

1. `docker compose ps` — is everything healthy?
2. `docker compose logs -f <service>` — Keycloak and RabbitMQ report their own problems clearly.
3. Is the failing thing authentication (401/403), routing (404), or the application (500)? The
   status code narrows it fast, and 403-versus-404 is meaningful here rather than incidental.
4. For anything event-shaped, check the Management UI before reading code: the message is either
   unrouted, sitting in a queue, or in the dead-letter queue, and each points somewhere different.

## Things that look broken and are not

- **404 on another user's resource** — an ownership rule, so ids cannot be enumerated.
- **404 on `/actuator/env`** — exposure is deliberately limited to `health,info`.
- **A `keycloak-bootstrap` container in `Exited (0)`** — it is a one-shot job; that is success.
- **No message consumed for `dictionary.visibility.*` or `word.created`** — no consumer exists yet,
  and a topic exchange discards a message with no bound queue.
- **A WARN when deleting a profile locally** — `KEYCLOAK_ADMIN_CLIENT_SECRET` is unset, so the
  Keycloak identity is deliberately left alone.
