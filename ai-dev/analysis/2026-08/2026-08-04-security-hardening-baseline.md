# Security Hardening Baseline

> Status: open
> Date: 2026-08-04
> Scope: Auth/JWT, encrypted values, AI Bash execution, and AI HTTP egress
> Conclusion: No Critical framework defect is currently confirmed. The listed High and Medium hardening findings require four separately planned remediation boundaries; trusted XLang DSL and test-friendly defaults are not code-remediation targets.

## Context

- The source-focused security review initially over-classified test defaults and
  misread action authorization as anonymous authentication bypass.
- Live source revalidation corrected those conclusions before implementation.
- This baseline is the durable evidence record for Plan 32 and its successor
  plans; it replaces `_tmp/security-analysis.md` as the planning source.

## Confirmed Baseline

### Authentication Is Default-Deny

- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java:43`
  defaults `servicePublic` to `false`.
- `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLActionAuthChecker.java:118-145`
  rejects a non-public action when `userContext` is absent.
- `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/directive/Auth.java:22`
  defaults `publicAccess` to `false`.

Conclusion: no `@Auth` annotation means no additional action-level
role/permission constraint after the default authentication boundary. It does
not mean anonymous access. The former fail-open finding is withdrawn.

### Trusted DSL Is A Design Premise

- `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/ReflectionManager.java:345`
  resolves a requested class without a sandbox whitelist.
- `nop-report/nop-report-core/src/main/java/io/nop/report/core/expr/ReportExpressionParser.java:16`
  enables the full expression feature set.

Conclusion: XLang, report, rule, workflow, and IoC DSL are trusted
developer-controlled artifacts. Sandboxing is an out-of-scope enhancement
unless a future product contract exposes editable DSL to non-developers.

### Finding Ownership

| ID | Severity | Live source anchor | Decision / remediation owner |
|----|----------|--------------------|------------------------------|
| H-1 | High | `nop-service-framework/nop-biz-auth-core/.../jwt/JwtHelper.java:69-87`; `JwtAuthTokenProvider`; `AuthHttpServerFilter`; `LoginApiBizModel` | Future Plan 329 after user-approved JWT contract |
| H-2 | High | `nop-auth/nop-auth-service/.../auth/DefaultActionAuthChecker.java:23-41`; `NopAuthConfigs.CFG_AUTH_SKIP_CHECK_FOR_ADMIN` | Future Plan 329 after user-approved auth behavior |
| H-3 | High | `nop-service-framework/nop-biz-auth-core/.../filter/AuthHttpServerFilter.java:220-228` | Future Plan 329 after user-approved `servicePublic` contract |
| H-4 | High | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:34-79` | Future Plan 331 after user-approved sandbox backend |
| M-1 | Medium | `nop-service-framework/nop-biz-auth-core/.../filter/AuthHttpServerFilter.java:196-218` | Future Plan 329 after user-approved browser contract |
| M-2 | Medium | `nop-service-framework/nop-biz-auth-core/.../AuthCoreConfigs.java:23-25`; `AuthHttpServerFilter.addCookie` | Future Plan 329 after user-approved browser contract |
| M-3 | Reclassified Low | Demo `application.yaml` SSO client-secret and database password | Deployment configuration constraint; no code successor |
| M-4 | Medium | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:78-107` | Future Plan 332 after user-approved transport enforcement contract |
| M-5 | Medium | `nop-service-framework/nop-biz-auth-core/.../filter/StateCookieHelper.java:81` | Future Plan 329 after user-approved auth behavior |
| M-6 | Medium | `nop-service-framework/nop-biz-auth-core/.../auth-core-defaults.beans.xml:14-20` | Future Plan 329 after user-approved password-policy baseline |
| M-7 | Medium | `nop-kernel/nop-commons/.../crypto/impl/AESTextCipher.java`; `nop-persistence/nop-orm/.../DefaultOrmColumnBinderEnhancer.java:20`; `nop-core-framework/nop-config/.../ConfigStarter.java:457-471` | Future Plan 330 after user-approved ciphertext compatibility contract |

### Reclassified Deployment Constraints

| Former finding | Classification | Rationale |
|----------------|----------------|-----------|
| Public demo JWT key, `nop`/`123` test user, and empty default crypto key | Low deployment configuration | Explicit testing/development conveniences; production must override configuration. |
| Demo SSO client-secret and database password | Low deployment configuration | Same testing/development-default rationale; not a distinct framework-code defect. |

These constraints require deployment guidance but do not own an implementation
successor plan.

## Required Decision Records Before Implementation Plans Activate

### Authentication And Browser Boundary

- JWT matrix: issuer, audience, purpose, issuer, and consumer for access,
  refresh, authorization-code, and SSO tokens; include an API migration
  decision if any public DTO/interface contract changes.
- `servicePublic` matrix: allowed paths, operation types, `@Auth` behavior,
  SYS identity, data authorization, tenant source, and whether a tenant header
  is ignored or rejected.
- Browser matrix: allowed redirect forms/origins, relative-path normalization,
  reverse-proxy TLS behavior, development HTTP opt-out, and cookie attributes.

### Encrypted Value Format

- Define a self-describing versioned ciphertext representation.
- Define per-call IV generation and thread-safety ownership.
- Define legacy/new KDF and ciphertext detection, migration, and failure rules
  for unknown version, truncation, and authentication-tag failure.
- Inventory all supported `ITextCipher` text/stream consumers before choosing
  a global or scoped compatibility boundary.

### Bash Isolation

- Select a supported runtime backend and state whether unrestricted host
  process execution remains supported.
- Define file, network, process, timeout, memory, and unavailable-backend
  behavior. The unavailable case must fail closed.
- Require real backend integration proof, not command-string blacklist tests.

### HTTP SSRF Enforcement

- Select the enforcement layer that owns both target validation and actual
  connection establishment; decide whether a `nop-http-api` contract change is
  required.
- Define a resolver/connection-pinning policy for multi-A/AAAA records, DNS
  rebinding, IPv4/IPv6 encoded representations, redirects, and proxies.
- Every redirect hop and final connection must be validated before transport
  sends data; unresolved or policy-ambiguous targets fail closed.

## Conclusion

- The former Critical findings are either withdrawn (authentication),
  reclassified as deployment constraints (test defaults), or design premises
  (trusted DSL).
- The remaining remediation work is partitioned by independent security
  boundary into future Plans 329 through 332 after user approval of each
  decision record.
- Rejected approach: one combined implementation plan. It was rejected because
  authentication, ciphertext migration, process isolation, and socket-level
  SSRF prevention require different protected-area approvals, compatibility
  decisions, and proof methods.
- Follow-up: `ai-dev/plans/328-security-hardening-remediation-planning.md`.

## Open Questions

- [ ] Which `servicePublic` tenant behavior is supported by product contract?
- [ ] Does the selected SSRF enforcement layer require a public HTTP API
  migration?
- [ ] Which Bash sandbox backend is supported in all target deployment modes?
- [ ] Is encrypted-value compatibility global to `AESTextCipher` or scoped to
  ORM/config consumers?
