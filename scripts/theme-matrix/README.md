# Login theme matrix

Opens nine Keycloak pages (login, password step, forgot password, mail sent, register, verify email, error,
try another way, email code) at six widths, in light and dark, left-to-right and right-to-left. Saves a
screenshot of each and reports every element that sticks out of the viewport. Local only. It registers one
throw-away user `matrixprobe…`; delete it afterwards (`users?search=matrixprobe` in the admin API).

    npm i puppeteer-core            # in a scratch folder, not in the repo
    OUT=/path/to/out node scripts/theme-matrix/matrix.js

Needs the stack running (Keycloak :8180, Mailpit :8025) and Google Chrome. Result: `docs/ops/login-theme-matrix.md`.
