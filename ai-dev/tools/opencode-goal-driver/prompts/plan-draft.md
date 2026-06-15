Draft a development plan for module {{module}}.

## Granularity Rule

A plan must have **sufficient workload** to justify the ceremony of drafting, executing, checklist-tracking, and independent closure audit. Use the following guidelines:

- **Default**: one work item per plan (for non-trivial items with distinct business semantics)
- **Bundle** when multiple work items are in the same subsystem/package **and** each individually would result in < ~100 lines of actual code change. Example: adding multiple extension-point interfaces (interface + NoOp impl + Builder wiring + test) that all follow the same pattern — bundle into one "L1 Extension Points" plan.
- **Split** when a single work item is large enough to have **6+** distinct phases — 2–4 phases is normal, not a reason to split.

> ⚠️ **Litmus test**: imagine the total ceremony cost (plan doc 150-250 lines + checklist + closure audit + log). If the actual code change is smaller than the plan doc, the item is too small — bundle it.

Source selection (priority order):
1. If <NEXT_ITEM type="carry-over">: handle as carry-over (see Carry-Over Workflow below)
2. If <NEXT_ITEM type="roadmap">: base the plan on that roadmap work item
3. If there are audit findings (ai-dev/audits/) and no NEXT_ITEM was provided: base the plan on the most critical finding

### Carry-Over Workflow

When working on a carry-over item (source-plan attribute points to the original plan):

1. **Review**: Verify the carry-over item is still needed:
   - Read the original plan file (`source-plan`) to understand the full context
   - Use grep/glob to check if the work has already been partially or fully implemented
   - If already fully implemented: create a minimal plan documenting "already complete" — no new work needed
2. **Modify original plan**: Update the original plan to record that followup is being handled:
   - Add a section: `## Follow-up handled by {new-plan-filename}`
   - This creates a traceable link from old plan → new plan
3. **Create new plan**: Draft the plan for the carry-over work (or a minimal plan if already complete) following the standard Requirements below

Requirements:
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

After creating the file, report its real path. The engine will verify the path exists on disk.

Output exactly this structure (replace `/path/to/plan.md` with the real path you used):
```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
<FLOW_VARS>
  <PLAN_FILE>/path/to/plan.md</PLAN_FILE>
</FLOW_VARS>
```

⚠️ CRITICAL: The literal string `/path/to/plan.md` in the template above is a placeholder — it will NOT exist on disk. You MUST substitute your real file path. If the engine receives `/path/to/plan.md`, it will reject the plan and force a retry.

### Revision Request

If your prompt includes a **REVISION REQUEST** section with review feedback, you must revise the **existing** plan file **in-place** (edit the file at the path already known to the engine, do NOT create a new plan). After editing, output `created` **without** a `<FLOW_VARS>` block — the engine already knows the plan path:
```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
```
