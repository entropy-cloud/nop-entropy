# 2398 full-code-review R1.32 — nop-ai 增量修复批（P1-7.1-001/002）

> Plan Status: active
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.32；findings `P1-7.1-001`（WI25 懒重投递状态机破坏）+ `P1-7.1-002`（PgVectorStore.search 列读取缺失），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.1-nop-ai-incremental-audit.md`
> Related: P1-7.1-001 与 WI25（4be11f2eba 引入）；P1-7.1-002 diff 锚 97be7d367a
> Owner Doc: `docs-for-ai/03-modules/nop-ai.md`

## Purpose

收口 nop-ai 两条 P1：①DefaultBackgroundTaskRegistry 懒重投递把簿记态 NOTIFICATION_FAILED 传桥且重投成功后无条件置 COMPLETED——源状态 FAILED/TIMEOUT 的任务被永久误报完成（状态机契约破坏）；②PgVectorStore.search SELECT 含 content/metadata 列但 ResultSet 仅回读列 1——生产 RAG 检索 hits 的 content/docId/chunkIndex 恒 null（静默错误结果）。

## Current Baseline

- **P1-7.1-001**：`DefaultBackgroundTaskRegistry`——complete():156 桥失败时 `entry.status = STATUS_NOTIFICATION_FAILED` **覆盖原始终态**（原态丢失）；retryBridgeIfNeeded:233-239 把簿记态传桥（桥收到错误状态）且成功后无条件 `entry.status = STATUS_COMPLETED`（FAILED/TIMEOUT 源被误报完成）。消费方全量枚举（audit 复核）：终态生产方 3 处（BashExecutor TIMEOUT/FAILED、stop()、run() 安全网）、桥唯一装配点 DefaultAgentEngine:753、status 消费方=task-output/task-stop 渲染+桥通知。状态机契约（javadoc :23-24）：RUNNING → COMPLETED/FAILED/TIMEOUT first-terminal-wins + NOTIFICATION_FAILED 簿记态。测试仅覆盖 COMPLETED 源路径（TestDefaultBackgroundTaskRegistry）。
- **P1-7.1-002**：`PgVectorStore.search:188-209`——`SELECT embedding, content, metadata` 三列但 `rs.getString(1)` 仅回读 embedding。生产装配亲证（rag-defaults.beans.xml Delta 替换）；消费链三环（ingest:72-75 写入 content/docId/chunkIndex/indexId 元数据 → store search → synthesize:65 hits.getContent() 恒 null → 合成上下文退化 `[1] null`）。toMetadataJson:269 写 jsonb 时跳过 content 键（content 独立列）——回读时 content 从列 2、docId/chunkIndex/indexId 从列 3 jsonb。
- **测试基建**：TestDefaultBackgroundTaskRegistry（COMPLETED 源路径已有）；TestPgVectorIntegration env 门控（NOP_TEST_PGVECTOR_JDBC_URL，现只断言个数——audit 指出内容断言缺口）。

## Goals

- P1-7.1-001：Entry 记录原始终态（first-terminal）；桥回调传原始终态（非簿记态）；重投成功后状态回到原始终态（FAILED/TIMEOUT 不再被改写 COMPLETED）；补 FAILED/TIMEOUT 源重投用例。
- P1-7.1-002：search 回读 content/metadata 列并填充 VectorData（content 键 + jsonb 各键）；pgvector 集成测试补内容断言。
- 判别测试双腿 stash 实证；nop-ai-agent/nop-ai-rag 测试全绿。

## Non-Goals

- WI25 其余面；P2-7.1-001..006 台账行；InMemoryVectorStore（无缺陷）；pgvector 实库 CI 化（env 门控维持）。

## Scope

### In Scope

- `DefaultBackgroundTaskRegistry`：Entry.originalStatus 字段 + complete() 记录 + retryBridgeIfNeeded 语义修正
- `PgVectorStore.search`：content/metadata 列回读 + jsonb 解析填充
- 测试：FAILED/TIMEOUT 源重投判别用例；TestPgVectorIntegration 内容断言增强
- owner doc：nop-ai.md 后台任务状态机注记

### Out Of Scope

- Non-Goals 全部；AgentToolDispatcher 复杂度（复杂度巡检移交 R1.0 评估）；tool.xdef 域外契约面（7.4 注记）

## Execution Plan

### Phase 1 - 双修 + 判别测试

Status: planned
Targets: 上列文件

- Item Types: `Fix`

- [ ] Entry 增 `originalStatus` 字段；complete() 首个终态时记录（NOTIFICATION_FAILED 覆盖之前）
- [ ] retryBridgeIfNeeded：桥回调传 `entry.originalStatus`（非簿记态）；重投成功后 `entry.status = originalStatus`（FAILED/TIMEOUT/COMPLETED 各归其位——不再无条件 COMPLETED）；javadoc 记录状态机修正
- [ ] PgVectorStore.search：回读列 2 content → addMetadata("content",…)；列 3 metadata jsonb → 解析逐键 addMetadata（docId/chunkIndex/indexId）
- [ ] 测试：①TestDefaultBackgroundTaskRegistry 新增——FAILED 源 + 桥先败后成：重投成功后 status==FAILED（修复前 COMPLETED）且桥收到 FAILED（非 NOTIFICATION_FAILED）；TIMEOUT 源同型；COMPLETED 源既有用例回归。②TestPgVectorIntegration.storeSearchDeleteLifecycle 增强：vec() 带 content 元数据，search 后断言 hits.get(0).getMetadata("content") 非空且 id 元数据正确（env 门控维持——无实库时跳过属既有声明）
- [ ] **判别验证（双腿 stash）**：①stash 状态机修正→FAILED 源用例 FAIL（status==COMPLETED）；②stash 列回读→env 门控用例无法本地判别，改用单测级判别：search 回读逻辑抽 protected 方法或以 mock ResultSet 直测（实施时核 PgVectorStore 可测性——ResultSet mock 需 jdbc stub），判别=FAIL（content null）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 双修全落地
- [ ] 判别测试双腿绿 + 既有测试零回归
- [ ] nop-ai-agent/nop-ai-rag 测试全绿
- [ ] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: planned
Targets: `docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Fix`

- [ ] nop-ai.md：后台任务状态机注记（NOTIFICATION_FAILED 为簿记态，原始终态保留并可重投）+ pgvector search 回读契约
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] roadmap R1.32 翻 done；index P1-7.1-001/002 → fixed；daily log 条目

Exit Criteria:

- [ ] owner doc 条目在位且与 live 行为一致
- [ ] doc-links 检查退出码 0
- [ ] 收口动作落盘

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（P1-7.1-001/002）
- [ ] 行为/契约结果已达成（判别双腿 + 零回归）
- [ ] 必要 focused verification 已完成（nop-ai-agent/nop-ai-rag 测试全绿）
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-rag` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- pgvector 实库 CI 化（env 门控维持既有声明）
- AgentToolDispatcher 复杂度评估（复杂度巡检移交 R1.0）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-
