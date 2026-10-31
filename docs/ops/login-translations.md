# Login and email translations (P5A-08, 2026-10-31)

Keycloak 26.8.0, theme `verborum`. **Machine drafts**, written formal ("Sie", "usted", "Lei", "u", "您", "siz",
"شما" and so on). Native-speaker review is `P5R-19`.

## What is ours
| Where | Files | Strings |
|---|---|---|
| Login theme | `login/messages/messages_<lang>.properties` | 13 (email-code screen, "email me a code" option, the authenticator errors). Lithuanian also holds 22 stock strings the stock theme lacks; Arabic and Portuguese hold 1 each |
| Email theme | `email/messages/messages_<lang>.properties` | 22 (subjects, verification and code mail, "action needed" mail, footer, tagline). Lithuanian also holds the 4 time units |
| Mail templates | `email/{html,text}/email-code.ftl`, `email-verification.ftl`, `html/template.ftl` | read their words with `msg(...)`; `lang` and `dir` follow the user's language |

`zh` is the file `messages_zh_Hans`. The mail language is the user's `locale` attribute, which Keycloak stores from the
login page language at registration. A user registered in German gets German mail.

## Check
`python3 scripts/theme-i18n/check.py` — every key the pages use must resolve in every language, from our file or the
stock theme (else English would show). It also flags our own values identical to English. Last run: OK for 18 languages
× login and email. The key list was found by rendering the pages and mapping their text to stock keys (69 login keys).

Also run on 2026-10-31 (headless Chrome, 320 px): login, password step, wrong password, forgot, mail sent, register,
for all 19 languages: no English text left and no overflow; verification mail in all 19; email-code mail in Arabic
and Lithuanian.

## Known gaps
- The **"Client not found" error page** stays English: Keycloak draws it before it knows a language. Only a broken link reaches it.
- Stock strings of pages the check does not open (account update, OTP setup, social-login linking) stay as the stock theme has them.
- The stock translations are Keycloak's own; their formality was not reviewed.
- Machine drafts: wrong gender or tone is possible. `P5R-19`.
