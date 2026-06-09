# Roadmap Check Procedure

Check the roadmap for module {module} and select the **single most important** work item to tackle now.

## Steps

1. Find *roadmap*.md files under ai-dev/design/ related to {module}
2. Read the full roadmap, focusing on the current status, priority guidance, work item list, and technical debt sections
3. Find all items marked with ❌ or ⚠️
4. For each unfinished item, use grep/glob to check whether an implementation already exists in the codebase (exclude _gen generated code)
5. Select the **single most urgent** work item by this priority order:
   - P0 technical debt (build/load failures)
   - Foundation layer (Layer 0) blockers
   - Layer 1 core interfaces
   - Higher-layer extensions
   - Within the same layer, prefer items with the most dependents
6. Output the selection

## Output Format

If no unfinished items remain (all ❌ are actually implemented):
```
<ROADMAP_RESULT>complete</ROADMAP_RESULT>
```

If unfinished items exist:
```
<ROADMAP_RESULT>pending</ROADMAP_RESULT>
<NEXT_ITEM id="item-id" layer="layer" priority="P0|P1|P2">Rationale and current status</NEXT_ITEM>
<ROADMAP_ITEMS><item id="id" priority="P0|P1|P2|P3">Summary of all unfinished items</item></ROADMAP_ITEMS>
```
