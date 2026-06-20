# 团队任务 RE-CLAIM + 超时自动 ABANDON 设计（闭合无人值守 DAG 任务生命周期自愈缺口）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/240-nop-ai-agent-team-task-reclaim-and-timeout-abandon.md`（Work Item: `L4-team-task-reclaim-and-timeout-abandon`）
> Related: `nop-ai-agent-actor-runtime-vision.md` §8.2（team task lifecycle）、`nop-ai-agent-task-scheduler-daemon.md`（plan 236 守护进程失败语义——失败任务诚实 abandon 供本设计消费）、`nop-ai-agent-task-flow-integration.md`（plan 233 DAG 编排——卡死 CLAIMED task 阻塞的 DAG 正是本设计闭合的缺口）

## 1. 定位

把 nop-ai-agent 的团队任务生命周期从"CLAIMED 单向不可逆——一个被成员认领后中途崩溃（session orphaned / timeout / 进程死亡）的任务永远卡在 CLAIMED，阻塞整个 task DAG 无法推进，也无超时自动收口"扩展为"RecoveryManager daemon 的 scanOnce 新增一步 team-task 卡死检测与恢复：对 `STATUS='CLAIMED'` 且 `UPDATED_AT` 超过配置阈值的任务，经可插拔 `ITeamTaskRecoveryHandler` 裁定动作——RE-CLAIM（CLAIMED→CREATED 重置为可重新认领）或 ABORT（CLAIMED→ABANDONED 终态标记失败）"。

这是 roadmap §4 Layer 4「无人值守多 Agent 自主编排」栈的**任务生命周期自愈缺口**闭合：plans 233→239 已交付同步编排 + blockedBy 自动调度守护 + auto-spawn + LLM 工具入口，但卡死的 CLAIMED 任务会永久阻塞 DAG，无人值守自动化定位不成立。本设计闭合此缺口——daemon 自愈使卡死 task 可被另一 member 重新认领（RECLAIM）或快速失败（ABORT），DAG 可继续推进。

团队任务持久化、状态机（CREATED→CLAIMED→COMPLETED / →ABANDONED）、RecoveryManager daemon、orphan/timeout handler 模式在本设计之前已由既有 plan（225 / 227 / 222 / 226 / 229 / 232）落地。本设计在其之上叠加 team-task 卡死检测 + 恢复，**不改既有契约**（reclaimTask 是 ITeamTaskStore 的新增方法，scanOnce 新增第 4 步 + handler 字段/setter，与 plan 226/229 扩展模式一致）。

## 2. 设计决策

### 决策 1：RE-CLAIM = `CLAIMED → CREATED`（重置可重新认领），非 `ABANDONED → CREATED`（终态复活）

reclaimTask 只作用于 CLAIMED（卡死态），重置为 CREATED + `claimedBy=null`，使任务可被任意 member 重新认领。ABANDONED/COMPLETED 为终态不可逆。

理由：
- 卡死问题的根因是 CLAIMED 任务持有者消失，重置到 CREATED 让其他 member 重新认领是最小恢复。
- 终态复活破坏状态机终态不变性（plan 227 已裁定 ABANDONED 为终态）。
- `claimTask` 语义为 `CREATED → CLAIMED`，reclaim 后任务回到 CREATED 即可走原 claim 流程。

### 决策 2：检测 = 时间基（`UPDATED_AT < now - threshold`），非 claimer-liveness 交叉检测

对 `ai_agent_team_task` 执行 `SELECT TASK_ID WHERE STATUS='CLAIMED' AND UPDATED_AT < ?`（阈值 = `now - taskTimeoutSeconds*1000`）。`UPDATED_AT` 在 CLAIMED 时即"认领时间"代理（plan 227 裁定 7）。

理由：
- 与 plan 229 session 超时检测一致（时间基）。
- 不误杀活跃长任务（仍在迭代的 member 会驱动 task 最终 complete，CLAIMED→COMPLETED 刷新 UPDATED_AT；只有真正卡死停止推进的才超时）。
- claimer-liveness 交叉检测需 session↔team-task 跨表 join + liveness 判定策略，是更重的独立 successor。
- 避免新 DDL（UPDATED_AT 已存在）。

### 决策 3：handler 自包含（`recoverStuckTasks()` 内部做检测 + 动作），scanOnce 仅调用此方法

与 plan 226/229 的"scanOnce 做 SELECT + handler 做 per-item 动作"模式不同——本设计 handler 内部完成全部 team-task 域逻辑（检测 + 裁定 + raw JDBC 动作）。scanOnce 一行调用 `handler.recoverStuckTasks()`，结果汇总到 `RecoveryScanResult`。

理由：
- team-task 是不同域表（`ai_agent_team_task`），scanOnce 当前只操作 session 域表（`ai_agent_session` / `ai_agent_session_lock`），将 team-task 检测逻辑封装在 handler 中保持 daemon 聚焦于 session 域。
- handler 是单一集成点（scanOnce 一行调用），team-task 表结构变更不波及 `ScheduledRecoveryManager`。
- NoOp shipped 默认（返回 emptyList）零 DB 访问零回归。
- handler 可独立测试（注入 H2 DataSource），无需经 scanOnce 时序。

这是对 plan 226/229 模式的**有意且裁定的偏差**。

### 决策 4：handler 动作 = raw JDBC UPDATE（RECLAIM → STATUS='CREATED',CLAIMED_BY=NULL / ABORT → STATUS='ABANDONED'），条件 WHERE STATUS='CLAIMED' CAS

与 plan 226 ABORT / plan 229 FORCE_FAILED 的 raw JDBC UPDATE 模式一致（handler 侧直接 UPDATE，不经 store 方法）。

理由：
- recovery 包既有约定——所有 handler 动作（ABORT session / FORCE_FAILED session）均 raw JDBC。
- 条件 WHERE STATUS='CLAIMED' 保证 CAS——若任务已被其他路径转换，affected-row=0 → succeeded=false（诚实，非静默）。
- reclaimTask store 方法独立存在（契约完整性 + in-memory 支持 + 未来 LLM 工具），handler 的 raw JDBC SQL 与 store 的 CAS SQL 语义等价（双路径产出相同结果）。两者不共享实现但语义一致（与 plan 226/229 handler 不复用 store 的既有模式一致）。

### 决策 4a：多租户隔离 — handler + DbTeamTaskStore.reclaimTask 统一经 `ITenantResolver` 注入租户守卫

`DefaultTeamTaskRecoveryHandler` 构造期接收 `ITenantResolver`（默认 `NullTenantResolver.INSTANCE`，与 `DefaultOrphanRecoveryHandler` / `DefaultSessionTimeoutHandler` 同一 opt-in 模式，plan 232）。所有 SQL（SELECT 检测 + UPDATE RECLAIM/ABORT）追加 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)`（经 `TenantSql.whereTenant(COL_TENANT_ID)`）。`DbTeamTaskStore.reclaimTask` 复用既有 `ITenantResolver` 字段 + 既有 `conditionalUpdate` 辅助方法注入同款守卫。

理由：
- plan 232 已确立 `ai_agent_team_task` 表的 `TENANT_ID` 列 + 全部 `DbTeamTaskStore` 操作的租户守卫为基线约定，新增 reclaimTask 不应打破。
- 省略将导致跨租户恢复（tenant A 的 daemon 重置/放弃 tenant B 的任务）。
- `NullTenantResolver.INSTANCE` 默认保证单租户部署零回归。

### 决策 5：单一 `defaultAction` 策略（RECLAIM 或 ABORT），所有卡死任务统一

不按卡死时长动态分级（年轻→RECLAIM，极旧→ABORT）。

理由：
- 首版最小可用——集成商按部署场景选择策略（开发/测试 → RECLAIM 重试友好；生产严格 → ABORT 快速失败）。
- 动态分级需双阈值 + 分级裁定逻辑，是独立产品策略决策。
- 与 plan 226 单一 RecoveryMode（整个 daemon 一个模式）一致。

### 决策 6：scanOnce 第 4 步置于 orphan detection 之后（最后）

步骤顺序：stale lock cleanup → timeout detection → orphan detection → **stuck team task recovery**。

理由：
- team-task 恢复独立于 session 恢复（不同域表，无数据依赖）。
- 置于最后最小化对既有 session 恢复步骤的干扰。
- 若 member session 被 orphan handler RESUME（恢复续跑），其 CLAIMED task 可能在 session 恢复后 complete——但本设计时间基检测只针对真正卡死（超阈值）的任务，恢复中的 session 的 task 不会超阈值（恢复会推进 task），故无冲突。

### 决策 7：NoOpTeamTaskRecoveryHandler 为 shipped 默认（返回 emptyList，零 DB 访问）

与 plan 222/226/229 的 NoOp 模式一致。集成商经 `manager.setTeamTaskRecoveryHandler(handler)` 注入功能性实现。

### 决策 8：`reclaimTask` 加入 `ITeamTaskStore` 契约（CLAIMED→CREATED），handler 不复用但 store 独立测试

理由：
- 状态机完整性——`CLAIMED → CREATED` 现在是合法转换，store 作为 canonical state machine 应支持。
- in-memory 团队支持 reclaim（测试场景）。
- 未来 `team-task-reclaim` LLM 工具可直接消费 store 方法。
- `TeamTaskStatus` Javadoc 需反映新转换消除 contract drift。
- handler 侧用 raw JDBC 是 recovery 包约定（决策 4），两者语义等价。

## 3. 失败隔离

`recoverStuckTasks` 采用 per-task 失败隔离（镜像 `DefaultOrphanRecoveryHandler.handleAbort` 的 per-session 隔离）：
- 检测 SELECT 的 SQLException → 包裹为 `NopAiAgentException` 抛出（检测失败 = 整步不可用，与 `ScheduledRecoveryManager.selectOrphanSessions` 一致）。
- per-task RECLAIM/ABORT UPDATE 的 SQLException → catch + 返回 `succeeded=false` outcome（不抛出——单 task 的 UPDATE 失败不阻断同批次其他 task 的恢复）。
- CAS 失败（task 已转换，affected=0）→ 返回 `succeeded=false` outcome（诚实，非静默）。

### 决策 9：claim-epoch CAS 绑定 complete/abandon（plan 279 / AR-01）

`completeTask` / `abandonTask`(CLAIMED 分支) 的 CAS 在 STATUS 之外**绑定 `CLAIM_EPOCH`**（claim 时 `COALESCE(CLAIM_EPOCH,0)+1` 在同一条 conditional UPDATE 内原子自增，不得用 `SELECT MAX+1` 后再 UPDATE 的 TOCTOU 形态）。调用方沿 claim→dispatch→execute→complete 透传 epoch。CAS 失败仍返回 `Optional.empty()`（非异常控制流，调用方转带上下文异常）。

**选了 `CLAIM_EPOCH`（方案 B）而非仅 `CLAIMED_BY`（方案 A）**：daemon 路径的 claim 与 complete 用同一身份 `DEFAULT_DAEMON_SESSION_ID`（多实例共享），reclaim 清 `CLAIMED_BY` 后实例 B 重新 claim 用**同一** daemon id → 仅 `AND CLAIMED_BY=?` 在共享 daemon id 下仍匹配，双重执行窗口不关闭。每次 claim 自增的 epoch 在 reclaim+re-claim 后产生**严格更大**的新 epoch，使旧在途 dispatcher 持有的旧 epoch 不再匹配 → CAS 失败。

**reclaim 保留 epoch（非置 NULL）**：这是关闭窗口的关键不变量。`reclaimTask` 清 `CLAIMED_BY` 但**保留** `CLAIM_EPOCH`，使下一次 claim 的 `COALESCE(prev,0)+1` 严格大于任何已废弃 claim 的 epoch。若 reclaim 置 epoch 为 NULL，则 `COALESCE(NULL,0)+1` 复位为 1，与旧在途 owner 的 epoch 重合 → 窗口重新打开。`DefaultTeamTaskRecoveryHandler` 的 raw-JDBC RECLAIM UPDATE 同样不动 epoch（双路径语义一致）。abandon 的 CREATED 分支因此为 epoch-agnostic（`STATUS='CREATED'`，无 owner 可绑定），匹配任意未认领 / 已 reclaim 的 CREATED 任务。

raw-JDBC `ai_agent_team_task` 表的幂等迁移：`CREATE TABLE IF NOT EXISTS` 不会给已部署表加列，故 `initSchema` 用 JDBC metadata 检测（**大小写不敏感**——各 RDBMS 存储未引号标识符的规范大小写不同：H2/Oracle 大写、MySQL-on-Linux/Postgres 小写）后按需 `ALTER TABLE ADD COLUMN CLAIM_EPOCH INTEGER`。

## 4. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| claimer-liveness 交叉检测（检测 CLAIMED 任务的 claimedBy session 是否 orphaned/timed-out） | 需 session↔team-task 跨表 join + liveness 判定策略，是更重的独立 successor。本设计时间基检测与 plan 229 一致，最小可用。 |
| per-task / per-team 超时配置 | 每个 task/team 独立超时阈值需 TeamTask 字段扩展 + 配置 precedence 裁定，是独立优化 successor。本设计为 handler 构造期全局 `taskTimeoutSeconds`。 |
| 动态分级动作策略（年轻→RECLAIM，极旧→ABORT） | 需双阈值 + 分级裁定逻辑，是独立产品策略决策（决策 5）。本设计为单一 `defaultAction`。 |
| ABANDONED → CREATED 终态复活 | 破坏状态机终态不变性（plan 227）。RECLAIM 只作用于 CLAIMED 非终态的卡死态（决策 1）。 |
| handler 复用 store 方法（不经 raw JDBC） | recovery 包既有约定——所有 handler 动作（ABORT session / FORCE_FAILED session）均 raw JDBC（决策 4）。store 的 reclaimTask 独立存在，两者语义等价。 |
| scanOnce 做 SELECT + handler 做 per-item 动作（镜像 plan 226/229） | team-task 是不同域表，封装在 handler 中保持 daemon 聚焦于 session 域（决策 3）。 |
| 仅 `AND CLAIMED_BY=?` 的 owner CAS（方案 A，plan 279 AR-01） | 共享 `DEFAULT_DAEMON_SESSION_ID` 场景下 reclaim→re-claim 后 CLAIMED_BY 相同，CAS 仍成功，双重执行窗口不关闭。已被 live code 证伪，仅可作 epoch 之外的附加校验。 |

## 5. 边界（Non-Goals，均为独立 successor）

- claimer-liveness 交叉检测 — successor。
- per-task / per-team 超时配置 — optimization candidate。
- 动态分级动作策略 — optimization candidate。
- `team-task-reclaim` LLM 工具（LLM agent 主动触发 reclaim）— successor（`reclaimTask` 已在 store 落地，未来工具可直接消费）。
- ABANDONED → CREATED 终态复活 — out-of-scope。
- 异步 / 跨进程流编排执行 — successor。
- 多成员 per-task 路由 — successor。
- spawn session 复用 / 池化 — optimization candidate。
- `RecoveryScanResult` 构造器重构（builder / action-log Map）— optimization candidate。

## 6. 落地证据

- 状态机扩展：`TeamTaskStatus` Javadoc 含 `CLAIMED → CREATED` 转换 + `ITeamTaskStore.reclaimTask(String, String) → Optional<TeamTask>` 契约 + `InMemoryTeamTaskStore`（compute CAS）+ `NoOpTeamTaskStore`（UOE）+ `DbTeamTaskStore`（条件 UPDATE CAS + 租户守卫）三实现同步。
- handler 契约 + 实现：`TeamTaskRecoveryAction`（RECLAIM/ABORT/SKIP）+ `TeamTaskRecoveryOutcome`（taskId/action/succeeded/message）+ `ITeamTaskRecoveryHandler`（`recoverStuckTasks() → List<TeamTaskRecoveryOutcome>`，自包含）+ `NoOpTeamTaskRecoveryHandler` shipped 默认（emptyList，零 DB 访问）+ `DefaultTeamTaskRecoveryHandler` functional 实现（时间基检测 + RECLAIM/ABORT raw JDBC CAS + `ITenantResolver` 租户守卫 + per-task 故障隔离）。
- daemon 集成：`ScheduledRecoveryManager` 新增 `teamTaskRecoveryHandler` 字段（默认 NoOp）+ `setTeamTaskRecoveryHandler`/`getTeamTaskRecoveryHandler` + scanOnce 第 4 步（orphan detection 之后）。
- `RecoveryScanResult` 8-arg 扩展（`teamTaskRecoveryActions` 字段 + getter + `empty()` 适配）。
- 端到端验证：RECLAIM 路径（卡死 CLAIMED → daemon → CREATED → 另一 member 重新 claim+complete）+ ABORT 路径（→ ABANDONED 终态不可逆）+ DAG 自愈（t2 blockedBy t1，t1 卡死后 daemon RECLAIM → member2 claim+complete t1 → t2 就绪可 claim）+ NoOp 零回归对比。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿。
