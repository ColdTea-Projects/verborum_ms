# Custom Caddy = stock Caddy + the rate-limit module (P5-04). The module is not in the stock image, so xcaddy
# builds it in. Rate limiting is Caddy's only job beyond TLS (roadmap Phase 5): no Redis, no API limits.
# Both versions are pinned. The module has one old tag (v0.1.0), so a commit is pinned instead — bump it on purpose.
#   caddy 2.11.7 · mholt/caddy-ratelimit commit 5625512f24f6 (2026-06-12)

# ---- Stage 1: build Caddy with the module ------------------------------------
FROM caddy:2.11.7-builder AS builder
RUN xcaddy build v2.11.7 --with github.com/mholt/caddy-ratelimit@5625512f24f6f59d6f64fb3aafe5eecff0b286db

# ---- Stage 2: the stock runtime image with our binary ------------------------
FROM caddy:2.11.7-alpine
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
