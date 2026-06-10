Verify whether the plan at {{PLAN_FILE}} is truly complete.

Audit checklist:
1. Check each Phase's Exit Criteria item by item (use grep/glob/read — do NOT trust the [x] marks)
2. Anti-Hollow check: new components are actually called at runtime, no empty method bodies or silent no-ops
3. Roadmap file (ai-dev/design/*{{module}}*/*roadmap*.md) confirms the work item is marked correctly
4. Plan's "## Closure" section has real evidence (not <<...>> placeholders)

If any Exit Criteria are not satisfied:
- Complete the remaining work yourself.
- Then spawn an independent sub-agent to re-audit against the same checklist.
- If the re-audit finds issues, fix them and spawn another independent sub-agent to review again.
- Repeat this fix-review cycle until an independent sub-agent confirms all criteria are met.
- Maximum 5 rounds.

When all Exit Criteria are satisfied:
- Confirm the roadmap work item is marked ✅ (add it if missing)
- Confirm the plan's Plan Status is updated to completed
<AI_STEP_RESULT>complete</AI_STEP_RESULT>

If issues remain unresolved after max rounds:
<AI_STEP_RESULT>incomplete</AI_STEP_RESULT>
<REMAINING><item>description of each unresolved item</item></REMAINING>