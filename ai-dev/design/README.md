# Design Docs Index

## Purpose

`ai-dev/design/` is the curated home for Nop Entropy's architecture decisions, usage contracts, and requirement specs that serve as the **attractor layer** for AI-driven development.

This subtree carries:

- **governing principles** that define the stable direction of the architecture
- **subsystem design docs** that record architecture decisions, rejected alternatives, usage contracts (meter names, API naming, module boundaries), and requirement specs
- **cross-cutting constraints** that apply across subsystems

This subtree does not carry execution history, migration diaries, rejected alternatives presented as historical narrative, or code-level details. Put those in `ai-dev/analysis/`, `ai-dev/plans/`, `ai-dev/logs/`, or `ai-dev/discussions/`.

Design docs must explain why the current design exists, what constraints it preserves, and what nearby misreadings it rejects. The rule is not "conclusion only". The rule is "current-design rationale only".

## Hierarchy

### 1. Writing Guide

- `00-design-writing-guide.md`

Role: defines what belongs in design docs, what does not, and the pseudocode judgment standard.

### 2. Cross-Cutting Constraints

- `code-quality/checkstyle-configuration.md` — static analysis rules

### 3. Subsystem Design

Each subsystem directory contains architecture decisions and usage contracts for a bounded area. Subsystems with a `README.md` define their own internal layering and reading order.

| Directory | Subsystem | README | Status |
|-----------|-----------|--------|--------|
| `nop-ai-agent/` | AI Agent DSL + Engine | [README](nop-ai-agent/README.md) | active — 8-layer structure (Vision / Architecture Baseline / Execution Model / DSL / Engine / Semantics / Strategy / Vision) |
| `nop-job/` | Job Scheduling | [README](nop-job/README.md) | active — AGE owner-doc (Vision / Architecture Baseline / Execution Strategy / Observability / Cluster) |
| `nop-code/` | Code Indexing & Semantic Analysis | [README](nop-code/README.md) | active — AGE owner-doc (Vision / Architecture Baseline / Query / Analysis / Integration) |
| `nop-stream/` | Stream Processing | [README](nop-stream/README.md) | active — AGE 8-layer structure (Vision / Architecture Baseline / Core Model / Graph & Execution / Checkpoint / State & Time / Integration / Reference) |
| `nop-nosql/` | NoSQL Data Access | [README](nop-nosql/README.md) | active — business-semantic NoSQL abstraction, Redis driver comparison |
| `crud/` | CRUD Relation Write Mode | *(not yet created)* | active |
| `word-editor/` | Online Word Editor Model | *(not yet created)* | active |

## Precedence Model

1. **Nop platform principles** (reversible computation, model-first, delta customization) take top-level precedence. These are codified in `docs-for-ai/00-start-here/ai-defaults.md` and the platform's theoretical foundations.
2. **Subsystem design docs** keep local precedence inside their own subject area. For example, `nop-job/invoker-design.md` owns the routing contract for job invokers.
3. When two subsystem designs make conflicting claims about a shared boundary, the conflict must be resolved by updating the docs — the older or less precise doc yields.
4. `docs-for-ai/` owns platform usage knowledge (API, conventions, development patterns). `ai-dev/design/` owns architecture decisions for platform development. When both cover the same topic, `docs-for-ai/` is the usage-facing source of truth; `ai-dev/design/` is the decision-facing source of truth.

## Relationship to `docs-for-ai/`

| `docs-for-ai/` | `ai-dev/design/` |
|----------------|-------------------|
| Platform **usage** knowledge: how to use Nop to build apps | Platform **development** decisions: how and why Nop is built this way |
| API, conventions, development patterns, runbooks | Architecture decisions, usage contracts, requirement specs |
| Source of truth for app developers | Source of truth for platform developers |
| Normative — describes what works today | Normative — describes why it works this way and what it must preserve |

## Relationship to `docs/`

`docs/` is human-written historical documentation (tutorials, theory, API docs). It is read-only for AI development purposes. `ai-dev/design/` does not replace or duplicate `docs/`; it records architecture decisions that `docs/` was never designed to capture.
