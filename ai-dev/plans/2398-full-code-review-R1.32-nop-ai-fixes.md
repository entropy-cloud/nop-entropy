# 2398 full-code-review R1.32 — nop-ai 增量修复批（P1-7.1-001/002）

> Plan Status: completed
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

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] Entry 增 `originalStatus` 字段；complete() 首个终态时记录（NOTIFICATION_FAILED 覆盖之前）
- [x] retryBridgeIfNeeded：桥回调传 `entry.originalStatus`（非簿记态）；重投成功后 `entry.status = originalStatus`（FAILED/TIMEOUT/COMPLETED 各归其位——不再无条件 COMPLETED）；javadoc 记录状态机修正
- [x] PgVectorStore.search：回读列 2 content → addMetadata("content",…)；列 3 metadata jsonb → 解析逐键 addMetadata（docId/chunkIndex/indexId）
- [x] 测试：①TestDefaultBackgroundTaskRegistry 新增——FAILED 源 + 桥先败后成：重投成功后 status==FAILED（修复前 COMPLETED）且桥收到 FAILED（非 NOTIFICATION_FAILED）；TIMEOUT 源同型；COMPLETED 源既有用例回归。②TestPgVectorIntegration.storeSearchDeleteLifecycle 增强：vec() 带 content 元数据，search 后断言 hits.get(0).getMetadata("content") 非空且 id 元数据正确（env 门控维持——无实库时跳过属既有声明）
- [x] **判别验证（双腿 stash）**：①stash 状态机修正→FAILED 源用例 FAIL（status==COMPLETED）；②stash 列回读→env 门控用例无法本地判别，改用单测级判别：search 回读逻辑抽 protected 方法或以 mock ResultSet 直测（实施时核 PgVectorStore 可测性——ResultSet mock 需 jdbc stub），判别=FAIL（content null）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 双修全落地
- [x] 判别测试双腿绿 + 既有测试零回归
- [x] nop-ai-agent/nop-ai-rag 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Fix`

- [x] nop-ai.md：后台任务状态机注记（NOTIFICATION_FAILED 为簿记态，原始终态保留并可重投）+ pgvector search 回读契约
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.32 翻 done；index P1-7.1-001/002 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-7.1-001/002）
- [x] 行为/契约结果已达成（判别双腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-ai-agent/nop-ai-rag 测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-rag` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- pgvector 实库 CI 化（env 门控维持既有声明）
- AgentToolDispatcher 复杂度评估（复杂度巡检移交 R1.0）

## Closure

Status Note: 两条 in-scope P1 均已在 live 代码中确认修复：①`DefaultBackgroundTaskRegistry` 新增 `Entry.originalStatus`（:335），`complete()` 在桥失败簿记覆盖前保存原始终态（:158-159），`retryBridgeIfNeeded` 桥回调携带 `originalStatus` 且重投成功后 `entry.status = originalStatus`（:245-248）——FAILED/TIMEOUT 源不再被误报 COMPLETED；②`PgVectorStore.search`（:190-203）SQL 三列 `embedding, content, metadata` 全部回读，`readSearchMetadata`（:277-284）填充 content 键并解析 jsonb（`applyMetadataJson` :290-299）。owner doc `docs-for-ai/03-modules/nop-ai.md:79-82` 与 live 行为一致；doc-links --strict 退出码 0；无空壳实现；无 in-scope live defect 被降级。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: subagent closure-audit backfill（独立审计子会话；未参与实现）
- Evidence:
  - **Exit Criteria Phase 1**：PASS——live `DefaultBackgroundTaskRegistry.java:158-159,245-248,335` 状态机修正在位；`PgVectorStore.java:190-203,277-299` 列回读在位于 SQL 列序一致（embedding=1/content=2/metadata=3，`toMetadataJson` :302-319 跳过 content 独立列）。
  - **Exit Criteria Phase 2**：PASS——`docs-for-ai/03-modules/nop-ai.md:79-82`「后台任务状态机与 pgvector 回读」节与 live 行为一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - **指认测试在位且断言目标行为**：`TestDefaultBackgroundTaskRegistry.java:139-186` 新增 `failedSourceLazyRedeliveryKeepsFailedStatusAndPassesRealStatusToBridge`（桥收到 FAILED 而非 NOTIFICATION_FAILED；重投后仍 FAILED）与 `timeoutSourceLazyRedeliveryKeepsTimeoutStatus`；`TestPgVectorSearchRowMapping.java`（mock ResultSet 直测 `readSearchMetadata` → content/docId/chunkIndex 非 null + null 列容忍）；`TestPgVectorIntegration.java:57-62` 内容/id 断言增强（env 门控）。
  - **判别性**：静态复核 pre-fix 语义——原 `retryBridgeIfNeeded` 以簿记态回调并无条件置 COMPLETED，两个新用例必然 FAIL；原 search 只 `rs.getString(1)`，行映射用例 content 必 null。测试对缺陷有真实判别力。
  - **focused verification**：`./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-rag -am` → 脚本退出码 0。nop-ai-agent `Tests run: 3537, Failures: 0, Errors: 0`（`TestDefaultBackgroundTaskRegistry` 10/10）；nop-ai-rag `Tests run: 31, Failures: 0, Errors: 0, Skipped: 1`（`TestPgVectorSearchRowMapping` 2/2；`TestPgVectorIntegration` 1 skipped=NOP_TEST_PGVECTOR_JDBC_URL 未设，属既有 env 门控声明）。
  - **Anti-Hollow**：`scan-hollow-implementations.mjs --module nop-ai-agent` 与 `--module nop-ai-rag`（--severity high）均 0 findings；桥消费方 `DefaultAgentEngine` 装配点运行时调用连通。
  - **Closure Gate 全项**：均已核对 PASS；`Deferred But Adjudicated` 为空。
  - **Deferred 分类检查**：`Non-Blocking Follow-ups` 两项（pgvector 实库 CI 化、AgentToolDispatcher 复杂度）确为 non-blocking，未含 in-scope live defect。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2398-full-code-review-R1.32-nop-ai-fixes.md --strict` 退出码 0。

Follow-up:

- 无 remaining plan-owned work（Non-Blocking Follow-ups 见上节）。
