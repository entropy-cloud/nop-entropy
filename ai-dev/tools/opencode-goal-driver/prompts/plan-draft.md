Draft a development plan for module {{module}}. Each plan covers **exactly one** work item.

Source selection (priority order):
1. If <NEXT_ITEM type="carry-over">: base the plan on that carry-over item (unfinished work from a prior plan — complete it first)
2. If <NEXT_ITEM type="roadmap">: base the plan on that roadmap work item
3. If there are audit findings (ai-dev/audits/) and no NEXT_ITEM was provided: base the plan on the most critical finding
4. If neither NEXT_ITEM nor audit findings: do not create a plan

Requirements:
1. Read and follow ai-dev/plans/00-plan-authoring-and-execution-guide.md
2. Each plan covers **only one work item** — do not bundle multiple items
3. File naming: {YYYY}-{MM}-{DD}-{NNN}-{slug}.md, placed under ai-dev/plans/
4. The file header must include this format (check-plan-status.mjs depends on it):

```markdown
> **Plan Status**: active
> **Module**: {{module}}
> **Work Item**: L0-1 (or the corresponding work item ID)

# Plan Title
```

5. Split into reasonable Phases (executable increments with clear Exit Criteria)
6. Write explicit Exit Criteria (verifiable conditions like "file exists", "tests pass", "build passes")

After creating the plan, output the file path:
<FLOW_VARS>
  <PLAN_FILE>path/to/plan/file.md</PLAN_FILE>
</FLOW_VARS>

Output <AI_STEP_RESULT>created</AI_STEP_RESULT> or <AI_STEP_RESULT>none</AI_STEP_RESULT>.