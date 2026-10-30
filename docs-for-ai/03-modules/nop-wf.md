# nop-wf — 工作流引擎

## 功能概览

完整的工作流/BPM 引擎。nop-wf 是独立引擎，**并非基于 nop-task 构建**（`nop-wf` 各模块 pom 不依赖 `nop-task`，源码不引用 `io.nop.task`）。

`nop-wf` 相比很多传统工作流引擎的一个关键差异是：它大量复用了 Nop 平台通用的 XDSL、Delta、模板继承和编译期元编程机制，而不是为 workflow 单独发明一套扩展体系。详见 `../06-extensibility/platform-extensibility-mechanism.md` 与 `../06-extensibility/nop-wf-as-example.md`。

- 复杂审批工作流
- 多步骤类型（人工步骤、自动步骤、子流程）
- 参与者分配（指定人、角色、部门）
- **委托**：将待办委托给他人
- **转办**：转交给他人处理
- **会签**：多人同时审批
- **加签**：动态增加审批人
- **顺序签**：按顺序逐一审批
- **投票**：按权重投票
- 子工作流
- 信号机制
- 超时升级
- 步骤级动作
- 完整状态生命周期

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopWfDefinition | `nop_wf_definition` | 工作流定义 |
| NopWfDefinitionAuth | `nop_wf_definition_auth` | 定义级权限 |
| NopWfInstance | `nop_wf_instance` | 运行中的工作流实例 |
| NopWfStepInstance | `nop_wf_step_instance` | 步骤实例 |
| NopWfStepInstanceLink | `nop_wf_step_instance_link` | 步骤转换链接 |
| NopWfAction | `nop_wf_action` | 工作流动作 |
| NopWfStatusHistory | `nop_wf_status_history` | 状态变更历史 |
| NopWfOutput | `nop_wf_output` | 工作流输出 |
| NopWfVar | `nop_wf_var` | 工作流变量 |
| NopWfWork | `nop_wf_work` | 待办工作项 |

## 步骤状态

`created` → `suspended` → `waiting` → `activated` → `completed` / `expired` / `failed` / `killed` / `skipped` / `rejected` / `withdrawn` / `cancelled` / `transferred`

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-wf-core` | 工作流核心引擎 |
| `nop-wf-dao` | ORM 实体与 DAO |
| `nop-wf-service` | 业务逻辑 |
| `nop-wf-web` | Web 层与 AMIS 页面 |

## 依赖

nop-wf 是独立工作流引擎，不依赖 nop-task。

## 并发与扫描契约（plan 2514 R2-11）

### wf 实例条带锁（五站点判定-迁移原子化）

同一 wf 实例的「组完成判定 → 组迁移 → 落盘」段经 **wfInstanceId 级进程内条带锁**（64 槽，`IWorkflowStore.withWfLock` 接口暴露、`DaoWorkflowStore` 实现）串行化，锁内经 store 重读兄弟步骤最新已提交状态（每请求 `WorkflowImpl` 步骤快照互不可见的竞态由此闭合）。覆盖五站点：组迁移、流程自动终结（checkEnd）、join 实例查建、用户 action（锁段收窄：`runXpl` 本体不持锁、执行前锁内状态双检 + 迁移段重取锁终检，状态已迁移即以 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 拒绝第二到达者）、子流结束回滚重放。**跨进程残留边界**：条带锁只覆盖单 JVM；多节点部署下跨请求互斥退化为 DB 乐观锁，多节点 + 关键审批流场景的幂等要求由应用层承接（DB 级 `SELECT FOR UPDATE` 为未启用的备选）。**实例线程封闭**：`WorkflowImpl`/`WorkflowStepImpl` 为每请求新建、不保证实例级线程安全，禁止跨请求/跨线程持有。

### WfTaskScanner fencing（R12 族平台语义权威）

due/remind 扫描以 **DB 条件 UPDATE 抢占**为事前 fencing：`NopWfStepInstance` 新增 `scanHolderId`（`nodeId:UUID`）+ `scanLeaseTime` 两列（ORM 源模型 + 迁移脚本 `nop-wf/deploy/sql/postgresql/2026-10-add-wf-step-scan-fencing.sql`；两列只经直发 UPDATE 读写，不经实体逐字段落盘）。抢占 `rowsAffected==1` 即单飞赢家，==0 以 skip INFO 跳过；租约键 `nop.wf.scheduler.scan-lease-ms`（缺省 120000，崩溃节点到期后他节点可重入）；`remindCount` 为 DB 原子自增（不再读-改-写）。引擎 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` status 守卫保留为事中兜底，两层并存。nop-job `LocalJobScheduler` 无集群锁的部署形态下，fencing 使多节点 cron 重叠无害化（不引入 nop-cluster 依赖）。**本节为 R12 族平台 fencing 语义的权威声明；后续批（如 1.7 RUNNING 死区）引用对齐，不重建第二套 fencing。**设计裁定权威为 AI 开发记录区 concurrency 设计集设计文档 wf-task-scanner-fencing-and-task-wf-concurrency（plan 2514，按边界规则此处不直接链接，以该设计文档为准）。

### 扫描分批与毒步骤收敛

due/remind 查询为 `limit(batchSize)` + `orderBy(dueTime asc)` 分页游标推进（无全量载入），单轮扫描受时间预算约束；键 `nop.wf.scheduler.scan-batch-size`（缺省 500，`<=0` 装配期 fail-fast）、`nop.wf.scheduler.scan-time-budget-ms`（缺省 30000，`<0` fail-fast，`0`=单批即止）。due action 执行失败的毒步骤按指数退避推进 dueTime（`now + min(60s × 2^n, 1h)`）退出本轮扫描集，扫描集有界收敛；**`dueTime` 兼作「下次重试时间」字段（退避记账载体）——显式失败计数字段/隔离标记/due-backlog gauge 等毒步骤治理本体归后续 R4-08 批，消费同一字段语义**。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-wf/model/nop-wf.orm.xml`（含 step 实例 `scanHolderId`/`scanLeaseTime` 两列） |
| 引擎（五站点条带锁消费点） | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java` |
| store 接口（`withWfLock`/`tryClaimScanLease` 契约） | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/IWorkflowStore.java` |
| store 实现（64 槽锁体/条件 UPDATE 抢占/分批查询族） | `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java` |
| 调度扫描器（fencing 接线/分批/退避） | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java` |

## 相关文档

- `../nop-task.md`
- `../reusable-modules-overview.md`
- `../06-extensibility/platform-extensibility-mechanism.md`
- `../06-extensibility/nop-wf-as-example.md`
- `../02-core-guides/workflow-configuration.md`
- `../03-runbooks/build-approval-flow.md`
- 审批流设计文档见 ai-dev/design/nop-wf/approval-flow-design.md（串签/并签/会签/条件路由/驳回等模式映射，仅平台开发者参考）
