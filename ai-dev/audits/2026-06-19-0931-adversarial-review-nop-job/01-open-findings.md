# Adversarial Review: nop-job Module (Round 10)

**Date**: 2026-06-19
**Scope**: nop-job 模块——在 R1~R9（AR-1~AR-82）之后独立重审，**切入点是 R9（2026-06-04）之后合入的全新功能代码（Plan 211~215），这些代码从未被任何审计覆盖**。
**Approach**: 发现导向。先看 git 历史，发现 R9 后合入了 nop-job-local（Plan 211）、资源限制 ResourceVector（Plan 212）、dispatchMode 路由（Plan 213）、任务优先级（Plan 214）、Best-Fit 派发（Plan 215）五大特性，均为全新代码。从"新特性必带新 bug"的假设出发，重点审查 coordinator 派发/分配、worker 认领/资源限制、SQL 聚合、跨事务竞态。

**Heuristics used**: 组合爆炸测试（资源限制 × 归因过滤 × 单 worker）、异常路径侦探（null cost × 单 try 批处理）、10x规模运维（批处理隔离、缓存、索引）、IoC 侦探（bean 注入与死错误码）。

**Dedup**: R1~R9 已覆盖 AR-1~AR-82（cron/trigger、Calendar 系列、recovery/cancel 竞态、生命周期、workerInstanceId 身份链、批处理隔离、blockStrategy fallthrough、配置校验等）。本次聚焦 R9 后的新代码（Plan 211~215）。AR-76（scanner 配置校验）已在 dispatcher/worker 的 setter 中修复（`scanIntervalMs < 1000` 抛异常等），本报告不重报。

> **说明：本报告所有 P1 及以上发现均已由审查者亲自读源码复核关键前提**（NopIoC `@Inject` 自动注入行为、实体字段类型 `java.lang.Integer` 可空性、`RESERVED_TASK_STATUSES` 是否含 WAITING、SQL 文本）。**一处 explore 子代理的 P0 候选（`AdaptiveJobTaskBuilder.scheduleStore 未注入`）经复核为误报，已剔除，见文末"误报校准"**。

---

## 新发现汇总（AR-83 ~ AR-99）

### [AR-83] Worker reserved cost 重复计入自身归因的 WAITING 任务 → 大于 capacity/2 的任务永远无法被认领（资源限制特性的核心正确性缺陷）

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:178-203`；`nop-job/nop-job-core/src/main/java/io/nop/job/core/NopJobCoreConstants.java:40-44`
- **证据片段**:
  ```java
  // NopJobCoreConstants.java:40-44  —— worker 侧与 dispatcher 侧共用，含 WAITING
  List<Integer> RESERVED_TASK_STATUSES = List.of(
          TASK_STATUS_WAITING, TASK_STATUS_CLAIMED, TASK_STATUS_SUSPICIOUS, TASK_STATUS_RUNNING);

  // JobWorkerScannerImpl.java:178-203
  ResourceVector myCapacity = capacityProvider.getMyCapacity();
  ResourceVector myReserved = taskStore.sumReservedCost(AppConfig.hostId()); // 含本 worker 归因的 WAITING
  ResourceVector myRemaining = myCapacity.subtract(myReserved);
  ...
  List<NopJobTask> candidates = taskStore.fetchWaitingTasks(
          overfetchBatchSize, assignedPartitions, AppConfig.hostId(), enforceAttribution); // 候选含本 worker 的 WAITING
  for (NopJobTask task : candidates) {
      if (myRemaining.fits(new ResourceVector(task.getCostCpu(), task.getCostMemory()))) { // 候选自身 cost 又被减一次
          tasks.add(task);
      }
  }
  ```
- **严重程度**: P1
- **现状**: `RESERVED_TASK_STATUSES` 故意包含 WAITING（注释 §3.3.4 称"度量资源承诺含已派发未执行"），worker 侧 `sumReservedCost(hostId)` 把本 worker 归因的 WAITING 任务成本计入 `myReserved`。但 `fetchWaitingTasks` 返回的候选集**同样包含**这些已归因给本 worker 的 WAITING 任务（enforceAttribution 开关下返回 `workerInstanceId=self OR NULL`，关闭时返回全部）。随后 `myRemaining.fits(candidateCost)` 又把同一个候选的 cost 当作新增负载再校验一次。

  代数化：`myReserved` 已含候选 cost C，`myRemaining = capacity − (Σothers + C)`，再要求 `myRemaining >= C` ⇒ `capacity >= Σothers + 2C`。即**单个任务的 cost 被计算两次**。即便 worker 完全空闲（Σothers=0），任务 C 也必须满足 `C <= capacity/2` 才能被认领。

  例：capacity=4000m（4 核），任务 cost=3000m，worker 空闲。`myReserved=3000`、`myRemaining=1000`、`fits(3000)?` 否 → 任务永不被认领。worker 明明有 4 核、任务只需 3 核，却永远卡在 WAITING。
- **风险**: 一旦启用资源限制（声明 capacity，Plan 212 的核心卖点），任何 cost > capacity/2 的任务在单 worker 或 `enforceAttribution=true` 部署下永久卡死。整个资源限制特性对"大任务"失效。注释将"dispatcher 已派发未执行"与"worker 在途负载"两种语义塞进同一个状态集，是根因。
- **建议**: worker 侧 `sumReservedCost` 应排除 WAITING（用 `[CLAIMED, SUSPICIOUS, RUNNING]`，WAITING 尚未占资源）；或对候选中已归因给自身的任务，在 fits 校验前先从 `myReserved` 扣除其自身 cost。`countInFlightTasks` 已用 `[CLAIMED,RUNNING]`，可参照。
- **信心水平**: 确定（已验证 `ResourceVector.subtract` 允许负值、`fits` 逐维 `>=`、`RESERVED_TASK_STATUSES` 含 WAITING）
- **发现来源视角**: 组合爆炸测试（资源限制 × 单 worker 归因）

---

### [AR-84] Null task cost 触发 worker fit-check NPE → 终止整批 worker 扫描（影响所有 Plan 212 之前的存量 schedule）

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:200`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/_NopJobTask.java:326,1495`
- **证据片段**:
  ```java
  // JobWorkerScannerImpl.java:197-203 （在 scanOnce 的单个 try 内）
  for (NopJobTask task : candidates) {
      if (tasks.size() >= effectiveBatchSize) break;
      if (myRemaining.fits(new ResourceVector(task.getCostCpu(), task.getCostMemory()))) { // 自动拆箱 null → NPE
          tasks.add(task);
      }
  }
  // _NopJobTask.java:326   private java.lang.Integer _costCpu;          // 可空
  // _NopJobTask.java:1495  public final java.lang.Integer getCostCpu()  // 返回 Integer
  ```
- **严重程度**: P1
- **现状**: `ResourceVector(int, int)` 构造器接收原始类型 int，而 `task.getCostCpu()`/`getCostMemory()` 返回 `java.lang.Integer`（实体字段经核为 Integer，非 primitive int）。当值为 null 时自动拆箱抛 NPE。该 NPE 发生在 `scanOnce` 唯一的 `try`（`:162-219`）内的 for 循环中，因此**一个 null-cost 候选会令整批扫描被 `catch` 丢弃**，记为 `nop.job.worker.scan-failed`。该任务停留 WAITING，下一轮再次成为候选，worker 持续 NPE-loop。

  null cost 的来源：dispatcher `JobDispatcherScannerImpl.java:153` 执行 `task.setCostCpu(schedule.getTaskCostCpu())`，对任何未配置 `taskCostCpu` 的 schedule（即 Plan 212 之前的**全部存量 schedule**）写入 null（详见 AR-95）。注意此 NPE **不依赖 capacity 是否声明**——只要候选集中存在 null-cost 任务即触发，即使 capacity=MAX_VALUE。
- **风险**: 部署 Plan 212 代码后，所有存量 schedule 产生的 null-cost 任务会让 worker 对**所有**任务（含正常任务）的处理归零，直到该 null-cost 任务被手工清理。可用性灾难，且现象（scan-failed 日志）不易直接归因到 cost。
- **建议**: 在 worker fit-check 前对 null 归一为 0（`defaultIfNull`），或在 dispatcher 落库时强制写非 null（见 AR-95）。
- **信心水平**: 确定（已验证实体字段类型为 `java.lang.Integer`）
- **发现来源视角**: 异常路径侦探

---

### [AR-85] Worker 在 CLAIMED→RUNNING 转换时忽略 `updateTask()` 返回值 → 失去任务所有权后仍执行任务（重复执行风险）

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:240-250`
- **证据片段**:
  ```java
  // executeTask()
  runningTask.setTaskStatus(TASK_STATUS_RUNNING);
  runningTask.setStartTime(new Timestamp(now));
  runningTask.setWorkerInstanceId(AppConfig.hostId());
  ...
  taskStore.updateTask(runningTask);          // CAS 版本检查，返回 boolean，被忽略

  IJobExecutionContext ctx = executionContextBuilder.buildContext(schedule, fire, runningTask);
  try {
      var promise = invoker.invokeAsync(ctx);  // 无论上一步 CAS 是否成功都会执行
  ...
  // 对比 handleExecutionResult:316  boolean updated = taskStore.updateTask(task); if (!updated) { ... }
  ```
- **严重程度**: P1
- **现状**: `updateTask` 内部用 `tryUpdateManyWithVersionCheck`（CAS）。如果并发地超时检查器已把该任务从 CLAIMED 扫成 SUSPICIOUS（租约过期，见 `tryLockTasksForExecute` 的 `lockTimeoutMs` 截止），CAS 静默失败返回 `false`，但 `executeTask` **忽略返回值**继续调用 `invoker.invokeAsync`。结果：一个已不属于本 worker 的任务仍被调用。同一类内 `handleExecutionResult:316` 和 `completeTaskWithFailure:389` 都**检查**了返回值，这种不对称强烈表明此处是疏漏。
- **风险**: 同一任务可能被本 worker（检查器已把任务移交给他人后仍在跑）和下一个认领 SUSPICIOUS 的 worker **同时**执行。非幂等任务（发邮件、扣款、写外部系统）的正确性隐患。
- **建议**: `executeTask` 应检查 `updateTask` 返回值，CAS 失败则跳过 `invokeAsync`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-86] Dispatcher 单个 try 包裹整个 fire 循环 + no-fitting-worker 抛异常 + loadSchedule 抛异常 → 一个 fire 失败令其余 fire 卡在 DISPATCHING 最终超时失败（fail 而非 defer）

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:131-166`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java:78-82`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:363-364`
- **证据片段**:
  ```java
  // JobDispatcherScannerImpl.java:141,148-158 （tryLockFiresForDispatch 已把全部 locked fire 翻成 DISPATCHING）
  var locked = fireStore.tryLockFiresForDispatch(fires, AppConfig.hostId(), lockTimeoutMs);
  for (NopJobFire fire : locked) {                    // 无 per-fire try/catch
      IJobTaskBuilder builder = resolveTaskBuilder(fire);
      List<NopJobTask> tasks = builder.buildTasks(fire);   // (A) AdaptiveJobTaskBuilder 无 worker 时抛 ERR_JOB_NO_FITTING_WORKER
      NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId()); // (B) requireEntityById 找不到时抛 UnknownEntity
      ...
      fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
  }
  // JobScheduleStoreImpl.java:364  return scheduleDao().requireEntityById(jobScheduleId);  // 抛异常式加载
  ```
- **严重程度**: P1
- **现状**: `tryLockFiresForDispatch` 在一个事务内把**所有** locked fire 翻成 DISPATCHING（`JobFireStoreImpl:82-87`）。其后 per-fire 循环没有独立 try/catch。`AdaptiveJobTaskBuilder.buildTasks` 在无适配 worker 时**抛** `ERR_JOB_NO_FITTING_WORKER`（注释自称"不静默 fallback"），`loadSchedule` 用 `requireEntityById`（找不到即抛）。任一 fire[k] 抛异常 ⇒ fire[k+1..n] 本轮永不处理，停留在 DISPATCHING 且无 task，只能等 `JobTimeoutCheckerImpl.scanDispatchTimeouts` 在 `dispatch-timeout-ms`（默认 300000ms=5 分钟）后以 **TIMEOUT/FAILED** 回收，而非重试。
- **风险**: bestFit 模式下"所有 worker 当前满载"是正常瞬态，却令 builder 在第一个 fire 即抛出，整批吞吐归零，且把本应排队/推迟的 fire **判失败**。一个被删除/归档的 schedule 会让 `loadSchedule` 抛异常拖垮整批。
- **建议**: per-fire 包独立 try/catch（记日志后继续）；"无适配 worker"应让 fire 回到 WAITING/重试而非 5 分钟后超时失败。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维 + 异常路径侦探

---

### [AR-87] `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED` 是死代码：未知/未注册 dispatchMode 静默 fallback，掩盖配置错误

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:168-186,28`；`nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java:99-101`
- **证据片段**:
  ```java
  // JobDispatcherScannerImpl.java:168-186
  IJobTaskBuilder resolveTaskBuilder(NopJobFire fire) {
      String dispatchMode = fire.getDispatchMode();
      if (dispatchMode != null && !dispatchMode.isBlank() && !"single".equals(dispatchMode)) {
          String beanName = TASK_BUILDER_PREFIX + dispatchMode;
          Object bean = BeanContainer.tryGetBean(beanName);
          if (bean instanceof IJobTaskBuilder) return (IJobTaskBuilder) bean;
      }                              // ← 静默向下，不抛异常
      String executorKind = fire.getExecutorKind();       // 再按 executorKind 找
      if (executorKind != null && !executorKind.isBlank()) {
          ... if (bean instanceof IJobTaskBuilder) return (IJobTaskBuilder) bean;
      }
      return defaultTaskBuilder;                           // ← 用户写 "bestFit" 但 bean 缺失 → 静默变单例
  }
  // JobCoreErrors.java:99  ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED = define("nop.err.job.dispatch-mode-not-implemented", ...)
  //   —— 专为该场景定义，import 到 JobDispatcherScannerImpl:28，但全仓库无任何抛出点
  ```
- **严重程度**: P1
- **现状**: 错误码 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED` 被定义、import，但**从未被抛出**（全仓库 grep 仅出现在错误码文件、未使用 import 与历史日志中）。开发日志（`ai-dev/logs/2026/06-18.md:42`）记录"bestFit 在 Plan 215 前抛此异常"，意图是 fail-fast，但实现变成了静默 fallback：用户配 `dispatchMode="bestFit"` 但 bean 未注册（模块未加载、bean 名拼错、测试配置不全）时，静默退化为单例派发，无任何告警。
- **风险**: 掩盖 P0/高优配置错误。声明的契约（错误消息明确列出 single/partition/broadcast）与实际行为（静默兜底）矛盾，调试困难。
- **建议**: 恢复 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED` 抛出（或至少记 ERROR 日志），让误配显性失败。
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探

---

### [AR-88] 无 WAITING-task 恢复路径：超时检查器只处理 RUNNING/CLAIMED/SUSPICIOUS，WAITING 任务永久滞留（放大 AR-83）

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:231-249`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`（`fetchRunningTasks` 只取非 WAITING）
- **证据片段**:
  ```java
  // JobTimeoutCheckerImpl.tryMarkSuspiciousIfWorkerGone
  private void tryMarkSuspiciousIfWorkerGone(NopJobTask task, Set<String> aliveWorkerIds) {
      if (task.getTaskStatus() == null || task.getTaskStatus() != TASK_STATUS_RUNNING) {
          return;                                   // WAITING 永不处理
      }
      ...
  }
  ```
- **严重程度**: P2
- **现状**: 超时检查器只把 RUNNING/CLAIMED/SUSPICIOUS 标记可疑/超时；`scanDispatchTimeouts` 只针对 DISPATCHING 状态的 **fire**。一旦 fire 已推进到 RUNNING（task 已插入），其中卡住的 WAITING task 既无超时回收、也无 worker 探活兜底（SUSPICIOUS 探活仅对 RUNNING）。
- **风险**: 与 AR-83 形成连锁——被 double-count 卡死的 WAITING 任务无人认领也无人回收，永久滞留并持续污染 `sumReservedCost`（让 worker 的 myReserved 虚高，进一步压制认领）。归因给已下线 worker 的 WAITING 任务（bestFit 模式）同样永久滞留。
- **建议**: 为 WAITING 任务增加"派发超时"回收，或在 worker 标记 unhealthy 时重分配其 WAITING 任务。
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试

---

### [AR-89] `SingleBestFitStrategy` 实现的是 worst-fit/spread（选最闲），却命名为 Best-Fit（应选最紧）——名称/文档与实现矛盾

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/SingleBestFitStrategy.java:36-41`；`ResourceVector.loadScore`
- **证据片段**:
  ```java
  // SingleBestFitStrategy.java:36-41  在 available.fits(taskCost) 的候选中选 loadScore 最【小】的
  int cmp = Double.compare(load.loadScore(), best.loadScore());
  if (cmp < 0 || (cmp == 0 && ...instanceId... < 0)) { best = load; }
  // loadScore = max(reserved.cpu/capacity.cpu, reserved.memory/capacity.memory)  即【利用率】
  ```
- **严重程度**: P2
- **现状**: 类名 `SingleBestFitStrategy`、Plan 215 文档与 bean id `nopJobTaskBuilder_bestFit` 均称"Best-Fit"。但装箱（bin-packing）意义上的 best-fit 应选**能放下且最紧**的 worker（最高利用率），以保留大块连续空闲给大任务；本实现选**利用率最低**的（最闲），是 **worst-fit / spread**。结果是把容量碎片化铺到所有 worker：(a) 大任务可能放不下（即便总空闲足够）；(b) 与"best-fit 派发"目标相反。作为负载均衡启发式没问题，但名实不符会误导运维与后续开发。
- **风险**: 命名误导；packing 目标未达成；后续若有人按"best-fit"语义推断行为会得出错误结论。
- **建议**: 要么改名（如 `LeastLoadedStrategy`/`SpreadStrategy`）并对齐文档，要么把比较方向改为选 `loadScore` 最大（真正的 best-fit）。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（名实不符）

---

### [AR-90] config "0"=无限容量 vs metadata "0"=零容量黑洞；负数容量无校验 → 静默禁用 worker

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/capacity/MetadataWorkerCapacityProvider.java:42,104-109,130-141`
- **证据片段**:
  ```java
  private static final int UNSET = 0;
  @InjectValue("@cfg:nop.job.capacity.cpu|0") public void setConfigCpu(int c){ this.configCpu = c; }
  ...
  if (cpu == null && configCpu != UNSET) { cpu = configCpu; }   // config=0 视为未设 → MAX_VALUE（无限）
  ...
  private Integer parseFromMetadata(Map<String,String> metadata, String key) {
      String raw = metadata.get(key);
      if (raw == null || raw.isBlank()) return null;
      return Integer.parseInt(raw.trim());                       // metadata "0" → 0（真实零，非未设）
  }
  ```
- **严重程度**: P2
- **现状**: 同一个 `0` 在两条路径上语义相反：config 路径把 `0` 当"未设"→ 回退 `MAX_VALUE`（无限容量）；metadata 路径把 `"0"` 解析为真实零 → capacity={0,...} → `available = 0 - reserved ≤ 0` → `isZeroOrNegative` → worker 早期 return 拒绝一切（`JobWorkerScannerImpl:181`）。用户在某处填 `0` 期望"关闭限制"，在另一处得到"黑洞 worker"。此外负数（config `-1` 或 metadata `"-1"`）无任何校验，capacity={-1} 同样让 worker 拒绝一切，无任何告警。
- **风险**: 难以诊断的 worker 静默停摆；config/metadata 语义分叉是陷阱。测试覆盖了空串/缺失/非数字，但**未覆盖字面量 "0"**。
- **建议**: 统一 `0` 语义（建议 `0` 一律视为未设→MAX_VALUE，或一律视为真实零并文档化）；对负数抛 `NopException`。
- **信心水平**: 确定
- **发现来源视角**: 灯下黑（同值异义）

---

### [AR-91] `enforceAttribution=true` 会让所有 single-dispatch 任务在非同地部署（coordinator≠worker）下饥饿

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:61-77`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobTaskBuilder.java:27-33`；`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:188-191`
- **证据片段**:
  ```java
  // DefaultJobTaskBuilder:27  task.setWorkerInstanceId(AppConfig.hostId());  // 协调器自己的 hostId
  // fetchWaitingTasks(enforceAttribution=true):
  query.addFilter(FilterBeans.or(
          FilterBeans.eq(PROP_NAME_workerInstanceId, workerInstanceId),   // worker 的 hostId
          FilterBeans.isNull(PROP_NAME_workerInstanceId)));
  ```
- **严重程度**: P2
- **现状**: `DefaultJobTaskBuilder`（single/默认模式）把 `workerInstanceId` 设为**协调器**的 `AppConfig.hostId()`。当分离部署的 worker 开启 `nop.job.fetch.enforce-attribution=true` 时，SQL 过滤变为 `workerInstanceId=workerHostId OR NULL`。single 任务带的是 coordinator hostId ≠ worker hostId，也非 NULL，因此**没有任何 single 任务能被认领**，永久 WAITING。注释（DefaultJobTaskBuilder:27）承认"同地部署假设"，但新的 enforceAttribution 过滤把这个隐含耦合变成了硬饥饿。
- **风险**: 分离 coordinator/worker 部署（分布式模式的合法用法）+ enforceAttribution 开关 = 静默死锁，无任何校验拒绝这种组合。
- **建议**: single 模式下让 `DefaultJobTaskBuilder` 不写 workerInstanceId（留 NULL 以走 IS NULL 分支），或在 dispatchMode∈{single,null} 时拒绝/告警 enforceAttribution=true。
- **信心水平**: 确定（与 R9 AR-71 同一根因 workerInstanceId=coordinator host，但此处是新 enforceAttribution 过滤放大的独立饥饿路径）
- **发现来源视角**: 组合爆炸测试

---

### [AR-92] 优先级排序缺少支持索引（热路径 filesort）+ priority NULL 排序在 MySQL/Oracle/PostgreSQL 之间不一致

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:72-76`；`nop-job/model/nop-job.orm.xml:462-475`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/_NopJobTask.java:335`
- **证据片段**:
  ```java
  // JobTaskStoreImpl.fetchWaitingTasks
  query.addOrderField(PROP_NAME_priority, true);     // priority DESC
  query.addOrderField(PROP_NAME_createTime, false);  // create_time ASC
  // 现有索引：IX_NOP_JOB_TASK_RUN_SCAN (taskStatus, partitionIndex, createTime) —— 无 priority
  ```
- **严重程度**: P2
- **现状**:
  1. **无索引**：过滤 `taskStatus=WAITING AND partitionIndex IN(...)` 后按 `priority DESC, createTime ASC` 排序，无任何索引以 priority 开头或在 (taskStatus,partitionIndex) 之后含 priority → filesort。每个 worker 每 `scan-interval`（默认 5s）overfetch `3×batchSize` 行，高负载下成热点。建议索引 `(taskStatus, partitionIndex, priority, createTime)`。
  2. **NULL 排序分叉**：`_priority` 字段为 `java.lang.Integer`（可空）。ORM 声明 defaultValue=0，但 dispatcher 经 `task.setPriority(schedule.getPriority())` 显式赋值，存量/未配置行实际为 NULL。不同 DB 对 NULL 排序默认不同：MySQL NULL 最小（DESC→NULL 在后），Oracle/PostgreSQL NULL 最大（DESC→NULL 在前）。ORM 模型明确 `ext:dialect="mysql,oracle,postgresql"`，同一代码三库行为不一致。
- **风险**: 性能退化随负载放大；跨库调度顺序静默分叉。
- **建议**: 增加复合索引；落库前把 priority null 归一为 0（见 AR-95），或显式 `NULLS LAST`。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（索引）+ 组合爆炸（NULL 分叉）

---

### [AR-93] Worker 客户端 overfetch fit 过滤会把可执行的低优先级任务饿死在窗口之外，且无任何升级日志

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:189-207`
- **证据片段**:
  ```java
  int overfetchBatchSize = effectiveBatchSize * OVERFETCH_FACTOR;   // 3 × batchSize
  List<NopJobTask> candidates = taskStore.fetchWaitingTasks(overfetchBatchSize, ...);  // priority DESC 取前 3N
  ...
  for (NopJobTask task : candidates) {
      if (myRemaining.fits(...)) { tasks.add(task); }
  }
  if (tasks.isEmpty()) { return; }   // ← 无 worker 能 fit 时无日志/无指标/无升级
  ```
- **严重程度**: P2
- **现状**: overfetch 只返回按 priority DESC 排序的前 `3×batchSize` 行。若这些任务对本 worker 都太大，本 worker 认领 0 条直接返回；**排在窗口之外的、低优先级但可执行的任务永远到不了本 worker**。高成本任务占满窗口时，可行任务无限排队。且 `tasks.isEmpty()` 分支静默 return，无日志/指标（对比资源耗尽分支 `:182` 有 `LOG.warn`），停滞无法诊断。
- **风险**: 资源受限集群下的调度饥饿 + 可诊断性黑洞。
- **建议**: 饥饿升级（窗口外二次扫描、或按 cost 而非纯 priority 取窗口）；`tasks.isEmpty()` 时记 WARN/指标。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-94] Reserved-cost 读与 task 插入分处不同事务 → 多 coordinator 跨事务 check-then-act 过度派发

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:122-158`；`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:90-109`；`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml:28-40`
- **证据片段**:
  ```java
  // reserved 读在外层 @SingleSession doScan（AdaptiveJobTaskBuilder.buildTasks → DefaultWorkerLoadProvider.getWorkerLoads → sumReservedCostByWorker，无锁）
  // 插入在独立 REQUIRES_NEW 事务：
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {
      for (NopJobTask task : tasks) { taskDao().saveEntityDirectly(task); }  // 无容量复检、无行锁
  }
  ```
- **严重程度**: P2
- **现状**: reserved 读取（EQL `sumReservedCostByWorker`，无 @Transactional 无锁）与 task 插入（REQUIRES_NEW）不在同一事务。fire 锁（`tryLockFiresForDispatch`）只串行化 fire，不串行化 worker 容量不变量。两个 coordinator 处理不同分区锁不同 fire，都读到 worker W 同一份 reserved 快照（双方都未插入前），都判定"W 有余量"，都向 W 插入任务 ⇒ W 被超额派发。单 coordinator 因顺序执行+每次 REQUIRES_NEW 提交后重读，安全；多 coordinator 不安全。
- **风险**: 与 AR-83 连锁——dispatcher 经竞态超额派发 W，超额任务（归因 W）又因 double-count 永不被 W 认领、也无回收（AR-88），永久丢失。
- **建议**: worker 侧强制（本应有 AR-83 修复后承担）；或在派发决策与插入间加 worker 行锁/乐观复检。
- **信心水平**: 很可能（多 coordinator 场景）
- **发现来源视角**: 事务边界追踪者

---

### [AR-95] Dispatcher 在 builder 已设 cost/priority 之后又重新覆盖一遍 → 冗余，且把可空 schedule 值强制写回（重新引入 NULL，是 AR-84 的根因）

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:148-156`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java:93-97,110-111`
- **证据片段**:
  ```java
  // JobDispatcherScannerImpl.scanOnce:148-156
  for (NopJobFire fire : locked) {
      IJobTaskBuilder builder = resolveTaskBuilder(fire);
      List<NopJobTask> tasks = builder.buildTasks(fire);       // builder 已据 schedule 设好 cost/priority
      NopJobSchedule schedule = scheduleStore.loadSchedule(...); // 又加载一遍同一个 schedule
      for (NopJobTask task : tasks) {
          task.setCostCpu(schedule.getTaskCostCpu());          // 覆盖，可空
          task.setCostMemory(schedule.getTaskCostMemory());
          task.setPriority(schedule.getPriority());            // 覆盖，可空
      }
      fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
  }
  ```
- **严重程度**: P2
- **现状**:
  1. **冗余**：`AdaptiveJobTaskBuilder.buildTasks`（`:93-97`）已根据同一 schedule 设过 `costCpu/costMemory/priority`，dispatcher 又加载同一 schedule 重设一遍。两处逻辑一旦不同步，后者静默胜出，是维护陷阱。
  2. **重新引入 NULL**：builder 的 `resolveCost`（`:110-111`）对 null 归一为 0（决策用 0），但 dispatcher 的覆盖用**原始可空** `schedule.getTaskCostCpu()`，把 null 强制写回 task 行——这是 AR-84（worker NPE）与 `sumReservedCost` 低估（SQL `SUM` 忽略 NULL 行，见 `NopJobTask.sql-lib.xml:16`）的直接根因。
- **风险**: 冗余 + 数据不一致 + AR-84 根因。
- **建议**: 删除 dispatcher 的二次覆盖，由 builder 统一负责（并在 builder 内归一 null→0）；或覆盖前 defaultIfNull。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（根因回溯）

---

### [AR-96] 每个 fire 都重新查服务发现 + GROUP-BY 聚合 SQL，无 per-scan 缓存（batchSize=100 时 100 次发现查询 + 100 次聚合）

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultWorkerLoadProvider.java:49-77`；`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:148-150`
- **证据片段**:
  ```java
  // AdaptiveJobTaskBuilder.buildTasks 每 fire 调一次 loadProvider.getWorkerLoads(serviceName)
  // DefaultWorkerLoadProvider.getWorkerLoads:
  List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);  // 每 fire 一次
  Map<String, WorkerReservedCost> reservedMap = loadReservedByWorker();          // 每 fire 一次全表 GROUP BY
  ```
- **严重程度**: P3
- **现状**: `getWorkerLoads` 被 `AdaptiveJobTaskBuilder.buildTasks` 每 fire 调一次，每次都重新查服务发现 + 跑全表 `GROUP BY workerInstanceId` 聚合（`NopJobTask.sql-lib.xml:31-39`，无分区过滤、无时间窗）。`batchSize=100` 时一次 dispatcher 扫描发出 100 次发现查询 + 100 次聚合查询。
- **风险**: 10x 规模下派发热路径的开销放大。
- **建议**: 每 `scanOnce` 快照一次 worker loads（缓存到本扫描周期）。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-97] `ResourceVector.add` 静默 int 溢出；SQL `SUM(costCpu)` 映射到 Integer（跨 worker 聚合溢出风险）

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/resource/ResourceVector.java:58-60`；`nop-job/nop-job-dao/src/main/resources/_vfs/nop/job/sql/NopJobTask.sql-lib.xml:16-17,33-34`
- **证据片段**:
  ```java
  public ResourceVector add(ResourceVector other) {
      return new ResourceVector(this.cpu + other.cpu, this.memory + other.memory);  // int+int 静默回绕
  }
  // sql-lib: sum(o.costCpu) as cpu —— 列为 INTEGER，结果映射 Integer
  ```
- **严重程度**: P3
- **现状**: `add` 是 `int+int` 静默回绕（`MAX_VALUE.add(任意正)` 变负）。当前生产路径聚合走 SQL `SUM`，`add` 仅测试用到，故 P3。但 SQL `SUM`（INTEGER 列）映射到 `Integer`，跨 worker 大规模聚合理论上有溢出/DB 行为差异（MySQL 可能返回 NULL/错误，PG INTEGER sum 可能报错）。
- **风险**: 当前概率低，但是潜在陷阱。
- **建议**: `Math.addExact` 或 BIGINT cast 防御。
- **信心水平**: 确定（缺陷真实，影响低）
- **发现来源视角**: 异常路径侦探

---

### [AR-98] `PartitionTaskBuilder` 用 `shortRange()[0,32766]` 划分，但 `partition_index` 是 SMALLINT（max 32767）→ 哈希到 32767 的数据无 task 覆盖（边界 off-by-one）

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/PartitionTaskBuilder.java:93-94`；`nop-job/model/nop-job.orm.xml:409-411`
- **证据片段**:
  ```java
  // PartitionTaskBuilder:93
  List<IntRangeBean> ranges = partitionAssigner.assignPartitions(IntRangeBean.shortRange(), selected);
  // IntRangeBean.shortRange() = intRange(0, Short.MAX_VALUE)，getLast() = 0 + 32767 - 1 = 32766
  // partition_index 列: stdSqlType="SMALLINT"（max 32767）
  ```
- **严重程度**: P3
- **现状**: 文档建议业务写 `WHERE partition_index BETWEEN offset AND getLast()`，即 `BETWEEN 0 AND 32766`。但 `partition_index` 是 SMALLINT（最大 32767）。若业务按 `hash & 0x7FFF` 把 partition_index 分布到 0..32767，则哈希到 32767 的记录**永远不属于任何 task**，静默丢数据。应为 `intRange(0, Short.MAX_VALUE + 1)` 或哈希限制为 `& 0x7FFE`。
- **风险**: 边界数据静默丢失（取决于业务哈希约定）。
- **建议**: 修正区间上界或文档化哈希范围。
- **信心水平**: 很可能（取决于业务是否用满 SMALLINT 范围）
- **发现来源视角**: 边界侦探

---

### [AR-99] `AdaptiveJobTaskBuilder` 每个 fire 硬编码只产 1 个 task；`serviceName` 用 `(String)` 强转 → 非 String 时 ClassCastException 中断批次

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java:66,89,103`
- **证据片段**:
  ```java
  String serviceName = (String) jobParams.get("serviceName");  // ClassCastException 若非 String
  ...
  task.setTaskNo(1);
  return List.of(task);                                         // 永远恰好 1 个 task
  ```
- **严重程度**: P3
- **现状**: bestFit 模式无法表达分区/广播作业（那些走其他 builder）。`serviceName` 强转 `(String)`，若 jobParams 里该 key 存了非 String 值（如数字/JSON 对象），抛 ClassCastException，按 AR-86 的分析会中断整批派发。
- **风险**: 限制 + 脆弱性。
- **建议**: 类型安全转换；bestFit 与分区拆分组合支持。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

## 总评

nop-job 在 R1~R9 后通过 Plan 211~215 引入了**本地配置调度、资源限制、dispatchMode 路由、任务优先级、Best-Fit 派发**五大全新能力，这些代码此前**从未被审计**。本轮审查最值得关注的 1~3 个方向：

1. **资源限制特性（Plan 212）的 worker 侧核心逻辑存在双重计算缺陷（AR-83）+ null cost NPE（AR-84）**。前者令"大于 capacity/2 的任务"永远无法被认领，整个资源限制卖点对大任务失效；后者在部署新代码后，对所有 Plan 212 之前的存量 schedule（cost 为 null）令 worker 整批扫描归零。这两条叠加表明 Plan 212 的 worker 侧资源校验路径从未在"声明 capacity + 存量 schedule"的真实组合下跑通过。

2. **批处理隔离缺失是跨 scanner 的系统性模式（AR-86 worker 侧 + AR-86 dispatcher 侧 + AR-84 NPE 落点）**。dispatcher 与 worker 的 `scanOnce` 都用单个 try 包裹整个 per-fire/per-task 循环，且 dispatcher 还先做了"预锁存入"（把全部 fire 翻 DISPATCHING）再循环，一个 fire 失败令其余卡死并被超时判失败（而非重试）。这与 R9 AR-73（completion 批隔离，已修）是同一类问题在新 scanner 上的重现。

3. **名实不符与静默失败（AR-87 死错误码 + AR-89 worst-fit 命名 + AR-90 容量 0 黑洞 + AR-93 静默停滞）**。这一组"灯下黑"问题单个不致命，但合起来让运维和后续开发者对系统行为的直觉全部失真：误配静默兜底、best-fit 实为 spread、`0` 在两处含义相反、饥饿无日志。

## 本次审查的盲区自评

1. **未实跑测试**：未执行 `./mvnw test -pl nop-job -am` 验证基线，所有结论基于静态阅读（实体类型、SQL、事务边界已交叉验证，但未运行时复现 AR-83/AR-84）。
2. **HA/集群分区再均衡（R9 自陈盲区）仍未覆盖**：`JobPartitionResolver` + INamingService 动态分区在多 coordinator 下的再分配与在途 fire/task 的交互未深入。
3. **nop-job-web 前端页面（AMIS/xmeta 生成）**：本轮聚焦后端新代码，web 层未审。
4. **retry-adapter 与 nop-retry-engine 的接口契约**：仅做边界检查，未深挖。
5. **Calendar 链式组合（3+ 链）**：R9 已覆盖单 Calendar，本轮未补组合测试。
6. **优先级惊群（thundering herd）与 CAS 浪费写入**的真实负载影响未量化。

## 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 5    | 资源双重计算、null-cost NPE、所有权丢失后仍执行、批处理 fail-vs-defer、死错误码静默兜底 |
| P2      | 8    | WAITING 无回收、best-fit 名实不符、容量 0 黑洞、enforceAttribution 饥饿、优先级索引/NULL 分叉、overfetch 饥饿、跨事务超额派发、cost 二次覆盖 |
| P3      | 4    | per-fire 查询风暴、int 溢出、分片边界 off-by-one、强转/单任务限制 |

## 误报校准（剔除项）

- **剔除**: "`AdaptiveJobTaskBuilder.scheduleStore` 在生产 beans.xml 未注入 → bestFit 永远选字典序最小 worker"（explore 子代理曾判为 P0）。
  **理由**: 该类的 `setScheduleStore` 标注了 `@Inject`（`AdaptiveJobTaskBuilder.java:45-48`）。NopIoC 对 `@Inject` setter 按类型自动注入，**不依赖 XML `<property>` 显式声明**——同一文件中 `JobDispatcherScannerImpl` 的 `setFireStore`/`setScheduleStore` 均为纯 `@Inject`（XML 无对应 property）却能正常工作即为反证。因此 `scheduleStore` 会被注入，`resolveCost` 会拿到真实 schedule，P0 不成立。

## 累计未修复项（AR-1~AR-99）

R9 报告累计未修复 44 项（1×P0, 6×P1, 24×P2, 13×P3），其中 R9 的 P1（AR-70/71/72）与高优 P2 已由 Plan 111 修复。本轮新增 17 项（0×P0, 5×P1, 8×P2, 4×P3），均集中于 Plan 211~215 的新代码，与历史发现不重叠。
