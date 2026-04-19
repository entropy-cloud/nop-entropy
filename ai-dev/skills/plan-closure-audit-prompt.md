# Plan Closure Audit Prompt

Use this prompt when asking an independent AI reviewer to audit whether a plan may be marked `completed`.

## Purpose

Verify that the plan has actually reached closure and that runtime completion gates are satisfied.

## Audit Focus

The auditor should focus on:

- whether plan status is consistent with the actual state of the plan
- whether any phase or task remains unfinished
- whether any required phase exit criterion remains unchecked
- whether any required task check remains unchecked
- whether any validation checklist item remains incomplete
- whether any unresolved blocking error remains, if blocking errors were recorded during execution
- whether closure notes and audit evidence are sufficient
- whether leftover work has explicit follow-up ownership

## Blocking Findings

The auditor should mark an issue as blocking if it means the plan must not be marked `completed`. Typical blocking issues include:

- unfinished phase or task
- unresolved blocking error
- incomplete required checks or exit criteria
- incomplete validation checklist
- closure note does not match actual plan state
- a blocking error exists but is not recorded clearly enough to audit closure
- remaining work exists but no explicit follow-up ownership is recorded

## Advisory Findings

The auditor should mark an issue as advisory if the plan is probably complete but the closure record could be improved. Typical advisory issues include:

- closure wording could be clearer
- audit evidence could be more explicit
- no `Errors` section was kept even though non-blocking errors happened and the record would benefit from it
- follow-up section could be more precise even if no blocking work remains

## Suggested Dispatch Template

```md
You are an independent closure auditor.

Audit whether the following plan may be marked completed.

Inputs:
- Plan file: <path>
- Related execution evidence: <path list>
- Relevant guide: ai-dev/plans/00-plan-authoring-and-execution-guide.md

Audit goals:
1. Decide whether the plan may be marked completed.
2. Identify blocking issues that prevent closure.
3. Separate blocking issues from advisory issues.
4. Do not modify the plan. Only audit it.

Output format:

## Verdict
- Can Close | Cannot Close

## Blocking Issues
- <issue>

## Advisory Issues
- <issue>

## Closure Summary
- <short summary>
```

## Notes

- This auditor is read-only.
- It should not silently fix the plan.
- Closure should remain blocked until blocking issues are resolved.
