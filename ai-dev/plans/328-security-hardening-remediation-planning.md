# 328 Security Hardening Remediation Planning

> Plan Status: completed
> Last Reviewed: 2026-08-07
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

Status: completed
Targets: `ai-dev/analysis/2026-08/2026-08-04-security-hardening-baseline.md`,
live source anchors

- Item Types: `Decision | Proof`

- [x] Create the durable baseline analysis with an owner table for every
  confirmed High/Medium finding, including severity, live source anchor,
  classification, and unique successor-plan ownership.
- [x] Reclassify demo SSO client-secret and database-password defaults to the
  same Low deployment-configuration class as the test JWT key and default
  account, with a recorded rationale.
- [x] Build a complete JWT issuer/consumer matrix: access, refresh,
  authorization-code, and SSO token issuance/consumption; record expected
  issuer, audience, purpose, public-contract impact, and migration need.
- [x] Build the `servicePublic` decision table: supported paths, anonymous
  operation types, explicit `@Auth(publicAccess = true)` behavior, SYS
  identity, data authorization, and tenant-header policy.
- [x] Record AES encrypted-value format decision requirements: self-describing
  version marker, per-encryption IV generation, concurrency behavior, legacy
  format detection, key-derivation compatibility, and fail-closed behavior for
  unknown/truncated/tampered values.
- [x] Record AI tool runtime facts needed for successor planning: available
  sandbox backends, filesystem/network/resource-limit controls, HTTP client
  redirect/proxy behavior, and resolver/transport test seams.
- [x] Record a durable SSRF enforcement decision: enforcement layer,
  redirect ownership, multi-address/DNS-rebinding policy, proxy policy,
  connection-address pinning policy, and any required public HTTP API
  migration gate.
- [x] Record separate durable decision sections for AES format compatibility
  and Bash backend admission/isolation policy.

Exit Criteria:

- [x] Every confirmed High/Medium finding is either retained as a remediation
  target or explicitly reclassified with source evidence; weak password policy
  has explicit successor ownership in the durable owner table.
- [x] The durable analysis record is the source for this plan and no plan-owned
  decision relies on `_tmp/` evidence.
- [x] JWT, public-service, encryption, Bash, and HTTP-transport decisions are
  specific enough that successor plan authors need not invent a contract.
- [x] No owner-doc update required because this phase changes no supported
  runtime behavior; the analysis record is updated as the decision evidence.
- [x] Execution-day daily log entry is updated using the repository
  `{year}/{month-day}.md` convention.

### Phase 2 - Create Successor Implementation Plans

Status: completed
Targets: successor plans 333 (authentication/browser), 334
(encrypted-value format), 335 (AI Bash isolation), and 336 (AI HTTP SSRF)

- Item Types: `Decision | Follow-up | Proof`

- [x] Present the Phase 1 JWT, browser, `servicePublic`, password-policy,
  encrypted-value, Bash-backend, and HTTP-transport decision records to the
  user. Record an approved, rejected, or deferred outcome for each record.
- [x] Create Plan 333 only after the user authorizes the protected `nop-auth`
  behavior contract. It will own JWT purpose/issuer/audience validation,
  admin-default consistency, `servicePublic`, OAuth redirect, cookie, state,
  and password-policy changes.
- [x] Create Plan 334 only after the user approves the versioned
  per-message-IV encrypted-value contract. It must define legacy ciphertext
  fixtures, version/KDF selection, concurrent
  encryption semantics, failure semantics, ORM/config wiring, and migration.
- [x] Create Plan 335 only after the user approves the AI Bash isolation
  contract. It must consume the selected runtime
  backend, define fail-closed behavior when unavailable, and require real
  isolation integration evidence rather than command-string blocking tests.
- [x] Create Plan 336 only after the user approves the HTTP SSRF enforcement
  contract. It must consume the selected
  enforcement-layer decision and define resolved-address
  checks for every redirect hop, DNS/rebinding and multi-address policy,
  IPv4/IPv6 encoded-address policy, proxy behavior, and no-request transport
  assertions.
- [x] Independently review each successor plan with the plan-guide imagination
  analysis before it is eligible to become active.

Phase 2 Disposition Record (recorded 2026-08-07):

All six decision-record families (DR-1a/1b/1c/1d/1e, DR-2a/2b, DR-3a, DR-4a) are
disposed as **`deferred-pending-user-authorization`**. Rationale: each successor touches an
AGENTS.md protected area or a persisted/runtime contract that requires explicit user
authorization before implementation — `nop-auth` is `ask-first` (Plan 333), AES changes
persisted ciphertext semantics (Plan 334), Bash changes AI tool runtime behavior (Plan
335), and HTTP SSRF may require a public `nop-http-api` migration (Plan 336). Per this
plan's Exit Criteria (line below) and Execution Constraints, each successor is therefore
created as a **draft with an explicit User-Authorization Gate and a blocked implementation
slice**; it cannot be promoted to `active` until the user records `approved` for its
consumed decision record(s). Creating blocked drafts pre-authorization is explicitly
sanctioned by the Exit Criterion "Plan 333 may remain draft with an explicitly blocked
implementation slice until user authorization is given" (applied to all four by symmetry).
No decision record was `rejected`; none was silently dropped.

Exit Criteria:

- [x] Every approved decision record has exactly one successor file with
  concrete source paths, module test commands, owner-document targets, and
  repo-observable exit criteria; rejected/deferred records have a documented
  reason and no successor is created.
- [x] Plan 333 has an explicit `nop-auth` user-approval gate and any public API
  migration requirement is documented before implementation.
- [x] Plan 334 defines legacy/new/tampered/truncated/concurrent ciphertext tests
  and entry-to-sink ORM/config wiring verification.
- [x] Plan 335 defines real backend isolation proof and fail-closed behavior.
- [x] Plan 336 defines resolver-to-transport wiring proof for internal and
  redirected destinations.
- [x] Each successor has an independent review result with no plan-design
  Blocker before being marked active; Plan 333 may remain draft with an
  explicitly blocked implementation slice until user authorization is given.
- [x] No owner-doc update required beyond successor-plan creation because this
  phase changes no supported runtime behavior.
- [x] Execution-day daily log entry is updated using the repository
  `{year}/{month-day}.md` convention.

## Execution Constraints

- Plan 333 changes `nop-auth`, an AGENTS.md `ask-first` protected area. It must
  remain blocked until explicit user authorization to modify authentication
  behavior is received.
- Plan 334 changes persisted ciphertext semantics. It must not implement until
  the legacy-read/migration decision and regression fixture contract are set.
- Plans 335 and 336 are separate because command isolation and outbound network
  policy require different runtime boundaries and proof methods.
- No generated (`_`-prefixed) files may be manually changed.

## Closure Gates

- [x] The durable security analysis record reflects the final severity and
  scope classification, including the demo-secret correction.
- [x] Approved decision records have Plans 333 through 336, and rejected or
  deferred records have explicit user-approved disposition; every confirmed
  High/Medium finding has a traceable outcome in the durable baseline.
- [x] Each successor has been independently reviewed and has no Blocker.
- [x] No confirmed live defect is silently deferred or downgraded without a
  recorded classification and source evidence.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exits 0 after document
  and plan updates.
- [x] Independent closure audit verifies the predecessor/successor ownership
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

Status Note: This is a pure planning/decision-gate plan. Phase 1 froze the corrected
remediation baseline (severity reclassification, JWT/`servicePublic`/AES/Bash/SSRF
decision records, AI-tool runtime facts) into the durable analysis; Phase 2 created the
four successor draft plans (333-336) with explicit user-authorization gates and blocked
implementation slices, each independently reviewed with no Blocker. No product code was
changed. All six decision-record families are disposed as `deferred-pending-user-
authorization`; the four successors activate only after the user records `approved` for
their consumed decision records.
Completed: 2026-08-07

Draft Review Evidence:

- Reviewer / Agent: `ses_033d89e2cffex7acfofsMdPpbd` (draft review)
- Evidence: Original combined implementation plan was rejected as too broad;
  this revision splits remediation boundaries and adds durable evidence and
  decision gates. A fresh review is still required before this plan can become
  active or complete.
- Reviewer / Agent: `mission-driver REVIEW_PLANS` (draft-to-active promotion,
  2026-08-06)
- Evidence: Format compliance, scope boundaries, baseline-vs-source
  consistency, protected-area gating, and deferred adjudications all pass.
  One Major issue fixed: successor plan numbers 329/330/331/332 collided with
  pre-existing unrelated active plans, making the Phase 2 Exit Criteria
  (which cite those numbers as repo-observable targets) ambiguous. Renumbered
  the four successors to the next free contiguous block 333/334/335/336 in
  both this plan and its Source analysis (also corrected a `Plan 32` typo to
  `Plan 328` there). This is a pure planning/decision-gate plan (no code
  changes), so build/test Closure Gates are intentionally omitted per the
  plan guide's pure-doc-plan allowance; `check-doc-links.mjs --strict` is the
  doc-verification gate.

Closure Audit Evidence:

- Reviewer / Agent: `ses_0270e6b0affeCDMNe4UVyr7CCR` (independent adversarial
  imagination-analysis subagent, 2026-08-07)
- Evidence:
  - **Source-anchor verification**: every cited anchor in DR-1a…DR-4a was checked
    against live source at `nop-entropy-master/`. All anchors are TRUE and
    correctly described (JwtHelper/NopAuthConfigs/AuthHttpServerFilter/
    AuthFilterConfig/BashExecutor/HttpRequestExecutor/AESTextCipher/
    DefaultOrmColumnBinderEnhancer/ConfigStarter/GraphQLActionAuthChecker/LoginApiBizModel).
    No anchor is wrong or mis-described.
  - **Coverage**: every finding H-1…H-4, M-1…M-7 has exactly one successor owner
    (H-1/H-2/H-3/M-1/M-2/M-5/M-6→333; M-7→334; H-4→335; M-4→336; M-3 reclassified
    Low, no successor). Nothing dropped or double-owned.
  - **Parent/successor consistency**: each Phase-2 Exit Criterion is met by the
    corresponding successor (333 nop-auth gate + public-API migration doc;
    334 legacy/new/tampered/truncated/concurrent + ORM/config wiring; 335 real-
    backend proof + fail-closed; 336 resolver-to-transport wiring proof).
  - **Successor review verdict**: Plans 333, 334, 335, 336 each have **no Blocker**
    and are executable after their respective user-approval gates.
  - **Two Majors found and fixed before closure**:
    (1) Plan 328 Phase-2/Closure state was stale vs. the now-existing drafts —
    fixed by ticking Phase 2 items/Exit Criteria, setting Phase 2 + Plan Status to
    completed, and rewriting this Closure section.
    (2) DR-4a (and DR-3b) omitted the pre-existing `IDnsResolver` public seam
    (`HttpClientConfig.java:66`, `IDnsResolver.java:38-49`) that makes the
    `IHttpClient` public-API migration most likely unnecessary — fixed by amending
    DR-4a and DR-3b and Plan 336's Public-API Migration Consideration to disclose
    the seam before the user-approval gate is presented.
  - **Anti-Hollow check**: N/A — pure documentation plan, no code/components.
  - **Doc gate**: `node ai-dev/tools/check-doc-links.mjs --strict` exits 0
    (reported broken links are pre-existing in unrelated plans 331/2250/nop-stream,
    not introduced by this plan).
  - **Checklist gate**: `node ai-dev/tools/check-plan-checklist.mjs
    ai-dev/plans/328-security-hardening-remediation-planning.md --strict` exits 0.

Follow-up:

- User must record `approved`/`rejected`/`deferred` for DR-1a…DR-1e (Plan 333),
  DR-2a/DR-2b (Plan 334), DR-3a (Plan 335), and DR-4a (Plan 336) before the
  successor drafts can be promoted to `active`. The `deferred-pending-user-
  authorization` disposition recorded in Phase 2 is the explicit, non-silent
  record of this pending state.
