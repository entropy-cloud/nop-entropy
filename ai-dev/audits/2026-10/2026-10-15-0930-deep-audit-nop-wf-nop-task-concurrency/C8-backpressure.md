# C8 背压、流控与过载行为 — nop-wf + nop-task 第 1 轮初审（盲审）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块），379 files / 36,107 lines
- 审计维度：C8 背压、流控与过载行为
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15
- 盲审纪律：已遵守。本审计未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何既有文件（本文件为本次交付的写入物）；仅审计当前 live code。
- 输入：owner doc `docs-for-ai/03-modules/nop-wf.md`、`docs-for-ai/03-modules/nop-task.md`；grep 基线 `_tmp/cra-baseline-nop-wf-nop-task.txt`（C8 supplement 两节）。

## 检查范围说明

**覆盖的代码面**：
- nop-task-core：`TaskFlowManagerImpl`（全局限流注册表）、`ThrottleTaskStepWrapper`/`RateLimitTaskStepWrapper`/`ExecutorTaskStepWrapper`/`TimeoutTaskStepWrapper`/`RetryTaskStepWrapper`、`TaskStepHelper`（timeout/retry/withCancellable）、`ParallelTaskStep`/`AbstractForkTaskStep`/`ForkTaskStep`/`ForkNTaskStep`/`GraphTaskStep`/`SequentialTaskStep`/`LoopTaskStep`/`CallTaskStep`/`DelayTaskStep`/`SleepTaskStep`、`TaskImpl`、`TaskStepEnhancer`（wrap 顺序）、`TaskConfigs`、`TaskFlowMetricsImpl`。
- nop-task-ext / nop-task-service / nop-task-queue / nop-task-web：配置面（`NopTaskConfigs` 为空接口）、批量端点、队列模块实体。
- nop-wf-scheduler：`WfTaskScanner`（due/remind 双扫描）、job 定义 yaml。
- nop-wf-core：`WorkflowServiceImpl`（含 `transferActorsAsync` 批量转办）、`DefaultWorkflowExecutor`、`WorkflowManagerImpl`、`WorkflowEngineImpl`（dueTime 设置点、auto-transition 上限）、`WorkflowStepImpl`（方法面清点）。
- nop-wf-dao：`DaoWorkflowStore`（三个 `findAllByQuery` 无 limit 查询）。
- 平台设施对照面（只审使用方式，不审实现）：`nop-kernel/nop-commons` 的 `DefaultSemaphore`/`DefaultRateLimiter`/`StandardThreadPoolExecutor`/`AsyncHelper.waitAsync`、`IBlockingQueue`/`QueueOverflowPolicy`/`HighWatermarkSemaphore` 的本单元使用计数。

**排除**：`_` 前缀生成文件（`_gen/`、`_TaskExecutableModel.java` 等）；测试专用代码（`TestWfTaskScanner` 等仅作为行为佐证参考）；平台设施自身实现缺陷（属平台单元审计范围）。

**规模声明对照**：owner doc 已声明的 C8 相关边界——(1) 全局限流器/信号量强引用注册表、按配置容量阈值告警、在用实例永不驱逐（nop-task.md L33，plan 364 [05-04]）；(2) parallel/fork/fork-n/graph 默认内联串行、仅显式 executor 才并行（nop-task.md L35）；(3) `nop-task-queue` 为"队列支持"子模块（nop-task.md L90）。本审计区分"缺陷"与"已声明边界"。

**判级口径**：P0=主路径语义违约/包络内必然资源耗尽；P1=负载上升必然死锁/队列溢出/崩溃恢复静默丢数；P2=无观测/边界配置缺陷/理论风险；P3=优化候选。

**发现条目 13 字段格式**：`发现ID` / `严重度` / `类别` / `位置` / `证据片段` / `机制描述` / `积压-降级链`（过载类必填，写不出则降 P2 并标注） / `触发条件与规模对照` / `影响` / `与 owner doc 声明对照` / `缺陷族对照(F2/F3/F5)` / `修复建议` / `误报校准`。

## 生产-消费边界清单

| # | 边界 | 生产者 | 消费者 | 有界缓冲 | 满后行为 | 判定 |
|---|------|--------|--------|----------|----------|------|
| B1 | wf 到期扫描→dueAction 执行 | `nop_wf_step_instance` 中 `status=ACTIVATED AND dueTime<=now`（`DaoWorkflowStore.findDueActivatedSteps`，无 LIMIT） | `WfTaskScanner.scanDueTasksInCurrentSession` 单线程 for 串行 + `FutureHelper.syncGet` | 无（DB 全量结果集即缓冲，单 ORM 会话持有全部行） | 无"满"概念——积压体现为单轮时长无界增长 | **C8-001/002** |
| B2 | wf remind 扫描→listener | `remindTime<=now` 的 ACTIVATED 步骤（无 LIMIT） | `scanRemindTasksInCurrentSession` 串行 | 同上 | 失败记录不推进 remindTime→下轮重试（C8-011） | **C8-011** |
| B3 | task 提交→执行（xbiz 绑定路径） | GraphQL/REST 请求线程 | 同一线程内联同步执行（`TaskImpl.execute` → `mainStep.execute`） | 无（同步调用栈即缓冲） | 无独立队列；过载由上游 web 容器线程池承担 | 已声明（同步模型），非缺陷 |
| B4 | task step→executor 线程池 | `ExecutorTaskStepWrapper.execute`（step 配置 `executor` 时） | `IThreadPoolExecutor`（`StandardThreadPoolExecutor`：有界 TaskQueue + force(timeout) + `RejectedExecutionException`） | **有界**（平台池队列） | 排队等待→超时拒绝异常→分支/步骤失败（fail-fast，语义可预期） | 平台设施正确使用，无发现 |
| B5 | fork/parallel 派发→聚合 | producer/items 全量物化（`ForkTaskStep` stateBean 持久化 items 列表） | 默认内联串行；配 executor 才提交 B4 | 无（分支数=items 数，无并发闸门；限流需逐分支配 throttle） | joinType 缺省=allSuccess；autoCancelUnfinished 缺省=true，未完成分支补 `ERR_TASK_CANCELLED` 占位 | 声明一致（nop-task.md L35），F2 结论见覆盖度节 |
| B6 | graph 节点调度→完成 | waitFuture 触发（buildWaitFuture 计数） | `runStep` → `executeAsync`（可配 executor 走 B4） | 无独立队列 | runningCount 归零 + 图未终结 → `ERR_TASK_GRAPH_NO_ACTIVE_STEP` 兜底（不挂死） | 无发现 |
| B7 | Delay 等待 | `DelayTaskStep` | `GlobalExecutors.globalTimer()` 定时器 | 定时器队列（平台全局） | 无拒绝路径；超长 delay 占定时器槽位 | 无发现 |
| B8 | throttle/rate-limit 全局闸门 | 各任务执行线程 | `TaskFlowManagerImpl.globalSemaphores/globalRateLimiters` 强引用注册表 | 注册表无界（强引用） | acquire 超时拒绝（`ERR_TASK_THROTTLE_TIMEOUT`/`ERR_TASK_REQUEST_RATE_EXCEED_LIMIT`） | **C8-004/005/006/007/008** |

**平台设施该用未用盘点**（对照任务简报）：两模块主代码 `IBlockingQueue`/`QueueOverflowPolicy`/`IBlockingSink` 使用数为 **0**（grep 全量确认）；`HighWatermarkSemaphore` 使用数为 **0**。同步执行模型下 B3/B5 主路径不需要队列，属合理；B1/B2 两个异步生产-消费边界是最该有界缓冲/批量上限的位置，实际两者均为"无界查询 + 串行消费"。

## 发现条目

### [nop-wf-nop-task-C8-001]

1. **发现ID**：nop-wf-nop-task-C8-001
2. **严重度**：P2（降级链具体、接近 P1，见字段 7；因不命中 P1 三情形——不死锁、不溢出、不丢数——判 P2）
3. **类别**：C8-1 生产-消费边界无界（wf 到期扫描→dueAction）
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:78-119`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:181-186`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-scheduler/src/main/resources/_vfs/nop/job/conf/wf-due-task-scan.job.yaml:1-9`
5. **证据片段**：
   ```java
   // DaoWorkflowStore.java:181-186 —— 无 limit、无分页、无批次上限
   public List<? extends IWorkflowStepRecord> findDueActivatedSteps() {
       QueryBean query = new QueryBean();
       query.addFilter(FilterBeans.eq(NopWfStepInstance.PROP_NAME_status, NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED));
       query.addFilter(FilterBeans.le(NopWfStepInstance.PROP_NAME_dueTime, CoreMetrics.currentTimestamp()));
       return stepDao().findAllByQuery(query);
   }
   ```
   ```java
   // WfTaskScanner.java:79,96 —— 全量结果集串行同步执行
   for (IWorkflowStepRecord stepRecord : workflowStore.findDueActivatedSteps()) {
       ...
       FutureHelper.syncGet(workflowService.invokeActionAsync(request, null, newSchedulerContext()));
   ```
   ```yaml
   # wf-due-task-scan.job.yaml —— 每分钟 cron，默认 disabled
   cronExpr: "@cfg:nop.job.wf-due-task-scan.cron-expr|0 0/1 * * * ?"
   enabled: "@cfg:nop.job.wf-due-task-scan.enabled|false"
   ```
6. **机制描述**：到期扫描每分钟一轮，一次加载全部到期步骤行到单个 ORM 会话（`scanDueTasks` 外层 `ormTemplate.runInNewSession`），逐条串行同步执行 dueAction；无每轮条数上限、无游标分页、无单轮耗时预算。
7. **积压-降级链**（可具体化）：设每分钟新增到期步骤 E 条、单条 dueAction 平均耗时 T 秒。停机恢复/批量导入/整点集中超时场景下存量 R 可达数千。消费者能力 = 每轮 60s 可串行执行 60/T 条；当 R > 60/T（例如 T=1s、R>60）单轮扫描时长超过 cron 周期，到期集 `dueTime<=now` 只增不减，R 逐步逼近 E 积累速率与失败驻留（C8-002）之和；同时整轮扫描的单 ORM 会话持有 R 个实体行、单事务横跨整轮——R 大时 DB 连接被扫描事务长期占用，拖累同实例其他请求。后果 = 到期处理延迟无界放大 + 连接池占用，非死锁/非丢数（at-least-once）。
8. **触发条件与规模对照**：需部署方启用 job（默认 disabled——关闭时 dueAction 完全不执行，属功能启用问题而非过载问题）；启用后 R 超过单轮消化能力即触发。owner doc 未声明 wf 到期扫描的吞吐包络。
9. **影响**：到期动作（超时升级、自动流转）延迟从分钟级放大到小时级；扫描事务长期占用连接。
10. **与 owner doc 声明对照**：nop-wf.md 未声明扫描批量上限/吞吐包络——非已声明边界，属缺失的边界配置。
11. **缺陷族对照**：F2（过载拒绝路径）不命中——本路径无拒绝语义；F5 关联（延迟不可见，无积压深度指标，见 C8-005 同根）；F3 不直接命中。
12. **修复建议**：`findDueActivatedSteps` 增加 limit + 游标分页（每轮处理 N 条，N 可配）；或单轮加时间预算（超预算即止、余量下轮续扫）；扫描轮暴露积压深度指标（due 未消化的行数）。
13. **误报校准**：非测试代码、非生成文件、非低频路径（每分钟常驻）。批量上限缺失在无任何替代限流机制下成立；若 nop-job 侧对超时 cron 有独占调度策略，仅影响轮次间隔，不改变单轮无界性质。
- **复核状态**：已保留（P2。复核属实：findDueActivatedSteps 无 limit（DaoWorkflowStore.java:181-186）、全量串行 syncGet（WfTaskScanner.java:79,96）、cron 每分钟默认 disabled（job yaml:2,6）逐项实证；积压-降级链成立且确不命中 P1 三情形（不死锁/不溢出/不丢数）。**同点对账（必注）**：与 C5-001 同点位，C5 复核已保留 P1（无界物化+毒步骤不收敛的内存口径）；本条 P2 为背压口径，同点判级差异留 7.1 合并裁定统一并防双计。现状：新发现）

### [nop-wf-nop-task-C8-002]

1. **发现ID**：nop-wf-nop-task-C8-002
2. **严重度**：P2
3. **类别**：C8-1 边界 + F5 挂死不可见（dueAction 缺失/持续失败无退出机制）
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:84-118`
5. **证据片段**：
   ```java
   // WfTaskScanner.java:84-89 —— dueAction 为空：DEBUG 级静默跳过，步骤永久留驻到期集
   dueAction = getDueAction(stepRecord);
   if (dueAction == null || dueAction.isBlank()) {
       LOG.debug("nop.wf.scheduler.skip-due-task-without-due-action:wfId={},stepId={}",
               stepRecord.getWfId(), stepRecord.getStepId());
       continue;
   }
   ```
   ```java
   // WfTaskScanner.java:112-118 —— dueAction 执行失败：仅 ERROR 日志，无退避、无重试上限、无死信
   LOG.error("nop.wf.scheduler.due-action-failed:wfId={},stepId={},dueAction={}",
           stepRecord.getWfId(), stepRecord.getStepId(), dueAction, e);
   ```
6. **机制描述**：步骤到期后若 (a) 模型无 dueAction（`getDueAction` 返回 null/blank）——每轮加载+跳过，行永不退出 `dueTime<=now AND status=ACTIVATED` 集合，且仅 DEBUG 日志（生产日志级别下不可见）；(b) dueAction 执行持续失败（如模型加载错、动作 bean 缺失）——行同样永久驻留，每分钟重试一次，无指数退避、无最大重试次数、无死信状态。
7. **积压-降级链**：每个坏配置/坏动作步骤为扫描集贡献一个**永不再减少**的成员；M 个此类步骤使 C8-001 的 R 永久性抬高 M，且随时间只增不减（可积累坏模型版本的历史遗留行）。错误日志以 1 条/分钟/步骤的恒定速率洪水（M 大时显著），而 dueAction=null 路径完全静默——运维既看不到"永不推进的步骤"，也拿不到积压深度。
8. **触发条件与规模对照**：(a) 模型配置了 `dueTimeExpr` 但未配 `dueAction`（`WorkflowEngineImpl.java:390-392` 只要有 dueTime 即进入到期集）或步骤定义变更后动作名失效；(b) 任一动作的持久性故障。属低频配置缺陷路径，但驻留效应使单次缺陷永久化。
9. **影响**：单个工作流步骤永久不推进且不可见（业务挂死）；扫描工作集与日志量单调增长。
10. **与 owner doc 声明对照**：nop-wf.md 步骤状态机含 `expired` 终态，但无"到期动作不可达"的降级声明——非已声明边界。
11. **缺陷族对照**：**F5 命中**（挂死不可见：步骤永不推进，默认日志级别无任何可见信号）；F2 不命中（无拒绝语义）。
12. **修复建议**：dueAction 为空时升级为 WARN（一次性/计数型）并考虑计入可观测指标；持续失败的到期步骤增加 `dueRetryCount`，超过阈值进入显式失败/死信状态（映射步骤状态机 `failed`），或至少拉长重试间隔。
13. **误报校准**：(a) 分支要求模型同时配置 dueTimeExpr 而缺 dueAction——可由 xdef 构建期校验消除（现状无此校验），缺陷真实存在；(b) 分支为常态故障处理路径。均非测试/生成代码。
- **复核状态**：已保留（P2，触发面修正。机制(b) 持续失败无退出实证（WfTaskScanner.java:112-118 仅 ERROR 日志、无重试上限/退避/死信）；**触发(a) 原文不成立**：WorkflowEngineImpl.java:389 实为 `dueTimeExpr != null && dueAction != null` 双条件守卫——仅配 dueTimeExpr 缺 dueAction 的步骤 dueTime 不会被设置、不进入到期集，"WorkflowEngineImpl.java:390-392 只要有 dueTime 即进入到期集"的引申有误；(a) 收窄为 VFS 热更新漂移路径（新模型步骤改名/删除使 getStepModel 取到 null，或已持 dueTime 行的步骤被移除 dueAction——VFS 热更新为声明能力，路径可达），驻留+DEBUG 静默（WfTaskScanner.java:85-88）在收窄后路径上仍成立；P2 由 (b)+收窄(a) 共同支撑。现状：新发现）

### [nop-wf-nop-task-C8-003]

1. **发现ID**：nop-wf-nop-task-C8-003
2. **严重度**：P2
3. **类别**：C8-3 批处理边界（批量转办无条数上限）
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java:213-255`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:170-178`
5. **证据片段**：
   ```java
   // WorkflowServiceImpl.java:224-249 —— 全量待办逐条串行事务执行，无条数上限
   List<? extends IWorkflowStepRecord> stepRecords = workflowStore.findActivatedStepsByOwner(
           request.getFromUserId(), request.getWfIds());
   for (IWorkflowStepRecord stepRecord : stepRecords) {
       WfReference wfRef = new WfReference("_", null, stepRecord.getWfId());
       try {
           FutureHelper.syncGet(workflowExecutor.execute(wfRef, ctx, wf -> { ... }));
           successCount++;
   ```
   ```java
   // DaoWorkflowStore.java:177 —— findAllByQuery 无 limit
   return stepDao().findAllByQuery(query);
   ```
6. **机制描述**：`transferActorsAsync`（转办）按 fromUserId 全量加载其所有 ACTIVATED 步骤（无 LIMIT），逐条同步执行独立事务的转办动作。无每批条数上限、无分页、无异步化。
7. **积压-降级链**：单次 HTTP mutation 的时长 = N × 单条转办事务时长（含每 wf 的鉴权 + 记录写 + `runAutoTransitions`）。owner 为公共服务账号（角色待办、系统代办的 ownerId 复用）时 N 可达万级 → 单请求持有连接与响应时间线性放大 → HTTP 网关超时后服务端仍在继续执行（无取消点）→ 客户端重试叠加第二遍全量循环。
8. **触发条件与规模对照**：N = 单 owner 活动待办数。常规个人 owner（数十）无感；共享账号/批量场景（数千）即触发。owner doc 未声明批量上限。
9. **影响**：长事务、连接占用、超时重试放大；前端只见超时，无法感知进度。
10. **与 owner doc 声明对照**：nop-wf.md 列"转办"为能力，未声明批量规模边界——缺失的边界配置。
11. **缺陷族对照**：F2 **部分命中且已声明处理**——逐条失败不中断整批，`successCount + failedItems` 显式回传部分成功清单（`WfTransferResultBean`），鉴权拒绝整体抛出。部分成功语义是显式声明而非"半个成功"缺陷；本条缺陷仅在条数无界。
12. **修复建议**：增加每批条数上限（可配，如默认 500）超限报 `ERR_WF_BATCH_TOO_LARGE`；或改分页游标 + 异步任务化（进度可查）。
13. **误报校准**：非低频路径（转办为核心运营操作）；非测试/生成代码。部分成功语义本身不算缺陷（已显式声明），仅条数无界立案。
- **复核状态**：已保留（P2。复核属实：transferActorsAsync 无条数上限逐条 syncGet 独立事务（WorkflowServiceImpl.java:224-249）、findActivatedStepsByOwner 无 limit（DaoWorkflowStore.java:170-178）、`catch (Throwable)` 单条隔离与鉴权整体拒绝（:241-248）逐项实证；部分成功为显式声明的收敛口径正确。与 C5-002 为同点平行登记（内存物化视角，C5 复核保留 P2），两侧判级一致无冲突。现状：新发现）

### [nop-wf-nop-task-C8-004]

1. **发现ID**：nop-wf-nop-task-C8-004
2. **严重度**：P2
3. **类别**：C8-2 信号量/限流使用——全局注册表 key 基数无上界
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:59-66,174-207,209-215`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:55-65`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/TaskConfigs.java:11-17`
5. **证据片段**：
   ```java
   // TaskFlowManagerImpl.java:63-66 —— 强引用注册表，永不驱逐
   private final ConcurrentHashMap<String, IRateLimiter> globalRateLimiters = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<String, ISemaphore> globalSemaphores = new ConcurrentHashMap<>();
   private final AtomicBoolean rateLimiterCapWarned = new AtomicBoolean();
   private final AtomicBoolean semaphoreCapWarned = new AtomicBoolean();
   ```
   ```java
   // TaskFlowManagerImpl.java:209-213 —— 配置阈值仅为一次性 WARN，不拒绝、不驱逐
   private void warnIfOverCap(AtomicBoolean warned, int cap, int currentSize, String registryName) {
       if (cap > 0 && currentSize >= cap && warned.compareAndSet(false, true)) {
           LOG.warn("nop.task.global-gate-registry-over-cap:registry={},size={},configuredCap={}; "
                   + "registry is strong-ref (never evicted, plan 364 [05-04]) — monitor key cardinality",
   ```
   ```java
   // ThrottleTaskStepWrapper.java:59-63 —— key 由 keyExpr 运行时求值，可绑定任意业务变量
   key = ConvertHelper.toString(keyExpr.invoke(stepRt));
   if (StringHelper.isEmpty(key))
       key = stepRt.getStepPath();
   ```
6. **机制描述**：`global="true"` 时注册表键为 `taskName + ":" + key`，key 默认为 stepPath、亦可由 `keyExpr` 引用任意任务变量（如订单 ID、用户 ID）。注册表为强引用 ConcurrentHashMap，无驱逐、无移除 API；配置容量阈值（默认 10000）只触发**每注册表一次**的 WARN，不做任何准入控制。
7. **积压-降级链**：增速差 = 任务执行速率 R（每个新 key 值首次执行即注册一个永久对象）× 零驱逐；缓冲 = 无界堆；后果 = 高基数 keyExpr（例如 `keyExpr="${entityId}"`）在长期运行进程内单调积累 limiter/semaphore 对象及字符串键，数百万 key 时达 GB 级老年代占用 → Full GC 频率上升 → 全局停顿。一次性 WARN 在进程生命周期内仅出现一次，基数持续增长无后续信号。
8. **触发条件与规模对照**：需要模型作者配置 `global=true` 且 keyExpr 绑定高基数变量。配置 10000 阈值声明了"预期基数 ~万级"，超出即进入未声明区域但系统不设防。
9. **影响**：慢性内存增长直至 GC 恶化；键空间本身无法清理（无 API）。
10. **与 owner doc 声明对照**：nop-task.md L33 已声明"注册表为强引用、按配置容量阈值告警、在用实例永不驱逐"——**强引用选择已声明**（正确性优先，防 permit 池分裂，判定合理）；但"keyExpr 高基数场景"未声明防护策略，且"monitor key cardinality"的 WARN 提示缺少配套的持续可观测手段（见 C8-005）。判边界配置缺陷而非纯已声明边界。
11. **缺陷族对照**：F3 关联（失效面枚举见 C8-008——本注册表无任何失效路径）；F2 不命中；F5 不命中。
12. **修复建议**：为注册表基数暴露持续 gauge 指标（而非一次性 WARN）；考虑对 key 增加速率设准入（超阈值后拒绝新的 global key 并报显式错误码），或将 key 空间约束为声明式枚举（模型加载期校验 keyExpr 引用的变量基数声明）。
13. **误报校准**：强引用永不驱逐为已声明裁定（保留，不作为缺陷主张）；本条主张收敛为"高基数 keyExpr 无防护 + 告警一次性不持续"，此部分未声明、可具体化，成立。
- **复核状态**：已保留（P2。复核属实：强引用注册表（TaskFlowManagerImpl.java:63-66）、WARN-once（:209-215，CAS 每注册表一次）、keyExpr 运行时求值（ThrottleTaskStepWrapper.java:55-65）、无任何移除 API 逐项实证；"强引用已声明、仅立高基数无防护+告警不持续"的收敛口径正确。**同点对账**：与 C5-003 同点位，C5 复核保留 P1（进程生命周期持久单调增长口径，现状"已知未修复（plan 364 [05-04] 声明边界）"）；本条 P2 为声明边界残留面视角，同点判级差异与 C8-001↔C5-001 一并留 7.1 统一。现状：新发现——强引用选择系 plan 364 [05-04] 既有裁定，本条立案其上残留面）

### [nop-wf-nop-task-C8-005]

1. **发现ID**：nop-wf-nop-task-C8-005
2. **严重度**：P2
3. **类别**：C8-4 过载可观测缺失
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:217-245`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/metrics/TaskFlowMetricsImpl.java:39-48`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/DefaultSemaphore.java:12-13`
5. **证据片段**：
   ```java
   // TaskFlowManagerImpl.java:218-233 —— stats API 存在，但全仓库生产代码零消费（grep 证实仅测试引用）
   public Map<String, ISemaphore.SemaphoreStats> getGlobalSemaphoreStats() {
       Map<String, ISemaphore.SemaphoreStats> ret = new HashMap<>();
       globalSemaphores.forEach((k, v) -> { ret.put(k, v.getStats()); });
       return ret;
   }
   ```
   ```java
   // TaskFlowMetricsImpl.java:39-43 —— meter 注册仅 task/step 成败计时器，无任何限流/信号量维度
   taskSuccessTimer = createTimer(TaskConstants.METER_TASK, taskNameTag, taskVersionTag, statusSuccessTag);
   taskFailureTimer = createTimer(TaskConstants.METER_TASK, taskNameTag, taskVersionTag, statusFailureTag);
   stepSuccessTimer = createTimer(TaskConstants.METER_STEP, ...);
   stepFailureTimer = createTimer(TaskConstants.METER_STEP, ...);
   ```
   ```java
   // DefaultSemaphore.java:12-13 —— 拒绝/成功计数在数据结构层已存在，但无人读取
   private final AtomicLong acquireSuccessCount = new AtomicLong();
   private final AtomicLong acquireFailCount = new AtomicLong();
   ```
6. **机制描述**：信号量等待/拒绝数（`acquireFailCount`）、限流拒绝数、全局限流器统计的读取入口（`getGlobalSemaphoreStats`/`getGlobalRateLimiterStats`/`resetGlobalStats`）在 `ITaskFlowManagerImplementor` 声明、`TaskFlowManagerImpl` 实现，但**无任何 xbiz 端点、GraphQL 查询、beans 暴露或 MeterRegistry 注册消费它们**（全仓库 grep 仅 nop-task-core 测试引用）。`GlobalMeterRegistry` 只接入 task/step 成败计时器。
7. **积压-降级链**：过载发生时（限流拒绝、throttle 超时、B1 扫描积压）唯一痕迹是业务侧异常码（`ERR_TASK_THROTTLE_TIMEOUT`/`ERR_TASK_REQUEST_RATE_EXCEED_LIMIT`）与散点日志；拒绝速率、等待分布、积压深度均不入指标体系 → 过载既不能告警也不能事后归因。C8-001 的扫描积压深度同样无指标。
8. **触发条件与规模对照**：任何启用 throttle/rate-limit 的部署；规模无关——是能力缺位而非规模触发。
9. **影响**：限流配置失效（如 C8-007 的 0 信号量）或过载降级发生时不可见；容量规划无数据支撑。
10. **与 owner doc 声明对照**：nop-task.md L33 提及"按配置容量阈值告警"，未声明 stats 读取端点；C8 维度要求"积压深度、拒绝数、限流等待是否有指标"——答案为否。
11. **缺陷族对照**：F5 关联（过载/积压不可见同族）；F2/F3 不直接命中。
12. **修复建议**：将 `SemaphoreStats`/`RateLimiterStats` 的 acquire 成功/失败计数注册进 `GlobalMeterRegistry`（gauge/function counter，tag=cacheKey 的 taskName 维度）；或提供只读 xbiz 查询端点。工作量小（数据结构已就绪）。
13. **误报校准**：stats API 存在但零生产消费是 grep 全量证实的事实；"API 存在即算可观测"不成立——无拉取入口即无观测。非测试/生成代码。
- **复核状态**：已保留（P2。复核属实：getGlobalSemaphoreStats/getGlobalRateLimiterStats/resetGlobalStats 生产零消费点（grep 全量复证，仅 ITaskFlowManagerImplementor 声明、TaskFlowManagerImpl 实现与两个测试类）；TaskFlowMetricsImpl 仅 task/step 成败计时器（:39-43）、DefaultSemaphore acquireSuccessCount/acquireFailCount 存在无人读（:12-13）。现状：新发现）

### [nop-wf-nop-task-C8-006]

1. **发现ID**：nop-wf-nop-task-C8-006
2. **严重度**：P2
3. **类别**：C8-2 acquire/release 配对——许可泄漏路径
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:36-53`
5. **证据片段**：
   ```java
   // ThrottleTaskStepWrapper.java:39-52
   if (!semaphore.tryAcquire(1, maxWait)) { ... }
   try {
       TaskStepHelper.checkNotCancelled(stepRt);
       return getTaskStep().execute(stepRt).whenComplete((ret, err) -> {
           semaphore.release(1);
       });
   } catch (Exception e) {          // ← 只捕获 Exception；Error 穿透
       semaphore.release(1);
       throw NopException.adapt(e);
   }
   ```
6. **机制描述**：release 有两条路径——(1) 返回的 `TaskStepReturn` promise 完成时（`whenComplete`，同步返回时立即触发，含 SUSPEND 返回——无挂起泄漏）；(2) `execute` 同步抛 **Exception** 时。若 `execute` 抛 **Error**（`StackOverflowError`/`OutOfMemoryError` 等，`catch (Exception)` 不命中、`whenComplete` 也未注册），许可即永久丢失。`DefaultSemaphore` 无泄漏检测、无配额重建机制。
7. **积压-降级链**：每次 Error 泄漏 1 个许可 → 该 key 可用并发 −1；累计泄漏达 `maxConcurrency` 后该 key 的 `tryAcquire` 恒假 → 所有后续执行在等待 `maxWait` 毫秒后抛 `ERR_TASK_THROTTLE_TIMEOUT`——**有界降级退化为该 key 永久全拒**，且只能靠进程重启恢复。
8. **触发条件与规模对照**：需要被包裹步骤抛 Error。Error 在本单元可达的具体来源：`CallTaskStep` 自递归无深度护栏（C8-009）导致的 `StackOverflowError`；深层 XPL 编译产物执行中的 `StackOverflowError`。属低频但可复现的路径。
9. **影响**：全局限流配额静默缩水至归零；无告警（泄漏无日志，后续只有 throttle 超时错误）。
10. **与 owner doc 声明对照**：nop-task.md 未声明 Error 路径的许可语义——未声明的缺口。
11. **缺陷族对照**：F2 关联（过载响应——本应是"限流拒绝"退化为"永久拒绝"）；F3 不命中；F5 部分关联（被 hang 住的异步 body 使 promise 永不完成时许可同样被占用——该场景上层已有 timeout 兜底/取消兜底终结 promise，残留面小）。
12. **修复建议**：`catch` 改为 `catch (Throwable e)`（release 后 re-throw，Error 同样保证 release）；或在 `finally` 结构中配对（同步路径），异步路径保持 `whenComplete`。同时考虑在 stats 中暴露 `inFlight` 与 `maxPermits` 差值作为泄漏探针。
13. **误报校准**：Exception 路径与 SUSPEND 路径配对正确（已逐一核验），本条仅主张 Error 路径缺口；Error 频率低故 P2 而非 P1。非测试/生成代码。
- **复核状态**：已保留（P2。复核属实：catch(Exception) 漏 Error（ThrottleTaskStepWrapper.java:49）、release 仅两路——返回 promise 的 whenComplete（:46-48）与同步 Exception（:50），同步抛 Error 时许可无任何释放路径；泄漏→该 key tryAcquire 恒假→ERR_TASK_THROTTLE_TIMEOUT 永久全拒链成立；Error 门控 P2 与 C8-013 同先例一致。现状：新发现）

### [nop-wf-nop-task-C8-007]

1. **发现ID**：nop-wf-nop-task-C8-007
2. **严重度**：P2
3. **类别**：C8-2 边界配置缺陷——throttle 参数无构建期校验（与 rate-limit 校验不对称）
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:136-154`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:115-122`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/semaphore/DefaultSemaphore.java:15-18`
5. **证据片段**：
   ```java
   // TaskStepEnhancer.java:145-152 —— rateLimit 有 <=0 硬失败；throttle 无任何数值校验
   if (stepModel.getThrottle() != null) {
       TaskThrottleModel throttleModel = stepModel.getThrottle();
       step = new ThrottleTaskStepWrapper(step, throttleModel.isGlobal(),
               throttleModel.getMaxConcurrency(), throttleModel.getMaxWait(),
               throttleModel.getKeyExpr());
   }
   if (stepModel.getRateLimit() != null) {
       ...
       if (rateLimitModel.getRequestPerSecond() <= 0) {
           throw invalidStepConfig(stepModel, "rateLimit.requestPerSecond", ...);
   ```
   ```xml
   <!-- task.xdef:115-116 —— maxConcurrency/maxWait 仅要求必填，无下界约束 -->
   <throttle maxConcurrency="!int" maxWait="!int" keyExpr="expr" global="!boolean=false"
             xdef:name="TaskThrottleModel"/>
   ```
6. **机制描述**：`maxConcurrency=0` 时构造 `new DefaultSemaphore(0)`——合法但许可恒为 0，每次执行都阻塞 `maxWait` 毫秒后抛 `ERR_TASK_THROTTLE_TIMEOUT`；`maxConcurrency<0` 时 `new Semaphore(负数)` 抛 `IllegalArgumentException`（构建期崩溃，尚属响亮）；`maxWait<0` 时 `Semaphore.tryAcquire(permits, 负数, unit)` 立即返回 false——表现为"必拒绝"。plan 364 已对 `timeout<0`/`retry<0`/`rate-limit requestPerSecond<=0` 建立构建期硬失败口径，唯独 throttle 两参数漏在校验清单外。
7. **积压-降级链**：误配 `maxConcurrency=0`（意图"禁用"或笔误）的步骤在负载到来时表现为每请求先阻塞 maxWait 再必拒绝——上游线程被无谓占住 maxWait 时长（结合 C8-010 的等待点位置），拒绝速率与上游增速一致且无专属错误码区分"配满拒绝"与"配置为零"。
8. **触发条件与规模对照**：模型误配即触发，与规模无关；规模只决定错误暴露的量。
9. **影响**：静默的永久限流误配；诊断成本高（错误码无法区分配置错误与真实过载）。
10. **与 owner doc 声明对照**：nop-task.md L158 声明了 plan 364 的负值硬失败清单（timeout/retry/rate-limit）——throttle 不在清单内，属该口径的遗漏而非已声明边界。
11. **缺陷族对照**：F3 关联（边界配置缺陷）；F2 关联（过载拒绝与误配拒绝不可区分）。
12. **修复建议**：补齐 `maxConcurrency <= 0` 与 `maxWait < 0` 的构建期硬失败（`ERR_TASK_STEP_CONFIG_INVALID` 同码），与 plan 364 口径对齐；顺带在 xdef 注释标注 maxWait 单位为毫秒（现无任何单位文档）。
13. **误报校准**：有界声明结构不适用——此处无任何声明；校验不对称是当前 live code 可直接对照的事实。非测试/生成代码。
- **复核状态**：已保留（P2，精度修正。校验不对称实证：throttle 构造零校验（TaskStepEnhancer.java:137-142）对照 rateLimit<=0（:147-150）/timeout<0（:130-132）/retry<0（:219-221）构建期硬失败、xdef 仅 !int 必填无下界（task.xdef:115-116）；两处精度修正：(1) maxConcurrency<0 的 IllegalArgumentException 发生在首次执行期（信号量在 getSemaphore 惰性创建），非"构建期崩溃"，仍属响亮失败；(2) maxWait<0 在有空闲许可时 tryAcquire 立即成功，仅竞争时表现为立即拒绝，非绝对"必拒绝"。两修正不动摇"maxConcurrency=0 静默误配 + plan 364 校验清单遗漏 throttle"主缺陷，P2 维持。现状：新发现）

### [nop-wf-nop-task-C8-008]

1. **发现ID**：nop-wf-nop-task-C8-008
2. **严重度**：P2
3. **类别**：C8-2 + F3 失效面枚举——全局限流器配置冻结与失效路径缺失
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:174-194`
5. **证据片段**：
   ```java
   // TaskFlowManagerImpl.java:176-188 —— cacheKey 不含 taskVersion；同 key 二次配置仅 WARN
   if (global) {
       String cacheKey = taskRt.getTaskName() + ":" + key;
       IRateLimiter limiter = globalRateLimiters.computeIfAbsent(cacheKey, k -> { ... });
       // 全局限流器首配置固化告警（plan 349 Phase 5，check2 P3）：同 key 二次配置不同速率时
       // 旧参数继续生效（缓存命中即返回），此处告警提示，避免参数调优静默不生效
       if (Math.abs(limiter.getPermitsPerSecond() - requestPerSecond) > 1e-9) {
           LOG.warn("nop.task.global-rate-limiter-config-frozen:cacheKey={},configuredPerSecond={},activePerSecond={}",
   ```
6. **机制描述**：全局闸门的失效面枚举（F3 要求非空）：(a) 注册表无 remove/失效 API——`getSemaphore`/`getRateLimiter` 只有 computeIfAbsent；(b) 模型缓存失效（`ResourceComponentManager.removeCachedModel`，nop-task.md L98 声明的动态更新路径）**不触及**限流注册表——新版本模型立即生效，但其全局限流参数被旧版本首注册的实例冻结；(c) `cacheKey = taskName + ":" + key` 不含 version——同任务新旧版本共享同一闸门对象，旧版本先执行则新版本的 `requestPerSecond`/`maxPermits` 调整完全无效；(d) 唯一恢复手段是进程重启。
7. **积压-降级链**：运维按新版本调低限流阈值以应对下游过载 → 因旧版本已注册（或共享 key 的另一任务版本先注册）新阈值静默不生效 → 下游持续按旧（过大）速率被打 → 过载防护形同虚设。每次参数不匹配调用都有 WARN（可见性尚可），但无自动收敛。
8. **触发条件与规模对照**：任务多版本并存 + 全局限流参数调整——动态更新（owner doc 明确支持 VFS 模型热更新）正是声明的主路径，冲突概率随版本迭代频率线性上升。
9. **影响**：限流调优在热更新场景静默失效，需重启才生效。
10. **与 owner doc 声明对照**：TaskFlowManagerImpl 注释自述"首配置固化告警"——冻结行为本身已声明（WARN 可见）；但 (b) 模型失效不清除注册表、(c) 跨版本共享键两点未在 owner doc 声明为边界。
11. **缺陷族对照**：**F3 命中**（失效面枚举：模型更新 ≠ 闸门更新；闸门对象零失效路径）；F2 关联（调参不生效 = 过载防护失灵）。
12. **修复建议**：模型缓存失效钩子中同步清除该 taskName 的全局闸门（需处理在用实例的 permit 池分裂问题——可延迟到在用计数归零后替换，或文档显式声明"限流参数变更需重启"并升级 WARN 为启动期汇总报告）。
13. **误报校准**：冻结告警已声明（不重复立案"无提示"）；本条主张限于失效路径缺失与跨版本键设计。非测试/生成代码。
- **复核状态**：已保留（P2。复核属实：cacheKey 不含 taskVersion（TaskFlowManagerImpl.java:177）、两个注册表全类无任何 remove/clear API、模型缓存失效（ResourceComponentManager）无触及限流注册表的钩子代码；(b)(c) 未声明面成立，冻结 WARN 已声明的收敛口径正确。现状：新发现——冻结告警行为系 plan 349 Phase 5 既有落地，本条立案其上的失效路径缺失与跨版本共享键设计）

### [nop-wf-nop-task-C8-009]

1. **发现ID**：nop-wf-nop-task-C8-009
2. **严重度**：P3
3. **类别**：C8-1 增长护栏缺失——call-task 自递归无深度上限
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/CallTaskStep.java:72-104`；对照 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/DefaultWorkflowExecutor.java:51-59`
5. **证据片段**：
   ```java
   // CallTaskStep.java:81-97 —— 子任务在当前线程同步递归执行，无任何深度计数
   if (StringHelper.isEmpty(taskId)) {
       task = getTask(taskManager);
       subRt = taskRt.newChildRuntime(task, stepRt.isSupportPersist());
       stepRt.setStateBean(subRt.getTaskInstanceId());
       stepRt.saveState();
   } ...
   return task.execute(subRt, outputNames);
   ```
   ```java
   // DefaultWorkflowExecutor.java:51-57 —— wf 侧同类风险已有护栏（对照）
   static void runAutoTransitionsWithLimit(IWorkflow wf, IServiceContext ctx) {
       int maxLoops = 10_000;
       int loops = 0;
       while (wf.runAutoTransitions(ctx)) {
           if (++loops >= maxLoops)
               throw new NopException(...ERR_WF_AUTO_TRANSITION_EXCEED_LIMIT)
   ```
6. **机制描述**：`call-task` 允许任务调用自身（直接或经环）。循环任务模型下执行在单线程调用栈上无限递归；每层 `newChildRuntime` + `saveState` 在同一事务内新建/写子任务实例行。task 引擎侧不存在与 wf 侧 `maxLoops=10_000` 对应的深度护栏（`LoopTaskStep` 有 items/maxCount 边界，但 call-task 环没有）。
7. **积压-降级链**：增速差 = 每层递归固定成本（1 次 DB insert + 1 次 state 序列化）× 无上限层数；缓冲 = 调用栈（先到 `StackOverflowError`）或事务超时；后果 = 单请求占用的连接在事务内进行无界写，最终 SOE/事务回滚。回滚使 DB 写不落盘（数据不损坏），但 SOE 若发生在配置了 throttle 的步骤栈内会触发 C8-006 的许可泄漏。
8. **触发条件与规模对照**：需要模型存在 call-task 环（配置缺陷）。模型可经 VFS 热更新（声明能力），环可在生产引入。owner doc 未声明递归深度边界。
9. **影响**：单请求失败 + 事务内无界写；连锁 C8-006 许可泄漏。
10. **与 owner doc 声明对照**：未声明。wf 侧同族风险已有显式护栏，防护不对称佐证这是遗漏而非设计取向。
11. **缺陷族对照**：F3 关联（模型缺陷的失效面无护栏）；F5 不命中（终将以 SOE/超时显式失败）；F2 不命中。
12. **修复建议**：在 `TaskRuntimeImpl.newChildRuntime` 链路上维护 depth 计数（stateBean 或 runtime 属性），超过可配上限（建议默认与 wf 的 10_000 对齐或更低如 100）抛 `ERR_TASK_CALL_DEPTH_EXCEED_LIMIT`。
13. **误报校准**：需要环配置才触发——但 VFS 热更新使该前提可在运行中被引入，且栈溢出类护栏是同类引擎标配；判 P3 优化候选而非 P2（触发前提为模型缺陷）。
- **复核状态**：已保留（P3。复核属实：CallTaskStep 递归执行无深度计数（CallTaskStep.java:97，taskId 复用同层 runtime）、newChildRuntime 无 depth 守卫（TaskRuntimeImpl.java:167-176）、wf 侧 maxLoops=10_000 护栏不对称（DefaultWorkflowExecutor.java:51-59）；模型环配置门控下 P3 口径恰当。现状：新发现）

### [nop-wf-nop-task-C8-010]

1. **发现ID**：nop-wf-nop-task-C8-010
2. **严重度**：P3
3. **类别**：C8-2 等待点语义——阻塞式 sleep 与 throttle 等待在 timeout 覆盖之外
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/SleepTaskStep.java:38-41`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/step/DelayTaskStep.java:39-49`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:119-151`
5. **证据片段**：
   ```java
   // SleepTaskStep.java:40-41 —— 占用调用线程轮询等待整段 sleep
   FutureHelper.waitUntil(stepRt::isCancelled, sleep);
   TaskStepHelper.checkNotCancelled(stepRt);
   ```
   ```java
   // DelayTaskStep.java:41-43 —— 对照：定时器非阻塞等待
   CompletableFuture<?> future = scheduledExecutor.schedule(() -> { return null; }, delay, TimeUnit.MILLISECONDS);
   ```
   ```java
   // TaskStepEnhancer.java:107-151 —— wrap 顺序（后包者为外层）：
   // RateLimit(外) → Throttle → Timeout → Executor → Retry(内)
   ```
6. **机制描述**：两个等待点语义问题。(a) `<sleep>` 步骤同步阻塞调用线程整段时长（xbiz 绑定路径即请求工作线程；fork/parallel 内联分支即父线程），对照 `<delay>` 用全局定时器非阻塞等待——同引擎内两个等价能力资源成本差一个数量级。(b) wrap 顺序上 Throttle 在 Timeout **外层**：throttle 的 `tryAcquire(maxWait)` 阻塞排队发生在 step `timeout` 计时启动之前，配置了 `timeout` 的步骤其排队时间不受 timeout 约束。
7. **积压-降级链**：(a) N 个并发请求各执行 sleep=T 的任务 → N 个工作线程被占满 T → web 容器线程池耗尽 → 全服务请求排队（不限于此任务）。(b) 下游过载时请求在 throttle 排队 maxWait 毫秒 × 每请求一个工作线程 → 即使 timeout 配置很小，线程占用仍由 maxWait 决定——过载从"快速失败"放大为"批量阻塞"。
8. **触发条件与规模对照**：(a) 模型使用 sleep + 并发请求量接近线程池大小；(b) throttle maxWait 配大（秒级以上）+ 上游并发接近池大小。两者均为模型配置可达的常态场景。
9. **影响**：线程池级联耗尽；timeout 语义与使用者直觉不符。
10. **与 owner doc 声明对照**：nop-task.md L35 声明了"parallel 内联串行不加速"，未声明 sleep 的阻塞成本与 throttle 排队在 timeout 之外——文档缺口。
11. **缺陷族对照**：F2 关联（过载时阻塞放大而非有界拒绝）；F5 不命中（有 maxWait/sleep 上界，必有终点）。
12. **修复建议**：文档标注 sleep 阻塞语义并推荐 delay 替代；考虑 throttle 排队计入 timeout（调序或计时起点前移）需谨慎评估语义兼容性，至少文档声明。
13. **误报校准**：sleep 语义由步骤名自明（判 P3 而非 P2 的原因）；(b) 项是真实的语义边界但无错误行为、仅资源占用预期偏差。非测试/生成代码。
- **复核状态**：已保留（P3。复核属实：sleep 阻塞调用线程（SleepTaskStep.java:38 waitUntil 轮询等待整段）对照 delay 定时器非阻塞（DelayTaskStep.java:41-49）、wrap 序 Throttle 外层于 Timeout（TaskStepEnhancer.java:134 timeout、:139 throttle——tryAcquire 发生在 timeout timer 创建前）逐项实证；两项等待均有界必终结，P3 恰当。现状：新发现）

### [nop-wf-nop-task-C8-011]

1. **发现ID**：nop-wf-nop-task-C8-011
2. **严重度**：P3
3. **类别**：C8-1 边界——remind 扫描失败重试无上限
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:122-143`
5. **证据片段**：
   ```java
   // WfTaskScanner.java:124-141
   try {
       IWorkflowStep step = workflowManager.getWorkflow(...).getStepById(...);
       reminderListeners.forEach(listener -> listener.onRemind(step));
       ...
       stepRecord.setRemindCount(remindCount + 1);
       stepRecord.setRemindTime(null);          // 成功才清 remindTime
       workflowStore.saveStepRecord(stepRecord);
   } catch (Exception e) {
       // 失败的记录不更新提醒计数，下一轮扫描可重试
       LOG.error("nop.wf.scheduler.remind-task-failed:...", e);
   }
   ```
6. **机制描述**：remind 记录失败时 `remindTime` 不清除 → 行永驻 `remindTime<=now` 集合，每 5 分钟（`wf-remind-task-scan.job.yaml` cron）重试一次，无退避、无上限、无死信；listener 持续故障（如通知通道宕机）时同一批行每轮全部失败。
7. **积压-降级链**：L 个通知目标故障 → 每轮全量重试 L 行 × 每 5 分钟 → 错误日志速率 = L/300s 恒定；L 增长则日志与查询负载线性放大。remind 行本身有终态退出（步骤完成即离开 ACTIVATED），驻留上界受步骤生命周期约束——比 C8-002 轻。
8. **触发条件与规模对照**：reminder listener 持续失败 + 大量待提醒行。默认 job disabled（同 C8-001 前提）。
9. **影响**：日志洪水、无谓重试负载；提醒语义仍正确（at-least-once，恢复后补发）。
10. **与 owner doc 声明对照**：未声明重试策略边界。
11. **缺陷族对照**：F5 弱关联（持续失败可见——ERROR 日志在，不像 C8-002(a) 静默）；F2 不命中。
12. **修复建议**：失败时可选设置 `remindTime = now + backoff`（退避重试）并在连续 N 次失败后告警。
13. **误报校准**：低频 + 有终态退出 + 语义正确，判 P3。非测试/生成代码。
- **复核状态**：已保留（P3，触发面修正。失败不推进 remindTime 实证（WfTaskScanner.java:133-141，成功才 setRemindTime(null)）；复核补充：remindTime 引擎侧零写入点（grep 全量仅 bean setter 与 scanner 置 null），remind 扫描集当前恒空——触发面当前不可达，属潜在缺陷。按 C5 复核先例（C5-010 无生产调用者仍保 P3 下限；C5-001 不确定项 1 对 remind 半区同一认定）维持 P3。现状：新发现——触发面当前不可达（remind 链无 remindTime 写入点））

### [nop-wf-nop-task-C8-012]

1. **发现ID**：nop-wf-nop-task-C8-012
2. **严重度**：P3
3. **类别**：C8-5 声明结构 vs 实体——nop-task-queue 空模块
4. **位置**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/nop-task-queue/src/main/java/io/nop/task/queue/ITaskQueue.java:1-5`；`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit/nop-task/pom.xml:23`
5. **证据片段**：
   ```java
   // ITaskQueue.java —— 模块全部内容，共 5 行
   package io.nop.task.queue;

   public interface ITaskQueue {
       void enqueueTask();
   }
   ```
   ```xml
   <!-- pom.xml:23 —— 仍作为子模块参与构建 -->
   <module>nop-task-queue</module>
   ```
6. **机制描述**：owner doc（nop-task.md L90）将 `nop-task-queue` 声明为"队列支持"子模块；实际为单个无参、无实现、无任何消费者（全仓库 grep 无 import）的空接口。任务提交→执行链路不存在任何平台队列设施（`IBlockingQueue`/`QueueOverflowPolicy` 使用数为 0），异步执行仅依赖 B4 的线程池内置有界队列。
7. **积压-降级链**：不适用（空结构非运行时路径）——登记为声明与实体不符的文档/结构问题，不构成运行时过载链。
8. **触发条件与规模对照**：不适用。
9. **影响**：误导集成者以为存在可用的任务队列/背压设施；空模块参与构建增加维护面。
10. **与 owner doc 声明对照**：nop-task.md L90 声明"队列支持"与实体（5 行 stub）不符——按审计纪律不按队列盘点，但声明应修正。
11. **缺陷族对照**：F3 弱关联（失效面为空——本来就没有实现可失效）；F2/F5 不命中。
12. **修复建议**：要么实现（对接 `IBlockingQueue` + `QueueOverflowPolicy` 做异步任务提交缓冲），要么从 owner doc 与 pom 中降级/移除该模块声明。
13. **误报校准**：任务简报已明示"勿按队列盘点"——本条不作为队列缺陷立案，仅登记声明失真。低风险、判 P3。
- **复核状态**：已保留（P3。复核属实：ITaskQueue 为 5 行无参空接口、全仓零消费者（grep 复证）、仍列 nop-task/pom.xml modules（:23）；声明失真登记口径正确。现状：新发现）

## 维度集外线索

本轮 C8 审计中未发现 C6/C9 形态的 P0/P1 级线索，无 `[维度集外线索-CL-0NN]` 条目登记。

说明：C8-009（call-task 自递归）具有 C6（资源获取无护栏）形态，但触发前提为模型环配置缺陷、终态为显式失败（SOE/事务回滚），判 P3 不满足"仅登记 P0/P1"的 overlay 门槛，已按维度内 P3 条目登记，不重复立案。C8-004 的注册表无界增长同为 C6 相邻形态，但其属 C8 维度明示的盘点对象（限流注册表配额），且强引用选择已由 owner doc 声明，判 P2 留在维度内。

## 覆盖度与残留边界

**已覆盖**：
- C8-1 生产-消费边界：B1-B8 全部盘清（见清单表）；wf 侧 `WorkflowEngineImpl` dueTime 设置点（:390-392）、`DefaultWorkflowExecutor` auto-transition 上限护栏、`WorkflowManagerImpl`（无实例缓存、模型走 `ResourceComponentManager` 缓存）均已核。
- C8-2 信号量/限流：`ThrottleTaskStepWrapper`/`RateLimitTaskStepWrapper` acquire/release 全路径核验（Exception/SUSPEND/异步完成路径配对正确，Error 路径缺口即 C8-006）；注册表并发性（`computeIfAbsent` 原子、`warnIfOverCap` CAS）；`CFG_TASK_MAX_GLOBAL_*` 配额语义（仅告警阈值）。
- C8-3 批处理边界：wf 侧唯一批量端点 `transferActorsAsync` 已核（C8-003）；task 侧 service 层仅有 CRUD + `copyForNew`（无批量端点）；两模块 `NopWfConfigs`/`NopTaskConfigs` 均为空接口——零批量上限配置存在。
- C8-4 过载可观测：meter 注册面、stats API 消费面全量 grep 核验（C8-005）。
- F2 对照（禁留空）：fork/parallel 分支被限流拒绝（`ERR_TASK_THROTTLE_TIMEOUT`/`ERR_TASK_REQUEST_RATE_EXCEED_LIMIT`）→ `AsyncHelper.waitAsync` 缺省 join=null 按 allSuccess 聚合 → 整聚合失败 → task FAILED（fail-fast，**非半个成功**）；`autoCancelUnfinished` 缺省=true（task.xdef:192/224/232），聚合完成时取消未完成分支并以 `ERR_TASK_CANCELLED` 占位（`AbstractForkTaskStep.java:128-132`、`ParallelTaskStep.java:98-102`），聚合口径完整；`transferActorsAsync` 部分成功为显式声明的 `successCount+failedItems` 语义。B4 线程池满时 `submit` 同步抛 `RejectedExecutionException` → 分支以 `FutureHelper.reject` 进入聚合 → 同一 fail-fast 路径。
- F5 对照：`GraphTaskStep` runningCount 收敛判据 + `ERR_TASK_GRAPH_NO_ACTIVE_STEP` 兜底；`TaskStepHelper.timeout` 超时兜底 promise（body 永不完成也有限时终结）；`ExecutorTaskStepWrapper` 取消兜底——task 侧挂死面已系统性闭合；残留挂死可见性问题集中在 wf 扫描侧（C8-002）与观测缺位（C8-005）。
- F3 失效面枚举（禁留空）：全局限流闸门的失效面四点枚举见 C8-008（无移除 API / 模型失效不清注册表 / cacheKey 跨版本共享 / 仅重启恢复）。

**未覆盖 / 残留边界**：
- 平台设施自身实现（`StandardThreadPoolExecutor` 的 force 队列、`DefaultRateLimiter` 的 Guava 封装、`GlobalExecutors` 池默认参数）不在本单元审计范围，只审了使用方式。
- nop-job 对超时 cron 的同组调度/misfire 策略（影响 C8-001 的轮次堆积形态）属 nop-job 单元。
- `WorkflowEngineImpl`（1949 行）按方法面清点覆盖（dueAction/invokeAction/autoTransitions/execGroup 计数），未逐行审计其表达式求值内部——属 C1/C2 维度深潜范围。
- `nop-wf-web`/`nop-task-web` 页面层与 `nop-wf-ai`/`nop-task-app`/`nop-task-meta`/`nop-task-codegen` 未发现 C8 相关运行时逻辑（codegen/meta 为模型层）。
- 测试代码中的并发行为（`TestWfTaskScanner` 等）仅作行为佐证，不立案。

## 维度复核结论（独立复核子代理）

- 复核人：C8 维度独立复核子代理（与第 1 轮、第 2 轮初审均非同一角色）
- 复核日期：2026-10-15；基线：concurrency-audit-remediation @ be0cbbb2a3（与初审同基线，受检文件无漂移；同日无 touching nop-wf/nop-task 的修复提交——git log 核实，全部 17 条均为修复前现状）
- 复核方法：17 条发现（含 round2）逐条重读 live code 验证文件/行号、引文、积压/降级链、平台侧行为（ExecutorHelper.submit、FutureHelper.bindCancelToken、AsyncHelper.waitAsync、DefaultSemaphore、TaskRuntimeImpl.cancel/newChildRuntime）与判级口径；每条以"被审计方可用什么证据反驳"作反驳性自检；同点对账依据允许范围内的同 campaign C5 维度复核结论节（C5-memory-bounds.md 维度复核结论节）；现状标注仅依据允许范围（ai-dev/audits/ 相关结论节、cra-index.md、git log）；未读 ai-dev/plans、ai-dev/bugs、ai-dev/lessons
- 判定总口径：Error 门控沿 campaign 先例（C8-006）判 P2/P3；触发面可达性/可见性修正不改判级时在判定与复核状态行中显式标注；同点不同视角判级差异不单方改判，留 7.1 统一

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C8-001 | P2 | 保留 | 无 limit 查询+全量串行消费+无批次上限全部实证，P2 辩护（不命中 P1 三情形）成立；与 C5-001（同点，C5 复核保留 P1）判级差异已注明留 7.1 统一 | 新发现 |
| C8-002 | P2 | 保留（证据修正） | 机制(b) 持续失败无退出实证；触发(a) 原文不成立——WorkflowEngineImpl:389 双条件守卫使"配 dueTimeExpr 缺 dueAction"无法进到期集，收窄为 VFS 热更新漂移路径后驻留+DEBUG 静默仍成立 | 新发现 |
| C8-003 | P2 | 保留 | 无上限批量+逐条独立事务全实证；与 C5-002 同点（内存视角）判级一致（均 P2），无冲突 | 新发现 |
| C8-004 | P2 | 保留 | 注册表/WARN-once/keyExpr 求值/无移除 API 实证，"强引用已声明、只立残留面"收敛正确；与 C5-003（同点，C5 复核保留 P1）判级差异留 7.1 统一 | 新发现（plan 364 [05-04] 声明边界之上的残留面） |
| C8-005 | P2 | 保留 | stats API 零生产消费、meter 面无闸门维度 grep 复证 | 新发现 |
| C8-006 | P2 | 保留 | catch(Exception) 漏 Error + 无 whenComplete 兜底实证，泄漏→该 key 永久全拒链成立 | 新发现 |
| C8-007 | P2 | 保留（精度修正） | 校验不对称（throttle 无校验 vs rateLimit/timeout/retry 硬失败）实证；修正两点：负 maxConcurrency 崩在首次执行期非"构建期"（信号量惰性创建）、maxWait<0 有空闲许可时仍成功非绝对"必拒绝"；主缺陷不变 | 新发现 |
| C8-008 | P2 | 保留 | cacheKey 无版本/注册表无移除 API/模型失效不触注册表实证；冻结告警已声明的收敛正确 | 新发现（冻结告警系 plan 349 Phase 5 既有行为，立案其失效路径缺失） |
| C8-009 | P3 | 保留 | 递归无深度护栏 + newChildRuntime 无计数 + wf 侧 10_000 护栏不对称实证；模型缺陷门控 P3 恰当 | 新发现 |
| C8-010 | P3 | 保留 | sleep 阻塞/delay 定时器对照、Throttle 外层于 Timeout 的 wrap 序实证；两项有界必终结，P3 恰当 | 新发现 |
| C8-011 | P3 | 保留（触发面修正） | 失败不推进 remindTime 实证；但 remindTime 引擎侧零写入点（grep 复证），remind 扫描集当前恒空、触发面不可达——按 C5 复核先例（C5-010 无调用者保 P3 下限）维持 P3 | 新发现（触发面当前不可达） |
| C8-012 | P3 | 保留 | 5 行空接口 + 零消费者 + 仍在 pom modules 实证 | 新发现 |
| C8-013 | P2 | 保留（可见性修正） | 双实例 promise 无 Error 终结路径 + 悬挂四链逐环实证；修正：实例 A 的 Error 有 ExecutorHelper 平台层 ERROR 日志（`nop.executor.execute-fail`，catch(Throwable)）非"完全静默"，但该信号与悬挂 promise 断连、业务层与悬挂本身仍零信号，后果链不变 | 新发现 |
| C8-014 | P2 | 保留 | release 挂 winner/timer 无条件终结/cancel(false) 无中断/僵尸叠加链全实证（cancel 全链无 interrupt） | 新发现 |
| C8-015 | P2 | 保留 | nop-wf 全主代码零指标注册 grep 复证 | 新发现 |
| C8-016 | P3 | 保留 | 五站点行号逐一实证，残留有界论证成立 | 新发现 |
| C8-017 | P3 | 保留 | acquire 等待不受取消覆盖 + kill 链无 interrupt 实证；有界（maxWait）P3 恰当 | 新发现 |

### 不确定项（显式列出）

1. **C8-001↔C5-001 同点判级差**（P2 vs P1，两侧复核各自保留）：C5 侧以"无界物化 + 毒步骤不收敛"（内存/DB 口径）保 P1，C8 侧以背压口径判 P2（不死锁/不溢出/不丢数）。按复核纪律不单方改判，留 7.1 合并裁定统一定级并防双计。
2. **C8-004↔C5-003 同点判级差**（P2 vs P1）：同一模式。C8-004 的 P2 建立在"强引用永不驱逐已声明、仅立高基数无防护+告警不持续残留面"上；C5-003 的 P1 建立在"进程生命周期持久单调增长"上且现状标注"已知未修复（plan 364 [05-04] 声明边界）"。7.1 需先裁定"高基数 keyExpr 是否属支持用法"，再统一定级。
3. **C8-002 触发(a) 的可达域**：复核证实引擎双条件守卫（WorkflowEngineImpl:389）后，dueAction=null 驻留仅在 VFS 热更新漂移（步骤改名/删除、dueAction 移除）下可达；若部署方不使用模型热更新，(a) 分支退化为理论路径，(b) 分支（持续失败）不受影响、独立支撑 P2。
4. **C8-011 当前不可达**：remindTime 无引擎写入点，触发面为死路径；维持 P3 的依据是 wf-remind-task-scan.job.yaml 与扫描骨架在位、remind 链补齐写入点后缺陷即生效（与 C5 复核对 remind 半区的认定一致）。

## 最终保留项

（含保留+降级项，不含驳回项；本轮无降级、无驳回，17 条全部保留。）

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C8-001 | P2 | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java`（+ `nop-wf/nop-wf-dao/src/main/java/io/nop/wf/dao/store/DaoWorkflowStore.java:181-186`） | 到期扫描无 LIMIT 无批次上限：全量载入+串行消费，积压时单轮时长与连接占用无界放大（与 C5-001 同点，7.1 对账） | 新发现 |
| C8-002 | P2 | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:84-118` | dueAction 持续失败无退出机制（无退避/上限/死信），行永驻到期集；dueAction=null 路径仅 DEBUG 静默（触发面收窄为模型热更新漂移，见不确定项 3） | 新发现 |
| C8-003 | P2 | `nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/service/impl/WorkflowServiceImpl.java:213-255`（+ `DaoWorkflowStore.java:170-178`） | transferActorsAsync 批量转办无条数上限，全量待办逐条同步事务执行，共享 owner 场景长请求+超时重试放大（与 C5-002 同点，判级一致） | 新发现 |
| C8-004 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java`（+ `ThrottleTaskStepWrapper.java:55-65`） | 全局闸门注册表高基数 keyExpr 无防护、容量告警 WARN-once 不持续（强引用已声明；与 C5-003 同点，7.1 对账） | 新发现（plan 364 [05-04] 声明边界之上的残留面） |
| C8-005 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:217-245`（+ `TaskFlowMetricsImpl.java:39-48`） | 信号量/限流统计 API 存在但零生产消费，acquire 成败计数无人读取，过载不可告警不可归因 | 新发现 |
| C8-006 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:36-53` | catch(Exception) 漏 Error：body 同步抛 Error 时 throttle 许可永久泄漏，累计后该 key 永久全拒且仅重启恢复 | 新发现 |
| C8-007 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:137-142`（+ `task.xdef:115-116`） | throttle maxConcurrency/maxWait 无构建期校验（与 rateLimit/timeout/retry 硬失败不对称），maxConcurrency=0 静默误配表现为先等待后必拒绝 | 新发现 |
| C8-008 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:174-194` | 全局闸门配置冻结且零失效路径：cacheKey 不含版本、注册表无移除 API、模型热更新不触注册表，调参静默不生效需重启 | 新发现（冻结告警系 plan 349 既有行为） |
| C8-009 | P3 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/CallTaskStep.java:72-104` | call-task 自递归无深度上限（wf 侧同族有 10_000 护栏），模型环配置下单请求栈溢出/事务内无界写，连锁 C8-006 许可泄漏 | 新发现 |
| C8-010 | P3 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/SleepTaskStep.java:38-41`（+ `TaskStepEnhancer.java:118-153`） | sleep 同步阻塞调用线程整段（对照 delay 定时器非阻塞）；throttle 排队等待在 step timeout 计时之外 | 新发现 |
| C8-011 | P3 | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:122-143` | remind 失败重试无退避无上限（remindTime 不清除永驻扫描集）；当前 remindTime 无引擎写入点、触发面不可达，属潜在缺陷 | 新发现（触发面当前不可达） |
| C8-012 | P3 | `nop-task/nop-task-queue/src/main/java/io/nop/task/queue/ITaskQueue.java`（+ `nop-task/pom.xml:23`） | nop-task-queue 为 5 行空接口零消费者，与 owner doc"队列支持"声明不符 | 新发现 |
| C8-013 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/ExecutorTaskStepWrapper.java:33-52`（+ `RunOnContextTaskStepWrapper.java:26-38`） | executor/runOnContext 派发回调 catch(Exception) 漏 Error：返回 promise 无终结路径，任务永驻非终态、清理/终态 driver 全跳过、throttle 许可泄漏（实例 A 有平台层断连 ERROR 日志，悬挂本身零信号） | 新发现 |
| C8-014 | P2 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:46-48`（+ `TaskStepHelper.java:197-204`、`ExecutorTaskStepWrapper.java:59-67`） | timeout/kill 竞速终结即归还 throttle 许可，僵尸 body（不响应取消）继续占线程与下游资源，过载时 maxConcurrency 上界被击穿 | 新发现 |
| C8-015 | P2 | `nop-wf/nop-wf-scheduler/src/main/java/io/nop/wf/scheduler/WfTaskScanner.java:78-143`（全模块 grep） | nop-wf 全模块零指标注册：B1/B2 扫描积压深度、扫描时长、成败速率均无 gauge/counter，过载诊断只能靠 DB 即席查询 | 新发现 |
| C8-016 | P3 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:142-151`（+ `TaskStepExecution.java:360-370`、`TaskStepHelper.java:209-216,379-385`、`ForkTaskStep.java:68-75` 等） | Error 穿透清理残留矩阵（task/step seam 跳过 cleanup/终态/meter、timer 不取消、fork 派发中断），各子项有界 | 新发现 |
| C8-017 | P3 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:39-44`（+ `RateLimitTaskStepWrapper.java:39-43`、`DefaultSemaphore.java:47-59`） | kill/取消不覆盖 throttle/rate-limit 等待点，等待中线程不受取消唤醒，kill 响应延迟有界于 maxWait | 新发现 |

**复核计数**：17 条全部保留（保留 17 / 降级 0 / 驳回 0），其中 4 条带证据/精度/可见性修正（C8-002 触发面、C8-007 精度、C8-011 触发面、C8-013 可见性）；最终 P2×11（C8-001/002/003/004/005/006/007/008/013/014/015）、P3×6（C8-009/010/011/012/016/017）。
