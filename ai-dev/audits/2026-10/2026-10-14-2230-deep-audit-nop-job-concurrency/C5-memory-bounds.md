# C5 内存有界性与大数据量维度审计 — nop-job（第 1 轮盲审）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块），src/main/java 全部手写代码，164 files / 17,664 lines
- 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- Owner doc：`docs-for-ai/03-modules/nop-job.md`（已全文读取）
- grep 基线：`_tmp/cra-baseline-nop-job.txt`（已读取）
- 盲审纪律：未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅以当前 live code 为准
- 审计日期：2026-10-14

---

## 检查范围说明

**逐文件核读**（全部 src/main/java 手写代码，`_` 前缀生成文件不作为发现对象）：

| 子模块 | 核读重点 |
|---|---|
| nop-job-api（42 files） | `JobLogReporter`、`TaskLogEntry`、`IJobScheduler` 默认方法、spec/config DTO |
| nop-job-core（29 files） | `AbstractBatchScanner`（批循环骨架 + Cursor 分页）、`TriggerBuilder`、`JobTriggerCalculator`、`CronExpression`（ThreadLocal）、全部 trigger 装饰器（Periodic/Cron/Once/LimitCount/LimitTime/CheckActive/HandleMisfire/PauseCalendar/AnchorRepeat）、全部 calendar（Annual/Daily/Weekly/Monthly/Holiday/Cron） |
| nop-job-local（4 files） | `LocalJobScheduler`（注册表 + ScheduledJob/SimpleJobState）、`LocalJobConfigLoader`、`BeanMethodJobInvoker`、`BeanContainerInvokerResolver` |
| nop-job-coordinator（41 files） | `RpcPollTaskManager`（entries 注册表）、`RemoteJobInvoker`、`DefaultRpcPollTaskClient`、`DefaultJobCancelHandler`、`JobCoordinator`、4 个 scanner（Planner/Dispatcher/Completion/TimeoutChecker）、`JobScheduleCounterReconciler`、`DefaultWorkerLoadProvider`（ThreadLocal scanCache）、task builders（Default/Partition/RpcBroadcast/Adaptive/AbstractService）、`LeastLoadedStrategy`、metrics |
| nop-job-worker（13 files） | `JobWorkerScannerImpl`（认领/执行/结果写回）、`JobWorker`、`DefaultJobExecutionContextBuilder`、`DefaultJobInvokerResolver`、`MetadataWorkerCapacityProvider` |
| nop-job-dao（29 files，_gen 除外） | 3 个 StoreImpl（全部 fetch 路径 LIMIT 核对）、`WorkerReservedCost`/`ReservedCostRow` DTO、状态机与 QueryHelper、mapper |
| nop-job-service（10 files） | 4 个 BizModel（含 `reportTaskLog` 批量端点、`cancelFire`/`rerunFire`）、`RpcJobInvoker`、`NopE2eTestJobInvoker` |
| nop-job-retry-adapter（1 file） | `NopRetryJobRetryBridge`（fire-and-forget 提交） |
| nop-job-app / codegen / meta / web | 1 个 main 启动类；其余无 src/main/java |

**定向 grep**：`ICache|CacheConfig`（0 命中——nop-job 全模块未使用平台缓存设施，无"有上限缓存缺淘汰"面）；`ConcurrentHashMap|HashMap|ArrayList|HashSet|Queue|Deque|ThreadLocal|newFixedThreadPool|Executors\.|findAllByQuery|setLimit|batchLoad`（全量核对，见无界风险清单）。

**已知口径遵循**：misfire 已核对为触发器标量阈值（`TriggerSpec.misfireThreshold` + `HandleMisfireTrigger` 纯时间比较，非队列）——不按队列盘点；DB 侧 `nop_job_task_log` 表随上报无限增长属 DB 容量约束，在"覆盖度与残留边界"单独标注，不与内存面混排。

**规模包络对照**：owner doc 声明的批量为 batch-size=100 × maxScanLoops=1000（5 个 scanner）、worker max-concurrency=0（不限）、poll-interval=5000ms/poll-threads=2。发现条目均对照此包络判定。

---

## 无界风险清单（路径 × 增长源 × 声明上界 × 判定）

| # | 路径（驻留结构） | 增长源 | 声明上界 / 由谁保证 | 判定 |
|---|---|---|---|---|
| R1 | `RpcPollTaskManager.entries`（ConcurrentHashMap，nop-job-coordinator） | executorKind=rpcPoll 的在途任务条目（每条捕获 schedule/fire/task 三实体 + 轮询句柄 + 取消监听器） | **无上界**。名义上"终态即移出"，但 deadline 仅由 `schedule.timeoutSeconds` 派生（`computeDeadline` L259-265），全局 `execution-timeout-ms` 不参与；远程不可达时 getJobStatus 每轮 transient-fail 被忽略，条目永不终结 → **发现 C5-001（P1）** | 无界（缺陷） |
| R2 | `NopJobTaskLogBizModel.reportTaskLog` 入参 `List<TaskLogEntry>`（请求作用域物化 + 单事务逐条落库） | 单次 REST 请求条目数 N × 每条 logMessage/logPayload 任意大小 | 模块内**无条目数/字节上限校验**；仅依赖模块外 HTTP 层 body 上限 → **发现 C5-002（P2）** | 边界配置缺陷 |
| R3 | 扫描线程 ThreadLocalMap ← `CronExpression.threadLocalCal`（每实例一个 ThreadLocal） | planner/completion 每个调度周期对每个 cron/fixed-delay schedule 经 `JobTriggerCalculator` → `TriggerBuilder.buildTrigger`（L33 `new CronExpression`）新建实例；扫描线程（globalTimer/globalWorker，长寿命）ThreadLocalMap 遗留 stale entry | ThreadLocalMap 对 dead-key entry 惰性清理（后续 set/get 触发 expunge），**非单调增长**；无 monotonic 触发链 → **发现 C5-003（P2，理论风险标注）** | 理论风险（反模式，可自愈） |
| R4 | worker 单周期在途 promise 堆积（`JobWorkerScannerImpl.scanBatch` drain 循环） | 每周期最多 maxScanLoops(1000)×batchSize(100)=10 万个任务被认领并 invokeAsync（异步 promise 未决）；`max-concurrency=0`（默认）时无运行时并发上限 | maxScanLoops×batchSize 为**配置可调的硬上限**（默认 10 万），count ceiling 仅在 `max-concurrency>0` 时生效 → **发现 C5-004（P2）** | 边界内高水位（默认配置下可达） |
| R5 | `LocalJobScheduler.jobs`（ConcurrentHashMap，nop-job-local） | addJob 注册的 job；非 once 任务到达 COMPLETED/FAILED 后仍驻留 | 键空间 = 应用/config 注册的 jobName（可枚举、有限）；有 removeJob/deactivate API；owner doc 声明"作业定义只存在于内存，重启即丢失" | 有界（声明结构，不立案） |
| R6 | `DefaultWorkerLoadProvider.scanCache`（ThreadLocal Map） | beginScan..endScan 作用域按 serviceName 缓存 | 每次 scan 清空（beginScan clear / endScan remove）；value 按 discovery 实例数（有限拓扑） | 有界（不立案） |
| R7 | `loadReservedByWorker` 返回 Map / `sumReservedCostByWorker` 全行载入 | GROUP BY workerInstanceId 的聚合行数 | 行数 ≤ 存活 worker 实例数（服务发现拓扑有限）；聚合 SQL 非 N 行 task 明细 | 有界（不立案） |
| R8 | task builders 产出的 `List<NopJobTask>`（broadcast/partition/adaptive） | 每 fire 的 task 数 | broadcast/partition = 健康实例数、partition ≤ min(partitionCount, 实例数)、bestFit 默认 1（LeastLoadedStrategy 返回单 assignment）；`findTasksByFireId` 无 LIMIT 但 dispatch 期已定界（JobFireStoreImpl L309-318 注释为声明边界） | 有界（声明结构，不立案） |
| R9 | 5 个 scanner（Planner/Dispatcher/Completion/TimeoutChecker/Worker）+ Reconciler 的批查询 | WAITING fire / due schedule / RUNNING task 行数 | 全部 `query.setLimit(batchSize)`（JobTaskStoreImpl L73/L121/L188、JobFireStoreImpl L56/L75/L246、JobScheduleStoreImpl L57）；循环上限 maxScanLoops=1000（AbstractBatchScanner L93-103）；cursor 分页 drain；`findActiveFires` 另有 ACTIVE_FIRES_QUERY_LIMIT=500 防御上限（JobScheduleStoreImpl L42/L379-386） | 有界（不立案） |
| R10 | 触发器/日历迭代（PauseCalendarTrigger、AnchorRepeatTrigger、HolidayCalendar、CronExpression 内部循环） | 病态 cron / 空洞日历导致的迭代次数 | 硬上限：MAX_TRY_COUNT=10000（PauseCalendarTrigger）、MAX_SCAN=36600（AnchorRepeatTrigger）、MAX_DAY_SCAN=366×5（HolidayCalendar）、`_NopJobCoreConstants` loop-limit 错误码兜底；迭代为栈上计算不留驻留结构 | 有界（不立案） |
| R11 | calendar 排除日集合（HolidayCalendar.excludedDays TreeSet、AnnualCalendar.excludeDays） | 排除日数量 | config/`CalendarSpec` 驱动（启动期构造，键空间有限） | 有界（声明结构，不立案） |
| R12 | `JobLogReporter` / `NopRetryJobRetryBridge` 异步提交 | 每次调用一个 future/HTTP 请求 | 无队列、无缓冲账本——纯 fire-and-forget，失败仅 WARN；不驻留 | 有界（不立案） |
| R13 | 全模块线程池使用 | — | 未自建任何线程池；统一走平台 `GlobalExecutors.globalTimer()/globalWorker()` 与注入的 `nopRpcPollScheduledExecutor`（池大小 poll-threads=2）；每 scanner 各注册 1 个 fixed-delay 句柄（≤6 个） | 有界（不立案；句柄级放大并入 C5-001） |
| R14 | DB 侧 `nop_job_task_log` 表行数 | reportTaskLog 持续上报 | 内存面之外——由 DB 容量与清理策略约束（模块内无 TTL/清理代码） | 单独标注（见覆盖度） |

---

## 发现条目

### [nop-job-C5-001] RpcPollTaskManager 注册表对"远程已失联且无 per-schedule 超时"的条目永不回收——长时运行必然无界增长（含全局 execution-timeout-ms 未参与 deadline 的口径缺口）

1. **严重度**：P1（长时运行必然无界增长；非 P0——单条目驻留为 KB 级，OOM 需长期累积大量失联条目，但增长本身无条件收敛）
2. **缺陷族**：F4 无界账本只增不删 + F7 OOM 同族（无界注册表）；叠加句柄级放大（每泄漏条目 = 1 个永久 fixed-delay 调度句柄 + 每 5s 一次 RPC churn）
3. **位置**：
   - `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java` L65（entries 注册表）、L105-128（register）、L175-207（pollOnce，L196-200 瞬态失败仅 warn 忽略）、L259-265（computeDeadline 仅读 schedule.timeoutSeconds）
   - 关联：`RemoteJobInvoker.java` L111（注册点）、L121-141（cancelAsync 仅远程 cancelJob，不 complete future）；`DefaultJobCancelHandler.java` L26-49（cancelRunningTask 只调 invoker.cancelAsync）
4. **证据片段**：
   ```java
   // RpcPollTaskManager L65
   private final ConcurrentHashMap<String, PollEntry> entries = new ConcurrentHashMap<>();
   // L259-265：deadline 只看 schedule.timeoutSeconds，全局 executionTimeoutMs(-1 默认) 不参与
   private static long computeDeadline(NopJobSchedule schedule) {
       Integer timeoutSeconds = schedule != null ? schedule.getTimeoutSeconds() : null;
       if (timeoutSeconds != null && timeoutSeconds > 0) {
           return CoreMetrics.currentTimeMillis() + timeoutSeconds * 1000L;
       }
       return 0L;   // 0 = 无墙钟上限
   }
   // pollOnce L196-200：远程不可达 → 每轮 transient warn 后继续下一轮，无轮次上限
   if (err != null) {
       LOG.warn("nop.job.remote.poll-failed:taskId={}", entry.getTaskId(), err);
       return;   // 连续失败由 TimeoutChecker 墙钟兜底 —— 但 DB 侧兜底不会 resolve 本条目
   }
   ```
5. **触发链（输入规模 × 路径 × 驻留结构）**：`executorKind=rpcPoll` 且 `schedule.timeoutSeconds` 未配置（deadline=0，无任何兜底上限）的 schedule × worker 实例在任务执行中崩溃/下线（滚动发布、宕机——常规运维事件；远程 getJobStatus 变为传输错误，命中 pollOnce 瞬态忽略分支）× DB 侧 worker-liveness 链/TimeoutChecker 把 task 行置 TIMEOUT 并经 `DefaultJobCancelHandler.cancelRunningTask → RemoteJobInvoker.cancelAsync` 发起远程 cancelJob（worker 已死，best-effort 失败即放弃；该链路**从不 complete** `RemoteJobInvoker.invokeAsync` L83 创建并注册进 manager 的 future）× 驻留结构 = `entries` 中该 taskId 的 PollEntry（捕获的 NopJobSchedule[含 jobParams JSON 组件] + NopJobFire + NopJobTask 三实体、fixed-delay 轮询句柄、cancelToken 上的 onCancel 监听器）永久驻留。每次 worker 失联事件泄漏 1 条目；条目数随失联任务数单调增长、无任何回收路径。次级泄漏窗口：register L119 `scheduleWithFixedDelay` 在 executor 停机/拒绝时抛出 → 条目已入 map 但无轮询句柄，future 若永不 complete 则同样永久驻留。
6. **声明上界 / 由谁保证**：无上界。类注释（L56-57）声称"状态权威仍在 DB（scanner writeback + TimeoutChecker 维护），认领者崩溃后轮询自然停止，由 TimeoutChecker 墙钟超时兜底回收"（RemoteJobInvoker L41）——但 DB 侧回收只改 task 行状态，与内存注册表无联动；文档化的三层超时（owner doc：`schedule.timeoutSeconds > executionTimeoutMs > 不超时`）中第二层在 `computeDeadline` 完全缺失，导致按文档配置了全局 `nop.job.coordinator.execution-timeout-ms` 的部署仍得不到内存面保护。
7. **判定**：包络外无界增长缺陷。owner doc 声明的默认执行超时为禁用（execution-timeout-ms=-1），rpcPoll 是模块正式特性（app-engine.beans.xml 内置装配），worker 失联属声明包络内的正常运维输入 → 满足 P1。
8. **影响面**：coordinator 进程内存单调增长（每条目约数 KB：三实体 + future 链 + 句柄）；nopRpcPollScheduledExecutor（poll-threads=2）或 globalTimer 上的任务句柄数同步增长，每失联任务永久产生 5s 周期 × 1 次注定失败的 RPC（网络与日志 churn：每条每日 ~17k 条 WARN）；长期运行（月级、频繁发布/宕机场景）可达万级条目、数十 MB 常驻 + 持续 RPC 风暴。
9. **修复方向**：(a) `computeDeadline` 纳入全局 `executionTimeoutMs`（对齐 owner doc 三层超时口径，schedule.timeoutSeconds 优先）；(b) PollEntry 增加最大轮询轮数/最大墙钟年龄兜底（超限 resolve `ERR_JOB_TIMEOUT` 并移出）；(c) TimeoutChecker/Completion 将 task 终态写回时联动 manager（如按 taskId 反查注册表并 complete future）。(a)+(b) 任一即可闭合无界性。
10. **误报校准核查**：非"有界声明结构"（无任何上限声明/配置）；非低频路径（worker 失联是分布式调度常规事件）；非平台设施自身实现（RpcPollTaskManager 为 nop-job-coordinator 手写代码）；键空间不可枚举（taskId 随任务产生，不可枚举）。
11. **置信度**：高（注册/清理/超时三链路代码全部核读，cancelAsync→future 不 complete 已逐行确认）。
- **复核状态**: 已保留（P1 维持。注册/清理/超时/取消四链路逐点重验成立：entries L65 无界、computeDeadline L259-265 仅读 schedule.timeoutSeconds（0=无墙钟上界）、pollOnce L196-200 瞬态失败无轮次上限、RemoteJobInvoker L121-141 与 DefaultJobCancelHandler L26-49 均不 complete future；全仓 grep 证实除 RemoteJobInvoker 外无任何组件回连 manager——DB 回收与内存注册表确无联动；register 次级窗口（L109 先 put、L119 调度失败则 L123 whenComplete 尚未挂接）成立。与 C4-001/C7-001 同机制三视角归并方向一致（C4 复核已声明，三方判级均 P1 自洽）。现状：新发现——rpcPoll 特性 2026-08-28（commit 5310efaf97）引入，晚于全部 nop-job 历史审计；历史 P1 修复 72b770b094 系"每次执行泄漏空转调度句柄"反方向，非本条机制）

### [nop-job-C5-002] reportTaskLog 批量上报端点无条目数上限——单请求全量物化 + 单事务逐条双实体加载

1. **严重度**：P2（边界配置缺陷：模块内无上限校验，峰值受模块外 HTTP body 上限间接约束）
2. **缺陷族**：F7 同族（全量载入 vs 流式/分批）+ 请求批无界
3. **位置**：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskLogBizModel.java` L72-86（reportTaskLog）、L100-112（saveEntry）、L114-133（fillRedundantColumns）
4. **证据片段**：
   ```java
   @BizMutation
   public int reportTaskLog(@Name("entries") List<TaskLogEntry> entries, IServiceContext context) {
       if (entries == null || entries.isEmpty()) { return 0; }
       Set<String> loadedTaskIds = new HashSet<>();
       for (TaskLogEntry entry : entries) {      // N 无上限；body 已整体物化为 POJO List
           validate(entry);
           ...
           saveEntry(entry);                     // 每条 save + fillRedundantColumns 内 1-2 次 DB load
       }
       return entries.size();
   }
   ```
5. **触发链（输入规模 × 路径 × 驻留结构）**：单个 `/r/NopJobTaskLog__reportTaskLog` 请求携带 N 条 `TaskLogEntry`（每条 logMessage 任意长字符串 + logPayload 任意嵌套 Map）× 反序列化将整个 list 物化在请求线程堆上（峰值 ≈ N × 单条大小）× 循环内 `fillRedundantColumns` 对每条再加载 NopJobTask + NopJobFire 两实体（事务内实体缓存随 N 增长），单事务时长随 N 线性膨胀。N 由调用方任意指定，模块内无 `entries.size()` 上限、无单条 payload 大小校验。
6. **声明上界 / 由谁保证**：owner doc 仅声明"批量接收"未声明上限；实际唯一约束是模块外 HTTP 服务器的 body size 上限（不属于本审计单元保证）。
7. **判定**：包络内峰值无模块级护栏 → P2。非 P1：正常 worker 上报为小批量、低频路径，OOM 需要异常/恶意超大请求（且通常先被 HTTP 层拦截）。
8. **影响面**：请求线程瞬时堆峰值 + 长事务 + 事务内 ORM 实体缓存膨胀；恶意/缺陷 worker 可放大 coordinator GC 压力。附带观察：L77 `loadedTaskIds` 集合只写不读（写入后仅做恒未命中的 contains 判断），属死代码且其本身随批内去重 taskId 数增长（有界于批大小）。
9. **修复方向**：增加批上限配置（如 `nop.job.log.report-max-batch-size`，超限抛 `nop.err.job.*` 显式失败）与单条 logMessage/logPayload 序列化后长度校验；删除或真正使用 `loadedTaskIds`（例如按 taskId 缓存 fillRedundantColumns 结果以减 DB 负载）。
10. **误报校准核查**：非平台设施（BizModel 为本模块手写）；非低频路径豁免——端点对所有 worker 开放，防御性上限属"边界配置缺陷"判级依据而非豁免。
11. **置信度**：高（上限缺失为直接代码事实；实际 OOM 需叠加模块外条件，故 P2）。
- **复核状态**: 已保留（P2 维持。L72-86 无条目数/字节上限、L100-112 saveEntry + L114-133 fillRedundantColumns 每条 1-2 次 DB load 逐行核实；JobLogReporter 正常路径为单条 fire-and-forget 上报，佐证"非 P1"校准成立。证据微勘（不改判级）：L77 loadedTaskIds 的 contains 对批内重复 taskId 会命中，原条目"恒未命中"表述不准确，但命中结果仅门控一次冗余 add、无行为影响，"死代码"实质成立。现状：新发现——reportTaskLog 属 plan 2254 特性，历史审计无登记）

### [nop-job-C5-003] CronExpression 每实例内嵌一个 ThreadLocal&lt;GregorianCalendar&gt;，而触发器链在 planner/completion 每周期重建——长寿命扫描线程 ThreadLocalMap 遗留 stale entry

1. **严重度**：P2（反模式 + 暂态驻留可自愈；OOM 触发链写不出 monotonic 增长，按规则标注"理论风险，未具体化"）
2. **缺陷族**：F7 同族边缘（ThreadLocal 生命周期错配）；非 F4（无业务账本）
3. **位置**：
   - `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java` L87（`private ThreadLocal<GregorianCalendar> threadLocalCal`）、L130-135 / L146-155（两个构造器各自 `ThreadLocal.withInitial(...)`）、L194（`threadLocalCal.get()`）
   - 每周期重建点：`TriggerBuilder.java` L33（`new CronExpression(spec.getCronExpr())`）← `JobTriggerCalculator.java` L25 ← `JobPlannerScannerImpl.java` L223-227（每 due schedule 每次 scanBatch）与 `JobCompletionProcessorImpl.java` L343-348（每次 fixed-delay fire 完成）
4. **证据片段**：
   ```java
   // CronExpression L130-135：每个实例（含短命实例）都新建一个 ThreadLocal
   public CronExpression(String expression, TimeZone timeZone) {
       ...
       this.threadLocalCal = ThreadLocal.withInitial(() -> {
           GregorianCalendar cal = new GregorianCalendar();
           cal.setTimeZone(this.timeZone);
           return cal;
       });
   // JobPlannerScannerImpl L223-227：每 schedule × 每 scan 周期都走 buildTrigger → new CronExpression
   long next = JobTriggerCalculator.calculateNextFireTime(
           toTriggerSpec(schedule), toEvalContext(schedule),
           scheduleStore.getCurrentTime());
   ```
5. **触发链（输入规模 × 路径 × 驻留结构）**：batchSize(100)×maxScanLoops(1000) 个 due cron schedule × 每 5s 扫描周期 × `TriggerBuilder.buildTrigger` 每次新建 CronExpression（其内嵌 ThreadLocal 为全新对象）× 计算完成后 trigger 链整体不可达，但扫描线程（globalTimer→globalWorker，长寿命、不重建）的 ThreadLocalMap 中遗留 key 已死、value（GregorianCalendar，数百字节）仍被 Entry 强引用的 stale entry，直至同线程后续 `ThreadLocal.set/get` 触发启发式 expunge 才回收。
6. **声明上界 / 由谁保证**：无显式上界；JDK ThreadLocalMap 的惰性清理保证**最终**回收（非泄漏终局），故不构成单调增长——常驻残留量与同线程后续 ThreadLocal 活动频率相关，无法写出确定性的无界触发链。
7. **判定**：理论风险（未具体化）+ 分配 churn（每 CronExpression×每线程首次 get 各分配一个 GregorianCalendar，纯垃圾）。P2 定级主要依据反模式修复价值：触发器为无状态纯计算，日历对象完全可以栈上局部化。
8. **影响面**：扫描线程 ThreadLocalMap 暂态膨胀与 stale-entry 清理开销；高频 cron schedule + 高扫描频率时 GC churn 增大。LocalJobScheduler 路径（L83）每 job 仅 build 一次且实例与 job 同生命周期，不受此影响。
9. **修复方向**：`getTimeAfter` 内改为方法栈局部 `GregorianCalendar`（或 ThreadLocal 提升为类级共享并 `remove()` 于 finally——但 capture timeZone 决定了更自然是栈局部）；`isValidExpression` 用的私有构造器同修。
10. **误报校准核查**：非有界声明结构、非低频路径（planner 主循环）；判定为 P2 而非 P1 是因为 ThreadLocalMap 惰性 expunge 使残留不随时间单调上升——无法满足 P1"必然无界增长"的证据标准。
11. **置信度**：中高（ThreadLocal 惰性清理语义依 JDK 实现而异，残留峰值上界无法静态给出精确值——这正是降 P2 并标注理论风险的原因）。
- **复核状态**: 已保留（P2 维持。主链全部核实：CronExpression L87/L130-137/L146-156/L194 每实例内嵌 ThreadLocal、TriggerBuilder L33 每次 new、JobTriggerCalculator L25 无缓存、planner L223-227 每 due cron schedule 每周期重建；LocalJobScheduler L83 每 job 仅 build 一次不受影响。证据勘误（不改判级）：completion 侧 JobCompletionProcessorImpl L343-348 仅在 isFixedDelay 分支调用（L199-200/L360-361），fixed-delay spec 的 cronExpr 为空 → buildTrigger 产 PeriodicTrigger，并非 CronExpression 重建点——该引用从触发链剔除后，planner cron 路径单独足以支撑本条。现状：新发现——历史修复 72b770b094 的 CronExpression 项系私有构造器不变式（NPE 风险），非 ThreadLocal 反模式）

### [nop-job-C5-004] worker 默认 max-concurrency=0（不限）叠加 drain 循环：单周期可认领至多 10 万个任务并持未决 promise——峰值内存高水位仅由宽松默认值约束

1. **严重度**：P2（包络内高水位：上限存在但默认配置下可达 10 万级并发在途执行）
2. **缺陷族**：F7 峰值叠加（单请求有界 × 并发数）；峰值驻留而非账本
3. **位置**：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java` L119-122（maxConcurrency 默认 0）、L136-148（count ceiling 仅 maxConcurrency>0 生效）、L162-199（candidates 过滤与认领）、L212-227（批量 CAS 认领后逐个 executeTask，L227 满批续扫）、`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java` L50（maxScanLoops=1000）与 L93-103（drain 循环）
4. **证据片段**：
   ```java
   // JobWorkerScannerImpl L140-147：maxConcurrency=0（默认）时 count 上限完全旁路
   if (maxConcurrency > 0) {
       long runningCount = taskStore.countInFlightTasks(AppConfig.hostId());
       int remaining = maxConcurrency - (int) runningCount;
       if (remaining <= 0) { ... return false; }
       effectiveBatchSize = Math.min(batchSize, remaining);
   }
   // L227：满批即续扫——scanOnce 最多 maxScanLoops(1000) 轮 × batchSize(100)
   return !lockedTasks.isEmpty() && candidates.size() >= batchSize;
   ```
5. **触发链（输入规模 × 路径 × 驻留结构）**：DB 中积压 ≥ 10 万条 WAITING task（协调器停机/网络分区恢复后的典型 backlog）× worker 以默认 `max-concurrency=0`（owner doc：0=不限）运行 × drain 循环连续认领至多 1000×100=100,000 个任务，逐个 `executeTask` → `invokeAsync` 异步返回 promise（L297-303 whenComplete 挂回调），执行期间每任务驻留：invoker promise 链 + WorkerJobExecutionContext（含 params map）+ NopJobTask/Fire/Schedule 三实体引用 ≈ 数 KB → 单周期峰值驻留可达数百 MB 量级；完成回调再以 10 万级任务量涌入 globalWorker 池队列。
6. **声明上界 / 由谁保证**：maxScanLoops×batchSize 为配置硬上限（默认 100,000）；resource-based 检查（L151-159）仅在声明 capacity 的部署生效（未声明 = MAX_VALUE 退化 count-based，owner doc 声明 0=未设=无限）。即：上限是"默认宽松的可配置值"，非结构性保证。
7. **判定**：声明包络内的可达高水位 → P2（非缺陷级无界；backlog 消化确实是期望行为，但默认无并发闸门使峰值无护栏）。
8. **影响面**：积压恢复场景 worker 瞬时堆峰值 + 回调洪峰；叠加 `nop.job.worker.batch-size`/`max-scan-loops` 被调大时线性放大。
9. **修复方向**：文档与实现提示 backlog 场景应显式配置 `max-concurrency`；或为 max-concurrency=0 提供防御性默认（如按 promise 在途数软上限）；drain 循环在在途 promise 数超阈值时提前返回 false（本周期收尾，下周期继续）。
10. **误报校准核查**：非低频路径豁免（backlog 恢复是真实运维场景）；但上限结构存在（maxScanLoops×batchSize）且行为属"已声明的 0=不限"语义边界，故不上探 P1。
11. **置信度**：中（峰值估算基于实体/promise 尺寸量级推断；drain 上限与默认旁路为代码事实）。
- **复核状态**: 已保留（P2 维持。maxConcurrency 默认 0（L48/L119-122）、count ceiling 仅 >0 生效（L140-148）、capacity 未声明退化 MAX_VALUE（MetadataWorkerCapacityProvider L111-127 + NopJobCoreConstants.java L20）、drain 1000×100（AbstractBatchScanner L50/L93-103 + JobWorkerScannerImpl L227 满批续扫）、promise 无超时挂回调（L297-303）全部核实；与 owner doc 声明包络（batch-size=100 × maxScanLoops=1000、0=不限）一致，"已声明语义边界内的可达高水位"判级恰当。现状：新发现）

---

## 覆盖度与残留边界

**覆盖声明**：nop-job 全部 12 个 jar 子模块的 src/main/java 手写代码（164 files / 17,664 lines）已逐文件核读或定向核读；`_` 前缀生成文件（`_gen/*.java`、`_NopJob*.java` 常量骨架）仅核对引用关系不作为发现对象。随输入增长的容器全量盘点为 R1-R14（见清单），其中 R1-R4 立案为 C5-001~004，R5-R13 判定有界并给出保证者，R14 为 DB 侧单独标注。平台并发设施对照：全模块 grep `ICache|CacheConfig` 零命中——不存在"有上限缓存缺淘汰策略"面；唯一无界集合型驻留即 C5-001。触发器迭代循环（PauseCalendar/AnchorRepeat/Holiday/Cron）逐一核对硬上限常量，均有界。物化 vs 流式：全部 DB 访问为 batchSize 限页或 per-fire/per-entity 定界载入，无全表物化路径；`sumReservedCostByWorker` 为聚合 SQL（行数=worker 数）非明细载入。

**DB 侧单独标注（不与内存面混排）**：`nop_job_task_log` 随 reportTaskLog 上报持续增长，模块内无 TTL/归档/清理代码，也无表容量治理钩子；增长受 DB 容量约束，属容量治理课题（建议后续在 docs 中声明清理策略或提供清理 job）。

**残留边界（未展开项）**：
1. `nopRpcPollScheduledExecutor`/`GlobalExecutors` 为平台设施（nop-commons），其内部队列有界性属设施自身实现，仅审集成边界——C5-001 的句柄级放大以此为背景但不重复立案。
2. HTTP 服务器 body 上限、GraphQL 层深度/宽度限制在 nop-web/平台层，C5-002 的实际峰值受其间接约束。
3. `LocalJobScheduler` 的 addJob/update 竞态与 `RemoteJobInvoker` L115 catch 块内 `jobCtx.getAttributes()` 潜在 NPE、`future.whenComplete` 双回调窄窗竞态——均为 C3 形态且评估为 P2/P3 级，按维度集外线索登记规则（仅 P0/P1）不立案、不登记。
4. 本轮为静态盲审：C5-001 的泄漏速率、C5-004 的峰值水位未做运行时验证；建议修复 PR 附带最小复现测试（manager 条目数断言）。

**发现汇总**：C5-001（P1，RpcPollTaskManager 无界注册表）、C5-002（P2，reportTaskLog 批无上限）、C5-003（P2，CronExpression ThreadLocal 反模式/理论风险）、C5-004（P2，worker 默认无并发闸门的峰值高水位）。维度集外 P0/P1 线索：无。

---

## 维度复核结论（独立复核子代理）

- 复核子代理：C5 独立复核（与初审者不同角色，逐条验证/保留/降级/驳回，不补充新发现）
- 复核日期：2026-10-15
- 复核基线：仓库 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation，HEAD 335e77caec）live code 逐条重读；历史对照仅读 `ai-dev/audits/*nop-job*` 历史目录检索、同 campaign C4/C7 round 文件交叉节、`cra-index.md`、git log
- 复核输入：本文件（第 1 轮）+ `C5-round2.md`（第 2 轮），全文读取

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C5-001 | P1 | **保留** | 四链路逐点重验全部成立：entries 无界、computeDeadline 不读全局 execution-timeout-ms（默认 -1）、pollOnce 瞬态失败无轮次上限、cancelAsync/DefaultJobCancelHandler 均不 complete future，且全仓 grep 证实无任何 DB 侧组件回连 manager——反证尝试（远端重启经 NOT_FOUND 清理）仅覆盖服务重启子集，永久下线场景反驳不能 | 新发现（rpcPoll 特性 2026-08-28 引入，晚于全部 nop-job 历史审计；历史修复 72b770b094 系"每次执行泄漏空转句柄"反方向） |
| C5-002 | P2 | **保留** | 端点无条目数/字节上限为直接代码事实，正常路径（JobLogReporter 单条上报）佐证 P2 非 P1 校准成立；唯一瑕疵是 loadedTaskIds"恒未命中"措辞（批内重复 taskId 会命中，但无行为影响），不改判级 | 新发现（reportTaskLog 属 plan 2254，历史审计无登记） |
| C5-003 | P2 | **保留** | 核心机制（每实例 ThreadLocal + planner 每周期 new CronExpression + 长寿命扫描线程）全部核实，P2"理论风险"判级与惰性 expunge 语义一致；但 completion 侧引用（JobCompletionProcessorImpl L343-348）经核为 fixed-delay → PeriodicTrigger 路径、并非 CronExpression 重建点，属证据部分勘误（planner cron 路径单独足以支撑） | 新发现（历史修复 72b770b094 的 CronExpression 项系构造器不变式 NPE 风险，非 ThreadLocal 反模式） |
| C5-004 | P2 | **保留** | max-concurrency=0 默认旁路 count ceiling、capacity 未声明退化 MAX_VALUE、drain 1000×100 满批续扫全部逐行核实，与 owner doc 声明包络一致；"已声明语义边界内的可达高水位"判级准确，无反驳证据 | 新发现 |
| C5-005 | P2 | **保留** | 钉住链（PollEntry→三实体→JsonOrmComponent.jsonValue）与每 tick 重建、注册前必然解析全部逐行核实；"非独立无界机制、宿主为 C5-001"定性成立；OOM 门槛下移两个数量级系 2-5x 通用倍率推断（原条目已自声明未测量），方向成立、量级存不确定性 | 新发现（rpcPoll 特性，历史审计无登记） |

### 不确定项（显式列出）

1. **C5-001 泄漏可逆性依远端实现**：若失联 worker 后续重启且其 `getJobStatus` 对未知 taskId 返回 NOT_FOUND，对应条目可经 `ERR_JOB_REMOTE_TASK_LOST` 清理；服务永久下线/传输层持续失败场景不可逆。不影响 P1 判定（运维常规场景含永久下线）。
2. **C5-003 completion 侧证据勘误**：`JobCompletionProcessorImpl` L343-348 非 CronExpression 重建点（fixed-delay spec 的 cronExpr 为空 → PeriodicTrigger）；触发链以 planner cron 路径为准。
3. **C5-005 驻留量级为静态推断**：JSON 对象图堆开销 2-5x 倍率与"门槛下移两个数量级"未经运行时堆测量验证；建议修复 PR 附条目驻留体积断言。
4. **C5-002 loadedTaskIds 措辞**："恒未命中"不准确（批内重复 taskId 会命中 contains），实质（死代码、无行为影响）成立。

### 三视角归并与口径核对

C4 复核（C4-thread-pools.md C4-001 复核状态行）已声明"与 C5-001（P1 无界注册表视角）、C7-001（P1 泄漏+重试风暴视角）为同一机制的三维度视角，后续 7.1 合并裁定时应归并单立案，判级 P1 一致"——本次复核维持 C5-001 P1，三方自洽；C5-005 的叠加验算引用 C4-009 之事实（scanner promise 无超时、tryMarkTimeout 无配置即 return、RESERVED 状态集、容量默认 MAX_VALUE）经 live code 独立复核全部属实，引用边界（不重复立案）正确。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| nop-job-C5-001 | P1 | `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java`（L65/L105-128/L175-207/L259-265；关联 `RemoteJobInvoker.java` L111/L121-141、`DefaultJobCancelHandler.java` L26-49） | rpcPoll 轮询注册表对"远程失联且无 per-schedule 超时"的条目永不回收：deadline 仅读 schedule.timeoutSeconds（全局 execution-timeout-ms 不参与），DB 侧回收不联动内存 future，每失联事件永久泄漏 1 条目 + 1 个 5s 周期句柄 | 新发现 |
| nop-job-C5-002 | P2 | `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskLogBizModel.java`（L72-86/L100-112/L114-133） | reportTaskLog 批量端点无条目数/单条体积上限：单请求全量物化 + 单事务逐条双实体加载，峰值仅受模块外 HTTP body 上限间接约束 | 新发现 |
| nop-job-C5-003 | P2 | `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`（L87/L130-137/L146-156/L194；重建点 `TriggerBuilder.java` L33 ← planner `JobPlannerScannerImpl.java` L223-227） | 每实例内嵌 ThreadLocal 的 CronExpression 在 planner 每周期重建，长寿命扫描线程 ThreadLocalMap 遗留 stale entry——惰性 expunge 保证最终回收，属理论风险/反模式（completion 侧重建点引用已勘误剔除） | 新发现 |
| nop-job-C5-004 | P2 | `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`（L48/L119-122/L140-148/L227/L297-303；`nop-job-core/.../AbstractBatchScanner.java` L50/L93-103） | worker 默认 max-concurrency=0（不限）+ capacity 未声明退化 MAX_VALUE 时双闸门全旁路，单周期可认领至多 10 万任务并持未决 promise——声明包络内的峰值高水位 | 新发现 |
| nop-job-C5-005 | P2 | `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java`（L107-109）+ `DefaultRpcPollTaskClient.java`（L183-192）+ `nop-job-dao/.../NopJobTask.java`（L20-33）+ `nop-persistence/nop-orm/.../JsonOrmComponent.java`（L32/L55-62） | C5-001 失效面扩展：泄漏条目经三实体 JSON 组件缓存钉住完整解析后的参数对象图，单条目驻留 O(payload) 而非 KB 级，OOM 门槛显著下移（量级为静态推断）；修复并入 C5-001 同一 PR | 新发现 |

**复核计数**：保留 5（P1×1、P2×4）、降级 0、驳回 0。**需子项协同复核的高风险项**：C5-001——与 C4-001、C7-001 为同一机制三视角（三方均 P1），7.1 修复裁定阶段须归并单立案并核对三维度修复方向覆盖（deadline 纳入全局超时 / 条目年龄兜底 / DB 终态回写联动 / PollEntry 精简视图），避免修复只闭合单一视角。
