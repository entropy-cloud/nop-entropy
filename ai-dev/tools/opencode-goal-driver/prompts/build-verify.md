Verify that the build passes for module {{module}}, then commit the work.

Steps:
1. Run `./mvnw clean install -pl {{module}} -am -T 1C` to check the build
2. If the build fails:
   a. Diagnose the root cause (compilation error, test failure, etc.)
   b. Fix the issue
   c. Re-run the build to confirm it passes
3. If the build passes, **commit the work** (this is mandatory, not optional):
   a. Run `git status` to inspect all uncommitted changes
   b. If the working tree is clean (no changes since the last commit), skip to step 4
   c. Derive commit metadata from the run context:
       - `NNN` = plan number parsed from `{{PLAN_FILE}}` basename (the leading digits before the first `-`)
       - `scope` = module path tail with `nop-` prefix stripped (e.g. `nop-ai/nop-ai-agent` → `ai-agent`)
   d. Split changes into logical commits following the nop-git-master skill style
      (Chinese primary, English technical terms; impl + tests always together):
      - **Code commit** (implementation + tests, never separated):
        ```
        feat(<scope>): plan-<NNN> <short title from plan header>

        - 交付项 1
        - 交付项 2
        - 交付项 3 (typical 3-5 items, extract from plan deliverables)

        Plan: ai-dev/plans/<NNN>-...md
        ```
      - **Doc commit** (plan file + design docs + roadmap/vision + daily log):
        ```
        docs(<scope>): plan-<NNN> 文档/日志/计划更新

        - 更新 ai-dev/design/...roadmap.md (§X ✅)
        - 更新 ai-dev/design/...vision.md (§Y 落地标注)
        - 更新 ai-dev/logs/2026/MM-DD.md (plan-<NNN> 条目)

        Plan: ai-dev/plans/<NNN>-...md
        ```
      - If code changes span multiple modules, emit multiple feat commits
        (split by `api → dao → service → web` layering rule).
   e. **Failure handling** — if any `git commit` fails (pre-commit hook rejection,
      message format issue, staging problem, etc.):
      - Try to auto-fix the root cause and retry (e.g. fix trailing whitespace /
        import order / message format / re-stage missing files). Up to 2 retries.
      - Never bypass hooks (`--no-verify`) or force anything (`--force`,
        reset shared refs).
      - If auto-fix fails after retries, leave the working tree as-is (preserve
        work) and emit `<AI_STEP_RESULT>fail</AI_STEP_RESULT>` with the failure
        reason in your narrative so the next run can pick up the uncommitted work.
   f. After all commits succeed, run `git log --oneline -5` to confirm the history
4. Output the final result.

Output <AI_STEP_RESULT>pass</AI_STEP_RESULT> or <AI_STEP_RESULT>fail</AI_STEP_RESULT>.
