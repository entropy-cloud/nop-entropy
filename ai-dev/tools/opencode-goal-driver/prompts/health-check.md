Ensure module {{module}} builds and tests pass before starting work.

Steps:
1. Run `./mvnw clean install -pl {{module}} -am -T 1C`
2. If the build passes, no further action needed
3. If the build fails:
   a. Diagnose the root cause (compilation error, test failure, etc.)
   b. Fix the issue
   c. Re-run to confirm the build passes
4. Repeat until the build passes or you cannot fix the issue

Output `<AI_STEP_RESULT>pass</AI_STEP_RESULT>` or `<AI_STEP_RESULT>fail</AI_STEP_RESULT>`.
