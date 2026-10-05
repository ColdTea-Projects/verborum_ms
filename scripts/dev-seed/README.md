# Dev seed — dummy users, dictionaries and words

Fills the **local** stack with realistic test data for the Library and the Forum (marketplace), and
removes it again. Everything goes through the real APIs — Keycloak's admin API, then ms_user,
ms_dictionary and ms_marketplace as each user — so all services' events fire and every database ends
up consistent: accounts, profiles, dictionaries, words, tags, Forum membership, listings, publishers,
imports and vaults.

```bash
python3 scripts/dev-seed/seed.py     # create (refuses if the seed users already exist)
python3 scripts/dev-seed/reset.py    # remove everything the seed created — nothing else
```

**Needs:** the docker-compose stack (Keycloak, the three Postgres databases, RabbitMQ) and ms_user,
ms_dictionary and ms_marketplace running on their usual ports with their RabbitMQ listeners on. Python
3.9+, no packages. `seed.py` checks the services and the RabbitMQ queues first and stops with a reason
if something is missing.

## What you get

**30 users, password `test1234`** — log in with the username or the email:

| User | Email | Forum | Learns | Dictionaries |
|---|---|---|---|---|
| Anna Bauer | anna.bauer@example.com | member | TR (from DE) | 10 |
| Mehmet Yılmaz | mehmet.yilmaz@example.com | member | DE (from TR) | 12 (2 hidden) |
| Elif Kaya | elif.kaya@example.com | member | EN (from TR) | 8 |
| Lukas Schmidt | lukas.schmidt@example.com | member | EN (from DE) | 25 |
| Sophie Martin | sophie.martin@example.com | member | EN, DE (from FR) | 14 (1 hidden) |
| Carlos García | carlos.garcia@example.com | member | EN (from ES) | 5 |
| Emma Johnson | emma.johnson@example.com | member | ES, FR, DE (from EN) | 18 |
| Zeynep Demir | zeynep.demir@example.com | member | DE (from TR) | 9 |
| Jonas Weber | jonas.weber@example.com | member | EN (from DE) | 11 (3 hidden) |
| Léa Dubois | lea.dubois@example.com | member | DE (from FR) | 7 |
| Ahmet Çelik | ahmet.celik@example.com | member | EN, DE (from TR) | 13 |
| María López | maria.lopez@example.com | member | EN (from ES) | 10 |
| Tom Becker | tom.becker@example.com | member | EN (from DE) | 6 |
| Oliver Brown | oliver.brown@example.com | **not a member** | FR (from EN) | 8 (all private) |
| Nina Koch | nina.koch@example.com | **not a member** | ES (from DE) | 10 (all private) |
| Hannah Fischer | hannah.fischer@example.com | member | EN (from DE) | 9 |
| Can Öztürk | can.ozturk@example.com | member | DE (from TR) | 11 (1 hidden) |
| Selin Arslan | selin.arslan@example.com | member | EN (from TR) | 10 |
| Max Wagner | max.wagner@example.com | member | ES (from DE) | 7 |
| Chloé Bernard | chloe.bernard@example.com | member | EN (from FR) | 12 (1 hidden) |
| Pablo Fernández | pablo.fernandez@example.com | member | DE, EN (from ES) | 15 |
| Olivia Smith | olivia.smith@example.com | member | FR (from EN) | 10 |
| Burak Şahin | burak.sahin@example.com | member | EN (from TR) | 5 |
| Felix Hoffmann | felix.hoffmann@example.com | member | FR, EN (from DE) | 20 (2 hidden) |
| Camille Petit | camille.petit@example.com | member | ES (from FR) | 8 |
| Ece Aydın | ece.aydin@example.com | member | DE, EN, FR (from TR) | 16 |
| Lucía Martínez | lucia.martinez@example.com | member | DE (from ES) | 9 |
| James Wilson | james.wilson@example.com | member | DE (from EN) | 22 |
| Mia Schulz | mia.schulz@example.com | **not a member** | TR (from DE) | 7 (all private) |
| Kerem Doğan | kerem.dogan@example.com | **not a member** | DE (from TR) | 12 (all private) |

26 Forum members and 4 non-members; 339 dictionaries, about 3,000 words. Every dictionary has at least 6 entries; most have 6–16, the
largest 68. Words follow the client word contract: JSON arrays of meanings with articles, and meta
with genders, plurals, feminine forms, verb forms (past, participle, aux, present) and adjective
comparisons — several verbs and adjectives have two meanings. About a third of the dictionaries mix
in sentences; some sentences are 150–400 characters long. Most words carry a `level` (0–5).

Members joined through `PUT /users/me/profile-info`, so the server shared all their dictionaries
(P4-16); six members then hid a few. The four non-members cannot browse the Forum (403) — use them
to test the Join screen.

**"Popular" is led by dictionaries chosen for client test cases:**

| Imports | Dictionary | Owner | Tests |
|---|---|---|---|
| 20 | The Big Vocabulary Collection | Lukas Schmidt | many words (68) |
| 18 | Long Sentences: Reading Practice | Anna Bauer | long inputs, up to ~400 characters |
| 16 | Everything I Need for My First Trip to Spain: Airport, Hotel, Restaurant and Small Talk Phrases | Emma Johnson | very long name, 7 tags, words mixed with sentences |
| 12 | Verbs with Several Meanings | Mehmet Yılmaz | multi-meaning entries with aligned fields |
| 8 | Comparisons and Feminine Forms | Sophie Martin | feminine and comparative fields |

Every member also imported 1–3 other dictionaries, so vaults and the rest of "popular" are not empty.

**Ratings (P4-19..P4-22):** about 80% of imports are followed by a 1–5 star rating from the importer.
Each dictionary has a fixed "quality" its raters roughly agree on, so averages spread from about 2 to 5
and `/marketplace/dictionaries/top-rated` has a clear order. Imports without a rating are left as they
are, so the "rate it" path stays testable. Publishers never rate their own, and non-members rate nothing.

## Reset

`reset.py` only touches the 30 seed usernames. For each one it deletes the profile as that user —
whose `user.deleted` event makes ms_dictionary and ms_marketplace delete everything of theirs — and
then the Keycloak account. `testuser`, `testadmin` and any real account are left alone. Afterwards,
the dead-letter queue (`verborum.dead-letter` in the RabbitMQ UI) should be empty.

## Changing it

- Users, language pairs, dictionary counts, showcases: `seed_data.py`.
- Vocabulary (5 languages: en, de, tr, fr, es): `vocab.py`.
- URLs and credentials: environment variables in `common.py` (`SEED_*`, `KEYCLOAK_ADMIN`,
  `KEYCLOAK_ADMIN_PASSWORD`, `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`); the defaults match the
  local docker-compose stack.
- The plan is deterministic: the same users, dictionaries and words every run (ids are new each time).

**Local only.** It uses the dev-only `verborum-dev-cli` password-grant client and the Keycloak admin
account; neither exists in a shared realm.
