Execute the plan at {{PLAN_FILE}}. Complete **the entire plan**.

Steps:
1. Read the plan file at {{PLAN_FILE}}
2. Skip Phases already marked [x], execute all [ ] Phases in order
3. After completing each Phase:
   a. Run ./mvnw test -pl {{module}} -am -T 1C to confirm tests pass
   b. Mark the Phase as [x] in the plan file
4. After all Phases are complete:
   a. Update the plan's Plan Status to completed
   b. Read the work item ID from the plan and update the roadmap file (ai-dev/design/*{{module}}*/*roadmap*.md): change the item from ❌ to ✅

If execution is interrupted or fails, that is fine — the plan records its own progress ([x]/[ ]), so the next run resumes from the breakpoint.
Do not skip steps — execute every unfinished Phase completely.

Output <AI_STEP_RESULT>success</AI_STEP_RESULT> or <AI_STEP_RESULT>failed</AI_STEP_RESULT>.