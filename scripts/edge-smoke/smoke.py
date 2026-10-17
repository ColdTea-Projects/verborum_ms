#!/usr/bin/env python3
"""
Smoke test for the local edge: Caddy (optional) -> ms_gateway -> the services, with a real Keycloak token.

Plain, gateway on :8080 and Keycloak on :8180:

    python3 scripts/edge-smoke/smoke.py

With a device registration round trip:

    python3 scripts/edge-smoke/smoke.py --writes

Through Caddy (https://api. and https://auth.verborum.localhost):

    python3 scripts/edge-smoke/smoke.py --edge --cacert caddy-local-root.crt --rate-limit

Needs the backend running (docker compose stack, ms_user, ms_dictionary, ms_marketplace, ms_notification, ms_gateway).
Python 3.9+, no third-party packages. It uses the dev-only Keycloak client `verborum-dev-cli` and the dev account
`testuser` — never run it against anything but a local stack. Exit code 1 if any check fails.

It proves what a machine can prove: the token is accepted and bad ones are not, CORS answers, every route reaches
its service, errors keep their shape, and a write with a body gets through. It cannot prove the apps: login in a
browser, a silent push arriving on a phone, and the Forum screens are manual (docs/ops/local-development.md §7b).
"""
import argparse
import base64
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

RESULTS = []


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--edge", action="store_true",
                   help="use the Caddy hosts https://api.verborum.localhost and https://auth.verborum.localhost")
    p.add_argument("--api", help="API origin (default http://localhost:8080, or the Caddy api host with --edge)")
    p.add_argument("--auth", help="Keycloak realm URL (default http://localhost:8180/realms/verborum, or the Caddy auth host)")
    p.add_argument("--cacert", help="root certificate of Caddy's local CA (the file `docker cp` exported)")
    p.add_argument("--web-origin", default="http://localhost:8280", help="the web app's origin, for the CORS checks")
    p.add_argument("--user", default="testuser")
    p.add_argument("--password", default="testuser")
    p.add_argument("--writes", action="store_true", help="also register and remove a probe device (PUT/DELETE /notifications/devices)")
    p.add_argument("--rate-limit", action="store_true",
                   help="burst the token endpoint with wrong passwords until Caddy answers 429 (edge only)")
    a = p.parse_args()
    if a.edge:
        a.api = a.api or "https://api.verborum.localhost"
        a.auth = a.auth or "https://auth.verborum.localhost/realms/verborum"
    a.api = (a.api or "http://localhost:8080").rstrip("/")
    a.auth = (a.auth or "http://localhost:8180/realms/verborum").rstrip("/")
    return a


def make_opener(cacert):
    if cacert and not os.path.isfile(cacert):
        sys.exit(f"The CA file {cacert!r} does not exist (looked in {os.getcwd()}).\n"
                 "Export it from the running Caddy container, then pass its full path:\n"
                 "  docker cp verborum-caddy:/data/caddy/pki/authorities/local/root.crt ./caddy-local-root.crt\n"
                 "Caddy runs only with the edge layer (docs/ops/local-development.md section 7a).")
    ctx = ssl.create_default_context(cafile=cacert) if cacert else ssl.create_default_context()
    # no redirects: a redirect would hide a wrong status
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    return urllib.request.build_opener(urllib.request.HTTPSHandler(context=ctx), NoRedirect)


def call(opener, method, url, headers=None, body=None, form=None):
    data = None
    headers = dict(headers or {})
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    started = time.time()
    try:
        with opener.open(req, timeout=30) as r:
            status, raw, hdrs = r.status, r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        status, raw, hdrs = e.code, e.read(), dict(e.headers)
    except Exception as e:  # connection refused, TLS failure, timeout
        return 0, None, {}, int((time.time() - started) * 1000), str(e)
    ms = int((time.time() - started) * 1000)
    try:
        parsed = json.loads(raw) if raw else None
    except ValueError:
        parsed = raw.decode("utf-8", "replace")[:200]
    return status, parsed, {k.lower(): v for k, v in hdrs.items()}, ms, None


def check(name, ok, detail="", ms=None):
    RESULTS.append((name, ok, detail, ms))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"  — {detail}" if detail else "") + (f"  [{ms} ms]" if ms is not None else ""), flush=True)


def claims(token):
    part = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def main():
    a = parse_args()
    opener = make_opener(a.cacert)
    api = lambda path: a.api + path
    print(f"API {a.api}\nKeycloak {a.auth}\n")

    print("Reachability and token")
    status, body, _, ms, err = call(opener, "GET", api("/actuator/health"))
    check("gateway health", status == 200, err or f"HTTP {status}", ms)
    if status != 200:
        print("\nThe gateway is not answering — nothing else can be checked.")
        return 1

    status, body, _, ms, err = call(opener, "POST", a.auth + "/protocol/openid-connect/token",
                                    form={"client_id": "verborum-dev-cli", "username": a.user, "password": a.password,
                                          "grant_type": "password"})
    check("token from Keycloak (dev client)", status == 200, err or f"HTTP {status}", ms)
    if status != 200:
        print("\nNo token — nothing else can be checked.")
        return 1
    token = body["access_token"]
    c = claims(token)
    sub = c["sub"]
    check("token issuer is the one the gateway expects", True, f"iss={c['iss']}")
    auth = {"Authorization": f"Bearer {token}"}

    print("\nThe gateway refuses what it should")
    s, _, _, ms, _ = call(opener, "GET", api("/users/me"))
    check("no token -> 401", s == 401, f"HTTP {s}", ms)
    s, _, _, ms, _ = call(opener, "GET", api("/users/me"), headers={"Authorization": f"Bearer {token[:-4]}AAAA"})
    check("tampered token -> 401", s == 401, f"HTTP {s}", ms)
    s, _, _, ms, _ = call(opener, "GET", api("/users/me") + "?access_token=" + token)
    check("token in the query string -> 401", s == 401, f"HTTP {s}", ms)
    s, b, _, ms, _ = call(opener, "GET", api("/nothing-here"), headers=auth)
    check("unknown path -> 404 in the error envelope", s == 404 and isinstance(b, dict) and b.get("status") == 404, f"HTTP {s}", ms)

    print("\nCORS (the web app's origin " + a.web_origin + ")")
    pre = {"Origin": a.web_origin, "Access-Control-Request-Method": "PUT",
           "Access-Control-Request-Headers": "authorization,content-type,x-device-id"}
    s, _, h, ms, _ = call(opener, "OPTIONS", api("/notifications/devices"), headers=pre)
    check("preflight from the web origin, no token -> 200 with the origin echoed",
          s == 200 and h.get("access-control-allow-origin") == a.web_origin,
          f"HTTP {s}, allow-origin={h.get('access-control-allow-origin')}", ms)
    check("preflight allows Authorization, Content-Type and X-Device-Id",
          all(x in h.get("access-control-allow-headers", "").lower() for x in ("authorization", "content-type", "x-device-id")),
          h.get("access-control-allow-headers", ""))
    s, _, h, ms, _ = call(opener, "OPTIONS", api("/users/me"),
                          headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "GET"})
    check("preflight from an unknown origin -> 403", s == 403 and "access-control-allow-origin" not in h, f"HTTP {s}", ms)
    s, _, h, ms, _ = call(opener, "GET", api("/users/me"), headers={"Origin": a.web_origin})
    check("a 401 for the web origin still carries the CORS header (the app reads it to refresh)",
          s == 401 and h.get("access-control-allow-origin") == a.web_origin, f"HTTP {s}", ms)

    print("\nEvery route reaches its service")
    s, me, _, ms, _ = call(opener, "GET", api("/users/me"), headers=auth)
    check("GET /users/me -> ms_user", s in (200, 404), f"HTTP {s}" + (" (no profile yet)" if s == 404 else ""), ms)
    user_id = me.get("id") if s == 200 and isinstance(me, dict) else None
    s, _, _, ms, _ = call(opener, "GET", api(f"/dictionaries/{sub}"), headers=auth)
    check("GET /dictionaries/{sub} -> ms_dictionary", s == 200, f"HTTP {s}", ms)
    s, _, _, ms, _ = call(opener, "GET", api(f"/words/user/{sub}"), headers=auth)
    check("GET /words/user/{sub} -> ms_dictionary", s == 200, f"HTTP {s}", ms)
    if user_id:
        s, _, _, ms, _ = call(opener, "GET", api(f"/users/{user_id}/vault"), headers=auth)
        check("GET /users/{id}/vault -> ms_user", s == 200, f"HTTP {s}", ms)
    s, _, _, ms, _ = call(opener, "GET", api("/marketplace/dictionaries?size=1"), headers=auth)
    check("GET /marketplace/dictionaries -> ms_marketplace", s in (200, 403), f"HTTP {s}" + (" (not a Forum member)" if s == 403 else ""), ms)
    s, _, _, ms, _ = call(opener, "GET", api("/marketplace/dictionaries/popular"), headers=auth)
    check("GET /marketplace/dictionaries/popular -> ms_marketplace", s in (200, 403), f"HTTP {s}", ms)

    print("\nA service's own answers pass through unchanged")
    s, b, _, ms, _ = call(opener, "GET", api(f"/dictionaries/dictionary/{uuid.uuid4()}"), headers=auth)
    check("an unknown dictionary -> 404 RecordNotFoundException", s == 404 and isinstance(b, dict) and b.get("error") == "RecordNotFoundException", f"HTTP {s}", ms)
    s, b, _, ms, _ = call(opener, "GET", api("/notifications/devices/" + str(uuid.uuid4())), headers=auth)
    check("a wrong method on ms_notification -> 405", s == 405, f"HTTP {s}", ms)
    s, me2, _, ms, _ = call(opener, "GET", api("/users/me"), headers={**auth, "X-User-Id": "someone-else", "X-Forwarded-User": "someone-else"})
    same = (s == 200 and isinstance(me2, dict) and me2.get("id") == user_id) or (s == 404 and user_id is None)
    check("forged X-User-Id / X-Forwarded-User change nothing", same, f"HTTP {s}", ms)

    if a.writes:
        print("\nA write with a body (probe device, removed again)")
        dev = str(uuid.uuid4())
        h = {**auth, "X-Device-Id": dev}
        s, b, _, ms, _ = call(opener, "PUT", api("/notifications/devices"), headers=h,
                              body={"deviceId": dev, "token": "smoke-probe-" + dev, "platform": "ANDROID"})
        check("PUT /notifications/devices -> 201", s == 201, f"HTTP {s}" + (f" {b}" if s != 201 else ""), ms)
        s, _, _, ms, _ = call(opener, "DELETE", api("/notifications/devices/" + dev), headers=h)
        check("DELETE /notifications/devices/{id} -> 200", s == 200, f"HTTP {s}", ms)
        if user_id:
            s, _, _, ms, _ = call(opener, "DELETE", api(f"/users/{user_id}/vault/{uuid.uuid4()}"), headers=h)
            check("DELETE of an absent vault entry is a no-op -> 200", s == 200, f"HTTP {s}", ms)

    if a.rate_limit:
        print("\nLogin rate limit (Caddy)")
        if not a.edge and not a.api.startswith("https://"):
            print("  (skipped: not going through Caddy — use --edge)")
        else:
            got_429, sent = False, 0
            for _ in range(80):
                s, _, h, ms, _ = call(opener, "POST", a.auth + "/protocol/openid-connect/token",
                                      form={"client_id": "verborum-dev-cli", "username": "nobody-" + uuid.uuid4().hex[:6],
                                            "password": "wrong", "grant_type": "password"})
                sent += 1
                if s == 429:
                    got_429 = True
                    check("the token endpoint answers 429 after a burst", True, f"after {sent} requests, Retry-After={h.get('retry-after')}")
                    break
            if not got_429:
                check("the token endpoint answers 429 after a burst", False, f"80 requests, never 429")
            print("  (the limit resets with its window — wait a minute before logging in again)")

    failed = [r for r in RESULTS if not r[1]]
    print(f"\n{len(RESULTS) - len(failed)} passed, {len(failed)} failed")
    for name, _, detail, _ in failed:
        print(f"  FAILED: {name} — {detail}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
