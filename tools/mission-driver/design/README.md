# mission-driver design docs

> Status: active
> Created: 2026-06-11

This directory records design decisions for the `tools/mission-driver` tool.

## Reading Order

| Document | Description |
|----------|-------------|
| `flow-engine-design.md` | Engine layer: Step, Transition, StepResult, subflows |
| `group-step-design.md` | Group step: rounds and sub-step mechanism |
| **`mission-driver-flow-design.md`** | Top-level flow orchestration: mission-driven work loop |

## Scope Boundaries

- **Engine docs**: explain the general execution mechanism of Step/Transition/Group/Subflow, without covering specific business steps.
- **Flow docs**: explain the arrangement of specific steps, transition logic, and design rationale.
