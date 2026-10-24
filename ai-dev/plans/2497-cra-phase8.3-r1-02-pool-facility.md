# 2497 CRA Phase 8.3 — R1-02 平台池设施面批（R13 设施批：globalTimer 死配置 + 池工厂，5 项）

> Plan Status: active
> Draft Review: R1 agent_077adf2e（0 Blocker、3 Major[M1 LocalResourceLockManager 迁移分支无承载/M2 ScheduledRecoveryManager 移交漏列/M3 提交纪律缺失]+5 Minor——修订子 agent_d12c6b14 全量应用，Major 复核通过后 active）
> Design Review: 设计文档 `pool-facility-hardening.md` 独立评审 agent_e8485aca（B-1 Blocker+M-1+2m → 修订 agent_91cae018 落盘 @ 工作区 → 增量复核**通过进入实现**；评审结论已登记，实现以修订版设计为唯一基准）
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 8.3（R1-02，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L145，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-02 行（L63：finding ID 清单 5 项 / PA 授权 / 依赖标注 / exit criteria 增补）；成员发现详情 = 各单元审计目录（见 Current Baseline 逐条回读路径）；执行基线 @ `8ff0cb6d74`（分支 `concurrency-audit-remediation`，roadmap 8.1/8.2 已 done，plan 2495/2496 均 completed；本 Baseline 全部行号为该基线 live 回读值）
> Related: plan 2496（R1-01 绝缘层批，completed——已落地 `IScheduledExecutor` 绝缘核心与 `INSUL-001/002` 锚点，与本批共享 `io.nop.commons.concurrent.executor` 代码位，交互边界见 Non-Goals 与 Phase 1 裁定⑥）；plan 2495（R1-00 P0 确认批，completed）/ plan 2493/2494（批次划分与 Phase 8 展开）；**下游依赖本批**：R1-03（硬依赖，9 项消费方按本批平台族口径落地；含 4.2-C4-003 两面映射本批设施面闭合）、R4-13（硬依赖 R1-02）、R4-32（软依赖 R1-02）、R2-16（软依赖 R1-02/R1-03，globalTimer 约束解除链）、R4-21（MA-16「不得指向 globalTimer」约束在 R1-03 落地后解除）
> Protected Area 授权：本批触碰 **nop-kernel 与 nop-core-framework 内部（框架核心引擎）= plan-first Protected Area**。授权来源 = AGENTS.md「Protected Areas」表框架核心引擎行 + batch-proposal §四.3 授权落批表（「框架核心引擎（nop-kernel/nop-core-framework 内部）plan-first（设计文档+回归测试）→ R1-01、R1-02、……」行）+ 本 plan Dependencies 声明：**设计文档为本批 Phase 1 产物（`ai-dev/design/`），回归测试为各 Phase 强制义务（rule 25）**。plan-first 前置在 Phase 1 内闭环：设计文档落地并经独立 draft review 后，Phase 1 内实现项与 Phase 2–4 才可推进。本批不触碰 `nop-api-core`（io.nop.api.core.*）、不触碰 `model/*.orm.xml`、不触碰 nop-auth（nop-auth ask-first 不适用）。

## Purpose

关闭 R13 平台池设施面的 5 项已确认发现（P1 族主 2.1-C4-001 + P2 搭车 4 项：G25 三成员 2.1-C4-003/C4-004/C4-006 + G32 一成员 2.2-C4-001）：①globalTimer 死配置与构造副作用改写归位，容量语义成为成文契约；②**globalTimer 隔离/卸载方案按平台族一次裁决**（本批唯一设计裁决权威，消费方批 R1-03 与 4.2-C4-003 两面映射同口径传导）；③池工厂（`DefaultThreadPoolExecutor.newExecutor`/`ExecutorHelper.newThreadPoolExecutor`）零校验补 fail-fast、收缩方向反置（缩容必炸裸 IAE）修正、cached 共享池补上界与熔断信号；④nop-config 两处刷新链直连占用站点卸载 + JDBC 超时两面闭合。批末 roadmap 8.3 → done，5 项成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-02 = 5 项成员（P1×1 族主 + P2×4）；cra-index P1 台账 1 行（`nop-kernel-concurrency-C4-001`，裁定批次=R1-02，open）+ P2 裁决台账 2 组行（G25 三成员、G32 一成员，均回填「修→提前（R13 平台池族批次）→实际批号 R1-02」）；roadmap 8.3 行 Status=todo。
- 前置：无前置（可并行）。R1-00（plan 2495）与 R1-01（plan 2496）均 completed @ `8ff0cb6d74`——本批启动的基线门禁已满足。
- **R1-01 已落地绝缘层**：`IScheduledExecutor` 全部周期方法路由绝缘核心（`InsulatedPeriodicTask`/`InsulatedPeriodicChain`/`PeriodicTaskHelper`/`IPeriodicTaskListener`），`DefaultScheduledExecutor`/`BindScheduledExecutor` 已按绝缘契约改造（周期任务 Throwable 绝缘 + 重臂 + 失败治理）。本批设施面改造与绝缘层共享 `ExecutorHelper`/`Default*Executor` 代码位——绝缘语义（重臂/三相容/失败治理参数/拒绝路径，权威 = `ai-dev/design/concurrency/periodic-task-insulation.md`）不被本批回改，改动边界由 Phase 1 裁定⑥显式声明；`TestPeriodicTaskInsulation`（9 条）与各下游绝缘回归为共享代码位的回归门禁。
- 全平台 `globalTimer()` 生产消费方 28 文件分类终表（A 直连长任务 / B 直连短任务 / C executeOn 自救 / D 轻量直连）= `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/C4-round2.md` §①，为本批平台族裁决与 R1-03 消费方落地的同口径底表（引用时按 Baseline 勘误 4 的终裁修正口径）。

### 5 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `8ff0cb6d74` 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.1-C4-001（P1 族主） | 2.1 / nop-kernel | `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:38-49`（`globalTimer()` 构造，`:44` 读 `CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE` 默认 5 后交给 `init()`）；`ExecutorHelper.java:56-71`（`newScheduledExecutor`，`:63` `config.setMaxPoolSize(0)` 副作用改写——改写的是调用方持有的同一 config 对象，`getConfig().getMaxPoolSize()` 恒 0、`refreshConfig()` 的 `maxPoolSize>0` 分支永久失活）；`CommonConfigs.java:21-23`（配置项定义「全局定时器线程池的大小」，默认 5）；消费方终表 = C4-round2.md §①（28 文件 A/B/C/D 四类） | globalTimer 唯一容量配置为死值且被工厂副作用改写丢弃：STPE 仅 core=4（daemon）生效，运维把配置调大也不多一个线程、无任何自救通道；直连长任务（A 类）与轻量触发（C/D 类）混用同一 4 线程预算，DB stall 类故障相关性放大为全平台定时任务集体延迟 | ①死配置修复 + 副作用改写移除（配置真实生效的语义取向——STPE 容量=core 映射或配置语义重定义/废弃声明——由 Phase 1 裁定②定夺）；②globalTimer 隔离/卸载平台族规约一次裁决（A 类直连站点逐站点处置矩阵：本批内站点随批卸载、批外站点显式移交 successor——R1-03 消费方同口径，4.2-C4-003 两面映射闭合）；③容量语义/使用规约 owner doc 成文。与 WI 1.5-C4-001（MA-04，R1-03）为同设施两面（彼立使用方占用面），修复方案对接本批裁决 |
| 2 | 2.1-C4-003（P2，G25；C10-010 验收前置随本体） | 2.1 / nop-kernel（设施）+ 缺陷面调用方跨模块 | 设施：`DefaultThreadPoolExecutor.java:29-43`（`newExecutor` 两重载，poolSize/queueSize 原样透传零校验）；`ExecutorHelper.java:73-93`（`newThreadPoolExecutor`，`:81-83` `maxPoolSize<0` 分支硬编码 cached 语义）。实证缺陷面调用方（live 回读一致）：`nop-batch/nop-batch-exp/src/main/java/io/nop/batch/exp/ExportDbTool.java:150`、`ImportDbTool.java:232`（`Integer.MAX_VALUE` 队列直通→无背压 OOM 候选）、`nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptService.java:40`（`newExecutor("javascript-engine", -1, -1)` 负值静默变 0-core 无界 cached 池）；非法值路径于 `init()` 深处抛裸 JDK `IllegalArgumentException`（非 NopException、无参数上下文） | 池工厂三核心参数（poolSize/queueSize/负值语义）无一校验、无一文档化：MAX_VALUE 队列直通使背压失效；负值被静默解释为与字面意图完全不同的机件；非法值深爆无诊断上下文 | 工厂入口 fail-fast（Guard 校验：poolSize 合法域；queueSize 显式无界 vs 非法值分界与上界策略由 Phase 1 裁定③定夺）+ 违例抛类型化异常（NopException + 错误码，错误处理两档策略）+ 缺陷面 3 调用方随批修复（MAX_VALUE→有界或显式无界常量 + warn；`-1,-1` 修正为显式语义）+ 负值/无界语义 owner doc 声明；与 WI 1.7 nop-batch CL-004 同站点重叠面防重复立项（见 Non-Blocking Follow-ups） |
| 3 | 2.1-C4-004（P2，G25） | 2.1 / nop-kernel（设施）+ 消费方跨模块（面登记） | `GlobalExecutors.java:71-83`（`cachedThreadPool()`，`:77` `config.setMaxPoolSize(-1)`「不限制线程池大小」）；`ExecutorHelper.java:81-83`（`maxPoolSize<0` → `TPE(0, Integer.MAX_VALUE, 60s, SynchronousQueue)`）。消费方 7 面（live 回读零漂移）：`ExportDbTool.java:284`、`ImportDbTool.java:296`（builder.executor）、`BatchTaskBuilder.java:337`、`BatchTask.java:87`（batch 默认执行器）、`SocketServer.java:140`（每连接派发）、`ShellRunner.java:153`（CLI 场景）、`ModelBasedBatchTaskBuilderFactory.java:210`（`getExecutor(executorName)` 按名解析间接命中） | nop-cached-thread-pool 按名注册的全局共享池无上界、无配额、无熔断信号：SynchronousQueue + core=0 + max=2³¹-1，过载直接转化为线程数突发，AbortPolicy 仅在线程创建失败时触发（届时进程已濒死）；与 globalWorker（有 30 上限）治理标准不一致 | 可配置上界 + 达界 Abort 明确报错（配置项名/默认值/拒绝语义由 Phase 1 裁定④定夺；审计建议默认 ~200 量级）+ 容量语义 owner doc 声明；主可达面 = batch 高并发分片（复核修正：SocketServer 受 `maxConnections` 默认 10 封顶，外部远程触发面为配置依赖）；SocketServer 有界池迁移归消费方治理（Deferred 登记） |
| 4 | 2.1-C4-006（P2，G25；C10-010 验收前置随本体） | 2.1 / nop-kernel | `ExecutorHelper.java:106-126`（`updateThreadPool`，`:107-117` 缩容方向条件反置：新 max < 当前 core 时条件 `max > core` 为假跳过预调整 → `setMaximumPoolSize` 抛裸 `IllegalArgumentException`——注释声称处理缩小场景，条件恰好写反；放大分支先 `setCorePoolSize(newMax)` 预抬致线程创建-销毁抖动）；链路 `DefaultThreadPoolExecutor.java:57-65` 与 `DefaultScheduledExecutor.java:70-78`（`refreshConfig`，live 回读——审计引用 :62-70 已因 R1-01 改造漂移，见勘误 3）；实证可达面 = IoC bean 管理池：nop-job `nop-job-coordinator/src/main/resources/_vfs/nop/job/beans/app-engine.beans.xml:88`（`nopRpcPollScheduledExecutor`）与 `nop-job-local/.../app-local-scheduler.beans.xml:7`（`nopJobScheduledExecutor`）均引用 `DefaultScheduledExecutor`（IoC refreshConfig 派发可达）；globalWorker/globalTimer 静态 config 非热更派发对象，为机制同源理论面 | 运行期热更缩容确定性失败（裸 IAE，违反错误处理两档策略）——缩容功能实际不可用；扩容热更产生无谓线程抖动；`queueCapacity`/`daemon` 等热更面静默忽略误导运维 | 缩容方向修正（真缩容先降 core 再降 max；放大分支去预抬）+ `refreshConfig` 捕获转类型化异常（NopException）+ 热更静默忽略面（queueCapacity/allowCoreThreadTimeOut/daemon）随批裁定（补齐或成文声明，二选一）——与 C4-001 修复后的 timer 容量语义的热更一致性由 Phase 1 裁定⑤一次定稿 |
| 5 | 2.2-C4-001（P2，G32；cra-index「WI2.1-C4-001 设施面映射，先平台后使用方」） | 2.2 / nop-core-framework（nop-config） | `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:44-46`（`:45-46` 构造器直连 `globalTimer().scheduleWithFixedDelay(this::refreshConfig, ...)`；任务体 `Files.walk` 目录遍历 `:86`）；`.../jdbc/JdbcConfigSource.java:58-64`（`:60-61` 直连——审计 :54-55 已漂移，见勘误 2；任务体 `stm.executeQuery` `:148` 无 `setQueryTimeout`——审计 :122 漂移）；`.../jdbc/JdbcConfig.java:91-100`（`:94` `DriverManager.getConnection` 无 login/connect timeout） | 配置刷新任务体（文件遍历 I/O、无超时 JDBC 全量查询）直连 4 线程共享 globalTimer：DB 网络分区/NFS 挂起即占死 timer 线程（MySQL 缺省 socketTimeout=0 无限期阻塞），故障从「配置不刷新」放大为「平台定时器心跳面退化」（锁租约回收延迟、WS keepalive 停摆、RPC 超时检测延迟） | 刷新体与调度槽解耦（平台既有 `executeOn(globalWorker)` 范式或模块内 `SingleThreadConfigExecutor` SPI 形态——两站点必须同口径，由 Phase 1 裁定①定夺防各走一套）+ JDBC 超时两面（queryTimeout + 连接 connect/login timeout）使「占死」退化为「周期性失败重试」；与 R1-01 绝缘设施正交（30 点表 #4/#5 设施级覆盖已闭「链存活」面，本条闭「占用」面——卸载改造不得回退绝缘覆盖） |

### 平台设施与验收前置盘点（live 实读 @ `8ff0cb6d74`）

- **池载体清单**（`nop-kernel/nop-commons/.../concurrent/executor/`）：`nop-global-worker`（DefaultThreadPoolExecutor→StandardThreadPoolExecutor+TaskQueue(10000)，core=4/max=30/Abort+force）；`nop-global-timer`（DefaultScheduledExecutor→STPE，core=4，max 配置死值=0，DelayedWorkQueue 无界）＝成员 1 缺陷面；`nop-cached-thread-pool`（plain TPE 0/2³¹-1/SynchronousQueue）＝成员 3 缺陷面；ad-hoc `newExecutor` 工厂（无校验）＝成员 2 缺陷面；`newSingleThreadExecutor`/`newSingleThreadTimer`（调用方负责关闭，审计核对合格面，不属本批）。
- **既有修复件**：`ThreadHelper.shutdownAndAwaitTermination`（优雅关闭工具，全仓 0 调用者）——属 C4-002 修复件（归 R4-13/G27），本批不承接。
- **C10-010（池工厂校验与热更路径零测试）** = G25 三成员的验收前置（inventory-p2-c10：C4-003/C4-004/C4-006 → C10-010 随本体）：表驱动参数校验测试 + 热更 grow/shrink 双向冒烟 + 容量 0/负值跨工厂语义一致性断言，随 Phase 1 本体落地。注意 C10-010 涉及缺陷面（C4-003/C4-006/C4-011/C4-012/C1-016/C5-007）大于本批成员面——本批只承接 C4-003/C4-006 成员面义务（勘误 6）。
- **R1-01 绝缘层回归面**（本批共享代码位改动的门禁）：`TestPeriodicTaskInsulation` 9 条（nop-commons）+ Phase 2/3/4 各下游绝缘回归（nop-retry/nop-cluster/nop-message/nop-ai/nop-metadata/nop-graphql-core/nop-netty 等站点回归）——Phase 3 聚焦通道以聚合模块测试覆盖。
- **4.2-C4-003 两面映射**（batch-proposal R1-02 exit criteria 第三句）：4.2 审计 C4-003（`ClusterRpcProxyFactoryBean.java:43`/`ClusterRpcServiceInvoker.java:68` 裸 globalTimer 注入 `PollingRpcClient`，轮询体在 timer 线程执行且链无整体截止）的使用方修复归 R1-03；其复核裁定明文「修复时与平台族隔离方案统一裁决」——本批 Phase 1 裁定①即为该统一裁决，闭合 WI2.1 设施面与 WI4.2 使用方面的映射。

### Baseline 勘误（输入不一致登记）

1. **batch-proposal R1-02 模块列不完整**：列出的模块为 nop-kernel；nop-core-framework。实测成员缺陷面/消费面实际触达 **nop-batch**（ExportDbTool/ImportDbTool/BatchTask/BatchTaskBuilder/ModelBasedBatchTaskBuilderFactory）、**nop-js**（所属聚合 nop-frontend-support；JavaScriptService）、**nop-network**（SocketServer 在 nop-socket）、**nop-utils**（ShellRunner 在 nop-shell）。本 plan 验证命令按实际 diff 触达模块列全（Phase 3）；消费方站点中不构成本批修复面的（SocketServer 有界池迁移、ShellRunner 等）按 Deferred/登记处置，不默认扩批。
2. **2.2-C4-001 JdbcConfigSource 行号漂移**：审计载 :54-55（调度注册）→ live :60-61；审计载 :122（`stm.executeQuery`）→ live :148。成因 = plan 2496 R1-01 C1-021 同文件修改（`tryTriggerChange` per-callback 隔离 + `callbackErrorCount` 字段插入使后续行号整体后移）。`AbstractFileConfigSource` 行号（:45-46 直连、:86 Files.walk）与 `JdbcConfig.java:94` 零漂移。执行时以 live 回读为准。
3. **2.1 设施文件行号整体漂移（R1-01 改造所致）**：`DefaultScheduledExecutor` 新增 `periodicTasks` 登记表与绝缘周期方法（审计基线后新增 ~90 行），`refreshConfig` 由审计载 :62-70 → live :70-78；`DefaultThreadPoolExecutor.refreshConfig` live :57-65。本 Baseline 已按 live 回读值书写；执行时再以 live 为准。
4. **C4-round2 §① A 类表 `LocalResourceLockManager` 行「阻塞源 DB」为已被终裁强制修正的表述**（2.1 审计终裁节：「checkTimeout 实为内存锁表遍历（locks.values() 短临界区 synchronized），非 DB 扫描，该类无子类——修复 plan 引用终表时不得沿用错误表述」）。本 plan 全文按修正后口径引用：该站点占用面为内存轻量（live `LocalResourceLockManager.java:104-105` 注册、`:117` checkTimeout），其是否随批卸载归 Phase 1 裁定①的逐站点处置矩阵统一裁定，不预设必迁。
5. **source-anchors.md 现无 GlobalExecutors/globalTimer 锚点条目**（grep 仅 `INSUL-001/002` 命中）：roadmap 8.3 Owner Doc 列的「GlobalExecutors/globalTimer 锚点」为**本批新建**条目，非更新既有条目。
6. **C10-010 涉及面大于本批成员面**：C10-010 涉及缺陷 = C4-003、C4-006、C4-011、C4-012、C1-016、C5-007；本批只承接 C4-003/C4-006 成员面的验收前置义务，C4-011/C4-012 归 R4-13/R4-14（G27）、C1-016/C5-007 归属批次以 cra-index 为准，防越界承接。
7. **「nop-job.md 调度契约」Owner Doc 的批次内依据**：nop-job.md 现文（REST Worker rpcPoll 节 + 六、周期任务异常绝缘契约节 + 配置表）涉及 `nopRpcPollScheduledExecutor` 专用池 bean 与 `GlobalExecutors.globalTimer()` fallback——本批 C4-001 容量语义与 C4-006 热更语义修复直达该契约面，故 roadmap 将其列为 Owner Doc；Phase 3 以「核对 + 受影响面更新或 No owner-doc update required 声明」落账。

## Goals

- Phase 1 设计文档一次裁决（plan-first 前置，独立 draft review 通过）：①globalTimer 隔离/卸载平台族方案（= R1-03 消费方同口径；4.2-C4-003 两面映射闭合；A 类直连站点逐站点处置矩阵）；②globalTimer 死配置修复取向与副作用移除语义；③池工厂校验语义（fail-fast 边界、queueSize 显式无界 vs 非法值分界、违例异常类型化）；④cached 共享池上界与熔断拒绝语义；⑤收缩语义修正与热更面处置；⑥与 R1-01 绝缘层共享代码位边界。
- 四项 2.1 成员全部修复并各带回归测试（rule 25）：2.1-C4-001（死配置+副作用+容量语义成文）、2.1-C4-003（工厂 fail-fast + 缺陷面调用方随批）、2.1-C4-004（cached 上界+达界熔断信号）、2.1-C4-006（收缩方向修正+类型化异常）。
- 2.2-C4-001 站点修复：nop-config 两刷新链卸载 + JDBC 超时两面，带回归测试；与 R1-01 绝缘覆盖正交性保持。
- C10-010 成员面验收前置随本体落地：表驱动参数校验测试 + 热更 grow/shrink 双向冒烟 + 容量语义一致性断言。
- Owner doc 随批更新：`docs-for-ai/04-reference/source-anchors.md`（**新建** GlobalExecutors/globalTimer 锚点：容量语义、池工厂校验契约、cached 上界、隔离卸载规约）、`docs-for-ai/03-modules/nop-job.md`（调度契约核对：专用池 bean 契约 + globalTimer fallback 受影响面）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 授权声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不做 R1-03 消费方站点落地**：Debezium 引擎移出 globalWorker（1.2-C4-004）、WI 1.5-C4-001（MA-04 retry/delay 业务体卸载）、4.2-C4-003（裸 timer 注入改 executeOn + PollingRpcClient 整体 deadline）、1.7-C8-003 兜底 cached 池统一评估、2.3-C8-001 WS 推送卸载（ρ=N·L/40）、3.1-C4-011/C4-004/C4-009、3.2-C4-004——本批只产出平台族口径与设施面修复，站点迁移归 R1-03（硬依赖本批）。
- **不承接 2.1 相邻面**：C4-002（关闭语义死配置/destroy 不等待，归 R4-13）、C4-005（全局单例可被 destroy 不可恢复，归 R4-13）、C4-011/C4-012（死旋钮族/nop-virtual-thread 假参数，归 R4-13/R4-14）、C4-007/C4-008/C4-009/C4-010（P3 治理面，归属批次以 cra-index 为准）、C3-016（bindCancel 误取消，归 R1-04）、C3-017（scheduleWithRandomDelay， cra-index 裁定批次）。设施改造若与上述面共享代码位，改动范围以 Phase 1 设计文档显式声明边界，不默认承接。
- **不回改 R1-01 绝缘层语义**：重臂/三相容/失败治理参数/拒绝路径/30 点处置状态（`INSUL-001/002`）维持 `periodic-task-insulation.md` 裁定；共享代码位（ExecutorHelper/Default*Executor）改动以 Phase 1 裁定⑥声明边界，绝缘回归全绿为门禁。
- 不做 4.2-C4-002/C1-004（归 R2-18）；不触碰 `nop-api-core`、`model/*.orm.xml`、nop-auth（本批 Protected Area 触碰面 = nop-kernel/nop-core-framework 框架核心内部，plan-first 已声明）。
- 不做 Deferred 节所列项（SocketServer 有界池迁移、globalTimer 按用途拆分池等长期方向）。

## Scope

### In Scope

- `nop-kernel/nop-commons` 执行器设施面：`GlobalExecutors`/`ExecutorHelper`/`DefaultThreadPoolExecutor`/`DefaultScheduledExecutor` + `CommonConfigs` 配置项语义 + `nop-commons` 单元测试。
- C4-003 缺陷面调用方随批最小修复：`ExportDbTool.java:150`、`ImportDbTool.java:232`（nop-batch-exp）、`JavaScriptService.java:40`（nop-js）。
- `nop-core-framework/nop-config` 刷新链两站点（AbstractFileConfigSource/JdbcConfigSource）+ `JdbcConfig` 超时面 + nop-config 测试。
- Owner doc：source-anchors.md（GlobalExecutors/globalTimer 新建锚点）、nop-job.md（调度契约核对）；触达模块 owner doc 归属核对与缺口声明（以实际 diff 为准）。

### Out Of Scope

- Deferred 节所列项；R1-03 消费方批工单；R4-13/R4-14/R2-18 等后续批次工单；C4-002/C4-005/C4-011/C4-012 等 2.1 相邻面（见 Non-Goals）。

## Execution Plan

### Phase 1 - 平台族裁决设计文档 + nop-kernel 设施面实现（C4-001/C4-003/C4-004/C4-006）+ 单元测试（plan-first 前置）

Status: completed
Targets: `ai-dev/design/concurrency/`（新建：全局线程池与定时器设施治理设计——globalTimer 隔离/卸载平台族裁决）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`（GlobalExecutors/ExecutorHelper/DefaultThreadPoolExecutor/DefaultScheduledExecutor）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java`、`nop-batch/nop-batch-exp/.../ExportDbTool.java`/`ImportDbTool.java`、`nop-frontend-support/nop-js/.../JavaScriptService.java`、`nop-kernel/nop-commons/src/test/java/io/nop/commons/concurrent/executor/`

- Item Types: `Decision`（设计文档）、`Fix`（2.1-C4-001/003/004/006 全部为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10-010 成员面验收前置）

- [x] 设计文档落 `ai-dev/design/`（最终状态写法，rule 14：只写决策与契约，不写 Proposed-vs-Current、不写类签名代码），须含以下六项裁定：
  - [x] **裁定① globalTimer 隔离/卸载平台族方案（本批核心裁定，R1-03 消费方同口径）**：基于 C4-round2 §① 28 消费方 A/B/C/D 终表定「调度槽 vs 执行体」契约——何种任务体必须卸载（耗时/阻塞 I/O 强制 `executeOn(globalWorker())` 或等价形态）、何种轻量直连可豁免（内存检查/一次性延迟清理/触发转发，写明豁免判据）；A 类直连站点**逐站点处置矩阵**（本批内站点：nop-config 两站点归 Phase 2、`LocalResourceLockManager` 按终裁修正口径裁定迁移或豁免——**两分支落地形态预设，无临场自加执行项空间**：默认**豁免** + owner doc（或处置登记）声明其占用面为内存锁表遍历轻量、不构成 DB 阻塞（终裁修正口径）；仅当设计裁定判**迁移**时，须回补执行项「`LocalResourceLockManager.checkTimeout` 卸载改码（live :104-105/:117）」纳入本 Phase Targets（nop-commons `concurrent/lock/impl` 包）并随设施面提交；批外站点：JsonRpcWebSocketHandler keepalive、SysDaoNamingService cleanup、ScheduledRecoveryManager（recovery scan，`nop-ai/nop-ai-agent/.../ScheduledRecoveryManager.java:361`，生产 IoC 注入 globalTimer——勘误 4 终裁口径下第三个 DB 相关站点；移交 successor 候选 = 3.1 单元批（R2-16）或 R1-03 口径传导，由本裁定定夺）、CLI 族等逐站点显式移交 successor ownership——R1-03/对应单元批/R-G 治理轨，禁止静默不处置）；与 R1-03 全部 9 项消费方工单（含 4.2-C4-003 两面映射、1.7-C8-003 兜底池统一评估）的口径传导声明；与 MA-16「不得指向 globalTimer」约束解除链的关系注记；逐站点移交结论落 cra-index 对应行注记（或 roadmap 行指针），移交载体可独立核验。
  - [x] **裁定② 死配置修复取向**：`global-timer.maxPoolSize`（默认 5）真实生效的具体语义（JDK STPE 仅 core 生效——映射到 corePoolSize、或配置语义重定义、或废弃声明+等价新配置，三选一并给出运维迁移含义）+ `ExecutorHelper.newScheduledExecutor` 副作用改写移除（**不得改写调用方持有的 config 对象**——`getConfig().getMaxPoolSize()` 恒 0 与 `refreshConfig` 分支永久失活两面一并闭合）+ 对既有单例（globalTimer）与 `newSingleThreadTimer` 等同工厂消费方的行为影响声明（source-compat：对外 API 零签名变更）。
  - [x] **裁定③ 池工厂校验语义（fail-fast）**：poolSize 合法域（>0）；queueSize 显式无界 vs 非法值分界（显式无界是否保留、以何种常量/参数形态表达、是否上界封顶 + warn）；违例异常类型 = NopException + 错误码（错误处理两档，杜绝裸 IAE 与静默 cached 语义）；与既有 11 调用点的兼容矩阵（缺陷面 3 站点随批修复：ExportDbTool:150/ImportDbTool:232 MAX_VALUE、JavaScriptService:40 `-1,-1`；合格 8 站点零改动声明）；`ExecutorHelper.newThreadPoolExecutor` 的 `maxPoolSize<0` 分支与 GlobalExecutors.cachedThreadPool 的关系（裁定④的上界是否改变该分支语义须一并声明）。
  - [x] **裁定④ cached 共享池上界与熔断语义**：新配置项名/默认值（审计建议 ~200 量级，最终值随裁决）+ 达界拒绝语义（Abort 明确报错——熔断信号必须在「进程濒死前」可见）+ 按名解析通道（`ModelBasedBatchTaskBuilderFactory.getExecutor`）与 batch 消费方的语义影响声明 + 7 消费方面行为变化核对。
  - [x] **裁定⑤ 收缩语义修正与热更面处置**：`updateThreadPool` 缩容方向修正（真缩容先降 core 再降 max）+ 放大分支去预抬（消除线程创建-销毁抖动）+ `refreshConfig` 转类型化异常 + 热更静默忽略面（queueCapacity/allowCoreThreadTimeOut/daemon）补齐或成文声明二选一 + 与裁定② timer 容量语义的热更一致性（DefaultScheduledExecutor 热更路径在死配置修复后的行为）。
  - [x] **裁定⑥ 与 R1-01 绝缘层共享代码位边界**：ExecutorHelper/Default*Executor 改动不得回改绝缘语义（重臂/三相容/失败治理参数/拒绝路径维持 periodic-task-insulation.md）；`TestPeriodicTaskInsulation` 9 条 + 下游绝缘回归为回归门禁；`DefaultScheduledExecutor.refreshConfig`/destroy 改动对绝缘链登记表（`periodicTasks`）无影响声明。

  （六裁定载体 = `ai-dev/design/concurrency/pool-facility-hardening.md` 修订版，评审修订 agent_91cae018 落盘 @ `8331251d38`，增量复核 agent_e8485aca 通过进入实现——见 plan 头部 Design Review 行。）
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析）通过后，本 Phase 后续实现项与 Phase 2 方可启动（plan-first 门禁）——记录于本 plan 头部 Draft Review 行或 daily log
- [x] 2.1-C4-001 设施面修复：死配置 + 副作用改写移除（按裁定②实施；`globalTimer()` 消费方零 API 变更）
- [x] 2.1-C4-003 设施面修复：`newExecutor`/`newThreadPoolExecutor` 工厂入口校验 fail-fast（按裁定③）+ 缺陷面调用方随批修复（ExportDbTool:150 / ImportDbTool:232 的 `Integer.MAX_VALUE` 处置、JavaScriptService:40 的 `-1,-1` 修正）
- [x] 2.1-C4-004 设施面修复：cachedThreadPool 可配置上界 + 达界拒绝语义（按裁定④）
- [x] 2.1-C4-006 设施面修复：`updateThreadPool` 缩容方向修正 + 放大分支去预抬 + `refreshConfig` 类型化异常 + 热更面处置（按裁定⑤）
- [x] 本体单元测试（rule 25 显式覆盖 + C10-010 成员面验收前置）：
  - [x] 表驱动参数校验测试：非法组合（poolSize≤0、非法 queueSize）断言类型化异常（非裸 IAE、非静默 cached 语义）——验证裁定③（`TestThreadPoolFacilityHardening.testFactoryParamValidationTableDriven_C4_003`，11 违例行全 NopException 同错误码）
  - [x] 热更 grow/shrink 双向冒烟：缩容后池仍可执行任务（修复前缩容必炸 IAE 的行为对照）；扩容无线程抖动断言（或按裁定⑤记录不可观测说明）——验证裁定⑤（`testShrinkHotUpdateReordersCoreBeforeMax_C4_006` + `testScheduledHotUpdateCoreOnlyWithoutMaxClamp_C4_006`；扩容抖动为 setter 间瞬态、公开 stats 不可观测，按裁定⑤以最终态一致为断言口径并在测试 javadoc 记录）
  - [x] globalTimer 容量语义断言：配置值→实际池容量传导成立、config 对象不再被副作用改写（`getConfig()` 语义修复的行为钉）——验证裁定②（`testGlobalTimerCorePoolSizeConfigTransmits_C4_001` + `testScheduledExecutorFactoryDoesNotRewriteCallerConfig_C4_001`）
  - [x] cached 池达界拒绝断言：达到上界后的提交获得明确拒绝信号（熔断可见）——验证裁定④（`testCachedFormBoundedWithSaturationSignal_C4_004`）
  - [x] 缺陷面调用方回归：ExportDbTool/ImportDbTool/JavaScriptService 以修复后参数构造成功且语义与裁定③一致（`TestDbToolExecutorContract` 2 条 + `TestJavaScriptServiceExecutorContract` 1 条 + `TestJavaScriptService` 5 条真实服务构造链）
  - [x] 容量 0/负值跨工厂语义一致性断言：globalTimer 传导 vs `newScheduledExecutor`/`newThreadPoolExecutor` 对容量 0/负值输入的语义以表驱动矩阵统一钉（同一输入跨工厂入口行为可对照，不静默分叉）——C10-010 成员面验收前置项（`testCapacityZeroNegativeCrossFactoryConsistencyMatrix_C10_010`）
- [x] **接线验证**（rule 23）：≥1 条断言证明修复后的配置/校验路径在真实构造链上生效（如 `GlobalExecutors.globalTimer()` 单例经修复后 config 不再被改写、容量可观测；`newExecutor` 校验在 `DefaultThreadPoolExecutor.init()` 真实链上触发）——证据见执行记录 §4
- [x] **无静默跳过**（rule 24）：校验失败路径显式抛类型化异常；热更不支持的字段按裁定⑤显式处置（补齐或成文声明），不得保留无告示的静默忽略
- [x] **提交纪律**：设计文档与实现分离提交（Phase 1 执行顺序 = 设计文档先落盘提交 → 独立 draft review → 实现按工单逐项提交，单会话超时可无损接续）；nop-kernel 设施面一提交；三缺陷面调用方（nop-batch-exp / nop-js）随批提交并在 commit message 声明与设施提交的 commit 关系；**C10-010 成员面测试与 C4-003/C4-006 本体同一 commit**（E2 同 diff 核验依据）；Phase 2 的 nop-config 站点修复一提交（本 Phase 内已达成设计文档先行、设施一提交 `7262812593`、调用方随批 `6309d3a5e0` 且 message 声明 commit 关系、C10-010 测试与本体同 commit；Phase 2 nop-config 提交纪律由 Phase 2 自身 checklist 承载）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计文档存在于 `ai-dev/design/`、为最终状态写法、含裁定①–⑥全部项，且独立 draft review 记录在案（plan 头部 Draft Review 行或 daily log）
- [x] 2.1-C4-001/C4-003/C4-004/C4-006 四项 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10-010 成员面（表驱动校验 + grow/shrink 双向 + 语义一致性）覆盖在案
- [x] 必红证据（E2）：至少 C4-006（修复前缩容 IAE 红）、C4-003（修复前非法值静默/深爆红）、C4-001（修复前 config 恒 0 红）、C4-004（cached 上界达界拒绝断言天然可变异红）四项有先红后绿或变异/回退论证留痕；其余工单按 E2 逐条可复现口径执行（四项全变异红→修复绿，快照 `_tmp/phase1b-c4-001-dead-config.log` / `_tmp/phase1b-c4-003-zero-validation.log` / `_tmp/phase1b-c4-004-unbounded.log` / `_tmp/phase1b-c4-006-shrink-iae.log`，各含 RED 与 GREEN 两段）
- [x] 接线验证证据登记（哪条构造链、哪条断言证明运行时生效）——见执行记录 §4
- [x] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS；nop-batch-exp / nop-js 所属聚合模块 focused 测试 BUILD SUCCESS（以实际触达为准登记）——nop-kernel EXIT=0（快照 `_tmp/phase1b-nop-kernel-gate.log`，含 TestThreadPoolFacilityHardening 7 条 + TestPeriodicTaskInsulation 9 条）；nop-batch `-am` EXIT=0（快照 `_tmp/phase1b-nop-batch-gate.log`，含 TestDbToolExecutorContract 2 条 + TestExportDbTool 回归）；nop-js 聚合模块 pom 已注释 nop-js（reactor 外模块），以 `./mvnw test -f nop-frontend-support/nop-js/pom.xml` EXIT=0 登记（快照 `_tmp/phase1b-nop-js-gate.log`，含 TestJavaScriptServiceExecutorContract 1 条 + TestJavaScriptService 5 条）；nop-frontend-support 聚合 `-am` EXIT=0（快照 `_tmp/phase1b-nop-frontend-support-gate.log`）；nop-dbtool 模块在仓内不存在（任务书「若触达」条件不成立），ExportDbTool/ImportDbTool 调用点归属 nop-batch-exp 已随批验证
- [x] Owner doc 裁定：设施契约/容量语义锚点内容定稿归 Phase 3 统一落账（本 Phase 显式登记该去向，或随批最小更新）；否则显式写 `No owner-doc update required` + 理由（登记：GlobalExecutors/globalTimer 新建锚点条目（容量语义/工厂校验契约/cached 上界与熔断/隔离卸载规约/LocalResourceLockManager 豁免声明）+ nop-job.md 调度契约核对，内容源 = 设计文档 §3.1–§3.5，归 Phase 3 统一落账——设计文档 §五 已声明 owner doc 去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Phase 1 执行记录（2026-10-24，实现子代理）

1. **设计评审引用**：设计文档修订版（评审修订 agent_91cae018 落盘 @ `8331251d38`，增量复核 agent_e8485aca 通过）为实现唯一基准；六裁定 ①–⑥ 全按其 §3.1–§3.6 实施，无设计偏差。
2. **实现面（设施一提交 `7262812593`）**：`CommonConfigs`（新键 `nop.commons.concurrent.global-timer.corePoolSize` 默认 4 + 旧键 @Deprecated 废弃声明 + 新键 `nop.commons.concurrent.cached-pool.maxPoolSize` 默认 200）；`CommonErrors`（`ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM` / `ERR_CONCURRENT_CACHED_POOL_SATURATED` + `ARG_MAX_POOL_SIZE`）；`ExecutorHelper`（newScheduledExecutor 去副作用 + core≥1 校验；newThreadPoolExecutor cached 分支构造期上界 + CachedPoolRejectHandler + Standard 分支五项校验 + MAX_VALUE 队列 WARN；updateThreadPool 收缩重排）；`DefaultThreadPoolExecutor`（三/四参重载校验 + refreshConfig 钳制 WARN + catch IAE 转 NopException）；`DefaultScheduledExecutor`（refreshConfig scheduled 专用路径：core-only 对齐、无 max 钳制、core≤0 类型化异常）；`GlobalExecutors`（globalTimer 新键解析 `resolveGlobalTimerCorePoolSize` + 旧键回读单次 WARN；cachedThreadPool `-1` 标记维持零改动）。错误码消息文案沿 `CommonErrors` 既有 zh-CN 惯例（设计文档 §3.3「消息文案按 CommonErrors 既有惯例，实现期定稿」）。
3. **调用方随批（`6309d3a5e0`，message 声明与设施 commit 关系）**：ExportDbTool/ImportDbTool MAX_VALUE→10000（有界，与 globalWorker TaskQueue 一致；吞吐调优归 1.7 CL-004 对账）；JavaScriptService `-1,-1`→配置重载显式 cached 形态（core=0/max=-1，保留 0-core cached 实际语义 + 获得熔断上界）。
4. **接线验证证据（rule 23）**：①`testGlobalTimerCorePoolSizeConfigTransmits_C4_001` 尾段——`GlobalExecutors.globalTimer()` 平台共享单例：`resolveGlobalTimerCorePoolSize()`（AppConfig 原始读→构造）== `getConfig().getCorePoolSize()` == `stats().getCorePoolSize()`，且 `getConfig().getMaxPoolSize()==20`（惰性默认，不再是副作用改写后的 0）；②`testFactoryParamValidationTableDriven_C4_003` 全部 `DefaultThreadPoolExecutor.newExecutor(...)` 三参重载经 `newExecutor(config)→init()→newThreadPoolExecutor` 真实构造链触发校验；③`testCachedFormBoundedWithSaturationSignal_C4_004` cached 分支上界/达界 REE 在真实构造 + 并发提交链上断言；④`TestJavaScriptService`（真实 service.start()→doStart 修复后构造链）5 条全绿。
5. **必红证据（E2，四项全变异红→修复绿）**：C4-001（`resolveGlobalTimerCorePoolSize` 恒 4 + `setMaxPoolSize(0)` 复置 → 3 失败：expected 7 was 4 / expected 9 was 0 / expected 20 was 0 → 恢复绿）；C4-003（双校验体禁用 → poolSize=0 深爆裸 `IllegalArgumentException` 而非 NopException，2 失败 → 恢复绿）；C4-004（cached 分支复置 `Integer.MAX_VALUE` + 去 handler → expected 3 was 2147483647 → 恢复绿）；C4-006（updateThreadPool 复置基线反置条件 → 缩容 `setMaximumPoolSize` IAE（refreshConfig 转类型化异常捕获留痕）→ 恢复绿）。快照 = Exit Criteria 第三条列出的四份 log。
6. **绝缘层零回改自证**：`InsulatedPeriodicTask`/`InsulatedPeriodicChain`/`PeriodicTaskHelper`/`IPeriodicTaskListener` 四文件 git diff 零改动；`DefaultScheduledExecutor` diff 仅 refreshConfig 方法体（`periodicTasks` 登记表/`trackPeriodicTask`/`destroy()` 拒绝路径/周期方法接线零触碰，grep diff 验证）；`ExecutorHelper.schedule`/`submit`（bindCancel 面）零改动；`TestPeriodicTaskInsulation` 9 条全绿（nop-kernel 门禁 log）。
7. **验证与门禁**：`./mvnw test -pl nop-kernel -am` EXIT=0；`./mvnw test -pl nop-batch -am` EXIT=0；`./mvnw test -f nop-frontend-support/nop-js/pom.xml` EXIT=0（nop-js 为 reactor 外模块，聚合 pom 已注释其 module 声明）；`./mvnw test -pl nop-frontend-support -am` EXIT=0；`node ai-dev/tools/check-doc-links.mjs --strict` EXIT=0；pre-commit Java lint（nop-cli gate）两提交均 0 violations。nop-dbtool 模块不存在，任务书「若触达」不成立。
8. **零触碰确认**：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物（`git status` 过滤验证）；`nop-ooxml-xlsx/samples/generated-pie-chart/_rels/.rels` 为构建副作用（plan 2496 既有登记同因）已还原。
9. **执行备注（非缺陷）**：nop-commons testCompile 曾现 plexus `ConcurrentModificationException`，根因 = 测试草稿将 `Runnable` 表达式直接传入 `assertThrows(Class,Executable,String)`（javac 错误恢复路径缺陷，最小复现归档于会话记录）；改为方法引用 `rejection.call()::run` 后消失，纯编译错误路径正常报错不受影响。设施代码无关联。

### Phase 2 - 2.2-C4-001 nop-config 刷新链隔离/超时站点修复

Status: completed
Targets: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java`、`.../jdbc/JdbcConfigSource.java`、`.../jdbc/JdbcConfig.java`、`nop-core-framework/nop-config/src/test/...`

- Item Types: `Fix`（2.2-C4-001 已确认 live defect）、`Proof`（回归测试）

- [x] 刷新体与调度槽解耦：AbstractFileConfigSource 与 JdbcConfigSource 两站点按 Phase 1 裁定①的同一口径实施（`executeOn(globalWorker)` 范式或模块内 `SingleThreadConfigExecutor` SPI 形态——**两站点同口径**，防各走一套）；站点行号以 live 回读为准（勘误 2）
- [x] JDBC 超时两面：`JdbcConfig` 查询路径 queryTimeout + 连接路径 connect/login timeout（`DriverManager.setLoginTimeout` 或 URL 参数形态随批最小定夺），使 DB 分区「占死」退化为「周期性失败重试」；连接超时形态须显式声明作用域选择及波及面——全局（`DriverManager.setLoginTimeout`，JVM 全局静态，波及同进程全部 DriverManager 连接）vs 连接级（URL 参数，仅本配置源连接）——二选一并写明选择理由
- [x] 与 R1-01 绝缘设施正交性保持：刷新链仍受 30 点表 #4/#5 设施级覆盖（绝缘覆盖不回退）；卸载后任务体在 worker 池的失败/取消语义与绝缘契约对齐声明（设计文档裁定①矩阵内落账）
- [x] 回归测试（rule 25）：①卸载后断言刷新任务体不再运行于 timer 线程（或按裁决形态断言经 Bind 载体 hop 到 worker 池）；②JDBC 超时生效断言（超时参数传导到 Statement/连接——stub 或可观测行为）；③既有 `TestJdbcConfigSourceCallbackIsolation`（C1-021 回归 4 条）与文件源测试（TestFileConfigSourceRefresh/TestFileConfigSourceWalk）零回归
- [x] **端到端验证**（rule 22，如适用）：构造→注册 globalTimer 载体→刷新轮次→worker 池执行→变更回调 的完整链路测试（或说明以接线验证 + 站点回归组合覆盖的理由）
- [x] **接线验证**（rule 23）：卸载链路运行时断言（刷新轮次任务体执行线程可观测为 worker 池线程，非 timer 线程）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 2.2-C4-001 两面（卸载 + 超时）landed，各带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：卸载面至少一项先红后绿或变异/回退论证留痕
- [x] `./mvnw test -pl nop-core-framework -am` BUILD SUCCESS
- [x] Owner doc 裁定：nop-config 无独立 03-modules 条目 → 配置源契约归属 doc（`ioc-and-config.md` 等）核对：若有契约变化随批最小更新，否则显式登记 `No owner-doc update required` + 理由；缺口声明归 Phase 3 统一
- [x] `ai-dev/logs/` 对应日期条目已更新

#### Phase 2 执行记录（2026-10-24，实现子代理）

1. **卸载面（两站点同口径，`executeOn(globalWorker())`，按设计 §3.1.4 拒绝 SingleThreadConfigExecutor SPI 形态）**：`AbstractFileConfigSource`（构造器调度注册改 `GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker()).scheduleWithFixedDelay(...)`，原 :45-46 直连消除）与 `JdbcConfigSource`（构造器同形态，原 :60-61 直连消除）——globalTimer 只承载触发（调度槽），刷新任务体（Files.walk 目录遍历 / JDBC 全量查询）hop 到 globalWorker 执行。`refreshFuture.cancel(false)`/`refreshTaskFuture.cancel(false)` 关闭语义零改动（InsulatedPeriodicChain cancel 契约：在飞轮次自然执行完毕、后续轮次不再触发）。
2. **JDBC 超时两面（m5 作用域声明）**：作用域裁定 = **连接级 + 语句级**，只作用于本配置源自身的连接与查询；**拒绝 `DriverManager.setLoginTimeout` 全局静态**——JVM 全局静态波及同进程全部 DriverManager 连接（跨模块副作用），且 login timeout 只覆盖建连、不覆盖 socket 读挂起（审计指认的占死根因 = MySQL 缺省 socketTimeout=0 无限读阻塞）。落地：①连接级 = `JdbcConfig` 新增 `connectTimeout`（默认 10s）/`socketTimeout`（默认 60s）Duration 配置键（`nop.config.jdbc.connect-timeout`/`socket-timeout`），`createConnection` 经 `buildConnectionUrl()` 以 URL 参数（毫秒，MySQL Connector/J/MariaDB 参数名）传导；URL 已带同名参数不重复追加，显式配置 ≤0/null = 停用该面（非 MySQL/MariaDB 驱动参数名不同的逃生通道）；②语句级 = `JdbcConfig.queryTimeout`（默认 60s，`nop.config.jdbc.query-timeout`），`JdbcConfigSource.loadConfig` 经 `applyQueryTimeout(stm)` 传导到 `Statement.setQueryTimeout`（秒粒度，正值不足 1s 向上取整，防亚秒配置静默失效为无超时）。默认有限值使 DB 分区默认（而非运维显式配置才）从「占死」退化为「周期性失败重试」。契约载体 = `JdbcConfig` 类内 javadoc。
3. **绝缘正交性（C1-021 零回改自证）**：`JdbcConfigSource` diff 仅两处——构造器调度行（卸载）与 `loadConfig` 语句行（超时传导）；`tryTriggerChange`/`callbackErrorCount` per-callback 隔离体零触碰（git diff 核验）。卸载后任务体执行路径 = timer 触发 → BindScheduledExecutor hop → worker 线程执行 `refreshConfig` → `tryTriggerChange` 回调循环，隔离语义（捕获边界 Throwable、callbackErrorCount<10 抑制、整轮成功清零）不因执行线程改变；`TestPeriodicTaskInsulation` 9 条全绿（Bind 载体周期方法路由绝缘核心，30 点表 #10–13 设施级覆盖保持）。
4. **回归测试（rule 25，全部测试名带工单标识 `_C4_001`）**：`TestFileConfigSourceWorkerOffload`（2 条：调度轮次任务体线程接线断言 + 端到端调度→worker→变更回调链）、`TestJdbcConfigSourceWorkerOffload`（2 条：卸载链端到端 + 坏回调隔离共存的 C1-021 正交性链路；语句级 queryTimeout 接线断言）、`TestJdbcConfigTimeout`（7 条：默认有限值行为钉、URL 参数追加/续接/不重复追加/显式停用、queryTimeout 按秒传导 + 亚秒取整、语句级停用面）。
5. **端到端与接线验证（rule 22/23）**：两站点各 1 条真实 globalTimer/globalWorker 单例端到端链路测试（构造 → 注册载体 → 调度轮次 → worker 池执行 → 变更快照回写 → 变更回调触发）；接线断言 = 刷新任务体/回调执行线程名 `startsWith("nop-global-worker")` 且非 `nop-global-timer`（线程名可观测）。
6. **必红证据（E2，先红后绿）**：4 条新测试先对修复前基线跑红（快照 `_tmp/phase2-c4-001-offload-red.log`：文件源任务体线程 `nop-global-timer-1-3`、JDBC 任务体线程 `nop-global-timer-1-2`、queryTimeout 传导 `null`——缺陷签名 = 直连 timer 线程 + 无超时）→ 修复后全绿（`_tmp/phase2-c4-001-offload-green.log`，Tests run: 11, Failures: 0）。
7. **验证与门禁**：`./mvnw test -pl nop-core-framework -am -Dsurefire.failIfNoSpecifiedTests=false` EXIT=0（快照 `_tmp/phase2-nop-config-gate.log`），含既有回归零失败：`TestJdbcConfigSourceCallbackIsolation` 4 条（C1-021）、`TestFileConfigSourceRefresh` 3 条、`TestFileConfigSourceWalk` 1 条、`TestPeriodicTaskInsulation` 9 条（nop-commons，绝缘门禁）+ 本 Phase 新增 11 条。
8. **Owner doc 裁定**：`No owner-doc update required`（本 Phase）——`ioc-and-config.md` 现文仅覆盖配置优先级/环境变量映射/`nop.datasource.*` 数据源绑定，不承载配置源刷新链与 `nop.config.jdbc.*` 键契约（grep 全 docs-for-ai 零命中）；卸载契约的 owner doc 归宿 = Phase 3 source-anchors.md 新建 GlobalExecutors/globalTimer 锚点条目（隔离卸载规约行，内容源 = 设计 §3.1），超时契约已随 `JdbcConfig` javadoc 就地成文；nop-config 无 03-modules 条目的缺口声明按 plan 既定归 Phase 3 统一登记。
9. **零触碰确认**：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物（git status 过滤验证）；Phase 1 设施面（GlobalExecutors/ExecutorHelper/Default*Executor/CommonConfigs/CommonErrors）零改动；R1-01 绝缘载体（InsulatedPeriodicTask/InsulatedPeriodicChain/PeriodicTaskHelper/IPeriodicTaskListener）零改动。

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: 受影响聚合模块全量测试；`docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/03-modules/nop-job.md`；触达模块 owner doc 归属核对

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-kernel -am`、`./mvnw test -pl nop-core-framework -am` BUILD SUCCESS 且登记；实际触达模块（勘误 1 清单中实际产生 diff 者）focused 测试逐条登记（模块/测试数/退出码/快照路径）
- [ ] 零触碰确认：本批产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记）
- [ ] Owner doc 更新：`source-anchors.md` **新建** GlobalExecutors/globalTimer 锚点条目（容量语义、池工厂校验契约、cached 上界与熔断语义、隔离卸载规约——内容源 = Phase 1 设计文档裁定，派生摘要标注「以设计文档为准」，按边界规则不直接链接 ai-dev 路径）；`nop-job.md` 调度契约核对（`nopRpcPollScheduledExecutor`/`nopJobScheduledExecutor` 专用池 bean 契约、RpcPollTaskManager globalTimer fallback、六、绝缘契约节——受 C4-001 容量语义与 C4-006 热更语义影响面更新，或登记 `No owner-doc update required` + 理由）
- [ ] 缺口声明：触达模块中无 `docs-for-ai/03-modules/` 条目者（nop-js/nop-socket/nop-shell 等，以实际 diff 为准）按 R-G 治理轨先例登记（plan 执行记录 + daily log），不在本批新建 owner doc
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（受影响模块全集与 Closure Gates 一致）退出码 0；若有批前既有发现，逐条裁定登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] source-anchors 锚点条目落地 + nop-job.md 核对结论登记；check-doc-links --strict 退出码 0
- [ ] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.3 行、cra-index 台账

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：P1 台账 `nop-kernel-concurrency-C4-001` 行 + P2 裁决台账 G25（C4-003/C4-004/C4-006 三成员）/G32（2.2-C4-001）状态收敛（open → 已修/本批）
- [ ] roadmap 8.3 行 Status → done
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2497-cra-phase8.3-r1-02-pool-facility.md --strict` 退出码 0；scan-hollow 全绿复核
- [ ] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [ ] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 5 成员行状态全部收敛、roadmap 8.3 = done
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 5 项成员全部修复：2.1-C4-001（设施主体：死配置+副作用+容量语义）、2.1-C4-003（工厂 fail-fast+缺陷面调用方）、2.1-C4-004（cached 上界+熔断）、2.1-C4-006（收缩语义）、2.2-C4-001（卸载+JDBC 超时）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；关键项有先红后绿或变异/回退论证（E2：C4-006 缩容 IAE、C4-003 校验缺失、C4-001 config 恒 0、C4-004 达界拒绝、2.2-C4-001 卸载面）
- [ ] C10-010 成员面验收前置随本体落地（表驱动校验 + 热更 grow/shrink 双向 + 容量语义一致性）；C10-010 其余涉及面（C4-011/C4-012/C1-016/C5-007）确认未越界承接
- [ ] globalTimer 隔离/卸载平台族裁决成文（Phase 1 设计文档裁定①）且与 R1-01 设计语言（periodic-task-insulation.md）口径一致；R1-03 同口径传导声明在案（含 4.2-C4-003 两面映射闭合、A 类直连站点逐站点处置矩阵无静默缺处置）
- [ ] 绝缘层零回改实证：R1-01 绝缘语义与 `TestPeriodicTaskInsulation` 回归全绿（共享代码位改动门禁）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-kernel -am`、`./mvnw test -pl nop-core-framework -am` BUILD SUCCESS（E1/E3）
- [ ] Owner doc 更新完成：source-anchors.md（GlobalExecutors/globalTimer 新建锚点）、nop-job.md（调度契约核对结论）；缺口声明登记（E5）
- [ ] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/消费方治理项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）修复后的配置/校验/卸载路径运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile`（受影响模块）
- [ ] `./mvnw test`（受影响模块）
- [ ] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2497-cra-phase8.3-r1-02-pool-facility.md --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 5 成员行收敛 + roadmap 8.3 → done（E6）

## Deferred But Adjudicated

### SocketServer 每连接派发改有界池 + 队列策略（2.1-C4-004 消费方面）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设施上界 + 达界拒绝落地后，cached 池的熔断信号已存在（本批缺陷定义的「无熔断」面闭合）；SocketServer 受 `maxConnections` 默认 10 封顶（2.1 审计复核修正），外部远程触发面为配置依赖而非默认可达；消费方站点迁移归 R1-03 消费方治理轨，符合「先平台后使用方」批次纪律。
- Successor Required: `yes`
- Successor Path: roadmap 8.4（R1-03）消费方治理；cra-index `2.1-C4-004` 行注记

### globalTimer 按用途拆分池（config-refresh / keepalive / cleanup 长期方向，2.1-C4-001 审计建议 (4)）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本批以「容量语义成文 + 卸载规约 + A 类站点处置矩阵」收口设施面；按用途拆分为长期架构优化，无既定工单，且其前提（规约落地后的实际占用观测）依赖 R1-03 消费方落地。
- Successor Required: `no`（长期方向；若 R1-03 落地后观测显示仍需隔离强化，经 backlog 立项）

### updateThreadPool 热更面补齐（queueCapacity / allowCoreThreadTimeOut / daemon 热更）——仅当 Phase 1 裁定⑤选择「成文声明不补齐」时成立

- Classification: `watch-only residual`
- Why Not Blocking Closure: 裁定⑤若判「补齐」则该项随 Phase 1 本体落地、不入本节；若判「声明不补齐」则残留面已由 owner doc 显式声明（消除误导），静默忽略面不存在。
- Successor Required: `no`（owner doc 声明即收口；后续按需求立项）

## Non-Blocking Follow-ups

- **与 WI 1.7 nop-batch CL-004 防重复立项对账**：C4-003/C4-004 缺陷面调用方（ExportDbTool/ImportDbTool 的 MAX_VALUE 队列与 cachedThreadPool 使用）在本批修复后，CL-004 工单面（exp 线程池无界队列+cachedThreadPool 切换）的剩余收敛义务需在其归属批次对账（cra-index cross-ref 注记），防止两批各修一半或重复立项。
- **28 消费方终表批外 A 类站点迁移**（JsonRpcWebSocketHandler keepalive、SysDaoNamingService cleanup、ScheduledRecoveryManager recovery scan（`nop-ai/nop-ai-agent/.../ScheduledRecoveryManager.java:361`，生产 IoC 注入 globalTimer——勘误 4 终裁口径下第三个 DB 相关站点；successor 候选 = 3.1 单元批（R2-16）或 R1-03 口径传导）、CLI 族等无工单承载面）：按 Phase 1 裁定①处置矩阵移交 R1-03/对应单元批/R-G 治理轨；本批负责把口径与矩阵写清，不承载批外站点改码。
- **LocalResourceLockManager.checkTimeout 直连站点**：占用面为内存轻量（终裁修正口径）；是否随批卸载随 Phase 1 裁定①矩阵定夺——两分支落地形态预设以裁定①为准（默认豁免 + owner doc/处置登记声明内存轻量、不构成 DB 阻塞；判迁移则回补执行项「checkTimeout 卸载改码（live :104-105/:117）」随设施面提交）。
- **C10-010 非成员涉及面**（C4-011/C4-012/C1-016/C5-007 的测试保护缺口）：随其本体归属批次（R4-13/R4-14 等）落地，本批仅在测试命名/形态上保持可复用。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence: <<每条 Exit Criterion 与 Closure Gate 的验证结果；check-plan-checklist --strict 退出码；Anti-Hollow 检查结果；Deferred 项分类检查>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
