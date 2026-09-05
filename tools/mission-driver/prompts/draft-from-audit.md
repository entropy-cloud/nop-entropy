Read `{{planGuide}}` **completely**. It defines the plan format, status lifecycle, and how plans relate to audit findings.

Read all audit result files in `{{auditsDir}}/` that have `Audit Status: open` **completely**. Each finding is priority-tagged `[P0]` / `[P1]` / `[P2]` (see the audit prompts).

**Drafting gate — only `P0`+`P1` warrant plans:**

- Collect the `P0` and `P1` findings across ALL open audits. Draft 1-3 remediation plans TOTAL covering ALL those `P0`+`P1` findings (NOT 1-3 per audit). Bundle related findings; split only when closure surfaces differ. `P0`/`P1` are non-degradable: each must land in a plan as a `Fix` item.
- `P2` findings do NOT get their own plan. Append them to a `## Follow-up Backlog` section (create if absent) in the mission roadmap or an audit-followups note under `{{backlogDir}}`, each with its source audit path so it stays traceable.

## Rules

1. **Order**: When drafting multiple plans, assign them an explicit execution order with `{N}` (single-digit sequence number: 1, 2, 3...). Plans that unblock others come first.

2. **Status**: Use `> Plan Status: draft`.

3. **Close every source audit** after processing (prevents re-processing the same findings next round):
   - An audit that contributed a `P0`/`P1` finding to a drafted plan → change its `> Audit Status: open` to `> Audit Status: planned`.
   - An audit whose findings are **all `P2`** (no `P0`/`P1`) → do NOT draft a plan; move its `P2` items to the follow-up backlog and change its `> Audit Status: open` to `> Audit Status: triaged`. `triaged` is a terminal, non-open state: it is NOT counted by `openAudits()`, so a `P2`-only audit no longer keeps the mission in the audit loop.

4. **Review before active**: For each drafted plan, follow the `Plan Review Rule` in `{{planGuide}}` — use an independent sub-agent (fresh session) to review repeatedly until consensus. **Only change `> Plan Status: draft` to `> Plan Status: active` after consensus is reached**; otherwise leave it `draft`.

When plans are created (at least one `P0`/`P1` finding existed), return results in the following format:
```
<AI_STEP_RESULT>created</AI_STEP_RESULT>
<FLOW_VARS>
  <PLAN_FILE>{{plansDir}}/{YYYY-MM-DD-HHmm}-{N}-{slug}.md</PLAN_FILE>
</FLOW_VARS>
```

If nothing to draft (no open audit has any `P0`/`P1` finding — i.e. all open audits are clean or `P2`-only, now marked `triaged`), return results in the following format:
```
<AI_STEP_RESULT>nothing</AI_STEP_RESULT>
```

Your output MUST end with exactly one `<AI_STEP_RESULT>` marker (`created` with the `<FLOW_VARS>` block, or `nothing`). This is the only marker that is parsed; a missing or malformed marker triggers an additional correction run, so emit it exactly as shown.
