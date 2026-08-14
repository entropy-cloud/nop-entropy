# W1 前置 spike：流式重订阅可行性验证 + 决策暴露

> Status: resolved
> Date: 2026-08-15
> Scope: nop-gateway 流式链路（StreamingProcessor/StreamingResponse/GatewayHttpFilter/IGatewayInterceptor）、nop-ai-core ChatServiceImpl.callStream、需求 Q2/Q5/Q7
> Conclusion: 网关形态字节流层重订阅**可行但需最小 nop-gateway 通用改动**（机制 C 不可行、机制 B 只能降级终止、推荐机制 A 链内缓冲 + 通用重执行扩展点，零 nop-ai 依赖可保持）；本地形态重订阅 = 以新 ChatOptions 重调 `callStream`，可行，取消/背压边界已实证；Q2/Q5 已整理为待人工裁决决策点；需求文档假设未被推翻，无需修订
> Source: `ai-dev/plans/2026-08-15-0604-1-w1-streaming-resubscribe-spike.md`（W1）、`ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`

## Context

- 需求 Q7：流式重订阅机制可行性——网关形态字节流层"缓冲 + 替换式重订阅"与 `StreamingProcessor`/`StreamingResponse` 路由期绑定 publisher 的耦合；本地形态 chunk 流层重订阅的背压/取消语义。
- 验证方式：只读代码追踪（全部结论附 `文件:行号` 证据）+ `_tmp/` 下 JDK-only scratch 实证（`SubmissionPublisher` 背压/取消竞态，`_tmp/SubmissionPublisherScratchV1-V6.java`）。
- 本计划为纯调研/文档计划，不产出任何 `src/` 实现代码。

## Analysis

### SPIKE-01 网关形态 Q7：字节流层"首段缓冲 + 替换式重订阅"验证

#### 1. 现状链路追踪（live 核实，行号与计划 baseline 一致）

- `RouteExecutor.execute`（`nop-gateway/.../core/executor/RouteExecutor.java:76-77`）：流式路由（`route.getStreaming() != null` 且 enabled）**直接 `return executeStreaming(...)`，不调用 `invocation.proceedInvoke`** → `IGatewayInterceptor.invoke`（`IGatewayInterceptor.java:82`）在流式路径**不可达**。`AiFailoverGatewayInterceptor.invoke` 的 `svcCtx.isStreamingMode()` 守卫分支（`AiFailoverGatewayInterceptor.java:42-45`）在现链路上不可达——spike 以代码事实为准，不被其误导。
- `StreamingProcessor.executeStreaming`（`StreamingProcessor.java:59`）时序：
  1. `context.setStreamingMode(true)`（`:67`）；
  2. `invocation.proceedOnStreamStart`（`:71`）——**此时 `StreamingResponse` attribute 尚未写入**（写于 `:95`），`onStreamStart` 钩子无法接触 publisher；
  3. `buildStreamingHttpRequest`（`:74`）；
  4. `httpClient.fetchServerEventFlow(httpRequest, context)`（`:77`）——**惰性** publisher：`ServerEventPublisher.subscribe` 仅 `onSubscribe`（`ServerEventPublisher.java:47-53`），HTTP 请求在首次 `request()` 时才经 `AbstractServerEventSubscription.request`（`AbstractServerEventSubscription.java:31-45`）→ `startRequest()` → `sendAsync` 发起；
  5. `createMappedPublisher`（`:80`，实现 `:108-169`）——包装事件流：onSubscribe 转发订阅 + `request(1)`（`:116-120`）、每元素 `invocation.proceedOnStreamElement`（`:131`）、onError → `invocation.proceedOnError`（`:147`，非空返回降级 `onNext`+`onComplete`，`:148-151`）、onComplete → `proceedOnStreamComplete`（`:161`）；
  6. `new StreamingResponse(mappedPublisher, ...)`（`:88`）——`publisher` 为 **final 字段**（`StreamingResponse.java:24`），消费期不可替换；
  7. `context.setAttribute(StreamingResponse.class.getName(), streamingResponse)`（`:95`）；
  8. 返回空成功 `ApiResponse`（`:98`）。同步异常经 catch → `proceedOnError`（`:100-102`）。
- 消费端 `GatewayHttpFilter`：
  - `write`（`GatewayHttpFilter.java:158-159`）在**响应写阶段**（`handler.handle(...)` future 完成后）从 context 读 attribute 强转 `StreamingResponse`；
  - `writeStreamingResponse`（`:172-225`）：订阅 `getPublisher()`（`:182`）→ onSubscribe 回调中 `streamingResponse.setUpstreamSubscription(subscription)`（`:188`，**消费订阅阶段、元素流动之前**）→ `request(1)`（`:190`）；
  - 写完成后 `streamingResponse.abort()`（`:220-222`）。
- `StreamingResponse.abort()`（`StreamingResponse.java:71-76`）：读 `upstreamSubscription`（AtomicReference），null 时 **no-op**；取消为**静默**（`AbstractServerEventSubscription.cleanup`（`AbstractServerEventSubscription.java:146-152`）：置 cancelled + `future.cancel(true)` + notifyAll，**不产生下游 onError/onComplete 信号**）。
- 错误钩子可达性：`proceedOnStreamError`/`onStreamError` 在现链路**不可达**——全代码库无生产调用点（grep 核实：仅接口/实现定义与测试文件 `GatewayFixTest.java:188`、`AiFailoverGatewayInterceptorTest.java:42/92`、`LoggingInterceptorTest.java:204`）。流式链路错误唯一到达的钩子是 **`onError`（经 `proceedOnError` 逆序钩子）**。
- `StreamingResponse` 的构造与 attribute 读写点全库唯一：构造 `StreamingProcessor.java:88`（+2 个测试）、写 `:95`、读 `GatewayHttpFilter.java:159`。
- **publisher 绑定时机**：路由执行期构造 `StreamingResponse` 时绑定 final publisher；attribute 写于路由期（`:95`），读于响应写阶段（`:159`）。**attribute 写入与 filter 读取之间不存在任何拦截器钩子**（`onStreamStart` 早于 `:95` 且被 `:95` 覆盖；`onStreamComplete` 晚于元素流结束）。注意：路由期绑定的只是 **publisher 对象**，HTTP fetch 本身惰性（响应写阶段首次 `request` 才发起）。

#### 2. 候选机制 C（拦截器侧替换，nop-gateway 零改动候选）——**不可行**

- `invoke()` 不参与流式路径（`RouteExecutor.java:76-77`）；拦截器在流式路径的接触点仅 `onRequest`（早于一切，可改请求但不改响应流）→ `onStreamStart`（`StreamingProcessor.java:71`，attribute 未写入，且 `:95` 会覆盖任何提前写入）→ `onStreamElement`/`onError`/`onStreamComplete`（元素已流动）。
- 不存在"attribute 写后、filter 读前"的钩子（已核实 `GatewayRouteExecution`、`InterceptedGatewayInvocation` 全部钩子定义与调用点）。拦截器**没有任何时点可以替换已写入的 attribute 值**。
- 若改 `RouteExecutor` 接线让流式路径也走 `proceedInvoke`，拦截器 `invoke` 可包装整个流式执行——但这要求拦截器能**重新执行路由**（重新发起 fetch + 重建请求），即需要 `StreamingProcessor`/`RouteExecutor` 暴露重执行能力 → **退化为机制 A 的改动面**。
- **结论：机制 C 在现结构下不可行；零 nop-gateway 改动的假设不成立。**

#### 3. 候选机制 B（onStream* 钩子承载首段缓冲 + 重试）——部分边界核实，**不能承载重订阅**

- `onStreamStart`：attribute 未写入（`:71` vs `:95`）、fetch 未发起 → 无法绑定/替换 publisher。
- `onStreamElement`（`:131`）：单元素变换钩子——非 null 返回值**必被转发**（`StreamingProcessor.java:133-135`），无法暂停/缓冲多元素、无法拦截转发。
- `onError`（经 `proceedOnError` 逆序钩子，`StreamingProcessor.java:147` + `InterceptedGatewayInvocation.proceedOnError`）：**流式链路上错误唯一到达的钩子**。
  - 能感知错误 ✓；
  - 能取消上游 ✓（此时 `setUpstreamSubscription` 已注入（消费订阅阶段 `:188` 早于元素流动）→ `context.getAttribute(StreamingResponse.class.getName()).abort()` 非 no-op）——取消通道存在；
  - 不能感知取消完成 ✗（abort 静默取消，无下游信号，钩子无从得知取消何时/是否完成）；
  - **不能触发重订阅** ✗（无重新执行路由的通道；钩子内重建请求/重新 fetch 无法替换已绑定的 publisher 与已订阅的下游）；
  - 能降级终止 ✓（返回非空 `ApiResponse` → `onNext`+`onComplete` 正常终止（`:148-151`）或原样转发 onError）。
  - "能取消 / 能重订阅"四象限：**能取消 ✓ / 能感知取消 ✗ / 能重订阅 ✗ / 能降级终止 ✓**。
- `proceedOnStreamError`/`onStreamError` 不可达（无生产调用点）——重试决策**不可能**挂在此钩子上。
- **结论：机制 B 只能承载"错误观测 + 降级终止"，不能承载缓冲与重订阅；重订阅必须由机制 A（缓冲层）承担，钩子在其外侧配合。**

#### 4. 候选机制 A（nop-gateway 内缓冲层）——**可行（推荐）**，最小改动面

- 可行位置：缓冲+重订阅层插在 `createMappedPublisher` 与 `StreamingResponse` 之间（**publisher 链内部**）。语义：首段元素缓冲不转发 → 上游失败且未越窗 → cancel 旧 subscription → 经重执行回调发起新 fetch（新 HttpRequest：新账号/新 model 由回调侧决定）→ 新链缓冲 → 越窗后转发。
- 关键前提：需要**重执行回调**——重放"构建请求 + 发起 fetch + 元素映射链"的通用扩展点（拦截器 `onRequest`/`onStreamStart` 语义、converter 均已在上游完成，重执行回调只需重建 `HttpRequest` 并重新跑 fetch 链）。该回调属**网关通用概念**（流式重试/重订阅），实现方注入（如 route 级可配置处理器或 bean），**不引入 nop-ai 依赖**。
- `StreamingResponse.publisher` final 字段：缓冲层在链内实现时**可以不动**；若采用"链外替换"形式（publisher 改 `AtomicReference` + setter）则**仍缺替换触发时点**（attribute 写后无钩子）——即 A2 单独不充分，与 A1 等价需要链内层。**最小改动 = A1**。
- 涉及类清单（全部为 nop-gateway 通用改动）：
  - `StreamingProcessor`（缓冲/重订阅扩展点接线：缓冲窗口计数/计时 + 重执行回调注入）；
  - `GatewayStreamingModel`（可选：缓冲窗口参数 N/T、重订阅开关配置）；
  - `StreamingResponse`（可选：publisher 可替换化，仅在链外替换形式下需要）；
  - `RouteExecutor`（不改，除非采用"流式也走 proceedInvoke"形式——非必要）。
- **nop-gateway 零 nop-ai 依赖约束可保持**：上述改动全部为通用流式能力（缓冲、重试回调接口），无 nop-ai 引用；与需求 §六裁决不冲突（不扩展 `AiFailoverGatewayInterceptor`，不迁移）。

#### 5. 机制 A vs C 比较

| 维度 | 机制 A（网关内缓冲层） | 机制 C（拦截器侧替换） |
|------|----------------------|----------------------|
| 现结构可行性 | **可行** | **不可行**（invoke 不参与流式路径；attribute 写后无钩子） |
| nop-gateway 改动面 | 最小通用改动（StreamingProcessor 扩展点 ± StreamingResponse 可替换化 ± 模型配置） | 零改动假设不成立；若改接线（proceedInvoke 走流式）则需重执行能力 = 机制 A 的改动面 |
| nop-ai 依赖 | 可保持零依赖（通用接口注入） | 拦截器在 nop-ai-gateway，但无法触发替换 |
| 缓冲/重试编排归属 | 网关通用层 + nop-ai-gateway 实现重执行回调 | — |
| 对"nop-gateway 只读复用"假设 | **推翻**（roadmap W7 module/area 表述需同步） | 保持（但功能不可落地） |

- **推荐：机制 A（A1 链内缓冲 + 通用重执行扩展点）**。理由：唯一可行路径；改动最小且通用；零 nop-ai 依赖纪律与 §六裁决均可保持。

#### 6. SPIKE-01 结论（对 W7 的输入）

1. **结论三选一：需最小网关改动（可行）**。字节流层缓冲+重订阅在现结构下**不可直接落地**，但以最小 nop-gateway 通用改动可落地；机制 C 不可行、机制 B 仅能降级终止。
2. W7 前置：nop-gateway 最小通用改动（StreamingProcessor 缓冲/重执行扩展点，± StreamingResponse publisher 可替换化）；缓冲/重订阅编排在 nop-ai-gateway 实现（拦截器或专用组件提供重执行回调：选账号 → 重建 `HttpRequest` → 重执行）。
3. 拦截器钩子定位：`onError` 只做观测/降级终止（返回错误 `ApiResponse` → `onNext`+`onComplete`）；`onStreamStart`/`onStreamElement`/`onStreamComplete` 不承载重试；`onStreamError` 不可达，勿依赖。
4. **并发计数挂钩点（网关形态）**：fetch 发起 = 响应写阶段（mappedPublisher 首次 `request` 时，`GatewayHttpFilter` 订阅触发）——与本地形态（callStream 调用时）不同；"流建立 +1"应挂钩 fetch 发起时刻，-1 挂钩流终止/取消。
5. `abort()` 不能用于同步取消感知（注入前 no-op、静默取消）；W7 不得依赖取消完成信号。
6. 对 roadmap 的影响：W7 module/area "nop-gateway（只读复用）"表述**被推翻**，同步为"最小通用改动（零 nop-ai 依赖保持）"；需求文档假设未变（见 SPIKE-04）。

### SPIKE-02 本地形态 Q7：chunk 流层重订阅验证

#### 1. 代码追踪（live 核实）

- `IChatService.callStream(ChatRequest, ICancelToken)` → `Flow.Publisher<ChatStreamChunk>`（`nop-ai-api/.../chat/IChatService.java:33`）。
- `ChatServiceImpl.callStream`（`nop-ai-core/.../service/ChatServiceImpl.java:176-231`）每次调用：
  - `checkRateLimit(provider, config)`（`:182`）——**每次调用重复执行**；失败同步抛 `ERR_AI_RATE_LIMITED`（429，`ChatServiceImpl.java:353-357`）；
  - requestId 仅在 `getRequestId()==null` 时生成（`:186-187`）——重订阅复用同一 `ChatRequest` 时 requestId 不变；
  - `chatLogger.logRequest(request)`（`:191`）——**每次调用重复记日志**；
  - `buildHttpRequest(config, provider, model, request, true, dialect)`（`:196`，实现 `:253-275`）——每次调用读取 `ChatOptions.accountKey/accountBaseUrl/model`（`:255-257`、`resolveModel`）→ **绑定当次账号配置**；
  - `new SubmissionPublisher<>()`（`:199`）——每次调用新建；
  - `httpClient.fetchServerEventFlow(httpRequest, cancelToken)`（`:201`）→ `eventPublisher.subscribe(...)`（`:202`）——fetch **在 callStream() 调用栈内同步发起**：`ServerEventPublisher.subscribe` 惰性仅 onSubscribe（`ServerEventPublisher.java:47-53`），但匿名 subscriber 的 `onSubscribe` 同步 `request(Long.MAX_VALUE)`（`ChatServiceImpl.java:205`）→ `AbstractServerEventSubscription.request` 同步 `startRequest()` → `sendAsync`（`ServerEventPublisher.java:69-92`）；
  - `cancelToken.appendOnCancelTask(subscription::cancel)`（`:206-208`）——token 取消 → SSE subscription cancel（`future.cancel(true)` 断 HTTP）；
  - onNext → `dialect.parseStreamChunk` → `publisher.submit(chunk)`（`:212-217`）；onError → `publisher.closeExceptionally`（`:221`）；onComplete → `publisher.close`（`:226`）；返回 publisher（`:230`）。
- **"单次使用 + 上游急切启动"事实确认**：每次 callStream = 新 SubmissionPublisher + 新 HTTP SSE fetch；fetch 发起早于调用方订阅返回的 publisher。推论：**每次重订阅 = 新 HTTP 请求**。

#### 2. 背压/取消语义（实证：`_tmp/SubmissionPublisherScratchV1-V6.java`，JDK 21 `SubmissionPublisher`）

| 场景 | 实证结果 |
|------|---------|
| submit() 无订阅者 | 立即返回、item 丢弃（S1） |
| submit() 订阅者 demand 耗尽（内部缓冲 256 满） | **阻塞在 submit()**（S4/S5）；背压唯一体现点 = SSE 线程的 submit() 阻塞处；fetch 侧 `request(Long.MAX_VALUE)` 无背压 |
| 订阅者 cancel 自己的 Subscription | **唤醒阻塞中的 submit()**（S6）；移除后后续 submit 立即返回（S3）——**但上游 HTTP fetch 不停止**（无 token 通道），SSE 线程继续解析至 EOF 或 token 取消 |
| cancelToken 取消 | 仅作用于 SSE subscription（断 HTTP）；**不唤醒已阻塞在 submit() 的 SSE 线程**（S4/S5/S9 佐证） |
| close()/closeExceptionally()（demand=0 且缓冲非空） | **自身阻塞等待 drain**（S9/S10 实证）——SSE 线程在 onError/onComplete 路径可能悬挂 |
| 唤醒阻塞 submit() 的唯一途径 | 新 demand 到达 或 订阅者移除（S6） |

- 竞态结论："窗口内失败 → 重订阅"时，若 adapter 停止 demand 且 SSE 线程已阻塞在 `submit()`，cancelToken 取消后 SSE 线程**悬挂**（线程泄漏）。规避契约（W6 输入）：**取消顺序 = 先 cancel 调用方订阅（唤醒阻塞 submit）→ 再 cancelToken（断 HTTP）**；窗口期保持 demand（如 `request(MAX)`），避免缓冲积压导致 submit/close 阻塞。
- 契约确认：`IChatService.callStream` 的背压 = Flow 标准 `request(n)` 语义（经 SubmissionPublisher 缓冲传导）；`ICancelToken` 取消的是**上游 fetch**（SSE subscription），不直接作用于返回的 publisher。

#### 3. SPIKE-02 结论（对 W6 的输入）

1. 重订阅机制：**以新 `ChatOptions`（新账号）重新调用 `callStream`**——可行；每次 = 新 HTTP 请求 + 新 SubmissionPublisher + 新 buffer window。
2. 缓冲层位置：adapter 内，订阅 callStream 返回的 publisher 后包首段缓冲窗口（N 元素/T 毫秒），越窗后透传。
3. 取消/背压边界：见上表与取消顺序契约；窗口期必须保持 demand。
4. **并发计数挂钩点：挂钩 `callStream` 调用时刻**（fetch 已发起），而非订阅时刻——否则存在未计数窗口；-1 挂钩 publisher 终止（onComplete/onError）或取消完成。
5. 重订阅副作用（每次重调重复执行）：`checkRateLimit`（`:182`）——**本地限流可能拦下重试**（同步抛 `ERR_AI_RATE_LIMITED` 429，须纳入重试决策）；`chatLogger.logRequest`（`:191`）——日志噪音；requestId 复用语义（同 ChatRequest 时不变）。
6. 丢弃 publisher 不订阅 → HTTP 连接持续到 EOF（资源泄漏面）；adapter 必须保证取消路径完整（先 cancel 订阅 → 再 token）。

### SPIKE-03 Q2/Q5 决策点整理与暴露（待人工裁决）

#### Q2：前端 `model` 参数语义

- 问题陈述：前端请求携带的 `model` 在账号切换时的语义——由路由**覆盖**（跨模型切换生效，前端无感知）还是**保留**（仅同模型账号间切换）？需求 §一.4 仅方向性承诺，Q2 未决。
- 选项 (a) **路由覆盖**（推荐默认）：
  - 语义：前端 `model` 只是"路由键"，映射到模型类候选集后，实际调用模型 = 选中候选的 model（前端不可感知）。
  - 影响：跨模型/跨协议切换全部生效（与 §六"拒绝仅同协议 failover"一致）；W4 双向转换范围 = 任意 dialect 对（协议转换面最大）；W5 模型类路由组是必备前提（model → 候选集映射）；W6 实现面 = ChatOptions.model 下沉 + 请求体重写（无新机制，`ChatOptions.model` 已存在）；W7 实现面 = converter 请求体 model 重写 + 重执行回调携带新 model；响应中的 model 字段 = 实际调用模型（与前端声明可能不同）——前端契约需注明。
  - 测试面：跨模型切换（OpenAI→Anthropic 等）、model 重写断言、前端无感知（响应格式经 dialect 转换仍为前端格式）。
- 选项 (b) 保留前端 model：
  - 语义：仅同模型（同 apiStyle）账号间切换，换 key/endpoint 不换 model。
  - 影响：跨模型/跨协议切换不可用（§一.4 方向性承诺落空）；W4 双向转换范围收窄为同协议对（跨协议部分白做）；模型类路由组退化为"账号组"（跨模型候选失去意义）；W6/W7 无需 model 重写（实现面略小）。
- 推荐默认：**(a) 路由覆盖**——与需求 §一.4 方向性承诺（允许跨模型/跨协议切换）和 §六 已决项（拒绝"仅同协议 failover"）一致；否则两者矛盾。
- 待裁决：默认 (a)，需产品确认（尤其"响应 model 字段语义"边界）。

#### Q5：RATE_LIMITED/TRANSIENT → 账号链切换偏离确认

- 问题陈述：需求 §3.1 已声明偏离 coordinator 语义——coordinator 仅 QUOTA/AUTH 走账号链（TRANSIENT 走模型 tier、RATE_LIMITED 原地退避重试）；网关/适配器语义要求 RATE_LIMITED/TRANSIENT 同样触发账号链切换（用户目标：限流/连接中断也换账号）。
- 选项：
  - **确认偏离**（推荐默认）：RATE_LIMITED/TRANSIENT → 账号链切换（先账号链、耗尽后 provider 链），消耗重试预算；行为契约 = 需求 §3.1 表。
  - 拒绝（回退 coordinator 语义）：TRANSIENT 走模型 tier 回退、RATE_LIMITED 原地退避重试——但 §六已拒绝引入 `IModelRouter`/模型 tier（"网关语境不引入模型 tier"，`02-account-failover-requirement.md` §4.3 表），拒绝 = 需重新裁决 tier 引入或"TRANSIENT 不切换直接失败"；RATE_LIMITED 原地退避 = 不消耗账号链、延迟更高。
- 影响：确认 → W6/W7 在 RATE_LIMITED/TRANSIENT 触发切换（含流式窗口内重订阅），重试预算覆盖此两类；拒绝 → 流式 failover 仅 QUOTA/AUTH 触发，用户目标（429 限流、连接中断换账号）落空，需求 §3.1/§一.1 需同步修订。
- 推荐默认：**确认偏离**——用户目标明确（§一.1"限流/连接中断"换账号），roadmap Why 与需求 §3.1 已声明；拒绝将收缩需求范围且与 §六裁决联动。
- 待裁决：默认确认，需产品拍板。

#### 决策暴露方式

- 本 spike 文档专用章节（本章）+ 当日 `ai-dev/logs/2026/08-15.md` 显式"待人工裁决"标记。
- 裁决时限：W6/W7 实现前（roadmap "前置决策"行；未裁决不阻塞本计划关闭，但必须已暴露）。

### SPIKE-04 结论回馈：假设核对与修订裁定

| 需求文档假设 | spike 结论 | 是否推翻 |
|------|-----------|---------|
| §3.2 网关形态缓冲/切换发生在字节流层（`StreamingResponse` + `IGatewayContext`） | 缓冲/重订阅确在字节流层（publisher 链内），语义成立；但**机制载体需最小 nop-gateway 通用改动**（final publisher + 无重执行通道 + attribute 写后无钩子） | **未推翻**（语义不变，机制前提补充） |
| §3.2 本地形态缓冲/切换发生在 chunk 流层（重订阅） | 重订阅 = 重调 callStream，可行；背压/取消边界已实证（见 SPIKE-02） | **未推翻** |
| §一.4 方向性承诺（允许跨模型切换） | Q2 推荐默认"路由覆盖"与之一致 | **未推翻** |
| §3.1 语义偏离声明（RATE_LIMITED/TRANSIENT → 账号链） | Q5 推荐默认"确认偏离"与之一致 | **未推翻** |
| §六 不直接扩展 `AiFailoverGatewayInterceptor`；nop-gateway 零 nop-ai 依赖 | 机制 A 最小改动不含 nop-ai 引用、不改该拦截器 | **未推翻** |
| roadmap W7 module/area "nop-gateway（只读复用）" | 机制 A 需最小网关通用改动 | **推翻**（roadmap 表述同步，见下） |

- **裁定：`02-account-failover-requirement.md` 无需修订**（假设未变；Q7 结论不推翻语义假设，仅补充机制前提）。Q 表（Q2/Q5/Q7）状态回填按 roadmap 设计归 W8 OBS-04。
- roadmap 同步：W1 → `done`；W7 module/area 的 "nop-gateway（只读复用）" 同步为 "nop-gateway（最小通用改动，按 W1 spike 结论；零 nop-ai 依赖保持）"。

## Conclusion

- **网关形态（SPIKE-01）**：字节流层重订阅**需最小 nop-gateway 通用改动（可行）**——推荐机制 A（A1 链内缓冲 + 通用重执行扩展点）；机制 C 不可行、机制 B 仅能观测/降级终止；nop-gateway 零 nop-ai 依赖可保持；对 W7 的输入见 SPIKE-01 §6。
- **本地形态（SPIKE-02）**：重订阅 = 重调 `callStream`（新 ChatOptions），可行；取消顺序契约（先 cancel 订阅 → 再 cancelToken）与窗口期保持 demand 为背压/取消边界结论（实证）；并发计数挂钩 `callStream` 调用时刻；对 W6 的输入见 SPIKE-02 §3。
- **Q2/Q5**：已整理为决策点（选项 + 影响 + 测试面 + 推荐默认：Q2=路由覆盖、Q5=确认偏离），暴露于本文档与 daily log 待人工裁决。
- **SPIKE-04**：需求文档假设未被推翻，`02-account-failover-requirement.md` 无需修订；roadmap W7 module/area 表述已同步。
- 后续工作：W6/W7 实现前裁决 Q2/Q5；W7 plan 起草时引用本 spike 结论作为机制方案输入。

## Open Questions

- [ ] Q2 前端 `model` 参数语义——待人工裁决（默认：路由覆盖）。
- [ ] Q5 RATE_LIMITED/TRANSIENT → 账号链切换偏离——待人工裁决（默认：确认）。
- [ ] Q3 缓冲窗口参数（N/T）与重试预算默认值——W6/W7 执行期裁决（本 spike 仅确认无硬依赖）。
- [ ] Q8/Q10 模型类配置形态、规则策略 DSL 形态——W5 plan 阶段裁决。
- [ ] Q7 处置结果回填需求文档 Q 表——归 W8 OBS-04（本 spike 结论已落档于此）。

## References

- `ai-dev/plans/2026-08-15-0604-1-w1-streaming-resubscribe-spike.md`（本 spike 计划）
- `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W1/W6/W7 定义、前置决策行）
- `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.1/§3.2/§4.3/§五 Q2/Q5/Q7/§六）
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java`
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/RouteExecutor.java`
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/streaming/StreamingResponse.java`
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java`
- `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/IGatewayInterceptor.java`、`InterceptedGatewayInvocation.java`、`AiFailoverGatewayInterceptor.java`
- `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java`、`nop-http-api/.../AbstractServerEventSubscription.java`
- `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/IChatService.java`
- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`
- `_tmp/SubmissionPublisherScratchV1-V6.java`（SubmissionPublisher 背压/取消实证 scratch，JDK-only）
- `ai-dev/logs/2026/08-15.md`（当日日志，含"待人工裁决"标记）
