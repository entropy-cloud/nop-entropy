# C2 锁与同步原语 — nop-wf + nop-task 第 2 轮深挖（盲审）

审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），379 files / 36,107 lines。
审计日期：2026-10-15。分支：concurrency-audit-remediation。仅审计 live code，`_` 前缀生成文件不作发现对象。
输入：第 1 轮 `C2-locks-sync.md`（C2-001~010）+ 基线 `_tmp/cra-baseline-nop-wf-nop-task.txt` C2 节。
本轮新增发现：**C2-011（P2）、C2-012（P3）、C2-013（P3）**，编号自 C2-011 起；不修改前轮文件。

---

## 1. 本轮发现

### [nop-wf-nop-task-C2-011]
1. **发现ID**：nop-wf-nop-task-C2-011
2. **严重度**：P2（C2-005 同族失效面扩展——两条**零显式共享 key** 即可达的嵌套获取路径，第 1 轮"默认 key 不触发"的结论对这两条路径不成立）
3. **维度/类别**：C2 锁正确性——非重入信号量嵌套获取的 F3 失效面扩展（深挖指令第 2 条指定项）
4. **位置**：路径 A = `nop-task/nop-task-core/src/main/java/io/nop/task/step/CallTaskStep.java:97`（子任务同线程内联执行）× `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:199`（global 注册表 key=`taskName:key`，递归同任务名即同 key）；路径 B = `nop-task/nop-task-core/src/main/java/io/nop/task/step/CallStepTaskStep.java:83`（lib 步骤复用调用方同一 stepRt）× `nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:58`（默认 key=`stepRt.getStepPath()`）× `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepLibBuilder.java:19-23`（lib 步骤走同一 wrap 管线、可自带 throttle）
5. **标题**：throttle 非重入信号量的嵌套获取无需显式共享 keyExpr——自递归 call-task 的 global 闸门按 `taskName:key` 天然同 key；call-step 的 lib 步骤与调用方共享同一 stepPath，默认 key 即碰撞
6. **问题描述**：第 1 轮 C2-005 将触发面限定为"递归 call-task **显式共享 keyExpr**"，影响面声明"默认 key=stepPath 时父子步骤天然不同 key，不触发"。本轮逐帧核查发现两条默认配置即碰撞的嵌套路径：**路径 A**——global 信号量注册表 key 为 `taskRt.getTaskName() + ":" + key`（TaskFlowManagerImpl:199），stepPath 是**任务内相对路径**：任务 T 的顶层步骤 S（path=`S`）持 global permit 后经 call-task 递归调用自身，子任务实例的同名步骤 S 的 path 同样为 `S`，注册表 key 同为 `T:S` → 命中同一 `DefaultSemaphore`；**路径 B**——`CallStepTaskStep.execute` 以调用方的**同一 stepRt** 执行 lib 步骤（CallStepTaskStep:83），`getKey` 无 keyExpr 时返回 `stepRt.getStepPath()`（ThrottleTaskStepWrapper:58）→ 调用方步骤与被调 lib 步骤的默认 key 完全相同，且非 global 信号量本就以 taskRt attribute（`semaphore:<key>`，TaskFlowManagerImpl:204-206）为共享单元 → 同一任务实例内的两层 throttle 命中同一信号量。两条路径中 `CallTaskStep`/`CallStepTaskStep` 的子执行均为**同线程/promise 链内联**（CallTaskStep:97 `return task.execute(subRt, outputNames)`），外层 permit 横跨子执行全程，内层 `tryAcquire(1, maxWait)` 在嵌套深度超过 maxConcurrency 时必然超时。
7. **证据片段**（CallTaskStep.java:97 与 TaskFlowManagerImpl.java:197-206；CallStepTaskStep.java:78-83）：
```java
// CallTaskStep.execute —— 子任务在当前 promise 链内联执行，外层 throttle 的 permit 全程被持有
return task.execute(subRt, outputNames);
...
// TaskFlowManagerImpl.getSemaphore —— global 注册表按 taskName:key；子任务同名步骤 path 相同 → 同 key
if (global)
    return globalSemaphores.computeIfAbsent(taskRt.getTaskName() + ":" + key, k -> ...);
return (ISemaphore) taskRt.computeAttributeIfAbsent("semaphore:" + key, k -> ...);
...
// CallStepTaskStep.execute —— lib 步骤复用调用方 stepRt → getKey 默认值 = 同一 stepPath
ITaskRuntime taskRt = stepRt.getTaskRuntime();
...
return step.execute(stepRt);
```
8. **触发时序**：路径 A——任务 T 步骤 S 配 `<throttle global="true" maxConcurrency="1" maxWait="10000"/>`（无 keyExpr），S 为 call-task 递归调用 T 自身（树遍历类任务的主流写法）。第 1 层 S 持 `T:S` 的 1/1 permit → 内联执行子任务 → 子任务同名步骤 S `tryAcquire` 阻塞 10s → 抛 `ERR_TASK_THROTTLE_TIMEOUT` → 错误沿 promise 链上抛、外层 whenComplete 释放。**每次递归深度 ≥2 的执行确定性地失败**，无竞态、无低概率成分；maxWait 配大（如小时级）时驱动线程同步阻塞 maxWait。路径 B——调用方步骤与被调 lib 步骤各配 throttle（复用场景下调用方加闸门、lib 步骤自带闸门是自然组合），同上确定性失败。global 变体使同名任务全部实例共享该故障面（owner doc :33 载明刻意跨租户共享）。
9. **影响面**：所有「global throttle + 自递归 call-task」与「throttled call-step + 自带 throttle 的 lib 步骤」组合；对照 owner doc nop-task.md:9-11/166-167，call-task、call-step、throttle 均为一等特性，组合位于常规使用面内而非第 1 轮假定的"非常规显式共享 key"配置。
10. **声明边界对照**：owner doc :33 只声明共享单元与容量告警契约，未声明嵌套获取语义、无「同 key 不得嵌套」的构建期/执行期检测（与 C2-005 第 10 项同一缺口）；本轮追加：第 1 轮影响面声明（C2-005 第 9 项）中"默认 key 天然不同"的判断对 global×递归与 call-step 两路径不成立，属前轮结论的事实性修正。
11. **F 族对照**：F3 死锁/自旋族（C2-005 的失效面矩阵补全——信号量 acquire 点全量 grep 证实模块内仅 ThrottleTaskStepWrapper:39 一个闸门点，本发现补齐其全部默认可达嵌套路径）；F5 相关（maxWait 大时为长阻塞窗口）。
12. **修复建议**：与 C2-005 合并收敛——(a) 执行期重入检测：throttle wrapper 进入时在 stepRt/taskRt attribute 记录当前执行链已持 key 集，重入同 key 即报 `ERR_TASK_STEP_CONFIG_INVALID` 族硬失败（对称 plan 364 [维度02-06] 已确立的构建期硬失败风格）；(b) 文档在 owner doc :33 补「global 闸门按 taskName:key 共享、同任务递归/call-step 内默认 key 即同 key、同 key 不得嵌套」；(c) 可选：global 闸门 key 加 taskInstanceId 作用域选项以支持递归自限流语义。
13. **置信度**：高（同 key 判定与内联执行均为代码事实，触发为确定性而非竞态；未升 P1 因后果为有界超时失败/长阻塞而非永久挂死——永久挂死仍需第 1 轮 C2-005 的大 maxWait 前提）。
14. **复核状态**： 已保留（P2——路径 A 逐帧核实成立：CallTaskStep:97 子任务同 promise 链内联、外层 permit 全程持有 × global 注册表 key=`taskName:key`（TaskFlowManagerImpl:199）；路径 B 核实成立：CallStepTaskStep:83 复用调用方同一 stepRt × 默认 key=`stepRt.getStepPath()`（ThrottleTaskStepWrapper:58）× lib 步骤走同一 wrap 管线（TaskStepLibBuilder:18 → TaskStepBuilder:409 buildDecoratedStep → Enhancer.wrap 含 :137-142 throttle），非 global 经 taskRt attribute `semaphore:<key>`（:204-206）同实例碰撞。细部修正：main step 相对路径为 `@main`（DaoTaskStateStore.newMainStepState），初审「path=S」为简化表述，递归实例相对路径空间重启 → 同 key 的结论不变）；现状：新发现

### [nop-wf-nop-task-C2-012]
1. **发现ID**：nop-wf-nop-task-C2-012
2. **严重度**：P3（锁性能——合法配置下 global 闸门 permit 被单实例重试链长期占用，吞吐坍缩；无死锁）
3. **维度/类别**：C2 锁性能——同步原语持有时长被包装器栈序放大（throttle 外于 retry/timeout）
4. **位置**：`nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:115`（Retry 先包、位于最内）、:134（Timeout）、:137-141（Throttle 后包、位于 Retry/Timeout 之外）、:160（Sync 最外）；持有时长链 `nop-task/nop-task-core/src/main/java/io/nop/task/step/ThrottleTaskStepWrapper.java:46-48` × `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:268-280`（retry 退避经 `schedule(action, delay)` 异步等待）
5. **标题**：wrap 栈序使 throttle 的 permit 横跨整个 retry 链（含全部退避延迟与 timeout 预算）被持有，global 闸门的实际并发额度被单个重试中实例蚕食
6. **问题描述**：`TaskStepEnhancer.wrap` 的栈序为 Sync(最外) → Validator → RateLimit → **Throttle** → Timeout → Executor → **Retry**(内) → body。throttle 位于 retry 之外，其 `whenComplete(release)`（ThrottleTaskStepWrapper:46-48）挂在完整返回链上：permit 从首次获取起被持续持有，横跨 body 的全部重试尝试、每轮 `retryPolicy.getRetryDelay` 的调度退避（TaskStepHelper:268-280 经 `getScheduledExecutor().schedule` 异步等待，驱动线程已返回但 promise 未完成）以及 timeout 预算（「timeout控制整个retry过程的时长」，Enhancer:129-130 注释自证 Timeout 包住 Retry）。指数退避 + 多次重试的合法配置下，单个实例可持有 global 闸门 permit 达分钟级；`maxConcurrency` 小时，同名任务全部实例（global 按 `taskName:key` 跨租户共享）的其余并发额度被单实例重试过程实质占用，表现为与业务无关的全局吞吐骤降。
7. **证据片段**（TaskStepEnhancer.java:137-141 与 TaskStepHelper.java:268-276）：
```java
if (stepModel.getThrottle() != null) {                    // :137 throttle 包在 retry/timeout 之外
    TaskThrottleModel throttleModel = stepModel.getThrottle();
    step = new ThrottleTaskStepWrapper(step, throttleModel.isGlobal(), ...
// ThrottleTaskStepWrapper:46 —— release 挂在完整链（含 retry 退避）之后
return getTaskStep().execute(stepRt).whenComplete((ret, err) -> { semaphore.release(1); });
// TaskStepHelper.retry —— 退避经 schedule 异步等待，期间 promise 未完成、permit 未释放
return TaskStepReturn.of(null, FutureHelper.thenCompleteAsync(
        stepRt.getTaskRuntime().getScheduledExecutor()
                .schedule(action, delay, TimeUnit.MILLISECONDS), ...
```
8. **触发时序**：非竞态登记项，无交错时序；负载时序：步骤配 `<throttle global="true" maxConcurrency="2"/>` + `<retry maxRetryCount="5" 指数退避至 60s/>`（未配 timeout）时，一个持续失败的实例持有 1/2 permit 达 Σ(delay_i) ≈ 数分钟，期间该任务名全部实例的可用并发降为 1；多实例同时进入重试时全局额度归零，新执行在 `tryAcquire` 上排队至 `ERR_TASK_THROTTLE_TIMEOUT`。
9. **影响面**：配置了 throttle + retry 组合的步骤；global 变体放大到同名任务全部租户实例。
10. **声明边界对照**：owner doc :11 将 throttle 与 retry 并列为正交的每步增强，未声明组合语义（permit 是否跨越重试）；无文档钉死栈序契约。
11. **F 族对照**：C2-4 锁性能（permit 持有时长放大，C2-006「锁内 I/O 拉长持锁时间」的同族非锁形态）；与 C2-011 叠加时（嵌套同 key）退避期的 permit 占用进一步压缩内层获取空间。
12. **修复建议**：文档钉死「throttle 的 permit 覆盖整个 retry 链」的既定语义（若为有意设计：单实例重试不重复过闸，有合理性）；若需收敛，提供栈序配置或把 throttle 包进 retry（每尝试过闸）作为可选语义，并在 owner doc 标注默认栈序的吞吐后果。
13. **置信度**：高（栈序与持有时长为代码事实；后果为吞吐退化而非正确性破坏，故 P3）。
14. **复核状态**： 已保留（P3——wrap 栈序确证（Retry :115 最内 ← Executor :118-120 ← Timeout :133-135 ← Throttle :137-142 ← RateLimit/Validator ← Sync :159-160 最外）+ retry 退避经 `getScheduledExecutor().schedule` 异步等待期间 promise 未完成（TaskStepHelper:268-284），permit 横跨全重试链成立；「timeout控制整个retry过程的时长」注释（Enhancer:127）确证；吞吐退化非正确性，P3 恰当）；现状：新发现

### [nop-wf-nop-task-C2-013]
1. **发现ID**：nop-wf-nop-task-C2-013
2. **严重度**：P3（闸门参数静默固化/非法值无守卫——确定性功能失效与静默限流失效，配额类配置校验缺口）
3. **维度/类别**：C2 锁正确性——同步原语参数（permit 数/等待上限）的首配置固化与非法值校验缺失（同口径缺口的不对称面）
4. **位置**：① `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:185-188`（rate limiter 首配置固化**有**告警）对照 :197-207（semaphore `maxPermits` 首配置固化**无**任何告警，global 与 taskRt attribute 两路径同）；② `nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:131-137`（timeout<0 硬失败）、:146-153（rateLimit requestPerSecond<=0 硬失败）对照 :137-141（throttle 的 maxConcurrency/maxWait **无**正值校验）；③ `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:115`（`maxConcurrency="!int" maxWait="!int"` 仅约束必填、不约束正值）
5. **标题**：throttle 闸门的双重校验缺口：maxConcurrency≤0 直达 DefaultSemaphore(0) 运行期必然超时失败；同 key 不同 maxConcurrency 首配置静默固化（rate limiter 同形态已修，semaphore 被遗漏）
6. **问题描述**：plan 364 [维度02-06] 已为 timeout 负值与 rateLimit 的 requestPerSecond≤0 建立「构建期同口径硬失败」惯例（Enhancer:131-137、:146-153 的守卫注释自证），throttle 的 `maxConcurrency`/`maxWait` 未纳入：xdef `!int` 只强制出现不强制正值，`maxConcurrency="0"`/负值解析通过 enhancer 原样构造 `DefaultSemaphore(0)`——`tryAcquire(1, maxWait)` 对 0-permit 信号量恒失败，步骤**每次执行**在等待 maxWait 后抛 `ERR_TASK_THROTTLE_TIMEOUT`，配置错误以运行期超时错误的形态出现而非构建期硬失败。同族第二缺口：`getSemaphore` 对同 key 二次配置不同 `maxPermits` 时缓存命中即返回（首配置固化），既不改参数也**无告警**——对照同一文件 rate limiter 的同形态缺陷已修（:183-188 「首配置固化告警」注释自证 plan 349 Phase 5 修复），semaphore 被遗漏；后果是同 key 两步骤配置不同 maxConcurrency 时，闸门实际额度由先执行者决定，配置的并发上界可能被静默突破（先注册大值）或静默收紧（先注册小值，放大 C2-011/C2-005 的超时面）。
7. **证据片段**（TaskFlowManagerImpl.java:183-207；TaskStepEnhancer.java:137-141）：
```java
// rate limiter：同 key 二次配置不同速率 → 有固化告警（plan 349 Phase 5）
if (Math.abs(limiter.getPermitsPerSecond() - requestPerSecond) > 1e-9) {
    LOG.warn("nop.task.global-rate-limiter-config-frozen:cacheKey={}...");
}
...
// semaphore：同形固化 → 无任何告警，maxPermits 静默失效
if (global)
    return globalSemaphores.computeIfAbsent(taskRt.getTaskName() + ":" + key,
            k -> { warnIfOverCap(...); return new DefaultSemaphore(maxPermits); });
// enhancer：throttle 无 maxConcurrency<=0 硬失败（对照 :146-153 rateLimit 同口径守卫）
if (stepModel.getThrottle() != null) {
    step = new ThrottleTaskStepWrapper(step, throttleModel.isGlobal(),
            throttleModel.getMaxConcurrency(), throttleModel.getMaxWait(), ...);
```
8. **触发时序**：非竞态配置缺陷，无交错时序。场景一：`maxConcurrency="0"` → 每次执行等待 maxWait 后确定性抛 `ERR_TASK_THROTTLE_TIMEOUT`。场景二：任务内步骤 A（同 key K，maxConcurrency=5）先执行注册 `DefaultSemaphore(5)`，步骤 B（同 key K，maxConcurrency=1）后执行命中缓存 → B 的并发上界 1 从未生效，闸门放行 5 并发；反向顺序则 B 类步骤全部超额排队。
9. **影响面**：使用 throttle 的全部任务模型；场景二与 C2-011/C2-005 叠加时改变其触发阈值（实际 permit 数 ≠ 任一处声明值）。
10. **声明边界对照**：无声明。Enhancer 内两处相邻守卫注释（timeout/rateLimit）自证「同口径硬失败」为已确立约定，throttle 是该约定的遗漏面；rateLimiter 固化告警注释自证 semaphore 同形态应修未修。
11. **F 族对照**：F3「修实例不修类别」的又一对照点（timeout/rateLimit 已修、throttle/semaphore 同族未修）；后果与 C2-005/C2-011 的失效阈值直接耦合。
12. **修复建议**：(a) Enhancer 对 `maxConcurrency <= 0`（及 `maxWait < 0`）按 :146-153 同口径构建期硬失败；(b) `getSemaphore` 在 global 命中路径复用 :185-188 形态：`Math.abs(semaphore unavailable...)` 改为记录注册时 maxPermits 并在二次配置不一致时 `LOG.warn("nop.task.global-semaphore-config-frozen:...")`（非 global attribute 路径同理）；(c) xdef 可加 `mustBeNumber`/正则约束作为第二道防线。
13. **置信度**：高（不对称与缺守卫均为代码事实；0/负 maxConcurrency 的确定性失败后果由 DefaultSemaphore 语义推得——该类为平台设施不审实现，但 `tryAcquire` 对 0 permit 恒败是信号量定义性语义）。
14. **复核状态**： 已保留（P3——双重校验缺口确证：Enhancer timeout<0（:130-131）与 rateLimit requestPerSecond<=0（:147-149）有硬失败 vs throttle 块（:137-142）无任何正值校验；TaskFlowManagerImpl rate limiter 固化告警（:183-188）vs semaphore global/attribute 两路径（:197-207）均无告警；task.xdef:115 `maxConcurrency="!int" maxWait="!int"` 仅必填约束；owner doc :158-159 可靠性取值约束清单自证同口径惯例存在而 throttle 遗漏。semaphore 半边为 plan 349 Phase 5 修复的对称遗漏面）；现状：新发现

---

## 2. 排除项/定向核查

**定向核查（深挖指令 4 项的执行记录）**：

1. **ISemaphore acquire 点全量**（C2-005 失效面扩展）：模块内信号量获取点**仅** `ThrottleTaskStepWrapper:39` 一处（grep `ISemaphore|tryAcquire|.acquire(` 全量核对，其余命中为接口管道/导入）；`RateLimitTaskStepWrapper:39` 的 `tryAcquire` 为**令牌桶限流器**（随时间补充），嵌套同 key 仅摊薄吞吐、不可能永久死锁——排除。global 注册表之外的获取面：非 global 为 taskRt attribute 共享（TaskFlowManagerImpl:204-206），已被 C2-011 路径 B 覆盖。
2. **C2-006 持锁 I/O 兄弟站点矩阵补齐**：`DaoTaskStateStore` 全文通读——`rowLock` 全文唯一获取点即 `saveStepState:357`（第 1 轮已登记）；`loadTaskState:146-156`/`loadMainStepState:290-297`/`loadStepState:318-329` 均**无锁**（读路径，`afterLoad` hook 在锁外）；`beforeSave` 在锁内（step :365 / task 经 L1 调用方）与 `afterLoad` 在锁外的不对称即第 1 轮锁序图边 3，无新站点。`filterSecretProps`（:970-981）非空集时的二次 `JsonTool.parse + serialize` 放大发生在 L1/L2 内，属第 1 轮「多段 JsonTool.serialize」登记包络内的代价项，不另立案。`TaskStateBean.newRunId:91`（synchronized）为纯自增，无 I/O。`TaskFlowModel:38/49/73` 三个 synchronized 惰性构建方法：监视器内为一次性模型编译（buildTask/buildTaskStepLib/buildBeanContainer），属「低频路径粗粒度锁」误报校准豁免；配对的 `setTask`/`setTaskStepLib` setter 无 synchronized，但全仓 grep 证实**零生产调用方**（仅构建管线赋值），无跨线程窗口——排除。
3. **等待点超时矩阵独立复核**（C2-010 之外独立 grep `syncGet|.sync()|.join()|waitUntil|Thread.sleep|FutureHelper.`）：新浮出面逐一定性——`StepResultBean.buildFrom:36`（syncGet 无超时）的两个调用点 `ParallelTaskStep:95-96`、`AbstractForkTaskStep:125-126` **均有 `isFutureDone` 前置守卫**，syncGet 只在已完成 future 上取值，有界——排除；`TaskStepReturn.syncGetOutputs/syncGetResult:173-184`（无守卫 syncGet）主代码**零调用方**，TaskStepReturn 未暴露给 XPL 可调用面，防御性 API 与 C2-010 同根等待原语、无新触发路径——并入 C2-010 族不另立案；`SleepTaskStep:38` `waitUntil(isCancelled, sleep)` 有界（sleep 配置时长）+ 轮询取消 + 出口 `checkNotCancelled`——sleep 步骤的声明语义，排除；`WorkflowServiceImpl.transferActorsAsync:230` 的 `syncGet(workflowExecutor.execute(...))`——`DefaultWorkflowExecutor.execute:32-48` 中 `task.apply(wf)` 全同步执行、`toCompletionStage(ret)` 对非 CompletionStage 返回值立即完成，transfer 场景 task 返回 null，syncGet 恒不阻塞——排除；`WfTaskScanner:96`（原第 1 轮 :79 同点位，行号因 P1-3.1-002 修复偏移）维持第 1 轮「同族旁证不单独立案」裁定。
4. **LockSupport/Condition/CountDownLatch/Object.wait/notify 盘点**：主代码（排除 test/target/_gen）**全部零命中**——模块内无此类等待原语，基线 C2 节无遗漏。`Thread.sleep` 仅 `WorkflowEngineImpl:893`（第 1 轮已排除：有界 + 中断正确）。volatile 全量：仅 `TaskStepRuntimeImpl:43/:47`（cancelToken/stepState，plan 364 [维度05-06]），C2-002 指出的 `TaskRuntimeImpl.taskState/metrics` 缺口维持不变。原子类：`rateLimiterCapWarned/semaphoreCapWarned`（CAS 一次性告警，正确）、`GraphTaskStep` 的 `waitingCount/runningCount`（见第 5 条）。
5. **GraphTaskStep AtomicInteger drain 补核**（第 1 轮锁载体清单未列的同步原语）：`buildWaitFuture:135-174` 为 decrement-then-recheck 形态——`AtomicInteger` 线性化保证末次递减者重读 `get() <= 0` 必然观测归零，`ret.complete` 幂等，无丢失唤醒；waitSuccess/waitError/waitComplete 三循环对同一 future 的重复注册各自递减、计数初值按注册数计（:137），交叉重复成员自洽。`execute:226-240` 首进入派发的 increment/decrement 包夹与 `completeGraphIfDrained:363-368` 收敛判据，其「级联先于减计数」调序不变式已由 plan 364 [维度05-07] 在 :333-336/:352-361 注释中钉死，四条驱动路径（错误消费 :288-291、错误转交 :320-328、跳过 :263-271、成功 :332-348）均已对齐——**干净，无发现**。孤儿/自等待节点的病态配置经 drain 兜底转为 `ERR_TASK_GRAPH_NO_ACTIVE_STEP`（活性保持，仅诊断信息泛化），低于立案阈值，记录备查。

**核实后排除的疑似项（防误报记录）**：

6. `TaskRuntimeImpl.newChildRuntime:167-176` 父子取消传播：`appendOnCancel`/`removeOnCancel` 的线程安全由平台 `Cancellable` 承担（误报校准：平台设施自身实现）；使用模式上监听器增删均发生在任务自身驱动链，父监听器表无跨链并发写窗口。
7. `TaskRuntimeImpl.cancel` 与 `svcCtx.cancel` 的表观互调（:85-88 与 onCancel=this::cancel :58）：重入终止依赖平台 Cancellable 幂等守卫（同第 6 条豁免面），本单元内无可达无限递归构造。
8. `TaskRuntimeImpl.enabledFlags:50`（getTagSet/setTagSet 非 volatile）：任务启动前一次性写入（TaskImpl:120-124），执行期只读，经线程池提交的安全发布到达 executor 分支——排除。
9. `ParallelTaskStep`/`AbstractForkTaskStep` 聚合回调中的 `StepResultBean.buildFrom`：见第 3 条（isFutureDone 守卫）；第 1 轮对 `promises` 的 HB 排除论证经本轮复核维持。
10. `GlobalSemaphoreStats/getGlobalRateLimiterStats/resetGlobalStats`（TaskFlowManagerImpl:217-244）：CHM 弱一致只读遍历 + stats 快照，第 1 轮已排除，维持。

---

## 3. 终止声明

本轮为 C2 维度第 2 轮深挖，产出 **P2 × 1（C2-011）、P3 × 2（C2-012、C2-013）**，无新增 P1。

**判定：C2 维度深挖终止。** 依据：(a) 信号量/闸门族已闭环——acquire 点全量唯一（ThrottleTaskStepWrapper:39）、嵌套路径矩阵补全（C2-011 的 call-task 递归 + call-step 同 stepRt 两条默认可达路径 + C2-005 的显式共享 key 路径）、参数固化与非法值守卫缺口登记（C2-013）、持有时长放大登记（C2-012），该族再无未审形态；(b) 锁载体独立重盘点与基线双向核对无新增（新增核对的 GraphTaskStep AtomicInteger drain 经逐路径核查干净）；(c) 等待点超时矩阵独立重推导，无新的生产可达无限期等待点（C2-010 族边界确认完整）；(d) LockSupport/Condition/CountDownLatch/Object.wait 零命中，原语空间穷尽。遗留开放面均为已声明边界或平台设施豁免面（第 1 轮残留边界 1-5 维持有效，本轮第 2 节补记第 6-10 项排除记录）。

---

## 4. 覆盖声明

- **深挖指令 4 项全部执行**：① C2-005 失效面扩展（→ C2-011，含前轮「默认 key 不触发」结论的事实性修正）；② C2-006 同族兄弟站点矩阵（→ 第 2 节第 2 条，无新站点，缺口登记完毕）；③ 等待点超时矩阵独立 grep 复核（→ 第 2 节第 3 条，5 个新浮出面全部定性排除或并入 C2-010 族）；④ LockSupport/Condition/CountDownLatch 盘点（→ 第 2 节第 4 条，零命中）。
- **本轮新增核对面**：TaskStepEnhancer.wrap 全栈序（retry/executor/timeout/throttle/rateLimit/sync 相对位置）、TaskStepLibBuilder 管线、CallTaskStep/CallStepTaskStep 执行模型、GraphTaskStep 全部 AtomicInteger 驱动路径（buildWaitFuture/execute/runStep/completeGraphIfDrained）、DaoTaskStateStore 全文（rowLock 唯一性、load 路径无锁性、hook 对称性）、TaskFlowModel/TaskStateBean 剩余 synchronized 方法、SleepTaskStep、DefaultWorkflowExecutor、WorkflowServiceImpl/WfTaskScanner 的 wf 侧 syncGet 站点、task.xdef throttle 定义。
- **盲审纪律**：未读取 ai-dev/audits（除本文件与指令指定的第 1 轮输入文件）、ai-dev/plans、ai-dev/bugs、ai-dev/lessons 下任何其他文件；未读取审计输出目录中其他维度文件。全部结论来自 live code + owner doc（docs-for-ai/03-modules/nop-task.md :9-11/:33/:166-167 定向引用）+ 基线 grep。
- **严重度分布（两轮累计）**：P1 × 1（C2-001），P2 × 6（C2-002~006、C2-011），P3 × 6（C2-007~010、C2-012、C2-013）。
