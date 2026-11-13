# 平台池设施加固设计（globalTimer 容量语义与池工厂契约）

> Status: active
> Last Reviewed: 2026-10-24（plan 2497 Phase 1a 产出；评审修订 agent_91cae018 落盘（B-1/M-1/m-1/m-2）；增量复核 agent_e8485aca 通过进入实现）
> Source: plan 2497（`ai-dev/plans/2497-cra-phase8.3-r1-02-pool-facility.md`）Phase 1 裁定①–⑥；发现事实源 = C4-round2 §① 28 消费方分类终表 + §② 池参数纪律矩阵（`ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/C4-round2.md`，引用按 plan Baseline 勘误 4 终裁修正口径）+ plan Current Baseline 五成员 live 回读
> 实现锚：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/`（GlobalExecutors / ExecutorHelper / DefaultThreadPoolExecutor / DefaultScheduledExecutor）+ `nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java`

## 一、设计结论

1. **裁定①（平台族契约）**：globalTimer 是全平台共享的**调度槽**（scheduling slot），不是执行体池。契约 =「调度槽只承载触发与轻量内存检查；阻塞 I/O / 时长无上界的任务体强制经 `executeOn(globalWorker())` 载体 hop 到 globalWorker 执行」。C4-round2 §① 终表 28 文件命中（消费方 27 文件 + 设施本体）+ 终表外新增登记 1（D5）逐一落处置矩阵：终表 27 消费方文件按五类处置无缺位——设施级覆盖 14、站点侧接入（本批 Phase 2）2、移交 R1-03 4、移交 R-G 治理轨 6、豁免+声明义务 1；加终表外新增登记 1（D5）＝28 消费方文件全处置——禁止静默缺处置；R1-03 全部 9 项消费方工单按同一契约口径传导；4.2-C4-003 两面映射在本矩阵内闭合。
2. **裁定②（容量语义）**：globalTimer 容量走**新配置键** `nop.commons.concurrent.global-timer.corePoolSize`（默认 4，= 现行实际生效容量，零行为变化）；旧键 `nop.commons.concurrent.global-timer.maxPoolSize` 废弃、显式配置时回读 + 单次 WARN。`ExecutorHelper.newScheduledExecutor` 移除对调用方 config 对象的 `setMaxPoolSize(0)` 副作用改写；scheduled 形态的容量旋钮唯一为 corePoolSize（JDK ScheduledThreadPoolExecutor 固有），maxPoolSize 为惰性展示字段。对外 API 零签名变更。
3. **裁定③（池工厂校验）**：两条工厂入口（`DefaultThreadPoolExecutor.newExecutor` 三参/四参重载与 `ExecutorHelper.newThreadPoolExecutor` 配置重载）补 fail-fast 校验：poolSize ≥ 1、queueSize ≥ 1、配置路径 core ≤ max（或显式 cached 形态 max < 0），违例抛 `NopException`（新错误码 `nop.err.commons.concurrent.invalid-thread-pool-param`），杜绝裸 JDK IAE 与静默 cached 语义。`Integer.MAX_VALUE` 队列保留为显式无界形态 + 创建期 WARN。live 12 个工厂路径站点全量兼容核对（3 缺陷面随批修复 + 9 合格零改动/输入硬化声明）。
4. **裁定④（cached 上界与熔断）**：nop-cached-thread-pool 全局共享池获得可配置上界：新配置键 `nop.commons.concurrent.cached-pool.maxPoolSize`（默认 200，构造期读取）。`GlobalExecutors.cachedThreadPool` 维持 `maxPoolSize=-1` 作为显式 cached 标记，上界由 `newThreadPoolExecutor` 的 `maxPoolSize<0` cached 分支构造期统一读取该键施加——该分支是平台唯一 cached 表达形，cachedThreadPool 经 `DefaultThreadPoolExecutor.init()` 进入此分支（无上界 cached 形态自平台废除）。达界提交获得显式拒绝信号（REE，注册错误键 `nop.err.commons.concurrent.cached-pool-saturated`，携带池名与上界）——熔断信号在进程濒死前可见。
5. **裁定⑤（收缩语义与热更面）**：`ExecutorHelper.updateThreadPool` 尺寸变更按「JDK 不变量 core ≤ max 在每次 setter 后成立」重排：真缩容先降 core 再降 max（修复缩容必炸裸 IAE），放大先升 max 再对齐 core（去预抬，消除线程创建-销毁抖动）；`refreshConfig` 捕获剩余 `IllegalArgumentException` 转 `NopException`。热更支持面 = core/max/keepAliveSeconds；queueCapacity 与 daemon 为 init-only 面（成文声明，不补齐——Deferred 节 watch-only 残留随此成立）；allowCoreThreadTimeOut/threadPriority 死旋钮维持零读取（归 C4-011/R4-13，本批不越界承接）。`DefaultScheduledExecutor.refreshConfig` 改为 scheduled 形态专用路径：仅对齐 corePoolSize、不经通用 `updateThreadPool`、无 max 钳制——与裁定②「scheduled 容量 = core」语义一致。
6. **裁定⑥（与 R1-01 绝缘层共享代码位边界）**：本批设施面改动不触碰绝缘语义载体——`InsulatedPeriodicTask` / `InsulatedPeriodicChain` / `PeriodicTaskHelper` / `IPeriodicTaskListener`、`DefaultScheduledExecutor` 的 `periodicTasks` 登记表与 `destroy()` 拒绝路径、`ExecutorHelper.schedule` 的 bindCancel 面（C3-016/R1-04）零改动；`TestPeriodicTaskInsulation` 9 条 + 下游绝缘回归为共享代码位改动的回归门禁。

## 二、背景与动机

globalTimer（`GlobalExecutors.globalTimer()`，DefaultScheduledExecutor→JDK STPE，daemon，DelayedWorkQueue 无界）是全平台共享定时器，现行**实际容量恒为 core 默认 4**：唯一容量配置 `nop.commons.concurrent.global-timer.maxPoolSize`（默认 5）是死值——`newScheduledExecutor` 以 `config.setMaxPoolSize(0)` 副作用改写调用方持有的同一 config 对象，JDK STPE 本身也只以 corePoolSize 为有效旋钮（2.1-C4-001）。28 个生产消费方（A 直连长任务 / B 直连短任务 / C executeOn 自救 / D 轻量直连）共用这 4 线程预算：DB stall 类故障因同库相关性放大为全平台定时任务集体延迟（概率模型见 C4-round2 §①）。

同设施面还有三项已确认缺陷：池工厂对 poolSize/queueSize/负值零校验（MAX_VALUE 队列直通、`-1,-1` 静默变 0-core cached、非法值深处爆裸 IAE——2.1-C4-003）；按名注册的 nop-cached-thread-pool 无上界无熔断（SynchronousQueue + max=2³¹-1，Abort 仅在线程创建失败时触发——2.1-C4-004）；`updateThreadPool` 缩容方向条件反置使热更缩容确定性失败且扩容产生线程抖动（2.1-C4-006）；nop-config 两刷新链把文件遍历与无超时 JDBC 查询直连 timer 线程（2.2-C4-001）。

R1-01 绝缘层（[periodic-task-insulation.md](periodic-task-insulation.md)）已闭「链存活 + 失败可观测」两面（任务体 Throwable 绝缘 + 重臂 + 拒绝终态）；本设计闭其余三面：**占用**（谁在 timer 线程上跑、跑什么）、**容量**（配置是否真实生效、池有没有界）、**契约**（工厂参数域、热更语义是否成文）。两设计共享 `ExecutorHelper` / `Default*Executor` 代码位，边界见 §3.6。

## 三、核心设计

### 3.1 裁定①：调度槽/执行体契约与 globalTimer 平台族处置

#### 3.1.1 平台族契约（调度槽 vs 执行体）

| 契约条目 | 内容 |
|---|---|
| 载体角色 | globalTimer = 调度槽：只承载「何时触发」；任务体在 timer 线程上的合法停留上限 = 轻量内存操作（毫秒级、有界、无阻塞 I/O） |
| 强制卸载形态 | 阻塞 I/O（DB/文件/网络）或时长无上界的任务体，周期触发一律经 `globalTimer().executeOn(globalWorker())`（`IScheduledExecutor.executeOn` → BindScheduledExecutor 既有范式，C 类站点为准绳）：timer 线程只触发，任务体 hop 到 globalWorker；一次性延迟任务用「timer 触发 + 载体提交」等价形态 |
| 专用载体例外 | 模块已有专用池 bean 先例（nop-job `nopRpcPollScheduledExecutor` / `nopJobScheduledExecutor`）的站点，允许以模块专用载体替代 executeOn；判定标准 = 载体有独立容量预算与关闭归属，且不经 globalTimer 执行任务体 |
| 豁免判据 | 同时满足四条方可直连：(a) 任务体纯内存、成本有界且毫秒级；(b) 无阻塞 I/O；(c) 非每连接/每租户扇出形态；(d) 一次性延迟任务或周期性内存检查。豁免判据即新代码评审规则（owner doc 锚点归 Phase 3 落 source-anchors） |
| 绝缘正交 | 本契约闭「占用」面；R1-01 绝缘闭「链存活 + 失败可见」面。executeOn 载体（BindScheduledExecutor）的周期方法已路由绝缘核心（30 点表 #10–13 设施级覆盖），卸载改造不得回退绝缘覆盖 |

#### 3.1.2 消费方处置矩阵（终表 28 命中 → 27 消费方文件逐行 + 终表外新增登记 1（D5）＝28 消费方文件全处置，禁止静默缺处置）

计数口径（终表消费方文件）：C4-round2 §① 终表 28 文件命中 = 27 个消费方文件 + 设施本体 `GlobalExecutors.java`（grep 命中源，非消费方）；D5（`JobWorkerScannerImpl`，plan 2496 看门狗引入）为终表外新增登记站点，计入本矩阵、不占终表 27 文件口径。`FileWatcher.java` 为 CLI 族的载体接收方（经构造注入 executor，非 `globalTimer()` 直呼点），并入 CLI 族行。行号为 live 回读值；与审计载值漂移处统一登记于表尾。

**A 类（直连占线程，长任务能力）**

| # | 站点 | 任务体 | 终裁处置 | successor / 义务 |
|---|---|---|---|---|
| A1 | `AbstractFileConfigSource.java:44-46`（任务体 `Files.walk` :86） | 配置刷新：文件遍历 | **站点侧接入（本批 Phase 2）**：`executeOn(globalWorker())` 卸载 | Phase 2 落地 + 回归（任务体线程可观测为 worker 线程） |
| A2 | `JdbcConfigSource.java:60`（任务体 `executeQuery` :148 无超时；`JdbcConfig.java:91-100` 连接无超时） | 配置刷新：JDBC 全量查询 | **站点侧接入（本批 Phase 2）**：同 A1 卸载口径 + JDBC 超时两面 | 超时取向见 3.1.4；`TestJdbcConfigSourceCallbackIsolation` 等既有测试零回归 |
| A3 | `LocalResourceLockManager.java:104-105`（注册）、`:117`（`checkTimeout`） | 锁过期扫描（内存锁表遍历，短临界区 synchronized） | **豁免（显式裁定 + 声明义务）** | 声明义务见 3.1.5 |
| A4 | `ScheduledRecoveryManager.java:360-362`（`:361` scheduleWithFixedDelay；生产经 IoC 注入 globalTimer，javadoc `:199`） | recovery scan：DB 孤儿会话扫描（多查询） | **移交 R1-03**（新增站点，无独立 finding ID；勘误 4 口径下第三个 DB 相关站点） | R1-03 承接：生产接线改 executeOn(globalWorker) 或模块专用载体；cra-index P1 行注记承载（见 3.1.6） |
| A5 | `JsonRpcWebSocketHandler.java:82`（nop-graphql-core） | sendKeepAlive WS ping，**每连接一条**周期任务 | **移交 R1-03**（与 2.3-C8-001「WS 推送卸载共享池」同工单合并——同为连接数线性扇出面） | R1-03 承接：keepalive 触发与连接生命周期解耦（扇出收敛或专用载体） |
| A6 | `SysDaoNamingService.java:77`（nop-sys-dao） | cleanup：DB 删除 | **移交 R-G 治理轨**（nop-sys 不在 R1-03 模块面；R1-07 为留存语义批、非占用面） | cra-index P1 行注记 + R-G 登记条目：卸载方向 = executeOn 或专用载体，无既定工单，随 nop-sys 下一批次立项 |
| A7 | `CliRunCommand.java:108`（nop-cli-core；watch 周期链已受 plan 2496 4.5-C4-001 绝缘 + 失败可见化） | runTasks→runFile：XPL 任务执行（时长无上界） | **移交 R-G 治理轨**（CLI 单进程独享、无共享占用受害面；绝缘面已闭，本行只余占用面） | R-G 登记条目：占用面声明 + 卸载方向，不设工单 |
| A8 | `CliWatchCommand.java:90` / `FileWatcherFactory.java:33`（→ `FileWatcher` 载体接收方） | debounce→processChangeEvent 文件处理回调 | **移交 R-G 治理轨**（同 A7） | 同 A7 |
| A9 | `CliWatchZipCommand.java:100` / `ZipFileWatcher.java:38`（RateLimitExecutorImpl 无 executeOn 转发） | throttle/debounce 任务体：zip 打包 IO 直连 timer 线程 | **移交 R-G 治理轨**（同 A7；注记中显式声明 RateLimitExecutorImpl 任务体直连形态） | 同 A7 |

**B 类（一次性延迟清理，毫秒级）——设施级覆盖，零改码**

| # | 站点 | 任务体 | 终裁处置 |
|---|---|---|---|
| B1–B6 | `DefaultBizEntityExporter.java:72`、`ExcelReportHelper.java:124`、`ReportDemoBizModel.java:155`、`ReportController.java:133`（demo）、`DownloadHelper.java:39`、`ShellRunner.java:226` | 临时文件删除 / 进程销毁清理（一次性 schedule） | **设施级覆盖**：豁免依据以 (a) 有界毫秒级 + (d) 一次性为主；(b) 无阻塞 I/O 加「本地常规文件系统」限定方成立——NFS 等网络文件系统挂起场景下删除调用可阻塞，此类部署应改走 executeOn 卸载；链存活由 R1-01 绝缘设施级兜底；容量面由裁定②修复后配置真实生效 + 卸载规约控制增量 |

**C 类（executeOn 自救，合格防线）——设施级覆盖，零改码**

| # | 站点 | 任务体 | 终裁处置 |
|---|---|---|---|
| C1–C5 | `AbstractBatchScanner.java:221`、`RetryScannerImpl.java:241`（nop-retry-engine）、`RetryEngineImpl.java:623`、`AbstractPollingLeaderElector.java:74`、`AutoRegistration.java:206` | `globalTimer().executeOn(globalWorker())` | **设施级覆盖**：本契约的合格准绳形态，无处置动作；作为 3.1.1 强制卸载形态的代码侧范例 |
| C6 | `SysDaoMessageService.java:213`（半自救：绑定经 executeOn，字段形态与其他直连站点同） | 消息清理扫描 | **设施级覆盖**：任务体已经 executeOn hop 到绑定 executor，「半自救」字段形态不构成占用缺陷，裁定合格、无改码义务 |

**D 类（轻量直连）**

| # | 站点 | 任务体 | 终裁处置 |
|---|---|---|---|
| D1 | `RpcPollTaskManager.java:172`（fallback 直连） | pollOnce：纯内存 deadline/cancel 检查 | **设施级覆盖**：满足豁免判据 (a)(b)(c)(d)；维持 C4-round2 原判定 |
| D2 | `TaskFlowManagerImpl.java:79`（fallback 直连） | 延时/超时触发（任务体在别处执行） | **设施级覆盖**：同 D1 |
| D3 | `ClusterRpcServiceInvoker.java:68` | 裸 globalTimer 注入 PollingRpcClient，**轮询体在 timer 线程执行且链无整体截止**（4.2 审计发现修正其「轻量」分类） | **移交 R1-03（4.2-C4-003 工单）**：站点侧接入 executeOn(globalWorker) + 轮询链整体 deadline |
| D4 | `ClusterRpcProxyFactoryBean.java:43` | 同 D3（同一注入面两行） | **移交 R1-03**：同 D3，同一工单 |
| D5 | `JobWorkerScannerImpl.java:383`（终表外新增站点，plan 2496 MA-05/MA-06 看门狗引入） | scheduleInvokeWatchdog：一次性延迟的纯内存观测检查（ERROR 日志 + 计数，永不终结任务） | **设施级覆盖**：满足豁免判据 (a)(b)(d)（一次性 + 纯内存）；登记于本矩阵防静默缺处置 |

处置统计（终表消费方文件口径）：设施级覆盖 14（B1–B6、C1–C6、D1、D2）/ 站点侧接入 2（A1、A2）/ 移交 R1-03 4（A4、A5、D3、D4）/ 移交 R-G 治理轨 6（A6、A7–A9）/ 豁免+声明义务 1（A3）——终表 27 文件按五类无缺位；另有终表外新增登记 1（D5，见 D 类表）。合计 28 消费方文件全处置，无静默缺处置。

> 行号漂移登记：审计载 → live = AbstractBatchScanner :135→:221、RetryScannerImpl :166→:241、AbstractPollingLeaderElector :19→:74、AutoRegistration :205→:206、CliRunCommand :90→:108、ScheduledRecoveryManager :361→:360-362、JdbcConfigSource :54→:60。成因 = plan 2496（R1-01）同仓改造与常规漂移；矩阵以 live 为准。

#### 3.1.3 R1-03 消费方同口径传导声明

R1-03 全部 9 项工单（batch-proposal §二 R1-03 行）按本节契约落地，不得各走一套：

| R1-03 工单 | 面 | 传导口径 |
|---|---|---|
| 1.2-C4-004 | Debezium 引擎移出 globalWorker（心跳/超时预算） | 契约对 globalWorker 侧同构适用：引擎 = 长任务体，须专用载体；共享池只承载触发/轻量 |
| 1.5-C4-001（MA-04） | retry/delay 业务体卸载 | 阻塞任务体强制 executeOn(globalWorker)（3.1.1 强制卸载形态） |
| 2.3-C8-001 | WS 推送卸载共享池（ρ=N·L/40） | 每连接扇出禁止直连共享池；矩阵行 A5（JsonRpcWebSocketHandler keepalive）合并本工单 |
| 3.1-C4-011 / 3.1-C4-004 / 3.1-C4-009 | nop-ai 消费面 | 按矩阵移交口径：站点侧接入 executeOn 或模块专用载体 |
| 3.2-C4-004 | SocketServer cached 池消费迁移（Deferred successor） | 在裁定④熔断基线上评估有界池迁移 |
| 1.7-C8-003 | batch 兜底 cached 池平台级统一评估 | 裁定④上界+达界熔断为设施基线；batch 侧形态评估在该基线内做（与 cra-index G22 行注记对账，防与 2.1-C4-004 重复立项） |
| 4.2-C4-003 | 裸 globalTimer 注入 PollingRpcClient | 站点侧接入：executeOn(globalWorker) + 轮询链整体 deadline（两面映射闭合，见 3.1.4） |

**4.2-C4-003 两面映射闭合声明**：4.2 复核裁定「修复时与平台族隔离方案统一裁决」——本节即为该统一裁决。WI2.1 设施面 = 本矩阵 D3/D4 行（占用缺陷的设施侧登记）+ 裁定②③④⑤设施修复；WI4.2 使用方面 = R1-03 工单（executeOn + deadline 站点改码）。两 face 在本节互指闭合，此后该缺陷的收敛核验以 R1-03 工单为唯一载体。

**MA-16 / R4-21 约束解除链注记**：3.1-MA-16「不得指向 globalTimer」约束的解除条件 = R1-03 落地（非本批）。本批只供口径：解除后的约束形态收窄为「阻塞/长任务体不得指向 globalTimer；满足 3.1.1 豁免判据的轻量直连合法」。R2-16（3.1 单元批，软依赖 R1-02/R1-03）不受本裁定新增义务；ScheduledRecoveryManager 已移交 R1-03，不落入 R2-16。

#### 3.1.4 站点侧接入同口径（本批 Phase 2 实施依据）

- **nop-config 两站点（A1/A2）统一 `executeOn(globalWorker())`**，不采用 `SingleThreadConfigExecutor` SPI 形态。理由：(a) 配置源在 IoC 容器启动前构造（`AbstractFileConfigSource` 构造器内直连 `GlobalExecutors.globalTimer()`，此时无法注入任何 IoC bean），SPI 形态不可达；(b) executeOn 是平台既有范式（C 类六站点先例），Bind 载体已继承 R1-01 绝缘覆盖；(c) 引第二种机制（专用 config executor bean）= 平行设施，违反平台族一次裁决原则。
- **JDBC 超时两面取向 = 连接级 URL 参数**（经 `JdbcConfig` 配置面暴露 connect/socket 超时）+ `Statement.setQueryTimeout` 查询面。理由：`DriverManager.setLoginTimeout` 是 JVM 全局静态（波及同进程全部 DriverManager 连接，跨模块副作用），且 login timeout 只覆盖建连、不覆盖 socket 读挂起——审计指认的占死根因恰是 MySQL 缺省 socketTimeout=0 的无限读阻塞。目标语义：DB 分区从「占死 timer 线程」退化为「周期性失败重试」（失败可见性由绝缘层 + `JdbcConfigSource` 既有 `errorCount` 分级抑制承载）。
- worker 池侧交互声明：刷新体迁入 globalWorker 后，其失败/取消语义走绝缘契约（提交拒绝 → future 终态 + 链不复活）；globalWorker 有界队列（TaskQueue 10000）+ RejectHandler 可感知，不引入新的静默丢弃。

#### 3.1.5 LocalResourceLockManager 豁免裁定（A3）

终裁 = **豁免 + 声明义务**，不随批卸载、不回补迁移执行项。依据：(a) plan Baseline 勘误 4 终裁口径——`checkTimeout` 为内存锁表遍历（`locks.values()` 短临界区 synchronized，`LocalResourceLockManager.java:117-129`），**非 DB 扫描**，不构成 DB 阻塞相关性；(b) 站点已有载体注入缝（`setCleanupTimer(IScheduledExecutor)`，`:57-59` 直连仅为 `timer == null` 时的 fallback 默认），需要隔离的部署可注入专用载体，设施不强制；(c) 周期链已受 R1-01 绝缘覆盖（30 点表设施级覆盖行）。声明义务（Phase 3 owner doc 落账时一并执行）：在 GlobalExecutors/globalTimer 锚点条目中显式登记「该站点为内存轻量豁免面、占用面不构成 DB 阻塞、注入缝可用」。

#### 3.1.6 移交落 cra-index 行注记清单

以下注记在 plan 2497 Phase 4 台账回填时写入（移交载体可独立核验；行号为当前 cra-index 行位置，以 finding ID 定位为准）：

| cra-index 行 | 追加注记内容 |
|---|---|
| P1 台账 `nop-kernel-concurrency-C4-001` 行（L193） | 平台族裁定权威 = 本设计 §3.1；处置矩阵 = 终表 27 消费方文件按五类无缺位（设施级覆盖 14 / 站点侧接入 2 / 移交 R1-03 4 / 移交 R-G 6 / 豁免+声明 1）+ 终表外新增登记 1（D5）＝28 消费方文件全处置；移交清单：ScheduledRecoveryManager→R1-03、JsonRpcWebSocketHandler→R1-03（2.3-C8-001 合并）、ClusterRpcServiceInvoker/ClusterRpcProxyFactoryBean→R1-03（4.2-C4-003）、SysDaoNamingService→R-G、CLI 族 5 文件（CliRunCommand/CliWatchCommand/FileWatcherFactory/CliWatchZipCommand/ZipFileWatcher）→R-G；4.2-C4-003 两面映射闭合 |
| P2-G22 行（1.7-C8-003，L386） | cached 上界与熔断基线 = 本设计 §3.4；R1-03 在该基线上评估 batch 兜底池形态（与 G25 行互查防重复立项） |
| P2-G25 行（C4-003/C4-004/C4-006，L389） | 工厂校验契约 = §3.3；缺陷面 3 调用方随 R1-02 修复；C10-010 成员面验收前置随本体 |
| P2-G32 行（2.2-C4-001，L396） | 卸载口径 = §3.1.4（executeOn(globalWorker)，两站点同口径）；JDBC 超时两面取向 = 连接级 URL 参数 + queryTimeout |
| P2-G66 行（4.2-C4-003/4.2-C4-004，L430） | 4.2-C4-003 两面映射闭合：设施面 = 2.1-C4-001（本设计 §3.1 矩阵 D3/D4 行），使用方面 = R1-03 工单唯一载体 |

R-G 治理轨登记条目（A6、A7–A9）：随 Phase 3 缺口声明一并登记（触达模块 nop-sys、nop-runner 无对应工单承载面，登记占用面声明 + 卸载方向，不新建 owner doc，不设工单）。

### 3.2 裁定②：globalTimer 容量语义与工厂副作用改写移除

**配置语义（三选一裁定：废弃声明 + 等价新配置 + 兼容回读）：**

| 配置键 | 状态 | 语义 |
|---|---|---|
| `nop.commons.concurrent.global-timer.corePoolSize` | **新权威键**（Integer，默认 4） | globalTimer 的固定线程数（STPE 容量 = core）。默认 4 = 现行实际生效容量，升级零行为变化 |
| `nop.commons.concurrent.global-timer.maxPoolSize` | **废弃**（@Description 改注废弃声明） | 仅当旧键被显式配置且新键未显式配置时回读其值并记单次 WARN（日志键 `nop.warn.commons.global-timer.legacy-max-pool-size`）；两者皆未配置 → 新键默认 4。显式配置探测经 `AppConfig.var(name)` 原始读（null = 未配置） |

运维迁移含义：部署配置**改名** `global-timer.maxPoolSize` → `global-timer.corePoolSize`；改名前旧键继续生效（带 WARN），不存在「配了不认」的静默断档——本缺陷的本体就是静默死配置，修复不得复刻同族形态。旧键删除时点归后续清理批次（非本批）。

**构造语义**：`GlobalExecutors.globalTimer()` 改设 `config.setCorePoolSize(newKey值)`；不再设置 maxPoolSize（保留 ThreadPoolConfig 默认 20 为惰性展示值）。`getConfig()` 的可观测变化：maxPoolSize 从「被改写后的 0」变为「惰性默认 20」——诊断面更诚实（0 曾是假参数），已在锚点条目声明。

**工厂副作用移除**：`ExecutorHelper.newScheduledExecutor` 删除 `config.setMaxPoolSize(0)` 改写行，**不得改写调用方持有的 config 对象**。两面一并闭合：(a) `getConfig().getMaxPoolSize()` 恒 0 的假读数消失；(b) `updateThreadPool` 的 `maxPoolSize>0` 分支对 scheduled 形态永久失活的问题随裁定⑤的 scheduled 专用热更路径一并消除。JDK 既有注释（STPE max 不生效）改写为成文 javadoc 契约：scheduled 形态容量旋钮唯一为 corePoolSize。

**同工厂消费方行为影响声明（source-compat：对外 API 零签名变更）**：

| 消费方 | 变化 |
|---|---|
| globalTimer 单例 | 容量从「恒 4」变为「配置驱动，默认 4」；单例语义、daemon、绝缘覆盖零变化 |
| `newSingleThreadTimer` 消费方（`StreamControlRpcProxyFactory.java:62`、`SimpleRpcClientFactory.java:122`） | 行为零变化（core=1 不变）；config 对象不再被改写（max 展示 1 而非 0）；C4-010 非 daemon 面不在本批（归 R4-13，不越界） |
| DefaultScheduledExecutor IoC bean（nop-job `nopRpcPollScheduledExecutor` / `nopJobScheduledExecutor`） | init 行为零变化；热更行为见 §3.5 裁定⑤专用路径（core-only 对齐） |

### 3.3 裁定③：池工厂 fail-fast 校验契约

**参数域表（统一语义，跨工厂入口表驱动可对照、不静默分叉）：**

| 入口 / 参数 | 合法域 | 违例处置 |
|---|---|---|
| `newExecutor(name, poolSize, queueSize[, daemon])` 三/四参重载 | `poolSize ≥ 1`；`queueSize ≥ 1` | `NopException(nop.err.commons.concurrent.invalid-thread-pool-param)`，params：池名、参数名、非法值。负值不再静默变 0-core cached（`-1,-1` 路径封死）；`queueSize ≤ 0` 不再深爆 `TaskQueue`/LinkedBlockingQueue 构造 IAE |
| `newThreadPoolExecutor(config)` / `newExecutor(config)` 配置重载（Standard 形态） | `corePoolSize ≥ 0`；`maxPoolSize ≥ corePoolSize` 或 `maxPoolSize < 0`（显式 cached 形态）；`maxPoolSize ≠ 0`；`queueCapacity ≥ 1` | 同上错误码。init 路径补齐 core>max 守卫（现状仅 refreshConfig 有钳制、init 裸爆 JDK IAE——C4-003 F3 扩展面闭合） |
| `newScheduledExecutor(config)`（scheduled 形态） | `corePoolSize ≥ 1`（STPE core=0 永不执行任务）；maxPoolSize 为惰性展示字段、不校验不改写 | 同上错误码 |
| `newThreadPoolExecutor` cached 分支（`maxPoolSize < 0`） | 形态 = plain TPE：core 0、SynchronousQueue、keepAlive 60s、**上界 = `nop.commons.concurrent.cached-pool.maxPoolSize`（裁定④）**；config 的 corePoolSize/queueCapacity 在该分支不适用（cached 语义固有，javadoc 成文） | 分支不再产生无上界池（无上界 cached 形态自平台废除，拒绝理由见 §四） |
| `queueCapacity = Integer.MAX_VALUE` | 合法（显式无界形态保留） | 创建期单次 WARN（日志键 `nop.warn.commons.thread-pool.unbounded-queue`，带池名）——背压失效须在创建时刻可见，不新增常量 API（避免工厂 API 扩面） |

**类型化异常**：新增错误码 `ERR_CONCURRENT_INVALID_THREAD_POOL_PARAM`（key `nop.err.commons.concurrent.invalid-thread-pool-param`，params：池名 / 参数名 / 非法值），定义于 `CommonErrors`（消息文案按 CommonErrors 既有惯例，实现期定稿）。校验位置 = 两工厂入口 + `DefaultThreadPoolExecutor.init()` 真实构造链（IoC config bean 注入路径同样被拦截）。既有裸 REE 拒绝键（`nop.err.thread-pool-executor.queue-full` 等）属错误处理维度既有线索，本批不改（非成员面，防越界）。

**既有调用点兼容矩阵（live 12 站点全量，无缺位）**：

| # | 站点 | 入口形态 | 终裁 |
|---|---|---|---|
| 1 | `ExportDbTool.java:150`（nop-batch-exp） | 3 参（threadCount, **Integer.MAX_VALUE**） | **随批修复**：队列改有界，取平台惯用 10000（与 globalWorker TaskQueue 容量一致）；吞吐调优归 1.7 CL-004 对账工单（Non-Blocking Follow-up 注记） |
| 2 | `ImportDbTool.java:232`（nop-batch-exp） | 3 参（threadCount, **Integer.MAX_VALUE**） | **随批修复**：同 #1 |
| 3 | `JavaScriptService.java:40`（nop-js） | 3 参（**-1, -1**） | **随批修复**：改配置重载显式 cached 形态（maxPoolSize<0 = 有界 cached，上界=平台配置）——保留其 0-core cached 实际语义、获得熔断上界、语义显式化 |
| 4 | `SingleThreadConfigExecutor.java:19` | 4 参（1, 100, true） | 合格，零改动 |
| 5 | `AbstractBatchProcessService.java:54` | 4 参（name, poolSize, 1, true） | 合格；行为硬化声明：poolSize≤0 配置从深爆变构造期类型化异常 |
| 6 | `JdkHttpClient.java:111` | 3 参（配置驱动） | 合格；同 #5 硬化声明（queueSize≤0 配置从深爆变类型化异常） |
| 7 | `SimpleRpcServer.java:132` | 3 参（threadPoolSize 配置驱动，10） | 合格；同 #5 |
| 8 | `SimpleRpcClientFactory.java:126` | 3 参（5, 10） | 合格，零改动 |
| 9 | `GrpcServer.java:82` | 配置重载 | 合格；硬化声明：core>max 配置从 init 裸 IAE 变类型化异常（C4-003 F3 扩展面闭合） |
| 10 | `DefaultTaskExecutionQueue.java:145` | 配置重载 | 合格；同 #9 |
| 11 | `GlobalExecutors.globalWorker`（直构造 init，`GlobalExecutors.java:85-96`） | 配置（core 默认 4 / max=CFG 30 / queue 默认 10000） | 合格，零改动 |
| 12 | `GlobalExecutors.cachedThreadPool`（直构造 init，`GlobalExecutors.java:71-83`） | 配置（max=-1） | 合格，零改动：`maxPoolSize=-1` 维持为显式 cached 标记，上界由裁定④ cached 分支构造期统一施加 |

> 计数口径对账：plan Phase 1 义务书「11 调用点（3 缺陷 + 8 合格）」；live 逐站枚举 = 12 站点（3 随批修复 + 9 合格零改动/硬化声明）。差异为计数口径（GlobalExecutors 两个直构造 init 站点按「一个工厂路径口」或「两站点」计），处置无缺位——12 站点全部在矩阵内，禁止静默缺处置原则以此为准。

### 3.4 裁定④：cached 共享池上界与熔断语义

- **配置项**：`nop.commons.concurrent.cached-pool.maxPoolSize`（Integer，**默认 200**——审计建议量级；构造期读取，热更不生效，属裁定⑤ init-only 声明面）。`GlobalExecutors.cachedThreadPool()` 的 config **维持 `maxPoolSize = -1`** 作为显式 cached 标记，不改设上界值——其经 `DefaultThreadPoolExecutor.init()` → `newThreadPoolExecutor` 进入 cached 分支，上界由分支统一读取该键施加（与 §3.3 兼容矩阵 #12 零改动结论一致）。
- **分支语义改写**：`ExecutorHelper.newThreadPoolExecutor` 的 `maxPoolSize<0` 分支（`ExecutorHelper.java:81-83`）从「0 / 2³¹-1 / SynchronousQueue」改为同形态**有界** cached（core 0 / SynchronousQueue / keepAlive 60s 不变，maximumPoolSize = 上述配置键构造期读取值）。该分支与 GlobalExecutors.cachedThreadPool 的关系裁定：**cachedThreadPool 经 init() 进入本分支，两者天然共用同一上界配置**——分支是平台唯一的 cached 表达形，若仅给全局池加界而保留分支的无上界形态，等于保留缺陷形态（C4-004 本体 = 无上界线程突发无信号）；统一后全部 cached 形态池受同一默认上界保护，个体差异以 Standard 形态（有界队列弹性池）表达。
- **达界拒绝语义（熔断信号）**：cached 池注册显式 `RejectedExecutionHandler`（Abort 语义不变——不排队不丢弃），抛 `RejectedExecutionException`，消息 = 注册错误键 `nop.err.commons.concurrent.cached-pool-saturated`（params：池名、上界值；错误码随裁定③一并落 `CommonErrors`）。类型选择理由：`Executor.execute` 契约通道是 REE（JDK 语义 + Standard 族既有先例 `nop.err.thread-pool-executor.queue-full`），抛 NopException 违背 Executor 契约；可辨识性由注册错误键与参数达成。达界信号出现在「第 201 个并发提交」，早于线程创建失败的进程濒死点——熔断可见性目标达成。
- **7 消费面行为变化核对**（全部零改码，设施级覆盖）：

| 消费面 | 达界行为变化 |
|---|---|
| `ExportDbTool.java:284` / `ImportDbTool.java:296`（builder.executor） | 并发 > 200 时提交获显式 REE（含池名/上界）；默认导出并发 = threadCount 配置，常态远低于界 |
| `BatchTaskBuilder.java:337` / `BatchTask.java:87`（batch 默认执行器） | 主可达面（batch 高并发分片）：> 200 并发分片获 REE——熔断信号替代线程突发；需要更高并发的部署调大配置键 |
| `SocketServer.java:140`（每连接派发） | 受 `maxConnections` 默认 10 封顶，默认配置零感知；有界池迁移归 R1-03（3.2-C4-004，Deferred 已裁定） |
| `ShellRunner.java:153`（CLI collectError，每命令 1 线程） | 零感知 |
| `ModelBasedBatchTaskBuilderFactory.java:210`（`getExecutor(executorName)` 按名解析） | 同 batch 面：达界获显式 REE；解析语义零变化 |

### 3.5 裁定⑤：收缩语义修正与热更面处置

**`updateThreadPool` 尺寸变更契约**（`ExecutorHelper.java:106-126`；伪代码表达行为契约）：

```text
applyResize(executor, target):
  # JDK 不变量 core <= max 必须在每次 setter 之后成立（违反即裸 IAE——现状缩容缺陷根因）
  if target.max < executor.core:            # 真缩容：先降 core 消化超额线程（JDK 语义：空闲线程优雅终止、不杀在飞任务）
    executor.setCorePoolSize(target.max)
  if target.max != executor.max:
    executor.setMaximumPoolSize(target.max) # 此时 core 已 ≤ target.max，不再爆 IAE
  if target.core != executor.core:
    executor.setCorePoolSize(target.core)   # 放大路径不预抬 core（现状先抬到 newMax 再回落 = 创建-销毁抖动，去除）
  # keepAliveSeconds 更新维持现状（有界 >0 且变化时对齐）
```

- 修复效果：缩容热更不再确定性失败（裸 IAE → 成功）；扩容热更线程数单调变化、无抖动。注释（「注释声称处理缩小场景，条件恰好写反」）随修正一并更正。
- **`refreshConfig` 类型化**：`DefaultThreadPoolExecutor.refreshConfig`（`DefaultThreadPoolExecutor.java:57-65`）对 `updateThreadPool` 调用以 try/catch 兜底——捕获 `IllegalArgumentException` 转 `NopException`（复用裁定③错误码，params 带池名/core/max），保证热更违例带诊断上下文、非裸 IAE（正确顺序下该分支为纵深防御，正常路径不触发）。
- **core > max 热更输入**：保持 `refreshConfig` 既有钳制（core 收敛到 max）但加 WARN 日志（日志键 `nop.warn.commons.thread-pool.config-clamped`，带池名与两值）——钳制而非抛异常的理由：热更派发链不应被单次配置错误中断，且「显式告示」满足无静默跳过义务。
- **热更支持面 / 声明面二分（成文声明，不补齐）**：

| 字段 | 热更语义 |
|---|---|
| corePoolSize / maxPoolSize / keepAliveSeconds | **支持**（updateThreadPool live 生效） |
| queueCapacity | **init-only**（成文声明）：TaskQueue 容量构造期固定，热更需重建队列（drain/替换竞态面大、收益低），不支持并声明 |
| threadDaemon | **init-only**（成文声明）：daemon 性属 ThreadFactory 构造期属性 |
| allowCoreThreadTimeOut / threadPriority | 维持零读取（C4-011 死旋钮族，归 R4-13/R4-14；本批不读取、不声明生效、不越界承接） |

成文载体：javadoc + Phase 3 source-anchors 锚点条目。plan Deferred 节「updateThreadPool 热更面补齐——仅当裁定⑤选择成文声明不补齐时成立」随本裁定成立（watch-only residual，Successor Required = no）。

**DefaultScheduledExecutor 热更一致性（与裁定②联动）**：`DefaultScheduledExecutor.refreshConfig`（`DefaultScheduledExecutor.java:70-78`）改为 scheduled 形态专用路径：仅将 `config.corePoolSize` 对齐到 STPE（≤ 0 输入 → 裁定③错误码类型化异常；`IllegalArgumentException` 同样 catch 转类型化）。此处与上方 TPE 侧 `core > max` 热更输入的「钳制 + WARN」构成**有意的不对称**，理由：STPE core≤0 无合法钳制目标——core=0 的 STPE 永不执行任务，钳制等于制造静默死配置，故失败前置（热更期类型化异常）优于静默永不执行；而 TPE core>max 存在合法收敛目标（max），钳制 + WARN 不中断热更派发链且满足显式告示义务；**不经通用 `updateThreadPool`、无 max 钳制分支**——否则新键配置的 core 值大于惰性默认 max=20 时会被静默钳回，复刻死配置形态。两个 nop-job IoC bean（`nopRpcPollScheduledExecutor` / `nopJobScheduledExecutor`，C4-006 实证可达面）的热更行为随之 = core-only 对齐。`refreshConfig` 改写不触碰 `periodicTasks` 登记表与 destroy 拒绝路径（见 3.6）。

### 3.6 裁定⑥：与 R1-01 绝缘层共享代码位边界

**触碰面（本批设施改动）**：`ExecutorHelper` 的 `newScheduledExecutor`（去改写 + 校验）、`newThreadPoolExecutor`（校验 + cached 分支上界 + 拒绝 handler）、`updateThreadPool`（顺序修正）；`DefaultThreadPoolExecutor`（init 校验接线、refreshConfig 类型化）；`DefaultScheduledExecutor.refreshConfig`（专用热更路径）；`GlobalExecutors`（globalTimer 构造语义；cachedThreadPool 维持 `maxPoolSize=-1` cached 标记零改动，上界经 cached 分支施加）；`CommonConfigs`（配置键）；`CommonErrors`（错误码）。

**不触碰面（绝缘语义载体，硬边界）**：

| 载体 | 声明 |
|---|---|
| `InsulatedPeriodicTask` / `InsulatedPeriodicChain` / `PeriodicTaskHelper` / `IPeriodicTaskListener` | 零改动（重臂/三相容/失败治理参数权威 = [periodic-task-insulation.md](periodic-task-insulation.md)，语义不被本批回改） |
| `DefaultScheduledExecutor.periodicTasks` 登记表、`trackPeriodicTask`、`destroy()` 拒绝路径（`DefaultScheduledExecutor.java:96-107`） | 零改动；`refreshConfig` 改写与其无交互（热更路径不枚举、不增删守卫登记） |
| `DefaultScheduledExecutor` 周期方法实现（`scheduleAtFixedRate`/`scheduleWithFixedDelay` 绝缘接线，`:151-189`） | 零改动 |
| `ExecutorHelper.schedule` / `submit`（bindCancel 面） | 零改动——C3-016 归 R1-04，绝缘核心逐轮调度行走该既有路径，本批不扩大不缩小其触发面 |
| `DefaultThreadPoolExecutor.destroy`（shutdown 无等待、submittedCount 泄漏面） | 零改动——C4-002 族归 R4-13，本批不顺手修（防越界承接） |
| 拒绝路径既有裸 REE 键（queue-full / task-queue-not-running） | 零改动——错误处理维度既有线索，非本批成员面 |

**回归门禁**：`TestPeriodicTaskInsulation` 9 条（nop-commons）+ 各下游绝缘回归（nop-retry / nop-cluster / nop-message / nop-ai / nop-metadata / nop-graphql-core / nop-netty 等站点，Phase 3 聚焦通道聚合覆盖）全绿为共享代码位改动的合并门禁；本批新增设施测试（裁定③表驱动校验、裁定⑤ grow/shrink 双向、裁定②容量传导与 config 不改写行为钉、裁定④达界拒绝）与绝缘测试共存于同一模块测试面，互不修改对方断言。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| ① 按用途拆分 globalTimer（config-refresh / keepalive / cleanup 专用池） | 长期架构方向，其前提（规约落地后的占用观测）依赖 R1-03 消费方落地；本批以「容量语义成文 + 卸载规约 + 处置矩阵」收口（plan Deferred 已登记，经 backlog 立项后方可推进） |
| ① 每消费方强制注入专用载体（废弃 executeOn 范式） | 配置源类站点在 IoC 容器前构造（`AbstractFileConfigSource` 构造器期），注入不可达；executeOn 是六站点先例的既有平台范式，统一成本最低 |
| ① nop-config 采用 `SingleThreadConfigExecutor` SPI 形态 | 同上 IoC 时序不可达 + 引入第二种卸载机制（平行设施），违反平台族一次裁决；且该 bean 自建池形态（1,100）无绝缘继承面 |
| ① LocalResourceLockManager 随批迁移 | 终裁口径：占用面为内存锁表遍历轻量、非 DB 阻塞；迁移无收益、徒增设施面 diff；注入缝已备，需要时部署侧可自行换载体 |
| ② 保留旧键名 `maxPoolSize` 映射到 core | 名实不符长期化——键名承诺 max 语义、实际驱动 core，复刻「契约说谎」缺陷族 |
| ② 旧键静默退役（不回读） | 已部署配置中设置的旧键值瞬间失效 = 复刻死配置缺陷本体；兼容回读 + WARN 是唯一不产生静默断档的迁移路径 |
| ② 新键默认取 5（旧键文档默认值） | 无行为保持依据：5 从未生效（实际恒 4），容量默认取现行实际值 4 才满足升级零行为变化；容量扩量归运维显式决策 |
| ③ `queueSize ≤ 0` 沿用 `newSingleThreadExecutor` 的 0→SynchronousQueue 语义 | 两工厂语义分叉：0 在单线程工厂是文档化形态、在 TaskQueue 路径是构造 IAE；统一 fail-fast（queueSize ≥ 1）可表驱动对照，双语义不可 |
| ③ 彻底禁止显式无界队列 | 框架使用方存在合法的大队列需求（如导出分片缓冲）；保留 `Integer.MAX_VALUE` + 创建期 WARN 兼顾逃生通道与可见性，不新增常量 API |
| ④ cached 分支保持无上界、仅 GlobalExecutors 加界 | 分支是唯一 cached 表达形，保留无上界形态 = 保留缺陷形态；与「熔断信号必须在进程濒死前可见」目标直接冲突 |
| ④ 达界抛 `NopException` | `Executor.execute` 的 JDK 契约通道是 REE；Platform Standard 族既有拒绝面同为 REE + 消息键；可辨识性由注册错误键达成即可 |
| ⑤ 热更补齐 queueCapacity / daemon | TaskQueue 容量构造期固定、daemon 属 ThreadFactory——热更需池重建，竞态面大收益低；成文声明消除误导即达义务（rule 24 成文声明支路） |
| ⑤ 热更 core>max 抛异常中断刷新 | 单次配置错误不应中断热更派发链；钳制 + WARN = 存活 + 显式告示 |
| ⑤ scheduled 形态沿用通用 `updateThreadPool`（含 max 分支） | STPE max 为惰性字段，通用路径的 max 比较分支对新键 core 值产生 `max=20` 钳制风险（复刻死配置形态）；scheduled 专用路径语义最诚实 |
| ⑥ 顺手修 destroy 守卫 / submittedCount 泄漏 / C4-010 非 daemon | 共享代码位的相邻缺陷（C4-002/C4-005/C4-010）有既定归属批次（R4-13）；越界承接破坏批次纪律（plan Non-Goals 显式排除） |

## 五、与已有设计的关系

- **上游（R1-01 绝缘层）**：[periodic-task-insulation.md](periodic-task-insulation.md) 闭「链存活 + 失败可观测」，本设计闭「占用 + 容量 + 契约」。正交性：绝缘不改任务体执行位置（Default 形态任务体仍在 timer 线程），本设计不改失败处理语义；executeOn 卸载后任务体 hop 至 worker、绝缘覆盖经 Bind 载体保持（30 点表 #10–13）。共享代码位边界与回归门禁见 §3.6。
- **下游（R1-03 消费方批）**：9 项工单同口径传导（§3.1.3）；4.2-C4-003 两面映射闭合；移交站点（ScheduledRecoveryManager / JsonRpcWebSocketHandler / ClusterRpc 两站点）的站点侧接入在 R1-03 落地；1.7-C8-003 以裁定④为设施基线。
- **治理轨**：MA-16/R4-21 约束解除链（§3.1.3 尾注）；R-G 登记条目（A6、A7–A9）随 Phase 3 缺口声明落账；R2-16 / R4-13 / R4-14 边界已在 §四 拒绝表声明，无越界承接。
- **owner doc 去向**：Phase 3 以本设计为内容源落 `docs-for-ai/04-reference/source-anchors.md` 新建 GlobalExecutors/globalTimer 锚点条目（容量语义、池工厂校验契约、cached 上界与熔断、隔离卸载规约、LocalResourceLockManager 豁免声明），派生摘要标注「以设计文档为准」、按边界规则不直接链接 ai-dev 路径；`docs-for-ai/03-modules/nop-job.md` 调度契约节按 §3.2/§3.5 影响面核对更新。
