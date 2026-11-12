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

due/remind 查询为 `limit(batchSize)` + `orderBy(dueTime asc)` 分页游标推进（无全量载入），单轮扫描受时间预算约束；键 `nop.wf.scheduler.scan-batch-size`（缺省 500，`<=0` 装配期 fail-fast）、`nop.wf.scheduler.scan-time-budget-ms`（缺省 30000，`<0` fail-fast，`0`=单批即止）。due action 执行失败的毒步骤按指数退避推进 dueTime（`now + min(60s × 2^n, 1h)`）退出本轮扫描集，扫描集有界收敛；**`dueTime` 兼作「下次重试时间」字段（退避记账载体）**。

**remind 半区退避与扫描观测（plan 2541 W-01，MA-06 残留面裁定 E）**：remind listener 执行失败同样按指数退避推进 `remindTime`（`now + min(60s × 2^n, 1h)`，直发限定列集 UPDATE）**持久化退出工作集**（与 due 半区收敛语义等价）；`remindTime` 的引擎侧唯一写入点=scanner 退避推进（业务侧写入仍可注入扫描集）。due 扫描观测：gauge `nop.wf.scheduler.due-scan-scanned`（最近一轮消费条数）/`nop.wf.scheduler.due-scan-failures`（最近一轮失败条数）。`dueTimeExpr` 配置存在而 `dueAction` 缺失的步骤在扫描期 WARN 显式化（每轮一次——部署盲区/VFS 热更新漂移可见；不做模型加载期硬失败）。测试锚点：`nop-wf-scheduler` `TestCraR408ScannerRemindBackoff`。

**批量转办请求级上界（plan 2541 W-02，MA-07 裁定 D）**：`transferActors` 按批取 `maxBatch+1` 条发现超限即 typed 拒绝（`ERR_WF_TRANSFER_BATCH_EXCEED`，不静默截断）——键 `nop.wf.transfer.max-batch`（缺省 200，`<=0` typed 配置错误）；查询走 `findActivatedStepsByOwner` 四参游标分页重载（stepId 升序 + 游标谓词；三参全量形态保留兼容）。部分成功语义（`successCount`+`failedItems`）保留。测试锚点：`nop-wf-core` `TestCraR408TransferAndRetry`。

**wf step retry 语义（plan 2541 W-12，MA-22 裁定 F）**：`computeRetryDelay` 对固定 delay 与 exponential 两分支统一应用 `maxRetryDelay` 封顶（对齐 task 侧 RetryPolicy）；`maxRetryDelay<=0` 维持不封顶（未配置语义，存量长退避零变更）。**活动事务上下文中延迟等待降级不重试**——事务内 sleep 持连接/行锁为更劣形态，检测到已开启事务时显式 WARN（`nop.wf.retry-degraded-in-transaction`）后抛原业务异常（事务检测经 `IWorkflowStore.hasOpenedTransaction` 接口投影，DaoWorkflowStore 以 ORM querySpace 实测）；升级注记：存量依赖事务内重试的模型升级后该场景改为显式失败。测试锚点：`TestCraR408TransferAndRetry`（封顶两分支 + 事务降级 execCount 断言）。

### 扫描时钟双缝（plan 2566 R4-32，1.5-C7-006）

`WfTaskScanner` 暴露两个 protected 时钟缝：`nowMillis()`（默认 `CoreMetrics::currentTimeMillis`，绝对域——`leaseUntil`/`dueTime`/`remindTime` 写值与 DB 可比，保留 epoch 语义；墙钟回拨=租约相对变长，自愈型有界）与 `nowNanos()`（默认 `CoreMetrics::nanoTime`，纯相对预算域——due/remind 两半区扫描时间预算 deadline，毫秒预算类内 `TimeUnit.MILLISECONDS.toNanos` 换算）。消费方按语义选缝，禁止缝外直连 `System.currentTimeMillis`。测试锚点：`TestWfScannerClockSeams_C7_006`（预算确定性耗尽钉 + 绝对域墙钟缝钉）。

### 扫描 job 装配与韧性（plan 2542 R4-09，CRA 8.49）

**invoker bean 装配（W-04，1.5-C4-003）**：`wf-due-task-scan.job.yaml` / `wf-remind-task-scan.job.yaml` 的 `invoker.bean` 必须为容器内实际 bean id **`nopWfTaskScanner`**（`app-scheduler.beans.xml` 定义）。修复前两文件写 `wfTaskScanner`（全仓零命中）——一旦启用 `nop.job.wf-*-task-scan.enabled=true`，每次 fire 在 `requireBean` 处同步抛 `ERR_JOB_BEAN_NOT_FOUND` → job FAILED 永久停摆，到期升级与提醒能力 100% 失效。**已部署环境若持有外部覆盖副本（delta/自定义 conf），须同步改名**。测试锚点：`TestWfScanJobWiringR409`（C10-010 装配三腿：yaml 解析 → bean id 集 → method 反射）。

**入口异常边界（W-06(a)，1.5-C4-007 形态 B 使用面）**：`scanDueTasks`/`scanRemindTasks` 方法体异常**不再外泄至 job fire 层**——单轮失败以 ERROR 留痕 + gauge `nop.wf.scheduler.scan-round-failures`（累计）反映后正常返回，fire 以成功收尾、下一轮 cron 自补偿。`Error` 不拦截（直传宿主）：job fire 线程无 promise 终结义务，Error 转业务成功=致命降级。**宿主侧终态语义（fire FAILED 永久停摆 / resumeJob 只认 SUSPENDED / fireNow 拒绝 FAILED / cancelRunning 空实现）归 nop-job 单元工单 nop-job-C7-006，本模块不含该修复**。测试锚点：`TestWfScanJobResilienceContractR409`（C10-012，使用面限定）。

**逐项 action deadline（W-06(b)×W-09，1.5-C7-004）**：due action 以带 deadline 的等待执行——键 `nop.wf.scheduler.action-deadline-ms`（缺省 60000，负值装配期 fail-fast，**`0`=禁用**即恢复无界 `syncGet` 语义，供合法长动作部署逃生）。超时条目记 error、跳过该条并走既有毒步骤退避推进 `dueTime` 退出本轮扫描集；`InterruptedException` 恢复中断位后同超时形态处理，`CancellationException`（任务侧取消）直接跳条。**诚实登记**：deadline 不终止底层 action 执行（宿主无 cancel 设施——`cancelRunning` 空实现属 nop-job-C7-006），僵尸动作残留以「每挂死条目至多一个、fire 正常收尾、退避退出扫描集」为收敛声明。修复前单条挂死使扫描线程永久滞留，pool-size=4 共享调度池四条即全冻结。测试锚点：`TestWfScanJobResilienceContractR409`。

**共享调度池挤压（W-06(c)）**：due/remind 扫描与其他 local job 共享 `nopJobScheduledExecutor`（core=max=4）。deadline 已把单条挂死的占用上界收窄到 `action-deadline-ms`；部署中若有多个长耗时 local job，应为其配置独立 executor（本批不做代码层池拆分，归优化轮）。

### wf 步骤账本读路径下推（W-08，1.5-C5-006 裁定 D）

`DaoWorkflowStore` override 三个高频读方法，把可翻译谓词下推为 SQL：`getLatestStepRecordByName`（`eq(wfId)+eq(stepName)+orderBy(createTime desc)+limit 1`）、`getStepRecords`（`includeHistory=false` 腿补 `status < HISTORY_BOUND` 预滤）、`getStepRecordsByName`（再补 `eq(stepName)`）。任意 `Predicate` 不可翻译 → 保持全集语义（静默截断比全集载入更危险）。join 两腿（`getJoinWaitStepRecords` / `getNextJoinStepRecord`）把 `in(stepNames)` / `eq(stepName)` + `status < HISTORY_BOUND` 下推，`joinGroup` 判定保留内存；**同 joinGroup 多行的返回顺序未定义**（修复前内存实现同样无序，不可依赖其排序）。

**语义等价前提（关键）**：下推仅在步骤集合处于 **proxy（未加载）态**——即跨请求冷读的放大场景——生效；集合**已加载**时回落到内存视图。原因是引擎在同一 ORM 会话内「先建/迁移步骤、随即读回」，已加载的 `wfRecord.getSteps()` 是权威视图（含未 flush 行与未落库状态），此时等价 SQL 会读到陈旧 DB 视图。该保护由 `nop-wf-service` 的 `TestDaoWorkflowEngine` 全 28 例作为回归门（执行期实测：无条件下推时 21 例全红）。测试锚点：`TestWfStepsReadPathR409`（查询形状 + 接线腿）；`TestWfScanQueryShape`（2514 C10-008）零改写。

### wf/task 账本保留策略（W-10，1.5-C7-007 裁定 E）

五张 `nop_wf_*` 表与两张 `nop_task_*` 表**只增不删，平台不内置归档/purge/TTL**（唯一删除入口是显式 kill 后 `removeWfRecord`）。随业务量线性膨胀会导致索引退化、扫描查询变慢。**保留策略归属 DBA 侧执行**：

- 建议按**终态时间戳**（`nop_wf_instance`/`nop_wf_step_instance`/`nop_wf_status_history` 的结束/创建时间；`nop_task_instance`/`nop_task_step_instance` 的 `END_TIME`/`UPDATE_TIME`）划分保留期；
- 建议**两段式**：先备份/归档到冷存储，确认后再清理，禁止直接 DELETE 不具备可恢复性；
- 终态白名单建议：wf 侧 `COMPLETED`/`EXPIRED`/`KILLED`（含 `wf_status_history` 归属实例），task 侧 `COMPLETED`/`TIMEOUT`/`FAILED`/`KILLED`；**非终态行永不清理**；
- 单 wf 实例内步骤数同样无上界——设计期应控制循环/并签展开规模（与读路径下推联动）。

内置归档 job（含保留期键、缺省关闭）为优化轮候选，本批不提供。

****扫描 job 部署盲区声明（plan 2541 W-01(d)）**：due/remind 扫描由调度 job 驱动，**该 job 缺省 disabled**——未启用扫描 job 的部署中 due/remind 扫描零执行（毒步骤退避/gauge/自动 dueAction 均不生效）。启用扫描 job 为运维动作；部署时若依赖到期自动推进，须确认扫描 job 已按 cron 配置启用。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-wf/model/nop-wf.orm.xml`（含 step 实例 `scanHolderId`/`scanLeaseTime` 两列） |
| 引擎（五站点条带锁消费点） | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java` |
| store 接口（`withWfLock`/`tryClaimScanLease` 契约） | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/store/IWorkflowStore.java` |
| store 实现（64 槽锁体/条件 UPDATE 抢占/分批查询族） | `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java` |
| 调度扫描器（fencing 接线/分批/退避/remind 退避/gauge） | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java` |
| 扫描 job 装配（invoker.bean 指向 `nopWfTaskScanner`） | `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml`、`wf-remind-task-scan.job.yaml` |
| 扫描器 bean 定义 | `nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/wf/beans/app-scheduler.beans.xml` |
| 转办批量配置键 | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/WfConfigs.java` |

## 相关文档

- `../nop-task.md`
- `../reusable-modules-overview.md`
- `../06-extensibility/platform-extensibility-mechanism.md`
- `../06-extensibility/nop-wf-as-example.md`
- `../02-core-guides/workflow-configuration.md`
- `../03-runbooks/build-approval-flow.md`
- 审批流设计文档见 ai-dev/design/nop-wf/approval-flow-design.md（串签/并签/会签/条件路由/驳回等模式映射，仅平台开发者参考）
