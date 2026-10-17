# edge-smoke

A smoke test for the local gateway and edge (roadmap P5-13). Python 3.9+, standard library only — the same convention as
`scripts/dev-seed`.

The gateway on `:8080` and Keycloak on `:8180`:

```bash
python3 scripts/edge-smoke/smoke.py
```

The same, plus a probe device `PUT`/`DELETE`:

```bash
python3 scripts/edge-smoke/smoke.py --writes
```

Through Caddy, with the local CA exported from the Caddy container:

```bash
python3 scripts/edge-smoke/smoke.py --edge --cacert caddy-local-root.crt --writes --rate-limit
```

Copy the command only, without trailing text: an interactive zsh does not treat `#` as a comment.

It signs in as the dev account `testuser` through the dev-only client `verborum-dev-cli`, so run it only against a local
stack. It leaves nothing behind (the probe device is deleted). The checklist for the apps, and what each flag needs, are in
`docs/ops/local-development.md` §7b.

With `--edge` against a stack whose services still use the plain issuer, the route checks fail with 401: start the gateway and
the services with `KEYCLOAK_ISSUER_URI=https://auth.verborum.localhost/realms/verborum` first (`docs/ops/local-development.md` §7a).
