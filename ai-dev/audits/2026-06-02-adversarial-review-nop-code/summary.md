# Adversarial Review: nop-code r7 Summary

**Date**: 2026-06-02
**Module**: nop-code
**New Findings**: 6 (AR-88 through AR-93)
**Fixed Since Last Review**: 3 (AR-77, AR-80, AR-82 from r5/r6)
**Known Unfixed**: ~13 from prior rounds

## Key Findings

1. **AR-88 (P1): NopCodeSymbolBizModel 15/17 methods lack @Auth** — Systemic security gap: symbol queries, type/call hierarchy, dead code detection, module digest, public surface, reference lookup, implementation lookup all accessible without any permission check. This is a broader finding than AR-82 (which only covered `detectFlows`).

2. **AR-90 (P1): `detectDeadCode` is @BizQuery without @Auth** — Triggers full symbol table + call graph rebuild (potentially 100K+ symbols), but classified as a read-only query with zero auth. Should be `@BizMutation` + `@Auth(roles = "admin")` like `detectFlows`.

3. **AR-91 (P1): FlowDetector.evictOverflow potential infinite loop** — `ConcurrentHashMap.Iterator.remove()` called without synchronization; if remove silently fails under concurrent load, the while loop never exits.

4. **AR-89 (P2): NopCodeFileBizModel 5 methods lack @Auth** — Complements AR-88; file-level queries bypass index-level permission checks.

5. **AR-93 (P2): NopCodeFlowMembership delete via nested property filter** — `flow.indexId` requires implicit SQL JOIN; if ORM engine behavior changes, orphan records accumulate.

6. **AR-92 (P3): CodeCacheManager redundant ConcurrentHashMap + synchronized** — Design inconsistency with FlowDetector's unsynchronized pattern.

## Verdict

`<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>`
