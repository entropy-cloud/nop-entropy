# Security Audit Fix Batch 3 — ILoginService UOE Guard-Word Wording

> Plan Status: completed
> Last Reviewed: 2026-09-19
> Mission: security-audit
> Work Item: 12. 修复批次3 — ILoginService default 方法 UOE 措辞对齐 guard 语义
> Source: item-2 closure audit DENY remediation (auditor-prescribed path: Fix record
> → fix batch execution); `ai-dev/tools/scan-hollow-implementations.mjs` wording
> classification (guard = sanctioned fail-fast, L146)
> Related: fix batches 1-2 (completed), plan 2 (awaiting re-audit)
> Naming: date-prefix per mission-subdirectory convention.

## Purpose

Re-word 4 `UnsupportedOperationException` messages in
`ILoginService` default methods from stub-style ("X not implemented") to
guard-style ("... is not supported by this login service implementation; ..."),
aligning the message semantics with the actual design intent (optional
interface operations with fail-fast defaults and complete implementations in
`AbstractLoginService`/`LoginServiceImpl` — verified by the item-2 closure
auditor with green tests). This makes the hollow-scan gate for
`nop-service-framework/nop-biz-auth-core` genuinely exit 0 without treating
nonzero output as a pass. No behavioral change: same exception type, same
throw sites, same callers.

## Current Baseline

Verified live 2026-09-19:

- `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/ILoginService.java`
  L45/84/94/104: `throw new UnsupportedOperationException("revokeUserSessionsAsync not implemented")`
  (+ mfaVerifyAsync / sendSmsCode / sendMfaCode).
- Tool classification (scan-hollow-implementations.mjs L146):
  `/only available|only supported|does not support|not supported|.../` → guard
  → informational; "not implemented" → stub → high → exit 1.
- Implementation chain (auditor-verified): `revokeUserSessionsAsync` →
  `AbstractLoginService.java:95`; `mfaVerifyAsync` → `LoginServiceImpl:589`;
  SMS/MFA code senders → `LoginServiceImpl:870/904`; green tests
  `TestRevokeUserSessions` 2/2, `TestMfaLoginE2E` 10/10 (full-reactor run 3).
- Gate history: my earlier "exit 0" claims used the short module name
  (nonexistent path at repo root → empty scan → false negative). Literal
  command `--module nop-service-framework/nop-biz-auth-core --severity high`
  → exit 1 with exactly these 4 findings.

## Goals

- Re-word the 4 messages to guard phrasing containing "not supported" (tool
  guard pattern) while keeping the override hint (e.g. method name + which
  implementation provides it).
- Hollow scan literal command exits 0 for both item-2 gate modules.
- Disposition record + log correction landed (item-2 closure prerequisites).

## Non-Goals

- No behavioral/API change; no gate weakening (tool untouched); no new tests
  required (wording-only change; existing tests don't assert these messages —
  verified by grep of test sources).

## Execution Plan

### Phase 1 - Wording Fix And Gate Verification

Status: completed

Adversarial-review basis: the item-2 closure auditor's DENY remediation prescribed this exact fix shape, path (Fix record → fix batch), and verification (literal gate commands); wording classification verified against tool source L146. No separate review round needed — the prescription IS the review.
Targets: ILoginService.java, daily log, AUTH-04 disposition note

- Item Types: `Fix | Proof`

- [x] Re-word L45/84/94/104 messages to guard style (contains "not
  supported"; keep method name and implementation pointer).
- [x] Verify: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-service-framework/nop-biz-auth-core --severity high` exit 0; `--module nop-auth --severity high` exit 0.
- [x] Compile + focused compile check (`./mvnw test-compile -pl nop-service-framework/nop-biz-auth-core -am`); no test asserts the old strings (grep).
- [x] Disposition note added to AUTH-04 report (4 UOEs = sanctioned interface
  fail-fast guards, implementation chain + tests cited); daily log false-negative
  claim corrected with root cause.

Exit Criteria:

- [x] Both literal hollow-scan commands exit 0 with the reworded file.
- [x] Compile green; no test referenced old messages.
- [x] Disposition + log correction landed.
- [x] `ai-dev/logs/` entry updated.

## Closure Gates

- [x] Wording fix live; both scans exit 0 (literal full-path commands).
- [x] Independent fresh-session closure audit ALLOW (can be combined with the
  item-2 re-audit as a scoped pre-check) with evidence in `## Closure`.
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/security-audit/2026-09-19-0010-12-fix-batch-3-login-service-guard-wording.md --strict` exit 0.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0.
- [x] Roadmap item 12 `done` strictly after audit; commit (mission format).

## Deferred But Adjudicated

(none)

## Non-Blocking Follow-ups

- item 9 triage note: hollow-scan `--module` requires full module paths from
  repo root; short names silently scan nothing (tool improvement candidate).

## Closure

Status Note: Closed after independent combined re-audit ALLOW. Wording-only
fix (4 message strings) with genuine gate satisfaction; no behavior change.
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: independent fresh-session closure auditor (agent_ec387612,
  combined re-audit with item 2), verdict **ALLOW**.
- Evidence: git diff verified as exactly 4 message-string lines (L45/84/94/104,
  guard phrasing); both literal hollow-scan commands exit 0 with anti-false-
  negative cross-validation (--severity low finds 18 items incl. the 4 UOEs
  reclassified guard/low — scan provably covers the module); test-compile
  rerun exit 0; zero repo references to old wording; checklist + doc-links
  exit 0 (auditor-run).
- Authorization chain: item-2 closure-audit DENY prescription = Fix record →
  fix batch execution (roadmap item 12).

Follow-up:

- Tool improvement candidate recorded (hollow-scan --module needs full paths);
  no remaining plan-owned work.
