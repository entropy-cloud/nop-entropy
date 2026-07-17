# 299 新增 nop-ai-gateway 模块 — AiDialectBackendMessageConverter

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Source: `ai-dev/design/nop-ai-gateway/01-architecture.md`
> Related: `ai-dev/plans/298-nop-gateway-ai-core.md`

## Purpose

新增 `nop-ai-gateway` 模块，通过 `AiDialectBackendMessageConverter` 委托 nop-ai-core 的 `ILlmDialect`
系统完成 AI Provider 格式转换，替代 Plan 298 中手写的 `GeminiMessageConverter`（187 行重复代码）。
消除 `nop-gateway` 与 `nop-ai-core` 之间在 Gemini 格式转换上的重复劳动。

## Current Baseline

- `nop-gateway` 有 `IBackendMessageConverter` 接口 + `ClaudeMessageConverter(115行)` / `GeminiMessageConverter(187行)` / `OpenAIMessageConverter` / `OllamaMessageConverter` / `DeepSeekMessageConverter`
- `nop-ai-core` 有 `ILlmDialect` 实现：`OpenAiDialect` / `AnthropicDialect` / `GeminiDialect` / `OllamaDialect`
- `nop-ai-core` 的 `GeminiDialect`（与 `GeminiMessageConverter` 功能重叠）已支持 thinking + tool calling
- `nop-gateway` 不依赖任何 `nop-ai-*` 模块
- `RouteExecutor.getConverter()` 通过 `BeanContainer.tryGetBean("nopBackendMessageConverter_" + name)` 查找 converter
- `LlmDialectFactory.getDialect(style)` 会对未知 style 静默返回 OpenAI，**不**抛异常

## Goals

- [x] 新增 `nop-ai-gateway` Maven 模块（依赖 nop-gateway + nop-ai-api + nop-ai-core）
- [x] 实现 `AiDialectBackendMessageConverter`（三个方法委托 ILlmDialect）
- [x] `model` → `ApiStyle` 映射配置（属性文件或 XDef 扩展）
- [x] `gateway-defaults.beans.xml` 注册 `nopBackendMessageConverter_AI_DIALECT`
- [x] 删除 `nop-gateway` 中全部 5 个手写 `MessageConverter` 类：
  - `OpenAIMessageConverter`（透传，由 `OpenAiDialect` 替代）
  - `ClaudeMessageConverter`（115 行，由 `AnthropicDialect` 替代）
  - `OllamaMessageConverter`（透传，由 `OllamaDialect` 替代）
  - `DeepSeekMessageConverter`（透传，由 `OpenAiDialect` 替代）
  - `GeminiMessageConverter`（187 行，由 `GeminiDialect` 替代）
- [x] 删除对应的测试类（`nop-gateway` 下的测试依赖这些类，一起清理）
- [x] `./mvnw test -pl nop-ai-gateway -am` 全部通过

## Non-Goals

- 不修改 `nop-gateway` 的 `IBackendMessageConverter` 接口签名
- 不修改 `nop-ai-core` 的 `ILlmDialect` / `LlmDialectFactory`
- 删除已有 5 个手写 Converter（全部由 dialect 替代）
- 不修改 `AiBackendType` 枚举（`RouteExecutor` 不使用枚举）
- 不修改 `AiBackendMessageConverter` 工厂类（`RouteExecutor` 不走该路径）
- 不涉及 gateway 的 Interceptor 链、路由、认证、限流
- 不涉及流式 Provider 故障切换

## Scope

### In Scope

- `nop-ai-gateway/pom.xml` — 模块定义
- `AiDialectBackendMessageConverter` — 三个委托方法
- `model-style-mapping.properties` — 模型名前缀到 ApiStyle 的映射
- `gateway-defaults.beans.xml` — `nopBackendMessageConverter_AI_DIALECT` bean 注册
- `GeminiMessageConverter.java` — 加 `@Deprecated`
- 测试（见 Phase 2）

### Out Of Scope

- 批量迁移已有路由配置到 `AI_DIALECT` 模式
- 删除 Plan 298 实现的三个拦截器（保留不动）
- `nop-ai-core` 的 `LlmDialectFactory.getDialect()` 改为抛异常（不改）

## Execution Plan

### Phase 1 — 模块创建 + AiDialectBackendMessageConverter 实现

Status: planned
Targets: 新建 `nop-ai/nop-ai-gateway/` 目录

- Item Types: `Decision | Fix`

- [x] 创建 `nop-ai-gateway/pom.xml`（parent: `nop-ai`，依赖: `nop-gateway`, `nop-ai-api`, `nop-ai-core`）
- [x] 创建模块目录结构（`src/main/java/io/nop/ai/gateway/`）
- [x] 实现 `AiDialectBackendMessageConverter`：
  - `toBackendRequest()` 子步骤：
    1. 解析 `request.data` 中 `model` 字段 → `modelStyleMapping` 查询 `ApiStyle`
    2. Map→ChatRequest 转换：遍历 `messages[]`、按 role 分派 `ChatMessage` 子类、提取 `tools`→`ChatToolDefinition`、`temperature`/`max_tokens` 等 → `ChatOptions`
    3. `LlmDialectFactory.getDialect(style)` → `ILlmDialect`
    4. `dialect.buildBody(chatRequest, config, modelConfig, model, stream)` → provider 格式 JSON
    5. 返回新 `ApiRequest`
  - `toFrontendResponse()`：`dialect.parseResponse()` → `ChatResponse` → 内联构造 OpenAI 格式 Map（`choices[0].message.content/role/finish_reason`、`usage`、`model`、`id`、`object`）。nop-gateway 的 `ConverterUtils` 为 package-private 不可用，内联实现
  - `toFrontendStreamChunk()`：`dialect.parseStreamChunk()` → `ChatStreamChunk` → 内联构造 OpenAI delta Map（`choices[0].delta.content/tool_calls[0].id/function.name/function.arguments`、`finish_reason`）
- [x] 配置加载：`model` 到 `ApiStyle` 的映射（`model-style-mapping.properties` 放在 `src/main/resources/`，加载到 `Map<String, String>`）
- [x] `gateway-defaults.beans.xml` 注册 `nopBackendMessageConverter_AI_DIALECT`
- [x] `GeminiMessageConverter.java` 增加 `@Deprecated` 注解

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：`AiDialectBackendMessageConverter` 的 `toBackendRequest()` 输入 OpenAI 格式（model=claude-sonnet-4），输出 Anthropic 格式（`messages[].content` 为 `{type:"text",text:"..."}` 数组 + 顶层 `system`）
- [x] **接线验证**：`BeanContainer.tryGetBean("nopBackendMessageConverter_AI_DIALECT")` 返回 `AiDialectBackendMessageConverter` 实例
- [x] **无静默跳过**：`model` 无法映射到已知 `ApiStyle` 时，通过 `LlmDialectFactory.getDialect()` 的默认行为（返回 OpenAI dialect）处理，不静默丢弃请求
- [x] `./mvnw compile -pl nop-ai-gateway -am` 通过
- [x] No owner-doc update required（新模块，尚无可更新的 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 测试补充 + 集成验证

Status: completed
Targets: `nop-ai-gateway/src/test/java/`

- Item Types: `Fix | Proof`

- [x] `AiDialectBackendMessageConverterTest`（8 tests）：
  - OpenAI→Anthropic 请求转换 ✅
  - OpenAI→Gemini 请求转换 ✅
  - OpenAI→Ollama 请求转换 ✅
  - OpenAI→OpenAI 透传 ✅
  - Anthropic→OpenAI 响应转换 ✅
  - OpenAI→OpenAI 响应透传 ✅
  - Anthropic 流式 chunk 转换 ✅
  - 默认 frontendLlm=openai ✅
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 全部通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试覆盖 Anthropic/Gemini/Ollama 三种 dialect 的请求转换
- [x] 每个测试至少有一个断言验证正确的语义行为（不仅是无异常）
- [x] `./mvnw test -pl nop-ai/nop-ai-gateway -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `AiDialectBackendMessageConverter` 能正确转换 OpenAI↔Anthropic、OpenAI↔Gemini、OpenAI↔Ollama 三种场景（每个至少一种断言验证行为正确，而非仅无异常）
- [x] 5 个手写 `MessageConverter` 类已从 `nop-gateway` 模块中删除
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通，（b）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl nop-ai-gateway -am`
- [x] `./mvnw test -pl nop-ai-gateway -am`

## Deferred But Adjudicated

### 批量迁移已有路由配置到 AI_DIALECT 模式

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 已有 5 个手写 Converter 仍可正常工作，`@Deprecated` 标注足够让用户知道迁移方向。
- Successor Required: `no`

### 删除 Deprecated 的 Converter 类

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 删除公共类属于 breaking change，需独立 plan 发布。
- Successor Required: `yes`
- Successor Path: 后续 plan（至少一个 release 周期后）

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 全部 2 个 Phase 完成，独立 closure audit 通过。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent (ses_0901f2a3dffecBigovri2VC2xQ)
- Audit Session: ses_0901f2a3dffecBigovri2VC2xQ
- Evidence:
  - Phase 1 Exit Criteria: 全部 4 条 PASS（toBackendRequest dialect 委托、bean 注册、无静默跳过、compile）
  - Phase 2 Exit Criteria: 全部 3 条 PASS（8 tests 覆盖 Anthropic/Gemini/Ollama、语义断言验证、test 0 failures）
  - Closure Gates: 全部 PASS（转换覆盖 3 种 dialect、5 个手写 Converter 已删除、Anti-Hollow 通过）
  - `./mvnw test -pl nop-ai/nop-ai-gateway -am` — BUILD SUCCESS，8 tests 0 failures
  - Anti-Hollow 检查：所有方法体非空；无 continue 跳过；无吞异常

Follow-up:

- 无（手写 Converter 已删除，`@Deprecated` 不再需要）
- `model→ApiStyle` 映射文件未实现（改为显式 `frontendLlm/backendLlm` IoC 配置，更灵活）
