Execute the plan at {{PLAN_FILE}}. Complete **the entire plan**.

Steps:
1. Read the plan file at {{PLAN_FILE}} **completely**.
2. Determine which Phases still need work. A Phase is unfinished if it contains ANY `- [ ]` item. Do NOT rely on the `Status:` line alone — a Phase marked `Status: completed` that still has `[ ]` items is INCONSISTENT (a prior run set the status but did not finish the work, or forgot to tick the items). Treat it as unfinished and execute it. Execute every unfinished Phase, in order.
3. After completing each Phase:
   a. Run `{{testCmd}}` to confirm tests pass. If the change is cross-module, also run `{{typecheckCmd}}` (whole workspace) to catch downstream breakage.
   b. Tick every `[ ]` item in that Phase to `[x]` AND set its `Status:` to `completed`. Both must happen together — a status-only or items-only update leaves the plan inconsistent and will re-trigger this Phase on the next run (causing the EXECUTE ↔ CLOSURE_VERIFY loop).
4. After all Phases are complete:
   a. Update the plan's `Plan Status` to `completed`
   b. Read the work item from the plan (its `> Work Item:` label) and update the relevant roadmap/backlog file (e.g. `{{roadmapPath}}` or the referenced architecture doc): change the work item from ❌ to ✅
   c. **Close source audits**: If the plan front matter has `> Source Audits:`, for each listed audit file change `> Audit Status: planned` to `> Audit Status: closed`. Skip files already `closed` (idempotent) and omit the step entirely if there is no `> Source Audits:` line (roadmap-sourced plan). Do NOT reopen or re-verify here — if a fix turns out insufficient, the next audit round's `OPEN_AUDIT` will re-discover it as a fresh `open` finding.

If execution is interrupted or fails, that is fine — the plan records its own progress ([x]/[ ]), so the next run resumes from the breakpoint.
Do not skip steps — execute every unfinished Phase completely.

Notes:
- Honor `AGENTS.md`: read it **completely** and follow the project's component contract, code conventions, and build artifact rules.
- After code changes, run `{{typecheckCmd}} && {{buildCmd}} && {{lintCmd}}` before declaring a Phase done.

---

## Output marker (both modes)

Your output MUST end with exactly one `<AI_STEP_RESULT>pass</AI_STEP_RESULT>` or `<AI_STEP_RESULT>fail</AI_STEP_RESULT>` marker (`pass` = all phases executed and green; `fail` = execution blocked or tests red). 
