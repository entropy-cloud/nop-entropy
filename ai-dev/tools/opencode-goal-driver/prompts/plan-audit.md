Review the plan at `{{PLAN_FILE}}`. You are the **coordinator**, not a reviewer.

## Coordinator constraints

- Read ONLY `{{PLAN_FILE}}` and `ai-dev/plans/00-plan-authoring-and-execution-guide.md`.
- Do NOT Read/Grep/Glob source code, design docs, or other plans. All repo verification is the review sub-agent's job — it runs in a separate session and cannot see your context, so pre-verifying references yourself is wasted work.
- Spawn review sub-agents, relay findings, and stop the instant the pass criterion is met.

## Review dimensions (sub-agent checks all four)

1. **Imaginative analysis** — mentally execute the plan; find design↔code gaps.
2. **Format completeness** — follows the plan guide template; required fields present.
3. **Content soundness** — Goals/Non-Goals clear; Phase decomposition reasonable; Exit Criteria repo-observable.
4. **Reference accuracy** — referenced paths exist; line numbers / method / class names correct. Verified by the sub-agent against the live repo.

Each finding carries a severity: **Blocker / Major / Minor**.

## Pass criterion

**Pass = zero Blockers AND zero Majors.** Minors never block and never trigger a revise.

## Process (max 2 review rounds)

**Round 1** — spawn one review sub-agent.
- 0 Blocker & 0 Major → output `approved` immediately. Do NOT revise to "fix Minors" or "reach consensus". Minors resolve during execution.
- ≥1 Blocker or Major → go to Round 2.

**Round 2** (only if Round 1 failed):
1. Spawn a revise sub-agent to fix **only** the Blocker/Major findings. It MUST NOT touch Minor-level text (editing Minors tends to introduce new errors).
2. Spawn a fresh review sub-agent to re-verify.
3. Pass → `approved`. Still failing → `issues`.

Never run Round 3+. Never add "polish"/"confirm" rounds after a pass.

## Output

- `<AI_STEP_RESULT>approved</AI_STEP_RESULT>` — pass criterion met.
- `<AI_STEP_RESULT>issues</AI_STEP_RESULT>` — Round 2 still has a Blocker/Major. Also output:
  ```
  <ISSUES><item severity="Blocker|Major|Minor">description</item></ISSUES>
  ```
