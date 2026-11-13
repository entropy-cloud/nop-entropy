# [nop-job] C1 共享可变状态与可见性 — 第 1 轮初审（盲审）

- 审计单元：`nop-job` 聚合器（12 个 jar 子模块，src/main/java 手写代码 164 files / 17,664 lines，不含 `_` 前缀生成文件与 target/）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- Owner doc：docs-for-ai/03-modules/nop-job.md（已全文读取）
- grep 基线：_tmp/cra-baseline-nop-job.txt（已全文读取）
- 审计日期：2026-10-14；维度：C1 共享可变状态与可见性；审计人：并发审计子代理（盲审，未读 ai-dev/audits|plans|bugs|lessons 下任何既有文件）
- vendored 边界声明：nop-job-core 的 utils/CronExpression、utils/ICronExpression、calendar/BaseCalendar 按纪律只审集成边界，不审算法内部。

## 检查范围说明

**逐文件覆盖方式**：

| 子模块 | 覆盖方式 |
|--------|---------|
| nop-job-core | 全文读：AbstractBatchScanner、trigger/*（JobTriggerCalculator/OnceTrigger/PeriodicTrigger/HandleMisfireTrigger/TriggerBuilder）；CronExpression 读 ThreadLocal 集成边界；calendar/*（Annual/Base/Daily/Holiday/Monthly/Weekly）经字段清单核查（build 期构造、运行期只读）；其余包装 trigger（CheckActive/LimitCount/LimitTime/AnchorRepeat/PauseCalendar/CronTrigger）经字段清单核查为 final 装配型不可变 |
| nop-job-local | 全文读：LocalJobScheduler、LocalJobConfigLoader、BeanMethodJobInvoker、BeanContainerInvokerResolver |
| nop-job-coordinator | 全文读：JobCoordinator、全部 5 scanner（Planner/Dispatcher/Completion/Timeout/CounterReconciler）、RpcPollTaskManager、RemoteJobInvoker、DefaultRpcPollTaskClient、DefaultJobCancelHandler、DefaultWorkerLoadProvider、全部 task builder（Default/AbstractService/Adaptive/Partition/RpcBroadcast）、LeastLoadedStrategy、metrics 3 实现 |
| nop-job-worker | 全文读：JobWorker、JobWorkerScannerImpl、DefaultJobExecutionContextBuilder、DefaultJobInvokerResolver、MetadataWorkerCapacityProvider、JobWorkerMetricsImpl |
| nop-job-dao | 全文读：JobFireStoreImpl、JobTaskStoreImpl、NopJobTask.getEffectiveParams；JobScheduleStoreImpl 经字段清单核查（唯一实例字段 daoProvider）+ 方法抽查（纯 QueryBean 构造，无实例状态）；state machine helper（JobSchedule/Task/FireStateMachine）grep 核实为 static 纯函数 + static final List.of |
| nop-job-api | 全文读：JobLogReporter；其余（JobSpec/TriggerSpec/JobFireResult/JobDetail/JobInstanceState/config DTO/beans）经字段清单核查为请求级 DTO 或 final 事件对象 |
| nop-job-service | 全文读：NopJobScheduleBizModel、NopJobFireBizModel、NopJobTaskLogBizModel、RpcJobInvoker、NopE2eTestJobInvoker（基线核实为无状态 completedFuture）、JobContextHelper |
| nop-job-retry-adapter | 全文读：NopRetryJobRetryBridge |
| nop-job-app | 全文读：NopJobApplication |
| nop-job-web / nop-job-meta / nop-job-codegen | find 核实无 src/main/java 源文件 |

**方法**：先以全量字段 grep（非 final 实例/静态字段清单）盘点五类载体，再对每个命中共享可变状态的类全文阅读并回答"谁写、谁读、靠什么同步建立 happens-before"；并发原语（synchronized/volatile/Atomic/ThreadLocal/线程池）全模块 grep 交叉验证，与基线一致。

## 发现条目

### [nop-job-C1-001] LocalJobScheduler.getJobDetail() 无锁读取受 job 监视器保护的可变状态（可见性竞态）

**文件**：nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:64-67（读方）、472-507（被读状态）、348-350（写方）

**证据片段**：
```java
// 读方：无 synchronized(job)
@Override
public JobDetail getJobDetail(String jobName) {
    ScheduledJob job = jobs.get(jobName);
    return job == null ? null : job.toJobDetail();   // toJobDetail 直接读 spec/state.*
}
// 写方：全部在 synchronized(job) 内
private void handleResult(ScheduledJob job, JobFireResult result, Throwable err) {
    job.state.fireCount++;
    job.state.lastEndTime = currentTime();
```
```java
// 被读状态：非 volatile、非 final，写均在 job 监视器下
static class ScheduledJob {
    final SimpleJobState state;
    JobSpec spec;              // update() 中可被替换
    IJobInvoker invoker;
    ...
    long fireCount; long lastScheduledTime; long lastEndTime;   // SimpleJobState
```

**严重程度**：P2

**并发场景**：LocalJobScheduler 为单例服务，管理线程（GraphQL/管理入口调用 `getJobDetail`）与调度线程（executor 回调内 `handleResult`/`update`）并发访问同一 `ScheduledJob`。写方全部持有 `synchronized(job)`，读方 `getJobDetail` 是全类唯一不持锁读 `state`/`spec` 的公开路径。

**触发时序**：
- T1（调度线程）：进入 `whenComplete → synchronized(job)`，执行 `job.state.fireCount++`、`job.state.lastEndTime = now`；
- T2（管理线程）：调用 `getJobDetail("x")` → `job.toJobDetail()`，无任何同步 → JMM 下 T2 与 T1 的写之间无 happens-before，T2 可无限期读到过期 `fireCount/lastEndTime/lastScheduledTime`；若 T1 正在 `addJob(allowUpdate=true)` 的 `update()` 中替换 `spec`（第 487-494 行），T2 还可读到"旧 spec + 新 fireCount"的撕裂快照。long 字段在 32 位 JVM 上另有字撕裂可能（JLS 17.7 允许非原子 long 写）。

**现状**：同类的其余公开读方全部正确持锁——`getJobState()`（155-157 行 `synchronized (job)`）、`awaitState/awaitIdle/awaitFireCount`（413/435/455 行均持锁），唯独 `getJobDetail` 遗漏。

**风险**：观测/管理路径返回过期或不一致的任务状态（fireCount 回退、spec 与计数错配），误导运维判断；无数据损坏（写方互斥未破坏）。属可观测性缺陷而非主路径数据竞争。

**建议**：`getJobDetail` 内对 `job` 加 `synchronized (job)` 快照后再构造 `JobDetail`（与 `getJobState` 同构），或将 `toJobDetail` 改为持锁快照字段后构建。一行级修复。

**失效面枚举（F3 全类 grep）**：`ScheduledJob`/`SimpleJobState` 的全部公开读方共 6 处——`getJobNames()`（CHM keySet 拷贝，安全）、`getJobState()`（持锁，安全）、`awaitState()`（持锁，安全）、`awaitIdle()`（持锁，安全）、`awaitFireCount()`（持锁，安全）、`getJobDetail()`（**缺陷，本条**）。分布式侧同族观测接口 `RpcPollTaskManager.activeCount()` 读 CHM.size()，安全。失效面 = 1 处。

**信心水平**：高（JMM 数据竞争定义直接命中；同类其余读方持锁的反差证明其为遗漏而非约定）。

**误报排除**：非"无状态单例被并发调用"——被读字段确为跨线程可变状态；非 CHM/synchronized 原语本身误报；`getJobState` 已持锁证明平台约定是持锁读，本条不在豁免之列。

**复核状态**：已保留（P2 维持。文件/行号逐点核实无误：getJobDetail 64-67 行无锁、handleResult 写方 348-350 行持锁、update 487-494 行换 spec、其余 5 个公开读方均持锁；唯一漏网读方定性成立。历史 nop-job 审计 2026-05/06 与 check/check2 均未登记 getJobDetail 无锁读，同文件 check2 [P3-13] SUSPENDED 热更新已修复（4bc1a7457c）系另一问题）

---

### [nop-job-C1-002] SimpleExecutionContext 将 live 可变 jobParams Map 与 SimpleJobState 逃逸给 invoker 用户代码（理论风险，未具体化）

**文件**：nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:317、559-595

**证据片段**：
```java
// executeJob 中构造执行上下文，直接持有 live state 与 spec
SimpleExecutionContext ctx = new SimpleExecutionContext(job.spec, job.state);
...
static class SimpleExecutionContext implements IJobExecutionContext {
    private final JobSpec spec;
    private final SimpleJobState state;      // live 可变状态引用
    ...
    @Override public Map<String, Object> getJobParams() { return spec.getJobParams(); }  // 原始可变 Map
```

**严重程度**：P2（理论风险，未具体化——依赖 invoker 用户代码主动变异）

**并发场景**：`LocalJobScheduler` 是公开嵌入式 API，`IJobInvoker` 为用户实现。每次触发把 `spec.getJobParams()` 返回的同一可变 Map（跨所有触发、所有线程共享）与 live `SimpleJobState` 交给 invoker。平台自带的 `BeanMethodJobInvoker` 做了防御性拷贝（`extractMethodParams` → `new LinkedHashMap<>(params)`），但自定义 invoker 无任何契约约束。

**触发时序**（写不出平台内部触发时序，以下为用户代码假设路径）：T1 触发 #1 的 invoker 实现 `ctx.getJobParams().put("key", ...)`（或异步线程在触发 #2 进行中仍持有该 Map 写入）→ 触发 #2、#3 … 的 `TriggerBuilder`/`BeanMethodJobInvoker.resolveMethod` 读到被污染的参数 → 后续所有触发的行为漂移，且 `spec` 与 `jobs` 表中其他读方（`toJobDetail`）共享同一被污染对象。

**现状**：无防御性拷贝、无不可变包装（`Collections.unmodifiableMap`）、无 javadoc 声明"invoker 不得变异 jobParams"。分布式路径对应物 `DefaultJobExecutionContextBuilder.resolveJobParams` 经 `task.getEffectiveParams(fire)`（已核实为 `new LinkedHashMap` 防御性拷贝）后再 `computeIfAbsent`，不逃逸实体原始 Map——本地模式是同族中唯一逃逸 live Map 的路径。

**风险**：用户 invoker（尤其异步 invoker 在回调线程写参数）导致跨触发参数污染与跨线程可见性竞态；`SimpleJobState` 逃逸还允许用户代码在 `getExecCount()` 等读取窗口外观察/干扰调度器内部状态。平台内部代码不触发，故降 P2。

**建议**：`SimpleExecutionContext.getJobParams()` 返回 `Collections.unmodifiableMap(spec.getJobParams())`（或构造时浅拷贝）；javadoc 声明 invoker 不得持有 context 引用跨触发使用。

**失效面枚举（F3 全类 grep）**：`IJobExecutionContext` 实现共 3 处——`SimpleExecutionContext`（**缺陷，本条**）、`DefaultJobCancelHandler.CancelJobExecutionContext`（构造时冻结 final 字段 + `resolveJobParams` 拷贝链，`jobParams.computeIfAbsent` 变异的是 `getEffectiveParams` 拷贝或新建 LinkedHashMap，安全）、`DefaultJobExecutionContextBuilder.WorkerJobExecutionContext`（同前，安全）。`JobInstanceState.setJobParams` 基类持有引用但生命周期为单次执行。失效面 = 1 处。

**信心水平**：中高（逃逸事实确定；是否构成实际缺陷取决于用户契约严格度，故按"写不出内部触发时序"规则降 P2 并标注）。

**误报排除**：非"测试专用代码"——SimpleExecutionContext 是本地模式主执行路径；已排除 `getEffectiveParams` 拷贝路径（确认分布式侧无同缺陷，避免误报同类）。

**复核状态**：已保留（P2 维持。live code 核实：SimpleExecutionContext 572 行返回 spec.getJobParams() 原始 Map、561 行持 live state；BeanMethodJobInvoker.java:192 extractMethodParams 确为 new LinkedHashMap 防御拷贝；NopJobTask.getEffectiveParams 与 DefaultJobExecutionContextBuilder.resolveJobParams:161 双路均新建拷贝，分布式/本地不对称成立。历史审计无同族登记）

---

### [nop-job-C1-003] AbstractBatchScanner 公开 scanOnce() 逃逸"scheduleWithFixedDelay 单线程"声明契约，无运行时 fail-fast 守卫（F10）

**文件**：nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:88-103、262-296

**证据片段**：
```java
public void scanOnce() {          // public，javadoc 明示"对同包测试与 lifecycle 调用方都公开"
    for (Cursor c : registeredCursors) {
        c.reset();               // 无 running/并发守卫
    }
    onCycleStart();
    for (int i = 0; i < maxScanLoops; i) { ... scanBatch() ... }
}
...
public static final class Cursor {   // javadoc: "非线程安全（依赖 scheduleWithFixedDelay 单线程调度语义）"
    private Timestamp time;
    private String id;
    private boolean drained;
```

**严重程度**：P2

**并发场景**：扫描器全部状态（`Cursor.time/id/drained` 三元组 + 各子类周期状态）依赖"同一时刻至多一个 scanOnce 在执行"的声明契约（Cursor javadoc 明示）。该契约仅靠 `scheduleWithFixedDelay` 的调度语义建立，而 `scanOnce()` 是 public 入口，任何外部调用方（javadoc 主动邀请 lifecycle/运维调用）都在守卫之外；`doScan()` 只检查 `running` 布尔，不检查是否有 scanOnce 在途。声明的单线程契约无任何运行时 fail-fast 守卫（无 inFlight CAS、无锁）。

**触发时序**：
- T1（timer 线程）：`doScan → scanOnce`，正在 `drainBatch` 中执行 `cursor.advance(lastTime, idFn)`（282-285 行，先写 time 再写 id，非原子）；
- T2（管理/测试线程）：并发调用公开的 `scanOnce()` → 入口 `c.reset()` 把 T1 刚推进的 cursor 清零 → T1 下一轮 drainBatch 以 `(null, null)` 重新 fetch 已处理窗口 → 同一批 fire/task 被二次处理（重复告警/重试事件触发）；反向交错（T2 reset 后 T2 advance 与 T1 advance 并发）可产生 `(新time, 旧id)` 撕裂 cursor，静默跳过一批记录。

**现状**：契约只写在 javadoc（262-263 行），无 `AtomicBoolean scanning` 之类守卫；`running` volatile 只用于 start/stop 生命周期，不防并发周期。

**风险**：外部一次性手动触发与定时 tick 相撞时产生重复处理或撕裂 cursor；因 in-module 当前无并发调用方（6 个 scanner 均仅由 timer 驱动），实际暴露面取决于部署方对 public 方法的使用，故 P2 而非 P1。

**建议**：`scanOnce()` 入口加 `AtomicBoolean` CAS 守卫（在途则记 warn 直接返回，fail-fast 可观测），或将方法降为 protected 并提供带守卫的公开包装。

**失效面枚举（F3 全类 grep）**：`AbstractBatchScanner` 子类共 6 个——JobPlannerScannerImpl、JobDispatcherScannerImpl、JobCompletionProcessorImpl、JobTimeoutCheckerImpl、JobScheduleCounterReconciler、JobWorkerScannerImpl，全部继承同一 public `scanOnce()` 与非线程安全 Cursor 机制，失效面 = 基类 1 处 × 6 个暴露实例；`registeredCursors`（ArrayList）仅构造期写入（`newCursor()` 在子类字段初始化器中），运行期只遍历，容器发布后安全，同族无第二缺陷点。

**信心水平**：中高（交错后果具体化成立；暴露概率依赖外部调用行为）。

**误报排除**：非"低频路径粗粒度锁"类豁免——这里是守卫完全缺失而非锁粒度问题；非平台基础设施自身实现（本类是 nop-job 自己的代码）；Cursor 的"非线程安全"是已声明边界，但**已声明边界无守卫且公开逃逸**正是 F10 的立案形态（对照：LocalJobScheduler 对同类契约用 `synchronized(job)` 提供了守卫，证明平台标准是有守卫的）。

**复核状态**：已保留（P2 维持。live code 核实：scanOnce 88-103 行 public 无任何在途守卫，Cursor 282-285 行 advance 先 time 后 id 非原子、291-295 行 reset 无同步；全 src/main 除 doScan 外零调用方（grep 核实，命中均为 javadoc 文字），"基类 1 处 × 6 实例"失效面成立。历史审计中 scanOnce 仅以测试覆盖（2026-05-18 F-16-5）与错误处理面出现，并发守卫缺口为新发现）

---

### [nop-job-C1-004] RpcPollTaskManager.register() 复合注册非原子：调度器拒绝时异常在 whenComplete 动作内静默消失，注册表条目泄漏且 promise 永不完成

**文件**：nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:105-128、135-151；RemoteJobInvoker.java:100-113

**证据片段**：
```java
// register()：put → 挂取消监听 → 调度 → 挂 whenComplete，四步之间无原子性
entries.put(entry.getTaskId(), entry);                 // 步骤1：先入注册表
if (cancelToken != null) { ...; cancelToken.appendOnCancel(onCancel); }
Future<?> handle = scheduledExecutor().scheduleWithFixedDelay(   // 步骤3：可抛 RejectedExecutionException
        () -> pollOnce(entry), pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
entry.setPollHandle(handle);
future.whenComplete((r, e) -> cancelEntry(entry));     // 步骤4：步骤3抛异常则永不执行
```
```java
// 调用方：register 在 startJob 的 whenComplete 动作内调用
rpcPollTaskClient.startJob(schedule, fire, task, jobCtx.getCancelToken())
        .whenComplete((remoteTaskId, err) -> {
            ...
            pollTaskManager.register(schedule, fire, task, jobCtx.getCancelToken(), future);  // 抛出即被吞
        });
```

**严重程度**：P2

**并发场景**：`register()` 由 RPC 回调线程调用（每任务一个），`entries` 为跨线程共享注册表；`future` 可被 poll 线程/取消回调线程并发 complete。复合操作"入表 → 挂监听 → 调度 → 挂终结清理"任一步失败时前两步已生效，无回滚路径。

**触发时序**：
- T1（RPC 回调线程）：`startJob` 成功 → 进入 `register()` → `entries.put` 成功、`appendOnCancel` 成功；
- T1 继续执行 `scheduleWithFixedDelay` 时 executor 已 shutdown（应用停机窗口）→ 抛 `RejectedExecutionException`；
- 该异常发生在 `startJob` future 的 whenComplete **动作**内 → 无 catch、无日志、无处传播 → `RemoteJobInvoker` 返回的 `future` 永不 complete；
- 后果链：`entries` 中该 PollEntry 永久滞留（无 whenComplete → 无 cancelEntry → 无 remove），cancel 监听器永久挂在 token 上；`JobWorkerScannerImpl.executeTask` 注册的 `promise.whenComplete(handleExecutionResult)` 永不触发 → task 行停留 RUNNING，只能依赖 worker-liveness 链（SUSPICIOUS→TIMEOUT，需 namingService 接线）或执行超时配置回收；未配置时永久滞留且**全程零日志**。

**现状**：`cancelEntry` 幂等且 `future.isDone()` 双保险（123-127 行）只覆盖"future 先完成"方向；对"注册中途抛异常"方向无 try/finally 回滚，`RemoteJobInvoker` 的 whenComplete 动作对 register 抛出也无兜底 complete。

**风险**：停机/池关闭窗口内的注册表泄漏 + promise 挂起 + task 行滞留；稳态运行期不可达（池不关闭），故 P2。含 C3 形态（异步链异常静默吞掉）但评估为 P2（有 DB 兜底链），不满足维度集外线索 P0/P1 立案门槛。

**建议**：`register()` 体整体 try/catch：异常时回滚（`cancelEntry(entry)` 摘监听 + `entries.remove`）并 `future.complete(JobFireResult.ERROR(...))`；或 `RemoteJobInvoker` 的 whenComplete 动作内对 register 调用 try/catch 兜底 complete promise 并记 error 日志。

**失效面枚举（F3 全类 grep）**：模块内"注册表 + 监听器 + 句柄"复合注册仅此一处（`RpcPollTaskManager.register`）；同族对照——`LocalJobScheduler.addJob` 的复合注册（putIfAbsent → synchronized(job) → scheduleNext）有 `!active` 回滚分支（123-127 行）覆盖 deactivate 竞态，但其 raced 分支另有本报告 C1-005 的语义缺陷；`DefaultJobCancelHandler` 无注册表。失效面 = 1 处。

**信心水平**：高（whenComplete 动作内异常被 future 框架吞掉是 JDK 明确定义行为；RejectedExecutionException 在停机窗口必然可达）。

**误报排除**：非有界声明的队列/池误报——泄漏的是注册表条目与挂起的 promise，非线程池容量；未报"并发容器 put 原语本身"——缺陷在复合操作缺回滚，不在 CHM。

**复核状态**：已保留（P2 维持。live code 核实：register 109-127 行四步复合注册无 try/catch 回滚，scheduleWithFixedDelay 119-120 行可抛 REE；RemoteJobInvoker 111 行 register 位于 startJob 的 whenComplete 动作内，动作抛出即被 future 框架吞掉、promise 永不 complete——JDK 语义直接命中；nopRpcPollScheduledExecutor 为独立 destroy-on-container-close bean（app-engine.beans.xml:88），REE 窗口确为停机窗口，P2 判级自洽。同族先例 check-findings P1"rpcPoll 轮询句柄 cancel（每次执行泄漏空转调度项）"已修复（72b770b094），系终结方向泄漏；本条为注册中途异常无回滚，方向不同，未见历史登记）

---

### [nop-job-C1-005] LocalJobScheduler.addJob() raced 分支静默丢弃作业注册，与 existing 分支的 fall-through 语义不一致

**文件**：nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:110-120（对照 88-104）

**证据片段**：
```java
ScheduledJob job = new ScheduledJob(spec, invoker, trigger, state);
ScheduledJob raced = jobs.putIfAbsent(spec.getJobName(), job);
if (raced != null) {
    // Another thread inserted first — treat as update.
    synchronized (raced) {
        if (!jobs.containsKey(spec.getJobName())) {
            return;                       // ← 静默丢弃本次 add，无异常无日志
        }
        raced.update(spec, invoker, trigger, state);
        rescheduleAfterUpdate(raced);
    }
}
// 对照：existing 分支对同一竞态选择 fall-through 重建
synchronized (existing) {
    if (!jobs.containsKey(spec.getJobName())) {
        // job was removed; fall through to create-and-schedule below   ← 语义：重建
    } else { ... }
}
```

**严重程度**：P2

**并发场景**：`addJob` 与 `removeJob`（或 `deactivate`）在不同管理线程并发操作同一 jobName。`jobs` 为 CHM，`putIfAbsent` 与其后的 `containsKey` 检查是两步"检查-然后-行动"，中间窗口内 remove 可插 入；`removeJob`（第 138 行）先 `jobs.remove` 后才 `synchronized(job)`，故 raced 锁内的 `containsKey` 复查不能排除已发生的移除。

**触发时序**：
- T2：`addJob("x", allowUpdate=true)`，第一次 `jobs.get("x")` 为 null（T3 尚未 put），构造 job2，执行 `putIfAbsent` → 返回 raced=job1（T3 刚放入），T2 在 job1 锁上等待；
- T3：`removeJob("x")` → `jobs.remove("x")` 成功，锁 job1 取消调度；
- T2：获得 job1 锁 → `jobs.containsKey("x")` 为 false → **return**：job2 被丢弃、job1 已被移除、`addJob` 无异常无日志地"成功"返回 → 作业 "x" 既不存在也无任何留痕。同竞态走 existing 分支（88-104 行）则会 fall-through 重建——两个分支对同一竞态语义相反。

**现状**：raced 分支的 `return` 与注释（106 行 "New job or existing was removed while we waited"）表达的重建意图矛盾；无异常、无 WARN。

**风险**：并发管理操作下作业静默丢失且不可观测（对比 first 分支至少会重建）。触发要求同 jobName 的 add/remove 毫秒级并发，管理路径低频，故 P2（保守取 P2；若按"罕见但可具体化"口径可 argued P1，因结果与并发 remove 的胜出语义部分重合——remove-wins 下作业本就不该存在——故未上调）。

**建议**：raced 分支 `containsKey==false` 时与 existing 分支对齐：释放锁后 fall-through 重试 putIfAbsent（或循环重试一次），至少记 WARN 留痕。

**失效面枚举（F3 全类 grep）**：`LocalJobScheduler` 内"先查后放"复合路径共 2 处——existing 分支（fall-through 重建，正确）、raced 分支（**缺陷，本条**）；另有 putIfAbsent 后 `!active` 回滚分支（123-127 行，正确）。分布式侧同族"检查-然后-放入"：`RpcPollTaskManager.register`（见 C1-004，缺陷形态不同）、`JobFireStoreImpl.insertTasksAndMarkFireDispatching`（DB 乐观锁原子判定，正确）。失效面 = 1 处。

**信心水平**：高（交错时序可在双管理线程下确定复现；语义不一致有源码直接对照）。

**误报排除**：非"CHM 原语本身"误报——putIfAbsent 正确，缺陷在其后的两步判定；非测试专用路径——addJob 是本地模式注册主入口。

**复核状态**：已保留（P2 维持。live code 核实：raced 分支 115-116 行 containsKey==false 即 return 无异常无日志，与 existing 分支 96-97 行 fall-through 重建语义相反；removeJob 138 行先 jobs.remove 后持锁，锁内复查不能排除已发生的移除——两分支反差由源码直接证明非有意约定。判 P2 而非 P1 的论证（remove-wins 语义部分重合、管理路径低频）成立。历史登记 2026-06-03 [09-01] addJob 错误码误用系另一问题，无同族竞态登记）

---

### [nop-job-C1-006] CronExpression.threadLocalCal（vendored）无 remove，集成路径按次新建实例造成池化线程 ThreadLocal 陈旧条目

**文件**：nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java:87、133、152、194（vendored，仅审集成边界）

**证据片段**：
```java
private ThreadLocal<GregorianCalendar> threadLocalCal;      // 每实例一个 ThreadLocal，无 remove 路径
...
this.threadLocalCal = ThreadLocal.withInitial(() -> { ... });   // 两处构造器各建一次
...
Calendar calendar = threadLocalCal.get();                    // getTimeAfter 每次计算取用
```

**严重程度**：P3

**并发场景**：两类集成路径——(a) 分布式 planner：`TriggerBuilder.buildTrigger`（JobPlannerScannerImpl.calculateNextFireTime 每周期每次计算调用）每次 new `CronExpression` → 在扫描执行线程的 ThreadLocalMap 中留下 key 弱引用/value 强引用的条目，实例丢弃后条目陈旧化；(b) 本地模式：`LocalJobScheduler` 长 lifecycle 持有 per-job `CronTrigger.cronExpression`，每个 cron job 在每个执行线程驻留一个 GregorianCalendar。

**触发时序**：非竞态——无跨线程共享变异；风险形态是池化线程（GlobalExecutors.globalWorker/timer 长生命周期线程）上的 ThreadLocal 条目滞留。(a) 路径的陈旧条目由 JDK 弱引用机制在后续 ThreadLocal 访问（cleanSomeSlots）中启发式清除，同线程每 5s 扫描即触发清理；(b) 路径每 job 每线程恰 1 个 Calendar（约百字节级），上界 = job 数 × 线程数，有界。

**现状**：无任何 `remove()` 调用（全模块 grep 核实）；与 `DefaultWorkerLoadProvider.scanCache`（`endScan` 中 `remove()`，同基线注释 check2 [P3-6]）形成对照——平台自有代码已按规范清理，vendored 代码未清理但量级有界。

**风险**：常驻内存微增（KB 量级、有界）、GC 启发式清理依赖；无正确性影响、无无界增长。

**建议**：维持 vendored 不动（改动违反 vendored 纪律）；如需治理，仅在集成侧文档记录该已知边界。不立案整改。

**失效面枚举（F3 全类 grep）**：模块内 ThreadLocal 共 2 处——`DefaultWorkerLoadProvider.scanCache`（begin/end-scan 配对 + remove，安全）、`CronExpression.threadLocalCal`（**本条**，vendored）。失效面 = 1 处（vendored 边界内）。

**信心水平**：高（事实清晰）；严重度判断为 P3（防御性冗余/内存卫生）。

**误报排除**：不报 P2"池化线程 ThreadLocal 未清理"——写出的事实上界有界且 JDK 自愈，按"写不出具体危害时序"降 P3；平台侧 scanCache 已正确清理，不重复报。

**复核状态**：已保留（P3 维持。live code 核实：CronExpression 87/133/152/194 行与所述一致，全文件无 remove；集成双路核实——TriggerBuilder:33 每次新建 CronExpression（planner 每周期计算路径）、CronTrigger final 持有（本地长 lifecycle 路径），vendored 仅审集成边界口径合规。同族先例：check2 [P3] scanCache ThreadLocal 已修复（35623e49cf）；check2 对 CronExpression ThreadLocal 既有结论为"用法正确"（NPE 不变式视角），remove 缺失角度未见登记，定性新发现）

## 共享可变状态清单表

| # | 载体类别 | 位置 | 谁写 | 谁读 | 同步机制 | 判定 |
|---|---------|------|------|------|---------|------|
| 1 | 单例 bean 实例字段 | LocalJobScheduler.jobs（CHM）/active（volatile）/ScheduledJob.*（spec/invoker/trigger/scheduledFire/running/SimpleJobState 全字段） | 管理线程（add/remove/suspend/resume/cancel/fireNow/deactivate）+ 调度回调线程 | 同左 + 观测接口 | CHM + volatile active + per-job `synchronized(job)` 监视器覆盖全部变异路径 | 唯一漏网读方 getJobDetail → C1-001 |
| 2 | 单例 bean 实例字段 | AbstractBatchScanner：running（volatile）/scanFuture（锁内读写）/scanIntervalMs/batchSize/maxScanLoops/partitionResolver（仅 init 期写）/registeredCursors（仅构造期写）/Cursor（单线程变异） | IoC init 线程 + timer 扫描线程 | timer 扫描线程 | volatile + synchronized start/stop + scheduleWithFixedDelay 单线程语义（声明契约） | 契约公开逃逸无守卫 → C1-003；init 期字段经容器发布安全 |
| 3 | 单例 bean 实例字段 | 6 个 scanner 子类配置字段（lockTimeoutMs/maxConcurrency/enforceAttribution/assignedPartitions 等）与 store 引用 | IoC init 线程 | 各自 timer 扫描线程 | 容器发布 happens-before（NopIoC 标准） | 安全，不立案 |
| 4 | 单例 bean 实例字段 | JobTimeoutCheckerImpl：workerServiceName（volatile）/livenessDisabledWarned（AtomicBoolean CAS）/dispatchTimeoutMs 等（init 期） | IoC init / 首次扫描 | 扫描线程 | volatile + CAS | 安全（CAS 用法正确，warn-once 语义保持） |
| 5 | 单例 bean 实例字段 | MetadataWorkerCapacityProvider：cached（volatile DCL）/metadataSource/configCpu/configMemory（init 期） | init 线程 + 首次 getMyCapacity | worker 扫描线程 | volatile + synchronized(this) 双检 | DCL 模式正确；resolveCapacity 抛错为有意的 fail-fast（AR-90），不立案 |
| 6 | 单例 bean 实例字段 | RpcPollTaskManager：entries（CHM）/PollEntry.pollHandle/onCancelListener（volatile）/scheduledExecutor 等（init 期） | RPC 回调线程 + poll 线程 + 取消回调线程 | 同左 | CHM + volatile + CompletableFuture CAS-complete；cancelEntry 幂等 + 双保险 | 复合注册无回滚 → C1-004 |
| 7 | 单例 bean 实例字段 | JobDispatcherScannerImpl.taskBuilders / DefaultWorkerLoadProvider / builder 们 / DefaultRpcPollTaskClient / RpcJobInvoker / JobLogReporter / BizModel store 字段 | IoC init（@Inject/collect-beans） | 扫描/请求线程 | 容器发布 | 全部无状态或 init 后只读，安全 |
| 8 | 单例 bean 实例字段 | JobCoordinator/JobWorker 的 scanner 引用 | IoC init | doStart/doStop | LifeCycleSupport 容器发布 | 安全 |
| 9 | static 可变字段 | NopJobApplication.globalArgs | main 线程（Quarkus.run 前） | StartupEvent 观察者 | Quarkus 启动内部同步建立 HB | 安全（demo app），记录不立案 |
| 10 | static final 集合 | JobFireStateMachine.ACTIVE_STATUSES/RECOVERABLE_STATUSES、JobTaskStateMachine.IN_FLIGHT/RUNNING_LIKE、NopJobTaskLogBizModel.LOG_LEVEL_CODES（Map.of）、_gen PROP_NAME_TO_ID（static 块初始化后只读） | 类初始化 | 全体 | final + 不可变容器 + 类初始化语义 | 安全 |
| 11 | static 工具 | JobTriggerCalculator/TriggerBuilder/CalendarBuilder/JobQueryHelper/state machine helper/JobContextHelper | — | — | 无状态静态纯函数 | 安全 |
| 12 | 全局注册表 | LocalJobScheduler.jobs、RpcPollTaskManager.entries | 见 #1/#6 | 同左 | CHM | 见 C1-001/004/005 |
| 13 | 缓存 | DefaultWorkerLoadProvider.scanCache（ThreadLocal<Map>） | 扫描线程自身 | 同线程（beginScan..endScan 作用域，buildTasks 与 begin/end 同线程已核实） | ThreadLocal 隔离 + endScan remove() | 安全（与基线 check2 [P3-6] 结论一致） |
| 14 | ThreadLocal | CronExpression.threadLocalCal（vendored） | 各执行线程 | 同线程 | ThreadLocal 隔离 | 无 remove，有界滞留 → C1-006（P3） |
| 15 | 逃逸可变引用 | SimpleExecutionContext → spec.getJobParams() 原始 Map + live SimpleJobState | invoker 用户代码潜在写 | 调度器 + invoker | 无 | C1-002（P2 理论） |
| 16 | 逃逸可变引用 | RpcPollTaskManager.PollEntry 捕获 schedule/fire/task 实体（capture 时冻结，javadoc 明示不查 DB） | 注册时一次性冻结 | poll/cancel 线程 | 构造后只读（final 字段） | 已声明边界，安全 |
| 17 | 逃逸可变引用 | DefaultJobExecutionContextBuilder/DefaultJobCancelHandler 的 resolveJobParams computeIfAbsent 变异 | 构造线程 | 单次执行上下文 | 变异对象为 getEffectiveParams 防御性拷贝（NopJobTask.getEffectiveParams 已核实 new LinkedHashMap） | 安全，不立案 |
| 18 | 数据库权威状态 | schedule/fire/task 行状态机（6 scanner + stores + BizModel） | 多节点多线程 | 同左 | DB 乐观锁（tryUpdateWithVersionCheck / tryUpdateManyWithVersionCheck）+ 状态机谓词 + per-fire 异常隔离 | 属 C4/C7 维度主体；C1 视角确认无进程内共享可变缓存（store 全部无实例状态） |

## 覆盖度与残留边界

**覆盖度**：164 个手写文件中，并发语义核心文件（调度器、6 scanner、轮询管理器、invoker、cancel handler、load provider、容量提供者、全部 builder/strategy/resolver、4 store 中的 3 个全文 + 1 个字段清单核查、3 BizModel、loader、retry bridge、reporter、metrics 5 实现、生命周期类）全部全文阅读；纯 DTO/beans/spec/config（约 60 文件）与 vendored 算法内部（CronExpression/ICronExpression/calendar/*）按纪律以字段清单 + 集成边界方式核查。并发原语 grep（synchronized/volatile/Atomic/ThreadLocal/new Thread/ExecutorService/sleep/wait）与基线逐条比对，无基线外遗漏命中。

**残留边界**：
1. JobScheduleStoreImpl（483 行）仅做字段清单核查（唯一实例字段 `daoProvider`）+ 方法抽查，未逐行全文阅读——其方法族与 JobFireStoreImpl/JobTaskStoreImpl 同构（QueryBean 构造 + 乐观锁更新），残留漏报风险低。
2. `NopIoC 容器发布 happens-before`（init 线程配置字段 → 运行线程读取）按平台标准假设豁免，未验证 NopIoC 内部发布机制（属平台基础设施自身实现，按校准规则不审）。
3. `GlobalExecutors.globalTimer/globalWorker`、Micrometer GlobalMeterRegistry、`ICancelToken.appendOnCancel/removeOnCancel` 的线程安全性按平台基础设施假定，只审了使用方式（使用方式均已核实正确）。
4. cron/calendar vendored 算法内部按纪律未审；C1-006 只覆盖其 ThreadLocal 集成边界。
5. 测试代码（src/test）按校准规则未审——`LocalJobScheduler.awaitState/awaitIdle/awaitFireCount` 虽为测试辅助但位于 main 源集且公开，已纳入审阅（持锁正确）。
6. 维度集外线索：本轮无 P0/P1 级 C3/C6/C8/C9 形态新线索（C1-004 含 C3 形态但评估 P2，已在条目内说明，不立 CL 编号）。

**总量统计**：P0 = 0；P1 = 0；P2 = 5（C1-001/002/003/004/005）；P3 = 1（C1-006）。

## 维度复核结论（独立复核子代理）

- 复核人：C1 维度独立复核子代理（与初审/深挖轮不同会话，逐条重读 live code 验证）
- 复核日期：2026-10-15
- 复核范围：第 1 轮 C1-001~006 全部 6 条 + 第 2 轮（C1-round2.md，0 新增）零新增声明抽验；复核输入两份文件完整读取
- 复核方法：逐条重读 live 源码核对文件/行号/证据片段/触发时序；历史现状标注仅依据 ai-dev/audits 下 nop-job 历史审计目录（check/check2/2026-05/2026-06 各轮 summary 与结论节）、cra-index.md、git log；未读 ai-dev/plans|bugs|lessons

### 逐条判定表

| 发现编号 | 原判级 | 判定 | 一句话理由 | 现状标注 |
|---------|-------|------|-----------|---------|
| C1-001 | P2 | 保留 | 行号/证据逐点核实无误：getJobDetail 是 6 个公开读方中唯一不持 `synchronized(job)` 者，JMM 数据竞争定性成立，观测面危害与 P2 相称 | 新发现（历史 check/check2/2026-05/06 审计均无同族登记；同文件 check2 [P3-13] 已修复系另一问题） |
| C1-002 | P2 | 保留 | 逃逸事实核实（572 行返回原始 Map + 561 行持 live state），本地/分布式防御拷贝不对称成立，"理论风险未具体化"的降档论证自洽 | 新发现（历史审计无同族登记） |
| C1-003 | P2 | 保留 | public scanOnce 无在途守卫 + Cursor 非原子 advance/reset 核实，src/main 除 doScan 外零调用方（暴露面判断准确），已声明边界公开逃逸立案形态成立 | 新发现（历史 scanOnce 记录仅测试覆盖 F-16-5 与错误处理面，非并发守卫） |
| C1-004 | P2 | 保留 | 四步复合注册无回滚 + whenComplete 动作内异常被框架吞掉核实（JDK 明确定义），专用池 destroy-on-container-close 证实 REE 窗口=停机窗口，P2（含 C3 形态不升级）论证成立 | 新发现（同族先例 check-findings P1 轮询句柄泄漏已修复 72b770b094，系终结方向；注册中途异常方向未见登记） |
| C1-005 | P2 | 保留 | raced 分支静默 return 与 existing 分支 fall-through 重建的语义反差由源码直接证明，removeJob 先移除后持锁使锁内复查失效核实；不升 P1 的论证（remove-wins 语义部分重合）成立 | 新发现（2026-06-03 [09-01] addJob 错误码误用系另一问题） |
| C1-006 | P3 | 保留 | ThreadLocal 无 remove + 两条集成路径（planner 每周期新建 / 本地长 lifecycle 持有）核实，vendored 仅审集成边界口径合规，P3 内存卫生定性恰当 | 新发现（同族先例 check2 [P3] scanCache 已修复 35623e49cf；check2 对 CronExpression ThreadLocal 既有结论为"用法正确"，remove 缺失角度未登记） |

### 第 2 轮零新增声明复核

C1-round2.md 无发现条目（无复核状态行需回填）。其零新增结论经 5 个关键排除点独立抽验全部成立：(1) 全模块 src/main `new Thread` 零命中；(2) JobScheduleStoreImpl 唯一实例字段 daoProvider（残留边界关闭声明属实）；(3) MonthlyCalendar.getDaysExcluded 返回裸数组且 main 源集零调用方（WeeklyCalendar 有 clone 防御，check2 [P3-9] 修复未同步至 Monthly 属实）；(4) DefaultWorkerLoadProvider.endScan 用 remove()（check2 [P3-6] 修复在位）；(5) JobWorkerScannerImpl.handleExecutionResult 为 per-call DB 加载、无跨回调共享可变对象。未发现推翻零新增结论的反证。

### 不确定项（显式列出）

1. **C1-001 的 32 位 long 字撕裂子论点**：Java 25 实际部署面（64 位 HotSpot）下 long 写实测原子，该子论点基本不可达，属次要夸张；主判级（可见性过期 + spec/计数撕裂快照）不受影响。
2. **C1-005 触发时序的 T3 标签**：原文时序将"put job1"与"removeJob"先后归于同一 T3 标签，需另一 addJob 先插入 job1 才能使 putIfAbsent 返回 raced——交错真实可复现，但叙述标签略有歧义（单一线程先 addJob 后 removeJob、或双管理线程均可成立）。
3. **C1-003 javadoc 转述强度**：原文为"对同包测试与 lifecycle 调用方都公开"（动因是避免空壳 override），初审"主动邀请 lifecycle/运维调用"的转述略有加强；公开入口脱离单线程契约且无守卫的核心事实不变。

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|-----|------------|---------|-----------|------|
| C1-001 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:64-67 | getJobDetail 无锁读取受 job 监视器保护的 state/spec，是全部公开读方中唯一漏网者（可见性竞态/撕裂快照） | 新发现 |
| C1-002 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:317,559-595 | SimpleExecutionContext 逃逸 live jobParams Map 与 SimpleJobState 给用户 invoker，本地路径是同族中唯一无防御拷贝者（理论风险） | 新发现 |
| C1-003 | P2 | nop-job/nop-job-core/src/main/java/io/nop/job/core/AbstractBatchScanner.java:88-103,262-296 | public scanOnce 无并发守卫逃逸"scheduleWithFixedDelay 单线程"声明契约，并发调用可致 cursor 重置重复处理/撕裂（基类 1 处 × 6 scanner 实例） | 新发现 |
| C1-004 | P2 | nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcPollTaskManager.java:105-128（+RemoteJobInvoker.java:100-113） | register 复合注册中途抛异常（停机窗口 REE）无回滚：条目泄漏 + promise 永不 complete + 全程零日志（whenComplete 动作内异常被框架吞掉） | 新发现 |
| C1-005 | P2 | nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:110-120 | addJob raced 分支并发 remove 下静默丢弃注册（无异常无日志），与 existing 分支 fall-through 重建语义相反 | 新发现 |
| C1-006 | P3 | nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java:87,133,152,194 | vendored threadLocalCal 无 remove，池化线程 ThreadLocal 条目滞留（有界、JDK 启发式自愈，vendored 仅审集成边界） | 新发现（同族先例 check2 scanCache 已修复） |

**复核后总量**：保留 6（P2×5、P3×1）、降级 0、驳回 0——与初审/深挖累计统计一致；无 P0/P1，无需子项复核的高风险项。
