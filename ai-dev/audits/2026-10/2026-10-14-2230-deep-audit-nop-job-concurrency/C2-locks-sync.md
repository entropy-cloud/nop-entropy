# nop-job 并发审计 — C2 锁与同步原语（第 1 轮初审 / 盲审）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块，src/main/java 手写代码，164 files / 17,664 lines，不含 `_` 前缀生成文件与 target/）
- 审计维度：C2 锁与同步原语（互斥成立性、死锁、锁代价）
- 审计分支：concurrency-audit-remediation；以 live code 为准
- 盲审纪律：未读取 ai-dev/audits|plans|bugs|lessons 下任何既有文件；仅读 owner doc（docs-for-ai/03-modules/nop-job.md）与 grep 基线（_tmp/cra-baseline-nop-job.txt）

## 检查范围说明

**锁载体全量盘点**（基线 C2 节 + 独立 grep 双通道核对，结果一致，无遗漏补充项）：

| 载体 | 位置 | 结论入口 |
|------|------|---------|
| `synchronized` 方法 ×2 | `AbstractBatchScanner.startScanning/stopScanning`（nop-job-core） | C2-006、覆盖度 |
| `synchronized(job)` 块 ×15 处 | `LocalJobScheduler`（nop-job-local，per-job 监视器） | C2-001 ~ C2-005 |
| `synchronized(this)` + volatile DCL | `MetadataWorkerCapacityProvider.getMyCapacity`（nop-job-worker） | 无缺陷（volatile 正确，见覆盖度） |
| `ConcurrentHashMap` ×2 | `LocalJobScheduler.jobs`、`RpcPollTaskManager.entries` | 覆盖度 |
| `volatile` | `AbstractBatchScanner.running`、`LocalJobScheduler.active`、`JobTimeoutCheckerImpl.workerServiceName`、`RpcPollTaskManager.PollEntry.pollHandle/onCancelListener`、`MetadataWorkerCapacityProvider.cached` | 覆盖度 |
| `AtomicBoolean` | `JobTimeoutCheckerImpl.livenessDisabledWarned`（CAS warn-once） | 无缺陷 |
| `computeIfAbsent` ×2 | `DefaultJobCancelHandler:75`、`DefaultJobExecutionContextBuilder:169` | 无缺陷（见覆盖度） |
| `ReentrantLock/ReadWriteLock/Collections.synchronizedXxx/wait/notify/CountDownLatch/Semaphore/lockInterruptibly` | 全模块 | **零命中** |
| ThreadLocal | vendored `CronExpression.threadLocalCal`（仅集成边界，见覆盖度） | 无缺陷 |

其余 9 个子模块（api/dao/service/web/app/meta/coordinator 多数类/retry-adapter/codegen）的并发正确性不依赖语言级锁：DB 乐观锁（`updateTask` 返回值 CAS）+ `scheduleWithFixedDelay` 单线程调度语义（`AbstractBatchScanner.Cursor` javadoc 显式声明），不在 C2 锁载体范围。

**vendored 源码**（`CronExpression`/`ICronExpression`/`BaseCalendar`）：只审集成边界——`TriggerBuilder.buildTrigger` 与 `JobTriggerCalculator.calculateNextFireTime` 每次**新建** CronExpression 实例（`TriggerBuilder.java:33`），`years` BitSet 仅构造期写入（`CronExpression.java:365-366`），实例不跨线程共享，可变状态被封死在单线程使用路径内。结论：集成边界安全。

## 发现条目

### [nop-job-C2-001] LocalJobScheduler 持 per-job 监视器调用外部 invoker 代码——同步执行型 invoker 使整个 job 体运行在锁内，管理面无限期阻塞，且跨模块锁序可构造 ABBA 死锁

- **严重度**：P1
- **缺陷族/维度**：C2 锁正确性——持锁调用未知外部代码（回调进入点持锁）+ 锁内重逻辑
- **位置**：
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:301-309`（timer 回调持锁调 executeJob）、`:209-224`（fireNow 持锁调 executeJob）、`:313-345`（executeJob 在锁内调 `job.invoker.invokeAsync(ctx)` 并注册 whenComplete）
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/executor/BeanMethodJobInvoker.java:113-127`（`invokeReflective`→`toCompletionStage`：方法体同步执行，非 CompletionStage 返回值包成 completedFuture）
- **证据片段**：

  LocalJobScheduler.java:301-311（scheduleNext 的调度回调，`synchronized (job)` 内直接 executeJob）：
  ```java
        CompletableFuture<?> future = executor.schedule(() -> {
            synchronized (job) {
                job.scheduledFire = null;
                if (!active || job.state.internal != InternalState.WAITING)
                    return null;
                executeJob(job);
            }
            return null;
        }, delay, TimeUnit.MILLISECONDS);
  ```

  LocalJobScheduler.java:320-345（executeJob 锁内调用外部 invoker；已完成 future 的 whenComplete 同步执行）：
  ```java
        try {
            future = job.invoker.invokeAsync(ctx);
        } catch (Exception e) {
        ...
            final CompletableFuture<?> done = new CompletableFuture<>();
            job.running = done;
            future.whenComplete((result, err) -> {
                synchronized (job) {
  ```

  BeanMethodJobInvoker.java:113-127（YAML 模式唯一内置 invoker：bean 方法在调用线程同步跑完）：
  ```java
    CompletionStage<Object> invokeReflective(IFunctionModel fn, Object bean, Map<String, Object> methodParams) {
        try {
            return toCompletionStage(invokeMethod(fn, bean, methodParams));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    CompletionStage<Object> toCompletionStage(Object result) {
        if (result instanceof CompletionStage) {
            return (CompletionStage<Object>) result;
        }
        return CompletableFuture.completedFuture(result);
    }
  ```

- **问题描述**：`synchronized (job)` 持有期间调用 `job.invoker.invokeAsync(ctx)`——invoker 是 SPI 外部代码。IJobInvoker 契约名义上是异步的，但平台自己推荐的 YAML 配置模式（owner doc §1.3）唯一内置 invoker `BeanMethodJobInvoker` 把任意 bean 方法**同步**执行完毕再包 `completedFuture`。于是本地模式下整个业务方法体（可长达分钟级）都运行在 per-job intrinsic monitor 内。
- **触发时序（死锁，可具体化）**：
  1. T1（调度线程）：job A 到期 → `synchronized(jobA)` → executeJob → BeanMethodJobInvoker 同步执行业务方法 → 业务方法内 `synchronized(L)`（应用级锁，如内部状态锁/连接池门闩）并阻塞等待 L；
  2. T2（任意管理/业务线程）：持有 L 的代码路径调用 `scheduler.getJobState("A")` / `suspendJob("A")` / `removeJob("A")` / `cancelJob("A")` → `synchronized(jobA)` 阻塞；
  3. T1 等 L（T2 持有），T2 等 jobA 监视器（T1 持有）→ intrinsic monitor 无超时、不可中断 → 永久死锁。
- **触发时序（无死锁也可复现的可用性退化）**：job A 业务方法跑 10 分钟 → 期间 `getJobState/suspendJob/resumeJob/cancelJob/fireNow/removeJob` 全部阻塞 10 分钟；`awaitState/awaitIdle/awaitFireCount`（`:405-466`）的 `synchronized(job)` 获取不受其 timeout 参数约束——deadline 仅在拿到锁后才检查，5 秒超时的 await 实际会先阻塞整个 job 体时长；`LocalJobConfigLoader.destroy()`（`@PreDestroy`）→ `scheduler.deactivate()`（`:232-241`）逐 job 取锁 → 应用停机被在跑 job 体阻塞至其自然结束。
- **影响面**：本地模式（nop-job-local，owner doc 推荐用法）。管理 API 失去响应性；停机挂死（无上界，等于最长 job 体时长）；特定用户代码形态下可达永久死锁。不涉数据损坏，故不判 P0。
- **失效面枚举（F3 对照）**：失效面 = 本地模式全部 per-job 管理操作 + deactivate 停机路径 + awaitXxx 辅助方法；分布式模式（coordinator/worker）不经 LocalJobScheduler，不受影响。
- **修复建议**：把 `invoker.invokeAsync` 移出 `synchronized(job)`——在锁内完成状态迁移（WAITING→RUNNING、占位 `job.running`）后先释放锁再调 invoker；whenComplete 回调内重新取锁做终态迁移（现有代码已具备该形态，只需把 executeJob 的调用点移到锁外）。或至少在 owner doc/接口 javadoc 强制 invoker 必须真异步，并对 BeanMethodJobInvoker 补充"方法体不得长于秒级"的契约声明。
- **误报校准核对**：非"synchronized 原语本身"误报——问题不在 monitor 而在其持有范围覆盖外部未知代码；也非低频路径——timer 触发是本地模式主路径。
- **置信度**：高（同步执行事实由 BeanMethodJobInvoker 代码直接证实；死锁时序依赖用户业务方法持第二把锁，属于"罕见但可具体化"）。
- **关联发现**：C2-002（同一监视器的无锁读面）、C2-005（deactivate 与重排程竞态）。
- **边界声明核对**：owner doc 声明"同一 jobName 任意时刻只跑一个实例"（执行互斥语义被声明），但**未声明**"执行期间持锁"这一实现细节及其管理面阻塞/停机阻塞后果；代码注释亦无。不构成已声明边界豁免。
- **复核状态**：已保留（P1 维持。逐环节重走验证全部成立：executeJob 的两个调用点（timer 回调 ：306、fireNow :221）均在 `synchronized(job)` 内，invokeAsync :321 持锁执行；BeanMethodJobInvoker invokeReflective/toCompletionStage（:113-127）证实非 CompletionStage 返回值同步包 completedFuture、bean 方法体在调用线程跑完；ABBA 反向边 EXT(L)→J(x) 由全部管理 API 取锁点实证（getJobState :155/removeJob :143/suspendJob :184/cancelJob :200/fireNow :215/deactivate :235/awaitXxx :413/:435/:455）；无死锁退化面（管理面阻塞 + 停机阻塞）由 LocalJobConfigLoader `@PreDestroy`（:223-227）→ deactivate（:232-241）逐 job 取锁实证，确定性可达、独立支撑 P1，死锁环为可构造加重项。历史 nop-job 审计（2026-05/06 各目录）与 cra-index 台账均无同族登记）

### [nop-job-C2-002] getJobDetail() 完全无锁读取 ScheduledJob/SimpleJobState 可变状态——与持锁写方构成 JMM 数据竞争，long 字段存在撕裂读

- **严重度**：P2
- **缺陷族/维度**：C2 锁正确性——synchronized 保护的字段存在锁外读点（互斥不完整）
- **位置**：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:63-67`（getJobDetail 无锁）、`:472-508`（ScheduledJob 字段声明与 toJobDetail）、`:510-533`（SimpleJobState plain long 字段）；写方 `:348-350`（handleResult：fireCount++/lastEndTime，持锁）、`:487-494`（update：spec/invoker/trigger 替换，持锁）
- **证据片段**：

  LocalJobScheduler.java:63-67（唯一不走 `synchronized(job)` 的状态读 API；对照 `getJobState` `:151-158` 是持锁的）：
  ```java
    @Override
    public JobDetail getJobDetail(String jobName) {
        ScheduledJob job = jobs.get(jobName);
        return job == null ? null : job.toJobDetail();
    }
  ```

  LocalJobScheduler.java:472-478 与 496-507（字段均非 volatile/final；toJobDetail 读 spec 与三个 long 计数）：
  ```java
    static class ScheduledJob {
        final SimpleJobState state;
        JobSpec spec;
        IJobInvoker invoker;
        ITrigger trigger;
        CompletableFuture<?> scheduledFire;
        CompletionStage<?> running;
  ```

  LocalJobScheduler.java:510-519（plain long，JLS §17.7 允许非 volatile long 撕裂）：
  ```java
    static class SimpleJobState implements ITriggerEvalContext {
        long fireCount;
        long lastScheduledTime;
        long lastEndTime;
  ```

- **问题描述**：`toJobDetail` 在无锁上下文读取 `spec`（addJob 更新路径会整体替换）、`state.fireCount`、`state.lastScheduledTime`、`state.lastEndTime`（执行路径持锁递增/覆写）。读方与写方之间无 happens-before：管理线程可能读到过期值（可见性失效），且 `fireCount` 等为非 volatile `long`，JMM 允许 64 位撕裂。
- **触发时序**：T1（调度线程）在 `synchronized(jobA)` 内执行 handleResult（`fireCount++`、`lastEndTime=now`）；T2（监控/诊断线程）同时调 `getJobDetail("A")` → CHM get 无同步 → toJobDetail 读到中间态：撕裂的 fireCount（理论可读到高低 32 位来自不同写入的混合值）、新 fireCount 配旧 lastEndTime 的错位组合、或 addJob 热更新半程的旧 spec 配新 state。
- **影响面**：仅观测/诊断路径（JobDetail 的 execCount/scheduledExecTime/execEndTime/jobSpec 快照）。无状态损坏、无调度错误（调度决策全部持锁）。监控面出现偶发的荒谬数值（如超大 execCount）会误导排障。
- **失效面枚举（F3 对照）**：失效面 = getJobDetail 全部调用方（编程式监控、诊断端点）。对照 getJobState/toJobState 持锁（`:155-157`），同类内锁纪律不一致证明无锁读非有意设计。
- **修复建议**：getJobDetail 内 `synchronized (job) { return job.toJobDetail(); }`（toJobDetail 纯读，锁内执行代价可忽略，与 getJobState 对齐即可）。
- **误报校准核对**：非"CHM 原语本身"误报——缺陷在 CHM 之外的字段读取纪律；非低频粗锁问题。
- **置信度**：高（数据竞争确凿；实际撕裂概率低，按"理论竞态"定 P2 上限合理，不升 P1）。
- **关联发现**：C2-001（同一监视器）。
- **边界声明核对**：无任何 owner doc / javadoc 声明 getJobDetail 为弱一致接口。不构成已声明边界。
- **复核状态**：已保留（P2 维持。getJobDetail :64-67 无锁、toJobDetail :496-507 读 spec 与三个 plain long、写方 handleResult :348-350 与 update :487-494 均持锁——JMM 数据竞争定性成立；与 getJobState :155-157 持锁的同类反差证明无锁读非有意设计。与 C1 维度 C1-001 为同一代码点同一机制（两维复核均保留 P2，判定自洽），合并裁定阶段归并。历史审计无同族登记）

### [nop-job-C2-003] addJob raced 分支在 raced 条目已被并发移除时静默 return——注册请求无痕丢失，与 existing 分支的 fall-through 重建语义不一致

- **严重度**：P2
- **缺陷族/维度**：C2 锁正确性——锁内复检（re-check after lock）后的分支处理不对称，产生丢失更新
- **位置**：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:106-120`（raced 分支）；对照 `:88-104`（existing 分支 fall-through 重建）
- **证据片段**：

  LocalJobScheduler.java:106-120：
  ```java
        // New job or existing was removed while we waited for the lock.
        // putIfAbsent prevents two threads from creating duplicate ScheduledJobs
        // for the same name; synchronized(job) then protects scheduleNext against
        // a concurrent removeJob or deactivate.
        ScheduledJob job = new ScheduledJob(spec, invoker, trigger, state);
        ScheduledJob raced = jobs.putIfAbsent(spec.getJobName(), job);
        if (raced != null) {
            // Another thread inserted first — treat as update.
            synchronized (raced) {
                if (!jobs.containsKey(spec.getJobName())) {
                    return;
                }
  ```

  对照 LocalJobScheduler.java:94-103（同场景在 existing 分支是"fall through 到下方重建"，而非丢弃）：
  ```java
            synchronized (existing) {
                // re-check: removeJob may have removed this entry between get and lock
                if (!jobs.containsKey(spec.getJobName())) {
                    // job was removed; fall through to create-and-schedule below
                } else {
  ```

- **问题描述**：`putIfAbsent` 返回 raced（他线程刚插入的同名条目）后取锁复检 `!containsKey` 为真时直接 `return`——本线程的 addJob 请求整体丢弃：既没有落到 raced 上更新，也没有 fall-through 重建，方法正常返回（void，调用方无从感知）。与 existing 分支同一复检条件的处理（fall-through 重建）不一致。
- **触发时序**：
  1. T1：`addJob("x", spec)`，`jobs.get("x")` → null；
  2. T2：`addJob("x", spec2)` 先完成 putIfAbsent 插入 j1（或普通注册路径）；
  3. T3：`removeJob("x")` → `jobs.remove` 成功移除 j1 并持锁清理；
  4. T1：`putIfAbsent("x", j2)` → 返回 raced=j1（已从 map 摘除的旧对象）；T1 `synchronized(j1)` 后复检 containsKey → false → `return`。
  净结果：T1 的 addJob 正常返回但 "x" 未注册，无异常、无日志、无返回值信号。
- **影响面**：运行期动态注册（编程式 `addJob` 调用方，含测试/嵌入场景对"addJob 成功即可被调度"的依赖）在同/异线程并发 add+remove 同名 job 时静默丢注册。因无返回值语义可依托，属"错误被静默化"形态。
- **失效面枚举（F3 对照）**：失效面 = raced 分支这一条路径（existing 分支有 fall-through、putIfAbsent 返回 null 分支有 active 复检清理，均正确）；枚举完整，无同类残留点（全文件仅此一处 `synchronized(raced)`）。
- **修复建议**：与 existing 分支对齐——复检失败时 fall-through 重新尝试 `putIfAbsent` 新建（或循环重试一次）；至少 LOG.warn 留痕（违反本模块"失败不静默"的既有口径，见 RpcPollTaskManager/WorkerScanner 的 warn 惯例）。
- **误报校准核对**：非"低频路径粗粒度锁"豁免——这是分支语义缺陷而非锁代价问题；触发窗口虽窄但每一步都有代码级落点。
- **置信度**：高（时序完全落在现有代码路径上，无假设成分）。
- **关联发现**：C2-004（同一分支的 allowUpdate 契约绕过）、C2-005（同分支缺 active 复检）。
- **边界声明核对**：无声明。注释自述 putIfAbsent + synchronized(job) 的防护意图，恰恰说明该分支的静默 return 是防护网上的一个洞。
- **复核状态**：已保留（P2 维持。raced 分支 ：114-120 复检失败静默 return（void、无异常、无日志）与 existing 分支 ：96-103 fall-through 重建的语义反差由源码直接证明；removeJob :138 先 jobs.remove 后持旧实例锁，锁内复检不能排除已发生的移除，触发时序每步都有代码落点。与 C1 维度 C1-005 为同一代码点同一机制（两维复核均保留 P2，判定自洽），合并裁定阶段归并。历史仅 2026-06-03 [09-01] addJob 错误码误用（已修复为 ERR_JOB_ALREADY_EXISTS），非本竞态）

### [nop-job-C2-004] addJob raced 分支绕过 allowUpdate=false 契约——并发注册同名 job 时第二个 addJob 静默改写首个 job 的 spec，应抛 ERR_JOB_ALREADY_EXISTS 而未抛

- **严重度**：P2
- **缺陷族/维度**：C2 锁正确性——putIfAbsent 竞态路径漏掉入口参数校验（fail-fast 契约在并发窗口失效）
- **位置**：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:88-93`（allowUpdate 仅在 existing 分支校验）、`:110-120`（raced 分支无条件 update）
- **证据片段**：

  LocalJobScheduler.java:88-93（allowUpdate=false 的 already-exists fail-fast 只挡住"入口时已存在"）：
  ```java
        ScheduledJob existing = jobs.get(spec.getJobName());
        if (existing != null) {
            if (!allowUpdate) {
                throw new NopException(ERR_JOB_ALREADY_EXISTS)
                        .param(ARG_JOB_NAME, spec.getJobName());
            }
  ```

  LocalJobScheduler.java:112-120（raced 分支直接 update，不再看 allowUpdate）：
  ```java
        if (raced != null) {
            // Another thread inserted first — treat as update.
            synchronized (raced) {
                if (!jobs.containsKey(spec.getJobName())) {
                    return;
                }
                raced.update(spec, invoker, trigger, state);
                rescheduleAfterUpdate(raced);
            }
  ```

- **问题描述**：`addJob(spec, false)` 的语义契约是"已存在则抛 already-exists"（owner doc §1.2：`scheduler.addJob(spec, false); // false = 已存在则抛 already-exists`）。该契约仅在方法入口的 map 快照上校验；两个线程并发注册同名 job 时，后完成 putIfAbsent 的一方拿到 raced 非 null，直接走 update 分支改写对方刚注册的 spec/invoker/trigger——契约被并发窗口绕过，且 `update()` 还可能因 `running == null` 替换 invoker（`:487-494`），后续触发用新 invoker 执行。
- **触发时序**：
  1. T1：`addJob(specA, false)`，`jobs.get("x")` → null；
  2. T2：`addJob(specB, false)`，`jobs.get("x")` → null；
  3. T1：`putIfAbsent("x", jA)` → null（注册成功，scheduleNext）；
  4. T2：`putIfAbsent("x", jB)` → raced=jA → `synchronized(jA)` → containsKey=true → `jA.update(specB,...)`。
  净结果：两次都传 `allowUpdate=false`，但 specB 无声覆盖 specA，没有任何 already-exists 异常。
- **影响面**：同名 job 并发首次注册（例如应用多组件各自注册同名 job、或配置重载与手动注册竞争）。静默覆盖违反 API 显式声明的 fail-fast 语义，错误配置（本应启动即暴露）被推迟到触发期才可能显形。
- **失效面枚举（F3 对照）**：失效面 = raced 分支一条路径；existing 分支与 null 分支契约正确。触发器/invoker 解析在 `:74-84` 已有独立 fail-fast，不受本缺陷影响。
- **修复建议**：raced 分支复检通过后补 `if (!allowUpdate) throw new NopException(ERR_JOB_ALREADY_EXISTS)...`（抛出时注意当前已 putIfAbsent 失败、未改动 map，无需回滚）。
- **误报校准核对**：非"低频路径粗粒度锁"豁免——属契约级竞态而非锁代价；窗口窄（毫秒级双注册）故不升 P1。
- **置信度**：高（代码路径无假设；发生率低）。
- **关联发现**：C2-003（同一分支的相邻缺陷）。
- **边界声明核对**：owner doc 明文声明 allowUpdate=false 抛异常语义，本缺陷是该声明的实现违背，非边界声明。
- **复核状态**：已保留（P2 维持。allowUpdate=false 校验仅在入口 existing 分支 ：90-93，raced 分支 ：118-119 无条件 update；owner doc :93 明文契约"false = 已存在则抛 already-exists"核实；双线程并发首注册时序完全落在现有代码路径、无假设成分。与 C2-003 同分支但缺陷独立（契约绕过 vs 静默丢注册），非重复立项。历史 check-findings 修复（72b770b094）中的"addJob fail-fast"系 invoker 解析期 bean 缺失 fail-fast（现 ：74-78），非本竞态面）

### [nop-job-C2-005] addJob 两个更新分支重排程前不复查 active 标志——与 deactivate() 并发时可为已下线调度器残留存活 timer future，cron 场景可滞留至下次触发点

- **严重度**：P3（理论风险，未具体化到可稳定复现；窗口极窄且有自愈上限，故降 P3 并标注）
- **缺陷族/维度**：C2 锁正确性——生命周期标志（volatile active）与 per-job 锁的组合防护存在未覆盖路径
- **位置**：`nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:94-103`（existing 更新分支）、`:112-120`（raced 更新分支）、`:256-264`（rescheduleAfterUpdate 无 active 检查）、`:227-241`（deactivate 逐 job 迭代后 clear）
- **证据片段**：

  LocalJobScheduler.java:94-103（更新路径唯一的状态复检是 containsKey，不含 active）：
  ```java
            synchronized (existing) {
                // re-check: removeJob may have removed this entry between get and lock
                if (!jobs.containsKey(spec.getJobName())) {
                    // job was removed; fall through to create-and-schedule below
                } else {
                    existing.update(spec, invoker, trigger, state);
                    rescheduleAfterUpdate(existing);
                    return;
                }
            }
  ```

  LocalJobScheduler.java:122-130（对照：新建分支有 `!active` 复检并清理）：
  ```java
        } else {
            synchronized (job) {
                if (!active) {
                    // deactivate ran after our putIfAbsent; clean up
                    cancelScheduledFire(job);
                    jobs.remove(spec.getJobName(), job);
                    return;
                }
                scheduleNext(job);
            }
  ```

- **问题描述**：新建分支在锁内复查 `!active`（deactivate 兜底），但 existing/raced 两个更新分支调用 `rescheduleAfterUpdate` → `scheduleNext` 时只查 containsKey 不查 active。`deactivate()` 的实现是"active=false → 弱一致迭代 values() 逐 job 取锁清理 → jobs.clear()"，其"已访问过 job X"与"X 上后续再排程"之间没有互斥关系（不同时刻的两次独立取锁），存在`清理先于重排程、clear 后于重排程`的交错。
- **触发时序（理论推演，窗口 = deactivate 迭代 X 与 clear() 之间）**：
  1. deactivate：active=false → 迭代至 job X：`synchronized(X)` 取消现有 fire → 迭代离开；
  2. T2：`addJob("X", spec, true)` 更新路径 → `synchronized(X)` → containsKey 仍真（clear 未到）→ rescheduleAfterUpdate → scheduleNext 为 X 挂上**新** future；
  3. deactivate：`jobs.clear()`（X 移除，但新 future 不在清理范围）。
  净结果：一个针对已下线 scheduler 的 timer future 存活至其触发时刻；触发时 `synchronized(X)` → `!active` → no-op 自愈（`:304`）。影响上限 = 1 条滞留 timer 项 + 一次空唤醒；delay 为 cron 次触发点时滞留可达数小时。资源不泄漏（future 最终 fire 后释放），无状态损坏。
- **影响面**：仅 `deactivate()`（PreDestroy/测试 teardown）与运行期 addJob 热更新并发这一窄窗口；后果限于定时器容量占用与停机后一次无害回调。
- **失效面枚举（F3 对照）**：失效面 = existing/raced 两个更新分支；新建分支、scheduleNext 回调、handleResult 均有 active/containsKey 守卫，枚举完整。
- **修复建议**：`rescheduleAfterUpdate` 入口（或 scheduleNext 锁内头部）加 `if (!active) return;` 与新建分支对齐；成本一行。
- **误报校准核对**：自愈上限明确（单条、有界、自清），故不按"泄漏"升 P1/P2；标注"理论风险，未具体化"符合降级纪律。
- **置信度**：中高（交错成立但窗口极窄；影响有界）。
- **关联发现**：C2-001（deactivate 停机阻塞同在此路径）、C2-003/C2-004（同分支家族）。
- **边界声明核对**：无声明；新建分支的 active 复检反证更新分支缺复检非有意设计。
- **复核状态**：已保留（P3 维持。rescheduleAfterUpdate :256-264 与 scheduleNext :275-311 确无 active 复检、新建分支 ：123-127 有——反差实证；与 deactivate（:232-241，active=false → 逐 job 取锁 → clear）的交错真实可构造，触发时 ：304 `!active` 空转自愈、滞留上界 1 条 timer 项——"理论风险、窗口极窄、有界自愈"的降档论证成立，P3 相称）

### [nop-job-C2-006] AbstractBatchScanner registeredCursors 迭代无并发守卫——startScanning 之后调用 newCursor() 会与调度线程的 scanOnce 迭代竞争（ArrayList ConcurrentModificationException/脏读），仅有 javadoc 推荐而无 fail-fast 校验

- **严重度**：P3
- **缺陷族/维度**：C2 锁正确性——非线程安全集合的迭代点核对；"已声明使用契约但缺运行时守卫"的边界缺陷
- **位置**：`nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:54`（ArrayList 声明）、`:88-103`（scanOnce 无锁迭代）、`:120-128`（newCursor 直接 add，无状态检查）
- **证据片段**：

  AbstractBatchScanner.java:54、88-103（迭代发生在调度线程、无锁）：
  ```java
    private final List<Cursor> registeredCursors = new ArrayList<>();
  ...
    public void scanOnce() {
        for (Cursor c : registeredCursors) {
            c.reset();
        }
  ```

  AbstractBatchScanner.java:120-128（契约只写在注释里，无 running 状态校验）：
  ```java
     * 创建并注册一个 {@link Cursor}。已注册的 cursor 在每个 {@link #scanOnce()} 入口自动 reset。
     * 推荐用法：在子类中作为 final 字段初始化（如 {@code private final Cursor taskCursor = newCursor();}），
     * 注册时机由 Java 字段初始化顺序保证（基类 {@code registeredCursors} 在子类字段初始化之前已就绪）。
     */
    protected final Cursor newCursor() {
        Cursor c = new Cursor();
        registeredCursors.add(c);
        return c;
    }
  ```

- **问题描述**：`registeredCursors` 是普通 ArrayList：写点 `newCursor()`（构造期，单线程）与读点 `scanOnce()`（GlobalExecutors 调度线程）。只要子类遵守"final 字段初始化期注册"的推荐契约，两者不重叠、安全；但 `newCursor()` 是 protected 扩展点，没有任何运行时防线（如 `if (running) throw`）阻止子类在 startScanning 之后注册——届时的 add 与调度线程的 for 迭代构成对 ArrayList 的数据竞争，典型表现为 ConcurrentModificationException 打断整轮扫描（被 onScanFailed 捕获记 error 后本轮结束）。
- **触发时序**：需要子类违反 javadoc 推荐（在生命周期启动后延迟注册 cursor）。当前 5 个内置子类（Planner/Dispatcher/Completion/Timeout/Worker scanner）全部为字段初始化期注册，**现网代码不可触发**——这是"守卫缺失"而非"现存缺陷"。
- **影响面**：仅面向未来子类扩展；触发后果为单轮扫描失败 + error 日志，下轮自愈，无持久状态损坏。
- **失效面枚举（F3 对照）**：失效面 = scanOnce 的唯一迭代点（`:89`）与 newCursor 唯一写点；枚举完整。
- **修复建议**：`newCursor()` 内加 fail-fast：`if (running) throw new NopException(ERR_JOB_INVALID_CONFIG_VALUE)...`（或专用错误码），把 javadoc 契约升级为运行时契约。改动一行级。
- **误报校准核对**：不在豁免清单内（非测试专用、非平台设施自身）；因现网 5 个子类均不可触发而定 P3。
- **置信度**：高（缺陷形态确凿；触发依赖未来违例用法）。
- **关联发现**：无。
- **边界声明核对**：javadoc 有声明（"推荐用法"）但**无 fail-fast 守卫**，按"声明+守卫双条件"规则不豁免，故以边界守卫缺失立案 P3。
- **复核状态**：已保留（P3 维持，证据细节修正。newCursor :124-128 无 running 校验、scanOnce :89 无锁迭代属实；但"当前 5 个内置子类全部为字段初始化期注册"表述不准——grep 实证 src/main 仅 JobTimeoutCheckerImpl 注册 cursor（:153-155 字段初始化期 3 个），其余 4 个 scanner 子类不注册任何 cursor（更强地支持"现网代码不可触发"），结论方向不变、扩展点守卫缺失的 P3 定性成立。历史无同族登记）

## 锁序图（文本，全模块锁边）

锁对象清单：`J(x)` = LocalJobScheduler per-job 监视器（ScheduledJob 实例，名字空间：jobName，同一时刻 map 内一个活实例对应一个锁）；`S` = AbstractBatchScanner 实例监视器（start/stop）；`M` = MetadataWorkerCapacityProvider 实例监视器；`CHM-bucket` = ConcurrentHashMap 内部锁（瞬态）；`EXT(L)` = 任意外部用户代码锁；`EXEC` = GlobalExecutors/IScheduledExecutor 内部队列锁（瞬态）。

模块内锁边（A → B 表示"持有 A 的同时获取 B"）：

```
E1: J(x) → EXEC          scheduleNext 在 synchronized(job) 内调 executor.schedule   [LocalJobScheduler:301]
E2: J(x) → J(x)          重入（fireNow→executeJob→whenComplete 同步完成→handleResult→scheduleNext）[自环，无环风险]
E3: J(x) → EXT(L)        executeJob 持锁调 invoker.invokeAsync（BeanMethodJobInvoker 同步执行业务方法体）[C2-001, LocalJobScheduler:321 + BeanMethodJobInvoker:115]
E4: deactivate: J(x1) → 释放 → J(x2) → 释放 → ...   逐 job 串行取锁，从不嵌套两把 J [LocalJobScheduler:234-239]
E5: S → EXEC             startScanning 持 S 调 scheduleWithFixedDelay [AbstractBatchScanner:58-65]（S 从不与 J/M 嵌套）
E6: M → （无）           DCL 块内仅解析本地配置 map，无外部调用、无第二把锁 [MetadataWorkerCapacityProvider:78-83]
```

环分析：
- 模块内部边集 {E1,E2,E4,E5,E6} 无环——E4 显式串行化，E1 的 EXEC 是瞬态提交锁（任务执行在锁释放后才开始，EXEC → J(x) 的运行期反边不构成嵌套）。
- **唯一环风险跨模块边界**：E3（J(x) → EXT(L)）与外部反向边 EXT(L) → J(x)（用户代码持 L 调 getJobState/suspendJob/removeJob 等管理 API）构成 ABBA 环 → 即 C2-001 的死锁时序。模块内无任何代码路径提供第二条进入 J(x) 的持锁 route，故该环完全由 E3 供出。
- RpcPollTaskManager 无监视器锁（CHM + volatile + future 回调），无锁边；coordinator/worker 其余组件无语言级锁，锁序图不涉及。

一句话结论：**nop-job 模块内部为单层锁（per-job 监视器，串行取用，无内部环）；全模块唯一可达死锁环位于 LocalJobScheduler 持锁调用外部 invoker 代码这一条跨边界锁边（C2-001）。**

## 覆盖度与残留边界

**覆盖度**：
- 锁载体盘点：基线 C2 节 20 条命中 + 独立全量 grep（synchronized/ReentrantLock/ReadWriteLock/Collections.synchronized*/CHM/Atomic*/volatile/wait/notify/CountDownLatch/Semaphore/lockInterruptibly/await/join/get(timeout)/Thread.sleep）双通道核对，逐条落实到文件行；两条通道集合相同，无遗漏载体。私有可变集合字段 grep（HashMap/ArrayList/HashSet 等）零命中，无隐藏共享状态。
- 逐文件精读：LocalJobScheduler、AbstractBatchScanner、BeanMethodJobInvoker、MetadataWorkerCapacityProvider、RpcPollTaskManager、JobTimeoutCheckerImpl、JobWorkerScannerImpl、JobCoordinator/JobWorker、LocalJobConfigLoader、JobLogReporter、RemoteJobInvoker、DefaultRpcPollTaskClient、4 个 MetricsImpl 全文；stores（JobScheduleStoreImpl/JobTaskStoreImpl/JobFireStoreImpl）、5 个 scanner 子类、DefaultJobCancelHandler、DefaultJobExecutionContextBuilder、NopJobTaskLogBizModel、JobCompletionProcessorImpl/JobPlannerScannerImpl/JobDispatcherScannerImpl 关键路径抽查。
- synchronized 集合迭代点核对：全模块无 Collections.synchronizedXxx；CHM 迭代点仅 `getJobNames()`（`new ArrayList<>(jobs.keySet())`，弱一致快照，安全）、`deactivate()`（values() 弱一致迭代，逐元素锁内清理，配合 C2-005 已立案的窄窗口）；无"迭代中改结构"的模块内违例。
- 锁对象替换检查：`jobs` 的 value 即锁对象；removeJob 先 `jobs.remove` 再取旧实例锁，addJob 重建走 putIfAbsent——同一 jobName 任意时刻至多一个活锁对象，无替换窗口内双锁并存。
- DCL 检查：MetadataWorkerCapacityProvider 为教科书式正确 DCL（`cached` volatile，`:48/:74-83`），不立案。
- computeIfAbsent 检查：两处（DefaultJobCancelHandler:75、DefaultJobExecutionContextBuilder:169）均作用于 `task.getEffectiveParams()` 返回的**方法内新建** LinkedHashMap（NopJobTask.java:20-33），非共享容器、非并发容器，单线程局部使用——无"computeIfAbsent 内重逻辑/递归"问题，不立案。
- F5 对照：全模块零 `Object.wait/Condition.await/lockInterruptibly/无超时 Future.get/join`；三个 awaitXxx 均为 deadline + 5ms sleep 轮询且传播 InterruptedException（但注意 C2-001 所述：其 `synchronized(job)` 获取段不受 timeout 约束）；RpcPollTaskManager 轮询 RPC 带 poll-timeout-ms 超时头（DefaultRpcPollTaskClient check2 P1-2 注释），pollOnce 全程 try/catch 不吞中断。AbstractBatchScanner.stopScanning 的 `cancel(false)` 不等待在途扫描完成——DB 写入路径无状态损坏，视为已声明调度语义（fixed-delay 单线程），不立案。

**残留边界**（本轮未立案、留档供后续维度）：
1. LocalJobScheduler.removeJob/cancelJob 的 `cancelRunning` 为 no-op（`:390-393`，代码注释显式声明"running CompletionStage cannot be cancelled in general"）：对真异步 invoker，deactivate/removeJob 返回后 job 继续跑到完成。属已声明边界 + C6 形态 P2，按维度集外 overlay 规则（P0/P1 才登记）不立案。
2. vendored CronExpression 的实例级 `ThreadLocal<GregorianCalendar>`：分布式路径 JobTriggerCalculator 每次计算新建 CronExpression → 每次新建 ThreadLocal + 初始 GregorianCalendar，产生 ThreadLocalMap 短命条目churn（弱引用键可回收，非硬泄漏）。C5 形态 P3，不登记。
3. 分布式 scanner 的 DB 乐观锁语义（updateTask 返回值 CAS、fire/schedule 版本冲突处理）属锁的数据库形态，归 C 类其他维度/事务维度，本轮仅在锁序图中标注"无语言级锁"。
4. `AbstractBatchScanner.partitionResolver` 懒创建（`:189-192`）非 volatile：依赖 IoC inject→start→executor.schedule 的 happens-before 链，标准生命周期下安全；若未来在 startScanning 后调用 setAssignedPartitions 则有可见性竞态——与 C2-006 同族的扩展点守卫缺失，本轮不单独立案。

**维度集外线索**：本轮未发现 C3/C6/C8/C9 形态的 P0/P1 级线索（上述残留边界 1/2 均为 P2/P3 形态，按 plan 2464 裁定不登记）。

## 维度复核结论（独立复核子代理）

- 复核子代理：C2 维度独立复核（与初审/第 2 轮深挖不同代理），日期 2026-10-15
- 复核基线：live code（concurrency-audit-remediation @ 335e77caec）逐条重读验证；复核输入 = 本文件（第 1 轮）+ C2-round2.md（第 2 轮）；现状标注核对面 = ai-dev/audits/ 下 nop-job 历史审计目录（2026-05-18/2026-06-03/2026-06-04/2026-06-19 各轮）summary/结论节、同 campaign WI 1.1–1.3 审计目录、cra-index.md、git log（LocalJobScheduler 全部历史提交）
- 逐条判定表（覆盖全部 8 条，含 round2 条目）：

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---------|-------|------|-----------|---------|
| C2-001 | P1 | 保留 | executeJob 全部调用点持锁、BeanMethodJobInvoker 同步执行方法体、ABBA 反向边（8 个管理 API 取锁点）与停机阻塞面（@PreDestroy→deactivate 逐 job 取锁）逐环节重验成立；无死锁退化面确定性可达、独立支撑 P1 | 新发现 |
| C2-002 | P2 | 保留 | getJobDetail :64-67 无锁 vs 全部写方持锁的 JMM 数据竞争定性成立；与 getJobState 持锁的同类反差证明非有意设计 | 新发现（与 C1-001 同一代码点同一机制，两维均保留 P2 判定自洽，合并裁定归并） |
| C2-003 | P2 | 保留 | raced 分支静默 return 与 existing 分支 fall-through 的语义反差由源码直接证明，触发时序每步有代码落点 | 新发现（与 C1-005 同一代码点同一机制，两维均保留 P2 判定自洽，合并裁定归并） |
| C2-004 | P2 | 保留 | allowUpdate 校验仅覆盖入口分支、raced 分支无条件 update 实证；owner doc :93 契约"false = 已存在则抛 already-exists"被并发窗口绕过；与 C2-003 同分支但缺陷独立 | 新发现（历史"addJob fail-fast"修复 72b770b094 系 invoker 解析期 bean 缺失校验，非本竞态面） |
| C2-005 | P3 | 保留 | 更新分支与 scheduleNext 无 active 复检、新建分支有的反差实证；deactivate 交错可构造但触发时 ：304 自愈、滞留上界 1 条 timer——降档论证成立 | 新发现 |
| C2-006 | P3 | 保留（证据细节修正） | newCursor 无 fail-fast 守卫、scanOnce 无锁迭代属实；但"5 个内置子类全部字段初始化期注册"表述不准——仅 JobTimeoutCheckerImpl 注册 cursor（3 个），其余 4 个子类不注册（更强支持"现网不可触发"），结论方向与 P3 定性不变 | 新发现 |
| C2-007 | P2 | 保留 | 占位赋值滞后（:321 vs :334）+ fireNow 判据（:216）+ 两层 catch(Exception) 放行 SOE + :314 置位无恢复路径，递归→静默死亡链路唯一确定；awaitIdle 执行全程误报为确定性缺陷独立支撑 P2 | 新发现（精确性备注：ExecutorHelper.java:187 记无 jobName 的通用 `nop.executor.execute-fail` 日志——"模块内无日志"表述精确，终态不变） |
| C2-008 | P3 | 保留 | ctx 持 live state + getter 直读 plain long 属实，IJobInvoker 契约不禁止跨线程持有；4 个内置 invoker 全部同步段读 ctx，触发需自定义异步 invoker——P3 与 C2-006 同口径恰当 | 新发现（与 C1-002 同一泄漏点：C1 以逃逸面 P2、本条以延迟读竞态面 P3，触发面更窄，合并裁定归并） |

### 不确定项（显式列出）

1. **C2-001 死锁前提依赖用户代码形态**：需业务方法持第二把锁 L 且他线程持 L 调同一 job 的管理 API——可构造但非常态；判定不依赖该前提：无死锁退化面（管理面阻塞 + awaitXxx 锁获取不受 timeout 约束 + 停机阻塞）确定性可达，独立支撑 P1。
2. **C2-002/C2-008 的 64 位 long 撕裂子论点**：Java 25 实际部署面（64 位 HotSpot）long 写实测原子，撕裂子论点基本不可达（与 C1 维度复核对 C1-001 的备注同款）；主判级（可见性过期/错位快照）不受影响。
3. **C2-007 "静默"表述强度**：SOE 逃逸后平台层 ExecutorHelper 会记一条无 jobName 上下文的通用 error 日志（ExecutorHelper.java:187）再抛入 FutureTask 吞掉；原文"无任何模块内日志"精确，但若读者把"静默"理解为"全平台零日志"则需以上述备注修正——job 级调度死亡确无任何带 job 标识的日志。
4. **C2-006 内置子类注册面事实修正**：原文"5 个内置子类全部为字段初始化期注册"不准确（仅 JobTimeoutCheckerImpl 注册 3 个 cursor，其余 4 个子类零注册）；该修正强化而非削弱"现网不可触发"，不影响 P3 立案。
5. **C2-004 现实触发频率**：同名 job 双线程毫秒级窗口并发首注册属罕见场景，P2 判级主要依据"显式 fail-fast 契约被静默绕过"的契约权重而非发生概率——修复裁定阶段可结合该点权衡批次。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|-----|------------|---------|-----------|------|
| C2-001 | P1 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:301-309,313-346（+executor/BeanMethodJobInvoker.java:113-127） | per-job 监视器内调用外部 invoker：同步执行型 invoker 使业务方法体全程持锁——管理 API/停机无限期阻塞（确定性），跨边界 EXT(L) 反向边可构造 ABBA 永久死锁 | 新发现 |
| C2-002 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:64-67,496-507,510-519 | getJobDetail 无锁读取持锁保护的 spec/plain long 计数——JMM 数据竞争，观测面可见过期/错位值 | 新发现（与 C1-001 同代码点，合并归并） |
| C2-003 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:110-120 | addJob raced 分支并发 remove 下静默 return 丢注册（无异常无日志），与 existing 分支 fall-through 重建语义相反 | 新发现（与 C1-005 同代码点，合并归并） |
| C2-004 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:88-93,112-120 | raced 分支绕过 allowUpdate=false 契约——并发首注册同名 job 时静默改写对方 spec，应抛 ERR_JOB_ALREADY_EXISTS 而未抛 | 新发现 |
| C2-005 | P3 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:94-103,112-120,256-264 | addJob 更新分支重排程前不复查 active——与 deactivate 并发可滞留 1 条存活 timer 至下次触发点（空转自愈，有界） | 新发现 |
| C2-006 | P3 | nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:54,88-103,124-128 | registeredCursors ArrayList 迭代无守卫、newCursor 无 running fail-fast——现网子类不可触发，扩展点守卫缺失（javadoc 契约无运行时防线） | 新发现 |
| C2-007 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:313-346,209-224,429-445,487-494 | 同步 invoker 执行期间 job.running 占位未置位——fireNow 可重入递归（叠加 catch(Exception) 致 SOE 逃逸后该 job 调度静默永久死亡、监控假 RUNNING）、awaitIdle 执行全程误报空闲、update 运行中守卫失效 | 新发现 |
| C2-008 | P3 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:313-321,559-595 | SimpleExecutionContext 持 live SimpleJobState 泄漏给 invoker——自定义异步 invoker 延迟读 plain long 与持锁写方构成 JMM 数据竞争（内置 invoker 全部不触发） | 新发现（与 C1-002 同泄漏点，合并归并） |

**复核后总量**：保留 8（P1×1、P2×4、P3×3）、降级 0、驳回 0——初审/深挖判级全部维持；现状全部为"新发现"（历史 nop-job 审计 2026-05/06 各轮、cra-index 台账、git log 均无同机制登记）。

**需子项复核的高风险项**：C2-001（本单元唯一 P1）。理由：修复方案（把 invokeAsync 移出 synchronized(job)、锁内完成状态迁移后锁外调 invoker）涉及 executeJob 锁范围重构，与 C2-007 的占位前置修复（running=done 前移至 invokeAsync 之前）作用于同一段代码，两项修复须合并设计并回归"已完成 future 同步回调覆盖 running"既有防护（:330-332 注释所述场景），建议修复阶段作为单独子项复核。
