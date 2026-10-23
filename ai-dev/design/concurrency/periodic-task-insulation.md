# 周期任务异常绝缘层设计（Periodic Task Insulation）

> Status: active
> Last Reviewed: 2026-10-24
> Source: plan 2496（CRA Phase 8.2 R1-01 周期任务异常绝缘层批）Phase 1；发现事实源 = C3-round2 §1 周期任务调用点绝缘状态表（`ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/C3-round2.md`）

## 一、设计结论

1. **选型裁定：重臂（re-arm），非 kill+alarm。** 周期任务体抛出的任何 `Throwable`（含 `Error`）在设施边界被捕获、观测、计数后，调度链强制续排；链只在两种情况下终止：调用方对返回 future 显式 `cancel`，或设施级提交拒绝（timer 销毁 / 线程池关闭 / 队列满 Abort）。禁止因任务体失败杀链——kill+alarm 会在告警无消费方时复刻「一次 Throwable 即静默永久停摆」这一原始缺陷。
2. **三相容裁定：返回 future 是「失败/取消/拒绝」信号，不是「链存活」指标。** 首次任务体失败使返回 future `completeExceptionally`（失败对外可见、且只结算一次）；链存活的判定条件是 `future.isCancelled()`，**不是** `future.isDone()`。`isDone()==true`（因失败）与链继续运行相容；这一裁定从结构上拆除了 BindScheduledExecutor 现状「future 异常完结 → isDone 守卫退出 → 杀链」的耦合。
3. **失败治理裁定：连续失败计数 + 日志抑制 + 指数退避 + 周期性升级，四件套随绝缘语义一次定稿。** 形态参照 `JdbcConfigSource` 的 `errorCount<10` 分级抑制；退避仅作用于 fixedDelay 手工重臂路径（fixedRate 保持 JDK 速率契约不退避）。失败永不触发杀链，升级（escalation）只是观测级别的提升，不是 kill。
4. **API 形态裁定：`IScheduledExecutor` 既有签名零改动（source-compat 硬约束）；绝缘能力以两个新增 default 方法（追加 `taskName` + `IPeriodicTaskListener` 参数）与新接口 `IPeriodicTaskListener` 提供；既有 4 个周期方法（Default/Bind 两实现 × fixedRate/fixedDelay）全部改路由到同一绝缘核心**——这是 30 点表「设施级覆盖」行的覆盖来源，站点无需改码即获得链存活兜底。
5. **MA-06 看门狗前置裁定：默认无执行超时；worker 侧宽松看门狗只做 ERROR 日志 + in-flight 年龄 gauge 观测，永不终结 future**；checker 链（coordinator 侧 worker-liveness SUSPICIOUS→TIMEOUT）保持唯一终结者。绝缘设施本身不提供执行超时，与本裁定一致。

## 二、背景与动机

全平台不存在任何「续链」周期设施。三条周期路径（DefaultScheduledExecutor 裸 JDK 语义 / BindScheduledExecutor 手工重排 / 调用方自续链）在任务体抛出 Throwable 时全部终止调度链，且默认不可感知（裸 JDK 路径异常吞入 ScheduledFuture 零日志）。globalTimer 是 core=4 的共享 daemon 单例（`ThreadPoolConfig` 默认 `corePoolSize=4`），承载全平台约 26 条周期任务、无队列隔离——任何「天真重臂」（失败后按原周期立即续排 + 每次失败全量 ERROR 日志）都会把一次静默停摆换成失败风暴 + 日志洪泛，挤压同池其余周期任务的调度延迟。

因此设施级修复必须同时裁定：捕获边界、失败向调用方的传播通道、链存活机制、失败风暴治理（计数/退避/抑制/升级）、拒绝路径、以及与既有取消机制（C3-016 bindCancel 面、plan 340 checker 契约）的边界。本文档是这些裁定的唯一权威来源；实现锚定于 `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`。

## 三、核心设计

### 3.1 统一绝缘语义（裁定①）

**捕获边界 = 周期任务体整体。** 设施在任务体执行位置包一层守卫（`InsulatedPeriodicTask`），`catch Throwable` 后执行观测与计数，再决定续排。守卫的 catch 分支必须同时具备观测与续排两个动作，禁止退化为吞异常。

任务体失败后的处理管线（伪代码，表达行为契约而非实现）：

```text
runBody():
  try
    command.run()
    onSuccess(): 连续失败计数清零；若清零前计数>0 记恢复日志 + onRunSuccess
    return SUCCESS
  catch Throwable e:            # 含 Error；取消类中断见 3.2
    if chain.isCancelled():      # 取消引发的在飞中断不算失败
      return STOPPED
    streak = ++consecutiveFailures
    listener.onRunFailure(e, streak)          # 每次失败必达（显式接入方要细粒度事件）
    if shouldLog(streak):        # streak==1 或 streak % 10 == 0
      LOG.error("nop.err.periodic-task.fail", taskName, streak, e)
    if streak % 10 == 0:
      listener.onFailureStreakExceeded(streak) # 周期性升级：观测升级，不杀链
      metric nop.periodic.task.escalations{task=...} +1
    metric nop.periodic.task.failures{task=...} +1
    return FAILED
```

**重臂 vs kill+alarm 的选型理由：**

- **拒绝 kill+alarm**：(a) 平台当前不存在统一的告警消费通道（审计核心事实：周期设施返回 future 无消费通道、无告警挂接点），kill+alarm 对不消费告警的链等价于静默永久停摆，复刻原始缺陷；(b) 14 项成员发现中的扫描链、心跳链、配置刷新链的期望行为都是穿越瞬时 Error 突刺后自愈，链存活是需求而不是风险；(c) 「停止一条链」的所有权属于链的调用方，设施通过返回 future 的 `cancel` 把这个决策权交给调用方，而不是替调用方决定。
- **采纳重臂**：链存活至显式 cancel 或设施拒绝；反复失败通过 3.1 失败治理参数变得可观测、有节奏、不失控。

**拒绝路径语义（拒绝 = 设施级提交失败，与任务体失败严格区分）：**

- 分类依据是**捕获位置**，不是异常类型：只有设施自身的提交调用（`timer.schedule(...)`、`runner.execute(...)`、`executor.scheduleAtFixedRate(...)`）抛出的 Throwable 才归类为拒绝；任务体内部抛出的异常（含任务体自己调用 `execute` 产生的 REE）一律走失败管线。
- 任何拒绝路径：`LOG.error("nop.err.periodic-task.chain-rejected", taskName, e)` + `listener.onChainRejected(e)` + 返回 future `completeExceptionally(e)`；**链终止且不复活**。不复活是对「关闭竞态」的裁定：timer 已销毁/池已关闭时继续重臂等于对死亡设施无限重试（REE 风暴 + 日志洪泛）。
- 想在 worker 池拒绝时自愈的调用方（如 cluster C4-004 的断链监督）在模块层挂 `whenComplete` 监听拒绝终态后自行带退避重排——设施保证终态可见，重排策略归调用方。

**失败治理参数（常量语义契约，实现锚 `InsulatedPeriodicTask`）：**

| 参数 | 默认值 | 语义 |
|------|--------|------|
| 日志抑制区间 `LOG_SUPPRESS_INTERVAL` | 10 | 第 1 次连续失败记 ERROR（全栈），此后每第 10 次连续失败记一条 ERROR 汇总（带累计计数），中间抑制。形态同 `JdbcConfigSource` 的 `errorCount<10` 分级抑制 |
| 升级阈值 `STREAK_ESCALATION_THRESHOLD` | 10 | 连续失败每达到 10 的整数倍触发 `onFailureStreakExceeded` + escalations metric——周期性升级（持续失败持续告警），升级不改写链存活 |
| 退避乘子 `BACKOFF_MULTIPLIER` | 2 | 连续失败时下一轮重臂延迟按 2 的幂放大 |
| 失败重臂下限 `MIN_FAILURE_REARM_MILLIS` | 1000 | 一旦进入失败状态，重臂延迟下限 1s——防止 0/极小周期的失败任务在共享 timer 上形成紧失败循环 |
| 退避上限 `MAX_BACKOFF_MILLIS` | 60000 | 重臂延迟封顶 60s |

- **退避只作用于 fixedDelay 手工重臂路径**（Default/Bind 的 `scheduleWithFixedDelay` 与新增 default 方法）。`scheduleAtFixedRate` 保持 JDK 原生周期语义：失败不改变触发节奏——调用方显式选择了速率契约，设施不静默改写；fixedRate 路径的失败风暴由日志抑制兜底（失败执行体本身很快，4 线程占用有界）。
- 失败计数在任务体成功一轮后清零并记恢复日志（`nop.info.periodic-task.recover`，带失败轮数）。

**观测钩子（裁定③）：**

| 通道 | 契约 |
|------|------|
| 日志 | `nop.err.periodic-task.fail`（失败，按抑制区间）、`nop.info.periodic-task.recover`（恢复）、`nop.err.periodic-task.chain-rejected`（拒绝终态）——均带 `taskName` 上下文 |
| Listener | `IPeriodicTaskListener` 四回调：`onRunSuccess` / `onRunFailure(e, streak)`（每次必达，不受日志抑制影响）/ `onFailureStreakExceeded(streak)` / `onChainRejected(e)`；全部 default 空实现，消费方按需覆写（CL-014「带业务标识失败上报」的挂接点：listener 实例按任务注册，业务标识由 `taskName` 携带） |
| Metric | counter `nop.periodic.task.failures{task=<taskName>}`、`nop.periodic.task.escalations{task=<taskName>}`；经 `GlobalMeterRegistry` 注册，registry 未初始化时静默跳过 metric（日志与 listener 不受影响） |

### 3.2 返回 future 终态 × isDone 守卫 × 重臂的三相容（裁定②）

| 事件 | 返回 future 终态 | 链状态 |
|------|-----------------|--------|
| 任务体 Throwable | `completeExceptionally(首次失败)`，只结算一次 | **继续重臂**（fixedDelay 按退避节奏） |
| 调用方 `cancel(mayInterrupt)` | cancelled（`CancellationException`）；对已因失败完结的 future，cancel 返回 false（CompletableFuture 既有语义）但**停止动作仍然生效** | 终止：取消 pending 触发器；在飞轮次自然跑完，下一轮入口守卫退出 |
| 设施提交拒绝 | `completeExceptionally(拒绝原因)` | 终止，不复活 |

- **链存活判定一律用链的终止标记（cancelled ∨ 拒绝 ∨ stopRequested），禁止用 `isDone()` 判活。** `isDone()` 在首次失败后即为 true，若继续像 Bind 现状那样以 `isDone()` 作轮次入口守卫，失败→future 终结→下一轮退出就是结构性杀链。三相容的关键就是解耦「future 终结」与「链死亡」：失败使 future 终结（可观测），守卫只认链终止标记（链死亡仅由调用方 cancel 或设施拒绝触发）；由于失败已占用 future 的终态，调用方对失败链的 cancel 以内部停止标记落地并始终生效。
- 在飞轮次语义：cancel 不中断正在执行的任务体（fixedRate JDK 路径 `cancel(true)` 经原生 handle 传递中断除外）；在飞轮次跑完后，入口守卫见 cancelled 即退出——「在飞轮次自然终止」。
- fixedRate 路径返回的 future 是绝缘包装 future：cancel 委托给 JDK 原生周期 handle（含中断语义），失败/拒绝在包装 future 上结算；JDK 原生 future 本身因守卫吞掉 Throwable 而永不异常完结，其「异常完结即杀链」的原生行为被结构性绕开。

### 3.3 30 点表载体覆盖矩阵（裁定③）

30 点绝缘状态表的载体判定列（C3-round2 §1）不被本设计改写；下表登记处置后状态的覆盖归属，供 Phase 2 逐行落账（「设施级覆盖」= 绝缘核心改造后该行自动获得链存活兜底，站点无需改码；「显式接入」= 站点不在平台设施覆盖范围，需逐点修复；「豁免/批外」= 有明确不覆盖理由）。

| 覆盖归属 | 30 点表行 | 说明 |
|----------|-----------|------|
| 设施级覆盖（13 行） | #1–6、#8–14 | globalTimer / IScheduledExecutor 直挂（#1–6、#8、#9）与 BindScheduledExecutor 载体（#10–14）。站点任务体内自备的 `catch(Exception)` 与设施兜底正交：Exception 被站点挡住，Error 由设施兜住。注意设施覆盖只闭合「链存活 + 失败可观测」两面；各成员工单的模块侧义务（如 #12 try-外段状态真实化、#10/#11 scanner 存活指标）仍归各 Phase 修复，不因设施覆盖而豁免 |
| 显式接入（14 行） | #7（Netty EventExecutor 载体）、#15–22（nop-stream 8 处 raw 载体）、#23–27（nop-ai raw/注入载体，MA-07 六站点）、#28（FeishuClient raw） | 非平台设施载体，平台绝缘核心不覆盖；逐点修复归 Phase 2/3/4 对应工单，修复形态须与本设计的捕获边界、失败管线、拒绝路径语义对齐（catch Throwable + 观测 + 重臂 + 拒绝终态可见） |
| 豁免/批外（2 行） | #29（MockScheduledExecutor，测试设施）、#30（`scheduleWithRandomDelay`，C3-017 批外项） | #29 非生产失效面；#30 的杀链修复独立归 cra-index `2.1-C3-017` 裁定批次，本设计不改其行为（见 §五 边界声明） |

### 3.4 config 变更回调在共享 timer 线程同步执行（裁定④，C3-round2 §1 F3 义务闭合）

裁定：**设施绝缘边界 = 周期任务体整体，不提供回调级隔离。** `AbstractFileConfigSource` / `JdbcConfigSource` 的变更回调（`task.run()`）同步运行在共享 timer 线程上是既有架构权衡（共享定时器无队列隔离），本设计不重构该拓扑；per-callback 隔离（单坏回调不拖累同轮其余回调）由 C1-021（Phase 3）在 `JdbcConfigSource` 站点内自行实现，因为只有站点自己知道回调边界。设施侧的兜底承诺：即使某个回调把 Throwable 逃逸到任务体边界，守卫按 3.1 管线处理，刷新链存活且失败可见。慢回调拖长共享 timer 尾延迟的面维持登记状态，不由本设计处置。

### 3.5 API 形态与 C3-016 交互边界（裁定⑤）

**source-compat 硬约束：`IScheduledExecutor` 既有方法签名零改动**；全部新增面为：

- 新接口 `IPeriodicTaskListener`（3.3 表内四回调，全 default）。
- `IScheduledExecutor` 新增两个 default 方法（既有四参方法后追加 `String taskName, IPeriodicTaskListener listener`）：
  - `scheduleAtFixedRate(command, initialDelay, period, unit, taskName, listener)`
  - `scheduleWithFixedDelay(command, initialDelay, delay, unit, taskName, listener)`
- 既有四参周期方法的 javadoc 升级为成文契约：任务体 Throwable 被设施绝缘（捕获+观测+重臂）、返回 future 终态语义按 3.2 表、拒绝路径按 3.1。

**实现职责划分**：`DefaultScheduledExecutor` 与 `BindScheduledExecutor` 重写新增 default 方法（它们的能力由绝缘核心直接提供，避免双层守卫）；其他实现（含测试 mock）继承 default 即获得基础绝缘（守卫包装 + 手工重臂），若其周期方法已自带绝缘应重写以避免双层计数。fixedDelay 的绝缘核心（`PeriodicTaskHelper`）只消费 `IScheduledExecutor.schedule` 一次性调度做逐轮重排；Default 传 `(this, this)`（任务体运行在 timer 线程），Bind 传 `(timer, executor)`（任务体 hop 到 worker 池）。

**与 C3-016（ExecutorHelper bindCancel 误取消面，归 R1-04）的边界：**

- 绝缘返回 future（`InsulatedPeriodicChain`）上**禁止**任何「任意完结 → 自动取消 native handle」的接线（`FutureHelper.bindCancel(chain, nativeHandle)` 形态被禁用）。native handle 的取消只有一条路径：调用方显式 `chain.cancel(...)`（override 内委托取消 native handle）。任务体内 `completeExceptionally`（失败结算）不触发任何取消动作——这是「任务体内 complete 形态」的禁用条件：**任务体内允许的 complete 仅限失败结算，且不得与任何 cancel 接线共存**。
- fixedDelay 逐轮一次性调度经由实现方的 `schedule`（Default 实现为 `ExecutorHelper.schedule`，内含 bindCancel）——该 face 是 Bind 现状逐轮重排已经行走的既有路径，绝缘核心**未新增 bindCancel 接线、未扩大触发面**；C3-016 的修复在 R1-04 于 `ExecutorHelper` 层收口，届时绝缘核心逐轮调度自动受益。
- C3-016 的兄弟面顺带声明：C3-003（Bind fixedRate 的 executor 拒绝杀链）在设施改造中其守卫位置被顺带覆盖（worker 提交拒绝从「裸逃逸杀 JDK 链」变为「守卫捕获 + 拒绝终态可见 + 链终止」），属共享代码位边界效应，已在本文档显式声明，不默认承接其工单收敛（工单仍归 cra-index 裁定批次）。

### 3.6 MA-06 看门狗语义前置裁定（裁定⑥）

- **超时来源**：新增配置 `nop.job.worker.invoke-timeout-ms`，默认 `0` = 无执行超时；`schedule.timeoutSeconds > 0` 时仍优先（沿用 checker 既有取值顺序）。
- **默认形态**：默认无执行超时；配置了阈值时，worker 侧看门狗为**宽松观测**——in-flight invoke 年龄超过阈值记 ERROR 日志（带 task/年龄）+ in-flight 年龄 gauge，**永不 complete/cancel invoke future、永不杀派发链**。与 plan 340 契约测试 `testLongRunningTaskWithoutTimeoutNotKilled` 及 `JobTimeoutCheckerImpl:497-501` 注释钉死契约（无超时配置不得回退到 dispatchTimeout、长任务由 worker-liveness 链恢复）完全一致，禁止在 worker 侧复活「长任务误杀」。
- **与 checker 链双向不冲突**：coordinator checker（SUSPICIOUS→TIMEOUT，依赖 worker liveness）是任务终结的唯一仲裁者；worker 看门狗是纯观测者（只写日志与 gauge，不写任务状态、不发取消），因此不存在双重终结、不存在看门狗复活链与 checker 钉死面的对抗。绝缘设施（本文档）自身不提供执行超时，周期链的时长边界与 MA-06 裁定同构。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|----------|
| kill+alarm 作为主语义 | 告警消费通道缺失时等价于静默永久停摆；把「停链」决策权从调用方手中拿走。链停止只由显式 cancel 或设施拒绝触发 |
| 继续用 `future.isDone()` 作轮次守卫 | 与「失败结算 future 终态」结构性冲突——失败→isDone→杀链正是 Bind 现状缺陷形态（C3-015 主证）。守卫判据唯一合法形态是 `isCancelled()` |
| 任务体失败不落到返回 future（只走 listener/日志） | 剥夺无 listener 调用方的失败可见通道，违背「completeExceptionally 传播」的终态要求；且 whenComplete 监听拒绝终态的模块级监督（cluster C4-004 方向）失去挂接点 |
| 失败后无退避、无抑制的天真重臂 | 在共享 core=4 timer 上把静默停摆换成失败风暴 + 日志洪泛（§二 洪泛约束） |
| fixedRate 路径引入退避 | 速率契约是调用方显式选择；设施静默改写触发节奏比失败风暴更难诊断。fixedRate 风暴由日志抑制兜底 |
| 在 `scheduleWithRandomDelay` 上同步落绝缘 | C3-017 批外项，行为变化须随其工单裁定，避免本批越界承接（见 §五） |
| per-callback 设施级隔离（F3 面） | 设施无法感知回调边界；站点内隔离（C1-021）才是正确归属 |
| 对 Default fixedRate 保留「同步抛 REE」的旧拒绝语义 | 与绝缘契约「拒绝→future 有终态、不悬挂」冲突；拒绝改为 ERROR 日志 + 终态 future，可观测性严格更强 |

## 五、与已有设计的关系

- **批次边界**：本设计只服务 plan 2496（R1-01）的设施主体与 30 点表处置；C3-017（`scheduleWithRandomDelay` 杀链）、C3-016（bindCancel 误取消）、C3-003（工单收敛归属）、C3-013（context 卫生）不在本设计改写范围，交互边界见 3.5。
- **上游设施**：绝缘核心复用 `ExecutorHelper` 的 catch(Throwable)+completeExceptionally 绝缘形态先例与 `GlobalMeterRegistry` 观测通道；不新建平行调度器、不造第三份周期实现（Default/Bind 扩展既有设施）。
- **下游契约**：nop-job MA-06 worker 看门狗（Phase 3）按 3.6 实施；cluster C4-004 断链监督（Phase 4）消费拒绝终态语义（whenComplete + 模块级带退避重排）；nop-retry C8-001、nop-ai MA-07 六站点、nop-message C3-009 等显式接入站点以 3.1 管线为对齐模板。
- **钉死面**：`JobTimeoutCheckerImpl:497-501` 声明注释与 plan 340 契约测试为钉死契约，本设计与其一致（3.6）；30 点表载体判定列维持 C3-round2 §1 原文，处置状态列的权威锚点在 Phase 5 落 `docs-for-ai/04-reference/source-anchors.md`。
