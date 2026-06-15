Based on the combined audit and adversarial findings above, draft 1 to 3 development plans.

## Multi-Plan Drafting Rules

1. **Scope per plan**: Each plan must have sufficient workload (see Granularity Rule below). Bundle small fixes into one plan; split large features across multiple plans.
2. **File naming**: Save each plan at `ai-dev/plans/{NNN}-{slug}.md` where NNN is sequential (scan ai-dev/plans/ for the highest existing number).
3. **File format** (check-plan-status.mjs depends on it):

```markdown
> **Plan Status**: active
> **Module**: {{module}}
> **Work Item**: L0-1 (or appropriate ID)

# Plan Title

## Phases
...
```

4. **First plan**: Output its path as `PLAN_FILE` in FLOW_VARS. The engine will pick up all active plans automatically via PLAN_ROUTER.

## Quality Review

Before finalizing, use a **sub-agent** to review each drafted plan:
- Check if phases are ordered correctly
- Verify exit criteria are measurable
- Ensure the plan scope is appropriate (not too small, not too large)
- Refine based on feedback

Repeat review-refine cycles until the sub-agent approves all plans.

## Granularity Rule

- **Default**: one work item per plan
- **Bundle** when multiple items are in the same subsystem and each individually would result in < ~100 lines of actual code change
- **Split** when a single work item has 6+ distinct phases

## Output

After creating all plan files, output the first plan's path:

```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
<FLOW_VARS>
  <PLAN_FILE>/absolute/path/to/first/plan.md</PLAN_FILE>
</FLOW_VARS>
```

⚠️ The PLAN_FILE path must exist on disk. If it does not exist, the engine will reject it and force a retry.
