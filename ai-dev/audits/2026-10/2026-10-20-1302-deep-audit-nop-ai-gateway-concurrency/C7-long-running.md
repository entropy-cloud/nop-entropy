# C7 长时运行稳定性 — nop-ai-gateway（WI 3.3）

- **审计维度**: C7 长时运行稳定性（单调增长 / 挂死可检测性 / 重试收敛 / 墙钟敏感 / 自愈语义 / 热路径慢性病）
- **审计日期**: 2026-10-20
- **审计员**: C7 维度盲审初审员（单批次）
- **复核状态**: 已复核（WI 3.3 独立复核合并批 C6+C7，见文末「维度复核结论」）

## 审计范围与口径

### 覆盖声明

单批次全源码根覆盖：`nop-ai/nop-ai-gateway/src/main/java` 全部 36 个文件、6,035 行，逐文件完整审读（含 javadoc 契约面），无抽样。重点站点核对完成：failover 重试/熔断链（AiGatewayFailoverInterceptor / ChatServiceFailoverAdapter / FailoverStreamFlow / GatewayStreamingRetryCallback / CircuitObservation / FailoverProbeSupport / CandidateHealth）、routing 游走与并发注册表（ModelClassRouter / ConcurrencyRegistry / Default & RuleBased SelectionStrategy）、channel 长连接生命周期（FeishuConnector / ChannelMessageServiceImpl / ChannelConnectorManager / ChannelSessionStoreImpl）、登录编排（ChannelLoginApiBizModel / ChannelLoginScanProcessor）。基线 `_tmp/cra-baseline-nop-ai-gateway.txt` 的 C7a/C7b/C7c 节全部人工复现核对（counter increments / lifecycle cleanup / session state writes 三节命中站点均已覆盖，数量结论按口径纪律人工重算，非脚本转录）。

### 心跳/超时调度器缺席裁定（留痕，不越界立案）

本模块源码根内 **不存在任何** 自建调度器/看门狗/心跳设施（grep `ScheduledExecutor|scheduleAtFixedRate|scheduleWithFixedDelay|new Timer|delayedExecutor` 零命中）。由此产生的三个长等待点裁定如下：

1. **流式 attempt 上游静默挂死**（无产出、无终态、无异常）：模块内无 stall 检测，FailoverStreamFlow 的 N/T 窗口只门控首段（N=10/T=1000ms），不覆盖挂死。该等待的期限设施完全依赖 delegate（nop-ai ChatServiceImpl 的 HTTP 客户端超时，越界）。**裁定：不在本维度立案**（期限设施属 delegate 批次；本模块无可立案的守卫缺失——模块契约未声明自带 watchdog）。
2. **HALF_OPEN 探活请求挂死 → breaker 悬置**：探活无额外 deadline，恢复语义依赖 ThresholdBreaker（nop-ai-core，另批次）+ delegate 超时。**裁定：不立案留痕**（熔断器状态机本体越界）。
3. **重试链无总延迟上限**：owner doc `docs-for-ai/03-modules/nop-ai-gateway.md` L105 明文声明「总延迟上限默认不设（仅次数预算）」——按口径纪律第 6 条（已声明的边界不是缺陷），**不立案**。

### 与 C1–C6 已立案条目的边界声明

- **C2-001（acquire 永不释放）**：其长程行为后果 = 账号计数永久占用 → 模型类整体饱和 fail-loud → 网关需重启才恢复，且 ConcurrencyRegistry 归零收缩语义使被泄漏卡在 >0 的键永不收缩、无 gauge 暴露当前计数。本维度确认该长程后果真实（registry 无对账/TTL/管理口），但按边界纪律**声明为 C2-001 的 F3 长程后果组成部分，不另立案**。本维度四条发现均不位于该站点。
- **C3-001（终态冲刷静默截断）**：流式终态路径的冲刷丢数据面归 C3-001。本维度 C7-001 审的是**非流式** future 永不完成面（不同站点、不同失效面），C7-002 审窗口判定的时钟面。
- **C4-004（停机零调用）/ C4-005（传输线程内联）**：FeishuConnector 停机与 onMessage 内联回复面归 C4。C7-003 的限流锁死后果中「拒绝回复走传输线程内联」部分**不重复立案**，只引用。
- **C6-004（会话孤儿化）**：channel↔session 映射的孤儿面归 C6-004。本维度 CL-015（并发首聊重复映射行）是**并发插入竞态**这一不同机制，以 CL 线索形式移交 C1/C6 维度裁定，不在 C7 立案（非长时运行专属失效面）。
- **C1/C2（FailoverStreamFlow 窗口清理/并发计数泄漏、监视器投递）**：本维度不重复；C7-004 的 fan-out 池是 ChannelMessageServiceImpl 的独立站点（非 FailoverStreamFlow）。

### 口径复核

- 严重度判级、误报校准、规模包络对照（owner doc L105 重试预算=2、L201 concurrencyLimit 层级语义、Feishu DEFAULT_RATE_LIMIT_PER_MINUTE=3000 声明于代码内）均按共享前缀执行；长时运行形态无启动期单线程降档适用项。
- 时钟语义取证：`io.nop.api.core.time.CoreMetrics.currentTimeMillis()` = `System.currentTimeMillis()`（墙钟）、`nanoTime()` = `System.nanoTime()`（单调钟），证据 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/CoreMetrics.java:23-28`。

### 单调增长源清单（C7 执行步骤 1 盘点结论）

| # | 增长源 | 界 | 结论 |
|---|---|---|---|
| 1 | `ConcurrencyRegistry.counts` | 归零即移除键（收缩语义，javadoc L37-39） | 有界，无发现 |
| 2 | `FeishuConnector.inboundWindow` | ≤ rateLimitPerMinute(3000)，滚动淘汰 | 有界；墙钟缺陷见 C7-003 |
| 3 | `ChannelMessageServiceImpl.subscribedTopics` | ≤ channelType 数 | 有界 |
| 4 | `ChannelConnectorManager.connectors/started` | 启动期装配，固定 | 有界 |
| 5 | `ModelClassRouter.attempted` | per-execution，随请求消亡 | 有界 |
| 6 | Micrometer meters（FailoverMetricsImpl 全部 12 指标） | 标签基数 = provider×model×mask(account)×outcome，配置界 | 有界，无基数爆炸 |
| 7 | `NopAiChannelSession` 表 | 每 (channelType,channelId) 一行，只增不删（无 TTL/清理路径） | 业务映射数据，增速=独立会话数；F4 观察项：无清理面 + 并发重复行（CL-015） |
| 8 | `FailoverStreamFlow` per-attempt buffer | 窗内 ≤ N(10)；越窗后受 demand 支配 | 零需求无界 → CL-014（C5 面） |
| 9 | fan-out 执行器队列 | 缺省 owned 池 = 无界 LinkedBlockingQueue | **无界 → C7-004 立案** |

### 挂死点清单（C7 执行步骤 2 盘点结论）

| # | 挂死点 | 期限设施 | 结论 |
|---|---|---|---|
| 1 | 非流式 future 永不完成路径 | 无 | **C7-001 立案** |
| 2 | 流式 attempt 上游静默挂死 | 仅 delegate HTTP 超时（越界） | 留痕裁定（见上），不立案 |
| 3 | HALF_OPEN 探活挂死 | 无模块内 deadline | 留痕裁定，不立案 |
| 4 | fan-out listener 不可中断阻塞 | 30s orTimeout + interrupt（不可强制） | **C7-004 立案** |
| 5 | dispatchInbound mode-1/2 | dispatchTimeoutMs(30s) 界定 | 有界，无发现 |
| 6 | Feishu onMessage 内联 sendMessage | 无 | C4-005 已立案面，不重复 |

### 重试收敛核验（C7 执行步骤 3，无发现声明）

retryBudget=2 双向有界；CACHE_STATE_LOST 原地重发、切换重选、饱和重选（invokeNonStreamingAttempt:262 / callAsyncAttempt:216 / startAttemptWithOptions:273 三处递归）均受 `ModelClassRouter.attempted` 单调增长约束，必然收敛至 `ERR_AI_MODEL_CLASS_SATURATED` fail-loud，递归深度 ≤ 候选池规模；provider 链扩展一次性（`providerChainExtended` 门控）；重试无退避为即时切换语义、账号并发上限为保护（声明语义）；无定时任务失败重试叠加。重试风暴面无发现。

## 发现

### [C7-001] 非流式终态回调 release/决策先行且异常被吞 → future 永不完成的不可检测挂死

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:283-310`；同构站点 `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java:245-274`
- **证据片段**:
  ```java
  // AiGatewayFailoverInterceptor.java:283-292
  CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
  stage.whenComplete((resp, err) -> {
      // 全终止路径 release（M-2）：成功、失败切换、NON_TRANSIENT、async 异常。
      registry.release(candidate.getProvider(), candidate.getAccountKey());   // ← 此处抛出 =
      if (metrics != null) {                                                   //   后续全部跳过
          metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
      }
      if (err != null) {
          decideNonStreamFailure(future, invocation, request, svcCtx, router, attempt, candidate, null, err, startNanos);
  ```
- **严重程度**: P2
- **并发场景**: HTTP worker 线程执行 `proceedInvoke` 返回 CompletionStage；其完成回调（可能是 provider HTTP 客户端线程或 ForkJoinPool.commonPool）执行 whenComplete 体内的 release/熔断记账/递归重试。回调内任何 Throwable 均被 CompletableFuture 语义静默吞掉，`future` 永不被 complete/completeExceptionally。
- **触发时序**: 前序缺陷使 acquire/release 失配（仓库内有实测先例：`ChatServiceFailoverAdapter.java:296-299` 注释自证「W8 OBS-02 实测：缺失 acquire 导致 release 下溢异常被 whenComplete 吞掉、future 永不完成」）。T1：请求 R 的 attempt 完成，回调执行到 :286 `registry.release(...)`，计数已为 0 → `ConcurrencyRegistry.release` 在 compute 内抛 `ERR_AI_AGENT_INVALID_ARG`（ConcurrencyRegistry.java:85-93）；T2：异常沿 whenComplete 被 CF 吞掉，`decideNonStreamFailure` 未执行；T3：`future` 永不完成 → 调用方无限期等待。同类触发面还包括 `decideNonStreamFailure` 链上 `CircuitObservation.recordFailure → breaker.recordFailure`（:344，ThresholdBreaker 非本模块异常契约）或递归 `invokeNonStreamingAttempt` 内 `registry.acquire → key()` 对 provider=null 抛错（ConcurrencyRegistry.java:113-117）。理论化的前序触发 + 结构性吞异常路径本身确定存在。
- **现状**: 终态 future 的完成完全依赖回调体内零异常；release 下溢、熔断记账异常、递归选择异常三条路径中任一抛出都使请求永久挂起，模块内无任何超时/看门狗兜底（dispatchTimeoutMs 只保护 channel 派发路径，不保护 failover 非流式路径）。
- **风险**: 单个失衡缺陷（历史已发生一次）即从「响亮错误」退化为「永久挂起」：上游 HTTP 连接与请求线程占用持续增长，且对外完全不可见（无日志、无指标）——正是 F5「不产出也不报错」形态；长时运行网关中挂起请求累积会放大连接池与内存压力。
- **建议**: 终态回调体内层包裹 try/catch，catch 中 `future.completeExceptionally(...)` 兜底（fail-loud 不吞）；或按平台设施复用 `FutureHelper`/`orTimeout` 给终态 future 挂模块级兜底期限。release 下溢建议降级为「记日志 + 强制终态」而非在回调内抛出（fail-fast 语义保留在直接调用路径即可）。
- **失效面枚举**（F3 同族清扫）:
  1. `AiGatewayFailoverInterceptor.invokeNonStreamingAttempt` 异步 whenComplete 路径（:284-310）——**受影响**（本条主站点）。
  2. `AiGatewayFailoverInterceptor` 同步 catch 路径（:271-282）——不受影响（release 抛出向调用方传播，loud）。
  3. `ChatServiceFailoverAdapter.callAsyncWithOptions` 异步 whenComplete（:246-274）——**受影响**（与 1 同构，本条一并立案）。
  4. `ChatServiceFailoverAdapter` 同步 catch（:235-244）——不受影响（loud）。
  5. CACHE_STATE_LOST 原地重发 `completeRelay`（ChatServiceFailoverAdapter:312-313 / AiGatewayFailoverInterceptor:334/351）——relay 本身不可抛；其内部嵌套 attempt 的 release 位于第 3 项面内，不另立。
  6. `FailoverStreamFlow` 终态路径——release 抛出会向上游 subscriber onError 链传播（不同失效面；终态冲刷截断面归 C3-001，不在此重复）。
  7. `GatewayStreamingLifecycleListener.onStreamTerminated`（:55-57）——release 抛出向 nop-gateway 缓冲层回调传播，属 C2-001 记账失衡 F3 面，不重复。
- **信心水平**: 确定（吞异常结构可直接从代码证明；触发依赖前序失衡缺陷，但该前序在仓库内有实测复发记录）
- **误报排除**: 不属于「原语使用不是问题」——问题不在 CompletableFuture 本身，而在终态回调体内执行可抛副作用且无兜底终态的复合结构；也不是低频管理路径（非流式是主路径）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同单元已立案站点重复登记——并入 C3-004 工单不独立计数）。与 C3-004 面①②同站点同机制：两站点行号完全重合（interceptor whenComplete L283-310 / adapter whenComplete L245-274，回调体无护栏、release 先行、异常落入无人消费从属 stage → future 永不完成——逐行回码证实）；C7-001 独有增量仅触发器侧重（release 下溢 + OBS-02 实测先例注释 adapter L296-299 在库实证）与 F5 挂死不可检测表述，不构成独立缺陷面。终判随 C3-004 P2（failover 非流式主路径 live 面，泄漏/长时运行降档口径均不适用）；台账查重：与 WI 1.7 C6-001 / WI 3.2 MA-01 同族异模块（回调体 Throwable 护栏统一形态），批次对齐不重复

### [C7-002] 流式缓冲窗口 T 判定与 attempt 时长指标用墙钟 — 时钟前跳使全部 in-flight 流的重试窗口同时塌缩

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:154,360`（窗口判定）；`:424,477`（时长指标）
- **证据片段**:
  ```java
  // FailoverStreamFlow.java:154, 358-360
  final long windowStart = CoreMetrics.currentTimeMillis();
  ...
  if (!state.passed) {
      if (state.buffer.size() < adapter.getBufferSize()
              && CoreMetrics.currentTimeMillis() - state.windowStart < adapter.getBufferTimeMs()) {
  ```
- **严重程度**: P2
- **并发场景**: 多条流式请求各持独立 AttemptState；Feishu/NTP 校正或 VM 迁移造成的系统时钟步进是全局事件，同时作用于所有 in-flight 流的窗口判定；与本地形态路径（ChatServiceFailoverAdapter 用 `CoreMetrics.nanoTime()`，:230/261/289）在同一模块内口径不一致。
- **触发时序**: T1：流式请求 R 建立 attempt1，`windowStart = W`（墙钟，:154）；首元素 0.3s 后到达，入缓冲。T2：系统时钟被前跳 +30s（NTP 初次同步步进/运维改钟/虚拟机迁移）。T3：R 的下一元素到达，`currentTimeMillis() - windowStart = 30.x s ≥ bufferTimeMs(1000)` → `passed=true`，缓冲冲刷、窗口即刻越界。后果：R 此后任何失败按窗口外处理 → 断流报错，failover 重订阅/账号切换被跳过；且**所有真实年龄 <1s 的 in-flight 流在同一时刻集体塌缩窗口**。时钟回拨方向则使 `handleStreamFailure`/`handleStreamComplete`（:424/477）的时长指标出现负值/失真（墙钟差为负），污染 request.duration timer。
- **现状**: 模块内两处墙钟判定点（本条窗口 + C7-003 限流窗）与三处单调钟判定点（nanoTime）并存；窗口语义（N/T 先到者越窗，Phase 1 D1）对时钟单调性有隐含假设，未声明。
- **风险**: 时钟步进是长时运行部署的常态事件；步进瞬间 failover 的「窗口内失败可重订阅」保障对整个 in-flight 流群失效，表现为一次时钟校正后短时间内流式失败全部直接断流（可用性劣化），且难以事后归因（时长指标同被污染）。
- **建议**: 窗口判定与 attempt 时长统一改用 `CoreMetrics.nanoTime()`（与 ChatServiceFailoverAdapter/AiGatewayFailoverInterceptor 既有口径一致）；`AttemptState.windowStart` 改存 `System.nanoTime()` 基准。
- **失效面枚举**（F3 同族清扫，全模块时钟判定点 grep 举证）:
  1. `FailoverStreamFlow:154/360` 窗口判定——**墙钟，本条主站点**。
  2. `FailoverStreamFlow:424/477` attempt 时长指标——**墙钟，本条附带立案**（同源 windowStart）。
  3. `FeishuConnector:465` 入站限流滚动窗——**墙钟**，独立后果较大，另立 C7-003。
  4. `AiGatewayFailoverInterceptor:269/299/326`——`nanoTime()` 单调钟，不受影响。
  5. `ChatServiceFailoverAdapter:230/261/289`——`nanoTime()` 单调钟，不受影响。
  6. `CoreMetrics.timeoutToExpireTime`（nop-api-core）——平台设施自身，按审计纪律不重审。
- **信心水平**: 确定（墙钟使用与步进行为可直接从代码与 JDK 语义证明；步进事件在生产环境属常态）
- **误报排除**: 不是「为压测写的简化并发模型」；窗口 T 是 Phase 1 正式裁定机制（D1），其实现时钟选择缺陷影响生产可用性行为；爆炸半径虽单流有界（窗口只存在于首秒级），但全局同时塌缩使该面超出单流噪声。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。机制回码证实：`windowStart = CoreMetrics.currentTimeMillis()`（AttemptState 字段初始化，墙钟）× 窗口判定（bufferOrForward 墙钟差）× 时长指标（handleStreamFailure/handleStreamComplete 同墙钟）三站点成立；对照组实证（ChatServiceFailoverAdapter/interceptor 均 nanoTime——模块内口径不一致属实）；CoreMetrics.currentTimeMillis = System.currentTimeMillis / nanoTime = System.nanoTime（nop-api-core 源码证实）；时钟前跳 → 全部 in-flight 流窗口同时塌缩、回拨 → 负值时长指标的 JDK 语义论证成立。触发面 live（failover 流式主链，C3 复核确认），非泄漏/残留/启动期形态，直接维持 P2。一处表述保留意见：初审"时钟步进属常态事件"为环境假设非代码事实——按"可具体化触发链 + live 面"口径判级仍成立。与 C7-003 同族（墙钟）不同站点同批复频修；与 WI 2.3 C8-005（令牌桶墙钟）异模块同族批次对齐不重复

### [C7-003] Feishu 入站限流滚动窗用墙钟 — 时钟回拨后窗口锁死，通道可达 Δ 量级时长的全量拒绝

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:464-475`（判定），`:135`（`inboundWindow` 持有）
- **证据片段**:
  ```java
  // FeishuConnector.java:464-475
  private boolean tryAcquireInbound() {
      long now = CoreMetrics.currentTimeMillis();          // 墙钟
      long cutoff = now - RATE_LIMIT_WINDOW_MS;
      synchronized (rateLimitLock) {
          inboundWindow.removeIf(ts -> ts < cutoff);        // 回拨后恒空转
          if (inboundWindow.size() >= rateLimitPerMinute) {
              return false;
          }
          inboundWindow.add(now);
          return true;
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: Feishu 长连接回调线程并发调用 `onMessage → tryAcquireInbound`；`rateLimitLock` 全局互斥；系统时钟回拨（NTP 校正，步进量 Δ）使淘汰谓词 `ts < cutoff` 对全部已记录时间戳恒为假。
- **触发时序**: T1：高流量部署使 `inboundWindow` 持续保有已接纳时间戳（每条被接纳消息 `add(now)`，被拒不记录）。T2：时钟回拨 Δ（如 NTP 校正 -1h）。T3：`cutoff = 回拨后 now − 60s` 低于全部已记录 ts → `removeIf` 不再淘汰；新消息继续入列至 `size == rateLimitPerMinute(3000)`。T4：此后**所有**入站消息走拒绝分支（回复「请求过于频繁」，该回复经传输线程内联发送——该内联面归 C4-005，此处只引用），直到墙钟重新越过最老时间戳，锁死时长 ≈ Δ−60s。前跳方向则反向失真：cutoff 前移使窗口瞬间清空，限流短暂失效（次要面，一并记载）。
- **现状**: 唯一限流实现；滚动窗语义正确（拒绝不入列、自然排水）但以墙钟为唯一时间基准，时钟单调性假设未声明亦未防御。
- **风险**: 长时运行网关上一次 NTP 回拨校正即可使 Feishu 通道在锁死期内对全部用户消息显式拒绝（用户可见的通道不可用），窗口内已接纳消息的时间戳基数越大、回拨量越大，锁死越长；默认上限 3000/min 使填满窗口在高活跃群聊部署中可达。
- **建议**: 时间戳改用单调钟（`System.nanoTime()` 基准差值），或淘汰谓词增加 `ts > now + RATE_LIMIT_WINDOW_MS`（未来时间戳视为异常立即淘汰）防御回拨；两种修法均不影响既有语义。
- **失效面枚举**（F3 同族清扫）:
  1. `FeishuConnector.tryAcquireInbound`——**受影响**（本条主站点，模块内唯一滚动窗实现，grep 举证无兄弟）。
  2. `ChannelCapabilities.rateLimitPerMinute`——纯声明字段（RESERVED，无生产消费者，javadoc 与 IChannelConnector.getCapabilities 裁定一致）——不涉及。
  3. `FailoverStreamFlow:154/360` 窗口——墙钟兄弟站点，独立后果，C7-002 已立案（F3 归属关系在此显式声明，不重复）。
  4. nop-integration-feishu `FeishuClient` 自身限流/重连——越界，不审。
- **信心水平**: 很可能（代码路径确定；锁死后果依赖「回拨 + 窗口填满」复合前提，实际 NTP 部署多采用渐变校正时触发概率下降）
- **误报排除**: 不是「有界声明存在的缓存」——列表界（3000）恰是缺陷放大器而非保护：达到界后淘汰停摆即全量拒绝；也不是已声明边界（owner doc 未声明时钟假设）。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：连接器投产接线即回升 P2)｜新发现。机制回码证实：tryAcquireInbound L464-475 墙钟 cutoff + removeIf（L468），回拨后谓词恒假 → 窗口填满 3000 后全量拒绝直至墙钟追平最老时间戳，前跳方向反向失真——锁死/失效两向机制成立；初审"很可能"信心标注（复合前提）如实。降级依据：触发面 = onMessage → tryAcquireInbound——连接器生命周期零接线（连接器未 start 即无 WS 回调线程、inboundWindow 恒空，startAll/stopAll 零生产调用 grep 独立复现），渠道栈休眠面条件化降档（与 C2-003/C2-004 同面处置对齐）。与 C7-002 同族（墙钟）不同站点——F3 归属声明维持，修复同批不重复计数

### [C7-004] fan-out 固定线程池 + 无界队列 + 不可强制中断 — 池耗尽后队列单调增长与入站派发永久停摆

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:216-234`（owned 池构造），`:371-445`（超时/中断路径）
- **证据片段**:
  ```java
  // ChannelMessageServiceImpl.java:226-228（newFixedThreadPool = 无界 LinkedBlockingQueue）
  owned = Executors.newFixedThreadPool(
          Math.max(2, Runtime.getRuntime().availableProcessors()), factory);
  ...
  // :403 listener.onInbound(message);   // 中断不响应的 listener 将永久占用线程
  ```
- **严重程度**: P2
- **并发场景**: 传输线程（mode-1 直接调用）或消息总线消费线程（mode-2 bridgeConsumer）将每条入站消息 × 每个 listener 提交到固定池；超时路径 `orTimeout(30s)` + `worker.interrupt()` 只能唤醒响应中断的 listener。
- **触发时序**: T1：listener A 阻塞在不可中断 I/O（普通 socket `read()`/部分 JDBC 驱动调用——JDK 中断协议对这些阻塞无效）。T2：30s 后 orTimeout 触发，`raw.cancel(true)` + `worker.interrupt()`（:427-436）均无效，线程被 A 永久占用。T3：重复 T1-T2 至 `max(2, CPU)` 个线程全部卡死。T4：此后每条入站消息的派发任务入队（无界队列）；30s 后各自触发 TimeoutException 日志 + `raw.cancel(true)`，但 **CompletableFuture.cancel 不会把已入队的 runnable 从执行器队列移除**——死任务对象（各持有 `InboundChannelMessage` + listener 引用）在队列中单调累积 → 天级 OOM；同时入站派发永久停摆，仅剩逐条 error 日志放大（每消息每 30s 一条，无抑制）。
- **现状**: I3 R-2-1 已把「挂死 listener 阻塞传输线程」升级为有界派发（设计正确），但缺省 owned 池采用无界队列，且「池健康」无任何看门狗/水位暴露——线程被无声耗尽后系统从「有界降级」退化为「无限累积 + 派发停摆」，日志是唯一信号。
- **风险**: 单个不响应中断的 listener 注册（第三方集成常态）即可在长时运行中把有界降级转化为无界内存增长与入站功能全灭；F7（无界队列）/F5（挂死对健康检测不可见）复合形态。
- **建议**: owned 池改用平台设施 `io.nop.commons.concurrent.executor.IThreadPoolExecutor` + `ThreadPoolConfig`（容量/`QueueOverflowPolicy` 溢出策略显式配置）；为超时任务改用可中断取消防（如 listener 契约要求协作中断 + 池提交前记录活跃任务数并暴露 gauge）；队列积压水位超阈值时 fail-loud。
- **失效面枚举**（F3 同族清扫）:
  1. 缺省 owned fan-out 池（:216-234）——**受影响**（本条主站点）。
  2. 注入 `fanOutExecutor` 形态（:207-209）——池归属注入方：注入无界队列池则同面复现，注入有界队列+拒绝策略池则面消失；本条按缺省 owned 池立案，注入形态在修复时随装配复核。
  3. mode-2 `sendAsync` 等待（:349-357）——orTimeout 只限时等待、不占用线程——不受影响。
  4. mode-2 `bridgeConsumer → fanOutToListeners`（:150-158）——与 1 共享同一执行机制，**F3 并入本条，不另立**。
  5. `FeishuConnector` 回复发送（客户端线程内联）——C4-005 已立案面，不重复；本条不涉及其站点。
  6. `ChannelConnectorManager`/`FeishuConnector` 模块内其余组件——无自建执行器（grep 举证），无同族站点。
- **信心水平**: 很可能（结构性累积链确定；触发前提是存在一个不响应中断且阻塞的 listener——该前提在「listener 为任意注册 bean」的开放契约下属现实负载形态而非纯理论）
- **误报排除**: 不属于「有界声明存在的池」——缺省池恰恰无界（Executors.newFixedThreadPool 语义）；也不是「原语使用本身」——30s 超时机制存在但与无界队列组合后产生新的长程失效面。
- **复核状态**: 未复核
- 复核状态: 保留(P2，条件化：fan-out 路径投产接线即回升 P1)｜新发现（同单元已立案站点重复登记——并入 C4-002 工单不独立计数）。与 C4-002 同站点同根同失效链：非协作 listener 池耗尽（interrupt 对不可中断阻塞无效，L433-436 实证）× owned 池无界 LinkedBlockingQueue（L227 newFixedThreadPool JDK 语义）→ 队列单调累积 + 派发停摆——C4-002 已按同链立案（终判 P2 条件化）；C7-004 增量仅"orTimeout 对队列中未启动任务逐个 30s 触发日志 + CF.cancel 不移除已入队 runnable"细节，不构成独立缺陷面。终判随 C4-002；触发面 = dispatchInbound/桥投递零生产调用（grep 独立复现）。台账查重：与 WI 3.1 MA-11（单线程 poller 内联消费）同族异模块，判级口径对齐不重复

---

## 维度集外线索（overlay C5/C9）

### [nop-ai-gateway-CL-014] FailoverStreamFlow 越窗后零需求无界缓冲（内存面 → C5）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:371-382`
- `deliver()` 在 `demand == 0` 时把元素追加进 `state.buffer`（:380），而上游订阅恒为 `request(Long.MAX_VALUE)`（:516）——背压被打破，慢消费者场景下每条流缓冲无界增长（上界 = 单次响应全量 chunk）。行为面归 C7 本维度已核对：per-stream、随流终止丢弃，非跨请求单调累积，故不入 C7 发现；作为内存面线索移交 C5：并发慢客户端 × 长响应 = 分配放大，且 `ArrayDeque` 扩容无水位告警。建议 C5 评估 demand==0 时对上游 `request(n)` 限流或缓冲水位上限。

### [nop-ai-gateway-CL-015] channel↔session 映射并发首聊 check-then-insert 竞态产生重复映射行（并发面 → C1/C6）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:311-318,405-407`；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelSessionStoreImpl.java:69-98`
- `findByChannel`（读）→ 引擎执行 → `saveMapping`（无条件 `newEntity + saveEntity` 插入）跨两次消息回调非原子：同一 chat 的两条并发首消息（T1 双双 miss）→ 两条新 session → 两行同 `(channelType,channelId)` 映射落库；后续 `findFirstByQuery` 任取一行，另一行成为永久影子行且无清理路径（叠加 F4 只增不删）。与 C6-004「会话孤儿化」机制不同（并发插入 vs 孤儿化），与 C7 长程面无直接交集，移交 C1/C6 维度裁定（另提示：ORM 模型若无 (channelType,channelId) 唯一约束则无库侧兜底——模型文件在源码根外，未核实）。

### [nop-ai-gateway-CL-016] 每 chunk JSON 序列化往返 + micrometer 标签分配的热路径成本（性能面 → C9）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/AiDialectBackendMessageConverter.java:104-110`（`toFrontendStreamChunk` 每 chunk `JsonTool.serialize` 后再 parse）；`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:442-446`（每 chunk 触发 `metrics.onStreamElementConverted`）→ `FailoverMetricsImpl.java:110-114`（每 chunk varargs Tags 构造 + meter 查找）
- 长流式响应（万级 chunk）下每元素一次完整 JSON 序列化/反序列化往返 + 一次 3-4 标签 micrometer counter 查找与 Tags 分配——无正确性缺陷（C7 核对非累积），纯热路径分配/序列化浪费，移交 C9 评估（候选修法：counter 预解析缓存 per-attempt、chunk 直传结构体免去 serialize 往返）。

---

## 维度复核结论（WI 3.3 独立复核，合并批 C6+C7）

- **复核日期**: 2026-10-20
- **复核人**: C6+C7 维度独立复核员（与初审/深挖/C1+C2/C3+C4 复核员相互独立）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准口径；C7-001…005 全部 5 条证据行逐条回码、时序论证逐步核验、三态判定 + 现状标注；降档口径核对（泄漏/残留/长时运行形态不适用启动期降档；渠道栈休眠面条件化降档——grep 证据独立重跑，逐条确认触发面依赖渠道栈接线）；与 `ai-dev/audits/cra-index.md` WI 1.1–3.2 台账逐条查重。

### 台账查重结论（C7 维度）

C7 维度 5 条与 cra-index WI 1.1–3.2 台账零同站点重复立项。跨单元同族（非重复，批次对齐）：C7-001↔WI 1.7 C6-001 / WI 3.2 MA-01（future 永不完成族，与 C3-004 复核节登记的对齐关系在此确认）；C7-002↔WI 2.3 C8-005（墙钟语义族）；C7-004↔WI 3.1 MA-11（内联消费族判级对齐）；C7-005↔WI 3.1 LlmCallCoordinator（引擎侧 allowCall 消费正确——反证面，非同案）。**C2-001 长程后果指针核对**：初审边界声明（作为 C2-001 的 F3 长程后果组成部分不另立案）维持，本复核确认 ConcurrencyRegistry 无对账/TTL/管理口的事实表述准确。

### 逐条判定表（C7-001…005）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C7-001 | P2 | 保留 | P2（并入 C3-004 工单） | 与 C3-004 面①②同站点同机制（行号重合、future 永不完成链同一）；增量仅触发器侧重；live 面 P2 |
| C7-002 | P2 | 保留 | P2 | 墙钟三站点回码证实 + 模块内 nanoTime 对照组实证；live 流式主链；启动期降档不适用 |
| C7-003 | P2 | 降级 | P3（条件化：投产接线即回升 P2） | 锁死/失效两向机制回码证实；触发面随连接器生命周期休眠（未 start 即无入站流） |
| C7-004 | P2 | 保留 | P2（条件化：投产接线即回升 P1；并入 C4-002 工单） | 与 C4-002 同站点同根同失效链；增量仅日志放大/队列驻留细节；终判随 C4-002 |
| C7-005 | P2 | 保留 | P2 | 四子项复核成立（惰性迁移/快照门控/allowCall 消费点唯一性/F3 兄弟）；live 面；非已声明边界 |

### 判定统计（C7 维度）

- **保留×4**：C7-002（P2 独立）、C7-005（P2 独立）、C7-001（P2 并入 C3-004）、C7-004（P2 条件化并入 C4-002）
- **降级×1**：C7-003（P2→P3 条件化，连接器生命周期面）
- **驳回×0**（5 条机制全部回码成立）
- 终判严重度分布：P2×4（独立×2 + 并入×2）/ P3×1（条件化）

### 现状标注分布（C7 维度）

- 新发现：全部 5 条（cra-index 零同站点）
- live 面：C7-001（随 C3-004）、C7-002、C7-005
- 渠道栈休眠（连接器生命周期零接线）：C7-003
- 渠道栈休眠（fan-out 面零生产接线）：C7-004（+ 并入 C4-002 工单）

### 同案命中清单（C7 维度）

1. **C7-001 = C3-004 面①②**（adapter/interceptor whenComplete 无护栏 → future 永不完成）——并入单工单，C7-001 的 release 下溢触发器视角与 F5 不可检测表述作为修复验收要点并入。
2. **C7-004 = C4-002**（fan-out 无界队列 × 非协作 listener 池耗尽 → 队列单调增长 + 派发停摆）——并入单工单。
3. **C7-002 ↔ C7-003** 同族（墙钟）不同站点不同后果——分别保留/降级，修复同批。
4. 初审边界声明核对：C3-001（终态冲刷）/C4-004/005/C6-004/C2-001 长程后果的边界划分经复核全部成立，无越界重复。

### 高风险条目子项复核说明

- **C7-005（P2）**：四子项复核见 C7-round2.md 状态行——原语语义（OPEN→HALF_OPEN 惰性迁移仅在 allowCall，ThresholdBreaker L166-218 无后台 timer）/门控快照（DefaultSelectionStrategy L36-48 只读 isAvailable；CandidateHealthProvider.healthOf L48-54 读 getState）/allowCall 消费点唯一性（模块内仅两处 probeBrokenCandidates；nop-ai-agent LlmCallCoordinator L240/890/912 正确消费 allowCall 构成反证面——gateway 选择面消费失真为模块特定缺陷）/F3 兄弟（RuleBasedSelectionStrategy.buildInputs L201-211 同读快照）全部回码证实。后果①（冷却期满候选常态不可达恢复 → 容量单调流失 + "越缩越易触发恢复"病态耦合）与后果②（HALF_OPEN 视同完全可用 → 单探针契约在真实调用面不执行）均结构成立；立案面锁定为本模块编排消费方式而非 ThresholdBreaker 原语（初审定性准确），live 面 P2 维持。
- **C7-002（P2，独立保留）**：初审"时钟步进属常态事件"为环境假设（非代码事实）——按"可具体化触发链 + live 面"口径判级仍成立；模块内墙钟/单调钟口径不一致（窗口+指标 4 处墙钟 vs adapter/interceptor 5 处 nanoTime）为 grep 可证的代码事实，P2 维持。
- **C7-001（P2，并入）**：与 C3-004 重合度逐行核对——两文件四处行号引证重合、机制同一（whenComplete 从属 stage 吞异常）、OBS-02 注释实证一致；判级一致（双方 P2）无冲突，合并不损失信息。

## 最终保留项（C7 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 终态回调无护栏 → future 永不完成 | C7-001 | P2 | 回调体 Throwable 护栏 + completeExceptionally 兜底 | 并入 C3-004 工单（FailoverStreamFlow catch 放宽同 diff）；与 WI 1.7 C6-001/WI 3.2 MA-01 族批次对齐 |
| 流式窗口/时长指标墙钟 | C7-002 | P2 | 窗口判定与时长统一改 nanoTime | 独立；与 C7-003 同批复频修；与 WI 2.3 C8-005 墙钟族对齐 |
| 入站限流窗墙钟 | C7-003 | P3 条件化 | 时间戳改单调钟或加未来时间戳防御 | 独立（回升 P2）；随 C7-002 同批 |
| fan-out 无界队列 × 非协作挂死 | C7-004 | P2 条件化 | 平台 IThreadPoolExecutor + QueueOverflowPolicy + listener 连续超时熔断 | 并入 C4-002 工单（回升 P1）；orTimeout 站点随 WI 2.3 CL-022 移交面对齐 |
| 选择面绕过 allowCall | C7-005 | P2 | 门控补 allowCall 冷却复查；HALF_OPEN 探活期并发语义显式化 | 独立；与 3.1 引擎侧 allowCall 消费模式对齐 |

---

*本文件为 C7 维度盲审初审产物；复核状态已由 WI 3.3 独立复核（合并批 C6+C7）更新，维度判定统计见「维度复核结论」。*
