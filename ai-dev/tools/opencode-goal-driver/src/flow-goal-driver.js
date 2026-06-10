import { execSync } from "node:child_process";

function detectStartPhase(delegates) {
  const config = delegates.config;
  try {
    const output = execSync("node ai-dev/tools/check-plan-status.mjs", {
      cwd: config.projectRoot,
      encoding: "utf8",
      timeout: 30_000,
    });
    const activeMatch = output.match(/Active:\s*(\d+)/);
    const activeCount = activeMatch ? parseInt(activeMatch[1], 10) : 0;

    if (activeCount > 0) return "execute";

    try {
      const roadmaps = execSync(
        `ls ${config.projectRoot}/ai-dev/design/*${config.moduleName}*/*roadmap* ${config.projectRoot}/ai-dev/design/*roadmap*${config.moduleName}* 2>/dev/null || true`,
        { encoding: "utf8", timeout: 5_000 },
      ).trim();
      if (roadmaps) return "roadmap";
    } catch {}

    const auditsDir = `${config.projectRoot}/ai-dev/audits`;
    const recentAudit = execSync(
      `ls -td ${auditsDir}/*${config.moduleName}* 2>/dev/null | head -1`,
      { encoding: "utf8", timeout: 5_000 },
    ).trim();
    if (recentAudit) return "plan";

    return "audit";
  } catch {
    return "roadmap";
  }
}

export function createGoalDriverFlow() {
  return {
    name: "goal-driver",
    maxTotalSteps: 80,
    maxCycleVisits: 15,

    entry: "DETECT_START",

    steps: {
      DETECT_START: {
        type: "script",
        run: (delegates) => detectStartPhase(delegates),
        transitions: {
          execute: { goto: "HEALTH_CHECK" },
          roadmap: { goto: "HEALTH_CHECK" },
          plan: { goto: "HEALTH_CHECK" },
          audit: { goto: "HEALTH_CHECK" },
        },
      },

      HEALTH_CHECK: {
        type: "tool",
        command: "./mvnw clean install",
        timeout: 1_800_000,
        transitions: {
          pass: { goto: "ROADMAP_CHECK" },
          fail: { goto: "FIX_BUILD" },
        },
        onError: { goto: "FIX_BUILD" },
      },

      FIX_BUILD: {
        type: "agent",
        prompt: "Fix compilation errors in module {module}. Run ./mvnw clean install -pl {module} -am -T 1C and fix all errors until the build passes.\n\nOutput <AI_STEP_RESULT>fixed</AI_STEP_RESULT> or <AI_STEP_RESULT>failed</AI_STEP_RESULT>.",
        onUnknownMaxRetries: 2,
        transitions: {
          fixed: { goto: "ROADMAP_CHECK" },
          failed: { done: "failed" },
        },
        onError: { done: "failed" },
      },

      ROADMAP_CHECK: {
        type: "agent",
        prompt: `Check the roadmap for module {module} and select the **single most urgent** work item.

Steps:
1. Find *roadmap*.md files under ai-dev/design/ related to {module}
2. Read the full roadmap, focusing on: §2 "Current State", §3 "Priority Guide", §4 "Work Items", §5 "Tech Debt"
3. Find all items marked with ❌ or ⚠️
4. For each unfinished item, use grep/glob to check whether the implementation already exists in the codebase (exclude _gen generated code)
5. Select the **single most urgent** item by priority (high to low):
   - P0 tech debt (build/loader failures)
   - Layer 0 blockers
   - Layer 1 core interfaces
   - Higher-layer extensions
   - Within the same layer, prefer items with the most dependents (fewest dependencies)
6. Output the selection result

If all items are implemented (every ❌ has actual code): <AI_STEP_RESULT>complete</AI_STEP_RESULT>
If unfinished items exist: <AI_STEP_RESULT>pending</AI_STEP_RESULT>
<NEXT_ITEM id="item-id" layer="layer" priority="P0|P1|P2">reason and current status</NEXT_ITEM>
<ROADMAP_ITEMS><item id="id" priority="P0|P1|P2|P3">summary of all unfinished items</item></ROADMAP_ITEMS>`,
        onUnknownMaxRetries: 2,
        transitions: {
          pending: { goto: "PLAN_DRAFT", append: true },
          complete: { goto: "DEEP_AUDIT" },
        },
        onError: { goto: "DEEP_AUDIT" },
      },

      PLAN_DRAFT: {
        type: "agent",
        prompt: `Draft a development plan for module {module}. Each plan covers **exactly one** work item.

Source selection:
- If the previous ROADMAP_CHECK provided <NEXT_ITEM>, base the plan on that work item
- If there are audit findings (ai-dev/audits/), base the plan on those
- If both exist, prioritize NEXT_ITEM
- If neither exists, do not create a plan

Requirements:
1. Read and follow ai-dev/plans/00-plan-authoring-and-execution-guide.md
2. Each plan covers **only one work item** — do not bundle multiple items
3. File naming: {YYYY}-{MM}-{DD}-{NNN}-{slug}.md, placed under ai-dev/plans/
4. The file header must include this format (check-plan-status.mjs depends on it):

\`\`\`markdown
> **Plan Status**: active
> **Module**: {module}
> **Work Item**: L0-1 (or the corresponding work item ID)

# Plan Title
\`\`\`

5. Split into reasonable Phases (executable increments with clear Exit Criteria)
6. Write explicit Exit Criteria (verifiable conditions like "file exists", "tests pass", "build passes")

Output <AI_STEP_RESULT>created</AI_STEP_RESULT> or <AI_STEP_RESULT>none</AI_STEP_RESULT>.`,
        onUnknownMaxRetries: 2,
        transitions: {
          created: { goto: "PLAN_AUDIT" },
          none: { goto: "ROADMAP_CHECK" },
        },
        onError: { goto: "ROADMAP_CHECK" },
      },

      PLAN_AUDIT: {
        type: "agent",
        prompt: `You are an independent plan reviewer. Review the plan that was just created.

Review dimensions (all must be checked):
1. **Imaginative analysis**: Imagine executing the plan step by step — find gaps between design and code
2. **Format completeness**: Does it follow the plan guide template? Are all required fields present?
3. **Content soundness**: Are Goals/Non-Goals clear? Is Phase decomposition reasonable?
4. **Reference accuracy**: Do referenced file paths exist in the repo? Are code locations correct?

Each finding must include a severity (Blocker/Major/Minor).
The plan passes only when there are zero Blockers and zero Majors.

Output <AI_STEP_RESULT>approved</AI_STEP_RESULT> or <AI_STEP_RESULT>issues</AI_STEP_RESULT>
When issues are found, also output:
<ISSUES><item severity="Blocker|Major|Minor">problem description</item></ISSUES>`,
        maxRetries: 3,
        onUnknownMaxRetries: 2,
        transitions: {
          approved: { goto: "EXECUTE" },
          issues: {
            retry: "PLAN_DRAFT",
            maxRetries: 3,
            append: { template: "\n\nPlan review feedback — revise the plan accordingly:\n${output}" },
          },
        },
        onError: { retry: "PLAN_DRAFT", maxRetries: 2 },
        onMaxRetries: { goto: "EXECUTE", append: { template: "\n\nWarning: plan did not pass automated audit after multiple rounds. Note these issues during execution:\n${output}" } },
      },

      EXECUTE: {
        type: "agent",
        prompt: `Execute the active plan for module {module}. Complete **the entire plan**.

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

Output <AI_STEP_RESULT>success</AI_STEP_RESULT> or <AI_STEP_RESULT>failed</AI_STEP_RESULT>.`,
        onUnknownMaxRetries: 2,
        transitions: {
          success: { goto: "CLOSURE_VERIFY" },
          failed: { goto: "CLOSURE_VERIFY" },
        },
        onError: { goto: "CLOSURE_VERIFY" },
      },

      CLOSURE_VERIFY: {
        type: "group",
        maxRounds: 3,
        onExhausted: "fail",
        steps: {
          SCRIPT_CHECK: {
            type: "script",
            run: async (delegates) => {
              const { execSync } = await import("node:child_process");
              try {
                execSync("node ai-dev/tools/check-plan-checklist.mjs --active-only --quiet --strict", {
                  cwd: delegates.config.projectRoot,
                  encoding: "utf8",
                  timeout: 30_000,
                });
                return "pass";
              } catch {
                return "fail";
              }
            },
            transitions: {
              pass: { exit: "pass" },
              fail: { goto: "AI_AUDIT" },
            },
          },

          AI_AUDIT: {
            type: "agent",
            prompt: `You are an independent verifier — you did NOT participate in plan execution. Verify whether the plan for module {module} is truly complete.

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
<REMAINING><item>description of unfinished item</item></REMAINING>`,
            onUnknownMaxRetries: 2,
            transitions: {
              complete: { goto: "_retry" },
              incomplete: { exit: "fail" },
            },
            onError: { exit: "fail" },
          },
        },
        transitions: {
          pass: { goto: "BUILD_VERIFY" },
          fail: {
            retry: "EXECUTE",
            maxRetries: 5,
            append: { extract: "REMAINING", template: "\n\nThe following items remain unfinished — continue:\n${output}" },
          },
        },
        onError: {
          retry: "EXECUTE",
          maxRetries: 3,
          append: { template: "\n\nClosure audit subprocess was killed. Re-check the plan Exit Criteria." },
        },
        onMaxRetries: { goto: "BUILD_VERIFY" },
      },

      BUILD_VERIFY: {
        type: "agent",
        prompt: `Verify that the build passes for module {module}.

Steps:
1. Run ./mvnw clean install -pl {module} -am -T 1C to check the build
2. If the build fails:
   a. Diagnose the root cause (compilation error, test failure, etc.)
   b. Fix the issue
   c. Re-run the build to confirm it passes
   d. Commit the fix using the nop-git-master skill
3. If the build passes, no further action needed

Output <AI_STEP_RESULT>pass</AI_STEP_RESULT> or <AI_STEP_RESULT>fail</AI_STEP_RESULT>.`,
        onUnknownMaxRetries: 2,
        transitions: {
          pass: { goto: "ROADMAP_CHECK" },
          fail: { retry: "EXECUTE", maxRetries: 3 },
        },
        onError: { retry: "EXECUTE", maxRetries: 2 },
        onMaxRetries: { goto: "ROADMAP_CHECK" },
      },

      DEEP_AUDIT: {
        type: "agent",
        prompt: `Perform a multi-dimensional deep audit on module {module}. Read and follow ai-dev/skills/deep-audit-prompts.md. Write results to ai-dev/audits/{DATE}-deep-audit-{module}/.

Output <AI_STEP_RESULT>clean</AI_STEP_RESULT> or <AI_STEP_RESULT>issues</AI_STEP_RESULT>.`,
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "PLAN_DRAFT" },
          clean: { goto: "ADVERSARIAL" },
        },
        onError: { goto: "PLAN_DRAFT" },
      },

      ADVERSARIAL: {
        type: "agent",
        prompt: `Perform an adversarial review on module {module}. Read and follow ai-dev/skills/open-ended-adversarial-review-prompt.md. Write results to ai-dev/audits/{DATE}-adversarial-review-{module}/.

Output <AI_STEP_RESULT>clean</AI_STEP_RESULT> or <AI_STEP_RESULT>issues</AI_STEP_RESULT>.`,
        onUnknownMaxRetries: 2,
        transitions: {
          issues: { goto: "PLAN_DRAFT" },
          clean: { done: "completed" },
        },
        onError: { done: "completed" },
      },
    },
  };
}
