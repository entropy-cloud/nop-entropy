# 2316 nop-stream R5：fail-fast 守卫族（FLOW-2/4 + CEP-1/2/7 + WIN-2/4 Phase 1）

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: ai-dev/audits/2026-10/2026-10-05-0523-deep-audit-nop-stream-quality-r5/01-cep-flow-correctness.md（FLOW/CEP + 深挖）、05-keyed-routing-window.md（WIN 族 + 子项复核两阶段建议）；summary.md 修复归属 2316
> Related: 2313（FLOW-5 已修）、2312（CEP-1 相邻面）

## Purpose

把 R5 审计确认的部署期静默缺陷改为响亮失败：断连子图（FLOW-2）、三空时间源（FLOW-4）、CEP×RocksDB 不可用组合（CEP-1）、无 watermark 供给的 CEP（CEP-2 WARN 起步）、PT timer 恢复不重挂（CEP-7）、窗口/CEP per-key 辅助状态 rescale 静默分裂（WIN-2/4 Phase 1 fail-fast）。

## Current Baseline

修复方向逐条采纳审计建议字段（FLOW-2 反向可达性、FLOW-4 三空 typed、CEP-1 ①②、CEP-2 ① WARN 起步、CEP-7 ① per-key 首时间戳重挂、WIN-2/4 Phase 1 按状态形状 fail-fast）。

## Goals

见 Purpose——每项从静默错误转为部署期/运行期响亮失败或自愈。

## Non-Goals

- WIN-2/4 Phase 2（per-key-group operator state 重分布）——结构改造，独立后续计划。
- CEP-2 的 typed 拒绝终态与 timeBehaviour/lateDataSideOutputTag DSL 属性（先 WARN 起步）。
- CEP-3（PT timer 去重/取消，P2 既有长尾）。

## Scope

### In Scope

- flow：validateDag 反向可达性（FLOW-2）、buildTimestampsAndWatermarks 三空守卫（FLOW-4）、buildCep watermark 供给 WARN（CEP-2①）。
- cep：resolveKeyedBackend RocksDB typed 守卫（CEP-1①）、COMMON_HINT 文案（CEP-1②）、open() PT ledger 重挂（CEP-7①）。
- runtime：RescaleStateAssembler scale-down per-key 辅助状态 fail-fast（WIN-2/4 Phase 1）。

### Out Of Scope

- WIN Phase 2 重分布、CEP-2 终态 typed、CEP-3。

## Execution Plan

### Phase 1 - 守卫批次

Status: completed
Targets: 上列三模块六处

- Item Types: `Fix`

- [x] FLOW-2：validateSinkReachability 反向 BFS，不可达 transforms typed 列出；门控：仅模型声明 ≥1 sink 时检查（无 sink 的部分接线模型为契约测试合法形态）
- [x] FLOW-4：三空 typed ERR_STREAM_REQUIRED_BODY 锚定节点 id
- [x] CEP-1①：resolveKeyedBackend 对 RocksDB 后端 typed 拒绝；②COMMON_HINT 文案更正
- [x] CEP-2①：buildCep 无 timestampsAndWatermarks 节点时 WARN（typed 终态随 DSL 属性轮次）
- [x] CEP-7①：open() PT 模式按恢复 ledger 每 key 重挂首时间戳墙钟（fire 回调全量排水）
- [x] WIN-2/4 Phase 1：scale-down 遇 per-key 辅助 operator state（internal-timers/cep-event-time-timers/trigger-accumulators/pane-tracking/reduce-state）typed 拒绝
- [x] 回归钉子：TestKeyedStageParallelismGuard 族已有；flow 三模块全量回归 + E2E

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] flow/cep/runtime 三模块全量绿（_tmp/plan2316-full3.log BUILD SUCCESS）
- [x] FLOW-2 门控修正记录（初版误伤无 sink 的合法部分接线模型，加 has-sink 门控后全绿）
- [x] No owner-doc update required 或已同步（CEP×RocksDB 不支持已写入 COMMON_HINT 与 module doc 既有边界注记一致）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复或转为响亮失败
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：守卫位于真实构建/打开/恢复链；无空方法体
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-cep,nop-stream/nop-stream-runtime` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2316-nop-stream-r5-failfast-guards.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` 退出码为 0

## Deferred But Adjudicated

### WIN-2/4 Phase 2：per-key-group operator state 重分布

- Classification: `out-of-scope improvement`（结构改造）
- Why Not Blocking Closure: Phase 1 fail-fast 已把静默丢输出转为响亮拒绝；重分布属独立结构计划
- Successor Required: `yes`
- Successor Path: 独立 WIN Phase 2 计划（GraphExecutionPlan 入边策略保留 + assembler redistributor）

### CON-08 完整键命名空间与 CON-04 recordCount 校验

- Classification: `watch-only residual`
- Why Not Blocking Closure: 2315 落地的键折叠与行数校验在恢复混键场景引入 exactly-once 回归（E2E 丢末行），已回退仅保留 WARN 与 fsync 前移；完整方案需恢复路径身份传递设计
- Successor Required: `yes`
- Successor Path: 独立连接器身份传递计划

### CEP-3 PT timer 去重/取消

- Classification: `watch-only residual`
- Why Not Blocking Closure: P2 既有长尾，与 CEP-7 正交
- Successor Required: `no`

## Closure

Status Note: 六项守卫落地（FLOW-2/4、CEP-1①②、CEP-2①、CEP-7①、WIN Phase 1），三模块全量绿。执行偏差：FLOW-2 加 has-sink 门控；CON-08/CON-04 增量在 2315 引入 exactly-once 回归后回退并移入 Deferred（诚实披露）。独立 closure audit APPROVE（含上述 Deferred 审阅）。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure auditor（general-purpose 子代理，2026-10-05）
- Evidence:
  - FLOW-2/4/CEP-1/2/7/WIN：_tmp/plan2316-full3.log 三模块 BUILD SUCCESS；FLOW-2 门控修正后 TestStreamConfValidator/TestStreamCheckpointAndWindowContract 等 165 flow tests 全绿
  - 回归记录：CON-08/04 增量在 TestStreamConnectorRegistryE2E 丢末行（40→39），回退后 E2E 绿——判定为恢复混键 exactly-once 回归，记入 Deferred
  - `check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs` 退出码 0
- Follow-up: WIN Phase 2、CON-08 身份传递、CEP-2 终态（见上）
