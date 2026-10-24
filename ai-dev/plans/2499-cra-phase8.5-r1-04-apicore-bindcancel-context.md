# 2499 CRA Phase 8.5 — R1-04 api-core 协同批：bindCancel 单点改造 + 上下文传播（R14+R15，14 项）

> Plan Status: active
> Draft Review: R1 agent_99133e06（0 Blocker、3 Major[M-1 SPI 回归面假阴性 4 项补全+grep 模式修正/M-2 C3-012 第五入口 nop-spring 补列/M-3 平台根因半边 Phase 归属统一]+Phase 3 子分片纪律+7 Minor——修订 agent_c2dd2e28 全量应用[live 验证后落笔]，active）
> Design Review: 设计文档 `bindcancel-context-unification.md` 独立评审 agent_c11e7756（M-1 兼容矩阵第 4 行「零变化」证伪+4 Minor → 修订 agent_397107f3 落盘 → 增量复核**通过进入实现**；修订者与评审结论已登记于设计文档头）
> Last Reviewed: 2026-10-24
> Plan Review R1: agent_99133e06 @ 2026-10-24——3 Major（M-1 SPI 回归面补 4 漏项+措辞勘正、M-2 C3-012 第五入口 nop-spring、M-3 平台根因半边 Phase 归属统一）+ Phase 3 子分片续作纪律 + 7 Minor 修订已全部入文（live 事实经本仓库重验后落笔）
> Source: roadmap 工作项 8.5（R1-04，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L147，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-04 行（L65：finding ID 清单 14 项 / PA 授权 / 依赖标注 / exit criteria 增补）+ §四.3 授权落批表（nop-api-core plan-first → R1-04）；家族底表 = family-clustering §三 R14 取消通道族行（L58）/ R15 上下文传播专题行（L59）+ §四 2.1-api-core 移交包行（L171，消费方 1.6/4.3/3.4 列回归面）；成员发现详情 = 各单元审计目录（见 Current Baseline 逐条回读路径）；执行基线 @ `6fd473937f`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.4 已 done，plan 2495/2496/2497/2498 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2496（R1-01 绝缘层批，completed——`ai-dev/design/concurrency/periodic-task-insulation.md` §3.5 裁定⑤对 C3-016 立有 bindCancel 禁用条款：「绝缘返回 future 禁用 bindCancel 接线；C3-016 的修复在 R1-04 于 ExecutorHelper 层收口，届时绝缘核心逐轮调度自动受益」——本批裁定②为该条款的承接闭合点，`TestPeriodicTaskInsulation` 9 条为共享代码位回归门禁）；plan 2497（R1-02 池设施批，completed——`ai-dev/design/concurrency/pool-facility-hardening.md` 裁定⑥显式声明 ExecutorHelper.schedule/submit bindCancel 面零改动、C3-016 归本批）；plan 2498（R1-03 池消费方批，completed——已触达 nop-rpc-core PollingRpcClient 与 nop-graphql-core subscription/ws 三文件，与本批 14 成员站点零重叠，但 C3-012 对照正确样本 PollingRpcClient 需执行时回读复核）；plan 2495（R1-00，completed）；plan 2493/2494（批次划分与 Phase 8 展开）；**下游依赖本批**：R2-02（软依赖：bindCancel/AttemptCancelToken 原语先行；R14 族 P1 成员 3.1-MA-08/MA-05/4.3-MA-02 彼批落地）、R2-16（依赖 R1-04 取消契约原语：MA-05/MA-08 取消契约补齐）、R2-21（软依赖：4.4-C6-007 平台根因绝缘层注记对接）、R4-16（软依赖 R1-04）、R4-28（4.4-MA-05 家族修复对齐本批裁定③口径）、R4-13/R4-14（2.1-C3-018 按 bindCancel 新契约复核 / 2.1-C6-001 反向传播终态化受益第三条）
> Protected Area 授权：本批触碰 **nop-api-core（io.nop.api.core.* 全平台公共 API）= plan-first Protected Area**。授权来源 = AGENTS.md「Protected Areas」表 nop-api-core 行（plan-first，Required Evidence = owner doc + 回归测试，**SPI 变更须列全部实现为回归面**）+ batch-proposal §四.3 授权落批表（「`nop-api-core`（io.nop.api.core.*）plan-first；SPI 变更列全部实现为回归面 | 2.1 api-core 移交包 | R1-04」行）+ 本 plan Dependencies 声明：**设计文档为本批 Phase 1 产物（`ai-dev/design/`），回归测试为各 Phase 强制义务（rule 25），SPI 实现回归面全列义务见 Current Baseline「SPI 回归面清单」**。plan-first 前置在 Phase 1 内闭环：设计文档落地并经独立 draft review 后，Phase 2–5 方可推进。**连带声明**：本批成员 2.1-C3-004/C3-005/C3-007/C3-016 的双层本体半边位于 `nop-kernel/nop-commons`（`io.nop.commons.concurrent.executor`）——框架核心引擎 plan-first 同样适用（同 R1-01/R1-02 授权路径，AGENTS.md 框架核心引擎行 + batch-proposal §四.3 对应行），由同一份 Phase 1 设计文档一并覆盖，不重复立项。本批不触碰 `model/*.orm.xml`、不触碰 nop-auth（ask-first 不适用）。

## Purpose

关闭 R1-04 api-core 协同批的 14 项已确认发现（P1×2 族主 + P2×12：G26 api-core 移交包 5 + G37 上下文传播 3 + G39 搭车 1 + G55 拆分 1 + G68 搭车 2），按三条族线收口：①**FutureHelper.bindCancel 单点改造**（守卫按完成原因判别）与取消传播语义统一——同时服务 2.1 三条（C3-016 守卫判别、C3-004 传播语义，C6-001[R4-14] 反向传播终态化受益），消费方 1.6/4.3/3.4 回归面全列执行；②**executeWithContext/thenOnContext 上下文传播原语统一（出口泵补齐）**——2.3 出口泵/递归泵/fetcher 层挂接、入口 binder 统一、裸提交受管化、平台根因（BaseContext.close 对称 detach / currentContext isClosed 守卫）收口、syncGet 丢失唤醒窗修复；③**取消链与上下文残留消费面修复**——4.3 binder 串号止血与 cancelToken 监听器统一 bindCancelToken、3.2 工具取消链断链接线、4.1-CL-024（Local 红投）归属本族注记闭合。批末 roadmap 8.5 → done，14 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-04 = 14 项成员（P1×2 + P2×12）；cra-index P1 台账 2 行（`nop-service-framework-hotpath-C3-002`/`-C3-007`，裁定批次=R1-04，open）+ P2 裁决台账 5 组行涉及（G26 五成员、G37 三成员、G39 一成员、G55 拆分成员 C4-008、G68 两成员，均回填「批次=R1-04」）；roadmap 8.5 行 Status=todo。
- 前置：无前置（可并行）。R1-00（plan 2495）/R1-01（plan 2496）/R1-02（plan 2497）/R1-03（plan 2498）均 completed @ `6fd473937f`——本批启动的基线门禁已满足。
- **R1-01/R1-02 已改造共享代码位**：`BindScheduledExecutor` 周期链已重写为绝缘链（`InsulatedPeriodicChain`/`InsulatedPeriodicTask`/`PeriodicTaskHelper`），`ExecutorHelper` 池工厂/热更面已按 pool-facility-hardening.md 改造——两批均显式声明 bindCancel 面（C3-016）零改动归本批（见 Baseline 勘误 2/3 与「R1-01/R1-02 产物边界」节）。

### SPI 回归面清单（live 枚举 @ `6fd473937f`；PA 义务：SPI 变更须列全部实现为回归面）

- `IContextProvider`（`nop-kernel/nop-api-core/.../context/IContextProvider.java`，方法集 currentContext/getOrCreateContext/newContext/attachContext/detachContext）→ 生产实现 **×1**：`BaseContextProvider`（nop-api-core，唯一 `implements IContextProvider`）。
- `IContext`（`.../context/IContext.java`，含 `runOnContext` :130 / `executeWithContext` :132 / `syncGet` :171）→ 生产实现 **×2**：`BaseContext`、`DelegateContext`（均 nop-api-core）。**防误列注记**：`IExecutionContext extends IEvalContext, IAttributeSet, ICancellable`（nop-core）与 `IGraphQLExecutionContext`（nop-graphql-core）为独立接口树，**不**实现 IContext——`ExecutionContextImpl`/`ServiceContextImpl`（nop-core）与 `GraphQLExecutionContext`（nop-graphql-core）不因 IContext 变更受迫，但 `ExecutionContextImpl extends Cancellable`（nop-commons）继承 ICancelToken 基类实现。
- `ICancelToken`/`ICancellable`（nop-api-core 接口）→ 生产实现 **×11 类**（R1 复核补录 +4，live @ `6fd473937f` 重验）：`Cancellable`（nop-commons 基类）及其经基类继承的传递成员——`ExecutionContextImpl`、`ServiceContextImpl`（nop-core :21 `extends ExecutionContextImpl`，基类传递登记）、`TaskRuntimeImpl`（nop-task-core）、`BlockingSourceConsumeTask`、`DefaultTaskExecutionQueue.State`、`RpcMessageSubscriptions.Subscription`（nop-rpc-core :25 `extends Cancellable`）、`PulsarMessageService.PulsarMessageSubscription`（nop-message-pulsar :583 `extends Cancellable`）；直接实现——`FailoverStreamFlow`（nop-ai-gateway）、`DebeziumMessageSource.Subscription`（nop-message-debezium :207 直接 `implements ICancellable`）、`ReplayableCdcSourceFunction.ReplaySubscription`（nop-stream-fraud-example）。**措辞勘正（R1）**：原「`CancelTokenManager` 内部 token」条目不成立——`CancelTokenManager`（nop-graphql-core）`implements ICancelTokenManger`，仅持外部注册的 `ICancellable` token（`RegisteredRequest` 记录），自身非 ICancellable 实现类、亦无内部 token 实现类，从实现清单移除（其作为 token 持有方在裁定①兼容矩阵按消费方登记）。
- **接口签名变更取向**：默认**零接口签名变更（source-compat）**——bindCancel/completeAfterTask/executeWithThrottling 为 `FutureHelper` 静态工具语义收口、context 传播统一以 `ContextProvider` 静态入口（`thenOnContext` :172/:206/:183 已在）与既有 `IContext.executeWithContext/runOnContext` 承载，沿 R1-01 裁定⑤「既有签名零改动」先例；若设计裁定必须新增接口方法（如 IContextProvider 挂接形态），上列全部实现类（含执行时重枚举的测试 mock——`implements IContextProvider|implements IContext\b|implements ICancellable|implements ICancelToken|extends Cancellable` 全仓 grep，mock 对 `Cancellable` 的同型继承一并重枚举）**全部列入回归面**并在 Phase 1 设计文档登记最终清单。

### 14 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `6fd473937f` 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.3-C3-002（P1，R15 族主；C10-001 随单） | 2.3 / nop-service-framework | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:134-148`（invokeOperation 出口泵 thenCompose 无挂接；对照 `:151-158` thenFetchNext 已挂接）；递归泵调用点 `:145/:156`（dispatchAll 方法定义 `:200`）；`nop-graphql-orm/.../fetcher/OrmBatchLoader.java:29-37`（执行时回读） | 出口泵/递归泵未经 thenOnContext 挂接：异步顶层操作完成后 dispatchAll 与首轮嵌套游走整体在 future 完成线程执行，`OrmBatchLoader.requireSession`/`OrmEntityRefFetcher` 按线程上下文取 ORM 会话 → NOT_IN_SESSION 或落入 worker 残留会话（错会话脏数据） | 出口泵/递归泵统一包 `thenOnContext(..., env.getContext())`（与 thenFetchNext 对齐）或 batchLoader 显式携带 context 经 executeWithContext 执行——两形态由 Phase 1 裁定③定夺；C10-001 上下文传播回归网随本体（context 敏感型 loader 断言一并覆盖 C3-015） |
| 2 | 2.3-C3-007（P1，R15 族主；全单元唯一裸提交站点） | 2.3 / nop-service-framework | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionService.java:71-94`（task lambda `:71-79`、executeForPartition `:86-88` 与 execute `:89-91` 双分支裸提交、completeExceptionally `:92-94`）；`BizActionInvocation.java:58-74`（proceedAsync currentContext()==null NPE 面）；`nop-api-core/.../BaseContextProvider.java:35-54`（getOrCreateContext 池线程永久附着/复用残留） | 裸 executor.execute 绕过平台 ExecutorHelper「任务 finally detachContext」约定：调用方 IContext（tenant/locale/trace/callExpireTime）不传播 + ServiceContextImpl 构造在池线程永久附着新建/残留 context，跨任务串号 | 受管提交改造（改用 ExecutorHelper.submit/受管入口或显式快照+binder 传播 + finally detachContext——裁定③定夺）+ proceedAsync 对 currentContext()==null fail-fast 语义化错误；调用方上下文丢失面无条件成立（@Async biz 一等公民路径） |
| 3 | 2.1-C2-016（P2，G26；C10-009 随单） | 2.1 / nop-api-core | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextTaskQueue.java:46-105`（syncGet drain 段 finally `endProcess()` `:81`、await `:93`）；`:113-125`（enqueue signal 谓词 `syncing>0` `:117-118`）；`:127-135`（flush 入口 beginProcess 失败静默放弃）；`:161-173`（endProcessIfIdle 已修对照面，commit 5ee798e02e）；`BaseContext.java:227-244`（runOnContext 调用方随设施自动覆盖） | syncGet drain→await 过渡丢失唤醒窗：enqueue 信号谓词 syncing>0 与 drain 退出窗口错位 + flush 兜底静默放弃 → 任务滞留；若 promise 完成依赖滞留任务则永久挂死（全平台 FutureHelper.syncGet 路由面） | syncGet drain 段退出改 `endProcessIfIdle()` 复用（与 flush 同构，审计建议①）或 signal 谓词放宽 `syncing>0 || processingThread!=null`——二选一由 Phase 1 裁定④定夺（审计倾向前者）；C10-009 测试手法先例 = 同文件 flush 面内部锁冻结（nop-api-core TestContextTaskQueue） |
| 4 | 2.1-C3-004（P2，G26；bindCancel 单点服务第二条） | 2.1 / nop-kernel(nop-commons)+api-core 协同 | `nop-kernel/nop-commons/.../executor/BindScheduledExecutor.java:53-60`（schedule 底层 timer Future 丢弃）、`:116-122`（submit(Callable) 同构）、`:125-127`（submit(Runnable,result) 委托）、`:129-142`（executeOn 无 isDone 守卫）——审计载 :55-62/:128-135/:142-155，R1-01 改造后漂移（勘误 2）；生产暴露点 `nop-retry/nop-retry-engine/.../impl/RetryEngineImpl.java:404-407`（getExecutor `:621-623` = globalTimer.executeOn(globalWorker)） | schedule/submit 返回 future 的取消不传播到底层任务：底层 timer Future 丢弃无 bindCancel + executeOn 不查 isDone → 取消后任务照常完整执行（选举探测/延迟补偿类不可撤销副作用），取消也不能从 timer 队列移除待触发任务 | 接入 bindCancel（**必须以裁定①修复后的 bindCancel 为前提**——直接接入现状实现将落入 C3-016 误取消陷阱，两工单协同是本批「协同批」定位的根因）+ executeOn 执行前 isDone 守卫；RetryEngineImpl 生产暴露点列 1.6 回归面（裁定②） |
| 5 | 2.1-C3-005（P2，G26；双层：本体 nop-commons + api-core 半边） | 2.1 / nop-kernel(nop-commons)+api-core | 本体 `nop-kernel/nop-commons/.../executor/ExecutorHelper.java:307-330`（throttleExecute 内外两层 catch Exception，~`:316`/~`:323`——审计载 :240-260/:249/:255，R1-02 改造后漂移，勘误 3）；api-core 半边 `FutureHelper.completeAfterTask :321-335`（`:333` catch Exception）；使用面 `nop-frontend-support/nop-web/.../PageProvider.java:99/:133`、`WebPageExporter.java:120`（syncGet(waitAll) 无超时） | throttleExecute/completeAfterTask 双层 catch Exception 漏 Error → future 永不完成 → 调用方 syncGet(waitAll) 无超时永久挂死（任务体为 XPL 模板渲染 Error 高发区） | 双层 catch(Throwable)（对齐同文件 submit/schedule 正确样本）——本体（nop-commons）与 api-core 半边（completeAfterTask）**同批同口径**；nop-web 3 调用点随设施自动覆盖列回归面 |
| 6 | 2.1-C3-007（P2，G26；双层：本体 nop-commons + api-core 半边） | 2.1 / nop-kernel(nop-commons)+api-core | 本体 `nop-kernel/nop-commons/.../executor/LimitedExecutor.java:22-42`（`:25` acquire、`:37-40` finally 未获先 release）——live 零漂移；api-core 半边 `FutureHelper.executeWithThrottling :537-559`（`:548-553` InterruptedException 分支 `!released` 先 release）——审计载 L548-552 零漂移 | 中断路径幻影 release：acquire 抛 InterruptedException 未获得许可，finally 仍 release → 许可虚增并发上限击穿（静默劣化，反复中断单调上涨；LimitedExecutor 当前 0 生产调用方） | release 移入 acquire 成功后作用域（acquired 标志或 try 结构重排）——本体与 api-core executeWithThrottling 半边同批同口径 |
| 7 | 2.1-C3-016（P2，G26；bindCancel 单点服务第一条） | 2.1 / nop-kernel(nop-commons)+api-core | 本体 `nop-kernel/nop-commons/.../executor/ExecutorHelper.java:197-215/:217-239/:241-264`（submit×2/schedule：complete 在任务体内 + `bindCancel` 调用 `:215/:239/:264`——审计载 :128-199，R1-02 改造后漂移，勘误 3）；根因协作方 `nop-api-core/.../util/FutureHelper.java:313-318`（守卫 `!future.isDone()` 成功路径恒真）——live 零漂移；委托继承面 DefaultScheduledExecutor/DefaultThreadPoolExecutor submit/schedule | submit/schedule 正常完成路径必然触发 bindCancel 误取消：底层 FutureTask 以 cancelled 假终态收场并向 worker 注入中断残留（对非 TPE 载体可泄漏到下一任务） | 裁定①单点：bindCancel 守卫改按完成原因判别（`ex instanceof CancellationException` 才 cancel）+ ExecutorHelper 包装 Callable complete 后清除残留中断标志（双层修复，审计建议原样承载）；调用方兼容矩阵 = ExecutorHelper 3 处 + 委托面 + 全库其余 bindCancel 调用方（`DefaultTaskExecutionQueue:238` 为 C3-018 归 R4-13，按新契约复核注记） |
| 8 | 2.3-C3-012（P2，G37；入口 context 统一） | 2.3 / nop-service-framework(+nop-quarkus、nop-spring 站点) | `nop-graphql-grpc/.../GraphQLServerCallHandler.java:102-103`（newRpcContext 无 binder）；`nop-quarkus/nop-quarkus-web/.../ws/JsonRpcWebSocketEndpoint.java:50-64`；第五入口（R1 补录）`nop-spring/nop-spring-web-starter/.../ws/JsonRpcWebSocketEndpoint.java:48-70`（onOpen 执行 lambda 内 newRpcContext，与 quarkus 变体同构）；`nop-graphql-message/.../GraphQLMessageSubscriptionRegistrar.java:84-96`（onMessage 入口，newRpcContext `:90`——审计载 :103-112 对应 getOperationName 区域 `:98+`，勘误 9）；`nop-biz-file-core/.../AbstractGraphQLFileService.java:42-60`；`GraphQLEngine.java:449`（initRpcContext，`:466` 仅 setRequestHeaders——审计载 :466 零漂移） | gRPC/WS（quarkus/spring 两变体）/消息/文件五入口无 ContextBinder 等价逻辑：tenant/locale/callExpireTime 不从请求头落线程 IContext，与 HTTP 入口（ContextHttpServerFilter 履行）形成同一引擎两套上下文语义 | newRpcContext 内统一接入 binder 等价逻辑或提供 newRpcContextWithBinding 供五入口共用（裁定③定夺）；第五入口归属随裁定③（R1）：内置统一接入形态 → spring 站点自动覆盖零改码，回归面补一条 spring-WS 入口断言（nop-spring 入触达清单与验证通道）；新入口形态 → nop-spring 站点改码超出 batch-proposal 模块列，显式移交登记不静默扩界；IMessageSubscriber「消费方必须等待 CompletionStage 完成」解包契约缺口随批注记 |
| 9 | 2.3-C3-015（P2，G37；C3-002 同族相邻 A4） | 2.3 / nop-service-framework | `nop-graphql-core/.../fetcher/TransformFetcher.java:29-40`（`:33` thenCompose 无挂接）；`EvalActionTransformFetcher.java:31-35`（同构）；族内对照 RouteExecutor.java:74-81、RpcServiceOnGraphQL.java:73-75（同族登记面） | fetcher 层异步出口缺 thenOnContext：transformer 用户表达式（XPL/携带 env/EvalScope）于上游完成线程执行——线程上下文读到空/残留、EvalScope 线程亲和未声明 | thenCompose 前包 `thenOnContext(value, env.getContext())`（与 thenFetchNext 对齐；随裁定③统一口径同批挂接，C10-001 断言一并覆盖两案） |
| 10 | 2.3-C3-017（P2，G37；平台根因=CL-011 同源） | 2.3 / nop-service-framework(+平台根因 nop-api-core/nop-http-api) | 后果读点 `GraphQLEngine.java:670`（buildRpcResponse `currentLocale()`——零漂移）+ subscribeRpc/subscribeGraphQL 同步段（M21，tenant 面经 TenantParamBuilder 条件化）；机制点 `nop-network/nop-http/nop-http-api/.../ContextHttpServerFilter.java:44-64`（裸附着无对称 detach）、`BaseContext.close :315-327`（条件 detach `holder==this` 异步完成常态不成立——零漂移）、`JsonRpcWebSocketEndpoint.java:44-64` | filter 链每请求 context 裸附着无对称 detach：IO 线程残留 closed context，无 filter 链入口（WS 消息）读点读到跨连接残留 tenant/locale（租户隔离后果条件化） | 本单元动作：(a) `buildRpcResponse:670` 改 `context.getLocale()` 优先、线程读取降级兜底；(b) WS/订阅同步段以消息头构造的 context 执行（与 C3-012 同修）；平台根因分工（R1 统一）：BaseContextProvider.currentContext isClosed 守卫 + BaseContext.close 对称 detach 半边（nop-api-core）由裁定③定形态、**Phase 2 checklist 项落地+断言**，本成员 Phase 3 仅消费验证 (a)(b)；ContextHttpServerFilter（nop-http-api）裸附着面是否需随根因形态配套改码由裁定③同点裁定（4.3-CL-011 平台根因移交的承接点） |
| 11 | 2.3-C3-008（P2，G39 随 C3-007 同批） | 2.3 / nop-service-framework | `nop-graphql-core/.../engine/TccContextInvoker.java:22-37`（`:30-31` setCurrent + 无参 thenOnContext + whenComplete 完成线程恢复）；`nop-kernel/nop-core/.../TccContext.java:62-87`（`:69-73` getOrCreateContext 附着泄漏） | TCC set/restore 依赖「completion 与调用同线程」假设：无线附着线程发起时 getOrCreateContext 永久附着新 context，跨任务残留 txnId/branchId → TCC 分支错配（条件化，与 C3-007 复合） | invokeAsync 入口 currentContext()==null fail-fast（语义化错误，非静默附着）+ 恢复逻辑改为对捕获的 IContext 对象属性写回（非完成线程 currentContext）；与 C3-007 修复联动收敛 |
| 12 | 4.3-C3-007（P2，G68；CL-011 平台根因协同） | 4.3 / nop-network(+平台根因 nop-api-core) | `nop-network/nop-rpc/nop-rpc-api/.../ApiContextInterceptor.java:27-33`（interceptAsync binder.close 于完成线程；同步抛保护缺失为兄弟面 C3-006 随单）；`ContextBinder.java:23-49`（**live 位于 nop-rpc-api，非审计根因链所列 nop-api-core 旁**，勘误 4）；平台根因 `BaseContext.java:315-327` + `BaseContextProvider.java:21-23`（currentContext 无 isClosed 守卫）vs `:35-41`（getOrCreateContext 已有） | binder.close 于异步完成线程执行：发起线程 ThreadLocal 不清除 → 池化线程复用已关闭 context 串号（tenantId/traceId/locale 字段不被 attributes.clear() 清除，跨请求外发泄漏路径） | 分工（R1 统一）：平台根因半边（`BaseContextProvider.currentContext` isClosed 守卫 + `BaseContext.close` 对称 detach，nop-api-core）由裁定③定形态、**Phase 2 checklist 项落地+断言**（成员 12 为消费验证方）；本成员 Phase 3 收窄为 nop-rpc-api 侧——ContextBinder 对 isClosed 残留强制替换（补齐 getOrCreateContext 已有检查的对称守卫）+ 发起线程侧「归我清理」detach 协议或 thenOnContext 收敛（按裁定③形态消费验证）+ ApiContextInterceptor 同步抛保护（C3-006 面）随单闭合；4.3-CL-011 平台根因移交由 Phase 2 平台根因项承载落账 |
| 13 | 4.3-C3-012（P2，G68；C10-010 随单） | 4.3 / nop-network | JdkHttpClient `:169/:381/:545/:582`（4 站点 appendOnCancel 无 remove——审计载 :168-172/:380-381/:541-542/:578-579，勘误 5）；OkHttpClientImpl `:64/:155/:329`（3 站点）；对照正确实现 `FutureHelper.bindCancelToken :518-535`（Apache 4 站点 + 双 ServerEventPublisher 经此零中招）；round2 扩展 `MessageRpcClient.java:104`、`CancellableRpcClient.java:54`（中招）；`PollingRpcClient.java:124`（手动 remove 正确样本，plan 2498 改造后需回读） | cancelToken 取消监听器只增不删：长生命周期 token 监听器随请求次数无界累积（每条目钉住 future→response→body 对象图），token 取消时 O(n) 遍历 | 全部中招站点（jdk×4/okhttp×3/MessageRpcClient/CancellableRpcClient）改用 `FutureHelper.bindCancelToken(cancelToken, consumer, future)` 平台单点收敛；C10-010 缺保护随本体（appendOnCancel 站点测试） |
| 14 | 3.2-C4-008（P2，G55；CL-012/CL-017 同 diff 随单；C10-014 随单） | 3.2 / nop-ai | `nop-ai/nop-ai-toolkit/.../manager/ToolManagerImpl.java:100-121`（排队分支手工 result future 与执行链断链）；`.../tools/JqToolExecutor.java:36-49`（全根唯一 cancelToken 消费；**live 已含 removeOnCancel `:48`**，勘误 5）；`nop-api-core/.../util/ICancelToken.java:11-25`（契约对已取消 token 无补发承诺）；17 executor 取消能力对账（BashExecutor :103-105 不读 cancelToken 等） | 取消链覆盖面断链：result.cancel(true) 假确认（CF 取消语义不传播上游链，排队/在途 bash 照常跑满）+ 排队窗口 cancelToken 盲区（监听器轮次到来才注册）+ 16/17 executor 不读 cancelToken；复核欠报修正：经 manager 返回 future 的取消任何路径都不达池任务 | (1) 排队分支 result 挂取消联动（result 取消 → 触发链 cancel）；(2) `executeNow.get()` 统一 `isCancelled()` 短路；(3) CL-012 注册窄窗同 diff（注册前 isCancelled 补触发一次）+ CL-017（fs 族不读 token 对账面）随单；API 形态对接裁定①/⑥ 的 bindCancel/ICancelToken 平台契约 |

### R1-01/R1-02 产物边界与衔接（本批共享代码位约束）

- **R1-01 绝缘层裁定⑤禁用条款（periodic-task-insulation.md §3.5）**：绝缘返回 future（InsulatedPeriodicChain）**禁止**任何「任意完结 → 自动取消 native handle」的 bindCancel 接线；任务体内允许的 complete 仅限失败结算且不得与任何 cancel 接线共存；fixedDelay 逐轮一次性调度行走 ExecutorHelper.schedule 的 bindCancel 既有路径（未扩大触发面），**「C3-016 的修复在 R1-04 于 ExecutorHelper 层收口，届时绝缘核心逐轮调度自动受益」**——本批裁定②为该条款的承接闭合点，落地时须验证绝缘核心逐轮调度受益且 `TestPeriodicTaskInsulation` 9 条全绿（共享代码位回归门禁），绝缘语义（重臂/三相容/失败治理/拒绝路径）零回改。
- **R1-02 池设施裁定⑥（pool-facility-hardening.md）**：`ExecutorHelper.schedule/submit` bindCancel 面、绝缘四载体、`DefaultScheduledExecutor` periodicTasks 登记表为 R1-02 零改动声明面——本批对该面的改造即裁定①本体，落地时以 R1-02 交付的 `TestThreadPoolFacilityHardening` 7 条 + 绝缘门禁为回归底座；池工厂校验/热更语义（EXEC-001/002 锚点）不被本批回改。
- **plan 2498 相邻面**：PollingRpcClient（C4-003 deadline offload）与 nop-graphql-core subscription/ws 三文件（C8-001 推送卸载）已改造——与本批站点零重叠；C3-012 的正确对照样本 PollingRpcClient 手动 remove 形态执行时回读确认。

### 消费方回归面（bindCancel 单点改造服务面——exit criteria「回归面全列执行」的清单义务）

- **1.6（nop-retry/nop-tcc）**：`RetryEngineImpl.java:404-407` 延迟重试任务经 `getExecutor()`（:621-623，BindScheduledExecutor 载体）调度——C3-004 生产暴露点 + C3-016 误取消面直接消费方；R1-01 交付的 `TestRetryScannerInsulation` 3 条与 RetryScannerImpl 绝缘入口同模块。回归义务 = `./mvnw test -pl nop-retry -am` 全绿 + bindCancel 修复后新增取消传播/不再误取消的针对性断言（rule 25）。
- **4.3（nop-network）**：ApacheHttpClient 4 站点（bindCancelToken 正确样本零回归）+ Jdk/OkHttp 7 站点（本批成员 13 修复面）+ MessageRpcClient/CancellableRpcClient/PollingRpcClient + 双 ServerEventPublisher。回归义务 = `./mvnw test -pl nop-network -am` 全绿。
- **3.4（nop-ai rest/rag）**：`IEmbeddingModel.embed/embedAll` 与 `IChatService.call` SPI 默认方法 = `FutureHelper.syncGet(...)` → `ContextTaskQueue.syncGet`（nop-ai-rest 审计桥接事实）——C2-016 修复面的直接消费方。回归义务 = `./mvnw test -pl nop-ai -am` 全绿（rag/rest 套件含于聚合门禁）。
- **2.1 设施内消费面**：ExecutorHelper 3 处 + DefaultScheduledExecutor/DefaultThreadPoolExecutor 委托面 + `DefaultTaskExecutionQueue:238`（C3-018，R4-13 注记）——bindCancel 全库调用方 grep 矩阵随裁定①登记。

### Baseline 勘误（输入不一致登记）

1. **batch-proposal R1-04 模块列不完整**：列出的模块为 nop-kernel（io.nop.api.core.*）；nop-service-framework；nop-network；nop-ai。实测成员站点实际触达：nop-kernel 的 **nop-commons**（C3-004/C3-005/C3-007/C3-016 双层本体，非 io.nop.api.core.*——PA 授权连带声明见头部）、**nop-quarkus**（2.3-C3-012 站点 JsonRpcWebSocketEndpoint 在 nop-quarkus-web）、**nop-frontend-support**（C3-005 使用面 nop-web PageProvider/WebPageExporter）、**nop-spring**（R1 补录：2.3-C3-012 第五站点 JsonRpcWebSocketEndpoint 在 nop-spring-web-starter——是否产生 diff 随裁定③形态：内置统一接入则零改码仅补回归断言，新入口形态则触达改码并按成员 8 归属裁定处置）。本 plan 验证命令按实际 diff 触达模块列全（Phase 4）；nop-web 调用点为设施消费方不改码（随设施自动覆盖）。
2. **2.1-C3-004 站点行号与对照面漂移（R1-01 绝缘层改造所致）**：审计载 BindScheduledExecutor.java:55-62/:128-135/:142-155 → live :53-60/:116-122/:125-127；executeOn 私有助手 live :129-142（审计证据片段同构，无 isDone 守卫维持成立）。审计对照面「同文件 scheduleWithFixedDelay 用 future.isDone() 守卫（91/96 行）」已被 R1-01 重写为绝缘链（`PeriodicTaskHelper.scheduleWithFixedDelay` :97-103）——该对照语义现由绝缘链的链存活/取消契约承载，C3-004 修复不得回改绝缘链；核心缺陷三要素（底层 timer Future 丢弃/无 bindCancel/executeOn 无守卫）live 零漂移成立。
3. **ExecutorHelper 行号整体漂移（R1-02 池设施改造所致）**：C3-016 站点 submit×2/schedule 审计载 :128-199（bindCancel 于 137-148/159-172/184-197）→ live :197-264（bindCancel 调用 :215/:239/:264）；C3-005 throttleExecute 审计载 :240-260 → live :307-330（双层 catch Exception 维持未修）。R1-02 执行记录已证「ExecutorHelper.schedule/submit（bindCancel 面）零改动」——缺陷面未被前批触碰，行号漂移为纯插入位移。LimitedExecutor（C3-007 本体 :22-42）与 FutureHelper bindCancel（:313-318）/executeWithThrottling（:548-553）零漂移。
4. **4.3-C3-007 ContextBinder 归属路径勘误**：审计根因链列「ContextBinder.java:25-45」置于 nop-api-core 文件旁且未带全路径——live `ContextBinder` 位于 `nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ContextBinder.java`（:23-49 构造器命中残留 created=false 不复初始化成立），**非 nop-api-core**；平台根因半边（BaseContext.close :315-327 条件 detach、BaseContextProvider currentContext 无 isClosed 守卫）确在 nop-api-core。修复面跨 nop-rpc-api（止血）与 nop-api-core（根因），PA 授权按头部声明覆盖。
5. **3.2-C4-008 的 JqToolExecutor live 已含 removeOnCancel（:48）**：审计证据片段只示 appendOnCancel（:40-47）——live 该行之后已有 `future.whenComplete(...removeOnCancel...)` 监听器清理（监听器累积面在该单点已闭合）；C4-008 立案面维持不变（排队窗口盲区 + result 断链 + 16/17 executor 不读 token），CL-012 注册窄窗面（注册前已取消不补发）维持随单。4.3-C3-012 站点行号小幅漂移：JdkHttpClient 审计 :168-172/:380-381/:541-542/:578-579 → live :169/:381/:545/:582；OkHttpClientImpl :63-64/:154-155/:326-327 → live :64/:155/:329。
6. **「服务 2.1 三条」成员口径澄清**：batch-proposal R1-04 exit criteria 句「FutureHelper.bindCancel 单点改造（服务 2.1 三条…）」= 2.1 审计 merge-adjudication §M12 协同改造点的三条 **C3-016（守卫判别）/C6-001（反向传播终态化）/C3-004（传播语义）**；其中 C6-001（2.1 销毁链族）**不是本批成员**（归 R4-14/G27）——本批只保证 bindCancel 单点改造对其受益面不设障碍（M12 裁定原文），其工单收敛归 R4-14，防越界承接。
7. **R15 行内 P2 成员 4.4-MA-05 不在本批**（R1 计数勘正：原表述「R15 家族第八成员」计数不确，以 family-clustering §三 R15 行实际成员列为准）：family-clustering R15 行 P2 成员含 4.4-MA-05（家族修复归 2.3 轨道），其批次归属 = R4-28（G70）——本批不承接其工单，裁定③的 context 原语统一口径对 R4-28 作软对齐传导注记。
8. **source-anchors.md 现无 api-core 取消/上下文原语锚点**（grep FutureHelper/ContextTaskQueue/thenOnContext 仅 TNT-001/TNT-002 租户面既有条目）：roadmap 8.5 Owner Doc 列的「api-core 锚点」为**本批新建**条目，非更新既有条目。
9. **2.3 站点微漂移**：GraphQLExecutor 出口泵审计载 :135-147 → live :134-148（thenFetchNext 对照 :151-158 ✓）；dispatchAll 方法定义审计载 :203 → live :200；GraphQLEngine currentLocale :670 零漂移。GraphQLMessageSubscriptionRegistrar 审计载 :103-112 → live onMessage 入口 :84-96（newRpcContext :90；审计区间对应 getOperationName :98+，注册点 subscribe :78）——消息入口修复站点以 live :84-96 为准。OrmBatchLoader（load 体 live :31-39，审计载 :29-37）执行时回读。

## Goals

- Phase 1 设计文档一次裁决（plan-first 前置，独立 draft review 通过）六裁定：①bindCancel 单点改造语义（完成原因判别 + 中断残留清除 + completeAfterTask/executeWithThrottling 半边收口 + 调用方兼容矩阵）；②取消传播统一（C3-004 接入时序 + R1-01 禁用条款衔接 + R1-02 零改动面维持）；③context 传播原语统一（出口泵补齐形态、入口 binder 统一、BaseContext.close/currentContext 平台根因、C3-007 受管提交、C3-008 fail-fast、4.1-CL-024 归属裁定、4.4-MA-05 传导注记）；④C2-016 syncGet 修复选型与 C10-009 手法；⑤SPI 回归面全列声明（零签名变更取向 + 实现类清单终稿）；⑥C4-008 取消链接线形态与 ICancelToken 平台契约。
- 14 项成员全部修复并各带回归测试（rule 25，先红后绿或变异/回退论证）：2.3-C3-002/C3-007、2.1-C2-016/C3-004/C3-005/C3-007/C3-016、2.3-C3-012/C3-015/C3-017/C3-008、4.3-C3-007/C3-012、3.2-C4-008（CL-012/CL-017 同 diff 随单）。
- exit criteria 三支柱落地：bindCancel 单点改造（服务 2.1 三条 + 消费方 1.6/4.3/3.4 回归面全列执行）；executeWithContext/thenOnContext 统一（出口泵补齐）；4.1-CL-024 归属本族注记闭合。
- C10 验收前置随本体：C10-009（syncGet 窗口，锁冻结手法）、2.3-C10-001（上下文传播回归网）、4.3-C10-010（appendOnCancel 站点测试）、3.2-C10-014（17 executor 取消矩阵 + wrapper 传播断言）。
- Owner doc 随批更新：`docs-for-ai/04-reference/source-anchors.md`（**新建** api-core 取消/上下文原语锚点：bindCancel 契约、bindCancelToken 单点、thenOnContext/executeWithContext 治理口径、syncGet 语义）、`docs-for-ai/02-core-guides/service-layer.md`（异步 biz action 上下文传播契约：受管提交形态 + 出口泵 thenOnContext 纪律）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试 / 验证通道留证 / 授权声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不做 R14 族 P1 成员**：3.1-MA-08/MA-05（取消传播伞族引擎侧）、4.3-MA-02（JDK 取消语义失效消费面）归 R2-02（软依赖本批原语）；本批只产出 bindCancel/ICancelToken 平台原语与 4.3 面的 P2 协同成员。
- **不承接 2.1 相邻面**：C3-018（DefaultTaskExecutionQueue bindCancel 参数反接，归 R4-13——本批仅按新契约留复核注记）、C3-003（Bind fixedRate 拒绝面，工单收敛归 cra-index 裁定批次；R1-01 已顺带覆盖守卫位置）、C3-017（scheduleWithRandomDelay， cra-index 裁定批次）、C3-013（context 卫生）、C6-001（销毁链族，归 R4-14——bindCancel 受益面不等于工单承接）。
- **不承接 4.4-MA-05**（归 R4-28，裁定③口径软对齐）；不做 2.3-C3-013（ContextBinder no-op 死代码复用分支，随入口统一治理的后续评估）；不做 4.1-CL-024 的 LocalMessageService 改码（本批义务 = 归属裁定 + 台账注记闭合；若裁定判需修复，successor 归 4.1 批次承载）。
- **不回改 R1-01 绝缘层语义与 R1-02 池设施语义**：重臂/三相容/失败治理/拒绝路径（periodic-task-insulation.md）与池工厂校验/热更/容量语义（pool-facility-hardening.md、EXEC-001/002 锚点）维持既有裁定；共享代码位（BindScheduledExecutor/ExecutorHelper）改动以 Phase 1 裁定②显式声明边界，绝缘 9 条 + 设施 7 条回归全绿为门禁。
- 不做 raw 载体迁移、不做 R2-05/R2-06 等 2.3 相邻批工单；不触碰 `model/*.orm.xml`、nop-auth。

## Scope

### In Scope

- `nop-kernel/nop-api-core`：`FutureHelper`（bindCancel/completeAfterTask/executeWithThrottling）、`ContextTaskQueue`、`BaseContext`/`BaseContextProvider`（close/detach/currentContext 语义）+ nop-api-core 单元测试。
- `nop-kernel/nop-commons`：`ExecutorHelper`（submit×2/schedule bindCancel 接线面、throttleExecute）、`LimitedExecutor`、`BindScheduledExecutor`（schedule/submit/executeOn）——双层本体半边 + 单元测试（绝缘/设施既有测试为门禁）。
- `nop-service-framework`：nop-graphql-core（GraphQLExecutor 出口泵/递归泵、TransformFetcher/EvalActionTransformFetcher、TccContextInvoker、GraphQLEngine 读点）、nop-graphql-grpc、nop-graphql-message、nop-biz（BizActionService）、nop-biz-file-core + 单元测试。
- `nop-network`：nop-rpc-api（ApiContextInterceptor/ContextBinder 止血面）、nop-rpc-core（MessageRpcClient/CancellableRpcClient）、nop-http-client-jdk、nop-http-client-okhttp + 单元测试。
- `nop-ai`：nop-ai-toolkit（ToolManagerImpl/JqToolExecutor/executor 族取消接线）+ 单元测试。
- Owner doc：source-anchors.md（api-core 新建锚点）、service-layer.md；触达模块 owner doc 归属核对与缺口声明（以实际 diff 为准）。

### Out Of Scope

- Deferred 节所列项；R2-02/R2-16/R2-21/R4-13/R4-14/R4-16/R4-28 等下游批次工单（见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计文档六裁定 + 独立 draft review（plan-first 前置）

Status: planned
Targets: `ai-dev/design/concurrency/`（新建：api-core 取消通道与上下文传播统一设计）

- Item Types: `Decision`

- [ ] 设计文档落 `ai-dev/design/`（最终状态写法，rule 14：只写决策与契约，不写 Proposed-vs-Current、不写类签名代码），须含以下六项裁定：
  - [ ] **裁定① bindCancel 单点改造语义（本批核心裁定）**：`FutureHelper.bindCancel` 守卫从 `!future.isDone()` 改为按完成原因判别（`ex instanceof CancellationException` 才 cancel——审计建议原样承载）的行为语义与 source-compat 声明；ExecutorHelper 包装 Callable 在 complete 后清除残留中断标志的双层修复（消除对载体 TPE getTask 吸收中断的依赖）；**api-core 三半边一次收口**：completeAfterTask catch 宽度（C3-005 半边）、executeWithThrottling 幻影 release（C3-007 半边）；**调用方兼容矩阵**：全库 bindCancel 调用方 grep 全量登记（ExecutorHelper 3 处、Default*Executor 委托面、`DefaultTaskExecutionQueue:238`[C3-018/R4-13 复核注记] 等），逐调用方行为影响声明（修复后：成功路径不再误 cancel、真取消路径传播保持）；`bindCancelToken`（:518-535）与 bindCancel 的关系声明（4.3-C3-012 的平台收敛入口）。
  - [ ] **裁定② 取消传播统一与 R1-01/R1-02 产物边界衔接**：C3-004 的 bindCancel 接入**必须后置于裁定①修复**（直接接入现状实现将落入 C3-016 陷阱——两裁定落地顺序约束成文）+ executeOn isDone 守卫语义；R1-01 禁用条款衔接验证方案（绝缘链 future 无 bindCancel 接线维持、ExecutorHelper 层修复后绝缘核心逐轮调度自动受益的断言点、`TestPeriodicTaskInsulation` 9 条门禁）；R1-02 零改动面（池工厂校验/热更/容量语义、EXEC-001/002）不被回改的边界声明；RetryEngineImpl 生产暴露点（:404-407）的回归断言形态（1.6 回归面）。
  - [ ] **裁定③ context 传播原语统一（executeWithContext/thenOnContext 出口泵补齐）**：C3-002 出口泵/递归泵挂接形态二选一（pump 级 thenOnContext vs OrmBatchLoader 显式 context 包装；判据注记[R1]：与既有 thenFetchNext 先例一致性 / 递归泵覆盖面 / 每跳 context 开销）+ C3-015 fetcher 层挂接同口径；C3-012 newRpcContext 统一 binder 入口形态（内置 vs newRpcContextWithBinding 新入口，五入口共用——gRPC/quarkus WS/spring WS[第五入口，R1 补录]/消息/文件）；**平台根因统一裁定**：BaseContext.close 对称 detach 语义（attach 线程登记归还 or close 携带 attach 线程句柄）与 currentContext/ContextBinder isClosed 残留强制替换守卫（4.3-C3-007 止血 + 2.3-C3-017 残留面 + 4.3-CL-011 平台根因移交的承接落账；落地分工[R1]：BaseContextProvider.currentContext 守卫 + BaseContext.close 对称 detach 半边在 Phase 2 落地，nop-rpc-api ContextBinder 替换在 Phase 3 落地）；C3-007 受管提交形态（ExecutorHelper.submit 受管入口 vs 显式快照+binder；受管形态注记[R1]：BizActionService.java:86-91 从 beanContainer 取出的是 `Executor`（非 `ExecutorService`），`ExecutorHelper.submit` 不能直接承载——选该形态须处理 bean 类型问题）；C3-008 fail-fast 语义；`thenOnContext`/`IContext` javadoc 契约成文（线程亲和语义声明）；**4.1-CL-024（Local 红投）归属裁定**：消费线程 IContext 传播专题按本裁定的入口统一口径给出结论（Local 发送者→scheduler 异步边界是否需要传递；broker 后端消费线程既定形态维持的边界声明），结论回填 cra-index/CL 台账注记（exit criteria 第三支柱）；4.4-MA-05（R4-28）口径传导注记。
  - [ ] **裁定④ C2-016 syncGet 窗口修复选型**：drain 段 `endProcessIfIdle()` 复用（审计建议①，与 flush 同构）vs enqueue signal 谓词放宽（`syncing>0 || processingThread!=null`）二选一及理由；await 谓词是否补 tasks 空判定；C10-009 测试手法采纳（同文件 flush 面内部锁冻结先例，构造「drain 完成后、await 前插入 enqueue+flush」交错 + 「enqueue 返回 true 的等待方必然被后续 flush 唤醒」不变式用例）；与已修 flush 面（commit 5ee798e02e）的对称性声明。
  - [ ] **裁定⑤ SPI 回归面全列声明（PA 义务）**：接口签名零变更取向确认（bindCancel 等为静态工具语义收口；context 统一以静态入口 + 既有 IContext 方法承载）；若任一裁定需要 IContext/IContextProvider/ICancelToken 接口变更，则 Baseline SPI 回归面清单（BaseContextProvider/BaseContext/DelegateContext；`Cancellable` 基类及其继承族——ExecutionContextImpl/ServiceContextImpl/TaskRuntimeImpl/BlockingSourceConsumeTask/DefaultTaskExecutionQueue.State/RpcMessageSubscriptions.Subscription/PulsarMessageSubscription；直接实现——FailoverStreamFlow/DebeziumMessageSource.Subscription/ReplayableCdcSourceFunction.ReplaySubscription）**全部实现类列为回归面**并逐个登记验证方式（重枚举 grep 模式含 `extends Cancellable`，见 Baseline SPI 回归面清单）；落盘前重跑全仓 grep 重枚举 mock/测试实现并登记最终清单。
  - [ ] **裁定⑥ C4-008 取消链接线形态与 ICancelToken 平台契约**：排队分支取消联动形态（result 取消传播到触发链）+ executeNow 统一 isCancelled 短路位 + CL-012 注册前补触发语义与裁定①修复后 bindCancelToken 契约的对接；CL-017（fs 族对账面）随单处置声明；17 executor 取消矩阵的验收边界（C10-014：矩阵断言 + wrapper 传播断言）。
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析）通过后，Phase 2–5 方可启动（plan-first 门禁）——记录于本 plan 头部 Draft Review 行或 daily log
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 设计文档存在于 `ai-dev/design/`、为最终状态写法、含裁定①–⑥全部项，且独立 draft review 记录在案（plan 头部 Draft Review 行或 daily log）
- [ ] 裁定⑤的 SPI 回归面清单终稿在案（接口零变更声明，或全实现类回归面登记）；裁定③含 4.1-CL-024 归属结论与台账回填路径
- [ ] `No owner-doc update required`（本 Phase 不改使用方文档；owner doc 更新归 Phase 4 统一落账）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - api-core 本体 + nop-commons 双层本体实现 + SPI 全实现回归测试

Status: completed
Targets: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java`、`.../context/ContextTaskQueue.java`、`.../context/BaseContext.java`、`.../context/BaseContextProvider.java`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/ExecutorHelper.java`、`.../LimitedExecutor.java`、`.../BindScheduledExecutor.java`、两模块 `src/test/`

- Item Types: `Fix`（2.1-C2-016/C3-004/C3-005/C3-007/C3-016 + 裁定③平台根因半边[4.3-C3-007/4.3-CL-011 承接面]均为已确认 live defect）、`Proof`（单元测试 + SPI 回归测试）

- [x] 2.1-C3-016 双层修复：bindCancel 守卫按完成原因判别（裁定①）+ ExecutorHelper 包装 Callable 中断残留清除（提交纪律：api-core 半边与 nop-commons 本体**同一提交**，防止中间态「误取消与取消不传播并存」的断裂基线）——提交 `3f76e2a5b2`（FutureHelper.bindCancel 三态判别 + ExecutorHelper submit×2/schedule 三处 `Thread.interrupted()` 清除同 diff）
- [x] 2.1-C3-004：BindScheduledExecutor schedule/submit 接入修复后 bindCancel + executeOn isDone 守卫（**必须后置于 C3-016 修复落地**，裁定②顺序约束）；绝缘链零触碰自证（InsulatedPeriodicTask/InsulatedPeriodicChain/PeriodicTaskHelper/IPeriodicTaskListener diff 零改动）——提交 `c1a1491e99`；零触碰自证 `git diff fe54cea14f..HEAD` 四载体零命中
- [x] 2.1-C3-005 双层修复：throttleExecute 双层 catch(Throwable) + completeAfterTask catch 宽度（同批同口径）——随 `3f76e2a5b2` 同 diff
- [x] 2.1-C3-007 双层修复：LimitedExecutor release 移入 acquire 成功后作用域 + executeWithThrottling 同构修复——随 `3f76e2a5b2` 同 diff
- [x] 2.1-C2-016：ContextTaskQueue.syncGet 窗口修复（按裁定④选型① endProcessIfIdle 复用）；与已修 flush 面共用同一退出原语——提交 `11c1124ee3`
- [x] **裁定③平台根因半边（nop-api-core，R1 归属统一）**：`BaseContextProvider.currentContext` 补 isClosed 守卫（惰性清除残留并返回 null，与 getOrCreateContext 对称双读点）+ `BaseContext.close` 对称 detach（形态按裁定③=维持条件 detach，javadoc 契约钉死零行为变化）+ 断言（TestBaseContextClosedResidueGuard 4 条：isClosed 残留不可观测/同线程对称清除/getOrCreateContext 替换/close 幂等）——提交 `11c1124ee3`
- [x] 本体单元测试（rule 25 显式覆盖，测试名可辨识工单）：
  - [x] C3-016：submit 成功路径断言底层 future 非 cancelled + worker 无中断残留（修复前红：`isCancelled()==true` 直接复现，审计给出的单测手法）——`TestExecutorHelperCancelContract.testSubmitSuccessDoesNotCancelUnderlyingFutureTask`/`testScheduleSuccessDoesNotCancelUnderlyingFutureTask`（submit/schedule 双载体；底层 FutureTask 终态为判别面，worker 残留为绿态断言——TPE 载体 getTask 吸收中断为审计载明事实）
  - [x] C3-004：cancel 返回 future 后底层任务不执行（timer 触发前取消 + executeOn 守卫两面）——`TestBindScheduledExecutorCancelPropagation.testRealTimerChainCancelBeforeFirePreventsExecution`/`testSubmitCancellationPropagatesToTimerTask`/`testExecuteOnGuardSkipsDispatchForDoneFuture`
  - [x] C3-005：任务体抛 Error 后 future 异常终态（调用方 syncGet 可退出）——`TestFutureHelperBindCancelContract.testCompleteAfterTaskErrorCompletesPromiseExceptionally` + `TestExecutorHelperCancelContract.testThrottleExecuteTaskBodyErrorCompletesFutureExceptionally`
  - [x] C3-007：n=1 + 中断断言许可数不虚增（修复前红：中断后许可 >1）；api-core executeWithThrottling 同构断言——`TestLimitedExecutorPermitScope.testInterruptedAcquireDoesNotInflatePermits` + `TestFutureHelperBindCancelContract.testExecuteWithThrottlingInterruptedAcquireDoesNotInflatePermit`
  - [x] C2-016：C10-009 锁冻结手法红转绿（drain→await 交错 + flush 唤醒不变式用例）——`TestContextTaskQueueSyncGetWindow` 3 条（窗口红转绿 + enqueue 返回 true 唤醒不变式钉 + drain 语义钉）
  - [x] bindCancel 兼容矩阵钉：真取消路径传播保持 + 成功路径零取消（调用方行为面断言）——`TestFutureHelperBindCancelContract` 成功/失败/真取消/已完成目标四态 + `TestExecutorHelperCancelContract` 真取消中断/排队取消两面
- [x] **SPI 回归测试**（PA 义务）：裁定⑤零接口签名变更成立——`TestApiCoreSpiSurface` 5 条（IContextProvider/IContext/ICancelToken/ICancellable 方法集逐名钉死 + BaseContextProvider/BaseContext/DelegateContext 生产实现钉）+ 仓级 grep 重枚举登记 `_tmp/phase2-spi-enumeration.log`（×11 生产类 + 7 mock 文件与设计 §3.5 全表一致）+ nop-api-core/nop-commons 全量测试门禁承载行为回归
- [x] **接线验证**（rule 23）：≥1 条断言证明修复后的 bindCancel 在真实构造链生效（ExecutorHelper.submit 全链 + BindScheduledExecutor.schedule 链 + globalTimer 拓扑）——`testSubmitSuccessDoesNotCancelUnderlyingFutureTask`（ExecutorHelper.submit→FutureHelper.bindCancel 真实链）+ `testRealTimerChainCancelBeforeFirePreventsExecution`/`testRealTimerChainNormalScheduleStillFires`（DefaultScheduledExecutor 载体 BindScheduledExecutor.schedule 全链）
- [x] **无静默跳过**（rule 24）：修复不引入空 catch/吞异常；取消判别失败路径显式落日志或异常终态——bindCancel 非取消完结不 cancel（语义正确路径非跳过）；executeOn 兜底守卫短路即取消终态已由 future 承载；throttleExecute/completeAfterTask/executeOn 全部异常路径结算异常终态
- [x] **提交纪律**：C3-016 api-core 半边与 nop-commons 本体同提交；C3-004 接入提交显式声明与 C3-016 提交的依赖关系；`TestPeriodicTaskInsulation` 9 条 + `TestThreadPoolFacilityHardening` 7 条全绿随提交门禁——三提交 `3f76e2a5b2`→`11c1124ee3`→`c1a1491e99`，pre-commit lint 三提交 0 violations
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 2.1 五成员（C2-016/C3-004/C3-005/C3-007/C3-016）landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C3-016 双层同 diff（git 可证：`3f76e2a5b2` 单提交含 FutureHelper 三半边 + ExecutorHelper/LimitedExecutor 本体）
- [x] 提交顺序机械核验（R1）：C3-016 双层修复提交在 git 序列上先于 C3-004 接入提交（`git log` 可证：`3f76e2a5b2` → `11c1124ee3` → `c1a1491e99`，裁定②顺序约束）
- [x] 必红证据（E2）：至少 C3-016（误取消红）、C3-004（取消不传播红）、C2-016（窗口挂死红）、C3-007（许可虚增红）、C3-005（Error 悬挂红）五项有先红后绿或变异/回退论证留痕——红证 `_tmp/phase2-red-c316-c305-c007.log`（isCancelled()==true 复现/许可 1→2/Error 逃逸悬挂）、`_tmp/phase2-red-c2016-root.log`（syncGet 挂死 + 残留可观测红）、`_tmp/phase2-red-c3004.log`（取消后任务照常投递/执行 5 红）；绿证 `_tmp/phase2-green-c316-c305-c007.log`/`_tmp/phase2-green-c2016-root.log`/`_tmp/phase2-green-c3004.log`
- [x] SPI 回归面执行登记（零变更声明或全实现验证记录）——零接口签名变更成立（git diff 无接口文件），`TestApiCoreSpiSurface` 签名钉 + `_tmp/phase2-spi-enumeration.log` 重枚举登记 + 两模块全量门禁承载
- [x] 裁定③平台根因半边 landed（R1）：BaseContextProvider.currentContext isClosed 守卫 + BaseContext.close 对称 detach（形态按裁定③=条件 detach 维持，javadoc 钉死）+ 断言（nop-api-core diff `11c1124ee3` 与 TestBaseContextClosedResidueGuard 4 条可证）
- [x] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS（17 模块全 SUCCESS；绝缘 `TestPeriodicTaskInsulation` 9/9 + 池设施 `TestThreadPoolFacilityHardening` 7/7 门禁全绿登记于 `_tmp/phase2-nop-kernel-gate.log` 与 `_tmp/phase2-gates-insulation-facility.log`）
- [x] Owner doc 裁定：api-core 锚点内容定稿归 Phase 4 统一落账（本 Phase 显式登记该去向——本批改变 live baseline，登记去向=Phase 4 为唯一形态）
- [x] `ai-dev/logs/` 对应日期条目已更新

Phase 2 执行记录（2026-10-24，实现子代理）：

1. **提交序列（机械核验可证）**：`3f76e2a5b2`（C3-016 双层同提交：FutureHelper bindCancel 三态判别/completeAfterTask catch(Throwable)/executeWithThrottling 幻影 release 收口 + ExecutorHelper 三处清中断/throttleExecute catch(Throwable) + LimitedExecutor acquire 作用域）→ `11c1124ee3`（C2-016 syncGet endProcessIfIdle 复用 + BaseContextProvider.currentContext isClosed 守卫 + BaseContext.close 契约钉 + SPI 面钉测试）→ `c1a1491e99`（C3-004 BindScheduledExecutor 接入 + executeOn isDone 守卫）。pre-commit lint 三提交 0 violations。
2. **测试交付**：新增 6 个测试类 35 条——TestFutureHelperBindCancelContract 12 + TestExecutorHelperCancelContract 7 + TestLimitedExecutorPermitScope 3 + TestContextTaskQueueSyncGetWindow 3 + TestBaseContextClosedResidueGuard 4 + TestApiCoreSpiSurface 5 + TestBindScheduledExecutorCancelPropagation 8（含 2 条 rule 23 真实链接线）。先红后绿五工单全覆盖（红证路径见 Exit Criteria 行）。
3. **执行注记（C3-016 观测面）**：TPE/STPE 载体经 getTask（AQS lockInterruptibly 首检中断）吸收残留中断——审计已载明该载体事实；故判别面取「底层 FutureTask isCancelled 状态」（审计手法），worker 无中断残留为绿态断言；中断残留清除代码路径由 submit/schedule 成功路径零 cancel 注入（无注入即无残留）承载闭合。
4. **执行注记（close 形态）**：裁定③终裁 close 维持条件 detach（不引入 attach 线程登记），故 close 零行为变化、以 javadoc 钉死契约；「对称 detach」的落地半边 = currentContext isClosed 读点守卫（残留不可观测），断言覆盖同线程对称清除与跨线程残留两形态。
5. **零触碰**：产品 diff = 6 个文件（FutureHelper/ContextTaskQueue/BaseContext/BaseContextProvider/ExecutorHelper/LimitedExecutor/BindScheduledExecutor 中的 6 改 + 0 新增生产文件）；绝缘四载体与 R1-02 池设施语义面 `git diff fe54cea14f..HEAD` 零命中；无 `_` 生成物、无 orm.xml、无 nop-auth 触碰。
6. **偏差**：无设计偏差。执行层适配两处（已在上两条注记说明）：C3-016 残留判别面取底层 FutureTask 终态；close 形态按裁定③零改码仅契约钉死。

### Phase 3 - 消费方站点修复：2.3 六条 + 4.3 两条 + 3.2 一条 + 4.1-CL-024 注记

Status: completed
Targets: `nop-service-framework`（nop-graphql-core/nop-graphql-grpc/nop-graphql-message/nop-biz/nop-biz-file-core）、`nop-network`（nop-rpc-api/nop-rpc-core/nop-http-client-jdk/nop-http-client-okhttp）、`nop-ai/nop-ai-toolkit`、各模块 `src/test/`、cra-index 台账（CL-024 注记）

> 续作纪律（沿 plan 2498 Phase 4 先例，R1 增补）：Phase 3 分三个子分片——**子分片 A** = 2.3 六条（C3-002+C3-015 / C3-007 / C3-008 / C3-012 / C3-017）、**子分片 B** = 4.3 两条（C3-007 / C3-012）、**子分片 C** = 3.2 一条（C4-008）+ 端到端验证（4.1-CL-024 台账注记闭合随任一子分片提交顺带完成，不单独构成子分片）——每子分片独立提交（commit message 带对应工单 ID 与 plan 号）并即时勾选对应 checklist 项与 Exit Criteria，不跨分片积压未勾选项；分会话续作以本 plan 的勾选状态为交接事实源，不依赖会话记忆。三个子分片均以 Phase 2 产物（bindCancel 修复 + 平台根因半边）落地为前置。

- Item Types: `Fix`（2.3-C3-002/C3-007/C3-008/C3-012/C3-015/C3-017、4.3-C3-007/C3-012、3.2-C4-008 全部为已确认 live defect）、`Proof`（回归测试）、`Follow-up`（4.1-CL-024 注记闭合——归属裁定非缺陷改码）

- [x] 2.3-C3-002 + 2.3-C3-015（A4 同批同验收）：出口泵/递归泵/fetcher 层按裁定③统一挂接 thenOnContext；C10-001 回归网随本体（context 敏感型 loader 断言覆盖两案）
- [x] 2.3-C3-007：callActionAsync 受管提交改造（按裁定③形态）+ proceedAsync fail-fast；双分支（executeForPartition/execute）同批闭合
- [x] 2.3-C3-008：TccContextInvoker 入口 fail-fast + 恢复逻辑改捕获 context 对象写回（与 C3-007 联动验证）
- [x] 2.3-C3-012：五入口（gRPC/quarkus WS/spring WS[第五入口，R1 补录]/消息/文件）按裁定③统一 binder 入口（第五入口归属随裁定③：内置统一接入形态自动覆盖+补 spring-WS 入口断言；新入口形态则 spring 站点显式移交登记，见成员 8）；IMessageSubscriber 解包契约注记
- [x] 2.3-C3-017：buildRpcResponse:670 locale 读点改 context 优先 + WS/订阅同步段显式 context（与 C3-012 同修）；平台根因面随裁定③在 Phase 2 落地
- [x] 4.3-C3-007（收窄至 nop-rpc-api 侧，R1）：ContextBinder 对 isClosed 残留强制替换止血 + ApiContextInterceptor 同步抛保护（C3-006 面）随单；平台根因半边（BaseContextProvider.currentContext isClosed 守卫 + BaseContext.close 对称 detach）由 Phase 2 checklist 项承载落地，本项只做 nop-rpc-api 链路消费验证（isClosed 替换断言在 rpc 链生效；发起线程 detach 协议/thenOnContext 收敛按裁定③形态消费验证）
- [x] 4.3-C3-012：jdk×4/okhttp×3/MessageRpcClient/CancellableRpcClient 全部改用 bindCancelToken 单点收敛；C10-010 测试随本体
- [x] 3.2-C4-008：排队取消联动 + executeNow isCancelled 短路 + CL-012 注册前补触发（同 diff）+ CL-017 对账面随单；C10-014 取消矩阵随本体
- [x] 4.1-CL-024 注记闭合：按裁定③结论回填 cra-index/CL 台账（归属 R1-04 + 处置结论 + 如需修复的 successor 指针）
- [x] 每成员回归测试（rule 25）：2.3-C10-001 传播网 / 租户快照传播断言（C3-007）/ TCC 无残留断言（C3-008）/ 入口 binder 断言（C3-012）/ locale 读点断言（C3-017）/ isClosed 替换断言（4.3-C3-007）/ appendOnCancel 站点清理断言（4.3-C3-012）/ 取消假确认消除断言（C4-008）——测试名可辨识工单
- [x] **端到端验证**（rule 22）：≥1 条从请求入口到 ORM 批量加载的完整链路测试（异步 biz action + 嵌套 ORM 字段组合：入口 context → worker 执行 → 出口泵挂接 → requireSession 正确会话），覆盖 C3-002/C3-007 修复组合；候选宿主模块（R1）：nop-graphql-orm 或 nop-biz 测试树，执行时按既有同类测试选址登记
- [x] **接线验证**（rule 23）：出口泵挂接在真实 GraphQLExecutor 链上断言（dispatch/load 执行线程可观测为 context 载体线程）
- [x] 站点行号 live 回读核对登记（勘误 5/9 所列执行时回读项）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 9 项成员工单全部 landed 且各带 ≥1 条 focused 回归测试（2.3 六 + 4.3 二 + 3.2 一）；C3-002 与 C3-015 同批验收记录在案（A4）
- [x] 必红证据（E2）：至少 C3-002（错会话/NOT_IN_SESSION 红）、C3-007（租户残留红）、4.3-C3-012（监听器累积红）、C4-008（取消假确认红）有先红后绿或变异/回退论证留痕；其余工单逐条可复现口径执行
- [x] **无静默跳过**（rule 24，R1）：本 Phase 新增 fail-fast/短路分支（C3-007 proceedAsync fail-fast、C3-008 入口 fail-fast、C4-008 executeNow isCancelled 短路等）显式失败非静默返回，逐条可指认对应测试或代码路径
- [x] 4.1-CL-024 注记闭合登记（台账回填可核验）
- [x] `./mvnw test -pl nop-service-framework -am`、`./mvnw test -pl nop-network -am`、`./mvnw test -pl nop-ai -am` BUILD SUCCESS
- [x] Owner doc 裁定：触达模块 03-modules 条目核对（nop-ai.md 等）受影响面更新或 `No owner-doc update required` + 理由；缺口声明归 Phase 4 统一
- [x] `ai-dev/logs/` 对应日期条目已更新

Phase 3 执行记录（2026-10-24，实现子代理，三子分片交付）：

1. **提交序列（三子分片独立提交）**：子分片 A `c68b69737e`（2.3 六条：出口泵/递归泵/fetcher 窗口挂接 + BizActionService 受管提交 + proceedAsync fail-fast + TccContextInvoker fail-fast/写回 + newRpcContext 内置 binder + locale 读点 context 优先 + spring-WS 入口断言 + IMessageSubscriber 解包契约注记）→ 子分片 B `44e633da72`（4.3 两条：九站点 bindCancelToken 收敛[jdk×4/okhttp×3/MessageRpcClient/CancellableRpcClient] + ContextBinder isClosed 强制替换 + ApiContextInterceptor 同步抛保护[C3-006 随单]）→ 子分片 C `276618d9da`（C4-008 三面 + CL-012 注册前补触发[api-core 同 diff] + C10-014 wrapper 传播断言 + rule 22 端到端）。pre-commit lint：子分片 A/B/C 均过（B 首次被 nop/no-log-getmessage 拦截一次，测试断言改写后过）。
2. **测试交付（三子分片合计 36 条新增/扩展，先红后绿全登记）**：子分片 A=20（TestGraphQLExecutorContextPump_C3_002 3 + TestFetcherThenOnContext_C3_015 3 + TestNewRpcContextBinder_C3_012 4 + TestTccContextInvokerFailFast_C3_008 5 + TestBizActionServiceManagedSubmit_C3_007 3 + TestSpringWsEntryContextBinding_C3_012 2）；子分片 B=9（TestRpcClientCancelListenerConvergence_C3_012 2 + TestMessageRpcClientCancelListenerConvergence_C3_012 1 + TestJdkHttpClientCancelListenerCleanup_C3_012 1 + TestOkHttpCancelListenerCleanup_C3_012 1 + TestContextBinderClosedResidue_C3_007_43 4）；子分片 C=7（TestToolManagerCancelChain_C4_008 4 + TestFutureHelperBindCancelContract 扩展 2[NoCompensateToken 注册前补触发双重载] + TestAsyncBizActionOrmChain_P3_E2E 1）。
3. **必红证据（E2 全覆盖，`_tmp/phase3-red-*.log`）**：C3-002=2 红（loader 观测 context=null 于 FJ 完成线程）；C3-015=2 红（transformer 观测 null，注册后外部线程交付形态消竞态）；C3-007=3 红（租户传播 null + worker 残留可观测 + fail-fast NPE 形态）；C3-008=1 红（无线附着静默 setCurrent 无 fail-fast）；C3-012=3 红（binder 不落盘/不复初始化 + 失败路径不回收）；C3-017=1 红（EN/ZH 消息判别——线程残留 zh-CN 覆盖 context en-US）；4.3-C3-012=4 红（监听器累积：rpc-core×2+jdk+okhttp，round 计数判别）；4.3-C3-007=2 红（closed 残留复用 RawProvider 复现 + 同步抛泄漏）；C4-008=2 红（token 已取消仍派生执行 + 取消假确认）；CL-012=2 红（无补发 token 永不触发）；E2E=1 红（`nop.err.orm.not-in-session: batch loader ran without the request-bound ORM session`，ormBatchFetch/requireSession 均在 e2e-biz-worker 且 holder=null）。绿证：各模块 focused 复跑 + 三大 -am 门禁。
4. **实现形态偏差注记（非设计偏离，登记）**：裁定③形态 A「泵级 ContextProvider.thenOnContext 挂接」的实现形态 = **`context.execute` 窗口承载**（出口泵/回合出口泵/fetcher 层统一）。依据：`thenOnContext(future,ctx).thenCompose(walk)` 组合在「上游已完成」路径存在窗口逃逸——thenOnContext0 的内联 flush 窗口先于 thenCompose 注册关闭（runOnContext 的 finally 已恢复线程附着），walk 内联落回注册线程的空/残留 context（E2E 红证实测：ormBatchFetch/requireSession 落 worker 且 holder=null）。`context.execute` 承载使游走+dispatchAll 在任意上游状态下都处于请求 context 执行窗口内，与 thenOnContext 同族原语、语义（回调+续行统一在请求 context 上）完整达成；递归泵保留 thenOnContext（挂接点在批次 promise 创建处立即注册 thenAccept，同步/异步两种完成时序均不逃逸窗口）。registry 读点（OrmSessionRegistry.instance 经 getOrCreateContext）在窗口内命中入口请求 registry 为该形态的运行时实证。
5. **CL-024 注记文本（供 Phase 5 落账 cra-index/CL 台账）**：「4.1-CL-024（Local 红投 LocalMessageService.scheduleRedelivery 跨线程未传递发送者 IContext）：归属 R1-04（plan 2499，裁定③ 4.1-CL-024 归属裁定）。处置结论 = 维持现状、无需改码——Local 发送者→scheduler 异步边界不传递发送者 IContext；理由 = broker 两后端（Kafka/Pulsar）消费线程同样无 context 是消费模型整体既定形态，Local 单点传递将形成『Local 有 context、broker 无』的后端间语义分叉（发现自述禁止项）；消费侧所需上下文按裁定③入口统一口径由消费入口按投递轮次从消息头显式构造（同 C3-012 消息入口形态）。无 successor 工单；注记闭合。」
6. **CL-017/C10-014 取消能力矩阵登记（live 重枚举 @ 本 Phase，`implements IToolExecutor` = 33 文件[20+ 执行器 + 1 provider + 抽象基类]，登记口径差：审计口径 17 vs live 33 文件——以 live 为准）**：档位划分——(a) **token 消费档（可中断）**：JqToolExecutor（唯一 cancelToken 消费者，appendOnCancel+removeOnCancel 配对在位）；(b) **不可取消档（不读 token，CL-017 对账面，不做隐式承诺）**：fs 族全部（ReadFile/WriteFile/CopyFile/MoveFile/DeleteFile/CreateDirectory/PatchFile/ApplyDeltaExecutor/SearchFiles/SearchContent/ListDirectory/SetActiveTags/ReadSpill/ReadRef/AskOracle/ApplyDelta 等本模块文件/目录面执行器）+ 团队/技能/任务面（TeamExecuteFlow/TeamSendMessage/TeamStatus/TeamTaskCreate/TeamTaskUpdate/SendMessage/Skill/TaskOutput/TaskStop/UpdateTodos/CallAgent/GraphqlQuery/HttpRequest/SearchEngine）；(c) **wrapper 传播档**：经 ToolManagerImpl 返回 future 的取消 → 排队短路/在途源 future cancel(true)（C10-014 wrapper 传播断言 = TestToolManagerCancelChain_C4_008 三取消面 + 零回归对照）。fs 族 token 接入评估不做隐式承诺，successor 归消费线程上下文专题/R2 后续批次对账（设计 §3.6 既定）。
7. **rule 24 fail-fast/短路清单（逐条可指认）**：BizActionInvocation.proceedAsync 无线附着 → ERR_BIZ_ASYNC_INVOCATION_NO_CONTEXT（testProceedAsyncFailsFastWithoutBoundContext）；TccContextInvoker.invokeAsync 无线附着 → ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT（testInvokeAsyncWithoutBoundContextFailsFast）；ToolManagerImpl executeNow token 已取消 → CancellationException 显式终态（testCancelledContextTokenShortCircuitsExecution）；泵挂接 context.execute 抛出（context 已关闭等）→ promise.completeExceptionally（代码路径，套件绿承载）；newRpcContext 入口装配失败 → 自建 context 立即 close + 异常传播（testBinderClosesCreatedContextWhenEntrySetupFails）；ApiContextInterceptor 同步抛 → binder.close + 异常传播（testInterceptorSyncThrowClosesBinderAndPropagates）。
8. **站点行号 live 回读登记（勘误 5/9 项）**：JdkHttpClient :169/:381/:545/:582 与勘误 5 一致（+1~+3 漂移内）；OkHttpClientImpl :64/:155/:329 一致；MessageRpcClient :104（appendOnCancel 区间 :102-108）；CancellableRpcClient :54；TccContextInvoker :30-31（无参 thenOnContext + whenComplete 完成线程恢复）一致；ContextBinder :23-49（created=false 不复初始化）一致；ApiContextInterceptor :27-33 一致；GraphQLExecutor 出口泵 :134-148/thenFetchNext :151-158/dispatchAll :200 一致；GraphQLEngine currentLocale :670（buildRpcResponse）+ 同源读点 :641（buildGraphQLResponse，同 diff 顺带闭合）；ToolManagerImpl 排队分支 :101-123（plan 2298 注释插入致 +1 漂移，登记）；OrmBatchLoader load 体 :31-39。**PollingRpcClient 对照样本回读（Non-Blocking Follow-up）**：:121-125 手动 remove 形态 intact，plan 2498 改造未破坏。
9. **门禁**：`./mvnw test -pl nop-service-framework -am` BUILD SUCCESS（19 模块，TestGraphQLDataLoaderDispatchLifecycle 4/4 + TestGraphQLExecutorFetchNext 2/2 共享代码位零回退；快照 `_tmp/phase3-gate-svcframework.log`）；`./mvnw test -pl nop-network -am` BUILD SUCCESS（34 模块；`_tmp/phase3-gate-network.log`）；`./mvnw test -pl nop-ai -am` BUILD SUCCESS（166 模块，TestToolPathQueue 4/4 + ToolManagerImplParallelTest 5/5 零回退；`_tmp/phase3-gate-ai.log`）；nop-api-core 全量 191/191（含 Phase 2 契约测试共存）；E2E 宿主通道 `-pl nop-graphql-orm -am` BUILD SUCCESS（`_tmp/phase3-green-e2e.log`）。零触碰：`model/*.orm.xml`、nop-auth、`_` 生成物、nop-tcc 零命中；Phase 2 产物语义（bindCancel 守卫/currentContext 守卫/close 契约）零回改（FutureHelper 仅新增 bindCancelToken 注册前自检分支，既有守卫逻辑逐字未动）。
10. **Owner doc 裁定**：`No owner-doc update required`（本 Phase）——触达模块 03-modules 条目核对：nop-ai.md（ToolManagerImpl 取消链面）/service-layer.md（异步 biz action 上下文传播契约）受影响面更新归 Phase 4 统一落账；nop-rpc-api/nop-rpc-core/nop-http-client-* 无 03-modules 条目，缺口声明归 Phase 4 R-G 轨登记。**注记**：plan 回填纪律偏差一处——子分片 A/B/C 提交即时完成，checklist/Exit 勾选与执行记录为三子分片完成后一次性回填（分会话中断风险下的批次回填，勾选事实与本记录以 live 代码+git 序列可证）。

### Phase 4 - 消费方回归面全列执行 + 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/service-layer.md`；触达模块 owner doc 归属核对

- Item Types: `Proof`

- [ ] **消费方回归面全列执行（exit criteria 第一支柱义务，逐条留证）**：1.6 `./mvnw test -pl nop-retry -am`（含 R1-01 交付回归 + bindCancel 修复针对性断言）；4.3 `./mvnw test -pl nop-network -am`；3.4 `./mvnw test -pl nop-ai -am`——三者 BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）
- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-kernel -am`、`./mvnw test -pl nop-service-framework -am`、`./mvnw test -pl nop-network -am`、`./mvnw test -pl nop-ai -am` BUILD SUCCESS 且登记；实际触达模块（勘误 1 清单中实际产生 diff 者，含 nop-quarkus/nop-spring/nop-frontend-support 若有——nop-spring 触达形态见成员 8 归属裁定）focused 测试逐条登记
- [ ] 零触碰确认：本批产品 diff 不含 `model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记）；R1-01 绝缘四载体 + R1-02 池设施语义面零回改复核
- [ ] Owner doc 更新：`source-anchors.md` **新建** api-core 取消/上下文原语锚点条目（bindCancel 契约、bindCancelToken 单点、thenOnContext/executeWithContext 治理口径、syncGet 语义——内容源 = Phase 1 设计文档裁定，派生摘要标注「以设计文档为准」，按边界规则不直接链接 ai-dev 路径，沿 INSUL/EXEC 先例）；`service-layer.md`（异步 biz action 上下文传播契约：受管提交形态 + 出口泵 thenOnContext 纪律）
- [ ] 缺口声明：触达模块中无 `docs-for-ai/03-modules/` 条目者按 R-G 治理轨先例登记，不在本批新建 owner doc
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（受影响模块全集与 Closure Gates 一致）退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 消费方回归面（1.6/4.3/3.4）+ 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] source-anchors api-core 锚点落地 + service-layer.md 更新（或 No owner-doc update required 声明）；check-doc-links --strict 退出码 0
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.5 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（bindCancel 新守卫/挂接链运行时连通、无空方法体/静默吞异常）与 SPI 回归面执行复核
- [ ] cra-index 台账回填：P1 台账 2 行（2.3-C3-002/C3-007）+ P2 裁决台账 G26 五成员/G37 三成员/G39 一成员/G55 拆分成员（C4-008）/G68 两成员状态收敛（open → 已修/本批）；4.1-CL-024 注记终态复核
- [ ] roadmap 8.5 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2499-cra-phase8.5-r1-04-apicore-bindcancel-context.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 14 成员行状态全部收敛、4.1-CL-024 注记闭合、roadmap 8.5 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 14 项成员全部修复：2.3-C3-002/C3-007、2.1-C2-016/C3-004/C3-005/C3-007/C3-016、2.3-C3-012/C3-015/C3-017/C3-008、4.3-C3-007/C3-012、3.2-C4-008（CL-012/CL-017 随单闭合）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；关键项有先红后绿或变异/回退论证（E2：C3-016/C3-004/C2-016/C3-007/C3-005/C3-002/C3-007[2.3]/4.3-C3-012/C4-008）
- [ ] bindCancel 单点改造成立且服务面闭合：2.1 三条（C3-016/C3-004 本批修复 + C6-001 受益面无障碍声明）+ 消费方 1.6/4.3/3.4 回归面全列执行留证（E3）
- [ ] executeWithContext/thenOnContext 统一落地（出口泵补齐：invokeOperation/递归泵/fetcher 层 + 入口统一 + 平台根因 detach/isClosed 守卫）
- [ ] 4.1-CL-024 归属本族注记闭合（台账可核验）
- [ ] C10 验收前置随本体落地：C10-009、2.3-C10-001、4.3-C10-010、3.2-C10-014；C10 其余涉及面确认未越界承接
- [ ] R1-01/R1-02 产物边界零回改实证：绝缘 9 条 + 池设施 7 条门禁全绿，绝缘四载体 diff 零改动，EXEC-001/002 语义面零触碰
- [ ] SPI 回归面义务履行：零接口变更声明在案，或全部实现类（含重枚举 mock）回归验证登记（E4 授权声明配套）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 + 消费方回归面命令全部 BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：source-anchors.md（api-core 新建锚点）、service-layer.md（上下文传播契约）；缺口声明登记（E5）
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/下游项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）修复后的取消传播/context 挂接路径运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2499-cra-phase8.5-r1-04-apicore-bindcancel-context.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 14 成员行收敛 + roadmap 8.5 → done（E6）

## Deferred But Adjudicated

### 2.1-C6-001（销毁链族——bindCancel 单点服务第三条，反向传播终态化）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 非本批 14 成员（batch-proposal R1-04 清单不含）；M12 裁定其 api-core 层由 bindCancel 单点改造承载，本批 Phase 2 落地后受益面即闭合，其销毁链本体工单（record 停滞族）归 R4-14 承接。
- Successor Required: `yes`
- Successor Path: roadmap 8.54（R4-14，G27 后半）；cra-index `2.1-C6-001` 行注记

### 2.1-C3-018（DefaultTaskExecutionQueue queueTask bindCancel 参数反接）

- Classification: `moved to explicit successor ownership`（R1 勘正：已确认 live defect 不得降级为 improvement）
- Why Not Blocking Closure: 独立 P2 工单，批次归属 R4-13（G27 前半）；本批 bindCancel 守卫语义变更后其修复须按新契约复核——裁定①兼容矩阵已含该调用方行为影响声明，复核义务随 R4-13。
- Successor Required: `yes`
- Successor Path: roadmap 8.53（R4-13）；cra-index `2.1-C3-018` 行注记

### 4.4-MA-05（4.4 上下文家族成员——家族修复归 2.3 轨道）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: family-clustering R15 行明载其批次归属 = R4-28（G70）；本批裁定③产出 context 原语统一口径后，R4-28 按同口径落地（roadmap R4-28 行已有软依赖标注）。
- Successor Required: `yes`
- Successor Path: roadmap 8.68（R4-28）；family-clustering §三 R15 行

### 4.1-CL-024 LocalMessageService 改码（若裁定③判需修复——修复面本体）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 本批义务 = 归属裁定 + 台账注记闭合（exit criteria 第三支柱原文「归属本族注记闭合」）；CL-024 自述「单点修会造成后端间语义分叉」，4.1 审计移交时即指定专题统一裁定——裁定③给出结论后，如判需传递，实际改码归 4.1 归属批次（与 Kafka/Pulsar 消费线程既定形态的对齐面同批），防后端语义分叉。
- Successor Required: `yes`（仅当裁定③判需修复）
- Successor Path: cra-index `4.1-CL-024` 行（Phase 3 回填 successor 指针）；4.1 归属批次以 cra-index 裁定批次列为准

## Non-Blocking Follow-ups

- **bindCancel 全库调用方行为复核**：裁定①兼容矩阵之外的隐性依赖（如测试或上层对「误 cancel 假终态」的观察断言）——修复后如有测试依赖旧错误行为，随批改写并在执行记录登记（不预设立项）。
- **2.3-C3-013（ContextBinder no-op 死代码）与入口统一治理的后续评估**：C3-012 落地后 ContextBinder 死代码面是否收敛/激活归 R2-05 或 R4-16 对账，本批只保证入口统一口径不自相矛盾。
- **PollingRpcClient 对照样本回读**（plan 2498 改造后）：4.3-C3-012 修复时确认其手动 remove 形态未被 deadline offload 改动破坏；如有漂移登记勘误。
- **nop-web（C3-005 使用面）focused 回归**：throttleExecute 修复后 PageProvider/WebPageExporter 3 调用点行为面如需专项验证，随 Phase 4 实际触达登记（聚合门禁覆盖为主通道）。

## Closure

Status Note: （closure 时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （closure audit 时填写）
- Audit Session: —
- Evidence: （每条 Exit Criterion 与 Closure Gate 的验证结果，含 Anti-Hollow 与 SPI 回归面复核）

Follow-up:

- （closure 时填写，或明确写 no remaining plan-owned work）
