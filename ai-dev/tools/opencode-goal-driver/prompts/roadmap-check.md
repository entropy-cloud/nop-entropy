Check the roadmap for module {{module}} and select the **single most urgent** work item.

Steps:
1. Find *roadmap*.md files under ai-dev/design/ related to {{module}}
2. Read the full roadmap, focusing on: §2 "Current State", §3 "Priority Guide", §4 "Work Items", §5 "Tech Debt"
3. Find all items marked with ❌ or ⚠️
4. For each unfinished item, use grep/glob to check whether the implementation already exists in the codebase (exclude _gen generated code)
5. Select the **single most urgent** item by priority (high to low):
   - P0 tech debt (build/loader failures)
   - Layer 0 blockers
   - Layer 1 core interfaces
   - Higher-layer extensions
   - Within the same layer, prefer items with the most dependents (fewest dependencies)
6. Scan the most recent 5 plan files in ai-dev/plans/ (sorted by date descending, skip 00-* guide files). For each plan:
   - Look for sections like "Follow-up", "Deferred Items", "Unfinished Work", "Known Issues"
   - Extract any items listed there
   - Check whether each item is already covered by an active plan (a plan whose Status is "active" or "in progress")
   - Items NOT covered by any active plan are carry-over candidates
7. When selecting the single most urgent item, apply this priority order:
   - **Carry-over**: followup/deferred items from previous plans (highest — unfinished work from prior iteration)
   - P0 tech debt (build/loader failures)
   - Layer 0 blockers
   - Layer 1 core interfaces
   - Higher-layer extensions
   - Within the same tier, prefer items with the most dependents (fewest dependencies)
8. Output the selection result

If all items are implemented (every ❌ has actual code) and no carry-over items exist: <AI_STEP_RESULT>complete</AI_STEP_RESULT>
If unfinished items exist: <AI_STEP_RESULT>pending</AI_STEP_RESULT>
<NEXT_ITEM id="item-id" layer="layer" priority="P0|P1|P2" type="roadmap|carry-over" source-plan="path/to/original-plan.md">reason and current status</NEXT_ITEM>
<ROADMAP_ITEMS><item id="id" priority="P0|P1|P2|P3">summary of all unfinished items</item></ROADMAP_ITEMS>