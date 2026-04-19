# Plan Authoring And Execution Guide

> Status: active workflow guide
> Last Reviewed: 2026-04-14
> Source: adapted from `nop-chaos-flux/docs/plans/00-plan-authoring-and-execution-guide.md`

## Purpose

This guide defines how to write, execute, review, and close plans under `ai-dev/plans/`.

`ai-dev/plans/` is for execution documents. It is not an idea dump and not a substitute for design documents.

All plans under `ai-dev/plans/` must be written in English.

The guide is intentionally strict only where it improves execution quality, review quality, or closure quality.

## Runtime-Consumed Fields Vs Narrative Guidance

The plan system has two layers:

### Runtime-Consumed Fields

These are the parts that the runtime may validate strongly and may use to block completion. When AI writes a plan, these fields must stay stable and explicit:

- plan status
- current phase / current task
- phase and task structure
- dependency links
- success criteria, exit criteria, task checks, validation checklist
- unresolved blocking errors
- closure state

The practical meaning is simple:

- if the runtime consumes a field, AI should not treat it as loose prose
- if a field participates in completion blocking, AI should keep it concrete and reviewable

### Narrative Guidance

These are primarily for AI and human readers. They help a plan stay readable and reviewable, but they are not all part of the current XML hard contract:

- purpose
- current baseline
- non-goals
- scope
- questions
- decisions
- additional notes
- risks and rollback
- supersession note

`Errors` is a special case. It is not required as a standing narrative section, but if blocking errors exist they must be recorded clearly because unresolved blocking errors affect closure.

## Minimum Rules

1. Check the live repo before writing `Current Baseline`.
2. One plan should own one clear result surface. If it becomes too wide, split it.
3. Every plan must have a plan-level status, execution-slice statuses, and a validation checklist.
4. `completed` means the current plan-owned scope is truly done and any leftover work has been explicitly moved out.
5. `completed` must come from a separate closure audit, not from finishing the last coding slice.
6. If any execution slice still has unfinished or blocked required work, the plan must not be marked `completed`.
7. Distinguish “contract surface exists” from “contract semantics are implemented and verified”.
8. If a baseline is outdated, explicitly mark the note as outdated or superseded.
9. Plans should be concise, but the baseline, execution structure, and leftover ownership must always be obvious.

## Required Status Markers

### Plan-Level Status

Use only runtime-aligned status values:

- `pending`
- `running`
- `completed`
- `failed`

Each plan header should include at least:

- `> Plan Status: pending | running | completed | failed`
- `> Last Reviewed: YYYY-MM-DD`
- `> Current Phase: <phase-name>`
- `> Current Task: <task-id>`
- `> Source: <source-1>, <source-2>`
- `> Related: <related-plan-1>, <related-plan-2>` (optional)

### Execution-Slice Status

Every execution slice must have its own status using the same aligned set:

- `pending`
- `running`
- `completed`
- `failed`

Use `Phase` for ordered work and `Workstream` for topic-parallel work.

### Checklist Status

Use checkbox markers consistently:

- unchecked: `[ ]`
- checked: `[x]`

## Required Sections

These sections should always exist in a normal execution plan:

1. `Purpose`
2. `Goal`
3. `Current Baseline`
4. `Success Criteria`
5. `Non-Goals`
6. `Scope`
7. `Execution Plan`
8. `Validation Checklist`
9. `Closure`

## Recommended Sections

These sections are recommended when they improve clarity:

- `Questions`
- `Decisions`
- `Additional Notes`
- `Risks And Rollback`
- `Supersession Note`
- `Documentation Follow-Up`

`Errors` should be added whenever execution hits errors worth tracking. If any blocking error remains unresolved, it stops closure.

## Canonical Template

```md
# <Plan Title>

> Plan Status: pending
> Last Reviewed: YYYY-MM-DD
> Current Phase: phase-1
> Current Task: T1
> Source: <source-1>, <source-2>
> Related: <related-plan-1>, <related-plan-2>

## Purpose

<One paragraph describing what this plan is trying to achieve.>

## Goal

<Single top-level goal statement.>

## Current Baseline

<Short baseline summary of the current repo/runtime state.>

## Success Criteria

- [SC1] <Success criterion>
- [SC2] <Success criterion>

## Non-Goals

- [NG1] <Non-goal item>
- [NG2] <Non-goal item>

## Scope

### In Scope

- [S1] <In-scope item>
- [S2] <In-scope item>

### Out Of Scope

- [O1] <Out-of-scope item>
- [O2] <Out-of-scope item>

## Read Files

- `path/to/fileA` - <why it was read>
- `path/to/fileB` - <why it was read>

## Written Files

- `path/to/fileC` - <what changed>
- `path/to/fileD` - <what changed>

## Execution Plan

### Phase: phase-1

Kind: phase
Status: pending
Targets: `path/or/module/A`, `path/or/module/B`

Description:

<Phase description>

Exit Criteria:

- [ ] [C1] <criterion text>
- [ ] [C2] <criterion text>

#### Task: T1

Status: pending
Depends On:

Instructions:

<Task instructions>

Result Message:

<Optional result summary>

Checks:

- [ ] [CHK-T1-1] <required task check>
- [ ] [CHK-T1-2] <required task check>

### Phase: phase-2

Kind: workstream
Status: pending
Targets: `path/or/module/C`

Description:

<Phase or workstream description>

Exit Criteria:

- [ ] [C3] <criterion text>

#### Task: T2

Status: pending
Depends On: T1

Instructions:

<Task instructions>

Result Message:

<Optional result summary>

Checks:

- [ ] [CHK-T2-1] <required task check>

## Questions

- [Q1] Task: T1 | Asked: YYYY-MM-DDTHH:mm:ssZ | Answered:
  - Question: <question text>
  - Answer:

## Decisions

- [D1] Task: T2 | Made At: YYYY-MM-DDTHH:mm:ssZ
  - Decision: <decision text>
  - Rationale: <rationale>

## Errors

- [E1] Task: T2 | Attempt: 1 | Encountered: YYYY-MM-DDTHH:mm:ssZ | Resolved: | Blocking: true
  - Error: <error text>
  - Resolution:

## Additional Notes

- [N1] Task: T1 - <free-form note>

## Validation Checklist

- [ ] [VC1] <required validation check>
- [ ] [VC2] <required validation check>

## Closure

Reviewed By:
Reviewed At:
Completed At:

Status Note:

<Why the plan can or cannot be closed.>

Audit Evidence:

<Independent review evidence or runtime evidence>

Follow-Ups:

- [F1] <follow-up item>
- [F2] <follow-up item>
```

## What The Runtime Actually Cares About

AI does not need to think about serialization details when writing a plan. It only needs to know which parts of the plan are consumed by the runtime and may block completion.

The runtime primarily cares about:

- top-level status and current execution pointer
- phase / workstream structure
- task structure and dependencies
- exit criteria and task checks
- validation checklist
- unresolved blocking errors, if any
- closure state

The runtime does not need to fully understand narrative sections such as:

- Questions
- Decisions
- Additional Notes
- Risks And Rollback

Those sections still matter, but they are for clarity, review, and continuation rather than for hard completion gating. `Errors` remains the exception when a blocking error is present.

## Review Focus

Plan review should happen at two different times:

1. before execution starts
2. before the plan is marked `completed`

Use independent reviewers for both cases.

### Pre-Execution Review

Before execution starts, review whether:

- required sections are present
- runtime-consumed fields are explicit
- phase and task decomposition is clear
- dependencies are executable
- exit criteria and checks are concrete enough to evaluate later
- the plan is still a plan, not just a todo list or checklist dump

### Closure Audit

Before marking a plan `completed`, audit whether:

- all required checks are complete
- all validation checklist items are complete
- no unresolved blocking error remains
- no unfinished phase or task remains
- closure note and follow-up ownership match the actual state

### Reviewer Prompts

Use these prompts for independent review:

- `ai-dev/skills/plan-reviewer-prompt.md`
- `ai-dev/skills/plan-closure-audit-prompt.md`

## How To Use The Guide

### When Drafting

1. Write `Current Baseline` only after checking the live repo.
2. If `Out Of Scope` is vague, the plan is probably too wide.
3. If `Success Criteria` are vague, closure will be weak.
4. If a slice has no clear `Exit Criteria`, it is probably not executable enough.
5. Prefer repo-observable exit criteria: concrete files, concrete APIs, concrete behaviors, concrete tests.

### When Executing

1. Move a slice to `running` when work starts.
2. Move a slice to `completed` only when its required checks and exit criteria are satisfied.
3. Do not mark a slice `completed` if only interfaces or scaffolding landed but semantics are still missing.

### When Closing The Plan

Before closure:

1. Re-read the entire plan, not just the last landing.
2. Re-check every phase/workstream exit criterion.
3. Re-check the validation checklist.
4. Move leftover work into explicit follow-up ownership.
5. Distinguish “interface exists” from “behavior is verified”.
6. Record independent closure-audit evidence.

If these are not done, do not mark the plan `completed`.

## Practical Rule

The plan does not need to be long, but three things must always be obvious:

- what the baseline is
- where execution currently is
- who owns the remaining work
