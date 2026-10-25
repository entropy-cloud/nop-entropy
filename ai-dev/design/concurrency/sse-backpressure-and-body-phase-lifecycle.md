# SSE 反压有界化与响应体阶段生命周期设计

**日期**：2026-10-25
**范围**：`nop-network/nop-http/nop-http-api`（`AbstractServerEventSubscription` 共享基类——plan 2505 PA 裁定面）、`nop-network/nop-http/nop-http-client-jdk`（`JdkHttpClient` 下载读循环 + `ServerEventPublisher`）、`nop-network/nop-http/nop-http-client-apache`（`ServerEventPublisher`）
**状态**：active
**来源**：plan 2505（CRA Phase 8.11 / R2-02）Phase 1 PA 裁定项——本批 4.3-C6-004 修复面①（`waitForDemand` 超时语义）经 live 结构性核验叶内不可达，按 AGENTS.md plan-first 在批内闭环；本设计是该闭环的设计文档，同时承载 4.3-MA-02 族（C3-002/C3-003/C6-002/CL-026 同 diff）与 C6-004 面②③④的契约。发现事实源 = plan 2505 Current Baseline 成员表与 `ai-dev/audits/2026-10/2026-10-21-0610-deep-audit-nop-network-integration-concurrency/`
**Last Reviewed**：2026-10-25 落笔（独立评审由协调方另行派发，评审结论回填本节）

---

## 一、设计结论

1. **裁定①（PA 面，基类 demand 等待有界化）**：`AbstractServerEventSubscription` 的 `waitForDemand` 从无限期 `wait()`（仅 cancel/notify 可唤醒）改为**有界等待**——预算缺省 120 秒（基类常量），经 protected 钩子可被叶或测试覆盖。预算耗尽即「订阅者停滞终态」：先 `cleanup`（置位取消 + 唤醒 + 释放承载资源）再向订阅者派发 `onError`（新错误码 `ERR_HTTP_SSE_DEMAND_TIMEOUT`，携带停滞预算诊断）——滞留可自愈、终态显式（R21 纪律：异常终态非静默）。`onComplete` 的残余事件派发（final flush）继承同一有界性。上移理由（live 结构性事实，PA 依据）：基类 `demand` 为 private、`dispatchEvent()` 为 private 且硬编码 waitForDemand→onNext 顺序，叶类既无法读写 demand 也无法改变派发时序，叶内影子计数会与基类 `request()` 记账双轨冲突——**面①叶内不可达**，唯一正确落点是共享基类。
2. **裁定②（apache 面，reactor 线程零等待）**：apache `ServerEventSubscription` 的 `processLine` / `onComplete` / `onError` 不再由 HC5 I/O reactor 线程同步直调——reactor 线程只把行与终态**投递**到有界 `OverflowBlockingQueue`（平台 `IBlockingQueue` + `QueueOverflowPolicy` 形态，溢出策略 = 满载 fail-loud 抛 `ERR_QUEUE_FULL`），由**专用 daemon 消费线程**逐行执行基类 `processLine`（demand 等待发生在消费线程上，被裁定①有界化）。终态（complete/error）经队列 sentinel 串行化，保持「先到的事件先派发、终态在最后一个事件之后」的顺序契约。
3. **裁定③（jdk 面，body 阶段可取消可超时）**：jdk 侧响应体阶段的取消与超时收敛为三个出口——(a) **cleanup 关闭底层流**：`ServerEventSubscription.cleanup` 先执行基类置位/唤醒，再显式关闭已登记的底层 body `InputStream`（volatile 登记），脱离「`future.cancel(true)` 对 headers 后已完成的 sendAsync future 为 no-op」的失效形态；(b) **读循环取消响应点**：下载 `processDownloadResponse` 每次迭代检查 `cancelToken.isCancelled()`，取消即抛 `CancellationException` 并关闭 sink/in，promise 以取消终态完成（R21）；(c) **body idle 读超时**：`BodyIdleWatchdog`（复用 `GlobalExecutors.globalTimer()` 调度，任务体仅「检查 + 关闭流」短动作，符合 MA-16 收窄后豁免判据）按 idle 预算监视读进度，超阈关闭底层流解除 `read/readLine` 无限阻塞。下载绑定动作补挂 pendingBody 即时关闭，使取消在「future 已完成、读任务未启动」窗口同样即时生效。取消监听注册维持 `FutureHelper.bindCancelToken` 平台单点（R1-04 原语零回退、零签名变更）。
4. **裁定④（预算承载，零 nop-http-api 配置面）**：body idle 预算承载为 **jdk 叶内配置键** `nop.http.client.jdk.body-idle-timeout`（毫秒，缺省 120000）——**不触碰 nop-http-api 配置面**（plan 头部第二潜在触碰形态裁定：不需要上移）。基类 demand 等待预算为基类常量缺省 + protected 覆盖钩子，不设配置键（PA 面最小触碰原则）。
5. **裁定⑤（C6-002 两早退出口 + daemon 兜底）**：下载 416 重试分支与 SSE cancel-at-entry 分支在 return 前显式关闭遗弃的 `response.body()`（`IoHelper.safeClose`）；本批新增的唯一长阻塞承载线程（apache SSE 消费线程）以 daemon 线程工厂落地（挂死不阻断 JVM 退出）。jdk `executor()` 的 `GlobalExecutors.globalWorker` 回退为既有平台约定（plan 2498 边界）维持不动，其线程本身已 daemon——R06 族「daemon 化」兜底半边落在新增承载面上。

## 二、背景与动机

R2-02 批次的 4.3 传输层缺陷聚为两条根因线：

- **响应体阶段生命周期线（4.3-MA-02 族）**：JDK `HttpResponse.BodyHandlers.ofInputStream` 的 future 完成于响应头，body 阶段由本模块手工读取——但取消监听只绑定 headers 阶段的 sendAsync future（`bindCancelToken(cancelToken, reason -> future.cancel(false), ...)` 对已完成 future 是 no-op），读循环无取消响应点、无读超时、两处早退分支遗弃 body 流。取消后幽灵下载继续写盘（C3-002）、SSE 读取线程按次泄漏（C3-003）、416/取消早退遗弃 socket（C6-002①②）、下载静默滞留不可中断（CL-026）。
- **反压承载线程线（4.3-C6-004）**：SSE 反压以「阻塞承载线程的 wait」实现——demand 等待无超时（仅 cancel 可唤醒），apache 形态直接挂在 HC5 I/O reactor 线程上（同 reactor 全部连接停摆），jdk 形态叠加 readLine 不可中断（线程按次永久滞留）。R06 族修复口径 = 可中断/有界执行路径 + daemon 承载兜底。

与既有设计的衔接：[bindcancel-context-unification.md](bindcancel-context-unification.md) §3.6 的 `FutureHelper.bindCancelToken`（注册窗口补触发 + 终态反注册）是本设计全部取消监听的唯一注册入口——本缺陷本体不在注册面而在**绑定动作**（cleanup 的目标、绑定 lambda 的副作用）与**读循环**，接口契约零变更。

## 三、核心设计

### 3.1 裁定① 基类 demand 等待有界化（PA 面）

**等待契约**：`waitForDemand()` 语义变为「在预算内等待 demand 或取消」——demand 到位消耗一个 demand 并返回 true；**预算耗尽**返回 false（停滞）；**取消**返回 false（不消耗 demand，不产生停滞错误）。`dispatchEvent` 对 false 返回值分派：已取消 → 静默跳过（Flow 取消契约：cancel 后无终止义务）；未取消 → **停滞终态** = `cleanup` + `onError(ERR_HTTP_SSE_DEMAND_TIMEOUT)`。停滞终态的优先序：先 cleanup（置位使 jdk 读循环在下一个行边界退出、唤醒全部等待者）再 onError（订阅者收到的终态与订阅内部状态一致）。

**预算**：基类常量缺省 120 秒（与 SSE 长静默常态兼容——审计口径「SSE 静默间隔 >30s 为常态」，30s 级预算会误杀合法空闲流）；protected `demandWaitTimeoutMillis()` 钩子供叶/测试收紧。预算是**每次等待**的上界而非累计流时长（流总量期限归消费方 3.1-C7-001 承载，两侧同批）。

**行为不变式（对既有调用方）**：demand 充足的订阅者（`request(MAX)` 派）行为零变化（等待永不发生）；有界 demand 且正常 request 的订阅者零变化；唯一行为变化 = 「不 request 也不 cancel 的停滞订阅者」从「挂起承载线程无限期」变为「预算内错误终态」——该行为正是被审计确认的缺陷而非可依赖契约。

### 3.2 裁定② apache 队列化

**拓扑**：

```mermaid
flowchart LR
    RT[HC5 reactor 线程<br>onLine/onStreamComplete/failed] -->|send line / sentinel<br>有界队列 满载 fail-loud| Q[OverflowBlockingQueue<br>ERROR 策略]
    Q -->|take| CT[daemon SSE 消费线程<br>nop-sse-dispatch]
    CT -->|super.processLine| B[基类事件累积与派发<br>waitForDemand 有界]
    B --> S[订阅者]
```

**队列契约**：容量有界（1024 行）；溢出策略 = `QueueOverflowPolicy` 缺省 ERROR 形态（满载抛 `ERR_QUEUE_FULL`）——异常沿 HC5 消费者协议进入 `failed` → 终态化订阅。reactor 线程在任何负载形态下**零阻塞**（不 wait、不 take）。被拒策略：BLOCK_WAIT（满载时 reactor 阻塞回归缺陷形态）、DROP_*/静默丢弃（SSE 数据静默丢失 = 数据完整性违约）、无界队列（C5 无界累积面，消费线程停滞时内存无上界）。

**终态串行化**：`onComplete` 与 `onError` 由 reactor 线程投递 sentinel，消费线程按队列序执行基类终态（含 final flush 的有界 demand 等待）。终态 sentinel 单次投递守卫（首个终态胜出，后到终态丢弃——Flow 单终态纪律），终态后到达的数据行由消费线程按 `isCancelled` 退出循环自然丢弃。

**消费线程生命周期**：首次行投递时惰性创建（daemon、命名）；每个条目处理后检查取消态，取消或终态后退出；demand 停滞时被裁定①有界化（≤ 预算）终态退出——线程按订阅有界存活，无池化残留。

### 3.3 裁定③ jdk body 阶段取消/超时出口

**三出口分工**：

| 出口 | 触发 | 动作 | 解除的阻塞点 |
|---|---|---|---|
| cleanup 关闭底层流 | 调用方 `cancel()` / token 取消（经 bindCancelToken） | 基类置位 + notifyAll 后，关闭已登记 body InputStream | readLine/read 即时抛 IOException（活跃与静默连接均生效） |
| 读循环取消响应点 | 迭代间 `isCancelled()` | 抛 `CancellationException`，sink/in 关闭，promise 取消终态 | 幽灵下载（数据流动时取消延迟 ≤ 一次 read 返回） |
| body idle watchdog | 进度停滞超过 idle 预算 或 token 已取消 | 关闭底层 InputStream | 服务端中途停滞（无数据可读、无取消数据流）时 read/readLine 无限阻塞（CL-026） |

**watchdog 承载**：`GlobalExecutors.globalTimer()` 固定延迟巡检（tick = max(100ms, idle/10)），任务体仅「进度/取消判定 + 关闭流」短动作——不属于 MA-16 收窄后禁止的「长阻塞任务体直连 globalTimer」。进度判定基准 = 最近一次成功 read 的墙钟时刻（`CoreMetrics.currentTimeMillis()` 口径）。

**生命周期出口收口**：416 重试分支与 SSE cancel-at-entry 分支 return 前关闭 body（C6-002①②）；下载取消路径 promise 以 `CancellationException` 异常终态（R21 显式可观测）；SSE 取消后按 Flow 契约静默退出（无义务补终态，下游以自身取消为准——3.1 聚合层由 C3-001 同批闭合下游）。

### 3.4 配置契约

| 配置键 | 承载面 | 缺省 | 语义 |
|---|---|---|---|
| `nop.http.client.jdk.body-idle-timeout` | nop-http-client-jdk 叶内（本批新增，唯一配置面增量） | 120000 ms | 下载/SSE body 阶段 idle 读超时；≤0 关闭 watchdog（不推荐） |
| 基类 demand 等待预算 | nop-http-api 基类常量 + protected 钩子 | 120000 ms | 停滞订阅者 demand 等待上界；无配置键（PA 最小触碰） |

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 面①在叶内以影子 demand 计数实现超时 | 基类 `demand` private、`dispatchEvent()` private 硬编码派发顺序——影子计数与基类 `request()` 记账双轨冲突（重复消耗/泄漏 demand），且无法拦截 onComplete 的 final flush 派发 |
| demand 超时后仅 cleanup 不派发 onError | 订阅者无终止信号即悬挂（3.1-C3-001 的下游悬挂形态），违反 R21 纪律（异常终态可观测） |
| apache 满载 BLOCK_WAIT 策略 | 满载时 reactor 线程阻塞回归 C6-004 缺陷形态（只是从 wait 换成 put） |
| apache 无界队列 | 消费线程停滞时入队无上界（C5 无界累积面）；有界 + fail-loud 使停滞显式可见 |
| DROP_NEWEST/DROP_ELDEST 丢行保活 | SSE 行丢弃 = 事件数据静默损坏（半个成功族形态）；停滞应显式失败而非静默降级 |
| SSE idle 超时上移 nop-http-api 配置面（第二潜在触碰形态） | plan 头部裁定预算承载优先 jdk 叶内配置键——叶内键已满足承载，避免 nop-http-api 配置面二次触碰 |
| watchdog 改用每行 cancel/reschedule 精确超时 | 高频流上调度器操作翻倍（每行两次全局 timer 操作）；固定巡检 tick 以极小检测延迟代价换取零调度churn |
| 下载取消仅在绑定动作关 body | 绑定动作触发时 body 引用常为 null（future 未完成）——必须读循环检查点 + pendingBody 登记 + watchdog 三点互补才覆盖全部时序窗 |
| 改动 jdk `executor()` globalWorker 回退 | plan 2498 载体约定边界（R1-03 卸载形态维持）；该池线程已 daemon，残留面收敛由读超时/可关闭化承担 |

## 五、SPI 回归清单（基类触达面全列）

`AbstractServerEventSubscription` 为 nop-http-api 公共抽象基类。live grep（`extends AbstractServerEventSubscription`，@ 基线 0eb3ea67ba）全部子类：

| # | 类 | 承载 | 回归义务 |
|---|---|---|---|
| 1 | `io.nop.http.client.jdk.ServerEventPublisher$ServerEventSubscription` | jdk SSE 订阅 | 本批改造面（裁定③）；回归 = `TestServerEventPublisherStreamFix` + `TestServerEventPublisherErrorResponse` + 本批新增 `_C3_002` 用例 |
| 2 | `io.nop.http.client.apache.ServerEventPublisher$ServerEventSubscription` | apache SSE 订阅 | 本批改造面（裁定②）；回归 = `TestServerEventPublisherErrorResponse`（apache）+ 本批新增 `_C6_004` 用例 |
| 3 | `io.nop.http.api.client.TestServerEventSubscriptionFix$TestSubscription` | 基类测试桩 | 既有 5 条全绿维持 + 本批新增 demand 超时用例 |

行为兼容矩阵：`waitForDemand`/`dispatchEvent` 无外部 override（grep 零命中）；`processLine` 唯一外部调用形态为测试桩 `feed` 直调（demand 充足路径，行为不变）；`fetchServerEventFlow` 生产消费方 `ChatServiceImpl`（本批 Phase 2 承接下游闭合）与 nop-gateway `BufferedStreamingPublisher`/`StreamingProcessor`（request(MAX) 派——demand 等待不触发，有界化对其零行为变化）。

## 六、与已有设计的关系

- **上游**：[bindcancel-context-unification.md](bindcancel-context-unification.md)（R1-04/plan 2499）的 `bindCancelToken` 单点收敛与 ICancelToken 契约为本设计全部取消监听的注册入口；本设计零接口签名变更维持其裁定⑤。
- **下游**：3.1 聚合层（plan 2505 Phase 2）消费本设计的可取消/有界 body 阶段——聚合层全程期限（C7-001）与本层 idle 读超时构成两侧同批双层预算（4.3 merge-adjudication §七-6）；CL-025 断连观测平台原语面维持平台轮归属（Deferred，实现面承接义务由本设计履行）。
- **批次边界**：C6-003（416 Range 头语义）与 C3-033（SSE 生命周期终态隔离）归 R4-26/R4-27——本设计只闭合响应体流生命周期（close）与 demand 等待有界性，不改 Range 语义、不建终态 once-guard 状态机（终态 sentinel 单次投递守卫仅服务队列串行化，非 C3-033 语义）。
- **钉死面**：`TestServerEventSubscriptionFix`（基类 5 条）、`TestServerEventPublisherStreamFix`、`TestJdkHttpClientCancelListenerCleanup_C3_012`、`TestServerEventPublisherErrorResponse`（jdk/apache）、`TestFileTransfer` 为本批共享代码位回归门禁。
