# Security Audit Fix Batch 2 — Auth Findings + Test-Infra Regression

> Plan Status: completed
> Last Reviewed: 2026-09-18
> Mission: security-audit
> Work Item: 11. 修复批次2 — item 2 auth 审计发现 + 阻塞验证的 autoconfig 回归
> Source: `ai-dev/audits/security-audit/2026-09-18-auth-audit-AUTH-02.md` (F-A2-1),
> `2026-09-18-auth-audit-AUTH-06.md` (F-A6-1), item-2 Phase 2 test-run diagnosis (F-INFRA-1)
> Related: fix batch 1 (`2026-09-18-1910-4-...`, completed), roadmap Phase 3 block
> (user-authorized early fix phase, authorization note of 2026-09-18 applies here too)
> Naming: date-prefix per mission-subdirectory convention (see plan 1 header).

## Purpose

Fix the three actionable outcomes of the item-2 auth audit: the password-baseline
owner-doc drift (F-A2-1), the SSO logout token-validation gap (F-A6-1), and the
autoconfig regression that currently breaks the entire nop-auth-service test suite
(F-INFRA-1 — blocks item 2's Phase 2 verification exit criterion and the full-reactor
test gate).

## Current Baseline

Verified against live repo on 2026-09-18 (HEAD e0f4353115):

- F-INFRA-1: commit 99c0bc4f97 added `_vfs/nop/autoconfig/nop-ai-gateway.beans`
  (autoconfig = auto-discovered by every IoC container) importing
  `ai-gateway-defaults.beans.xml`, registering its FULL bean set into every
  container. `nop-auth-service`'s W6-2 test dependency excludes FOUR artifacts
  (nop-auth/nop-auth-service/pom.xml L137-154: nop-gateway, nop-ai-core,
  nop-ai-agent, nop-ai-dao) — so every auth-service container test dies during
  `CoreInitialization` (48 distinct failing classes, all `io.nop.auth.service.*`,
  `_tmp/security-audit/auth-phase2-tests.log`, exit 1). Beans referencing
  excluded-artifact classes (adversarial-review-verified, NON-exhaustive — IoC
  `initBeanTypes` iterates a TreeMap and aborts at the first failure, so the log
  only ever shows the lexicographically-first offender):
  `nopBackendMessageConverter_AI_DIALECT` (ioc:type nop-gateway),
  `nopAiGatewayFailoverInterceptor` (ioc:type nop-gateway),
  `nopAiRuleBasedSelectionStrategy` (class nop-ai-core RuleBasedSelectionStrategy),
  `nopFailoverCircuitBreaker` (nop-ai-core ThresholdBreaker),
  `nopFailoverConcurrencyRegistry` (nop-ai-core ConcurrencyRegistry),
  `nopChatServiceFailoverAdapter` (field/setter types nop-ai-core + non-optional
  ref `nopChatService` defined only in nop-ai-core's ai-defaults),
  `nopAiFailoverMetrics` (nop-ai-core CircuitState),
  `nopChannelSessionStore` (method param nop-ai-dao NopAiChannelSession).
  **Adjudication (B-1)**: option (a) — per-bean `ioc:condition`/`<on-class>`
  gating keyed on the missing classes' FQCNs, with rerun-driven iteration until
  the suite is green (restores the pre-99c0bc4f97 auth-test classpath behavior:
  gated beans skip when their classes are absent; production unchanged because
  nop-gateway/nop-ai-core/nop-ai-dao are compile deps of nop-ai-gateway and the
  conditions evaluate true there). Option (b) (narrowing the autoconfig import)
  rejected: it would undo the M7-P1 production auto-assembly intent.
- Fix shape precedent: `ioc:condition` + `<on-class>` child (auth-service.beans.xml:45-48
  redis stores). Evaluator semantics verified: missing class → condition false →
  bean silently skipped at container build (BeanConditionEvaluator.checkStaticCondition,
  isMissingClass → setDisabled(true)).
- Ref-graph constraint (M-1): `nopAiFailoverMetrics` is non-optionally ref'd by
  the (gated) interceptor and (gated) adapter — consistent gating required; the
  final gate set must leave no ungated bean holding a ref into a gated one
  (verify after iteration settles; `nopChannelConnectorManager` by-type collect
  over gated providers is a documented legal no-op).
- F-A6-1: `OAuthLoginServiceImpl.logoutAsync:189` parses a client-supplied token via
  2-arg `JwtHelper.parseToken` (signature+expiry only). Full overload
  (JwtHelper.java:116-153) validates iss/aud/typ with null-expected → skip semantics
  (L173-186); KID-less tokens rejected without legacyKey+grace (external IdP tokens
  carry kid). `SsoConfig` has `issuer` field (L23/97-101) but no `audience` field.
  Caveat: local `typ=access` must NOT be enforced on external IdP tokens (Keycloak
  uses `typ: "Bearer"`) — purpose isolation on this seam is addressed by iss+aud.
- F-A2-1: auth-and-permissions.md L420-428 + config table claim default baseline
  12/four-class; live wiring is 8/special-only (auth-core-defaults.beans.xml:28-32),
  TestPasswordPolicyBaseline:13 documents the relaxation as intentional.

## Goals

- F-STREAM-1 (added during execution — pre-existing HEAD regression blocking the
  full-reactor gate, user authorization covers it): `nop-stream` checkpoint
  checksum mismatch (deterministic; S1 CDC + distributed scenario E2Es fail).
  Root cause: commit 79250581fc replaced the complete "serialize→parseMap→
  serialize" text-round-trip fixed point in `computeCanonicalChecksumHex` with
  `normalizeNumbersDeep`, which passes POJO bean objects through unchanged —
  bean fields like `EnrichedTransaction.avgAmount` (BigDecimal scale-2) write
  as "100.00" but read-side parse→normalize yields 100 ⇒ checksum never
  converges. Fix: restore text round-trip BEFORE numeric normalization
  (both layers applied symmetrically on write/read sides). Regression test:
  bean-with-BigDecimal case in `TestCheckpointManifestChecksum`.
- F-AIWEB-1 (added during execution — second hidden pre-existing failure,
  stash-attributed to HEAD): `NopAiWebPagesTest` container build fails —
  `nopAiChatService` by-type autowires `IHttpClient` but nop-ai-web's test
  classpath has no concrete client module (nop-ai-core declares
  nop-http-client-jdk optional). Fix per established repo pattern
  (nop-ai-mcp-server pom precedent): test-scope `nop-http-client-jdk` dep in
  nop-ai-web/pom.xml.
- F-INFRA-1: gate the two gateway-typed beans with `ioc:condition`/`<on-class>` so
  they register only when `nop-gateway` classes are present (production unchanged —
  nop-gateway is a compile dep there; auth-service test classpath skips them).
- F-A6-1: switch `OAuthLoginServiceImpl.parseAuthToken` to the full overload with
  `config.getIssuer()` and new nullable `config.getAudience()` (null → skip each);
  typ left null (external IdP semantics); no legacy path (null key, 0 grace).
  Regression test: logoutAsync rejects a token with wrong issuer when issuer
  configured; accepts a correct token (reuse contract-test harness).
- F-A2-1: correct auth-and-perpermissions doc baseline section + config table to
  live defaults; present 12/four-class as recommended production override.

## Non-Goals

- No MFA/auth control changes (all verified clean).
- No nop-ai-gateway product redesign (condition gate is registration-only).
- No SSO aud/typ hard-mandate (config-driven opt-in per IdP reality).

## Scope

In: `nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`;
`nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/SsoConfig.java` +
`login/OAuthLoginServiceImpl.java` (+ test); `docs-for-ai/02-core-guides/auth-and-permissions.md`.
Out: everything else.

## Execution Plan

### Phase 1 - Fixes With Regression Tests

Status: completed
Targets: the three files above + tests

- Item Types: `Fix | Proof`

- [x] F-INFRA-1: add `<ioc:condition><on-class>{FQCN}</on-class></ioc:condition>` to
  each bean listed in Current Baseline (missing-artifact class per bean: gateway
  interfaces for the two gateway-typed beans; `io.nop.ai.core.routing.RuleBasedSelectionStrategy`
  / `io.nop.ai.core.reliability.ThresholdBreaker` (also for the adapter) /
  `io.nop.ai.core.routing.ConcurrencyRegistry` / `io.nop.ai.core.reliability.CircuitState`
  for the ai-core family; `io.nop.ai.dao.entity.NopAiChannelSession` for the
  session store), each with a one-line comment citing the auth-service W6-2
  test-classpath rationale. Iteration contract: rerun the auth suite after each
  pass and extend the gate set (or adjust) until exit 0 — the baseline list is
  explicitly non-exhaustive.
- [x] F-INFRA-1 verification (regression test = the suite itself): rebuild into
  local repo and rerun
  `./mvnw test -pl nop-service-framework/nop-biz-auth-core,nop-auth/nop-auth-service,nop-auth/nop-auth-sso -am`
  → exit 0, zero failing classes (48-fail baseline → 0). Also rerun
  `./mvnw test -pl nop-ai/nop-ai-gateway -am` to prove the conditions are
  transparent when the excluded artifacts ARE present. After the gate set
  settles, record the final ref-graph check: no ungated bean holds a ref into a
  gated bean (grep the file for `<ref` + collect lists).
- [x] F-A6-1: add nullable `audience` field (+getter/setter) to SsoConfig;
  `parseAuthToken` → full overload `(config.getIssuer(), config.getAudience(), null,
  null, 0)`.
- [x] F-A6-1 test (main approach per review Mi-2 — the contract test is plain
  JUnit with NO existing key/token harness): same-package unit test that
  self-mints an RS256 JWT with kid + issuer via `JwtHelper.genToken`, installs a
  stubbed `JWKPublicKeyLocator` (subclass overriding `getPublicKey`; field is
  protected, same-package accessible): (a) `config.issuer` set to a DIFFERENT
  issuer → parse/logout rejected with `ERR_JWT_INVALID_ISSUER`; (b) matching
  issuer → parses; (c) config issuer null → parses (skip semantics locked).
- [x] F-A2-1: doc fix (baseline defaults 8/special=1; strong baseline as
  recommended override; keep seeded-user note).

Exit Criteria:

- [x] All three fixes compile; new SSO test passes and asserts the issuer
  rejection error code (observable in-repo).
- [x] Auth-suite rerun exit 0 with zero failing classes (48-fail baseline
  eliminated; iteration log recorded if extra gates were needed); nop-ai-gateway
  suite green; final ref-graph check recorded.
- [x] Style: imports grouped; no unused imports; no silent no-op.
- [x] `ai-dev/logs/` entry updated.

### Phase 2 - Verification, Gates, Closure

Status: completed

- [x] Full-reactor baseline `./mvnw test -T 1C` (also serves item 2's closure gate).
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-gateway --severity high`
  and `--module nop-auth/nop-auth-sso --severity high` exit 0 (guide rule 5b).
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0; plan checklist tool
  (repo-root relative path) exit 0 pre- and post-completion.
- [x] Independent fresh-session closure audit; evidence into `## Closure`; roadmap
  item 11 `done` strictly after it; commit (mission format, task-owned files only).

Exit Criteria:

- [x] Closure audit ALLOW recorded; gates ticked; textual consistency holds.

## Closure Gates

- [x] All three findings fixed with evidence; zero dropped.
- [x] F-INFRA-1 proven by before/after suite results (48-fail → 0-fail) and
  gateway-side transparency run + final ref-graph no-dangling check.
- [x] F-A6-1 carries a regression test that observes issuer rejection.
- [x] Doc fix verified against live wiring (numbers match beans.xml).
- [x] `./mvnw test -T 1C` exit 0; both node gates exit 0.
- [x] Independent closure audit with evidence; Anti-Hollow (condition gate actually
  evaluated at container build — proven by the rerun; SSO parse path actually
  branch-changed — proven by the new test).
- [x] Textual consistency (plan/roadmap/log).

## Deferred But Adjudicated

### F-A4-1 concurrent-session policy

- Classification: `watch-only residual`
- Why Not Blocking Closure: documented platform limitation (AUTH-04); token TTL
  bounds sessions; successor = item 9 triage.
- Successor Required: no; Path: item 9.

## Non-Blocking Follow-ups

- Item 9 consolidation will fold auth findings (F-A6-1 fixed here; deployment
  constraints checklist: enc-key, action-auth enable, seed user, password override).

## Closure

Status Note: Fix batch 2 closed after independent closure audit ALLOW. All
five items (F-INFRA-1 autoconfig gating, F-A6-1 SSO issuer/audience, F-A2-1
doc baseline, F-STREAM-1 checkpoint checksum, F-AIWEB-1 nop-ai-web test dep)
fixed with live evidence; full reactor green (BUILD SUCCESS, exit 0).
Completed: 2026-09-18

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditor subagent
  (agent_1e0897a3, 39 tool uses), verdict **ALLOW**.
- Per-fix verification all PASS: 9-bean on-class gating with ref-graph
  no-dangling recheck; SsoConfig.audience + full-overload parse (7-arg,
  null-skip semantics); CheckpointSerDe round-trip+normalize fix with zero
  residual debug code; nop-ai-web test-scope dep; TestChannelScanBindLoginE2E
  error-code assertion.
- Tests PASS: TestOAuthLoginServiceImplContract 4/4 (3 new issuer cases,
  non-hollow RSA self-minted tokens); TestCheckpointManifestChecksum 15/15
  (new bean-BigDecimal case asserts store-hash == load-hash and verbatim
  write-side text); `_tmp/security-audit/full-reactor-tests3.log` FULL-EXIT:0
  / BUILD SUCCESS / zero failing lines; S1 E2E 1/1 + distributed 6/6.
- Anti-Hollow PASS: gating proven by 48-fail → 0-fail auth-suite transition;
  SSO branch change proven by ERR_JWT_INVALID_ISSUER assertion (impossible
  under old 2-arg overload); checksum fix proven end-to-end by fraud E2Es.
- Tool gates (auditor-run): check-plan-checklist --strict exit 0 (21/21
  ticked); check-doc-links --strict exit 0; scan-hollow --module
  nop-ai/nop-ai-gateway exit 0; --module nop-auth/nop-auth-sso exit 0.
- Auditor requirements applied: F-AIWEB-1 enumerated in Goals (this revision);
  this evidence block written BEFORE the Plan Status flip; roadmap item 11
  `planned → done` strictly after this audit.

Follow-up:

- F-A4-1 watch-only residual stays with item 9 triage. No remaining
  plan-owned work.
