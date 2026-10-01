# End-to-end tests

Browser tests (Playwright) against a running CloudFlow stack.

```bash
# 1. Start the stack (infrastructure/.env configured, see docs/deployment.md)
cd ../infrastructure && docker compose up -d --build
# 2. Run the tests
cd ../frontend
npx playwright install chromium            # or reuse Chrome: E2E_BROWSER_CHANNEL=chrome
E2E_API_URL=http://localhost:8080 npm run e2e
```

Signed-in tests need a session. GitHub's login page cannot be automated, so the fixture issues a
CloudFlow refresh token for a test user directly in the stack's database (`e2e/support.ts`). That
is exactly the state a completed GitHub sign-in leaves behind, so everything after sign-in is
tested for real. Test users are named `e2e-*` (GitHub ids from 900000000); a global teardown
deletes them and the organizations they created after every run. Run these tests only against a
local or CI stack, never against production.
