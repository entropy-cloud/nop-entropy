# nop-job 并发审计 — C2 锁与同步原语（第 2 轮深挖）

- 审计单元：`nop-job` 聚合器（12 jar 子模块，src/main/java 164 files / 17,664 lines，不含 `_` 前缀生成文件与 target/）
- 审计维度：C2 锁与同步原语（互斥成立性、死锁、锁代价）
- 仓库/分支：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit；concurrency-audit-remediation；以 live code 为准
- 盲审纪律：本轮仅读取前序输入 C2-locks-sync.md（第 1 轮）、owner doc（docs-for-ai/03-modules/nop-job.md）、grep 基线（_tmp/cra-baseline-nop-job.txt）+ live code；未读取 ai-dev/audits|plans|bugs|lessons 下其他任何文件
- 本轮聚焦（追加指令）：① C2-001 失效面扩展（invoker 实现矩阵 F3）；② 等待点超时矩阵复核；③ S/M 两实例监视器嵌套反证；④ vendored CronExpression 集成边界复核

## 1. 本轮发现

### [nop-job-C2-007] 同步 invoker 执行期间 job.running 占位尚未置位——running 判据三处失真：fireNow 可重入递归触发（叠加 catch(Exception) 无 Throwable 兜底 → StackOverflowError 使该 job 调度静默永久死亡）、awaitIdle 执行中误报空闲、update 的 running==null 守卫失效

- **严重度**：P2
- **缺陷族/维度**：C2 锁正确性——per-job 监视器重入语义 + 锁保护状态机的占位判据时序缺陷（状态迁移无 Throwable 兜底，错误逃逸后监视器内状态无恢复路径）
- **位置**：
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:313-334`（executeJob：`:321` invokeAsync 期间 running 仍为 null，`:333-334` 占位 future 在 invokeAsync **返回之后**才赋值；`:322` 仅 catch Exception）
  - `LocalJobScheduler.java:209-224`（fireNow `:216` 以 `job.running != null` 判"运行中"——同步 invoker 体执行期间恒为 null，判据失真）
  - `LocalJobScheduler.java:429-445`（awaitIdle `:436` 同一失真判据）、`:487-494`（update `:490` `if (this.running == null)` 守卫同被绕过）
  - `LocalJobScheduler.java:301-309`（timer 回调调 executeJob 无 Throwable 防护）、`:335-344`（whenComplete 回调体内 handleResult 若抛出，异常被 CompletableFuture 吞掉且 `done` 永不完成）
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/executor/BeanMethodJobInvoker.java:113-119`（invokeReflective 仅 catch Exception，Error 穿透）
- **证据片段**：

  LocalJobScheduler.java:319-334（占位置于 invokeAsync 返回后；catch 仅 Exception）：
  ```java
        CompletionStage<JobFireResult> future;
        try {
            future = job.invoker.invokeAsync(ctx);
        } catch (Exception e) {
            handleResult(job, null, e);
            return;
        }
        ...
            final CompletableFuture<?> done = new CompletableFuture<>();
            job.running = done;
  ```

  LocalJobScheduler.java:215-221（fireNow 的运行中判据）：
  ```java
        synchronized (job) {
            if (job.running != null)
                return false;
            if (job.state.internal == InternalState.COMPLETED || job.state.internal == InternalState.FAILED)
                return false;
            cancelScheduledFire(job);
            executeJob(job);
  ```

  BeanMethodJobInvoker.java:113-119（Error 穿透点）：
  ```java
    CompletionStage<Object> invokeReflective(IFunctionModel fn, Object bean, Map<String, Object> methodParams) {
        try {
            return toCompletionStage(invokeMethod(fn, bean, methodParams));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
  ```

- **问题描述**：owner doc §1.3 的 YAML 推荐模式唯一 invoker `BeanMethodJobInvoker` 在调用线程**同步**执行 bean 方法体（第 1 轮 C2-001 已证）。在该方法体运行期间，`job.running` 尚未置位（占位赋值在 `invokeAsync` 返回之后才执行）、`state.internal == RUNNING`。于是以 `running` 为判据的三处逻辑全部失真：(a) `fireNow` 不拒绝而是**重入执行**；(b) `awaitIdle` 在作业体执行全程返回 true；(c) `update()` 的"运行中不替换 spec/invoker"守卫被绕过。其中 (a) 与"仅 catch Exception"叠加产生最严重后果链。
- **触发时序（递归→静默死亡，完全具体）**：
  1. timer 线程（或 fireNow 调用线程）：`synchronized(job)` → executeJob → BeanMethodJobInvoker 同步执行 bean 方法体（此刻 `job.running == null`、`internal == RUNNING`）；
  2. bean 方法体内调用 `scheduler.fireNow(自身jobName)`（"队列排空后立即再跑一轮"类自触发写法）：`synchronized(job)` 同线程重入通过 → `running == null` 判定"未在运行" → 再次 `executeJob` → 嵌套再次执行 bean 方法体；
  3. 若 bean 方法体无条件自触发 → 无限递归 → `StackOverflowError`（Error 非 Exception）穿透 `BeanMethodJobInvoker.invokeReflective` 与 `executeJob` 的两层 catch → 穿透全部重入 synchronized 帧（最外层释放监视器）→ 抛出到 `executor.schedule` 的任务体，被调度执行器吞掉；
  4. 净终态：`internal == RUNNING`、`job.running == null`、`job.scheduledFire == null`——周期调度**静默永久停止**，无任何模块内日志；`getJobState` 永远报告 RUNNING（监控误判"长跑任务"）；仅手工再次 fireNow 可复活。
  单次（非无条件）重入的较轻形态：嵌套执行正常返回后，内/外两层 whenComplete 先后同步执行 handleResult → 同一次逻辑触发 fireCount 计 2、lastScheduledTime 被内层覆写——违反 owner doc §1.2 `:98` 明文契约"同一 jobName 任意时刻只跑一个实例"。
  独立于递归的等价触发：任何 Error（NoClassDefFoundError/OOM 等）从同步 invoker 同步段逃逸，同样使 job 卡在 `RUNNING + running==null + scheduledFire==null` 的静默死亡态。whenComplete 回调体内 handleResult 抛出（低概率）则异常被 CF 吞掉、`done` 永不完成，job 停摆且无日志。
- **影响面**：本地模式（nop-job-local）全部同步 invoker 作业。awaitIdle 误报发生在**每一次**长同步作业体的执行全程（确定性语义缺陷，非低概率竞态），直接破坏其 javadoc 声明的测试/嵌入式等待用途；递归/Error 形态使单个 job 周期调度静默死亡且监控呈假 RUNNING。不涉数据损坏、不阻塞管理面（区别于 C2-001），故不判 P1。
- **失效面枚举（F3 对照）**：失效面 = running 占位判据的全部消费点：`fireNow:216`、`awaitIdle:436`、`update:490`、`whenComplete:337`（复位点本身不受影响）；无锁读面 getJobDetail/toJobDetail 不读 running，不受影响。分布式模式（worker 链路）不使用该占位机制，不受影响。枚举完整。
- **修复建议**：① executeJob 在 `invokeAsync` 调用**之前**置占位（锁内 `job.running = done` 前移），并在 catch/Error 兜底中复位——一处改动同时修复 (a)(b)(c) 与 Error 逃逸；② executeJob 与 timer 回调改 catch Throwable（或 finally 恢复态）并对 Error 记 error 日志 + 置 FAILED；③ BeanMethodJobInvoker.invokeReflective 的 catch 升为 Throwable。三处均为行级改动。
- **误报校准核对**：非"锁原语本身"豁免——缺陷在占位赋值时序与判据消费点，非 monitor 机制；非低频路径粗锁——awaitIdle 误报是同步 invoker 主路径上的确定性行为；非有界声明结构——running 语义无任何 javadoc/owner doc 声明其"invokeAsync 返回后才置位"的时序边界。
- **置信度**：高（占位时序由 `:321` 与 `:334` 的语句顺序直接证实；递归时序每一步都有代码落点；SOE 终态由 `catch Exception`×2 与 Error 类型关系唯一确定）。
- **关联发现**：C2-001（同一同步执行事实的锁持有面；本发现是其互补面——占位时序而非锁持有范围）；C2-003/C2-004（同属分支判据失真家族）。
- **边界声明核对**：owner doc `:98` 声明"同一 jobName 任意时刻只跑一个实例"——fireNow 重入执行正是对该声明的实现违背，非已声明边界；awaitIdle javadoc 仅称"等待 job 当前没有正在执行的实例"，其实现判据使该声明在同步 invoker 下失真，亦非豁免。
- **复核状态**：已保留（P2 维持。占位时序（invokeAsync :321 → `job.running = done` :334）与 fireNow 判据（:216 `running != null`）逐行核实；同线程监视器重入 + 两层 catch(Exception)（BeanMethodJobInvoker :116、executeJob :322）放行 StackOverflowError 的链路由类型关系唯一确定；终态 `internal==RUNNING / running==null / scheduledFire==null` 由 :314 置位后无任何恢复路径实证。精确性备注：ExecutorHelper.schedule 捕 Throwable 后记**无 jobName 上下文**的通用日志 `nop.executor.execute-fail`（ExecutorHelper.java:187）再抛入 FutureTask 吞掉——"模块内无日志"表述精确，平台层有一条无 job 标识的通用日志，不改变"调度静默死亡 + 监控假 RUNNING"终态。awaitIdle :436 执行全程误报空闲为确定性缺陷，独立支撑 P2。历史审计无同族登记）

### [nop-job-C2-008] SimpleExecutionContext 持 live SimpleJobState 引用泄漏给 invoker——自定义异步 invoker 在锁外线程读 plain long 字段与持锁写方构成 JMM 数据竞争（C2-002 家族的第二读面）

- **严重度**：P3
- **缺陷族/维度**：C2 锁正确性——synchronized 保护的字段经对象引用泄漏产生锁外读点（互斥不完整的第二种泄漏形态；第 1 轮 C2-002 的 F3 仅枚举了 getJobDetail 读面）
- **位置**：
  - `nop-job/nop-job-local/src/main/java/io/nop/job/local/LocalJobScheduler.java:313-321`（ctx 以 `job.state` 构造并交予 invoker）、`:559-595`（SimpleExecutionContext 全部 getter 直读 state）、`:510-519`（SimpleJobState plain long 字段，非 volatile）
  - 写方：`LocalJobScheduler.java:348-350`（handleResult 持锁 `fireCount++`/`lastEndTime=now`）、`:299-300`（scheduleNext 持锁写 scheduledFireTime）
  - 契约面：`nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/IJobInvoker.java:23`（invokeAsync 返回 CompletionStage，实现方可持有 ctx 跨线程）
- **证据片段**：

  LocalJobScheduler.java:313-321（ctx 绑定 live state）：
  ```java
    private void executeJob(ScheduledJob job) {
        job.state.internal = InternalState.RUNNING;
        ...
        SimpleExecutionContext ctx = new SimpleExecutionContext(job.spec, job.state);

        CompletionStage<JobFireResult> future;
        try {
            future = job.invoker.invokeAsync(ctx);
  ```

  LocalJobScheduler.java:573-577（getter 直读 plain long）：
  ```java
        @Override public String getInstanceId() { return String.valueOf(state.fireCount); }
        @Override public long getExecCount() { return state.fireCount; }
        @Override public long getScheduledExecTime() { return state.lastScheduledTime; }
        @Override public long getExecBeginTime() { return state.lastScheduledTime; }
        @Override public long getExecEndTime() { return state.lastEndTime; }
  ```

- **问题描述**：ctx 持有 `job.state` 的活引用并随 invokeAsync 交给 invoker 实现。平台内置 invoker 均只在同步段（持锁内）读 ctx（RpcJobInvoker 全部 ctx 读取在异步提交前的同步段；BeanMethodJobInvoker 只取 params map，ctx 不下传 bean 方法），但 SPI 契约不禁止自定义 invoker 将 ctx 捕获进异步回调/其他线程延迟读取。此类读与 handleResult 的持锁写之间无 happens-before：可读到过期值，且 `fireCount` 等为非 volatile long，JMM 允许 64 位撕裂（JLS §17.7）。
- **触发时序**：编程式注册的自定义异步 invoker 在 `invokeAsync` 返回前把 ctx 存入自身字段/闭包；其完成回调线程随后调 `ctx.getExecCount()`/`getInstanceId()`（如日志/幂等判定）→ 与调度线程 handleResult 的 `fireCount++` 并发 → 读到中间态/撕裂值。内置 invoker 全集不触发；需自定义异步 invoker 延迟读 ctx。
- **影响面**：仅编程式模式 + 自定义异步 invoker 延迟读 ctx 的场景；后果限于 ctx 读值失真（execCount/instanceId 用于业务判定时可能一次性错乱），无调度状态损坏（调度决策全持锁）。较 C2-002 触发面更窄，故 P3。
- **失效面枚举（F3 对照）**：失效面 = SimpleExecutionContext 全部透传 state 的 getter（`:573-577` 五个 + isInstanceRunning/isJobFinished 两个 boolean 读）；spec 侧 getter（getJobName/getJobParams）绑定 `job.spec` 快照引用，addJob update 换 spec 后 ctx 仍读旧 spec——旧引用内容不可变（HashMap 仅构造期填充），无竞争。枚举完整。
- **修复建议**：executeJob 构造 ctx 时对计数字段做一次性快照（新建轻量 record：execCount=state.fireCount+1 预取、scheduledExecTime、lastEndTime），SimpleExecutionContext 改持快照——同时消除撕裂与语义歧义（当前 `getExecCount()` 返回的是**执行前**计数，getInstanceId 也基于旧值，快照化顺带固定该语义）；或在 IJobInvoker javadoc 声明"ctx 仅可在 invokeAsync 同步段内使用"。
- **误报校准核对**：非"CHM/锁原语本身"豁免；非平台设施自身实现（SimpleExecutionContext 是本模块私有类）；因内置 invoker 全部不触发、需自定义违例用法，按 C2-006 同口径定 P3。
- **置信度**：中高（数据竞争形态确凿；触发依赖自定义 invoker 延迟持 ctx，现网内置路径不可达）。
- **关联发现**：C2-002（同族第一读面：getJobDetail；本条为第二读面：ctx 泄漏，C2-002 的 F3 未覆盖）、C2-007（同处 executeJob 的占位/ctx 构造时序）。
- **边界声明核对**：无任何 javadoc 声明 ctx 的线程可见性契约或"同步段内使用"限制；不构成已声明边界。
- **复核状态**：已保留（P3 维持。ctx 持 live state（:317、:561）+ getter 直读 plain long（:573-577）属实；IJobInvoker javadoc 仅约定"异步执行的返回结果"、不禁止实现方跨线程持有 ctx（IJobInvoker.java:23）核实；4 个内置 invoker 全部仅在同步段读 ctx（RpcJobInvoker :34-62 且 thenApply 不捕获 ctx、BeanMethodJobInvoker 仅取 params map 不下传、NopE2eTestJobInvoker 忽略 ctx、RemoteJobInvoker getCancelToken 于 ：100 同步取值）——触发需自定义异步 invoker 延迟持 ctx，P3 与 C2-006 同口径恰当。与 C1 维度 C1-002 为同一泄漏点（C1 以共享可变状态逃逸面立案 P2，本条以延迟读 JMM 竞态面立案 P3，判级差异源于触发面更窄），合并裁定阶段归并。历史审计无同族登记）

## 2. 排除项/定向核查

**① C2-001 失效面扩展——synchronized(job) 内 invoker 实现矩阵（F3）**：src/main 全量 `implements IJobInvoker` 清点 = 4 个实现 + 编程式自定义。全部经 `BeanContainerInvokerResolver`（`nopJobInvoker_` 前缀 bean 查找）或 resolver lambda 可达 LocalJobScheduler：

| Invoker | 同步段（锁内执行）内容 | 阻塞上界 | 死锁环可达性 |
|---|---|---|---|
| BeanMethodJobInvoker（YAML 硬编码 `jobInvoker("beanMethod")`，LocalJobConfigLoader:243） | bean 解析 + 反射解析 + **完整 bean 方法体**（非 CS 返回值包 completedFuture 后 whenComplete 同步在锁内跑 handleResult→scheduleNext→cron 计算） | 无上界（用户方法体时长） | **可达**：E3 环 J(x)→EXT(L)，即 C2-001 原判 |
| RpcJobInvoker（`nopJobInvoker_rpc`，需 nop-job-service 在 classpath + 编程式指名） | 参数解析 + header 注入 + `rpcServiceInvoker.invokeAsync` 异步**提交**（不等待响应）；`.thenApply` 在 RPC 响应线程，whenComplete 回锁 | 毫秒级提交（pooled 实现最坏为借连接超时，平台相关） | 环窗口 = 提交时长（毫秒）；另存持续的反向边 EXT响应线程→J(x)（whenComplete 取锁），受锁持有时长约束，与 E3 组合的 ABBA 窗口毫秒级，实践不可达 |
| NopE2eTestJobInvoker（`nopJobInvoker_test`） | 返回已完成 future；whenComplete 在 invokeAsync 调用内同步完成（E2 自环重入） | ≈0 | 无外部代码，不可达 |
| RemoteJobInvoker（`nopJobInvoker_rpcPoll`，coordinator） | 首行 `jobCtx.getAttributes().get("jobTaskId")`——SimpleExecutionContext.getAttributes() 返回 **null**（LocalJobScheduler:586）→ NPE；catch 块 `:115` 再次对 null getAttributes 取值 → 第二次 NPE 逃出 invokeAsync → 被 executeJob `catch(Exception)` 捕获 → handleResult → FAILED | ≈0（理论上的 3 次同步 DB 读——loadTask/loadFire/loadSchedule——在锁内的路径因 attributes=null **先于 DB 访问即失败**，不可达） | 不可达（误装配快速失败，fail-fast 被吞为 FAILED + NPE 日志） |
| 自定义 IJobInvoker（resolver lambda / 自注册 bean） | 任意 | 无上界 | 同 BeanMethod 族（E3） |

**放大器（跨维度备注，归 C4 维度）**：本地默认 executor `nopJobScheduledExecutor` 为**单线程**调度执行器（owner doc §1.3 bean 表），同步 invoker 体在该唯一调度线程上持锁运行——C2-001 失效面除管理 API/停机外，还包含**同一 executor 上全部其他 job 的定时精度**（队头阻塞）。C2-001 的修复（invoker 移出锁）须与"单线程池上跑长任务"一并评估，登记为 C4 维度线索。

**② 等待点超时矩阵复核（第 1 轮 F5"全过"抽验）**：独立 grep（`.get()`/`.join()`/`Thread.sleep`/`Object.wait`/`.await`/`CountDownLatch`/`Semaphore`/`lockInterruptibly`/`awaitTermination`/`invokeAny`/`invokeAll`）src/main 全量：唯一等待原语 = LocalJobScheduler awaitXxx 的 3 处 `Thread.sleep`（`:421/:442/:463`），均为 `min(5ms, remaining)` 有界 sleep + 每轮 deadline 复查 + InterruptedException 传播——第 1 轮 F5 结论**确认**。补充定性：三方法的 `synchronized(job)` 获取段（monitorenter）不受 timeout 参数约束，deadline 仅在获锁后生效——该点第 1 轮已在 C2-001 描述中记载，系 C2-001（E3 边）的派生阻塞而非独立缺陷，本轮不重复立案。零 CountDownLatch/Condition/Object.wait/Semaphore/无超时 Future.get/join——与第 1 轮声明一致。

**③ S/M 两实例监视器嵌套反证（第 1 轮"互不相交"抽验）**：S = AbstractBatchScanner 实例监视器，仅 `startScanning:58-65`/`stopScanning:67-73` 持有；调用方全量 = JobCoordinator `:55-70/:78-108`（6 scanner 生命周期）与 JobWorker `:17/:24`——IoC 生命周期线程，串行逐个取不同 scanner 实例的 S，从不嵌套两把 S。M = MetadataWorkerCapacityProvider `synchronized(this):78-83`；唯一调用方 = JobWorkerScannerImpl `:151`，位于 `scanBatch()`（经 doScan→scanOnce 在 executor 线程执行，**scanOnce 不持 S**）。反证双向闭合：持 S 期间（running 写 + executor 提交 + cancel）不触及 IWorkerCapacityProvider；持 M 期间（resolveCapacity：map 读 + int 解析 + LOG.warn）不触及任何 scanner 方法。第 1 轮结论**维持**。

**④ vendored CronExpression 集成边界复核（第 1 轮"误报"判定）**：**结论维持、理由修正**。第 1 轮称"每次新建实例、实例不跨线程共享"——不准确：`TriggerBuilder.buildTrigger:33-34` 每次 **trigger 构建**（非每次计算）新建一个 CronExpression，`CronTrigger:20` 以 final 字段持有其整个生命周期；本地模式下该实例被同一 job 的全部触发复用，且被 timer 线程与 fireNow/resumeJob/addJob 管理线程**跨线程共享**。实际安全性由三重机制保证：(a) 本地路径 `job.trigger.nextScheduleTime` 的全部 4 个调用点（addJob:129、rescheduleAfterUpdate:100/119、resumeJob:170、handleResult:379）均持该 job 的 J(x) 监视器——实例访问按 job 串行化，跨 job 实例互不相同；(b) 计算期变异仅及 threadLocalCal（实例级 ThreadLocal，`:194`）的线程私有 Calendar 与局部变量，全部 BitSet 写点均在构造/解析期（`:372-373/:454/:551-579`），findNext（`:309-330`）只写 Calendar；(c) CronExpression 经 CronTrigger 的 final 字段发布，享受 final field freeze 安全发布。分布式路径（JobPlannerScannerImpl:223、JobCompletionProcessorImpl:343、JobFireStoreImpl:340、NopJobScheduleBizModel:224）经 JobTriggerCalculator 每次计算重建全新链，无共享。误报判定**维持**。附：包装 trigger 链仅 OnceTrigger 持可变 `first` 标志（OnceTrigger.java:17），其注释显式声明"LocalJobScheduler 保留 trigger 实例，first 作为实例级兜底"——同一 J(x) 串行化覆盖，不立案。

**⑤ 本轮新发现载体清点**：`DefaultWorkerLoadProvider.scanCache`（`:42`）为 ThreadLocal 包裹的 per-scan HashMap，`:38-42` 与 `:59-64`（check2 P3-6 注释）显式声明线程隔离设计，endScan `remove()` 防驻留——非锁载体、无跨线程共享，第 1 轮盘点未列此项，本轮补录后载体清单闭合（C2 维度不立案）。

**⑥ 第 1 轮"无缺陷"结论抽验**：computeIfAbsent 两处（DefaultJobCancelHandler:75、DefaultJobExecutionContextBuilder:169）目标容器经 `NopJobTask.getEffectiveParams`（NopJobTask.java:21-35）每次调用新建 LinkedHashMap，方法内单线程使用——**确认**。MetadataWorkerCapacityProvider DCL（volatile cached + 锁内复检，`:73-84`）教科书正确；`refreshForTest:89-91` 为 package-private 测试专用 volatile 置 null，与读方竞态无害；负配置抛 NopException 时锁随异常释放、cached 保持 null 走重复 fail-fast——均不立案。RpcPollTaskManager 全文零 synchronized/显式锁（全量 grep 只有 3 文件 18 处命中，与基线 C2 节逐条一致）——**确认**。

**⑦ cancel 路径锁面**：LocalJobScheduler.cancelRunning 为 no-op（`:390-393`），本地模式从不调用 `invoker.cancelAsync`——cancel 路径不产生任何 J(x) 锁边；附带功能性备注（非 C2）：`.job.yaml` 的 `cancelMethodName` 配置在本地模式为死配置，归功能维度。

## 3. 终止声明

第 2 轮立案 2 项（P2×1 = C2-007，P3×1 = C2-008）。四项指定盲区（invoker 矩阵、等待点矩阵、S/M 嵌套、CronExpression 边界）已全部核查完毕：锁载体清单经基线 + 独立 grep 双通道闭合（18 处 synchronized 全部落档，无第 3 个锁文件）；剩余不确定性集中于"用户自定义 invoker/bean 方法体行为"，属框架声明的外部边界（C2-001/C2-007 修复建议已给出契约化收口方案），语言级锁维度内无进一步可挖实体。

**未发现新的高价值问题。C2 维度深挖终止（第 2 轮为终轮）。**

## 4. 覆盖声明

- **精读文件（本轮全文）**：LocalJobScheduler、BeanMethodJobInvoker、RpcJobInvoker、NopE2eTestJobInvoker、IJobInvoker、IJobExecutionContext（cancelToken 默认方法）、BeanContainerInvokerResolver、LocalJobConfigLoader、RemoteJobInvoker、AbstractBatchScanner、MetadataWorkerCapacityProvider、DefaultWorkerLoadProvider、TriggerBuilder、CronTrigger、JobTriggerCalculator、OnceTrigger、AnchorRepeatTrigger、PauseCalendarTrigger、LimitCountTrigger、JobTimeoutCheckerImpl（头部 120 行）、JobWorkerScannerImpl（scanBatch 关键段）。
- **grep 定向核查**：synchronized 全量（18 处/3 文件，与基线一致）；等待原语全量（见 ②）；`implements IJobInvoker` 全量（4 实现 + 测试 stub）；`nextScheduleTime/getTimeAfter/calculateNextFireTime` 全部调用点（锁面归属逐一落档）；CronExpression 可变字段与 BitSet 写点；`getMyCapacity/startScanning/stopScanning` 全部调用点。
- **vendored 源码**：仅审集成边界（TriggerBuilder/CronTrigger/各 trigger 包装 + CronExpression 的构造期/计算期变异点分布），未审 vendored 解析算法正确性。
- **未覆盖（留档）**：测试代码（src/test）的并发正确性；nop-commons GlobalExecutors/DefaultScheduledExecutor/LifeCycleSupport 内部实现（平台设施，按校准规则排除）；DB 乐观锁语义（第 1 轮残留边界 #3 维持，归事务/其他维度）；单线程本地 executor 的队头阻塞（C4 维度线索，见 ① 放大器备注）。
