# C8 背压、流控与过载行为 — nop-ai-gateway（WI 3.3）

- **审计员**: C8 维度盲审初审员（单批次，Phase 2 收官维）
- **日期**: 2026-10-20
- **Status**: 初审完成（已复核——WI 3.3 独立复核，见文末「维度复核结论」）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118（F1–F11 + 共享前缀）、L289–307（C8 正文）、L351–377（附录 A 13 字段）

## 审计范围与口径

### 覆盖声明（单批次全源码根）

- 审计根：`nop-ai/nop-ai-gateway/src/main/java`（36 文件 / 6,035 行）。**全部 36 文件逐一审读完成**（FailoverStreamFlow 545、AiGatewayFailoverInterceptor 660、ChatServiceFailoverAdapter 530、FeishuConnector 781、ChannelMessageServiceImpl 520 等大文件全文精读；14 个 DTO/常量/接口小文件全文通读）。
- 基线对照：`_tmp/cra-baseline-nop-ai-gateway.txt` C8a（queues and buffers：FailoverStreamFlow ArrayDeque 两处）与 C8b（batch size / unbounded collect：FeishuConnector 限流窗口 + RATE_LIMITED 分类）已逐点人工复现并纳入下文口径；数量结论未采信脚本输出。

### 生产-消费边界清单（C8 正文执行步骤 1 盘点结果）

| # | 边界 | 有界性 | 队列满/过载行为 | 裁定 |
|---|------|--------|----------------|------|
| B1 | LLM 上游 attempt 流 → FailoverStreamFlow → 输出订阅者 | 越窗前有界（N=10/T=1000ms）；**越窗后无界** | 无策略（无阻塞/丢弃/拒绝/降级） | **C8-001 立案** |
| B2 | transport/bridge → fan-out executor（fixed 2..CPU）→ listeners | 队列无界（`Executors.newFixedThreadPool`）；per-task timeout 锚定在提交时刻 | 超时=丢弃+带中断执行，无拒绝/无积压指标 | **C8-002 立案**（有界性内存面归 C7-004） |
| B3 | 引擎完成回调 → Feishu 出站同步发送（按 4000 字符分段，串行） | 段数无上限 | 无超时包装、无出站限流、无隔离 | **C8-003 立案（P2）** |
| B4 | Feishu 入站 → `tryAcquireInbound` 滚动窗准入 → engine.execute | 有界（3000/min 滚动窗），拒绝显式回复（非静默） | 已声明语义，与实现一致 | 无缺陷 |
| B5 | per-账号并发上限（ConcurrencyRegistry + acquire 后复查 + 重选） | 饱和跳过递归受 attempted 集合约束（池大小有限 → 递归有界，无栈溢出）；复查谓词 W6/W7 两形态一致（acquire 后 `count > limit`） | 全池饱和 fail-loud（ERR_AI_MODEL_CLASS_SATURATED + onSaturation 指标） | 无缺陷；网关流式"预检语义不承诺硬上限"（B-13）为 javadoc 明文声明边界 → 不立案 |
| B6 | mode-2 backbone `sendAsync.orTimeout` | 平台 DBMessageService persist-before-return，at-least-once + 幂等消费为 javadoc 明文声明 | 超时只停等待，语义已声明 | 无缺陷 |

### 心跳/超时调度器缺席裁定（不越界立案留痕）

本模块根内**不存在**任何流级心跳/stall 调度器：FailoverStreamFlow 越窗后上游挂起（不产出也不报错）时下游无 deadline 可依赖（仅 caller token 取消可退出）；fan-out 与 mode-2 有 per-task 超时但不覆盖"上游整体停滞"。该缺席属 F5（挂死生产者对健康检测不可见）/C7 挂死可检测性失效面，**按派单纪律不在 C8 立案**，仅在此留痕供 C7 复核交叉引用。

### Phase 2 收官与 C1–C7 边界声明

C8 为 Phase 2 最后批次。与已立案条目的边界逐一声明如下，避免同失效面重复：

- **C1-001（FailoverStreamFlow 窗口清理面）**：C8-001 审的是越窗后缓冲的**有界降级缺失**（过载/内存行为面），不触及窗口期缓冲元素的清理时序。
- **C3-001（终态冲刷 demand 约束丢数据）**：`flushAll`/窗口外失败路径中"受 demand 约束的剩余缓冲元素在 onError/onComplete 前被静默截断"（`FailoverStreamFlow.java:427-434, 465-483`）属 **C3-001 失效面，本维度不重复立案**。C8-001 的失效面是零需求期间缓冲**增长无界**，两者是同一缓冲区（`AttemptState.buffer`）的写入侧与终态读取侧，方向相反。
- **C7-004（fan-out 无界队列，长程内存面）**：C8-002 与其**同站点、不同失效面**——C7-004 定性"队列只增不删的内存行为"，C8-002 定性"过载时超时时钟锚定错误 + 取消后仍执行 + 丢弃误报为超时"的**行为语义面**。符合"同站点不同失效面可立案"边界。
- **C6（传输线程内联）**：FeishuConnector.onMessage 在传输回调线程内联 `engine.execute` 属 C6 面，不重复；C8-003 的执行点是**引擎完成回调线程**上的出站同步发送（入站→出站的慢下游传播），非同一点。
- **C7 墙钟**：`windowStart`/`CoreMetrics.currentTimeMillis` 的时钟回拨敏感度归 C7，不在本维度重复。
- **并发计数面（C-filed）**：`GatewayStreamingLifecycleListener` acquire/release 配对、double-booking 竞态（B-13 已声明为预检语义）不在此重复；本维度仅核了饱和复查谓词的一致性（B5，无缺陷）。

### 降档口径声明

按派单纪律，过载/无界面属**行为性缺陷，单线程不豁免**：C8-001 的零需求缓冲增长在单订阅者单线程下同样成立（Reactive Streams 背压契约面，非数据竞争）；C8-002 的超时锚定缺陷是纯时序语义（提交时刻 vs 执行时刻），单线程积压下即可触发。

---

## 发现

### [C8-001] FailoverStreamFlow 越窗后零需求缓冲无上界 + 上游 request(MAX) 恒定——下游慢时整流积压，无有界降级语义

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/FailoverStreamFlow.java:371-405`（deliver/flush）、`:353-369`（bufferOrForward）、`:516`（request(MAX)）
- **证据片段**:
  ```java
  // L516：窗口期保持 demand（request(MAX)），避免缓冲积压导致 submit/close 阻塞（spike）。
  subscription.request(Long.MAX_VALUE);          // 无条件、全流生命周期恒定

  // L371-382：越窗后（passed=true）零需求路径
  private void deliver(AttemptState state, ChatStreamChunk item) {
      if (cancelled.get()) { return; }
      if (demand > 0) { demand--; state.forwarded = true; subscriber.onNext(item); }
      else { state.buffer.add(item); }           // ← 无任何容量检查
  }
  // L359：容量检查只存在于越窗前分支
  if (state.buffer.size() < adapter.getBufferSize()
          && CoreMetrics.currentTimeMillis() - state.windowStart < adapter.getBufferTimeMs()) {
  ```
- **严重程度**: P1
- **并发场景**: 单订阅者 Flow 链——上游 LLM HTTP 流的发射线程（经 `AttemptSubscriber.onNext`）与下游输出订阅者（本地形态下为 SSE/GraphQL 桥接的授信线程）之间；`StreamSubscription.demand`（monitor 保护）为唯一背压介质。无需多线程竞争：这是请求/demand 背压契约的结构性违约。
- **触发时序**: T1 = 下游订阅者按写进度授信（SSE 桥），客户端 TCP 零窗口/停滞 → `request()` 停止，`demand` 递减至 0（L193）。T2 = 上游 attempt 流因 L516 已获 `request(MAX)`，不受下游 demand 任何约束，持续 `onNext` → `bufferOrForward`（L353）→ `state.passed` 已为 true（窗口 N=10/T=1s 先到者越窗）→ `deliver` → L380 `state.buffer.add(item)` 无界入队。T3 = 长补全（数万 token）期间每个到达 chunk 永久积压于 `AttemptState.buffer`，直至补全结束或取消；交叠后果 = 每条慢流占有一份完整响应体大小的堆，N 条并发慢客户端线性放大 → OOM。全程无溢出策略、无积压深度指标、无降级信号——与维度目标"上游快于下游时有界降级"直接相悖。
- **现状**: 背压契约只有"半套"：发射侧严格按 demand 递减（无超发），但缓冲侧越窗后无界（无越界积压上限），且上游 demand 恒为 MAX（永不节流上游）。`bufferSize`（默认 10）仅约束窗口期。
- **风险**: 网关主场景（LLM 补全流 + 浏览器/客户端消费）中客户端停滞是常态故障而非边角；负载上升时慢客户端把内存压力从"一条流一个缓冲"放大为并发流 × 完整响应体。对照 F7（OOM 同族：无界缓冲/队列）。另成本面：request(MAX) 意味着即使下游完全停止，上游 token 仍全额计费消耗（缓冲后可能随取消丢弃）。
- **建议**: 越窗后缓冲复用 `adapter.getBufferSize()` 作为硬上限（与窗口期同界）；溢出时走显式策略——优先对上游改为按下游 demand 逐额授信（`request(demand)` 替代 `request(MAX)`；若上游为阻塞 submit 语义不能反向节流，则溢出即 `deliverError`（overload onError）+ `buffer.overflow.total` 指标），可复用平台 `HighWatermarkSemaphore`（`io.nop.commons.concurrent.semaphore`）实现水位门。禁止无界 `ArrayDeque.add`。
- **失效面枚举**（`AttemptState.buffer` 全部写入点 + 上游授信点，F3 清扫）:
  1. `bufferOrForward` L361 越窗前入队——有界（bufferSize + 时间窗）——无缺陷；
  2. `deliver` L380 越窗后入队——**无界——本条缺陷主体**；
  3. `flushBuffer` L389-393 / `flushAll` L400-404——只读消费侧（demand 门控），写入侧修复即可闭合；
  4. `handleStreamFailure` L438 窗口内失败 `buffer.clear()`——丢弃为 Major-1 已声明语义——非缺陷；
  5. `deliverError` L492 `buffer.clear()`——终态清理——无缺陷；
  6. 上游授信 `onSubscribe` L516 `request(MAX)`——**恒定 MAX——本条缺陷的传播使能者**，与写入侧同票修复；
  7. 同族兄弟：网关形态缓冲层（nop-gateway StreamingProcessor）在模块根外，不立案；仅提示两形态可能同构。
- **信心水平**: 确定
- **误报排除**: 不属"有界声明存在的队列"误报——越窗分支无任何上限配置；不属"原语使用非问题"——ArrayDeque 本身无罪，缺陷是容量检查只写在两个分支之一；不是 C3-001 重复——那条是终态冲刷丢数据（读取侧截断），本条是增长无界（写入侧积压）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。行号全部回码命中（bufferOrForward L353-369 / deliver L371-382 / request(MAX) L516），三子项逐一证实：①`subscription.request(Long.MAX_VALUE)` 每 attempt 经 onSubscribe 无条件发放（L507-516），此后全流无任何节流回授——javadoc"窗口期保持 demand"与实际"全生命周期恒定 MAX"不符，初审定性准确；②越窗后 `deliver` L380 `state.buffer.add(item)` 无任何容量检查；③容量检查仅存在于越窗前分支（L359-360，bufferSize+时间窗双门）。行为性背压契约缺陷，单线程不豁免（降档口径核对通过）。live 面维持 P1：FailoverStreamFlow 为 LOCAL 形态 opt-in 流式主链（ai-gateway-defaults.beans.xml L221-222 定义 nopChatServiceFailoverAdapter 证实；C1/C3 复核"请求触达面不适用启动期降档"先例沿用），判级与本单元 C1-001/C3-001（同文件 P1 保留）、跨单元孪生 WI 2.3 C3-003（BufferedStreamingPublisher 背压断裂 P1 open）、WI 3.1 MA-01 成员 C5-001（StreamAggregator 无界 P1 族）四方对齐。单元内 CL-001/005/012/014 为同站点同失效面 F3 重复登记 → 并入本条单工单（C8-001 主登记，四处 CL 不独立计数）

### [C8-002] fan-out 派发超时时钟锚定在提交时刻 + 取消后仍带中断标志执行 listener——队列积压下消息被"超时"名义静默丢弃/半执行，半个成功无恢复语义

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:371-445`（fanOutToListeners）、`:227-228`（fixed pool）
- **证据片段**:
  ```java
  // L393-403：runAsync 任务体——取消检查后仍然执行 listener
  CompletableFuture<Void> raw = CompletableFuture.runAsync(() -> {
      Thread worker = Thread.currentThread();
      workerRef.set(worker);
      try {
          if (cancelled.get()) {
              worker.interrupt();          // ← 自我中断后【不 return】
          }
          listener.onInbound(message);     // ← 被取消的任务依然执行，且线程已带中断标志
      } finally { ... }
  // L418：orTimeout 在提交时刻起算，而非执行开始时刻
  CompletableFuture<Void> timed = raw.orTimeout(dispatchTimeoutMs, TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P1
- **并发场景**: inbound 消息生产者（Feishu transport 回调线程 / mode-2 bridge 消费线程，L329-358 两个入口同汇 `fanOutToListeners`）与 fan-out fixed 池（`Executors.newFixedThreadPool(Math.max(2, CPU))`，L227，**无界 LinkedBlockingQueue**）的 worker 线程之间；超时由 `orTimeout` 的内部调度线程触发。
- **触发时序**: T1 = 若干 listener 挂死/慢（> dispatchTimeoutMs=30s），池线程（仅 2..CPU 个）全部占住。T2 = 新入站消息 `dispatchInbound` → `runAsync` 入队（无界队列，无拒绝策略、无积压指标）。T3 = 提交后 30s `orTimeout` 触发——**任务可能尚未出队执行过一次**——`cancelled.set(true)`，`workerRef.get()==null`（未启动）无需中断。T4 = 某 worker 空闲、任务出队：L398 `cancelled.get()==true` → 自我中断 → **仍执行 `listener.onInbound`**：协作式 listener 首个阻塞调用抛 InterruptedException（半执行、副作用已发生一部分）；非阻塞 listener 完整跑完。T5 = finally 中 `Thread.interrupted()` 消费中断标志 → L413-416 抛 CancellationException → L437 日志"**timed out — task cancelled**"。交叠后果：(a) 该消息从未获得完整投递却无法从观测面区分"队列丢弃"与"执行超时"（同一日志、无 rejected/dropped 计数器）；(b) 带中断标志执行 = F2 半个成功（listener 部分副作用 + 对外报告取消），无恢复语义；(c) 只要积压等待 > 30s，消息以恒定速率静默丢失，且无界队列使该状态无背压信号。
- **现状**: per-task 超时机制（I3 R-2-1）正确解决了"挂死 listener 占住线程"的 C4/C6 面，但超时锚点错置在提交时刻，且取消路径对"任务从未执行"与"执行到一半"两种状态使用同一处理（仍执行 + 报取消），队列满行为语义（阻塞/丢弃/拒绝）整体缺席——无界队列使"拒绝"永不发生，实际语义退化为"延迟超阈值即丢"。
- **风险**: mode-1（默认直连形态）单 JVM 部署下，一次依赖抖动（listener 背后的 DB/HTTP 慢 30s+）即可把所有后续入站消息转为丢弃/半执行，业务侧（agent 触发）无感知丢失；"负载上升必然触发的队列溢出/消息丢失"（P1 校准）。
- **建议**: (1) 超时锚点移至执行开始——在 runnable 内 `workerRef.set` 之后启动 per-task deadline（复用调度器 schedule 或 `FutureHelper` 超时原语），而非提交时 `orTimeout`；(2) `cancelled.get()` 命中且任务未开始时**跳过 `listener.onInbound`**，记 `fanout.dropped-in-queue` 指标（与执行超时分离计数）；(3) 队列有界化 + 平台 `QueueOverflowPolicy`（`io.nop.commons.concurrent`）显式选择 caller-runs/drop-with-metric（有界性本体归 C7-004，此处只要求拒绝语义存在）；(4) 部分执行路径（已执行但被中断）补 `onInboundFailed(message, partial)` 类恢复钩子或至少区分日志码。
- **失效面枚举**（该服务两个派发入口 + 同族超时点，F3 清扫）:
  1. `dispatchInbound` mode-1 直连 L334 → `fanOutToListeners`——缺陷主体；
  2. `bridgeConsumer.onMessage` L152-157（mode-2）→ 同一 `fanOutToListeners`——同缺陷（同方法共享，修复点唯一）；
  3. mode-2 `sendAsync.orTimeout(dispatchTimeoutMs)` L349-351——超时同样锚定提交时刻，但 persist-before-return + at-least-once + 幂等消费为 javadoc 明文声明契约——**已声明边界，不立案**；
  4. 同仓先例对照：`fanOutToListeners` 注释自引的 SingleTurnExecutor / AgentToolDispatcher / MemberFanOutDispatcher 中断取消模式（在 nop-ai-agent 等模块，根外）——建议同轮复核其超时锚点是否同病（F3 跨模块线索）。
- **信心水平**: 确定
- **误报排除**: 不按"压测/测试专用简化并发"豁免——这是 mode-1 默认生产路径；不属"有界声明存在"误报——队列无界且超时语义已实质偏离"挂死 listener 不阻塞传输线程"的声明目的（阻塞转成了静默丢弃）；不是 C7-004 重复（见边界声明：内存面 vs 行为语义面）。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：fan-out 路径投产接线即回升 P1)｜新发现（cra-index WI 1.1–3.2 台账零同站点；WI 2.3 CL-022 为本站点 orTimeout 的 JDK Delayer 共享调度器移交核对面，非缺陷重复登记）。机制逐子项回码证实、零修正：①orTimeout 锚定提交时刻（L418——JDK 语义：timeout 于 orTimeout 调用时调度，队列积压 >30s 的任务未出队即被判超时）；②取消后任务仍执行 listener（L398-403 自我中断后不 return；L399-400 代码注释自述"do not let the listener run unguarded"与实现直接矛盾——非阻塞 listener 完整执行副作用后仍经 L410-416 报 CancellationException）；③"队列丢弃/半执行/完整执行"三态同报单条日志 L437"timed out — task cancelled"，无分离计数，观测面混淆成立；④无界 LinkedBlockingQueue 无拒绝语义无积压指标（L227 + JDK fixed 池语义）。降档依据：触发面 = fan-out 休眠面（`dispatchInbound`/`subscribeInbound` 全仓非测试零生产调用——C1/C4 复核 grep 证据沿用，sendToUser 唯一 live 消费面不经池），按同站点先例条件化降档（C4-002 P1→P2、C7-004 P2 条件化并入同工单）。失效面独立于 C4-002（池治理面）/C7-004（内存面）成立——本条修复指令不同（deadline 移至执行开始 + 未启动跳过执行 + dropped/timeout 分离计数），不随 C7-004 吸收，与 C4-002 工单同站点同批

### [C8-003] Feishu 出站回复路径同步无界发送：引擎回调线程串行分段阻塞，慢下游无隔离、无超时包装、无出站限流

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:439-452`（deliverSegmented）、`:366/376-416`（whenComplete → onExecutionComplete → sendReply）；`channel/ChannelMessageServiceImpl.java:302`（sendToUser 同步直通）
- **证据片段**:
  ```java
  // FeishuConnector L439-451：同步分段串行发送，异常仅记日志继续下一段
  for (String seg : segments) {
      try {
          feishuClient.sendMessage(RECEIVE_ID_TYPE_CHAT, chatId, MSG_TYPE_TEXT, toJsonText(seg));
      } catch (Exception e) {
          LOG.error("feishu-connector failed to send a segment to chat {}", chatId, e);
      }
  }
  // FeishuConnector L366：回调点 = 引擎完成线程
  future.whenComplete((result, error) -> onExecutionComplete(result, error, replyTarget, ...));
  ```
- **严重程度**: P2
- **并发场景**: IAgentEngine 完成回调线程（未来完成点，根外）执行 `whenComplete` → 同步 HTTP（`FeishuClient.sendMessage` → `httpApi.sendMessage`，无网关层超时包装/退避/限流）；`sendToUser`（L302）则是业务调用线程同步阻塞至发送完成。入站侧已有 3000/min 滚动窗准入（B4），出站侧零流控。
- **触发时序**: T1 = Feishu Open API 延迟飙升或挂起（`sendMessage` 同步 HTTP 无网关层 deadline）。T2 = 高准入率下（限流上限 3000/min）多条消息的引擎执行相继完成 → 各完成回调线程进入 `deliverSegmented` 阻塞在 `sendMessage`。T3 = 引擎回调线程被出站发送占住 → 新的 `execute` 完成回调排队 → 跨 chat 队头阻塞，入站准入（B4）继续放行新负载 → in-flight 持续增长。限流拒绝路径（L301-304）在**入站传输回调线程**上同步发"请求过于频繁"回复，同一无保护发送点，过载时反而放大传输线程占用。交叠后果 = 慢下游（Feishu）经无隔离的同步回复路径反向传导为引擎/传输线程池耗尽。注：`httpApi` 客户端内部超时配置在 nop-integration-feishu（审计根外），最终是否无限阻塞无法在根内证实——故按纪律降档 P2 并标注部分未具体化。
- **现状**: 同一模块对 fan-out 路径已按 I3 R-2-1 建立"dedicated executor + deadline + 中断取消"的完整防护，而对引擎回复路径与 `sendToUser` 直通路径零防护；出站方向无 `IRateLimiter`（平台 `io.nop.commons.concurrent.ratelimit` 未用）。
- **风险**: 慢消费者（Feishu API）触发的跨 chat 全局降级；分段放大（见 CL-017）使单条大回复占住回调线程 `ceil(S/4000)` 倍时长；对比已防护的 fan-out 面，属防护不对称。
- **建议**: 回复路径复用 I3 R-2-1 同款模式：dedicated executor + per-send deadline + 超时取消（可直接抽取 `fanOutToListeners` 的 guard 模板）；出站接入 `IRateLimiter`；`sendToUser` 文档明示同步阻塞语义或改异步 + `SendResult` 补充 QUEUED/TIMEOUT 值。
- **失效面枚举**（出站同步发送全部调用点，F3 清扫）:
  1. `onExecutionComplete` L388/L392/L397/L402 → `sendReply` → `deliverSegmented`——缺陷主体（引擎回调线程）；
  2. `onMessage` 同步异常路径 L355/L359/L363 → `sendReply`——调用线程 = execute 同步返回时的调用线程（Feishu transport 回调线程），与 C6 传输线程内联面相邻，此点归 C6、发送无界归本条；
  3. 限流拒绝回复 L303 `deliverSegmented`——入站传输回调线程，同缺陷且过载期自放大；
  4. `ChannelMessageServiceImpl.sendToUser` L302 → `connector.sendOutbound` → `deliverSegmented`——业务调用线程同步阻塞，同缺陷；
  5. `start()` 内无发送；stop 路径无发送——无兄弟遗漏。
- **信心水平**: 很可能（阻塞本身确定；升级为线程池耗尽的链路依赖根外 FeishuClient/httpApi 超时配置与引擎池规模，未能根内闭合）
- **误报排除**: 不属"低频路径粗粒度同步"豁免——这是每条入站消息的必经回复路径；不是 C6 重复（执行点为完成回调线程，非传输线程内联；边界见上文）；发送失败"记日志继续下一段"本身是已声明的 no-silent-drop 语义，缺陷面在无超时/无流控，不在错误处理。
- **复核状态**: 未复核
- 复核状态: 降级(P3，条件化：连接器投产接线即回升 P2)｜新发现（cra-index WI 1.1–3.2 台账零同站点）。机制回码证实、零修正：deliverSegmented 同步串行分段发送、无超时包装/无出站限流/无隔离、失败记日志续发（L439-452）；whenComplete 回调点 L366；限流拒绝回复同站点 L303；sendToUser 直通 L302。降档依据：F3 四面全部位于渠道栈 start 门后——①引擎完成回调面与②onMessage 同步异常面均在 onMessage 内、首行 `!started` 早退（L268-271）；③限流拒绝回复在 onMessage 内同门；④sendToUser 面落 `sendOutbound`，其 `!started` 直接抛 NopFeishuException（L221-224）——`startAll` 全仓非测试零生产调用（C1/C4 复核 grep 证据沿用）下 started 恒 false，出站分段发送生产零触达（"无可阻塞之物"）；按休眠面 P2→P3 先例（C4-003/C4-006/C7-003）条件化降档。FeishuClient.sendMessage L238-243 → httpApi.sendMessage（根外超时配置不可证）维持初审"很可能"诚实标注；CL-017（段数无上限）与本条同站点，随同批联动

---

## 维度集外线索（overlay C5/C9）

### [CL-017]（C5：内存有界性/批边界）deliverSegmented 分段数无上限——单条超大回复 = ceil(S/4000) 次串行同步 API 调用

`FeishuConnector.splitIntoSegments`（`:477-500`）保证不丢字符，但输入 `text` 长度无上限校验、段数无上限：S=1MB 回复 → 250 次串行同步 `sendMessage`（叠加 C8-003 的阻塞面）。批处理边界对照 C8 执行步骤 3"单批超大输入绕过批上限"成立，降档为线索：S 受引擎输出规模约束（根外），且 MAX_MESSAGE_LENGTH 是 Feishu API 侧约束而非本模块防护。建议：段数上限 + 超限截断/降级为文件链接，显式声明。

### [CL-018]（C9：热路径分配）ConcurrencyRegistry 每次 acquire/release 分配 Key + int[1] 捕获

`ConcurrencyRegistry.acquire/release`（`:55-102`）每次调用构造 `Key` 对象、`int[1]` holder、捕获 lambda（`counts.compute`）；网关形态每请求 ≥2 次（acquire + release，重试翻倍），非流式重试路径每次 attempt 各一对。分配量级小（无逃逸优化可消部分），但属可量化热路径浪费候选；Key 不可变、equals/hashCode 固定，具备缓存/复用条件。C9 复核时与 CHM compute 的临界区开销一并测量。

### [CL-019]（C9：热路径指标开销）onStreamElementConverted 每 chunk 一次 micrometer 自增 + 标签字符串构造

`FailoverMetricsImpl.onStreamElementConverted`（`:110-114`）在流式**每个 chunk**（token 级频率）执行：`maskAccount` 字符串拼接（`:131-137`）+ `registry.counter(...)` 查找 + increment；`AiGatewayFailoverInterceptor.onStreamElement`（`:442-446`）每 chunk 还调用 `metrics != null` 分支。label 基数有界（provider×model×masked-account），无基数爆炸，但 token 级频率下的字符串构造 + meter 查找是 C9 可量化热点候选。建议：per-request 缓存 meter 引用或采样计数。

### [CL-020]（C9：锁竞争）Feishu 入站限流全局锁 + O(n) removeIf——全 chat 共享单一 rateLimitLock

`FeishuConnector.tryAcquireInbound`（`:464-475`）：所有 chat 共享 `rateLimitLock` + `inboundWindow` ArrayList，每条入站消息 O(n) 全窗扫描（n ≤ 3000），高入站速率下是跨 chat 串行化点（估算 50 msg/s × O(3000) 比较尚轻，但锁是全局唯一竞争点）。C9 复核候选：换分桶/环形缓冲，或至少确认实际入站包络远低于 3000/min。

### [CL-021]（相邻 C3 线索，超出 C5/C9 overlay 范围、显式标注）Feishu onMessage 无按 chat 串行化——同一会话可并发 execute

`FeishuConnector.onMessage`（`:267-367`）准入后直接 `agentEngine.execute(request)`，无 per-chat 队列/串行化：同一 chatId 快速连发两条消息 → 两个并发执行共享同一 session → 回复乱序/交错（是否引擎内部已按 session 串行在 nop-ai-agent 根，无法根内证实）。属异步链路正确性/语义面而非 C8 过载行为面，留作 C3 复核线索；若引擎确无串行化，还叠加 session 状态竞争。

---

## 维度复核结论（WI 3.3 独立复核）

- **复核日期**: 2026-10-20
- **复核人**: C8 维度独立复核员（与初审/深挖/C1+C2/C3+C4/C6+C7 复核员相互独立）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` 校准口径；两轮 4 条发现证据行逐条回码、时序论证逐步核验、三态判定（保留/降级/驳回）+ 现状标注；降档口径核对（过载/无界面属行为性缺陷——单线程不豁免；渠道栈休眠面按 WI 3.2 零生产接线先例条件化降档——C1/C4/C7 复核已确立的 grep 证据独立核对采信：`dispatchInbound`/`subscribeInbound`/`startAll`/`stopAll` 全仓非测试零调用、`sendToUser` 唯一 live 消费面不经 fan-out 池）；与 `ai-dev/audits/cra-index.md` WI 1.1–3.2 台账（P1 汇总 + CL 台账 + 2.3 节网关登记面）逐条查重；第 2 轮深挖三定向面（饱和谓词/分段流控/429 传播）抽验。

### 台账查重结论

C8 维度 4 条与 cra-index WI 1.1–3.2 台账**零同站点重复立项**。两处需区分的邻接面：①WI 2.3 C3-003（P1 open，BufferedStreamingPublisher 背压断裂）与 C8-001 为**跨单元孪生实现**（nop-gateway vs nop-ai-gateway，同类越窗零需求无界缓冲）——同族非同站点，判级 P1 对齐，修复同批联动；②WI 2.3 C8-002（Gateway 429 重试放大 ×7，P1）与 C8-004（429 Retry-After 信号链断裂，P3）为不同模块不同失效面——彼为网关重试准入上界缺失（有 OOM 轨迹），本条为编排层信号消费缺口（无资源面），非同案。

### 逐条判定表（C8-001…004）

| ID | 初判 | 判定 | 终判 | 核心依据 |
|---|---|---|---|---|
| C8-001 | P1 | 保留 | P1 | 三子项回码全证实（request(MAX) 恒定 L516 / 越窗 add 无容量检查 L380 / 容量门仅越窗前分支 L359-360）；行为性背压缺陷单线程不豁免；live 面（LOCAL opt-in 流式主链，beans.xml L221-222），与本单元 C1-001/C3-001 同文件 P1、WI 2.3 C3-003 孪生 P1、WI 3.1 MA-01 C5-001 族 P1 四方判级对齐 |
| C8-002 | P1 | 降级 | P2（条件化：投产接线即回升 P1） | 五子项机制回码全证实零修正（锚定提交时刻/取消后仍执行/三态单日志/无界队列/无指标）；L399-400 注释与实现矛盾佐证；触发面 = fan-out 休眠面（dispatchInbound 零生产接线）；同站点先例 C4-002 P1→P2、C7-004 P2 条件化，判级对齐；失效面独立成立不随 C7-004 吸收 |
| C8-003 | P2 | 降级 | P3（条件化：投产接线即回升 P2） | 机制回码证实（deliverSegmented 同步串行无超时/无限流 L439-452）；F3 四面全部在 start 门后（onMessage L268-271 早退 / sendOutbound L221-224 抛 !started）——startAll 零接线下出站分段发送生产零触达；休眠面 P2→P3 先例（C4-003/C4-006/C7-003）对齐；根外 httpApi 超时未证实维持"很可能"标注 |
| C8-004 | P3 | 保留 | P3 | grep 全根零引用证实（retryAfterMs/Retry-After 0 命中）；headers 恢复后仅透传分类（L508/510-511/517）+ classifyResponseStatus 连读不读（L360-377）回码证实；四形态即时切换一致（adapter L332 / interceptor L350 / FailoverStreamFlow L462 / GatewayStreamingRetryCallback retry L95 区）；recordSuccess 清零连续失败计数（ThresholdBreaker L232-245）证实减速器穿插失效；声明语义边界（无退避即时切换）尊重，立案面收窄为信号可用而未用——P3 恰当 |

### 判定统计（C8 维度）

- **保留×2**：C8-001（P1）、C8-004（P3）
- **降级×2**：C8-002（P1→P2 条件化，零生产接线）；C8-003（P2→P3 条件化，零生产接线）
- **驳回×0**（4 条机制全部回码成立，未发现初审夸大——降档全部源于触发面休眠而非机制不成立）
- 终判严重度分布：P1×1 / P2×1（条件化）/ P3×2（条件化×1 + 保留×1）

### 现状标注分布（C8 维度）

- 新发现：全部 4 条（cra-index WI 1.1–3.2 台账零同站点）
- 流式主链 live（LOCAL 形态 opt-in 装配）：C8-001
- failover 请求主链 live：C8-004
- 渠道栈休眠（fan-out 面零生产接线）：C8-002
- 渠道栈休眠（连接器生命周期零接线）：C8-003

### 降档口径核对（grep 证据留痕）

1. **行为性缺陷不豁免**：C8-001（零需求缓冲增长）与 C8-002 的超时锚定语义均为行为性/契约性缺陷，单线程单订阅者下同样成立——不适用任何单线程豁免，C8-001 因此维持 P1。
2. **休眠面条件化（C1/C4/C7 复核 grep 证据沿用并核对）**：`dispatchInbound`/`subscribeInbound` 全仓非测试零调用 → C8-002 降档；`startAll`/`stopAll` 全仓非测试零调用 + `sendOutbound`/`onMessage` 双重 `!started` 门（L221-224/L268-271 回码补证）→ C8-003 降档；`sendToUser`（nop-datav NotificationSender 唯一 live 消费面）在 started==false 下于 sendOutbound 抛异常处止步，**不可达** deliverSegmented——C8-003 四面全休眠的核心理由。
3. **请求触达面不适用启动期降档**：C8-001/C8-004 位于 failover 请求面（adapter opt-in by-name 装配），与 C1/C3 复核"流式主链 live"裁定及 WI 2.3"缓冲流式层为 AI 路由现实路径"先例一致，维持原判。

### 跨维指针核对（同案命中清单）

1. **C8-001 = 单元内 CL-001（C1 文件）/CL-005（C3 文件）/CL-012（C6 文件）/CL-014（C7 文件）同站点同失效面**：五处登记（含 C8-001）收敛为单工单，C8-001 为主登记，四处 CL 不独立计数（C1 复核指针 9"归 C5/C8 处置"在此落位；与 C3 复核指针 2"C3-001 终态截断 vs 无界缓冲异面"边界维持）。
2. **C8-001 ↔ WI 2.3 C3-003 / WI 3.1 MA-01（成员 C5-001）**：跨单元同族（越窗零需求无界缓冲三实现），判级 P1 全对齐，零站点重复——修复批次对齐（shared 有界缓冲组件方向见 CL-023 处置）。
3. **C8-002 = C4-002 + C7-004 同站点第三失效面**：C7-004（内存面）已并入 C4-002 工单；C8-002（超时锚定/取消半执行/观测混淆行为语义面）失效面独立、修复指令独立，**保留独立条目、与 C4-002 工单同站点同批**（不同于 C7-004 的吸收式并入——其增量仅日志/驻留细节，本条修复不随队列治理自动闭合）。
4. **C8-002 修复对齐 WI 2.3 CL-022**（L351/L418 orTimeout → JDK Delayer 共享调度器移交面）：deadline 移至执行开始的实现不得加深 Delayer 依赖，随移交轮统一。
5. **C8-004 ↔ C7-005**：同为"平台原语信号在编排消费面未被消费"族（RetryContext.retryAfterMs vs ThresholdBreaker.allowCall），但站点/后果/判级不同（本条有界 ×3 且行为面为声明语义，彼条容量单调流失）——非同案，判级差异（P3 vs P2）成因登记。
6. **CL-023 ↔ WI 2.3 C3-003**：孪生站点登记与既有台账重复，处置见 C8-round2.md 复核结论（cross-ref 关闭新立案，修复联动保留）。

### 高风险条目子项复核说明

- **C8-001（P1 保留）**：逐子项核验——(a) request(MAX)：onSubscribe 内守卫（cancelled/state!=attempt）后无条件发放，全流无第二次 request 调用，"恒定 MAX"成立；(b) 越窗 add：L380 无容量检查，与 L359-360 越窗前双门（bufferSize+时间窗）形成不对称；(c) 读取侧 flushBuffer/flushAll 仅 demand 门控，写入侧修复即闭合（初审 F3 枚举 7 项逐一核对无误，L438/L492 两处 clear 为终态清理非缺陷）；(d) 成本面（下游停滞期间 request(MAX) 使 token 持续计费消耗）为 (a) 的逻辑必然；(e) live 面：opt-in 装配为本单元已裁定 live 形态（两前序复核 + 孪生台账 P1 三方一致），WI 3.1 "opt-in 装配面降级"先例不适用——彼两条降级对象是装配路径自身的缺陷，本条是装配后请求面上的行为缺陷。
- **C8-002（P1→P2 条件化）**：逐子项核验——(a) orTimeout 锚点：JDK `orTimeout` 于调用时调度 timeout，提交-执行间隔计入期限，队列积压 >30s（dispatchTimeoutMs 默认 30_000，L103）即未执行先判超时；(b) 取消后执行：L398-403 无 return，自我中断后 listener 照常执行；L399-400 注释"do not let the listener run unguarded"与实现矛盾，是初审未引用的增证；(c) 三态观测混淆：非阻塞 listener 完整执行后仍经 L410-416 抛 CancellationException → L437 同一日志，dropped/exec-timeout 无分离计数；(d) mode-2 sendAsync（L349-351）persist-before-return + at-least-once + 幂等消费为 javadoc 明文契约（L343-348 回码证实），初审"已声明边界不立案"维持；(e) 降档仅因触发面休眠，机制零修正。
- **C8-003（P2→P3 条件化）**：四面门控逐一回码——onMessage 首行早退（L268-271）、sendOutbound 抛 !started（L221-224，sendToUser 的 try/catch 仅裹 lookup 不裹 sendOutbound，异常路径同样到不了 deliverSegmented）、限流拒绝回复与同步异常路径均在 onMessage 内。FeishuClient.sendMessage（L238-243）→ httpApi.sendMessage 根外超时不可证——初审"部分未具体化"诚实标注维持，是降档而非升档的依据之一。

## 最终保留项（C8 维度）

| 工单面 | 条目 | 终判 | 修复方向 | 合并指令 |
|---|---|---|---|---|
| 越窗零需求无界缓冲 + 上游恒定 request(MAX) | C8-001 | P1 | 越窗缓冲复用 bufferSize 硬上限 + 溢出显式策略（deliverError + 指标）；上游改按下游 demand 授信或高水位门（平台 HighWatermarkSemaphore） | 单工单（吸收单元内 CL-001/005/012/014）；与 WI 2.3 C3-003 孪生站点、WI 3.1 MA-01 C5-001 族同批对齐（CL-023 联动） |
| fan-out 超时锚定提交时刻 + 取消后半执行 + 丢弃观测混淆 | C8-002 | P2 条件化 | deadline 移至执行开始；未启动任务跳过执行 + dropped/timeout 分离计数；部分执行恢复语义区分日志码 | 并入 C4-002 工单同批（同站点第三失效面，回升 P1）；deadline 实现随 WI 2.3 CL-022 Delayer 移交面对齐 |
| Feishu 出站同步无界发送（无超时/无限流/无隔离） | C8-003 | P3 条件化 | 回复路径复用 I3 R-2-1 guard 模板（dedicated executor + per-send deadline）；出站接 IRateLimiter；sendToUser 同步语义文档化 | 独立（投产接线即回升 P2）；与 CL-017 段数上限同批；与 C8-002 修复模板复用对齐 |
| 上游限流信号链断裂（Retry-After 可用而未用） | C8-004 | P3 | 分类为 RATE_LIMITED 时解析 Retry-After/复用 RetryContext.retryAfterMs，per-provider "静默至"时间戳重选跳过 | 独立（7.1 裁决修/deferred）；与 C7-005（allowCall 消费缺口）同批评估"平台信号消费面"治理 |

---

*本文件为 C8 维度盲审初审产物；复核状态已由 WI 3.3 独立复核更新，维度判定统计见「维度复核结论」。*
