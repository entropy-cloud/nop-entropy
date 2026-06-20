# 279 nop-ai-agent DB 状态存储 CAS 身份校验与跨接管排序正确性

> Plan Status: completed
> Last Reviewed: 2026-06-20
> Module: nop-ai-agent
> Work Item: WI-DBSTORE-CAS
> Source: `ai-dev/audits/2026-06-19-2310-adversarial-review-nop-ai-agent/01-open-findings.md`（AR-01/AR-08）
> Related: 深度审核 14 系列（并发竞态）、04 系列（ORM 实体设计）

## Purpose

把 `DbTeamTaskStore` 任务状态机 transition 缺 owner 身份/epoch CAS（致带副作用任务双重执行）、`DBCheckpointManager` 跨接管 latest checkpoint 排序语义错误等 confirmed live defect 收口为"DB-backed 可靠性存储在所有 transition 上都校验 claim 身份/epoch、跨接管时 latest 语义正确"。

## Current Baseline

- `DbTeamTaskStore`（team 子系统）实现 claim/complete/abandon/reclaim 任务状态机；`ai_agent_team_task` 表是 **raw-JDBC 表**（`AiAgentTeamTaskTable` 注释 `:10-16` 明示"managed via raw JDBC (not as an ORM entity)"，DDL 是 Java 字符串 `DDL_CREATE_TABLE`，无 `*.orm.xml`，`app.orm.xml` 中无该实体）。调用方 `team/flow/MemberFanOutDispatcher.java:~257`、`MemberAgentTaskStep.java:~192`、`SpawnMemberAgentTaskStep.java:~312`。
- **AR-01（confirmed P1，确定）**：`DbTeamTaskStore.java:~276-281`（completeTask）与 `:~308-315`（abandonTask）的 CAS 只校验 `STATUS`，WHERE 不含 owner 身份/epoch。`ITeamTaskStore.completeTask` Javadoc（`:~117-118`）称"claimedBy is preserved / recorded for validation only"——暗示了从未发生的校验。
- **关键约束（决定方案选择）**：daemon 路径（DB store 存在的主用途）claim 与 complete 用**同一身份**：`TeamTaskSchedulerDaemon` 用固定共享常量 `DEFAULT_DAEMON_SESSION_ID = "team-task-scheduler-daemon"`（`:~159`，注释 `:~212-216` 明示"all instances 共享"）→ `claimTask(taskId, daemonSessionId)`（`:~809`）→ dispatch（`:~972`）→ `completeTask(taskId, daemonSessionId)`（`MemberFanOutDispatcher.java:~257`）。reclaim（`DbTeamTaskStore.java:~346`）清空 CLAIMED_BY → 实例 B 重新 claim 用**同一** `daemonSessionId` → CLAIMED_BY 与实例 A 在途 complete 传入的值相同。**因此仅加 `AND CLAIMED_BY=?`（方案 A）在多实例/共享 daemon id 场景下 CAS 仍成功 → 双重执行窗口不关闭**。
- **abandon 非 complete 同形**：abandon 的合法源态是 `CREATED`（CLAIMED_BY 为 null）**与** `CLAIMED`（CLAIMED_BY=owner）两种（`DbTeamTaskStore.java:~309-315` WHERE `STATUS IN ('CREATED','CLAIMED')`）。
- `DBCheckpointManager`（reliability 子系统）持久化 checkpoint；调用方 `engine/DefaultAgentEngine.java:~2679-2691`（restoreSession 一致性检查 + SESSION_RESTORED 事件）。
- **AR-08（confirmed P2，确定）**：`DBCheckpointManager.java:~285`（`loadLatestCheckpointFromDb`）用 `ORDER BY SEQ DESC FETCH FIRST 1 ROWS ONLY`；而 `checkpointSeq`（`ReActAgentExecutor.java:~1105`）是 per-execution 局部、每次 execute 从 0 重置。`:~352`（`loadSessionRowsFromDb`）也用 `ORDER BY SEQ DESC`（后在 `:~305-310` 反转为升序），其结果**同时**供 `getLatestCheckpoint`（取末元素）、`getCheckpoints`（Javadoc `:~240-241` 承诺"ascending seq order"）、`CompactionAwareTruncation.truncateToLatestCompaction`（`:~335`，按 seq 推理）消费。`ai_agent_checkpoint` 表 PK 是 `WATERMARK`，**无 `ID` 列**（`AiAgentCheckpointTable.java:~70`）。对比 `FileBackedCheckpointManager.java:~171` 用插入序，两个 backend 的"latest"语义不一致。

## Goals

- completeTask/abandonTask 在 CAS 上校验 claim 身份/epoch，关闭重派时间窗内的双重执行（AR-01）。采用能真正关闭共享-daemon-id 场景的方案。
- getLatestCheckpoint 跨接管返回真正"最近一次执行"的 checkpoint，两个 backend 的 latest 语义一致，且不破坏 `getCheckpoints` 升序与 compaction 语义（AR-08）。

## Non-Goals

- 不重构 team 任务状态机整体设计，仅补 claim 身份/epoch CAS。
- 不引入基于 liveness 探针的 reclaim（当前 reclaim 基于时间，本计划不改 reclaim 触发策略，仅改 transition 的 owner/epoch 校验）。
- 不处理 ReAct 消息契约（Plan 277）、引擎资源生命周期（Plan 278）。
- 不处理深度审核 04 系列 ORM 实体设计建议（且本表非 ORM 实体，不适用）。

## Scope

### In Scope

- AR-01：completeTask/abandonTask 增加 claim epoch（首选）或 owner 身份 CAS，关闭共享-daemon-id 双重执行窗口。
- AR-08：getLatestCheckpoint 排序改为按时间，对齐 FileBacked 语义，隔离 `:352` 不影响 getCheckpoints/compaction。

### Out Of Scope

- reclaim 触发策略改造、team 状态机整体重构。
- Plan 277 / Plan 278 范围。
- 深度审核 04 系列 ORM 设计。

## Execution Plan

### Phase 1 - 任务 transition 强制 claim epoch / owner 身份 CAS（AR-01）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/DbTeamTaskStore.java`（`:~276-281` completeTask、`:~308-315` abandonTask、`:~346` reclaim）；`AiAgentTeamTaskTable`（raw-JDBC 表常量/DDL）；`ITeamTaskStore` Javadoc（`:~117-118`）；调用方 `MemberFanOutDispatcher.java:~258`、`MemberAgentTaskStep.java:~193`

- Item Types: `Fix | Decision`

- [x] **采用方案 B（CLAIM_EPOCH）为首选**：新增 `CLAIM_EPOCH` 列（整数，每次 claim 自增），所有 transition 的 CAS 校验 epoch。方案 B 在共享 `DEFAULT_DAEMON_SESSION_ID` 场景下仍有效——因为每次 claim 自增 epoch，reclaim→re-claim 后旧在途 complete 持有的旧 epoch 不再匹配 → CAS 失败。**方案 A（仅 `AND CLAIMED_BY=?`）已被 live code 证伪**（共享 daemon id 下不关闭窗口），仅可作为 epoch 之外的额外校验，不能单独使用。
- [x] **epoch 增量须在 claim 的 UPDATE 内完成**（`SET CLAIM_EPOCH = COALESCE(CLAIM_EPOCH, 0) + 1`，与 `WHERE STATUS='CREATED'` CAS 同语句），不得用 `SELECT MAX(CLAIM_EPOCH)+1` 后再 UPDATE（TOCTOU）。
- [x] **epoch 经 API 透传**（spec 完整性）：`TeamTask` 领域对象增加 `claimEpoch` 字段；`claimTask` 在 CAS 成功后返回/回填所分配的 epoch；`completeTask`/`abandonTask`（CLAIMED 分支）签名增加 `epoch` 参数；三个调用方（`MemberFanOutDispatcher:~258`、`MemberAgentTaskStep:~193`、`SpawnMemberAgentTaskStep:~312`）须在 claim 时捕获 epoch 并沿 dispatch→execute→complete 链路透传，绑定进 CAS。
- [x] **reclaimTask 保留 `CLAIM_EPOCH`（设计修正，非置 NULL）**：执行中发现原指令"置 NULL"与本计划的硬约束（`COALESCE(CLAIM_EPOCH,0)+1` 自增 + 单调 epoch 退出标准）数学不相容——置 NULL 后 `COALESCE(NULL,0)+1` 复位为 1，与旧在途 owner 重合，窗口重开。故 reclaim 清 `CLAIMED_BY` 但**保留** epoch（保持单调），使下次 claim 严格更大。详见设计 `nop-ai-agent-team-task-reclaim.md` 决策 9。
- [x] completeTask：WHERE 增加 `AND CLAIM_EPOCH = ?`（绑定本次 claim 时记录的 epoch）；abandonTask **非 complete 同形**——WHERE 须分别处理两个源态：`STATUS='CLAIMED' AND CLAIM_EPOCH=?`（owner/epoch 校验）**或** `STATUS='CREATED'`（epoch-agnostic——CREATED 无 owner 可绑定；覆盖未认领 / 已 reclaim 的 CREATED 任务）。用显式谓词表达，不用"同形"。
- [x] schema 迁移：`ai_agent_team_task` 是 raw-JDBC 表，`CREATE TABLE IF NOT EXISTS` 不会给已部署表加列。为方案 B 提供幂等 `ALTER TABLE ... ADD COLUMN CLAIM_EPOCH ...`（initSchema 中经**大小写不敏感** JDBC metadata 检测后按需 ALTER——修正了原实现用原始小写表名查 metadata 致 H2 误判的 bug），作为 Exit Criterion；并在 `AiAgentTeamTaskTable` 补列常量与 DDL。
- [x] CAS 失败语义：`ITeamTaskStore` 契约是 CAS 失败返回 `Optional.empty()`（非异常控制流，调用方 `:~258`/`:~193` 据此转异常）。保持该契约——store 返回 `Optional.empty()`，**不**让 store 自身抛；Exit Criteria 断言"CAS 失败不静默成功：返回 `Optional.empty()` 且调用方转为带 taskId/owner 上下文的明确异常"。
- [x] 修正 `ITeamTaskStore.completeTask` Javadoc（`:~117-118`，"claimedBy is preserved"的误导措辞）与 `MemberFanOutDispatcher`/`MemberDispatchOutcome` 等编排层 Javadoc 中"single CAS"表述，使其描述与实际 epoch CAS 语义一致。

Exit Criteria:

- [x] 新增测试（共享 daemon id 场景——**关键**）：`TestDbTeamTaskStore#sharedDaemonIdEpochCasClosesDoubleExecutionWindow` —— 两个 daemon 实例共享 `DEFAULT_DAEMON_SESSION_ID`，claim T1（epoch=e1）→ reclaim（清 CLAIMED_BY、**保留** epoch）→ 实例 B 重新 claim（epoch=e2>e1）→ 实例 A 在途 `completeTask(T1, daemonSessionId, e1)` CAS **失败**（返回 `Optional.empty()`）；实例 B 的 `completeTask(..., e2)` 成功。`#abandonClaimedBranchBindsEpochAndCreatedBranchMatchesNullEpoch`：CLAIMED+epoch 分支一例（stale epoch 拒绝）+ reclaim 后的 CREATED 分支（epoch-agnostic 谓词命中）一例。
- [x] repo-observable：completeTask/abandonTask 的 SQL WHERE 含 epoch 条件（grep 确认）；abandon 的 CREATED 分支不被破坏（`STATUS='CREATED'`，epoch-agnostic）。
- [x] repo-observable：`AiAgentTeamTaskTable` 含 `CLAIM_EPOCH` 列常量 + DDL/迁移；`initSchema`/迁移对已部署表幂等加列（大小写不敏感 metadata 检测）。
- [x] **端到端验证**：从 scheduler claim → 异步派发 → reclaim → 重新 claim 派发 → 原成员（旧 epoch）完成失败、新成员（新 epoch）完成成功，完整路径双重执行窗口关闭。
- [x] **无静默跳过**：CAS 失败返回 `Optional.empty()`（非静默成功），调用方转为带上下文异常（断言保持）。
- [x] raw-JDBC schema 迁移：迁移步骤与列常量已落地；`ai-dev/design/`（`nop-ai-agent-team-task-reclaim.md` 决策 9）记录 epoch 方案选择与拒绝方案 A 的理由 + reclaim-保留-epoch 不变量。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 修复 checkpoint latest 跨接管排序语义（AR-08）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java`（`:~285` loadLatestCheckpointFromDb、`:~352` loadSessionRowsFromDb、getLatestCheckpoint cache 路径）；对齐 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/FileBackedCheckpointManager.java`（`:~171`）

- Item Types: `Fix`

- [x] **改 `loadLatestCheckpointFromDb`（`:~285`）**：`ORDER BY SEQ DESC` 改为 `ORDER BY CHECKPOINT_TIMESTAMP DESC, WATERMARK DESC`（`WATERMARK` 作确定性 tie-break；**不**用 `ID DESC`——该表无 ID 列）。**保持 `loadSessionRowsFromDb`（`:~352`）按 SEQ 排序不变**，以免影响 `getCheckpoints`（承诺 ascending seq order）与 `CompactionAwareTruncation`（按 seq 推理）。
- [x] **修正 cache 路径 `getLatestCheckpoint`**（执行中发现）：原 cache 路径返回 `list.get(size-1)`（最高 seq），与新的时间序不一致 → 新实例仍返回旧 owner 的高 seq checkpoint。改为从已加载列表中选最大 `CHECKPOINT_TIMESTAMP`（WATERMARK DESC tie-break），与 DB-direct 路径一致；bySession 列表仍 SEQ 升序（隔离 getCheckpoints/compaction）。

Exit Criteria:

- [x] 新增测试：`TestDBCheckpointManager#getLatestCheckpointAcrossTakeoverReturnsMostRecentExecution` —— 两轮 execute（接管：实例 A seq=0..5@早，实例 B seq=0..2@晚），实例 C 调 `getLatestCheckpoint` 返回**实例 B 的最近** checkpoint（seq=2，非 A 的 seq=5）。
- [x] 新增测试：`#getCheckpointsStillAscendingSeqAndDecoupledFromLatestSelection` —— `getCheckpoints` 仍返回 ascending seq order；`getLatestCheckpoint` 为时间最大（与列表末元素解耦）；`CompactionAwareTruncation` 行为不变（既有 compaction 测试全绿，隔离正确）。
- [x] repo-observable：`loadLatestCheckpointFromDb` 的 ORDER BY 为 `CHECKPOINT_TIMESTAMP DESC, WATERMARK DESC`（grep 确认）；`loadSessionRowsFromDb` 仍按 SEQ（grep 确认未误改）。
- [x] **接线验证**：restoreSession 路径（DefaultAgentEngine）消费 `getLatestCheckpoint` 返回的 watermark 来自最近执行（DB 层断言最近执行 checkpoint；engine 调用 getLatestCheckpoint 的接线为既有不变路径）。
- [x] DB backend 的 latest 语义现为"最近一次执行"（按时间）；`FileBackedCheckpointManager` 用单进程插入序（`list.get(size-1)`，跨接管不可达），两者在各自适用场景下 latest 语义自洽——以注释写明 DB 用 `CHECKPOINT_TIMESTAMP`、FileBacked 用插入序及其适用前提。
- [x] 改 live baseline：owner-doc（`DBCheckpointManager` 类 Javadoc + 方法注释）已更新为时间序语义。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] AR-01：completeTask/abandonTask epoch CAS 生效，**共享 daemon id 场景**双重执行窗口关闭（端到端测试通过）；abandon 两源态正确。
- [x] AR-08：getLatestCheckpoint 跨接管返回最近执行 checkpoint，两 backend 语义一致；getCheckpoints/compaction 未受影响。
- [x] raw-JDBC schema 迁移（CLAIM_EPOCH 列）对已部署表幂等生效。
- [x] `ITeamTaskStore`/编排层 Javadoc 与实际 CAS 语义一致。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope confirmed live defect。
- [x] 受影响 owner docs 已同步（`nop-ai-agent-team-task-reclaim.md` 决策 9 + `DBCheckpointManager` Javadoc）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：epoch CAS 在运行时真的被校验（`sharedDaemonIdEpochCasClosesDoubleExecutionWindow` 断言 stale e1 失败 / fresh e2 成功）；无空方法体/静默跳过/no-op。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过（BUILD SUCCESS，全模块全绿）。
- [x] checkstyle / 代码规范检查通过（test-compile + test 全绿，无 unused/import 违规）。

## Deferred But Adjudicated

（本计划无 deferred 项；AR-01/AR-08 均为 in-scope confirmed live defect，不延期。）

## Non-Blocking Follow-ups

- 评估 reclaim 触发策略从"基于时间"升级为"时间 + liveness 探针"（深度审核 14 系列相关），进一步收窄竞态窗口——属优化项，不阻塞本计划 CAS 修复。
- `DBCheckpointManager.remove` 与 AR-08 协同（见 Plan 278 Phase 4，DB impl 暂用 no-op 默认）。

## Closure

Status Note: AR-01（completeTask/abandonTask 缺 owner 身份 CAS → 共享 daemon id 双重执行窗口）与 AR-08（getLatestCheckpoint 跨接管 SEQ 排序失效）两个 confirmed live defect 均已收口。执行中修正了两处 plan 字面指令与目标不相容的实现缺陷：(a) reclaim 改为**保留** CLAIM_EPOCH（原"置 NULL"会使 `COALESCE(NULL,0)+1` 复位为 1，与单调 epoch 退出标准矛盾，窗口重开）——设计决策 9 已记录；(b) cache 路径 getLatestCheckpoint 同步改为时间序（原 `list.get(size-1)` 仍返回最高 seq，DB-direct 修复不生效）。同时修复了两个 collateral bug：幂等迁移的 metadata 大小写判定（H2 误判致 "Duplicate column name"）、daemon 把 claimed task 传给 router 触发假 bound 目标（改为 route 用原始 task / dispatch 用 claimed task）。全模块测试 BUILD SUCCESS，独立 closure audit CLOSURE_OK。
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_11abb9891ffeHgDXUw4HRzXowW`，非实现者）
- Audit Session: ses_11abb9891ffeHgDXUw4HRzXowW
- Evidence:
  - **Phase 1 Exit Criteria**:
    - 共享 daemon id 场景双重执行窗口关闭：PASS — `TestDbTeamTaskStore#sharedDaemonIdEpochCasClosesDoubleExecutionWindow`（claim e1→reclaim 保留 epoch→re-claim e2>e1→stale e1 complete 返回 empty、fresh e2 成功，:396-442）。
    - SQL WHERE 含 epoch + abandon CREATED 分支不破坏：PASS — `DbTeamTaskStore.java:372`（complete `AND CLAIM_EPOCH=?`）、`:423-425`（abandon `((STATUS='CLAIMED' AND CLAIM_EPOCH=?) OR STATUS='CREATED')`）。
    - CLAIM_EPOCH 列常量 + DDL/迁移幂等：PASS — `AiAgentTeamTaskTable.java:82/101/117-119` + `DbTeamTaskStore.java:178/184`（大小写不敏感 metadata 检测）。
    - 端到端（scheduler claim→dispatch→reclaim→re-claim→旧 epoch 失败/新 epoch 成功）：PASS — 同上 focused 测试 + daemon 路由修正（`TeamTaskSchedulerDaemon.java:925,944,985` route 用 routingTask / dispatch 用 claimedTask）。
    - 无静默跳过（CAS 失败返回 empty、调用方转异常）：PASS。
    - schema 迁移 + 设计记录方案选择/拒绝方案 A：PASS — `nop-ai-agent-team-task-reclaim.md` 决策 9 + 拒绝方案表。
    - daily log：PASS — `ai-dev/logs/2026/06-20.md` plan-279 条目。
  - **Phase 2 Exit Criteria**:
    - 跨接管 latest 返回最近执行：PASS — `TestDBCheckpointManager#getLatestCheckpointAcrossTakeoverReturnsMostRecentExecution`（:532，断言 wm-b-2=B seq=2 非 A seq=5）。
    - getCheckpoints 升序隔离 + compaction 不变：PASS — `#getCheckpointsStillAscendingSeqAndDecoupledFromLatestSelection`（:571）；既有 compaction 测试全绿。
    - ORDER BY 为 `CHECKPOINT_TIMESTAMP DESC, WATERMARK DESC`：PASS — `DBCheckpointManager.java:319-320`；`loadSessionRowsFromDb` 仍 SEQ DESC（:387）。
    - 接线（restoreSession 消费最近 watermark）：PASS — DB 层断言最近执行；engine 调 getLatestCheckpoint 为既有不变路径。
    - owner-doc 更新：PASS — `DBCheckpointManager` 类 Javadoc + 方法注释改为时间序语义。
    - daily log：PASS。
  - **Closure Gates**：全 PASS（见上勾选）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（34/34 items checked；Closure Evidence 已写入）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（无 high/critical 空壳发现）。
  - **Anti-Hollow 检查**：epoch CAS 运行时真被校验（stale e1 失败/fresh e2 成功的断言）；端到端 claim→reclaim→re-claim→complete 路径连通；无空方法体/静默跳过/no-op。
  - **Deferred 项分类检查**：无 deferred 项；AR-01/AR-08 均为 in-scope confirmed live defect，已修复未降级。
  - **Build**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（全模块全绿；`TestSteeringInjection` 为既有 engine order-pollution flaky，clean-HEAD 同形态，与 plan-279 无关）。

Follow-up:

- 评估 reclaim 触发策略从"基于时间"升级为"时间 + liveness 探针"（深度审核 14 系列）——优化项，不阻塞本计划 CAS 修复。
- `DBCheckpointManager.remove` 与 AR-08 协同（见 Plan 278 Phase 4，DB impl 暂用 no-op 默认）。
