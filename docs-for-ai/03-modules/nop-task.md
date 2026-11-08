# nop-task — 任务/逻辑流引擎

## 功能概览

通用任务/逻辑流引擎，定义和执行多步骤任务流。核心能力是把一段复杂逻辑拆成可组合的步骤（step），用声明式的控制结构编排，并支持条件、重试、限流、并行、挂起、断点重启等编排语义。

- 任务定义与版本管理（`UNPUBLISHED` → `PUBLISHED` → `DEPRECATED` → `ARCHIVED`）
- 多步骤编排：sequential / selector / choose / if / loop / fork / parallel / graph（数据驱动 DAG）
- 子任务/子流程（`call-task`）
- 步骤库复用（`call-step`）
- 每步增强：`when` 条件跳过、`validator`、`retry`、`catch/finally`、`timeout`、`throttle`、`rate-limit`
- 断点重启（`saveState` + 持久化变量）
- 挂起等人工（`suspend` + `resume-when`）
- 任务权限管理

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopTaskDefinition | `nop_task_definition` | 任务流定义 |
| NopTaskDefinitionAuth | `nop_task_definition_auth` | 任务定义权限 |
| NopTaskInstance | `nop_task_instance` | 运行中的任务实例 |
| NopTaskStepInstance | `nop_task_step_instance` | 步骤实例 |

**CRUD 写保护（plan 364）**：实例/步骤表的状态机列与引擎数据列（`status`/`stepStatus`、`taskInputs`、`stateBeanData`、`version`、错误诊断列、生命周期时间戳、归属字段等）经 xmeta `updatable=false`（状态机列另 `insertable=false`）收敛为引擎独写——CRUD update 携带这些字段会被写入口静默丢弃，手工建实例行会因必填列缺失响亮失败；`copyForNew` 因 cloneInstance 会克隆引擎状态列而对四个实体全部禁用（`ERR_TASK_CRUD_WRITE_DISABLED`）。引擎自身经 `DaoTaskStateStore` 的 dao 直写路径不受影响。机制与保护清单裁定的设计记录在平台开发过程文档（design 区 crud 专题）。

**安全与授权边界（plan 364 裁定）**：
- `NopTaskDefinitionAuth`（任务定义权限）**当前仅为登记用途，不参与执行鉴权**——引擎加载与执行路径不消费 definitionAuths，配置它不产生任何强制效果（管理页已同文标注）；接入执行入口鉴权为独立后续计划。
- 任务实例/步骤表的错误诊断列（errCode/errMsg/errorStack/errorBeanData）持久化是既定设计（plan 265/266），读出面经通用 CRUD `query` 权限可见、**无字段级裁剪**——裁定为 residual-risk-only：内部类名/XPL 栈可辅助框架测绘、异常 params 可能含业务值，部署时应将 `NopTaskInstance:query` 授予可信角色。
- **持久化明文 residual-risk 全列清单（F-WF-02-1 脱敏契约，plan 2380）**：除错误诊断列外，以下列同样以结构化明文落库且读出面无字段级裁剪——`taskInputs`（任务输入 request）、`remark`（终态 resultValue）、`stateBeanData`（resultValue/stateBean/outputs/nextStepName/persistVars 版本化 wrapper）、`errorBeanData`（ErrorBean 含 params + cause chain）。DB 泄露即暴露结构化明文，部署时应将实例/步骤表的 `query` 权限收敛到可信角色。
- **`secret` 标记脱敏契约（plan 2380）**：task 模型的 `<input>`/`<output>` 属性支持 `secret="true"`——`DaoTaskStateStore` 序列化落库（taskInputs/remark/stateBeanData/errorBeanData 五锚点）时按声明名集递归排除同名键（任意层级、宁过滤勿漏）。语义要点：①无 secret 声明的任务落库行为字节级零变化；②**secret 属性不持久化 → 中断恢复（resume）时该属性不可用**（输出可由步骤体重算的才适合标记）；③过滤失败的异常路径 fail-open 返回原文（resume 数据完整性优先，warn 可见）；④落库加密（encrypted-at-rest）为正交补充手段，平台不翻 persist 缺省、不内置加密。
- 任务实例表族**无租户列**（tenantId/namespaceId）：多租户场景的行级隔离需应用层自行过滤（数据权限按租户过滤对本模块不可下推）。
- `global="true"` 的限流器/信号量以 `taskName:key` 为共享单元，**刻意跨租户共享**（一个租户的高频执行会消耗同名任务的全局限额）。注册表为强引用，plan 364 [05-04] 的「永不驱逐」边界之上已落**键空间治理**（plan 2514 MA-08）：entry 持 `lastAccess`，逐出为同 key 复访时内联执行（空闲超阈且 permits 满额才逐出，permit 池不分裂；无独立清扫位/无后台线程）；键 `nop.task.global-gate.idle-evict-ms`（缺省 300000）、`nop.task.global-gate.max-entries`（缺省 10000，超限周期 WARN + 渐进收敛，冷 key 驻留 ≤ 历史取值域）、gauge `nop.task.global-gate.registry-size`。**旧容量键 `nop.task.max-global-rate-limiters` / `nop.task.max-global-semaphores` 已退役**（定义保留、主代码不再引用）。动态 keyExpr（如 `${input.*}` 直通）为合法用法，受上述逐出治理约束；包络 fail-fast 面 = 配置负值/非法值装配期拒绝。
**配置固化语义（plan 2541 W-03，MA-10 裁定 A）**：global 闸门的限流参数（requestPerSecond/maxConcurrency/maxWait）**变更需重启生效**——模型热更新不触注册表（cacheKey 无版本维度，钩子清除分支已裁定否决），首配置固化后同 key 二次配置不同值时旧参数继续生效；同 key 二次配置不同值触发「配置固化」告警（`nop.task.global-gate-config-frozen:registry=rate-limiter/semaphore`），告警全局周期化（60s 间隔，rate-limiter 与 semaphore 两侧对称）。测试锚点：`nop-task-core` `TestCraR408ThrottleGate`（frozen WARN 周期化 + semaphore 对称告警钉）。

**throttle 重入禁用（plan 2541 W-04，MA-11 裁定 A）**：同一执行链对同一 throttle key 的嵌套获取（显式共享 keyExpr 嵌套、call-task 自递归同名任务、call-step 同 stepRt 二次进入）**一律运行期确定性硬失败**（`ERR_TASK_STEP_CONFIG_INVALID` 族）——非重入信号量按深度耗 permit 的嵌套从未是合法契约，「有意嵌套限流」不支持；fork 兄弟分支并发同 key 为合法并发不误报（held 键集按分支隔离）。检测时点 = permit 获取成功之后（不误报合法并发兄弟），held 键移除与 permit 归还同点成对。**升级注记：存量模型若依赖共享 key 嵌套，升级后运行期确定性报错，须改用不同 key 或移除内层 throttle**。maxConcurrency=1 时内层在获取处以既有超时失败（信号量语义正确呈现，非缺陷）。测试锚点：`TestCraR408ThrottleGate`（C10-002 三缺省形态 + 合法并发兄弟腿）。

**并行语义（plan 364 [03-06]**）：`parallel`/`fork`/`fork-n`/`graph` 只做 promise 聚合——子步骤默认在调用线程内联串行执行，仅当子步骤显式配置 `executor` 时才提交线程池真正并行。把耗时同步步骤放入 `parallel` 而不配 executor 不会获得并行加速。

**executor 步骤的上下文传播（plan 2542 W-03，CRA 8.49 R4-09 裁定 C）**：`executor` 包装的步骤体默认以 **task runtime 构造时捕获的 `IContext` 重投递执行**——池线程上 `ContextProvider.currentLocale()` / 用户 / 租户等 ThreadLocal 依赖与调用线程一致，不再静默失效或串号（修复前 body 在池线程直跑，编程式构造 `newTaskRuntime(task,false,null)` 路径下 `getLocale` 回退 ThreadLocal 必错）。无捕获 context 的极端路径（无会话上下文）保持直跑语义、不静默丢执行。**升级注记**：依赖「池线程无 context」旧行为的边缘模型会观察到 context 有值。body 抛 `Error` 时 promise 必然终结且 Error 原样 rethrow（2541 W-06 不变量零弱化）。

## 任务状态

定义状态：`UNPUBLISHED` → `PUBLISHED` → `DEPRECATED` → `ARCHIVED`

### 运行时状态码（plan 349 对齐）

任务/步骤的运行时状态常量（`TaskConstants`）与 ORM 字典（`nop-task/model/nop-task.orm.xml` 的 `task/task-status`、`task/task-step-status`）及生成常量 `_NopTaskCoreConstants` **数值一致**（历史版本曾错位，plan 349 已对齐，守卫测试 `TestPlan349Fixes.statusConstantsAlignWithDictAndGeneratedConstants` 强制约束）：

| 状态 | task | step | 字典标签 |
|------|------|------|----------|
| CREATED | 0 | 0 | 已创建 |
| SUSPENDED | 10 | 10 | 已暂停 |
| WAITING | — | 20 | 等待中 |
| ACTIVE/ACTIVATED | 30 | 30 | 执行中 |
| EXECUTED | — | 35 | 已执行 |
| COMPLETED | 40 | 40 | 已完成 |
| TIMEOUT/EXPIRED | 50 | 50 | 已超时 |
| FAILED | 60 | 60 | 已失败 |
| KILLED | 70 | 70 | 已中止 |

终态语义（first-terminal-wins）：task/step 一旦进入终态（COMPLETED/EXPIRED/TIMEOUT/FAILED/KILLED），后到的终态 driver 不会覆写先到者（异步完成与 kill 竞态场景），仅在日志留痕。实现保障（plan 364 [05-01/02-12]）：task 级四个终态 driver 的"判定+写入+落盘"收敛到 taskState 监视器内的原子序列；step 级 `fail()` 与 `succeed()` 对称携带 first-terminal-wins 守卫（`ITaskStepState.fail` 的非终态记账语义见接口 javadoc）。

## 挂起与恢复（SUSPEND/resume）语义

`<suspend>` 是一等步骤，SUSPEND 是引擎级契约（plan 349 契约化）：

- `TaskStepReturn.isSuspend()` 按返回值中的哨兵名（`@suspend`）判断，任何包装层（BuildOutput/retry 等）重建返回值都不会丢失挂起语义。
- 挂起发生时：task 状态置为 `SUSPENDED`（非终态）、挂起点保存 stateBean 与 bodyStepIndex；**不**驱动为 COMPLETED、**不**清理任务级 bean 容器（保留给进程内 resume）。
- 挂起传播：sequential/selector（同步与异步）、loop/loop-n、fork/fork-n、parallel、graph 中的分支挂起都会向上传播 SUSPEND，不会被当作成功聚合项或静默跳过。
- 恢复：`ITaskFlowManager.getTaskRuntime(taskInstanceId, ...)` → 重新 `execute`。

  **loop/fork items 不持久化（plan 2542 W-07，1.5-C5-005 裁定 D）**：`loop`/`fork` 的 items 集合已移出持久化面——stateBean 只承载游标（loop `index` / fork `itemCount`），items 在一次执行内经 task attribute 进程内持有、跨 resume 由 `itemsExpr`（loop）/ `producer`（fork）**重算**。收益：每迭代 `saveState` 的 stateBean 序列化量 O(1)（修复前含全量 items = 单请求 O(N) 驻留 + 累计 O(N²) 序列化）。**契约要求：`itemsExpr`/`producer` 必须是确定性/纯函数**，否则断点续跑恢复出的集合与原集合不一致（语义漂移）。旧持久化行（wrapper 内含 items 键）由只写兼容 setter 吸收，可正常读回、零迁移。`persistVars` 声明的变量、stateBean（循环下标/分支决策/suspend first 标记）、outputs 导出变量、动态 nextStepName 随 `saveState` 持久化（plan 349 起 `DaoTaskStateStore` 经 `stateBeanData` 版本化 wrapper 持久化，向后兼容旧格式）。
- 挂起任务的 kill（plan 364 [05-03]）：`cancel()` 对处于 `SUSPENDED`（无驱动在飞）的任务直接驱动 KILLED 终态并落盘，不再等待 resume；执行中（ACTIVE）任务的取消仍走 cancel token → step driver 路径。
- **SUSPENDED 转移原子化（plan 2514 MA-01）**：执行线程的 SUSPENDED 写入与落盘纳入 `synchronized(taskState)` 监视器（与四个终态 driver 同一监视器），块内做终态前置检查——任务已终态（如 cancel 先驱动 KILLED）则拒绝 SUSPENDED 裸覆写，**KILLED 不会被挂起转移复活**；cancel 判定块对「刚转入 SUSPENDED 尚在保存中」的任务同样即时驱动 KILLED。挂起任务被 timeout-cancel 归类记 **KILLED 非 TIMEOUT**（TIMEOUT 语义保留给在飞步骤的 step-timeout）。resume 在非终态确认后显式重置 ACTIVE 并落盘，终态任务 resume 显式拒绝。

- **执行级 fencing（plan 2542 W-13，1.5-C7-014 裁定 B）**：`TaskFlowManagerImpl.getTaskRuntime` 维护 per-`taskInstanceId` **在途注册表**（`ConcurrentHashMap.putIfAbsent` 抢占 + runtime 终态移除）——同一 taskInstanceId 并发 resume 时第二路以 typed `ERR_TASK_INSTANCE_ALREADY_RUNNING` 快速失败，mainStep 业务体不会双执行（修复前进程内双击/重复投递即可让副作用翻倍）。claim/release 为 Throwable-safe（同步 Error 逃逸不会永久泄漏 fence）；合法顺序 resume（先路退出后再 resume）不误伤。与 C1-009（SUSPENDED 写者守卫）为**分立裁定**，2514 MA-01 监视器语义零重开。测试锚点：`TestConcurrentResumeSingleExecutionR409`（50 轮竞速 + 顺序对照腿）。
- **滞留可观测（plan 2542 W-11，1.5-C7-009 裁定 B）**：`DaoTaskStateStore.newTaskState` 写入节点身份 `workerId`（配置 `nop.task.node-id`，缺省=主机名+8 位随机后缀，实例内一次生成）；`loadTaskState` 对「ACTIVE 且属主非本节点」的行 WARN 留痕并累加 gauge `nop.task.stale-active-loaded`——崩溃残留 ACTIVE 行由此可判定、可计数。**只读不自动恢复**（恢复仍唯一经显式 resume，与跨进程 resume 完整性暂缓裁定兼容）。同节点 ACTIVE 行不告警（防误报）。
- **SUSPENDED 滞留监控（plan 2542 W-12，1.5-C7-010 裁定 E）**：gauge `nop.task.suspended-tasks`，`kind=all` 计全部 SUSPENDED 行、`kind=stale` 计 `updateTime` 早于 `nop.task.suspended.stale-ms`（缺省 86400000）的行——「等人工」与「条件已满足但被遗忘」两类滞留由此可区分。scrape 期执行索引聚合查询（status+updateTime 既有列，零 DDL）；查询失败返回 NaN 表示诚实缺席（非静默 0）。**「等人工」声明语义零触碰**：gauge 为纯观测面，resume-when 仍唯一经显式 resume 求值。

**已知边界**：fork/fork-n 的并发分支共享同一 stepPath 行（无 (taskInstanceId, stepPath) 唯一索引），跨进程 fork+DB-resume 场景尚不可靠（已裁定为 DB 断点续跑完整性设计主题，暂缓实施）；步骤业务副作用与终态保存之间无事务原子性，**步骤体必须幂等**或使用 `TransactionTaskStepDecorator` 并接受该窗口。进程内同 stepPath 行的并发 `saveStepState` 已由 `DaoTaskStateStore` 条带锁串行化（plan 364 [05-02]，不再冒泡乐观锁异常），跨进程并发写仍属上述边界。

**task 行写对称串行化（plan 2542 W-01，1.5-C1-006 裁定 B）**：`DaoTaskStateStore.saveTaskState` 与 `saveStepState` 对称置于同一 64 槽条带锁内，并以**锁内 fresh-read 终态守卫**承载 first-terminal-wins——同 `taskInstanceId` 双 runtime 并发终态保存时先到终态赢单、输家内存 bean 向 DB 真值收敛（修复前仅 DB 乐观锁收敛：裸 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` 冒泡且输家内存永久漂移）。残余跨进程版本冲突映射为 typed `ERR_TASK_STATE_SAVE_CONFLICT`（不再冒泡裸 ORM 错误码）。**锁序**：全部生产调用点在 `synchronized(taskState)` 临界区内（TaskImpl 六处终态 driver + TaskRuntimeImpl SUSPENDED→KILLED），获取序恒为 `monitor → rowLock` 单向无环；**禁则**——`ITaskState.beforeSave` 实现禁止回调 taskRt/taskState 的同步方法（反向序即 ABBA）。测试锚点：`TestTaskStateFencingR409`。

**查找索引（plan 364 [03-01]**）：`nop_task_step_instance` 声明单列索引 `IX_TASK_STEP_TASK_ID (TASK_INSTANCE_ID)`（`findStepEntity` 的 eq(taskInstanceId) AND eq(stepPath) 查询此前全表扫描；stepPath VARCHAR(2000) 参与 MySQL utf8mb4 复合索引超键长上限，故单列）。新建部署经 ORM `<indexes>` 自动生效（`_app.orm.xml`）；**存量库需执行** `nop-task/deploy/sql/{mysql,postgresql,oracle}/upgrade-nop-task-step-instance-index.sql`（CREATE TABLE DDL 不产出二级索引）。

## 图模式（graph）错误边语义

graph 内 `nextOnError` / `waitErrorSteps` / `STEP_RESULTS.x.error` 数据依赖（plan 349 起两个方向均可用）：

- 被依赖步骤**失败**：waitError 等待的错误分支节点执行（`TaskStepExecution` 把失败包装为携带 nextOnError 跳转的返回，图层识别后按失败语义级联）。
- 被依赖步骤**成功**：错误分支节点按"跳过"处理——不执行 body，但正常级联其后续节点，图继续推进（修复前图会永久挂死）。
- 失败节点若无任何错误消费者（waitError/waitComplete 依赖），图维持 fail-fast 整图失败；全部路径死端时以 `ERR_TASK_GRAPH_NO_ACTIVE_STEP` 报错（不挂死）。
- 图内构建期校验：enterSteps/exitSteps/waitSteps/waitErrorSteps 引用不存在的步骤、步骤重名（`ERR_TASK_DUPLICATE_STEP_IN_GRAPH`）在模型加载期报错。

**enter∩wait 双调度禁止（plan 2542 W-02，1.5-C3-003 裁定 C）**：同一 graph 步骤同时出现在 `enterSteps`（直跑循环）与 `waitSteps`/`waitErrorSteps`（等待调度循环）构成两条独立 `runStep` 调度路径——步骤体双执行、结果覆写。`GraphTaskStep` 的调度入口另有 `stepFuture.isDone()` 一次性守卫（完成窗闭合）兜底；两者叠加。**升级注记**：`GraphStepAnalyzer` 现对此配置**构建期硬失败**（`ERR_TASK_GRAPH_ENTER_WAIT_OVERLAP`，含 `overlapStepNames` 参数）——存量模型若存在该重叠，升级后装配即 fail-fast，须先移除任一侧声明（`next` 会自动派生 waitStep，误配门槛很低）。残余「在飞窗」为已知边界。测试锚点：`TestGraphScanR409Contract` + `TestGraphDrainRace` 族。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-task-core` | 任务核心引擎 |
| `nop-task-dao` | ORM 实体与 DAO |
| `nop-task-service` | 业务逻辑 |
| `nop-task-web` | Web 层与 AMIS 页面 |
| `nop-task-queue` | 队列支持 |
| `nop-task-ext` | 扩展功能（事务/重试/限流/超时等 decorator） |

## 模型与加载

task flow 的权威定义文件是 `*.task.xml`，由 `nop/schema/task/task.xdef` 约束（XDSL），根节点 `<task>` 即 `TaskFlowModel`。

- **加载入口**：`ITaskFlowManager.getTask(name, version)` / `getTaskFlowModel(name, version)`，底层走 `ResourceComponentManager.loadComponentModel(path)`，从 VFS（`VirtualFileSystem`）按 `task:name/version` 路径解析。
- **缓存与动态更新**：模型由 `IResourceLoadingCache` 缓存。通过 `ResourceComponentManager.removeCachedModel(path)` 精确失效单个 task，或 `clearCache(modelType)` 批量失效。VFS 可挂载多种资源源（包括数据库资源源），因此"模型存库 + 失效缓存"即可在不重启、不重新部署的前提下动态更新流程逻辑。这是 task flow 作为可配置业务编排层的基础。

> 模型解析、XDSL 合并机制见 `../02-core-guides/xdef-and-xdsl.md`、`../02-core-guides/vfs-and-resource-resolution.md`。

## 事务模型

NopTaskFlow **默认复用外部环境中的事务**，不自带事务边界：

- task flow 作为 xbiz 的 action 直接定义（见下文"用法一"），或在某个 action（`@BizMutation`）内被调用时，整条流程跑在该 action 所开启的**同一个事务**内，零额外配置（action 的事务由入口层决定：R1.10 起 `@BizMutation` 经 action 装饰器默认开启，经 GraphQL mutation 入口另有 operation 层事务；见 `../03-runbooks/transaction-boundaries.md` 入口矩阵）。
- 需要对某个步骤做更细粒度控制时（子流程独立事务、某步失败不回滚前置步骤），在对应 `<step>` 上配置 `<decorator bean="..." >`，使用 `nop-task-ext` 提供的 `TransactionTaskStepDecorator`，指定 `txnGroup` 与 `propagation`（`REQUIRES_NEW` 等）。
- 对应还有 `OrmSessionTaskStepDecorator`（管理 ORM 会话）等，都在 `nop-task-ext`。

**结论**：内部事务控制是可选项，默认即同事务。判断"这段流程要不要独立事务"时，先默认复用外层，仅在确有隔离需求时才按步声明。

## 两种用法

### 用法一：xbiz action 直接绑定 task flow（声明式，推荐）

xbiz schema 原生支持把一个 BizModel action 直接绑定到 task flow 定义，无需写 Java。`BizActionModel` 上有 `task:name` 与 `task:version` 属性（见 `nop/schema/biz/xbiz.xdef`，`xmlns:task="task"`）：

```xml
<mutation name="approveOrder" task:name="approve-order" task:version="1"/>
```

绑定后，`<arg>` 和 `<return>` 由 task flow 的 `<input>` / `<output>` 定义**自动生成**（实现见 `BizActionGenHelper.buildBizActionFromActionModel`），不需要手写。task 的 input name/type/mandatory/schema 直接映射为 xbiz 的 arg 属性。该 action 的执行体就是这个 task flow。事务由 action 的 `<txn>` 显式声明或 `@BizMutation` 默认（R1.10 起 action 装饰器）开启，task flow 内部所有 step 默认复用。

### 用法二：在 BizModel/Processor Java 内调用 task flow（命令式）

当入口逻辑复杂、需要在 task flow 前后做 Java 编排（例如状态写回、乐观锁、业财回链）时，注入 `ITaskFlowManager` 显式调用：

```java
@BizModel("ErpSalOrder")
public class ErpSalOrderBizModel extends CrudBizModel<ErpSalOrder> {
    @Inject
    protected ITaskFlowManager taskFlowManager;

    @BizMutation
    public ApproveResult approveOrder(@RequestBean ApproveRequest req, IServiceContext ctx) {
        // 1. 事务由 @BizMutation 经 action 装饰器默认开启（R1.10 起）；状态真相源的写回留在 Java
        // 2. 内部编排交给 task flow（复用本事务）
        ITask task = taskFlowManager.getTask("sal-approve-order", 1);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, ctx);
        TaskStepReturn ret = task.execute(taskRt);
        // 3. 状态权威写回 + post-commit 事件留在 Java
        return buildResult(ret);
    }
}
```

判断准则：

| 场景 | 用哪种 |
|------|--------|
| 整个 action 就是一段多步骤业务流程，前后无特殊 Java 逻辑 | 用法一（声明式绑定） |
| 流程前后需要状态写回、跨聚合一致性、post-commit 事件等必须在 Java 控制的真相源逻辑 | 用法二（Java 调用），把 task flow 作为内部编排段 |

## 步骤（step）怎么写

每个 step 是一个一等公民，声明 input/output，可选 `when/validator/retry/catch/timeout` 等。step 的执行体有几种形态：

**可靠性属性取值约束（plan 364）**：`timeout` 负值、`retry` 负值（maxRetryCount/retryDelay/maxRetryDelay）、`rate-limit` 节点存在但 `requestPerSecond<=0`，在 first-class 属性与 `<decorator>` 两条路径上均为构建期硬失败（`ERR_TASK_STEP_CONFIG_INVALID` / `ERR_TASK_DECORATOR_INVALID_CONFIG`）；`timeout=0`/缺省在 first-class 路径表示"未配置"。per-step `saveState="false"` 表示该步骤自身状态行不落盘（跳过 ACTIVE/挂起/终态保存，挂起恢复对该步骤退化为重新执行），未配置时行为不变（任务级 `defaultSaveState` 开启即落盘）。

**executor bean 解析链（plan 2542 W-05，1.5-C4-004 裁定 C）**：`getThreadPoolExecutor` 的解析顺序为 `GlobalExecutors` 三保留名（`nop-global-timer` / `nop-cached-thread-pool` / `nop-global-worker`）→ IoC bean。解析结果做 **instanceof `IThreadPoolExecutor` 检查**，类型不符抛 typed `ERR_TASK_EXECUTOR_BEAN_INVALID`（含 `executorBean` / `actualType`；修复前裸 `ClassCastException` 无 bean 名与模型位置上下文）。保留名命中且容器内存在同名自定义 bean 时 WARN 留痕（部署方同名定制池被静默遮蔽、线程转储不可诊断）。**保留名清单**：`nop-global-timer`（定时器池，禁放阻塞任务）、`nop-cached-thread-pool`（弹性缓存池）、`nop-global-worker`（通用 worker 池）。

**`sync` × `executor` 组合构建期禁止（plan 2514 MA-03）**：`sync="true"` 与 `executor` 同步配置为构建期硬失败（`ERR_TASK_STEP_CONFIG_INVALID` 族，与上述校验链同位）——sync 包装（外层，调用线程阻塞等待）× executor 包装（内层，池上执行）同池嵌套时池饱和即全体永久互等。**部署期行为变更：存量模型若存在该组合，升级后装配即 fail-fast，须先移除组合再升级**（无静默兼容降级）。`sync` 等待本身有界：取消 token 可唤醒，时限键 `nop.task.step.sync-wait-timeout-ms`（缺省 600000，`0`=不限时仅取消唤醒，负值装配期 fail-fast），超时显式抛错。

**throttle 参数校验（plan 2541 W-05，MA-12 裁定 A）**：`<throttle>` 节点存在时 `maxConcurrency<=0` / `maxWait<0` 构建期硬失败（`ERR_TASK_STEP_CONFIG_INVALID` 同码同位——对齐 timeout/rateLimit 校验链）。**升级注记：存量模型非法值升级后装配即 fail-fast，须先修正再升级。**

**运行期表达式上界（plan 2541 W-09/W-10，MA-16/MA-18 裁定 B/D）**：`sleep` 时长、`fork-n` count、`loop-n` 迭代数为运行期求值表达式（构建期不可判定），超上界在执行期 typed 拒绝（`ERR_TASK_STEP_CONFIG_INVALID` 族）——键：`nop.task.step.max-sleep-ms`（缺省 600000）、`nop.task.step.max-fork-count`（缺省 1000，fork-n 预分配同步改增量消除 OOM 通道）、`nop.task.step.max-loop-count`（缺省 10000，迭代数公式=floor(|end-begin|/|step|)+1 含端点；负 step 反向计数合法语义保留，int 边界回绕运行期兜底硬失败）。count<=0/sleep<=0 维持既有 no-op 语义不拒绝。**长时等待指引：优先用 `<delay>`（异步释放线程）；`<sleep>` 为同步轮询占线程形态（100ms 粒度），仅限短时等待。**

**取消/清理契约（plan 2541 W-06/W-07，MA-13/MA-14 裁定 B）**：①步骤包装层的同步抛出边界为 `catch(Throwable)`——Error（OOM/StackOverflowError 等致命错误）不吞不包装、原样传播，且 permit 归还/promise 终结/终态 driver 对 Error 同样可达（修复前 Error 泄漏 permit → 该 key 永久全拒、executor/runOnContext 回调 promise 悬挂）。②`maxConcurrency` 保护的是步骤**进入并发度**，不是 executor 线程占用上界（winner 终结即归还 permit——早释为裁定语义；僵尸 body 的线程占用由池自身 max 配置承接）。③**同步体必须自行响应 cancel token 或自带超时**——不配合取消的同步步骤体会钉死调用线程直到自身完成；timeout 竞速任何 winner 完成路径后向 body seam 施加协作式取消信号（不引入中断式取消），延迟重试段接 cancel token（cancel/timeout 触发时延迟载体上的步骤体不执行）。测试锚点：`nop-task-core` `TestCraR408ErrorBoundaryLifecycle`（C10-013 Error 注入三断言一套 + 幽灵 body/延迟撤回行为腿）。

**step cleanup 扩展点并发契约（plan 2541 W-11，MA-20 裁定 C）**：`ITaskStepRuntime.addStepCleanup` / `runStepCleanups` 为无锁并发安全（`ConcurrentLinkedQueue` + 单消费者 drain）——生产者与 drain 可并发，已提交的 cleanup 恰执行一次。

**延迟重试执行载体（1.5-C4-001/MA-04，plan 2498）**：`<retry>` 延迟轮次采用「定时器只触发 + 载体执行」形态——调度器（未注入 scheduledExecutor 时的回退面为平台 globalTimer）仅承载延迟唤醒，重试步骤体与完成回调链（含 `saveState` 簿记）在步骤显式 `executor` 指定的池执行；未配置 executor 时回落平台 `globalWorker` 池。**重试业务体不在共享 globalTimer 定时器线程执行**，且 `executor` 隔离配置对延迟路径真实生效（与首次尝试的 executor 包装同一池语义）。测试锚点：`nop-task-core` `TestTaskStepHelperRetryDelayOffload_C4_001`（延迟体/簿记线程断言 worker 非 timer；显式 executor 池隔离钉）。

| step 类型 | 说明 | 适用 |
|-----------|------|------|
| `<simple bean="...">` | 从 BeanContainer 取 `ITaskStep` bean 执行 | 复用的 Java step bean |
| `<invoke bean="..." method="...">` | 调用任意 bean 的指定方法 | 直接复用现有 service 方法，无需实现 `ITaskStep` |
| `<step>` | 内联 xpl 脚本（`<step>` 是推荐名，`<xpl>` 已弃用），支持 `customType` 扩展 | 轻量逻辑、数据转换、简单判断，或通过 `customType` 调用任意 XLib 标签 |
| `<script lang="...">` | 执行注册的脚本语言 | 多语言脚本 |
| `<call-task>` | 调用子 task flow | 子流程 |
| `<call-step>` | 调用步骤库中的步骤 | 跨流程复用单步 |
| `<step customType="ns:Tag">` | 通过 `customType` 引用 XLib 标签，自动转为 `<source>` 内调用 | 调用任意 xlib 标签（如 `rule:Execute`）|

控制结构（作为容器 step）：`sequential`（顺序）、`selector`（行为树选择，取第一个非空返回）、`choose`（switch）、`if`、`loop`/`loop-n`、`fork`/`fork-n`（动态分片并行）、`parallel`（并行+聚合）、`graph`（数据驱动 DAG）、`suspend`（挂起）、`exit`/`end`。

Java step bean 的最小实现（实现 `ITaskStep`，或继承 `AbstractTaskStep`）：

```java
@BizBean
public class DeductStockStep extends AbstractTaskStep {
    @Inject
    protected IErpInvStockMoveBiz stockMoveBiz;

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        // 从 step input 取参数
        ErpSalDelivery delivery = (ErpSalDelivery) stepRt.getValue("delivery");
        // 通过 I*Biz 安全能力执行，不直接 dao()
        stockMoveBiz.deductForOutgoing(delivery, stepRt.getServiceContext());
        // 输出到 step output
        stepRt.setValue("moveOrderId", ...);
        return TaskStepReturn.EMPTY;
    }
}
```

step 的 input/output 在 XML 里声明，`persist="true"` 的变量随 `saveState` 持久化，支持断点重启。

### `customType` 扩展机制

通过 `customType="ns:TagName"` 可以在 `<step>` 上直接引用任意 XLib 标签作为步骤执行体，无需写 Java 或显式写 `<source>`。由 `common.task.xml` 的 `x:post-extends` 触发 `TransformCustomStepHelper` 在 XDEF 加载期自动转换。

转换规则（`transformCustomStepToXpl`）：

1. 克隆节点，tag 转为 `step`，移除 `customType`
2. 创建 `<source>` → `<ns:TagName>`（即 `customType` 的值）
3. 命名空间 `ns` 的 URL 映射到 `xpl:lib`，自动设为 `xmlns:xpl="..."` 属性
4. `<input name="x">` 自动展开为 `x="${x}"` 属性加到 `<ns:TagName>` 上
5. **`ns:*` 前缀的属性**从 `<step>` 搬到 `<ns:TagName>`，前缀被剥掉（如 `rule:ruleModelPath` → `ruleModelPath`）
6. **`ns:*` 前缀的子节点**从 `<step>` 搬到 `<ns:TagName>`，前缀被剥掉
7. 如果 xlib 中存在 `{TagName}-generator` 标签（如 `Execute-generator`），优先走 generator 做精确转换

示例——调用 `rule:Execute` 执行规则：

```xml
<step name="calcDiscount" customType="rule:Execute"
      rule:ruleModelPath="/nop/demo/rule/discount.rule.xlsx"
      rule:inputs="${{order: order}}">
    <input name="order"/>
    <output name="discount"/>
</step>
```

转换后等价于：

```xml
<step name="calcDiscount">
    <input name="order"/>
    <output name="discount"/>
    <source>
        <rule:Execute ruleModelPath="/nop/demo/rule/discount.rule.xlsx"
                      inputs="${{order: order}}"
                      order="${order}"
                      xpl:lib="/nop/rule/xlib/rule.xlib"/>
    </source>
</step>
```

注意：`ruleModelPath` 和 `inputs` 等 `rule:Execute` 标签的参数必须用 `rule:` 前缀写在 `<step>` 上，否则不会被搬到 `<rule:Execute>`。非名字空间的属性（如 `displayName`）保留在 `<step>` 上，作为步骤元数据。

如果目标 XLib 标签的 `<attr>` 定义中不包含输入变量名（如 `rule:Execute` 只认 `inputs` 不认单个变量名），多出的 `order="${order}"` 属性可能产生警告或错误。此时有两种解法：
- 在 `rule.xlib` 中添加 `Execute-generator` 标签做精确映射（推荐长期方案）
- 临时用 `<xpl>` 直接写 `<source>` 绕过 customType 转换

## 与其他机制的关系

### 与 nop-wf

nop-task 与 nop-wf 是**两个相互独立的引擎**。nop-wf 并非基于 nop-task 构建：`nop-wf` 各模块的 pom 不依赖 `nop-task`，源码中也不引用 `io.nop.task`。两者分工：

- **nop-task**：通用业务流程编排。强调"做什么、什么顺序"——多步骤、数据流、条件/重试/并行/挂起/断点重启。流程拓扑可由实施/管理员通过 VFS 动态更新。事务默认跟随外部。
- **nop-wf**：审批/BPM 工作流。强调"谁来批、怎么批"——参与者分配、会签/加签/委托/转办/驳回、待办工作项、状态生命周期、定义版本管理。

典型配合：业务单据提交审核时由 nop-wf 驱动审批段（几级审、谁审），审批通过后触发的**业务执行流程**（扣库存、生成凭证、业财回链）由 nop-task 编排。

### 与 BizModel / Processor / 状态机

- **事务入口**：留在 BizModel 的 `@BizMutation` 方法。它开启事务、做状态真相源写回、发 post-commit 事件。
- **内部编排**：交给 task flow。task flow 内的 step 只调 `I*Biz` / `CrudBizModel` 安全能力，不直接 `dao()`，保证事务/权限/状态机管道不被绕过（与 `../02-core-guides/domain-logic-and-ddd.md` 一致）。
- **状态字段写回**：不应出现在 task flow 的 step 里。task flow 的 step 可以读状态、产生"建议目标状态"，但状态字段的最终赋值由 BizModel 通过统一的状态权威完成，保证状态真相源唯一、不被可变的流程定义破坏。
- **xbiz 与状态机**：`mutation` action 上的 `bo:triggerStateChange` 属性用于声明该动作触发状态机迁移，与 nop-fsm（有限状态机，见 `nop/schema/biz/state-machine.xdef`）配合。

### 与 nop-rule

- task flow 编排"步骤结构"（拓扑、顺序、分支、重试）。
- nop-rule 解决"决策点"（容差校验、信用额度、科目映射等），决策矩阵运行时可配置、带版本与执行日志。
- 两者正交：task flow 的某步可以调用 nop-rule 做决策；nop-rule 不编排步骤，task flow 不替代规则配置。
- **集成方式**：通过 `<step customType="rule:Execute">` 调用 `rule:Execute` XLib 标签（见上面 `customType` 扩展机制节）。此时 `rule:*` 前缀的参数自动映射到规则标签，`<input>` 自动传入规则输入。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-task/model/nop-task.orm.xml` |
| task 模型 schema | `nop/schema/task/task.xdef` |
| xbiz 绑定属性 | `nop/schema/biz/xbiz.xdef`（`BizActionModel.task:name/task:version`） |
| 管理器 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java` |
| 状态监视器（SUSPENDED 原子化/四终态 driver） | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java` |
| step 装配校验链（sync×executor fail-fast + throttle 参数校验） | `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java` |
| step 接口 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java` |
| 事务 decorator | `nop-task/nop-task-ext/src/main/java/io/nop/task/ext/dao/TransactionTaskStepDecorator.java` |
| xlib 例子 | `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task.xlib` |

## 相关文档

- `../nop-wf.md` — 工作流引擎（审批段，与 task flow 独立）
- `../nop-rule.md` — 规则引擎（决策点）
- `../reusable-modules-overview.md` — 模块选择总览
- `../02-core-guides/xdef-and-xdsl.md` — XDSL 模型与 Delta 合并机制
- `../02-core-guides/vfs-and-resource-resolution.md` — VFS 与模型加载/缓存
- `../02-core-guides/domain-logic-and-ddd.md` — step 应调 I*Biz 而非 dao 的约束
- `../03-runbooks/implement-complex-business-flow.md` — 何时用 Processor、何时上状态机/Workflow/Task
