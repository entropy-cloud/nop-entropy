# 2498 CRA Phase 8.4 — R1-03 平台池消费方批（R13 使用方：隔离/卸载落地，9 项）

> Plan Status: active
> Draft Review: R1 agent_30f87b02（0 Blocker、3 Major[M1 A4 无仓内装配面改写/M2 warn-only 半修出口删除/M3 roadmap 8.61 假事实修正]+5 Minor——修订 agent_eb08b971 全量应用；m5 勘正经三重 live 验证驳回[R1 锚点有误，:204 为正确值]，裁决记录在修订报告）
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 8.4（R1-03，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L146，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-03 行（L64：finding ID 清单 9 项 / PA=无（impl 叶）/ 依赖=硬依赖 R1-02 / exit criteria 增补）；**设计传导基准 = `ai-dev/design/concurrency/pool-facility-hardening.md` §3.1.3（R1-03 消费方同口径传导声明，九工单逐单口径——本批的实现契约）+ §3.1.2 处置矩阵 A4/A5/D3/D4 行（移交承接面）+ §3.1.4 站点侧接入同口径**；成员发现详情 = 各单元审计目录（见 Current Baseline 逐条回读路径）；执行基线 @ `e7388fc9b9`（分支 `concurrency-audit-remediation`，roadmap 8.1/8.2/8.3 已 done，plan 2495/2496/2497 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2497（R1-02 平台池设施面批，completed——**硬依赖已解除**：本批消费方接入依赖的设施件全部在位：①`IScheduledExecutor.executeOn(Executor)` 卸载载体（`IScheduledExecutor.java:24` default 方法，nop-config 两站点已用）；②新配置键 `nop.commons.concurrent.global-timer.corePoolSize`（默认 4）与 `nop.commons.concurrent.cached-pool.maxPoolSize`（默认 200）；③池工厂 fail-fast 校验（`nop.err.commons.concurrent.invalid-thread-pool-param`，poolSize ≥ 1 / queueSize ≥ 1 / core ≤ max）；④cached 池达界熔断信号（REE + `cached-pool-saturated` 错误键））；plan 2496（R1-01 绝缘层批，completed——JsonRpcWebSocketHandler sendKeepAlive 链与 Bind 载体已受绝缘覆盖，本批占用面改造**不得回退绝缘覆盖**）；plan 2495（R1-00）/ plan 2493/2494（批次划分与 Phase 8 展开）；**下游交互**：R2-16（软依赖 R1-02/R1-03——globalTimer 约束解除链）、R4-21（MA-16「不得指向 globalTimer」约束在本批落地后解除，解除声明归本批注记）、R1-08（1.2-C3-014/MA-07 与本批成员 1 同文件续作——本批专用载体为其 shutdownNow 形态的载体前提，边界见 Baseline 勘误 4）、R4-16/R4-17（2.3 WS/网关束——适配器层写超时同族面 successor）
> Protected Area 与授权：本批 **PA 触碰面为零（batch-proposal PA 列 = 无，impl 叶）**——无 plan-first/ask-first 义务；**设计传导口径（pool-facility-hardening.md §3.1.3）仍为实现契约**，九工单修复不得各走一套。零触碰声明（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef` 为源 schema 模型（非 `_` 生成物），本批对其修改限于 `@concurrency` 头注释声明文本与既有属性语义内（声明失真是经复核裁定的产品缺陷随修面，见成员 7）。

## Purpose

关闭 R13 平台池使用方面的 9 项已确认发现（batch-proposal §二.1 R1-03 行：P1×4——1.2-C4-004[MA-06]、1.5-C4-001[MA-04]、2.3-C8-001、3.1-C4-011；P2×5——1.7-C8-003[G22]、3.1-C4-004/3.1-C4-009[G51]、3.2-C4-004[G55]、4.2-C4-003[G66]）：①长驻/阻塞任务体全部移出平台共享载体（Debezium 引擎移出 globalWorker、task retry/delay 业务体与 WS 推送/keepalive 移出 globalTimer、AgentDispatchExecutor 与 SingleTurnExecutor 移出 commonPool、rpc-cluster 轮询链移出 timer 线程）；②移交承接面全处置（设计矩阵 A4 ScheduledRecoveryManager、A5 keepalive 合并、D3/D4 ClusterRpc 两站点、plan 2497 Deferred 的 SocketServer 迁移评估）——禁止静默缺处置；③batch 兜底 cached 池在裁定④熔断基线上完成平台级统一评估 + 装配期护栏 + xdef 声明失真修正。批末 roadmap 8.4 → done，9 项成员台账全部收敛，MA-16（R4-21）约束解除声明落账。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-03 = 9 项成员（P1×4 + P2×5）；cra-index P1 台账 4 行（L149 `C4-004（MA-06）`、L164 `C4-001（MA-04，成员 C4-008/C3-013）`、L210 `nop-service-framework-hotpath-C8-001`、L249 `nop-ai-core-nop-ai-agent-C4-011`，裁定批次均=R1-03、状态均=open）+ P2 裁决台账 4 组行（G22[L388]/G51[L417]/G55[L421]/G66[L432] 的 R1-03 成分）；roadmap 8.4 行 Status=todo。
- 前置：硬依赖 R1-02（plan 2497）已 completed @ `e7388fc9b9`——先平台后使用方的排序前置已满足；plan 2497 closure 收敛注（cra-index L366）明文「P2-G22/G66 的 R1-02 设施面成分已由本批闭合，使用方成分维持 R1-03 批次；设计 §3.1.6 移交注记（ScheduledRecoveryManager→R1-03）按矩阵 A4 行落位」。
- **设计传导契约在位**：`pool-facility-hardening.md` §3.1.3 九工单逐单传导表 + §3.1.1 平台族契约（调度槽 vs 执行体；强制卸载形态 = `globalTimer().executeOn(globalWorker())`；专用载体例外判定标准 = 载体有独立容量预算与关闭归属且不经 globalTimer/globalWorker 执行任务体；豁免判据四条 (a)(b)(c)(d)）+ §3.1.2 矩阵 A4/A5/D3/D4 移交行。本批全部修复按该契约落地，不得各走一套。
- R1-01 绝缘覆盖在位（plan 2496）：`JsonRpcWebSocketHandler.sendKeepAlive` 周期链已受绝缘（live :360 绝缘注释在位）、`executeOn` 的 Bind 载体周期方法路由绝缘核心（30 点表 #10–13）——本批占用面改造**不得回退**链存活覆盖；`TestPeriodicTaskInsulation` 9 条与各下游绝缘回归为共享载体改动的回归门禁。

### 9 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `e7388fc9b9` 回读值，执行前如再漂移以 live 为准并登记；漂移成因见勘误 3）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向（设计 §3.1.3 传导口径） |
|---|---|---|---|---|---|
| 1 | 1.2-C4-004（P1，MA-06 族主；cra-index L149） | 1.2 / nop-message（wrapper 本体）+ nop-stream（connector 使用链） | `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:160`（`GlobalExecutors.globalWorker().execute(() -> runEngineWorker(engine))`——审计载 :159 内联 lambda，plan 2496 4.1-C3-009 改造后漂移）；in-scope 使用链锚点 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceReader.java:87,92-106`（launchEngine→嵌入函数）与 `DebeziumCdcSourceFunction.java`（subscribe→引擎启动） | Debezium 嵌入引擎以无限阻塞任务常驻平台共享 nop-global-worker（core=4/max=30/queue=10000）：每 CDC subtask 永久钉死 1 条共享线程，任务隔离失效、影响半径为全平台异步面 | 引擎 = 长任务体，须专用载体（§3.1.3 行 1：契约对 globalWorker 侧同构适用）：per-engine 专用单线程执行器（平台 `NamedThreadFactory` daemon 命名形态）+ stop 关闭路径有界终结预算（shutdown + awaitTermination 上界，衔接既有 close 链，不引入无界等待）；**心跳/超时预算验收面 = ①引擎线程专用命名可辨识（线程 dump 与共享 worker 可区分）②stop 有界终结**；stop 终结 latch/启动守卫（C4-005/C4-006 已并入 1.2-C3-014/MA-07）归 R1-08（勘误 4 边界） |
| 2 | 1.5-C4-001（P1，MA-04 族主，成员 C4-008/C3-013；cra-index L164） | 1.5 / nop-task（nop-task-core） | `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:269-270`（`getScheduledExecutor().schedule(action, delay)`——action=步骤体）；`step/RetryTaskStepWrapper.java:29`（`getTaskStep().execute(stepRt)`）；`impl/TaskFlowManagerImpl.java:77-79`（未注入恒回退 `GlobalExecutors.globalTimer()`）；簿记对照站点 `step/DelayTaskStep.java:39-41`、`step/TimeoutTaskStepWrapper.java:25`（C4-round3 矩阵 S1/S2 轻量簿记类，非缺陷面） | retry 延迟重试把步骤体（DB/HTTP 业务体）与完成回调链（含 `saveState` ORM 写）直接调度到全平台共享 globalTimer 线程执行：≥4 个长体重试并发即钉死全池，且 executor 隔离配置对延迟路径无效（成员面 C4-008），全平台定时语义失准（C3-013 同池面） | 阻塞任务体强制卸载（§3.1.3 行 2 = §3.1.1 强制卸载形态）：「timer 触发 + 载体提交」等价形态——schedule 仅做定时唤醒，action 与完成回调链（含 saveState 簿记）经 `globalWorker` 或步骤显式 executor 执行（executor 隔离配置随之对延迟路径真实生效 = C4-008 行为钉；C3-013 同池面随同 diff 收敛） |
| 3 | 2.3-C8-001（P1；cra-index L210） | 2.3 / nop-service-framework（nop-graphql-core） | `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:82`（每连接 10s keepalive 落 globalTimer——N 连接=N 条周期任务）、`:469-478`（`SubscriptionSubscriber.onNext` 同步阻塞 send）、`:360-367`（`sendKeepAlive`——R1-01 绝缘注释在位、占用面未改）；`subscription/GraphQLSubscriptionManager.java:204,217`（`pushToClient` 同步 send，经发布者线程/分发线程） | WS 订阅推送同步阻塞发送：单慢客户端（TCP 零窗口）阻塞发布线程并钉死 globalTimer 全池（4 线程，钉死门槛 ρ=N·L/40——L=慢客户端单条发送延迟，无写超时） | 每连接扇出禁止直连共享池（§3.1.3 行 3；矩阵 A5 行合并本工单）：①keepalive 触发与连接生命周期解耦（扇出收敛为 O(1) 共享扫描或模块专用载体，二选一以实现期对齐设计为准绳）；②推送/keepalive 的阻塞 send 不再在 timer 线程执行（卸载共享池——ρ=N·L/40 钉死面消除）；适配器层写超时（审计建议②，`getAsyncRemote`/写超时族）不在本批（Deferred，successor=R4-16/R4-17 适配器清扫面）；绝缘覆盖不回退 |
| 4 | 3.1-C4-011（P1；cra-index L249，←CL-025 随条处置） | 3.1 / nop-ai（nop-ai-agent） | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/AgentDispatchExecutor.java:34,40,52`（三方法 `CompletableFuture.runAsync` 一参重载→commonPool）；装配点 `engine/AgentToolDispatcher.java:560`（`AgentDispatchExecutor.INSTANCE`）；阻塞消费方 = toolkit 18 文件族 `context.getExecutor().submit(...)` | 工具分发执行器缺省 ForkJoinPool.commonPool：本质阻塞的工具体（Bash `waitFor` 最长 300s、阻塞 HTTP、文件 I/O）占满 JVM 全局共享池；cap≤0 禁超时为合法配置→挂死工具体永久占线程；与同模块 DefaultAgentEngine 的 commonPool 饥饿规避自述相悖 | 站点侧接入（§3.1.3 行 4）：阻塞工具体不再落 commonPool——注入命名有界平台池（经 R1-02 校验契约内的池工厂 `ThreadPoolConfig`/`newExecutor` 构造）或复用引擎自有 agentExecutor 注入 `AgentToolDispatcher`（三方法 execute/submit×2 一并收口）；cap≤0 配置加 LOG.warn + javadoc 补阻塞占用与池宽封顶边界声明；CL-025 无界 runAsync 面随条处置注记落 cra-index |
| 5 | 3.1-C4-004（P2，G51；cra-index L417） | 3.1 / nop-ai（nop-ai-core） | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:89-142`（:89 `retryScheduler`、:123-124 `setRetryScheduler` 换装不处理旧池、:131+ 惰性建 ScheduledThreadPoolExecutor(1) 全类无 shutdown）；`src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml:12-13`（bean 无 ioc:stop/destroy-method） | 重试调度器无关闭路径：bean 未注册 stop 钩子、`setRetryScheduler` 换装后旧内部池成永久孤儿线程（`nop-ai-embedding-retry` 常驻）；在途重试静默丢弃 | 站点侧接入（§3.1.3 行 4；MA-16 解除后约束形态内修复——审计原建议「复用 globalTimer」的先决平台修复 R1-02 已落地）：私有池补生命周期闭合（stop/destroy + `shutdown`+有界 `awaitTermination` + beans.xml 注册 `ioc:stop` + `setRetryScheduler` 换装时旧池有界终结），或移除私有池改平台载体（timer 触发+载体提交等价形态——重试 dispatch 体含 LLM HTTP 阻塞面，**不得直连 globalTimer 线程执行**）；两分支验收同标：无常驻孤儿线程、换装路径旧池有界终结、任务体不常驻共享载体阻塞面 |
| 6 | 3.1-C4-009（P2，G51；cra-index L417） | 3.1 / nop-ai（nop-ai-agent） | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SingleTurnExecutor.java:42`（向后兼容构造器缺省 `ForkJoinPool.commonPool()`）、`:59`（全参构造器 null 兜底 commonPool）、`:158`（`callWithTimeout` 使用点，阻塞 `chatService.call` 最长 120s） | SingleTurnExecutor 两缺省点把阻塞 LLM HTTP 调用放 JVM 全局共享池执行；生产接线（`AgentExecutorResolver:215-216` 显式专用 executor）不受影响，暴露面=向后兼容构造器与传 null 的调用方（嵌入方/测试） | 站点侧接入（§3.1.3 行 4）：两缺省点不再静默落 commonPool——缺省载体改平台设施（`globalWorker`，nop-http `ServerEventPublisher` 既有约定）或模块专用载体（终态唯一）；LOG.warn 仅作伴随可见性手段，**warn-only 不得作为终态**（rule 16——scope 内已确认缺陷不得以告警替代修复，防假收敛）；javadoc 补缺省池语义；生产接线零变化（回归钉） |
| 7 | 1.7-C8-003（P2，G22；cra-index L388） | 1.7 / nop-batch（+ nop-xdefs 源 schema） | `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:83-87`（`getExecutor` 兜底 `GlobalExecutors.cachedThreadPool()`）、`core/BatchTaskBuilder.java:335-337`（dispatch 兜底同池）、`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:285`、`ImportDbTool.java:297`（per-table 内层兜底——审计载 :284/:296，R1-02 表级池修复致 +1）；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef` 头注释（live :2 `@concurrency` 声明「不配 executor 会使用 SyncExecutor 串行」与实现相反）；owner doc `docs-for-ai/03-modules/nop-batch.md:14,211`（沿用同一失真） | concurrency/fetchThreadCount 无上界护栏 + 缺省兜底全局 cached 池 + xdef 声明与实现相反（用户按声明误信「不配 executor=串行安全」→ 线程爆炸/共享池占用放大） | 裁定④基线内平台级统一评估（§3.1.3 行 6；设施面 2.1-C4-004 已在 R1-02 闭合——cached 池已有上界 200 + 达界 REE 熔断，G22 行注记对账防重复立项）：①兜底池形态评估落账（改有界形态 or 维持 cached + 熔断基线声明，结论写 owner doc）；②装配期护栏：concurrency/fetchThreadCount 合理上界 fail-fast（Guard 或全局配置项，随实现期定夺）；③xdef 声明失真修正（复核裁定=产品缺陷随修，非 owner-doc drift 豁免）；④nop-batch.md F3⑥ 随修义务同批落账 |
| 8 | 3.2-C4-004（P2，G55；cra-index L421） | 3.2 / nop-ai（nop-ai-shell，模块=nop-ai） | `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:58-60`（:60 缺省回退 `GlobalExecutors.globalWorker()`）、`:263-271`（pipeline 阶段 `supplyAsync(..., executor)` 落共享池） | pipeline 阶段与全平台任务共池（缺省 globalWorker）：生产/消费阶段占用线程造成负载饥饿（相邻提交使永久死锁不可达，主张为饥饿/延迟放大），无池隔离、10000 深队列使拒绝长时间不触发 | 装配点注入独立有界池（§3.1.3 行 4「模块专用载体」口径；审计建议 `newExecutor("nop-ai-shell-pipeline", n, m)`——R1-02 工厂校验契约内构造），与前台 bash 工具池、平台 worker 三者隔离；池参数对照「最大并发会话 × 最大管道段数」设定并在 owner doc 声明包络 |
| 9 | 4.2-C4-003（P2，G66；cra-index L432） | 4.2 / nop-cluster（+ 载体 nop-rpc-core） | `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcProxyFactoryBean.java:42-43`、`ClusterRpcServiceInvoker.java:67-68`（`if (timer == null) timer = GlobalExecutors.globalTimer()` 裸注入）；`ClusterRpcClient.java:147`（`new PollingRpcClient(service, pollingMethod, timer, ...)` 装配）；载体 `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/PollingRpcClient.java:94-101`（轮询体 timer 线程直跑、链无整体截止） | rpc-cluster 把裸 globalTimer 交给 PollingRpcClient：轮询任务体（全量请求体序列化等同步前缀）在 timer 线程执行，且轮询链无整体 deadline——被放弃/远端挂死的 polling 请求成不可回收常驻链，挤压全平台定时精度 | 站点侧接入（§3.1.3 行 7 = 矩阵 D3/D4 行「executeOn(globalWorker) + 轮询链整体 deadline」；4.2-C4-003 两面映射的**唯一收敛载体**）：①两注入点改 `globalTimer().executeOn(globalWorker())`（与 `AbstractPollingLeaderElector.java:74`/`AutoRegistration.java:206` C 类先例同口径）；②`PollingRpcClient` 增加可选整体 deadline（cancelToken 缺失时也强制终止链）——「链无整体截止」面闭合 |

### 移交承接面（非 9 成员 ID，设计 §3.1.2 矩阵落在 R1-03 的处置行——本批必须全处置，禁止静默缺处置）

- **A4 ScheduledRecoveryManager**（勘误 4 终裁口径下第三个 DB 相关站点，无独立 finding ID；live @ `e7388fc9b9` 核实：该类**零 beans.xml 注册、零 main 构造点，生产默认 `NoOpRecoveryManager`，集成方经 `engine.config().setRecoveryManager(...)` 程序化注入**——设计矩阵 A4 行「生产经 IoC 注入 globalTimer」系该类 javadoc `:199` 装配例示的误读，仓内不存在 IoC 装配面）：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/ScheduledRecoveryManager.java:361`（`start()` 内 `scheduleWithFixedDelay` 调用点）。recovery scan = DB 孤儿会话扫描（多查询）。**R1-03 承接（仓内可实现形态）：在 `start()`（:361）对注入载体绑定 `executeOn(GlobalExecutors.globalWorker())`（或模块专用载体），`:199` javadoc 装配示例同步改为 executeOn 形态**（集成方接线由 javadoc 契约收敛）——scan 体不常驻 timer 线程；绝缘覆盖保持（与既有 `TestScheduledRecoveryManagerInsulation` 并行）。
- **A5 JsonRpcWebSocketHandler keepalive**（`:82` 每连接周期任务）：矩阵裁定「与 2.3-C8-001 同工单合并」——成员 3 承接，见 Phase 3。
- **D3/D4 ClusterRpcServiceInvoker.java:67-68 / ClusterRpcProxyFactoryBean.java:42-43**：4.2-C4-003 工单本体（成员 9）——两面映射闭合后该缺陷的收敛核验以本批工单为唯一载体（§3.1.3 闭合声明）。
- **SocketServer cached 池消费迁移**（plan 2497 Deferred 移交项，Successor Path=roadmap 8.4；设计 §3.1.3 3.2-C4-004 行明文载于本批）：`nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java:140`（每连接派发 cached 池）。**在裁定④熔断基线上评估有界池迁移**：评估结论三选一落账（改码迁移有界池 / 维持 cached + `maxConnections` 封顶与熔断信号声明[2497 已核 maxConnections 默认 10] / 两者结合）；若判改码则随批最小迁移 + 回归，评估结论写 owner doc 或处置登记（勘误 5）。

### Baseline 勘误（输入不一致登记）

1. **roadmap 8.4 / batch-proposal R1-03 模块列不完整**：模块列 = nop-stream；nop-wf；nop-task；nop-service-framework；nop-ai；nop-batch；nop-cluster。实测成员缺陷面实际触达 **nop-message**（DebeziumEngineWrapper 在 nop-message-debezium——1.2 审计已声明「缺陷定位横跨 wrapper（nop-message-debezium）与 in-scope 使用链」）与 **nop-network**（成员 9 载体 PollingRpcClient 在 nop-rpc-core；SocketServer 在 nop-socket）。本 plan 验证命令按实际 diff 触达模块列全（`./mvnw test -pl nop-message -am` 为成员 1 必跑通道；nop-network 若产生产品 diff 随批登记通道），不默认扩批。
2. **设计 §3.1.3 表「3.2-C4-004」行的面列与其审计本体不符**：设计该行面列写「SocketServer cached 池消费迁移（Deferred successor）」，而 3.2 审计 C4-004 的本体 = ShellCommandExecutor 缺省 globalWorker 共池（G55 行「与全平台任务共池」同证）；SocketServer 迁移是 plan 2497 Deferred 节的移交项（Successor Path=roadmap 8.4）。**本 plan 两者均承接**：成员 8 按审计本体（shell pipeline 池隔离）落 Phase 4；SocketServer 按 2497 Deferred + 设计行文字落 Phase 5 评估面。行文以各自裁定为准，防漏防扩。
3. **行号漂移登记（成因各注）**：DebeziumEngineWrapper 审计载 :159-173（内联 lambda，`engine.run()` :163）→ live :160（runEngineWorker helper 形态；plan 2496 4.1-C3-009 七屏障改造 commit `1925eb2dd5`）；JsonRpcWebSocketHandler sendKeepAlive 审计载 :357-363 → live :360-367（R1-01 绝缘注释加入）、onNext 审计载 :457-469 → live :469-478（:82 keepalive 与 GraphQLSubscriptionManager :204/:217 零漂移）；TaskStepHelper 审计载 :268-270 → live :269-270、RetryTaskStepWrapper :28-29 → :29、TaskFlowManagerImpl :76-81 → :77-79；EmbeddingServiceImpl 审计载 :88-142/:122-124 → live :89-142/:123-124；SingleTurnExecutor 审计载 :41-42 → :42、:54-60（:59 零漂移）、:156-177 → :158；AgentDispatchExecutor 审计载 :32-53（:33-34）→ live 方法体 :34/:40/:52、AgentToolDispatcher :552-568 → INSTANCE 行 :560；BatchTask getExecutor 审计载 :83-88 → live :83-87、BatchTaskBuilder :336-337 → :335-337、ExportDbTool/ImportDbTool cached 兜底审计载 :284/:296 → live :285/:297（R1-02 表级池修复 `6309d3a5e0` 致 +1）；ShellCommandExecutor :58-60 零漂移；ClusterRpc 两注入点 :42-43/:67-68 零漂移、ClusterRpcClient 审计载 :143-150 → 构造行 :147。本 Baseline 已按 live 回读值书写；执行时再以 live 为准。
4. **1.2-C4-004 的 MA-06 族边界**：P1 台账行挂 MA-06 族主，其 P2 成员 C4-005（stop fire-and-forget 无终结保证）与 C4-006（启动 lambda 缺守卫）已并入 `1.2-C3-014`（MA-07，cra-index L150）→ **R1-08 批次**。本批只承接常驻挤压面（专用载体 + stop 有界终结预算），stop 终结 latch 契约/启动守卫归 R1-08 同文件续作——审计明示「专用单线程执行器 + 终结 latch 可同时消除三面」，本批引入的专用载体即 R1-08 `shutdownNow()` 形态的载体前提；边界注记落 cra-index 1.2-C4-004 行与 1.2-C3-014 行（Phase 8），防两批各修一半或重复立项。
5. **SocketServer 模块面（nop-network/nop-socket）不在 roadmap 8.4 模块列与 batch-proposal R1-03 finding 清单**：按 plan 2497 Deferred（Successor Path=roadmap 8.4，分类 `out-of-scope improvement`）+ 设计 §3.1.3 行文字承接为「裁定④基线上的迁移评估」；评估可判「维持 cached + 封顶声明」而不改码（2497 Deferred 已载 maxConnections 默认 10 封顶 + 熔断信号已存在）——**不默认扩批改码**，改码与否以评估结论为准并落 owner doc/处置登记；nop-network 无产品 diff 时无需 `-am` 通道。
6. **1.5-C4-001 簿记站点 S1/S2 为豁免对照面**：`DelayTaskStep.java:39-41`/`TimeoutTaskStepWrapper.java:25` 属 C4-round3 矩阵「轻量簿记」类（纯内存、一次性延迟），满足 §3.1.1 豁免判据 (a)(d)——非本批缺陷面；成员 2 修复若使同链形态变化，以「schedule 仅做定时唤醒」口径一致性核对为准，不扩批改码。
7. **「3.1-MA-16 约束解除」的批次归属**：约束本体在 P2-G52/R4-21，约束真实载体 = cra-index L418（P2-G52 行「MA-16 不得指向 globalTimer 批次约束」）+ batch-proposal R4-21 行（L136，Deps 列「MA-16 不得指向 globalTimer（约束在 R1-03 落地后解除）」，只读核对）——**不向 roadmap 8.61 行状态块外写注记**（live 核对该行 Deps 列仅载 batch-proposal 引用与硬依赖、无 MA-16 注记；本 plan 初稿「roadmap 8.61 行同注记」系假事实，R1 勘正）。解除条件 = 本批落地（设计 §3.1.3 尾注）。本批不承接 MA-16 本体工单，只承载**解除声明注记**（Phase 7，由 cra-index P2-G52 行注记承载）：解除后约束形态收窄为「阻塞/长任务体不得指向 globalTimer；满足 §3.1.1 豁免判据的轻量直连合法」。

## Goals

- 9 项成员全部修复并各带 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项先红后绿或变异/回退论证（E2）——线程归属类断言（timer/worker/commonPool 线程名）以修复前缺陷基线跑红为标准动作。
- 长驻/阻塞任务体全部移出平台共享载体：globalWorker 不再被 Debezium 引擎常驻钉死；globalTimer 不再承载 task retry/delay 业务体、WS 每连接 keepalive 扇出与 rpc-cluster 轮询体；commonPool 不再承载 AgentDispatchExecutor 工具体与 SingleTurnExecutor 缺省 LLM 调用。
- 移交承接面全部落账：A4（ScheduledRecoveryManager 卸载改码，随 nop-ai 分片）、A5（并入成员 3）、D3/D4（成员 9 本体，两面映射闭合）、SocketServer（裁定④基线评估结论落账）——设计矩阵「移交 R1-03 4 行」在本批全处置。
- 1.7-C8-003 三面收敛：兜底池形态评估结论落 owner doc + 装配期上界护栏 fail-fast + batch.xdef 声明失真修正（产品缺陷随修）+ nop-batch.md F3⑥ 随修。
- **MA-16（R4-21）「不得指向 globalTimer」约束解除声明落对应批次注记**：cra-index P2-G52 行注记承载 + batch-proposal R4-21 行（L136）只读核对，解除后形态收窄声明（勘误 7）。
- Owner doc 随批更新：roadmap 8.4 四份（`docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/03-modules/nop-task.md`、`docs-for-ai/02-core-guides/service-layer.md`、`docs-for-ai/03-modules/nop-ai.md`）核对与更新 + 实际触达模块缺口声明（nop-message 有 03-modules 条目→核对；nop-socket/nop-rpc-core 若产生产品 diff→R-G 轨缺口声明）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 零触碰声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不做平台设施面回改**：R1-02 已闭设施面（GlobalExecutors/ExecutorHelper/Default*Executor/CommonConfigs/CommonErrors——executeOn API、容量配置键、工厂校验、cached 上界熔断）；本批只做消费方站点接入，设施文件零改动。站点修复过程中若暴露设施缺陷，按批次纪律登记（cra-index/新 finding）不越界承接。
- **不承接 1.2-C4-005/C4-006**（stop 终结 latch 契约/启动守卫——已并入 1.2-C3-014/MA-07 → R1-08；本批仅保证载体替换不回退既有 close 语义，勘误 4）。
- **不承接 WS 适配器层写超时**（2.3-C8-001 建议②：`JakartaWebSocketSession`/`SpringWebSocketSession` 的 `getBasicRemote`→`getAsyncRemote`/写超时——审计明示适配器维度独立清扫，同族面归 R4-16/R4-17 [G36] 批次）。
- **不承接 G51/G55/G66 的非本批成员**：3.1-C4-015（spawn 池 close 接线 → R2-16）、3.2-C4-008（api-core bindCancel 协同 → R1-04）、4.2-C4-004（自续链断链监督——已在 R1-01 落地，本批仅核对零回退）。
- **不承接 1.5 相邻面**：1.5-C4-002（sync+executor 嵌套死锁 → R2-11）、1.5-C4-009（延迟任务无撤单绑定，P3 不立项，不扩批）。
- **不做批外 A 类站点**：A6 SysDaoNamingService、A7–A9 CLI 族维持 R-G 治理轨登记（plan 2497 既有裁定）；B1–B6/C1–C6/D1/D2/D5 设施级覆盖面零改码。
- **不回退 R1-01 绝缘覆盖**：sendKeepAlive 链、executeOn Bind 载体的绝缘语义（重臂/失败可见）维持 `periodic-task-insulation.md` 裁定；占用面改造后的载体周期方法仍路由绝缘核心。
- 不触碰 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物（零触碰声明见 plan 头部；batch.xdef 为源 schema 非生成物）。
- 不做 Deferred 节所列项（适配器写超时族、retry 独立执行池抽象、桥接队列反压等）。

## Scope

### In Scope

- `nop-message/nop-message-debezium`（DebeziumEngineWrapper 专用载体 + stop 有界终结）+ `nop-stream/nop-stream-connector-debezium`（使用链回归面）。
- `nop-task/nop-task-core`（retry/delay 业务体卸载——TaskStepHelper/RetryTaskStepWrapper/TaskFlowManagerImpl）。
- `nop-service-framework/nop-graphql/nop-graphql-core`（WS 推送卸载共享池 + keepalive 扇出收敛——JsonRpcWebSocketHandler/GraphQLSubscriptionManager）。
- `nop-ai/nop-ai-agent`（AgentDispatchExecutor/SingleTurnExecutor/ScheduledRecoveryManager）+ `nop-ai/nop-ai-core`（EmbeddingServiceImpl + ai-defaults.beans.xml）+ `nop-ai/nop-ai-shell`（ShellCommandExecutor）。
- `nop-batch/nop-batch-core` + `nop-batch/nop-batch-exp`（兜底池形态 + 装配护栏）+ `nop-kernel/nop-xdefs` batch.xdef（声明文本修正）。
- `nop-cluster/nop-rpc-cluster`（两注入点 executeOn + deadline 装配）+ `nop-network/nop-rpc/nop-rpc-core`（PollingRpcClient 整体 deadline）。
- `nop-network/nop-socket`（SocketServer——仅评估结论落账；改码以评估结论为准）。
- Owner doc：roadmap 8.4 四份核对与更新 + 实际触达缺口声明；cra-index 台账回填（9 成员行 + MA-16 解除注记 + CL-025 处置注记 + 1.2 勘误 4 边界注记）；roadmap 8.4 → done。

### Out Of Scope

- Deferred 节所列项；R1-08（1.2-C3-014）、R2-16、R1-04、R4-16/R4-17、R2-11 等后续批次工单；批外 A 类站点（A6/A7–A9）；平台设施面回改（见 Non-Goals）。

## Execution Plan

> 六个模块组分片修复（Phase 1–6，对应 roadmap 模块列六单元：nop-stream[含 nop-message 面] / nop-wf-task / nop-service-framework / nop-ai / nop-batch[含 SocketServer 评估面] / nop-cluster）→ 全量验证 + owner doc（Phase 7）→ closure audit + 收口（Phase 8）。分片间无硬依赖、可按序执行；每分片独立提交（提交纪律：每模块一提交，commit message 声明工单 ID 与 plan 号）。

### Phase 1 - 分片 A：1.2-C4-004 Debezium 引擎移出 globalWorker（nop-message + nop-stream）

Status: completed
Targets: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java`（:160 提交点 + stop 路径）、`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/`（DebeziumCdcSourceReader/DebeziumCdcSourceFunction 使用链回归面）、两模块测试目录

- Item Types: `Fix`（1.2-C4-004 已确认 live defect，不得写成 Feature）、`Proof`（回归测试）

- [x] 引擎提交点改专用载体（按设计 §3.1.3 行 1「引擎 = 长任务体，须专用载体」与审计建议形态）：per-engine 专用单线程执行器（平台 `io.nop.commons.concurrent.thread.NamedThreadFactory`，daemon，命名含 connector 名如 `debezium-engine-<name>`）；载体有独立容量预算与关闭归属、不经 globalWorker 执行任务体（§3.1.1 专用载体例外判定标准）
- [x] stop 关闭路径有界终结预算：专用载体 `shutdown()` + `awaitTermination(有界上界)`，与既有 close 链（reader close→cancel→stop→engine.close→join(5s)）衔接，不引入无界等待；**不改动 C4-005/C4-006 归属面的 stop 终结 latch 契约/启动守卫**（勘误 4 边界——载体替换不回退既有 close 语义）
- [x] 回归测试（rule 25，测试名带 `_C4_004` 标识）：①引擎任务体执行线程断言（startsWith 引擎专用线程名、非 `nop-global-worker`）；②stop 后载体线程有界终结断言；③既有 connector 测试（DebeziumCdcSourceReader/Function 链）零回归
- [x] **接线验证**（rule 23）：断言 `wrapper.start()` 后任务体真实运行于专用载体线程（线程名可观测），globalWorker 不再接收引擎任务
- [x] **无静默跳过**（rule 24）：载体关闭失败/超时路径显式日志与异常传播，不吞异常
- [x] **提交纪律**：nop-message 与 nop-stream 各一提交（或按实际 diff 面单提交并声明关系），commit message 带 `1.2-C4-004` 与 plan 号
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 1，agent 分片 A @ `a6a59f2035`）：

- **修复面**：`DebeziumEngineWrapper` 新增 per-engine 专用载体字段 `engineExecutor`（`Executors.newSingleThreadExecutor(NamedThreadFactory("debezium-engine-<connector>", true))`，独立容量预算 + 关闭归属，不经 globalWorker/globalTimer 执行任务体 = §3.1.1 专用载体判定标准）；`start()` 提交点（live :160 `GlobalExecutors.globalWorker().execute(...)`）改专用载体；`stop()` 在既有 `engine.close()` 后新增 `carrier.shutdown() + awaitTermination(5s)`（常量 `STOP_TERMINATION_BUDGET_SECONDS = 5`，对齐 reader close 链 join(5s)，无无界等待）；超时路径 WARN、中断路径恢复中断位 + ERROR（rule 24 无静默）；引擎体退出 finally 自关停载体（daemon 线程不空转滞留）。`shutdownNow` 升级与终结 latch 契约未触碰（归 1.2-C3-014/R1-08，勘误 4 边界），close 语义零改写；C4-006 启动守卫未加（同边界）。
- **测试**：`TestDebeziumEngineDedicatedCarrier_C4_004` 2 条——①`engineBodyRunsOnDedicatedCarrierThreadNotGlobalWorker`（probe 子类捕获 `runEngineWorker` 执行线程名，断言 `debezium-engine-carrier-probe` 前缀、非 `nop-global-worker`）②`stopTerminatesCarrierWithinBoundedBudget`（在跑体 800ms 场景：stop 耗时 ∈ [300ms, 5000ms) 证明有界等待真实发生 + 载体线程 TERMINATED）。测试仅用修复前既有 API 面（probe 覆写 protected `runEngineWorker` + `start/stop`），保证先红可编译。
- **必红证据（E2）**：修复前基线（stash 产品 diff）2/2 红——线程名 `nop-global-worker-1-1`（共享池钉死签名）、stop 0ms 返回（fire-and-forget 签名）；快照 `_tmp/phase1-c4-004-red-baseline.log` → 修复后 2/2 绿 `_tmp/phase1-c4-004-green-fixed.log`。
- **接线验证证据**：上列测试 ① 即 rule 23 断言——真实 `wrapper.start()` 构建真实 Debezium 引擎（connector.class=mysql 全链 build）后，任务体线程可观测为专用载体线程。
- **门禁**：`./mvnw test -pl nop-message -am` BUILD SUCCESS EXIT=0（106 tests 0 失败，快照 `_tmp/phase1-gate-nop-message.log`）；`./mvnw test -pl nop-stream -am` BUILD SUCCESS EXIT=0（使用链 DebeziumCdcSourceReader/Function 回归面零失败，快照 `_tmp/phase1-gate-nop-stream.log`）。nop-stream 产品零 diff（本工单缺陷定位横跨 wrapper 与使用链，修复落 wrapper 本体）——**单提交承载两模块面**（`a6a59f2035`，commit message 声明关系），符合 checklist「按实际 diff 面单提交并声明关系」支路。
- **Owner doc 裁定去向**：`nop-stream-connectors.md` 引擎线程模型契约（共享池 → 专用载体 + 有界终结）——**归 Phase 7 统一落账**（本 Phase 显式登记去向，随 roadmap 8.4 四份 owner doc 核对一并执行）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 1.2-C4-004 landed：引擎任务体不再运行于 globalWorker（专用载体在位），带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：线程归属断言先红后绿或变异/回退论证留痕（修复前基线断言 `nop-global-worker` 前缀红；快照 `_tmp/phase1-c4-004-*.log` 含 RED 与 GREEN 两段）
- [x] 接线验证证据登记（哪条断言证明运行时生效）
- [x] `./mvnw test -pl nop-message -am` BUILD SUCCESS；`./mvnw test -pl nop-stream -am` BUILD SUCCESS（使用链回归面）
- [x] Owner doc 裁定：`nop-stream-connectors.md` 引擎线程模型契约核对（「引擎 = 共享池」未声明边界的缺陷面随修复收敛为专用载体契约）——受影响面随批更新，或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 7 统一但须在本 Phase 显式登记去向
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 分片 B：1.5-C4-001（MA-04）task retry/delay 业务体卸载（nop-task）

Status: completed
Targets: `nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java`（:269-270 延迟分支）、`nop-task/nop-task-core/src/main/java/io/nop/task/step/RetryTaskStepWrapper.java`、`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java`（:77-79 回退面）、`nop-task/nop-task-core/src/test/`

- Item Types: `Fix`（1.5-C4-001 及成员面 C4-008 已确认 live defect）、`Proof`（回归测试）

- [x] 延迟重试路径改「timer 触发 + 载体提交」等价形态（§3.1.3 行 2 = §3.1.1 强制卸载形态）：`schedule` 仅做定时唤醒，action（步骤体）与完成回调链（含 `saveState` 簿记）经 `GlobalExecutors.globalWorker()` 或步骤显式 executor 执行——executor 隔离配置对延迟路径真实生效（成员面 C4-008 行为钉）；C3-013 同池面随同 diff 收敛
- [x] 簿记对照站点核对（S1 `DelayTaskStep.java:39-41` / S2 `TimeoutTaskStepWrapper.java:25`）：确认豁免判据 (a)(d) 成立且未被修复改动破坏（勘误 6），或随同口径对齐——不改码须留核对结论
- [x] 回归测试（rule 25，测试名带 `_C4_001` 标识）：①延迟到期后 action 执行线程断言（非 `nop-global-timer` 线程；未配 executor 时为 worker 线程）；②配置显式 executor 时延迟路径在该池线程执行（C4-008 隔离生效钉）；③`saveState` 簿记不落 timer 线程断言；④既有 task 测试零回归
- [x] **接线验证**（rule 23）：真实 TaskFlowManagerImpl 回退链（未注入 scheduledExecutor→globalTimer）上断言任务体执行线程为 worker
- [x] **提交纪律**：nop-task 一提交，commit message 带 `1.5-C4-001`（MA-04）与 plan 号
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 2，agent 分片 B @ `a31a4f03db`）：

- **修复面**：`TaskStepHelper.retry` 新增 5 参重载（4 参保留委托 null），延迟分支 `schedule(action, delay)` 前挂 `.executeOn(resolveRetryCarrier(stepRt, executorBean))`——BindScheduledExecutor 语义下 timer 只触发、action 与完成回调链（`thenCompleteAsync` handler → `doRetry` → `setRetryAttempt` + `saveState`）在载体线程执行；`resolveRetryCarrier` = 步骤显式 executor（`ITaskRuntime.getThreadPoolExecutor(executorBean)` 显式接缝，plan 指定形态；bean 缺失 WARN `nop.task.retry-executor-bean-not-found` + 回退，不静默）否则 `GlobalExecutors.globalWorker()`，无路径落 globalTimer（C4-008 行为钉 + C3-013 同池面收敛）。`RetryTaskStepWrapper` 增 `executorBean` 字段（2 参构造保留，nop-task-ext `RetryTaskStepDecorator` 零改动）；`TaskStepEnhancer.wrap` retry 包装点传入 `stepModel.getExecutor()`（executor 包装在 retry 外层，延迟轮次体经本修复直达隔离池——首次尝试仍经 ExecutorTaskStepWrapper 提交，语义一致）。
- **不改码裁定（对照站点）**：`TaskFlowManagerImpl:77-79` globalTimer 回退零改动——矩阵 D2 豁免面「任务体在别处执行」经本修复对 retry 路径真实成立（timer 只承载触发）。S1/S2 豁免核对（勘误 6，零改码留结论）：S1 `DelayTaskStep`（schedule 体 `() -> return null` 纯触发，等待经返回 future）与 S2 `TimeoutTaskStepWrapper`→`TaskStepHelper.timeout`（timer 体 = `cancellable.cancel` + `completeExceptionally` 纯内存毫秒级）满足豁免判据 (a)(d)（且 (b) 无阻塞 I/O），`git diff` 证实两站点及 `timeout()` 方法零触碰。
- **测试**：`TestTaskStepHelperRetryDelayOffload_C4_001` 3 条（仿既有 `FakeTaskStepRuntime` 桩模式）——①`delayedRetryBodyRunsOnWorkerNotTimerThread_C4_001`（4 参 API，fake taskRt `getScheduledExecutor`=真实 globalTimer = 未注入回退链，延迟体线程断言 worker）②`delayedRetryBodyHonorsStepExecutorIsolation_C4_001`（5 参 + `DefaultThreadPoolExecutor.newExecutor("test-retry-pool",1,10,true)` 真实池，延迟体线程钉该池）③`delayedRetrySaveStateBookkeepingNotOnTimerThread_C4_001`（saveState 调用点线程记录，第 2 次[延迟轮次失败后的 doRetry 簿记]断言 worker）。既有 retry 测试（ExecutionCount 2/Suspend 2/SyncReturn 2/PersistOrder 1）零回归。
- **必红证据（E2）**：测试 ① 仅用修复前既有 API（4 参 retry）可对真基线编译；采用回退论证（E2 允许支路）：`resolveRetryCarrier` 暂时改回缺陷语义（直连 `globalTimer()`）→ 3/3 红（`nop-global-timer-2-2` 前缀，三断言全覆盖），快照 `_tmp/phase2-c4-001-red-defect-baseline.log` → 恢复修复 3/3 绿（含既有 retry 测试共 10 条）`_tmp/phase2-c4-001-green-fixed.log`。
- **接线验证证据**：测试 ①/③ 的 fake `getScheduledExecutor()` 返回**真实** `GlobalExecutors.globalTimer()`（= `TaskFlowManagerImpl` 未注入 scheduledExecutor 的生产回退链），在此真实链上断言任务体/簿记线程为 `nop-global-worker-*`。
- **门禁**：`./mvnw test -pl nop-task -am` BUILD SUCCESS EXIT=0（快照 `_tmp/phase2-gate-nop-task.log`；首次运行失败 3 条系同仓未提交的 Phase 3 graphql-core 中间态被 nop-task 反应堆带入——修复适配后复跑全绿，nop-task 本体测试两轮均零失败）。nop-wf 零 diff（plan 预登记「若零 diff 以实际为准登记」）。
- **Owner doc 裁定去向**：`nop-task.md` 重试体执行位置契约（「延迟重试体在共享定时器线程执行」未声明面收敛为「timer 触发 + 显式 executor/globalWorker 载体」）——**归 Phase 7 统一落账**（本 Phase 显式登记去向）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 1.5-C4-001（含成员面 C4-008/C3-013 同 diff 面）landed：步骤体与 saveState 簿记不再运行于 globalTimer 线程，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：线程归属断言先红后绿或变异/回退论证留痕（修复前基线断言 `nop-global-timer` 前缀红；快照 `_tmp/phase2-c4-001-*.log`）
- [x] 接线验证证据登记
- [x] `./mvnw test -pl nop-task -am` BUILD SUCCESS（nop-wf 若零 diff 以实际为准登记）
- [x] Owner doc 裁定：`nop-task.md` 重试体执行位置契约核对（「重试体在共享定时器线程执行」未声明面随修复收敛）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 7 但须在本 Phase 显式登记去向
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 分片 C：2.3-C8-001 WS 推送卸载共享池 + A5 keepalive 扇出收敛（nop-service-framework）

Status: completed
Targets: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java`（:82 keepalive、:360-367 sendKeepAlive、:469-478 onNext）、`.../subscription/GraphQLSubscriptionManager.java`（:204-218 pushToClient）、`nop-graphql-core` 测试目录

- Item Types: `Fix`（2.3-C8-001 已确认 live defect；A5 为设计矩阵移交承接行）、`Proof`（回归测试）

- [x] keepalive 触发与连接生命周期解耦（矩阵 A5 行口径）：消除每连接一条 globalTimer 周期任务的 N 线性扇出——扇出收敛为 O(1) 共享扫描（单条周期任务遍历连接注册表）或模块专用载体，二选一以实现期对齐设计为准绳；阻塞 send 不再占用 timer 线程
- [x] 订阅推送路径卸载共享池（§3.1.3 行 3）：`onNext`/`pushToClient`/`sendKeepAlive` 三立案本体的阻塞 send 与 timer 线程解耦——慢客户端 L 不再可钉死 globalTimer（ρ=N·L/40 钉死面消除）；推送形态按审计建议①方向（带界队列 + 溢出策略[平台 `IBlockingQueue`+`QueueOverflowPolicy`，满则断开该会话]）或等效卸载形态，随实现期对齐设计落地
- [x] 绝缘覆盖不回退：keepalive 链改造后仍受 R1-01 绝缘覆盖（sendKeepAlive 绝缘语义与失败可见不回退）；适配器层零改动（`JakartaWebSocketSession`/`SpringWebSocketSession` 写超时面归 Deferred）
- [x] 回归测试（rule 25，测试名带 `_C8_001` 标识）：①globalTimer 上无每连接周期任务（扇出数量断言：N 连接 → timer 周期任务 O(1)；观测手段不限单一形态——注入探测 executor 计数、`GlobalExecutors.allExecutors()` 统计或反射读取任务注册表均等效，以能钉「N→O(1)」为准，防执行期卡断言手段）；②keepalive/推送路径阻塞 send 执行线程断言（非 timer 线程）；③慢客户端注入场景 timer 健康断言（单慢会话不阻塞其他会话 keepalive/推送）；④既有 graphql-core 测试零回归
- [x] **接线验证**（rule 23）：真实 globalTimer 单例上断言连接建立前后 timer 周期任务数量与执行线程（周期任务数量可用注入探测 executor 或 `GlobalExecutors.allExecutors()` 统计等效观测）
- [x] **提交纪律**：nop-service-framework 一提交，commit message 带 `2.3-C8-001`（含 A5 合并声明）与 plan 号
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 3，agent 分片 C @ `77cbfd2f64`）：

- **修复面（A5 扇出收敛，二选一取「O(1) 共享扫描」支路）**：`JsonRpcWebSocketHandler` 每连接 `globalTimer().scheduleWithFixedDelay(...)`（live :82）改为 JVM 单条共享链——静态 `LIVE_HANDLERS`（CHM keySet）注册表 + 首次注册时武装 `globalTimer().executeOn(globalWorker()).scheduleWithFixedDelay(sendKeepAliveToAll, 10, 10, SECONDS)`；扫描体惰性驱逐 closed 会话（close 回调缺失防泄漏）。共享链载体 = BindScheduledExecutor（timer 只触发、扫描体在 worker 执行），扫描体逐会话仅入队不发送——单慢会话不可能拖慢他会话 keepalive。N 连接 → timer 周期任务 O(1)。
- **修复面（推送卸载，审计建议①形态）**：新增 `WebSocketSendPump`（`OverflowBlockingQueue(128, DROP_NEWEST)` + `SequentialTaskExecutor(globalWorker, queue, -1)` 平台设施组合：会话级有界、FIFO 有序、无每连接线程、漏极任务仅在有待发任务时调度到共享 worker 池）；`onNext`（数据/错误消息）、`onComplete`（complete 消息，与数据共 pump 保序）、`sendKeepAlive`（ping）、`GraphQLSubscriptionManager.pushToClient`（manager 侧 per-session pump 表，序列化留在调用线程、仅阻塞 socket 写卸载）四立案本体全部经 pump 卸载——`session.sendMessage` 不再在 timer/发布/分发线程执行，ρ=N·L/40 钉死面消除；队列满（慢消费者）→ 断开该会话（`close(1011)`）+ 注销订阅，不反压全局。适配器层零改动（写超时族归 R4-16/R4-17 Deferred）。
- **绝缘覆盖不回退**：`sendKeepAlive` 站点级 `catch (IOException)` + `catch (Throwable)` 绝缘语义与结构化日志键原样保留（外壳改为 pump 入队）；共享链经 executeOn Bind 载体继承 R1-01 绝缘核心（30 点表 #10–13）。`TestJsonRpcKeepAliveInsulation` 2 条共存全绿（投递断言 `awaitUntil` 适配异步 pump 时序，Throwable 不传播/失败轮次不投递/链续投/句柄不取消四项绝缘断言零删减）；`TestPeriodicTaskInsulation`（nop-commons）不在本模块门禁内，plan 2497 已核载体绝缘设施零回改——本批产品 diff 未触碰 `InsulatedPeriodicTask`/`InsulatedPeriodicChain`/`PeriodicTaskHelper`/`DefaultScheduledExecutor`（设施/绝缘零改动由 `git diff` 证实）。
- **测试**：新增 `TestJsonRpcWsSendOffload_C8_001` 4 条——①`keepAliveFanOutConvergesToConstantOnGlobalTimer_C8_001`（反射读 globalTimer 底层 `DefaultScheduledExecutor.periodicTasks` 登记表计数：5 连接建立前后 delta ≤ 1）②`keepaliveSendRunsOnWorkerNotTimerThread_C8_001` ③`slowSessionDoesNotBlockOtherSessionsKeepalive_C8_001`（阻塞会话 send 在飞时健康会话 ping 照常投递，且阻塞会话 send 未完成）④`subscriptionDataPushOffloadsSendToWorker_C8_001`（真实 onMessage→publisher 链，数据 send 线程=worker、发布线程不发 send）；`TestSubscriptionPushOffload_C8_001` 2 条（manager push 卸载线程断言 + 溢出断连行为钉[200 连发 @ 容量 128 → session closed]）。
- **必红证据（E2）**：修复前基线（stash handler/manager 产品 diff，pump 类不在修复前引用面）5 断言全红——fanout delta=5（每连接 1 条 timer 任务）、keepalive send 线程=`main`（同步发送）、慢会话串行阻塞（healthy ping 只能在慢 send 结束后投递）、manager push 线程=`main`、无溢出断连；测试 ④ 在真基线上以 StackOverflowError 形态红（发布者 request(1) 同步回环 × 同步 send 无溢出断连保护）。快照 `_tmp/phase3-c8-001-red-baseline.log` → 修复后 6+21+18+2 条全绿 `_tmp/phase3-c8-001-green-fixed.log`。
- **接线验证证据**：测试 ① 在**真实 globalTimer 单例**上以反射计数周期任务登记表（N 连接前后 delta ≤ 1 = 扇出 O(1) 运行时证明）；测试 ②④ 的 send 线程名 `nop-global-worker-*` = pump 载体真实执行证明。
- **门禁**：`./mvnw test -pl nop-service-framework -am` BUILD SUCCESS EXIT=0（快照 `_tmp/phase3-gate-nop-service-framework.log`）；nop-task 门禁复跑亦含 graphql-core 测试面（reactor 依赖）全绿。
- **Owner doc 裁定去向**：`service-layer.md` WS 订阅推送契约（同步直推 → 会话级有界 pump 卸载 + O(1) keepalive 扫描）——**归 Phase 7 统一落账**（本 Phase 显式登记去向）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 2.3-C8-001（含 A5 keepalive 合并面）landed：globalTimer 不再承载每连接周期任务、阻塞 send 不在 timer 线程执行，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：扇出/线程归属断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase3-c8-001-*.log`）
- [x] 接线验证证据登记
- [x] `./mvnw test -pl nop-service-framework -am` BUILD SUCCESS
- [x] Owner doc 裁定：`service-layer.md` WS 订阅推送契约核对——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 7 但须在本 Phase 显式登记去向
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 分片 D：3.1 三项 + A4 ScheduledRecoveryManager + 3.2-C4-004 shell 池隔离（nop-ai）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/AgentDispatchExecutor.java`（:34/:40/:52）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java`（:560 装配）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SingleTurnExecutor.java`（:42/:59）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/ScheduledRecoveryManager.java`（:361 `scheduleWithFixedDelay` 调用点 + :199 javadoc 装配示例）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java`（:89-142）+ `nop-ai/nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml`（:12-13）、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java`（:58-60 装配点）、各模块测试目录

> 续作纪律：本分片五面（3.1-C4-011 / 3.1-C4-004 / 3.1-C4-009 / A4 / 3.2-C4-004）允许分会话续作——每面独立提交（commit message 带对应工单 ID 与 plan 号），每面完成即时勾选对应 checklist 项与 Exit Criteria，不跨面积压未勾选项；分会话交接以本 plan 的勾选状态为进度事实源，不依赖会话记忆。

- Item Types: `Fix`（3.1-C4-011[C4-004/C4-009 为 G51 确认缺陷]、3.2-C4-004、A4 移交行）、`Proof`（回归测试）

- [x] 3.1-C4-011：`AgentDispatchExecutor` 缺省 commonPool 收敛——注入命名有界平台池（R1-02 工厂校验契约内构造：poolSize ≥ 1/queueSize ≥ 1）或复用引擎自有 agentExecutor，经 `AgentToolDispatcher` 装配点（:560）接入；三方法（execute/submit×2）一并收口；`cap ≤ 0` 禁超时配置加 LOG.warn；javadoc 补阻塞占用与池宽封顶边界声明
- [x] 3.1-C4-004：`EmbeddingServiceImpl` 重试调度器生命周期闭合（成员 5 修复方向两分支任一，验收同标）：私有池补 stop/destroy（shutdown + 有界 awaitTermination）+ `ai-defaults.beans.xml` 注册 `ioc:stop` + `setRetryScheduler` 换装旧池有界终结；或移除私有池改平台载体（timer 触发+载体提交，dispatch 体不得直连 timer 线程）
- [x] 3.1-C4-009：`SingleTurnExecutor` 两缺省点（:42 向后兼容构造器、:59 null 兜底）不再静默落 commonPool——缺省载体改平台设施（`globalWorker`）或模块专用载体（终态唯一，与 Current Baseline 成员 6 修复方向同口径）；LOG.warn 仅作伴随可见性、warn-only 不构成终态（rule 16 防假收敛）；javadoc 补缺省池语义；生产接线（`AgentExecutorResolver:215-216`）零变化回归钉
- [x] A4 ScheduledRecoveryManager（矩阵移交行）：recovery scan 体（DB 多查询）不常驻 timer 线程——在 `start()`（:361 `scheduleWithFixedDelay` 调用点）对注入载体绑定 `executeOn(GlobalExecutors.globalWorker())`（或模块专用载体，矩阵 A4 行口径）；`:199` javadoc 装配示例同步改为 executeOn 形态（集成方接线由 javadoc 契约收敛）；绝缘覆盖保持；本项无独立 finding ID 且**无仓内装配面**（零 beans.xml 注册/零 main 构造点，生产默认 `NoOpRecoveryManager`，集成方经 `engine.config().setRecoveryManager(...)` 程序化注入）——cra-index A4 注记载明「无仓内装配面，集成方接线由 javadoc 契约收敛」，归 Phase 8 台账回填
- [x] 3.2-C4-004：shell pipeline 阶段与全平台任务池隔离——嵌入装配点注入独立有界池（审计建议 `newExecutor("nop-ai-shell-pipeline", n, m)` 形态，R1-02 校验契约内构造），与前台 bash 工具池、平台 worker 三者隔离；池参数对照「最大并发会话 × 最大管道段数」并在 owner doc 声明包络
- [x] 回归测试（rule 25，测试名带 `_C4_011`/`_C4_004`/`_C4_009`/`_A4`/`_C4_004_3_2` 对应标识）：①C4-011 工具体执行线程断言（非 commonPool[ForkJoinPool.worker-*]）；②C4-004 换装/关闭后无孤儿线程断言（`nop-ai-embedding-retry` 线程有界终结）；③C4-009 缺省构造行为断言（不落 commonPool[ForkJoinPool.worker-*]，LOG.warn 伴随可见——warn-only 不作为通过形态）；④A4 scan 体执行线程断言（直接构造 manager 注入真实/探测 scheduler，断言 scan 体执行线程非 timer——与既有 `TestScheduledRecoveryManagerInsulation` 并行）；⑤3.2 阶段任务执行线程断言（专用池线程、非 nop-global-worker）；⑥既有 nop-ai 测试零回归
- [x] **接线验证**（rule 23）：真实装配链（AgentToolDispatcher 装配点/beans.xml bean/ShellCommandExecutor 构造链）上断言载体生效，非仅类型系统存在
- [x] **无静默跳过**（rule 24）：cap≤0 warn、缺省池告警、换装旧池终结失败路径均显式可见，不吞异常
- [x] **提交纪律**：nop-ai-agent、nop-ai-core、nop-ai-shell 各一提交（同聚合模块可合并为按模块面提交并声明关系），commit message 带对应工单 ID 与 plan 号
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 4，agent 分片 D @ `a435aede0e`/`930b8dc579`/`81e85c2c7c`/`496b29b659`/`15f65b2c66`）：

- **面 1（3.1-C4-011，提交 `a435aede0e`，nop-ai-agent）**：`AgentDispatchExecutor` 三方法（execute/submit(Callable)/submit(Runnable,T)）全部经 R1-02 工厂 `DefaultThreadPoolExecutor.newExecutor("nop-ai-agent-dispatch", 32, 1024, true)` 构造的模块专用有界池收口（一参 `runAsync` 缺省 commonPool 形态消除；饱和 fail-loud 拒绝 = CL-025 无界 runAsync 面随条处置，处置注记文本供 Phase 8 落 cra-index L249 行：无界提交面由本修复的有界队列+fail-loud 拒绝替代，随 C4-011 一并闭合）；`AgentToolDispatcher.resolveToolTimeoutMs` 对 cap≤0 配置一次性 LOG.warn（`nop.ai.agent.tool.timeout-cap-disabled`，rule 24 可见性）；javadoc 补阻塞占用/池宽封顶（上限=本池 32 宽，不再封顶于 commonPool）/cap0 永久占线边界声明。
- **面 2（3.1-C4-004，提交 `930b8dc579`，nop-ai-core）**：取「私有池补生命周期闭合」分支——`EmbeddingServiceImpl` 跟踪自建池（`internalRetryScheduler`），`setRetryScheduler` 换装/传 null 恢复默认时对旧内部池有界终结（`shutdown` + 1s 宽限 + 超时 `shutdownNow` + 有界再等待 + 中断恢复，全路径显式日志不吞异常）；新增 `stop()` + `destroy()`（implements `IDestroyable`，所有权边界=仅终结自建池、外部注入调度器不触碰）；`ai-defaults.beans.xml` 注册 `destroy-method="stop"`（beans.xdef 生命周期钩子属性——即 plan 文本 `ioc:stop` 的 schema 形态）。
- **面 3（3.1-C4-009，提交 `81e85c2c7c`，nop-ai-agent）**：两缺省点（向后兼容构造器 + 全参构造器 null 兜底）缺省载体改 `GlobalExecutors.globalWorker()`（M2 终态=载体真实变更 + `nop.ai.agent.single-turn.default-timeout-executor` LOG.warn 伴随，非 warn-only）；`ForkJoinPool` import 移除；javadoc 补缺省池语义；生产接线回归钉：显式专用 executor 原样使用且不触发 warn（`explicitExecutorIsHonouredWithoutWarn_C4_009`）。
- **面 4（A4，提交 `496b29b659`，nop-ai-agent）**：`start()`（:361）对注入载体绑定 `executeOn(GlobalExecutors.globalWorker())`——timer 只触发轮次，scan 体（DB 孤儿会话多查询）在 worker 池执行；Bind 载体继承 R1-01 绝缘链（守卫包裹+失败退避重臂，`InsulatedPeriodicTask` 周期契约保持）；构造器 javadoc（:199 装配示例）与类级 Scheduling 段同步 executeOn 形态并载明「无仓内装配面，集成方接线由 javadoc 契约收敛」。**A4 cra-index 注记文本（供 Phase 8 落账）**：「A4 ScheduledRecoveryManager 已由 plan 2498 Phase 4 闭合（提交 496b29b659）：start() 对注入载体绑定 executeOn(globalWorker)，scan 体不常驻 timer 线程；无仓内装配面，集成方接线由 javadoc 契约收敛」。绑定形态下接线契约变化（scheduleWithFixedDelay 直调 → Bind 一次性触发器+手工重臂链），既有测试按新契约适配且意图全保留：`RecordingScheduler` 增一次性触发器记录；`TestScheduledRecoveryManagerScheduling` 9 条改钉触发器注册/真实 globalWorker 轮次派发（Anti-Hollow #22 保持）/stop 后链终止负等待；`TestScheduledRecoveryManagerInsulation` 改有界等待 warn 观测（Error 存活+链可重臂语义不变）；recovery 全族 51 条零失败。
- **面 5（3.2-C4-004 本体，提交 `15f65b2c66`，nop-ai-shell）**：`ShellCommandExecutor` 缺省装配（executor==null，:58-60）不再回退 `GlobalExecutors.globalWorker()`——建 R1-02 工厂契约内专用有界池 `nop-ai-shell-pipeline`（32 线程/队列 1024/daemon，`DefaultThreadPoolExecutor.newExecutor`）；与前台 bash 工具池、平台 worker 三者隔离；池参数包络 =「最大并发会话（≤32 有效并发行）× 最大管道段数（1024 队列深度）」，javadoc 成文（owner doc `nop-ai.md` 包络声明落账去向归 Phase 7）；所有权边界=内部池由实例持有、`close()` 有界关闭，显式注入 executor 保持优先且永不被 close 触碰。
- **测试（rule 25，五面 5 个测试类 14 条）**：`TestAgentDispatchExecutor_C4_011` 3 条（execute/submit×2 线程归属 + 有界配置 + `prepareDispatchContext` 真实装配链 `assertSame(INSTANCE)` 接线）；`TestEmbeddingRetrySchedulerLifecycle_C4_004` 4 条（换装孤儿终结/destroy 钩子/注入不触碰/关闭后惰性重建）；`TestSingleTurnExecutorDefaultCarrier_C4_009` 3 条（2 参构造 worker 线程+warn/null 兜底+显式 executor 零 warn 回归钉）；`TestScheduledRecoveryManagerWorkerOffload_A4` 1 条（真实 globalTimer 注入 1s 间隔周期注册，scan 体线程断言）；`TestShellPipelinePoolIsolation_C4_004_3_2` 3 条（缺省链隔离钉/注入优先钉/close 终结钉）。
- **必红证据（E2，先红后绿五面全覆盖）**：C4-011 修复前 3/3 红（`ForkJoinPool.commonPool-worker-1`，`_tmp/phase4-c4-011-red-baseline.log`）→ 绿；C4-004 修复前 4/4 红（换装孤儿线程 10s 不终结 + destroy 钩子缺失 CCE，`_tmp/phase4-c4-004-red-baseline.log`）→ 绿；C4-009 修复前 2/3 红（commonPool 签名+缺 warn，`_tmp/phase4-c4-009-red-baseline.log`）→ 绿；A4 修复前 6 失败+1 错误（scan 体线程 `nop-global-timer-1-2` 等，`_tmp/phase4-a4-red-baseline.log`）→ 绿；3.2 修复前 2/3 红（阶段线程 `nop-global-worker-1-1`，`_tmp/phase4-c4-004-3-2-red-baseline.log`）→ 绿。GREEN 快照各对应 `_tmp/phase4-*-green-fixed.log`。
- **接线验证证据（rule 23）**：C4-011 = `dispatchContextAssemblyWiresModulePool_C4_011` 在真实 `AgentToolDispatcher.prepareDispatchContext`（:560 生产装配点）上断言 `assertSame(AgentDispatchExecutor.INSTANCE, toolExecCtx.getExecutor())` + 任务体线程 `nop-ai-agent-dispatch-*`；C4-004 = `destroy-method="stop"` 经 `TestEmbeddingModelBeanAssembly` 真实 IoC 容器装配零失败 + 换装路径真实线程终结断言；C4-009 = 真实 `SingleTurnExecutor.execute` 链上 call 体线程 `nop-global-worker-*`；A4 = 真实 `GlobalExecutors.globalTimer()` 注入 + 真实 `start()` 周期注册，scan 体线程 `nop-global-worker-*`；3.2 = 真实 2 参构造链真实 pipeline 执行，阶段线程 `nop-ai-shell-pipeline-*`。
- **门禁**：`./mvnw test -pl nop-ai -am -Dsurefire.failIfNoSpecifiedTests=false` BUILD SUCCESS EXIT=0（29 模块全 SUCCESS，33,508 tests / 0 失败 / 0 错误，含 Phase 1–3 无关面全量回归，快照 `_tmp/phase4-gate-nop-ai.log`）；五次面内提交 pre-commit Java lint 全部 0 violations。
- **Owner doc 裁定去向**：`nop-ai.md` 核对——现有文本（agentExecutor cached 池契约 :123 行）与本批零冲突；三处受影响面（工具分发载体缺省池契约 [AgentDispatchExecutor 模块池]、embedding 重试调度器生命周期契约、shell pipeline 池包络声明）**归 Phase 7 统一落账**（本 Phase 显式登记去向；`nop-ai.md` 本 Phase 零改动）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 3.1-C4-011 / 3.1-C4-004 / 3.1-C4-009 / A4 / 3.2-C4-004 五面全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：至少 C4-011（修复前 commonPool 线程断言红）与 A4（修复前 timer 线程断言红）两项先红后绿或变异/回退论证留痕；其余工单按 E2 逐条可复现口径执行（快照 `_tmp/phase4-*.log`）
- [x] 接线验证证据登记（每工单哪条装配链断言证明运行时生效）
- [x] `./mvnw test -pl nop-ai -am` BUILD SUCCESS
- [x] Owner doc 裁定：`nop-ai.md` 核对（工具分发载体缺省池契约、embedding 重试调度器生命周期、shell 池包络声明）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 7 但须在本 Phase 显式登记去向
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 分片 E：1.7-C8-003 batch 兜底池统一评估 + SocketServer 消费迁移评估（nop-batch + nop-xdefs [+ nop-socket 评估面]）

Status: completed
Targets: `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java`（:83-87）、`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java`（:335-337）、`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java`（:285）/`ImportDbTool.java`（:297）、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef`（头注释）、`nop-network/nop-socket/src/main/java/io/nop/socket/SocketServer.java`（:140，评估面）、`docs-for-ai/03-modules/nop-batch.md`（F3⑥ 随修）、各模块测试目录

- Item Types: `Fix`（1.7-C8-003 确认缺陷面：护栏缺失 + 声明失真）、`Decision`（兜底池形态与 SocketServer 迁移评估结论）、`Proof`（回归测试）

- [x] 裁定④基线核对与兜底池形态评估（§3.1.3 行 6；G22 行注记对账防与 2.1-C4-004 重复立项——设施面上界 200 + REE 熔断已在 R1-02 落地）：batch 兜底池（BatchTask:87/BatchTaskBuilder:337/ExportDbTool:285/ImportDbTool:297 四兜底点）形态评估结论落账——改有界形态（globalWorker 或平台 `ThreadPoolConfig` 有界池 + QueueOverflowPolicy 声明）或维持 cached + 熔断基线声明；评估结论写 owner doc（`nop-batch.md`），四兜底点处置一致不静默分叉
- [x] 装配期护栏：concurrency/fetchThreadCount 合理上界 fail-fast（Guard 校验或全局配置项封顶——具体形态随实现期定夺），超界构造期类型化报错
- [x] **接线验证**（rule 23）：真实 `BatchTaskBuilder`→`BatchTask` 构造链上断言超界 `concurrency`/`fetchThreadCount` 触发构造期类型化 fail-fast 报错（护栏生效于真实装配链，非仅 Guard 单测）
- [x] batch.xdef 声明失真修正（复核裁定=产品缺陷随修）：`@concurrency` 头注释（live :2）改为与实现一致（修后兜底池形态的真实语义），声明边界失真消除；验证形态 = schema 装载 + batch 模型解析绿（rule 25 声明 `No new test required: 注释声明文本，schema 装载绿为验证`——注释文本修正不新增独立测试）
- [x] owner doc 随修义务（F3⑥）：`nop-batch.md:14,211` 沿用失真的两处随批修正
- [x] SocketServer cached 池消费迁移评估（plan 2497 Deferred 移交 + 设计 §3.1.3 行；勘误 5）：在裁定④熔断基线上评估——结论三选一落账（改码迁移有界池[随批最小迁移+回归] / 维持 cached + `maxConnections` 封顶与熔断信号声明 / 两者结合）；评估结论写 owner doc 或处置登记（cra-index 2.1-C4-004 行注记核验）
- [x] 回归测试（rule 25，测试名带 `_C8_003` 标识）：①装配护栏 fail-fast 断言（超界 concurrency/fetchThreadCount 构造期报错）；②兜底池形态断言（按评估结论钉行为——如改有界则达界拒绝可见）；③既有 batch 测试零回归
- [x] **提交纪律**：nop-batch 一提交；batch.xdef（nop-xdefs）与 owner doc 随同提交或分提交并声明关系；SocketServer 若判改码则 nop-network 单独一提交
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 5，agent 分片 E @ `d77bcc8cc9` + codegen 再生产物配套提交 `ab8cb1845b`）：

- **评估结论（①，裁定④基线）**：四兜底点（`BatchTask:87`/`BatchTaskBuilder:337`/`ExportDbTool:285`/`ImportDbTool:297`）**维持平台共享 cached 池兜底 + 裁定④熔断基线声明**。理由：(a) 无界面已由 R1-02 在设施层闭合（上界 `nop.commons.concurrent.cached-pool.maxPoolSize` 缺省 200 + 达界 REE `nop.err.commons.concurrent.cached-pool-saturated`），线程爆炸链（审计 P2 本体）不可达；(b) 迁移 globalWorker 被拒——chunk 循环为长任务体，按 §3.1.1 调度槽/执行体契约不得常驻平台共享 worker（该方向复刻本批正在消除的共享载体占用缺陷）；(c) per-task 专用有界池被拒——BatchTask 无关闭钩子，引入池所有权属资源生命周期负担且无在立缺陷承载。四兜底点处置一致（同一池、同一基线），不静默分叉；结论落 owner doc `nop-batch.md`。
- **装配期护栏（②）**：新配置键 `nop.batch.max-concurrency-cap`（`BatchConfigs`，缺省 200 与 cached 池上界对齐——单任务并发不可能超过兜底池容量，更高并发须同时显式调大两键）；concurrency 护栏 = `BatchTask.normalizeConcurrency`（构造期：≤0→1 下界既有文档化行为保留，超界 `NopException` 新错误键 `nop.err.batch.concurrency-exceeds-cap`，params paramName/paramValue/maxConcurrencyCap）；fetchThreadCount 护栏 = `BatchDispatchConfig.setFetchThreadCount`（DSL 装配 `ModelBasedBatchTaskBuilderFactory.buildDispatchConfig` 与程序化装配的共同漏斗，超界同错误键且无半装配状态）；Export/ImportDbTool per-table 链经 `builder.concurrency → buildTask` 真实构造链同护栏覆盖，处置一致。
- **xdef 修正（③）**：`batch.xdef:2 @concurrency` 注释改为与实现一致（兜底 cached 池并行非 SyncExecutor 串行 + 双护栏声明文本）；验证形态 = schema 装载 + batch 模型解析绿（`TestBatchTaskDsl` 2/2 于门禁内）——**No new test required: 注释声明文本修正，schema 装载绿为验证**。同失真副本同任务 reconcile：`docs-for-ai/02-core-guides/batch-dsl.md:36` 同一失真声明与 `docs-for-ai/04-reference/xdefs/batch.xdef`（引用副本）随同修正；nop-batch-dsl codegen 从源 xdef 再生成 `_BatchTaskModel.java`（javadoc 承载注释文本，配套提交 `ab8cb1845b`，非手编生成物）。
- **F3⑥（④）**：`nop-batch.md:14/:211` 失真两处随批修正 + 评估结论落账（并发处理 bullet 与关键配置项 concurrency 行）。
- **SocketServer 评估面（勘误 5，不改码）**：结论 = 三选一取**「维持 cached + `maxConnections` 封顶与熔断信号声明」**。live 核实：`SocketServer.java:140` 兜底 cached 池仅在 executor==null 时（显式注入优先）；`ServerConfig.maxConnections` 默认 10（`ServerConfig.java:22`）封顶并发连接派发面，远低于裁定④池上界 200；达界提交有显式 REE 熔断信号。有界池迁移被拒：per-connection handler 数已被连接封顶、迁移引入服务端池生命周期所有权而不闭合任何在立缺陷——plan 2497 Deferred 分类 `watch-only residual` 维持，Successor Required = no。结论落账载体 = 本执行记录 + 2026-10-24 daily log；nop-network 零产品 diff（`-pl nop-network` 通道不适用）。**cra-index 2.1-C4-004 行注记文本（供 Phase 8 落账）**：「SocketServer cached 池消费迁移评估已在 plan 2498 Phase 5 完成（裁定④基线）：维持 cached 兜底 + maxConnections(默认 10) 封顶声明 + 达界 REE 熔断信号，不迁移有界池；评估记录见 plan 2498 Phase 5 执行记录与 2026-10-24 daily log」。
- **测试（rule 25）**：`TestBatchAssemblyGuard_C8_003` 4 条——①`overCapConcurrencyFailsFastOnRealBuildChain`（真实 `BatchTaskBuilder→buildTask→BatchTask` 构造链，201>200 断言错误键 + 三参数）②`overCapFetchThreadCountFailsFastOnDispatchConfigAssembly`（真实装配漏斗 setter，201 超界 + 无半装配状态钉）③`executorFallbackPinsPlatformBoundedCachedPool`（兜底=平台共享 cached 池单例 assertSame + 显式 executor 优先 + 真实构造链兜底字段反射钉——评估结论行为钉）④`withinCapConcurrencyBuildsAndNormalizesLowerBound`（≤0→1 下界零回退钉 + cap 界内正常装配）。
- **必红证据（E2）**：修复前基线（stash 产品 diff 5 文件；测试仅用修复前可编译 API——参数断言用字面量保证双基线可编译）2/4 红（`Expected NopException to be thrown, but nothing was thrown` = 超界静默放行缺陷签名），快照 `_tmp/phase5-c8-003-red-baseline.log` → 恢复修复 4/4 绿 `_tmp/phase5-c8-003-green-fixed.log`。
- **接线验证证据（rule 23）**：测试 ① 在真实 `BatchTaskBuilder→BatchTask` 构造链上断言护栏生效（超界 buildTask 即抛类型化异常）；测试 ② 在 fetchThreadCount 真实装配漏斗（DSL 与程序化装配共同路径）断言生效；测试 ③④ 真实构造链兜底池/归一值反射钉。
- **门禁**：`./mvnw test -pl nop-batch -am` EXIT=0（9461 tests / 0 失败，快照 `_tmp/phase5-gate-nop-batch.log`；首跑 1 失败系 nop-commons `TestPeriodicTaskInsulation` 观测钩子计数时序 flaky——nop-commons diff=0 零触碰，隔离复跑 9/9 绿（`_tmp/phase5-insulation-retry.log`）+ 全门禁复跑 EXIT=0 证实环境性）。xdef 消费面全绿：nop-batch-dsl（`TestBatchTaskDsl` 2/2 = schema 装载 + batch 模型解析绿）+ nop-batch-exp（`TestDbToolExecutorContract` 2/2）。pre-commit lint 0 violations。
- **Owner doc 裁定**：F3⑥ 在位（`nop-batch.md` 两处 + 评估结论 + 同失真副本 reconcile 两处）；SocketServer 结论落账（载体见上）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 1.7-C8-003 三面收敛：兜底池形态评估结论落 owner doc、装配护栏 fail-fast 在位、batch.xdef 声明与实现一致（`nop-batch.md` F3⑥ 同步）
- [x] SocketServer 评估结论落账（三选一，载体可核验：owner doc 或处置登记）；不默认扩批改码
- [x] 必红证据（E2）：护栏 fail-fast 断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase5-c8-003-*.log`）
- [x] 接线验证证据登记（BatchTaskBuilder/BatchTask 真实构造链 fail-fast 断言）
- [x] `./mvnw test -pl nop-batch -am` BUILD SUCCESS；batch.xdef 修改后受影响模块（nop-batch 及其 schema 消费面）构建与测试绿；SocketServer 若改码则 `./mvnw test -pl nop-network -am` BUILD SUCCESS（评估面零改码，通道不适用）
- [x] Owner doc 裁定：`nop-batch.md` F3⑥ 随修在位 + SocketServer 结论落账；`No owner-doc update required` 不适用于本 Phase（F3⑥ 为既定随修义务）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 分片 F：4.2-C4-003 executeOn 卸载 + 轮询链整体 deadline（nop-cluster + nop-rpc-core）

Status: completed
Targets: `nop-cluster/nop-rpc-cluster/src/main/java/io/nop/rpc/cluster/ClusterRpcProxyFactoryBean.java`（:42-43）、`ClusterRpcServiceInvoker.java`（:67-68）、`ClusterRpcClient.java`（:147）、`nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/PollingRpcClient.java`（:94-101）、两模块测试目录

- Item Types: `Fix`（4.2-C4-003 已确认 live defect——两面映射唯一收敛载体）、`Proof`（回归测试）

- [x] 两注入点裸 globalTimer 改 `GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())`（§3.1.3 行 7 = 矩阵 D3/D4 行口径；与 `AbstractPollingLeaderElector.java:74`/`AutoRegistration.java:206` C 类先例同口径）——轮询体（全量请求体序列化等同步前缀）不再在 timer 线程执行
- [x] `PollingRpcClient` 增加可选整体 deadline：轮询链有界终止（deadline 到期 future 异常完成 + 停止续排），cancelToken 缺失时也强制终止链——「链无整体截止」面闭合；既有 `maxErrorCount`/cancelToken 出口语义零回退
- [x] 回归测试（rule 25，测试名带 `_C4_003` 标识）：①轮询体执行线程断言（非 `nop-global-timer` 线程）；②deadline 到期链终止断言（future 完成、timer 队列不再续排）；③cancelToken/maxErrorCount 既有出口零回归
- [x] **接线验证**（rule 23）：真实 `ClusterRpcClient` 装配链上断言 `executeOn` 载体生效（轮询 tick 线程可观测为 worker）
- [x] **提交纪律**：nop-cluster 一提交；nop-rpc-core（PollingRpcClient）单独一提交并声明与 nop-cluster 提交的关系
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 6，agent 分片 F @ `98cdde0e0e`[nop-rpc-core 载体面] + `7ac75dc674`[nop-rpc-cluster 本体]，两提交互指关系）：

- **修复面①（两注入点卸载，矩阵 D3/D4）**：`ClusterRpcProxyFactoryBean.init`（:42-43）与 `ClusterRpcServiceInvoker.init`（:67-68）裸 globalTimer 兜底改 `GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())`——轮询体（URL 构建/全量请求体序列化/`whenComplete` 续排等同步前缀）hop 到 globalWorker 执行，timer 只承载触发；与 `AbstractPollingLeaderElector.java:74`/`AutoRegistration.java:206` C 类先例同口径（§3.1.3 行 7）；Bind 载体继承 R1-01 绝缘覆盖（30 点表 #10–13 设施级覆盖零回退，`TestPollingRpcClientFix` 既有 2 条共存全绿）。
- **修复面②（轮询链整体 deadline，nop-rpc-core）**：`PollingRpcClient` 新增 6 参构造（`pollTimeout>0` 启用）：①deadline watchdog——PollTask 构造期单次 `timer.schedule`，到期 `future.completeExceptionally(nop.err.rpc.poll-deadline-exceeded)` + cancel timerFuture + `LOG.warn`（链终止不依赖轮次续排路径——cancelToken 缺失同样终止、在飞轮次挂死/底层 callAsync 永不完成亦被兜底）；②`schedule()` 续排前 belt 检查（已过 deadline 即终止，watchdog 为主终态通道）；③future 结束时 watchdog 有界释放（完成/取消/异常完成三态）。既有出口零回退：5 参构造向后兼容（pollTimeout=0 = 不启用，链终止仍由 maxErrorCount/cancelToken 承载——可选 deadline 形态按 plan 文本，升级零行为变化）；装配链 = 新 `RpcConstants.PROP_POLL_TIMEOUT` + 新配置键 `nop.rpc.client-ext.default-poll-timeout`（`RpcConfigs`，缺省 0）+ `RpcHelper.getPollTimeout/setPollTimeout`（请求属性按请求覆盖）+ `ClusterRpcClient:147` 传入。
- **测试（rule 25，9 条两模块）**：nop-rpc-core `TestPollingRpcClientDeadline_C4_003` 4 条——①`deadlineTerminatesChainWhenCancelTokenAbsent`（cancelToken=null，200ms deadline 到期 future 异常完成 + 错误键断言 + 终止后观察窗 tick 计数不增长 = timer 队列不再续排）②`deadlineWatchdogTerminatesChainWhenPollCallHangs`（底层 callAsync 永不完成，链在 deadline 处有界终止 <2s）③`maxErrorCountExitUnaffectedByDeadline`（deadline=10s 不先到期，maxErrorCount=2 既有出口照常终止 = 零回退钉）④`legacyConstructorCompletesNormallyWithoutDeadline`（5 参构造语义零变化钉）；nop-rpc-cluster `TestClusterRpcPollOffload_C4_003` 3 条——①/② 双注入点真实 `init()` 装配链反射捕获传入 ClusterRpcClient 的 timer，probe 任务体线程 `nop-global-worker-*`（Bind 载体真实生效）③ rule 22 端到端：真实 invoker→ClusterRpcClient→PollingRpcClient 全链 polling 请求（pollingMethod/pollInterval 请求属性），tick 线程全 worker 非 timer。
- **必红证据（E2）**：offload——注入点修复 stash 真基线 3/3 红（probe/tick 线程 `nop-global-timer-1-1`/`nop-global-timer-1-3` 缺陷签名），快照 `_tmp/phase6-c4-003-red-baseline.log`；deadline——变异论证（E2 允许支路）：watchdog+belt 条件反转禁用 deadline → 2/4 红（`TimeoutException` 链永续签名），快照 `_tmp/phase6-c4-003-red-deadline-mutation.log` → 恢复修复（grep 断言 `if (pollTimeout > 0)` :104 在位）全绿 `_tmp/phase6-c4-003-green-fixed.log`。
- **接线验证证据（rule 23）**：测试 ①② 在真实 `init()` 构造链上（真实 ClusterRpcClient 反射 timer + 真实 probe 调度）断言 Bind 载体；测试 ③ 真实 invoker 装配链发起 polling 请求，轮询 tick 线程运行时可观测为 worker。
- **门禁**：`./mvnw test -pl nop-cluster -am` EXIT=0（1967 tests / 0 失败，含 Deadline 4 条 + Offload 3 条 + 既有 `TestPollingRpcClientFix` 2 条零回归，快照 `_tmp/phase6-gate-nop-cluster.log`）；nop-rpc-core 产生 diff → `./mvnw test -pl nop-network -am` EXIT=0 BUILD SUCCESS（plan 通道，快照 `_tmp/phase6-gate-nop-network.log`）；两提交 pre-commit lint 0 violations。
- **Owner doc 裁定**：`No owner-doc update required`（本 Phase）——触达模块 nop-rpc-cluster/nop-rpc-core 均无 `docs-for-ai/03-modules/` 条目，缺口声明归 Phase 7 R-G 轨登记；`nop.rpc.client-ext.default-poll-timeout` 配置键契约核对随 Phase 7 owner doc 收口面一并执行。

Exit Criteria:

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4.2-C4-003 两面 landed：轮询体不在 timer 线程执行 + 轮询链整体 deadline 在位，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：线程归属断言与无 deadline 链永续断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase6-c4-003-*.log`）
- [x] 接线验证证据登记
- [x] `./mvnw test -pl nop-cluster -am` BUILD SUCCESS；nop-rpc-core 若产生 diff 则 `./mvnw test -pl nop-network -am` BUILD SUCCESS（PollingRpcClient 面 diff，通道已跑 EXIT=0）
- [x] Owner doc 裁定：`No owner-doc update required`（本 Phase）——触达模块 nop-rpc-cluster/nop-rpc-core 均无 03-modules 条目，缺口声明归 Phase 7 R-G 轨登记（去向前向已显式登记）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 全量验证 + owner doc 更新 + MA-16 解除声明

Status: completed
Targets: 受影响聚合模块全量测试；`docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/03-modules/nop-task.md`、`docs-for-ai/02-core-guides/service-layer.md`、`docs-for-ai/03-modules/nop-ai.md`（roadmap 8.4 Owner Doc 列四份）；触达模块 owner doc 归属核对；`ai-dev/audits/cra-index.md`（MA-16 解除注记 + CL-025 处置注记）

- Item Types: `Proof`、`Decision`（MA-16 解除注记）

- [x] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明）
- [x] 批行验证命令（E3）逐条登记：`./mvnw test -pl nop-stream -am`、`./mvnw test -pl nop-message -am`、`./mvnw test -pl nop-task -am`、`./mvnw test -pl nop-service-framework -am`、`./mvnw test -pl nop-ai -am`、`./mvnw test -pl nop-batch -am`、`./mvnw test -pl nop-cluster -am`（nop-network/nop-wf 若实际产生产品 diff 随批补登记）——每条登记模块/测试数/退出码/快照路径
- [x] 零触碰确认（E4）：本批产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记）；设施面文件（GlobalExecutors/ExecutorHelper/Default*Executor/CommonConfigs/CommonErrors）零改动核对
- [x] 绝缘层零回改实证：`InsulatedPeriodicTask`/`InsulatedPeriodicChain`/`PeriodicTaskHelper`/`IPeriodicTaskListener` diff 零改动 + `TestPeriodicTaskInsulation` 9 条全绿（共享载体改动门禁）
- [x] Owner doc 更新（E5）：roadmap 8.4 四份逐份核对与更新（内容源 = 各 Phase 修复面：引擎载体契约[connectors]、重试体执行位置契约[task]、WS 推送卸载契约[service-layer]、工具分发/调度器生命周期/shell 池包络[nop-ai]），或逐份显式登记 `No owner-doc update required` + 理由；`nop-batch.md` F3⑥ 若未在 Phase 5 落账则本 Phase 收口
- [x] 缺口声明：实际 diff 触达模块中无 `docs-for-ai/03-modules/` 条目者（nop-socket/nop-rpc-core 等，以实际 diff 为准）按 R-G 治理轨先例登记（执行记录 + daily log），不在本批新建 owner doc；nop-message 有 03-modules 条目（`nop-message.md`）→ 核对其 Debezium 引擎线程模型描述受影响面
- [x] **MA-16（R4-21）约束解除声明落对应批次注记**（勘误 7）：cra-index P2-G52 行（L418）「MA-16 不得指向 globalTimer 批次约束」追加解除注记（解除条件 = 本批落地；解除后形态收窄为「阻塞/长任务体不得指向 globalTimer；满足 §3.1.1 豁免判据的轻量直连合法」——设计 §3.1.3 尾注口径）；batch-proposal R4-21 行（L136）只读核对登记（核对其既有「约束在 R1-03 落地后解除」注记与本批解除声明一致；不向 roadmap 状态块外写注记——注记由 cra-index P2-G52 行承载，roadmap 8.61 行 live 无 MA-16 注记，勘误 7）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（受影响模块全集与 Closure Gates 一致）退出码 0；若有批前既有发现，逐条裁定登记
- [x] `ai-dev/logs/` 对应日期条目已更新

执行记录（Phase 7，agent 全量验证+owner doc 子代理 @ HEAD `4bdd94a836` + 本回填提交）：

1. **E1 全量构建（授权支路，理由声明）**：`./mvnw clean install -T 1C -DskipTests` BUILD SUCCESS EXIT=0（453/453 模块 SUCCESS、0 FAILURE、01:09 min wall，快照 `_tmp/phase7-e1-full-build-skiptests.log` / `_tmp/phase7-e1-exit.txt`）。支路理由：本批九工单全部行为面回归已在 Phase 1–6 按 rule 25 落地并各有必红/变异/回退论证证据（E2）；八条聚焦门禁（下条）已承载行为验证，全仓通道在此之上以编译/装配面为增量目标——沿 plan 2496/2497 同支路先例（skipTests 全仓 + 聚焦测试组合覆盖）。
2. **批行验证命令（E3）逐条登记**：七通道 + nop-network 通道全部引用 Phase 1–6 终态快照（gate-then-commit 序：各快照覆盖该模块最终产品提交内容，快照时间戳与模块末次产品提交核对在案），每份快照 BUILD SUCCESS 且全日志失败/错误行 grep 复核 0 命中——①nop-message `_tmp/phase1-gate-nop-message.log`（106 tests，Phase 1 记录在案）②nop-stream `_tmp/phase1-gate-nop-stream.log`（产品零 diff，使用链回归面）③nop-task `_tmp/phase2-gate-nop-task.log`（含 graphql-core 反应堆面，Phase 2 记录在案）④nop-service-framework `_tmp/phase3-gate-nop-service-framework.log`（Phase 3 记录在案）⑤nop-ai `_tmp/phase4-gate-nop-ai.log`（33,508 tests / 0 失败，Phase 4 记录在案）⑥nop-batch `_tmp/phase5-gate-nop-batch.log`（9461 tests / 0 失败，与 Phase 5 记录一致）⑦nop-cluster `_tmp/phase6-gate-nop-cluster.log`（1967 tests / 0 失败，Phase 6 记录在案）⑧nop-network `_tmp/phase6-gate-nop-network.log`（nop-rpc-core 载体面产品 diff 通道，Phase 6 记录在案）。nop-wf 零产品 diff，通道不适用。**终态补强**：codegen 再生产物 `_BatchTaskModel.java` 终态由三重覆盖——快照内 `TestBatchTaskDsl` 2/2 绿（再生成 mtime 20:05:06 先于快照内该测试轮次）+ Phase 7 终态补跑 `./mvnw test -pl nop-batch/nop-batch-dsl -Dtest=TestBatchTaskDsl` 2/2 绿 EXIT=0（快照 `_tmp/phase7-batch-dsl-finalstate.log`）+ E1 全仓编译绿。
3. **零触碰确认（E4）**：`git diff dce9209787..HEAD --name-only`（50 文件）过滤核对——nop-tcc / nop-api-core（io.nop.api.core.*）/ `model/*.orm.xml` / nop-auth 零命中；设施面（GlobalExecutors/ExecutorHelper/Default*Executor/CommonConfigs/CommonErrors）与绝缘层（InsulatedPeriodicTask/InsulatedPeriodicChain/PeriodicTaskHelper/IPeriodicTaskListener）diff 零命中。`_` 前缀路径命中 3 处逐一裁定：①`nop-ai/nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml`——非生成物（手维护 beans 装配文件，`_vfs` 为 VFS 资源根，plan Phase 4 显式 Targets）；②`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef`——源 schema 模型（plan 头部显式声明非生成物）；③`nop-batch/nop-batch-dsl/src/main/java/io/nop/batch/dsl/model/_gen/_BatchTaskModel.java`——**获准豁免登记**：batch.xdef `@concurrency` 注释修正（源 schema 变更，提交 `d77bcc8cc9`）触发 nop-batch-dsl codegen 的确定性再生成产物（配套提交 `ab8cb1845b`），按 AGENTS.md「改源模型 → 重新生成」路径豁免（非手编；不修生成物、只改源 xdef 的合规路径正是本产物成因），终态覆盖见上条三重证据。`.rels` 构建副作用：diff 零命中 = 终态还原成立（Phase 4 补齐提交 `8e5b9d6f42` 的还原在最终态保持）。
4. **绝缘层零回改实证**：四绝缘设施文件 diff 零命中（上条）+ `TestPeriodicTaskInsulation` 9/9 绿——Phase 7 终态补跑 `./mvnw test -pl nop-kernel/nop-commons -Dtest=TestPeriodicTaskInsulation` EXIT=0（快照 `_tmp/phase7-insulation-finalstate.log`）；Phase 5 门禁内同测试全绿（`_tmp/phase5-insulation-retry.log` 隔离复跑 9/9）。
5. **Owner doc 更新（E5）逐份结论**：①`nop-stream-connectors.md`——新增「Debezium 引擎线程模型与终结预算（1.2-C4-004）」节（专用载体命名形态/stop 有界终结预算/R1-08 边界/测试锚点），Phase 1 预登记去向落账；②`nop-task.md`——新增「延迟重试执行载体（1.5-C4-001/MA-04）」段（timer 只触发 + 显式 executor/globalWorker 载体、executor 隔离对延迟路径真实生效、簿记不在 timer 线程），Phase 2 预登记去向落账；③`service-layer.md`——**最小补注**：新增「GraphQL WS 订阅推送的执行载体（2.3-C8-001）」节（会话级有界泵 + O(1) keepalive 扫描 + 适配器 Deferred 边界）。裁定说明：该文本体只承载 BizModel service 层通用规约、无 WS 机制章节，按任务口径取最小补注入位（不复制模块实现细节，仅运行时契约 + 锚点），Phase 3 预登记去向落账；④`nop-ai.md`——三处受影响面落账：工具分发执行器缺省池契约（`nop-ai-agent-dispatch` 32/1024/daemon + cap≤0 warn + fail-loud 拒绝）、embedding 重试调度器生命周期闭合（destroy-method + 换装有界终结 + 无常驻孤儿）、shell pipeline 池包络声明（`nop-ai-shell-pipeline` 32/1024/daemon，包络=最大并发会话 × 最大管道段数），Phase 4 预登记去向落账；⑤`nop-batch.md` 核对（零改动）：F3⑥ 在位（:14 并发处理 bullet 与 :211 concurrency 配置行均承载修正后兜底池语义 + 装配护栏 + 1.7-C8-003 裁定），Phase 5 已落账核对通过；⑥`nop-message.md` 核对（零改动）：该文无 Debezium 引擎线程模型描述面（仅模块表与 Throwable 屏障契约提及 `runEngineWorker`，屏障语义未被 Phase 1 触碰）——修复不引入 drift，`No owner-doc update required`（核对结论登记）。
6. **缺口声明（R-G 治理轨）**：实际 diff 触达模块中无 `docs-for-ai/03-modules/` 条目者 = **nop-rpc-cluster、nop-rpc-core**（Phase 6 本体与载体面；`ls docs-for-ai/03-modules/` grep rpc/socket/cluster 零命中核实）——沿 plan 2496/2497 先例在本执行记录 + daily log 登记缺口，不在本批新建 owner doc；nop-socket 零产品 diff（Phase 5 评估面不改码），无需缺口声明。
7. **MA-16（R4-21）约束解除声明（勘误 7）**：cra-index P2-G52 行（L418）注记已追加——「3.1-MA-16[R4-21]『不得指向 globalTimer』约束已因 R1-03 落地解除（plan 2498 Phase 7；R1-02 设施面 + R1-03 消费方卸载/专用载体就绪，解除条件=R1-03 落地已满足）；解除后形态收窄为『阻塞/长任务体不得指向 globalTimer；满足设计 §3.1.1 豁免判据的轻量直连合法』」。batch-proposal R4-21 行（L136）**只读核对登记**：其 Deps 列既有「MA-16 不得指向 globalTimer（约束在 R1-03 落地后解除）」与本解除声明一致——不改 batch-proposal（历史裁定档案）；未向 roadmap 8.61 行写注记（勘误 7：该行 live 无 MA-16 注记，注记由 cra-index P2-G52 行承载）。
8. **scan-hollow（8 触达模块 --severity high，与 Closure Gates「受影响模块」一致：nop-message/nop-stream/nop-task/nop-service-framework/nop-ai/nop-batch/nop-cluster/nop-network）**：6 模块退出码 0（nop-message/nop-stream/nop-task/nop-batch/nop-cluster/nop-network）；**nop-service-framework（3 条）与 nop-ai（4 条）共 7 条为批前既有发现**——与 plan 2496 批前基线（`_tmp/phase5-scanhollow-nop-service-framework.txt`/`-nop-ai.txt`）逐条一致，涉事文件（`ChunkFileUploadHandler.java`、`ReActAgentExecutor.java`、`PlanReplanner.java`、`NoOpProviderFailoverQueue.java`）在本批 diff 零命中 = 与本批 diff 无关。沿 2496 先例逐条裁定：ChunkFileUploadHandler 三 UOE = 上游脚手架类 fail-fast 模式（rule #24 认可形态）；ReActAgentExecutor:1373/:1502 与 PlanReplanner:273 = 注释关键词「placeholder」误报（注释描述数据占位/计划节点领域术语，后接真实逻辑）；NoOpProviderFailoverQueue:34 = 有意 NoOp 直通队列的成文注释（交付时即自我援引 rule #24）。**本批触达面 high=0**。快照 `_tmp/phase7-scanhollow-<module>.txt`（8 份）。
9. **门禁**：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 error）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [x] 四份 Owner Doc 核对与更新结论逐份登记（含 `No owner-doc update required` 声明者）；缺口声明登记；check-doc-links --strict 退出码 0
- [x] MA-16 解除声明已落 cra-index G52 行注记（batch-proposal R4-21 行 L136 只读核对登记，勘误 7）
- [x] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.4 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）；重点抽查「载体替换后任务体真实运行于新载体」（九站点线程归属断言）与「设施/绝缘零回改」
- [ ] cra-index 台账回填：P1 四行（`C4-004（MA-06）`L149 / `C4-001（MA-04，成员 C4-008/C3-013）`L164 / `nop-service-framework-hotpath-C8-001`L210 / `nop-ai-core-nop-ai-agent-C4-011`L249）+ P2 四组行 R1-03 成分（G22 L388 / G51 L417 / G55 L421 / G66 L432）状态收敛（open → 已修/本批）；附注：1.2-C4-004 行与 1.2-C3-014 行的 R1-08 边界注记（勘误 4）、CL-025 处置注记（随 C4-011）、A4 接收侧闭合注记（设计 §3.1.6；注记载明「无仓内装配面，集成方接线由 javadoc 契约收敛」）
- [ ] roadmap 8.4 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2498-cra-phase8.4-r1-03-pool-consumers.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 9 成员行状态全部收敛 + 移交承接面（A4/A5/D3/D4/SocketServer）处置注记可核验、roadmap 8.4 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 9 项成员全部修复/评估收敛：1.2-C4-004（引擎专用载体+有界终结）、1.5-C4-001（业务体+簿记卸载，C4-008/C3-013 同 diff）、2.3-C8-001（推送卸载+A5 扇出收敛）、3.1-C4-011（缺省池收敛+cap warn）、3.1-C4-004（调度器生命周期闭合）、3.1-C4-009（缺省点收敛）、3.2-C4-004（shell 池隔离）、4.2-C4-003（executeOn+deadline）、1.7-C8-003（评估结论+护栏+xdef 修正）
- [ ] 移交承接面全部落账无静默缺处置：A4（ScheduledRecoveryManager 卸载改码）、A5（并入成员 3）、D3/D4（成员 9，两面映射闭合）、SocketServer（评估结论落账）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项有先红后绿或变异/回退论证（E2：九站点线程归属断言 + 护栏 fail-fast + deadline 链终止）
- [ ] MA-16（R4-21）约束解除声明已落对应批次注记（cra-index G52 行注记承载 + batch-proposal R4-21 行只读核对登记）
- [ ] 设施面（R1-02 产物）与绝缘层（R1-01 产物）零回改实证：设施文件 diff 零改动、`TestPeriodicTaskInsulation` 与下游绝缘回归全绿
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令（七条 `-pl ... -am` 通道）BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：roadmap 8.4 四份逐份核对与更新（或 `No owner-doc update required` 声明）；`nop-batch.md` F3⑥ 落账；缺口声明登记（E5）
- [ ] 零触碰确认（E4）：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/相邻面，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）九站点载体替换运行时真实生效（任务体线程可观测、不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2498-cra-phase8.4-r1-03-pool-consumers.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 9 成员行收敛 + roadmap 8.4 → done（E6）

## Deferred But Adjudicated

### WS 适配器层写超时（2.3-C8-001 建议②：`getBasicRemote`→`getAsyncRemote`/写超时，`JakartaWebSocketSession`/`SpringWebSocketSession` 同族）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审计明示适配器维度独立清扫（「适配器在本轮范围外，需单独立项」）；本批卸载后 timer 钉死面已闭，适配器阻塞面降级为会话线程内延迟；同族清扫面有既定批次承载（R4-16/R4-17 [G36] WS/网关束）。
- Successor Required: `yes`
- Successor Path: roadmap 8.56/8.57（R4-16/R4-17）；cra-index `nop-service-framework-hotpath-C8-001` 行注记

### SocketServer 有界池改码迁移（若 Phase 5 评估判「维持 cached + 封顶声明」时成立）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 裁定④熔断基线（上界 200 + 达界 REE）与 `maxConnections` 默认 10 封顶（plan 2497 已核）已使 cached 池消费面有界且熔断可见；评估三选一落账后无静默残留——判改码则随 Phase 5 落地、判维持则声明即收口。
- Successor Required: `no`（owner doc 声明/处置登记即收口；后续按需求立项）

### 1.5 retry 独立执行池抽象（`ITaskRuntime.getRetryExecutor` 新语义——审计建议可选支）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本批以「timer 触发 + 载体提交」等价形态满足 §3.1.1 强制卸载契约（executeOn 载体为平台既有范式，统一成本最低）；新增专用抽象属增强项，若批内已采用该形态则本项不成立。
- Successor Required: `no`（若实际占用观测显示需要隔离强化，经 backlog 立项）

### Debezium reader 桥接队列（256）反压阻塞面

- Classification: `watch-only residual`
- Why Not Blocking Closure: 专用载体落地后反压阻塞发生在引擎专用线程内（设计内形态），不再消耗共享 worker 预算；反压语义（reader 队列 256）属连接器功能面，非池治理缺陷。
- Successor Required: `no`（随 1.2 连接器批 [R2-08] 既有工单面观察）

## Non-Blocking Follow-ups

- **1.2-C3-014（R1-08）协同注记**：本批引入的 Debezium 专用载体为 R1-08 stop 终结 latch/`shutdownNow` 形态的载体前提——Phase 8 在 cra-index 两行互注，防止 R1-08 重做载体或本批越界做 latch。
- **批外 A 类站点**（A6 SysDaoNamingService、A7–A9 CLI 族）：维持 plan 2497 既有 R-G 治理轨登记，本批不承载改码。
- **G51/G55 其余成员对账**：3.1-C4-015 → R2-16、3.2-C4-008 → R1-04——Phase 8 台账回填时核对各归其批，防漏防重。
- **CL-025 处置注记**：3.1-C4-011 修复后将其 ←CL-025（无界 runAsync 面）处置结论落 cra-index L249 行注记。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2498-cra-phase8.4-r1-03-pool-consumers.md --strict` 退出码为 0
  - Anti-Hollow 检查结果：<<九站点载体运行时断言追踪>>；`scan-hollow-implementations.mjs` 退出码为 0
  - Deferred 项分类检查：<<确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
