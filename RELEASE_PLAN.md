# Verborum — Release Plan

*Prepared 2026-10-05 for the Verborum team. The backend state is `verborum_ms` @ `ccc972d`; the apps are
Android (`verborum_android`), with iOS and web (`verborum_kmp`) to follow. Prices were checked in October 2026
and exclude VAT. Re-check them before you buy. Sources are listed at the end.*

---

## 1. The short answer

| Question | Answer for Verborum |
|---|---|
| Where to host | **One Hetzner Cloud server in Germany** (CX33: 4 vCPU, 8 GB RAM, 80 GB SSD) for production, plus an optional small one for staging |
| What it costs | **About €14–20 a month** for everything at launch scale, plus **$25 once** for the Google Play account. Apple adds $99 a year when iOS ships |
| How it runs | Docker Compose on the server: Caddy (HTTPS) → the three Spring services + Keycloak → one PostgreSQL + RabbitMQ |
| Environments | **dev** (laptops, as today), **staging** (a copy of production for testing app builds), **prod** |
| Biggest blockers | Production setup for the backend (Dockerfiles, prod Keycloak, SEC-03/SEC-11, backups); **a "report" feature for the Forum** (store rule for user-generated content); legal pages; Android release hardening; Google's **12 testers × 14 days** closed test |
| Realistic timeline | **About 4–6 weeks** of part-time work, most of it on the checklist in §6, not on servers |

---

## 2. What we are deploying

The production server runs what you already run locally, minus the development tools:

```
                  Internet (443 only)
                         │
                  ┌──────┴──────┐
                  │    Caddy    │  automatic HTTPS (Let's Encrypt), the only open port
                  └──┬───────┬──┘
     api.<domain>    │       │   auth.<domain>
   ┌─────────────────┤       └────────────┐
   │ path routing     │                    │
   ▼                  ▼                    ▼
ms_dictionary   ms_user   ms_marketplace   Keycloak 26 (custom image + email-code SPI)
   │                  │        │           │
   └──────── RabbitMQ ┴────────┘           │
   └──────── PostgreSQL 14: vdbdictionary, vdbprofile, vdbmarket, keycloak ───┘
```

**Not deployed:** Adminer, Mailpit, `verborum-dev-cli`, the test users, Swagger (`SWAGGER_ENABLED=false`).

### Decision: do you need the API gateway (Phase 5) before launch?

> **Superseded 2026-10-19:** the gateway was built anyway (roadmap Phase 5, decided 2026-10-15, done
> 2026-10-17): `ms_gateway` on 8080 behind Caddy, which does TLS and the auth rate limit. The comparison
> below is kept as the reasoning of 2026-10-05.

The roadmap plans a Spring Cloud Gateway (`ms_gateway`). For launch you can skip it, because the three
services' paths don't overlap: `/dictionaries`, `/words` → ms_dictionary; `/users` → ms_user;
`/marketplace` → ms_marketplace. Caddy can route by path:

```
api.verborum.app {
    handle /dictionaries* { reverse_proxy ms_dictionary:8085 }
    handle /words*        { reverse_proxy ms_dictionary:8085 }
    handle /users*        { reverse_proxy ms_user:8086 }
    handle /marketplace*  { reverse_proxy ms_marketplace:8087 }
}
auth.verborum.app { reverse_proxy keycloak:8080 }
```

| | Caddy path routing (recommended for v1) | Spring Cloud Gateway (Phase 5 as planned) |
|---|---|---|
| Memory | ~30 MB | +400–600 MB (a fourth JVM) |
| Work | an hour | a new service, tests and CI |
| JWT check at the edge | no; each service validates tokens already (SEC-05) | yes, though it duplicates what the services do |
| Rate limiting | needs a Caddy plugin build, or nginx `limit_req` | built in |
| CORS (for the web app) | configurable in Caddy | built in |

The apps keep one base URL (`https://api.<domain>`) either way, so moving to a gateway later is invisible
to them.

### Decision: the domain

You need to **buy a domain**, because you don't own `coldtea.de`. One domain is enough: the subdomains
`api.`, `auth.`, `app.` (web), `staging-api.` and `staging-auth.` are free once you have it. The examples in
this plan use `verborum.app`. Prices at Cloudflare Registrar, which sells at cost with no renewal mark-up:

| Domain | ≈ per year | Notes |
|---|---|---|
| `verborum.app` | **$14.18** | Fits an app. The `.app` ending is HTTPS-only (browsers enforce it), which suits an API and login server |
| `verborum.com` | $10.46 | Classic, if it is still free (check availability first) |
| `verborum.de` | ~€5–15 | Cloudflare doesn't sell `.de`; use a German registrar (e.g. INWX, Strato) |

Buy it **in week 1, before** creating the Google/Facebook login apps, the email sending setup (SPF/DKIM) and
the store listing, because all three need the domain. DNS can stay at the registrar or move to Cloudflare
(free).

**One code change in the KMP app:** the iOS release config (`ApiConfig.ios.kt` in `core/network`) hard-codes
`https://api.verborum.coldtea.de`, a domain you don't own. Replace it with the real one before the first iOS
build. Android only has LAN addresses so far and gets its production URLs in the cutover step (§6, Phase D).

---

## 3. Hosting: options and prices (October 2026)

Hetzner raised prices twice in 2026 (April, and 15 June for new orders). The figures below are after
those increases.

### Hetzner Cloud: recommended

| Plan | vCPU / RAM / disk | Traffic | €/month (excl. VAT) | Fit for Verborum |
|---|---|---|---|---|
| CX23 | 2 / 4 GB / 40 GB | 20 TB | **5.49** | staging, or a very tight production |
| **CX33** | **4 / 8 GB / 80 GB** | 20 TB | **8.49** | **production at launch: comfortable** |
| CX43 | 8 / 16 GB / 160 GB | 20 TB | 15.99 | later: thousands of active users, or prod + staging together |
| CAX21 (Arm) | 4 / 8 GB / 80 GB | 20 TB | 10.49 | fallback if CX is sold out (needs arm64 images, see below) |

- **Memory budget on CX33 (8 GB):** 3 Spring services × ~500 MB, Keycloak ~700 MB, PostgreSQL ~300 MB,
  RabbitMQ ~200 MB, and Caddy and the OS ~500 MB, about **3.5 GB in total**. That leaves room to grow,
  even to run staging on the same machine if you choose to. Set memory limits per container and
  `-XX:MaxRAMPercentage`.
- **Extras:** a public IPv4 may be billed separately (about €0.50/month). Automatic server backups are
  an add-on, about 20% of the server price (≈ €1.70 on CX33); check the current rate in the console.
- **Why Hetzner for Verborum:** EU data centres (Germany/Finland), which suits users in the EU and GDPR;
  traffic is effectively free; and it is about 5× cheaper than the US clouds for the same machine.
- **Arm (CAX) caveat:** the images must be built for `linux/arm64`. Temurin, PostgreSQL, RabbitMQ and
  Keycloak all publish arm64 images, so this is one `docker buildx` flag in CI, but decide before the
  first build.

### For comparison

| Option | Same setup costs | Verdict |
|---|---|---|
| DigitalOcean Droplet 4 vCPU / 8 GB | **$48/month** | Simpler dashboard, 5× the price |
| DigitalOcean managed PostgreSQL (8 GB) | **$122/month** on top | Managed backups and failover. Not worth it at launch; reconsider at real scale |
| AWS / GCP / Azure | $60–150+/month | Overkill; complex billing |

**Recommendation:** Hetzner CX33 for production. Run your own PostgreSQL in Docker, with nightly dumps
to an off-site backup (§4).

---

## 4. Full cost estimate

### Monthly (launch scale, roughly 0–5,000 users)

| Item | Choice | €/month |
|---|---|---|
| Production server | Hetzner CX33 | 8.49 |
| Server snapshots | Hetzner automated backups (~20%) | ~1.70 |
| Off-site database backups | Hetzner Storage Box BX11, 1 TB (or Backblaze B2, $6.95/TB) | 3.20 |
| Staging server | Hetzner CX23, **optional**; can run only during release testing (hourly billing) | 0–5.49 |
| Domain | e.g. `verborum.app` at $14.18 a year, shown here per month (subdomains free) | ~1.20 |
| Transactional email (verification and login codes) | Amazon SES $0.10 per 1,000, or Brevo free (300/day) | ~0 |
| CI/CD and image registry | GitHub Actions + GitHub Container Registry, free tier | 0 |
| Uptime monitoring | UptimeRobot / Better Stack, free tier | 0 |
| Crash reporting (Android) | Firebase Crashlytics, free | 0 |
| **Total** | | **≈ €14.60 – €20.10** (+ VAT where it applies) |

### One-time and yearly

| Item | Cost | When |
|---|---|---|
| Google Play developer account | **$25 once** | before the closed test |
| Domain (e.g. `verborum.app`) | **~$14 a year** (`.com` ~$10.50, `.de` ~€5–15) | week 1, before the OAuth apps, email setup and store listing |
| Apple Developer Program | **$99 a year** | only when the iOS app ships |
| Legal texts (privacy policy, terms, imprint) | €0 (generators, self-written) to a few hundred € (lawyer review) | before launch |

**When to scale up:** move to CX43 (€15.99) when the server's RAM stays above ~75% or responses slow
down; it takes a few minutes of downtime. Consider managed PostgreSQL only when downtime or data loss
would really hurt, and plan on roughly €50–120/month for it.

---

## 5. Environments

| | dev | staging | prod |
|---|---|---|---|
| Where | each laptop (today's setup) | small Hetzner server | Hetzner CX33 |
| Purpose | coding | **test an app release candidate against a copy of production** | real users |
| Backend | `mvnw spring-boot:run` + `docker-compose.yml` | Docker images, `docker-compose.prod.yml` | the same images and compose |
| Images | built locally | CI tag = commit SHA | CI tag = version `vX.Y.Z` |
| API | `http://localhost:808x` | `https://staging-api.verborum.<domain>` | `https://api.verborum.<domain>` |
| Keycloak | `http://localhost:8180`, H2 dev DB | `https://staging-auth…`, Postgres, **own realm and secrets** | `https://auth…`, Postgres, **own realm and secrets** |
| Google/Facebook login | the dev OAuth apps | **separate** OAuth clients | **separate** OAuth clients |
| Email | Mailpit | real provider, sandbox or low volume | real provider |
| Data | the seed script | the seed script, resettable | real, backed up |
| Android build | `debug` | a `staging` build type → Play internal testing | `release` → Play production |

**Why staging matters for Verborum:** a released Android build can't be hotfixed. Users stay on a version for
weeks. Every app release candidate should run against staging before it reaches users. Staging can be a
CX23 that you create from a snapshot when you test and delete afterwards; hourly billing makes that cents.

**Configuration rule (already in place):** every value is `${VAR:local-default}`, so environments differ only
in their `.env` files. Production secrets live only on the server (or in GitHub Actions secrets), never in git.

---

## 6. The release checklist, in order

### Phase A: make the backend production-ready (≈ 1–2 weeks)

1. **Dockerfiles** for the three services: multi-stage, Temurin 17 JRE, non-root user, memory limits.
   `docs/ops/dockerization-and-environments.md` §5 already has the plan.
2. **`docker-compose.prod.yml`**: Caddy, the services, Keycloak in production mode
   (`start --optimized`, PostgreSQL instead of H2, `KC_PROXY_HEADERS=xforwarded`), one PostgreSQL holding
   four databases with a separate user each (§12 of the ops doc), and RabbitMQ. Only Caddy publishes ports.
3. **Close the last two security items:**
   - **SEC-03:** one RabbitMQ user per service, with topic permissions.
   - **SEC-11:** the production realm. `sslRequired: external`; only the app scheme and the HTTPS web
     origin as redirect URIs; no test users and no `verborum-dev-cli`; events enabled; a stronger
     password policy.
4. **Rotate every secret (BL-03):** the Keycloak admin password, the `verborum-backend` client secret,
   and **new** Google/Facebook OAuth secrets. The old ones were shared in chat. Production has its own
   OAuth apps, with redirect URIs `https://auth.<domain>/realms/verborum/broker/{google|facebook}/endpoint`.
5. **Real email:** an SES or Brevo account; set SPF, DKIM and DMARC on the domain. Without them,
   verification and login-code emails land in spam, and login breaks for new users.
6. **CI (GitHub Actions):** on every push run `mvnw verify` and the osv-scanner check (the `maven` skill),
   then build and push images to GHCR tagged with the commit SHA. A version tag creates the production
   images.
7. **Backups:** a nightly `pg_dump` of all four databases to the Storage Box, kept 7 days plus 4 weekly.
   **Rehearse one restore before launch**; a backup that has never been restored doesn't count.
8. ~~**The rest of Phase 5**~~ — done 2026-10-17: `ms_gateway` behind Caddy, auth rate limit in Caddy (P5-04).

### Phase B: build the servers (≈ 2–3 days)

1. Create the Hetzner project and the CX33 (Ubuntu 24.04 LTS). Add a **Hetzner Cloud Firewall** that
   allows only 22 (SSH, your IPs only), 80 and 443.
2. Harden the server: SSH keys only, no root login, unattended security upgrades, Docker log rotation.
3. DNS: `api.`, `auth.`, `staging-api.` and `staging-auth.` (later `app.` for the web client) → the
   server IPs.
4. Deploy **staging first**: `docker compose pull && up -d`, run the seed script, run the Android
   `staging` build against it, then run the security probes from the 2026-10-05 audit against it.
5. Deploy production the same way, with no seed data.
6. Monitoring: an uptime check on `/actuator/health` (exposed through Caddy only for health), a disk-space
   alert, and an alert when the RabbitMQ dead-letter queue grows.

### Phase C: product, legal and store requirements (≈ 1 week, can run alongside A and B)

These are easy to underestimate, and **the stores reject apps without them**:

1. **A "report" feature for the Forum (new backend work).** Public dictionaries are user-generated
   content. Google Play's UGC policy and Apple's guideline 1.2 require a way to **report** content in the
   app, and in practice to **block or remove** it. Minimum version: an endpoint to report a listing with
   a reason, a way for the admin to hide a listing or ban a user, and a contact email. Display names are
   also user-generated; the reserved-word filter (SEC-10) helps but doesn't replace reporting.
2. **Privacy policy:** what is stored (email, display name, dictionaries, learning progress), where
   (Hetzner, Germany), which processors (Hetzner, the email provider, Google/Facebook login, Crashlytics),
   how long it is kept, and how to delete it. It needs a public URL, for the store listing and the
   Data Safety form.
3. **Terms of use and the Forum terms:** the backend already records `marketplaceAgreementVersion`,
   so publish the text at a stable URL and use its date as the version string.
4. **Imprint (Impressum):** if you operate from Germany, a public contact and imprint page is expected.
5. **Account deletion:** Play requires both an in-app path (the backend supports it, with a fresh login
   since SEC-13) **and a public web page** where users can request deletion. A simple page with
   instructions and a contact form or email is enough.
6. **Data processing agreements:** accept Hetzner's and the email provider's DPAs in their consoles.
7. **Content rating questionnaire** and the **Data Safety form** in Play Console.
8. **Store listing:** title, short and long description, icon, feature graphic and screenshots. The app
   ships **19 UI languages**, so translate at least the descriptions for the main markets (DE, TR, EN,
   ES, FR, …).

### Phase D: release the Android app (≈ 3+ weeks, most of it waiting)

1. **Code changes** from `verborum_android/docs/production-cutover.md` and the client findings (A-S1…A-S4):
   - HTTPS base URLs for `staging` and `release`
   - cleartext only in debug builds
   - remove the insecure connection builder from release builds
   - strip the token from HTTP logs
   - R8 on, and a version bump
2. **Signing:** use **Play App Signing**. Keep the upload key in two safe places; losing it is painful.
3. **Google Play account:** **personal accounts created after 13 Nov 2023 must run a closed test with at
   least 12 testers, opted in for 14 continuous days, before production access.** Plan 3–4 weeks for this
   and recruit testers early: friends, language-learning groups. An **organization account** is exempt but
   needs a registered legal entity and a D-U-N-S number.
4. Release tracks: **internal testing** (you two, against staging) → **closed testing** (12+ testers,
   14 days, against prod or staging) → **production** with a **staged rollout** of 10% → 50% → 100%,
   watching crashes and the backend between steps.
5. After launch: watch Android vitals and Crashlytics, and keep the next app version's API changes
   backward compatible. Old app versions stay in use for weeks.

### Phase E: later releases

- **Web (KMP Wasm):** static files served by Caddy at `app.<domain>`. Needs CORS and the Keycloak web
  origin. Fix K-S1 first: no `offline_access` on web, plus a strict Content-Security-Policy header.
- **iOS:** Apple Developer Program ($99/year), TestFlight, App Store review. Apple requires in-app account
  deletion and UGC reporting too, and **Sign in with Apple** if you offer Google/Facebook login (the realm
  decision dropped Apple; revisit that before iOS ships).

---

## 7. What is generally done during a release

| Step | What it means for Verborum |
|---|---|
| Freeze | No new features once the release candidate is chosen, only fixes |
| Tag | `git tag v1.0.0` in `verborum_ms`. CI builds `…:v1.0.0` images, and the Android `versionName`/`versionCode` matches |
| Migrate | Liquibase runs on service start. Keep migrations **additive** (new tables or columns) so rolling back an image is safe |
| Deploy backend first | The backend must work with both the old and the new app version. Deploy it, smoke-test it, then release the app |
| Smoke test | Login (password, email code, Google), create a dictionary and words, sync, Join Forum, browse, import, rate, delete account |
| Staged app rollout | 10% → 50% → 100% over a few days |
| Watch | Uptime, error logs, dead-letter queue, Crashlytics, store reviews |
| Rollback plan | Backend: `docker compose up -d` with the previous image tag. App: halt the staged rollout. Data: restore from the nightly dump (rehearsed) |
| Release notes | A short "what's new" for the store, and a changelog entry |

---

## 8. Suggested timeline

| Week | Backend (verborum_ms) | Android | Product/legal |
|---|---|---|---|
| 1 | Dockerfiles, prod compose, SEC-03, SEC-11 | Forum on the real API (profile/Join, browse, import, ratings) | **Buy the domain**; privacy policy, terms, imprint drafts |
| 2 | CI, backups + restore drill, report feature, staging server | Production cutover changes, `staging` build type | Play account ($25), store listing, Data Safety form |
| 3 | Production server, monitoring, secret rotation | Internal test → **start closed test (12 testers)** | Account deletion web page |
| 4–5 | Fixes from testers | Fixes from testers | — |
| 5–6 | — | **Production, staged rollout** | Launch |

Recruit the 12 testers in week 1–2. The 14 days only start once 12 are opted in.

---

## 9. Open decisions for you two

1. ~~**Caddy path routing for v1, or build the gateway (Phase 5) first?**~~ **Done: own gateway `ms_gateway` behind Caddy** (roadmap Phase 5, built 2026-10-17)
2. **Domain:** which one to buy: `verborum.app`, `.com` or `.de`? Check availability, buy it in week 1, and replace the `coldtea.de` URL in the KMP iOS config.
3. **Play account type:** personal (12 × 14 closed test) or organization (needs a company and D-U-N-S)?
4. **Staging:** an always-on CX23 (€5.49/month), or created only for release testing?
5. **Email provider:** Amazon SES (cheapest, more setup) or Brevo (free tier, simpler)?
6. ~~**Report and moderation scope for v1:** just "report + admin hides it", or more?~~ **Decided 2026-10-19:
   report + take-down with a stated reason to the owner** (roadmap `P5L-05`, DSA Art. 16, 17)
7. **Who is on call** for production, and how will you be alerted (email, Telegram, …)?

---

## Sources

- [Hetzner price adjustment, 15 June 2026 (official)](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/)
- [Hetzner Cloud VPS pricing calculator (October 2026)](https://costgoat.com/pricing/hetzner)
- [Hetzner cloud price increases in 2026: breakdown](https://northflank.com/blog/hetzner-cloud-server-price-increases)
- [Hetzner Storage Box BX11 price](https://www.whtop.com/plans/hetzner.com/128269)
- [Backblaze B2 pricing 2026](https://tech-insider.org/backblaze-b2-vs-wasabi-vs-s3-2026/)
- [Cloudflare Registrar domain prices (October 2026)](https://tld-list.com/registrars/cloudflare)
- [Cloudflare: buy .app domains](https://www.cloudflare.com/application-services/products/registrar/buy-app-domains/)
- [Cloudflare vs GoDaddy domain pricing (.com)](https://www.snazzy.solutions/blog/hosting/cloudflare-vs-godaddy-domain-pricing)
- [DigitalOcean droplet pricing](https://www.digitalocean.com/pricing/droplets)
- [DigitalOcean managed PostgreSQL pricing](https://docs.digitalocean.com/products/databases/postgresql/details/pricing/)
- [Transactional email services compared (2026)](https://mailtrap.io/blog/transactional-email-services/)
- [Amazon SES vs Postmark 2026](https://blogs.blueyemail.com/amazon-ses-vs-postmark/)
- [Play Console: testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Play Console: app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [Google Play Data Safety form guide 2026](https://stora.sh/blog/2026-05-20-google-play-data-safety-form-2026-guide)

*Not re-verified in this research: the Google Play $25 and Apple $99/year fees (long-standing; check at
sign-up), Hetzner's backup percentage and IPv4 charge (check in the console), and the Google Play UGC and
Apple guideline 1.2 details (read the current policy pages before submitting).*

*Delete this file once it is converted to PDF; it is not committed.*
