# 328 Security Hardening Remediation Planning

> Plan Status: draft
> Last Reviewed: 2026-08-04
> Source: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`
> Related: `ai-dev/plans/00-plan-authoring-and-execution-guide.md`

## Purpose

Turn the confirmed security hardening findings into separately executable,
testable remediation plans. This is a planning and decision-gate plan: it does
not authorize or implement product behavior changes.

## Current Baseline

- Authentication is default-deny: `AuthFilterConfig.servicePublic` defaults to
  `false`; non-public actions without a user context are rejected by
  `GraphQLActionAuthChecker`.
- `JwtHelper` verifies signature and expiry. Access, refresh, and authorization
  code issuers/consumers need a complete purpose, issuer, and audience map
  before their public contract can change.
- `DefaultActionAuthChecker` injects `skip-check-for-admin` with a `true`
  fallback while `NopAuthConfigs` defines the same configuration with a
  `false` default.
- `AESTextCipher` defaults to AES-GCM with a reusable IV. Its ORM binder and
  config enhancer instantiate it without a per-message-IV contract.
- `BashExecutor` invokes `sh -c` or `cmd /c` and uses a destructive-command
  regex; `HttpRequestExecutor` validates host text before network execution.
- The security report classified demo SSO secrets and database passwords as
  Medium, but they share the same test/development-default rationale as the
  JWT key and default account. This classification must be corrected to a
  deployment configuration constraint before successor plans use the report.
- Weak password policy is a confirmed supported-baseline hardening concern and
  must be owned by the authentication successor plan.
- XLang DSL and IoC models are trusted developer-controlled artifacts. XLang
  sandboxing is not a remediation target unless a future contract enables
  non-developer DSL editing.

## Goals

- Correct the security analysis classification and create a durable source
  record for every confirmed remediation target.
- Produce one implementation-ready successor plan per independent security
  boundary, with source anchors, explicit compatibility decisions, tests, and
  owner-document obligations.
- Make protected-area approval and public-contract migration gates explicit
  before any implementation begins.

## Non-Goals

- Do not modify authentication, encryption, or AI tool production code.
- Do not sandbox trusted developer-controlled XLang DSLs.
- Do not remove test/development defaults; record their production deployment
  requirements as documentation/configuration responsibility.
- Do not perform a third-party dependency CVE scan.

## Scope

### In Scope

- Reclassify test/development secrets consistently in the durable security
  baseline.
- Create and independently review four successor implementation plans:
  authentication/browser boundary, encrypted-value format, Bash isolation, and
  HTTP SSRF egress validation.
- Record the required design and approval gates for each successor.

### Out Of Scope

- Product behavior changes and code implementation.
- Generated source changes.
- Secret-manager integration or secret-rotation tooling.

## Execution Plan

### Phase 1 - Establish Corrected Remediation Baseline

Status: planned
Targets: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`,
live source anchors

- Item Types: `Decision | Proof`

- [ ] Create the durable baseline analysis with an owner table for every
  confirmed High/Medium finding, including severity, live source anchor,
  classification, and unique successor-plan ownership.
- [ ] Reclassify demo SSO client-secret and database-password defaults to the
  same Low deployment-configuration class as the test JWT key and default
  account, with a recorded rationale.
- [ ] Build a complete JWT issuer/consumer matrix: access, refresh,
  authorization-code, and SSO token issuance/consumption; record expected
  issuer, audience, purpose, public-contract impact, and migration need.
- [ ] Build the `servicePublic` decision table: supported paths, anonymous
  operation types, explicit `@Auth(publicAccess = true)` behavior, SYS
  identity, data authorization, and tenant-header policy.
- [ ] Record AES encrypted-value format decision requirements: self-describing
  version marker, per-encryption IV generation, concurrency behavior, legacy
  format detection, key-derivation compatibility, and fail-closed behavior for
  unknown/truncated/tampered values.
- [ ] Record AI tool runtime facts needed for successor planning: available
  sandbox backends, filesystem/network/resource-limit controls, HTTP client
  redirect/proxy behavior, and resolver/transport test seams.
- [ ] Record a durable SSRF enforcement decision: enforcement layer,
  redirect ownership, multi-address/DNS-rebinding policy, proxy policy,
  connection-address pinning policy, and any required public HTTP API
  migration gate.
- [ ] Record separate durable decision sections for AES format compatibility
  and Bash backend admission/isolation policy.

Exit Criteria:

- [ ] Every confirmed High/Medium finding is either retained as a remediation
  target or explicitly reclassified with source evidence; weak password policy
  has explicit successor ownership in the durable owner table.
- [ ] The durable analysis record is the source for this plan and no plan-owned
  decision relies on `_tmp/` evidence.
- [ ] JWT, public-service, encryption, Bash, and HTTP-transport decisions are
  specific enough that successor plan authors need not invent a contract.
- [ ] No owner-doc update required because this phase changes no supported
  runtime behavior; the analysis record is updated as the decision evidence.
- [ ] Execution-day daily log entry is updated using the repository
  `{year}/{month-day}.md` convention.

### Phase 2 - Create Successor Implementation Plans

Status: planned
Targets: future successor plans 329 (authentication/browser), 330
(encrypted-value format), 331 (AI Bash isolation), and 332 (AI HTTP SSRF)

- Item Types: `Decision | Follow-up | Proof`

- [ ] Present the Phase 1 JWT, browser, `servicePublic`, password-policy,
  encrypted-value, Bash-backend, and HTTP-transport decision records to the
  user. Record an approved, rejected, or deferred outcome for each record.
- [ ] Create Plan 329 only after the user authorizes the protected `nop-auth`
  behavior contract. It will own JWT purpose/issuer/audience validation,
  admin-default consistency, `servicePublic`, OAuth redirect, cookie, state,
  and password-policy changes.
- [ ] Create Plan 330 only after the user approves the versioned
  per-message-IV encrypted-value contract. It must define legacy ciphertext
  fixtures, version/KDF selection, concurrent
  encryption semantics, failure semantics, ORM/config wiring, and migration.
- [ ] Create Plan 331 only after the user approves the AI Bash isolation
  contract. It must consume the selected runtime
  backend, define fail-closed behavior when unavailable, and require real
  isolation integration evidence rather than command-string blocking tests.
- [ ] Create Plan 332 only after the user approves the HTTP SSRF enforcement
  contract. It must consume the selected
  enforcement-layer decision and define resolved-address
  checks for every redirect hop, DNS/rebinding and multi-address policy,
  IPv4/IPv6 encoded-address policy, proxy behavior, and no-request transport
  assertions.
- [ ] Independently review each successor plan with the plan-guide imagination
  analysis before it is eligible to become active.

Exit Criteria:

- [ ] Every approved decision record has exactly one successor file with
  concrete source paths, module test commands, owner-document targets, and
  repo-observable exit criteria; rejected/deferred records have a documented
  reason and no successor is created.
- [ ] Plan 329 has an explicit `nop-auth` user-approval gate and any public API
  migration requirement is documented before implementation.
- [ ] Plan 330 defines legacy/new/tampered/truncated/concurrent ciphertext tests
  and entry-to-sink ORM/config wiring verification.
- [ ] Plan 331 defines real backend isolation proof and fail-closed behavior.
- [ ] Plan 332 defines resolver-to-transport wiring proof for internal and
  redirected destinations.
- [ ] Each successor has an independent review result with no plan-design
  Blocker before being marked active; Plan 329 may remain draft with an
  explicitly blocked implementation slice until user authorization is given.
- [ ] No owner-doc update required beyond successor-plan creation because this
  phase changes no supported runtime behavior.
- [ ] Execution-day daily log entry is updated using the repository
  `{year}/{month-day}.md` convention.

## Execution Constraints

- Plan 329 changes `nop-auth`, an AGENTS.md `ask-first` protected area. It must
  remain blocked until explicit user authorization to modify authentication
  behavior is received.
- Plan 330 changes persisted ciphertext semantics. It must not implement until
  the legacy-read/migration decision and regression fixture contract are set.
- Plans 331 and 332 are separate because command isolation and outbound network
  policy require different runtime boundaries and proof methods.
- No generated (`_`-prefixed) files may be manually changed.

## Closure Gates

- [ ] The durable security analysis record reflects the final severity and
  scope classification, including the demo-secret correction.
- [ ] Approved decision records have Plans 329 through 332, and rejected or
  deferred records have explicit user-approved disposition; every confirmed
  High/Medium finding has a traceable outcome in the durable baseline.
- [ ] Each successor has been independently reviewed and has no Blocker.
- [ ] No confirmed live defect is silently deferred or downgraded without a
  recorded classification and source evidence.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0 after document
  and plan updates.
- [ ] Independent closure audit verifies the predecessor/successor ownership
  chain and that no plan relies on `_tmp/` as sole evidence.

## Deferred But Adjudicated

### Trusted DSL Sandboxing

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: XLang models are trusted developer-controlled
  artifacts, equivalent to IoC configuration. A sandbox is required only if a
  future product contract exposes editable DSL content to non-developer users.
- Successor Required: `no`
- Successor Path: `N/A`

### Test-Friendly Default Configuration

- Classification: `watch-only residual`
- Why Not Blocking Closure: Default keys, default account, demo SSO secret,
  and demo database password are explicit test/development conveniences.
  Production deployment requirements are documentation/configuration
  responsibility, not framework-code defects.
- Successor Required: `no`
- Successor Path: `N/A`

## Non-Blocking Follow-ups

- Run a separate dependency-CVE audit after the source remediation plans close.
- Evaluate platform-wide secret-manager integration as a separate design
  decision.

## Closure

Status Note: Draft only; no implementation or successor-plan creation has
begun.
Completed: N/A

Draft Review Evidence:

- Reviewer / Agent: `ses_033d89e2cffex7acfofsMdPpbd` (draft review)
- Evidence: Original combined implementation plan was rejected as too broad;
  this revision splits remediation boundaries and adds durable evidence and
  decision gates. A fresh review is still required before this plan can become
  active or complete.

Closure Audit Evidence:

- Reviewer / Agent: N/A
- Evidence: N/A

Follow-up:

- Present the Phase 1 decision records for user disposition before creating any
  successor implementation plan.
