Read `{{multiAuditPrompt}}` **completely** and follow it precisely.

Perform a multi-dimensional audit on mission `{{missionName}}`. Focus on `{{moduleDir}}/` — code, config, tests, and public contracts (exports, API surface). Cross-reference against architecture docs for documented contract drift.

Write results to `{{auditsDir}}/{{TIMESTAMP}}-multi-audit-{{missionName}}.md`. The result file MUST start with:

```
> Audit Status: open
> Audit Type: multi-dimensional
> Mission: {{missionName}}
```

## Priority every finding — `[P0]` / `[P1]` / `[P2]`

Prefix EVERY finding in the report body with a priority tag and one-line justification:

- **`[P0]`** — blocking: contract break, incorrect behavior, data loss, security, failing/absent test for changed behavior. MUST be fixed.
- **`[P1]`** — material: a real defect or contract drift that should be fixed but is not blocking. MUST be fixed.
- **`[P2]`** — trivial / non-blocking polish: doc line-number rot, wording, naming consistency, cosmetic nits. Record it, but it does NOT by itself warrant a remediation plan.

Downstream, only `P0`+`P1` findings drive remediation-plan drafting; `P2`-only audits are triaged to the follow-up backlog without a plan. Do not inflate a cosmetic nit to `P1` — that is the exact behavior this grading prevents.

Your output MUST end with exactly one `<AI_STEP_RESULT>` marker:
- Any finding at all (`P0`/`P1`/`P2`): `<AI_STEP_RESULT>issues</AI_STEP_RESULT>`
- Clean (no finding of any priority): `<AI_STEP_RESULT>clean</AI_STEP_RESULT>`
