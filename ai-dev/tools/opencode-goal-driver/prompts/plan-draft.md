Draft **and** self-audit a development plan for module {{module}}.

This step combines two responsibilities in sequence:
1. **Author** — write the plan file following the plan guide.
2. **Coordinator** — run an independent sub-agent review loop until the pass criterion is met, then output `created`.

You do NOT review your own plan yourself. The audit is performed by **independent sub-agents** spawned in separate sessions (they cannot see your context). Your job as coordinator is to spawn them, relay findings, revise, and stop the instant the pass criterion is met.

---

## Phase 1 — Author the Plan

### Granularity Rule

A plan must have **sufficient workload** to justify the ceremony of drafting, executing, checklist-tracking, and independent closure audit. Use the following guidelines:

- **Default**: one work item per plan (for non-trivial items with distinct business semantics)
- **Bundle** when multiple work items are in the same subsystem/package **and** each individually would result in < ~100 lines of actual code change. Example: adding multiple extension-point interfaces (interface + NoOp impl + Builder wiring + test) that all follow the same pattern — bundle into one "L1 Extension Points" plan.
- **Split** when a single work item is large enough to have **6+** distinct phases — 2–4 phases is normal, not a reason to split.
- **Carry-over items**: always apply the Litmus test. Carry-overs are frequently tiny (1–50 lines of production code). Before creating a standalone plan, read the source-plan's §Non-Goals / §Deferred / §Follow-up sections for **sibling carry-overs** (same file, same class, or same fix pattern) and bundle them into this plan until combined workload reaches ~100 lines. A plan that changes only 1–50 lines of production code is **never acceptable** as a standalone plan — merge it into a sibling plan or wait for a larger bundle.

> ⚠️ **Litmus test**: imagine the total ceremony cost (plan doc 150-250 lines + checklist + closure audit + log). If the actual code change is smaller than the plan doc, the item is too small — bundle it.

### Source selection (priority order)

1. If one or more `<NEXT_ITEM>` tags were provided: **bundle ALL of them into a single plan**. The ROADMAP step has already pre-grouped bundle-eligible items — respect that grouping and do not split them into separate plans. Carry-over items (type="carry-over") follow the Carry-Over Workflow below.
2. If there are audit findings (ai-dev/audits/) and no NEXT_ITEM was provided: base the plan on the most critical finding

### Carry-Over Workflow

When working on a carry-over item (source-plan attribute points to the original plan):

1. **Review**: Verify the carry-over item is still needed:
   - Read the original plan file (`source-plan`) to understand the full context
   - Use grep/glob to check if the work has already been partially or fully implemented
   - If already fully implemented: create a minimal plan documenting "already complete" — no new work needed
2. **Check for bundle siblings (mandatory)**: A carry-over item alone is frequently too small to justify a standalone plan. Before drafting:
   - If multiple `<NEXT_ITEM>` tags were provided by ROADMAP: they are already pre-bundled — include all of them in this plan.
   - Otherwise, read the source-plan's §Non-Goals / §Deferred / §Follow-up sections. Sibling carry-overs listed there (same file / same class / same fix pattern) are prime bundle candidates. Search the live codebase to confirm they are still unimplemented.
   - **Enforce minimum workload**: estimate production-code change lines for the carry-over alone. If < ~100 lines, you MUST find and include at least one bundle sibling. A standalone plan for a 1–50 line change is never acceptable — if no bundle sibling exists anywhere, flag this in the plan header as below-granularity and proceed (ROADMAP will try harder to bundle next cycle).
3. **Modify original plan(s)**: Update **every** source-plan (primary + bundled siblings) to record that its followup is being handled:
   - Add a section: `## Follow-up handled by {new-plan-filename}`
   - This creates a traceable link from old plan → new plan
4. **Create plan**: Draft the plan for the carry-over work (bundled with siblings if any), following the standard Requirements below. When bundling, add a `## Bundled Items` section listing each item with its source-plan reference.

### Authoring Requirements

1. Read and follow ai-dev/plans/00-plan-authoring-and-execution-guide.md
2. Obey the Granularity Rule above — bundle small items, split large ones
3. Save the plan file at `ai-dev/plans/{NNN}-{slug}.md` where NNN is the next available number (scan ai-dev/plans/ to find the highest existing number and add 1)
4. The file header must include this format (check-plan-status.mjs depends on it):

```markdown
> **Plan Status**: active
> **Module**: {{module}}
> **Work Item**: L0-1 (or the corresponding work item ID)

# Plan Title
```

5. Split into reasonable Phases (executable increments with clear Exit Criteria)
6. Write explicit Exit Criteria (verifiable conditions like "file exists", "tests pass", "build passes")

---

## Phase 2 — Independent Audit (MANDATORY — do not skip)

> **The plan guide (`00-plan-authoring-and-execution-guide.md` §"Before & After Drafting: 迭代对抗性审查") requires independent sub-agent review of every plan draft.** A plan written by you but never independently reviewed is NOT ready for execution. Spawn review sub-agents — do not self-review.

### Coordinator constraints

- Read ONLY the plan you just wrote and `ai-dev/plans/00-plan-authoring-and-execution-guide.md`.
- Do NOT Read/Grep/Glob source code, design docs, or other plans yourself to "pre-verify" references. All repo verification is the review sub-agent's job — it runs in a separate session and cannot see your context, so pre-verifying references yourself is wasted work.
- Spawn review sub-agents, relay findings, revise in-place, and stop the instant the pass criterion is met.

### Review dimensions (sub-agent checks all four)

1. **Imaginative analysis** — mentally execute the plan; find design↔code gaps.
2. **Format completeness** — follows the plan guide template; required fields present.
3. **Content soundness** — Goals/Non-Goals clear; Phase decomposition reasonable; Exit Criteria repo-observable; **workload sufficient** (estimated ≥ ~100 lines of production-code change, or a justified exception noted in the plan header). A plan that changes only 1–50 lines of production code without bundled siblings is a **Blocker**.
4. **Reference accuracy** — referenced paths exist; line numbers / method / class names correct. Verified by the sub-agent against the live repo.

Each finding carries a severity: **Blocker / Major / Minor**.

### Pass criterion

**Pass = zero Blockers AND zero Majors.** Minors never block and never trigger a revise.

### Process (max 2 review rounds)

**Round 1** — spawn one review sub-agent (fresh session, different task_id).
- 0 Blocker & 0 Major → go to Output immediately. Do NOT revise to "fix Minors" or "reach consensus". Minors resolve during execution.
- ≥1 Blocker or Major → go to Round 2.

**Round 2** (only if Round 1 failed):
1. Revise the plan **in-place** (edit the existing file, do NOT create a new plan) to fix **only** the Blocker/Major findings. Do NOT touch Minor-level text (editing Minors tends to introduce new errors).
2. Spawn a fresh review sub-agent (different task_id again) to re-verify.
3. Pass → go to Output. Still failing → go to Output anyway (degraded mode — the plan proceeds; downstream execution/closure/deep audits will catch residual issues).

Never run Round 3+. Never add "polish"/"confirm" rounds after a pass.

---

## Output

After Phase 2 completes (pass OR degraded), report the plan's real path. The engine will verify the path exists on disk.

Output exactly this structure (replace `/path/to/plan.md` with the real path you used):
```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
<FLOW_VARS>
  <PLAN_FILE>/path/to/plan.md</PLAN_FILE>
</FLOW_VARS>
```

⚠️ CRITICAL: The literal string `/path/to/plan.md` in the template above is a placeholder — it will NOT exist on disk. You MUST substitute your real file path. If the engine receives `/path/to/plan.md`, it will reject the plan and force a retry.
