# AggregationContext & BizModel Split Design

> Status: **placeholder** — design decisions will be added during Phase 1 of `2026-07-22-0900-2-nop-metadata-aggregation-context-bizmodel-split.md`
> Last Reviewed: 2026-07-22

This file will document the split decisions for:

- `AggregationContext.java` (1854 lines) — extract helpers to `AggregationHelper.java`
- `NopMetaTableBizModel.java` (931 lines) — extract non-CRUD actions to dedicated action classes
- `NopMetaLineageEdgeBizModel.java` (878 lines) — extract lineage actions to dedicated action classes
