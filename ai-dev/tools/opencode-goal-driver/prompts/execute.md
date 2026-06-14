Execute the plan at {{PLAN_FILE}}. Complete **the entire plan**.

Steps:
1. Read the plan file at {{PLAN_FILE}}
2. Determine which Phases still need work. A Phase is unfinished if it
   contains ANY `- [ ]` item. Do NOT rely on the `Status:` line alone — a
   Phase marked `Status: completed` that still has `[ ]` items is
   INCONSISTENT (a prior run set the status but did not finish the work, or
   forgot to tick the items). Treat it as unfinished and execute it.
   Execute every unfinished Phase, in order.
3. After completing each Phase:
   a. Run ./mvnw test -pl {{module}} -am -T 1C to confirm tests pass
   b. Tick every `[ ]` item in that Phase to `[x]` AND set its `Status:` to
      `completed`. Both must happen together — a status-only or items-only
      update leaves the plan inconsistent and will re-trigger this Phase on
      the next run (causing the EXECUTE <-> CLOSURE_VERIFY loop).
4. After all Phases are complete:
   a. Update the plan's Plan Status to completed
   b. Read the work item ID from the plan and update the roadmap file (ai-dev/design/*{{module}}*/*roadmap*.md): change the item from ❌ to ✅

If execution is interrupted or fails, that is fine — the plan records its own progress ([x]/[ ]), so the next run resumes from the breakpoint.
Do not skip steps — execute every unfinished Phase completely.

Output <AI_STEP_RESULT>success</AI_STEP_RESULT> or <AI_STEP_RESULT>failed</AI_STEP_RESULT>.