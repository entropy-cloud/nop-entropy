Execute the active plan for module {module}. Complete **the entire plan**.

Steps:
1. Run node ai-dev/tools/check-plan-status.mjs to list active plans
2. Select the first plan in the Active list
3. Read the plan file — skip Phases already marked [x], execute all [ ] Phases in order
4. After completing each Phase:
   a. Run ./mvnw test -pl {module} -am -T 1C to confirm tests pass
   b. Mark the Phase as [x] in the plan file
   c. Commit using the nop-git-master skill (commit message must include the work item ID)
5. After all Phases are complete:
   a. Update the plan's Plan Status to completed
   b. Read the work item ID from the plan and update the roadmap file (ai-dev/design/*{module}*/*roadmap*.md): change the item from ❌ to ✅

If execution is interrupted or fails, that is fine — the plan records its own progress ([x]/[ ]), so the next run resumes from the breakpoint.
Do not skip steps — execute every unfinished Phase completely.

Output <AI_STEP_RESULT>success</AI_STEP_RESULT> or <AI_STEP_RESULT>failed</AI_STEP_RESULT>.