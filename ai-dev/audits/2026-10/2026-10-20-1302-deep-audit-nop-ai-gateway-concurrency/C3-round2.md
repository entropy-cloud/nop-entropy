# C3 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C3 维度深挖第 2 轮盲审员（盲审纪律同第 1 轮；编号续接 C3-007 起）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L170–196（C3 正文）+ L351–377（附录 A）

## 深挖范围与方法

按派单深挖面逐一执行，跨模块证据面全文核对 `StreamingProcessor.java`（258L）与 `BufferedStreamingPublisher.java`（413L，nop-gateway——本模块回调的宿主链路）：

1. **GatewayStreamingRetryCallback/LifecycleListener 回调链**：两回调全文精读，逐方法核对异常路径、预算语义（1-based attempt 与 `attempt > retryBudget` 跟 0-based `attempt + 1 > retryBudget` 等价换算复核一致）、属性通道写序。发现 C3-007/C3-008 立案。
2. **ChannelLoginScanProcessor 轮询/回调交错**：Processor 内无轮询（无 sleep/schedule/poll）；唯一异步边界 `createSessionForUserAsync().thenApply(...)`（L143-151）。复核：同步抛 MFA 适配（try/catch L152-162）与 javadoc L138-141 声明的"LoginServiceImpl L355 bare throw"假设一致，且有测试漂移哨兵（javadoc 自述）——**已声明假设 + 守卫测试，负验证**；异步完成异常经 thenApply 传播至返回 stage，由 GraphQL/REST 调用方消费——无静默丢弃。`buildResult` 内 `generateAccessCode` 抛错 → 会话存量问题归 C6-005（本维度不重复）。
3. **FeishuConnector 投递线程的异常传播**：`onMessage`（L267-367）逐语句异常面核对——extractors 全为无抛字符串扫描；`findByChannel`/`updateLastActive`/`engine.execute`/`deliverSegmented` 四个可抛点全部就地 catch；`onExecutionComplete`（L376-416）有顶层 catch(Throwable)（L412-414）——投递线程异常传播面**收敛完备，负验证**。`onError`（L370-372）仅 WARN——连接级错误不改变 connector 状态，属传输层（FeishuClient）重连语义消费面，第 1 轮 C4-004/C4-005 家族已覆盖后果，不重复。
4. **whenComplete 嵌套链**：三组嵌套逐一核对——①`decideNonStreamFailure` 的 CACHE_STATE_LOST/切换分支 `.whenComplete(completeRelay)`（ChatServiceFailoverAdapter L313/L333、AiGatewayFailoverInterceptor L334/L351）：relay 只做 complete/completeExceptionally，不可抛——嵌套层完成正确；②`FeishuConnector` execute future 的 whenComplete → onExecutionComplete（有顶层护栏，见上）；③`dispatchInbound` mode-2 `sendFuture.whenComplete`（仅日志）。嵌套链内层抛非 Nop 异常的悬挂面第 1 轮 C3-004/C7-001 已立案（含 relay 前置决策链），本轮负验证其 relay 层本身。

## 新增发现

### [C3-007] 网关流式 NON_TRANSIENT 降级路径绕过缓冲层窗口丢弃语义：降级响应作为普通元素进入 AttemptSubscriber 通路——attempt-1 已缓冲前缀泄漏进最终输出 + 失败 attempt 经 onStreamComplete 被记为熔断成功（记账反转）；与本地形态 deliverError 语义分裂

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:404-427`（onError 降级分支）、`:457-465`（onStreamComplete recordSuccess）；宿主链路证据 `nop-service-framework/nop-gateway/.../StreamingProcessor.java:170-183`（proceedOnError → onNext+onComplete）；对照面 `nop-ai/nop-ai-gateway/.../FailoverStreamFlow.java:449-452, 485-496`
- **证据片段**:
  ```java
  // StreamingProcessor.createMappedPublisher（缓冲层内链，先于缓冲窗口判定执行）:
  public void onError(Throwable throwable) {
      try {
          Object element = invocation.proceedOnError(throwable, context);   // L172 → 拦截器 onError
          if (element != null) {
              subscriber.onNext(element);      // L175 —— 降级响应作为普通元素喂给 AttemptSubscriber
              subscriber.onComplete();         // L176 —— 失败伪装为正常完成
          }
  // AiGatewayFailoverInterceptor.onError（仅 NON_TRANSIENT 返回非 null）:
  metrics.onDegradedTermination(...);                       // L424
  return degradedErrorResponse(exception);                  // L426 —— 进入元素通路
  // AiGatewayFailoverInterceptor.onStreamComplete —— 内链 onComplete 必经:
  CircuitObservation.recordSuccess(metrics, breaker, ...);  // L463 —— 失败 attempt 记熔断成功
  ```
  缓冲层声明的丢弃语义（BufferedStreamingPublisher javadoc L294-296"窗口内 → 丢弃已缓冲元素（attempt1 前缀不得进入最终输出）"）只在 `handleError` 的重试路径执行（L301 `state.buffer.clear()`）；降级路径不经过 handleError。
- **严重程度**: P2
- **并发场景**: 无需竞态——**确定性时序缺陷**：单条流式请求、接管路由（isActive=true）、attempt-1 在窗口内（未转发任何元素）以 NON_TRANSIENT 分类失败（如 provider 返回 400/413/404 类客户端错误，`classifyStreamError`/`LlmErrorClassifier` 按 4xx 归 NON_TRANSIENT）。
- **触发时序**: T1 attempt-1 首批 chunk 到达（0 ≤ k < N=10 且 T=1000ms 未满）→ 全部入 `state.buffer`（BufferedStreamingPublisher handleItem L250-256）→ 上游投递 onError → 内链 `proceedOnError` → 拦截器 `onError` 判 NON_TRANSIENT **返回降级响应（非 null）** → 内链 `subscriber.onNext(降级响应)` → AttemptSubscriber.onNext → handleItem：窗口未过 → 降级响应**与前缀 chunk 一起继续缓冲** → 内链 `subscriber.onComplete()` → AttemptSubscriber.onComplete → handleComplete → `flushAll` 把 **attempt-1 前缀 chunk + 降级 wrapper 一并交付下游** → `proceedOnStreamComplete` → 拦截器 `onStreamComplete` → `recordSuccess(modelKey)`。交叠后果三连：①用户收到 attempt-1 残缺前缀 + 一个 wrapper JSON 块 + "正常"完成信号（违反缓冲层 Major-1 丢弃契约）；②刚失败的候选被 `recordSuccess` 记账为熔断成功（consecutiveFailures 清零——真实故障被洗白，熔断对该候选的累计被重置）；③同场景本地形态走 `deliverError`（清缓冲 + onError，FailoverStreamFlow L449-452）——两形态对外语义分裂。
- **现状**: B-17 契约声明"仅 NON_TRANSIENT 族返回降级终止响应"，B-18 声明"链上无先返回非空响应的吞错者"前提——但缓冲层接管链路中拦截器自己的降级返回恰是该前提的违例形态；`proceedOnError` 返回值进入元素通路的后果（窗口丢弃被绕过 + onStreamComplete 记账反转）在两侧 javadoc 均未声明。
- **风险**: 客户端可见脏输出且无错误信号（F2"半个成功"变体：失败被当成功终止）；熔断记账反转使重复性 4xx 故障的候选永不累积失败计数（`CandidateHealth` 恒 CLOSED）——健康视图失真；前端收到非流式格式的 wrapper 混入 SSE 事件流可能导致客户端解析异常。
- **建议**: 拦截器 `onError` 的降级分支感知缓冲层接管状态（`svcCtx` 已知 retryCallback 已注入时改为 rethrow，让缓冲层 `terminate(throwable)` 走原生错误路径），或在缓冲层 `handleComplete`/`flushAll` 前丢弃未越窗 attempt 的缓冲（对齐 Major-1）；`onStreamComplete` 的 recordSuccess 增加守卫——仅当本次内链未发生过 onError 降级时记账（可经 request property 标记降级发生）。
- **失效面枚举（F3）**: 流式失败终态的全部路径逐一登记——①切换类/CACHE_STATE_LOST 窗口内失败：rethrow → handleError → 清缓冲 + 重订阅（**正确**）；②NON_TRANSIENT 窗口内失败（**本条缺陷面**）；③NON_TRANSIENT 窗口外失败：内链同路径 onNext+onComplete，但已 forwarded、缓冲为空——降级响应作为尾元素交付 + recordSuccess 反转仍然发生（同根，修复点相同，并入本条）；④本地形态 FailoverStreamFlow 同分类路径（deliverError 清缓冲 + onError，正确，对照面）；⑤预算耗尽/饱和：retry 返回 null → terminate(throwable) 原生错误路径（正确）。
- **信心水平**: 很可能（链路路由为两模块代码实证；NON_TRANSIENT 判定对"带 httpStatus 参数的 4xx NopException"的分类映射为在库分类器行为——具体 4xx 触发器未逐 provider 指认）
- **误报排除**: 非 C3-001 重复（那条是 demand 约束下的终态冲刷截断，本条是降级路径绕过窗口丢弃 + 记账反转）；非"降级响应作为正常终止"这一网关声明设计本身的指控（非缓冲形态下该设计自洽）——指控的是该设计与缓冲层窗口语义/熔断记账的**组合后果**未声明且违反缓冲层自身 Major-1；非第 1 轮 C3-004（异常护栏缺失面）同失效面。
- **复核状态**: 未复核
- 复核状态: 保留(P2，子项修正：后果② recordSuccess 记账反转驳回)｜新发现（同文件 WI 2.3 C3-003 为缓冲层背压断裂——不同失效面，补充登记非重复）。核心链回码证实：interceptor.onError L426 非 null 降级响应 → StreamingProcessor L173-176 `onNext+onComplete` **直达缓冲层 AttemptSubscriber** → `handleItem` 窗口未过与前缀同缓冲 → `handleComplete` L329 `flushAll` 交付 + `terminate(null)` 成功终态——Major-1 丢弃契约（L301 `state.buffer.clear()` 仅在 handleError 重试路径执行）被确定性绕过，脏输出 + 伪装成功维持 P2。**后果②驳回详证**：`proceedOnStreamComplete` 在 nop-gateway main 代码唯一调用点为 StreamingProcessor L188，位于 mapped publisher 的 onComplete lambda 内、由源（HTTP 事件流）正常完成驱动；降级路径自 onError 发出 onNext+onComplete 不经过该 lambda，`interceptor.onStreamComplete` → `recordSuccess`（L457-465）在降级与窗口外面均不可达——初审触发链末段混淆了订阅者回调 onComplete 与源完成 onComplete，证据链断裂；后果①③维持，修复建议中"recordSuccess 增加守卫"一项随之撤销

### [C3-008] retry() 切换路径 sinkCandidate 先行落盘新候选属性，buildHttpRequest 随后可能返回 null（config 缺失）——断流终态的错误归因/指标面指向从未发起请求的候选

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/GatewayStreamingRetryCallback.java:93-116`
- **证据片段**:
  ```java
  interceptor.sinkCandidate(request, next, attempt + 1);   // L95 —— 属性通道已写入新候选
  ...
  return buildHttpRequest(request, next);                  // L101
  ...
  private HttpRequest buildHttpRequest(ApiRequest<?> request, ModelClassCandidate candidate) {
      LlmModel config = AiGatewayFailoverInterceptor.loadLlmConfigOrNull(candidate.getProvider()); // L109
      if (config == null) {
          LOG.warn("...retry-aborted...");
          return null;                                     // L115 —— null = 不重试（断流报错）
      }
  ```
- **严重程度**: P3
- **并发场景**: 单流式请求的重试决策线程（无竞态，纯状态序缺陷）；后续终态消费方（`onStreamComplete`/`onError` 的 `currentCandidate` 读属性、`onStreamElement` 残余 chunk 转换）在同一投递线程上读到已漂移的属性。
- **触发时序**: T1 attempt-N 失败（可切换）→ `selectNextWithProbe` 选中 next → `sinkCandidate` 把 PROP_PROVIDER/MODEL/ACCOUNT_KEY/BASE_URL/API_STYLE 全量改写为 next → `buildHttpRequest` 因 provider 无 `.llm.xml` 返回 null → 缓冲层 `terminate(原错误)` → 交叠后果：终态信号与后续观测读取 `currentCandidate` 得到 **next**——断流错误被归因到从未收到任何请求的候选；若部署挂接 per-candidate 告警，故障账号（原候选）隐身、无辜候选（next）背锅。
- **现状**: `sinkCandidate` 与 `buildHttpRequest` 的成功耦合未原子化；config 缺失路径有 WARN（可观测）但属性回滚缺失。
- **风险**: 低频（需 next 的 provider 配置缺失 + 可切换失败组合）；后果限于观测/归因失真，无正确性损害。
- **建议**: `buildHttpRequest` 前置 config 校验（null → 直接 return null，不 sinkCandidate）；或 config==null 时回滚属性（重写回 failedCandidate 五字段）。
- **失效面枚举（F3）**: `sinkCandidate` 全部调用点——①`onRequest` L197（首选择后，后续全部消费同线程，无漂移面）；②retry 切换路径 L95（**本条**）；③`invokeNonStreamingAttempt` L268（非流式，sink 后 proceedInvoke 同线程消费，无窗口）；④CACHE_STATE_LOST 路径 L63 仅写 PROP_ATTEMPT 不换候选（attempt+1 写入后 `buildHttpRequest` 返回 null 的同型漂移——残余属性仅 PROP_ATTEMPT 虚高，后果可忽略，并入本条登记）。
- **信心水平**: 确定（代码序即触发序）
- **误报排除**: 非"错误处理策略"维度越界——立案面是异步终态的归因一致性（C3 时序假设面）而非错误码/日志规范；P3 判级与后果有界一致。
- **复核状态**: 未复核
- 复核状态: 保留(P3，证据增强)｜新发现。代码序回码证实（sinkCandidate L95 → L96-100 metrics → L101 buildHttpRequest null 判定）；**证据增强**：初审未指出的即时记账漂移成立——retry 中止路径（buildHttpRequest 返回 null）上 `onSwitchAttempt`/`onResubscribe`（L96-100）已先记到从未收到请求的 next，属性漂移之外另有指标面即时错归因，失效面枚举③④维持；后果有界（观测/归因失真），P3 维持。与 WI 3.1 MA-08 超时取消传播族不同模块不同失效面（MA-08 为 nop-ai-agent 工具执行取消传播），仅治理轴向邻接，非同案

## 维度集外线索（overlay）

—— 本维度第 2 轮无新增集外线索（CL-022/CL-023 由 C7/C8 文件登记）。

深挖面负验证留痕（不立案）：①RetryCallback 预算换算两形态一致（1-based/0-based 等价）；②LifecycleListener exactly-once 释放复核成立（onFetchStarted 先释放后获取 + released 守卫）；③ChannelLoginScanProcessor 异步边界异常传播完备（同步 MFA 假设有守卫测试）；④FeishuConnector 投递线程异常收敛完备（onMessage 四可抛点全就地 catch + onExecutionComplete 顶层护栏）；⑤whenComplete 嵌套链 relay 层不可抛。

## 继续判定

新增 2 条（P2×1 + P3×1，无 P0/P1）。判定：**终止信号**——①C3-007 虽为确定性缺陷但失效面封闭（NON_TRANSIENT 降级 × 缓冲接管组合，修复点唯一且不产生新的子代站点）；②第 1 轮六条中未闭合的悬挂面（C3-004④ GatewayStreamingRetryCallback 非 Nop 异常传缓冲层）已由本轮 C3-007 的缓冲层宿主链路核对收敛（`handleError` L303-310 对 retry 抛 Throwable 有 catch-all + terminate fail-loud——**该待证面在缓冲层侧已有兜底，负验证闭合**）；③四个定向深挖面均闭合。建议 C3 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C3 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=1，P3=1；CL 追加=0。*

## 复核结论（WI 3.3 独立复核，合并批 C3+C4）

C3-007 **保留(P2，子项修正)**：核心缺陷（降级响应绕过缓冲层 Major-1 窗口丢弃 + 失败伪装成功终态）经宿主链路逐行回码证实；子后果②（recordSuccess 记账反转）**驳回**——`proceedOnStreamComplete` 唯一调用点（StreamingProcessor L188）由源正常完成驱动，降级路径自 mapped onError 直接向缓冲层订阅者发 onNext+onComplete，不经过该调用点，初审触发链末段为两 onComplete 混淆；修复建议相应缩减（记账守卫不再需要）。C3-008 **保留(P3，证据增强)**：代码序证实之外补证 retry 中止路径上 switch/resubscribe 指标先行错记 next 的即时漂移。两条与 cra-index WI 1.1–3.2 台账零同站点重复；完整判定表与合并指令见 C3-async-chains.md「维度复核结论」。
