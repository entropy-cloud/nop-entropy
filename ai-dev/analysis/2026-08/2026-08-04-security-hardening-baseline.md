# Security Hardening Baseline

> Status: closed (decision records frozen; successor plans 333-336 own implementation)
> Date: 2026-08-04
> Last Reviewed: 2026-08-07
> Scope: Auth/JWT, encrypted values, AI Bash execution, and AI HTTP egress
> Conclusion: No Critical framework defect is currently confirmed. The listed High and Medium hardening findings require four separately planned remediation boundaries; trusted XLang DSL and test-friendly defaults are not code-remediation targets.

This is the durable baseline for Plan 328. All source anchors below were revalidated
against live source during Plan 328 Phase 1 (2026-08-07). The decision records in the
second half of this document are the contract successors 333-336 must consume; they are
specific enough that successor authors need not invent a contract.

## Context

- The source-focused security review initially over-classified test defaults and
  misread action authorization as anonymous authentication bypass.
- Live source revalidation corrected those conclusions before implementation.
- This baseline is the durable evidence record for Plan 328 and its successor
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

Every confirmed High/Medium finding has exactly one successor owner. Weak password policy
(M-6) is owned by Plan 333 per the supported-baseline hardening rule.

| ID | Severity | Live source anchor | Decision / remediation owner |
|----|----------|--------------------|------------------------------|
| H-1 | High | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtHelper.java:102-107` (hardcoded `issuer("nop")`, no audience claim); `JwtAuthTokenProvider.java:40-49,52-64` (single key for all token kinds, UUID-derived key when `encKey` empty); `JwtHelper.java:69-87` (signature+expiry verified, issuer/audience/purpose NOT verified); `LoginApiBizModel.java:79-83` (refresh consumed via same `parseAuthToken`) | Plan 333 after user-approved JWT contract |
| H-2 | High | `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/auth/DefaultActionAuthChecker.java:23` (`@InjectValue("...skip-check-for-admin\|true")` — `true` fallback) vs `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/NopAuthConfigs.java:77` (`CFG_AUTH_SKIP_CHECK_FOR_ADMIN` default `false`) | Plan 333 after user-approved auth behavior |
| H-3 | High | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java:43` (`servicePublic=false` default); `AuthHttpServerFilter.java:150-152` (SYS context synthesized when `servicePublic && servicePath` and no user); `AuthHttpServerFilter.java:220-228` (`newSysUserContext` takes `tenantId` from client `HEADER_TENANT`) | Plan 333 after user-approved `servicePublic` contract |
| H-4 | High | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:34-38` (destructive-command regex), `:67-72` (`sh -c` / `cmd /c` via `ProcessBuilder`), `:145-153` (string-blocking `validateCommand`) | Plan 335 after user-approved sandbox backend |
| M-1 | Medium | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:196-218` (`isAllowedRedirectUri` allows any string with `startsWith("/") && !contains("://")`; `//host/path` and `/\host` pass as "relative"); `AuthFilterConfig.java:40,205-214` (absolute redirects need `allowedRedirectPrefixes`, default empty) | Plan 333 after user-approved browser contract |
| M-2 | Medium | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/AuthCoreConfigs.java:23-25` (`CFG_AUTH_USE_SECURE_COOKIE` default `false`); `AuthHttpServerFilter.java:325-332` (`addCookie`: HttpOnly + SameSite=Lax, Secure from config, no `__Host-` prefix) | Plan 333 after user-approved browser contract |
| M-3 | Reclassified Low | Demo `application.yaml` SSO client-secret and database password | Deployment configuration constraint; no code successor (see Reclassified Deployment Constraints) |
| M-4 | Medium | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:78-107` (host text validated pre-flight via `validateUrl`); `:133` (`httpClient.fetch` owns the actual connection); `nop-network/nop-http/nop-http-api/.../client/HttpClientConfig.java:33,226-230` (`followRedirects` + proxy owned by transport, not by validator) | Plan 336 after user-approved transport enforcement contract |
| M-5 | Medium | `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/StateCookieHelper.java:29-38` (`secureCookie` from `CFG_AUTH_USE_SECURE_COOKIE`, default `false`); `:81` (state comparison); `:51-63` (`setStateCookie`) | Plan 333 after user-approved auth behavior |
| M-6 | Medium | `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/auth/beans/auth-core-defaults.beans.xml:14-20` (`DefaultPasswordPolicy`: upper=0, lower=0, digit=0, special=1, minLen=8) | Plan 333 after user-approved password-policy baseline |
| M-7 | Medium | `nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java:71-82` (default ctor pins static `DEFAULT_GCM_IV`, no per-message IV), `:115-119` (`generateIv()` exists but unused by default ctor), `:153-168` (`buildSecretKey` = `md5(encKey+saltKey)`, no PBKDF2/Argon2); `nop-persistence/nop-orm/.../DefaultOrmColumnBinderEnhancer.java:20` (`new AESTextCipher()` — static IV); `nop-core-framework/nop-config/.../ConfigStarter.java:457-471` (`new AESTextCipher()`, never calls `generateIv()`) | Plan 334 after user-approved ciphertext compatibility contract |

### Reclassified Deployment Constraints

The original security report classified the public demo JWT key, the `nop`/`123` test
user, and the empty default crypto key as Low, but classified the demo SSO client-secret
and demo database password as Medium. That split was inconsistent: all five are explicit
test/development conveniences that production must override, and none of them is a
distinct framework-code defect. They are unified into a single Low deployment-configuration
class so that successor plans inherit a single, consistent rationale.

| Former finding | Classification | Rationale |
|----------------|----------------|-----------|
| Public demo JWT key, `nop`/`123` test user, and empty default crypto key | Low deployment configuration | Explicit testing/development conveniences; production must override configuration (`nop.auth.jwt.enc-key`, `nop.auth.login.allow-create-default-user`, `nop.crypt.default-enc-key`). |
| Demo SSO client-secret and database password | Low deployment configuration (corrected from Medium) | Same testing/development-default rationale as above; they live only in demo `application.yaml` files and are not referenced by framework defaults. Not a distinct framework-code defect. |

These constraints require deployment guidance but do not own an implementation successor
plan. Production hardening guidance (override all five keys, disable
`allow-create-default-user`, set a strong `enc-key`, rotate on install) is a
documentation/configuration responsibility, tracked as a Non-Blocking Follow-up in Plan 328.

## Required Decision Records Before Implementation Plans Activate

The decision records below are the contract successors 333-336 consume. Each records
the live-source facts that constrain the decision and the specific questions a successor
must resolve. Phase 2 of Plan 328 records a disposition (`approved` / `deferred-pending-user`)
for each; the four successor draft plans are created from the approved records.

### DR-1: Authentication And Browser Boundary (consumed by Plan 333)

#### DR-1a: JWT Issuer / Consumer Matrix (addresses H-1)

Live source facts that constrain the decision:

- `JwtHelper.genToken` (`JwtHelper.java:102-107`) hardcodes `issuer("nop")` and sets no
  `audience` claim for every token kind.
- `JwtAuthTokenProvider` (`JwtAuthTokenProvider.java:52-64`) issues access, refresh, and
  authorization-code tokens with the **same signing key** and the **same issuer**, overloading
  the JWT `subject` field as a purpose marker: `"a"` (access), `"r"` (refresh), `"c"` (code).
- `JwtHelper.parseToken` (`JwtHelper.java:69-87`) verifies signature and expiry only. It
  does **not** verify `issuer`, `audience`, or that the subject/purpose matches what the
  consumer expects. As a result a refresh token is accepted wherever an access token is,
  and vice versa (token-purpose confusion).
- `JwtAuthTokenProvider.getSignKey` (`JwtAuthTokenProvider.java:40-49`) derives the key from
  a random UUID when `encKey` is empty, so single-instance deployments get an ephemeral key
  that cannot survive restart or be validated by peer instances.
- SSO consumption: `AuthHttpServerFilter.loginWithOAuthCode` (`AuthHttpServerFilter.java:271-279`)
  exchanges the SSO code for a normal access token via `loginService.loginAsync`; the SSO
  code itself is never parsed as a JWT by this filter.

Required JWT matrix (Plan 333 must populate and enforce):

| Token kind | Issuer (claim `iss`) | Audience (claim `aud`) | Purpose/type claim | Signing key | Consumer(s) | Consumer validation rule |
|------------|----------------------|------------------------|--------------------|-------------|-------------|--------------------------|
| Access | TBD (proposed `"nop"`) | TBD (proposed resource-server id) | TBD (e.g. `typ=access`) | TBD (single vs split keys) | `AuthHttpServerFilter` via `parseAuthToken` | verify sig + exp + iss + aud + typ==access |
| Refresh | TBD | TBD (proposed auth-server id) | TBD (`typ=refresh`) | TBD | `LoginApiBizModel.refreshTokenAsync` via `parseAuthToken` | verify sig + exp + iss + aud + typ==refresh |
| Authorization code | TBD | TBD | TBD (`typ=code`) | TBD | `LoginApiBizModel.getLoginResultAsync` / SSO flow | verify sig + exp + iss + aud + typ==code |
| SSO code | N/A (opaque to framework) | N/A | N/A | N/A | `loginService.loginAsync(LOGIN_TYPE_SSO)` | exchanged, not parsed as JWT |

Public-contract impact & migration: Adding `aud` and a purpose claim changes the JWT
payload but not the wire format (still a compact JWT). Existing tokens issued before the
change will fail the new `iss`/`aud`/`typ` checks; Plan 333 must decide a migration window
(grace period vs forced re-login). If split signing keys are chosen, the key-locator
function in `JwtHelper.parseToken(String, Function<String,Key>)` already supports per-KID
lookup, so no public API change is required.

#### DR-1b: `servicePublic` Decision Table (addresses H-3)

Live source facts that constrain the decision:

- `AuthFilterConfig.servicePublic` defaults to `false` (`AuthFilterConfig.java:43`).
- When `servicePublic && servicePath` and no user context is present,
  `AuthHttpServerFilter._filterAsync` (`AuthHttpServerFilter.java:150-152`) synthesizes a
  SYS user context via `newSysUserContext`.
- `newSysUserContext` (`AuthHttpServerFilter.java:220-228`) sets `userId=SYS`,
  `userName=SYS`, and takes `tenantId` from the client-supplied `HEADER_TENANT`. A client
  therefore controls the tenant scope of the synthesized SYS identity.

Required `servicePublic` table (Plan 333 must decide each cell):

| Dimension | Current behavior | Decision required |
|-----------|------------------|-------------------|
| Default value of `servicePublic` | `false` | Keep `false` as the only supported default? (recommended) |
| Supported anonymous operation types on service paths | SYS identity used for any service path when enabled | Restrict to a whitelist of explicitly-`@Auth(publicAccess=true)` actions? |
| `@Auth(publicAccess=true)` interaction | Not consulted before SYS synthesis | Should SYS synthesis require the target action to be declared `publicAccess=true`? |
| SYS identity scope | Full SYS user with no permission gating | Should SYS be a reduced-privilege principal (e.g. anonymous role) instead? |
| Data authorization under SYS | None — SYS bypasses site-map checks via `DefaultActionAuthChecker` skip-for-admin if enabled | Should SYS actions be subject to a data-auth policy? |
| Tenant source when SYS is synthesized | Client `HEADER_TENANT` is trusted verbatim | Reject tenant header (force single tenant), or accept from a trusted-proxy allowlist? |
| Tenant-header policy generally | Header trusted on all paths | Reject on public/service-public paths; require `X-Forwarded-Tenant` from trusted proxy only? |

#### DR-1c: Browser Boundary Matrix (addresses M-1, M-2)

Live source facts:

- `isAllowedRedirectUri` (`AuthHttpServerFilter.java:196-210`) accepts any string for which
  `startsWith("/") && !contains("://")` as "relative". `//evil.com/x`, `/\evil.com`, and
  `/\\evil.com` all pass this test and are protocol-relative or backslash-relative open
  redirects in major browsers.
- Absolute redirects require an explicit `allowedRedirectPrefixes` entry
  (`AuthFilterConfig.java:40,205-214`); the default empty list rejects all absolute redirects.
- `addCookie` (`AuthHttpServerFilter.java:325-332`) sets `HttpOnly`, `Secure` (from
  `CFG_AUTH_USE_SECURE_COOKIE`, default `false`), `SameSite=Lax`, `Path=/`. No `__Host-`
  prefix and no configurable SameSite.

Required browser matrix (Plan 333 must decide):

- Allowed redirect forms: relative path only, or also `allowedRedirectPrefixes`? What is
  the strict relative-path definition (must reject `//`, `/\`, `/\\`, control chars)?
- Redirect-origin normalization: scheme + authority canonicalization before prefix match?
- Reverse-proxy TLS: trust `X-Forwarded-Proto`? Build redirect URLs with absolute scheme?
- Development HTTP opt-out: allow `Secure=false` only behind an explicit dev profile?
- Cookie attributes: default SameSite (`Lax` vs `Strict`), `Secure` default, `__Host-`
  prefix for the auth cookie, configurable path/domain.
- State cookie: inherit `Secure` default decision; `SameSite=Strict` already set by
  `StateCookieHelper.setStateCookie` (`StateCookieHelper.java:61`).

#### DR-1d: Password-Policy Baseline (addresses M-6)

Live source facts:

- `auth-core-defaults.beans.xml:14-20` configures `DefaultPasswordPolicy` with
  `upperCaseCount=0, lowerCaseCount=0, digitCount=0, specialCharCount=1, minLength=8`.

Required decision (Plan 333 must decide): Is the supported-baseline default
`minLength=8 + specialCharCount=1` acceptable, or must it be raised (e.g.
`minLength=12`, require upper+lower+digit)? Any change is a backward-compatibility
constraint for existing seeded users and must include a migration/override path.

#### DR-1e: Admin-Skip Default Consistency (addresses H-2)

Live source facts:

- `DefaultActionAuthChecker.setSkipCheckForAdmin`
  (`DefaultActionAuthChecker.java:23`) declares `@InjectValue("@cfg:nop.auth.skip-check-for-admin|true")`
  — a `true` fallback.
- `NopAuthConfigs.CFG_AUTH_SKIP_CHECK_FOR_ADMIN` (`NopAuthConfigs.java:77`) declares the
  same config key with default `false`.

These two defaults are inconsistent: when no value is configured, the `@InjectValue`
fallback (`true`) wins and admins bypass permission checks. Required decision (Plan 333):
which default is the supported behavior (`false` recommended), and should the
`@InjectValue` fallback be removed so the single `IConfigReference` default is the only
source of truth?

### DR-2: Encrypted-Value Format (consumed by Plan 334)

#### DR-2a: AES Encrypted-Value Format Decision Requirements (addresses M-7)

Live source facts that constrain the decision:

- `AESTextCipher`'s default constructor (`AESTextCipher.java:71-82`) selects
  `AES/GCM/NoPadding` but pins `this.iv = DEFAULT_GCM_IV` — a static 12-byte value derived
  from a config/default string (`AESTextCipher.java:49-52`). The IV is reused across every
  encryption unless `generateIv()` is explicitly called.
- `generateIv()` (`AESTextCipher.java:115-119`) exists and uses `secureRandom()`, but is
  never invoked by the default constructor, by `DefaultOrmColumnBinderEnhancer`
  (`DefaultOrmColumnBinderEnhancer.java:20`), or by `ConfigStarter.newVmValueEnhancer`
  (`ConfigStarter.java:457-471`).
- `concatIv` (`AESTextCipher.java:87,262-264`) prepends the IV to the ciphertext, but the
  IV it prepends is still the static one unless `generateIv()` ran first. There is no
  self-describing version marker in the output.
- Key derivation: `buildSecretKey` (`AESTextCipher.java:153-168`) uses
  `md5(encKey + saltKey)` — a single-pass MD5, not PBKDF2/Argon2/scrypt.
- Failure behavior: `decrypt` (`AESTextCipher.java:273-286`) wraps any exception in
  `NopException` — GCM tag failure, truncation, and unknown-format values already fail
  closed by throwing.

Required format decision (Plan 334 must define all of):

| Requirement | Decision needed |
|-------------|-----------------|
| Self-describing version marker | Byte/magic prefix (e.g. `v1`) so legacy and new formats are distinguishable without guessing? |
| Per-encryption IV generation | `generateIv()` invoked on every `encrypt`; IV always prepended (concatIv forced true for new format)? |
| Concurrency / thread-safety ownership | Is the cipher instance shared across threads (must be stateless per call) or per-call constructed? Current `iv` field is mutable instance state — not thread-safe. |
| Legacy-format detection & read | How is legacy (static-IV, MD5-KDF) ciphertext recognized, and is read-only legacy support required (no re-encryption) or lazy migration? |
| Key-derivation compatibility | New KDF (PBKDF2/scrypt) keyed off the version marker; same `encKey` config, different derived bytes per version? |
| Fail-closed behavior for unknown/truncated/tampered | Confirmed already throwing; successor must preserve this and add explicit tests for each case. |
| Compatibility scope | Global to `AESTextCipher`, or scoped to ORM binder + config enhancer only? Inventory every `ITextCipher` consumer before deciding. |

#### DR-2b: AES Format Compatibility Boundary (separate durable decision)

The compatibility boundary choice determines blast radius. Options Plan 334 must choose
between:

- **Global**: change `AESTextCipher` defaults so every consumer (ORM binder, config
  enhancer, report/rule encryption, any third-party `ITextCipher` user) gets versioned
  per-message-IV automatically. Highest consistency, largest migration surface.
- **Scoped**: introduce a new versioned format only at the ORM binder
  (`DefaultOrmColumnBinderEnhancer`) and config enhancer (`ConfigStarter`) entry points,
  leaving raw `AESTextCipher` as-is for callers that opt out. Smaller surface, but
  duplicates the versioning logic and leaves direct `AESTextCipher` callers on the static IV.

The inventory of `ITextCipher` / `IStreamCipher` consumers (required before choosing) is
Plan 334 Phase 1 work; the decision itself is a user-approval gate because persisted data
is affected.

### DR-3: Bash Isolation (consumed by Plan 335)

#### DR-3a: Bash Backend Admission / Isolation Policy (addresses H-4)

Live source facts that constrain the decision:

- `BashExecutor.doExecute` (`BashExecutor.java:67-72`) unconditionally builds
  `sh -c <command>` (or `cmd /c` on Windows) via `ProcessBuilder` and runs it on the host.
  There is no process-isolation backend; the host shell is the only backend.
- The only pre-execution control is `validateCommand` (`BashExecutor.java:145-153`), a
  regex (`DESTRUCTIVE_COMMAND`, `BashExecutor.java:34-38`) that blocks a handful of
  destructive patterns. This is string-level blocking, trivially bypassable (quoting,
  variables, base64, aliases), and provides no filesystem/network/process confinement.
- `parseEnv` (`BashExecutor.java:155-180`) strips a `DANGEROUS_ENV_VARS` set, which is a
  defense-in-depth hint but not isolation.
- Timeout is enforced (`BashExecutor.java:109-113`); memory/CPU/file/network limits are not.

Required backend admission/isolation decision (Plan 335 must define):

- **Selected runtime backend**: none (host shell — current), or a real isolator
  (container/seccomp/jail/landlock/WASM). Nop does not currently bundle a sandbox; the
  successor must state which backend is supported in all target deployment modes and
  whether unrestricted host execution remains a supported mode.
- **Fail-closed behavior when the backend is unavailable**: must throw / refuse the call,
  not fall back to host `sh -c`. (Hard requirement — a fallback to host execution would
  silently revert the entire control.)
- **Resource-limit policy**: CPU, memory, wall-clock, process-count, file access
  (allowlist/denylist), network egress (allow/deny), working-directory jail.
- **Proof method**: real-backend integration test (e.g. confirm a denied syscall/file is
  actually denied), not command-string blacklist assertions.

#### DR-3b: AI Tool Runtime Facts (consumed by Plans 335 and 336)

Recorded so successor authors need not re-audit the toolkit runtime:

- `IToolExecutor.executeAsync` runs on `context.getExecutor()`
  (`BashExecutor.java:47`, `HttpRequestExecutor.java:75`), a supplied `Executor` — there is
  no built-in per-tool timeout/memory isolation; `timeoutMs` is read from the call
  (`BashExecutor.java:55`, `HttpRequestExecutor.java:60`) and enforced only as wall-clock.
- Available sandbox backends in the toolkit today: **none**. `BashExecutor` and
  `HttpRequestExecutor` are the only built-in executors that touch the OS/network; both run
  on the host process. A successor that wants isolation must introduce a backend abstraction
  (there is no existing seam to inject one beyond the `IToolExecutor` interface).
- Filesystem controls: `IToolExecuteContext.getWorkDir()` (`BashExecutor.java:64`) sets the
  working directory only; it does not chroot or constrain access outside it.
- Network controls: `HttpRequestExecutor` validates the host text pre-flight
  (`HttpRequestExecutor.java:78-107`) but the actual connection (and any redirect/proxy
  handling) is owned by `IHttpClient` / `HttpClientConfig`
  (`followRedirects`, `proxy` config at `HttpClientConfig.java:33,59,226-230`). The
  validator-to-transport seam is exactly where SSRF enforcement must attach. Note:
  `HttpClientConfig.dnsResolver` (`HttpClientConfig.java:66`) holds a public
  `IDnsResolver` (`IDnsResolver.java:38-49`) — a pre-existing resolver-injection point
  that Plan 336 should evaluate before declaring a public `IHttpClient` migration.
- Resolver/transport test seams: `IHttpClient` is an injectable interface
  (`HttpRequestExecutor.java:40-43`), so a fake client can assert "no request was sent for
  a denied target" — this is the seam Plan 336 will use for no-request transport assertions.

### DR-4: HTTP SSRF Enforcement (consumed by Plan 336)

#### DR-4a: SSRF Enforcement Decision (addresses M-4)

Live source facts that constrain the decision:

- `HttpRequestExecutor.validateUrl` (`HttpRequestExecutor.java:78-107`) parses the URL with
  `java.net.URI`, checks scheme (`http`/`https`), and rejects `localhost`, a regex of
  private/link-local IPs, and a `BLOCKED_HOSTS` set of cloud-metadata endpoints
  (`HttpRequestExecutor.java:33-36`). This is **pre-flight text validation only**.
- The actual connection is `httpClient.fetch(request, null)` (`HttpRequestExecutor.java:133`).
  `IHttpClient` implementations own redirect following (`HttpClientConfig.followRedirects`,
  `HttpClientConfig.java:33,226-230`) and proxy behavior (`HttpClientConfig.java:59`).
- Because the validator and the transport are separate components, the validator cannot see
  redirect hops, DNS-rebinding, or multi-address resolution: a request to a public hostname
  that 302-redirects to `169.254.169.254` passes pre-flight and is followed by the transport.

Required SSRF enforcement decision (Plan 336 must define all of):

| Requirement | Decision needed |
|-------------|-----------------|
| Enforcement layer | Validator-only (insufficient), transport-only (re-implement in every `IHttpClient`), or a single resolver+pinning layer that the transport is forced through? Does the `nop-http-api` public `IHttpClient` contract need to change? |
| Redirect ownership | Validator owns a "no-follow" decision, or transport exposes each redirect hop to a callback that re-validates? |
| Multi-address / DNS-rebinding policy | Resolve once and pin the connection to that IP (reject if the resolved set changes), or re-resolve per hop? Reject if any A/AAAA record is internal? |
| IPv4/IPv6 encoded representations | Decimal/octal/hex IPv4, `[::ffff:169.254.169.254]` IPv6, and unicode/IDN hosts must all normalize to the same internal/external verdict as the literal form. |
| Proxy policy | If an outbound proxy is configured, does the proxy bypass host validation (proxy is the egress) or must targets still be validated? |
| Connection-address pinning | After resolution, the socket MUST connect to a validated address; no re-resolution between validate and connect. |
| Fail-closed behavior | Unresolved host, policy-ambiguous target, or any unvalidated redirect hop MUST fail closed (no request sent). |
| Public HTTP API migration gate | If the chosen layer requires a new `IHttpClient` method or a mandatory resolver argument, that is a public-contract migration and needs its own approval. |

No-request transport assertion seam: because `IHttpClient` is injectable
(`HttpRequestExecutor.setHttpClient`), Plan 336 can substitute a fake client that records
whether `fetch` was called, proving a denied target never reached the transport.

**Pre-existing resolver seam (material to the public-API-migration decision).** A
DNS-resolver injection point already exists in `nop-http-api`:
`HttpClientConfig.dnsResolver` (`HttpClientConfig.java:66`) holds an
`io.nop.http.api.IDnsResolver`, and `IDnsResolver.resolve(String host)`
(`IDnsResolver.java:38-49`) returns `InetAddress[]` — a public contract. This means a
validating + pinning resolver can very likely be wired **without** changing the public
`IHttpClient` method surface: implement an `IDnsResolver` that rejects internal addresses
and returns a single pinned `InetAddress`, and inject it via `HttpClientConfig`. Plan 336
Phase 1 MUST evaluate this seam first; the "public HTTP API migration" answer is therefore
most likely **no**, but the successor must confirm the chosen `IHttpClient` implementation
actually consults `IDnsResolver` on every connection (including after redirects) before
declaring the migration unnecessary. Pinning beyond DNS (socket-level) and per-redirect-hop
re-validation still need design and may require implementation-specific (not
public-contract) changes.

## Conclusion

- The former Critical findings are either withdrawn (authentication),
  reclassified as deployment constraints (test defaults), or design premises
  (trusted DSL).
- The remaining remediation work is partitioned by independent security
  boundary into successor Plans 333 through 336. The decision records (DR-1
  through DR-4) above are the contract each successor consumes; they are
  specific enough that successor authors need not invent a contract — only
  resolve the explicit per-cell decisions and obtain user approval.
- Rejected approach: one combined implementation plan. It was rejected because
  authentication, ciphertext migration, process isolation, and socket-level
  SSRF prevention require different protected-area approvals, compatibility
  decisions, and proof methods.
- Follow-up: `ai-dev/plans/328-security-hardening-remediation-planning.md`
  (Phase 1 froze these records; Phase 2 created draft successors 333-336).

## Open Questions

These questions are the user-approval gates for the four successor plans. Each is now
owned by a decision record above; the successor plan cannot activate until the user
records a disposition.

- [ ] Which `servicePublic` tenant behavior is supported by product contract? — owned by DR-1b (Plan 333).
- [ ] Does the selected SSRF enforcement layer require a public HTTP API migration? — owned by DR-4a (Plan 336).
- [ ] Which Bash sandbox backend is supported in all target deployment modes? — owned by DR-3a (Plan 335).
- [ ] Is encrypted-value compatibility global to `AESTextCipher` or scoped to ORM/config consumers? — owned by DR-2b (Plan 334).
- [ ] Which default wins for `nop.auth.skip-check-for-admin` (`true` vs `false`)? — owned by DR-1e (Plan 333).
- [ ] What is the supported password-policy baseline (M-6)? — owned by DR-1d (Plan 333).
