# 🔐 Arkil (अर्कील)

> **The Dynamic Auth Fortress.**
> *Derived from Sanskrit: **Argala** (The Bolt) + **Kilaka** (The Pin).*

![Build Status](https://img.shields.io/badge/build-passing-brightgreen) ![Java](https://img.shields.io/badge/Java-21-orange)

**Arkil** is a lightweight, high-performance Authentication Server designed for dynamic ecosystems. Unlike static auth templates, Arkil treats Identity Providers (IdPs) and Claims as data, not code. It allows for **runtime configuration** of OAuth strategies, **multi-tenant isolation**, and **hot-swappable security policies** without restarting the application.

---

## 🚀 Key Differentiators

* **⚡ Dynamic Provider Injection:** Add new OAuth providers (Google, Apple, Custom OIDC) via database configuration. No code changes required.
* **🏢 Native Multi-Tenancy:** Single deployment serves multiple applications with isolated user bases and claim mappings.
* **🛡️ Apple Sign-In Ready:** Pre-built, battle-tested implementation of Apple's complex OIDC flow (including name extraction on first auth).
* **🔄 Smart Token Rotation:** Secure Refresh Token system with device tracking, rotation reuse detection, and remote revocation.
* **🚦 Adaptive Rate Limiting:** IP-based throttling using `Bucket4j` to prevent brute-force attacks.

---

## 🏗️ Architecture

Arkil isolates the immutable core security logic from the mutable provider configurations.

```text
arkil-server/
├── core/                     # The Immutable "Argala" (Bolt)
│   ├── jwt/                  # HS512 Signing & Claim Injection
│   ├── token/                # Refresh Token Rotation Logic
│   └── filter/               # Security Chain & Rate Limiting
│
├── providers/                # The Dynamic "Kilaka" (Pin)
│   ├── factory/              # OAuthServiceFactory (Runtime instantiation)
│   ├── impl/                 # Apple, Google, Facebook Implementations
│   └── config/               # DB-loaded Provider Configurations
│
└── api/                      # The Interface
    └── AuthController.java   # Unified Entry Point (Provider Agnostic)
```

## 🛠️ Core Capabilities
1. Configurable Claims Engine: <br/>
Don't hardcode claims. Arkil injects claims dynamically based on the Tenant and Role context.

```text
// Claims are injected dynamically at runtime
String token = jwtUtil.generateToken(user, tenantConfig.getCustomClaims());
```

2. The "Universal" Auth Facade: <br/>
A clean abstraction layer that standardizes how user data is retrieved, regardless of whether they logged in via Password, Google, or Apple.

3. Database-Driven OAuth: <br/>
Instead of application.yml, providers are loaded from the database: | Provider | ClientID | Secret | Scopes | Tenant_ID | | :--- | :--- | :--- | :--- | :--- | | google | 123-xyz... | *** | email,profile | app_A | | apple | com.app... | *** | name,email | app_A | | google | 789-abc... | *** | email | app_B |

## 🔌 API Endpoints
```
Method |	Endpoint               |	Description
POST   |	/auth/login            |	Standard username/password login
POST   |	/auth/refresh          |	Rotate access token using refresh token
POST   |	/auth/oauth/{provider} |	Initiate dynamic OAuth flow (e.g., /auth/oauth/apple)
POST   |	/auth/register         |	User registration with email verification trigger
GET    |	/auth/me               |	Get current authenticated user context
```

## 📦 Getting Started
### Prerequisites
 - Java 21+
 - PostgreSQL / MySQL
 - Redis (Optional, for distributed rate limiting)

### Installation
1. <b>Clone the Citadel:</b>
   ```bash
   git clone [https://github.com/jkjitendra/arkil.git](https://github.com/jkjitendra/arkil.git)
   ```
2. <b>Configure Database:</b> Update src/main/resources/application.properties with your credentials.
3. <b>Run the Bolt:</b>
   ```bash
    ./mvnw spring-boot:run
   ```
## 🤝 Contribution
Arkil is designed to be the foundational "Bolt" for your infrastructure. Pull requests are welcome for new Provider Implementations (e.g., GitHub, LinkedIn) or storage strategies.

