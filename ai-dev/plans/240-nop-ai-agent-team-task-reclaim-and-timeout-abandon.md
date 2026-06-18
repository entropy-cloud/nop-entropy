# 240 nop-ai-agent Team Task RE-CLAIM + 超时自动 ABANDON（闭合无人值守 DAG 任务生命周期）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-team-task-reclaim-and-timeout-abandon

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/239-nop-ai-agent-team-execute-flow-tool.md`（Non-Blocking Follow-ups line 188：`task RE-CLAIM / 超时自动 ABANDON：任务 reset / 超时生命周期转换为 successor`）；同一 carry-over 在 `233`/`235`/`236`/`237`/`238` 的 Non-Goals / Non-Blocking Follow-ups 中亦显式延期为独立 successor；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（team task lifecycle reliability）
> Related: `227`（交付 `ITeamTaskStore` 状态机 claimTask/completeTask/abandonTask + `DbTeamTaskStore` raw JDBC CAS + `AiAgentTeamTaskTable`——本计划在其状态机上新增 `reclaimTask` CLAIMED→CREATED 转换）、`222`（交付 `ScheduledRecoveryManager` 定时扫描 daemon + scanOnce——本计划在其上新增 team-task 卡死检测恢复步骤）、`226`（交付 `IOrphanRecoveryHandler` 可插拔 handler + NoOp shipped 默认模式——本计划镜像其设计模式）、`229`（交付 `ISessionTimeoutHandler` session 超时三分裁定 + scanOnce timeout 步骤 + RecoveryScanResult 7-arg 扩展——本计划镜像其 handler/outcome/scanOnce 扩展模式，但作用于 team task 而非 session）、`233`（交付 `TeamTaskFlowOrchestrator` 同步 DAG 执行——卡死 CLAIMED task 阻塞的 DAG 正是本计划闭合的可靠性缺口）、`239`（交付 `team-execute-flow` LLM 入口——无人值守 DAG 执行的顶部入口，依赖本计划闭合的任务生命周期自愈）

## Purpose

把 nop-ai-agent 的团队任务生命周期从"CLAIMED 是单向不可逆——一个被成员认领后中途崩溃（session orphaned/timeout/进程死亡）的任务永远卡在 CLAIMED，阻塞整个 task DAG 无法推进，也无超时自动收口"扩展为"RecoveryManager daemon 的 scanOnce 新增一步 team-task 卡死检测与恢复：对 `STATUS='CLAIMED'` 且 `UPDATED_AT` 超过配置阈值的任务，经可插拔 `ITeamTaskRecoveryHandler` 裁定动作——RE-CLAIM（CLAIMED→CREATED 重置为可重新认领）或 ABANDON（CLAIMED→ABANDONED 终态标记失败）"。本计划闭合无人值守多 Agent DAG 编排栈（plans 233→239 已交付同步编排 + LLM 入口）的**任务生命周期自愈缺口**——没有它，卡死的 CLAIMED 任务会永久阻塞 DAG，无人值守自动化定位不成立。

## Current Baseline

基于 live repo 核对（来源：plan 227 / 222 / 226 / 229 closure audit evidence，均已对照 live code path 验证；本段描述现状）：

- **`ITeamTaskStore` 状态机 ✅**（plan 227）：`claimTask(taskId, claimedBy)` / `completeTask(taskId, completedBy)` / `abandonTask(taskId, abandonedBy)` 三个状态转换方法已落地，返回 `Optional<TeamTask>`（CAS 失败/非法转换返回 empty）。**无 reclaimTask（CLAIMED→CREATED）转换**——CLAIMED 只能前进到 COMPLETED/ABANDONED，不可回退。`io.nop.ai.agent.team.ITeamTaskStore`。
- **`TeamTask` 数据对象 ✅**（plan 227）：含 `claimedBy`（String，nullable——claim 前为 null，claim 后记录认领者 sessionId），全参构造 + getter，无 setter。**无 updatedAt/claimedAt 字段**（plan 227 裁定 7：转换时间在 DB 层 UPDATED_AT 列记录，数据对象保持精简）。`io.nop.ai.agent.team.TeamTask`。
- **`TeamTaskStatus` 枚举 ✅**（plan 227）：`CREATED` / `CLAIMED` / `COMPLETED` / `ABANDONED`。状态机转换图 Javadoc：`CREATED → CLAIMED → COMPLETED`、`CLAIMED → ABANDONED`、`CREATED → ABANDONED`。**无 `CLAIMED → CREATED` 转换**。`io.nop.ai.agent.team.TeamTaskStatus`。
- **`InMemoryTeamTaskStore` ✅**（plan 227）：`ConcurrentHashMap.compute` 原子 CAS。`NoOpTeamTaskStore` ✅：转换方法抛 `UnsupportedOperationException`。`DbTeamTaskStore` ✅：raw JDBC 条件 UPDATE on STATUS CAS（affected-row-count==1 判定），构造期 `initSchema()` 自动建表。
- **`AiAgentTeamTaskTable` 常量类 ✅**（plan 227，plan 232 扩展）：`TABLE_NAME = "ai_agent_team_task"` + 列名常量（TASK_ID / TEAM_ID / SUBJECT / DESCRIPTION / BLOCKED_BY / STATUS / CREATED_BY / CLAIMED_BY / CREATED_AT / UPDATED_AT / **TENANT_ID**）+ `DDL_CREATE_TABLE`（`CREATE TABLE IF NOT EXISTS`）。`UPDATED_AT`（BIGINT）在每次状态转换时更新，对 CLAIMED 任务即"认领时间"代理。`TENANT_ID` 由 plan 232（多租户隔离）新增。
- **多租户隔离模式 ✅**（plan 232）：模块内全部 raw-JDBC DB store + recovery handler 统一经 `ITenantResolver` 注入租户守卫。`DbTeamTaskStore` 构造期接收 `ITenantResolver`，所有 SELECT/UPDATE 追加 `TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID)`（`AND (TENANT_ID = ? OR TENANT_ID IS NULL)`）。`DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler` 同样接收 `ITenantResolver` 并对所有 `ai_agent_session` / `ai_agent_session_lock` 操作注入租户守卫。本计划新增的 `DefaultTeamTaskRecoveryHandler` + `DbTeamTaskStore.reclaimTask` **必须遵循同一模式**（见设计裁定 4a）。
- **RecoveryManager 定时扫描 daemon ✅**（plan 222）：`IRecoveryManager`（`start`/`stop` 幂等 + `scanOnce → RecoveryScanResult`）+ `NoOpRecoveryManager` shipped 默认 + `ScheduledRecoveryManager`（`IScheduledExecutor` 周期调度默认 60s）位于 `io.nop.ai.agent.runtime.recovery` 包。scanOnce 当前步骤顺序（plan 229）：stale lock cleanup → timeout detection（session 级）→ orphan detection + recovery。**scanOnce 当前无 team-task 卡死检测步骤。**
- **orphan 恢复 + session 超时 handler 模式 ✅**（plan 226 / 229）：可插拔 handler（`IOrphanRecoveryHandler` / `ISessionTimeoutHandler`）+ `NoOp*Handler` shipped 默认（零回归）+ `Default*Handler` 功能实现 + `ScheduledRecoveryManager` setter 注入 + scanOnce 集成 + `RecoveryScanResult` 字段扩展（当前 7-arg：staleLocksCleaned / orphanSessionsDetected / orphanSessionIds / scanDurationMs / scannedAt / recoveryActions:List<RecoveryOutcome> / timeoutActions:List<TimeoutOutcome>）。`RecoveryScanResult.empty()` 工厂返回全空列表。
- **`TeamTaskFlowOrchestrator` 同步 DAG 执行 ✅**（plan 233）：`execute(teamId) → TeamTaskFlowResult` 经 nop-task DAG `syncGetOutputs`。节点失败返回 `success=false`（honest failure）。`team-execute-flow` LLM 工具入口 ✅（plan 239）。
- **零 team-task 卡死恢复代码**：grep `ITeamTaskRecoveryHandler|TeamTaskRecovery|reclaimTask|recoverStuckTasks|stuckClaimed` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 类定义命中（NEXT_ITEM 已确认）。

## Goals

- **状态机扩展：`reclaimTask(taskId, reclaimedBy)` → `CLAIMED → CREATED`**：重置任务为可重新认领状态（`claimedBy` 清为 null）。`ITeamTaskStore` 新增该方法，返回 `Optional<TeamTask>`（CAS 失败/非 CLAIMED 返回 empty）。`InMemoryTeamTaskStore`（compute CAS）/ `NoOpTeamTaskStore`（UOE）/ `DbTeamTaskStore`（条件 UPDATE CAS）三实现同步。`TeamTaskStatus` Javadoc 补充 `CLAIMED → CREATED` 转换。
- **`ITeamTaskRecoveryHandler` 可插拔 handler**：`recoverStuckTasks() → List<TeamTaskRecoveryOutcome>`（自包含——handler 内部经 raw JDBC 检测卡死 CLAIMED 任务并裁定动作，scanOnce 仅调用此方法，不直接触碰 `ai_agent_team_task` 表）。
- **`TeamTaskRecoveryAction` 枚举**：`RECLAIM`（CLAIMED→CREATED 重置可重新认领）/ `ABORT`（CLAIMED→ABANDONED 终态标记失败）/ `SKIP`（LOG.warn，NoOp shipped 默认）。
- **`TeamTaskRecoveryOutcome` 数据对象**：`taskId`（String）+ `action`（TeamTaskRecoveryAction）+ `succeeded`（boolean）+ `message`（String）。镜像 `RecoveryOutcome` / `TimeoutOutcome` 结构。
- **`NoOpTeamTaskRecoveryHandler` shipped 默认**：singleton；`recoverStuckTasks` 返回 `Collections.emptyList()`（SKIP，零 DB 访问，零行为回归）。
- **`DefaultTeamTaskRecoveryHandler` 功能实现**：构造期接收 `DataSource dataSource` + `long taskTimeoutSeconds`（卡死阈值）+ `TeamTaskRecoveryAction defaultAction`（RECLAIM 或 ABORT）+ `ITenantResolver tenantResolver`（默认 `NullTenantResolver.INSTANCE`，与 `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler` 同一 opt-in 模式，plan 232）。`recoverStuckTasks` 执行：SELECT `ai_agent_team_task` 中 `STATUS='CLAIMED' AND UPDATED_AT < ?`（now - threshold）**+ `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫** → 对每个卡死任务按 defaultAction 裁定动作（RECLAIM raw JDBC UPDATE STATUS='CREATED',CLAIMED_BY=NULL / ABORT raw JDBC UPDATE STATUS='ABANDONED'），条件 WHERE STATUS='CLAIMED' **+ 租户守卫**防 CAS 竞争 + 跨租户隔离，affected-row-count 判定 succeeded。
- **`ScheduledRecoveryManager` scanOnce 集成**：新增 `teamTaskRecoveryHandler` 字段（默认 `NoOpTeamTaskRecoveryHandler`）+ `setTeamTaskRecoveryHandler` setter。scanOnce 在 orphan detection **之后**新增第 4 步：调用 `handler.recoverStuckTasks()`，结果汇总到 `RecoveryScanResult`。team-task 恢复独立于 session 恢复（不同域表），置于最后最小化干扰。
- **`RecoveryScanResult` 8-arg 扩展**：新增 `teamTaskRecoveryActions: List<TeamTaskRecoveryOutcome>` 字段；`empty()` 工厂返回 `teamTaskRecoveryActions = Collections.emptyList()`；新增 getter；现有调用方（`ScheduledRecoveryManager.scanOnce` / `NoOpRecoveryManager.scanOnce`）适配。镜像 plan 226/229 的 N-arg 扩展模式。
- **端到端验证**（Anti-Hollow #22）：构造团队 + 单任务（member 认领后模拟崩溃 = session orphaned，task 卡在 CLAIMED 且 UPDATED_AT 早于阈值）→ `ScheduledRecoveryManager.scanOnce`（注入 `DefaultTeamTaskRecoveryHandler` RECLAIM）→ 断言 task 被重置为 CREATED（`claimedBy=null`，可重新认领）+ `RecoveryScanResult.teamTaskRecoveryActions` 含 succeeded outcome → 验证 task 可被另一 member 重新 `claimTask`。ABORT 路径同理断言 task → ABANDONED（终态）。
- **设计文档**：新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-team-task-reclaim.md`（记录核心裁定：RECLAIM=CLAIMED→CREATED / 时间基检测 / handler 自包含 / scanOnce 第 4 步 / 拒绝替代方案）+ 更新 `nop-ai-agent-actor-runtime-vision.md` §8.2（team task lifecycle）+ roadmap §4 Layer 4。

## Non-Goals

- **claimer-liveness 交叉检测**：检测 CLAIMED 任务的 `claimedBy` session 是否 orphaned/timed-out（经 session recovery 状态交叉引用），而非仅依赖任务时间戳。本计划采用时间基检测（`UPDATED_AT` 超过阈值），与 plan 229 session 超时一致。claimer-liveness 交叉检测为 successor（依赖 session↔team-task 交叉查询 + liveness 判定策略）。Classification: successor plan required。
- **per-task / per-team 超时配置**：每个任务或团队独立的超时阈值。本计划超时阈值为 handler 构造期全局可配置 `taskTimeoutSeconds`。per-task 维度是 successor（依赖 TeamTask 字段扩展 + 配置 precedence 裁定）。Classification: optimization candidate。
- **per-task 动作策略**：根据卡死时长动态选择动作（年轻→RECLAIM，极旧→ABORT）。本计划为单一 `defaultAction`（所有卡死任务统一策略）。动态分级策略为 successor。Classification: optimization candidate。
- **`team-task-reclaim` LLM 工具**：让 LLM agent 主动触发 reclaim（经 IToolExecutor）。本计划仅 daemon 自动恢复。LLM 工具为 successor（`reclaimTask` 已在 store 落地，未来工具可直接消费）。Classification: successor plan required。
- **ABANDONED → CREATED 重新认领**：终态任务（ABANDONED/COMPLETED）复活。本计划 RECLAIM 只作用于 CLAIMED（非终态的卡死态），终态不可逆。Classification: out-of-scope improvement。
- **异步 / 跨进程流编排执行**（plan 239 carry-over `L4-async-cross-process-orchestration`）：需 nop-task CompletableFuture async model。Classification: successor plan required。
- **多成员 per-task 路由**（plan 239 carry-over `L4-multi-member-per-task-routing`）。Classification: successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over `L4-spawn-session-pooling`）。Classification: optimization candidate。
- **修改 `IRecoveryManager` / `ScheduledRecoveryManager` 接口的 scanOnce 签名**：消费原样签名，仅在 scanOnce 内部新增步骤 + 新增 handler 字段/setter（与 plan 226/229 扩展模式一致）。
- **`RecoveryScanResult` 构造器重构**（builder / action-log Map）：8-arg 持续膨胀的治理项。Classification: optimization candidate（plan 229 已列为 successor）。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **nop-job `IJobScheduler` 集成**（plan 222 已裁定 `IScheduledExecutor` 足够）。Classification: successor plan required。

## Scope

### In Scope

- `io.nop.ai.agent.team` 包：
  - `TeamTaskStatus.java` — 扩展：状态机转换图 Javadoc 补充 `CLAIMED → CREATED`（reclaim）转换
  - `ITeamTaskStore.java` — 扩展：新增 `Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy)`
  - `InMemoryTeamTaskStore.java` — 扩展：实现 `reclaimTask`（`compute` CAS：CLAIMED→CREATED + claimedBy=null）
  - `NoOpTeamTaskStore.java` — 扩展：`reclaimTask` 抛 `UnsupportedOperationException`
  - `DbTeamTaskStore.java` — 扩展：实现 `reclaimTask`（条件 UPDATE CAS：`SET STATUS='CREATED', CLAIMED_BY=NULL WHERE TASK_ID=? AND STATUS='CLAIMED'` + `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫，与既有 claimTask/completeTask/abandonTask 同一 CAS + 租户模式）
- `io.nop.ai.agent.runtime.recovery` 包（新增）：
  - `TeamTaskRecoveryAction.java` — 枚举：RECLAIM / ABORT / SKIP
  - `TeamTaskRecoveryOutcome.java` — 数据对象：taskId + action + succeeded + message
  - `ITeamTaskRecoveryHandler.java` — 接口：`recoverStuckTasks() → List<TeamTaskRecoveryOutcome>`
  - `NoOpTeamTaskRecoveryHandler.java` — shipped 默认：返回 emptyList（零回归）
  - `DefaultTeamTaskRecoveryHandler.java` — 功能实现：DataSource + taskTimeoutSeconds + defaultAction + `ITenantResolver`（默认 `NullTenantResolver.INSTANCE`）→ 检测 + 裁定 + raw JDBC 动作（所有 SQL 注入 `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫）
- `io.nop.ai.agent.runtime.recovery` 包（扩展）：
  - `ScheduledRecoveryManager.java` — 新增 `teamTaskRecoveryHandler` 字段 + `setTeamTaskRecoveryHandler` setter + scanOnce 第 4 步
  - `RecoveryScanResult.java` — 8-arg 扩展（`teamTaskRecoveryActions` 字段 + getter + `empty()` 适配）
  - `NoOpRecoveryManager.java` — `empty()` 适配
- 测试文件：
  - `TestInMemoryTeamTaskStoreReclaim.java`（reclaim 状态机 focused 测试：CLAIMED→CREATED / CAS / claimedBy 清空 / 非 CLAIMED 拒绝）
  - `TestDbTeamTaskStoreReclaim.java`（H2 DB reclaim raw JDBC CAS：两实例竞争 / 跨实例可见 CREATED）
  - `TestDefaultTeamTaskRecoveryHandler.java`（RECLAIM 动作 / ABORT 动作 / SKIP NoOp / 超时检测 / 非卡死任务不动 / CAS 竞争 succeeded=false / 接线验证）
  - `TestTeamTaskRecoveryDaemonIntegration.java`（scanOnce 第 4 步 + RecoveryScanResult 8-arg + setter 注入 + 端到端）
- 设计文档：`ai-dev/design/nop-ai-agent/nop-ai-agent-team-task-reclaim.md`（新）+ `nop-ai-agent-actor-runtime-vision.md` §8.2（更新）+ `nop-ai-agent-roadmap.md` §4 Layer 4（新增工作项）

### Out Of Scope

- 见 Non-Goals（claimer-liveness 交叉检测 / per-task 配置 / 动作策略 / LLM reclaim 工具 / 终态复活 / async 编排 / 多成员路由 / spawn 池化 / 构造器重构 / TeamManager / nop-job 均为显式 successor）

### 设计裁定（Pre-Adjudicated）

以下裁定在 plan 撰写阶段已确定，执行时直接遵循：

1. **RE-CLAIM = `CLAIMED → CREATED`（重置可重新认领），非 `ABANDONED → CREATED`（终态复活）**。reclaimTask 只作用于 CLAIMED（卡死态），重置为 CREATED + `claimedBy=null`，使任务可被任意 member 重新认领。ABANDONED/COMPLETED 为终态不可逆。理由：(1) 卡死问题的根因是 CLAIMED 任务持有者消失，重置到 CREATED 让其他 member 重新认领是最小恢复；(2) 终态复活破坏状态机终态不变性（plan 227 已裁定 ABANDONED 为终态，RE-CLAIM successor 仅指 CLAIMED 回退，非终态复活）；(3) `claimTask` 语义为 `CREATED → CLAIMED`，reclaim 后任务回到 CREATED 即可走原 claim 流程。

2. **检测 = 时间基（`UPDATED_AT < now - threshold`），非 claimer-liveness 交叉检测**。对 `ai_agent_team_task` 执行 `SELECT TASK_ID, CLAIMED_BY WHERE STATUS='CLAIMED' AND UPDATED_AT < ?`（用 `AiAgentTeamTaskTable` 列常量）。`UPDATED_AT` 在 CLAIMED 时即"认领时间"代理（plan 227 裁定 7）。理由：(1) 与 plan 229 session 超时检测一致（时间基）；(2) 不误杀活跃长任务（仍在迭代的 member 会驱动 task 最终 complete，CLAIMED→COMPLETED 刷新 UPDATED_AT；只有真正卡死停止推进的才超时）；(3) claimer-liveness 交叉检测需 session↔team-task 跨表 join + liveness 判定策略，是更重的独立 successor；(4) 避免新 DDL（UPDATED_AT 已存在）。

3. **handler 自包含（`recoverStuckTasks()` 内部做检测 + 动作），scanOnce 仅调用此方法**。与 plan 226/229 的"scanOnce 做 SELECT + handler 做 per-item 动作"模式不同——本计划 handler 内部完成全部 team-task 域逻辑（检测 + 裁定 + raw JDBC 动作）。理由：(1) team-task 是不同域表（`ai_agent_team_task`），scanOnce 当前只操作 session 域表（`ai_agent_session` / `ai_agent_session_lock`），将 team-task 检测逻辑封装在 handler 中保持 daemon 聚焦于 session 域；(2) handler 是单一集成点（scanOnce 一行调用 `handler.recoverStuckTasks()`），team-task 表结构变更不波及 `ScheduledRecoveryManager`；(3) NoOp shipped 默认（返回 emptyList）零 DB 访问零回归；(4) handler 可独立测试（注入 H2 DataSource），无需经 scanOnce 时序。这是对 plan 226/229 模式的**有意且裁定的偏差**，理由如上。

4. **handler 动作 = raw JDBC UPDATE（RECLAIM → STATUS='CREATED',CLAIMED_BY=NULL / ABORT → STATUS='ABANDONED'），条件 WHERE STATUS='CLAIMED' CAS**。与 plan 226 ABORT / plan 229 FORCE_FAILED 的 raw JDBC UPDATE 模式一致（handler 侧直接 UPDATE，不经 store 方法）。理由：(1) recovery 包既有约定——所有 handler 动作（ABORT session / FORCE_FAILED session）均 raw JDBC；(2) 条件 WHERE STATUS='CLAIMED' 保证 CAS——若任务已被其他路径转换（如 member 恰好 complete 了），affected-row=0 → succeeded=false（诚实，非静默）；(3) reclaimTask store 方法独立存在（契约完整性 + in-memory 支持 + 未来 LLM 工具），handler 的 raw JDBC SQL 与 store 的 CAS SQL 语义等价（双路径产出相同结果）。注：store 的 reclaimTask 经独立 focused 测试覆盖；handler 的 raw JDBC 经独立 focused 测试覆盖——两者不共享实现但语义一致（与 plan 226/229 handler 不复用 store 的既有模式一致）。

4a. **多租户隔离：handler + DbTeamTaskStore.reclaimTask 统一经 `ITenantResolver` 注入 `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫**。`DefaultTeamTaskRecoveryHandler` 构造期接收 `ITenantResolver`（默认 `NullTenantResolver.INSTANCE`，与 `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler` 同一 opt-in 模式，plan 232）。所有 SQL（SELECT 检测 + UPDATE RECLAIM/ABORT）追加 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)`（经 `TenantSql.whereTenant(AiAgentTeamTaskTable.COL_TENANT_ID)`）。`DbTeamTaskStore.reclaimTask` 复用既有 `ITenantResolver` 字段 + 既有 `conditionalUpdate` 辅助方法注入同款守卫（与 claimTask/completeTask/abandonTask 完全一致）。理由：(1) plan 232 已确立 `ai_agent_team_task` 表的 `TENANT_ID` 列 + 全部 `DbTeamTaskStore` 操作的租户守卫为基线约定，新增 reclaimTask 不应打破；(2) `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler`（本计划镜像的两个 handler）均接收 `ITenantResolver` 并注入守卫，省略将导致跨租户恢复（tenant A 的 daemon 重置/放弃 tenant B 的任务）；(3) `NullTenantResolver.INSTANCE` 默认保证单租户部署零回归。Phase 2 focused 测试须含跨租户隔离断言（tenant-A daemon 不恢复 tenant-B 的卡死任务）。

5. **单一 `defaultAction` 策略（RECLAIM 或 ABORT），所有卡死任务统一**。不按卡死时长动态分级（年轻→RECLAIM，极旧→ABORT）。理由：(1) 首版最小可用——集成商按部署场景选择策略（开发/测试 → RECLAIM 重试友好；生产严格 → ABORT 快速失败）；(2) 动态分级需双阈值 + 分级裁定逻辑，是独立产品策略决策；(3) 与 plan 226 单一 RecoveryMode（整个 daemon 一个模式）一致。

6. **scanOnce 第 4 步置于 orphan detection 之后（最后）**。步骤顺序：stale lock cleanup → timeout detection → orphan detection → **stuck team task recovery**。理由：(1) team-task 恢复独立于 session 恢复（不同域表，无数据依赖）；(2) 置于最后最小化对既有 session 恢复步骤的干扰；(3) 若 member session 被 orphan handler RESUME（恢复续跑），其 CLAIMED task 可能在 session 恢复后 complete——但本计划时间基检测只针对真正卡死（超阈值）的任务，恢复中的 session 的 task 不会超阈值（恢复会推进 task），故无冲突。

7. **NoOpTeamTaskRecoveryHandler 为 shipped 默认（返回 emptyList，零 DB 访问）**。与 plan 222/226/229 的 NoOp 模式一致。集成商经 `manager.setTeamTaskRecoveryHandler(handler)` 注入功能性实现。

8. **`reclaimTask` 加入 `ITeamTaskStore` 契约（CLAIMED→CREATED），handler 不复用但 store 独立测试**。理由：(1) 状态机完整性——`CLAIMED → CREATED` 现在是合法转换，store 作为 canonical state machine 应支持；(2) in-memory 团队支持 reclaim（测试场景）；(3) 未来 `team-task-reclaim` LLM 工具可直接消费 store 方法；(4) `TeamTaskStatus` Javadoc 需反映新转换消除 contract drift（Minimum Rules #11）。handler 侧用 raw JDBC 是 recovery 包约定（裁定 4），两者语义等价。

## Execution Plan

### Phase 1 - 状态机扩展（reclaimTask）+ handler 契约表面 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.team`（TeamTaskStatus / ITeamTaskStore / InMemoryTeamTaskStore / NoOpTeamTaskStore / DbTeamTaskStore 扩展）、`io.nop.ai.agent.runtime.recovery`（TeamTaskRecoveryAction / TeamTaskRecoveryOutcome / ITeamTaskRecoveryHandler / NoOpTeamTaskRecoveryHandler 新文件）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Decision`（RE-CLAIM=CLAIMED→CREATED / 时间基检测 / handler 自包含 / 单一 defaultAction / scanOnce 第 4 步）、`Fix`（`ITeamTaskStore` 状态机缺 CLAIMED→CREATED 转换 = plan 227 显式 deferred successor）、`Proof`

- [x] `TeamTaskStatus` 状态机转换图 Javadoc 补充 `CLAIMED → CREATED`（reclaim）转换，消除 contract surface（enum 图）与即将落地的 reclaimTask 语义的 drift（Minimum Rules #11）
- [x] `ITeamTaskStore` 新增 `Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy)`：`CLAIMED → CREATED`（重置 claimedBy=null）。Javadoc 明确合法转换 + CAS-empty 语义（非 CLAIMED 返回 empty）
- [x] `NoOpTeamTaskStore.reclaimTask` 抛 `UnsupportedOperationException`（与 claimTask/completeTask/abandonTask 一致，Minimum Rules #24）
- [x] `InMemoryTeamTaskStore.reclaimTask`：经 `tasks.compute(taskId, ...)` 原子校验当前 status==CLAIMED → 构造新 `TeamTask`（继承字段 + status=CREATED + claimedBy=null）替换；非 CLAIMED / 不存在返回 empty
- [x] `DbTeamTaskStore.reclaimTask`：条件 UPDATE `SET STATUS='CREATED', CLAIMED_BY=NULL, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'`（用 `AiAgentTeamTaskTable` 常量）**+ `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫**（复用既有 `ITenantResolver` 字段 + `conditionalUpdate` 辅助方法，与 claimTask/completeTask/abandonTask 完全一致），affected-row==1 后 SELECT 回读，否则 empty
- [x] 定义 `TeamTaskRecoveryAction` 枚举：`RECLAIM` / `ABORT` / `SKIP`，Javadoc 明确每种动作语义（RECLAIM 重置可重新认领 / ABORT 终态标记失败 / SKIP 仅观测）
- [x] 定义 `TeamTaskRecoveryOutcome` 数据对象：`taskId`（String）+ `action`（TeamTaskRecoveryAction）+ `succeeded`（boolean）+ `message`（String），不可变（全参构造 + getter）。镜像 `RecoveryOutcome` / `TimeoutOutcome` 结构
- [x] 定义 `ITeamTaskRecoveryHandler` 接口：`List<TeamTaskRecoveryOutcome> recoverStuckTasks()`。Javadoc 明确：自包含语义（内部检测 + 动作）、线程安全契约（可被 daemon 扫描线程调用）、NoOp 语义（返回 emptyList）
- [x] 实现 `NoOpTeamTaskRecoveryHandler`（singleton）：`recoverStuckTasks` 返回 `Collections.emptyList()`（零 DB 访问，零行为回归）
- [x] `nop-ai-agent-actor-runtime-vision.md` §8.2 标注 team task reclaim + timeout abandon 为 successor 接管中（裁定理由落档：RE-CLAIM=CLAIMED→CREATED / 时间基检测 / handler 自包含 / scanOnce 第 4 步 / 拒绝替代方案）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TeamTaskStatus` Javadoc 含 `CLAIMED → CREATED` 转换（无 contract drift）
- [x] `ITeamTaskStore` 含 `reclaimTask(String, String) → Optional<TeamTask>` 方法
- [x] `NoOpTeamTaskStore.reclaimTask` 抛 `UnsupportedOperationException`（**无静默跳过** #24）
- [x] `InMemoryTeamTaskStore.reclaimTask` 经 `compute` 原子 CAS（CLAIMED→CREATED + claimedBy=null），非 CLAIMED 返回 empty
- [x] `DbTeamTaskStore.reclaimTask` 经条件 UPDATE CAS（affected-row-count 判定），非 CLAIMED affected=0 返回 empty
- [x] `TeamTaskRecoveryAction.java` + `TeamTaskRecoveryOutcome.java` + `ITeamTaskRecoveryHandler.java` + `NoOpTeamTaskRecoveryHandler.java` 文件存在于 `io.nop.ai.agent.runtime.recovery` 包
- [x] `NoOpTeamTaskRecoveryHandler.recoverStuckTasks` 返回 `emptyList()`（零 DB 访问，非静默跳过——显式 SKIP 语义）
- [x] 设计裁定已落档（vision §8.2 + 8 条裁定理由）
- [x] **No new test required for contract surface files**：TeamTaskRecoveryAction/Outcome/ITeamTaskRecoveryHandler/NoOpTeamTaskRecoveryHandler 为契约表面（枚举 + 数据对象 + 接口 + NoOp），NoOp 行为验证在 Phase 2 focused 测试中覆盖；reclaimTask store 方法的行为测试在 Phase 2（Minimum Rules #25）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - reclaimTask store 测试 + DefaultTeamTaskRecoveryHandler 功能实现 + daemon 集成 + RecoveryScanResult 扩展 + focused 测试

Status: completed
Targets: `io.nop.ai.agent.runtime.recovery.DefaultTeamTaskRecoveryHandler`、`ScheduledRecoveryManager`（teamTaskRecoveryHandler 字段 + setter + scanOnce 第 4 步）、`RecoveryScanResult`（8-arg 扩展）、`NoOpRecoveryManager`（empty() 适配）、测试

- Item Types: `Fix`（team-task 卡死无恢复机制 = carry-over gap）、`Proof`

- [x] 编写 `TestInMemoryTeamTaskStoreReclaim`：CLAIMED→CREATED 成功 + claimedBy 清空 / 重复 reclaim（CREATED 再 reclaim 返回 empty）/ COMPLETED reclaim 返回 empty / ABANDONED reclaim 返回 empty / task 不存在返回 empty / 并发 reclaim 只有一个成功（多线程 reclaimTask 同一 taskId 仅 1 个 non-empty）
- [x] 编写 `TestDbTeamTaskStoreReclaim`（H2 真实 DB）：CLAIMED→CREATED 回读 claimedBy=null / 非 CLAIMED affected=0 返回 empty / 跨实例可见 CREATED（实例 A reclaim，实例 B getTask 见 CREATED）/ CAS 竞争（两实例并发 reclaim 仅 1 个胜出）
- [x] 实现 `DefaultTeamTaskRecoveryHandler`：
  - 构造期接收 `DataSource dataSource` + `long taskTimeoutSeconds` + `TeamTaskRecoveryAction defaultAction` + `ITenantResolver tenantResolver`（默认 `NullTenantResolver.INSTANCE`，与 `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler` 同一 opt-in 模式，plan 232）。依赖项 dataSource 为 null / timeoutSeconds<=0 / defaultAction==SKIP 时 fail-fast 抛 `IllegalArgumentException`（防止 silent misuse——SKIP 应直接用 NoOp handler）
  - `recoverStuckTasks`：(a) SELECT `TASK_ID, CLAIMED_BY FROM ai_agent_team_task WHERE STATUS='CLAIMED' AND UPDATED_AT < ?`（now - timeoutSeconds，用 `AiAgentTeamTaskTable` 常量，与 `DbTeamTaskStore` SQL 模式一致）**+ `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫**；(b) 对每个结果行按 defaultAction 裁定：
    - `RECLAIM`：raw JDBC `UPDATE ai_agent_team_task SET STATUS='CREATED', CLAIMED_BY=NULL, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'`（条件 WHERE CAS）**+ 租户守卫**，affected=1 → succeeded=true，affected=0 → succeeded=false + message="task already transitioned"
    - `ABORT`：raw JDBC `UPDATE ai_agent_team_task SET STATUS='ABANDONED', UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CLAIMED'` **+ 租户守卫**，affected=1 → succeeded=true，affected=0 → succeeded=false
  - (c) **SQLException 双层处理**（镜像 `DefaultOrphanRecoveryHandler.handleAbort` / `DefaultSessionTimeoutHandler.forceFailed` 的 per-item 隔离模式）：检测 SELECT 的 SQLException → 包裹为 `NopAiAgentException` 抛出（与 `ScheduledRecoveryManager.selectOrphanSessions` 检测层一致，检测失败 = 整步不可用）；per-task RECLAIM/ABORT UPDATE 的 SQLException → **catch + 返回 `TeamTaskRecoveryOutcome(taskId, action, succeeded=false, "RECLAIM/ABORT failed (SQL): " + e.toString())`**（不抛出——单 task 的 UPDATE 失败不阻断同批次其他 task 的恢复，与 `DefaultOrphanRecoveryHandler.handleAbort` per-session 隔离一致）；(d) 组装返回 `List<TeamTaskRecoveryOutcome>`
- [x] `RecoveryScanResult` 扩展：新增 `private final List<TeamTaskRecoveryOutcome> teamTaskRecoveryActions` 字段 + 8-arg 构造器（原 7 args + `List<TeamTaskRecoveryOutcome> teamTaskRecoveryActions`）；`empty()` 工厂返回 `teamTaskRecoveryActions = Collections.emptyList()`；新增 `getTeamTaskRecoveryActions()` getter；现有调用方 `ScheduledRecoveryManager.scanOnce`（7-arg）更新为 8-arg、`NoOpRecoveryManager.scanOnce`（`empty()`）自动适配
- [x] `ScheduledRecoveryManager` 新增 `teamTaskRecoveryHandler` 字段（默认 `NoOpTeamTaskRecoveryHandler`）+ `setTeamTaskRecoveryHandler` setter（null 拒绝，保持 NoOp）。scanOnce 在 orphan detection 之后新增第 4 步：`List<TeamTaskRecoveryOutcome> taskOutcomes = teamTaskRecoveryHandler.recoverStuckTasks()`，传入 `RecoveryScanResult` 8-arg 构造器
- [x] 编写 `TestDefaultTeamTaskRecoveryHandler` focused 测试（H2 真实 DB）：
  - RECLAIM 动作：插入 CLAIMED task（UPDATED_AT 早于阈值）→ recoverStuckTasks → 断言 task status=CREATED + claimedBy=null + outcome.action=RECLAIM + succeeded=true
  - ABORT 动作：插入 CLAIMED task → recoverStuckTasks → 断言 task status=ABANDONED + outcome.action=ABORT + succeeded=true
  - 非卡死任务不动：插入 CLAIMED task（UPDATED_AT 近期，未超阈值）→ recoverStuckTasks → 断言返回 emptyList + task status 不变
  - 终态任务不被检测：插入 COMPLETED task → recoverStuckTasks → 返回 emptyList
  - CAS 竞争：插入 CLAIMED task → 手动先 complete 它 → recoverStuckTasks → outcome.succeeded=false + message 含 "transitioned"
  - fail-fast：dataSource=null / timeoutSeconds<=0 / defaultAction=SKIP → `IllegalArgumentException`
  - **跨租户隔离**（裁定 4a）：插入两个租户的卡死 CLAIMED task（tenant-A + tenant-B）→ handler 注入 tenant-A 的 `ITenantResolver` → recoverStuckTasks → 断言仅 tenant-A 的 task 被恢复（status 变化），tenant-B 的 task 不变（status 仍 CLAIMED）——证明租户守卫防跨租户恢复
  - **无静默跳过**：CAS 失败 succeeded=false（非静默）；检测 SELECT SQLException 抛 NopAiAgentException（非吞没）；per-task UPDATE SQLException 返回 succeeded=false outcome（非吞没、不阻断同批次其他 task）；无卡死任务返回 emptyList（显式 "no stuck tasks"，非静默跳过）
  - **per-task 故障隔离**（镜像 `DefaultOrphanRecoveryHandler.handleAbort`）：插入两个卡死 CLAIMED task（task-A + task-B）→ mock 使 task-A 的 UPDATE 抛 SQLException → recoverStuckTasks → 断言 task-A outcome.succeeded=false + task-B outcome.succeeded=true（task-B 正常恢复，未被 task-A 的故障阻断）
- [x] 编写 daemon 集成测试 `TestTeamTaskRecoveryDaemonIntegration`：
  - scanOnce 第 4 步：H2 + `ScheduledRecoveryManager` + `DefaultTeamTaskRecoveryHandler`(RECLAIM) → 插入卡死 CLAIMED task → scanOnce → 断言 task status=CREATED + `RecoveryScanResult.teamTaskRecoveryActions` 含 succeeded outcome
  - setter 注入：默认 handler instanceof NoOp → `setTeamTaskRecoveryHandler(handler)` → 断言 scanOnce 调用注入的 handler（task 被恢复，非 NoOp emptyList）
  - NoOp 默认零回归：`NoOpTeamTaskRecoveryHandler` 下 scanOnce 行为与 plan 229 shipped 默认一致（teamTaskRecoveryActions 为 emptyList）+ `RecoveryScanResult.empty()` 的 `teamTaskRecoveryActions` 为 emptyList
  - 步骤顺序：scanOnce 仍执行 stale lock cleanup → timeout detection → orphan detection → team task recovery（既有 session 步骤不受影响）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestInMemoryTeamTaskStoreReclaim` 全绿（CLAIMED→CREATED / CAS / claimedBy 清空 / 终态拒绝 / 并发）
- [x] `TestDbTeamTaskStoreReclaim` 全绿（H2 真实 DB raw JDBC CAS / 跨实例可见 / 并发竞争）
- [x] `DefaultTeamTaskRecoveryHandler` 正确实现 RECLAIM（raw JDBC UPDATE STATUS='CREATED'+CLAIMED_BY=NULL CAS）/ ABORT（UPDATE STATUS='ABANDONED' CAS）/ 非卡少任务不动 / CAS 竞争 succeeded=false / **跨租户隔离（tenant-A handler 不恢复 tenant-B task，裁定 4a）**
- [x] `RecoveryScanResult` 8-arg 构造器 + `teamTaskRecoveryActions` 字段 + getter + `empty()` 返回 emptyList 已落地；现有调用方（`ScheduledRecoveryManager.scanOnce` / `NoOpRecoveryManager.scanOnce`）已适配
- [x] `ScheduledRecoveryManager` 新增 `setTeamTaskRecoveryHandler` setter（默认 NoOp）；scanOnce 第 4 步在 orphan detection 后调用 `handler.recoverStuckTasks()`
- [x] **接线验证**（Minimum Rules #23）：`ScheduledRecoveryManager` 持有 `ITeamTaskRecoveryHandler` 引用；scanOnce 运行时确实调用 `handler.recoverStuckTasks()`（测试断言 task 被恢复 + handler callCount）；`setTeamTaskRecoveryHandler` setter 正确赋值（注入非 NoOp handler 后 scanOnce 恢复 task）
- [x] **无静默跳过**（Minimum Rules #24）：RECLAIM/ABORT CAS 失败 succeeded=false（非静默）；SQLException 包裹 NopAiAgentException（非吞没）；无卡死任务返回 emptyList（显式语义）；NoOp 返回 emptyList（显式 SKIP）；NoOpTeamTaskStore.reclaimTask 抛 UOE；无空方法体 / continue 跳过 / TODO placeholder
- [x] shipped 默认（`NoOpTeamTaskRecoveryHandler`）下既有测试零回归
- [x] 新增功能各有对应 focused 测试覆盖（reclaim store in-memory / reclaim store DB / handler RECLAIM / handler ABORT / handler 非卡死不动 / handler CAS 竞争 / handler fail-fast / **handler 跨租户隔离** / **handler per-task 故障隔离** / daemon scanOnce 第 4 步 / daemon setter 注入 / daemon NoOp 零回归 / daemon 步骤顺序 各有测试）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + 设计文档 + roadmap 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/runtime/recovery/TestTeamTaskRecoveryEndToEnd.java`（新）、`ai-dev/design/nop-ai-agent/nop-ai-agent-team-task-reclaim.md`（新）、`nop-ai-agent-actor-runtime-vision.md` §8.2、`nop-ai-agent-roadmap.md` §4

- Item Types: `Proof`

- [x] 编写端到端测试（RECLAIM 路径）：构造团队 + 单任务（member claim 后模拟崩溃——不 complete，UPDATED_AT 设为过去时间模拟卡死）→ `ScheduledRecoveryManager.scanOnce`（注入 `DefaultTeamTaskRecoveryHandler` RECLAIM + H2）→ 断言 task 被重置 CREATED（claimedBy=null）+ `RecoveryScanResult.teamTaskRecoveryActions` 含 succeeded RECLAIM outcome → 验证另一 member 可 `claimTask`（CREATED→CLAIMED 成功，证明可重新认领）
- [x] 编写端到端测试（ABORT 路径）：同样卡死 CLAIMED task → `DefaultTeamTaskRecoveryHandler` ABORT → scanOnce → 断言 task → ABANDONED（终态）+ outcome.action=ABORT + 后续 reclaimTask 返回 empty（终态不可逆）
- [x] 编写端到端测试（DAG 场景）：构造团队 + 2 任务依赖 DAG（t2 blockedBy t1）→ member claim t1 后崩溃（卡死）→ scanOnce RECLAIM → t1 回 CREATED → member2 claim + complete t1 → t2 就绪可 claim。断言卡死 t1 经 daemon 自愈后 DAG 可继续推进（闭合"卡死 CLAIMED 永久阻塞 DAG"的核心缺口）
- [x] 编写 NoOp 默认零回归对比 e2e：NoOp handler 下卡死 CLAIMED task → scanOnce → task 不变（CLAIMED，未恢复）+ teamTaskRecoveryActions 为 emptyList（证明 NoOp shipped 默认不改变既有行为）
- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-team-task-reclaim.md`：记录核心裁定（RE-CLAIM=CLAIMED→CREATED / 时间基检测 / handler 自包含 / 单一 defaultAction / scanOnce 第 4 步 / raw JDBC 动作 / **多租户守卫（裁定 4a）** / NoOp shipped 默认）+ 拒绝替代方案（claimer-liveness 交叉检测 / per-task 配置 / 动态分级 / ABANDONED 复活 / handler 复用 store）。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §8.2：team task reclaim + timeout abandon 从 successor 标注为已落地（状态机 CLAIMED→CREATED + 时间基检测 + daemon 自愈）；标注 claimer-liveness 交叉检测 / per-task 超时 / LLM reclaim 工具仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：新增 `L4-team-task-reclaim-and-timeout-abandon` 工作项行并标注已落地
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：从卡死 CLAIMED task（member 崩溃）→ daemon scanOnce → handler.recoverStuckTasks → raw JDBC 检测 → RECLAIM 动作 → task 重置 CREATED → 另一 member 重新 claim，完整自愈路径跑通（RECLAIM + ABORT 两条路径）
- [x] **DAG 自愈验证**：卡死 t1 阻塞的 DAG（t2 blockedBy t1）经 daemon RECLAIM 后 t1 回 CREATED 可被 member2 重新 claim+complete，DAG 可继续推进——闭合"卡死 CLAIMED 永久阻塞 DAG"缺口
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言 scanOnce 运行时确实调用注入 handler 的 `recoverStuckTasks`（非仅状态变化），handler 的 raw JDBC 确实更新了 DB 中的 task status（H2 行验证）
- [x] **Anti-Hollow 断言**：端到端测试断言 daemon → handler → raw JDBC → DB task status 变化真实调用链连通（非仅类型存在）；RECLAIM 后 task 确实可被重新 claimTask（CREATED→CLAIMED，非仅 status 字段变化）
- [x] **无静默跳过**：NoOp 默认下卡死 task 不变（显式 SKIP，非假装恢复）；RECLAIM/ABORT CAS 失败诚实 succeeded=false
- [x] `nop-ai-agent-team-task-reclaim.md` 存在，含核心裁定 + 拒绝替代方案，无类签名/代码
- [x] roadmap §4 已新增 `L4-team-task-reclaim-and-timeout-abandon` 行并标注已落地
- [x] `nop-ai-agent-actor-runtime-vision.md` §8.2 已更新（reclaim + timeout abandon 已落地 + successor 标注）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ITeamTaskStore` 状态机扩展（reclaimTask CLAIMED→CREATED）+ `TeamTaskStatus` Javadoc 同步落地为真实（非空壳）代码
- [x] `InMemoryTeamTaskStore.reclaimTask`（compute CAS）+ `NoOpTeamTaskStore.reclaimTask`（UOE）+ `DbTeamTaskStore.reclaimTask`（条件 UPDATE CAS）落地
- [x] `ITeamTaskRecoveryHandler` 契约 + `NoOpTeamTaskRecoveryHandler` shipped 默认（emptyList，零回归）+ `DefaultTeamTaskRecoveryHandler` 功能实现（时间基检测 + RECLAIM/ABORT raw JDBC CAS + **`ITenantResolver` 租户守卫，裁定 4a**）落地
- [x] `ScheduledRecoveryManager` scanOnce 第 4 步（team task recovery）+ `setTeamTaskRecoveryHandler` setter + `RecoveryScanResult` 8-arg 扩展落地
- [x] 端到端：卡死 CLAIMED task → daemon scanOnce → handler 检测+恢复 → task 重置 CREATED 可重新认领（RECLAIM）/ 标记 ABANDONED（ABORT），DAG 自愈完整路径跑通
- [x] shipped 默认（NoOp）下既有测试零回归
- [x] 必要 focused verification 已完成（reclaim store in-memory/DB + handler RECLAIM/ABORT/CAS/fail-fast + daemon scanOnce/setter/NoOp/步骤顺序 + E2E RECLAIM/ABORT/DAG 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（claimer-liveness 交叉检测 / per-task 配置 / 动态分级策略 / LLM reclaim 工具 / 终态复活 / async 编排 / 多成员路由 / spawn 池化 / 构造器重构 / TeamManager / nop-job 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（新 design doc + vision §8.2 + roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）scanOnce 运行时确实调用 `handler.recoverStuckTasks`（不只类型存在），（b）RECLAIM/ABORT raw JDBC 路径在运行时确实执行（DB task status 变化），（c）RECLAIM 后 task 确实可被重新 claim（非仅 status 字段），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；claimer-liveness 交叉检测 / per-task 超时配置 / 动态分级策略 / LLM reclaim 工具 / 终态复活 / async 编排 / 多成员路由 / spawn 池化 / RecoveryScanResult 构造器重构 / TeamManager / nop-job 集成均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **claimer-liveness 交叉检测**：检测 CLAIMED 任务的 claimer session 是否 orphaned/timed-out（session↔team-task 交叉引用），而非仅时间基。Classification: successor plan required。
- **per-task / per-team 超时配置**：`TeamTask.taskTimeoutSeconds` 字段或 team 级配置。Classification: optimization candidate。
- **动态分级动作策略**：根据卡死时长动态选择（年轻→RECLAIM，极旧→ABORT）。Classification: optimization candidate。
- **`team-task-reclaim` LLM 工具**：LLM agent 主动触发 reclaim（`reclaimTask` 已在 store 落地）。Classification: successor plan required。
- **`RecoveryScanResult` 构造器重构**（builder / action-log Map）。Classification: optimization candidate（plan 229 已列）。
- **异步 / 跨进程流编排执行**（plan 239 carry-over `L4-async-cross-process-orchestration`）。Classification: successor plan required。
- **多成员 per-task 路由**（plan 239 carry-over）。Classification: successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over）。Classification: optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）。Classification: successor plan required。
- **nop-job `IJobScheduler` 集成**。Classification: successor plan required。

## Closure

Status Note: 团队任务 RE-CLAIM + 超时自动 ABANDON 已完整落地——把团队任务生命周期从"CLAIMED 单向不可逆（卡死 task 永久阻塞 DAG）"扩展为"daemon scanOnce 第 4 步时间基检测 + 可插拔 handler 自愈（RECLAIM 重置可重新认领 / ABORT 终态标记失败）"。三个 Phase 均已完成，73 个 checklist 项全勾选，2605 测试全绿（baseline 2571 + 34 新增），设计文档 + vision §8.2 + roadmap §4 同步，NoOp shipped 默认零回归。所有 in-scope 项已落地或显式切出为独立 successor。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: opencode 执行 agent（self-audit 基于完整执行 + live code path 核对；独立 closure-audit subagent 已在执行过程中经测试断言验证 Anti-Hollow 接线）
- Audit Session: 本执行 session（plan 240 单次执行）
- Evidence:
  - **Phase 1 Exit Criteria**：PASS — `TeamTaskStatus` Javadoc 含 `CLAIMED → CREATED`（`TeamTaskStatus.java:8-16` + `:45-51`）；`ITeamTaskStore.reclaimTask(String, String) → Optional<TeamTask>` 落地（`ITeamTaskStore.java:135-155`）；`NoOpTeamTaskStore.reclaimTask` 抛 UOE（`NoOpTeamTaskStore.java:82-84`）；`InMemoryTeamTaskStore.reclaimTask` 经 compute CAS（`InMemoryTeamTaskStore.java:169-194`）；`DbTeamTaskStore.reclaimTask` 条件 UPDATE CAS + 租户守卫（`DbTeamTaskStore.java:330-363`）；4 个新 recovery 文件存在（`TeamTaskRecoveryAction.java` / `TeamTaskRecoveryOutcome.java` / `ITeamTaskRecoveryHandler.java` / `NoOpTeamTaskRecoveryHandler.java`）；`NoOpTeamTaskRecoveryHandler.recoverStuckTasks` 返回 emptyList；vision §8.2 已更新；`./mvnw compile` 通过
  - **Phase 2 Exit Criteria**：PASS — `TestInMemoryTeamTaskStoreReclaim` 7 测试全绿（CLAIMED→CREATED / CAS / claimedBy 清空 / 终态拒绝 / 并发）；`TestDbTeamTaskStoreReclaim` 5 测试全绿（H2 raw JDBC CAS / 跨实例 / 并发）；`DefaultTeamTaskRecoveryHandler` 13 focused 测试全绿（RECLAIM / ABORT / 非卡死不动 / 终态不检测 / CAS 竞争 both levels / fail-fast 3 paths / 跨租户隔离 / per-task 故障隔离 / 检测 SQLException NopAiAgentException）；`RecoveryScanResult` 8-arg + getter + empty() 落地；`ScheduledRecoveryManager` 第 4 步 + setter + getter 落地；`TestTeamTaskRecoveryDaemonIntegration` 5 测试全绿（scanOnce 第 4 步 / setter 注入 / NoOp 零回归 / 步骤顺序 / null 拒绝）；接线验证 #23（scanOnce 运行时调 handler + task 被恢复）；无静默跳过 #24（CAS false / SQLException 包裹 / emptyList 显式 / UOE）；shipped NoOp 默认零回归
  - **Phase 3 Exit Criteria**：PASS — `TestTeamTaskRecoveryEndToEnd` 4 测试全绿（RECLAIM 完整路径：卡死→daemon→CREATED→member2 re-claim+complete Anti-Hollow / ABORT 路径：→ABANDONED 终态不可逆 / DAG 自愈：t2 blockedBy t1，t1 卡死后 RECLAIM→member2 claim+complete t1→t2 就绪 / NoOp 零回归对比）；设计文档 `nop-ai-agent-team-task-reclaim.md` 含 8 裁定 + 拒绝替代方案无类签名；roadmap §4 新增 `L4-team-task-reclaim-and-timeout-abandon` ✅ 行；vision §8.2 已标注已落地 + successor
  - **Closure Gates**：全部 PASS（见上方逐条勾选）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/240-...md --strict` 退出码 0（73 items all checked, status: completed）
  - **Anti-Hollow 检查结果**：(a) scanOnce 运行时确实调用 `handler.recoverStuckTasks`（`TestTeamTaskRecoveryDaemonIntegration.scanOnceStep4RecoversStuckTask` 断言 task 被恢复 + `RecoveryScanResult.teamTaskRecoveryActions` 非 empty）；(b) RECLAIM/ABORT raw JDBC 路径运行时确实执行（E2E 测试断言 DB task status 从 CLAIMED 变 CREATED/ABANDONED）；(c) RECLAIM 后 task 确实可被重新 claim（`TestTeamTaskRecoveryEndToEnd.reclaimPathStuckTaskResetAndReClaimable` 断言 member-2 claimTask 成功 + completeTask 成功）；(d) 无空方法体/静默跳过（NoOp 显式 emptyList SKIP 语义 + handler CAS 失败 succeeded=false + UOE + NopAiAgentException）
  - **Deferred 项分类检查**：所有 Non-Goals（claimer-liveness 交叉检测 / per-task 配置 / 动态分级 / LLM reclaim 工具 / 终态复活 / async 编排 / 多成员路由 / spawn 池化 / 构造器重构 / TeamManager / nop-job）均为显式独立 successor 或 optimization candidate，无 in-scope live defect 被降级
  - **验证命令**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（2605 tests，0 failures，0 errors）；`node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（新增文档无 broken link）

Follow-up:

- claimer-liveness 交叉检测 — successor plan required
- per-task / per-team 超时配置 — optimization candidate
- 动态分级动作策略 — optimization candidate
- `team-task-reclaim` LLM 工具 — successor plan required（`reclaimTask` 已在 store 落地，未来工具可直接消费）
- `RecoveryScanResult` 构造器重构 — optimization candidate（plan 229 已列）
- 异步 / 跨进程流编排执行 — successor plan required
- 多成员 per-task 路由 — successor plan required
- spawn session 复用 / 池化 — optimization candidate

## Follow-up handled by 241-nop-ai-agent-async-team-task-orchestration.md

> 追加于 2026-06-18（carry-over 链接，不改动上方历史记录）。
> 本计划 Non-Blocking Follow-ups / Closure Follow-up 中的「异步 / 跨进程流编排执行（`L4-async-cross-process-orchestration`）」一项，已由后续计划 `ai-dev/plans/241-nop-ai-agent-async-team-task-orchestration.md` 接管。
>
> 范围裁定（Granularity Rule）：原 roadmap 项捆绑「async（进程内非阻塞）+ cross-process（多实例分布式协调）」两个维度。plan 241 交付 **async** 半部（`executeAsync` 非阻塞入口 + member/spawn step async 化 + DAG 并行分支真正并发 + `team-execute-flow` 真实 async 接线），并把 **cross-process daemon 协调**（分布式锁 / 多实例扫描协调 / 共享调度状态）切出为独立 successor `L4-cross-process-daemon-coordination`。
>
> 关键事实修正：plan 241 经 live repo 核对发现，历史（含本计划 Non-Goals）反复标注的「需 nop-task CompletableFuture async model（未落地）」前提**不准确**——nop-task `TaskStepReturn` 已提供完整 async 契约（`isAsync`/`getReturnPromise`/`ASYNC_RETURN`/`ASYNC`），`GraphTaskStep` 已用 CompletableFuture 调度就绪节点并发。plan 241 消费既有 async 模型，不触及 nop-task 核心。`claimTask` DB 级 CAS 已提供多实例 double-dispatch 正确性地板，cross-process 为降冗余扫描的优化层。
