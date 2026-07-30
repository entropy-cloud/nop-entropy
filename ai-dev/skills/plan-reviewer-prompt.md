# Plan Reviewer Prompt

Use this prompt when asking an independent AI reviewer to review a plan **before execution starts**.

## Purpose

Verify that the plan is executable, that runtime-consumed fields are clear, and that the plan is not missing critical structure before implementation begins.

## Review Focus

The reviewer should focus on:

- whether the plan boundary is clear and not too wide
- whether runtime-consumed fields are explicit and stable
- whether phase / task decomposition is understandable and executable
- whether dependencies are well-formed and not ambiguous
- whether exit criteria and task checks are concrete enough to review later
- whether validation checklist items are explicit and not placeholder text
- whether the plan accidentally collapses into a todo list or checklist-only form
- whether required sections are present
- whether optional narrative sections are being treated as optional, rather than silently promoted into fake hard requirements
- whether there are TODO markers, placeholders, fake progress, or vague “do the thing” steps

## Blocking Findings

The reviewer should mark an issue as blocking if it would make execution or later closure unreliable. Typical blocking issues include:

- missing or contradictory runtime-consumed fields
- missing execution slices
- ambiguous dependencies
- empty or vague exit criteria for major phases
- missing validation checklist
- blocking-error handling is impossible or contradictory
- unresolved plan scope ambiguity that should force a split

## Advisory Findings

The reviewer should mark an issue as advisory if it improves readability or reviewability but should not block execution. Typical advisory issues include:

- weak decisions narrative
- thin additional notes
- missing optional narrative sections that do not affect execution or closure reliability
- minor wording clarity issues
- optional recommended sections that would help but are not essential

## Suggested Dispatch Template

```md
You are an independent plan reviewer.

Review the following plan before execution starts.

Inputs:
- Plan file: <path>
- Related design/spec documents: <path list>
- Relevant runtime contract doc: ai-dev/plans/00-plan-authoring-and-execution-guide.md

Review goals:
1. Check whether the plan is executable.
2. Check whether runtime-consumed fields are explicit and reviewable.
3. Separate blocking issues from advisory issues.
4. Do not rewrite the plan. Only review it.

Output format:

## Verdict
- Ready | Not Ready

## Blocking Issues
- <issue>

## Advisory Issues
- <issue>

## Suggested Next Action
- <short recommendation>
```

## Historical Value of Draft Review

Draft review has intercepted multiple P0-level defects before implementation. Common pitfalls to check:

| Defect Pattern | Type | Unreviewed Consequence |
|---|---|---|
| Enum value conflict (duplicate code) | Data routing corruption | Runtime data contamination requiring rollback |
| Shared enum between semantically different business types | Semantic aliasing → indistinguishable classification | Reports misclassification |
| Field referenced in design does not exist in target xmeta | Discovery only at development time → design rework | Phase rework, schedule slip |
| Field filter in plan scope (`asideFilter`, etc.) does not exist on target entity | Discovery only at implementation → rework | Phase repartitioning |

**Extension check**: Scan the plan for any field/entity/configuration that the plan assumes exists — verify each against the actual codebase. Common AI failure: referencing fields that don't exist, assuming configurations without verification.

## Notes

- This reviewer is read-only.
- It should not silently fix the plan.
- The plan author updates the document after review.
