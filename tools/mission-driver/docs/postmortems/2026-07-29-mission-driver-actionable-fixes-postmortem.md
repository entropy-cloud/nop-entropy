# Run Postmortem — mission-driver-actionable-fixes — 2026-07-29

- **Run:** 2026-07-29-121842-mission-driver
- **Result:** aborted
- **Top-steps:** 6   **Retries:** 39   **Limit hits:** 19   **Skipped:** 0
- **Wall time:** ~1h10m (04:18:43Z → 05:28:57Z; user SIGINT during the 3rd subflow)

## 1. Executive summary

The run aborted (SIGINT) after burning ~1h10m without closing a single plan. DRAFT_PLANS worked fine (3 plans in 9m), but both EXEC_PLANS subflows that ran terminated at `max_cycles` (20 steps / ~22–24 min each). The single biggest failure mode: the `opencode run` subprocess kept returning **exit=1 with an empty or header-only body**, so no agent ever emitted an `<AI_STEP_RESULT>` marker — 39 retries, almost all marked `empty/short output, exit=1 … (no stderr captured)`. A compounding factor is that even when the agent did real work and the build was provably green, it spent its whole turn re-verifying already-complete code and got cut off mid-sentence before ticking the plan's `[ ]` boxes, so `CLOSURE_SCRIPT_CHECK` failed on every visit ("17/21 unchecked items remain"). The top three fixes: (1) make the harness treat empty-body `exit≠0` as a transient fault retrying on the independent budget, (2) add an "emit early when work is already landed" short-circuit to the execute/closure prompts, and (3) stop misclassifying 6s crashes as rate-limiting in the backoff heuristic.

## 2. Findings

### F1. opencode subprocess exits code 1 with empty/header-only body — agents never emit the result marker — SEV1 — origin: ENV/TOOL

- **Symptom:** Dominant failure across both subflows. Hit EXECUTE, CLOSURE_AUDIT, and BUILD_VERIFY. Two shapes: a ~6s run that produces nothing at all, and a 100–300s run where the agent works then is cut off mid-sentence. Accounts for the bulk of the 39 retries and the run abort.
- **Evidence:**
  > `# cmd: opencode run ...` / `# cwd: ...` / `# started: 2026-07-29T12:34:13` — (file ends; 0 chars body)
  _source: oc-EXECUTE-1785299653961-73hw8q.log (4 lines total)_
  > `output is header-only/empty (0 chars body) — skipping parse fallback`
  _source: mission-driver-actionable-fixes.log, step EXECUTE visit #2_
  > `empty/short output, exit=1 (6s) — cause unknown; see stderr tail — [exit=1] (no stderr captured)`
  _source: run-state-EXEC_PLANS-2-0.json, EXECUTE visits #2/#4/#5_
  > `…This is the inconsistent state the instructions describe (work done, checkboxes not ticked).` / `…Let me verify by running the test suite first, then the typecheck/lint/build gates.` (turn ends here — no `<AI_STEP_RESULT>`, 110s)
  _source: oc-EXECUTE-1785299720088-v2cwcp.log:8,13_
- **Root cause:** The opencode CLI turn terminates before producing any parseable output. The ~6s zero-stderr cases are immediate provider/CLI faults; the 100–300s cases are turns that exhaust their budget mid-work. Because the body is empty, the engine's parse-fallback is correctly skipped (`src/engine.js:795-800`), leaving `marker: null` → hard `fail`. These crashes are NOT being caught by the transient-retry path (`src/engine.js:1698-1744`), so each one consumes `onError.maxRetries` and feeds the closure cycle instead of retrying quietly on the independent transient budget.
- **Fix:** `src/engine.js` — extend the transient-error signature detection (around the `transient` block, ~`:1698`) to include "body length below `PARSE_MIN_BODY_CHARS` AND `exitCode !== 0` AND no `stderrTail`" as a transient-class fault, so empty-body crashes retry on the transient budget (which already doesn't trip `maxCycleVisits`) instead of burning the closure loop. Then surface a distinct marker so the run-state error tail distinguishes "provider crash" from "agent produced wrong marker".

### F2. Closure/execute prompts drive exhaustive re-verification — agents burn the whole turn and never reach the tick+emit step (even with green tests) — SEV1 — origin: PROMPT

- **Symptom:** `CLOSURE_SCRIPT_CHECK` failed on all 5 visits in subflow 2-0 and all 5 in 2-1 ("unchecked items remain"), yet the work was already in the tree and BUILD_VERIFY had observed a fully green build. No agent ever ticked a box. Both subflows reached `max_cycles`.
- **Evidence:**
  > `Plan closure check FAILED.` / `- 17 unchecked items remain after EXECUTE (every [ ] must become [x] before closure)`
  _source: oc-CLOSURE_SCRIPT_CHECK-1785299866513-l1lf6o.log:4-6_
  > `…as an auditor I must NOT blindly tick items — I must first verify the work actually landed in the codebase.`
  _source: oc-CLOSURE_AUDIT-1785299866524-6mu8xp.log:5_
  > `The code implementation landed in all 4 files. Now I must verify the tests, CONTEXT.md doc update, and docs/logs entry exist before ticking anything.` (turn cut off here, 106s)
  _source: oc-CLOSURE_AUDIT-1785301302983-24agop.log:6-7_
  > `All four verification commands passed (typecheck, build, lint:prompts, test — all green). Let me confirm…` (BUILD_VERIFY then cut off — never emitted `pass`)
  _source: run-state-EXEC_PLANS-2-0.json, BUILD_VERIFY visit #2 error tail_
- **Root cause:** `prompts/execute.md` (steps 3a, 19) demands running `testCmd` + `typecheckCmd` per phase and `typecheck && build && lint` before declaring done, with no "already-done short-circuit". `prompts/closure-audit.md` pushes the auditor to re-verify the whole codebase before ticking. When work is already landed (the documented "inconsistent state"), the agent re-derives everything and runs the full gate suite, exhausting the turn before it ticks `[ ]`→`[x]` and emits the marker. Combined with F1's turn cutoffs, the plan can never close.
- **Fix:** `prompts/execute.md` — add an explicit short-circuit near the top: "If, after reading the plan, you find the code AND tests already present (inconsistent state: work done, boxes unticked), tick the items for that Phase, run `{{testCmd}}` ONCE, and emit `<AI_STEP_RESULT>pass</AI_STEP_RESULT>` immediately. Do not re-derive or re-run the full gate suite for already-landed work." Apply the same "tick-then-emit, minimal re-verify" guidance to `prompts/closure-audit.md`.

### F3. Backoff heuristic mislabels immediate crashes as "likely rate-limited" — SEV2 — origin: FLOW

- **Symptom:** After every short failure the engine sleeps 30–90s before retrying. With empty-body crashes being the common case, most retries paid a pointless 30–60s backoff, inflating wall time.
- **Evidence:**
  > `⏳ backing off 30s before retry (previous attempt lasted 6s — likely rate-limited)`
  > `⏳ backing off 90s before retry (previous attempt lasted 6s — likely rate-limited)`
  _source: mission-driver-actionable-fixes.log:38,66 (and 19 `backoff` events in events.jsonl)_
  _mechanism: `src/engine.js:1780-1792` backs off whenever `failedRec.durationMs < 60_000`_
- **Root cause:** The backoff trigger keys purely on `durationMs < 60_000` and assumes short = rate-limit. A 6s run with an empty body and no stderr is an immediate crash, not a rate-limit — waiting 30–90s cannot help and the run never exits the regime by waiting.
- **Fix:** `src/engine.js:1780` — gate the rate-limit backoff on an actual rate-limit signal (e.g. non-empty `stderrTail` matching a provider rate-limit/overload signature, or a transient-class flag from F1), not on duration alone. For empty-body no-stderr crashes, skip or drastically shorten the backoff (e.g. a fixed 2–5s) so retries don't serialize on a 30s+ tax.

### F4. Unresolved `{{forEachItem}}` template variable warning on the EXEC_PLANS container step — SEV3 — origin: FLOW

- **Symptom:** A non-fatal warning logged twice. forEach still resolved correctly (3 plans found), so no impact this run, but it indicates a template that never gets its variable substituted.
- **Evidence:**
  > `WARNING: unresolved template variable {{forEachItem}}`
  _source: mission-driver-actionable-fixes.log:8,19 (EXEC_PLANS visits #1 and #2)_
- **Root cause:** The EXEC_PLANS step's prompt template references `{{forEachItem}}`, but EXEC_PLANS is the forEach-container (subflow spawner) — `forEachItem` is only bound to each child subflow, not to the container itself.
- **Fix:** `flows/mission-driver.json` (EXEC_PLANS step) — drop the `{{forEachItem}}` reference from the container's prompt template, or move that text into the per-item subflow prompt where the variable is actually bound.

## 3. What worked

- **CHECK passed cleanly** in 33s — the gate logic itself is healthy.
- **DRAFT_PLANS produced 3 well-formed, actionable plans** in ~9m (review-approved-marker-alias, per-mission-promptsdir, check-configurable-gate).
- **forEach subflow fan-out worked** — 3 active plans were detected and spawned as separate `plan-execution` subflows with correct per-item `run-state-EXEC_PLANS-2-N.json` files.
- **The safety caps fired correctly** — `maxRetries` bailed each transition chain to its `goto`/`done`, `limit_hit` events were emitted, and both subflows terminated at `max_cycles` instead of looping forever. No runaway.
- **Failure-closed closure** — `CLOSURE_SCRIPT_CHECK` correctly refused to pass on unticked boxes and auditors correctly refused to blindly tick; the integrity model is sound (the problem was upstream, not a false closure).

## 4. Prioritized action list

- [ ] (SEV1) `src/engine.js`: classify empty-body + `exitCode≠0` + no-stderr as a transient fault (retry on transient budget, not `onError`/`maxCycleVisits`) ← ref F1
- [ ] (SEV1) `prompts/execute.md`: add an "already-landed short-circuit" — tick boxes, run test gate once, emit immediately; do not re-derive done work ← ref F2
- [ ] (SEV1) `prompts/closure-audit.md`: add "tick-then-emit with minimal re-verify" guidance mirroring execute.md ← ref F2
- [ ] (SEV2) `src/engine.js:1780`: gate the rate-limit backoff on an actual rate-limit signal, not duration alone; use a short fixed delay for empty-body crashes ← ref F3
- [ ] (SEV3) `flows/mission-driver.json`: remove the unresolved `{{forEachItem}}` from the EXEC_PLANS container prompt ← ref F4

## 5. Data completeness

All key artifacts were present and consistent: `run-state.json`, both `run-state-EXEC_PLANS-2-{0,1}.json`, `events.jsonl` (222 lines), the main `mission-driver-actionable-fixes.log`, and all referenced red-flag step logs. `run-state-EXEC_PLANS-2-2.json` (the 3rd subflow) was never written because the run was SIGINT-interrupted mid-subflow; events.jsonl lines 177–222 confirm subflow 2-2 was actively retrying EXECUTE when killed, so its findings are inferred from the two completed subflows (same failure signature). No `sys-snapshot.csv` anomalies were checked — no OOM/resource evidence was sought because every failure carried an explicit `empty/short output, exit=1` signature pointing to the provider/turn layer, not resource exhaustion. Confidence in F1–F3 is high; F4 is low-impact and inferred from the warning text only.
