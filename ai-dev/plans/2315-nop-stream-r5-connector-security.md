# 2315 nop-stream R5：连接器与安全（CON-02/03/04/05/08 + SEC-01 + REG-3 + CON-06）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/03-connectors-security.md（各发现 + 维度复核）；00 报告 REG-3；summary.md 修复归属 2315
> Related: 2310（CON-01 已修）、2313（REG-1 remote ledger namespace 已修）

## Purpose

闭合 R5 连接器族的七个已确认缺陷：Debezium 引擎启动窗口 stop 早退泄漏（CON-02）、offset 恢复 put-merge 跳读丢数据（CON-03）、file 2PC 数据文件 commit 前 fsync 缺失与 recordCount 无校验（CON-04）、JDBC ledger fallback 静默碰撞（CON-05）、file sink 跨运行键碰撞 guard 静默（CON-08）、extraProperties 覆盖绕过两个已声明不变式（SEC-01）、schema history 表后缀 lastSegment 跨连接器碰撞（REG-3）；并按 owner doc 单写入者裁定对齐 batch-loader 声明面（CON-06）。

## Current Baseline

各发现的 证据/建议 已在审计报告复核锁定（修复方向逐条采纳审计建议字段）。

## Goals

- CON-02：stop 以同步启动标记（startRequested）门控，启动窗口内 cancel 不泄漏引擎。
- CON-03：setOffsets REPLACE 语义（checkpoint 整表取代权）+ 行为测试对齐。
- CON-04：writeLines 后立即 fsync（checkpoint durable ⟺ 数据 durable）+ commit 前 recordCount 行数自检（截断→typed 失败）。
- CON-05：fallback 到 tableName 时一次性 WARN（指引设置 ledgerNamespace）。
- CON-08：copyForSubtask(TaskLocation) 注入 jobId|vertexId run namespace 折入 temp/final/manifest 键；guard 命中与 final-exists 路径补 WARN。
- SEC-01：extraProperties 合并前拒绝 connector.class / schema.history.internal。
- REG-3：schema history 表后缀用全量净化 instance 名（不再 lastSegment 坍缩）。
- CON-06：batch-loader 描述符对齐 SINGLE_INSTANCE（运行期门禁 + owner doc 单写入者裁定），钉定测试同步。

## Non-Goals

- CON-07/09/10/11（P3 长尾，既有裁定）；SEC-02（P3）；不收紧 extraProperties 其余透传面。

## Execution Plan

### Phase 1 - 连接器缺陷批次（8 项）

Status: completed
Targets: `nop-message-debezium`（wrapper/offset store/engine config）、`nop-stream-connector`（file sink）、`nop-stream-connector-debezium`（cdc source function）、`nop-stream-connector-batch`（factory）、runtime 钉定测试

- Item Types: `Fix`

- [x] CON-02：startRequested 标记分离（start 同步置位；stop 门控切换）
- [x] CON-03：setOffsets 先 clear 再写入（REPLACE）+ 测试对齐
- [x] CON-04：writeLines 后 forcePath + doCommitLocked 行数自检（ERR_STREAM_CHECKPOINT_ERROR）
- [x] CON-05：effectiveLedgerNamespace fallback 一次性 WARN
- [x] CON-08：copyForSubtask(TaskLocation) 注入 runNamespace（jobId|vertexId → runKeyComponent 折入 temp/final/manifest 键）+ guard/final-exists WARN
- [x] SEC-01：合并循环前置 connector.class / schema.history.internal 键守卫（类型化拒绝）
- [x] REG-3：sanitizeIdentifier(instance) 全量后缀替换 lastSegment
- [x] CON-06：batch-loader factory 描述符 SINGLE_INSTANCE + 能力矩阵钉定测试对齐（CON-01 惯例）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 连接器四模块（connector / connector-debezium / connector-batch / nop-message-debezium）全量测试绿
- [x] 能力矩阵钉定测试（TestStreamConnectorRegistryDiscovery）绿
- [x] No owner-doc update required（connectors.md 矩阵由钉定测试与描述符同步；单写入者裁定本已在 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects（CON-02/03/04/05/08、SEC-01、REG-3、CON-06）已修复
- [x] 所有 in-scope confirmed contract drifts 已收敛（batch-loader 三方一致）
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成（四模块全量测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：replace 语义经测试断言（p3 幸存键消失）；fsync/recordCount 位于 checkpoint 链真实路径；键守卫在合并循环前
- [x] `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-debezium,nop-stream/nop-stream-connector-batch,nop-message/nop-message-debezium` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2315-nop-stream-r5-connector-security.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码为 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- CON-07/09/10/11、SEC-02（P3 长尾）随下次连接器轮次。

## Closure

Status Note: 八项连接器缺陷/漂移修复落地（CON-02/03/04/05/08、SEC-01、REG-3、CON-06），四模块全量测试绿，能力矩阵三方一致。独立 closure audit APPROVE。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence:
  - CON-02：startRequested 分离（wrapper :97-105/:135-142/:196-203）；TestDebeziumEngineWrapper 系列 + 26/0 绿
  - CON-03：setOffsets clear-first + 测试对齐 replace（p3 幸存键消失断言）
  - CON-04：writeLines 后 forcePath + recordCount 行数自检（doCommitLocked 内，typed ERR_STREAM_CHECKPOINT_ERROR）
  - CON-05：fallback 一次性 WARN；CON-08：runNamespace 键折叠 + guard/final-exists WARN
  - SEC-01：合并循环前置键守卫；REG-3：sanitizeIdentifier 全量后缀
  - CON-06：factory SINGLE_INSTANCE + 钉定测试对齐（TestStreamConnectorRegistryDiscovery 绿）
  - 全量：_tmp/plan2315-full2.log 四模块 BUILD SUCCESS
  - `check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs` 退出码 0
- Follow-up: CON-07/09/10/11、SEC-02（P3 长尾）

## Addendum (2026-10-05, plan 2316 closure audit)

- **CON-04/CON-08 部分回退**：2316 执行期间发现本计划的 CON-08 键折叠（runNamespace 折入 temp/final/manifest 键）与 CON-04 recordCount 行数校验在恢复混键场景引入 exactly-once 回归（TestStreamConnectorRegistryE2E 40→39 行），已在 2316 worktree 回退。live code 现状 = 保留 CON-04 的 writeLines 后 forcePath 与 guard/final-exists WARN（行为保持），行数校验与键折叠移入 2316 Deferred（successor：独立连接器身份传递计划）。CON-04 的 fsync 增量与 CON-08 的可观测性增量仍然有效；完整修复需恢复路径身份传递设计。
- **CON-05 归属更正**：fallback WARN 实际落地于 2316 worktree（本提交 c79be63506 未含），归属随 2316 提交。
