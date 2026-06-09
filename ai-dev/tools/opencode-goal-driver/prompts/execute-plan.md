# Execute Plan Procedure

Execute the active plan for module {module}. **Execute the entire plan to completion.**

## Execution Steps

1. Run `node ai-dev/tools/check-plan-status.mjs` to get the active plan list
2. Select the first plan in the Active list
3. Read the plan file, skip Phases already marked [x], and execute all [ ] Phases in order
4. After completing each Phase:
   a. Run `./mvnw test -pl {module} -am -T 1C` to confirm tests pass
   b. Mark that Phase as [x] in the plan file
   c. Commit code using the nop-git-master skill (include the work item ID in the commit message)
5. After all Phases are complete:
   a. Update the plan's Plan Status to `completed`
   b. Read the work item ID from the plan, then update the roadmap file (ai-dev/design/*{module}*/*roadmap*.md) — change that work item from ❌ to ✅

If execution is interrupted or fails partway through, that is fine — the plan itself records progress ([x]/[ ]), and the next run will resume from the checkpoint.
Do not skip steps — fully execute every incomplete Phase.

## Output Format

```
<EXECUTE_RESULT>success</EXECUTE_RESULT>
```
or
```
<EXECUTE_RESULT>failed</EXECUTE_RESULT>
```
