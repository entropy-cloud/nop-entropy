---
name: grillme
description: Relentless interview that stress-tests a requirement, plan, or design until every branch of the decision tree is resolved. Use when the user says "grill me", before finalizing a non-trivial plan, or when a requirement is too fuzzy to implement safely.
---

# Grill Me

Interview the user relentlessly about the target (requirement / plan / design) until you reach shared understanding. You are the challenger, not the implementer: this skill never writes code and never starts implementation.

## When NOT To Use

- the spec is already clear and written down
- the task is a small, single-file change with obvious behavior
- the user explicitly says "just do it"

## Mechanics

Work the target as a **design tree**: every decision branches into the decisions that hang off it.

1. **Map the tree first** (silently): goal → constraints → success criteria → affected surfaces → risks.
2. **Ask in rounds — the frontier**. The frontier is every question whose prerequisites are already settled. Ask the whole frontier in one round: number each question (`Q1`, `Q2`, …), and **attach your recommended answer** to every question (`➡️ recommended: …`). Then wait.
3. **Answers reshape the tree**: settled decisions push the frontier outward. Recompute and ask the next round. A question whose answer depends on something still open belongs to a later round.
4. **Facts are your job, decisions are the user's.** When a question can be answered by reading the codebase, docs, or running a check — go answer it yourself (dispatch a subagent for exploration if one is available; otherwise read the files directly — never downgrade this to asking the user). Decisions go to the user, always. A running exploration does not block the round: only questions downstream of it wait; ask the rest of the frontier now.
5. **Challenge against the project's language**: when the user's term conflicts with an owner doc or glossary, call it out immediately ("your design doc defines X as A, but you seem to mean B — which is it?").

## Question Quality

- Every question is specific enough to have a wrong answer. "Any thoughts on auth?" is not a question.
- Surface hidden assumptions as explicit either/or choices.
- Include the negative space: what breaks, who is excluded, what happens at the boundary, what is explicitly NOT being built.
- When you believe the user's answer creates a new risk, say so in the next round with the risk stated concretely.

## Termination

The session is done when the frontier is empty — every branch visited, nothing silently assumed. Before closing:

1. Sanity pass over the three dimensions: **goal clarity** (one sentence?), **constraints** (boundaries and non-goals explicit?), **success criteria** (testable?). If any is still fuzzy, it reopens the interview.
2. Summarize the resolved decision tree in one screen.
3. **Write the outcome to a file — this is the durable part, and chat may be discarded at any moment.** Every grillme session records a discussion note: `docs/discussions/YYYY-MM-DD-HHmm-grillme-<topic>.md` (local time; create the directory with this first note if needed). The note captures: the target (requirement/plan/design under grill), the resolved decision tree (each decision: question, options considered, chosen answer, rationale), the still-open branches, and pointers to follow-up artifacts. If the grilling produced requirement content, additionally synthesize it into `docs/requirements/` and link the two; if it revised a plan/design, record the revision in the artifact itself and link it from the note.
4. Do not act on the result until the user confirms the shared understanding.

## Round Format

```
❓ **Q1 — <title>**: <body; include concrete options when they exist>
➡️ recommended: <your recommendation and why>

❓ **Q2 — <title>**: ...
➡️ recommended: ...
```

One round may carry 3–8 questions. Never ask more than one round ahead of what is settleable.
