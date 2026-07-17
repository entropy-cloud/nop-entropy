# nop-ai-gateway 设计

**日期**：2026-07-17（更新于 2026-07-17）
**范围**：新增 `nop-ai-gateway` 模块，使 nop-gateway 的 AI 格式转换委托到 nop-ai-core 的 ILlmDialect 系统
**状态**：草案

---

## 一、设计结论

1. 新增 `nop-ai-gateway` 模块，依赖 `nop-gateway` + `nop-ai-api` + `nop-ai-core`
2. 实现 `AiDialectBackendMessageConverter`，通过 `LlmDialectFactory.getDialect(apiStyle)` 委托格式转换
3. 网关路由通过 `backendMessageConverter=AI_DIALECT` 配置选择 dialect 模式
4. 删除 `nop-gateway` 中全部 5 个手写 `MessageConverter`，统一由 `AiDialectBackendMessageConverter` + `ILlmDialect` 替代
5. `nop-gateway` 不增加对 `nop-ai-*` 的依赖

## 二、背景与动机

`nop-ai-core` 已有 `ILlmDialect` 系统（OpenAI/Anthropic/Gemini/Ollama 四个完整 dialect + `LlmDialectFactory`），而 `nop-gateway` 手写了相同的格式转换逻辑（`ClaudeMessageConverter` 115 行、`GeminiMessageConverter` 187 行）。两套代码维护相同功能且缺少 thinking/tool-call 支持。

## 三、核心设计

### 模块依赖

```
nop-gateway (IBackendMessageConverter, AiBackendType)
    ↑
nop-ai-gateway (AiDialectBackendMessageConverter)
    ↑
nop-ai-api (ChatRequest, ChatResponse, ChatStreamChunk, ChatMessage)
    ↑
nop-ai-core (ILlmDialect, LlmDialectFactory, ApiStyle, ChatServiceImpl)
```

### ILlmDialect 双向转换

每个 dialect 提供标准内部模型（ChatRequest/ChatResponse/ChatStreamChunk）与自身 Provider 格式之间的双向转换：

```
buildBody(ChatRequest) → Map         ← ChatRequest → Provider请求体（已有）
parseRequestBody(Map) → ChatRequest  ← Provider请求体 → ChatRequest（新增）

parseResponse(String) → ChatResponse  ← Provider响应 → ChatResponse（已有）
buildResponse(ChatResponse) → Map     ← ChatResponse → 前端响应（新增）

parseStreamChunk(String) → Chunk     ← Provider流 → ChatStreamChunk（已有）
buildStreamChunk(Chunk) → Map        ← ChatStreamChunk → 前端delta（新增）
```

默认的 `buildResponse`/`buildStreamChunk` 产生 OpenAI 格式（通用网关前端格式）。

### AiDialectBackendMessageConverter 数据流

```
toBackendRequest():
  1. frontendLlm.parseRequestBody(request.data) → ChatRequest
  2. backendLlm.buildBody(chatRequest, config, model, stream) → Provider请求体
  3. 返回 ApiRequest〈Provider请求体〉

toFrontendResponse():
  1. backendLlm.parseResponse(backendResponse, config) → ChatResponse
  2. frontendLlm.buildResponse(chatResponse) → 前端格式响应Map
  3. 返回 ApiResponse〈前端格式Map〉

toFrontendStreamChunk():
  1. backendLlm.parseStreamChunk(backendDelta) → ChatStreamChunk
  2. frontendLlm.buildStreamChunk(chunk) → 前端delta Map
  3. 返回 delta Map
```

### 配置方式

通过 IoC bean 属性配置 `frontendLlm` 和 `backendLlm`，支持任意两端格式：

| 场景 | frontendLlm | backendLlm | 效果 |
|------|-------------|------------|------|
| 客户端OpenAI→后端Anthropic | openai | anthropic | OpenAI请求→Anthropic请求，响应自动反转 |
| 客户端OpenAI→后端Gemini | openai | gemini | OpenAI请求→Gemini请求 |
| 客户端OpenAI→后端Ollama | openai | ollama | OpenAI请求→Ollama请求 |
| 透传 | openai | openai | 不转换，直接透传 |

### AiDialectBackendMessageConverter 注册路径

nop-gateway 的 `RouteExecutor.getConverter()` 通过 bean name 查找：
`"nopBackendMessageConverter_" + route.getBackendMessageConverter()`

当配置写 `backendMessageConverter=AI_DIALECT`，查找 `nopBackendMessageConverter_AI_DIALECT` bean。
该 bean 在 `nop-ai-gateway` 模块的 `ai-gateway-defaults.beans.xml` 中注册，配置了 `frontendLlm=openai` 和 `backendLlm=openai` 默认值。

不需要 `AiBackendType` 枚举参与（枚举已用于 `FALLBACK_CONVERTERS` 的 IoC 不可用回退）。
`RouteExecutor` 不经过 `AiBackendMessageConverter` 工厂，直接通过 `BeanContainer.tryGetBean()` 获取。

## 四、拒绝了什么

### 方案 A：nop-gateway 直接依赖 nop-ai-core
**拒绝**。nop-gateway 保持零 AI 依赖，轻量可嵌入。

### 方案 B：废弃 IBackendMessageConverter 接口
**拒绝**。接口在 `RouteExecutor` 中有硬编码调用，且外部可能已使用。保持稳定。

### 方案 C：nop-ai-core 增加 gateway 适配
**拒绝**。nop-ai-core 不应感知网关。

### 方案 D：保留手写 Converter 不动
**拒绝**。已全部删除，由 `AiDialectBackendMessageConverter` + `ILlmDialect` 统一替代。

## 五、与已有设计的关系

| 模块/文档 | 关系 |
|---|---|
| `nop-gateway` | 提供 `IBackendMessageConverter` 接口和 `RouteExecutor` 调用点 |
| `nop-ai-api` | 提供 `ChatRequest`/`ChatResponse`/`ChatStreamChunk`/`ChatMessage` 类型 |
| `nop-ai-core` | 提供 `ILlmDialect`/`LlmDialectFactory`/`ApiStyle`/`ChatServiceImpl` |
| `ai-dev/plans/298-nop-gateway-ai-core.md` | 本设计将替代 Plan 298 中手写 Gemini Converter 的部分 |
| `ai-dev/plans/299-nop-ai-gateway-module.md` | 本设计的实现计划 |
