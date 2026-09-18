# Security Audit Plan 4 — Service Framework / API Layer Controls Assurance Audit

> Plan Status: draft
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 4. 服务框架与API层审计 (`nop-service-framework`, `nop-dyn`)
> Source: `ai-dev/backlog/security-audit-roadmap.md` (item 4, stages API-01..API-04)
> Related: plans 1-3 (this mission, completed/in-progress), plan 333 (auth browser),
> `docs-for-ai/02-core-guides/api-and-graphql.md`, `service-layer.md`
> Naming: date-prefix per mission-subdirectory convention (plan 1 header note).

## Execution Rules And Evidence Contract

Same contract as plan 3 (read `ai-dev/audits/README.md` + owner docs first;
dual severity labels CRITICAL/HIGH/MEDIUM/LOW + P0-P3; no real secrets in
reports; Fix handoff fields for every confirmed finding — bare "deferred to
consolidation" insufficient; roadmap item 4 → `planned` at execution entry;
task-owned-change assertion against recorded starting git status; no commit
unless requested).

## Purpose

Read-only control-effectiveness audit of the API exposure surface (roadmap
item 4): GraphQL engine guards, action-auth checking, field-level visibility,
distributed proxy `/px/` — plus the `nop-dyn` `runWithoutTenantId` standing
re-verification attached by CORE-05 F-C5-1. Produce severity-classified
reports for item 9 consolidation. Audit only — no product changes.

## Current Baseline

Verified against live repo on 2026-09-18:

- Items 1-2 audited (CORE + AUTH reports under `ai-dev/audits/security-audit/`);
  item 4 depends only on item 1 (done). GraphQL engine classes:
  `nop-service-framework/nop-graphql/nop-graphql-core/.../engine/` —
  `GraphQLExecutor` (operation-MFA + restricted-session checkpoints, verified
  auth-side in AUTH-03), `GraphQLActionAuthChecker`,
  `GraphQLEngine`/`IGraphQLEngine`. API model auth metadata:
  `ActionAuthMeta` (publicAccess/roles/permissions), `@Auth` annotation,
  biz-model builder `ReflectionBizModelBuilder` (MfaRequired constraints).
- `nop-service-framework/` subgroups: graphql (nop-graphql-core, -grpc,
  -message, -orm), biz, gateway (`nop-gateway` = message-routing gateway, NOT
  the /px/ proxy owner), biz-auth-api/core (done in item 2). Field visibility: xmeta `published`/`auth` props, `not-pub` tagSet
  chain (verified auth-side for MFA tables). `nop-dyn` call sites verified
  justified in CORE-05; item 4 owns standing re-verification.
- Known defaults to verify: action-auth default off (AUTH-05 verified the
  checker; item 4 verifies ENGINE-side enforcement when enabled);
  `nop.graphql.schema-introspection.enabled` (default false,
  GraphQLConfigs.java:53); max-depth=7 / max-operation-count=10 /
  parse-max-length (GraphQLConfigs); audit flag `nop.auth.graphql.enable-audit`
  (consumed in nop-auth-service, TestGraphQLLogger).
- Test baselines: nop-graphql-core / nop-biz / nop-gateway / nop-dyn suites
  exist at HEAD; fresh green evidence comes from the fix-batch-2 full-reactor
  run recorded in the daily log.

## Goals

- API-01: GraphQL endpoint guards — query depth/complexity limits, batch-size
  limits, introspection exposure, error-message leakage; classify each as
  enforced-by-default / configurable / absent.
- API-02: Action auth checking — `GraphQLActionAuthChecker` invocation points
  (RPC + document paths), `ActionAuthMeta` propagation, publicAccess semantics,
  fail behavior when checker bean absent (fail-open/closed), and enforcement
  when `enable-action-auth=true` (engine side; checker semantics done in AUTH-05).
- API-03: Field-level visibility — xmeta `published`/`internal`/`auth`
  enforcement in the GraphQL schema build + selection validation; biz-loader
  masking interplay; sensitive-field default exposure warning from
  auth-and-permissions.md (敏感字段默认全开).
- API-04: Distributed proxy `/px/` — core logic at
  `GraphQLWebService.runProxy`
  (`nop-service-framework/nop-graphql/nop-graphql-core/.../web/GraphQLWebService.java`);
  boundary-crossing reads into the HTTP-layer starters mounting the endpoint
  (`nop-quarkus/nop-quarkus-web/.../QuarkusGraphQLWebService.java`,
  `nop-spring/nop-spring-web-starter/.../SpringGraphQLWebService.java`) for
  token forwarding / header sanitization. `nop-gateway` is a separate
  message-routing gateway (zero `/px` references repo-wide) — audited for its
  forwarding-model security only, NOT as the /px/ owner.
- DYN-01 (from CORE-05 F-C5-1): re-verify the three `nop-dyn`
  `runWithoutTenantId` sites remain justified + scoped (standing owner).
- Produce `ai-dev/audits/security-audit/` reports `{date}-api-audit-API-0N`
  (N=1..4) + `{date}-api-audit-DYN-01`, severity-classified, ready for item 9.

## Non-Goals

- No product changes; no re-audit of auth-module checkers (AUTH-05), JWT
  (AUTH-01), ORM tenant isolation (CORE-05 mechanism), or biz-auth-core
  (item 2 complete). No E2E browser testing (plan 333 scope).

## Scope

In: `nop-service-framework/` (nop-graphql/*, nop-biz, nop-gateway),
`nop-dyn/nop-dyn-service`, their `_vfs` DSL resources + `_gen` spot check,
`@cfg:` secret sampling. Reports to mission audit dir.
Out: auth modules, credential, AI/network/file modules (items 2/3/6/7/8).

## Execution Plan

### Phase 1 - Evidence Collection

Status: planned
Targets: `nop-service-framework/`, `nop-dyn/` (read-only), `_tmp/security-audit/`

- Item Types: `Proof | Follow-up`

- [ ] Control inventory (engine guards, checker, meta propagation, gateway
  proxy, dyn sites) → `_tmp/security-audit/api-inventory.md`.
- [ ] QA static analysis (checkstyle/pmd/compile+spotbugs full GAV, report-only,
  read outputs) for `nop-service-framework,nop-dyn` → `_tmp/security-audit/`.
- [ ] Config-default inventory: graphql depth/complexity/introspection/audit
  flags, gateway proxy flags, with live anchors.
- [ ] Secret-leak sweep (Java + `_vfs`) + `_gen` spot check.
- [ ] Test-coverage map (graphql/biz/gateway/dyn suites).
- [ ] No task-owned product change (relative to recorded start status).

Exit Criteria:
- [ ] Inventory exists, every entry backed by a live repo path.
- [ ] QA outputs captured AND read; every security-rule hit registered or dispositioned.
- [ ] Config-default inventory complete with file:line anchors.
- [ ] Secret sweep (Java + _vfs) has a disposition per flagged key; _gen spot verdicts recorded.
- [ ] Test-coverage map complete with explicit gap notes.
- [ ] No task-owned product change vs recorded starting git status.
- [ ] `ai-dev/logs/` entry updated.

### Phase 2 - Control-Effectiveness Review (API-01..04, DYN-01)

Status: planned
Targets: `ai-dev/audits/security-audit/` reports

- Item Types: `Proof | Decision | Follow-up`

- [ ] API-01 endpoint guards review; [ ] API-02 action-auth engine review;
- [ ] API-03 field visibility review; [ ] API-04 /px/ proxy review;
- [ ] DYN-01 dyn sites re-verification;
- [ ] Five reports written with findings tables + no-finding statements;
- [ ] Cross-check vs items 1-3 reports (no double ownership).

Exit Criteria:
- [ ] Five reports exist with repo-resolvable anchors; every inventory entry classified exactly once.
- [ ] Findings carry Fix handoff fields (no bare "deferred to consolidation").
- [ ] Focused tests pass: `./mvnw test -pl nop-service-framework/nop-graphql/nop-graphql-core,nop-service-framework/nop-gateway,nop-dyn/nop-dyn-service -am` (paths verified live); results recorded.
- [ ] End-to-end evidence: authenticated RPC through the engine exercising enforcement — existing auth-module E2E suites (engine-routed per AUTH-03) are consumer evidence; engine-side action-auth assertion has NO dedicated graphql-core test (record as explicit coverage gap per evidence contract, not as satisfied).
- [ ] `ai-dev/logs/` entry updated.

### Phase 3 - Adjudication, Roadmap Sync, Closure

Status: planned
Targets: roadmap, this plan, daily log

- Item Types: `Decision | Proof`

- [ ] Adjudicate all findings (plan-328 precedent for defaults);
- [ ] Roadmap item 4 `planned` (at entry) → `done` only post-closure;
- [ ] Reports self-contained; log entry.

Exit Criteria:
- [ ] Zero findings remain "pending"; each has remediation-target / adjudicated-no-fix with reason.
- [ ] Roadmap item 4 transitions correct (planned at entry; done only post-closure-audit).
- [ ] Reports self-contained (no _tmp-only evidence).
- [ ] `ai-dev/logs/` entry updated.

## Closure Gates

- [ ] Five deliverables exist, severity-classified, repo-resolvable anchors.
- [ ] Every confirmed finding adjudicated with Fix handoff; nothing silently deferred.
- [ ] Roadmap item 4 `done` strictly after independent closure audit.
- [ ] Independent sub-agent closure audit + evidence in `## Closure`.
- [ ] Anti-Hollow check (anchors re-verified; no stale findings).
- [ ] Textual consistency.
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-18-2340-6-api-layer-controls-audit.md --strict` exit 0.
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.

## Deferred But Adjudicated

### Field-level default exposure (敏感字段默认全开)

- Classification: `watch-only residual` (documented platform semantics)
- Why Not Blocking Closure: documented in auth-and-permissions.md as a
  declaration-layer sync requirement; API-03 verifies enforcement mechanics,
  the default-open posture is a design decision for item 9 triage.
- Successor Required: no; Path: item 9.

## Non-Blocking Follow-ups

- Reusable grep patterns to `ai-dev/skills/` if applicable.

## Closure

Status Note: (pending)
Completed: (pending)
Closure Audit Evidence: (pending)
Follow-up: (pending)
