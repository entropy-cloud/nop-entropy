# Plan Authoring And Execution Guide

> Status: active workflow guide
> Last Reviewed: 2026-05-04
> Source: adapted from `nop-chaos-flux/docs/plans/00-plan-authoring-and-execution-guide.md`

## Purpose

This guide defines how to write, execute, review, and close plans under `ai-dev/plans/`.

`ai-dev/plans/` is for execution documents. It is not an idea dump and not a substitute for design documents.

All plans under `ai-dev/plans/` must be written in English.

The guide is intentionally strict only where it improves execution quality, review quality, or closure quality.

## Lessons From History

From actual plan execution in this repo, the most common problems are:

1. Not auditing the live repo baseline before writing a plan, directly reusing old baselines or old completion notes.
2. A plan that is too wide, requiring rewrite or split later.
3. Only recording the most recent landing, not going back to check every item in the entire plan.
4. Leftover work has no clear ownership, making the plan appear complete while carrying hidden debt.
5. Marking `completed` as a side effect of finishing the last coding slice, without an independent closure audit.
6. Seeing that interfaces, types, or method names exist, and incorrectly assuming the corresponding semantics are fully implemented — without checking live behavior and focused tests.
7. All Phase/Task statuses and checkboxes left in their initial `pending` / `[ ]` state even though the implementation landed — the plan document was never updated to reflect reality.

## Runtime-Consumed Fields Vs Narrative Guidance

The plan system has two layers:

### Runtime-Consumed Fields

These are the parts that the runtime may validate strongly and may use to block completion. When AI writes a plan, these fields must stay stable and explicit:

- plan status
- current phase / current task
- phase and task structure
- dependency links
- success criteria, exit criteria, task checks, validation checklist
- closure gates
- deferred but adjudicated items
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
7. Distinguish "contract surface exists" from "contract semantics are implemented and verified". An interface, type, or method signature existing is NOT sufficient — the actual runtime behavior and focused tests must confirm the semantics.
8. If a baseline is outdated, explicitly mark the note as outdated or superseded.
9. Plans should be concise, but the baseline, execution structure, and leftover ownership must always be obvious.
10. When closing a plan, ALL phase statuses, task statuses, exit criteria checkboxes, and validation checklist items must be updated to reflect the actual state. Leaving them as `pending` / `[ ]` while the plan header says `completed` is a closure violation.
11. If a Phase changes live baseline, public contract, or owner behavior, that Phase's Exit Criteria must include corresponding documentation update items. Pure internal refactoring can explicitly write `No doc update required`, but cannot silently skip the adjudication.
12. Each execution item must be classifiable as `Fix`, `Decision`, `Proof`, or `Follow-up`. Confirmed live defects or contract drift can only be `Fix`, not downgraded to `Follow-up`.

## Closure Gates

Every plan must define explicit closure gates. These are the hard prerequisites that ALL must be `[x]` before the plan can be marked `completed`.

Closure gates are plan-level (not phase-level) and must include at minimum:

- All in-scope confirmed live defects are fixed
- All in-scope confirmed contract drifts are converged
- Necessary focused verification is complete
- All applicable build/test gates pass (`mvn compile`, `mvn test`, etc.)
- Affected `docs-for-ai/` docs are synced to live baseline, or explicitly marked `No doc update required`
- No in-scope item was silently downgraded to deferred / follow-up

The closure gates section goes between `Scope` and `Execution Plan` in the plan document.

## Deferred But Adjudicated

When an in-scope item is deferred rather than completed in the current plan, it must be explicitly classified and justified. This prevents hiding real problems as "we'll do it later."

### Classification

Each deferred item must be classified as exactly one of:

- `watch-only residual` — Known minor issue that does not affect correctness
- `optimization candidate` — Performance or quality improvement, not a correctness issue
- `out-of-scope improvement` — Valid improvement but outside current plan's scope

### Required Fields

For each deferred item:

- **Classification**: one of the above three
- **Why Not Blocking Closure**: one clear reason why this does not block plan closure
- **Successor Required**: `yes` or `no`
- **Successor Path**: plan file path if a successor plan is needed

### Non-Degradable Items

The following items **cannot** be deferred or classified as non-blocking:

- Confirmed live defects (bugs, incorrect behavior)
- Confirmed public-contract drift (API does not match documentation)
- Confirmed owner-doc drift (`docs-for-ai/` does not match live code)
- Missing focused verification for already-landed behavior
- Build or test failures

If any of these exist, the plan **must not** be marked `completed`. They must be fixed, not deferred.

### Allowed Deferral

Only the following types of work may be deferred:

- Watch-only residuals
- Optimization candidates
- Out-of-scope improvements

Each must include an explicit `Why Not Blocking Closure` reason. Deferred items without a reason are treated as unfinished work.

## Closure Audit Rule

Marking a plan `completed` requires treating "execution" and "closure audit" as two separate activities.

### Requirements

1. The closure action must happen during a dedicated closure-audit pass, not as a side effect of completing the last implementation task.
2. The closure audit must look at the live repo, not just old completion notes, old checklists, or the most recent commit messages.
3. The closure audit must be performed by an independent reviewer or an independent sub-agent (a fresh session started specifically for the audit). The implementer's own self-audit cannot be the sole basis for `completed`.
4. Every `Phase` and `Workstream` must already be `completed`. If any slice is not `completed`, the plan cannot close.
5. If a slice's work no longer belongs to this plan, it must be explicitly moved to a successor plan or cancelled with a recorded reason before closing.
6. `Validation Checklist` incomplete items can only remain if the plan is still open. If the plan closes, these items must be completed or moved out of current scope.
7. The closure audit must spot-check that "key behavior is actually implemented" — not just that interfaces, types, method names, or comments exist.
8. If the closure audit finds only partial landing, the plan must be changed to `running` (not kept as `completed`).
9. The closure audit must verify that deferred / follow-up items are honestly classified. Confirmed live defects, contract drift, owner-doc drift, or hard-gate failures cannot remain in the non-blocking area.

### Closure Audit Evidence

The closure audit must produce evidence, recorded in the plan or the corresponding daily log:

- Live code or docs paths that satisfy each slice's exit criterion
- Focused verification results or a clearly cited already-green workspace baseline
- Daily log entry recording the closure pass and any final doc-sync work
- Independent reviewer / sub-agent findings (task ID or cited review note) that explicitly check for plan/doc drift and interface-vs-semantics mismatch
- Explicit justification for each deferred item that remained non-blocking at closure

A common mistake: "The interface already exists, so this phase should be considered complete."

Correct approach: Continue checking whether that interface is actually called, whether it satisfies the documented semantics, and whether focused tests prove the behavior works. Otherwise it is at most a partial landing.

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
7. `Closure Gates`
8. `Execution Plan`
9. `Validation Checklist`
10. `Closure`

## Conditionally Required Sections

These sections are required when the condition is met, omitted otherwise:

- `Deferred But Adjudicated` — required when any in-scope item is deferred rather than completed
- `Non-Blocking Follow-ups` — required when there are follow-up items that don't block closure
- `Errors` — required when blocking errors exist (unresolved blocking errors stop closure)

## Recommended Sections

These sections are recommended when they improve clarity:

- `Questions`
- `Decisions`
- `Additional Notes`
- `Risks And Rollback`
- `Supersession Note`
- `Documentation Follow-Up`

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

## Closure Gates

> All gates must be `[x]` before `Plan Status` can change to `completed`. See Closure Audit Rule in the Guide.

- [ ] All in-scope confirmed live defects are fixed
- [ ] All in-scope confirmed contract drifts are converged
- [ ] Necessary focused verification is complete
- [ ] All applicable build/test gates pass (`mvn compile`, `mvn test`)
- [ ] Affected `docs-for-ai/` docs are synced to live baseline, or `No doc update required`
- [ ] No in-scope item was silently downgraded to deferred / follow-up

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

## Deferred But Adjudicated

> Only include this section if items are deferred. Each item must have Classification, Why Not Blocking Closure, and Successor Required.

### <Item Name>

- Classification: `watch-only residual | optimization candidate | out-of-scope improvement`
- Why Not Blocking Closure: <one clear reason>
- Successor Required: `yes | no`
- Successor Path: <plan path if yes>

## Non-Blocking Follow-ups

> Only include this section if there are non-blocking follow-up items. Confirmed live defects MUST NOT appear here.

- <follow-up item that does not affect current contract closure>
- <follow-up item that does not affect current contract closure>

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

> **Closure condition**: This section, `Closure Gates`, and every Phase's Exit Criteria must ALL be `[x]` before `Plan Status` can change to `completed`. See Closure Audit Rule in the Guide.

- [ ] [VC1] <behavior/contract result>
- [ ] [VC2] <relevant docs/examples updated>
- [ ] [VC3] <focused verification complete>
- [ ] [VC4] <no silently downgraded in-scope live defects or contract drifts>
- [ ] [VC5] <independent closure-audit by separate agent/session complete, evidence recorded>
- [ ] [VC6] `mvn compile` passes for affected modules
- [ ] [VC7] `mvn test` passes for affected modules

## Closure

Reviewed By:
Reviewed At:
Completed At:

Status Note:

<Why the plan can or cannot be closed.>

Audit Evidence:

- Reviewer / Agent: <independent reviewer or independent sub-agent>
- Evidence: <task id / daily log link / findings summary>

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
- closure gates are defined and concrete
- deferred items (if any) have honest classifications and reasons

### Closure Audit

Before marking a plan `completed`, follow the Closure Audit Rule (see above). At minimum:

- all required checks are complete (`[x]`)
- all validation checklist items are complete (`[x]`)
- all closure gates are complete (`[x]`)
- all phase and task statuses are updated to `completed`
- no unresolved blocking error remains
- no unfinished phase or task remains
- no in-scope live defect was silently deferred
- closure note and follow-up ownership match the actual state
- audit evidence from an independent reviewer/agent is recorded

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

Before closure, do ALL of the following:

1. Re-read the entire plan, not just the last landing.
2. Check every phase/workstream exit criterion — update each `[ ]` to `[x]` if satisfied, or fix the implementation.
3. Check every task check — update each `[ ]` to `[x]` if satisfied.
4. Check the Validation Checklist — update each `[ ]` to `[x]`.
5. Check the Closure Gates — update each `[ ]` to `[x]`.
6. Update ALL phase and task statuses from `pending`/`running` to `completed`. Leaving statuses as `pending` while the plan header says `completed` is a closure violation.
7. Move leftover work into explicit follow-up ownership.
8. Distinguish "interface exists" from "behavior is verified" — spot-check live code paths and tests.
9. Verify deferred items are honestly classified (no live defects hidden as follow-ups).
10. Record independent closure-audit evidence from a separate agent/session.

If these are not done, do not mark the plan `completed`.

## Practical Rule

The plan does not need to be long, but four things must always be obvious:

- what the baseline is
- where execution currently is
- who owns the remaining work
- what must be true before the plan can close (closure gates)
