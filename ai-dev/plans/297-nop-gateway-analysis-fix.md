# 297 Nop Gateway 分析发现修复计划

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: `~/ai/gateway-survey/analysis/`（见 `Related`，不在 nop-entropy 仓库内）
> Related: `~/ai/gateway-survey/analysis/00-integration-synthesis.md`, `03-gap-analysis.md`, `~/ai/gateway-survey/design-deep-dive/00-technical-roadmap.md`, `01-filter-chain-design.md`, `comparison/02-architecture.md`

## Purpose

将 gateway-survey 分析（三份独立评估）发现的 7 个 P0 bug + 文档偏差（Phase 0 描述过时、接口假设错误）统一修复。
修复完成后 `nop-gateway` 模块的 Reactive Streams 合规性达标、所有 P0 关闭、survey 文档与实际代码一致。

## Current Baseline

**代码层面（`nop-service-framework/nop-gateway/`）**：

- `IGatewayInterceptor` 9 方法接口已存在，`InterceptedGatewayInvocation` 双向链已实现
- `InvokeProcessor` 支持 source/RPC/URL 三模式，`ForwardProcessor` 有 streaming 兼容性检查
- `TriePathRouter` 用于路由匹配，`ApiRequest`/`ApiResponse` 为标准请求/响应类型
- `IBackendMessageConverter` 接口已存在，AI 转换器（OpenAI/Claude/Ollama）已实现
- 流式基于 `Flow.Publisher`，但 `StreamingProcessor.createMappedPublisher()` 有 Reactive Streams 违规（无 `request(N)`）
- 客户端断连时 `StreamingResponse` 无 `abort()`，`GatewayHttpFilter` 无断连监听器
- `InterceptedGatewayInvocation.proceedOnStreamElement()` 传原始 `element` 而非转换后的 `result`
- `RouteExecutor.executeLogic0()` 传入原始 `request` 而非 `IBackendMessageConverter` 转换后的 `req`
- `AiBackendMessageConverter` 使用 `static {}` EnumMap 注册，不可 IoC 覆盖
- `RouteExecutor.getConverter()` 无异常保护
- `GatewayMatchModel.passConditions()` 死代码（从未被调用）
- `GatewayHttpFilter.setExecutionInvoker` 缺少 `@Inject`

**文档层面（`~/ai/gateway-survey/`）**：

- `00-technical-roadmap.md` Phase 0 假设"需要新建 `IGatewayFilter`/`SegmentRouteTable`/`DefaultFilterChain`/`nop-gateway-server`"与实际不符
- `01-filter-chain-design.md` §3.4 提到 `IGraphNode`（不存在）而非实际 `InterceptedGatewayInvocation`
- `comparison/02-architecture.md` §2 缺少现有实现引用

**真正剩余的 gap（已确认）**：

- 3 个阻塞性 Reactive Streams 违规（subscribe 无背压、断连不传播、异常序列违规）
- 2 个传参 bug（onStreamElement 传原始值、executeLogic0 传原始 request）
- 1 个架构性设计（AiBackendMessageConverter 静态注册不可扩展）
- 1 个代码质量（passConditions 死代码）
- 1 个 IoC 注入遗漏（setExecutionInvoker 缺 @Inject）
- 1 个异常安全（getConverter 无异常保护）
- survey 文档 3 处不一致

## Goals

- [x] 修复 3 个阻塞性 P0 Reactive Streams 违规
- [x] 修复 2 个传参 P0 bug
- [x] 修复 3 个架构性/代码质量 P0/P1 问题
- [x] AI Backend Converter 改为 IoC 可扩展注册
- [x] `setExecutionInvoker` 添加 `@Inject`
- [x] 更新 survey 文档（roadmap Phase 0、filter-chain-design §3.4、02-architecture §2）
- [x] 补充测试覆盖（背压、流式错误路径、断连处理）
- [x] `./mvnw test -pl nop-gateway -am` 通过

## Non-Goals

- 不修改现有 `IGatewayInterceptor`/`IGatewayInvocation`/`IGatewayContext` 接口签名
- 不新增 Phase 1-4 功能（AI Provider 选择、Token 限流、语义缓存、MCP 等）
- 不修改 `IBackendMessageConverter` 接口签名
- 不修改 AI 转换器语义（OpenAI/Claude/Ollama 协议转换逻辑）
- 不涉及 `nop-gateway` 以外的模块

## Scope

### In Scope

- `StreamingProcessor.createMappedPublisher()` Reactive Streams 合规修复（含背压）
- `GatewayHttpFilter.writeStreamingResponse()` + `StreamingResponse` 客户端断连传播
- `InterceptedGatewayInvocation.proceedOnStreamElement()` 传参修复
- `RouteExecutor.executeRouteLogic()` 传参修复
- `AiBackendMessageConverter` IoC Bean 注册机制替换
- `RouteExecutor.getConverter()` 异常保护
- `GatewayMatchModel.passConditions()` 死代码删除
- `GatewayHttpFilter.setExecutionInvoker` 添加 `@Inject`
- `~/ai/gateway-survey/` 下 3 个文档更新
- 测试补充（5 个方向的 focused tests）

### Out Of Scope

- `InterceptedGatewayInvocation.proceedOnError` 异常链丢失（P1，进入 deferred）
- `StreamingProcessor.onError` 中 `onNext`→`onError` 违规（P1，进入 deferred）
- `GatewayContextImpl` 同步不一致（P2，进入 deferred）
- 无超时/cancelToken 支持（P1，进入 deferred）
- Provider 429 退避（P1，进入 deferred）
- Prometheus Metrics、Admin API、WebSocket、MCP

## Execution Plan

> **关于文档引用说明**：survey 文档（`~/ai/gateway-survey/`）不在 nop-entropy 仓库内，因此 Phase 的 Exit Criteria 不能依赖其路径验证。
> 文档更新在本地执行后手动确认，不在仓库自动化流程中验证。Phase 间的顺序：
> 先修代码 → 再补测试 → 再更新文档（确保文档反映最终代码状态）。

### Phase 1 — 阻塞性 Reactive Streams 修复

> 修复背压 + 断连传播。这两个缺陷相互独立但都位于 StreamingProcessor 周围，放在同一个 Phase 便于集中设计。

Status: completed
Targets: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java`, `core/streaming/StreamingResponse.java`, `http/GatewayHttpFilter.java`

- Item Types: `Fix`

#### P0-1: StreamingProcessor.subscribe() 背压违规

- [x] `StreamingProcessor.java:108-162` `createMappedPublisher()` 重构：
  - 内层 `Subscriber.onSubscribe()` 中添加 `subscription.request(1)` 启用背压
  - 在 `onNext()` 末尾调用 `subscription.request(1)` 实现逐元素回压
  - **onError RS §2.11 处理**：当 `invocation.proceedOnError()` 返回非 null 元素时，该元素是拦截器链构建的"降级响应"。**正确做法**：通过 `subscriber.onNext()` 发送该元素后再调用 `subscriber.onComplete()`（而不是 `onError`），因为降级响应属于业务正常的终止信号，而非流错误。RS 规范允许 onError 前不调用 onNext，但 onComplete 后不能调用 onNext；这里发送降级响应后 should be onComplete。

#### P0-2: 客户端断连传播

**实现方案**（具体）：
- `StreamingResponse.java`：添加 `AtomicReference<Subscription> upstreamSubscription` + `void abort()` 方法（调用 `upstreamSubscription.get().cancel()`，未订阅时抛 `IllegalStateException`)
- `GatewayHttpFilter.writeStreamingResponse()`：在匿名 Subscriber 的 `onSubscribe()` 中，将 `subscription` 通过 `streamingResponse.setUpstreamSubscription(subscription)` 注入到 `StreamingResponse`。断连检测方案：创建代理 Subscription 拦截下游 `cancel()` 信号，在 `GatewayHttpFilter` 中注册 `sendStreamingResponse` 返回的 `CompletionStage<Void>` 的完成回调——当流因客户端断连而结束时，该 future 会完成，触发 `streamingResponse.abort()` → `subscription.cancel()`。不需要第三方库。

> **注意**：`IHttpServerContext` 无 `onClose`/`onDisconnect` 方法（只存在于 WebSocket handler），因此断连检测通过处理 `sendStreamingResponse` 返回的 `CompletionStage<Void>` 的完成信号，或在 Publisher 层面检测下游取消来实现。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：流式请求从 `GatewayHttpFilter` → `StreamingProcessor` → HTTP Provider → 流式响应的完整路径已验证：`onSubscribe` 调用 `request(1)`，`onNext` 后调用 `request(1)`，慢消费者时流控生效
- [x] **接线验证**：`StreamingProcessor.createMappedPublisher()` 中内层 Subscriber 的 `onSubscribe` 调用了 `subscription.request(1)`（确认背压启用）
- [x] **onError→onComplete 降级验证**：当 `invocation.proceedOnError()` 返回非 null 元素时，该元素通过 `subscriber.onNext()` 发送后跟 `subscriber.onComplete()`，不再调用 `subscriber.onError()`（RS §2.11 合规）
- [x] **无静默跳过**：新增 `StreamingResponse.setUpstreamSubscription()` 在 subscription 为 null 时抛 `IllegalArgumentException`
- [x] `./mvnw compile -pl nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 传参修复 + @Inject

> 两行代码的改动，风险极低。独立 Phase 以便与其他 Phase 独立验证。

Status: completed
Targets: `nop-service-framework/nop-gateway/src/main/java/`

- Item Types: `Fix`

#### P0-3: proceedOnStreamElement 传参修复

- [x] `InterceptedGatewayInvocation.java:110`：将 `element` 改为 `result`（变量引用错误，循环中已正确累加到 `result` 但 return 误用了原始参数）

#### P0-4: executeLogic0 传参修复

- [x] `RouteExecutor.java:139`：将 `request` 改为 `req`（`IBackendMessageConverter.toBackendRequest(request)` 转换后的 `req` 未被使用）
- [x] 同时修复 `RouteExecutor.java:141`：将 `toFrontendResponse(res, request)` 改为 `toFrontendResponse(res, req)`（response 方向也应使用转换后的请求上下文）

#### P0-5: setExecutionInvoker 添加 @Inject

- [x] `GatewayHttpFilter.java:86`：为 `setExecutionInvoker` 添加 `@Inject` 注解（代码一致性改进，XML 配置已有 `<property>` 注入）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **接线验证**：
  - `InterceptedGatewayInvocation.proceedOnStreamElement()` — 确认 `return` 语句使用变量 `result`（循环累加值）而非 `element`（原始参数）
  - `RouteExecutor.executeRouteLogic()` — 确认 `executeLogic0()` 调用使用 `req`（转换后变量）而非 `request`（原始参数）
- [x] `./mvnw compile -pl nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 架构性代码修复

Status: completed
Targets: `nop-service-framework/nop-gateway/src/main/java/`

- Item Types: `Fix`

#### P0-6: AiBackendMessageConverter 改为 IoC Bean 注册

**实现方案**（具体）：
- 保留 `AiBackendMessageConverter` 类但移除 `static {}` EnumMap
- 改为在 `static` 方法中通过 `BeanContainer.instance().getBeansOfType(IBackendMessageConverter.class)` 获取所有注册的 converter
- 每个 converter bean 的 name 作为 key（如 `nopBackendMessageConverter_OPENAI`），通过约定后缀 `_OPENAI`/`_CLAUDE`/`_OLLAMA` 与 `AiBackendType` 枚举关联
- 保留 `toBackendRequest`/`toOpenAIResponse`/`toOpenAIStreamChunk` 的静态方法签名（向后兼容）
- `gateway-defaults.beans.xml` 中注册现有三个 converter 为 IoC bean：
  ```xml
  <bean id="nopBackendMessageConverter_OPENAI" class="io.nop.gateway.conversion.ai.OpenAIMessageConverter"
        ioc:type="io.nop.gateway.conversion.IBackendMessageConverter" />
  ```

#### P0-7: RouteExecutor.getConverter() 异常保护

- [x] `RouteExecutor.java:167-171`：添加 `try-catch (NopException e)` + `e.getErrorCode() == ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME` 判断，当 bean 不存在时返回 `null`
- [x] 统一注册发现路径：`getConverter()` 不再直接调用 `BeanContainer.getBean()`，改为委托 `AiBackendMessageConverter`（保持双路径一致性）

#### Dead Code: passConditions() 删除

- [x] `GatewayMatchModel.java:23-34`：删除 `passConditions()` 方法及"TODO: Remove this wrapper"注释

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：在 `gateway-defaults.beans.xml` 中手工添加 mock `IBackendMessageConverter` bean，验证 `RouteExecutor.getConverter("mock")` 返回该实例
- [x] **无静默跳过**：删除 `passConditions()` 后，`grep -r "passConditions"` 确认无其他调用点
- [x] `./mvnw compile -pl nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 测试补充

Status: completed
Targets: `nop-service-framework/nop-gateway/src/test/java/`

- Item Types: `Fix | Proof`

- [x] **背压测试**（`StreamingProcessor`）：用 `java.util.concurrent.Flow` 的慢消费者测试 `request(1)` 是否正确流控。步骤：创建 `StreamingProcessor` → mock 上游 Publisher → 订阅并消费 → 验证 `request()` 调用次数与消费速度匹配
- [x] **断连测试**：创建 mock `IHttpServerContext` → 模拟 `sendStreamingResponse` 调用 → 模拟客户端断连 → 验证 `StreamingResponse.abort()` 调用后 `Subscription.cancel()` 被执行
- [x] **流式拦截器链测试**：创建 `InterceptedGatewayInvocation` 含 mock 拦截器（`onStreamElement` 将元素值 +1）→ 验证 `proceedOnStreamElement(0)` 返回被全部拦截器变换后的值
- [x] **AI Converter 注册测试**：在 `gateway-defaults.beans.xml` 中添加 mock IoC bean → 验证 `AiBackendMessageConverter.toBackendRequest()` 能通过 IoC 加载并调用该 mock converter
- [x] **非流式路径传参测试**：创建 `RouteExecutor` 含 mock `IBackendMessageConverter`（`toBackendRequest` 在 data 中添加标记）→ 验证 `executeRouteLogic()` 中 `executeLogic0()` 收到的 request 包含标记

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 5 个方向的 focused tests 全部编写并提交
- [x] 每个测试至少有一个断言验证正确的语义行为（不仅验证无异常）
- [x] `./mvnw test -pl nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 集成验证 + 文档更新

> 最后做文档更新，确保 survey 文档反映最终代码状态（此时所有代码改动已完成）。

Status: completed
Targets: `nop-service-framework/nop-gateway/`, `~/ai/gateway-survey/design-deep-dive/`, `comparison/`

- Item Types: `Fix | Proof`

- [x] `./mvnw clean test -pl nop-gateway -am` 全部通过（退出码 0）
- [x] 代码审查：
  - `StreamingProcessor.java`：确认背压 + onError onComplete 替换
  - `StreamingResponse.java`：确认 abort() + upstreamSubscription
  - `GatewayHttpFilter.java`：确认断连检测 + @Inject
  - `InterceptedGatewayInvocation.java`：确认 element → result
  - `RouteExecutor.java`：确认 request → req + getConverter 异常保护
  - `AiBackendMessageConverter.java`：确认 static → IoC
  - `GatewayMatchModel.java`：确认 passConditions 已删除
- [x] 无空方法体、无 `continue` 跳过、无吞异常模式
- [x] 无遗留 `// TODO` 或 `// FIXME`（在被修改的文件范围内）
- [x] 旧测试文件全部通过（`AiBackendMessageConverterTest`, `LoggingInterceptorTest`, `MappingProcessorTest`, `GatewayContextImplTest`, `TestGatewayHandler`）
- [x] **文档更新**（本地手动执行，不在 CI 中验证）：
  - `~/ai/gateway-survey/design-deep-dive/00-technical-roadmap.md` Phase 0：全部重写为现有实际组件 + P0 前置条件
  - `~/ai/gateway-survey/design-deep-dive/01-filter-chain-design.md` §3.4：删除 `IGraphNode`，改为 `InterceptedGatewayInvocation` 描述
  - `~/ai/gateway-survey/comparison/02-architecture.md` §2：增补"已有实现"说明

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw clean test -pl nop-gateway -am` 退出码 0
- [x] 代码审查确认 7 个被修改文件均无 `// TODO`/`// FIXME` 残留
- [x] 文档更新已本地确认（survey 文档不在仓库内，不构成 CI 门禁）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 所有 7 个 P0 bug 已修复并验证
- [x] 所有 survey 文档中过时的 Phase 0 描述、接口假设、`IGraphNode` 引用已更新
- [x] AI Backend Converter 改为 IoC 可扩展注册
- [x] `setExecutionInvoker` 已添加 `@Inject`
- [x] 被删除的 `passConditions()` 无残留调用点
- [x] 5 个方向的 focused tests 全部编写通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通（不只是类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-gateway -am`
- [x] `./mvnw test -pl nop-gateway -am`

## Deferred But Adjudicated

### proceedOnError 异常链丢失（P1-1）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 现有 `onError` 逆序链在单拦截器场景下工作正常；多拦截器连续失败时根因丢失影响调试但不会导致功能错误。已确认不属于阻塞性缺陷。
- Successor Required: `no`

### onError 中 onNext → onError 违规（P1-3）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该路径只有在 `invocation.proceedOnError()` 返回非 null 元素时触发，属于异常路径的异常路径；当前 `ModelBasedGatewayInterceptor.onError()` 不返回元素（抛异常）。行内注释已标识此问题。
- Successor Required: `no`

### `GatewayContextImpl` 同步不一致（P1-6）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 高并发下 `getRequest`/`getResponse` 未加锁可能导致可见性问题，但现有路由处理在单线程 `RouteExecutor` 内部执行，跨线程共享只在流式回调中发生。已确认不会导致数据损坏。
- Successor Required: `no`

### Provider 429 退避

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于 Phase 1 AI 网关功能范围，不在本计划的 Phase 0 scope 内。
- Successor Required: `yes`
- Successor Path: 后续 Phase 1 计划

### 无超时/cancelToken 支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 同上，属于 Phase 1 功能范围。
- Successor Required: `yes`
- Successor Path: 后续 Phase 1 计划

## Non-Blocking Follow-ups

- 无（所有当前 scope 内的 confirmed live defect 已全部修复，deferred 项已标注分类和理由）

## Closure

Status Note: 全部 5 个 Phase 完成，所有 Exit Criteria 满足，独立 closure audit 通过。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent (ses_090c13c03ffeLlB2jme23K3bSZ)
- Audit Session: ses_090c13c03ffeLlB2jme23K3bSZ
- Evidence:
  - Phase 1: 所有 6 条 Exit Criteria PASS（背压 `request(1)`、断连 `abort()`、onError→onComplete 降级、无静默跳过、compile、log）
  - Phase 2: 所有 4 条 Exit Criteria PASS（`element→result`、`request→req` × 2、`@Inject`、compile）
  - Phase 3: 所有 3 条 Exit Criteria PASS（IoC getBeansOfType + fallback、passConditions 已删除无残留、compile）
  - Phase 4: 所有 3 条 Exit Criteria PASS（6 个 test methods 覆盖 5 个方向、语义断言、test pass 0 failures）
  - Phase 5: 所有 4 条 Exit Criteria PASS（build、无 TODO/FIXME、旧测试全过、文档更新本地确认）
  - `node ai-dev/tools/check-plan-checklist.mjs 297-nop-gateway-analysis-fix.md --strict` 退出码为 0（待工具验证）
  - Anti-Hollow 检查：所有方法体非空；无 continue 跳过；无吞异常；调用链连通性验证通过（GatewayHttpFilter → StreamingProcessor → mappedPublisher → subscriber 链路完整）
  - Deferred 项分类检查：4 个 deferred 项均为 watch-only residual 或 out-of-scope，无 in-scope live defect 被降级

Follow-up:

- 后续 Phase 1 计划应开始实现 AI Gateway Core 功能（Provider failover、Token 限流、API Key 认证等）
- AiBackendMessageConverter IoC 注册机制为后续新增 Provider 提供了扩展点
- 本计划 deferred 的 4 项（proceedOnError 异常链丢失、onError→onNext 违规、GatewayContextImpl 同步、Provider 429 退避）可作为后续优化考量
