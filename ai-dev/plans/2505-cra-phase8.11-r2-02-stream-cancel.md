# 2505 CRA Phase 8.11 — R2-02 流式取消/期限两侧同批（R14 消费面 + R05/R06/R11/R21 相邻，5 项）

> Plan Status: completed
> Draft Review: R1 agent_59c548fb（0 Blocker、2 Major[F1 cra-index 行号三处连坐自纠/F2 PA 预期主路径校正——面①叶内不可达结构性事实]+5 Minor——修订 agent_c78e09bf 全量应用[live 核验后落笔]，active）
> Last Reviewed: 2026-10-25
> Plan Review R1: agent_59c548fb @ 2026-10-25——2 Major（M-1 cra-index 行号 L263/L281 误载连坐修正为 L274/L290 + 勘误 3 自纠补记；M-2 C6-004 面① waitForDemand 上移经 live 结构性核验叶内不可达、PA 裁定预期主路径校正为 plan-first 批内闭环，四处同步 + Phase 1 工时预留）+ 5 Minor（idle 读超时预算承载建议与触碰声明、Phase 3 场景②真实 JdkHttpClient+raw ServerSocket、Phase 2 子分片纪律、成员 2 无人 interrupt 补注等）修订已全部入文（live 事实经本仓库重验后落笔）
> Source: roadmap 工作项 8.11（R2-02，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L153，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-02 行（L76：finding ID 清单 5 项 / PA=无 / 软依赖 R1-04 / exit criteria 增补：4.3-MA-02 消费面回写与 3.1-C7-001 **两侧同批**、C6-004 不可中断面 daemon 化、C7-002 错误以成功交付族纪律[R11]、completeExceptionally 纪律[R21]）；家族底表 = family-clustering §三 R14 取消通道族（L58）/ R05 F5-1 超时族（L49）/ R06 F5-2 不可中断族（L50）/ R11 错误以成功交付族（L55）/ R21 future-永不完成族（L65）行；两侧同批裁定 = 4.3 审计 `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/merge-adjudication.md` §七-6（L378）；成员发现详情 = 3.1 审计目录（`ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`，见 Current Baseline 逐条回读路径）与 4.3 审计目录；执行基线 @ `03b9dce0ae`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.10 已 done，plan 2495–2504 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2499（R1-04 api-core 协同批，completed——**软依赖已解除**：本批取消面修复依赖的原语全部在位：①`FutureHelper.bindCancel` 新守卫（仅取消原因完结且目标未完成才 cancel）；②`FutureHelper.bindCancelToken` 平台收敛入口（`:518-535` 两重载，注册前 `isCancelled()` 补触发 + promise 终态反注册）；③ICancelToken 契约成文（appendOnCancel 不承诺补发、注册方自检窗口，零接口签名变更）。4.3 两站点已随 2499 Phase 3 子分片 B（commit `44e633da72`）转换为 bindCancelToken 单点收敛——本批缺陷本体不在注册面而在**绑定动作**（见 Baseline 成员 1/2）；plan 2496（R1-01 绝缘层批，completed——其 Phase 4 承接 nop-message 4.1-C3-009 七屏障，与本批 4.3-C3-002 同号异站，防混注记见勘误 2）；plan 2503（R1-08，completed——nop-message-C3-002+C8-004 Debezium 屏障已修，同号异站第二注记）；plan 2498（R1-03 池消费方批，completed——多面批范式先例；jdk `ServerEventPublisher`/`JdkHttpClient.executor()` 的 globalWorker 回退为既有平台约定，本批不可中断面改造的载体约束见 Non-Goals）；plan 2493/2494（批次划分与 Phase 8 展开）；**下游交互**：R2-06（3.3 FailoverStreamFlow 同文件批 A——callStream 直接订阅者本体缺陷归该批，本批仅做消费面行为钉）、R2-16（3.1 agent 编排/会话批——同模块续作，零重叠已核，勘误 6）、R2-19/R2-20（4.3 其余两批）、R4-26/R4-27（G67——4.3-C6-003[416 Range 头]与 4.3-C3-033[SSE 生命周期]归该两批，同文件同分支边界见勘误 5）
> Protected Area 与授权：本批 batch-proposal PA 列 = **无**；但 live 回读发现成员 4.3-C6-004 的修复面①（`AbstractServerEventSubscription.waitForDemand` 共享基类）位于 `nop-network/nop-http/nop-http-api`——nop-\*-api 跨模块公共 API 属 AGENTS.md Protected Area 判定面（仓内先例：4.4 审计「C6-009 触碰 nop-http-api 申报 plan-first」、batch-proposal R2-19 行对 nop-http-api/nop-rpc-api/nop-integration-api 三叶显式零触碰声明）。**本批 PA 裁定（R1 修订后预期主路径，Phase 1 内闭环）**：live 结构性事实（R1 补核 @ `03b9dce0ae`）——基类 `demand` 为 private long、`dispatchEvent()` private 且硬编码 waitForDemand→onNext 顺序，叶类 override `waitForDemand` 无法读写 demand、叶内影子计数会与基类 `request()` 记账双轨冲突：**面①（waitForDemand 超时语义上移）叶内不可达，预期主路径 = 按 AGENTS.md plan-first 在批内闭环改共享基类**（Phase 1 预留设计文档 `ai-dev/design/concurrency/` + 独立评审 + SPI 回归清单工时；R1-04 对 nop-api-core 的 plan-first 批内闭环同路径先例，plan 2499 头部授权节先例）；面②③④维持客户端实现叶（`nop-http-client-jdk`/`nop-http-client-apache`）落地、该三面 nop-http-api 零触碰。零触碰声明（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.\*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物；nop-http-api 触碰以 Phase 1 裁定为准并随执行记录登记（预期触碰形态 = C6-004 面① waitForDemand 上移；SSE idle 读超时若需 nop-http-api 配置面为第二潜在触碰形态，预算承载优先 jdk 叶内配置键或常量缺省，见成员 1 修复方向③）。

## Purpose

关闭 R2-02 流式取消/期限两侧同批的 5 项已确认发现（batch-proposal §二.2 R2-02 行：4.3 传输层 C3-002[C3-002/C3-003/C6-002 三出口同构，MA-02 族主]+C6-004；3.1 聚合层 C3-001[MA-01 流式主链族主]+C7-001+C7-002）：①**两侧同批**落地（4.3-MA-02 消费面回写与 3.1-C7-001 同批——聚合层全程期限 + 传输层读超时/可中断，4.3 §七-6 回写闭环）；②4.3-C6-004 不可中断面按 R06 族口径收敛（有界等待 + daemon 化）；③3.1-C3-001（MA-01 族）取消传播闭合 + 族五修复点单 PR 面收敛；④3.1-C7-002 按 R11 错误以成功交付族纪律收敛（无终止信号的干净关闭不再以成功交付）；⑤全批失败路径按 R21 completeExceptionally 纪律。批末 roadmap 8.11 → done，5 成员台账全部收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-02 = 5 项成员；cra-index P1 台账 5 行：L239 `nop-ai-core-nop-ai-agent-C3-001（MA-01 主）`（←C3-002/009+C4-001/002+C5-001 族吸收）、L250 `nop-ai-core-nop-ai-agent-C7-001`、L251 `nop-ai-core-nop-ai-agent-C7-002`、L274 `nop-network-integration-C3-002（MA-02 主）`（成员 C3-003+C6-002 吸收）、L290 `nop-network-integration-C6-004`——裁定批次均=R2-02、状态均=open；roadmap 8.11 行 Status=todo。
- 前置：软依赖 R1-04（plan 2499）已 completed——`bindCancel` 守卫/`bindCancelToken` 收敛入口/ICancelToken 契约成文三件套在位；4.3 两站点（`JdkHttpClient.java:382`、jdk `ServerEventPublisher.java:91`）已转换为 bindCancelToken 单点收敛（注册与 remove 按 promise 终态配对，commit `44e633da72`）。**本批缺陷本体在绑定动作与读循环**：绑定动作 `reason -> future.cancel(false)`（下载）与 `this::cleanup → future.cancel(true)`（SSE）对 headers 到达后已完成的 `sendAsync` future 均为 no-op（JDK `BodyHandlers.ofInputStream` 契约：future 完成于响应头），body 阶段读循环无取消响应点、无读超时。原语就绪 = 本批只改绑定动作、读循环与生命周期出口，**不改 ICancelToken/FutureHelper 接口契约**（零签名变更维持，2499 零回退义务见 Non-Goals）。
- 家族底表（family-clustering §三）：R14 取消通道族（L58——4.3-MA-02 为 P1 成员；族级裁定「4.3 MA-02 消费面回写与 3.1 C7-001 两侧同批」；协同点 = WI 2.1 api-core 移交包已由 R1-04 闭合）；R05 F5-1 超时族（L49——3.1-C7-001 为 P1 成员）；R06 F5-2 不可中断族（L50——4.3-C6-004 为 P1 成员，族修复口径 = **daemon 线程工厂 + 可中断/有界执行路径**）；R11 错误以成功交付族（L55——3.1-C7-002 为 P1 成员，族口径 = 成功信号与产物落地原子性）；R21 future-永不完成族（L65——族纪律 = 失败路径必须 completeExceptionally）。
- 两侧同批裁定（4.3 merge-adjudication §七-6，L378）：本单元 MA-02（C3-002/C3-003/C6-002）+ C6-004 + CL-026 ↔ WI 3.1 C7-001——消费面（聚合层）↔ 本体面（传输层），回写闭环成立，**修复两侧同批**（聚合层期限 + 传输层读超时/可中断）。§七-7：MA-02/C6-004/CL-026 对 WI 2.3 CL-025（IHttpResponse 断连观测平台原语）为「实现面承接 ↔ 平台原语面」关系——实现面承接义务由本批履行，平台原语面维持 WI 2.3 平台轮归属不在本批（Deferred 节）。

### 5 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `03b9dce0ae` 回读值，执行前如再漂移以 live 为准并登记；漂移成因见勘误 3）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向（审计建议 + 家族口径传导） |
|---|---|---|---|---|---|
| 1 | 4.3-C3-002（P1，4.3-MA-02 主登记，cra-index L274；成员 C3-003+C6-002 与吸收线索 CL-026 同 diff 收敛） | 4.3 / nop-network（nop-http-client-jdk 本体；nop-http-api 契约面只读） | `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:379`（`sendAsync(..., BodyHandlers.ofInputStream())`——future 完成于 headers）、`:382`（`bindCancelToken(cancelToken, reason -> future.cancel(false), promise)`——headers 后 no-op）、`:391-403`（416 分支 return 遗弃 `response.body()`——成员面 C6-002①）、`:407-412`（`executor().execute` + `try (InputStream in = response.body())` 读循环，无取消响应点）、`:449`（`processDownloadResponse` 内 `while ((n = in.read(buf)) > 0)` 无 isCancelled 检查）；成员面 C3-003 = `ServerEventPublisher.java:91`（`bindCancelToken(cancelToken, this::cleanup, promise)`——cleanup→`future.cancel(true)` 同 no-op）+ `:138-145`（`parseEvents` 的 `reader.readLine()` 行间才查 isCancelled、行间阻塞点无超时）+ `:119-121`（cancel-at-entry 分支 return 遗弃 body——成员面 C6-002②）；CL-026 = 下载 body 读取无超时且 cancel 不可中断（jdk 泵面随同 diff） | JDK 客户端响应体阶段取消失效：cancelToken 只绑 headers 阶段 sendAsync——取消后幽灵下载继续读入写盘（带宽/磁盘持续消耗）、SSE 读取线程按次泄漏（静默连接上无限期阻塞 readLine）、416 重试与 SSE cancel-at-entry 两早退分支遗弃响应体（连接/socket 不归还） | 响应体生命周期收敛（MA-02 族修复点「cancel/416/终态三出口统一 close + 可中断读」）：①读循环每次迭代检查 `cancelToken.isCancelled()`（或将 token 传入 `processDownloadResponse`），取消即抛 CancellationException 并关闭 sink/in（幽灵下载消除）；②SSE cleanup 路径显式关闭底层 InputStream/HttpResponse（脱离「future.cancel 对已完成 future 无效」形态——C3-003 泄漏面）；③readLine 阻塞点加 idle 读超时（可中断行读取包装或 SoTimeout 等价物——C3-003/CL-026 无超时面；SSE 静默间隔 >30s 为常态，超时预算独立于 request timeout 的 headers 语义；预算承载建议 jdk 叶内配置键或常量缺省，不上移 nop-http-api 配置面——若实现期裁定必须上移，按 Phase 1 PA 裁定项触碰声明处理）；④416 分支与 cancel-at-entry 分支 return 前安全关闭 `response.body()`（`IoHelper.safeClose` 或统一收拢单一 TWR 块——C6-002 两出口）；⑤全部取消监听维持 `bindCancelToken` 收敛入口（R1-04 原语，注册窗口补触发语义继承，零签名变更）；⑥Apache/OkHttp 未中招面（apache future.cancel→abort 交换生效、okhttp call.cancel 中断 read）仅回归钉不改造 |
| 2 | 4.3-C6-004（P1，cra-index L290；与成员 1 失效面③同点回写） | 4.3 / nop-network（失效面①= nop-http-api 共享基类[PA 裁定面]；②=nop-http-client-apache；③=nop-http-client-jdk 同成员 1） | `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/AbstractServerEventSubscription.java:117-127`（`dispatchEvent` onNext 前同步阻塞）、`:143-149`（`waitForDemand`：`while (demand <= 0 && !isCancelled) { wait(); }` 无超时——仅 cancel 可唤醒）、`:150-157`（`cleanup` 置位 + `future.cancel(true)` + notifyAll，无终态派发）；`nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ServerEventPublisher.java:107-109`（`onLine→processLine→dispatchEvent` 由 HC5 I/O reactor 线程同步直调）；失效面③ = jdk `ServerEventPublisher.parseEvents`（同成员 1，readLine 不可中断 + cancel 无效）。**补注（R1 修订）**：正常路径无人 interrupt（R06 不可中断口径成立——cleanup 的 `future.cancel(true)` 对 headers 后已完成的 sendAsync future 为 no-op，不产生中断）；红形态⑤ demand 挂起构造须避开 interrupt 解除——interrupt 会经 `waitForDemand` 的 InterruptedException→cleanup 分支自行解锁、掩盖滞留 | SSE 反压以无限期阻塞等待实现且两处等待点（demand 等待、readLine）均无超时——单个停滞订阅者（有界 demand 不 request 也不 cancel）挂起 HC5 I/O reactor 线程（同 reactor 全部连接停摆）/JDK executor 线程永久滞留（executor 未配置时回退 `GlobalExecutors.globalWorker`，`:56-57`） | R06 族口径「daemon 线程工厂 + 可中断/有界执行路径」：①demand 等待加有界超时（`wait(timeout)` 形态，超时走 onError/cleanup 终态化，不再无限挂起——滞留可自愈；**live 结构性事实：叶内不可达**——基类 `demand` 为 private long、`dispatchEvent()` private 硬编码 waitForDemand→onNext 顺序，叶类 override `waitForDemand` 无法读写 demand、叶内影子计数与基类 `request()` 记账双轨冲突，**预期主路径 = 按头部授权 plan-first 批内闭环上移共享基类**）；②Apache 侧消除 reactor 线程内联 wait（事件投递独立消费队列——平台 `IBlockingQueue` + `QueueOverflowPolicy` 形态，audit 建议；reactor 线程只投递不阻塞）；③JDK 侧 parse 循环可中断化或 cleanup 时关闭底层 in（与成员 1 ②③同 diff 收敛）；④不可中断残留面 daemon 化——确需长阻塞读的承载线程改 daemon 工厂形态（挂死不阻断 JVM 退出，R06 族口径的兜底半边）；**PA 裁定面**：①叶内不可达（结构性事实见修复方向①），预期主路径 = plan-first 批内闭环改基类（设计文档 + 独立评审 + SPI 回归清单，Phase 1 预留工时）；面②③④维持 apache/jdk 客户端叶内落地、该三面 nop-http-api 零触碰；SSE idle 读超时若需 nop-http-api 配置面为第二潜在触碰形态，按 Phase 1 裁定项触碰声明处理（预算承载优先 jdk 叶内配置键或常量缺省，见成员 1 修复方向③） |
| 3 | 3.1-C3-001（P1，3.1-MA-01 流式主链族主，cra-index L239；族成员 ←C3-002[背压]/C3-009[commonPool 承载]/C4-001[缺省承载+终态同步盘写]/C4-002[取消路径 publisher 永不关闭]/C5-001[聚合无界]随族单 PR 面收敛，cra-index 无独立行） | 3.1 / nop-ai（nop-ai-core 本体；nop-ai-gateway 消费面回归钉） | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:202`（无参 `new SubmissionPublisher<>()`——缺省 executor=ForkJoinPool.commonPool）、`:207-213`（`onSubscribe`：`request(Long.MAX_VALUE)` + `appendOnCancelTask(subscription::cancel)` 只传上游）、`:223-231`（onError→`closeExceptionally`/onComplete→`close`——取消路径下不可达）、`:391-440`（`aggregateStreamToResponse`：`:405` `request(Integer.MAX_VALUE)`、`:412-426` onError 先 `chatLogger.logResponse` 可失败 I/O 再 complete[C3-003 悬挂面]、`:427-438` onComplete 直接 `future.complete`）、`:483-529/:535`（`StreamAggregator` textBuilder/reasoningBuilder/toolCallAccumulators[键=线上 itemIndex] 无上界，finishReason 只记不校验）；消费面回归钉 = `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:51/:92`（callStream 直接订阅者，`AttemptCancelToken` implements ICancelToken——R1-04 SPI 回归面成员） | callStream 取消传播单向（上游 cancel 后桥接 publisher 既不 close 也不 closeExceptionally——下游悬挂流 + 聚合 future 永不完成 + 订阅注册泄漏）+ 背压缺失（双层 request(MAX_VALUE) 使有界缓冲满载策略失效、慢下游 uninterruptibly 阻塞 reader）+ 缺省 commonPool 承载阻塞日志 I/O 与 AiCommand 延续链 + 终态回调先可失败日志 I/O 再 complete（日志抛异常 future 永不完成）+ 聚合累积与键累加器无上界（恶意端点单请求 OOM）——MA-01 族五修复点整体缺失 | 族五点单 PR 面（MA-01 修复点 ①–⑤，3.1 merge-adjudication §二 MA-01 行口径）：①publisher 关闭绑定取消/终态——`appendOnCancelTask` 内同时闭合下游（`publisher.close()` 或 `closeExceptionally(CancellationException)` 让下游可区分取消与正常完成；lambda 捕获 publisher 于注册点前完成赋值），onNext 容错 + closeExceptionally 兜底（取消后下游流有界终止，C4-002 同点闭合）；②背压窗口与订阅 request 上界——两级订阅改有限 request 窗口恢复 SubmissionPublisher 有界缓冲语义（平台 `HighWatermarkSemaphore` 水位闸为审计可选项），C5-001 失效面⑤⑥同修（C3-002 慢下游阻塞 reader 面随背压恢复收敛）；③缺省承载换平台受管池——`:202` 构造点显式 executor（R1-02 池工厂校验契约内构造或 `GlobalExecutors` 治理面；阻塞日志 I/O 任务体**不得直连 globalTimer 线程**[MA-16 收窄后约束]，C3-009/C4-001 承载面同修）；④`DefaultChatLogger` 盘写异步化——logResponse 序列化/正则/写盘移出终态回调线程，日志失败不阻断 future 完成（C3-003 悬挂面；rule 24 失败显式可见不吞）；⑤聚合累积与键累加器上界——StreamAggregator 可配置累积上界（max chars/bytes + tool-call 条目数，超界 fail-fast `NopException` 新错误码入 `NopAiCoreErrors`），C5-001 失效面①②③④同修；⑤' 终态清理注记：取消路径悬挂 publisher/订阅注册的按次累积随 ① 消除（长时运行内存增长面） |
| 4 | 3.1-C7-001（P1，cra-index L250；R05 族成员；**两侧同批消费面回写本体落点**） | 3.1 / nop-ai（nop-ai-core；对端本体面 = 本批成员 1/2） | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:272`（唯一超时注入点 `httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get())`——JDK client 路径仅覆盖响应头到达[JdkHttpClient 发送阶段]，SSE body `readLine()` 无 socket 读超时）、`:391-440`（`aggregateStreamToResponse` 聚合订阅链无任何 deadline/watchdog/stall 检测） | 流式聚合路径无全程期限与中途静默检测——provider 返回 200 后连接静默（服务端 hang/代理黑洞/进程假死）时 onNext/onError/onComplete 三者均不发生，聚合 future 永不完成且无日志/指标可见；每次发生泄漏一个 reader 线程与一条挂起调用，累积耗尽线程池 | 聚合层全程期限 + inactivity 检测（R05 族口径；与成员 1/2 传输层读超时/可中断**同批**[§七-6]）：①future 级总期限（`orTimeout` 语义，复用 `CoreMetrics.timeoutToExpireTime` 口径——独立于 `CFG_AI_SERVICE_READ_TIMEOUT` 的流式总期限配置）；②chunk 间 inactivity 上界（独立配置键，审计建议名 `nop.ai.service.stream-idle-timeout`，具体键名随实现期定夺）；超时经 `subscription.cancel()` + `closeExceptionally` 走既有错误路径（R21 纪律：异常终态非静默）；③watchdog 载体优先复用 `io.nop.commons` 调度设施（ScheduledExecutor/CycleSynchronizer）非自旋；④缺省装配（http-api.beans.xml 默认 JdkHttpClient）下挂死链在聚合层期限内有界终止——端到端验证见 Phase 3（两侧同批验收面） |
| 5 | 3.1-C7-002（P1，cra-index L251；R11 错误以成功交付族成员） | 3.1 / nop-ai（nop-ai-core） | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:427-438`（`onComplete` 无终止信号校验直接 `future.complete(response)`）、`:535`（`StreamAggregator.toResponse` 仅 set finishReason 不判是否收到）；五方言终止信号产出点齐备（`OpenAiDialect.java:297-301` finishReason→Phase.DONE 等——信号已在流中，聚合端未消费） | 流式中途干净半关闭（TCP FIN，无 `[DONE]`/finish_reason chunk）被 onComplete 误判为成功完成——截断内容以成功响应交付，`hasStreamedContent` 流式守卫与重试体系全部失效（F2「半个成功」，数据完整性违约无告警） | R11 错误以成功交付族纪律（成功信号与终止信号对齐）：`onComplete` 校验聚合器是否收到终止信号（`StreamItemPhase.DONE`/非空 finishReason）；缺失时按传输级错误 `completeExceptionally`（R21 纪律，带截断字节数诊断），或 ChatResponse 置 invalid 标记交上层裁决——两分支均 fail-loud 不静默成功（rule 24）；「错误以异常/标记交付、不以成功交付」为验收钉；族内对齐注记（4.3-C3-005[R2-19]/3.3-C3-001[R2-06]/3.4-C3-005[R2-17] 同族成员各归其批，Phase 4 台账回填时互注） |

### 前批产物边界（防混与零回退）

- **plan 2499（R1-04）原语边界**：注册面已收敛（bindCancelToken 单点 + 注册窗口补触发 + 终态反注册），本批只改绑定动作、读循环与生命周期出口；`ICancelToken`/`FutureHelper` 零接口签名变更维持。R1-04 SPI ×11 回归面中本批涉及两处零回退义务：`FailoverStreamFlow.AttemptCancelToken`（nop-ai-gateway，:92）与 jdk `ServerEventPublisher`（既有回归 `TestJdkHttpClientCancelListenerCleanup_C3_012` 全绿维持）。
- **plan 2496 Phase 4 / plan 2503 同号异站防混**：plan 2496 Phase 4 承接的是 nop-message 4.1-C3-009 七屏障（commit `1925eb2dd5`）——与 3.1-C3-009（callStream commonPool 承载，本批 MA-01 族成员）同号异站；plan 2503 已修的 nop-message-C3-002+C8-004（Debezium 事件屏障）——与 4.3-C3-002（JDK 下载 body 取消，本批成员 1）同号异站。本批行文一律带单元前缀（3.1-/4.3-/4.1-）消歧（勘误 2）。
- **plan 2498（R1-03）载体约定边界**：jdk `ServerEventPublisher.executor()`/`JdkHttpClient.executor()` 的 `GlobalExecutors.globalWorker` 回退（`:56-57`/`:423-426`）为既有平台约定（单次短触发形态）——本批不可中断面改造**不得把长阻塞读任务体改为常驻 globalWorker/globalTimer**（MA-16 收窄后约束：阻塞/长任务体不得指向 globalTimer；满足豁免判据的轻量直连合法），C6-004 修复新增的承载线程按 daemon 专用形态落地（成员 2 修复方向④）。

### Baseline 勘误（输入不一致登记）

1. **PA 列不一致（本批最重要输入勘误）**：batch-proposal R2-02 行 PA 列 = 「无」，但成员 4.3-C6-004 修复面①（`AbstractServerEventSubscription.waitForDemand` 共享基类，live :143-149）位于 `nop-network/nop-http/nop-http-api`——nop-\*-api 属 AGENTS.md「跨模块公共 API」plan-first Protected Area 判定面（仓内先例：4.4 审计计数口径「Protected Area：C6-009 触碰 nop-http-api 申报 plan-first」；batch-proposal R2-19 行对同三 api 叶显式零触碰声明——说明提案阶段对「触碰 api 叶」有声明惯例而 R2-02 行未载）。本批处理（头部授权节 + Phase 1 裁定项，R1 修订校正）：面②③④修复收口客户端实现叶（叶内落地，该三面 nop-http-api 零触碰）；面①（waitForDemand 超时上移）经 live 结构性补核确认**叶内不可达**——基类 `demand` 为 private long、`dispatchEvent()` private 硬编码 waitForDemand→onNext 顺序，叶类 override 无法读写 demand、影子计数与基类 request 记账双轨冲突——**预期主路径 = plan-first 批内闭环**（设计文档 + 独立评审 + SPI 回归清单，Phase 1 预留工时；R1-04 api-core 同路径先例）——roadmap 8.11 行不回改（历史展开器产物），PA 声明以本 plan 头部为准。
2. **同号异站三处防混**：①4.3-C3-002（JDK 下载 body 取消，本批）≠ nop-message-C3-002+C8-004（Debezium 屏障，已修 plan 2503/R1-08，cra-index L266）；②4.1-C3-009（nop-message 七屏障，已修 plan 2496 Phase 4）≠ 3.1-C3-009（callStream commonPool 承载，本批 MA-01 族成员）——plan 2498 勘误 3 曾以「4.1-C3-009」指代 nop-message 面，跨批行文须带单元前缀；③**同批内**两单元同号：3.1-C3-002（callStream 背压缺失，MA-01 族成员随族主收敛）≠ 4.3-C3-002（本批成员 1）——本 plan 行文一律带单元前缀。
3. **行号漂移登记（成因各注）**：JdkHttpClient 审计载 `cancelToken.appendOnCancelTask(() -> future.cancel(false))` :380-381 → live :382 `FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise)`（plan 2499 Phase 3 子分片 B commit `44e633da72` 收敛转换；缺陷本体[绑定动作 headers 后 no-op]不变，判级不变）；`executor().execute` 读循环审计载 :384 附近 → live :407-412；读循环 `in.read` 审计未载行号 → live :449；executor() 回退 live :423-426。jdk ServerEventPublisher 审计载 :69-93/:119-145 → live `bindCancelToken` :91、`executor().execute` :119、cancel-at-entry return :120-121、`parseEvents` :138-145（readLine :140）。AbstractServerEventSubscription 审计载 :117-127/:143-161 → live `dispatchEvent` :117-127、`waitForDemand` :143-149、`cleanup` :150-157。ChatServiceImpl 审计载 callStream :202-233 → live :202-233（`appendOnCancelTask` 审计载 :205 → live :210）；`aggregateStreamToResponse` :391-440 零漂移（subscribe :400、request(MAX) :405）；onComplete 审计载 :427-436 → live :427-438；StreamAggregator 审计载 :483-529/:531-535 → live :483-529/:535；`httpRequest.setTimeout` :272 零漂移。FailoverStreamFlow `AttemptCancelToken` live :92（R1-04 设计 §3.6 SPI 表 :92 载值零漂移）。本 Baseline 已按 live 回读值书写；执行时再以 live 为准。**R1 修订自纠（2026-10-25，R1 agent_59c548fb）**：本 Baseline 初稿误将 4.3-C3-002 台账行载为 cra-index L263（该行实为 nop-ai-rest-C1-001）、4.3-C6-004 载为 L281（该行实为 nop-host-C6-007）——已按 live 回读改正为 L274/L290（批次判级基线/成员表第 1-2 行/Phase 4 回填项三处连坐修正），L239/L250/L251 复核无漂移。
4. **起草输入措辞勘误（不影响 Phase 结构）**：起草要求中「3.1 聚合层面批[C3-001 全程期限 + …]」的「全程期限」实为 C7-001 的缺陷面（C3-001 本体 = 取消单向悬挂 + MA-01 族五修复点；cra-index L239/L250 两行分立）——本 plan 按 cra-index/审计本体行文，C3-001 与 C7-001 同 Phase 落地不受影响。
5. **C6-003/C3-033/C10 矩阵跨批边界**：4.3-C6-003（416 陈旧 Range 头）与 4.3-C3-033（SSE 生命周期）归 R4-26/R4-27（P2-G67，cra-index L447），不在本批 5 项清单——但 C6-002①/②（416 分支/cancel-at-entry 遗弃 body）在本批 MA-02 面内，两批同文件同分支：**本批只闭合响应体流生命周期（close），不改 Range 头语义与 SSE 生命周期批次语义**，防两批各修一半或互相越界。C10-002（下载取消/416 恢复矩阵缺格）与 C10-003（SSE 取消/终态隔离/背压缺保护）为跨批共享保护矩阵（4.3 merge-adjudication L440-441）：本批只落成员面格子（下载取消响应点/SSE 取消-终态-背压），G67 侧格子（416 恢复矩阵/SSE 生命周期批）留其批（两 C10 行验收前置随本体工单口径）。
6. **MA-01 族吸收面与 R2-16/R2-06 零重叠核实**：族成员 3.1-C3-002/009/C4-001/C4-002/C5-001 全部随族主 C3-001 行在本批收敛（cra-index P1 台账无独立行；p2-adjudication WI 3.1 组 G50–G53 与 R2-16 行[batch-proposal L90]的 10 项清单均不含族成员——零重复立项）；nop-ai-gateway `FailoverStreamFlow` 本体缺陷（3.3-C1-001/C2-001/C3-001/C8-001，同文件批 A）归 R2-06（11 项清单）——本批对其只做「callStream 直接订阅者」消费面行为钉（回归面），**不改其本体**。

## Goals

- 5 项成员全部修复并各带 ≥1 条 focused 回归测试（rule 25，测试名带 `_C3_002`/`_C6_004`/`_C3_001`/`_C7_001`/`_C7_002` 工单标识）；可复现项先红后绿或变异/回退论证（E2）——**流式取消先红形态具体化**：①取消传播红 = `cancelToken.cancel()` 后下游 Flow 订阅在有界观察窗（如 2s）内收不到任何终态信号（onComplete/onError 均不发生）且 publisher 未关闭（悬挂签名）；②传输层读红 = 取消/静默后读线程仍阻塞在 `read/readLine`（线程栈或 latch 观测）、sink 持续增长（幽灵下载签名）；③期限红 = 挂死传输下聚合 future 在配置期限内不完成且无失败可见性；④R11 红 = 半关闭流以 `future.complete` 成功交付（无异常/无 invalid 标记）；⑤demand 挂起红 = reactor/executor 线程滞留 `waitForDemand` 超过预算窗。
- **两侧同批落地**（§七-6）：4.3-MA-02 消费面回写与 3.1-C7-001 同批闭合——缺省装配（http-api beans 默认 JdkHttpClient）下取消/挂死/半关闭三场景在「聚合层期限 + 传输层读超时/可中断」双层预算内有界终止（端到端验证，Phase 3）。
- MA-01 族五修复点单 PR 面收敛：取消闭合（下游可终止）、背压恢复（有限 request 窗口）、平台受管承载（commonPool 缺省消除）、盘写异步化（日志 I/O 不阻断终态）、聚合上界（fail-fast）。
- C6-004 不可中断面按 R06 族口径收敛：demand 等待有界化 + apache reactor 线程内联 wait 消除 + jdk 读可中断/可关闭 + 不可中断残留面 daemon 化。
- C7-002 按 R11 族纪律收敛：无终止信号的干净关闭不再以成功交付；全批失败路径按 R21 纪律 completeExceptionally（异常终态可观测、非静默丢弃）。
- Owner doc 随批更新两份（roadmap 8.11 Owner Doc 列）：`docs-for-ai/03-modules/nop-ai.md`（流式链取消/期限/交付语义契约）+ `docs-for-ai/03-modules/nop-network.md`（下载/SSE body 阶段取消与读超时契约）。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制并发回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / 零触碰与 PA 裁定声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不做平台设施面回改**：R1-04 产物（`FutureHelper.bindCancel`/`bindCancelToken`/`ExecutorHelper`、ICancelToken 契约）零改动——本批是消费方；R1-01 绝缘层（InsulatedPeriodicTask 族）、R1-02 池设施（GlobalExecutors/池工厂/熔断）零回改；站点修复中若暴露设施缺陷，按批次纪律登记（cra-index/新 finding）不越界承接。
- **不承接 R2-06**（3.3 FailoverStreamFlow 本体四面：窗口清理竞态/并发计数泄漏/终态冲刷截断/越窗无界缓冲，同文件批 A 11 项）——本批仅以 callStream 直接订阅者身份做消费面行为回归钉，不改 gateway 本体。
- **不承接 R2-16**（3.1 agent 编排/会话批 10 项：MA-03 F4 终态清理/MA-04 spawn 池/MA-05/MA-08 取消契约等）。
- **不承接 4.3 相邻批**：R2-19（feishu/文件通道 7 项）、R2-20（连接生命周期 6 项）、R4-26/R4-27（G67：C6-003 Range 头语义、C3-033 SSE 生命周期、OkHttp future 完成保障等 28 条）。
- **不改接口签名**：ICancelToken/FutureHelper 零签名变更；nop-http-api 若按 PA 裁定需要改共享基类，走 plan-first 批内闭环而非绕道。
- **不做未中招面改造**：Apache/OkHttp 下载取消面（audit 失效面枚举：均未中招）仅回归钉；OkHttp 无 SSE Publisher 实现（不适用）。
- **不回退 R1-03 卸载形态**：executor() globalWorker 回退等既有约定维持；新增承载线程不违反 MA-16 收窄后约束（长阻塞体不指向 globalTimer）。
- 不触碰 nop-api-core（io.nop.api.core.\*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物（零触碰声明见头部；PA 裁定面 nop-http-api 另行登记）。
- 不做 Deferred 节所列项（CL-025 平台原语面、R11/R21 族跨单元成员、C5-001 面⑦⑧⑨ 观察面）。

## Scope

### In Scope

- `nop-network/nop-http/nop-http-client-jdk`（下载读循环取消响应点/idle 读超时/SSE cleanup 关闭底层流/416 与 cancel-at-entry 关闭 body——C3-002+C3-003+C6-002+CL-026）+ `nop-network/nop-http/nop-http-client-apache`（reactor 线程内联 wait 消除/消费队列化——C6-004 面②）+ `nop-network/nop-http/nop-http-api`（**PA 裁定面**：面②③④零触碰；面① `AbstractServerEventSubscription.waitForDemand` 上移按 plan-first 批内闭环后触碰——预期主路径见头部授权节；SSE idle 读超时配置面若需上移同按触碰声明处理）。
- `nop-ai/nop-ai-core`（ChatServiceImpl callStream/aggregateStreamToResponse/StreamAggregator + DefaultChatLogger 盘写异步化 + 新配置键/错误码——C3-001 族五点 + C7-001 + C7-002）。
- 消费面回归钉（不改本体）：`nop-ai/nop-ai-gateway` FailoverStreamFlow（callStream 直接订阅者行为钉）。
- Owner doc：`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/03-modules/nop-network.md` 两份核对与更新；cra-index 台账回填（5 成员行 + §七-6 两侧同批回写注记 + R11/R21 族对齐注记）；roadmap 8.11 → done。

### Out Of Scope

- Deferred 节所列项；R2-06/R2-16/R2-19/R2-20/R4-26/R4-27 等后续批次工单；平台设施面（R1-01/R1-02/R1-04 产物）回改；G67 侧 C10 格子；CL-025 平台原语面。

## Execution Plan

> 两个模块组分片修复（Phase 1 = 4.3 传输层面、Phase 2 = 3.1 聚合层面）→ 全量验证 + 两侧同批端到端 + owner doc 两份（Phase 3）→ closure audit + 收口（Phase 4）。分片间无实现顺序硬依赖（两侧同批 = 同批闭合约束，非排序依赖；Phase 2 红测以 fake/stub 传输挂死可独立构造），建议按序执行；每分片独立提交（提交纪律：每分片一提交或按模块面提交，commit message 声明工单 ID 与 plan 号）。

### Phase 1 - 分片 A：4.3 传输层——MA-02 族响应体生命周期收敛 + C6-004 反压/不可中断面（nop-network）

Status: completed
Targets: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java`（:382 绑定动作、:391-403 416 分支、:407-412 读循环、:449 读体）、`.../jdk/ServerEventPublisher.java`（:91 cleanup 绑定、:119-121 cancel-at-entry、:138-145 parseEvents）、`nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ServerEventPublisher.java`（:107-109 reactor 内联面）、`nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/AbstractServerEventSubscription.java`（:143-149 waitForDemand——PA 裁定面）、两模块测试目录

- Item Types: `Fix`（4.3-C3-002[含 C3-003/C6-002/CL-026]、4.3-C6-004 已确认 live defect，不得写成 Feature）、`Decision`（nop-http-api PA 裁定）、`Proof`（回归测试）

- [x] **PA 裁定先行**：面①（waitForDemand 超时上移）经 live 结构性核验**叶内不可达**——**plan-first 批内闭环物已落**：设计文档 `ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md`（含裁定①-⑤ + §五 SPI 回归清单 ×3 子类全表）已提交 @ `94bcc9f035`；**独立评审已完成：agent_b3456a7b（Major-1 gateway request(1) 拉取链定性改写+Minor-3~6 → 修订 agent_ff14f367 落盘 → 增量复核 agent_b3456a7b 通过）**——偏离计划「先评审后动手」顺序的授权来源 = Phase 1/2 执行任务契约，评审结论已回填设计文档（提交 be1e423380）；面②③④已在 apache/jdk 客户端叶内落地（commit `8407a3c50c`），该三面 nop-http-api 零触碰实证 = `git diff --name-only` 无 http-api 面②③④相关改动；**触碰声明裁定**：C6-004 面① waitForDemand 上移已按闭环触碰（HttpApiErrors 增 `ERR_HTTP_SSE_DEMAND_TIMEOUT`/`ARG_TIMEOUT` + 基类有界化）；SSE idle 读超时**未上移 nop-http-api 配置面**（预算承载 = jdk 叶内配置键 `nop.http.client.jdk.body-idle-timeout`，JdkHttpConfigs）
- [x] 4.3-C3-002（含 C3-003/C6-002/CL-026 同 diff）：读循环迭代间 isCancelled 检查→CancellationException 终态（JdkHttpClient.processDownloadResponse）；SSE cleanup 覆盖显式关闭登记的底层 body 流（`ServerEventSubscription.cleanup` override；**promise 生命周期移至 body 阶段终态**——修复中发现的注册面事实：`future.whenComplete` promise 在 headers 即完结导致 bindCancelToken 监听器 body 阶段被反注册，cleanup 关流不可达，已随本批修正）；idle 读超时 = BodyIdleWatchdog（globalTimer 短任务巡检 + 关流解除阻塞，预算 `nop.http.client.jdk.body-idle-timeout` 缺省 120s，独立于 headers request timeout）；416 分支与 cancel-at-entry 分支 return 前关闭遗弃 body（C6-002①②）；取消监听维持 bindCancelToken 收敛入口（R1-04 原语零签名变更，`TestJdkHttpClientCancelListenerCleanup_C3_012` 全绿）
- [x] 4.3-C6-004：demand 等待有界化（基类 `waitForDemand` 有界预算 + 停滞终态 cleanup + `onError(ERR_HTTP_SSE_DEMAND_TIMEOUT)`，protected `demandWaitTimeoutMillis()` 钩子）；apache reactor 线程内联 wait 消除（行/终态经有界 `OverflowBlockingQueue`（ERROR 溢出策略 fail-loud）投递，专用 `nop-sse-dispatch` daemon 消费线程执行基类处理）；jdk parse 循环可关闭化（cleanup 关流 + watchdog idle 关流解除 readLine 阻塞）；残留面 daemon 化（新承载线程 NamedThreadFactory daemon；jdk `executor()` globalWorker 回退维持 plan 2498 约定不动，该池线程本身 daemon）
- [x] **边界纪律**：C6-002① 修复只闭合 416 分支 body 流生命周期（close），Range 头语义零改动（C6-003 归 R4-27）；Apache/OkHttp 未中招面零改造（apache 仅 C6-004 面②队列化触达 SSE 订阅，下载/非 SSE 面未动）
- [x] 回归测试（rule 25，测试名带 `_C3_002`/`_C6_004` 标识）：`TestBodyPhaseCancel_C3_002`（6 条：①幽灵下载有界退出+sink 停增 ②静默 body idle 预算终态 ③SSE cancel 对端 EOF ④SSE idle onError ⑤416 遗弃 body EOF ⑥cancel-at-entry 遗弃 body EOF）+ `TestServerEventSubscriptionDemandTimeout_C6_004`（2 条：停滞预算终态/取消唤醒无停滞错）+ `TestApacheSseReactorDecouple_C6_004`（2 条：停滞订阅不阻塞同 reactor 探针请求/派发承载 daemon）；**先红后绿证据**：`_tmp/phase1-red-{api,jdk,apache,416}.log`（api 2F/jdk 5F/apache 2F/416 隔离红）→ `_tmp/phase1-green-{api,jdk,apache}.log` 全绿；⑥既有回归零回退：`TestJdkHttpClientCancelListenerCleanup_C3_012`/`TestServerEventPublisherStreamFix`/`TestServerEventPublisherErrorResponse`（jdk+apache）/`TestFileTransfer`（jdk+apache）/`TestServerEventSubscriptionFix`/`TestApacheHttpClientFix`/`TestLineAsyncDataConsumerFix`/`TestFileDownloadSinkClose` 全绿（`_tmp/phase1-green-jdk.log`/`_tmp/phase1-green-apache.log`）
- [x] **接线验证**（rule 23）：真实 `JdkHttpClient`/`ServerEventPublisher`/`ApacheHttpClient` 装配链上断言（raw socket 对端 EOF 作关闭信号、HttpServer 真流做幽灵下载/静默形态、apache 双连接探针做 reactor 存活信号）——取消/超时语义运行时生效，非类型系统存在
- [x] **无静默跳过**（rule 24）：取消路径 promise 以 CancellationException 异常终态；demand 停滞 onError 显式错误码；416/cancel-at-entry 显式关流；队列满 fail-loud（ERROR 策略）；watchdog 关流走既有 IOException→onError 路径
- [x] **提交纪律**：nop-network 一提交 @ `8407a3c50c`（jdk/apache/api 同提交，message 带 `4.3-C3-002`/`4.3-C6-004` 与 plan 号，pre-commit lint 通过）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-26.md`，随 Phase 2 收口一并落账）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4.3-C3-002（含 C3-003/C6-002/CL-026 面）landed：body 阶段取消有界生效、三出口 body 流全关闭、读超时在位，focused 回归 6 条（`TestBodyPhaseCancel_C3_002`，测试名可辨识工单）
- [x] 4.3-C6-004 landed：demand 等待有界（基类）、reactor 线程不再内联 wait（apache 队列化）、jdk 读可中断/可关闭（cleanup 关流 + watchdog）、残留面 daemon 化（nop-sse-dispatch daemon 工厂），focused 回归 2+2 条
- [x] 必红证据（E2）：幽灵下载/线程滞留/遗弃/demand 挂起断言先红后绿留痕——`_tmp/phase1-red-api.log`（2F）/`_tmp/phase1-red-jdk.log`（5F）/`_tmp/phase1-red-apache.log`（2F）/`_tmp/phase1-red-416.log`（隔离红）→ `_tmp/phase1-green-{api,jdk,apache}.log`（GREEN）
- [x] PA 裁定结论登记：plan-first 批内闭环物 = 设计文档 `ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md` @ `94bcc9f035`（裁定①-⑤ + SPI 回归清单 ×3）；独立评审已完成并闭环（agent_b3456a7b/agent_ff14f367，结论登记于设计文档头）；面②③④+idle 预算承载 nop-http-api 零触碰（唯一触碰形态 = 面①基类有界化 + HttpApiErrors 新错误码，已登记）
- [x] 接线验证证据登记：raw socket 对端 EOF（真实关闭信号）+ HttpServer 真实流（幽灵下载/静默）+ apache 双连接探针（reactor 存活）——`TestBodyPhaseCancel_C3_002`/`TestApacheSseReactorDecouple_C6_004` 装配链断言
- [x] `./mvnw test -pl nop-network -am` BUILD SUCCESS（`_tmp/phase1-gate-nop-network.log`，exit 0；首轮 exit 1 为 nop-netty `TestNettyTcpClient` 环境性端口竞争 BindException（findAvailableTcpPort 后 bind 前被占），隔离复跑绿 + 全量复跑绿，与本批 diff 无关，已登记）
- [x] Owner doc 裁定：`nop-network.md` body 阶段取消/读超时契约——**受影响面更新归 Phase 3 统一落账**（本 Phase 显式登记去向：Phase 3 owner doc 更新项覆盖 nop-network.md 下载/SSE body 阶段取消与读超时契约）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-26.md`）

### Phase 2 - 分片 B：3.1 聚合层——MA-01 族五点 + C7-001 全程期限 + C7-002 R11 纪律（nop-ai）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`（:202 构造点、:207-213 取消绑定、:391-440 聚合链、:427-438 onComplete、:483-529 StreamAggregator）、`nop-ai/nop-ai-core` 的 ChatLogger 面（盘写异步化）、`nop-ai/nop-ai-core` 配置/错误码面（AiCoreConfigs/NopAiCoreErrors 新增随实现期定夺）、`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java`（仅消费面行为钉，不改本体）、nop-ai 测试目录

- Item Types: `Fix`（3.1-C3-001[含 MA-01 族成员]/C7-001/C7-002 已确认 live defect）、`Proof`（回归测试）

- [x] 3.1-C3-001（MA-01 族五点单 PR 面）：①取消闭合——appendOnCancelTask 同时 `subscription.cancel()` + `publisher.closeExceptionally(CancellationException)`（注册点前完成 publisher 捕获 ✓），onNext try/catch(Throwable) 容错 + closeExceptionally 兜底；②背压恢复——两级订阅有限 request 窗口（`STREAM_REQUEST_WINDOW=64`，初始 + 滑动补给；替代 Long.MAX_VALUE/Integer.MAX_VALUE）+ 桥接 offer 满载 fail-loud（`ERR_AI_STREAM_BUFFER_OVERFLOW`，替代 uninterruptible submit 阻塞）；③受管承载——构造点 `GlobalExecutors.cachedThreadPool()`（平台治理面 daemon cached 池；阻塞日志体不指向 globalTimer，MA-16 收窄后约束满足）；④盘写异步化——`logResponseAsync`（complete 后异步记日志，cached 池承载，失败 WARN 显式可见不阻断 future；落点在 ChatServiceImpl 终态回调侧而非 DefaultChatLogger 内部——IChatLogger 同步契约保持、脱敏测试零回退，偏差已登记）；⑤聚合上界——StreamAggregator 累积字符上界（text+reasoning+tool-call arguments 总和，键 `nop.ai.service.stream-max-accumulated-chars` 缺省 200 万）+ tool-call 条目上界（键 `nop.ai.service.stream-max-tool-calls` 缺省 100），超界 fail-fast `ERR_AI_STREAM_ACCUMULATION_EXCEEDED`
- [x] 3.1-C7-001：聚合层全程期限（`CFG_AI_SERVICE_STREAM_TOTAL_TIMEOUT` = `nop.ai.service.stream-total-timeout` 缺省 10min，`CoreMetrics.timeoutToExpireTime` 口径）+ chunk 间 inactivity 上界（`CFG_AI_SERVICE_STREAM_IDLE_TIMEOUT` = `nop.ai.service.stream-idle-timeout` 缺省 2min，独立配置键，与 READ_TIMEOUT 分离）；超时经 `subscription.cancel()` + `future.completeExceptionally(ERR_AI_STREAM_TOTAL_TIMEOUT/ERR_AI_STREAM_IDLE_TIMEOUT)` 走既有错误路径（R21 纪律，带 llmName+timeout 诊断）；watchdog 复用 `GlobalExecutors.globalTimer()` 调度（一次性总期限任务 + 固定延迟 inactivity 巡检，任务体短动作非自旋非长阻塞体，MA-16 豁免判据），终态 whenComplete 双取消无残留
- [x] 3.1-C7-002：onComplete 终止信号校验（`hasTerminationSignal` = finishReason 非空 ∨ `StreamItemPhase.DONE`）——**分支裁定 = completeExceptionally**（`ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING`，带截断字符数诊断 `accumulatedChars`，ARG_LLM_NAME 在案），fail-loud 不静默成功（R11 族纪律 + R21）；零 chunk 空流保持既有成功契约（`TestChatServiceImplCallPathContract` 零回退），分支选择落 owner doc 归 Phase 3
- [x] 消费面行为钉：`TestFailoverStreamCancelPin_C3_001`（gateway）——FailoverStreamFlow 作为 callStream 直接订阅者，挂死传输上调用方取消后有界窗内 attempt 终止（并发计数释放 + 上游 SSE 订阅取消 + attempt token 精确取消一次，订阅注册不泄漏）；**gateway 本体零改动**（R2-06 边界，勘误 6；适配器「调用方取消不向输出发终态」为既有语义归 R2-06，钉内注记 + 核心侧取消闭合红转绿在 nop-ai-core 两条 _C3_001 用例承载）
- [x] 回归测试（rule 25）：`TestStreamCancelDeadlineAndDelivery_C3_001`（nop-ai-core，13 条全带 `_C3_001`/`_C7_001`/`_C7_002` 标识：①取消闭合直订阅者+聚合 future ②request 窗有界+慢下游 fail-loud ③承载非 commonPool ④日志失败不悬挂 ⑤双上界 fail-fast ⑥总期限/inactivity ⑦R11 半关闭 fail-loud + 空流/终止流双零回归边界）+ `TestFailoverStreamCancelPin_C3_001`（⑧消费面钉）；**先红后绿证据**：`_tmp/phase2-red-core.log`（13 条中 11F——红形态①③④全部真红，2 条零回归边界用例基线绿符合设计）→ `_tmp/phase2-green-core-recapture.log` + 3x 复跑全绿；⑨既有零回归：`TestStreamAggregator`/`TestChatServiceImplCallPathContract`/`TestChatServiceImplErrorResponse`/`TestChatLoggerRedaction`/`TestChatLogHelper`/`TestChatServiceRateLimit`/`TestLlmConfigHelper`/`TestLlmErrorMapping` 50 条全绿（`_tmp/phase2-regression-core.log`）；`./mvnw test -pl nop-ai` 聚焦全绿 48 tests BUILD SUCCESS（`_tmp/phase2-gate-nop-ai-focused.log`）
- [x] **接线验证**（rule 23）：真实 `ChatServiceImpl.callStream → aggregateStreamToResponse` 链 + fake 挂死传输（gate/静默形态）上断言取消闭合/期限/watchdog 运行时生效（`cancelTerminatesAggregatedFuture`/`totalDeadlineTerminatesHungStream`/`idleTimeoutTerminatesSilentStream`/`slowDownstreamFailsFastInsteadOfBlocking` 均为全链断言非类型存在）；端到端最小链 = callAsync(stream=true)→fake 挂死→期限终止（组件级预演 ✓，全链端到端归 Phase 3）
- [x] **无静默跳过**（rule 24）：日志失败 WARN 显式可见（`nop.ai.chat-log-write-fail`）；上界超界类型化 `ERR_AI_STREAM_ACCUMULATION_EXCEEDED`；期限到期 `ERR_AI_STREAM_TOTAL_TIMEOUT`/`ERR_AI_STREAM_IDLE_TIMEOUT` 显式异常终态；缓冲满载 `ERR_AI_STREAM_BUFFER_OVERFLOW`；半关闭 `ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING`——不吞异常、不以静默 complete 替代
- [x] **提交纪律与子分片纪律**：nop-ai-core 一提交（生产+测试同提交；消费面钉随同声明关系），commit message 带 `3.1-C3-001`/`3.1-C7-001`/`3.1-C7-002` 与 plan 号；单会话内收口未触发子分片拆分
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-26.md`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 3.1-C3-001（含 MA-01 族五点）/ 3.1-C7-001 / 3.1-C7-002 全部 landed，focused 回归 13+1 条且测试名可辨识工单
- [x] 必红证据（E2）：取消悬挂/期限/R11 三类断言先红后绿留痕——`_tmp/phase2-red-core.log`（11F：`cancelTerminatesAggregatedFuture`/`cancelClosesDownstreamSubscriber` 悬挂、`totalDeadlineTerminatesHungStream`/`idleTimeoutTerminatesSilentStream` 悬挂、`halfCloseWithoutTerminationSignalFailsLoud` 误判成功、五点其余各面）→ `_tmp/phase2-green-core-recapture.log`（13/13 GREEN）+ 3x 复跑稳定
- [x] 接线验证证据登记：真实 ChatServiceImpl 全链断言（fake 挂死传输 gate/静默形态）；watchdog 运行时生效证明 = `totalDeadlineTerminatesHungStream_C7_001`（800ms 预算内挂死流异常终态）/`idleTimeoutTerminatesSilentStream_C7_001`；取消闭合运行时生效证明 = `cancelTerminatesAggregatedFuture_C3_001`/gateway 钉计数释放
- [x] `./mvnw test -pl nop-ai -am` 门禁：链全模块构建+测试通过，唯 nop-auth-service `TestOperationMfaE2E` 为**基线批前既有失败**（基线 `0eb3ea67ba` worktree 隔离复跑同败 6 errors，证据 `_tmp/mfa-baseline.log`，与本批 diff 零交集[nop-auth 零触碰]）；授权支路证据组 = ①`./mvnw test -pl nop-ai` 聚焦 BUILD SUCCESS 48 tests（`_tmp/phase2-gate-nop-ai-focused.log`）②链构建+全测试运行在案（`_tmp/phase2-gate-nop-ai.log`/`_tmp/phase2-gate-nop-ai-unfiltered.log`，唯 nop-auth-service 该类失败）——支路理由随执行记录登记，MFA 修复归 nop-auth 工单不在本批 scope
- [x] Owner doc 裁定：`nop-ai.md` 流式链契约（取消/期限/交付语义 + C7-002 分支裁定 + 新配置键）——**更新归 Phase 3 统一落账**（本 Phase 显式登记去向：Phase 3 owner doc 更新项）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-26.md`）

### Phase 3 - 全量验证 + 两侧同批端到端 + owner doc 两份

Status: completed
Targets: 受影响聚合模块全量测试；`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/03-modules/nop-network.md`（roadmap 8.11 Owner Doc 列两份）；`ai-dev/audits/cra-index.md`（回填准备）

- Item Types: `Proof`、`Decision`（两侧同批回写注记）

- [x] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路：`-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明）【授权支路：`-DskipTests` 沿 plan 2496~2503 先例，全量测试由两通道批行门禁 + 端到端测试组合覆盖，`_tmp/phase3-e1-install.log` exit 0】
- [x] 批行验证命令（E3）逐条登记：`./mvnw test -pl nop-network -am`、`./mvnw test -pl nop-ai -am`——每条登记模块/测试数/退出码/快照路径【三快照：nop-network 重跑 2369 tests/0F exit 0 + nop-ai focused 5177/0F exit 0 + nop-ai -am 16989/0F exit 0；首轮 nop-core 抖动隔离复验绿，执行记录段】
- [x] **两侧同批端到端验证**（rule 22，§七-6 验收面）：缺省装配（http-api beans 默认 JdkHttpClient）下三场景全链有界终止——①取消场景：callAsync(stream=true) 进行中 cancelToken.cancel() → 下游终态有界到达、reader 线程释放、无悬挂注册；②挂死场景：**真实 `JdkHttpClient` + 本地静默 SSE 服务端（raw `ServerSocket` 可构造——accept 后不回发任何数据）** → 聚合层期限终止且传输层读预算兜底（双层预算均断言；fake/stub 挂死传输仅限 Phase 2 组件级红测，不计入本端到端验收）；③半关闭场景：部分 chunk 后干净断连 → 非 success 终态（completeExceptionally 或 invalid 标记）——端到端测试留名与快照【`TestStreamFullChainEndToEnd_C3_001` 4 条（`_tmp/phase3-e2e-green.log` + 2x 复跑 + 两门禁内绿），构造注记与场景映射见执行记录段】
- [x] 零触碰确认（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.\*）、`model/*.orm.xml`、nop-auth、`_` 前缀生成物路径（`git diff --name-only` 核对登记）；nop-http-api 触碰与否按 Phase 1 裁定核对；R1-04 设施文件（FutureHelper/ExecutorHelper/ICancelToken）与 R1-01 绝缘层、R1-02 池设施 diff 零改动核对【禁触面 grep 零命中；nop-http-api 触碰 = Phase 1 PA 裁定面①在案；R1-01/02/04 面 diff 零改动】
- [x] Owner doc 更新（E5）：`nop-ai.md` 流式链取消/期限/交付语义契约（含 C7-002 分支裁定与配置键）+ `nop-network.md` 下载/SSE body 阶段取消与读超时契约逐份核对与更新，或逐份显式登记 `No owner-doc update required` + 理由【两份均更新，逐份结论见执行记录段】
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0【`_tmp/phase3-doc-links-final.log`】
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high`（nop-network/nop-ai，与 Closure Gates 一致）退出码 0；若有批前既有发现，逐条裁定登记【nop-network exit 0；nop-ai 4 条批前既有 P6b 已逐条裁定（注记模式误报×3 + 显式文档化 NoOp×1，四文件本批 diff 零命中）】
- [x] `ai-dev/logs/` 对应日期条目已更新【`ai-dev/logs/2026/10-26.md` 顶部 Phase 3 条目】

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] E1 + 批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [x] 两侧同批端到端三场景（取消/挂死/半关闭）留名留证（rule 22）
- [x] 两份 Owner Doc 核对与更新结论逐份登记（含 `No owner-doc update required` 声明者）；check-doc-links --strict 退出码 0
- [x] 零触碰确认登记（含 nop-http-api 裁定核对、R1-04/R1-01/R1-02 产物零回改实证）
- [x] scan-hollow 受影响模块逐条登记（触达面 high=0 或批前既有已裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: completed
Targets: 本 plan、roadmap 8.11 行、cra-index 台账

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（调用链运行时连通、无空方法体/静默吞异常）；重点抽查「取消后下游真实终止」「挂死链真实有界终止」「半关闭不再成功交付」（三场景线程/终态断言）与「原语/设施零回改」
- [x] cra-index 台账回填：5 成员行（L239 C3-001[MA-01 族吸收注记]、L250 C7-001、L251 C7-002、L274 C3-002[MA-02 族吸收注记]、L290 C6-004）状态收敛（open → 已修/本批）；§七-6 两侧同批回写注记（MA-02/C6-004 行与 3.1-C7-001 行互指）；R11 族对齐注记（4.3-C3-005/3.3-C3-001/3.4-C3-005 各归其批互注）与 R21 纪律适用注记；C10-002/C10-003 本批格子落账注记（G67 侧格子留其批，勘误 5）
- [x] roadmap 8.11 行 Status → done
- [x] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2505-cra-phase8.11-r2-02-stream-cancel.md --strict` 退出码 0；scan-hollow 全绿复核
- [x] Closure Evidence 写入本 plan `## Closure` 段（rule 27）
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [x] cra-index 5 成员行状态全部收敛 + 两侧同批/R11/R21/C10 注记可核验、roadmap 8.11 = done
- [x] 双门禁退出码 0 登记在案
- [x] `ai-dev/logs/` 收口条目已更新



> **Phase 4 回填注（2026-10-26，closure audit agent_6fdbcdc8 PASS with corrections）**：Major-1 证据引用订正——原 `_tmp/phase2-green-core.log` 被中间调试跑覆盖（2F），已重捕获 `_tmp/phase2-green-core-recapture.log`（BUILD SUCCESS，13 条全绿）并全量替换引用；Minor-1 登记时序敏感 watch-only（carrier-thread 断言/5s 取消窗单次时序性失败，HEAD 复跑绿）；Minor-2 流程观察（audit 清单第 2 项 G56/C4-003 系从他批误植，与本批无关——cra-index/roadmap/batch-proposal 三处 grep 确认本批无 G56 条款）。## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 5 项成员全部修复收敛：4.3-C3-002（body 阶段取消+读超时+三出口关闭，含 C3-003/C6-002/CL-026）、4.3-C6-004（demand 有界+reactor 内联消除+jdk 可中断+daemon 化）、3.1-C3-001（MA-01 族五点）、3.1-C7-001（全程期限+inactivity）、3.1-C7-002（R11 纪律）
- [x] 两侧同批闭合（§七-6）：聚合层期限与传输层读超时/可中断同批落地，缺省装配三场景端到端有界终止留证
- [x] 每成员 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；可复现项有先红后绿或变异/回退论证（E2：取消悬挂/幽灵下载/线程滞留/期限/R11 五类断言）
- [x] R11/R21 纪律落地：半关闭不以成功交付、失败路径 completeExceptionally 可观测
- [x] R1-04 原语零回退实证：FutureHelper/ExecutorHelper/ICancelToken diff 零改动 + `TestJdkHttpClientCancelListenerCleanup_C3_012` 等 2499 回归面全绿；R1-01/R1-02 设施零回改
- [x] PA 裁定落账：nop-http-api 零触碰或 plan-first 闭环物（设计文档+评审+回归）证据在案
- [x] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令（`-pl nop-network -am`、`-pl nop-ai -am` 两条通道）BUILD SUCCESS（E1/E3）
- [x] Owner doc 更新完成：`nop-ai.md`/`nop-network.md` 两份逐份核对与更新（或 `No owner-doc update required` 声明）（E5）
- [x] 零触碰确认（E4）：产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-auth/`_` 前缀生成物
- [x] 受影响模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外/相邻面，successor 清晰）
- [x] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）取消/期限/交付语义运行时真实生效（下游终态、线程释放、非 success 终态可观测，不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile`（受影响模块）
- [x] `./mvnw test`（受影响模块）
- [x] checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2505-cra-phase8.11-r2-02-stream-cancel.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] cra-index 台账 5 成员行收敛 + roadmap 8.11 → done（E6）

## Execution Record

### Phase 1（分片 A，4.3 传输层）——completed @ 分片提交 `8407a3c50c` + PA 设计文档 `94bcc9f035`

- 落地面：基类有界化（`AbstractServerEventSubscription` waitForDemand 有界预算 + 停滞终态 `ERR_HTTP_SSE_DEMAND_TIMEOUT`）、apache 队列化（`OverflowBlockingQueue` ERROR 策略 + `nop-sse-dispatch` daemon 消费线程）、jdk body 三出口（cleanup 关流/读循环取消点/BodyIdleWatchdog idle 预算，叶内键 `nop.http.client.jdk.body-idle-timeout`）、416+cancel-at-entry 关流；HttpApiErrors 新增 `ERR_HTTP_SSE_DEMAND_TIMEOUT` + `ARG_TIMEOUT`（PA 面内）。
- 先红后绿：`_tmp/phase1-red-{api,jdk,apache,416}.log`（2F/5F/2F/隔离红）→ `_tmp/phase1-green-{api,jdk,apache}.log`；门禁 `_tmp/phase1-gate-nop-network.log` BUILD SUCCESS。
- **实现期发现（注册面事实修正）**：jdk SSE `startRequest` 原以 `future.whenComplete(...)` 返回值作 bindCancelToken 的 promise——该 promise 在 headers 到达即完结，取消监听器在 body 阶段已被反注册，「cleanup 显式关流」修复不可达。修正 = promise 生命周期移至 body 阶段终态（parse 任务 finally 完结），监听器覆盖整个 body 阶段且按 promise 配对移除（C3-012 无累积契约保持，`TestJdkHttpClientCancelListenerCleanup_C3_012` 零回退）。该发现为 2499 注册面收敛的正确性补强（非回退），已钉 `sseCancelClosesUnderlyingStream_C3_003`。
- 测试基建裁定：raw socket 对端 EOF 作「body 流真实关闭」信号（SSE 遗弃面用 Content-Length>实发字节构造在途交换，保证关闭可观测性与确定性——完全到达的未消费 body 的 socket 关闭由 JDK 客户端延迟化，不可作确定性断言）；idle 配置键在用例级锚定生产缺省值（`assignConfigValue(key,null)` 不能复原数值型缺省，防跨用例泄漏）。

### Phase 2（分片 B，3.1 聚合层）——completed @ 分片提交 `477e1afa03`

- 落地面：MA-01 族五点（取消闭合/背压窗口+满载 fail-loud/受管承载/盘写异步化/聚合双上界）+ C7-001 双 watchdog（新键 ×2）+ C7-002 R11 校验（分支裁定 = completeExceptionally）+ 新错误码 ×5 + gateway 消费面钉（本体零改动）。
- 先红后绿：`_tmp/phase2-red-core.log`（13 条用例 11F——红形态①③④真红；2 条零回归边界用例基线绿符合设计）→ `_tmp/phase2-green-core-recapture.log` + 3x 复跑稳定；既有回归 50 条零回退（`_tmp/phase2-regression-core.log`）。
- 门禁证据组（`./mvnw test -pl nop-ai -am` 授权支路，理由登记）：链全量运行唯一失败 = nop-auth-service `TestOperationMfaE2E`，**基线批前既有**（基线 `0eb3ea67ba` worktree 隔离复跑同败 6 errors，`_tmp/mfa-baseline.log`；本批 diff nop-auth 零触碰）；`./mvnw test -pl nop-ai` 聚焦 BUILD SUCCESS 48 tests（`_tmp/phase2-gate-nop-ai-focused.log`）；链全量运行留痕（`_tmp/phase2-gate-nop-ai-unfiltered.log`）。附带登记：首轮门禁 `nop-netty TestNettyTcpClient` BindException 为 findAvailableTcpPort 端口竞争环境抖动（隔离复跑绿，与本批无关）；`-Dtest='!X'` 过滤式排除会破坏 jpath 数据驱动生成测试（`JSONPointTest_*`），门禁禁用过滤式排除。
- **实现期偏差登记**：①④盘写异步化落点在 `ChatServiceImpl` 终态回调侧（`logResponseAsync`）而非 `DefaultChatLogger` 内部——IChatLogger 同步契约与既有脱敏/落盘测试零回退优先，异步调度属回调方职责（owner doc 注记归 Phase 3）；②消费面钉语义：适配器对调用方取消「不向输出发终态信号」为既有契约（`callerCancelStopsCurrentAttemptOnly` 钉死；适配器先 cancel 内部订阅再 cancel attempt token 的顺序使核心侧 closeExceptionally 不达已取消订阅），该输出语义归 R2-06，本钉按「有界终止 + 注册不泄漏」断言（核心侧取消闭合红转绿由 nop-ai-core 两条 `_C3_001` 用例承载）；③测试基建：fake 流发射加启动延迟（桥接在 callStream 内部即订阅上游，无延迟则首 chunk 于下游订阅前丢弃——FailoverTestSupport 同型先例）。

### Phase 3（全量验证 + 两侧同批端到端 + owner doc 两份）——completed @ Phase 3 docs+test 提交

- **E1**：`./mvnw clean install -T 1C -DskipTests` BUILD SUCCESS exit 0（`_tmp/phase3-e1-install.log`，453 模块）；授权支路沿 plan 2496~2503 先例，支路理由 = 全量测试由两通道批行门禁 + 新增端到端测试组合覆盖。
- **批行验证（E3）三快照**：①`./mvnw test -pl nop-network -am` BUILD SUCCESS exit 0（`_tmp/phase3-gate-nop-network-rerun.log`，链 2369 tests/0F/0E/11 skip，`TestBodyPhaseCancel_C3_002` 6/6 绿）——首轮 exit 1（`_tmp/phase3-gate-nop-network.log`）为 nop-core `TestResourceCacheInvalidationChain` 3F 时间戳窗抖动：单类隔离复跑 4/4 绿（`_tmp/phase3-nopcore-cache-flake-check.log`）+ 该测试已由 plan 2504 裁定为跨类隔离敏感 watch-only 伪影（`ai-dev/logs/2026/10-26.md` plan 2504 条）+ nop-core 本批 diff 零命中——环境抖动非产品回退，复跑全链绿；②`./mvnw test -pl nop-ai` BUILD SUCCESS exit 0（`_tmp/phase3-gate-nop-ai-focused.log`，链 5177/0F/0E/10 skip）；③`./mvnw test -pl nop-ai -am` BUILD SUCCESS exit 0（`_tmp/phase3-gate-nop-ai-am.log`，链 16989/0F/0E/46 skip，13:51 min，含 nop-auth-service 全绿）——Phase 2 的 MFA 基线既有失败授权支路理由本轮升级为全绿实跑。
- **两侧同批端到端（rule 22）**：新增 `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/TestStreamFullChainEndToEnd_C3_001.java`（4 条，全绿 + 2x 复跑稳定 + 两门禁内绿；`_tmp/phase3-e2e-green.log`/`_tmp/phase3-e2e-rerun{1,2}.log`）：①`cancelTerminatesFullChainCallAsync_C3_001`——callAsync(stream=true) 真实 JdkHttpClient + raw ServerSocket SSE 服务端（headers+首 chunk 后保持连接），流进行中（`future.isDone()==false` 先验）cancel → 2s 观察窗 CancellationException 终态 + 服务端观测客户端 EOF（body 读线程释放/底层流关闭；无悬挂注册由 promise 按 body 终态配对移除 + `TestJdkHttpClientCancelListenerCleanup_C3_012` 承载）；②`aggregateTotalDeadlineTerminatesFullChainSilentSse_C7_001`（仅收紧 `nop.ai.service.stream-total-timeout=800ms`，传输层 idle 锚定缺省 120s → `ERR_AI_STREAM_TOTAL_TIMEOUT` 类型化终态）+ `transportIdleBudgetTerminatesFullChainSilentSse_C7_001`（仅收紧 `nop.http.client.jdk.body-idle-timeout=600ms`，聚合层双键锚定缺省 → 5s 窗内异常终态）——双层预算各自独立断言；③`halfCloseCleanDisconnectFailsLoudFullChain_C7_002`——部分 chunk 后服务端 `shutdownOutput()` 干净半关闭（TCP FIN，无 finish_reason/[DONE]）→ `ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING` fail-loud（R11 红形态④全链版）。构造注记：静默服务端采用「headers+首 chunk 后停滞」而非纯 accept 不回发（后者挂死在 headers 阶段，body 阶段双层预算均不可达）；装配按 `TestChatServiceImpl` 既有先例直构 JdkHttpClient（= http-api beans 缺省 raw client 同型），无新建模块 infra。Phase 2 fake 链 13 条（组件级）与 Phase 1 传输层 6 条不重复计入端到端验收。
- **零触碰（E4）**：`git diff 0eb3ea67ba..HEAD --name-only` 禁触面过滤（nop-tcc/`model/*.orm.xml`/nop-auth/nop-api-core（io.nop.api.core.\*）/`_` 前缀生成物/`.rels`）零命中；产品 diff 全集 = nop-http-client-jdk ×5（产品 4+测试 1）+ nop-http-client-apache ×2 + nop-http-api ×3（= Phase 1 PA 裁定面①：`AbstractServerEventSubscription` 基类有界化 + `HttpApiErrors` 新错误码 + 测试）+ nop-ai-core ×5（产品 3+测试 2，含本 Phase 新增端到端测试）+ nop-ai-gateway 测试 ×1；R1-04 设施（FutureHelper/ExecutorHelper/ICancelToken）/R1-01 绝缘层/R1-02 池设施 diff 零改动。
- **Owner doc（E5）两份逐份结论**：①`docs-for-ai/03-modules/nop-ai.md` = 已更新——「服务级配置（nop-ai-core）」表新增 4 行流式键（stream-total-timeout/stream-idle-timeout/stream-max-accumulated-chars/stream-max-tool-calls 含缺省与 0=禁用语义）+ 新增「流式调用取消/期限/交付语义契约（3.1-C3-001/C7-001/C7-002，plan 2505）」节（取消闭合/背压窗口/受管承载/盘写异步化/R11 分支裁定[completeExceptionally + 空Stream 成功契约边界]五条 + 双层预算与端到端/组件级/消费面钉测试锚点）；②`docs-for-ai/03-modules/nop-network.md` = 已更新——新增「响应体阶段取消与读超时契约（4.3-C3-002 族 / 4.3-C6-004，plan 2505）」节（body 取消响应与 promise 生命周期/body idle 键 + **120s 缺省边界声明**[评审 Minor-2：无 keepalive 且静默 >120s 的合法流将由「滞留后续传」变为 120s 关流错误——配置键调高与 `<=0` 关闭为缓解面，上层聚合预算兜底]/SSE demand 停滞预算/三出口统一关流/反压不可中断面收敛/测试锚点五组）。
- **scan-hollow**：nop-network exit 0（`_tmp/phase3-scan-hollow-nop-network.log`）；nop-ai exit 1 = 4 条批前既有 P6b「not yet implemented」注释模式发现（`ReActAgentExecutor.java:1395`/`:1526`「placeholders」描述性注释误报、`PlanReplanner.java:273`「split placeholder」注释误报、`NoOpProviderFailoverQueue.java:34` 显式文档化 NoOp 缺省实现）——四文件本批 diff 零命中，沿 Phase 1/2 同口径裁定为既有面（`_tmp/phase3-scan-hollow-nop-ai.log`）。
- **门禁**：`check-doc-links.mjs --strict` 退出码 0（`_tmp/phase3-doc-links-final.log`）；`check-plan-checklist.mjs --strict` 退出码 0（non-completed 警告面=Phase 4 未勾选项，符合预期）。

## Deferred But Adjudicated

### CL-025 IHttpResponse 断连观测平台原语（WI 2.3 平台轮归属面）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 4.3 merge-adjudication §七-7 裁定「实现面承接 ↔ 平台原语面」分立——本批已履行实现面承接义务（MA-02/C6-004 修复即承接），平台原语面维持 WI 2.3 平台轮归属及其「MA-13（C3-010）/C4-003 修复先决」关系，不在 5 项 finding 清单内。
- Successor Required: `yes`
- Successor Path: WI 2.3 平台轮（cra-index CL-025 行；R4 相应批次）

### R11/R21 族跨单元成员（4.3-C3-005、3.3-C3-001、3.4-C3-005；R21 各成员）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 族纪律（成功信号与产物落地原子性/completeExceptionally）在本批成员面内适用并落 owner doc；族成员工单各有批次承载（R2-19/R2-06/R2-17 等），Phase 4 台账互注防漏防重。
- Successor Required: `yes`
- Successor Path: 各成员既有批次行（batch-proposal §二 R2-19/R2-06/R2-17 行）

### 3.1-C5-001 失效面⑦⑧⑨（非流式 body/错误响应 JSON/方言逐 chunk 瞬态解析的全量物化面）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计失效面枚举明示该三面为「单次瞬态、上界=HTTP body（client 层配置）」的观察面，非本条缺陷对象；聚合上界（面①–⑥）落地后主 OOM 轨迹已闭。
- Successor Required: `no`（随 3.1 相邻批观察，触发条件 = 实际占用观测）

## Non-Blocking Follow-ups

- **R2-06/R2-16 批次对齐互注**：Phase 4 台账回填时在本批 5 行与 R2-06（FailoverStreamFlow 本体四面）、R2-16（3.1 编排/会话 10 项）行互注「零重叠已核、消费面/本体面分界」，防后续批重做或越界。
- **C6-003/C3-033（R4-26/R4-27）同文件协同注记**：本批 C6-002①/② 与 G67 两工单同文件同分支，Phase 4 回填时在 G67 行注记「416 分支 body close 已由 R2-02 落地，Range 头/SSE 生命周期语义未触碰」。
- **流式超时配置键对账**：新配置键（stream-idle-timeout/流式总期限等）落 owner doc 后与配置参考文档对账（若 `docs-for-ai/` 存在配置索引面则同步，否则登记缺口）。
- **AiCommand 延续链上下文传播面**：3.1-C3-009 现状段载「延续函数在无调用方上下文的线程执行（IContext ThreadLocal 不跨此边界）」——承载受管化后该面形态变化，与 R15/R2-05 上下文传播对账注记（非本批缺陷面）。

## Closure

Status Note: 本计划收口 CRA Phase 8.11（R2-02 流式取消/期限两侧同批）：五项 P1 全修——4.3-C3-002 MA-02 族（读循环取消响应+cleanup 关流+idle watchdog+416/cancel-at-entry 关流+promise 生命周期移至 body 终态）、4.3-C6-004 R06 族（基类 waitForDemand 有界化[PA 面①批内闭环]+apache 队列解耦 daemon 化）、3.1-C3-001 MA-01 族五修复点（取消闭合/双级 request 窗/受管承载/盘写异步化/聚合双上界）、3.1-C7-001 全程期限+inactivity 独立键、3.1-C7-002 R11 fail-loud 分支裁定——两侧同批端到端四场景真实全链绿（E2E 4/4）。roadmap 8.11 → done。
Completed: 2026-10-26

Closure Audit Evidence:

- Reviewer / Agent: agent_6fdbcdc8（独立 closure audit 子代理，fresh session）
- Audit Session: agent_6fdbcdc8-c3fe-48fd-82b1-42aeb3f73b3b
- Evidence: 判定 **PASS with corrections**（零 Blocker；1 Major 证据引用订正+2 Minor 均随本收口处置）。①五成员实仓抽验全在位（file:line 见审计报告）；②零回退钉 9 条+九条 R1-01~R1-08 产物零回改实证；③测试实跑：E2E 4/4+TestBodyPhaseCancel_C3_002 6/6+TestStreamCancelDeadlineAndDelivery_C3_001 13/13 全绿（audit fresh 实跑）；④Major-1 订正：`_tmp/phase2-green-core.log` 被中间调试跑覆盖（2F），已重捕获 `_tmp/phase2-green-core-recapture.log`（BUILD SUCCESS 13 条全绿）并全量替换 plan 引用；⑤Minor-1 时序敏感 watch-only 登记（carrier-thread/5s 取消窗单次时序性失败，HEAD 复跑绿）；⑥Minor-2 流程观察登记（audit 清单 G56 条款系他批误植）。双门禁：`check-doc-links --strict` EXIT=0；`check-plan-checklist --strict` EXIT=0（收口后复核）。Deferred 分类检查：三条均为入批前已裁定批外/相邻项、successor 清晰，无 in-scope live defect 被降级。

Follow-up:

- Non-Blocking Follow-ups 所列项均已登记归属（R4-23 cancelToken/G67 边界/C10 跨批矩阵/R2-16 对齐），no remaining plan-owned work
