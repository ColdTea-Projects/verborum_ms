# The Seven Rules

Standing conventions for anything event-driven. Each exists because of a specific way this system
can go wrong. Read them before designing an event, not after.

## 1. Publish after commit, never inside the transaction

A send inside a transaction can be followed by a rollback, and then you have announced something
that never happened — a *phantom event*. Publishing after commit can instead lose an event if the
process dies in the gap.

These are not equally bad. A phantom `user.deleted` destroys live data in another service and
cannot be undone, while a lost one leaves orphaned rows that a re-publish or a reconciliation sweep
can clean up. **Always prefer the recoverable failure.**

## 2. An event carries what the consumer needs

A consumer should never have to call back into the publisher to act on an event.
`DictionaryVisibilityEvent` carries the full listing payload for exactly this reason. Callbacks
reintroduce runtime coupling, they race with the publisher's own transaction, and they turn a
broker outage into a cascade.

## 3. Every consumer is idempotent

Delivery is at-least-once. A redelivery must be a no-op, not a duplicate row or a double increment.
The established trick is a UNIQUE constraint plus a find-or-create service method — one code path
then serves both the HTTP caller and the listener (`VaultService.addVaultEntry`,
`DictionaryTagService.addTag`).

An empty result short-circuiting also counts: the `user.deleted` cascade finds nothing on a
redelivery and returns without writing.

## 4. A projection-updating event carries a version or timestamp, and the consumer drops stale ones

Messages can arrive out of order. Two quick renames delivered in reverse leave the projection
holding the older name, permanently, with nothing to signal it. Carry the entity's `updatedAt` and
have the consumer ignore anything not newer than what it already holds.

`DictionaryVisibilityEvent` carries `updatedAt` for this.

## 5. Denormalize for read models; do not reach across services at request time

If a service must filter, sort or paginate on a field, it has to store that field. Fetching it from
the owning service per request means you cannot page in the database, you inherit that service's
latency and downtime, and you hit the ownership filter, which returns nothing to a service account.
A marketplace listing is a read model; treat it as one.

## 6. A reconciliation job is not optional once you have projections

Rule 1 admits a small window where an event can be lost, and rule 4 admits a stale projection if an
event is missed entirely. A periodic re-sync is the backstop for both, and it is the thing you will
want during an incident. Write it with the projection, not after the first drift is reported.

## 7. External, non-transactional calls belong after commit too — best-effort, with a loud log

Keycloak, email, payments. They cannot be rolled back, so doing them inside a transaction has the
same phantom problem as rule 1 — worse, in Keycloak's case, because a deleted identity cannot be
recreated with the same subject. Do them after commit; on failure log at ERROR with the id, because
that log line is the only record that a manual cleanup is owed.

## The corollary: events fire on change only

`saveDictionary()` and `saveWords()` each back both POST and PUT, so both compare against stored
state first. Visibility events fire only when `is_public` actually flips, and `word.created` only
for word ids that did not already exist.

Without this, a rename would give ms_marketplace a duplicate listing and an edit would make
ms_autofil double-count a translation. The trade-off is that plain edits are invisible to
consumers — a renamed public dictionary sends no event, so a marketplace listing's name can go
stale. That gap is recorded against the relevant roadmap tasks.

## Identity on the wire

`user.deleted` carries **both** `userId` and `keycloakId`; `dictionary.imported` carries
`{dictionaryId, keycloakId, eventTimestamp}`.

ms_dictionary and ms_marketplace store the JWT subject in `fk_user_id`, and that value is ms_user's
`keycloak_id`, not its `user_id`. A consumer cascading on `userId` deletes nothing and reports
success. `userId` is carried for correlation only.
