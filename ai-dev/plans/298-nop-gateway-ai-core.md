# 298 Nop Gateway AI Gateway Core 基础能力

> Plan Status: completed
> 注意：本 plan 在对抗性审查后发现 6 个 Blocker + 4 个 Major 问题，以下已全部修复。
> 核心修正：拦截器配置改为 IoC bean 模式（gateway.xdef 无 `<config>` 字段），
> 包路径改为 `core.interceptor`，合并 Phase 1+3 的 InvokeProcessor 重复工作，
> 重写 failover 方案为 RouteExecutor 级增强而非拦截器级。
> Last Reviewed: 2026-07-17
> Source: `~/ai/gateway-survey/analysis/03-gap-analysis.md` §5.3 + §6（中期行动项）
> Related: `ai-dev/plans/297-nop-gateway-analysis-fix.md`

## Purpose

在现有 `nop-gateway` 的 Filter Chain + Streaming + Backend Converter 基础上，实现 AI Gateway 最核心的生产必需能力：
Provider 故障切换、API Key 认证、Token 级限流、Provider 429 退避。使 `nop-gateway` 在非流式 AI 场景下具备多 Provider
容错和访问控制能力，达到可用于生产最小集。

## Current Baseline

- `IBackendMessageConverter` + 3 个 Provider 转换器（OpenAI/Claude/Ollama）已存在，通过 IoC 注册（Plan 297）
- `IGatewayInterceptor` 拦截器链已存在，支持 `onRequest`/`onResponse`/`onError`/`invoke` 包装模式
- `InvokeProcessor` 支持 source/RPC/URL 三种调用方式，但 **无失败重试 + 无 429 退避**
- `InterceptedGatewayInvocation` 支持正序/逆序链
- **拦截器是全局的**（`gateway.xdef` 中 `interceptors` 是 `GatewayModel` 的直接子元素，非 `GatewayRouteModel` 子元素），按路径匹配生效，无法按路由配置独立拦截器
- **拦截器配置不支持任意 `<config>` 字段**（`GatewayInterceptorModel` 无 `config` 属性）。配置需要通过 IoC bean 属性注入
- `AiBackendType` 枚举只有 `OPENAI/OLLAMA/CLAUDE`，缺 `DEEPSEEK`/`GEMINI`
- **缺失**：Provider 故障切换（非流式场景：HTTP URL 调用失败时自动重试到备用 Provider）
- **缺失**：API Key 认证拦截器
- **缺失**：Token 级/请求级限流拦截器
- **缺失**：Provider 429 响应的 Retry-After 解析 + 退避算法
- **缺失**：更多 AI Provider 适配（仅 3 个）

## Goals

- [x] `InvokeProcessor.invokeUrl()` 增强容错 — 支持 429 Retry-After 退避 + 失败时抛异常包含 HTTP 状态码供上层拦截器捕获
- [x] 新增 `AiFailoverGatewayInterceptor` — `invoke()` 包装模式：捕获 `InvokeProcessor` 抛出的异常，使用 `IHttpClient` 直接调用备用 URL
- [x] 新增 `AiRateLimitGatewayInterceptor` — 本地 Token Bucket 限流
- [x] 新增 `AiAuthGatewayInterceptor` — API Key 认证（静态 Key 校验）
- [x] `AiBackendType` 枚举增加 `DEEPSEEK`/`GEMINI` + 新增 2 个 Provider 适配
- [x] `./mvnw test -pl nop-service-framework/nop-gateway -am` 全部通过

## Non-Goals

- 不改 `StreamingProcessor` 的流式故障切换（已在 Phase 0 Plan 297 中作为 deferred 项保留，本计划仅覆盖非流式 failover）
- 不实现分布式限流（仅本地内存桶，不引入 Redis 依赖）
- 不实现 Token 计费/成本追踪（保留给后续 plan）
- 不实现语义缓存、MCP 代理、Admin API
- 不改 `IGatewayInterceptor` 接口签名

## Scope

### In Scope

- `io.nop.gateway.core.interceptor.AiFailoverGatewayInterceptor` — `invoke()` 包装模式：调用失败时使用 `IHttpClient` 直接重试备用 URL
- `io.nop.gateway.core.interceptor.AiRateLimitGatewayInterceptor` — 本地 Token Bucket 限流
- `io.nop.gateway.core.interceptor.AiAuthGatewayInterceptor` — API Key 校验
- `InvokeProcessor.invokeUrl()` 增强 — 解析 `Retry-After` header + 指数退避；失败时抛 `NopException` 携带 HTTP 状态码和响应体
- `AiBackendType` 增加 `DEEPSEEK`/`GEMINI` + `FALLBACK_CONVERTERS` 对应更新
- 新增 `DeepSeekMessageConverter` + `GeminiMessageConverter`（Io C bean 注册）
- 测试配置（test 资源目录下新增 `test-ai-routes.gateway.xml` 含全局拦截器配置）

### Out Of Scope

- 流式 Provider 故障切换（deferred，见 Plan 297）
- 集群级分布式限流（需要 Redis，后续 Phase 2）
- Token 计量与成本追踪（保留给后继 plan）
- 语义缓存、MCP 代理、Admin API

## Execution Plan

### Phase 1 — InvokeProcessor 429 退避 + 新增 AiBackendType

> 先增强 `InvokeProcessor.invokeUrl()` 的容错性：解析 429 Retry-After、指数退避、失败时抛异常携带 HTTP 状态码。
> 同时扩展 `AiBackendType` 枚举为新增 Provider 做准备。本 Phase 是 Phase 2（failover）的前置条件。

Status: completed
Targets: `InvokeProcessor.java`, `AiBackendType.java`, `AiBackendMessageConverter.java`, `gateway-defaults.beans.xml`

- Item Types: `Fix`

**InvokeProcessor 增强**：
- 新增 `GatewayErrors.ERR_GATEWAY_UPSTREAM_429` + `ERR_GATEWAY_UPSTREAM_FAILED` 两个 ErrorCode
- `invokeUrl()` 收到 HTTP 429 时：解析 `Retry-After` header（支持秒数格式和 HTTP-date 格式）
- 实现指数退避：`delay = min(retryAfter, baseDelay * 2^retryCount) + random(0, 1000ms)` 
- 重试上限 3 次，超过后返回 `ERR_GATEWAY_UPSTREAM_429` 异常（携带原始响应），上层拦截器可捕获后 failover
- 其他 5xx 错误：返回 `ERR_GATEWAY_UPSTREAM_FAILED` 异常（携带 HTTP 状态码 + 响应体）

**AiBackendType 扩展**：
- 新增 `DEEPSEEK` / `GEMINI` 枚举常量
- `AiBackendMessageConverter.FALLBACK_CONVERTERS` 中添加两个新条目（先使用 placeholder 简单实现，Phase 3 替换为完整转换器）
- `gateway-defaults.beans.xml` 中添加 placeholder bean 定义

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `InvokeProcessor.invokeUrl()` 在收到 429 时解析 `Retry-After` + 退避重试（最多 3 次）
- [x] 超过重试上限后抛出 `NopException` 携带原始 HTTP 状态码和响应体
- [x] `AiBackendType` 包含 `DEEPSEEK` / `GEMINI`
- [x] **无静默跳过**：5xx 错误不静默忽略，抛异常供上层捕获
- [x] `./mvnw compile -pl nop-service-framework/nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — AiFailoverGatewayInterceptor + AiRateLimitGatewayInterceptor + AiAuthGatewayInterceptor

> 三个拦截器都注册为全局 IoC bean，通过 `gateway.xdef` 中 `<interceptor bean="..."/>` 引用。
> 拦截器配置通过 bean 属性注入（`gateway.xdef` 不支持任意 `<config>` 字段）。

Status: completed
Targets: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/`

- Item Types: `Fix`

**设计说明**：
- 拦截器是**全局**的（`gateway.xdef` 中 `interceptors` 是 `GatewayModel` 的直接子元素）
- 每个拦截器通过 `bean="..."` 引用全局 IoC bean
- 拦截器通过 `match` 条件（`path`/`when` XPL 表达式）过滤生效路由
- 三个拦截器在 `gateway-defaults.beans.xml` 中注册，带 IoC 条件开关

#### AiFailoverGatewayInterceptor

- 实现 `IGatewayInterceptor.invoke()` 包装模式
- 在 `invoke()` 中调用 `invocation.proceedInvoke()`，如果抛出 `ERR_GATEWAY_UPSTREAM_FAILED` 或 `ERR_GATEWAY_UPSTREAM_429`，使用注入的 `IHttpClient` 直接调用备用 URL
- 备用 URL 列表通过 `@Inject` setter 注入（bean 属性）
- 每个备用尝试使用 `ApiRequest<?>` 副本（`request.clone()` 或重新构建）
- 流式请求（`context.isStreamingMode()` 为 true）→ 抛 `UnsupportedOperationException("Streaming failover not yet implemented")`
- 使用 `LoggingInterceptor` 同样的 Logger 模式记录 failover 尝试日志

**IoC bean 配置**：
```xml
<bean id="nopAiFailoverGatewayInterceptor" 
      class="io.nop.gateway.core.interceptor.AiFailoverGatewayInterceptor"
      ioc:default="true">
    <property name="fallbackUrls">
        <list>
            <value>https://api.anthropic.com/v1/messages</value>
            <value>https://api.deepseek.com/v1/chat/completions</value>
        </list>
    </property>
    <property name="maxRetries" value="3" />
</bean>
```

**全局拦截器配置**（在 `app.gateway.xml` 中）：
```xml
<gateway>
    <interceptors>
        <interceptor bean="nopAiAuthGatewayInterceptor">
            <match>
                <path>/v1/chat/completions</path>
            </match>
        </interceptor>
        <interceptor bean="nopAiRateLimitGatewayInterceptor">
            <match>
                <path>/v1/chat/completions</path>
            </match>
        </interceptor>
        <interceptor bean="nopAiFailoverGatewayInterceptor">
            <match>
                <path>/v1/chat/completions</path>
            </match>
        </interceptor>
    </interceptors>
    <routes>
        <route id="ai-chat">
            <match>
                <path>/v1/chat/completions</path>
                <httpMethod>POST</httpMethod>
            </match>
            <invoke>
                <url>https://api.openai.com/v1/chat/completions</url>
            </invoke>
        </route>
    </routes>
</gateway>
```

#### AiRateLimitGatewayInterceptor

- 本地 Token Bucket，基于 `ConcurrentHashMap<String, TokenBucket>` + `ScheduledExecutorService`
- Bucket 参数通过 bean 属性注入：`capacity`、`refillRate`、`refillPeriod`
- 限流 key：`clientIp`（从 `IGatewayContext` 获取），或自定义策略
- 超出限流 → `onResponse` 中返回 `ApiResponse` 带 429 状态码
- 注意：限流逻辑在 `onRequest` 阶段执行（请求进入时），前置检查

#### AiAuthGatewayInterceptor

- `onRequest` 中检查 `Authorization: Bearer <key>` header
- 有效 key 列表通过 `@Inject` setter 注入
- 认证失败 → 返回 `ApiResponse` 带 401 状态码，不走 `invoke`
- 支持 `skipPaths` 白名单（bean property `skipPathPatterns`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三个拦截器类存在并实现 `IGatewayInterceptor`
- [x] **端到端验证**：
  - Failover：mock 首个 URL 返回 503，验证 fallback URL 被调用且返回正确结果
  - Auth：有效 key → 200，无效 key → 401
  - Rate-limit：超限请求 → 429
- [x] **接线验证**：`gateway-defaults.beans.xml` 中注册三个 bean，测试中通过 IoC 加载并按序执行
- [x] **无静默跳过**：流式 failover 抛 `UnsupportedOperationException`；认证失败不 fallthrough
- [x] `./mvnw compile -pl nop-service-framework/nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — DeepSeek + Gemini Provider 适配

Status: completed
Targets: `conversion/ai/DeepSeekMessageConverter.java`, `conversion/ai/GeminiMessageConverter.java`

- Item Types: `Fix`

#### DeepSeekMessageConverter

DeepSeek API 兼容 OpenAI 格式，因此转换器实现极简：
- 请求方向：`model` 映射（如 `deepseek-chat` → `deepseek-chat`，不修改也可透传）
- 响应方向：透传（格式与 OpenAI 一致）
- 核心逻辑：只需实现 `IBackendMessageConverter` 三个方法，调用 `ConverterUtils` 现有工具方法

#### GeminiMessageConverter

Google Gemini 格式与 OpenAI 差异较大，需要实现映射：
- 请求：`contents` ← `messages`（`role` + `content` 结构映射）
- 请求：`generationConfig` ← `temperature`/`max_tokens`/`top_p`
- 响应：`candidates[0].content` → `choices[0].message`
- 流式 chunk：`candidates[0].content` → `choices[0].delta`
- 参考 `ClaudeMessageConverter` 的实现模式

两个 converter 在 `gateway-defaults.beans.xml` 中注册（替换 Phase 1 中的 placeholder）：
```xml
<bean id="nopBackendMessageConverter_DEEPSEEK" 
      class="io.nop.gateway.conversion.ai.DeepSeekMessageConverter"
      ioc:type="io.nop.gateway.conversion.IBackendMessageConverter" />
<bean id="nopBackendMessageConverter_GEMINI"
      class="io.nop.gateway.conversion.ai.GeminiMessageConverter"
      ioc:type="io.nop.gateway.conversion.IBackendMessageConverter" />
```

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DeepSeekMessageConverter` 实现 `IBackendMessageConverter`，通过 IoC 加载
- [x] `GeminiMessageConverter` 实现 `IBackendMessageConverter`，通过 IoC 加载
- [x] **端到端验证**：`AiBackendMessageConverter.toBackendRequest(req, DEEPSEEK)` 返回非 null 结果
- [x] `./mvnw compile -pl nop-service-framework/nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 测试补充 + 集成验证

Status: completed
Targets: `nop-service-framework/nop-gateway/src/test/java/io/nop/gateway/core/interceptor/`

- Item Types: `Fix | Proof`

- [x] `AiFailoverGatewayInterceptorTest` — mock `IHttpClient` 验证 failover 链
- [x] `AiRateLimitGatewayInterceptorTest` — 验证桶满后请求被限流、正常请求通过
- [x] `AiAuthGatewayInterceptorTest` — 验证有效 key 通过、无效 key 拒绝
- [x] `InvokeProcessorRetryTest` — 验证 429 Retry-After 退避
- [x] `DeepSeekMessageConverterTest` — 验证 OpenAI ↔ DeepSeek 格式转换
- [x] `GeminiMessageConverterTest` — 验证 Gemini ↔ OpenAI 格式转换
- [x] `./mvnw clean test -pl nop-service-framework/nop-gateway -am` 全部通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 6 个测试类全部编写并提交
- [x] 每个测试至少有一个断言验证正确的语义行为
- [x] `./mvnw test -pl nop-service-framework/nop-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Provider failover 在非流式场景下可用（primary 失败 → fallback 返回正确结果）
- [x] API Key 认证 + 限流可组合使用
- [x] Provider 429 退避生效
- [x] DeepSeek + Gemini Provider 适配可用
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通（不只是类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-service-framework/nop-gateway -am`
- [x] `./mvnw test -pl nop-service-framework/nop-gateway -am`

## Deferred But Adjudicated

### 流式 Provider 故障切换

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 流式 failover 涉及已发送 chunk 无法撤回的语义问题，需要更深入的设计（与 StreamingProcessor 重构联动）。本计划仅覆盖非流式场景。
- Successor Required: `yes`
- Successor Path: 后续 StreamingProcessor 重构 plan

### 分布式限流（Redis 集成）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 单实例本地限流对于生产环境最小集已够用。分布式限流需要引入 Redis 依赖，属于 Phase 2 范畴。
- Successor Required: `no`（可按需从本地桶升级）

### Token 计量与成本追踪

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Token 计费需要 ORM 持久化和计费策略设计，属于独立功能模块。当前拦截器设计预留了 `onStreamComplete` 钩子，后续可插入计费逻辑。
- Successor Required: `yes`
- Successor Path: 后续 Phase 2 plan

## Non-Blocking Follow-ups

- 无（所有当前 scope 内的 confirmed live defect 已全部纳入 Phase 1-3）

## Closure

Status Note: 全部 4 个 Phase 已完成并通过验证。InvokeProcessor 429 退避（含 HTTP-date 解析）+ 异常携带 httpStatus/responseBody；三个 AI 拦截器（Failover/RateLimit/Auth）已实现并通过端到端测试；DeepSeek（OpenAI 兼容透传）+ Gemini（完整 messages↔contents 映射）转换器已实现并注册为 IoC bean；6 个测试类共 9 个新增测试用例全部通过。`FALLBACK_CONVERTERS` 不一致问题已修复。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session)
- Audit Session: opencode subagent — task_id `ses_08f75971effeCB62DttKpDqTKB` (`298-closure-audit`)
- Evidence:
  - Phase 1 Exit Criteria: PASS
    - `InvokeProcessor.invokeUrl()` 429 Retry-After 解析（秒数 + HTTP-date）见 `InvokeProcessor.java:201-222`；重试上限 3 见 `invokeUrl:134`；行为测试 `InvokeProcessorRetryTest.invokeUrl_429exhausted_throwsWithStatusAndBody` / `parseRetryAfter_httpDate_returnsPositiveDelta` 全绿
    - 异常携带 httpStatus + responseBody：`InvokeProcessor.java:170-179`（`.param("responseBody", httpResponse.getBody())`），断言见 `InvokeProcessorRetryTest:155-157`
    - `AiBackendType` 含 DEEPSEEK/GEMINI：`AiBackendType.java:7-8`
    - 无静默跳过：5xx 抛 `ERR_GATEWAY_UPSTREAM_FAILED`，测试 `invokeUrl_5xxexhausted_throwsWithStatusAndBody` 验证
  - Phase 2 Exit Criteria: PASS
    - 三拦截器实现 `IGatewayInterceptor`：`AiFailoverGatewayInterceptor:19`、`AiRateLimitGatewayInterceptor:13`、`AiAuthGatewayInterceptor:14`
    - 端到端：`AiFailoverGatewayInterceptorTest.primaryFails_usesFallback`（503→fallback 200）、`AiAuthGatewayInterceptorTest.invalidKey_rejects`（401）、`AiRateLimitGatewayInterceptorTest.overLimit_throwsRejection`（429）全绿
    - 接线：`gateway-defaults.beans.xml` 注册 `nopAiAuthGatewayInterceptor`/`nopAiRateLimitGatewayInterceptor`/`nopAiFailoverGatewayInterceptor` 三个 bean
    - 无静默跳过：流式 failover 抛 `UnsupportedOperationException`（`AiFailoverGatewayInterceptor:43-45`）；认证失败抛 `GatewayRejectException`
  - Phase 3 Exit Criteria: PASS
    - `DeepSeekMessageConverter` / `GeminiMessageConverter` 实现 `IBackendMessageConverter`，IoC bean `nopBackendMessageConverter_DEEPSEEK`/`_GEMINI` 注册于 `gateway-defaults.beans.xml`
    - `AiBackendMessageConverter.toBackendRequest(req, DEEPSEEK)` 返回非 null：`AiBackendMessageConverterTest.toBackendRequest_deepseek_returnsNonNull` / `toBackendRequest_gemini_returnsNonNullWithContents` 验证（`FALLBACK_CONVERTERS` 已填充）
    - Gemini 映射语义测试 `GeminiMessageConverterTest`（4 用例）全绿
  - Phase 4 Exit Criteria: PASS
    - 6 个测试类存在；新增 9 个有语义断言的用例；`./mvnw clean test -pl nop-service-framework/nop-gateway -am` → 53 tests, 0 failures
  - `node ai-dev/tools/check-plan-checklist.mjs 298-nop-gateway-ai-core.md --strict` 退出码为 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查：`scan-hollow-implementations.mjs --module nop-service-framework/nop-gateway --severity high` 退出码 0（0 critical/high findings）；调用链连通性由 `InvokeProcessorRetryTest`（InvokeProcessor→mock IHttpClient→异常）与 `AiFailoverGatewayInterceptorTest`（interceptor→proceedInvoke→IHttpClient fallback）端到端覆盖；先前 hollow 的 `InvokeProcessorRetryTest` 已重写为真实断言
  - Deferred 项分类检查：流式 failover / 分布式限流 / Token 计费均为 `out-of-scope improvement`，无 in-scope live defect 被降级

Follow-up:

- 流式 Provider 故障切换（deferred，需 StreamingProcessor 重构联动）
- 分布式限流（Redis 集成，可按需从本地桶升级）
- Token 计量与成本追踪（后续 plan）
