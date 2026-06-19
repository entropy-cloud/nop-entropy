Check the roadmap for module {{module}} and select the most urgent work item(s), bundling small siblings to meet the minimum plan workload (see step 8).

Steps:
1. Find *roadmap*.md files under ai-dev/design/ related to {{module}}
2. Read the full roadmap, focusing on: §2 "Current State", §3 "Priority Guide", §4 "Work Items", §5 "Tech Debt"
3. Find all items marked with ❌ or ⚠️
4. Scan the most recent 5 plan files in ai-dev/plans/ (sorted by date descending, skip 00-* guide files). For each plan:
   - Look for sections like "Follow-up", "Deferred Items", "Unfinished Work", "Known Issues"
   - Extract any items listed there
   - Check whether each item is already covered by an active plan (a plan whose Status is "active" or "in progress")
   - Items NOT covered by any active plan are carry-over candidates
5. Determine the top 3 candidate items to consider:
   - Priority order for candidates: carry-over items (highest) → P0 tech debt → Layer 0 blockers → Layer 1 core → higher layers
   - Within the same tier, prefer items with the most dependents (fewest dependencies)
6. For the top 3 candidates only, do a quick code existence check: use a **single** grep or glob that covers all 3 candidates at once (do NOT run one tool call per item)
7. Select the **single most urgent** item from the verified candidates as the **primary item**.
8. **Bundle check (mandatory)** — every plan must carry sufficient workload to justify its ceremony cost (plan doc + independent audit + closure audit + build + commit). After selecting the primary item:
   - **Estimate** the primary item's production-code change size (non-test `.java` lines). Read the referenced source locations to estimate; do not guess.
   - **Must-bundle threshold**: if the primary item alone is **< ~100 lines** of production-code change, scan `<ROADMAP_ITEMS>` for bundle-eligible siblings. Bundle until the combined estimate reaches ~100 lines or no eligible sibling remains. A plan that changes only 1–50 lines of production code is **never acceptable** as a standalone plan.
   - **Bundle-eligible** (ALL must hold): same file / same class / same class hierarchy / same fix pattern (e.g. the same wrap/delegate/refactor applied at different call sites). Items must share one coherent design rationale.
   - **Never bundle**: items from different modules, different risk profiles (Protected Area vs non-Protected), or items requiring understanding different execution models.
   - If the primary is already ≥ ~100 lines, bundling is optional — bundle only when it clearly reduces ceremony without bloating scope (≤ ~6 phases, ≤ ~400 lines combined).
   - Output one `<NEXT_ITEM>` tag for the primary, plus one per bundled sibling. The DRAFT step will bundle all provided `<NEXT_ITEM>`s into a single plan.

If all items are implemented (every ❌ has actual code) and no carry-over items exist: <AI_STEP_RESULT>complete</AI_STEP_RESULT>
If unfinished items exist: <AI_STEP_RESULT>pending</AI_STEP_RESULT>
<NEXT_ITEM id="item-id" layer="layer" priority="P0|P1|P2" type="roadmap|carry-over" source-plan="TODO_REPLACE_WITH_REAL_PATH_IF_CARRY_OVER">reason and current status</NEXT_ITEM>
<ROADMAP_ITEMS><item id="id" priority="P0|P1|P2|P3">summary of all unfinished items</item></ROADMAP_ITEMS>