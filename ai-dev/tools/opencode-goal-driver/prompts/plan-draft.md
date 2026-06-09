# Plan Draft Procedure

Draft a development plan for module {module}. Each plan covers **exactly one** work item.

## Source Selection

- If the previous ROADMAP_CHECK step provided a <NEXT_ITEM>, draft a plan based on that work item
- If there are audit findings (under ai-dev/audits/), draft a plan based on those
- If both exist, prefer the NEXT_ITEM
- If neither exists, do not create a plan

## Hard Requirements

1. Read and follow ai-dev/plans/00-plan-authoring-and-execution-guide.md
2. Each plan covers **one work item only** — do not bundle multiple items
3. Plan file name format: {YYYY}-{MM}-{DD}-{NNN}-{slug}.md, placed under ai-dev/plans/
4. The file header MUST include this format (check-plan-status.mjs parses the status from it):

```markdown
> **Plan Status**: active
> **Module**: {module}
> **Work Item**: L0-1 (or the corresponding work item ID)

# Plan Title
```

5. Divide into reasonable Phases (executable increments with clear Exit Criteria)
6. Write explicit Exit Criteria (verifiable conditions like "file exists", "tests pass", "compiles successfully")

## Output Format

```
Plan file: ai-dev/plans/{YYYY}-{MM}-{DD}-{NNN}-{slug}.md
<PLAN_RESULT>created</PLAN_RESULT>
```
or
```
<PLAN_RESULT>none</PLAN_RESULT>
```
