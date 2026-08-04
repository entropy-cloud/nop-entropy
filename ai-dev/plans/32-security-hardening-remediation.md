# 32 Security Hardening Remediation Planning

> Plan Status: draft
> Last Reviewed: 2026-08-04
> Source: Live source anchors in Current Baseline; `_tmp/security-analysis.md` v3
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

- Reclassify test/development secrets consistently in the security analysis.
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
Targets: `_tmp/security-analysis.md`, live source anchors, `ai-dev/analysis/`

- Item Types: `Decision | Proof`

- [ ] Correct the analysis classification of demo SSO client-secret and
  database-password defaults to the same Low deployment-configuration class as
  the test JWT key and default account.
- [ ] Move or reproduce the final security analysis in a durable
  `ai-dev/analysis/` record, including its v3 corrections and concrete source
  anchors; do not retain `_tmp/` as the only plan source.
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

Exit Criteria:

- [ ] Every confirmed High/Medium finding is either retained as a remediation
  target or explicitly reclassified with source evidence; weak password policy
  has explicit successor ownership.
- [ ] The durable analysis record and plan no longer depend on `_tmp/` as their
  only evidence source.
- [ ] JWT, public-service, encryption, Bash, and HTTP-transport decisions are
  specific enough that successor plan authors need not invent a contract.
- [ ] No owner-doc update required beyond the analysis record because this phase
  changes no supported runtime behavior.
- [ ] `ai-dev/logs/` corresponding date entry is updated.

### Phase 2 - Create Successor Implementation Plans

Status: planned
Targets: `ai-dev/plans/33-authentication-browser-hardening.md`,
`ai-dev/plans/34-encrypted-value-format-hardening.md`,
`ai-dev/plans/35-ai-bash-isolation.md`,
`ai-dev/plans/36-ai-http-ssrf-hardening.md`

- Item Types: `Decision | Follow-up | Proof`

- [ ] Create Plan 33 for JWT purpose/issuer/audience validation, admin-default
  consistency, `servicePublic` tenant contract, OAuth redirect validation,
  secure-cookie behavior, constant-time state comparison, and password-policy
  hardening. Mark it `blocked` until the user explicitly authorizes protected
  `nop-auth` behavior changes.
- [ ] Create Plan 34 for the versioned per-message-IV encrypted-value format.
  It must define legacy ciphertext fixtures, version/KDF selection, concurrent
  encryption semantics, failure semantics, ORM/config wiring, and migration.
- [ ] Create Plan 35 for AI Bash isolation. It must select a supported runtime
  backend, define fail-closed behavior when unavailable, and require real
  isolation integration evidence rather than command-string blocking tests.
- [ ] Create Plan 36 for AI HTTP SSRF hardening. It must define resolved-address
  checks for every redirect hop, DNS/rebinding and multi-address policy,
  IPv4/IPv6 encoded-address policy, proxy behavior, and no-request transport
  assertions.
- [ ] Independently review each successor plan with the plan-guide imagination
  analysis before it is eligible to become active.

Exit Criteria:

- [ ] All four successor files exist with concrete source paths, module test
  commands, owner-document targets, and repo-observable exit criteria.
- [ ] Plan 33 has an explicit user-approval gate and any public API migration
  requirement is documented before implementation.
- [ ] Plan 34 defines legacy/new/tampered/truncated/concurrent ciphertext tests
  and entry-to-sink ORM/config wiring verification.
- [ ] Plan 35 defines real backend isolation proof and fail-closed behavior.
- [ ] Plan 36 defines resolver-to-transport wiring proof for internal and
  redirected destinations.
- [ ] Each successor has an independent review result with no Blocker before
  being marked active.
- [ ] No owner-doc update required beyond successor-plan creation because this
  phase changes no supported runtime behavior.
- [ ] `ai-dev/logs/` corresponding date entry is updated.

## Execution Constraints

- Plan 33 changes `nop-auth`, an AGENTS.md `ask-first` protected area. It must
  remain blocked until explicit user authorization to modify authentication
  behavior is received.
- Plan 34 changes persisted ciphertext semantics. It must not implement until
  the legacy-read/migration decision and regression fixture contract are set.
- Plans 35 and 36 are separate because command isolation and outbound network
  policy require different runtime boundaries and proof methods.
- No generated (`_`-prefixed) files may be manually changed.

## Closure Gates

- [ ] The durable security analysis record reflects the final severity and
  scope classification, including the demo-secret correction.
- [ ] Plans 33 through 36 exist and explicitly own every confirmed High/Medium
  remediation target from the corrected analysis.
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

Closure Audit Evidence:

- Reviewer / Agent: `ses_033d89e2cffex7acfofsMdPpbd` (draft review)
- Evidence: Original combined implementation plan was rejected as too broad;
  this revision splits the remediation boundaries and records the required
  protected-area and compatibility gates. A fresh review is still required
  before this plan can become active or complete.

Follow-up:

- Create and review Plans 33 through 36 after the Phase 1 baseline decisions.
