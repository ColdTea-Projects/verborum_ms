#!/usr/bin/env python3
"""
Diff the key sets of the login and email themes for the 19 app languages (P5A-08).

    python3 scripts/theme-i18n/check.py

For every language it checks that each key the pages use resolves in that language: from our theme
(keycloak/themes/verborum/*/messages/messages_<lang>.properties) or from the stock theme of the running
Keycloak (copied out of the container). A key that resolves nowhere would fall back to English. It also
reports our own values that are identical to the English text. Exit code 1 when anything is missing.

The stock keys below are the ones the login pages (login, password, forgot, register, verify email, error,
try another way, email code) and the mails (password reset, expiry units) use; found by rendering the pages
and mapping their text to keys (P5A-08). Extend the list when a page is added.
Needs the stack running (container verborum-keycloak). Local only.
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
THEME = os.path.join(ROOT, "keycloak", "themes", "verborum")
LANGS = "ar az de el es fa fr it ja ko lt nl pl pt ru tr uk zh".split()
STOCK_NAME = {"zh": "zh_Hans"}   # Keycloak 26 names Chinese zh-Hans
STOCK_LOGIN = ["accountDisabledMessage", "accountTemporarilyDisabledMessage", "auth-username-form-display-name", "backToApplication", "backToLogin", "clientNotFoundMessage", "confirmLinkIdpTitle", "doClickHere", "doForgotPassword", "doLogIn", "doNo", "doRegister", "doRegisterSecurityKey", "doSubmit", "doTryAnotherWay", "doYes", "email", "emailExistsMessage", "emailForgotTitle", "emailInstruction", "emailSentMessage", "emailVerifyInstruction1", "emailVerifyInstruction2", "emailVerifyInstruction3", "emailVerifyInstruction4", "emailVerifyTitle", "errorTitle", "errorTitleHtml", "expiredActionMessage", "expiredActionTokenNoSessionMessage", "expiredActionTokenSessionExistsMessage", "expiredCodeMessage", "firstName", "hidePassword", "identity-provider-login-label", "invalidEmailMessage", "invalidParameterMessage", "invalidPasswordMessage", "invalidRequestMessage", "invalidUserMessage", "invalidUsernameMessage", "lastName", "loginAccountTitle", "loginTimeout", "loginTitle", "missingEmailMessage", "missingFirstNameMessage", "missingLastNameMessage", "missingUsernameMessage", "noAccount", "noCertificate", "pageExpiredMsg1", "pageExpiredMsg2", "pageExpiredTitle", "password", "password-display-name", "password-help-text", "passwordConfirm", "registerTitle", "rememberMe", "requiredFields", "restartLoginTooltip", "sessionLimitExceeded", "showPassword", "termsTitle", "username", "usernameExistsMessage", "usernameOrEmail", "verifyEmailMessage"]
STOCK_EMAIL = ["passwordResetSubject", "passwordResetBody", "passwordResetBodyHtml",
               "linkExpirationFormatter.timePeriodUnit.seconds", "linkExpirationFormatter.timePeriodUnit.minutes",
               "linkExpirationFormatter.timePeriodUnit.hours", "linkExpirationFormatter.timePeriodUnit.days"]


def parse(text):
    out = {}
    for line in text.splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            out[key.strip()] = value
    return out


def ours(kind, lang):
    path = os.path.join(THEME, kind, "messages", f"messages_{STOCK_NAME.get(lang, lang) if lang != 'en' else 'en'}.properties")
    return parse(open(path, encoding="utf8").read()) if os.path.exists(path) else {}


def main():
    jar = os.path.join(tempfile.mkdtemp(), "themes.jar")
    subprocess.run(["docker", "cp", "verborum-keycloak:/opt/keycloak/lib/lib/main/org.keycloak.keycloak-themes-26.8.0.jar", jar],
                   check=True, capture_output=True)
    z = zipfile.ZipFile(jar)

    def stock(kind, lang):
        name = f"theme/base/{kind}/messages/messages_{STOCK_NAME.get(lang, lang)}.properties"
        return parse(z.read(name).decode("utf8")) if name in z.namelist() else {}

    problems = 0
    for kind, stock_keys in (("login", STOCK_LOGIN), ("email", STOCK_EMAIL)):
        en_ours = ours(kind, "en")
        required = set(en_ours) | set(stock_keys)
        for lang in LANGS:
            mine, base = ours(kind, lang), stock(kind, lang)
            missing = sorted(k for k in required if k not in mine and k not in base)
            same = sorted(k for k in mine if k in en_ours and mine[k] == en_ours[k] and len(mine[k]) > 12)
            if missing or same:
                problems += 1
                print(f"{kind} {lang}: missing {missing} identical-to-English {same}")
    print("OK: every key resolves in every language" if not problems else f"{problems} language/theme combinations have gaps")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
