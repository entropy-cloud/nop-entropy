# 2585 运行时服务并发与协议正确性修复（wf/gateway/graphql/stream/datav/metadata）

> Plan Status: active
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（3 Major 先修已吸收：off-by-one 改语义核查、endSubFlow 首选同步移出锁外、end 脚本防双跑取舍）
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（运行时分区 Major/Minor 群）
> Related: `docs-for-ai/02-core-guides/service-layer.md`

## Purpose

消除工作流条带锁死锁面、WS idle 误杀、网关重试上界偏差等运行时正确性缺陷。

## Current Baseline

- `WorkflowEngineImpl.java:1798,1821,1265`：子流程终结持子 wf 条带锁内同步通知父流程取父锁；父侧迁移段持锁内 startSubFlow 反向取子锁——ABBA 死锁面。`:1817` 用户 end 脚本在锁内执行，违反 doInvokeAction 已确立的"脚本移出锁"不变式。
- `JsonRpcWebSocketHandler.java:583-590`：idle 断连仅以自定义 JSON-RPC pong 续期，标准 graphql-ws 客户端被误杀断连并 cancel 订阅；订阅闸/上界为类加载期定格（:111-118）。
- `BufferedStreamingPublisher.java:650`：重订阅上界 off-by-one（实际 maxAttempts+1 次）。
- `DaoWorkflowStore.java:290-296` + `WfTaskScanner.java:293,484`：due 扫描同毫秒 dueTime 超批时整批跳过；WfTaskScanner 无 `actionDeadlineMs ≤ scanLeaseMs` 装配校验；公共 SPI 参数校验用裸 IllegalArgumentException。
- `MetaMethodExecutor.java:83-98`：结果集无 maxRows/queryTimeout 防线。
- `NopDatavExportTaskBizModel.java:129`：per-user 信号量 map 无淘汰。
- `PollingJdbcMessageService.java:155-160`：配置解析裸 NumberFormatException。

## Goals

- wf 引擎锁序单一化（子→父单向）且用户脚本全部移出锁；WS 活性判定对标准客户端公平；扫描游标与租约防线补齐。

## Non-Goals

- 不重做 ws 协议栈；不引入分布式锁；不动 MetaMethod 沙箱模型。

## Scope

### In Scope

- nop-wf-core（WorkflowEngineImpl）、nop-wf-dao（DaoWorkflowStore）、nop-wf-scheduler（WfTaskScanner/WfSchedulerConfigs）
- nop-graphql-core（JsonRpcWebSocketHandler）、nop-gateway（BufferedStreamingPublisher）
- nop-metadata-service（MetaMethodExecutor）、nop-datav-service（NopDatavExportTaskBizModel）、nop-stream-runtime（PollingJdbcMessageService）

### Out Of Scope

- nop-stream-core 背压/CheckpointCoordinator 深审（follow-up 分析）；nop-rpc client（未深审区域）。

## Execution Plan

### Phase 1 - wf 锁序与脚本执行面（2 Major + 1 Major 面）

Status: completed
Targets: `nop-wf/nop-wf-core/.../engine/WorkflowEngineImpl.java`

- Item Types: `Fix`

- [x] endSubFlow 通知移出 doEndWorkflowLocked 锁段——裁定首选同步移出锁外（saveWfRecord 之后触发）；不采用异步变体（父侧 notify 先于子事务提交会竞态/丢失，无重放机制不可靠）
- [x] end 脚本 runXpl 移出锁外：按「锁内预留终态（先写 ended 状态/状态预留防双跑）→ 锁外执行脚本 → 锁内完成收尾」模式；并发双跑面有钉测试（若实现后确认无法预留则显式接受双跑并登记）
- [x] 登记全局锁序不变式（子→父单向）并在 startSubFlow 路径核对不持父锁

Exit Criteria:

- [x] 回归钉：父子流程并发终结/启动压测（或确定性交错测试）无死锁、无锁序违反断言
- [x] end 脚本慢执行不阻塞同 stripe 其他操作（钉）
- [x] `ib test -pl :nop-wf-core` 全绿
- [x] `ai-dev/logs/` 已更新

### Phase 2 - 协议与扫描防线（群修）

Status: in progress
Targets: `JsonRpcWebSocketHandler.java`、`BufferedStreamingPublisher.java`、`DaoWorkflowStore.java`、`WfTaskScanner.java`、`MetaMethodExecutor.java`、`NopDatavExportTaskBizModel.java`、`PollingJdbcMessageService.java`

- Item Types: `Fix`

- [x] WS idle：任意入站帧续期活性（pong 分支保留；字段 lastPongAt 同步更名为 lastInboundAt，idleMs 结构化日志口径跟随；连续 send 失败信号不改）
- [x] 订阅闸/上界改运行期读取或提供重建缝（GLOBAL_SUBSCRIPTION_GATE 为跨连接共享计数器，重建需处理存量许可迁移/排空，实现时给缝或维持 static 并登记）
- [x] 重订阅上界语义核查（审查裁定：`attemptCount >= maxAttempts + 1` 与配置键 `MAX_RESUBSCRIBE_ATTEMPTS` 语义自洽，审计口径有误）——不翻转比较符；补断言钉固化「重订阅恰好 maxAttempts 次」语义，修正审计底账登记
- [x] due 与 remind 扫描游标加 tie-breaker（二元组游标 (dueTime, stepId)，谓词形状 `((eq dueTime and gt stepId) or gt dueTime)`；DaoWorkflowStore 与 WfTaskScanner 的 due、remind 两个半区同修）
- [x] WfTaskScanner.init() 增加 deadline > lease 时 fail-fast/WARN；DaoWorkflowStore 参数校验改 NopException + 错误码
- [x] MetaMethodExecutor 设 setMaxRows + queryTimeout 二道防线
- [x] datav per-user 信号量：先复查代码内既有裁定⑨（NopDatavExportTaskBizModel.java:126-129 注释，plan 2526，论证条目受平台用户数有界）——引用或推翻该裁定后再决定是否加淘汰；PollingJdbcMessageService 配置解析 fail-fast（StreamException）

Exit Criteria:

- [x] 每项 focused 钉测试（同毫秒批不跳过、off-by-one 断言钉、maxRows 生效等）——已执行绿：`TestWfTaskScannerFencing.testSameMillisecondDueTime_tieBreakerCursorConsumesAll`、`TestWfScanQueryShape`、`testMaxRowsSecondLineOfDefense`（metadata 8/0）、`TestPollBatchSizeConfigFailFast`（stream-runtime 1246/0）、wf-core 57/0、dao+scheduler 9/0；gateway `pin3a` 为既有绿钉（本批 gateway 仅注释零语义 diff）未复跑，graphql-core 钉（pin_a~d）随该模块测试阻塞未复跑（见下条）
- [ ] `ib test -pl :nop-graphql-core,:nop-gateway,:nop-wf-dao,:nop-wf-scheduler,:nop-metadata-service,:nop-datav-service,:nop-stream-runtime` 全绿（或按受影响模块裁剪）——dao/scheduler/metadata/stream-runtime 已绿；graphql-core/gateway 被环境阻塞（nop-ib 闭包含 JDK22 门控 nop-cli-core depsUnresolved → Maven 回退 → nop-record-mapping precompile NoClassDefFound，均为并行代理 nop-ib/nop-orm 半成品竞态，非本 plan 改动面），待并行批收敛后复跑收口
- [ ] owner docs 若行为面变化已同步，否则显式 No owner-doc update required
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] wf ABBA 锁序面修复并有并发钉
- [ ] WS idle 误杀修复并有标准客户端行为钉
- [ ] Minor 群全部落地或移入 Deferred But Adjudicated
- [ ] focused tests 全绿
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-wf-core --severity high` 退出码 0

## Deferred But Adjudicated

### BoundedWindowBuffer fallback 腿许可记账（低置信发现）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计自评低置信，需先与设计文档对齐语义再决定是否为缺陷
- Successor Required: `yes`
- Successor Path: `ai-dev/analysis/`（先语义对齐分析，确认缺陷后回本计划或新计划）

## Non-Blocking Follow-ups

- nop-stream-core InputGate/CheckpointCoordinator 深审；nop-rpc client 深审

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
