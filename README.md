# Arkil

Arkil is a multi-tenant authentication server built on Spring Boot and Spring Authorization Server. It supports hosted login, password and magic-link auth, social login, passkeys, TOTP MFA, project-scoped OAuth client registration, webhook delivery, and a dashboard for tenant admins.

## What Arkil ships today

- Runtime project auth policy configuration for `EMAIL_PASSWORD`, `MAGIC_LINK`, `PASSKEY`, `TOTP`, `OAUTH2_GOOGLE`, `OAUTH2_GITHUB`, `OAUTH2_APPLE`, `OAUTH2_LINKEDIN`, and `OAUTH2_CUSTOM_OIDC`
- Per-project OAuth provider credentials stored in the database, including explicit endpoint metadata for custom OIDC providers
- Hosted browser flows for sign-up, login, password reset, magic links, email verification, passkeys, and TOTP challenges
- Dashboard and admin APIs for project management, API keys, webhook subscriptions, MFA factors, and user lifecycle actions
- Transactional email through either:
  - `console` provider for local development
  - `smtp` provider for production
- Rate limiting backends:
  - in-memory for local development
  - Redis-backed counters for distributed deployments
- Soft-delete for projects with scheduled permanent cleanup after the retention window

## Architecture

Arkil uses Spring Authorization Server for OAuth2 / OIDC issuance, JPA for durable state, and optional Redis for distributed throttling.

Key runtime components:

- `RegisteredClientBridgeService`: keeps each project synchronized with a Spring Authorization Server `RegisteredClient`
- `DynamicClientRegistrationRepository`: resolves built-in and custom OIDC providers from database configuration at request time
- `PolicyEnforcementFilter`: blocks disabled auth methods on hosted and factor routes
- `ProjectCleanupService`: permanently deletes expired soft-deleted projects and their registered-client artifacts

## Configuration

Main application properties live in:

- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`

Important settings:

- Public URLs
  - `ARKIL_AUTH_SERVER_URL` — externally reachable authorization-server URL
  - `ARKIL_DASHBOARD_URL` — exact dashboard origin registered for OIDC callbacks
  - `ARKIL_PLATFORM_CORS_ORIGINS` — optional comma-separated additional Arkil dashboard origins
  - `ARKIL_DEVELOPMENT_CORS_ORIGINS` — optional comma-separated machine-specific dev origins

- Email
  - `arkil.email.provider=console|smtp`
  - `arkil.email.from`
  - `arkil.email.base-url`
  - `spring.mail.host`
  - `spring.mail.port`
  - `spring.mail.username`
  - `spring.mail.password`
- Rate limiting
  - `arkil.ratelimit.backend=memory|redis`
  - `arkil.ratelimit.redis.key-prefix`
  - `spring.data.redis.host`
  - `spring.data.redis.port`
  - `spring.data.redis.password`
- MFA / passkeys
  - `arkil.mfa.totp.issuer`
  - `arkil.webauthn.rp-name`
  - `arkil.webauthn.rp-id`
  - `arkil.webauthn.origin`
  - `arkil.webauthn.timeout-ms`
- Project lifecycle
  - `arkil.project.deletion-retention-days`
  - `arkil.project.cleanup-cron`

## Local development

Prerequisites:

- Java 21+
- Node.js 20+

Start the backend:

```bash
./mvnw spring-boot:run
```

Start the dashboard:

```bash
cd dashboard
npm install
npm run dev
```

Default local services:

- Backend: `http://localhost:8080`
- Dashboard: `http://localhost:5173`

The local dashboard reads `dashboard/.env.development`. Do not edit production
settings to accommodate a local machine. Put LAN addresses or nonstandard ports
in the ignored `dashboard/.env.development.local` and set the matching backend
`ARKIL_DEVELOPMENT_CORS_ORIGINS` environment variable (or ignored
`src/main/resources/application-local.properties`).

## Production notes

- Start the backend with `SPRING_PROFILES_ACTIVE=prod`.
- Set `ARKIL_AUTH_SERVER_URL` and `ARKIL_DASHBOARD_URL` to the HTTPS URLs users
  actually visit. Production intentionally fails fast if either is missing.
- Build the dashboard with matching `VITE_AUTH_SERVER_URL` and
  `VITE_API_BASE_URL` values. These are build-time values, so redeploy the
  dashboard after changing them.
- The dashboard origin must exactly match `ARKIL_DASHBOARD_URL`: scheme, host,
  and port all matter to OAuth redirect URI matching.
- Keep `ARKIL_PLATFORM_CORS_ORIGINS` narrow; the configured dashboard origin is
  allowed automatically, while localhost defaults are removed in the prod profile.
- Set a real `ARKIL_ENCRYPTION_KEY`
- Use `arkil.email.provider=smtp`
- Set `arkil.ratelimit.backend=redis`
- Configure `ARKIL_WEBAUTHN_RP_ID` to the auth domain you serve. The WebAuthn
  origin defaults to `ARKIL_AUTH_SERVER_URL`, but can be overridden with
  `ARKIL_WEBAUTHN_ORIGIN`.
- Production rejects empty or localhost redirect URIs for production projects,
  and it does not create demo data or the local `demo-client`.

## Verification

Backend:

```bash
./mvnw test
```

Dashboard:

```bash
cd dashboard
npm run build
```
