# Documentation Routing Audit Prompt

Use this prompt when auditing whether AGENTS.md + docs-for-ai routing correctly guides AI agents to the right documents.

## Purpose

Verify that routing tables, audience declarations, and cross-references are unambiguous, complete, and internally consistent — by simulating diverse agent scenarios.

## Execution

### Step 1: Collect Current State

Read these files from the current project:

1. `AGENTS.md` — "By Task" table, "By Code Location" table, "Protected Areas" table
2. `docs-for-ai/INDEX.md` — audience declaration
3. `ai-dev/README.md` — audience declaration
4. Any standalone README with audience declaration (e.g., `nop-entropy-e2e/README.md`)

Do NOT read the actual content of the routed documents (e.g., `service-layer.md`). This audit tests whether the routing layer is self-sufficient.

### Step 2: Design Simulation Scenarios

Create at least 4 scenarios covering these dimensions:

| Dimension | Must-cover values |
|-----------|------------------|
| Reader role | Platform developer, business app developer |
| Task type | New feature, bug fix, plan creation |
| Code area | Framework core (`nop-core/`), business module (`nop-auth/`), demo (`nop-chaos/`), test infra (`nop-entropy-e2e/`) |
| Governance | Protected area (plan-first / ask-first), unrestricted |
| Cross-domain | Task that triggers both By Task and By Code Location |

Each scenario specifies only: **reader's working directory + task description**. No hints about which documents to read.

### Step 3: Run Scenarios via Subagents

For each scenario, launch a `general` subagent that:

1. Has access to the routing tables and audience declarations from Step 1 (paste them into the prompt)
2. Does NOT have access to this audit prompt's scenario answers
3. Reports:
   - Which documents it would read, in order
   - Which it would skip, and why
   - Any confusion or ambiguity (must cite specific routing entry)
   - Whether it correctly identified Protected Area constraints

### Step 4: Classify Findings

| Category | Meaning |
|----------|---------|
| **Missing route** | A task/code-area combination has no routing entry |
| **Wrong route** | Routing points to a document that doesn't match the task |
| **Cross-reference trap** | Two documents reference each other without clear audience distinction |
| **Governance gap** | Protected Area constraint not surfaced at the routing decision point |
| **Ambiguous wording** | Phrasing that means different things to different readers |
| **Priority conflict** | By Task and By Code Location point to different docs, no merge rule stated |

### Step 5: Fix and Retest

Apply fixes, then re-run only the failed scenarios. Iterate until all pass.

## Notes

- Minimum 4 scenarios per audit. Cover all routing table rows.
- Re-audit after any significant change to AGENTS.md routing tables or audience declarations.
- This is a read-only audit. Do not modify routing tables during Steps 2–4. Apply fixes only in Step 5.
