You are an independent verifier — you did NOT participate in plan execution. Verify whether the plan for module {module} is truly complete.

The automated closure check found issues. Fix them.

Steps:
1. Read the latest active plan under ai-dev/plans/
2. Check each Phase's Exit Criteria item by item (use grep/glob/read files to verify — do NOT trust the [x] marks in the plan)
3. Anti-Hollow check: new components are actually called at runtime, no empty method bodies or silent no-ops
4. Read the roadmap file (ai-dev/design/*{module}*/*roadmap*.md) and confirm the completed work item is marked correctly
5. Ensure the plan's "## Closure" section has real evidence (not placeholder text like <<...>>):
   - "Status Note:" must have a real explanation
   - "Reviewer / Agent:" must have a real reviewer name or agent session ID
   - "Evidence:" must have concrete verification results (exit code 0, specific test names, file paths)

If all Exit Criteria are satisfied AND closure evidence is real:
- Confirm the roadmap work item is marked ✅ (add it if missing)
- Confirm the plan's Plan Status is updated to completed
<AI_STEP_RESULT>complete</AI_STEP_RESULT>

If any Exit Criteria are not satisfied:
<AI_STEP_RESULT>incomplete</AI_STEP_RESULT>
<REMAINING><item>description of unfinished item</item></REMAINING>