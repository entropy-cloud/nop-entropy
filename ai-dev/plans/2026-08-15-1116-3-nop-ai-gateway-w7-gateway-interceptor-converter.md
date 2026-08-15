# W7 网关形态拦截器 + converter 扩展

> Plan Status: active
> Last Reviewed: 2026-08-15
> Review Consensus: 三轮独立 fresh-session 对抗性审查达成共识（R1: 2 Blocker + 4 Major 修复；R2: 1 Blocker + 8 Major 修复；R3: 0 Blocker 0 Major，consensus approve——终审 agent 核验：properties 通道（ApiRequest @JsonIgnore）、委托 wrapper（upstreamSubscription/abort）、context-attribute 注入（GatewayHandler:77 + StreamingProcessor:95 同款）、B-8 判定自洽、base 替换语义、onError 职责与 proceedOnError 行为一致、全部引用 live 核实）
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W7）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.1/§3.2/§3.3/§4.1/§4.3/§4.4/§五 Q2/Q3/Q5/Q7）、`ai-dev/analysis/2026-08/2026-08-15-w1-streaming-resubscribe-spike.md`（SPIKE-01 对 W7 的输入）
> Related: `ai-dev/design/nop-ai-gateway/01-architecture.md`、`ai-dev/plans/2026-08-15-0604-1-w1-streaming-resubscribe-spike.md`、`ai-dev/plans/2026-08-15-0849-2-w5-model-class-routing-and-selection.md`
> Mission: nop-ai-gateway-failover
> Work Item: W7

## Purpose

落地网关形态（独立 nop-gateway）的透明账号 failover：在 nop-ai-gateway 实现 `IGatewayInterceptor`（选择/切换/重试编排，语义同 W6），扩展 `AiDialectBackendMessageConverter`（stream=true 请求体生成 + per-request 动态 dialect + 目标 provider 真实 `LlmModel` config），并按 **W1 spike 结论（机制 A）** 对 nop-gateway 做最小通用改动（`StreamingProcessor` 缓冲/重执行扩展点 ± `StreamingResponse` publisher 可替换化），字节流层首段缓冲 + 替换式重订阅，流式钩子并发计数；nop-gateway 保持零 nop-ai 依赖。

## Current Baseline

（live repo 核实，2026-08-15）

- **W1 spike SPIKE-01 结论（对 W7 的输入，已落档）**：
  1. **结论三选一：需最小网关改动（可行）**。机制 A（A1 链内缓冲 + 通用重执行扩展点）推荐；机制 C（拦截器侧替换）不可行（`RouteExecutor.java:76-77` 流式路由直接 `return executeStreaming`，不调 `proceedInvoke`；attribute 写后无钩子）；机制 B（onStream* 钩子）仅能观测/降级终止（"能取消 ✓ / 能感知取消 ✗ / 能重订阅 ✗ / 能降级终止 ✓"四象限）。
  2. 最小改动面：`StreamingProcessor`（缓冲/重执行扩展点接线：缓冲窗口计数/计时 + 重执行回调注入）；`GatewayStreamingModel`（可选：缓冲窗口参数 N/T、重订阅开关配置）；`StreamingResponse`（可选：publisher 可替换化——仅链外替换形式需要，A1 链内层不需要）；`RouteExecutor` 不改。
  3. 拦截器钩子定位：`onError` 只做观测/降级终止（返回错误 `ApiResponse` → `onNext`+`onComplete`）；`onStreamStart`/`onStreamElement`/`onStreamComplete` 不承载重试；`onStreamError` 不可达，勿依赖。
  4. **并发计数挂钩点（网关形态）**：fetch 发起 = 响应写阶段（mappedPublisher 首次 `request`，`GatewayHttpFilter` 订阅触发）——"流建立 +1"挂钩 fetch 发起时刻，-1 挂钩流终止/取消。
  5. `abort()` 不能用于同步取消感知（注入前 no-op、静默取消）；不得依赖取消完成信号。
  6. nop-gateway 零 nop-ai 依赖约束可保持（改动全部为通用流式能力，无 nop-ai 引用）。
- **网关流式链路现状**（spike 核实）：`StreamingProcessor.executeStreaming`（`nop-service-framework/nop-gateway/.../core/executor/StreamingProcessor.java:59`）路由期构建 `mappedPublisher`（`:80`）→ `StreamingResponse`（`:88`，publisher 为 **final 字段**，`StreamingResponse.java:24`）→ `context.setAttribute`（`:95`）→ 空成功响应；`GatewayHttpFilter`（`.../http/GatewayHttpFilter.java`）响应写阶段读 attribute（`:158-159`）→ 订阅 `getPublisher()`（`:182`）→ `setUpstreamSubscription`（`:188`，消费订阅阶段、元素流动之前）→ `request(1)`；`StreamingResponse.abort()`（`:71-76`）null 时 no-op + 静默取消。
- **converter 现状**：`AiDialectBackendMessageConverter`（`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/AiDialectBackendMessageConverter.java`）——`frontendLlm`/`backendLlm` 固定 bean 属性；`toBackendRequest` 硬编码 `stream=false`（`:54` `buildBody(chatRequest, config, null, model, false)`）+ `new LlmModel()` 空配置（`:53`）；`toFrontendResponse`/`toFrontendStreamChunk` 用固定 backendLlm/frontendLlm；bean 注册于 `ai-gateway-defaults.beans.xml`（`nopBackendMessageConverter_AI_DIALECT`，frontendLlm=openai, backendLlm=openai）。**流式链路 converter 零调用点**（grep 实证：`IBackendMessageConverter` 唯一生产调用点 = `RouteExecutor.executeRouteLogic`（`RouteExecutor.java:136-143`，非流式）；`toFrontendStreamChunk` 生产零调用点）——流式转换接线必须由拦截器承担（Phase 1 B-1 裁定）。
- **W5 游走原语**（nop-ai-core，W7 消费，语义同 W6）：`ModelClassRouter.forRequest/selectNext/selectNextAfterFailure/toChatOptions/hasRoutingGroup`；`ConcurrencyRegistry`（`(provider, accountKey)` 键，主账号 null）；`ThresholdBreaker`/`ModelKeys.buildModelKey`/`LlmErrorClassifier`/`IRetryPolicy`。
- **拦截器接口**：`IGatewayInterceptor`（`nop-gateway/.../core/interceptor/IGatewayInterceptor.java`）——`onRequest`/`onResponse`/`onError`/`onStreamStart`/`onStreamElement`/`onStreamError`/`onStreamComplete`/`invoke`（default 方法，`invoke` 默认 `invocation.proceedInvoke()`）。
- **既有网关构件**：`AiFailoverGatewayInterceptor`（nop-gateway，非流式 URL 级 fallback）保持兼容不迁移（需求 §六裁决 + roadmap 网关依赖纪律）；`AiRateLimitGatewayInterceptor` 等入站限流与本需求无关。
- **nop-ai-gateway 模块**：pom 已依赖 nop-gateway + nop-ai-api + nop-ai-core（+ channel 相关 nop-ai-agent 依赖维持不变，需求 §4.1）；beans 文件 `ai-gateway-defaults.beans.xml` 已注册 converter。
- **dialect 面**（W4 已 done）：`LlmDialectFactory.getDialect(ApiStyle)`（`nop-ai-core/.../dialect/LlmDialectFactory.java:34`）；5 dialect 双向转换全绿；`ApiStyle` 枚举（`nop-ai-core/.../model/ApiStyle.java`）；目标 provider 真实配置 = `LlmConfigHelper.loadConfig(provider)`（`LlmConfigHelper.java:70`，含 apiStyle/errorMappings/defaultModel）。
- **Q2/Q5 前置决策（待人工裁决，W6/W7 实现前必须收口）**：同 W6 计划——按推荐默认起草（Q2 = 路由覆盖 model；Q5 = 确认 RATE_LIMITED/TRANSIENT → 账号链切换偏离）；若执行前人工裁决相反，先修订需求文档再继续（plan-first）。
- **Q3 参数待执行期裁决**：缓冲窗口（N 元素 / T 毫秒）、重试预算默认值——本计划 Phase 1 裁定（与 W6 同值裁定，保持一致）。
- 无任何网关形态拦截器实现代码（roadmap Current baseline 确认）；converter 无流式扩展。

## Goals

- 网关形态拦截器（nop-ai-gateway）实现选择/切换/重试编排（语义同 W6，复用 W5 游走原语 + W2 可靠性机制），集成既有 `IGatewayInterceptor` 链；**流式路径的 converter 接线由拦截器承担**（onRequest 首次转换 + onStreamElement 反向转换 + request headers per-request 通道，Phase 1 B-1 裁定）。
- `AiDialectBackendMessageConverter` 扩展：stream=true 请求体生成 + per-request 动态 dialect（经 request headers 读取目标候选 apiStyle，`LlmDialectFactory` 解析）+ 目标 provider 真实 `LlmModel` config（替换 `new LlmModel()`）。
- 按 W1 spike 机制 A 对 nop-gateway 做最小通用改动（`StreamingProcessor` 缓冲/重执行扩展点 ± `StreamingResponse` publisher 可替换化 ± `GatewayStreamingModel` 参数 + **流式生命周期回调扩展点（并发计数通道，Phase 1 B-2 裁定）**），字节流层首段缓冲 + 替换式重订阅；**nop-gateway 零 nop-ai 依赖保持**。
- 流式并发计数经生命周期回调（fetch 发起 +1 / 流终止/取消 -1，spike §6.4 + B-2 裁定）；全池饱和 fail-loud；`AiFailoverGatewayInterceptor` 保持兼容回归。
- 网关测试（路由级 + 流式全链 + onError 次序契约 + 委托 wrapper 取消传播）；需求文档 §3.2/§4.3/§4.4 落地状态同步 + roadmap W7 状态推进。

## Non-Goals

- 本地形态适配器（W6）。
- 不改 `AiFailoverGatewayInterceptor`（保留兼容不迁移——需求 §六裁决）。
- nop-gateway 超出 W1 spike 结论范围的核心改动（最小通用改动：StreamingProcessor 缓冲/重执行扩展点 ± StreamingResponse publisher 可替换化；RouteExecutor 不改）。
- 不新建第二套账号池/熔断/错误分类（复用优先硬约束）。
- 不实现多实例状态共享/手动运维/主动余额配额感知（显式 non-goal，§3.6）。
- 不实现规则策略（W5b）。

## Scope

### In Scope

- GW-01: 拦截器实现（选择/切换/重试编排，语义同 W6；集成既有 `IGatewayInterceptor` 链）。
- GW-02: `AiDialectBackendMessageConverter` 扩展：stream=true 请求体生成 + per-request 动态 dialect（按目标候选 apiStyle 经 `LlmDialectFactory`）+ 目标 provider 真实 `LlmModel` config（替换 `new LlmModel()`）。
- GW-03: 字节流层首段缓冲 + 替换式重订阅（按 W1 spike 机制 A 落地于 nop-gateway 最小通用改动）；流式钩子并发计数。
- GW-04: 网关测试（路由级 + 流式全链；`AiFailoverGatewayInterceptor` 保持兼容回归）。
- Phase 1 裁定：Q3 参数默认值（与 W6 一致）+ nop-gateway 改动面细化（机制 A 落地形态）+ 拦截器/重执行回调协作边界 + converter 动态 dialect 触发机制。

### Out Of Scope

- 本地形态（W6）。
- 可观测性指标契约与 docs-for-ai 使用文档（W8 OBS-01/OBS-03）。
- 规则选择策略（W5b）。
- nop-gateway 其他核心改动（非 spike 结论范围）。

## Execution Plan

### Phase 1 - 裁定：Q3 参数 + nop-gateway 改动面 + 拦截器/回调协作边界 + converter 动态 dialect

Status: planned
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（仅裁定记录；正式回填在 Phase 4）、`ai-dev/analysis/2026-08/2026-08-15-w1-streaming-resubscribe-spike.md`（复核引用）

- Item Types: `Decision | Proof`

- [ ] **Q3 参数默认值裁定（与 W6 一致）**：首段缓冲窗口 = N 元素（建议 10）/ T 毫秒（建议 1000），先到者越窗，可配置；重试预算 = 次数（建议 2 次重订阅/重发）+ 可选总延迟上限（建议 30s 或 null），可配置；主动切换不消耗预算、不记熔断（需求 §3.3）。理由落档。
- [ ] **流式路径 converter 接线裁定（审查 B-1/B-8/B-9/B-10 修复——必须先裁）**：已核实 `IBackendMessageConverter` 唯一生产调用点 = `RouteExecutor.executeRouteLogic`（`RouteExecutor.java:136-143`，**非流式**路径经 `proceedInvoke` 到达）；**流式路径（`RouteExecutor.java:76-77` → `StreamingProcessor.executeStreaming`）完全不调用 converter**（`buildStreamingHttpRequest` 直接用前端格式 `request.getData()` 作 body；`toFrontendStreamChunk` 生产代码零调用点）。因此：
  - (a) **接线分工裁定（B-8 Blocker 修复——消除双重转换矛盾）**：拦截器**只对流式路径**做请求体转换；**非流式路径保持既有 `executeRouteLogic` 调用 converter**（`RouteExecutor.java:136-143`），拦截器 `invoke` 内不做重复转换——否则非流式请求被转换两次（onRequest 一次 + executeRouteLogic 再一次，跨 dialect 产生垃圾 body）。拦截器经 `svcCtx.getCurrentRoute()`（`IGatewayContext.java:30`，`GatewayHandler:115` 已设置 route）判定流式，**判定条件与 `RouteExecutor:76` 同构 = `route.getStreaming() != null && isStreamingEnabled(...)`（审查 F2 修复——仅判 `getStreaming() != null` 会在 streaming.enabled=false 时误转 body 造成双重转换复现）**；非流式路径的动态 dialect 由 converter 自身读 per-request 信息处理（见 (e)）。
  - (a2) **请求实例一致性契约（审查 F1 修复）**：`MappingProcessor.mapRequest:63-67` 新建 `ApiRequest` **不拷贝 properties**，route 级 onRequest xpl（`GatewayRouteExecution:60-66`）可替换实例——落档：拦截器 `onRequest` **原地修改并返回同一实例**；流式 failover 路由**不配置 requestMapping/onRequest xpl**（Phase 5 测试路由遵守该约束）；重执行回调与 `onStreamElement` 经 `svcCtx.getRequest()` 读到的须为同一实例。
  - (b) **流式首次请求体转换** = 拦截器 `onRequest`（`RouteExecutor.java:74`，先于 `:76-77` 流式分发，返回的 request 传入 `executeStreaming`）执行首次候选选择 + 调 converter `toBackendRequest`（stream=true）→ `buildStreamingHttpRequest` 以转换后 body 构建请求；
  - (c) **重试转换** = 重执行回调按新候选重做（`toBackendRequest`，新 model/新 apiStyle）；
  - (d) **流式元素反向转换** = 拦截器 `onStreamElement` 调用 converter `toFrontendStreamChunk`（非 null 返回值被转发，`StreamingProcessor.java:133-135`）；
  - (e) **per-request 信息通道 = `ApiRequest.properties`（审查 B-9 修复）**：已核实 `ApiRequest.java:31,54-99` 有 `@JsonIgnore` 的 `properties` map（`setProperty/getProperty`，`cloneInstance:137-138` 拷贝）——**不序列化、客户端不可注入（`GatewayHttpFilter.buildRequest:305` 只从 HTTP 请求填充 headers/data）、不转发给 provider**，比 headers 更优。**拒绝 headers 通道**（`x-nop-*` header 会 (i) 被 `buildStreamingHttpRequest:193-197`/converter `:56` 转发给 provider；(ii) 客户端可伪造（headers 初值 = 客户端请求头）→ 未声明无条件覆盖时存在 SSRF/注入面；(iii) 转换顺序耦合（先写 headers 再调 converter））。拦截器把目标候选信息（目标 provider/apiStyle/model/accountKey/accountBaseUrl）写入 **request properties**；converter 读取 properties 覆盖固定 bean 属性（fallback：无 per-request 信息 = 沿用 bean 属性，**零回归**）。
  - (f) **per-attempt 反向转换 dialect（审查 B-10 修复）**：跨 dialect 重订阅（Q2 默认允许）后，第二 attempt 的 chunk 必须用**当前 attempt** 的 backend dialect 做 `toFrontendStreamChunk`。`onStreamElement` 钩子经 `svcCtx.getRequest()` 读到的是**原始请求**（properties 停在 attempt 1 时点）——**重执行回调必须同步更新 request properties（新 apiStyle/model）**，拦截器 `onStreamElement` 每次从 `svcCtx.getRequest()` 读当前 properties 做反向转换（attempt 2 用 attempt 2 的 dialect）。落档该 per-attempt 状态传播链。
- [ ] **并发计数通道裁定（审查 B-2/B-11/B-12/B-13 修复——必须先裁）**：fetch 发起物理位置 = `GatewayHttpFilter` 订阅/首次 `request(1)`（`GatewayHttpFilter.java:182-190` → `StreamingProcessor.java:112-120` 链），取消 = `abort()` 静默（`StreamingResponse.java:71-76`，无下游信号）——**拦截器钩子在这两个时点均不可见**（`onStreamStart` 早于 attribute 写入且 ≠ fetch 发起；`onStreamComplete`/`onError` 不覆盖静默取消）。裁定：
  - **计数生命周期钩子必须进 nop-gateway 改动面**——缓冲层扩展点注入通用生命周期回调（`onFetchStarted()` / `onStreamTerminated(cause)`，nop-ai-gateway 实现计数；重订阅时 -1 旧 attempt +1 新 attempt）；naive 拦截器实现（onStreamStart +1 / onStreamComplete -1）**明确禁止**（违反 spike §6.4 契约）。
  - **缓冲层必须传递"委托式 subscription wrapper"（审查 B-11 修复）**：缓冲层向下游传 wrapper（`cancel()` 委托给**当前活跃** subscription）而非原始 subscription——否则 (i) 静默取消绕过缓冲层（`abort()` 直取消原始 sub，`onStreamTerminated` 不可靠触发）；(ii) M-6"重注入"带竞态（缓冲层创建于 `StreamingResponse` 构造**之前**，拿不到实例；abort 与重订阅并发时读到旧/空 subscription → 新 fetch 泄漏）。**wrapper 设计取代"重注入"表述**（`abort()` 经 wrapper 取消当前活跃 sub，无竞态、顺带解决 B-2 静默取消观测）。**`abort()` 双触发去重（审查 F4 修复）**：`GatewayHttpFilter:220-222` 在 send 完成时（含正常完成）总是 `abort()` → wrapper.cancel()——缓冲层生命周期回调必须 exactly-once 去重（onComplete + abort 不双记 -1），Phase 2 测试断言配对。
  - **回调/监听器注入通道（审查 B-12 修复——最大执行断层）**：`GatewayHandler.java:77` 是 `new StreamingProcessor(httpClient, mappingProcessor)` 硬构造，无构造注入通道；nop-gateway 又零 nop-ai 依赖。裁定：**注入通道 = `IGatewayContext` attribute**——拦截器 `onRequest` 把重执行回调 + 生命周期监听器（nop-ai-gateway 实现）`context.setAttribute(...)` 写入（`StreamingProcessor:95` 同款用法），`StreamingProcessor` 缓冲层从 context 读取（缺省 null = 不重订阅/不计数，零回归）。Phase 5 端到端测试断言该运行时接线（拦截器写入 → 缓冲层消费）。
  - **并发 double-booking 竞态裁定（审查 B-13 修复）**：选择期饱和检查（onRequest，更早）与 fetch 期计数（filter 订阅期）之间存在并发窗口——并发请求可同时通过饱和检查随后都 +1，账号并发超限。已核实回调**不能否决已发起的 fetch**。裁定：接受该竞态为**预检语义**（与需求 §3.3"请求发出前检查"的工程边界一致：检查为尽力而为预检，计数为记账语义；并发窗口下可能短暂超限，不承诺硬上限——落档为显式契约边界，W8 指标审计可追溯）。Phase 5 并发饱和测试按此语义写断言。
- [ ] **nop-gateway 改动面细化裁定（机制 A 落地形态 + 审查 M-3/M-6 修复）**：
  - 缓冲/重订阅层（A1 链内）插在 `createMappedPublisher` 与 `StreamingResponse` 之间：首段元素缓冲不转发（N/T，先到者越窗）→ 越窗透传；窗口内上游失败 → cancel 旧 subscription → 经重执行回调发起新 fetch → 新链缓冲 → 越窗转发；窗口外失败 → 原样转发错误（降级终止）。
  - **重执行回调接口收窄（M-3 修复）**：回调输入 = 错误 + 当前上下文（route/request），输出 = **新 `HttpRequest`（null = 不重试）**——fetch + 元素映射链（`createMappedPublisher`，私有）重跑**留在 nop-gateway 缓冲层内部**（缓冲层已持有 route/request/context/invocation，`buildStreamingHttpRequest` 同文件可达）；**禁止**回调实现第二条映射链或回调反向调用 nop-gateway 内部（Anti-Hollow 高危面）。**重试不重放 `onStreamStart`/拦截器钩子（审查 B-15 修复）**：`:71` 只在 executeStreaming 执行一次，重订阅只重跑 fetch + 映射链——显式落档。
  - **首次/重试 URL 构造统一（审查 B-14 修复）**：`accountBaseUrl` 是 **base**（非全 URL；`ChatServiceImpl.buildHttpRequest:259-266` 经 `dialect.buildUrl(base, chatUrl, apiKey)` 拼接 path）。裁定：首次与重试两路径统一为 **base 替换语义**——首次：`buildStreamingHttpRequest` 支持从 request properties 读取 base-url 覆盖（route URL 表达式缺省仍用既有求值——零回归）；重试：回调按同一 base 替换语义构造 `HttpRequest`（与 `dialect.buildUrl` 组合一致）。**非流式 per-attempt baseUrl 覆盖（审查 F3 修复）**：非流式 URL 仅 `InvokeProcessor.invokeUrl:128` 求值 route 表达式——route URL 表达式可读 `svcCtx.request.properties`（`ServiceContextImpl:38` 把 svcCtx 放入 eval scope），拦截器每 attempt 把选中候选 `accountBaseUrl` 写入 request properties 供表达式求值（Phase 4 落档；本期若裁定不支持则显式声明，不得静默）。
  - **缓冲层传递委托式 subscription wrapper（B-11 裁定落档，见上）**；`StreamingResponse` 仅保持既有 `setUpstreamSubscription`/`abort()` 契约（abort 经 wrapper 生效）。
  - `GatewayStreamingModel` 增加缓冲窗口参数（N/T/开关，xdef 生成物经 codegen 再生成——**`gateway.xdef` 属 nop-kernel/nop-xdefs protected area，plan-first 载体为本 plan**）；`StreamingResponse.publisher` 可替换化（AtomicReference + setter）仅在链外替换形式需要——裁定是否必要（推荐：A1 链内层不必要，除非测试/实现发现链外替换需求，届时落档理由）；`RouteExecutor` 不改。
  - **缓冲关闭与计数耦合（审查 Minor 修复）**：缓冲关闭时生命周期回调**仍须触发**（计数与缓冲解耦，生命周期钩子独立于缓冲开关）——否则"缓冲关闭 ⇒ 计数静默消失"。落档。
  - 未配置重执行回调/缓冲关闭 = 既有零缓冲直通（零回归，显式声明非静默跳过）。
- [ ] **onError 钩子与缓冲层重试决策次序契约（审查 M-5/B-17 修复）**：上游错误先经 `createMappedPublisher.onError` → `proceedOnError`（拦截器 onError 钩子）再到达缓冲层；若 onError 返回非空 `ApiResponse` → 转为 `onNext`+`onComplete`（`StreamingProcessor.java:148-151`），缓冲层看到正常元素而非错误 → 重试永不触发。**职责划分裁定（B-17 修复——拦截器不可能知道窗口内外状态）**：拦截器只做**重试性分类**——可重试错误（QUOTA/AUTH/RATE_LIMITED/TRANSIENT 族）一律 **rethrow（纯观测，重试决策唯一归属缓冲层/重执行回调）**；不可重试错误（NON_TRANSIENT 族）才返回降级终止响应；**窗口内外判断唯一归属缓冲层**（窗口外 → 原样转发错误断流，不 consult 拦截器）。**链上吞错前提（审查 B-18 修复）**：`proceedOnError` 逆序短路——route 级 onError xpl（`GatewayRouteExecution:92-102`）或链上任何拦截器先返回非空 `ApiResponse` 即吞掉 AI 拦截器的 rethrow；落档该生效前提（AI 拦截器须在链中靠前/链上无吞错者）。
- [ ] **执行依赖声明（审查 M-8 修复）**：W7 编排语义以 W6 为对照基准，但 W6 未落地——裁定：**W7 执行依赖 W6 落地**（roadmap 执行顺序 W6 → W7 由 engine 保证）；W7 Phase 4 实现前必须复核 W6 分类动作表终态并保持一致（若 W6 已按人工裁决调整，W7 对齐之）；Q3 默认值与 W6 草稿一致（N=10/T=1000/2 次/30s 可选），无冲突。
- [ ] 消费点盘点（Proof）：`StreamingProcessor`/`StreamingResponse`/`GatewayStreamingModel`/`gateway.xdef` 行号与 spike 一致复核；converter 既有测试清单（`AiDialectBackendMessageConverterTest`）与 bean 注册点确认；`AiFailoverGatewayInterceptor` 既有测试清单（兼容回归面）；**流式链路 converter 零调用点核实（grep 实证，B-1 依据）**；**`ApiRequest.properties` 通道核实（setProperty/getProperty/cloneInstance，B-9 依据）**。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 全部裁定（Q3 参数、**流式 converter 接线分工（B-8 双转消除）、请求实例一致性契约（F1）、per-request properties 通道（B-9）、per-attempt 反向 dialect（B-10）、委托式 subscription wrapper + abort 双触发去重（B-11/F4）、回调/监听器 context-attribute 注入通道（B-12）、并发 double-booking 边界（B-13）、URL base 替换语义 + 非流式 baseUrl（B-14/F3）、onError 职责划分（B-17）、缓冲/计数解耦、执行依赖**、nop-gateway 改动面）落档于本 plan 文件，无未决项。
- [ ] nop-gateway 改动面与 spike 结论一致（机制 A，零 nop-ai 依赖保持），未引入 spike 范围外改动；**流式 converter 接线点（a-f）与并发计数通道有明确机制与消费方**（非"自然可达"假设）；**非流式路径无双重转换**（B-8）。
- [ ] Proof：spike 行号复核一致；**流式链路 converter 零调用点 grep 实证**；**`ApiRequest.properties` 通道实证**；converter/拦截器既有测试与兼容回归面盘点完成。
- [ ] `No owner-doc update required`（正式回填在 Phase 4）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - nop-gateway 最小通用改动（GW-03 机制载体）

Status: planned
Targets: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java`、`.../core/streaming/StreamingResponse.java`（如裁定需要）、`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/model/GatewayStreamingModel.java` + `nop-kernel/nop-xdefs/.../schema/gateway.xdef`（如裁定需要，protected area + codegen 再生成）

- Item Types: `Fix | Proof`

- [ ] 按 Phase 1 裁定落地 nop-gateway 最小通用改动：`StreamingProcessor` 缓冲/重订阅层（A1 链内）+ 重执行回调扩展点（**窄接口：错误 + 上下文 → 新 `HttpRequest`（null = 不重试）；fetch + 映射链重跑留在缓冲层内部**）+ **流式生命周期回调扩展点（`onFetchStarted()` / `onStreamTerminated(cause)`，Phase 1 B-2 裁定——并发计数通道；缓冲关闭时仍触发，与缓冲开关解耦）** + **委托式 subscription wrapper（Phase 1 B-11 裁定：向下游传 wrapper，`cancel()` 委托当前活跃 sub；`abort()` 经 wrapper 生效，取代"重注入"）**；**回调/监听器经 `IGatewayContext` attribute 注入（Phase 1 B-12 裁定：拦截器 `onRequest` 写入，缓冲层读取；缺省 null = 零回归）**；`GatewayStreamingModel` 缓冲窗口参数（N/T/开关）——**若涉及 `gateway.xdef`：经 codegen 再生成，禁止手编 `_gen` 产物；`./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 再下游 codegen（W3 同款流程）；`git diff` 核对模板漂移**；`StreamingResponse` publisher 可替换化（仅裁定需要时）。
- [ ] 缓冲语义：首段元素缓冲不转发（N 元素 / T 毫秒，先到者越窗）→ 越窗后透传；窗口内上游失败 → cancel 旧 subscription → 经重执行回调发起新 fetch（新 HttpRequest，由回调侧决定账号/model）→ 新链缓冲 → 越窗转发；窗口外失败 → 原样转发错误（降级终止）；**重订阅后 wrapper 指向新活跃 subscription（B-11：无需也不做 StreamingResponse 重注入）**；**重试不重放 `onStreamStart`/拦截器钩子，仅重跑 fetch + 映射链（B-15）**。
- [ ] 首次尝试 base-url 覆盖通道（Phase 1 B-14 裁定）：`buildStreamingHttpRequest` 支持从 **request properties** 读取 base-url 覆盖（base 替换语义，与 `dialect.buildUrl(base, chatUrl, apiKey)` 组合一致；缺省 = 既有 URL 表达式求值，零回归）；拦截器 `onRequest` 写入。
- [ ] **无静默跳过**：缓冲层/重执行扩展点无配置时行为 = 既有零缓冲直通（零回归，非静默跳过——显式声明"未配置重执行回调 = 不重订阅，原样透传/转发错误"）；新参数默认值 = 关闭缓冲（零回归）。
- [ ] nop-gateway 测试（本 Phase 内，纯 JUnit 直构模式——`GatewayFixTest` 先例：手动 Publisher stub + `GatewayContextImpl` + 匿名 `IGatewayInvocation`，无 VFS 引导，**不引用 nop-ai 类型**）：缓冲层单元测试（窗口内失败触发重执行回调/窗口外失败原样转发/未配置回调零回归直通/参数默认关闭/**生命周期回调 `onFetchStarted`/`onStreamTerminated` 在 fetch 发起与流终止（含静默取消经 wrapper）时被调用（B-2 断言）**/**wrapper 取消委托当前活跃 sub（B-11 断言）**/**回调经 context attribute 注入生效（B-12 断言）**）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] nop-gateway 最小通用改动落地；`./mvnw compile -pl :nop-gateway -am`（或 module 全路径）通过。
- [ ] 缓冲/重订阅层单元测试绿（含未配置回调零回归直通 + 参数默认关闭 + 生命周期回调断言 + 委托 wrapper 取消传播断言）。
- [ ] **接线验证**（Minimum Rules #23）：重执行回调接口被缓冲层在运行时调用（nop-gateway 侧测试以 stub 回调断言调用发生）。
- [ ] 零 nop-ai 依赖保持（grep 实证 nop-gateway 改动文件无 io.nop.ai 引用）。
- [ ] **无静默跳过**：未配置回调语义显式声明（零回归直通，非吞错）。
- [ ] `No owner-doc update required`（文档回填在 Phase 4；gateway.xdef 生成物变更记录在 Phase 4 需求文档 §3.4 同款处理）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - converter 流式扩展 + 动态 dialect（GW-02）

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/AiDialectBackendMessageConverter.java`、`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/AiDialectBackendMessageConverterTest.java`

- Item Types: `Fix | Proof`

- [ ] stream=true 请求体生成：`toBackendRequest` 支持流式标志（request options/路由 streaming 标志）→ `buildBody(chatRequest, config, model, true)`；既有非流式路径（stream=false）零回归。
- [ ] per-request 动态 dialect：converter 读取 **request properties 中的 per-request 目标候选信息**（拦截器写入，Phase 1 B-9 裁定——`ApiRequest.setProperty/getProperty`，非 headers）→ 经 `LlmDialectFactory.getDialect(apiStyle)` 解析 backendLlm；无 per-request 信息 = 沿用 bean 属性（零回归）。
- [ ] 目标 provider 真实 `LlmModel` config：`LlmConfigHelper.loadConfig(targetProvider)`（含 errorMappings/apiStyle/defaultModel）替换 `new LlmModel()`；无 per-request provider 信息 = 沿用既有空配置路径（零回归）或 bean 属性 provider。
- [ ] converter 测试（Minimum Rules #25）：stream=true 请求体断言（逐 dialect 或代表性 dialect）+ per-request 动态 dialect 覆盖固定属性（经 request properties 注入）+ 真实 config 加载断言（errorMappings/apiStyle 来自 provider 配置）+ 无 per-request 信息零回归（既有测试不改）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] converter 扩展落地；`./mvnw compile -pl :nop-ai-gateway -am` 通过。
- [ ] 新增测试绿（stream=true / 动态 dialect / 真实 config / 零回归四类断言）。
- [ ] 既有 `AiDialectBackendMessageConverterTest` 全绿（零回归）。
- [ ] **无静默跳过**：动态 dialect 读取缺失显式回退（声明语义，非吞错）。
- [ ] `No owner-doc update required`（文档回填在 Phase 4）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 拦截器实现 + beans 注册（GW-01 + GW-03 编排接线）

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/`（拦截器 + 重执行回调实现，包名 Phase 1 裁定）、`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`

- Item Types: `Fix | Proof`

- [ ] 拦截器实现 `IGatewayInterceptor`（选择/切换/重试编排，语义同 W6，Phase 1 执行依赖裁定：实现前复核 W6 分类动作表终态）：**转换分工（B-8，判定条件与 `RouteExecutor:76` 同构——F2）**——经 `svcCtx.getCurrentRoute()` 判定流式（`getStreaming() != null && isStreamingEnabled`），**仅流式路径**执行首次选择 + converter `toBackendRequest` 请求体转换（stream=true）+ 目标候选信息写入 **request properties**（apiStyle/model/accountKey/accountBaseUrl，供 converter 与首次 base-url 覆盖读取）；非流式路径不转换（保留 `executeRouteLogic` 既有 converter 调用），**但 `invoke` 内每次 attempt 仍写 properties（选中候选信息——供非流式 converter 动态 dialect 与 route URL 表达式 baseUrl 覆盖消费，F5 收口）**；`invoke`（非流式路径）内包装选择/切换/重试（`ModelClassRouter` + 双源分类 + 熔断记账 + 预算）；**`onError` 契约（B-17）**：可重试错误 **rethrow（纯观测，重试决策唯一归属缓冲层/重执行回调）**，仅不可重试错误返回降级终止响应；`onStreamElement` 调 converter `toFrontendStreamChunk`（**每次从 `svcCtx.getRequest()` 读当前 properties——per-attempt dialect（B-10）**）；`onStreamStart`/`onStreamComplete` 不承载重试；`onStreamError` 不可达勿依赖。
- [ ] 重执行回调实现（Phase 1 M-3/B-12 窄接口）：按选中候选（新账号/新 model）重建 `HttpRequest`（消费 W5 `toChatOptions` 下沉信息 + converter 转换产物 + **base 替换语义（B-14）**），null = 不重试；**同时更新 request properties（新 apiStyle/model——B-10 per-attempt 反向 dialect 传播链）**；经 `context.setAttribute` 注入缓冲层（B-12）。
- [ ] 并发计数：经 Phase 2 生命周期回调（`onFetchStarted` +1 / `onStreamTerminated` -1）实现（Phase 1 B-2 裁定：**禁止** onStreamStart/onStreamComplete 替代；缓冲关闭仍计数）；重订阅时 -1 旧 attempt +1 新 attempt；**并发 double-booking 边界按 Phase 1 B-13 裁定（预检语义，不承诺硬上限）**；全池饱和 fail-loud（`ERR_AI_MODEL_CLASS_SATURATED` 透传）。
- [ ] beans 注册 + **拦截器挂载（审查 M-7 修复）**：拦截器经 gateway 配置 `<interceptors><interceptor id=... bean=...>`（`gateway.xdef:91-110` → `GatewayModel` interceptorRouter）进入既有链——仅 beans.xml 注册不会挂载到路由，落档挂载契约（测试用 gateway.xml/等价配置接线）；`AiFailoverGatewayInterceptor` 保持不动。
- [ ] **无静默跳过**：预算耗尽/全池饱和/不可切换分类显式失败（错误响应或抛错）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 拦截器 + 重执行回调 + beans 注册就位；`./mvnw compile -pl :nop-ai-gateway -am` 通过。
- [ ] 编排语义与 W6 一致（选择/切换/重试/记账/预算/fail-loud 对照核对）。
- [ ] **接线验证**（Minimum Rules #23）：拦截器/重执行回调被 nop-gateway 缓冲层在运行时调用（Phase 5 端到端测试断言；本 Phase 以 stub/接口契约收口）。
- [ ] **无静默跳过**：失败路径显式失败。
- [ ] `No owner-doc update required`（文档回填在 Phase 5）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - 网关测试收口 + 文档同步（GW-04）

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/failover/`（路由级 + 流式全链测试）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`、`ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`

- Item Types: `Fix | Proof | Follow-up`

- [ ] 路由级测试：非流式路由全链（选择 → converter 转换（含动态 dialect/真实 config）→ 转发 → 失败 → 分类 → 重选 → 二次转发成功）；**非流式路径无双重转换断言（B-8：拦截器未对流式判定外的请求做 toBackendRequest）**；`AiFailoverGatewayInterceptor` 既有测试兼容回归。
- [ ] **流式全链端到端测试**（Minimum Rules #22 + 测试台裁定，审查 M-10 修复：需 **CoreInitialization/VFS 引导 + 测试 gateway.xml 资源**（`CFG_GATEWAY_MODEL_PATH` 配置），fake `IHttpServerContext`（`sendStreamingResponse` 有 default 实现，`IHttpServerContext.java:102-118`，fake 只需子集）+ fake `IHttpClient`（受控失败注入））：流式路由（经 `<interceptors>` 挂载形态）→ `onRequest` 首次选择 + 转换 + properties → `StreamingProcessor` 缓冲层（**经 context attribute 取到回调/监听器——B-12 运行时接线断言**）→ 首次 fetch 窗口内失败 → **重执行回调被调用**（接线断言）→ 新候选重订阅（wrapper 指向新 sub）→ 越窗转发（`onStreamElement` 反向转换，**attempt 2 用新 dialect——B-10 断言**）→ 客户端输出，从路由入口到客户端输出完整走通。
- [ ] **onError 次序契约断言（B-17）**：窗口内失败确实触发重试（未被 onError 降级终止吞掉）；窗口外失败 → 断流报错不切换；NON_TRANSIENT 错误 → 降级终止响应。
- [ ] 流式窗口外失败测试：断流报错不切换；预算耗尽断流；**并发计数 +1/-1 配对（经生命周期回调，含静默取消经 wrapper 路径——B-2/B-11 断言；并发饱和按预检语义断言——B-13）**。
- [ ] 全池饱和 fail-loud（并发饱和 + 健康度饱和，错误码断言）；无路由组直通零回归。
- [ ] 零回归：`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` 绿 + `./mvnw test -pl nop-gateway 相关模块`（nop-gateway 改动回归）。
- [ ] 需求文档同步：§3.2 流式缓冲/重订阅落地状态、§3.3 并发记账/饱和落地状态、§4.3 复用 vs 新增边界核对（converter 扩展落地）、§4.4 网关形态数据流核对；Q 表 Q2/Q3/Q5/Q7 处置记录（Q7 spike 结论回填；Q2/Q5 若已人工裁决按裁决回填，未裁决沿用默认并标注）。
- [ ] roadmap W7 状态：`todo` → `planned`（**本计划经独立 draft review 通过时立即置**，非执行期）→ `done`（独立 closure audit 通过后，Phase 5 只做 `→ done`）；W7 module/area 的 nop-gateway 改动表述与落地一致。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] GW-04 全部测试绿（路由级 + 流式全链 + 窗口外/预算/并发配对/饱和 + 零回归）。
- [ ] **端到端**：流式路由从入口到客户端输出的完整路径已验证（含重执行回调运行时调用断言）。
- [ ] **接线验证**：拦截器→converter→缓冲层→重执行回调调用链连通性已验证。
- [ ] 需求文档落地状态与 live baseline 一致；Q 表处置可追溯。
- [ ] roadmap W7 状态推进到位。
- [ ] `check-doc-links.mjs --strict` 退出码 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 `00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 网关形态全部落地（拦截器编排 + converter 流式/动态 dialect + nop-gateway 最小通用改动 + 字节流层缓冲/重订阅 + 并发计数），无空洞组件。
- [ ] 端到端全链测试绿（流式路由入口 → onRequest 转换 → 缓冲 → 窗口内失败 → 重执行回调（经 context attribute 注入）→ 重订阅 → 越窗 → 客户端输出）；接线验证（重执行回调/生命周期监听器被 nop-gateway 运行时调用）通过。
- [ ] nop-gateway 零 nop-ai 依赖保持（grep 实证）；改动面未超出 spike 结论范围；**非流式路径无双重转换（B-8）**。
- [ ] 复用优先不变式验证通过（消费 `ModelClassRouter`/`ConcurrencyRegistry`/`ThresholdBreaker`/`LlmErrorClassifier`/`LlmDialectFactory`/`LlmConfigHelper`，无第二套机制）。
- [ ] `AiFailoverGatewayInterceptor` 保持兼容（既有测试全绿）；既有 converter 测试零回归。
- [ ] 既有 nop-ai-core/nop-ai-gateway/nop-ai-agent/nop-gateway 测试零回归。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（Q2/Q5 待裁决项已暴露且沿用推荐默认，不属降级）。
- [ ] 受影响的 owner docs（requirement）已同步到 live baseline。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：调用链运行时连通、fail-loud 语义、取消/并发契约、复用优先、零回归、nop-gateway 依赖纪律）。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）拦截器→converter→缓冲层→重执行回调→fetch 的调用链在运行时确实连通（端到端 + 接线测试断言），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `./mvnw compile -pl :nop-ai-core,:nop-ai-gateway -am` + nop-gateway 模块编译
- [ ] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` + nop-gateway 模块测试
- [ ] checkstyle / 代码规范检查通过（或按 mission 既有 lint 兜底通道判定）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（文档变更后执行）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-gateway --severity high` 退出码 0（关闭时执行）

## Deferred But Adjudicated

### Q2/Q5 人工裁决未收口（本计划按推荐默认起草）

- Classification: `watch-only residual`（决策点已暴露于 spike 文档 + daily log 双通道；推荐默认与需求 §一.4/§3.1 方向一致）
- Why Not Blocking Closure: 推荐默认（Q2 = 路由覆盖 model、Q5 = 确认 RATE_LIMITED/TRANSIENT → 账号链切换）即需求文档既有方向性承诺；若执行前人工裁决相反，先修订需求文档再调整本计划实现（plan-first），不构成静默降级。
- Successor Required: `no`（裁决回填归 W8 OBS-04）

### Q3 剩余参数（总延迟上限默认值）

- Classification: `optimization candidate`（延迟上限为可选维度，Phase 1 已裁定默认 null = 仅次数预算）
- Why Not Blocking Closure: 次数预算已构成重试预算契约；总延迟上限是运维级可选约束，默认关闭不影响行为契约成立。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 可观测性指标（切换次数/熔断状态/冷却/饱和计数）契约归 W8 OBS-01；docs-for-ai 使用文档归 W8 OBS-03。
- Q2/Q5/Q7 人工裁决/处置结果回填需求文档 Q 表归 W8 OBS-04。
- nop-gateway 改动在 W8 全链回归中复核（OBS-02 全链测试含网关形态流式路径）。

## Closure

Status Note: （关闭时填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- （关闭时填写）
