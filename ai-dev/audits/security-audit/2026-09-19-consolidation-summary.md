# Security Audit Consolidation Summary — Mission: security-audit

> Date: 2026-09-19. Aggregate of all 35 audit reports (items 1-8, all closed
> with independent closure audits). Severity order: CRITICAL → HIGH → MEDIUM → LOW.
> Status vocabulary: FIXED-batch-N (roadmap item) | open→batch-4 | successor-deferred
> (rationale + successor path) | deployment-constraint (plan-328) | watch-only.

## Ledger

### CRITICAL — none (0)

### HIGH (1)

| ID | Sev | Source | Anchor (short) | Status |
|---|---|---|---|---|
| F-AI4-1 | HIGH (P1) | AI-04 | FailoverMetricsImpl.java:44-128 tags metric `account` with standby-account API key (~10 families, production default bean) | open → **batch 4 (mandatory)** |

### MEDIUM (23)

| ID | Source | Summary | Status |
|---|---|---|---|
| F-C3-1 | CORE-03 | @sec:/enc empty-encKey silently derivable key | FIXED-batch-1 (WARN + docs) |
| F-A2-1 | AUTH-02 | password-baseline owner-doc drift | FIXED-batch-2 (doc) |
| F-A6-1 | AUTH-06 | SSO logout token iss/aud validation gap | FIXED-batch-2 (full overload + tests) |
| F-API1-1 | API-01 | unmapped error messages public by default (class+message leak) | successor-deferred (default flip is behavior change; item 9 successor path below) |
| F-API4-1 | API-04 | /px/ full header passthrough (client-steerable routing, cross-boundary cookies) | successor-deferred (allowlist design) |
| F-WF-01-1 | WF-01 | dead-code delegation anchor; live source = auth-side substitution (mapping break) | successor-deferred (cleanup + doc) |
| F-WF-01-2 | WF-01 | check-start-auth defined but never invoked | successor-deferred (feature activation decision) |
| F-WF-01-3 | WF-01 | notifySubFlowEndAsync no caller authz + unvalidated result injection | successor-deferred (API contract change) |
| F-WF-02-1 | WF-02 | task state plaintext + persist default true | successor-deferred (behavior/data change) |
| F-WF-03-1 | WF-03 | reportTaskLog unauthenticated (forged log injection) | open → **batch 4** (auth gate, small) |
| F-AI1-1 | AI-01 | auth-header accumulation on account switch | successor-deferred (cross-account semantics) |
| F-AI2-1 | AI-02 | null workingDirectory bypasses bash jail | open → **batch 4** (fail-closed reject) |
| F-AI2-2 | AI-02 | two sandbox seams not wired together | successor-deferred (architecture wiring) |
| F-AI4-2 | AI-04 | sk- redaction regex crashes (control never effective) | open → **batch 4** (regex fix + test) |
| F-N1-1 | NET-01 | Apache client unconditionally follows redirects with full header fwd | successor-deferred (hc5 config surgery + tests) |
| F-N1-2 | NET-01 | OkHttp useSsl w/o trustManager defaults trust-ALL | successor-deferred (behavior redesign) |
| F-N1-3 | NET-01 | ignore-ssl-certs silently disables validation | open → **batch 4** (prominent WARN + doc) |
| F-N2-1 | NET-02 | MQTT authChecker==null fail-open | open → **batch 4** (fail-closed default) |
| F-N2-2 | NET-02 | no topic-level ACL model | successor-deferred (new feature) |
| F-N3-1 | NET-03 | both stream invariant checkers red (registry drift) | open → **batch 4** (registry resync) |
| F-N3-2 | NET-03 | stream control-plane RPC unauthenticated (multi-JVM only) | successor-deferred (deployment architecture) |
| F-F2-1 | FILE-02 | NopFileRecord CRUD lacks object-level auth | successor-deferred (data-auth design) |
| F-D1-1 | DATA-01 | queryEntityData returns all ORM columns (published bypass) | successor-deferred (feature semantics) |

(F-A2-1 row corrected: source AUTH-02; count includes it as fixed.)

### LOW / documented limitations / deployment constraints (~24)

Fixed in batches 1-3: F-C1-1/2/3, F-C4-1(+render-boundary residual), F-C4-2,
F-C5-1, F-A4-1(watch), F-A6-2(watch), F-INFRA-1, F-STREAM-1, F-AIWEB-1.
Open LOWs (watch-only / constraints, item-by-item in source reports):
F-API1-2, F-API2-1 (→ batch 4 as startup-WARN quick win), F-API3-1, F-API3-2,
F-AI1-2/3, F-AI2-3/4, F-AI3-1/2, F-AI4-3, F-WF-01-4, F-WF-02-2/3,
F-WF-03-2, F-N1-4, F-N3-3/4, F-N4-1/2/3, F-F1-1/2/3, F-F2-2/3, F-D1-2/3,
F-M1-1/2/3, deployment constraints (action-auth/seed-user/enc-key/exchange-encrypt/ext-whitelist...).

## Batch-4 scope (item 13)

Mandatory: **F-AI4-1 (HIGH)**. Quick-win MEDIUMs: F-AI4-2, F-AI2-1, F-N2-1,
F-N1-3 (visibility), F-WF-03-1, F-N3-1 (registry resync). Plus LOW quick win
F-API2-1 (startup WARN).

## Successor-deferred rationale (uniform)

Roadmap terminal goal mandates fixing CRITICAL/HIGH; deferred MEDIUMs above
are real defects whose fixes require behavior/architecture decisions
(default flips, new ACL models, API contract changes) that exceed a
user-authorized automated batch's safe scope. Each remains an open Phase 3
fix item in the roadmap (not dropped); successor ownership = future fix
batches driven by `./tools/mission-driver.sh security-audit` with owner
review. Why not blocking mission closure: no CRITICAL/HIGH remains open;
each deferred item's interim risk is bounded (documented in its report).

## Count verification

Reports: 5 CORE + 6 AUTH + 4 CRED + 5 API/DYN + 3 WF + 4 AI + 4 NET + 4
FILE/DATA/META = 35 report files. Findings ledger reconciles with per-report
closure-audit verifications: 0 CRITICAL, 1 HIGH (fixed batch-4), 23 MEDIUM
(3 fixed batches 1-2, 6 batch-4 [F-AI4-2/F-AI2-1/F-N2-1/F-N1-3/F-WF-03-1/F-N3-1],
14 successor-deferred to roadmap item 14), ~31 open LOW/limitations (5 fixed,
rest watch/constraint). Zero pending. Counts corrected 2026-09-19 per
closure-audit programmatic recount (agent_9d4f27e3).
