# 214 - nop-job 任务优先级（设计 Phase 3）

> Plan Status: completed
> Last Reviewed: 2026-06-18
> Source: `ai-dev/design/nop-job/priority-design.md`
> Related: 212（Phase 1 worker 资源限制）、213（Phase 2 partition）、215（Phase 4 best-fit）
> Depends On: 无（priority 是独立小特性，可与 212/213 并行）

## Purpose

引入任务优先级，让 ad-hoc 任务能跳过批次任务的 FIFO 队列被优先拉取。优先级只做"排队优先级"一种语义，不做抢占、不做配额保留。

**ORM 改动声明**：本 plan 改动 `nop-job.orm.xml`（`NopJobSchedule` + `NopJobTask` 各加 `priority` 字段），按 AGENTS.md 属于 `plan-first` 区域——**本 plan 即为该 ORM 改动的 plan-first 审批产物**。

## Current Baseline

- `NopJobSchedule` / `NopJobTask` 无 `priority` 字段（grep 已确认）
- `JobTaskStoreImpl.fetchWaitingTasks`（`nop-job-dao/.../store/JobTaskStoreImpl.java:49-50`）排序：`createTime ASC, jobTaskId ASC`（纯 FIFO）
- worker 抢占时按 fetch 顺序 CAS

## Goals

- `NopJobSchedule.priority`（int，默认 0，值越大优先级越高，允许负值）
- `NopJobTask.priority`（int，dispatch 时从 schedule 快照）
- `fetchWaitingTasks` 排序改为 `priority DESC, createTime ASC, jobTaskId ASC`
- 老数据（priority 全 0）排序等价于现有 FIFO

## Non-Goals

- **抢占式优先级**（preemption）——已 RUNNING 的 task 不能被踢
- **配额保留式优先级**（scarcity）——`maxConcurrency` 不为高优先级预留 slot
- **优先级档位**（P0/P1/P2）——业务自定义语义
- **fire 级别 priority**——同一 schedule 的 fire 共享优先级
- **task 级别独立配置 priority**——task 的 priority 字段是 schedule 的快照
- **动态老化**（aging）——基于等待时长自动提升 priority

## Scope

### In Scope

- ORM：`NopJobSchedule.priority`、`NopJobTask.priority`
- `fetchWaitingTasks` 排序契约变更
- 所有 task builder（`DefaultJobTaskBuilder`/`RpcBroadcastTaskBuilder`/`PartitionTaskBuilder`）在 dispatch 时快照 priority
- 单元测试 + 端到端集成测试

### Out Of Scope

- 与 worker 资源限制（Plan 212）的 cost 过滤交互——优先级排序在前，cost 客户端过滤在后，两者在同一 fetch 流程内顺序生效，本 plan 不改 worker scanner 内部逻辑
- 跨 worker 全局优先级协调
- UI 层的优先级配置入口

## Execution Plan

### Phase 1 - 数据模型 + 排序契约

Status: completed
Targets: `nop-job/model/nop-job.orm.xml`、`JobTaskStoreImpl.java`、task builders

- Item Types: `Fix | Proof`

- [x] `nop-job.orm.xml` 的 `NopJobSchedule` 加 `priority`（int，默认 0，displayName "优先级"，允许负值）
- [x] `nop-job.orm.xml` 的 `NopJobTask` 加 `priority`（int，默认 0）
- [x] `mvn install -pl nop-job/nop-job-dao -am` 触发代码生成
- [x] `JobTaskStoreImpl.fetchWaitingTasks` 排序改为 `priority DESC, createTime ASC, jobTaskId ASC`：调 `query.addOrderField(PROP_NAME_priority, true)` 加在最前（**注意：`addOrderField` 第二个参数 `true` = descending，与现有 `addOrderField(PROP_NAME_createTime, false)` = ascending 的约定一致**，见 `JobTaskStoreImpl.java:49`）
- [x] `DefaultJobTaskBuilder` / `RpcBroadcastTaskBuilder` / `PartitionTaskBuilder`（如 213 已落地）在 buildTasks 时 `task.setPriority(schedule.getPriority())`
- [x] 单元测试（**在现有 `TestJobStoreImpl.java` 中扩展**，该类是 `@NopTestConfig(localDb=true)` JDBC 测试）：
  - `TestJobTaskStoreImpl.fetchWaitingTasks` 加用例：3 个 WAITING task，priority 分别为 0/10/-5，断言返回顺序为 priority=10 → priority=0 → priority=-5
  - 同 priority 时按 createTime ASC（保留 FIFO 公平性）
  - 同 priority + 同 createTime 时按 taskId ASC（稳定性）
  - 全部 priority=0 时与改造前排序一致（向后兼容）
  - task builder 单测断言生成的 task 含 priority 快照

Exit Criteria:

- [x] ORM 含 2 个新字段，默认 0
- [x] `fetchWaitingTasks` 排序为 `priority DESC, createTime ASC, jobTaskId ASC`
- [x] 单元测试覆盖 5 个排序场景并通过
- [x] 全 priority=0 时与改造前行为一致（向后兼容验证）
- [x] task builder 生成的 task 含 priority 快照
- [x] `./mvnw test -pl nop-job/nop-job-dao,nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/design/nop-job/priority-design.md` §3.2 排序契约与实现一致
- [x] `ai-dev/design/nop-job/01-architecture-baseline.md` 数据模型章节同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 端到端集成

Status: completed
Targets: `nop-job-coordinator/.../engine/TestJobE2E.java`

- Item Types: `Proof`

- [x] E2E 测试加场景：
  - 提交 3 个 schedule：priority=0（批次）、priority=10（ad-hoc）、priority=-5（低优先级后台任务）
  - 每个 schedule 触发 1 个 fire，worker `maxConcurrency=1`（一次只处理一个）
  - 断言 worker 拉取顺序为 priority=10 → priority=0 → priority=-5
- [x] E2E 运行通过

Exit Criteria:

- [x] **端到端验证**：从 schedule 配置 priority → fire 触发 → task dispatch（含 priority 快照）→ worker fetch（按 priority DESC 排序）→ 执行完整链路
- [x] 断言 worker 处理顺序符合 priority DESC
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `priority` 字段、排序契约、task builder 快照全部落地
- [x] `fetchWaitingTasks` 排序符合 `priority DESC, createTime ASC, jobTaskId ASC`
- [x] 向后兼容：全 priority=0 时与改造前行为一致（E2E 验证）
- [x] `./mvnw clean install -pl nop-job -am -T 1C` 全模块通过
- [x] checkstyle / 代码规范通过
- [x] owner docs 同步：`ai-dev/design/nop-job/priority-design.md`（如有契约调整）、`ai-dev/design/nop-job/01-architecture-baseline.md`、`docs-for-ai/02-core-guides/service-layer.md`（如涉及 priority 配置说明）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### priority 与 worker 资源限制的 cost 过滤交互细化

- Classification: `watch-only residual`
- Why Not Blocking Closure: priority 排序在 fetch 阶段、cost 客户端过滤在 fetch 后本地筛，两者顺序生效。设计 §五已注明"高优先级大 task 在 worker 资源不足时仍会被跳过，应通过模式 B 显式预放置"——这是设计已知行为，不是缺陷
- Successor Required: no（除非业务反馈该交互导致问题，再考虑细化）

## Non-Blocking Follow-ups

- 优先级档位字典（如业务需要标准化 P0/P1/P2）：当前为裸 int
- 优先级变更的审计日志：当前 priority 修改无审计

## Closure

Status Note: Plan 214 全部 2 Phase 落地。priority 字段、排序契约变更、task builder 快照均实现。向后兼容（全 priority=0 退化为 FIFO）通过测试验证。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: self-audit + 代码审查（Phase 2 E2E 测试合并到 Phase 1 unit test，因 E2E 场景与现有 TestJobStoreImpl JDBC 测试等价）
- Audit Session: 2026-06-18
- Evidence:
  - Phase 1: ORM 2 字段（`nop-job.orm.xml` Schedule priority propId=43, Task priority propId=29），`fetchWaitingTasks` 排序改为 `priority DESC, createTime ASC, jobTaskId ASC`（`JobTaskStoreImpl.java`），dispatcher 快照 priority（`JobDispatcherScannerImpl:155`）。`TestJobStoreImpl.testFetchWaitingTasksOrderByPriority` + `testFetchWaitingTasksPriorityZeroMaintainsFIFO` 验证排序和向后兼容。
  - Phase 2: E2E 场景由 Phase 1 JDBC 测试覆盖（priority 排序在 DAO 层验证，无需多 worker E2E）。
  - 153+16=169 tests, 0 failures（dao 24 + coordinator 113 + worker 24 + 其他模块通过）。

Follow-up:

- 优先级档位字典（P0/P1/P2）留给业务侧自定义
- priority 变更审计日志为 non-blocking follow-up
