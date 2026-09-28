# Developer billing plan for Arkil

Status: proposed design. Billing and plan enforcement are **not implemented**.

## Boundary

Arkil authenticates people for a developer's application. Arkil bills the **developer or company that owns the tenant** for using Arkil. It does not take payments from that application's end users and does not decide which products those users may purchase. An application remains responsible for its own commerce, subscriptions, and product entitlements.

The subscription belongs to the tenant. All projects in that tenant share its plan, while each project can choose a subset of the permitted authentication modules. An upgrade or downgrade must be visible in the dashboard before it takes effect.

The tier table governs **end-user sign-in for onboarded applications**. Arkil's own developer dashboard registration, login, recovery, and admin security remain available regardless of tier.

## Proposed tiers

| Plan | Available sign-in methods | Other authentication capabilities | Vendor-cost basis |
| --- | --- | --- | --- |
| Basic | Google and GitHub sign-in, exactly two | Core OAuth/OIDC tokens, key discovery, rate limiting, session security, audit events | Developer supplies both OAuth app credentials. Validate provider terms before launch; no provider fee per sign-in is assumed. Arkil still pays normal infrastructure costs. |
| Growth | Everything in Basic, plus Apple, LinkedIn, and passkeys | TOTP as an optional second factor; developer supplies social provider credentials | Passkeys and TOTP do not need a paid message-delivery vendor. Apple's developer membership, where required, is the developer's cost when using their credentials. |
| Scale | Every currently implemented module: all Growth options, email/password, magic links, and custom OIDC | All supported factors and enterprise provider configuration | Arkil-funded transactional email for verification, recovery, and magic links has usage-based cost. Include a monthly email allowance, then charge for measured overage. Custom OIDC may also need higher support effort. |

TOTP is a factor, not an independent way to create an account. Growth users can enroll a passkey after a social sign-in; Basic has only the two stated sign-in methods. Critical protections such as rate limiting, token validation, logout, and account security stay enabled on every tier.

These are *product entitlements*, not yet active behavior. Today a new project defaults to email/password, which conflicts with the proposed Basic tier. Existing projects must be grandfathered or explicitly migrated before enforcement. Do not silently switch off a method that current users need to sign in.

The distinction between provider costs and Arkil costs matters. Google and GitHub integrations use the developer's credentials. [AWS SES bills for outbound email](https://aws.amazon.com/ses/pricing/), so repeated magic links and recovery email can create a direct Arkil expense. [Apple's developer membership has an annual fee](https://developer.apple.com/programs/); it is the developer's expense under the proposed bring-your-own-credentials model. Pricing and terms should be rechecked when the feature launches.

## Billing architecture

1. Select a billing processor for **Arkil-to-developer** subscriptions. Hosted checkout and a billing portal should be separate from Arkil's end-user hosted login. Never store card numbers in Arkil.
2. Add tenant-level `billing_customer_id`, `subscription_id`, plan code, billing status, current period, and grace period. Store provider event IDs in a unique table for idempotent webhook handling. Keep a historical entitlement record for audits.
3. Accept billing webhooks only after signature verification. Process them idempotently, retrieve current subscription state when events arrive out of order, and reconcile periodically. The billing provider is the payment source of truth; Arkil's entitlement snapshot is the runtime decision source.
4. Define plan entitlements in one versioned catalog. Effective methods are the intersection of **subscription allowance**, **project selection**, and **configured provider credentials**. Use the same entitlement service in the dashboard, public project configuration, hosted login, social callbacks, direct session endpoint, passkey and factor endpoints, and token refresh. A UI-only restriction is insufficient.
5. Add a safe billing lifecycle: trial or initial Basic, active, past due with a published grace period, canceled at period end, and suspended. During a downgrade, stop new enrollment or login through newly excluded methods only after a migration notice and grace period. Preserve recovery and already-established security factors until users can migrate. Revoke or expire sessions according to a documented policy.
6. Meter the cost drivers by tenant and month: authentication attempts, monthly active users, emails by purpose, provider API calls, and support-heavy custom OIDC configurations. Separate metered costs from non-metered subscription fees. Set prices only after measuring infrastructure and vendor costs, billing fees, fraud losses, and support load; then set an allowance and a capped or clearly disclosed overage rate for Scale email.
7. Add admin and integration tests for entitlement boundaries, webhook retries/out-of-order events, upgrade and downgrade behavior, project changes, method visibility, and user recovery. Roll out behind a feature flag, reconcile existing tenants, then enforce on new tenants first.

## Developer and application responsibilities

A developer creates an Arkil tenant, selects a plan, registers each application as a project, adds its redirect URIs and chosen provider credentials, and enables methods allowed by the plan. The application sends users through Arkil's authorization-code flow with PKCE, validates issuer, signature, audience, expiry, and scopes on returned tokens, then maps Arkil's stable `sub` to its own user record. Arkil owns identity and authentication; the application continues to store business columns such as expenses, blog posts, game progress, purchases, and product access keyed by that `sub`.

Example: Xpense Pro on Basic enables Google and GitHub. A user signs in at Arkil, Xpense Pro validates the token and creates its own profile keyed by `sub`. If Xpense Pro later buys Scale, it can add password and magic-link sign-in. Any Xpense Pro premium feature stays in Xpense Pro's database and payment system.

## Implementation order

1. Measure costs and confirm provider terms; finalize plan and migration policy.
2. Add billing schema and webhook ingestion with signature checks, idempotency, and reconciliation.
3. Add the central entitlement service and enforce it at every authentication entry point.
4. Add checkout and self-service plan controls for tenant admins; show the effective methods for each project.
5. Migrate existing projects safely, test downgrade and recovery flows, and release gradually.
