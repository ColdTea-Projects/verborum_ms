"""
HTTP plumbing shared by seed.py and reset.py — standard library only.

Every URL and credential can be overridden with an environment variable; the defaults are the local
docker-compose stack and the services on their usual ports.
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK = os.environ.get("SEED_KEYCLOAK_URL", "http://localhost:8180")
REALM = os.environ.get("SEED_REALM", "verborum")
KEYCLOAK_ADMIN = os.environ.get("KEYCLOAK_ADMIN", "admin")
KEYCLOAK_ADMIN_PASSWORD = os.environ.get("KEYCLOAK_ADMIN_PASSWORD", "admin")
DEV_CLIENT = os.environ.get("SEED_DEV_CLIENT", "verborum-dev-cli")

USER_URL = os.environ.get("SEED_USER_URL", "http://localhost:8086")
DICTIONARY_URL = os.environ.get("SEED_DICTIONARY_URL", "http://localhost:8085")
MARKETPLACE_URL = os.environ.get("SEED_MARKETPLACE_URL", "http://localhost:8087")
RABBIT_URL = os.environ.get("SEED_RABBIT_URL", "http://localhost:15672")
RABBIT_USER = os.environ.get("RABBITMQ_DEFAULT_USER", "verborum")
RABBIT_PASSWORD = os.environ.get("RABBITMQ_DEFAULT_PASS", "verborum")


class HttpError(Exception):
    def __init__(self, status, body, method, url):
        super().__init__(f"{method} {url} -> {status}: {body[:300]}")
        self.status = status
        self.body = body


def request(method, url, token=None, body=None, form=None, basic=None):
    """JSON in, JSON out. Raises HttpError on 4xx/5xx; returns None on an empty body."""
    headers = {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if basic:
        import base64
        headers["Authorization"] = "Basic " + base64.b64encode(f"{basic[0]}:{basic[1]}".encode()).decode()

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode()
            if not raw:
                return None
            try:
                return json.loads(raw)
            except ValueError:
                return raw
    except urllib.error.HTTPError as e:
        raise HttpError(e.code, e.read().decode(errors="replace"), method, url) from None


# ---- Keycloak ---------------------------------------------------------------------------------

def admin_token():
    return request("POST", f"{KEYCLOAK}/realms/master/protocol/openid-connect/token",
                   form=dict(client_id="admin-cli", username=KEYCLOAK_ADMIN, password=KEYCLOAK_ADMIN_PASSWORD,
                             grant_type="password"))["access_token"]


def find_keycloak_user(admin, username):
    found = request("GET", f"{KEYCLOAK}/admin/realms/{REALM}/users?exact=true&username={urllib.parse.quote(username)}",
                    token=admin)
    return found[0] if found else None


class Tokens:
    """Per-user access tokens via the dev-only password-grant client, renewed before they expire."""

    def __init__(self):
        self._cache = {}

    def get(self, username, password):
        cached = self._cache.get(username)
        if cached and time.time() < cached[1]:
            return cached[0]
        response = request("POST", f"{KEYCLOAK}/realms/{REALM}/protocol/openid-connect/token",
                           form=dict(client_id=DEV_CLIENT, username=username, password=password, grant_type="password"))
        # Renew a minute early; access tokens live five minutes
        self._cache[username] = (response["access_token"], time.time() + response.get("expires_in", 300) - 60)
        return response["access_token"]


def subject(token):
    """The JWT `sub` — what every service stores as the owner."""
    import base64
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["sub"]


# ---- RabbitMQ -------------------------------------------------------------------------------

def queue(name):
    return request("GET", f"{RABBIT_URL}/api/queues/%2F/{urllib.parse.quote(name)}", basic=(RABBIT_USER, RABBIT_PASSWORD))


def wait_until(check, timeout=30, interval=0.5, what="condition"):
    deadline = time.time() + timeout
    while time.time() < deadline:
        result = check()
        if result:
            return result
        time.sleep(interval)
    raise TimeoutError(f"timed out after {timeout}s waiting for {what}")
