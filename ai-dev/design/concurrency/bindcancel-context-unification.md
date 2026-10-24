# api-core 取消通道与上下文传播统一设计

**日期**：2026-10-24
**范围**：`nop-kernel/nop-api-core`（FutureHelper/ContextTaskQueue/BaseContext/BaseContextProvider/ICancelToken）、`nop-kernel/nop-commons` executor 家族（ExecutorHelper/BindScheduledExecutor/LimitedExecutor）、nop-service-framework（nop-graphql-core/nop-biz/nop-graphql-grpc/nop-graphql-message/nop-biz-file-core）、nop-network（nop-rpc-api/nop-rpc-core/nop-http-client-jdk/nop-http-client-okhttp）、nop-ai/nop-ai-toolkit；含 nop-quarkus/nop-spring 的 WS 入口站点归属
**状态**：active
**来源**：plan 2499（R1-04 api-core 协同批）Phase 1 裁定文档——本文档是六项裁定的唯一权威来源；14 项成员发现的站点/缺陷事实源为该 plan 的 Current Baseline 逐条表

---

## 一、设计结论

1. **裁定①**：`FutureHelper.bindCancel` 的守卫从「任意完结且目标未完成即 cancel」收口为「**仅当完结原因为取消**（异常终态为 `CancellationException`）且目标未完成才 cancel」；`ExecutorHelper` 包装 Callable 在 complete 之后清除本线程残留中断标志。同批一次收口 api-core 三个半边（bindCancel 守卫 / `completeAfterTask` catch 宽度 / `executeWithThrottling` 幻影 release）与 nop-commons 三个本体半边（ExecutorHelper 双处 / throttleExecute / LimitedExecutor）。全库生产调用方共 4 处，兼容矩阵逐点声明行为不变式。
2. **裁定②**：取消传播统一以「先修原语、后接新线」为硬顺序——`BindScheduledExecutor` schedule/submit 接入 bindCancel **必须后置于裁定①落地**；R1-01 绝缘层禁用条款（绝缘返回 future 禁止 cancel 接线）由本裁定承接闭合，绝缘核心逐轮调度经 ExecutorHelper 层修复自动受益；R1-02 池设施零改动面（工厂校验/热更/容量语义）不被本批回改。
3. **裁定③**：context 传播原语统一为三件套——**出口泵统一形态 = 泵级 `ContextProvider.thenOnContext` 挂接**（与 thenFetchNext 既有先例同构，覆盖递归泵与 fetcher 层）；**入口统一 = `IGraphQLEngine.newRpcContext` 内置 binder 等价语义**（五入口共用，nop-spring 第五入口零改码）；**平台根因 = `BaseContextProvider.currentContext` 补 isClosed 守卫（惰性清除残留）+ `BaseContext.close` 维持条件 detach**，4.1-CL-024 归属裁定为「Local 红投不传递发送者 IContext，台账注记闭合、无需改码」。
4. **裁定④**：`ContextTaskQueue.syncGet` 的 drain→await 丢失唤醒窗修复选型 **`endProcessIfIdle()` 复用**（与已修 flush 面同构）；谓词放宽方案被证伪拒绝。C10-009 采纳同文件 flush 面的内部锁冻结测试手法。
5. **裁定⑤**：SPI 策略终裁为**零接口签名变更**——全部修复落在静态工具语义收口、实现类内部守卫与既有 `IContext` 方法承载上；若实施阶段被迫引入接口变更，×11 实现类全表 + grep 重枚举程序（含 mock）为强制回归面（§3.5 全表在案）。
6. **裁定⑥**：C4-008 取消链接线 = 排队分支取消联动 + `executeNow` 统一 isCancelled 短路 + CL-012 注册前补触发；`ICancelToken` 契约以「appendOnCancel 不承诺补发、注册方自检已取消窗口 + 平台单点 `FutureHelper.bindCancelToken` 内置补触发」为成文契约，零接口签名变更。

## 二、背景与动机

R1-04 批的 14 项发现聚为三条根因线：

- **取消通道根因**：`bindCancel` 守卫不判完成原因，使「包装 future 正常/异常完结」与「包装 future 被取消」不可区分——正常完成路径必然对底层任务注入假取消与中断残留（C3-016）；该原语失真同时封锁了 C3-004（Bind 载体取消不传播）的正确接入路径，并使 4.3 面只能各自手写 `bindCancelToken` 变体（C3-012 的 9 个中招站点）。
- **上下文传播根因**：`IContext` 的线程附着语义缺少成文纪律——出口侧（GraphQL 泵/fetcher 异步续行）与入口侧（gRPC/WS/消息/文件）各有一半由约定俗成补齐，HTTP 入口的 filter 形态成为唯一被完整履行的样本；平台根因半边（close 不对称 detach + currentContext 不设守卫）使残留 context 可被读到。
- **取消链根因**：`ICancelToken` 对「注册时已取消」无补发承诺，消费方各自为政（4.3 三客户端手写 append/remove、toolkit 仅 jq 一个执行器读 token），取消链在排队窗口与 executor 层两处断链（C4-008）。

与既有设计的衔接：[periodic-task-insulation.md](periodic-task-insulation.md) §3.5 对 C3-016 立有禁用条款（绝缘返回 future 禁止 cancel 接线），并显式声明「C3-016 的修复在 R1-04 于 ExecutorHelper 层收口，届时绝缘核心逐轮调度自动受益」——本批裁定②是该条款的承接闭合点；[pool-facility-hardening.md](pool-facility-hardening.md) §3.6 裁定⑥将 `ExecutorHelper.schedule/submit` 的 bindCancel 面列为零改动声明面——本批对该面的改造即裁定①本体，两批的回归测试（绝缘 9 条 + 池设施 7 条）为本批共享代码位改动的门禁底座。

## 三、核心设计

### 3.0 裁定索引：14 成员 → 裁定映射

| # | Finding ID | 判级 | 落点裁定 | 处置要点 |
|---|---|---|---|---|
| 1 | 2.3-C3-002 | P1 | ③ | 出口泵/递归泵按 §3.3 形态 A 挂接 thenOnContext |
| 2 | 2.3-C3-007 | P1 | ③ | 受管提交按 §3.3 快照+executeWithContext 形态 + proceedAsync fail-fast |
| 3 | 2.1-C2-016 | P2 | ④ | syncGet drain 段退出改 endProcessIfIdle + C10-009 锁冻结手法 |
| 4 | 2.1-C3-004 | P2 | ②+① | 接入后置于裁定①；executeOn isDone 守卫；RetryEngineImpl 列 1.6 回归面 |
| 5 | 2.1-C3-005 | P2 | ① | throttleExecute（本体）+ completeAfterTask（api-core 半边）catch(Throwable) 同批同口径 |
| 6 | 2.1-C3-007 | P2 | ① | LimitedExecutor release 移入 acquire 成功后作用域 + executeWithThrottling 同构 |
| 7 | 2.1-C3-016 | P2 | ① | 守卫按完成原因判别 + 中断残留清除（双层同提交） |
| 8 | 2.3-C3-012 | P2 | ③ | 五入口内置统一 binder；nop-spring 第五入口零改码 + 入口断言 |
| 9 | 2.3-C3-015 | P2 | ③ | fetcher 层 thenOnContext 同口径（与 C3-002 同批验收，A4） |
| 10 | 2.3-C3-017 | P2 | ③ | 读点 context 优先 + 平台根因半边（Phase 2）承接残留面 |
| 11 | 2.3-C3-008 | P2 | ③ | TCC 入口 fail-fast + 恢复逻辑对捕获 context 对象写回 |
| 12 | 4.3-C3-007 | P2 | ③ | 平台根因半边（Phase 2）+ nop-rpc-api 侧 isClosed 强制替换（Phase 3） |
| 13 | 4.3-C3-012 | P2 | ①+⑥ | 全部中招站点收敛到 `FutureHelper.bindCancelToken` 单点；ICancelToken 契约按 §3.6 |
| 14 | 3.2-C4-008 | P2 | ⑥ | 排队取消联动 + isCancelled 短路 + 注册前补触发；C10-014 矩阵验收 |

批外注记成员：4.1-CL-024 → ③（归属裁定闭合）；4.4-MA-05 → ③（口径传导）；2.1-C3-018 → ①（矩阵复核注记，工单归 R4-13）；2.1-C6-001 → ②（受益面不设障碍，工单归 R4-14）。

### 3.1 裁定① bindCancel 单点改造语义

**守卫契约（api-core 半边，`nop-kernel/nop-api-core/.../util/FutureHelper.java` :313-318）**：`bindCancel(promise, future)` 的行为语义为——

```
promise.whenComplete((value, ex) -> {
    if (ex instanceof CancellationException && !future.isDone())
        future.cancel(true);   // mayInterruptIfRunning=true 维持既有语义
});
```

三类完结的原因判别：**正常完成**（ex == null）不触发 cancel；**失败完结**（ex 非 CancellationException）不触发 cancel；**取消**（ex 为 CancellationException，CF 被取消时监听器收到的异常终态）且目标未完成才 `cancel(true)`。语义收口为 source-compat：方法签名零变更，行为变化即缺陷修复本身——修复前「成功路径必然误取消」是被审计确认的缺陷行为而非可依赖契约；误 cancel 假终态的观察方依赖（若有）按 plan Non-Blocking Follow-ups 处置。

**中断残留清除（nop-commons 本体半边，`ExecutorHelper` 包装 Callable）**：三处包装 Callable（submit×2/schedule）在 `future.complete(...)`/`completeExceptionally(...)` 之后、返回之前执行一次 `Thread.interrupted()` 清除（清除并返回，不吞异常通道）。作用：真取消路径上 worker 被注入的中断（合法动作）不再泄漏给该 worker 执行的下一个任务，消除对载体 TPE `getTask` 吸收中断的依赖。声明的权衡：TPE `shutdownNow` 的中断若恰落在「complete 之后、清除点之前」也会被清除——worker 的退出由 shutdown 后队列排空保证（`getTask` 状态复检），不依赖该中断标志，可接受；该清除仅存在于 ExecutorHelper 包装层，不进入 `FutureHelper`（后者不拥有任务体边界）。

**api-core 三半边一次收口（同一批、同一口径）**：

| 缺陷 | api-core 半边 | nop-commons 本体半边 | 修复后语义 |
|---|---|---|---|
| C3-016 误取消 | `bindCancel` 守卫按完成原因判别 | ExecutorHelper 包装 Callable complete 后清中断 | 成功/失败完结零 cancel、零残留；真取消传播保持 |
| C3-005 catch 宽度 | `completeAfterTask`（:321-332）`catch (Exception)` 改 `catch (Throwable)` | `throttleExecute`（:307-327）内外两层 `catch (Exception)` 改 `catch (Throwable)` | 任务体抛 Error 后 promise 异常终态，调用方 syncGet 可退出；与同文件 submit/schedule 的 Throwable 样本对齐 |
| C3-007 幻影 release | `executeWithThrottling`（:537-558）InterruptedException 分支不 release（该分支仅 `limit.acquire()` 可抛中断，进入分支即未获许可） | `LimitedExecutor.execute` release 移入 acquire 成功后作用域（区分「acquire 失败」与「execute 被拒」——前者不补偿、后者补偿） | 许可数不虚增；executor 拒绝路径的补偿语义保持 |

**调用方兼容矩阵（全库 grep 结论：`FutureHelper.bindCancel(` 生产调用方共 4 处，无其他站点、无静态导入别名调用）**：

| 调用方 | 站点 | 语义 | 修复后行为声明 |
|---|---|---|---|
| `ExecutorHelper.submit(ExecutorService, Callable)` | `ExecutorHelper.java:215` | 包装 future 取消 → 取消底层 FutureTask | 成功路径不再误 cancel/不再注入中断；排队中或运行中的真取消传播保持（cancel(true) 从 timer 队列/运行中任务两面生效） |
| `ExecutorHelper.submit(ExecutorService, Runnable, T)` | `:239` | 同上 | 同上 |
| `ExecutorHelper.schedule(STPE, Callable, delay, unit)` | `:264` | 同上（延时任务） | 同上；取消从 STPE 队列移除待触发任务 |
| `DefaultTaskExecutionQueue`（C3-018，参数反接面） | `DefaultTaskExecutionQueue.java:238` | `bindCancel(future, state.getPromise())`——promise/future 两参反接 | 行为零变化：`state.promise` 由更早注册的 whenComplete 先行完结，bindCancel 回调判定时 `isDone()` 恒真，守卫新旧语义下均不触发；真取消路径语义保持。参数反接本体归 R4-13（C3-018），修复时按本契约复核 |

委托继承面（`DefaultScheduledExecutor`/`DefaultThreadPoolExecutor` 的 submit/schedule）经上述三处收口自动覆盖，无独立 bindCancel 调用点。

**`bindCancelToken` 与 `bindCancel` 的关系**：`FutureHelper.bindCancelToken`（:518-535 两重载）是 **ICancelToken → future 方向**的收敛入口（token 取消 → future 取消，完成时反注册）；`bindCancel` 是 **promise → Future 方向**的原语（promise 取消 → 底层 future 取消）。二者方向相反、互不替代；4.3-C3-012 的全部中招站点以 `bindCancelToken` 为平台收敛入口（裁定⑥给出其注册窗口契约），toolkit 取消链以二者组合为对齐模板。

### 3.2 裁定② 取消传播统一与 R1-01/R1-02 产物边界衔接

**C3-004 接入后置于 C3-016 修复（实施顺序硬约束）**：`BindScheduledExecutor` schedule（:53-60）/submit（:116-122）返回 future 接入 bindCancel、以及 `executeOn`（:129-142）执行前 isDone 守卫，**必须在裁定①的守卫判别修复落地之后实施**。理由：现状 bindCancel 对任意完结触发 cancel——在失真原语上接入新线，即使个别时序窗口（timer future 先于包装 future 完结）不触发误取消，新站点也整体落入 C3-016 缺陷类（原因盲的 cancel 语义），且消费者回归要做两轮。先修原语、后接新线使接入点一次性继承正确语义。机械门禁：C3-016 双层修复提交在 git 序列上先于 C3-004 接入提交（plan Phase 2 exit criteria 可证）；两提交不合并，防止「误取消与取消不传播并存」的中间基线。

**executeOn isDone 守卫语义**：`executeOn` 在向 worker 提交前检查包装 future 的取消/完成状态，已取消的提交直接短路（不投递 worker，取消终态已由 future 承载）；该守卫是排队窗口取消的兜底，不替代 bindCancel 传播。

**R1-01 禁用条款衔接（承接闭合）**：绝缘返回 future（`InsulatedPeriodicChain`）禁止 cancel 接线的条款维持——本批不对绝缘链新增任何 bindCancel 接线；[periodic-task-insulation.md](periodic-task-insulation.md) §3.5 声明的「修复在 ExecutorHelper 层收口、绝缘核心逐轮调度自动受益」由本裁定①兑现。验证方案：fixedDelay 逐轮重排行走 `ExecutorHelper.schedule` 的既有路径（绝缘核心未扩大触发面），修复后绝缘核心的逐轮调度不再暴露误取消/中断残留；断言点 = `TestPeriodicTaskInsulation` 9 条全绿（共享代码位门禁）+ C3-016 新增的成功路径断言覆盖 schedule 载体；绝缘语义（重臂/三相容/失败治理/拒绝路径）零回改，绝缘四载体 diff 零改动自证。

**R1-02 零回改面维持**：[pool-facility-hardening.md](pool-facility-hardening.md) §3.6 的零改动表——绝缘四载体、`DefaultScheduledExecutor.periodicTasks` 登记表与 destroy 拒绝路径、池工厂校验（EXEC-001/002 锚点）、cached 熔断与热更语义——不被本批触碰；本批对 `ExecutorHelper` 的改动范围 = 三处包装 Callable 的残留清除 + `throttleExecute` catch 宽度，均为 R1-02 显式留归本批的 bindCancel/C3-005 面。回归底座 = `TestThreadPoolFacilityHardening` 7 条。

**消费方边界注记**：C6-001（销毁链族，R4-14）只声明受益面——bindCancel 修复后其 api-core 层反向传播受益不设障碍，工单不承接；`DefaultTaskExecutionQueue:238` 的复核义务随 R4-13 按 §3.1 矩阵执行；RetryEngineImpl（:404-407，经 `getExecutor()` = globalTimer.executeOn(globalWorker) 载体）为 C3-004/C3-016 的直接生产消费方，列 1.6 回归面（`./mvnw test -pl nop-retry -am` + 取消传播/不再误取消针对性断言）。

### 3.3 裁定③ context 传播原语统一

**出口泵形态终裁（C3-002）：泵级 thenOnContext 挂接（形态 A）**。`invokeOperation` 的 thenCompose 出口（`GraphQLExecutor.java:134-148`）与 `dispatchAll` 递归泵（:200 定义的 `promise.thenAccept(v -> dispatchAll(context))`）统一包 `ContextProvider.thenOnContext(future, context)` 后再续行，`context` 取 `env.getContext()`（api-core IContext，经 `IGraphQLExecutionContext.getContext()` → ServiceContext 承载）。判据三条逐一成立：

1. **先例一致性**：同文件 `thenFetchNext`（:151-158）已用 `ContextProvider.thenOnContext(future, env.getContext())` 承载嵌套续行，形态 A 是把既有先例升格为全泵纪律，不引入第二种挂接风格。
2. **递归泵覆盖面**：形态 A 在泵接缝统一覆盖出口泵、递归泵与 fetcher 层（C3-015 的 TransformFetcher/EvalActionTransformFetcher thenCompose 前同口径挂接）；batchLoader 显式携带 context 的形态（被拒）只覆盖 OrmBatchLoader 一个 loader，Transformer 用户表达式、fetcher 游走与其余 context 敏感续行均不受益。
3. **每跳开销**：`thenOnContext` 自带快路径（future 已完成且当前线程运行于该 context 上时原样返回）；泵级挂接的挂接密度是「每游走回合一跳」，不是每字段每回调一跳，开销有界。

OrmBatchLoader（`nop-graphql-orm/.../fetcher/OrmBatchLoader.java`）不改：其 `requireSession` 在泵挂接后于 context 附着线程上执行，读点语义随泵恢复；loader 自身保持无 context 感知。

**入口 binder 统一终裁（C3-012）：内置统一接入**。`IGraphQLEngine.newRpcContext`（default 方法，五入口共用的唯一 chokepoint）内置 binder 等价语义：从 `ApiRequest` 头落 tenant/locale/timezone/callExpireTime/propagateHeaders 到请求关联的 IContext，语义与 `ContextBinder`（`nop-network/nop-rpc/nop-rpc-api/.../ContextBinder.java`）同构——线程已有 open context 则复用（HTTP filter 链入口的既有附着不被二次建 context 分叉），无则按头新建并在请求终态关闭。五入口（gRPC `GraphQLServerCallHandler:102-103`、quarkus WS `JsonRpcWebSocketEndpoint:50-64`、**spring WS `nop-spring-web-starter/.../JsonRpcWebSocketEndpoint:48-70`（第五入口）**、消息 `GraphQLMessageSubscriptionRegistrar:84-96`、文件 `AbstractGraphQLFileService:42-60`）共用同一引擎入口零改码；`initRpcContext`（`GraphQLEngine.java:449-466`）既有「仅 setRequestHeaders」的缺口在统一入口内闭合。**第五入口归属结论**：内置形态下 nop-spring 站点零改码，回归面补一条 spring-WS 入口断言；不采用 `newRpcContextWithBinding` 新入口形态（被拒理由见 §四）——因此不产生「新入口形态下 spring 站点改码移交」的分支。IMessageSubscriber「消费方必须等待 CompletionStage 完成」的解包契约缺口随本裁定注记，归消费线程上下文专题的后续对账面。

**平台根因统一裁定（4.3-C3-007 / 2.3-C3-017 / 4.3-CL-011 移交承接）**：

- `BaseContextProvider.currentContext`（:22-24）补 isClosed 守卫：读到 closed context 时惰性清除 ThreadLocal 残留并返回 null——与 `getOrCreateContext`（:35-41）既有检查构成对称双读点守卫，残留 context 从「可被读到」变为「不可观测」。
- `BaseContext.close`（:315-327）维持条件 detach（holder==this 才 clear），**不引入 attach 线程登记**。理由：ThreadLocal 附着点不集中（`attachContext`/`runOnContext`/`executeWithContext` 及使用方直接 set 均为合法附着路径），context 跨线程迁移是平台语义的一部分（runOnContext/executeWithContext 本身就是迁移原语），登记「attach 线程」需要一张跨线程登记表，引入锁竞争与泄漏面，且仍无法覆盖使用方绕过 provider 的附着；守卫惰性清除以读点防御达成同一缺陷类的闭合，成本为零。close 携带 attach 线程句柄的形态同理被拒：attach 线程不是 context 的属主（异步完成线程合法地 close 别线程创建的 context）。
- **落地分工**：平台根因半边（currentContext 守卫 + close 语义钉死 + 断言）在 **Phase 2**（nop-api-core）落地；`ContextBinder` 对 isClosed 残留的强制替换（构造器读到 closed context 不复用、按 getOrCreateContext 既有检查的对称守卫）与 `ApiContextInterceptor` 同步抛保护（C3-006 面）在 **Phase 3**（nop-rpc-api）消费该半边落地；`ContextHttpServerFilter`（nop-http-api）的裸附着面在根因守卫落地后其残留不可观测，不改码，`4.3-CL-011` 平台根因移交由 Phase 2 平台根因项落账。
- 2.3-C3-017 的读点面（`buildRpcResponse` locale 读点改 context 优先、WS/订阅同步段以消息头构造的 context 执行）在 Phase 3 与 C3-012 同修。

**C3-007 受管提交形态终裁**：采用**「提交时快照 + `IContext.executeWithContext` 执行」**形态——`BizActionService`（`nop-service-framework/nop-biz/.../BizActionService.java:71-94`）的 task lambda 在提交线程捕获 `ContextProvider.currentContext()`，任务体内经捕获 context 的 `executeWithContext` 附着执行（restore 由原语的 finally 语义承载），双分支（`executeForPartition`/`execute`）同批闭合；`proceedAsync`（`BizActionInvocation`）补 currentContext()==null 的语义化 fail-fast。`ExecutorHelper.submit` 受管入口形态被拒：`:86-91` 从 beanContainer 取出的是 `Executor`（非 `ExecutorService`），`ExecutorHelper.submit` 无法直接承载，改造 bean 装配类型超本批边界且与 biz 顺序执行的 `IPartitionedExecutor` 分支不相容。该形态同时闭合 ServiceContextImpl 字段初始化（:35 `getOrCreateContext()`）在池线程永久附着新建/残留 context 的串号面：任务体执行时线程附着的是调用方 context，构造捕获语义正确。

**C3-008（TCC）**：`TccContextInvoker.invokeAsync` 入口对无线附着场景 fail-fast（语义化错误，非静默 `getOrCreateContext` 附着）；恢复逻辑改为对捕获的 IContext 对象属性写回（非完成线程 `currentContext`）；`TccContext.getOrCreateContext` 的附着泄漏面随入口 fail-fast 收敛。与 C3-007 联动验证。

**thenOnContext / IContext javadoc 契约成文**：`thenOnContext(future, context)` 保证回调在 context 附着状态下执行、单线程串行（BaseContext 经任务队列）；context 已关闭时包装 promise 异常终态（既有 `thenOnContext0` 行为钉死为契约）；无参重载绑定 `getOrCreateContext()`，返回的 promise 必须立即被消费、跨线程传递不保证绑定（既有 javadoc 声明维持并升格为治理口径：**异步续行凡读线程上下文者必须在泵接缝挂接 thenOnContext**）。

**4.1-CL-024 归属裁定（Local 红投，exit criteria 第三支柱）**：结论 = **Local 发送者→scheduler 异步边界不传递发送者 IContext，维持现状，无需改码**。理由：broker 两后端（Kafka/Pulsar）消费线程同样无 context 是消费模型的整体既定形态；Local 红投若单点传递，将形成「Local 有 context、broker 无」的后端间语义分叉——正是该发现移交专题时自述的禁止项。与裁定③入口统一口径自洽：消费侧需要的上下文按**入口显式构造**（按投递轮次从消息头构建，同 C3-012 消息入口形态），而非发送侧隐式携带。台账路径：Phase 3 回填 cra-index `4.1-CL-024` 行（归属 R1-04 + 本结论 + 无 successor 工单），注记闭合。

**4.4-MA-05 传导注记**：其家族修复归 R4-28；本裁定③的 context 原语统一口径（入口显式构造、泵接缝挂接、isClosed 守卫语义）为 R4-28 的软对齐基线，R4-28 落地时按同口径执行，不重复裁定。

### 3.4 裁定④ C2-016 syncGet 窗口修复选型

**终裁：drain 段退出复用 `endProcessIfIdle()`（与 flush 同构，审计建议①）**。`ContextTaskQueue.syncGet`（`nop-kernel/nop-api-core/.../context/ContextTaskQueue.java:46-105`）的 drain 段退出从无条件 `endProcess()`（:81）改为 flush 面已修复的「排空-复检-退出」原子形态（endProcessIfIdle 在锁内确认队列空后才清 processingThread；队列非空则返回 false 继续排空）。不变式：任何 enqueue 的任务要么被处理线程排空、要么按「enqueue 返回 false → 调用方自行 flush」契约由入队线程处理；promise 完成的唤醒在锁内 signal，等待方唤醒后外层循环重查 result 与队列。

**谓词放宽方案（`syncing>0 || processingThread!=null`）被证伪拒绝**：在失效交错（B 于 A 退出处理态之前 enqueue——放宽谓词下 signal 落空因 A 尚未在条件上注册，B 又因返回 true 而放弃自行 flush；A 随后清 processingThread、syncing++、await）中，丢唤醒依旧发生——放宽只改变信号发射时机，不建立「退出处理态」与「队列空」的原子关系，且使 enqueue 放弃 flush 兜底，严格劣于方案①。await 侧不补 tasks 空判定谓词：外层 do-while 在每次唤醒后重查 result/队列，既有结构对虚假唤醒天然容忍，补谓词是冗余状态。

**C10-009 测试手法采纳**：沿同文件 flush 面修复（commit 5ee798e02e）在 `TestContextTaskQueue` 建立的内部锁冻结手法（反射取 `ReentrantLock` + park 标记对齐），构造「drain 完成后、await 注册前插入 enqueue+flush」交错用例 + 「enqueue 返回 true 的等待方必然被后续 flush 唤醒」不变式用例；修复前红（等待方滞留）、修复后绿。**对称性声明**：本修复后，flush 与 syncGet drain 共用同一个「退出处理态」原子原语（endProcessIfIdle），ContextTaskQueue 不再存在第二种退出路径。

### 3.5 裁定⑤ SPI 策略与回归面全列

**终裁：零接口签名变更（source-compat）**。逐接口核对：

- `bindCancel`/`completeAfterTask`/`executeWithThrottling`：`FutureHelper` 静态工具语义收口，签名不动。
- context 统一：出口泵/入口绑定以 `ContextProvider` 静态入口与既有 `IContext.executeWithContext/runOnContext` 承载；平台根因是 `BaseContextProvider`/`BaseContext` 实现类内部守卫；入口绑定内置于 `IGraphQLEngine.newRpcContext` 既有 default 方法，不新增接口方法。
- 取消链：`ICancelToken` 契约以 javadoc 成文 + `bindCancelToken` 静态工具内置补触发承载（§3.6），接口签名不动。

**条件义务（若实施阶段任一裁定被迫引入接口变更）**：下表 ×11 实现类全部列入回归面并逐个登记验证方式；落盘前重跑全仓 grep 重枚举（模式：`implements IContextProvider|implements IContext\b|implements ICancellable|implements ICancelToken\b|extends Cancellable\b`，mock 对 `Cancellable` 的同型继承一并重枚举）。

**SPI 回归面 ×11 类全表（live 枚举 @ 本基线，本设计落笔前重验）**：

| # | 实现类 | 承载路径 | 登记关系 |
|---|---|---|---|
| 1 | `Cancellable`（基类，implements ICancellable） | `nop-kernel/nop-commons/src/main/java/io/nop/commons/lang/impl/Cancellable.java` | ICancelToken/ICancellable 基类，#2–#8 经基类继承传递 |
| 2 | `ExecutionContextImpl`（extends Cancellable） | `nop-kernel/nop-core/src/main/java/io/nop/core/context/ExecutionContextImpl.java` | 传递成员 |
| 3 | `ServiceContextImpl`（extends ExecutionContextImpl，:21） | `nop-kernel/nop-core/src/main/java/io/nop/core/context/ServiceContextImpl.java` | 基类传递登记 |
| 4 | `TaskRuntimeImpl`（extends Cancellable） | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java` | 传递成员 |
| 5 | `BlockingSourceConsumeTask`（extends Cancellable） | `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/batch/BlockingSourceConsumeTask.java` | 传递成员 |
| 6 | `DefaultTaskExecutionQueue.State`（extends Cancellable，:38 内部类） | `nop-kernel/nop-core/src/main/java/io/nop/core/execution/DefaultTaskExecutionQueue.java` | 传递成员 |
| 7 | `RpcMessageSubscriptions.Subscription`（extends Cancellable，:25） | `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/RpcMessageSubscriptions.java` | 传递成员 |
| 8 | `PulsarMessageService.PulsarMessageSubscription`（extends Cancellable，:583） | `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java` | 传递成员 |
| 9 | `FailoverStreamFlow`（implements ICancelToken） | `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java` | 直接实现 |
| 10 | `DebeziumMessageSource.Subscription`（implements ICancellable，:207） | `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java` | 直接实现 |
| 11 | `ReplayableCdcSourceFunction.ReplaySubscription`（implements ICancellable，:133） | `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/scenario/ReplayableCdcSourceFunction.java` | 直接实现 |

配套接口面：`IContextProvider` 生产实现 ×1（`BaseContextProvider`）；`IContext` 生产实现 ×2（`BaseContext`、`DelegateContext`，均 nop-api-core）。防误列：`IExecutionContext`/`IGraphQLExecutionContext` 为独立接口树不实现 IContext；`CancelTokenManager`（nop-graphql-core）`implements ICancelTokenManger` 仅为 token 持有方，非实现类，按消费方登记。测试 mock 重枚举基线（live grep `/test/` 命中 `implements ICancelToken` 7 文件：JqToolExecutorTest、TestChatServiceFailoverAdapterStreaming、TestBodyPublisherResourceFix、TestCancellableRpcClient、TestPollingRpcClientFix、TestRemoteJobInvoker、TestDefaultRpcPollTaskClient）；无测试 mock 实现 IContext/IContextProvider。零签名变更成立时，SPI 义务以 BaseContextProvider/BaseContext/DelegateContext 现有行为回归 + nop-api-core 全量测试门禁承载。

### 3.6 裁定⑥ C4-008 取消链接线与 ICancelToken 平台契约

**取消链接线（3.2-C4-008，CL-012/CL-017 同 diff 随单）**：`ToolManagerImpl`（`nop-ai/nop-ai-toolkit/.../manager/ToolManagerImpl.java:100-121`）排队分支的取消联动形态——(1) 排队 result 挂取消联动：result 被取消 → 触发链剩余段短路（执行段入口判 result 取消状态即不再派生执行），消除「result.cancel(true) 假确认」；(2) `executeNow.get()` 统一 isCancelled 短路位：所有排队/非排队路径的执行入口统一判取消短路，排队窗口的 cancelToken 盲区随执行入口守卫闭合；(3) CL-012 注册窄窗：`JqToolExecutor` 形态（:48 已含 removeOnCancel）为对齐模板，注册前 `isCancelled()` 补触发一次（已取消 token 立即取消 future），随同 diff 落地；(4) CL-017（fs 族不读 token）为对账面声明：fs 族执行器的取消能力按 C10-014 矩阵逐个登记（可中断/检查点/不可取消三档），不做隐式承诺。

**ICancelToken 契约（零签名变更）**：`ICancelToken`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ICancelToken.java`）的成文契约钉死为——`appendOnCancel` **不承诺对已取消 token 补发回调**；注册方必须自检「注册时已取消」窗口（或经平台收敛入口获得补触发）。平台收敛入口 `FutureHelper.bindCancelToken`（:518-535）内置注册前补触发：注册前 `isCancelled()` 为真则立即以 `getCancelReason()` 取消目标 future——全部消费方（4.3 客户端族、toolkit）经该单点获得一致的注册窗口语义。监听器清理契约：注册与 remove 配对（promise 终态反注册），4.3-C3-012 的 jdk×4/okhttp×3/MessageRpcClient/CancellableRpcClient 全部中招站点改经 `bindCancelToken` 收敛，`PollingRpcClient` 手动 remove 形态为执行时回读的对照样本。

**C10-014 验收边界**：toolkit 全部 IToolExecutor 实现的取消矩阵断言 + wrapper 传播断言（经 ToolManagerImpl 返回 future 的取消可观测地到达执行层或被矩阵显式登记为不可达）。计数口径注记：审计口径 17 executor，live grep `implements IToolExecutor` = 20 工具执行器 + 1 provider；矩阵以 live 重枚举为准并登记口径差，不预设数量。当前唯一 cancelToken 消费者为 JqToolExecutor，其余执行器按矩阵逐个登记处置档位。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| bindCancel 守卫维持 `!future.isDone()` 并在调用方侧各自规避 | 缺陷类原语化：每个调用方重演一次误取消判别，C3-004 接入即成新站点；单点收口后 4 调用方一次受益 |
| `newRpcContextWithBinding` 新入口形态（五入口显式换用） | 留下可误用的旧入口，纪律靠约定不靠结构；且 nop-spring 第五入口将产生批外改码（超出 batch-proposal 模块列需显式移交）；内置形态零改码覆盖五入口 |
| BaseContext.close 引入 attach 线程登记表 / close 携带 attach 线程句柄 | 附着点不集中、跨线程迁移是平台语义；登记表引入锁竞争与泄漏面；读点守卫（currentContext isClosed 惰性清除）以零成本达成同缺陷类闭合 |
| C3-007 改造 executor bean 为 ExecutorService 以复用 ExecutorHelper.submit 受管入口 | 装配类型变更跨模块超本批边界，且与 `IPartitionedExecutor` 顺序分支不相容；快照+executeWithContext 用既有原语承载，装配零改动 |
| syncGet 谓词放宽 `syncing>0 \|\| processingThread!=null` | 被交错证伪（§3.4）：信号可落在等待者注册前而丢失，且 enqueue 返回 true 使入队方放弃 flush 兜底，丢唤醒窗不闭合 |
| OrmBatchLoader 显式携带 context 作为 C3-002 修复形态 | 只覆盖单个 loader；transformer/fetcher/递归泵的 context 敏感续行不受益；与 thenFetchNext 先例分裂为两套挂接风格 |
| 4.1-CL-024 在 Local 单点传递发送者 IContext | 与 Kafka/Pulsar 消费线程既定形态分叉（发现自述禁止项）；消费上下文按裁定③口径由入口按投递轮次显式构造 |
| ICancelToken 增加「注册即补发」接口语义（如 appendOnCancel 返回已取消状态或新增 tryAppend） | 接口签名变更触发 ×11 全表回归且语义改动外溢全部实现类；平台收敛入口（bindCancelToken）内置补触发可在零签名变更下达到一致语义 |
| 对 `scheduleWithRandomDelay`（C3-017）顺带改写 | 批外项，行为变化须随其工单裁定（沿 R1-01 同一边界先例） |

## 五、与已有设计的关系

- **上游**：[periodic-task-insulation.md](periodic-task-insulation.md)（R1-01）§3.5 禁用条款由裁定②承接闭合；绝缘链生命周期/重臂/三相容语义零回改，其「ExecutorHelper 层收口后绝缘核心自动受益」的预期由本设计兑现并给出断言点。[pool-facility-hardening.md](pool-facility-hardening.md)（R1-02）§3.6 零改动表是本批共享代码位改动的边界依据；EXEC-001/002 锚点语义不触碰。
- **下游**：R2-02/R2-16 消费本批的 bindCancel/ICancelToken 原语（软依赖）；R4-13 按 §3.1 矩阵复核 C3-018；R4-14 的 C6-001 受益面不设障碍；R4-28 按裁定③口径软对齐（4.4-MA-05）；R2-05/R4-16 对账 ContextBinder 死代码面与入口统一的后续收敛。
- **批次边界**：本设计只裁决 plan 2499 的六项裁定；站点级行号事实与逐成员回归义务以 plan 2499 Current Baseline 与 Execution Plan 为准（行号为基线回读值，执行时以 live 为准）。owner doc（source-anchors api-core 锚点、service-layer 上下文传播契约）归 plan Phase 4 统一落账，内容源以本文档为准。
- **钉死面**：`TestPeriodicTaskInsulation` 9 条 + `TestThreadPoolFacilityHardening` 7 条为共享代码位回归门禁；`TestContextTaskQueue` 的锁冻结手法为 C10-009 的先例载体；`FutureHelper.bindCancelToken` 为 4.3 面取消监听器的唯一平台收敛入口。
