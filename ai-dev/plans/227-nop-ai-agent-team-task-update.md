# 227 nop-ai-agent team-task-update 任务状态机 + DB-backed 共享任务表（raw JDBC CAS 认领）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-team-task-update

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/225-nop-ai-agent-team-communication-tools.md`（Non-Blocking Follow-ups 第一条：`team-task-update 工具（vision §8.2）：任务认领（CLAIMED）/ 完成（COMPLETED）/ 放弃（ABANDONED）状态机 + DB 乐观锁 CAS 认领。Classification: successor plan required` + 第二条：`DB-backed 团队任务持久化（vision §8.2 "DB 事务保护的团队级共享任务表"）：nop_ai_team_task ORM 实体 + raw JDBC 持久化 + 跨进程共享。Classification: successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §8.2（团队通信模型：`team-task-update(taskId, status) → 成员认领/完成/放弃任务 (DB 乐观锁)`）+ §8.3（关键差异表：任务认领 = DB 乐观锁 + CAS）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 254（"多 Agent 任务可以通过 Flow / Task 组织"——team-task-update 状态机 + DB-backed 共享任务表是闭合该验收的关键能力）
> Related: `225`（交付 `ITeamTaskStore` 契约 + `InMemoryTeamTaskStore` + `NoOpTeamTaskStore` + `TeamTask`/`TeamTaskStatus` 数据对象 + team-task-create 工具，本计划在其上新增状态机 + DB 持久化）、`221`（交付 `DbSessionTakeoverLock` + `AiAgentSessionLockTable`——本计划 DB 持久化层直接遵循其 raw JDBC + 条件 UPDATE CAS + 构造期 `initSchema` 模式）、`223`（交付 `ITeamManager` + `InMemoryTeamManager`，team-task-update 工具经 `AgentToolExecuteContext.getTeamManager()` 反查调用者所属团队）

## Purpose

把 nop-ai-agent 的团队任务协同从"只能创建 `CREATED` 状态任务、in-memory 不跨进程、无认领/完成/放弃语义"扩展为"LLM 可经 `team-task-update` 工具驱动任务全生命周期（claim → complete / abandon），任务状态经 DB 共享表跨进程可见，并发认领由 DB 条件 UPDATE CAS 保证至多一个认领者胜出"。本计划闭合 roadmap §4 Layer 4 验收标准"多 Agent 任务可以通过 Flow / Task 组织"的核心能力：任务可被认领、可推进到完成、可跨进程共享。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **`ITeamTaskStore` 契约仅含 create + 3 个读**（plan 225 ✅）：`createTask` / `getTask` / `getTasksByTeam` / `getTasksByCreator`。**无任何状态转换方法**（claim/complete/abandon 不存在）。`io.nop.ai.agent.team.ITeamTaskStore`。
- **`TeamTask` 不可变数据对象**（plan 225 ✅）：字段 taskId / teamId / subject / description / blockedBy(List<String>) / status(TeamTaskStatus) / createdBy / createdAt，全参构造 + getter，无 setter，无 `claimedBy` 字段。`io.nop.ai.agent.team.TeamTask`。**注意既有 8 参构造调用点**：`InMemoryTeamTaskStore.createTask`（生产代码）+ `TestInMemoryTeamTaskStore` 私有 helper `t1()`（`TestInMemoryTeamTaskStore.java:205-208`，直接 `new TeamTask("x","t","s",null,Collections.emptyList(),TeamTaskStatus.CREATED,"c",0L)`）——本计划扩展全参构造时这两处调用点都必须同步更新，否则编译失败。
- **`TeamTaskStatus` 枚举已预置 4 态**（plan 225 ✅）：`CREATED` / `CLAIMED` / `COMPLETED` / `ABANDONED`，Javadoc 明确状态转换图（`CREATED → CLAIMED → COMPLETED`，`CLAIMED → ABANDONED`），且明确 CLAIMED/COMPLETED/ABANDONED 为本 successor 预留。`io.nop.ai.agent.team.TeamTaskStatus`。
- **`InMemoryTeamTaskStore` ConcurrentHashMap 双索引**（plan 225 ✅）：taskId 主索引 + teamId 副索引，createTask 生成 UUID + `putIfAbsent` + teamIndex `compute`。**无状态转换实现**。
- **`NoOpTeamTaskStore` shipped 默认**（plan 225 ✅）：`createTask` 抛 `UnsupportedOperationException`（Minimum Rules #24），读返回 empty。**无状态转换方法**。
- **`team-task-create` 工具已交付**（plan 225 ✅）：`TeamTaskCreateExecutor`（TOOL_NAME=`"team-task-create"`）经 `AgentToolExecuteContext.getTeamTaskStore()` 创建任务。NoOp 诚实报告模式已确立。
- **`AgentToolExecuteContext` 已携带 teamManager + teamTaskStore**（plan 225 ✅）：`getTeamManager()` / `getTeamTaskStore()` 已存在。**team-task-update 工具无需新增 context 字段**——直接复用既有 `getTeamTaskStore()`。
- **`DefaultAgentEngine.teamTaskStore` 字段 + `setTeamTaskStore`（null-safe 回退 NoOp）已存在**（plan 225 ✅，`DefaultAgentEngine.java:299` / `:1326`）。**无需新增引擎 DataSource 字段**——DB-backed store 由集成商显式构造并经 `setTeamTaskStore` 注入（与 `setSessionTakeoverLock(new DbSessionTakeoverLock(dataSource))` 同一 opt-in 模式）。
- **raw JDBC + Table 常量类 + 构造期 `initSchema` 模式已确立**（plan 221 ✅）：`AiAgentSessionLockTable`（DDL 常量 + 列名常量，`CREATE TABLE IF NOT EXISTS`）+ `DbSessionTakeoverLock`（`DataSource` + `PreparedStatement`，构造期 `initSchema()` 自动建表）。模块内所有 DB 持久化均遵循 raw JDBC：`DBSessionStore` / `DBMessageService` / `DBDenialLedger` / `DBCheckpointManager` / `DBUsageRecorder` / `DbModelSwitchedMessageWriter` / `DefaultOrphanRecoveryHandler`（ABORT 模式 raw JDBC UPDATE）。
- **条件 UPDATE CAS 模式已确立**（plan 221 ✅）：`DbSessionTakeoverLock.tryAcquire` 的 conditional `UPDATE ... WHERE SESSION_ID=? AND (LOCK_OWNER=? OR LOCK_EXPIRES_AT<=?)`，以 affected-row-count（`executeUpdate()==1`）判定 CAS 成败。
- **DB 测试模式已确立**（plan 221 ✅）：`TestDbSessionTakeoverLock` 经 `io.nop.dao.jdbc.datasource.SimpleDataSource` + H2 in-memory（`jdbc:h2:mem:test-...;DB_CLOSE_DELAY=-1`），`CoreInitialization.initializeTo(...)` 初始化。
- **零 team-task-update 代码**：grep `team-task-update|TeamTaskUpdate|UpdateTeamTask|ai_agent_team_task|DbTeamTaskStore|AiAgentTeamTaskTable` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中。`ai-agent-tools.beans.xml` 注册 8 个工具，无 team-task-update。
- **roadmap §4 Layer 4 验收标准 line 254**："多 Agent 任务可以通过 Flow / Task 组织"标注为"团队通信工具 foundational 已落地"，但完整 Flow/Task 组织能力（team-task-update 状态机 + DB-backed 共享任务表）仍为显式 successor。

## Goals

- **`team-task-update` IToolExecutor**（TOOL_NAME = `"team-task-update"`）：参数 `taskId`（必填）+ `action`（必填，枚举 claim/complete/abandon）。经 `AgentToolExecuteContext.getTeamTaskStore()` 驱动状态转换。遵循 `TeamTaskCreateExecutor` 的 NoOp 诚实报告 + `resolveArguments`/`getStringArg` 参数解析模式。
- **`ITeamTaskStore` 契约扩展**：新增 3 个状态转换方法，每个返回 `Optional<TeamTask>`（转换后的任务；CAS 失败/状态不合法时返回 `Optional.empty()`，非异常控制流）：
  - `claimTask(taskId, claimedBy)` — `CREATED → CLAIMED`，记录 claimedBy
  - `completeTask(taskId, completedBy)` — `CLAIMED → COMPLETED`
  - `abandonTask(taskId, abandonedBy)` — `CLAIMED → ABANDONED` 或 `CREATED → ABANDONED`
- **`TeamTask` 数据对象扩展**：新增 `claimedBy`（String，nullable——claim 前为 null，claim 后记录认领者 sessionId），全参构造追加该参数，既有构造路径传入 null 保持向后兼容。
- **`InMemoryTeamTaskStore` 状态转换实现**：经 `ConcurrentHashMap.compute(taskId, ...)` 原子 CAS（校验当前 status 合法 → 替换为新 `TeamTask` 含新 status + claimedBy）。
- **`NoOpTeamTaskStore` 状态转换实现**：3 个转换方法抛 `UnsupportedOperationException`（与 `createTask` 一致，Minimum Rules #24）。
- **DB-backed 共享任务表**：`AiAgentTeamTaskTable`（DDL + 列名常量，`CREATE TABLE IF NOT EXISTS ai_agent_team_task`，构造期 `initSchema` 自动建表）+ `DbTeamTaskStore`（raw JDBC：INSERT createTask / SELECT 读 / 条件 UPDATE 状态转换 CAS = affected-row-count 判定）。
- **`ai-agent-tools.beans.xml`** 注册 team-task-update bean。
- **NoOp shipped 默认零回归**：NoOpTeamTaskStore + NoOpTeamManager 下，team-task-update 诚实报告"团队功能未启用"，既有全量测试零回归。
- **端到端验证**（Anti-Hollow #22）：lead agent 经 team-task-create 建任务 → member agent 经 team-task-update claim → member agent 经 team-task-update complete → 测试经 `taskStore.getTask(t1)` 验证 status=COMPLETED（`TeamStatusExecutor` 仅返回 `taskCount` 整数，不暴露 per-task 状态，故 per-task COMPLETED 断言经 store 直接查询验证；team-status 仍可调用以断言 taskCount 反映任务存在）。DB-backed store 下跨"两个 store 实例共享同一 H2"验证 CAS 认领竞争。
- roadmap §4 Layer 4 验收标准"多 Agent 任务可以通过 Flow / Task 组织"升级为"team-task-update 状态机 + DB-backed 共享任务表已落地"。

## Non-Goals

- **Team ACL 强制**（vision §5.1）：team-task-update 不做权限检查（任何团队成员可认领/完成/放弃任何任务）。角色权限矩阵（LEAD=ADMIN / MEMBER=READ+WRITE+EXECUTE）+ 权限派生 + 权限拦截是独立 successor plan required（plan 223/225 Non-Goal）。本计划状态机只校验状态转换合法性，不校验"谁"有权转换。
- **`blockedBy` 依赖解析引擎**（plan 225 follow-up）：`blockedBy` 在 `ai_agent_team_task` 表中存储为字符串列，但本计划不实现依赖阻塞检查（不阻止创建被阻塞的任务、不自动调度就绪任务）。依赖解析是 successor plan required（属任务调度范畴）。
- **nop-task DAG 集成**：将团队任务映射为 nop-task 工作流 DAG 节点。Classification: successor plan required（依赖 nop-task 模块集成裁定）。
- **DB-backed 团队（team）持久化**（vision §4.2 `@BizModel("AiTeam")` + ORM 实体）：本计划只持久化 `ai_agent_team_task` 任务表，不持久化团队（team/member）本身。团队注册表仍是 `InMemoryTeamManager`。team 表 DB 持久化是独立 successor plan required（plan 223 follow-up）。
- **任务重新认领（RE-CLAIM）**：`ABANDONED` 终态任务是否允许回到 `CREATED` 重新认领。`TeamTaskStatus` Javadoc 标注"May be re-claimable in a successor slice"。本计划 ABANDONED 为终态，RE-CLAIM 是 successor。
- **任务过期/超时自动 ABANDONED**：被认领但长期未完成的任务自动转 ABANDONED。需要超时策略 + 定时扫描（类似 RecoveryManager）。Classification: successor plan required。
- **任务结果/产物存储**：complete 时记录任务产出（文件路径、结果摘要）。本计划 complete 只标记状态，不存储产物。Classification: successor plan required。
- **自动团队绑定 / TeamSpec XDSL / 跨进程团队消息路由 / ResourceGuard 配额**：均为 plan 225 显式 successor，本计划不触及。
- **独立的 VERSION 乐观锁列**（见 Design Decisions 裁定 4：状态转换 CAS 经条件 UPDATE on STATUS 实现，affected-row-count 判定，无需冗余 VERSION 列）。

## Scope

### In Scope

- `io.nop.ai.agent.team` 包：
  - `TeamTask.java` — 扩展：新增 `claimedBy` 字段 + getter，全参构造追加参数
  - `TeamTaskStatus.java` — 扩展：状态机转换图 Javadoc 补充 `CREATED → ABANDONED` 转换（与 Design Decisions 裁定 2 一致），CREATED/ABANDONED Javadoc 标注新增的合法转换
  - `ITeamTaskStore.java` — 扩展：新增 `claimTask` / `completeTask` / `abandonTask` 3 个状态转换方法
  - `InMemoryTeamTaskStore.java` — 扩展：实现 3 个状态转换（`ConcurrentHashMap.compute` CAS）；createTask 构造 TeamTask 时传 `claimedBy=null`
  - `NoOpTeamTaskStore.java` — 扩展：3 个转换方法抛 UOE
  - `AiAgentTeamTaskTable.java` — 新文件：`ai_agent_team_task` 表 DDL + 列名常量
  - `DbTeamTaskStore.java` — 新文件：raw JDBC 实现 ITeamTaskStore 全部方法（create/get/transition）
- `io.nop.ai.agent.tool` 包：
  - `TeamTaskUpdateExecutor.java` — 新文件：TOOL_NAME = `"team-task-update"`
- `ai-agent-tools.beans.xml` — 注册 team-task-update bean
- 既有测试同步更新：`TestInMemoryTeamTaskStore.java` 私有 helper `t1()`（`:205-208`）由 8 参构造更新为 9 参构造（追加 `null` claimedBy），使既有 create/get 测试在新构造签名下继续编译通过
- 测试文件：
  - `TestInMemoryTeamTaskStoreTransitions.java` — 状态机 + CAS 并发（claim 成功/重复 claim 失败/complete 需 CLAIMED/abandon 两源/终态拒绝转换/claimedBy 记录）
  - `TestTeamTaskUpdateExecutor.java` — NoOp 诚实报告 + claim/complete/abandon 三动作 + CAS 失败诚实报告 + 调用者不在团队
  - `TestDbTeamTaskStore.java` — H2 真实 DB（建表/CRUD/条件 UPDATE CAS：两个 store 实例竞争同一 taskId 只有 1 个 claim 成功/终态拒绝/跨实例可见）
  - `TestTeamTaskUpdateEndToEnd.java` — 端到端（lead create → member claim → member complete → lead team-status 见 COMPLETED；DB-backed 路径）

### Out Of Scope

- `TeamAclEntry` / 角色权限矩阵（Non-Goal: Team ACL）
- `blockedBy` 依赖阻塞检查引擎（Non-Goal: 依赖解析）
- nop-task DAG 节点映射（Non-Goal: nop-task 集成）
- `ai_agent_team` / `ai_agent_team_member` 表（Non-Goal: 团队 DB 持久化）
- ABANDONED → CREATED RE-CLAIM（Non-Goal: 重新认领）
- VERSION 列（Non-Goal: 冗余乐观锁列，裁定 4）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **持久化方案 = raw JDBC + Table 常量类（非 ORM 实体）**。NEXT_ITEM 与 plan 225 follow-up 措辞为"nop_ai_team_task ORM 实体"，但模块内既有 DB 持久化全部为 raw JDBC（`DBSessionStore` / `DbSessionTakeoverLock` / `DBMessageService` / `DBDenialLedger` / `DBCheckpointManager` / `DBUsageRecorder` / `DefaultOrphanRecoveryHandler`），`app.orm.xml` 中的 `AiAgentSession` 实体并非 `DBSessionStore` 实际使用路径。"ORM 实体"是 vision 层方向描述，实施层遵循已验证的 raw JDBC + `AiAgentTeamTaskTable` 常量类 + 构造期 `initSchema()` 自动建表模式（直接镜像 `AiAgentSessionLockTable` + `DbSessionTakeoverLock`）。表名 `ai_agent_team_task`。理由：(1) 与模块 DB 持久化约定 100% 一致；(2) raw JDBC 无 ORM 依赖，`nop-ai-agent` 无需引入 DAO/codegen 管线；(3) 构造期自动建表使集成商无需手工 DDL。

2. **状态机转换语义（与 `TeamTaskStatus` Javadoc 转换图一致）**：
   - `claimTask`：仅 `CREATED → CLAIMED`。当前非 CREATED 返回 empty（已被认领/已完成/已放弃）。
   - `completeTask`：仅 `CLAIMED → COMPLETED`。当前非 CLAIMED 返回 empty。
   - `abandonTask`：`CLAIMED → ABANDONED` 或 `CREATED → ABANDONED`（lead 可放弃未认领任务）。当前为终态（COMPLETED/ABANDONED）返回 empty。
   - 非法转换返回 `Optional.empty()`（CAS 失败语义，非异常控制流——调用方据此向 LLM 诚实报告"任务当前状态不允许该操作"）。理由：与 `DbSessionTakeoverLock.tryAcquire` 返回 boolean（非异常）的 CAS 控制流一致；工具层把 empty 转为诚实错误结果而非抛异常中断 ReAct 循环。

3. **乐观锁 CAS = 条件 UPDATE on (TASK_ID + 期望当前 STATUS)，affected-row-count==1 判定成功**。DB claim：`UPDATE ai_agent_team_task SET STATUS='CLAIMED', CLAIMED_BY=?, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CREATED'`。complete：`... SET STATUS='COMPLETED' WHERE TASK_ID=? AND STATUS='CLAIMED'`。abandon：`... SET STATUS='ABANDONED' WHERE TASK_ID=? AND STATUS IN ('CREATED','CLAIMED')`。理由：(1) 每个转换都改变 STATUS，STATUS 列本身就是乐观锁 guard——DB 行级 UPDATE 原子性保证至多一个 claimer 把 CREATED 改成 CLAIMED（第二个 UPDATE 找到 STATUS='CLAIMED' 影响 0 行）；(2) 与 `DbSessionTakeoverLock` 条件 UPDATE CAS 同一已验证模式；(3) 无需冗余 VERSION 列（裁定 4）。in-memory CAS 经 `ConcurrentHashMap.compute` 原子校验+替换实现等价语义。

4. **不引入独立 VERSION 乐观锁列**。状态机每次转换都改变 STATUS，条件 UPDATE on STATUS 已提供 CAS 正确性。VERSION 列适用于"不改 STATUS 的并发更新"（如重分配），当前状态机无此需求。RE-CLAIM / 重分配 successor 若引入不改 STATUS 的更新，届时再加 VERSION 列。

5. **team-task-update 工具参数 = `taskId` + `action`**。`action` 取值 claim / complete / abandon（字符串，大小写不敏感），映射到 3 个 store 方法。工具从 `agentCtx.getSessionId()` 反查所属团队（经 `teamManager.getTeamBySession`，与 `TeamTaskCreateExecutor` 同一路径）校验 taskId 属于调用者团队（`task.teamId == team.teamId`），跨团队操作返回错误。NoOp 诚实报告短路在最前（teamManager/taskStore 为 NoOp 时返回"团队功能未启用"，遵循 `TeamTaskCreateExecutor` DD#5）。CAS 失败（store 返回 empty）转为诚实错误结果（status="success" 但 output 说明"任务 {taskId} 当前状态为 {currentStatus}，不允许 {action}"——给 LLM 看的策略反馈，非异常）。

6. **`claimedBy` 由 claim 动作写入，complete/abandon 不改 claimedBy**。claim 记录认领者 sessionId（供 team-status 展示谁在做、供未来 ACL）。complete/abandon 保留已记录的 claimedBy（不改写），便于审计追溯。`TeamTask` 新增 `claimedBy` 字段（nullable），全参构造追加该参数；`InMemoryTeamTaskStore.createTask` 构造时传 null。

7. **`updated_at` 列记录最近转换时间**。`ai_agent_team_task` 表含 `CREATED_AT`（create 时写入）+ `UPDATED_AT`（每次状态转换更新）。便于观测/审计。`TeamTask` 数据对象不新增 updatedAt（保持数据对象精简；转换时间在 DB 层记录，读回时若需可后续扩展）。

### Phase 1 - ITeamTaskStore 状态机契约 + in-memory 实现 + team-task-update 工具 + focused 测试

Status: completed
Targets: `io.nop.ai.agent.team`（TeamTask / ITeamTaskStore / InMemoryTeamTaskStore / NoOpTeamTaskStore 扩展）、`io.nop.ai.agent.tool`（TeamTaskUpdateExecutor 新文件）、`ai-agent-tools.beans.xml`

- Item Types: `Fix`（team-task-update 状态机 gap = plan 225 carry-over）、`Proof`

- [x] `TeamTask` 新增 `claimedBy`（String，nullable）字段 + `getClaimedBy()` getter；全参构造追加 `claimedBy` 参数（位于 createdAt 前）；既有调用点（`InMemoryTeamTaskStore.createTask` + `TestInMemoryTeamTaskStore` 私有 helper `t1()`）同步更新（后者追加 `null` claimedBy，保持既有 create/get 测试编译通过）
- [x] `TeamTaskStatus` 状态机转换图 Javadoc 补充 `CREATED → ABANDONED` 转换（与裁定 2 abandonTask 的 `CREATED → ABANDONED` 源一致），消除 contract surface（enum 图）与 contract semantics（store 转换）的 drift（Minimum Rules #11）
- [x] `ITeamTaskStore` 新增 3 个状态转换方法：`Optional<TeamTask> claimTask(String taskId, String claimedBy)` / `Optional<TeamTask> completeTask(String taskId, String completedBy)` / `Optional<TeamTask> abandonTask(String taskId, String abandonedBy)`，Javadoc 明确合法转换 + CAS-empty 语义（裁定 2）
- [x] `NoOpTeamTaskStore` 实现 3 个转换方法：均抛 `UnsupportedOperationException("NoOpTeamTaskStore: team task store is not enabled")`（与 createTask 一致，Minimum Rules #24）
- [x] `InMemoryTeamTaskStore` 实现 3 个转换：经 `tasks.compute(taskId, (k, v) -> ...)` 原子校验当前 status 合法 → 构造新 `TeamTask`（继承既有字段 + 新 status + claimedBy）替换；非法转换返回 `Optional.empty()`；task 不存在返回 empty
- [x] 实现 `TeamTaskUpdateExecutor`（TOOL_NAME = `"team-task-update"`）：裁定 5 路由 + NoOp 诚实报告 + `resolveArguments`/`getStringArg`（遵循 `TeamTaskCreateExecutor` 模式）+ action 大小写不敏感解析 + CAS-empty 诚实错误结果
- [x] `ai-agent-tools.beans.xml` 注册 `<bean id="ai-agent-tools:team-task-update" class="io.nop.ai.agent.tool.TeamTaskUpdateExecutor"/>`
- [x] 编写 `TestInMemoryTeamTaskStoreTransitions`：claim 成功（CREATED→CLAIMED + claimedBy 记录）/ 重复 claim 失败（empty）/ complete 需 CLAIMED（CREATED 直接 complete 返回 empty）/ complete 成功（CLAIMED→COMPLETED）/ abandon 从 CLAIMED / abandon 从 CREATED / 终态（COMPLETED/ABANDONED）拒绝任何转换 / task 不存在返回 empty / 并发 claim 只有一个胜出（多线程 `claimTask` 同一 taskId，断言仅 1 个 non-empty）
- [x] 编写 `TestTeamTaskUpdateExecutor`：(a) NoOp teamManager/taskStore 下诚实报告"团队功能未启用"、(b) claim 成功返回 status=CLAIMED + claimedBy、(c) complete 成功返回 status=COMPLETED、(d) abandon 成功返回 status=ABANDONED、(e) CAS 失败（重复 claim）诚实报告当前状态不允许、(f) 跨团队 taskId 操作返回错误、(g) 调用者不在团队返回错误、(h) action 大小写不敏感

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TeamTask.claimedBy` 字段 + getter 存在；`InMemoryTeamTaskStore.createTask` 构造的 TeamTask claimedBy 为 null
- [x] `TestInMemoryTeamTaskStore` 私有 helper `t1()` 已更新为新构造签名（既有 create/get 测试在新构造下编译通过）
- [x] `TeamTaskStatus` 状态机转换图 Javadoc 包含 `CREATED → ABANDONED`（与 abandonTask 实现一致，无 contract drift）
- [x] `ITeamTaskStore` 含 claimTask/completeTask/abandonTask 3 个方法，返回 `Optional<TeamTask>`
- [x] `NoOpTeamTaskStore` 3 个转换方法抛 `UnsupportedOperationException`（**无静默跳过** #24）
- [x] `InMemoryTeamTaskStore` 3 个转换经 `compute` 原子 CAS，非法转换返回 empty
- [x] `TeamTaskUpdateExecutor.java` 存在于 `io.nop.ai.agent.tool` 包，TOOL_NAME = `"team-task-update"`
- [x] team-task-update 在 `ai-agent-tools.beans.xml` 注册
- [x] **接线验证**（Minimum Rules #23）：focused 测试断言工具经 `AgentToolExecuteContext.getTeamTaskStore()` 访问到功能性 store（非 NoOp），且 claim/complete/abandon 实际改变了 store 中的 task status
- [x] **无静默跳过**（Minimum Rules #24）：NoOp 分支返回诚实消息（非空方法体）；CAS 失败返回诚实错误（非静默成功）；NoOpTeamTaskStore 转换抛 UOE（非返回 empty 假装成功）
- [x] `TestInMemoryTeamTaskStoreTransitions` + `TestTeamTaskUpdateExecutor` 全绿
- [x] No owner-doc update required（owner doc 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DB-backed 共享任务表（raw JDBC CAS）

Status: completed
Targets: `io.nop.ai.agent.team`（AiAgentTeamTaskTable + DbTeamTaskStore 新文件）

- Item Types: `Fix`（DB-backed 任务持久化 gap = plan 225 carry-over）、`Proof`

- [x] 新建 `AiAgentTeamTaskTable`：`TABLE_NAME = "ai_agent_team_task"` + 列名常量（TASK_ID / TEAM_ID / SUBJECT / DESCRIPTION / BLOCKED_BY / STATUS / CREATED_BY / CLAIMED_BY / CREATED_AT / UPDATED_AT）+ `DDL_CREATE_TABLE`（`CREATE TABLE IF NOT EXISTS ... PRIMARY KEY (TASK_ID)`），镜像 `AiAgentSessionLockTable` 结构
- [x] 新建 `DbTeamTaskStore implements ITeamTaskStore`：构造期接收 `DataSource`，`initSchema()` 自动建表（镜像 `DbSessionTakeoverLock`）；实现全部 7 个方法：
  - `createTask`：INSERT（生成 UUID taskId，STATUS='CREATED'，CREATED_AT=UPDATED_AT=now，CLAIMED_BY=null）
  - `getTask`：SELECT by TASK_ID，行→TeamTask 映射（BLOCKED_BY 字符串列按逗号 split 还原 List，空值→空列表）
  - `getTasksByTeam`：SELECT by TEAM_ID，逐行映射
  - `getTasksByCreator`：SELECT by CREATED_BY，逐行映射
  - `claimTask`：裁定 3 条件 UPDATE（`SET STATUS='CLAIMED', CLAIMED_BY=?, UPDATED_AT=? WHERE TASK_ID=? AND STATUS='CREATED'`），affected-row==1 后 SELECT 回读返回更新后的 TeamTask，否则 empty
  - `completeTask`：条件 UPDATE（`WHERE TASK_ID=? AND STATUS='CLAIMED'`），affected-row 判定 + 回读
  - `abandonTask`：条件 UPDATE（`WHERE TASK_ID=? AND STATUS IN ('CREATED','CLAIMED')`），affected-row 判定 + 回读
- [x] 编写 `TestDbTeamTaskStore`（H2 真实 DB，`SimpleDataSource`，镜像 `TestDbSessionTakeoverLock` setUp/tearDown）：(a) 构造期自动建表（无需手工 DDL）、(b) createTask + getTask 回读字段一致（含 BLOCKED_BY 还原）、(c) getTasksByTeam / getTasksByCreator、(d) **CAS 认领竞争**：两个独立 `DbTeamTaskStore` 实例共享同一 H2 DataSource，对同一 taskId 并发 claimTask，断言仅 1 个返回 non-empty（至多一个认领者）、(e) complete 需 CLAIMED（CREATED 直接 complete 返回 empty）、(f) abandon 从 CREATED/CLAIMED 两源、(g) 终态拒绝任何转换、(h) 跨实例可见性（实例 A create，实例 B getTask 命中）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AiAgentTeamTaskTable.java` 存在，含 `TABLE_NAME` + 列常量 + `DDL_CREATE_TABLE`（`CREATE TABLE IF NOT EXISTS`）
- [x] `DbTeamTaskStore.java` 存在，`implements ITeamTaskStore`，构造期 `initSchema()` 自动建表
- [x] DbTeamTaskStore 实现全部 7 个方法（create/get×3/transition×3）为真实 raw JDBC（非空壳/非 stub）
- [x] **CAS 乐观锁**：claim/complete/abandon 经条件 UPDATE on STATUS，affected-row-count==1 判定成功
- [x] **无静默跳过**（Minimum Rules #24）：CAS 失败（affected-row==0）返回 `Optional.empty()`（非静默返回原 task 假装成功）
- [x] **DB CAS 竞争验证**：测试断言两个 store 实例并发 claimTask 同一 taskId 仅 1 个胜出（端到端跨实例共享表语义）
- [x] `TestDbTeamTaskStore` 全绿（H2 真实 DB，非 mock）
- [x] No owner-doc update required（owner doc 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + 设计文档同步 + roadmap 升级

Status: completed
Targets: 端到端测试、`nop-ai-agent-actor-runtime-vision.md` §8.2/§8.3、`nop-ai-agent-roadmap.md` §4

- Item Types: `Proof`

- [x] 编写端到端测试 `TestTeamTaskUpdateEndToEnd`（in-memory 路径）：构造 `DefaultAgentEngine`（InMemoryTeamManager + InMemoryTeamTaskStore + LocalAgentMessenger + mock LLM）→ 程序化创建团队 + 绑定 lead/member session → lead agent ReAct 调用 team-task-create（建任务 t1）→ member agent ReAct 调用 team-task-update（taskId=t1, action=claim）→ 断言 status=CLAIMED + claimedBy=member → member agent 调用 team-task-update（action=complete）→ 经 `agentCtx.getTeamTaskStore().getTask(t1)` 断言 status=COMPLETED → lead agent 调用 team-status → 断言返回 JSON `taskCount` 反映任务存在（注意：`TeamStatusExecutor` 仅返回 taskCount 整数，不暴露 per-task 状态，故 per-task COMPLETED 经 store 直接查询断言，不在 team-status JSON 上断言 t1 status=COMPLETED）
- [x] 编写端到端测试 DB-backed 路径（同一测试类或独立测试）：`DbTeamTaskStore`（共享 H2）+ InMemoryTeamManager → lead 经 store-A create → member 经 store-B claim（不同 store 实例同一 DB，验证跨进程共享 + CAS）→ 断言 claim 成功（仅 1 个胜出）→ complete → 跨实例 getTask 见 COMPLETED
- [x] 编写 NoOp 默认零回归验证：默认配置（NoOpTeamManager + NoOpTeamTaskStore）下 team-task-update 被调用返回诚实报告；既有全量测试零回归
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §8.2：team-task-update 从 `⏳ successor` 标注为 `✅ 已落地`；DB-backed 共享任务表（raw JDBC CAS）从 successor 标注为已落地；§8.3 任务认领 = "DB 乐观锁 + CAS" 标注已实现
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 254："多 Agent 任务可以通过 Flow / Task 组织"升级为"team-task-update 状态机 + DB-backed 共享任务表已落地"（注意：nop-task DAG 集成 / Team ACL / 自动团队绑定仍为 successor，验收标准文案需保留这些 successor 的未完成状态）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：从 lead agent `engine.execute()` 入口 → ReAct → team-task-create → member team-task-update claim → complete → team-status，完整路径跑通且有测试覆盖（in-memory + DB-backed 两条路径）
- [x] **DB 跨进程共享验证**：DB-backed 端到端测试断言两个 store 实例（模拟两进程）经共享 H2 表完成 create→claim→complete，CAS 保证至多一个 claimer
- [x] NoOp 默认配置下既有全量测试零回归
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言工具经 context 访问到功能性 teamTaskStore（非 NoOp），且状态转换实际改变了 store/表中的 task status
- [x] vision §8.2 + §8.3 + roadmap §4 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `ITeamTaskStore` 状态机契约（claim/complete/abandon）+ `TeamTask.claimedBy` 扩展落地为真实（非空壳）代码
- [x] `InMemoryTeamTaskStore` 状态转换（compute CAS）+ `NoOpTeamTaskStore`（UOE）落地
- [x] `TeamTaskUpdateExecutor` IToolExecutor 落地为真实（非空壳）代码，在 `ai-agent-tools.beans.xml` 注册
- [x] `AiAgentTeamTaskTable` + `DbTeamTaskStore`（raw JDBC CAS）落地为真实（非空壳）代码
- [x] NoOp shipped 默认零回归（NoOpTeamTaskStore 转换抛 UOE / 工具诚实报告）
- [x] 必要 focused verification 已完成（TestInMemoryTeamTaskStoreTransitions + TestTeamTaskUpdateExecutor + TestDbTeamTaskStore + TestTeamTaskUpdateEndToEnd）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（Team ACL / blockedBy 依赖解析 / nop-task DAG / 团队 DB 持久化 / RE-CLAIM / 超时自动 ABANDONED / 任务产物存储 / 自动团队绑定 / 跨进程团队消息路由 / ResourceGuard 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（vision §8.2/§8.3 + roadmap §4）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）team-task-update 在运行时确实经 context 访问到 teamTaskStore 并调用 claim/complete/abandon（不只是注册存在），（b）端到端路径从 ReAct 工具调用到 store 状态变更完整连通（in-memory + DB 两条），（c）无空方法体/静默跳过/no-op 作为正常实现（NoOp 转换抛 UOE、CAS 失败诚实报告）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；Team ACL 强制 / blockedBy 依赖解析引擎 / nop-task DAG 集成 / 团队（team）DB 持久化 / RE-CLAIM / 超时自动 ABANDONED / 任务产物存储 / 自动团队绑定 / 跨进程团队消息路由 / ResourceGuard 配额强制 / VERSION 列均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Team ACL 强制**（vision §5.1）：角色权限矩阵 + 权限派生 + team-task-update / team-task-create / team-send-message 的权限检查拦截。Classification: successor plan required。
- **`blockedBy` 依赖解析引擎**（plan 225 follow-up）：任务依赖阻塞检查（阻止创建被阻塞的任务、自动调度就绪任务）。Classification: successor plan required。
- **nop-task DAG 集成**：团队任务映射为 nop-task 工作流节点。Classification: successor plan required（依赖 nop-task 模块集成裁定）。
- **DB-backed 团队（team）持久化**（plan 223 follow-up）：`@BizModel("AiTeam")` + `ai_agent_team` / `ai_agent_team_member` 表。Classification: successor plan required。
- **任务 RE-CLAIM**（ABANDONED → CREATED 重新认领）。Classification: successor plan required（依赖任务重置语义裁定）。
- **任务超时自动 ABANDONED**：被认领但长期未完成的任务经定时扫描自动转 ABANDONED。Classification: successor plan required（依赖超时策略 + RecoveryManager 集成）。
- **任务结果/产物存储**：complete 时记录产出（文件路径、结果摘要）。Classification: successor plan required。
- **自动团队绑定 / TeamSpec XDSL / 跨进程团队消息路由 / ResourceGuard 配额**：均为 plan 225 显式 successor，Classification: successor plan required。

## Closure

Status Note: team-task-update 状态机（claim/complete/abandon）+ DB-backed 共享任务表（raw JDBC CAS）已完整落地。`ITeamTaskStore` 契约 + `InMemoryTeamTaskStore`（compute CAS）+ `NoOpTeamTaskStore`（UOE）+ `DbTeamTaskStore`（条件 UPDATE on STATUS affected-row-count CAS）+ `TeamTaskUpdateExecutor` IToolExecutor（beans.xml 注册）均为真实非空壳代码。NoOp shipped 默认零回归。LLM 现可在 ReAct 循环中经 `team-task-update` 工具驱动团队任务全生命周期，并发认领经 DB 条件 UPDATE CAS 保证至多一个认领者。Team ACL / blockedBy 依赖解析 / nop-task DAG 集成 / 团队 DB 持久化 / RE-CLAIM 等均显式 Non-Goals 切出为独立 successor。闭合 roadmap §4 Layer 4 验收标准"多 Agent 任务可以通过 Flow / Task 组织"的核心能力。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 explore subagent（closure audit，fresh session）— task id `ses_12d31d7afffe1UHA0a4csSBjo3`
- Audit Session: ses_12d31d7afffe1UHA0a4csSBjo3（read-only adversarial audit，未参与实现）
- Evidence:
  - **每条 Exit Criterion 验证结果（12/12 PASS）**：
    - TeamTask.claimedBy（field + getter + 构造顺序 claimedBy 在 createdAt 前）PASS — `TeamTask.java:50,69-71,139-141`；`InMemoryTeamTaskStore.createTask` 传 null PASS；`TestInMemoryTeamTaskStore.t1()` 9 参构造 PASS
    - TeamTaskStatus Javadoc 含 CREATED→ABANDONED（无 drift）PASS — `TeamTaskStatus.java:7-16`
    - ITeamTaskStore 3 转换方法返回 Optional PASS — `ITeamTaskStore.java:104,118,132`
    - NoOpTeamTaskStore 3 转换抛 UOE（非静默）PASS — `NoOpTeamTaskStore.java:67-79`
    - InMemoryTeamTaskStore compute CAS（claim 写 claimedBy / complete+abandon 保留）PASS — `InMemoryTeamTaskStore.java:195-216`；并发 claim 仅 1 胜出经 TestInMemoryTeamTaskStoreTransitions 16 线程断言 PASS
    - TeamTaskUpdateExecutor（TOOL_NAME + 大小写不敏感 + NoOp 短路 + 跨团队拒绝 + CAS-empty 诚实非异常）PASS — `TeamTaskUpdateExecutor.java:61,93-104,132,141-145,165-177`
    - beans.xml 注册 PASS — `ai-agent-tools.beans.xml:26`
    - AiAgentTeamTaskTable（10 列常量 + CREATE TABLE IF NOT EXISTS + PK TASK_ID）PASS — `AiAgentTeamTaskTable.java:41-67`
    - DbTeamTaskStore 7 方法真实 raw JDBC（构造期 initSchema + claim/complete/abandon 条件 UPDATE + CAS 失败 0 行返回 empty 非 original task + BLOCKED_BY CSV round-trip）PASS — `DbTeamTaskStore.java:79-82,98,151,173,181,193,218,242,282-291,332-347`
    - 4 测试类非平凡覆盖 PASS — `TestInMemoryTeamTaskStoreTransitions`(11) + `TestTeamTaskUpdateExecutor`(9) + `TestDbTeamTaskStore`(16 含两 store 实例 16 线程仅 1 胜出) + `TestTeamTaskUpdateEndToEnd`(3 含 in-memory 全 ReAct + DB 跨实例)
    - Anti-Hollow wiring：in-memory E2E 经 engine.execute() → ReAct → tool → context → store，断言 store 反映 COMPLETED（非仅 tool response）PASS
    - Doc sync：vision §8.2（⏳→✅）+ §8.3（任务认领 CAS ✅）+ roadmap 新增 L4-8-team-task-update 行 ✅ + Layer 4 验收标准文案 PASS
  - **每条 Closure Gate 验证结果（全 PASS）**：见上文 12 项证据，对应 closure gates 各条；Anti-Hollow（a/b/c）三项由 audit 验证 PASS；NoOp 零回归由 2262 tests 0 failures 验证
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/227-nop-ai-agent-team-task-update.md --strict` 退出码为 0（Closure Gates 已勾选 + Closure Evidence 已写入，无未勾选项）
  - Anti-Hollow 检查结果：独立子 agent 追踪调用链（engine.execute → ReAct → TeamTaskUpdateExecutor → AgentToolExecuteContext.getTeamTaskStore → InMemoryTeamTaskStore.claimTask/completeTask → store status 变更 COMPLETED），无空壳；`scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0（0 findings）
  - Deferred 项分类检查：Non-Goals 段显式列出 10 项独立 successor（Team ACL / blockedBy 依赖解析 / nop-task DAG / 团队 DB 持久化 / RE-CLAIM / 超时自动 ABANDONED / 任务产物存储 / 自动团队绑定 / 跨进程团队消息路由 / ResourceGuard / VERSION 列），无 in-scope live defect 被降级
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS，2262 tests, 0 failures
  - `node ai-dev/tools/check-doc-links.mjs --strict` → 退出码 0（No errors found）

Follow-up:

- 无 plan-owned 剩余工作。显式 successor（均为独立 successor plan required）记录在 Non-Goals / Non-Blocking Follow-ups：Team ACL 强制、blockedBy 依赖解析引擎、nop-task DAG 集成、DB-backed 团队持久化、任务 RE-CLAIM、超时自动 ABANDONED、任务产物存储、自动团队绑定/TeamSpec XDSL/跨进程团队消息路由/ResourceGuard。

## Follow-up handled by 228-nop-ai-agent-team-acl-enforcement.md

> Team ACL 强制 successor（Non-Blocking Follow-ups 第一条）由 `ai-dev/plans/228-nop-ai-agent-team-acl-enforcement.md` 接管。

## Follow-up handled by 230-nop-ai-agent-team-db-persistence.md

> DB-backed 团队（team）持久化 successor（Non-Blocking Follow-ups 第四条 + plan 223/228 Non-Goals）由 `ai-dev/plans/230-nop-ai-agent-team-db-persistence.md` 接管。

## Follow-up handled by 233-nop-ai-agent-task-flow-dag-integration.md

> nop-task DAG 集成 successor（Non-Goals「nop-task DAG 集成」+ Non-Blocking Follow-ups 第三条：「将团队任务映射为 nop-task 工作流 DAG 节点。Classification: successor plan required（依赖 nop-task 模块集成裁定）」）由 `ai-dev/plans/233-nop-ai-agent-task-flow-dag-integration.md` 接管。该计划裁定 nop-task 模块集成定位（nop-task 作为 DAG 编排引擎，团队任务持久化保持 DbTeamTaskStore），交付团队任务 → nop-task 图模型桥 + 环检测 + 依赖序同步编排。
