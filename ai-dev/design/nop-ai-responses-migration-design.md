# nop-ai 消息模型重构设计：ChatMessage 体系改造为拆分模型（Responses 迁移落地）

**日期**：2026-08-01
**范围**：`nop-ai-api`（消息模型重构）、`nop-ai-core`（dialect 层）、`nop-ai-agent`（引擎适配）、`nop-ai-gateway`（远期挂点）
**状态**：草案

---

## 一、设计结论

1. **对现有折叠消息模型做彻底改造，不另起新命名体系**。复用 `ChatRequest`/`ChatResponse`/`ChatMessage` 系列既有名称，改造内部结构。命名即契约：现有名称准确，弃用即浪费认知成本。
2. **模型定位：对 AI 交互模型的抽象，而非第三方 API 的封装**。`IChatService`/`ChatRequest`/`ChatResponse`/`ChatOptions` 是协议无关的领域模型，可以（且应当）承载 wire 之外的规范化信息——如 `ChatResponse` 的 `error`/`errorCode`（归一化为 Nop 风格错误码）、`requestId`/`responseTime`，`ChatRequest` 的 `requestTime`/`retryTimes`。反过来，wire 特有字段默认丢弃或归一化后才允许进入核心模型。
3. **ChatMessage 从"角色折叠"改造为"类型开放"**：`ChatAssistantMessage` 的 `think`/`toolCalls` 寄居字段删除，拆分为独立的 `ChatReasoningMessage`（推理）与 `ChatToolCallMessage`（工具调用）；删除兜底类型 `ChatCustomMessage`。新增仅限语义必需的两个类型，不引入平行概念。
4. **请求与响应统一为消息序列**：`ChatRequest.messages` 与 `ChatResponse.messages` 都是 `List<ChatMessage>`（`ChatResponse.message` 单条改造为 `messages` 列表）。上下文回放 = `request.messages.addAll(response.messages)`，零转换。输入输出共用一套消息类型，不存在 OpenAI `input_text`/`output_text` 双命名问题。
5. **多模态成为一等公民**：`ChatUserMessage` 扩展 content parts（text/image/audio），根治现有 `attachments` 无方言序列化的问题。
6. **流式改造为 item 增量**：`ChatStreamChunk` 复用名称，改造为携带 item 上下文（itemType/itemIndex/phase: ADDED|DELTA|DONE）的增量模型，事件流 → chunk 状态机是唯一解析路径。
7. **`ChatOptions` 复用并升级**：`responseFormat` 从 String 升级为对象载体（承载 json_schema）；其余字段不变。
8. **新增 `ResponsesDialect`**：改造后的消息体系 ↔ Responses wire 双向转换（结构对齐，几乎 1:1）；四个既有方言（openai/anthropic/gemini/ollama）同步改造为基于新消息体系实现，`convertMessage` 承担 Chat 兼容 wire 的单向映射。
9. **并发工具执行不变**：agent 引擎从消息序列中提取 `ChatToolCallMessage` 列表，复用 `ReActAgentExecutor` 现有 fan-out（CompletableFuture.allOf + per-tool 超时），零改动。
10. **状态管理无状态**：上下文 = 消息序列回放；`previous_response_id`/Conversations 属协议耦合，拒绝实现。
11. **`ApiStyle` 新增 `RESPONSES`**；`normalizeFinishReason` 扩展 `completed→stop`、`incomplete→length`。

## 二、背景与动机

- OpenAI Responses API 的 typed items 设计揭示了折叠模型的本质局限；行业方向（OpenAI Responses、Anthropic tool_use blocks、MCP）一致朝向"类型化输出单元 + 具名事件流"。
- nop-ai 现状的每一处硬伤都源于折叠：`ChatAssistantMessage.think` 单格（一次推理多次调工具时寄居）、多模态 content parts 无落点、`parseStreamChunk` 的流式 tool_calls 永远填不上、`output[]` 多 item 折叠丢顺序、`ChatCustomMessage` 往返有损、`ChatOptions.responseFormat` String 无法承载 json_schema。
- 决策要求：**彻底重构、清理代码，只考虑长期演化最优，不受既有兼容面约束**。现有实现均未投入使用，不存在迁移负担——改造现有类型而非另起炉灶，是"清理"而不是"新建"。

## 三、核心设计

### 3.1 模型定位：领域抽象而非 API 封装

`IChatService` 与 `ChatRequest`/`ChatResponse`/`ChatOptions` 是对"AI 交互"的领域抽象：请求 = 消息序列 + 生成选项，响应 = 消息序列 + 元数据 + 结果状态。它们不是任何第三方 API 的 DTO，因此可以承载 wire 之外的规范化信息：

- **错误归一化**：`ChatResponse.error`/`errorCode` 是唯一错误出口。各厂商错误结构（OpenAI `error`、Anthropic `error`、Responses `error`、HTTP 层错误）由方言解析时归一化为 Nop 风格错误码（`errorCode` 字符串 + `error` 描述），调用方与 agent 引擎只识别这一种错误形态。
- **追踪与重试语义**：`ChatRequest.requestId`/`requestTime`/`retryTimes`、`ChatResponse.requestId`/`responseTime` 是领域层字段，与任何 wire 无关。
- **领域层选项**：`ChatOptions.provider`/`sessionId`/`requestTimeout`/`stream`/`enableThinking` 由服务层消费，方言只翻译与 wire 相关的子集。

反过来说，wire 特有字段（厂商私有响应 ID、计费明细、协议扩展点）默认丢弃，或归一化后才允许进入核心模型。**"不抄 OpenAI 字段"是定位的自然推论而非额外约定**：核心模型是自研领域抽象，Responses/Anthropic 都只是双向转换的一端。

### 3.2 消息类型体系改造

```
ChatMessage（abstract，type 区分：user/assistant/system/tool_call/tool_output/reasoning）
├── ChatUserMessage          // user 输入：content + 多模态 parts（text/image/audio）
├── ChatAssistantMessage     // assistant 输出文本（删除 think/toolCalls 寄居字段）
├── ChatSystemMessage        // system：wire 层转 instructions
├── ChatToolCallMessage      // 工具调用：callId + name + arguments(JSON)  [新增，拆自 assistant.toolCalls]
├── ChatToolResponseMessage  // 工具结果：callId + output（自由 Object：JSON/纯文本/错误信息均可）  [type 标识 tool → tool_output]
└── ChatReasoningMessage     // 推理过程：summary（+ 可选 detail）        [新增，拆自 assistant.think]
```

```
伪代码（数据流）：
  上下文 = List<ChatMessage>                          // 请求/响应共用同一序列
  一轮对话：
    response = dialect.chat(上下文, options)           // wire 请求/响应
    上下文.append(response.messages)                   // 回放 = append，零转换
    工具循环：提取 ChatToolCallMessage 列表 → fan-out 并发执行
            → 结果转 ChatToolResponseMessage append → 再调模型
```

设计要点：

- **不设独立的"请求消息类型/响应消息类型"**：`ChatRequest.messages` 与 `ChatResponse.messages` 是同一 `ChatMessage` 序列的不同阶段。`ChatMessage` 的 user/assistant 区分是**角色**语义（谁说的），不是**方向**语义（发给谁/谁返回）——这正是与 OpenAI `input_text`/`output_text` 双命名的本质区别。
- **system 语义**：归入 instructions 顶层参数（wire 层处理），Chat 兼容方向由 dialect 生成 system 消息。
- **callId 关联**：`ChatToolCallMessage` 与 `ChatToolResponseMessage` 靠 callId 配对，禁止数组下标绑定。
- **type 标识与注册**：序列化区分字段沿用 `@JsonTypeInfo` 机制，标识从 role 语义改为 type 语义（user/assistant/system/tool_call/tool_output/reasoning）。

#### 为什么输入输出合一（对比 OpenAI 的 input_text/output_text 双命名）

OpenAI 把同一文本语义拆成两个类型：请求侧 `input_text`、响应侧 `output_text`。多轮手动回放（`store:false` 且不用 `previous_response_id`）时，必须把上一轮 `output` 的 content part 改写为 `input` 接受的格式（并剥离输出独有元数据）——这就是"回放的转换损耗"。nop-ai 的单一 `ChatMessage` 序列让回放 = append，零转换。

OpenAI 为什么区分（其理由成立，但前提与 nop-ai 不同）：

| OpenAI 区分的理由 | nop-ai 的处理 |
|---|---|
| 输入/输出类型**真实不对称**：output 有 annotations/logprobs/reasoning 元数据，input 有 image detail 等参数 | 输出独有元数据作为消息的**可选字段**（如 `ChatAssistantMessage` 上的 annotations），而非**类型区分** |
| wire 协议 schema 紧致性：公开 API 的 request/response 分离是 OpenAPI 惯例，各自校验严格 | 进程内模型，无公开 schema 压力，语义统一优先 |
| 内部请求管线与生成管线分离，双命名与内部结构自然对齐 | 单进程、单序列，无此约束 |
| 有 `previous_response_id`/Conversations 服务端状态兜底，手动回放非主路径，改写损耗多数用户遇不到 | **无状态 + 手动回放是主路径**，回放路径上任何转换成本都不可接受 |

**合一的成立条件恰是"无状态回放主路径 + 进程内模型"**——这正是设计结论 9（无状态）带来的自由。若未来引入服务端会话存储，合一仍然成立（回放依旧 append，只是存储载体不同）。

### 3.3 流式：ChatStreamChunk 改造为 item 增量

```
伪代码（canonical 流式产物）：
  ChatStreamChunk { itemType, itemIndex, callId?, delta, phase: ADDED|DELTA|DONE,
                    finishReason?, usage? }

  on output_item.added(item)            → chunk(ADDED, itemType/itemIndex)
  on output_text.delta(d)               → chunk(DELTA, itemType=text, delta=d)
  on reasoning_summary_text.delta(d)    → chunk(DELTA, itemType=reasoning, delta=d)
  on function_call_arguments.delta(d)   → chunk(DELTA, itemType=tool_call, callId, delta=d)
  on output_item.done / completed       → chunk(DONE, 带 finishReason/usage)
```

- 事件声明 → 增量 → 终结 三段式与消息类型一一对应，item 边界在 canonical 层保留。
- 消费者按 `itemType` 分派：文本增量喂对话 UI，工具调用增量组装 `ChatToolCallMessage` 参数。
- 删除死代码：`ChatToolCallChunk` 不再作为独立模型（`tool_call` 增量直接落在 chunk 的 itemType 维度上）。

### 3.4 Dialect 层改造

- `ILlmDialect` 契约保持（`buildBody`/`parseResponse`/`parseStreamChunk`/`convertMessage`/`parseRequestBody`/`buildResponse`/`buildStreamChunk`），输入输出对象为改造后的 `ChatRequest`/`ChatResponse`（内部消息体系已变）。
- 每个方言 = 消息体系 ↔ wire 的**双向转换**。折叠/展开逻辑归属单一实现（`convertMessage` 承担 Chat 兼容方向的单向映射），方言内不重复实现。

```
消息体系 → Responses wire（ResponsesDialect）：
  List<ChatMessage>                 → input[] items + instructions
  ChatToolCallMessage               → {"type":"function_call", call_id, name, arguments}
  ChatToolResponseMessage           → {"type":"function_call_output", call_id, output}
  ChatUserMessage（text/image/audio）→ message item content parts
  ChatReasoningMessage              → reasoning item（回放时保留）
  options.maxTokens                 → max_output_tokens
  options.responseFormat            → text.format
  store                             → false（无状态）

Responses wire → 消息体系：
  output[] type=message             → ChatAssistantMessage（content parts → 文本）
  output[] type=reasoning           → ChatReasoningMessage
  output[] type=function_call       → ChatToolCallMessage
  usage / status                    → 顶层元数据（finishReason 归一化扩展 completed→stop）
```

- `normalizeFinishReason` 扩展映射表：`completed→stop`、`incomplete→length`（现状只认 stop/end_turn/length/tool_calls）。
- **工具结果字符串化规则归属方言**：wire 层 `function_call_output.output` 是普通字符串，格式由调用方自定——JSON、纯文本、错误信息（如 `Error 408: timeout`）均可，模型按需解读；工具返回"失败"也是合法普通文本，不需要错误封装。领域侧 `ChatToolResponseMessage.output` 保持自由 Object，方言负责序列化：非流式场景任意字符串化（对象则 JSON 序列化）；Responses 流式场景必须编码为合法 JSON 字符串（非 JSON 文本转成字符串字面量），SDK 直传 dict 自动序列化属 SDK 行为、协议不强制。
- **错误归一化**：方言解析 HTTP 错误体与 wire error 结构（OpenAI `error`、Anthropic `error`、Responses `error`）→ `ChatResponse.error`/`errorCode`（Nop 风格），超时/限流等基础设施错误在服务层归一化；核心模型不暴露厂商错误结构。注意与工具返回文本错误区分：`ChatResponse.error` 是**调用**失败（请求级），工具 output 里的错误文本是**工具业务**失败（内容级），两者通道不同。
- `ApiStyle` 枚举新增 `RESPONSES`（与 Ollama 原生 / OpenAI 兼容 / Responses 三格式并存）。

### 3.5 Agent 引擎适配

- `ReActAgentExecutor` 的"assistant 消息 + toolCalls 寄居"逻辑替换为：从响应消息序列提取 `ChatToolCallMessage` 列表 → 现有 fan-out 并发执行 → 结果转 `ChatToolResponseMessage` append 回上下文。
- 删除折叠相关补偿逻辑：think/toolCalls 寄居、`ChatCustomMessage` 角色归并、`ChatToolCallChunk` 死代码路径。
- 迭代判定从"消息 role 分派"改为"消息 type 分派"：`ChatAssistantMessage` 终结 or `ChatToolCallMessage` 继续循环。

### 3.6 使用契约

- 配置：`LlmModel` 中 `dialect=responses` + `chatUrl=/v1/responses` 启用 Responses 端点；其余四个方言配置不变。
- 便利 API：`ChatResponse` 提供聚合访问器（如 `outputText()` 拼接全部 `ChatAssistantMessage` 文本、`outputToolCalls()`），纯文本场景不受拆模型复杂度影响。
- 错误出口：`ChatResponse.isSuccess()`/`error`/`errorCode` 为唯一错误形态（Nop 风格错误码），上层不感知厂商错误结构。
- 多模态：image/audio parts 直接进入 `ChatUserMessage`，dialect 负责编码为 wire 对应 part；UI/审计按消息 type 渲染。

## 四、拒绝了什么

1. **保持折叠模型为核心（现状）**——拒绝理由：有损投影；现状硬伤清单（think 单格、多模态无落点、流式 tool_calls 永不填充、多 item 丢顺序、custom 往返有损）全部源于折叠，无法在不换模型的前提下根治。
2. **另起新命名体系（如 `LlmItem`/`LlmItemDelta` 及独立投影层）**——拒绝理由：命名即契约，`ChatRequest`/`ChatResponse`/`ChatMessage` 系列名称准确且可复用，弃用即浪费认知成本；独立投影层（items↔折叠消息）在彻底重构后没有存在价值——Chat 兼容 wire 本就是 `convertMessage` 的职责。
3. **双核心并存（折叠为主、拆分辅助）**——拒绝理由：两个模型并存必然漂移；拆分是超集，折叠是其有损投影，投影不应与源模型竞争地位。
4. **直接抄 OpenAI items 字段（input_text/output_text 双命名、previous_response_id 等）**——拒绝理由：协议耦合；消息体系是自研协议无关设计，Responses/Anthropic 都只是双向转换的一端，未来换协议不动核心。
5. **一刀切全量替换**——拒绝理由：重构分阶段、每阶段独立可验证（golden test + 既有测试回归）。
6. **以网关中间层（litellm 部署）为主方案**——拒绝理由：独立部署运维成本；仅在出现"服务 Codex 类客户端"需求时评估 `nop-ai-gateway` 前端方言挂点（`AiDialectBackendMessageConverter` 已预留）。

## 五、与已有设计的关系

- `nop-ai-agent/04-tool-invocation.md`：工具循环语义不变（并发 fan-out 保留），仅消息载体切换为 `ChatToolCallMessage`。
- `nop-ai-gateway/01-architecture.md`：`AiDialectBackendMessageConverter` 的 frontend/backend 双方言结构不变，本重构后双方言输入输出统一为消息序列，前端方言（Codex 场景）可后续接入。
- 实施路径（获批后拆 `ai-dev/plans/`，每 Phase 独立验证）：
  - Phase 0：消息类型体系改造（新增 `ChatToolCallMessage`/`ChatReasoningMessage`，删除寄居字段与 `ChatCustomMessage`）+ 序列化 golden test。（✅ plan 325 已落地：新增 `ChatToolCallMessage`(`tool_call`)/`ChatReasoningMessage`(`reasoning`)，`ChatToolResponseMessage` 改名 `callId` + type `tool_output`，删 `ChatCustomMessage`，`ApiStyle.RESPONSES` 枚举 + `normalizeFinishReason` 扩展，golden test 全绿。寄居字段 `think`/`toolCalls` 已由 plan 329 删除并收敛到单一拆分模型。）
  - Phase 1：dialect 层改造（4 个既有方言基于新消息体系，既有测试全绿）。
  - Phase 2：agent 引擎切换（`ReActAgentExecutor` 提取 `ChatToolCallMessage`，工具循环回归）。（✅ plan 327 已落地：引擎从 `response.getMessages()` 提取 `ChatToolCallMessage` → `List<ChatToolCall>` 喂既有 fan-out；5 个辅助生产引用点迁移；85 个 mock-LLM 测试迁移到 `ChatResponseFixtures` 双轨产出；3404 测试全绿。）
  - Phase 3：流式 `ChatStreamChunk` item 增量重构 + UI 文本消费适配。（✅ plan 328 已落地：`ChatStreamChunk` 改为 item 增量模型（itemType/itemIndex/callId/delta/phase:ADDED|DELTA|DONE），删 `ChatToolCallChunk`；4 dialect `parseStreamChunk` 产出 item 增量 + 补齐 OpenAI 流式 tool_calls 缺口；StreamAggregator/ChatStreamAccumulator 按 itemType 状态机汇聚产出与非流式 326 同构的 `response.messages`。旧 `message`/think/toolCalls 寄居字段双轨保留待 329 删。）
  - Phase 4：`ResponsesDialect` + 流式/非流式集成测试（mock Responses wire fixture）。
