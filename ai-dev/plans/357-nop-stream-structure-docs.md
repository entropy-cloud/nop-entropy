# 357 nop-stream 结构治理与审计基线回写

> Plan Status: completed
> Last Reviewed: 2026-09-13
> Source: 审计 04-nop-stream-findings ST-3（24 个超长方法）/ST-6（ops 面自建调度）/ST-7（file 连接器 IResource）/ST-8（巨型类）；06-uncovered-hotspots §下一轮动作 4（基线结论回写 docs-for-ai）
> Related: 350-356（已完成）

## Purpose

收口 nop-stream 剩余结构发现（ST-3/6/7/8）并完成审计 06 的 docs-for-ai 基线回写（防"每方法一 Processor"误读）。

## Current Baseline

- ST-3：24 个 >80 行方法（>120 行 9 个）。Top5：CheckpointPlanBuilder.build 173、RpcDistributedExecutor.startJob 172、EmbeddedDistributedExecutor.execute 157、WindowOperator.open 149、CheckpointSerDe.deserializeEpochManifest 148。裁量：数据面算子 open 容忍度高；plan builder/executor 编排/SerDe 应拆。
- ST-6：ops 面自建调度（OpsJobManager:316 治理 sweeper、StreamMetricsReporter:71 周期 sink、webhook 投递线程）；引擎底座（barrier/心跳/checkpoint 超时）属引擎内部边界可辩护。
- ST-7：FileSourceReader:163-168（new FileInputStream）、FileTwoPhaseCommitSink 直连 IO；LocalFile checkpoint/RocksDB 属本地文件契约豁免。
- ST-8：JobCoordinator 2534 行、WindowOperator 2331、GraphModelCheckpointExecutor 2008、InputGate 1153、StreamTaskInvokable 1113。
- 06-4：docs-for-ai/02-core-guides/domain-logic-and-ddd.md 未写明"参照模块无业务 Processor 层"（基线校准结论）。

## Goals

- ST-3 Top5 拆分（行为保持 extract-method，同 plan 355 方法论）：CheckpointPlanBuilder.build（按 per-vertex/per-subtask 收集器拆）、RpcDistributedExecutor.startJob 与 EmbeddedDistributedExecutor.execute（按 NodeBootstrap/ControlPlane/Assignment 相位拆）、CheckpointSerDe.deserializeEpochManifest（字段组解码拆）；WindowOperator.open 数据面裁定保留（登记）。
- ST-6：ops 面三处调度线程登记边界裁定（引擎自包含运行时，外置 nop-job 为 successor；owner doc 补一句）；引擎底座已可辩护不另立。
- ST-7：FileSourceReader/FileTwoPhaseCommitSink 包 IResource 读层（FileResource 优先、本地路径回退——参照 StreamConfValidateCommand:115 先例）。
- ST-8：按 CheckpointCoordinator/JobHealthStateMachine 既有方向抽离 1 个最高价值切面：JobCoordinator 的 assignment 相关私有方法组 → AssignmentPlanner（行为保持）；其余登记 Deferred（巨型类治理为持续演进）。
- 06-4：domain-logic-and-ddd.md 补"参照系实证"小节（73 BizModel 对 0 业务 Processor；编排三形态；防误读）。

## Non-Goals

- 不 TaskStep 化、不改对外契约、不动 fraud-example/quickstart。

## Scope

### In Scope / Out Of Scope

- In：nop-stream-runtime/core 的 5+1 方法拆分、2 文件 IResource 化、AssignmentPlanner 抽离、2 处 docs 更新。
- Out：其余 19 个 >80 行方法（登记清单）、WindowOperator/巨型类其余部分、src/test。

## Execution Plan

### Phase 1 - ST-3 Top5 拆分（行为保持）

Status: completed
Targets: CheckpointPlanBuilder/RpcDistributedExecutor/EmbeddedDistributedExecutor/CheckpointSerDe

- [x] 四方法 extract-method 拆分（每方法 ≤150 行；契约注释随迁）；WindowOperator.open 裁定保留（数据面 + 登记）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（1059 基线）

### Phase 2 - ST-7 文件连接器 IResource 化

Status: completed
Targets: FileSourceReader/FileTwoPhaseCommitSink

- [x] 读层经 FileResource/IResource 抽象（本地路径构造 FileResource——VFS 语义、保留本地契约）
- [x] `./mvnw test -pl nop-stream/nop-stream-connector -am`

### Phase 3 - ST-8 AssignmentPlanner 抽离

Status: completed
Targets: JobCoordinator

- [x] assignment 私有方法组抽离为协作类（纯移动）；其余巨型类登记 Deferred
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am`

### Phase 4 - ST-6 裁定 + 06-4 基线回写

Status: completed
Targets: owner doc + domain-logic-and-ddd.md + 代码注释

- [x] ST-6 三处 ops 调度线程裁定注释 + owner doc 一句（nop-stream.md 运维节）
- [x] domain-logic-and-ddd.md 补"参照系实证"小节（防误读）
- [x] check-doc-links --strict

### Phase 5 - 全量验证与收口

Status: completed

- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector -am` 全绿；复扫方法行数；closure audit + checklist

## Closure Gates

- [x] ST-3 Top4 拆分 + WindowOperator 裁定；ST-7 两文件 IResource；ST-8 抽离 1 切面 + Deferred；ST-6 裁定；06-4 回写
- [x] 行为零变化（全量测试绿）
- [x] 独立 closure audit 证据写入

## Deferred But Adjudicated

### 其余 19 个 >80 行方法 + 巨型类其余切面

- Classification: `optimization candidate`
- Why Not Blocking Closure: 审计核心为防单方法全流程与最大风险切面（已处置）；长尾为持续演进。
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 全部完成。Phase 1-3 委托执行并已提交（dde61278/448626f/8e33ab1——AssignmentPlanner 抽离 + CheckpointPlanBuilder/SerDe/两 Executor 拆分 + FileConnector FileResource 化）；Phase 4 裁定注释与基线回写；Phase 5 收口。
Completed: 2026-09-13
Closure Audit Evidence:
- Reviewer / Agent: agent_1cd8acd7（独立 closure audit，7/7 PASS）
- Evidence:
  - ST-3：CheckpointPlanBuilder.build/RpcDistributedExecutor.startJob/EmbeddedDistributedExecutor.execute/CheckpointSerDe.deserializeEpochManifest 拆分（提交 448626f/dde61278）；WindowOperator.open 数据面裁定保留
  - ST-7：FileSourceReader/FileTwoPhaseCommitSink 经 FileResource 抽象（提交 8e33ab1）
  - ST-8：AssignmentPlanner 抽离（dde61278）；其余巨型类 Deferred
  - ST-6：OpsJobManager sweeper/StreamMetricsReporter/WebhookAlertChannel 三处裁定注释 + owner doc 运维节裁定段
  - 06-4：domain-logic-and-ddd.md 补"参照系实证"小节（73 BizModel 对 0 业务 Processor，防误读）
  - 测试：core 1592/runtime 1060/connector 69 全绿（FINAL=0）；check-doc-links --strict 0 errors
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/357-nop-stream-structure-docs.md --strict` 退出码 0
Follow-up:
- no remaining plan-owned work
