Check the roadmap for module {module} and select the **single most urgent** work item.

Steps:
1. Find *roadmap*.md files under ai-dev/design/ related to {module}
2. Read the full roadmap, focusing on: §2 "Current State", §3 "Priority Guide", §4 "Work Items", §5 "Tech Debt"
3. Find all items marked with ❌ or ⚠️
4. For each unfinished item, use grep/glob to check whether the implementation already exists in the codebase (exclude _gen generated code)
5. Select the **single most urgent** item by priority (high to low):
   - P0 tech debt (build/loader failures)
   - Layer 0 blockers
   - Layer 1 core interfaces
   - Higher-layer extensions
   - Within the same layer, prefer items with the most dependents (fewest dependencies)
6. Output the selection result

If all items are implemented (every ❌ has actual code): <AI_STEP_RESULT>complete</AI_STEP_RESULT>
If unfinished items exist: <AI_STEP_RESULT>pending</AI_STEP_RESULT>
<NEXT_ITEM id="item-id" layer="layer" priority="P0|P1|P2">reason and current status</NEXT_ITEM>
<ROADMAP_ITEMS><item id="id" priority="P0|P1|P2|P3">summary of all unfinished items</item></ROADMAP_ITEMS>