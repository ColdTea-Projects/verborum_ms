# Verborum Reading Tutorials — Suggestion 1: small open LLM model + DB with inverted index

**Status:** Design proposal. Not yet a roadmap item.
**Scope:** A backend feature that gives a learner short reading texts (a couple of paragraphs)
built largely from the vocabulary they already have, so the same words recur across texts for
reinforcement.
**Grounding:** This feature is not yet in `roadmap.md` or the rest of the knowledge base. The one
pre-existing dependency it relies on — the per-word `level` (mastery) field — already exists
(`roadmap.md` P0-19). The remainder is a design to be ratified and then built.

---

## 1. Purpose

Verborum is a vocabulary app, but its reason for existing is to help people learn languages.
Reading practice built from a learner's own saved words is a high-value study aid: it recycles
known vocabulary in fresh context and introduces a small, controlled number of new words.

The design goal is to make **serving cheap and generation rare**, so the feature remains viable for
thousands to tens of thousands of users on a no-revenue, open-source project.

## 2. Core principle — retrieval-first

The feature is built as a **retrieval** system with generation as a backing store filler.

- Every stored text is tagged with the exact set of word lemmas it contains.
- For a given user, the system **searches the corpus** for the text whose word set best fits that
  user's vocabulary and level, and serves it.
- Generation runs only when no suitable text exists yet. Each generated text is stored back into
  the corpus, immediately available to every future user.

This creates a flywheel: as the corpus grows, the hit rate rises and generation frequency (the only
real cost) falls toward zero. Cost scales with how completely the corpus covers the *common*
vocabulary space — which is finite and heavily shared between learners — rather than with the number
of users.

### 2.1 Why this fits language learning

- **Shared vocabulary.** Learners at a similar stage have highly overlapping vocabularies because
  word frequency dominates acquisition (the high-frequency words are learned first). A text built
  from one user's words is therefore reusable by many others.
- **Convergence.** Reading a text teaches words; the learner saves them and starts using them; their
  vocabulary drifts toward what the corpus already contains; their match rate climbs. Every text
  served pulls a user toward the corpus's centre of mass, so generation is concentrated in the
  bootstrap phase rather than being a permanent per-user cost.

## 3. Architecture — the tiered cascade

Requests resolve through the cheapest tier that can satisfy them.

| Tier | Name | When it runs | Cost |
|---|---|---|---|
| 0 | **Batch pre-warming** | Offline background job, continuous | Low (batched, no latency pressure) |
| 1 | **Curated cold-start** | User has too little vocabulary signal to match (proxy: < ~50 saved words) | Near-zero (serve from seed corpus) |
| 2 | **Corpus retrieval** | User has enough vocabulary to find a good match | Near-zero (DB query) |
| 3 | **On-demand generation** | No good match exists | One LLM call, then stored |

### Tier 0 — Batch pre-warming

A background job proactively generates texts for common vocabulary/level bands, independent of any
live request. This is where the bulk of generation happens: batch inference is cheaper, has no
latency requirement, and fills the common ground before real users reach it. Tier 0 covering the
mainstream is what keeps live on-demand generation confined to the genuine long tail.

Generation is **front-loaded**: heaviest while the corpus is still filling, tapering as coverage
grows. It also supplies **variety** — once a user has read their available good matches, fresh
batch-generated material keeps the most engaged users (who read the most) supplied with new texts.

### Tier 1 — Curated cold-start

For a brand-new user with too little vocabulary to match against, serve a level-appropriate text
from a small **curated / pre-generated starter corpus** of high-frequency beginner texts.

- The `< 50 words` value is a tunable **proxy** for the real question — *"is there enough signal to
  find a good match?"* Keep it configurable.
- Beginner texts are naturally frequency-dense, so this seed set graduates users into Tier 2
  quickly and is what makes Tier 2 effective for everyone during the app's early life.

### Tier 2 — Corpus retrieval

Search the corpus for a same-language, level-appropriate text ranked by fit (see §5) and serve the
best match. This is the steady-state common path once the corpus is warm.

### Tier 3 — On-demand generation

When no good match exists, generate a text prompted with the user's word list, store it with its
computed lemma set (see §4), and serve it. It becomes part of the corpus for all future users.

## 4. Generation pipeline

```
request
  → lemmatize the user's target-language vocabulary
  → query the index for same-language, level-appropriate texts, ranked by fit (§5)
  → good match?
        yes → serve
        no  → generate a text prompted with the user's word list
            → compute the actual lemma set of the generated output
            → store text + actual lemma set + difficulty estimate
            → serve
```

**The stored lemma set is computed from the generated output, not from the intended word list.**
Models do not reliably confine themselves to a supplied vocabulary, so the ground-truth tags used
for all future retrieval must reflect the words the text actually contains. If a generated text
carries more out-of-vocabulary words than desired, regenerate or let overlap-matching (§5) place it
with the users it suits.

## 5. Ranking — comprehensible input

The ranking budgets a small, controlled number of unknown words per text: mostly words the user
knows, plus a few new ones. This is the mechanism by which reading both reinforces known vocabulary
and teaches new vocabulary, and it is what drives the convergence described in §2.1.

For a candidate text and a user vocabulary:

- **maximize** `|text_lemmas ∩ user_vocab|` — reinforcement (the recurrence the feature exists for),
- **keep within budget** `|text_lemmas \ user_vocab|` — the new-word allowance (e.g. ≤ 2–4 new
  words),
- **target** the user's new or weak words where possible.

Enhancements:
- **Mastery targeting via `level`.** The per-word `level` field (P0-19) lets retrieval prefer texts
  that reinforce a user's *low-mastery* words, making the feature double as spaced repetition.
- **Variety tracking.** Record which texts each user has seen and rotate, so the system supplies
  fresh material rather than repeatedly serving the same top match.

**The definition of "good match" — the minimum overlap plus the maximum new-word count — is the
central tunable of the whole design.** Both cost and pedagogical quality are governed by that
threshold.

## 6. Datastore — inverted index

Texts are matched by the vocabulary they share with a user, so the corpus is stored as an **inverted
index** (word → texts containing it), with results ranked by overlap count.

- **Implementation:** a Postgres `text_lemmas` array column with a **GIN index** supports
  array-overlap queries and overlap-count ranking directly, reusing the datastore Verborum already
  runs.
- **Scale path:** at larger corpus sizes and query volumes, a dedicated search index
  (OpenSearch / Elasticsearch) provides the same overlap ranking at higher throughput.

The text body itself can be stored in any low-cost store; the index over lemma sets is the component
that carries the feature.

## 7. Supporting components

- **Lemmatizer (required).** Used both to tag stored texts and to normalize user vocabulary for
  matching. A per-language CPU lemmatizer (e.g. spaCy / simplemma / stanza) is sufficient — cheap,
  no LLM. Quality is strongest for major languages and lower for low-resource ones, which suits a
  German-first rollout with more languages phased in later.
- **Text-difficulty estimator.** Provides the per-text difficulty band used for level matching in
  Tiers 1 and 2. A CEFR-style estimate derived from the text's word-frequency profile is enough —
  cheap, no LLM.

## 8. Model and hosting

Generation uses a **hosted open-model API**, keeping the feature free of dedicated inference
hardware while giving broad language coverage.

Use a **single strong multilingual model as the default**, and escalate the harder languages to a
larger model tier where output quality needs more capacity — two tiers in total. The default model
must carry a commercial-friendly license.

| Role | Recommended | License |
|---|---|---|
| Default multilingual model | **Qwen3** | Apache 2.0 |
| Alternative / escalation tier | **Gemma 3** (or a larger Qwen3) | Gemma license (commercial-usable) |

Free API tiers (e.g. Groq, Google Gemini API within quota) are suitable for bootstrapping early
users, moving to a paid open-model host for production.

*(The model landscape and pricing move quickly; re-verify current options and rates before
implementation. Figures in §9 reflect mid-2026 pricing.)*

## 9. Cost model

Generation is the only spend, and it is rare after warm-up.

- A single generation is ~700 tokens ≈ **~$0.0003** on a small open model.
- At 10,000 users reading a few times a day (~900k reads/month), a 5% miss rate is **~$13/month**;
  Tier 0 warming drives misses — and therefore cost — down further, into single digits.
- The one-time corpus build dominates total cost and is a rounding error.

Cost is highest for users with unusual vocabularies (fewer matches → more generation) and lowest for
mainstream learners; the convergence dynamic (§2.1) reduces this over time.

## 10. Fit with the Verborum backend

- A **new backend service** that reads a user's target-language vocabulary from `ms_dictionary` (the
  words on the relevant side of their dictionaries) and uses the `level` field for mastery
  targeting.
- Sits **behind the gateway (Phase 5)** and is **JWT-secured (Phase 3)** like every other service,
  inheriting the single-origin and auth contracts with no new client-side auth work.
- Corpus storage begins in the existing Postgres deployment (§6), so it adds no new infrastructure
  at launch.
- Realistic sequencing: **after Phase 3 and Phase 5**, alongside or after the V2 `ms_autofil` work.

## 11. Open decisions

1. **Pre-warming vs. on-demand balance** — how much to lean on Tier 0 batch generation versus live
   Tier 3 calls.
2. **The "good match" threshold** (§5) — minimum overlap and maximum new-word budget. The central
   quality/cost dial.
3. **Cold-start threshold** — the `~50 words` proxy value and how "enough signal" is detected.
4. **Datastore scale trigger** — when to graduate from Postgres GIN to a dedicated search index.
5. **Model and host choice** — default multilingual model, escalation tier, and provider.
6. **Language coverage rollout** — lemmatizer and generation quality per language beyond the initial
   German focus.

## 12. Next steps for the roadmap

- Add a roadmap entry (with task IDs) for this feature, sequenced after Phase 3 and Phase 5.
- Produce a dedicated design doc covering the corpus schema (text + lemma set + difficulty),
  the ranking function and its thresholds, the generation prompt/level contract, the lemmatizer and
  difficulty-estimator integration, and the model/host selection.
- The per-word `level` field (P0-19) is the existing dependency to build the mastery-targeting on;
  everything else in this note is new work.
