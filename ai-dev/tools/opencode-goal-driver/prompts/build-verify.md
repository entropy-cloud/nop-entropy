Verify that the build passes for module {{module}}.

Steps:
1. Run ./mvnw clean install -pl {{module}} -am -T 1C to check the build
2. If the build fails:
   a. Diagnose the root cause (compilation error, test failure, etc.)
   b. Fix the issue
   c. Re-run the build to confirm it passes
3. If the build passes, no further action needed

Output <AI_STEP_RESULT>pass</AI_STEP_RESULT> or <AI_STEP_RESULT>fail</AI_STEP_RESULT>.