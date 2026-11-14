# Project Keystone

An explainable onboarding-risk service for Indian business current accounts. It provides the backend vertical slice of an RM-to-operations/compliance journey: draft creation, validation, risk tiering, controlled review actions and an immutable audit timeline.

## What is implemented

- Java 21 / Spring Boot 3 REST API, JPA persistence, Flyway schema and H2 PostgreSQL-mode test runtime.
- HTTP Basic seeded users: `rm1`, `rm2`, `ops1`, `compliance1`; password `password`. Roles are RM, OPS and COMPLIANCE.
- One workflow state-machine boundary. The API response derives `allowedActions` from that same state machine, preventing UI/server drift.
- V1-V5 business validation, explainable factor-level risk scoring and PEP HIGH override.
- RM ownership controls, state-transition authorization, server-side note rules, API error contracts, and an append-only event entity.
- PAN and mobile masking in application responses. Values are intentionally never written to application logs.
- Cross-application link factors for duplicate entity PAN, signatory spread, shared mobile and address clusters. The `IdentifierIndex` boundary uses HMAC-SHA256 keys so raw PAN, mobile and addresses are never used as index keys; its synchronized in-memory adapter makes the submit read/write sequence atomic for unit tests.
- A migration-defined composite index `(status, risk_tier, submitted_at)`: equality filters precede the ascending queue sort, allowing PostgreSQL to narrow the range before walking it in queue order.

## Architecture

```text
RM / OPS / Compliance
        | HTTP Basic
OnboardingController
        |
OnboardingService ---- ApplicationValidator (V1-V5)
        |             RiskEngine (factor explanations)
        |             state machine / authorization
        v
PostgreSQL tables: application, party, factor, event
```

The event collection only gains rows inside the same transaction as a successful state change. There is no update/delete repository method exposed for audit events.

## Run and test

Prerequisite: Java 21+ and Maven 3.9+.

```powershell
mvn test
mvn spring-boot:run
```

Example create call (as `rm1`):

```powershell
curl.exe -u rm1:password -X POST http://localhost:8080/api/onboarding -H "Content-Type: application/json" -d "{\"entityType\":\"SOLE_PROPRIETORSHIP\",\"legalName\":\"Acme Traders\",\"pan\":\"ABCDE1234F\",\"registeredAddress\":{\"line1\":\"1 Main Street\",\"city\":\"Pune\",\"pincode\":\"411001\"},\"industryCode\":\"OTHER\",\"expectedMonthlyTurnover\":100000,\"expectedMonthlyCashDeposit\":10000,\"relatedParties\":[{\"name\":\"Aman\",\"pan\":\"ABCDE1234F\",\"mobile\":\"9876543210\",\"role\":\"PROPRIETOR\",\"ownershipPercent\":100,\"signatory\":true,\"pep\":false,\"nationality\":\"IN\"}]}"
```

`docker-compose.yml` starts the production system-of-record dependencies. The default tests deliberately use H2 and no Docker, so `mvn test` is clean-container safe.

To use the PostgreSQL profile locally:

```powershell
docker compose up -d postgres redis
$env:SPRING_PROFILES_ACTIVE = "docker"
mvn spring-boot:run
```

## Risk-factor table

| Factor | Points |
|---|---:|
| HIGH_RISK_INDUSTRY | 40 |
| MEDIUM_RISK_INDUSTRY | 20 |
| CASH_INTENSIVE | 10 or 25 |
| PEP_RELATED_PARTY | 50 and forces HIGH |
| FOREIGN_RELATED_PARTY | 15 |
| OPAQUE_OWNERSHIP | 20 |
| HIGH_TURNOVER | 10 |

Tier thresholds: `<30 LOW`, `30-59 MEDIUM`, `>=60 HIGH`.

## Workflow transitions

| Action | Actor / source | Destination |
|---|---|---|
| SUBMIT | RM owner / DRAFT | OPS review or EDD after tiering |
| REQUEST_INFO | OPS / OPS review; Compliance / OPS review or EDD | PENDING_INFO |
| RESUBMIT | RM owner / PENDING_INFO | revalidate and re-tier |
| ESCALATE_TO_EDD | OPS / OPS review | PENDING_EDD |
| APPROVE, REJECT | OPS / OPS review; Compliance / OPS review or EDD | terminal |
| WITHDRAW | RM owner / DRAFT, PENDING_INFO, OPS review | WITHDRAWN |

## Next steps

The remaining production increment is a Redis sorted-set adapter and React review queue. The in-memory index is intentionally only the offline-test adapter. The Redis adapter should use a Lua script to atomically query + insert keyed sorted-set members, with timestamps and configurable 30/90-day trimming, so concurrent submissions cannot miss duplicate identities. The UI should use the API's `allowedActions` as the only action authority.
