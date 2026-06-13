# 前缀缓存与 ILlmDialect 序列化确定性讨论

> Status: resolved
> Date: 2026-06-10

## 第 1 轮

### 用户原始表述

分析了 Reasonix 的前缀缓存不变性机制后，AI 产出了详细的设计构想（`ai-dev/analysis/2026-06-10-nop-prefix-cache-design-analysis.md`），方案 C 包含 `PrefixState`、`PrefixFingerprint`、`IContextGovernor` 等新类和接口。用户认为设计过于复杂。

### AI 复述

AI 认为需要在前缀消息上标记 frozen、拆分 fixedMessages/messages 两个列表，或者引入引擎层的 PrefixState + IContextGovernor 管线。核心关注点是 ILlmDialect 的 `convertMessage()` 有 `isLast` 参数会破坏序列化字节稳定性。

### AI 分析

Reasonix 源码调研揭示：ImmutablePrefix 是引擎内部状态，不是 API 请求字段。Nop 的架构差异在于消息转换发生在 `IChatService` 内部的 `ILlmDialect`，而非引擎层直接调 Provider API。

### 待澄清问题

1. 前缀缓存是否需要新数据结构（PrefixState、PrefixFingerprint）？
2. `isLast` 参数是否必须移除？
3. API 层（ChatMessage/ChatRequest）是否需要加缓存相关标记？

---

## 第 2 轮

### 用户回答

> 只要每次向大模型发送的消息的前缀一样就可以，不需要考虑大模型返回的消息的格式吗？比如大模型返回 toolcall，但是解析后重新向大模型发送的时候 toolcall 被重新编码了，这影响吗？应用层保证每次向大模型发送的消息前缀都保持不变，它对解析后的 toolcall 的序列化可以确保是确定性的

### AI 整理

用户指出了核心简化原则：前缀缓存只看应用发送给模型的字节序列，不看模型返回了什么。应用层全权控制发送内容，工具调用的重编码是确定性的（LinkedHashMap 保证键序），所以不影响缓存。

### AI 提问

> 简化设计，修改文档

### 待澄清问题

1. 简化后的方案是否还需要任何新类？

---

## 第 3 轮

### 用户回答（关于 isLast 参数）

> 这个参数内部不处理不就可以了吗？为什么会 applyThinkingPrompt？是不是不 apply 对于某些模型就不行？thinking prompt 是什么？大模型返回的 thinking 信息到底要不要回传大模型，deepseek 好像是不建议回传的

### AI 整理

用户质疑 `isLast` 参数的必要性。AI 解释：

1. **thinking prompt** 是给没有原生 thinking API 参数的模型用的文本 hack（追加 "Please think step by step" 之类的文本到最后一条 user 消息）
2. **有原生支持的模型不需要**：DeepSeek R1 的 `reasoning_content` 是模型自产生的，Anthropic Claude 的 thinking 由 API 参数控制
3. **thinking 回传规则因 Provider 而异**：
   - DeepSeek：`reasoning_content` 不应回传
   - Anthropic：Extended Thinking 要求多轮对话时回传 thinking block（含 signature），否则 API 报错
4. `isLast` 是设计遗留，把"控制模型是否思考"的逻辑塞进了序列化层

### 用户决定

> 移除 isLast

---

## 纠正

### 关于 design 文档写法

用户指出 AI 的 design 文档写成了源码的复现（字段表、代码片段），违反了 `ai-dev/design/00-design-writing-guide.md` 的原则：

> design 不是源码的复现，源码本身很清楚的内容不用在 design 内再说，design 中说明的是不直观的原因和容易搞错的点。

> 不要的内容要删除（不要用划线标记保留）

### 关于废弃模块

用户指出 `nop-ai-llm` 和 `nop-ai-llms` 是空模块，`ILlmDialect` 已吸收差异性，应直接删除。

---

## 总结

### 最终决策

1. **不引入 PrefixState / PrefixFingerprint / IContextGovernor**：引擎层用 `prefixLength` + `prefixHash` 两个字段管理即可
2. **ILlmDialect.convertMessage() 移除 isLast 参数**：变为纯函数（相同输入 → 相同字节），thinking prompt 注入移到各 Dialect 的 buildBody() 后置处理
3. **ChatUsage 加 cacheMissTokens + getCacheHitRate()**：补充诊断能力
4. **ChatMessage 加 providerHints**：Anthropic `cache_control` 透传通道
5. **AnthropicDialect 从 providerHints 注入 cache_control**
6. **删除 nop-ai-llm / nop-ai-llms 空模块**（目录 + BOM 引用）
7. **design 文档精简**：只保留决策理由和易错点，删除字段表、代码片段、划线标记

### 关键易错点

- Anthropic thinking 必须回传（含 thinkSignature），DeepSeek thinking 不应回传——两个 Provider 行为相反
- `convertMessage()` 是纯函数，不接受位置参数——这是前缀缓存正确性的根基
- `providerHints` 是元数据透传通道，不影响消息文本序列化

### 已完成的代码变更

- `nop-ai-api`: ChatUsage、ChatMessage（+ providerHints）、所有子类 copy()
- `nop-ai-core`: ILlmDialect、OpenAiDialect、AnthropicDialect、GeminiDialect、OllamaDialect、TestLlmDialectFactory
- 删除 nop-ai-llm、nop-ai-llms 两个子模块目录
- 清理 `nop-bom/pom.xml` 中的引用

### 已更新的设计文档

- `nop-ai-agent-llm-layer.md` §三（消息格式）、§八（前缀缓存）
- `01-architecture-baseline.md`（模块边界、前缀缓存）
- `glossary.md`（删除 IContextGovernor）
- `nop-ai-agent-roadmap.md`（删除废弃引用、Layer 2 重编号）
- `README.md`（LLM 层描述）
