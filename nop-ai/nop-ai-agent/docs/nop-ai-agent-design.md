# Nop AI Agent 框架设计文档（v3/v5）

> 基于 ~/ai 目录 70+ 项目研究 + nop-ai 现有代码库深度分析 + 6 个关键定制点发现，设计下一代 Agent 框架。
> 关键原则：**在已有基础上增量演进**，而非推翻重来。
>
> v3 新增：
> - Tool Call 双模式（NATIVE / XML / AUTO）
> - 工具失败自动重试 + AI 修复分支
> - 异常转 Bug 系统
> - call-agent 工具扩展（mode/context）
> - Plan 作为 Skill + Hook 事件模式（Ralph Loop）
> - Todo 和 Plan 独立概念
>
> v5 新增（容错处理体系）：
> - 三级错误分类 + 指数退避重试
> - 断路器模式（CLOSED/OPEN/HALF_OPEN）
> - 签名式循环检测（两级策略）
> - 三层上下文窗口保护
> - 多级超时预算
> - 工具安全验证（四步流程）
> - 模型回退链 + 冷却追踪
> - 检查点恢复（可插拔存储）

---

## 1. 现状分析：nop-ai 已有什么

### 1.1 模块现状

| 模块 | 包路径 | 状态 | 说明 |
|------|--------|------|------|
| **nop-ai-api** | `io.nop.ai.api.chat` | ✅ 成熟 | ChatMessage/ChatRequest/ChatResponse/ChatStreamChunk，流式累积器 |
| **nop-ai-core** | `io.nop.ai.core` | ⚠️ 过渡中 | 旧 AiMessage 体系标记 @Deprecated，IAiChatService、ILlmDialect、IPromptTemplate |
| **nop-ai-toolkit** | `io.nop.ai.toolkit` | ✅ 可用 | IToolManager/IToolExecutor/IToolCallInterceptor，18 个内置工具执行器 |
| **nop-ai-agent** | `io.nop.ai.agent` | 🔴 骨架 | BaseAgent 空壳，Plan model 体系已有，IAiMemoryStore/AiMemoryConfig |
| **nop-ai-llm** | `io.nop.ai.core.dialect` | ✅ 成熟 | OpenAI/Anthropic/Gemini/Ollama 四种 Dialect，IChatService + Flow.Publisher |
| **nop-ai-coder** | `io.nop.ai.coder` | ✅ 可用 | ai-plan.xdef，编码 Agent 应用层 |

### 1.2 已有消息模型（nop-ai-api）

**ChatMessage 体系**（`io.nop.ai.api.chat.messages`）：

```
ChatMessage (abstract)
├── ChatUserMessage          content: String
├── ChatAssistantMessage     content: String + think: String + toolCalls: List<ChatToolCall>
├── ChatSystemMessage        content: String
├── ChatToolResponseMessage  toolCallId + name + content: String
└── ChatCustomMessage        content: String

ChatToolCall: id + name + arguments: Map<String,Object>
ChatStreamChunk: content + thinking + toolCall(ChatToolCallChunk) + finishReason + usage
```

**关键观察**：
- 消息 content 是纯 String，**不是** ContentBlock 多态列表
- Assistant 消息已内嵌 think + toolCalls（平铺字段，非嵌套 content block）
- 工具调用结果独立为 ChatToolResponseMessage（role="tool"），**不是** AssistantMessage 的子块
- 流式累积器 ChatStreamAccumulator 支持 content + thinking + toolCalls 并行累积

**这个设计是合理的。** 它不同于 AgentScope-Java 的 ContentBlock 模型，但它：
1. 与 OpenAI/Anthropic API 的消息结构直接对应
2. 更符合 Java 开发者的直觉（类型明确而非多态列表）
3. 序列化/反序列化更简单（@JsonSubTypes 多态）
4. 已有完整的流式累积支持

### 1.3 已有工具模型（nop-ai-toolkit）

**核心接口**：

```java
IToolExecutor:                    getToolName() + executeAsync(AiToolCall, IToolExecuteContext)
IToolManager:                     callTool/callTools + listTools/loadTool
IToolExecutorProvider:            getExecutor(toolName) + getToolNames()
IToolCallInterceptor:             beforeCall/afterCall（同步拦截器）
IToolExecuteContext:              workDir + envs + cancelToken + fileSystem + executor

AiToolCall:                       id + toolName + explanation + timeoutMs + input(String) + XNode
AiToolCallResult:                 id + status + output(AiToolOutput) + error(AiToolError) + exitCode
AiToolModel:                      工具元数据（由 *.tool.xml 声明式定义）

ToolManagerImpl:                  拦截器链 + 顺序/并行执行 + VirtualFileSystem 加载 .tool.xml
```

**已有 18 个工具执行器**：ReadFile, WriteFile, PatchFile, ApplyDelta, CopyFile, MoveFile, DeleteFile, CreateDirectory, ListDirectory, SearchFiles, SearchContent, Bash, HttpRequest, GraphqlQuery, SearchEngine, ManageTodoList, CallAgent, AskOracle, Skill

**工具 XML 定义**（来自 tool.xdef）：
- `<schema>`：工具调用格式（XML 结构）
- `<description>`：工具说明
- `<examples>`：Few-shot 示例
- 每个工具都有独立的 `.tool.xml` 文件

### 1.4 已有 LLM 集成（nop-ai-core + nop-ai-llm）

```java
IChatService:          callAsync(ChatRequest) + callStream(ChatRequest) → Flow.Publisher<ChatStreamChunk>
ILlmDialect:           buildUrl/buildBody/parseResponse/parseStreamChunk/convertMessage/convertToolDefinitions
                       实现: OpenAiDialect, AnthropicDialect, GeminiDialect, OllamaDialect

ChatRequest:           messages + options + tools(List<ChatToolDefinition>) + toolChoice
ChatResponse:          message(ChatAssistantMessage) + usage + finishReason + error
```

**关键发现（v3 新增）**：
- `ILlmDialect` 已支持 4 种 LLM 提供商的 **native tool_call**
- `convertToolDefinitions()` 方法将 ChatToolDefinition 转换为各 LLM 的工具格式
- OpenAI、Anthropic、Gemini、Ollama 都已支持原生工具调用
- 但某些 LLM 不支持原生工具调用，需要回退到 XML prompt 模式

### 1.5 已有 Memory（nop-ai-agent）

```java
IAiMemoryStore:        getAll(filters) + getLastN(n) + search(query) + add(item)
AiMemoryConfig:        trimRounds=100, enableSummary=true, summaryRounds=5, summaryContextLength=10000
AiMemoryItem:          记忆项模型
```

### 1.6 已有 Agent（nop-ai-agent）

```java
BaseAgent:              仅 IAiMemoryStore memory 一个字段
AgentModel:             XDSL 生成的配置模型（name + sysPrompt + ...）
AgentExecStatus:        执行状态枚举
AgentHookModel:         Hook 配置模型（使用事件模式，非固定方法）
AgentPermissionModel:   权限配置模型
AgentConstraintsModel:  约束配置模型

AgentPlan:              计划模型（objective + phases[] → tasks[]）
AgentPlanNote:          计划笔记
AgentPlanPhase/Task:    阶段/任务
AgentPlanError:         错误记录（errorText + resolution + attemptNumber + resolvedAt + relatedTaskNo）
```

### 1.7 Agent Hook 系统的真相（v3 关键发现）

**agent.xdef 的实际定义**：

```xml
<hooks xdef:body-type="list" xdef:key-attr="id">
    <on xdef:name="AgentHookModel" id="!string"
        event="!event-pattern-string">xpl-fn:(event, agentRt)=>void
    </on>
</hooks>
```

**关键发现**：
- Hook 使用 **事件模式匹配**（event="!event-pattern-string"）
- Hook body 是 `IEvalFunction`（可以是 XPL 表达式或脚本）
- **不是** v2 文档中设计的 4 方法接口（beforeReasoning/afterReasoning/beforeActing/afterActing）
- Hook 可以响应任意事件：`before_reasoning`、`after_acting`、`plan_task_complete`、`error_occurred` 等
- 这为 Skill 系统注册 Hook 提供了极大灵活性

**设计调整**（v3）：
- 保留 **事件模式**作为声明式 Hook 配置（在 .agent.xml 中）
- 提供 **便利基类**将事件模式映射到类型化方法（方便 Java 代码实现）
- 例如：`ReActHookBase` 提供 `beforeReasoning()` 方法，内部将其注册为 `before_reasoning` 事件的处理器

### 1.8 Advisor Agent 模式（来自 agent-design.md）

**核心发现**：
- Advisor Agent 是普通 agent，通过 `call-agent` 工具调用
- 所有决策（一致性检查、重试、压缩）都通过 Advisor Agent 实现
- Advisor Agent 有独立的 session 和权限配置
- 返回结构化的 JSON 决策结果

**Advisor Agent 类型**：
- `consistency-checker`：检查 plan 和 chat 的一致性
- `retry-advisor`：判断是否应该重试（shouldRetry/reason/suggestedAction/modifiedParams）
- `compression-advisor`：判断是否需要压缩

---

## 2. Gap 分析：缺什么

| 能力 | 现状 | 缺失 | 优先级 |
|------|------|------|--------|
| **Agent 接口** | BaseAgent 空壳 | IAgent 接口、call/stream 生命周期 | P0 |
| **ReAct 循环** | 无 | Reasoning→Acting 循环、maxIters、summary | P0 |
| **Agent ↔ LLM 桥接** | IChatService 独立 | Agent 调用 IChatService + 工具的完整循环 | P0 |
| **Agent ↔ Toolkit 桥接** | IToolManager 独立 | Agent 持有 IToolManager、工具 Schema 注入 LLM | P0 |
| **Tool Call 双模式** | 仅 NATIVE | XML prompt 模式、AUTO 检测、ToolCallExtractor | P0（v3 新增）|
| **工具失败重试** | 无 | 自动重试 + AI 修复分支 + RetryAdvisor 集成 | P0（v3 新增）|
| **Agent Hook 系统** | IToolCallInterceptor（仅工具层） | Agent 级别的 Hook（事件模式 + 便利基类） | P1（v3 更新）|
| **Agent Memory 集成** | IAiMemoryStore 独立 | Agent 持有 Memory、自动压缩 | P1 |
| **Skill 系统** | SkillExecutor 存在 | ISkill 接口 + 条件激活 + Hook 注册 | P1（v3 新增）|
| **Plan 驱动循环** | Plan model 存在 | PlanSkill、Ralph Loop、Hook 驱动 Agent 循环 | P1（v3 新增）|
| **异常转 Bug** | AgentPlanError 存在 | bug-report 工具（.tool.xml + markdown 触发指令）| P1（v3 新增）|
| **Todo 系统** | ManageTodoListExecutor | Todo 持久化、与 Plan 独立但可关联 | P1（v3 新增）|
| **call-agent 扩展** | 基础 schema | mode（sync/async/detached）、context（XML）| P1（v3 新增）|
| **多 Agent 编排** | CallAgentExecutor（单工具） | Pipeline/Graph/Supervisor | P2 |
| **中断/HITL** | ICancelToken（取消） | interrupt 挂起 + 恢复 | P2 |
| **Session 持久化** | 无 | AgentState save/load、检查点 | P2 |

---

## 3. 下一代设计

### 3.1 核心设计原则

1. **复用而非重建**：nop-ai-api 的 ChatMessage 体系、nop-ai-toolkit 的 IToolManager 体系直接复用
2. **增量演进**：在 nop-ai-agent 模块内新增接口和实现，不修改已有稳定接口
3. **Nop 风格优先**：用 @Inject/@InjectValue 注入，避免 private 字段注入，用 XDSL 声明式配置
4. **CompletionStage > Reactor**：Nop 平台不用 Project Reactor，统一用 Java 8 的 CompletionStage + Flow.Publisher
5. **事件驱动 Hook**：使用 agent.xdef 定义的事件模式，提供便利基类简化实现
6. **Advisor Agent 决策**：重试、修复、压缩等通过 Advisor Agent 智能决策
7. **Prompt 驱动 > 接口驱动**：AI 系统的很多行为靠 markdown/tool.xml 自然语言描述驱动（如 bug 上报、重试策略），而非为每个关注点定义强类型接口。核心抽象（ISkill、IContextCompressor）保留，但触发时机比接口层次更重要

> **与 v1/v2/v3 文档的关键偏离**：
> - v1：参考 AgentScope-Java 引入了 ContentBlock 多态体系和 Reactor Flux
> - v2：发现 ChatMessage 体系已成熟，改为复用；保留 4 方法 Hook
> - v3：发现 Hook 实际使用事件模式，改为 **事件模式 + 便利基类** 双层设计；新增 Tool Call 双模式、AI 修复分支、Skill 系统
> - v3 修正：去除 IBugReporter 等过度设计的接口层次。AI Agent 框架中很多行为靠 prompt + tool.xml 驱动，不需要强类型接口。关键是 **触发时机** 设计，而非接口继承

### 3.2 Agent 接口设计

> 灵感来源：AgentScope-Java（接口分离）、Solon-AI（Agent+FlowContext）、Spring-AI-Alibaba（invoke+stream 双模式）

```java
/**
 * Agent 核心接口。
 *
 * 灵感来源：
 * - AgentScope-Java: IAgent = CallableAgent + StreamableAgent + ObservableAgent（接口分离）
 * - Spring-AI-Alibaba: Agent.invoke() + Agent.stream()（invoke/stream 双模式）
 * - Solon-AI: Agent.call(Prompt, AgentSession)（Session 绑定）
 *
 * 下一代创新：
 * - 不引入 IObservableAgent（Pub/Sub 在 TeamAgent 层用更简单的方式实现）
 * - 不用 Reactor Flux，用 Flow.Publisher（Nop 平台标准）
 * - 简化为两个核心方法：chat（一次性）和 chatStream（流式）
 */
public interface IAgent {

    String getAgentId();
    String getName();
    String getDescription();

    /**
     * 一次性调用：发送消息，返回完整响应（含多轮工具调用后的最终结果）。
     * 内部自动执行 ReAct 循环直到结束。
     */
    CompletionStage<AgentResponse> chat(AgentRequest request);

    /**
     * 流式调用：实时推送推理和工具执行过程。
     *
     * 灵感来源：
     * - OpenCode: Event bus 推送 AgentEvent
     * - Pi-Agent: 事件驱动 agent_start/turn_start/message_update
     * - AgentScope-Java: Flux<Event> with EventType 枚举
     */
    Flow.Publisher<AgentEvent> chatStream(AgentRequest request);

    /**
     * 中断当前执行（协作式中断，不立即终止）。
     *
     * 灵感来源：
     * - AgentScope-Java: interrupt() + checkInterruptedAsync()
     * - LangGraph: interrupt_before/interrupt_after
     */
    void interrupt();
}

/**
 * Agent 请求 —— 封装调用参数
 *
 * 复用 nop-ai-api 的 ChatMessage，不引入新的消息类型。
 */
public class AgentRequest {
    private List<ChatMessage> messages;     // 复用 ChatMessage 体系
    private String systemPrompt;
    private ChatOptions options;            // 复用 ChatOptions
    private IToolExecuteContext context;    // 复用工具执行上下文
    private ICancelToken cancelToken;       // 复用 Nop 取消令牌

    // v3 新增：工具调用模式
    private ToolCallMode toolCallMode;     // NATIVE | XML | AUTO

    // v3 新增：关联的 Plan（用于 PlanSkill）
    private AgentPlan plan;

    // v3 新增：关联的 Session ID（用于会话延续）
    private String sessionId;
}

/**
 * Tool Call 模式枚举（v3 新增）
 *
 * 灵感来源：
 * - OpenAI: native tool_call API
 * - Anthropic: native tool use API
 * - 某些 LLM: 不支持工具调用，需要 XML prompt
 */
public enum ToolCallMode {
    /**
     * 原生工具调用：使用 LLM 的 tool_call 功能。
     * OpenAI、Anthropic、Gemini、Ollama 支持。
     */
    NATIVE,

    /**
     * XML Prompt 模式：
     * - 将工具 Schema + Examples 注入 system prompt
     * - 设置 toolChoice="none"
     * - LLM 返回文本，用 XmlResponseParser 解析
     */
    XML,

    /**
     * 自动检测：
     * - 检查 ILlmDialect.supportToolCalls()
     * - 如果支持 → NATIVE
     * - 如果不支持 → XML
     */
    AUTO
}

/**
 * Agent 响应 —— 最终结果
 */
public class AgentResponse {
    private ChatAssistantMessage message;   // 复用 ChatAssistantMessage
    private ChatUsage usage;                // token 用量
    private int iterations;                 // 实际迭代次数
    private String finishReason;            // "stop" | "max_iterations" | "interrupted" | "error"
    private List<ToolExecutionRecord> toolExecutions; // 工具执行记录
    private String sessionId;               // 会话 ID（v3 新增）
}

/**
 * Agent 事件 —— 流式推送
 *
 * 灵感来源：
 * - AgentScope-Java: EventType 枚举 (AGENT_RESULT, REASONING_CHUNK, TOOL_CALL_COMPLETE)
 * - OpenCode: MessagePart 体系 (text, toolCall, toolResult, error)
 * - Pi-Agent: agent_start, turn_start, message_start/update/end
 *
 * 下一代创新：
 * - 用 sealed interface + records 实现类型安全的事件（Java 17+）
 * - 事件体直接复用 ChatMessage/ChatStreamChunk，不引入新类型
 */
public abstract class AgentEvent {
    private final String agentId;
    private final long timestamp;

    // 推理文本块
    public static class TextChunk extends AgentEvent {
        private final String delta;           // 增量文本
        private final String accumulated;     // 累积文本
    }

    // 思考块
    public static class ThinkingChunk extends AgentEvent {
        private final String delta;
        private final String accumulated;
    }

    // 工具调用开始
    public static class ToolCallStart extends AgentEvent {
        private final ChatToolCall toolCall;  // 复用 ChatToolCall
    }

    // 工具执行完成
    public static class ToolCallComplete extends AgentEvent {
        private final String toolCallId;
        private final String toolName;
        private final AiToolCallResult result; // 复用 AiToolCallResult
    }

    // Agent 最终结果
    public static class AgentResult extends AgentEvent {
        private final AgentResponse response;
    }

    // 错误
    public static class AgentError extends AgentEvent {
        private final String error;
        private final String errorCode;
    }

    // 中断
    public static class AgentInterrupted extends AgentEvent {
        private final String reason;
    }
}
```

### 3.3 Tool Call 双模式设计（v3 新增）

> 灵感来源：
> - OpenAI/Anthropic/Gemini/Ollama: native tool_call
> - 某些 LLM: 不支持工具调用，需要 XML prompt
> - AgentScope-Java: Toolkit 转换为不同格式
> - DeepAgents: XML-based tool calling

**核心挑战**：
- 部分高级 LLM（GPT-4、Claude 3.5、Gemini 2.0）支持原生工具调用（function calling）
- 部分本地模型（Ollama 某些模型、开源小模型）不支持，需要将工具 Schema 注入 prompt
- 需要统一接口，让 Agent 配置可以选择模式或自动检测

**设计方案**：

```java
/**
 * Tool Call 模式适配器 —— 根据模式处理工具调用。
 *
 * v3 新增。
 *
 * 核心流程：
 * 1. 根据 ToolCallMode 决定处理方式
 * 2. NATIVE: 使用 ILlmDialect.convertToolDefinitions() + ChatToolCall
 * 3. XML: 注入 Schema + Examples 到 system prompt，解析 XML 文本
 * 4. AUTO: 检查 ILlmDialect.supportToolCalls()，自动选择
 */
public class ToolCallModeAdapter {

    private final ILlmDialect dialect;
    private final List<AiToolModel> tools;
    private final XmlResponseParser xmlParser;

    /**
     * 准备 ChatRequest（根据模式）。
     */
    public CompletionStage<ChatRequest> prepareRequest(ChatRequest request, ToolCallMode mode) {
        return switch (mode) {
            case NATIVE -> prepareNativeRequest(request);
            case XML -> prepareXmlRequest(request);
            case AUTO -> detectAndPrepare(request);
        };
    }

    /**
     * NATIVE 模式：使用原生工具调用。
     *
     * 流程：
     * 1. 调用 ILlmDialect.convertToolDefinitions(tools) → ChatToolDefinition[]
     * 2. 设置 ChatRequest.tools = ChatToolDefinition[]
     * 3. LLM 返回 ChatAssistantMessage.toolCalls
     * 4. ChatToolCall → AiToolCall 转换
     */
    private CompletionStage<ChatRequest> prepareNativeRequest(ChatRequest request) {
        List<ChatToolDefinition> toolDefs = dialect.convertToolDefinitions(tools);
        request.setTools(toolDefs);
        request.setToolChoice("auto");
        return CompletableFuture.completedFuture(request);
    }

    /**
     * XML 模式：将工具 Schema 注入 prompt。
     *
     * 流程：
     * 1. 将工具 Schema 转换为 XML 格式
     * 2. 将 Few-shot Examples 注入 prompt
     * 3. 设置 toolChoice="none"（禁用原生工具调用）
     * 4. LLM 返回文本，用 XmlResponseParser 解析 <call-tools>
     */
    private CompletionStage<ChatRequest> prepareXmlRequest(ChatRequest request) {
        // 1. 生成工具 Schema 文本
        String toolSchemas = buildToolSchemaText(tools);

        // 2. 生成 Examples 文本
        String examples = buildToolExamplesText(tools);

        // 3. 注入到 system prompt
        String xmlPrompt = """
            You are an AI assistant with access to tools.
            When you need to call a tool, output XML in this format:

            <call-tools parallel="true">
                <read-file id="1" explanation="Read the file" path="..."/>
                <search-files id="2" explanation="Search files" pattern="..."/>
            </call-tools>

            Available tools:
            %s

            Examples:
            %s
            """.formatted(toolSchemas, examples);

        String newSystemPrompt = request.getSystemPrompt() + "\n\n" + xmlPrompt;
        request.setSystemPrompt(newSystemPrompt);
        request.setToolChoice("none"); // 禁用原生工具调用

        return CompletableFuture.completedFuture(request);
    }

    /**
     * AUTO 模式：自动检测并选择。
     */
    private CompletionStage<ChatRequest> detectAndPrepare(ChatRequest request) {
        if (dialect.supportToolCalls()) {
            return prepareNativeRequest(request);
        } else {
            return prepareXmlRequest(request);
        }
    }

    /**
     * 提取工具调用（根据模式）。
     *
     * NATIVE: 从 ChatAssistantMessage.toolCalls 提取
     * XML: 从 message.content 解析 <call-tools>
     */
    public List<AiToolCall> extractToolCalls(ChatAssistantMessage message, ToolCallMode mode) {
        return switch (mode) {
            case NATIVE -> extractFromToolCalls(message.getToolCalls());
            case XML, AUTO -> extractFromXml(message.getContent());
        };
    }

    /**
     * 从原生 ChatToolCall 提取。
     */
    private List<AiToolCall> extractFromToolCalls(List<ChatToolCall> toolCalls) {
        return toolCalls.stream()
            .map(tc -> {
                AiToolCall aiCall = new AiToolCall();
                aiCall.setId(tc.getId());
                aiCall.setToolName(tc.getName());
                aiCall.setArguments(tc.getArguments());
                return aiCall;
            })
            .toList();
    }

    /**
     * 从 XML 文本解析。
     */
    private List<AiToolCall> extractFromXml(String content) {
        // 使用 XmlResponseParser 解析 <call-tools>
        AiToolCalls toolCalls = xmlParser.parse(content);
        return toolCalls.getToolCalls();
    }
}

/**
 * XML 响应解析器 —— 解析 LLM 返回的 XML 格式工具调用。
 *
 * v3 新增。
 *
 * 对应 schema: call-tools.xdef
 */
public class XmlResponseParser {

    /**
     * 解析 <call-tools> XML。
     *
     * 输入示例：
     * <call-tools parallel="true">
     *     <read-file id="1" explanation="..." path="..."/>
     *     <search-files id="2" explanation="..." pattern="..."/>
     * </call-tools>
     */
    public AiToolCalls parse(String xmlContent) {
        XNode node = XNodeParser.instance().parse(xmlContent);
        return node.toModel(AiToolCalls.class);
    }
}
```

**并行工具执行策略（v4 新增）**：

> 灵感来源：OpenCode（Promise.all + per-tool try-catch）、AgentScope-Java（Semaphore + per-call 隔离）、Nop Task（ParallelTaskStep + JoinType）
>
> **核心挑战**：
> - LLM 可能一次返回多个工具调用（例如同时读取多个文件、搜索多个目录）
> - 需要并行执行这些工具以提高效率
> - 需要错误隔离：一个工具失败不应该影响其他工具的执行
> - 需要可配置的并发限制，避免资源耗尽

**设计方案**：

```java
/**
 * 工具调用状态枚举（v4 新增）。
 */
public enum ToolCallStatus {
    PENDING,    // 等待执行
    RUNNING,    // 正在执行
    COMPLETED,  // 执行成功
    ERROR       // 执行失败
}

/**
 * 工具调用包装器 —— 包含状态、结果和错误信息（v4 新增）。
 */
public class ToolCallWrapper {

    /** 工具调用 */
    private final AiToolCall toolCall;

    /** 执行状态 */
    private volatile ToolCallStatus status = ToolCallStatus.PENDING;

    /** 执行结果（成功时） */
    private volatile AiToolCallResult result;

    /** 执行错误（失败时） */
    private volatile Throwable error;

    /** 开始时间 */
    private volatile Instant startedAt;

    /** 结束时间 */
    private volatile Instant completedAt;

    public ToolCallWrapper(AiToolCall toolCall) {
        this.toolCall = toolCall;
    }

    // Getters and setters
    public AiToolCall getToolCall() { return toolCall; }
    public ToolCallStatus getStatus() { return status; }
    public void setStatus(ToolCallStatus status) { this.status = status; }
    public AiToolCallResult getResult() { return result; }
    public void setResult(AiToolCallResult result) { this.result = result; }
    public Throwable getError() { return error; }
    public void setError(Throwable error) { this.error = error; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

/**
 * 并行工具执行器（v4 新增）。
 *
 * 灵感来源：
 * - OpenCode: Promise.all + per-tool try-catch
 * - AgentScope-Java: Semaphore + per-call 隔离
 * - Nop Task: ParallelTaskStep + JoinType
 */
public class ParallelToolExecutor {

    /** 工具管理器 */
    private final IToolManager toolManager;

    /** 最大并发工具数（默认 5，最大 25，与 OpenCode 一致） */
    private final int maxParallelTools;

    /** 并发限制信号量 */
    private final Semaphore semaphore;

    /** 执行器线程池 */
    private final ExecutorService executor;

    public ParallelToolExecutor(IToolManager toolManager, int maxParallelTools) {
        this.toolManager = toolManager;
        this.maxParallelTools = Math.min(25, Math.max(1, maxParallelTools)); // 限制在 1-25 之间
        this.semaphore = new Semaphore(this.maxParallelTools);
        this.executor = Executors.newVirtualThreadPerTaskExecutor(); // 使用虚拟线程
    }

    /**
     * 并行执行多个工具调用。
     *
     * 流程：
     * 1. 为每个工具调用创建 Wrapper
     * 2. 使用 CompletableFuture 并行执行所有工具
     * 3. 使用 Semaphore 限制并发数
     * 4. 每个 try-catch 包裹，错误不传播
     * 5. 等待所有工具完成，返回结果列表
     *
     * @param toolCalls 工具调用列表
     * @param context 工具执行上下文
     * @return 工具调用结果列表（包含失败的结果）
     */
    public CompletionStage<List<AiToolCallResult>> executeTools(
            List<AiToolCall> toolCalls,
            IToolExecuteContext context) {

        // 1. 创建工具调用包装器
        List<ToolCallWrapper> wrappers = toolCalls.stream()
            .map(ToolCallWrapper::new)
            .toList();

        // 2. 并行执行所有工具
        List<CompletionStage<ToolCallWrapper>> futures = wrappers.stream()
            .map(wrapper -> executeToolWithPermit(wrapper, context))
            .toList();

        // 3. 等待所有工具完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> wrappers.stream()
                .map(wrapper -> wrapper.getResult())
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * 使用信号量限制执行单个工具（错误隔离）。
     */
    private CompletionStage<ToolCallWrapper> executeToolWithPermit(
            ToolCallWrapper wrapper,
            IToolExecuteContext context) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取信号量许可
                semaphore.acquire();
                try {
                    // 更新状态为 RUNNING
                    wrapper.setStatus(ToolCallStatus.RUNNING);
                    wrapper.setStartedAt(Instant.now());

                    // 执行工具（per-tool try-catch，错误不传播）
                    AiToolCallResult result = toolManager.callTool(
                        wrapper.getToolCall(),
                        context
                    ).join();

                    // 更新状态为 COMPLETED
                    wrapper.setStatus(ToolCallStatus.COMPLETED);
                    wrapper.setResult(result);
                    wrapper.setCompletedAt(Instant.now());

                    return wrapper;
                } finally {
                    // 释放信号量许可
                    semaphore.release();
                }
            } catch (Exception e) {
                // 更新状态为 ERROR（错误隔离）
                wrapper.setStatus(ToolCallStatus.ERROR);
                wrapper.setError(e);
                wrapper.setCompletedAt(Instant.now());

                // 创建失败结果
                AiToolCallResult errorResult = new AiToolCallResult();
                errorResult.setId(wrapper.getToolCall().getId());
                errorResult.setStatus(ToolCallStatus.ERROR);
                errorResult.setError(new AiToolError(e.getMessage()));

                wrapper.setResult(errorResult);

                return wrapper;
            }
        }, executor);
    }

    /**
     * 关闭执行器。
     */
    public void shutdown() {
        executor.shutdown();
    }
}
```

**使用示例**：

```java
// 创建并行工具执行器
ParallelToolExecutor executor = new ParallelToolExecutor(toolManager, 5); // 最多 5 个并行

// 并行执行多个工具
List<AiToolCall> toolCalls = List.of(
    createToolCall("read-file", Map.of("path", "file1.txt")),
    createToolCall("read-file", Map.of("path", "file2.txt")),
    createToolCall("search-files", Map.of("pattern", "*.java"))
);

executor.executeTools(toolCalls, context)
    .thenAccept(results -> {
        // 结果列表包含所有工具的结果，包括失败的
        for (AiToolCallResult result : results) {
            if (result.getStatus() == ToolCallStatus.ERROR) {
                System.out.println("Tool failed: " + result.getId());
            } else {
                System.out.println("Tool succeeded: " + result.getId());
            }
        }
    });
```

**设计原则**：

1. **Per-tool 状态追踪**：每个工具有独立的状态（PENDING → RUNNING → COMPLETED/ERROR）
2. **错误隔离**：per-tool try-catch，一个工具失败不影响其他工具（与 OpenCode 的 Promise.all + per-tool catch 一致）
3. **结果聚合**：收集所有结果，包括部分失败的结果
4. **并发限制**：使用 Semaphore 限制最大并发数（默认 5，最大 25，与 OpenCode 一致）
5. **虚拟线程**：使用虚拟线程提高并发效率（Java 21+）
6. **JoinType**：可通过配置决定是等待所有工具完成（ALL_SUCCESS/ANY_SUCCESS/ALL_COMPLETED），类似 Nop ParallelTaskStep

**Agent 配置扩展**（agent.xdef）：

```xml
<agent name="my-agent" toolCallMode="AUTO">  <!-- NATIVE | XML | AUTO -->
    <!-- ... -->
</agent>
```

### 3.4 ReAct Agent 实现

> 灵感来源：AgentScope-Java（ReActAgent.reasoning + acting）、Solon-AI（ReActAgent + SolonFlow 图）、Spring-AI-Alibaba（ReactAgent + AgentToolNode）、OpenCode（AbortController + abort.aborted 检查）、Nop 平台（ICancelToken 级联取消）

```java
/**
 * ReAct Agent —— 基于 Reasoning-Acting 循环的核心 Agent 实现。
 *
 * 灵感来源：
 * - AgentScope-Java ReActAgent: reasoning(iter) + acting(iter) + summarizing()，Mono 响应式
 * - Solon-AI ReActAgent: SolonFlow 图编排 (Reason → ExclusiveGateway → Action → End)
 * - Spring-AI-Alibaba ReactAgent: AgentLlmNode + AgentToolNode + Hook 系统
 *
 * 下一代创新：
 * - 不依赖 Reactor，纯 CompletionStage + Flow.Publisher
 * - 桥接已有的 IChatService（而非引入新的 IChatModel）
 * - 桥接已有的 IToolManager（而非引入新的 Toolkit）
 * - Hook 系统使用事件模式（agent.xdef 定义）
 * - PlanNotebook 用已有 AgentPlan 体系
 * - 集成 ToolCallModeAdapter（v3 新增）
 * - 集成错误处理和 AI 修复（v3 新增）
 */
public class ReActAgent implements IAgent {

    // ===== 依赖：复用已有接口 =====
    private final IChatService chatService;        // 来自 nop-ai-api，LLM 调用
    private final IToolManager toolManager;         // 来自 nop-ai-toolkit，工具执行
    private final IAiMemoryStore memoryStore;       // 来自 nop-ai-agent，记忆存储
    private final AiMemoryConfig memoryConfig;      // 来自 nop-ai-agent，记忆配置
    private final ToolCallModeAdapter toolCallAdapter; // v3 新增
    private final List<AgentHookModel> hooks;       // Hook 配置（来自 agent.xdef）

    // ===== 配置 =====
    private final String name;
    private final String systemPrompt;
    private final int maxIterations;                // 默认 10
    private final ToolCallMode toolCallMode;        // v3 新增
    private final LlmModel llmConfig;               // 来自 nop-ai-core，LLM 配置

    // ===== 核心状态 =====
    private final AtomicBoolean interruptFlag = new AtomicBoolean(false);
    private final ReActContext currentContext;       // 当前执行上下文

    // ===== 取消机制（v4 新增） =====
    // 取消是协作式的：通过 ICancelToken.cancel() 请求取消，Agent 在关键检查点检查并响应
    //灵感来源：
    // - OpenCode: AbortController + abort.aborted 检查（loop start + streaming chunks）
    // - AgentScope-Java: AtomicBoolean interruptFlag + checkInterruptedAsync() 每次迭代
    // - Nop 平台: ICancelToken 级联取消（子任务继承父任务取消令牌）

    // ===== 核心 ReAct 循环 =====

    @Override
    public CompletionStage<AgentResponse> chat(AgentRequest request) {
        // 1. 初始化上下文
        ReActContext ctx = new ReActContext(request);
        this.currentContext = ctx;

        // 2. 如果有挂起的工具结果，先处理
        if (hasPendingToolResults(request)) {
            return resumeFromToolResults(ctx, 0);
        }

        // 3. 进入 ReAct 循环
        return executeReActLoop(ctx, 0);
    }

    /**
     * ReAct 循环：Reasoning → [Acting → Reasoning → ...] → Result
     *
     * 流程（与 AgentScope-Java ReActAgent 一致）：
     * 1. 检查 maxIterations
     * 2. 检查取消（v4 新增：5 个检查点）
     * 3. 准备 ChatRequest（调用 ToolCallModeAdapter）
     * 4. 调用 IChatService（LLM 推理）
     * 5. 提取工具调用（根据 ToolCallMode）
     * 6. 有工具调用 → 执行工具 → 回到 1
     * 7. 无工具调用 → 返回结果
     *
     * v3 新增：
     * - 集成 ToolCallModeAdapter
     * - 集成事件触发（before_reasoning/after_reasoning/before_acting/after_acting）
     * - 集成错误处理和 AI 修复分支
     *
     * v4 新增（Phase 5 跨语言研究）：
     * - 取消机制：ICancelToken 协作式取消，5 个检查点
     * - 检查点位置：loop start、before LLM call、during streaming chunks、before tool execution、after tool execution
     * - in-flight 工具：等待完成但丢弃结果
     */
    private CompletionStage<AgentResponse> executeReActLoop(ReActContext ctx, int iteration) {
        // 检查迭代上限
        if (iteration >= maxIterations) {
            return summarizeAndReturn(ctx);
        }

        // 【检查点 1】Loop start：检查取消
        if (ctx.getCancelToken().isCancelled() || interruptFlag.get()) {
            return CompletableFuture.completedFuture(
                AgentResponse.cancelled(getAgentId(), iteration, "cancelled_at_loop_start"));
        }

        // 1. 构建请求（使用 ToolCallModeAdapter）
        ChatRequest chatReq = buildChatRequest(ctx);

        // 2. 触发 before_reasoning 事件
        return fireEvent("before_reasoning", ctx, chatReq)
            .thenCompose(modifiedReq -> {
                // 【检查点 2】Before LLM call：检查取消
                if (ctx.getCancelToken().isCancelled() || interruptFlag.get()) {
                    return CompletableFuture.completedFuture(
                        AgentResponse.cancelled(getAgentId(), iteration, "cancelled_before_llm_call"));
                }

                // 准备工具调用模式
                ToolCallMode mode = request.getToolCallMode();
                return toolCallAdapter.prepareRequest(modifiedReq, mode);
            })
            .thenCompose(preparedReq -> {
                // 3. 调用 LLM（传入 ICancelToken）
                // 【检查点 3】During streaming：ICancelToken 会在流式处理中自动检查
                return chatService.callAsync(preparedReq, ctx.getCancelToken());
            })
            .thenCompose(response -> {
                ChatAssistantMessage msg = response.getMessage();
                ctx.addAssistantMessage(msg);

                // 4. 触发 after_reasoning 事件
                return fireEvent("after_reasoning", ctx, msg);
            })
            .thenCompose(hookResult -> {
                ChatAssistantMessage msg = hookResult.getMessage();

                // 检查 Hook 是否要求停止
                if (hookResult.isStopRequested()) {
                    return CompletableFuture.completedFuture(
                        AgentResponse.fromMessage(msg, ctx.getIteration(), "stop_requested"));
                }

                // 5. 提取工具调用（根据 ToolCallMode）
                ToolCallMode mode = request.getToolCallMode();
                List<AiToolCall> toolCalls = toolCallAdapter.extractToolCalls(msg, mode);

                // 没有工具调用 → 完成
                if (toolCalls.isEmpty()) {
                    return CompletableFuture.completedFuture(
                        AgentResponse.fromMessage(msg, ctx.getIteration(), "stop"));
                }

                // 6. 有工具调用 → Acting
                return executeActing(ctx, toolCalls, iteration)
                    .thenCompose(v -> executeReActLoop(ctx, iteration + 1));
            });
    }

    /**
     * Acting 阶段：执行工具调用（含错误处理和 AI 修复）。
     *
     * 桥接 IToolManager：
     * 1. 转换为 toolkit 模型
     * 2. 调用 IToolManager.callTools()
     * 3. 处理结果（含失败处理）
     *
     * v3 新增：
     * - 集成错误处理流程（after_acting + RetryAdvisor）
     * - 集成 AI 修复分支
     * - 集成异常转 Bug 系统
     *
     * v4 新增（Phase 5 跨语言研究）：
     * - 【检查点 4】Before tool execution：检查取消
     * - 【检查点 5】After tool execution：检查取消
     * - in-flight 工具：如果工具正在执行，等待完成但丢弃结果
     */
    private CompletionStage<Void> executeActing(ReActContext ctx, List<AiToolCall> toolCalls, int iteration) {
        // 1. 触发 before_acting 事件
        return fireEvent("before_acting", ctx, toolCalls)
            .thenCompose(modifiedCalls -> {
                // 【检查点 4】Before tool execution：检查取消
                if (ctx.getCancelToken().isCancelled() || interruptFlag.get()) {
                    // 如果有正在执行的工具，等待完成但丢弃结果
                    // 这里简单地返回 cancelled，实际实现中可能需要跟踪正在执行的工具
                    return CompletableFuture.completedFuture(null);
                }

                // 2. 转换为 toolkit 模型
                AiToolCalls aiToolCalls = new AiToolCalls();
                aiToolCalls.setToolCalls(modifiedCalls);

                // 3. 调用 IToolManager（传入 ICancelToken，工具执行器内部会检查）
                return toolManager.callTools(aiToolCalls, ctx.getToolExecuteContext());
            })
            .thenApply(response -> {
                // 【检查点 5】After tool execution：检查取消
                if (ctx.getCancelToken().isCancelled() || interruptFlag.get()) {
                    // 丢弃工具执行结果
                    return null;
                }
                return response;
            })
            .thenCompose(response -> {
                // 4. 处理每个结果（含失败处理）
                List<ChatToolResponseMessage> toolResponses = new ArrayList<>();

                for (AiToolCallResult result : response.getResults()) {
                    // 4a. 触发 after_acting 事件
                    fireEvent("after_acting", ctx, result);

                    // 4b. 检查是否失败
                    if (result.getStatus() != ToolCallStatus.SUCCESS) {
                        // v3 新增：错误处理流程
                        return handleToolError(ctx, result, iteration);
                    }

                    // 4c. 成功 → 转换为消息
                    toolResponses.add(toToolResponse(result));
                }

                // 5. 写入上下文
                ctx.addToolResponses(toolResponses);
                return CompletableFuture.completedFuture(null);
            });
    }

    /**
     * 工具错误处理（v3 新增）。
     *
     * 流程：
     * 1. 记录到 AgentPlanError（attemptNumber++）
     * 2. 如果 attemptNumber <= maxRetries：调用 RetryAdvisor via call-agent
     * 3. RetryAdvisor 返回 suggestedAction：retry|modify_params|repair|skip|abort
     * 4. 对于 repair：创建隔离状态分支，让 AI 修复
     * 5. 对于 abort：触发 bug-report 工具（prompt 驱动）
     *
     * 灵感来源：
     * - agent-design.md: RetryAdvisor Agent 设计
     * - OpenCode: Compaction agent 修复流程
     * - DeepAgents: Error handling middleware
     */
    private CompletionStage<Void> handleToolError(ReActContext ctx, AiToolCallResult result, int iteration) {
        // 1. 记录到 AgentPlanError
        AgentPlanError error = new AgentPlanError();
        error.setErrorText(result.getError().getBody());
        error.setAttemptNumber(ctx.getErrorCount() + 1);
        error.setRelatedTaskNo(ctx.getCurrentTaskNo());
        ctx.addError(error);
        ctx.incrementErrorCount();

        // 2. 检查重试次数
        int maxRetries = ctx.getMaxRetries();
        if (error.getAttemptNumber() > maxRetries) {
            // 超过最大重试次数 → 触发 bug-report 工具
            return triggerBugReport(ctx, error);
        }

        // 3. 调用 RetryAdvisor Agent
        return callRetryAdvisor(ctx, error)
            .thenCompose(decision -> {
                // 4. 根据 RetryAdvisor 的建议执行
                return switch (decision.getSuggestedAction()) {
                    case RETRY -> retryTool(ctx, result);
                    case MODIFY_PARAMS -> retryWithModifiedParams(ctx, result, decision.getModifiedParams());
                    case REPAIR -> executeAiRepair(ctx, error);
                    case SKIP -> skipTool(ctx, result);
                    case ABORT -> triggerBugReport(ctx, error);
                };
            });
    }

    /**
     * 调用 RetryAdvisor Agent（v3 新增）。
     *
     * 灵感来源：agent-design.md
     */
    private CompletionStage<RetryDecision> callRetryAdvisor(ReActContext ctx, AgentPlanError error) {
        // 构建 call-agent 调用
        AiToolCall callAgentCall = new AiToolCall();
        callAgentCall.setToolName("call-agent");
        callAgentCall.setAgentId("retry-advisor");
        callAgentCall.setInput(buildRetryAdvisorInput(ctx, error));

        // 执行调用
        return toolManager.callTool(callAgentCall, ctx.getToolExecuteContext())
            .thenApply(result -> parseRetryDecision(result));
    }

    /**
     * AI 修复分支（v3 新增）。
     *
     * 流程：
     * 1. 创建隔离状态分支
     * 2. 让 AI 在分支中修复问题
     * 3. 只在修复成功时合并到主上下文
     *
     * 灵感来源：
     * - OpenCode: Git branch isolation pattern
     * - DeepAgents: State branching
     */
    private CompletionStage<Void> executeAiRepair(ReActContext ctx, AgentPlanError error) {
        // 1. 创建隔离分支
        ReActContext repairCtx = ctx.createBranch();

        // 2. 调用专门的 Repair Agent
        return callRepairAgent(repairCtx, error)
            .thenCompose(repairResult -> {
                // 3. 检查修复是否成功
                if (repairResult.isSuccess()) {
                    // 合并到主上下文
                    ctx.mergeBranch(repairCtx);
                    return CompletableFuture.completedFuture(null);
                } else {
                    // 修复失败 → 记录错误，继续
                    error.setResolution("AI repair failed: " + repairResult.getError());
                    return CompletableFuture.completedFuture(null);
                }
            });
    }

    /**
     * 触发 bug-report（v3 新增，v3 修正：不通过 IBugReporter 接口）。
     *
     * 流程：
     * 1. 标记 AgentPlanError.resolvedAt = null（未解决）
     * 2. 将错误信息注入上下文，AI 根据 prompt 指令决定调用 bug-report 工具
     * 3. bug-report 工具的具体实现（文件/JIRA/GitHub）由 .tool.xml 配置决定
     */
    private CompletionStage<Void> triggerBugReport(ReActContext ctx, AgentPlanError error) {
        // 1. 标记为未解决
        error.setResolvedAt(null);

        // 2. 注入触发指令 — AI 根据 prompt 决定调用 bug-report 工具
        ctx.addSystemInstruction(
            "工具执行反复失败且无法修复。请调用 bug-report 工具记录此错误，" +
            "包含错误描述、重试次数、关联任务编号和上下文信息。"
        );

        // 3. AI 会在下一轮 Reasoning 中自行调用 bug-report 工具
        // 不需要 IBugReporter 接口，触发时机由 prompt 控制
        return CompletableFuture.completedFuture(null);
    }
}
```

**ReActContext 类扩展（v4 新增）**：

```java
/**
 * ReAct 执行上下文 —— 每次会话的执行上下文。
 *
 * v4 新增：集成 ICancelToken 取消机制。
 *
 * 取消检查点（5 个）：
 * 1. Loop start（executeReActLoop 开始）
 * 2. Before LLM call（调用 IChatService 前）
 * 3. During streaming chunks（流式处理中，由 IChatService 内部检查）
 * 4. Before tool execution（executeActing 中，调用 IToolManager 前）
 * 5. After tool execution（executeActing 中，工具执行完成后）
 *
 * 灵感来源：
 * - OpenCode: AbortController + abort.aborted 检查
 * - AgentScope-Java: AtomicBoolean interruptFlag + checkInterruptedAsync()
 * - Nop 平台: ICancelToken 级联取消（通过 IToolExecuteContext 传递给工具执行器）
 */
public class ReActContext {

    // 原有字段
    private final AgentRequest request;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<ChatToolResponseMessage> toolResponses = new ArrayList<>();
    private final List<AgentPlanError> errors = new ArrayList<>();
    private final AgentPlan plan;
    private final String sessionId;

    // v4 新增：取消令牌
    private final ICancelToken cancelToken;

    // ... 其他原有方法

    /**
     * 获取取消令牌。
     */
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    /**
     * 检查是否被取消（便利方法）。
     */
    public boolean isCancelled() {
        return cancelToken.isCancelled();
    }

    /**
     * 请求取消（协作式）。
     *
     * 调用后，Agent 会在下一个检查点响应取消请求。
     * in-flight 工具会等待完成但丢弃结果。
     */
    public void cancel() {
        cancelToken.cancel();
    }
}
```

### 3.4.1 Agent 消息队列（v4 新增）

> 灵感来源：OpenCode（busy session 入队 + Promise callback）、AgentScope-Java（interrupt flag）、Nop Workflow（Command Queue 防止重入）
>
> **核心挑战**：
> - Agent 在执行 ReAct 循环时，如果收到新的用户请求，应该如何处理？
> - 有些消息需要立即中断当前执行（如紧急停止指令）
> - 有些消息可以排队等待当前循环完成后处理（如新的任务）
> - 需要区分这两种消息语义

**设计方案**：

```java
/**
 * 消息类型枚举。
 *
 * v4 新增。
 */
public enum MessageType {
    /** INTERRUPT：立即中断当前执行，插入消息，然后恢复 */
    INTERRUPT,

    /** QUEUED：排队等待当前循环迭代结束后再处理 */
    QUEUED
}

/**
 * Agent 消息 —— 封装消息类型、负载和响应 Future。
 *
 * v4 新增。
 *
 * 灵感来源：
 * - OpenCode: Promise callback 模式（新请求作为 Promise 回调，在 loop 结束后解析）
 * - AgentScope-Java: interruptFlag 用于立即中断
 */
public class AgentMessage {

    /** 消息类型 */
    private final MessageType type;

    /** 消息负载 */
    private final ChatMessage payload;

    /** 响应 Future（用于 QUEUED 消息） */
    private final CompletableFuture<AgentResponse> responseFuture;

    /** 创建时间戳 */
    private final Instant createdAt;

    public AgentMessage(MessageType type, ChatMessage payload) {
        this.type = type;
        this.payload = payload;
        this.responseFuture = type == MessageType.QUEUED
            ? new CompletableFuture<>()
            : null;
        this.createdAt = Instant.now();
    }

    // Getters
    public MessageType getType() { return type; }
    public ChatMessage getPayload() { return payload; }
    public CompletableFuture<AgentResponse> getResponseFuture() { return responseFuture; }
    public Instant getCreatedAt() { return createdAt; }
}

/**
 * Agent 会话 —— 管理消息队列和执行状态。
 *
 * v4 新增。
 *
 * 灵感来源：
 * - OpenCode: busy session 模式（session busy 时，新请求入队）
 * - Nop Workflow: Command Queue 防止重入
 */
public class AgentSession {

    /** 消息队列 */
    private final ConcurrentLinkedQueue<AgentMessage> messageQueue = new ConcurrentLinkedQueue<>();

    /** 是否正在执行 ReAct 循环 */
    private volatile boolean busy = false;

    /** 当前正在处理的消息 */
    private volatile AgentMessage currentMessage = null;

    /** Agent 实例 */
    private final IAgent agent;

    /** 取消令牌 */
    private final ICancelToken cancelToken;

    /**
     * 发送消息到 Agent。
     *
     * 流程：
     * 1. 如果是 INTERRUPT 消息：
     *    - 如果 agent 不 busy：立即处理
     *    - 如果 agent busy：取消当前执行，插入消息到队首，恢复执行
     * 2. 如果是 QUEUED 消息：
     *    - 如果 agent 不 busy：立即处理
     *    - 如果 agent busy：入队，等待当前循环结束
     */
    public CompletionStage<AgentResponse> sendMessage(AgentMessage message) {
        MessageType type = message.getType();

        if (type == MessageType.INTERRUPT) {
            return handleInterruptMessage(message);
        } else {
            return handleQueuedMessage(message);
        }
    }

    /**
     * 处理 INTERRUPT 消息。
     */
    private CompletionStage<AgentResponse> handleInterruptMessage(AgentMessage message) {
        if (!busy) {
            // Agent 不忙，立即处理
            return executeMessage(message);
        } else {
            // Agent 忙，取消当前执行
            cancelToken.cancel();

            // 将消息插入队首
            messageQueue.add(message);

            // 等待当前执行取消并恢复
            return waitForNextExecution();
        }
    }

    /**
     * 处理 QUEUED 消息。
     */
    private CompletionStage<AgentResponse> handleQueuedMessage(AgentMessage message) {
        if (!busy && messageQueue.isEmpty()) {
            // Agent 不忙且队列为空，立即处理
            return executeMessage(message);
        } else {
            // Agent 忙或队列有其他消息，入队
            messageQueue.add(message);

            // 返回 Future，等待循环结束后解析
            return message.getResponseFuture();
        }
    }

    /**
     * 执行消息。
     */
    private CompletionStage<AgentResponse> executeMessage(AgentMessage message) {
        currentMessage = message;
        busy = true;

        try {
            // 重置取消令牌（为新的执行）
            cancelToken.reset();

            // 构建 AgentRequest
            AgentRequest request = new AgentRequest();
            request.setSessionId(getSessionId());
            request.setMessages(List.of(message.getPayload()));

            // 调用 Agent
            return agent.chat(request)
                .whenComplete((response, error) -> {
                    busy = false;
                    currentMessage = null;

                    // 处理队列中的下一个消息
                    processNextMessage();

                    // 如果是 QUEUED 消息，解析 Future
                    if (message.getType() == MessageType.QUEUED) {
                        if (error != null) {
                            message.getResponseFuture().completeExceptionally(error);
                        } else {
                            message.getResponseFuture().complete(response);
                        }
                    }
                });
        } catch (Exception e) {
            busy = false;
            currentMessage = null;
            processNextMessage();

            if (message.getType() == MessageType.QUEUED) {
                message.getResponseFuture().completeExceptionally(e);
            }

            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 处理队列中的下一个消息。
     */
    private void processNextMessage() {
        AgentMessage next = messageQueue.poll();
        if (next != null) {
            // 异步处理下一个消息
            CompletableFuture.runAsync(() -> {
                executeMessage(next);
            });
        }
    }

    /**
     * 等待下一次执行（用于 INTERRUPT 消息）。
     */
    private CompletionStage<AgentResponse> waitForNextExecution() {
        // 创建一个新的 QUEUED 消息，等待循环结束
        CompletableFuture<AgentResponse> future = new CompletableFuture<>();
        AgentMessage queued = new AgentMessage(MessageType.QUEUED, null);
        queued.responseFuture = future;

        messageQueue.add(queued);

        return future;
    }

    // Getters
    public boolean isBusy() { return busy; }
    public AgentMessage getCurrentMessage() { return currentMessage; }
    public int getQueueSize() { return messageQueue.size(); }
}
```

**使用示例**：

```java
// 创建 AgentSession
AgentSession session = new AgentSession(myAgent, new CancelToken());

// 发送普通消息（QUEUED）
AgentMessage taskMsg = new AgentMessage(MessageType.QUEUED, userMessage);
session.sendMessage(taskMsg)
    .thenAccept(response -> {
        System.out.println("Task completed: " + response.getContent());
    });

// 发送紧急中断消息（INTERRUPT）
AgentMessage interruptMsg = new AgentMessage(
    MessageType.INTERRUPT,
    ChatUserMessage.of("STOP NOW! Emergency stop.")
);
session.sendMessage(interruptMsg)
    .thenAccept(response -> {
        System.out.println("Agent interrupted: " + response.getContent());
    });
```

**设计原则**：

1. **协作式取消**：INTERRUPT 消息通过 ICancelToken.cancel() 请求取消，Agent 在检查点响应
2. **队列非阻塞**：QUEUED 消息通过 CompletableFuture 返回，不会阻塞调用方
3. **顺序处理**：队列中的消息按顺序处理（FIFO）
4. **状态可见**：通过 isBusy()、getQueueSize() 可以查询会话状态

### 3.5 Agent Hook 系统（v3 重写：事件模式）

> 灵感来源：agent.xdef（事件模式定义）、AgentScope-Java（Hook 事件）、Solon-AI（ChatInterceptor）、VoltAgent（Middleware 栈）
>
> **v3 关键变更**：使用 agent.xdef 定义的事件模式，而非固定的 4 方法接口。提供便利基类简化实现。

```java
/**
 * Agent Hook 配置模型（来自 agent.xdef）。
 *
 * v3 关键发现：Hook 使用 **事件模式匹配**，而非固定方法。
 *
 * 示例：
 * <hooks>
 *     <on id="plan-hook" event="before_reasoning">
 *         inject-plan-progress
 *     </on>
 *     <on id="error-hook" event="after_acting" status="failure">
 *         handle-tool-error
 *     </on>
 * </hooks>
 */
public class AgentHookModel {
    private String id;
    private String event;              // 事件模式：before_reasoning, after_acting, plan_task_complete, etc.
    private IEvalFunction body;       // Hook 处理逻辑（XPL 表达式或脚本）
    private int priority = 0;         // 优先级（越小越先执行）
}

/**
 * 便利 Hook 基类 —— 将事件模式映射到类型化方法。
 *
 * v3 新增：为 Java 代码提供类型安全的 Hook 实现。
 *
 * 设计思路：
 * - AgentHookBase 维护事件 → 方法的映射
 * - 注册 Hook 时，自动注册为事件处理器
 * - 例如：beforeReasoning() 方法自动注册为 before_reasoning 事件的处理器
 */
public abstract class AgentHookBase {

    private final Map<String, Consumer<Object[]>> eventHandlers = new HashMap<>();

    /**
     * 注册事件处理器。
     */
    protected void registerHandler(String event, Consumer<Object[]> handler) {
        eventHandlers.put(event, handler);
    }

    /**
     * 触发事件。
     */
    public void onEvent(String event, Object... args) {
        Consumer<Object[]> handler = eventHandlers.get(event);
        if (handler != null) {
            handler.accept(args);
        }
    }

    /**
     * 便利方法：before_reasoning 事件。
     */
    protected void beforeReasoning(AgentRequest request, ChatRequest chatRequest) {
        // 默认不实现，子类覆盖
    }

    /**
     * 便利方法：after_reasoning 事件。
     */
    protected void afterReasoning(AgentRequest request, ChatAssistantMessage message) {
        // 默认不实现，子类覆盖
    }

    /**
     * 便利方法：before_acting 事件。
     */
    protected void beforeActing(AgentRequest request, List<AiToolCall> toolCalls) {
        // 默认不实现，子类覆盖
    }

    /**
     * 便利方法：after_acting 事件。
     */
    protected void afterActing(AgentRequest request, AiToolCallResult result) {
        // 默认不实现，子类覆盖
    }

    /**
     * 初始化事件映射（构造函数中调用）。
     */
    protected void initializeEventHandlers() {
        registerHandler("before_reasoning", args ->
            beforeReasoning((AgentRequest) args[0], (ChatRequest) args[1]));
        registerHandler("after_reasoning", args ->
            afterReasoning((AgentRequest) args[0], (ChatAssistantMessage) args[1]));
        registerHandler("before_acting", args ->
            beforeActing((AgentRequest) args[0], (List<AiToolCall>) args[1]));
        registerHandler("after_acting", args ->
            afterActing((AgentRequest) args[0], (AiToolCallResult) args[1]));
    }
}

/**
 * Hook 事件类型定义（v3 新增）。
 *
 * 支持的事件：
 * - before_reasoning: LLM 推理前
 * - after_reasoning: LLM 推理后
 * - before_acting: 工具执行前
 * - after_acting: 工具执行后
 * - plan_task_complete: Plan 任务完成
 * - plan_phase_complete: Plan 阶段完成
 * - error_occurred: 错误发生
 * - tool_call_failed: 工具调用失败
 * - compression_needed: 需要压缩
 */
public class AgentEventTypes {
    public static final String BEFORE_REASONING = "before_reasoning";
    public static final String AFTER_REASONING = "after_reasoning";
    public static final String BEFORE_ACTING = "before_acting";
    public static final String AFTER_ACTING = "after_acting";
    public static final String PLAN_TASK_COMPLETE = "plan_task_complete";
    public static final String PLAN_PHASE_COMPLETE = "plan_phase_complete";
    public static final String ERROR_OCCURRED = "error_occurred";
    public static final String TOOL_CALL_FAILED = "tool_call_failed";
    public static final String COMPRESSION_NEEDED = "compression_needed";
}

/**
 * Hook 事件触发器（v3 新增）。
 *
 * 在 ReActAgent 中使用：
 * 1. 从 agent.xdef 加载 AgentHookModel 列表
 * 2. 按 priority 排序
 * 3. 触发事件时，调用匹配的 Hook
 */
public class HookEventTrigger {

    private final List<AgentHookModel> hooks;

    public HookEventTrigger(List<AgentHookModel> hooks) {
        this.hooks = hooks.stream()
            .sorted(Comparator.comparingInt(AgentHookModel::getPriority))
            .toList();
    }

    /**
     * 触发事件。
     *
     * @param event 事件名（如 "before_reasoning"）
     * @param args 事件参数
     */
    public CompletionStage<Object> trigger(String event, Object... args) {
        CompletionStage<Object> result = CompletableFuture.completedFuture(null);

        for (AgentHookModel hook : hooks) {
            // 检查事件是否匹配
            if (isEventMatch(hook.getEvent(), event)) {
                // 执行 Hook
                result = result.thenCompose(v ->
                    executeHook(hook, args));
            }
        }

        return result;
    }

    /**
     * 检查事件是否匹配（支持模式匹配）。
     *
     * 示例：
     * - event="before_reasoning" 精确匹配 "before_reasoning"
     * - event="after_acting*" 匹配 "after_acting", "after_acting_success", etc.
     */
    private boolean isEventMatch(String pattern, String event) {
        if (pattern.equals(event)) {
            return true;
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return event.startsWith(prefix);
        }
        return false;
    }

    /**
     * 执行 Hook（IEvalFunction）。
     */
    private CompletionStage<Object> executeHook(AgentHookModel hook, Object[] args) {
        return CompletableFuture.supplyAsync(() ->
            hook.getBody().invoke(args));
    }
}
```

**示例：Plan Hook 实现（用于 Ralph Loop）**

```java
/**
 * Plan Hook —— 驱动 Agent 执行 Plan（v3 新增）。
 *
 * 功能：
 * - before_reasoning: 注入当前 Plan 进度到 system prompt
 * - after_acting: 检查当前任务是否完成，决定是否继续下一个任务
 *
 * 用于 Ralph Loop：Plan 驱动 Agent 循环，直到所有任务完成。
 */
public class PlanHook extends AgentHookBase {

    private final AgentPlan plan;

    public PlanHook(AgentPlan plan) {
        this.plan = plan;
        initializeEventHandlers();
    }

    @Override
    protected void beforeReasoning(AgentRequest request, ChatRequest chatRequest) {
        // 注入 Plan 进度
        String planHint = buildPlanHint(plan);
        String newSystemPrompt = chatRequest.getSystemPrompt() + "\n\n" + planHint;
        chatRequest.setSystemPrompt(newSystemPrompt);
    }

    @Override
    protected void afterActing(AgentRequest request, AiToolCallResult result) {
        // 检查当前任务是否完成
        String currentTaskNo = request.getPlan().getCurrentTaskNo();

        if (isTaskComplete(result)) {
            // 标记任务为完成
            AgentPlanTask task = plan.findTask(currentTaskNo);
            if (task != null) {
                task.setStatus(AgentExecStatus.COMPLETED);
                task.setCompletedAt(Instant.now());
            }

            // 检查是否还有任务
            if (plan.hasNextTask()) {
                // Ralph Loop: 继续下一个任务
                String nextTaskNo = plan.getNextTaskNo();
                triggerReReason(nextTaskNo);
            }
        }
    }

    /**
     * 触发重新推理（Ralph Loop）。
     *
     * 实现方式：返回 HookResult.reReason() 或通过 Hook 事件控制流。
     */
    private void triggerReReason(String nextTaskNo) {
        // 通过 Hook 事件触发重新推理
        // 例如：设置标志位，在 ReActAgent 中检测并继续循环
    }

    private String buildPlanHint(AgentPlan plan) {
        // 构建 Plan 进度提示文本
        return String.format("""
            Current Plan Progress:
            Phase: %s
            Current Task: %s
            Completed Tasks: %d / %d
            """,
            plan.getCurrentPhase(),
            plan.getCurrentTaskTitle(),
            plan.getCompletedTaskCount(),
            plan.getTotalTaskCount()
        );
    }

    private boolean isTaskComplete(AiToolCallResult result) {
        // 判断当前任务是否完成（可以根据工具调用结果判断）
        return result.getStatus() == ToolCallStatus.SUCCESS;
    }
}
```

### 3.6 错误处理与自动修复（v3 新增）

> 灵感来源：agent-design.md（RetryAdvisor Agent）、OpenCode（Compaction agent）、DeepAgents（Error handling middleware）

**核心设计**：

1. **错误处理流程**（在 ReActAgent.executeActing 中）：
   - 工具失败 → 记录到 AgentPlanError（attemptNumber++）
   - 如果 attemptNumber <= maxRetries → 调用 RetryAdvisor Agent
   - RetryAdvisor 返回 suggestedAction：retry | modify_params | repair | skip | abort
   - 根据建议执行相应操作

2. **RetryAdvisor Agent**（来自 agent-design.md）：
   - 输入：errorType, errorMessage, toolName, arguments, retryCount
   - 输出：{shouldRetry, reason, suggestedAction, modifiedParams}
   - 通过 `call-agent` 工具调用

3. **AI 修复分支**：
   - 创建隔离状态分支（ReActContext.createBranch()）
   - 让 AI 在分支中修复问题
   - 只在修复成功时合并到主上下文

4. **异常转 Bug 系统**：
   - RetryAdvisor 返回 abort 或 maxRetries 超过 → 触发 bug-report 工具
   - bug-report 是一个 .tool.xml 工具 + markdown 指令，**不定义强类型接口**
   - 触发时机由 Hook 事件 + prompt 指令控制，不是接口回调
   - 不同后端（文件、JIRA、GitHub Issues）通过配置切换，不是接口实现

> **设计理念**：AI Agent 框架中的很多行为（bug 上报、重试策略、日志记录）靠 **prompt + tool.xml** 驱动，而非强类型接口。核心区别在于：
> - **需要核心抽象的**（ISkill、IContextCompressor）：多个框架验证过的概念，保留接口
> - **靠自然语言驱动的**（bug 上报、advisor 调用）：tool.xml + markdown 触发指令即可，不需要 IBugReporter 接口层次
> - **关键设计点是触发时机**：什么时候 AI 判断该报 bug？由 error_handler Hook 的 prompt 决定

**bug-report 工具定义示例（tool.xml）**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tool name="bug-report" xmlns:xdef="http://www.canonical-entropy.com/xdef">
  <description>
    当工具执行反复失败、AI 修复也无法解决时，将错误信息记录为 bug 报告。
    此工具只在 RetryAdvisor 返回 abort 或超过最大重试次数时触发。
  </description>
  <input xdef:name="BugReportInput">
    <title xdef:type="String" xdef:mandatory="true">Bug 标题</title>
    <errorText xdef:type="String" xdef:mandatory="true">错误描述</errorText>
    <attemptNumber xdef:type="int">重试次数</attemptNumber>
    <relatedTaskNo xdef:type="String">关联的 Plan 任务编号</relatedTaskNo>
    <context xdef:type="String">错误发生的上下文信息</context>
  </input>
  <output xdef:name="BugReportOutput">
    <bugId xdef:type="String">Bug 编号</bugId>
    <bugUrl xdef:type="String">Bug URL（如果提交到外部系统）</bugUrl>
  </output>
</tool>
```

**错误处理触发时机设计（核心）**：

```java
/**
 * 错误处理触发时机 —— 这是错误处理设计的核心，不是接口层次。
 *
 * 触发链：Tool 失败 → error_handler Hook → prompt 决策 → 执行动作
 * 整个链路由 Hook 事件 + prompt 驱动，不依赖 IBugReporter 接口。
 *
 * 灵感来源：
 * - OpenCode: 错误处理由 agent loop 的 prompt 指令驱动
 * - AgentScope-Java: Hook.onEvent() 控制流（stopAgent/gotoReasoning）
 * - DeepAgents: Middleware 栈决定处理策略
 */
public enum ErrorTriggerPoint {
    /** 工具执行失败时触发 */
    ON_TOOL_ERROR,

    /** 重试次数超过阈值时触发 */
    ON_MAX_RETRIES_EXCEEDED,

    /** AI 修复分支失败时触发 */
    ON_REPAIR_FAILED,

    /** 同一任务反复失败（循环检测）时触发 */
    ON_REPEATED_FAILURE
}

/**
 * 触发时机在 Hook 中的配置（agent.xdef 事件模式）。
 *
 * 示例：在 after_acting Hook 中配置错误处理策略。
 * Hook 的 body 是一个 IEvalFunction（XLang 表达式），
 * 通过 prompt 指令告诉 AI 如何处理错误。
 *
 * 这不需要 IBugReporter 接口 — AI 根据 prompt 指令
 * 决定是重试、修复、还是调用 bug-report 工具。
 */
// agent.xdef 配置示例（XML）：
// <hook event="after_acting" body="
//   if (result.status == ERROR) {
//     if (ctx.errorCount > MAX_RETRIES) {
//       // 触发 bug-report 工具（由 AI 决定调用）
//       ctx.addInstruction('工具执行反复失败，请调用 bug-report 工具记录此错误');
//     } else {
//       // 触发重试（由 AI 决定是否重试）
//       ctx.addInstruction('工具执行失败，请分析原因并决定是否重试');
//     }
//   }
// " />
```

> **注意**：本节聚焦于业务层面的错误处理（工具失败后的AI自修复、异常转bug等）。系统层面的容错处理（重试、断路器、循环检测、超时管理等）详见 **§3.12 容错处理体系**。

### 3.7 Skill 系统（v3 新增）

> 灵感来源：Solon-AI（Skill + SkillDesc + isSupported）、OpenCode（skill tool + SKILL.md）、DeepAgents（SkillsMiddleware）

**核心设计**：

1. **ISkill 接口**：
   - getName(): 技能名称
   - getDescription(): 技能描述
   - isSupported(AgentRequest): 是否激活（根据上下文动态判断）
   - getInstruction(AgentRequest): 动态指令（注入到 system prompt）
   - getTools(): 技能提供的工具
   - getHooks(): 技能注册的 Hook

2. **PlanSkill 实现**：
   - getName() → "plan"
   - getHooks() → 返回 PlanHook（注册 before_reasoning/after_acting 事件）
   - PlanHook 实现 Ralph Loop：after_acting 检查任务完成 → 继续下一个任务

3. **SkillManager**：
   - 从 `/nop/skills` 发现技能
   - 加载技能并调用 isSupported() 判断是否激活
   - 将激活技能的 tools 注册到 IToolExecutorProvider
   - 将激活技能的 hooks 注册到 Agent

```java
/**
 * Skill 接口（v3 新增）。
 *
 * 灵感来源：
 * - Solon-AI: Skill（isSupported + instruction + tools）
 * - OpenCode: skill tool（加载目录下的 AGENTS.md）
 * - DeepAgents: SkillsMiddleware（skill-based enhancements）
 *
 * 下一代创新：
 * - 技能通过 getHooks() 注册 Agent Hook
 * - 技能可以驱动 Agent 循环（如 PlanSkill 驱动 Ralph Loop）
 * - 技能可以提供工具（getTools()）
 */
public interface ISkill {

    /**
     * 技能名称。
     */
    String getName();

    /**
     * 技能描述。
     */
    String getDescription();

    /**
     * 是否激活（根据上下文动态判断）。
     *
     * @param context Agent 请求上下文
     * @return true 表示激活
     */
    boolean isSupported(AgentRequest context);

    /**
     * 动态指令（注入到 system prompt）。
     *
     * @param context Agent 请求上下文
     * @return 指令文本
     */
    String getInstruction(AgentRequest context);

    /**
     * 技能提供的工具。
     *
     * @return 工具执行器列表
     */
    List<IToolExecutor> getTools();

    /**
     * 技能注册的 Hook（v3 关键）。
     *
     * @return AgentHookModel 列表
     */
    List<AgentHookModel> getHooks();
}

/**
 * Plan Skill —— 驱动 Agent 执行 Plan（v3 新增）。
 *
 * 用于 Ralph Loop：Plan 驱动 Agent 循环，直到所有任务完成。
 *
 * 功能：
 * - getHooks(): 返回 PlanHook（注册 before_reasoning/after_acting 事件）
 * - getInstruction(): 注入 Plan 进度提示
 * - getTools(): 提供 manage-plan 工具（读写 Plan）
 */
public class PlanSkill implements ISkill {

    @Override
    public String getName() {
        return "plan";
    }

    @Override
    public String getDescription() {
        return "Plan-driven task execution with Ralph Loop support";
    }

    @Override
    public boolean isSupported(AgentRequest context) {
        // 如果 Agent 有 Plan 配置，则激活
        return context.getPlan() != null;
    }

    @Override
    public String getInstruction(AgentRequest context) {
        AgentPlan plan = context.getPlan();
        return String.format("""
            You are executing a structured plan.
            - Follow the plan phases and tasks in order.
            - Complete each task before moving to the next.
            - Use the `manage-plan` tool to update task status.
            - Plan Goal: %s
            """,
            plan.getGoal()
        );
    }

    @Override
    public List<IToolExecutor> getTools() {
        return List.of(new ManagePlanExecutor());
    }

    @Override
    public List<AgentHookModel> getHooks() {
        // 注册 PlanHook
        AgentHookModel hook = new AgentHookModel();
        hook.setId("plan-hook");
        hook.setEvent("*");  // 匹配所有事件
        hook.setBody(createPlanHookScript());
        hook.setPriority(100);

        return List.of(hook);
    }

    /**
     * 创建 Plan Hook 脚本（XPL 表达式）。
     */
    private IEvalFunction createPlanHookScript() {
        return (event, agentRt) -> {
            // 实现 PlanHook 逻辑
            // - before_reasoning: 注入 Plan 进度
            // - after_acting: 检查任务完成，继续下一个任务
            return null;
        };
    }
}

/**
 * Manage Plan 工具执行器（PlanSkill 提供）。
 *
 * 功能：
 * - 读取 Plan 状态
 * - 更新 Plan 任务状态
 * - 添加/删除 Plan 任务
 */
public class ManagePlanExecutor implements IToolExecutor {

    @Override
    public String getToolName() {
        return "manage-plan";
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(
        AiToolCall toolCall,
        IToolExecuteContext context
    ) {
        // 解析操作类型：read | write
        String action = toolCall.getAttribute("action");

        return switch (action) {
            case "read" -> readPlan(toolCall, context);
            case "write" -> writePlan(toolCall, context);
            default -> CompletableFuture.completedFuture(
                AiToolCallResult.error("Invalid action: " + action)
            );
        };
    }

    private CompletionStage<AiToolCallResult> readPlan(AiToolCall toolCall, IToolExecuteContext context) {
        // 读取 Plan 文件
        Path planFile = getPlanFile(context);
        AgentPlan plan = loadPlan(planFile);

        String output = formatPlan(plan);
        return CompletableFuture.completedFuture(AiToolCallResult.success(output));
    }

    private CompletionStage<AiToolCallResult> writePlan(AiToolCall toolCall, IToolExecuteContext context) {
        // 更新 Plan 文件
        AgentPlan updatedPlan = parsePlan(toolCall.getInput());
        Path planFile = getPlanFile(context);
        savePlan(planFile, updatedPlan);

        String output = "Plan updated";
        return CompletableFuture.completedFuture(AiToolCallResult.success(output));
    }
}

/**
 * Skill Manager —— 管理 Agent 的技能（v3 新增）。
 *
 * 功能：
 * - 从 `/nop/skills` 发现技能
 * - 加载技能并判断是否激活
 * - 注册激活技能的 tools 和 hooks
 */
public class SkillManager {

    private final Map<String, ISkill> skills = new ConcurrentHashMap<>();
    private final IToolExecutorProvider toolProvider;
    private final IAgentHookRegistry hookRegistry;

    public SkillManager(
        IToolExecutorProvider toolProvider,
        IAgentHookRegistry hookRegistry
    ) {
        this.toolProvider = toolProvider;
        this.hookRegistry = hookRegistry;
        loadSkills();
    }

    /**
     * 从 `/nop/skills` 加载技能。
     */
    private void loadSkills() {
        // 扫描技能目录
        List<Path> skillDirs = listSkillDirectories();

        for (Path skillDir : skillDirs) {
            ISkill skill = loadSkill(skillDir);
            if (skill != null) {
                skills.put(skill.getName(), skill);
            }
        }
    }

    /**
     * 加载单个技能。
     */
    private ISkill loadSkill(Path skillDir) {
        // 读取 SKILL.md 或 skill.xml
        // 解析技能元数据
        // 创建 ISkill 实例
        // ...
        return null;  // 示例
    }

    /**
     * 激活技能（根据 Agent 请求上下文）。
     */
    public void activateSkills(AgentRequest request) {
        for (ISkill skill : skills.values()) {
            if (skill.isSupported(request)) {
                // 注册工具
                for (IToolExecutor tool : skill.getTools()) {
                    toolProvider.registerExecutor(tool);
                }

                // 注册 Hook
                for (AgentHookModel hook : skill.getHooks()) {
                    hookRegistry.registerHook(hook);
                }

                log.info("Activated skill: {}", skill.getName());
            }
        }
    }

    /**
     * 获取激活技能的指令。
     */
    public String getActiveInstructions(AgentRequest request) {
        return skills.values().stream()
            .filter(skill -> skill.isSupported(request))
            .map(skill -> skill.getInstruction(request))
            .collect(Collectors.joining("\n\n"));
    }
}
```

### 3.8 Memory 增强

> 灵感来源：AgentScope-Java（Memory + LongTermMemory）、OpenCode（SQL Session + compaction.ts）、DeepAgents（SummarizationMiddleware）
>
> **v3 变更**：增加 ChatMessage 适配 + 自动压缩 + 工具结果精简

```java
/**
 * Agent Memory —— 在已有 IAiMemoryStore 上的增强。
 *
 * 已有：IAiMemoryStore（CRUD）+ AiMemoryConfig（压缩配置）
 * 新增：ChatMessage 适配 + 自动压缩 + 工具结果精简
 *
 * 灵感来源：
 * - AgentScope-Java: InMemoryMemory（addMessage/getMessages）+ LongTermMemory（语义搜索）
 * - OpenCode: CompactionAgent（40K token 保护窗口 + LLM 摘要）
 * - DeepAgents: SummarizationMiddleware（按 token 阈值触发压缩）
 */
public interface IAgentMemory {

    /** 添加 ChatMessage（复用 nop-ai-api 类型） */
    void addMessage(ChatMessage message);

    /** 获取所有消息 */
    List<ChatMessage> getMessages();

    /** 获取准备发送给 LLM 的消息列表（含自动压缩） */
    CompletionStage<List<ChatMessage>> prepareMessages(String systemPrompt, int maxTokens);

    /** 清空 */
    void clear();

    /** 消息数量 */
    int size();
}

/**
 * 上下文压缩策略。
 *
 * 灵感来源：
 * - OpenCode: CompactionAgent（自动压缩，保留 40K token 保护窗口）
 * - DeepAgents: SummarizationMiddleware（按 fraction 阈值触发）
 * - DeerFlow: SummarizationMiddleware（可选上下文缩减）
 *
 * 下一代创新：
 * - 利用已有的 AiMemoryConfig（summaryContextLength、trimRounds）
 * - 三种策略可切换：trim（截断）、summarize（摘要）、hybrid（混合）
 * - v3 新增：通过 CompressionAdvisor Agent 智能决策
 */
public interface IContextCompressor {
    boolean needsCompression(List<ChatMessage> messages, int maxTokens);
    CompletionStage<List<ChatMessage>> compress(List<ChatMessage> messages, IChatService chatService, int maxTokens);
}

/**
 * CompressionAdvisor Agent（v3 新增）。
 *
 * 功能：判断当前对话是否需要压缩。
 *
 * 灵感来源：agent-design.md
 */
public class CompressionAdvisorAgent {

    /**
     * 调用 CompressionAdvisor Agent 判断是否需要压缩。
     */
    public CompletionStage<CompressionDecision> adviseCompression(AgentRequest request) {
        // 构建 call-agent 调用
        AiToolCall callAgentCall = new AiToolCall();
        callAgentCall.setToolName("call-agent");
        callAgentCall.setAgentId("compression-advisor");
        callAgentCall.setInput(buildCompressionInput(request));

        // 执行调用
        return toolManager.callTool(callAgentCall, request.getContext())
            .thenApply(result -> parseCompressionDecision(result));
    }
}
```

**两阶段压缩算法（v4 新增）**：

> 灵感来源：OpenCode（两阶段 pruning + LLM summary）、AgentScope-Java（AutoContextMemory 6-level compression）、Nop Task（ITaskStateStore checkpoint pattern）
>
> **核心挑战**：
> - 长对话导致上下文 token 数量超过 LLM 上下文窗口限制
> - 需要在保留关键信息的同时压缩上下文
> - 需要保护最近的消息不被压缩，避免影响 Agent 对当前状态的判断

**设计方案**：

```java
/**
 * 两阶段上下文压缩器（v4 新增）。
 *
 * 灵感来源：
 * - OpenCode: 两阶段 pruning + LLM summary，40K 保护窗口
 * - AgentScope-Java: AutoContextMemory 6-level compression
 * - Nop Task: ITaskStateStore checkpoint pattern
 */
public class TwoStageContextCompressor implements IContextCompressor {

    /** LLM 服务 */
    private final IChatService chatService;

    /** Token 计数器 */
    private final ITokenCounter tokenCounter;

    /** 保护窗口大小（token 数，默认 40K，与 OpenCode 一致） */
    private final int protectionWindowSize;

    /** 压缩目标大小（token 数，默认为最大限制的 70%） */
    private final int targetSize;

    public TwoStageContextCompressor(IChatService chatService,
                                     ITokenCounter tokenCounter,
                                     int maxTokens) {
        this.chatService = chatService;
        this.tokenCounter = tokenCounter;
        this.protectionWindowSize = 40000; // 40K token 保护窗口
        this.targetSize = (int) (maxTokens * 0.7);
    }

    /**
     * 判断是否需要压缩。
     */
    @Override
    public boolean needsCompression(List<ChatMessage> messages, int maxTokens) {
        int totalTokens = countTokens(messages);
        return totalTokens > maxTokens * 0.8; // 超过 80% 触发压缩
    }

    /**
     * 压缩消息列表（两阶段）。
     *
     * 流程：
     * Stage 1 (Pruning): 移除旧工具输出、裁剪大结果、保留元数据
     * Stage 2 (LLM Summary): 如果仍超预算，调用 LLM 生成结构化摘要
     */
    @Override
    public CompletionStage<List<ChatMessage>> compress(
            List<ChatMessage> messages,
            IChatService chatService,
            int maxTokens) {

        // Stage 1: Pruning
        List<ChatMessage> pruned = prune(messages);

        // 检查是否还需要 Stage 2
        if (countTokens(pruned) <= targetSize) {
            return CompletableFuture.completedFuture(pruned);
        }

        // Stage 2: LLM Summary
        return summarize(pruned);
    }

    /**
     * Stage 1: Pruning（裁剪）。
     *
     * 策略：
     * 1. 移除工具输出（保留工具名称和状态）
     * 2. 裁剪过长的文本结果
     * 3. 保护最近 N tokens（40K 保护窗口）
     * 4. 保留系统消息和最近 2-3 轮对话
     */
    private List<ChatMessage> prune(List<ChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();

        // 1. 保护最近的消息（保护窗口）
        int protectedCount = countProtectedMessages(messages);
        int startIdx = messages.size() - protectedCount;

        // 2. 处理旧消息（裁剪）
        for (int i = 0; i < startIdx; i++) {
            ChatMessage msg = messages.get(i);

            // 系统消息：保留
            if (msg instanceof ChatSystemMessage) {
                result.add(msg);
            }
            // 用户消息：保留
            else if (msg instanceof ChatUserMessage) {
                result.add(msg);
            }
            // Assistant 消息：保留但裁剪工具输出
            else if (msg instanceof ChatAssistantMessage assistantMsg) {
                ChatAssistantMessage pruned = pruneAssistantMessage(assistantMsg);
                result.add(pruned);
            }
            // 工具响应消息：保留元数据，裁剪输出
            else if (msg instanceof ChatToolResponseMessage toolResp) {
                ChatToolResponseMessage pruned = pruneToolResponse(toolResp);
                result.add(pruned);
            }
        }

        // 3. 保留保护窗口内的消息（不裁剪）
        result.addAll(messages.subList(startIdx, messages.size()));

        return result;
    }

    /**
     * 计算需要保护的消息数量（根据保护窗口大小）。
     */
    private int countProtectedMessages(List<ChatMessage> messages) {
        int totalTokens = countTokens(messages);
        int protectedTokens = 0;
        int count = 0;

        // 从最新消息开始倒序计算
        for (int i = messages.size() - 1; i >= 0; i--) {
            int msgTokens = tokenCounter.count(messages.get(i));
            if (protectedTokens + msgTokens > protectionWindowSize) {
                break;
            }
            protectedTokens += msgTokens;
            count++;
        }

        return count;
    }

    /**
     * 裁剪 Assistant 消息（移除长工具输出）。
     */
    private ChatAssistantMessage pruneAssistantMessage(ChatAssistantMessage msg) {
        // 保留文本内容，移除思考过程（think）和工具调用详情
        String content = msg.getContent();
        if (content != null && content.length() > 5000) {
            content = content.substring(0, 5000) + "\n\n[... truncated ...]";
        }

        ChatAssistantMessage pruned = new ChatAssistantMessage();
        pruned.setContent(content);
        // 不保留 think 和 toolCalls（它们会在工具响应中体现）
        return pruned;
    }

    /**
     * 裁剪工具响应消息（保留元数据，裁剪输出）。
     */
    private ChatToolResponseMessage pruneToolResponse(ChatToolResponseMessage msg) {
        String output = msg.getContent();
        if (output != null && output.length() > 1000) {
            output = msg.getName() + ": " + output.substring(0, 1000) + "\n\n[... truncated ...]";
        } else {
            output = msg.getName() + ": " + (output != null ? output : "(no output)");
        }

        ChatToolResponseMessage pruned = new ChatToolResponseMessage();
        pruned.setToolCallId(msg.getToolCallId());
        pruned.setName(msg.getName());
        pruned.setContent(output);
        return pruned;
    }

    /**
     * Stage 2: LLM Summary（生成结构化摘要）。
     *
     * 使用结构化摘要模板：
     * - Goal: 用户原始意图
     * - Instructions: 给出的关键指令
     * - Discoveries: 工作中发现的什么
     * - Accomplished: 完成的任务摘要
     * - Files: 读取/修改的文件及简要描述
     */
    private CompletionStage<List<ChatMessage>> summarize(List<ChatMessage> messages) {
        // 构建摘要 prompt
        String summaryPrompt = buildSummaryPrompt(messages);

        // 调用 LLM 生成摘要
        ChatRequest request = new ChatRequest();
        request.addMessage(ChatSystemMessage.of(
            "You are an AI assistant that summarizes conversation context. " +
            "Output a structured summary in JSON format."
        ));
        request.addMessage(ChatUserMessage.of(summaryPrompt));

        return chatService.callAsync(request)
            .thenApply(response -> {
                // 解析 LLM 返回的结构化摘要
                String summary = response.getMessage().getContent();
                return buildSummaryMessages(summary, messages);
            });
    }

    /**
     * 构建摘要 prompt（包含所有消息）。
     */
    private String buildSummaryPrompt(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the following conversation into a structured JSON:\n\n");
        sb.append("{\n");
        sb.append("  \"goal\": \"[user's original intent]\",\n");
        sb.append("  \"instructions\": \"[key instructions given]\",\n");
        sb.append("  \"discoveries\": \"[what was learned during work]\",\n");
        sb.append("  \"accomplished\": \"[completed tasks summary]\",\n");
        sb.append("  \"files\": \"[files read/modified with brief descriptions]\"\n");
        sb.append("}\n\n");
        sb.append("Conversation:\n");

        for (ChatMessage msg : messages) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建摘要消息列表。
     */
    private List<ChatMessage> buildSummaryMessages(String summary, List<ChatMessage> original) {
        List<ChatMessage> result = new ArrayList<>();

        // 添加系统消息
        ChatSystemMessage systemMsg = new ChatSystemMessage();
        systemMsg.setContent(
            "The following is a summary of the previous conversation:\n" +
            summary + "\n\n" +
            "Continue based on this summary."
        );
        result.add(systemMsg);

        // 添加保护窗口内的消息（最近 2-3 轮）
        int protectedCount = countProtectedMessages(original);
        result.addAll(original.subList(
            original.size() - protectedCount,
            original.size()
        ));

        return result;
    }

    /**
     * 计算 token 总数。
     */
    private int countTokens(List<ChatMessage> messages) {
        return messages.stream()
            .mapToInt(msg -> tokenCounter.count(msg))
            .sum();
    }
}

/**
 * Token 计数器接口（v4 新增）。
 */
public interface ITokenCounter {
    int count(ChatMessage message);
}

/**
 * Token 计数器实现（基于 tiktoken 或简单启发式）。
 */
public class SimpleTokenCounter implements ITokenCounter {

    @Override
    public int count(ChatMessage message) {
        // 简单启发式：1 token ≈ 4 字符（英文），中文 1.5 字符 ≈ 1 token
        String content = message.getContent();
        if (content == null) {
            return 0;
        }

        // 检测是否包含中文
        boolean hasChinese = content.matches(".*\\p{Han}.*");

        if (hasChinese) {
            return (int) (content.length() / 1.5);
        } else {
            return content.length() / 4;
        }
    }
}
```

**溢出处理**：

如果即使经过两阶段压缩，上下文仍然超出 LLM 限制，则采取以下策略：

```java
/**
 * 溢出处理策略（v4 新增）。
 */
public class OverflowHandler {

    /**
     * 处理上下文溢出。
     *
     * 策略：
     * 1. 移除最旧的消息（保留系统消息和保护窗口）
     * 2. 重新插入用户的原始消息
     * 3. 添加 "context truncated" 提示
     */
    public List<ChatMessage> handleOverflow(
            List<ChatMessage> messages,
            int maxTokens,
            ITokenCounter tokenCounter) {

        // 1. 保留系统消息
        List<ChatMessage> result = messages.stream()
            .filter(msg -> msg instanceof ChatSystemMessage)
            .toList();

        // 2. 保留最近的消息（保护窗口）
        int protectedCount = countProtectedMessages(messages, maxTokens, tokenCounter);
        result.addAll(messages.subList(
            messages.size() - protectedCount,
            messages.size()
        ));

        // 3. 添加溢出提示
        ChatUserMessage overflowMsg = new ChatUserMessage();
        overflowMsg.setContent(
            "[Note: Previous conversation context was truncated due to token limit. " +
            "Please continue based on the summary above.]"
        );
        result.add(overflowMsg);

        return result;
    }
}
```

**设计原则**：

1. **两阶段压缩**：Stage 1 pruning（轻量级）+ Stage 2 LLM summary（重量级）
2. **保护窗口**：始终保护最近 N tokens（默认 40K），避免影响 Agent 对当前状态的判断
3. **结构化摘要**：使用结构化模板，确保摘要包含关键信息（goal、instructions、discoveries、accomplished、files）
4. **溢出处理**：如果压缩后仍超限，移除最旧消息并重新插入用户原始消息
5. **Token 计数优化**：使用简单的启发式算法（1 token ≈ 4 英文字符 或 1.5 中文字符）

### 3.9 Plan 与 Todo（v3 新增：独立概念）

> 灵感来源：agent-design.md（Plan model）、manage-todo-list.tool.xml（Todo model）
>
> **v3 关键发现**：Plan 和 Todo 是独立的两个概念，不应混淆。

**核心区别**：

| 维度 | Plan（计划） | Todo（待办） |
|------|-------------|--------------|
| **用途** | 结构化多阶段计划 | 线性任务队列 |
| **结构** | Phase → Task → SubTask（递归） | 线性 List（id/content/status/priority） |
| **依赖** | 支持 dependsOn | 无依赖关系 |
| **持久化** | XML 文件（plan.xml） | 内存 ConcurrentHashMap（可持久化到 session） |
| **复杂度** | 复杂，嵌套结构 | 简单，平铺列表 |
| **管理方式** | PlanSkill / manage-plan 工具 | ManageTodoListExecutor / manage-todo-list 工具 |
| **驱动循环** | Ralph Loop（Plan 驱动） | 手动驱动（Agent 主动更新） |
| **关联** | TodoItem 可引用 Plan Task | Plan Task 不引用 Todo |

**设计原则**：

1. **独立存储**：
   - Plan 存储在 `plan.xml`（结构化 XML）
   - Todo 存储在 session.json 的 `todos` 字段（简单 JSON 数组）

2. **独立管理**：
   - Plan 由 PlanSkill 驱动（Ralph Loop）
   - Todo 由 manage-todo-list 工具直接管理

3. **可选关联**：
   - TodoItem 可选字段 `relatedTaskNo`：引用 Plan 任务的 taskNo
   - 用于将 Todo 的线性任务映射到 Plan 的结构化任务

4. **使用场景**：
   - Plan：复杂的多阶段项目，需要跟踪进度、依赖关系
   - Todo：简单的任务清单，当前会话的步骤跟踪

**实现**：

```java
/**
 * Todo Item 模型（v3 新增）。
 *
 * 对应 manage-todo-list.tool.xml。
 */
public class TodoItem {
    private String id;
    private String content;
    private TodoStatus status;  // pending | in_progress | completed | cancelled
    private TodoPriority priority;  // high | medium | low

    // v3 新增：可选关联到 Plan Task
    private String relatedTaskNo;
}

/**
 * Todo Manager —— 管理 Todo List（v3 新增）。
 */
public class TodoManager {

    private final Map<String, TodoItem> todos = new ConcurrentHashMap<>();

    /**
     * 读取 Todo List。
     */
    public List<TodoItem> readTodos() {
        return new ArrayList<>(todos.values());
    }

    /**
     * 写入 Todo List（替换式更新）。
     */
    public void writeTodos(List<TodoItem> newTodos) {
        todos.clear();
        for (TodoItem todo : newTodos) {
            todos.put(todo.getId(), todo);
        }
    }

    /**
     * 更新单个 Todo。
     */
    public void updateTodo(String id, TodoStatus status) {
        TodoItem todo = todos.get(id);
        if (todo != null) {
            todo.setStatus(status);
        }
    }
}

/**
 * Plan 和 Todo 的交互（v3 新增）。
 *
 * 场景：Plan Skill 将当前 Plan 任务同步到 Todo。
 */
public class PlanTodoSync {

    /**
     * 将 Plan 的当前任务同步到 Todo。
     *
     * 规则：
     * 1. 只同步 Plan 中 pending/in_progress 的任务
     * 2. TodoItem.relatedTaskNo = task.taskNo
     * 3. TodoItem.content = task.title
     * 4. TodoItem.priority 根据 task 的重要程度设置
     */
    public List<TodoItem> syncPlanToTodo(AgentPlan plan) {
        List<TodoItem> todos = new ArrayList<>();

        for (AgentPlanTask task : plan.getPendingTasks()) {
            TodoItem todo = new TodoItem();
            todo.setId("task-" + task.getTaskNo());
            todo.setContent(task.getTitle());
            todo.setStatus(task.getStatus() == AgentExecStatus.IN_PROGRESS
                ? TodoStatus.IN_PROGRESS
                : TodoStatus.PENDING);
            todo.setPriority(mapPriority(task));
            todo.setRelatedTaskNo(task.getTaskNo());

            todos.add(todo);
        }

        return todos;
    }

    /**
     * 将 Todo 状态同步回 Plan。
     *
     * 场景：用户通过 manage-todo-list 工具更新 Todo，Plan 同步更新。
     */
    public void syncTodoToPlan(List<TodoItem> todos, AgentPlan plan) {
        for (TodoItem todo : todos) {
            if (todo.getRelatedTaskNo() != null) {
                AgentPlanTask task = plan.findTask(todo.getRelatedTaskNo());
                if (task != null) {
                    task.setStatus(mapStatus(todo.getStatus()));
                }
            }
        }
    }
}
```

### 3.10 多 Agent 编排

> 灵感来源：LangGraph（StateGraph + Pregel）、Solon-AI（FlowEngine + YAML）、Spring-AI-Alibaba（Sequential/Parallel/Routing Agent）
>
> **v3 变更**：利用 Nop 已有的 flow.xdef（工作流编排）和 task.xdef（任务编排），不重新造编排引擎

```java
/**
 * Agent 编排器 —— 复用 Nop 平台的 Flow/Task 引擎。
 *
 * 灵感来源：
 * - LangGraph: StateGraph + addNode + addEdge + compile() → Pregel 执行
 * - Solon-AI: FlowEngine + SolonFlow 图编排（最直接的参考，因为 Solon Flow 与 Nop Flow 类似）
 * - Spring-AI-Alibaba: SequentialAgent + ParallelAgent + LlmRoutingAgent
 *
 * 下一代创新：
 * - 不重新实现图引擎，复用 Nop 已有的 flow.xdef/task.xdef 编排能力
 * - Agent 只需实现 IAgentNode 接口即可插入 Flow
 * - 状态通过 FlowContext 传递（与 Solon-AI 的 FlowContext 模式一致）
 */
public interface IAgentNode {

    String getNodeId();
    String getName();
    String getDescription();

    /**
     * 节点执行。
     *
     * 灵感来源：
     * - LangGraph: Node = State → Partial<State>
     * - Solon-AI: Agent.run(FlowContext, Node)
     * - Spring-AI-Alibaba: AgentToolNode.execute()
     */
    CompletionStage<NodeResult> execute(FlowContext context);
}

/**
 * Agent 节点适配器 —— 将 IAgent 包装为 Flow Activity。
 *
 * 灵感来源：Solon-AI 的 Agent implements NamedTaskComponent
 */
public class AgentFlowActivity implements IFlowActivity {
    private final IAgent agent;

    @Override
    public void execute(FlowContext context) {
        AgentRequest request = buildRequestFromContext(context);
        AgentResponse response = agent.chat(request).join();
        context.setValue("lastAgentResponse", response);
    }
}
```

**Nop Task 引擎集成（v4 新增）**：

> 灵感来源：Nop 平台（GraphTaskStep、ParallelTaskStep、SuspendTaskStep、LoopTaskStep、ITaskStateStore）、LangGraph（StateGraph + Checkpoint）
>
> **核心挑战**：
> - 多 Agent 编排需要复杂的流程控制（依赖关系、并行执行、人工干预、循环迭代）
> - 需要状态持久化以支持会话恢复和崩溃恢复
> - Nop 平台已有成熟的 Task 引擎，应该复用而非重新实现

**设计方案**：

Nop Task 引擎已经提供了以下功能，可以直接用于多 Agent 编排：

1. **GraphTaskStep**：依赖图执行，Agent 可以声明依赖关系，引擎自动并行执行独立节点
2. **ParallelTaskStep**：并行执行多个 Agent，支持 JoinType 配置
3. **SuspendTaskStep**：yield/resume 模式，用于 HITL（Human-in-the-Loop）
4. **LoopTaskStep**：带检查点的循环，每次迭代保存状态，支持中断恢复
5. **ITaskStateStore**：状态持久化接口，启用崩溃恢复和会话恢复

**使用示例（task.xdef）**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<workflow xmlns="http://www.canonical-entropy.com/xdef"
          xmlns:xdef="http://www.canonical-entropy.com/xdef">

    <!-- 多 Agent 工作流：研究型编码任务 -->
    <task name="multi-agent-research" id="multi-agent-research">

        <!-- Phase 1: 需求分析 -->
        <step id="analyze-requirements" type="agent-node">
            <agentId>requirements-agent</agentId>
            <prompt>分析用户需求，输出需求文档</prompt>
        </step>

        <!-- Phase 2: 并行执行多个研究 Agent（使用 GraphTaskStep） -->
        <step id="parallel-research" type="graph-task">
            <nodes>
                <!-- 研究 Agent 1：代码库分析 -->
                <node id="code-analysis">
                    <step type="agent-node">
                        <agentId>code-analyzer-agent</agentId>
                        <prompt>分析代码库结构</prompt>
                        <dependsOn>analyze-requirements</dependsOn>
                    </step>
                </node>

                <!-- 研究 Agent 2：文档分析 -->
                <node id="doc-analysis">
                    <step type="agent-node">
                        <agentId>doc-analyzer-agent</agentId>
                        <prompt>分析项目文档</prompt>
                        <dependsOn>analyze-requirements</dependsOn>
                    </step>
                </node>

                <!-- 研究 Agent 3：测试用例分析 -->
                <node id="test-analysis">
                    <step type="agent-node">
                        <agentId>test-analyzer-agent</agentId>
                        <prompt>分析测试用例</prompt>
                        <dependsOn>analyze-requirements</dependsOn>
                    </step>
                </node>
            </nodes>

            <!-- 边定义（可选，默认按 dependsOn 自动推断） -->
            <edges>
                <edge from="analyze-requirements" to="code-analysis"/>
                <edge from="analyze-requirements" to="doc-analysis"/>
                <edge from="analyze-requirements" to="test-analysis"/>
            </edges>
        </step>

        <!-- Phase 3: 人工审查（使用 SuspendTaskStep） -->
        <step id="human-review" type="suspend-task">
            <prompt>请审查研究成果，提供反馈</prompt>
            <dependsOn>code-analysis,doc-analysis,test-analysis</dependsOn>
            <!-- 人工输入后继续 -->
        </step>

        <!-- Phase 4: 合并结果（使用 ParallelTaskStep） -->
        <step id="merge-results" type="parallel-task">
            <joinType>ALL_SUCCESS</joinType> <!-- 所有成功才继续 -->
            <steps>
                <!-- 合并 Agent -->
                <step type="agent-node">
                    <agentId>merge-agent</agentId>
                    <prompt>合并研究成果</prompt>
                </step>

                <!-- 验证 Agent -->
                <step type="agent-node">
                    <agentId>validation-agent</agentId>
                    <prompt>验证合并结果</prompt>
                </step>
            </steps>
            <dependsOn>human-review</dependsOn>
        </step>

        <!-- Phase 5: 循环迭代（使用 LoopTaskStep）-->
        <step id="iteration-loop" type="loop-task">
            <condition>!validation-agent.isComplete()</condition>
            <maxIterations>3</maxIterations>
            <body>
                <steps>
                    <!-- 修复 Agent -->
                    <step type="agent-node">
                        <agentId>fix-agent</agentId>
                        <prompt>根据验证结果修复问题</prompt>
                    </step>

                    <!-- 重新验证 -->
                    <step type="agent-node">
                        <agentId>validation-agent</agentId>
                        <prompt>重新验证修复结果</prompt>
                    </step>
                </steps>
            </body>
            <checkpoint>true</checkpoint> <!-- 每次迭代保存检查点 -->
            <dependsOn>merge-results</dependsOn>
        </step>

    </task>
</workflow>
```

**Task 引擎集成接口**：

```java
/**
 * Agent 节点类型（v4 新增）。
 *
 * 将 IAgent 集成到 Nop Task 引擎。
 */
public class AgentTaskStep implements ITaskStepRuntime {

    private final IAgent agent;
    private final String agentId;
    private final String prompt;

    public AgentTaskStep(IAgent agent, String agentId, String prompt) {
        this.agent = agent;
        this.agentId = agentId;
        this.prompt = prompt;
    }

    @Override
    public CompletionStage<TaskStepResult> execute(ITaskContext context) {
        // 构建请求
        AgentRequest request = new AgentRequest();
        request.setAgentId(agentId);
        request.setMessages(List.of(ChatUserMessage.of(prompt)));

        // 传递上下文
        request.setContext(context);

        // 执行 Agent
        return agent.chat(request)
            .thenApply(response -> {
                // 将结果存储到上下文
                context.setValue("agentResponse." + agentId, response);

                // 返回任务结果
                TaskStepResult result = new TaskStepResult();
                result.setStatus(TaskStepStatus.COMPLETED);
                result.setOutput(response.getContent());
                return result;
            })
            .exceptionally(error -> {
                // 错误处理
                TaskStepResult result = new TaskStepResult();
                result.setStatus(TaskStepStatus.ERROR);
                result.setError(error.getMessage());
                return result;
            });
    }

    @Override
    public void cancel(ICancelToken cancelToken) {
        // 取消 Agent 执行
        // Agent 会在检查点响应取消
    }
}
```

**GraphTaskStep 使用（依赖图）**：

```java
/**
 * 使用 GraphTaskStep 进行依赖图编排（v4 新增）。
 */
public class AgentGraphOrchestrator {

    /**
     * 创建依赖图（多个 Agent 并行执行）。
     */
    public ITaskStepRuntime createGraph(List<AgentDefinition> agents) {
        GraphTaskStep graph = new GraphTaskStep();

        // 添加节点
        for (AgentDefinition agent : agents) {
            AgentTaskStep step = new AgentTaskStep(
                agent.getAgent(),
                agent.getAgentId(),
                agent.getPrompt()
            );

            graph.addNode(agent.getAgentId(), step);

            // 添加依赖
            for (String dep : agent.getDependsOn()) {
                graph.addEdge(dep, agent.getAgentId());
            }
        }

        return graph;
    }
}
```

**ParallelTaskStep 使用（并行执行）**：

```java
/**
 * 使用 ParallelTaskStep 进行并行 Agent 执行（v4 新增）。
 */
public class AgentParallelOrchestrator {

    /**
     * 创建并行任务（多个 Agent 并行执行，配置 JoinType）。
     */
    public ITaskStepRuntime createParallel(List<AgentDefinition> agents, JoinType joinType) {
        ParallelTaskStep parallel = new ParallelTaskStep();
        parallel.setJoinType(joinType); // ALL_SUCCESS, ANY_SUCCESS, ALL_COMPLETED

        for (AgentDefinition agent : agents) {
            AgentTaskStep step = new AgentTaskStep(
                agent.getAgent(),
                agent.getAgentId(),
                agent.getPrompt()
            );

            parallel.addStep(step);
        }

        return parallel;
    }
}
```

**SuspendTaskStep 使用（HITL）**：

```java
/**
 * 使用 SuspendTaskStep 进行人工干预（v4 新增）。
 */
public class AgentHumanInLoopOrchestrator {

    /**
     * 创建人工审查步骤。
     */
    public ITaskStepRuntime createSuspendStep(String prompt, Consumer<HumanInput> inputHandler) {
        SuspendTaskStep suspend = new SuspendTaskStep();
        suspend.setPrompt(prompt);

        // 设置输入处理器
        suspend.setInputHandler(context -> {
            HumanInput input = new HumanInput();
            input.setContent(context.getHumanInput());
            inputHandler.accept(input);
        });

        return suspend;
    }
}
```

**LoopTaskStep 使用（循环迭代）**：

```java
/**
 * 使用 LoopTaskStep 进行循环迭代（v4 新增）。
 */
public class AgentLoopOrchestrator {

    /**
     * 创建循环步骤（带检查点）。
     */
    public ITaskStepRuntime createLoopStep(
            Predicate<ITaskContext> condition,
            int maxIterations,
            ITaskStepRuntime body) {

        LoopTaskStep loop = new LoopTaskStep();
        loop.setCondition(condition);
        loop.setMaxIterations(maxIterations);
        loop.setBody(body);
        loop.setCheckpoint(true); // 启用检查点

        return loop;
    }
}
```

**状态持久化（ITaskStateStore）**：

```java
/**
 * 任务状态持久化（v4 新增）。
 *
 * 灵感来源：Nop ITaskStateStore
 */
public interface ITaskStateStore {

    /**
     * 保存任务状态。
     */
    void saveState(String taskId, ITaskState state);

    /**
     * 加载任务状态。
     */
    ITaskState loadState(String taskId);

    /**
     * 删除任务状态。
     */
    void deleteState(String taskId);

    /**
     * 检查点：保存当前迭代状态（用于 LoopTaskStep）。
     */
    void saveCheckpoint(String taskId, int iteration, ITaskState state);

    /**
     * 从检查点恢复。
     */
    ITaskState loadCheckpoint(String taskId, int iteration);
}
```

**设计原则**：

1. **复用已有引擎**：GraphTaskStep、ParallelTaskStep、SuspendTaskStep、LoopTaskStep 都是 Nop 已有的能力
2. **声明式配置**：通过 task.xdef 定义多 Agent 工作流，无需编码
3. **状态持久化**：ITaskStateStore 支持会话恢复和崩溃恢复
4. **JoinType 配置**：ParallelTaskStep 支持 ALL_SUCCESS/ANY_SUCCESS/ALL_COMPLETED
5. **检查点**：LoopTaskStep 支持每次迭代保存检查点，支持中断恢复
6. **依赖图**：GraphTaskStep 自动并行执行独立的 Agent 节点

### 3.11 call-agent 扩展（v3 新增）

> 灵感来源：call-agent.tool.xml（现有 schema）、agent-design.md（Advisor Agent 调用）

**v3 新增的扩展**：

```xml
<call-agent
    id="!int"
    explanation="!string"
    timeoutMs="int"
    agentId="!string"
    sessionId="string"
    skills="csv-set"
    inheritContext="boolean"
    mode="enum:sync|async|detached"    <!-- v3 新增 -->
    context="xml"                       <!-- v3 新增 -->
>
    <input>string</input>
    <input-files xdef:body-type="list" xdef:key-attr="path">
        <input-file path="!full-path" description="string"/>
    </input-files>
</call-agent>
```

**扩展属性说明**：

| 属性 | 类型 | 说明 | v3 新增 |
|------|------|------|----------|
| `mode` | enum: sync\|async\|detached | 调用模式 | ✅ |
| `context` | xml | 结构化上下文（用于 Advisor Agent） | ✅ |

**mode 属性**：

- `sync`：同步调用（当前默认），等待 Agent 完成
- `async`：异步调用，立即返回，通过事件推送结果
- `detached`：分离调用，创建独立 Agent 实例，不关联当前会话

**context 属性**：

- 用于传递结构化数据（如 Plan 进度、错误信息）
- Advisor Agent 使用此参数接收决策所需上下文
- 例如：RetryAdvisor 的 input 包含 `errorType`, `errorMessage`, `retryCount`

**CallAgentExecutor 实现（v3 扩展）**：

```java
/**
 * Call Agent 工具执行器（v3 扩展）。
 *
 * 灵感来源：call-agent.tool.xml, agent-design.md
 */
public class CallAgentExecutor implements IToolExecutor {

    private final IAgentRegistry agentRegistry;

    @Override
    public String getToolName() {
        return "call-agent";
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(
        AiToolCall toolCall,
        IToolExecuteContext context
    ) {
        // 解析参数
        String agentId = toolCall.getAttribute("agentId");
        String sessionId = toolCall.getAttribute("sessionId");
        String mode = toolCall.getAttribute("mode", "sync");
        String contextXml = toolCall.getAttribute("context");

        // 根据 mode 执行
        return switch (mode) {
            case "sync" -> executeSync(agentId, sessionId, toolCall, context);
            case "async" -> executeAsync(agentId, sessionId, toolCall, context);
            case "detached" -> executeDetached(agentId, toolCall, context);
            default -> CompletableFuture.completedFuture(
                AiToolCallResult.error("Invalid mode: " + mode)
            );
        };
    }

    /**
     * 同步调用（原有逻辑）。
     */
    private CompletionStage<AiToolCallResult> executeSync(
        String agentId,
        String sessionId,
        AiToolCall toolCall,
        String contextXml
    ) {
        // 1. 查找 Agent
        IAgent agent = agentRegistry.getAgent(agentId);
        if (agent == null) {
            return CompletableFuture.completedFuture(
                AiToolCallResult.error("Agent not found: " + agentId)
            );
        }

        // 2. 构建 AgentRequest
        AgentRequest request = buildRequest(toolCall, contextXml);
        request.setSessionId(sessionId);

        // 3. 调用 Agent
        return agent.chat(request)
            .thenApply(response -> formatResponse(response));
    }

    /**
     * 异步调用（v3 新增）。
     */
    private CompletionStage<AiToolCallResult> executeAsync(
        String agentId,
        String sessionId,
        AiToolCall toolCall,
        String contextXml
    ) {
        // 1. 立即返回
        String callId = UUID.randomUUID().toString();
        AiToolCallResult immediateResult = AiToolCallResult.success(
            "Agent call started. Call ID: " + callId
        );

        // 2. 后台执行 Agent
        CompletableFuture.runAsync(() -> {
            IAgent agent = agentRegistry.getAgent(agentId);
            AgentRequest request = buildRequest(toolCall, contextXml);
            request.setSessionId(sessionId);

            AgentResponse response = agent.chat(request).join();

            // 3. 通过事件推送结果
            EventBus.publish(new AgentCallCompletedEvent(callId, response));
        });

        return CompletableFuture.completedFuture(immediateResult);
    }

    /**
     * 分离调用（v3 新增）。
     */
    private CompletionStage<AiToolCallResult> executeDetached(
        String agentId,
        AiToolCall toolCall,
        String contextXml
    ) {
        // 1. 创建独立 Agent 实例
        IAgent agent = agentRegistry.createAgent(agentId);

        // 2. 新建会话
        AgentRequest request = buildRequest(toolCall, contextXml);
        request.setSessionId(null);  // 强制新建会话

        // 3. 异步执行（不等待）
        String newSessionId = UUID.randomUUID().toString();

        CompletableFuture.runAsync(() -> {
            AgentResponse response = agent.chat(request).join();
            // 保存到 .nop/sessions/{newSessionId}
            saveSession(newSessionId, response);
        });

        // 4. 立即返回新 Session ID
        String output = String.format(
            "Detached agent created. Session ID: %s",
            newSessionId
        );
        return CompletableFuture.completedFuture(AiToolCallResult.success(output));
    }
}
```

**Advisor Agent 调用示例**：

```xml
<!-- 调用 RetryAdvisor（使用 context 传递错误信息） -->
<call-agent
    id="1"
    explanation="Decide whether to retry failed tool call"
    agentId="retry-advisor"
    mode="sync"
>
    <context>
        <error-type>TimeoutException</error-type>
        <error-message>Execution timed out after 5 minutes</error-message>
        <tool-name>shell</tool-name>
        <retry-count>1</retry-count>
        <arguments>
            <command>npm install</command>
        </arguments>
    </context>
</call-agent>
```

---

### 3.12 容错处理体系（v5 新增）

> 灵感来源：OpenCode（完整容错体系）、AgentScope-Java（ExecutionConfig/ToolValidator）、Solon-AI（StopLoopInterceptor）、
> Spring-AI-Alibaba（ModelRetryInterceptor/ModelFallbackInterceptor）、Codex（retry/sandbox）、LangGraph（Checkpoint/RetryPolicy）、DeepAgents（SummarizationMiddleware）

**设计哲学**：AI Agent 在生产环境中面临独特的故障模式——LLM API 不稳定、工具执行不可预测、上下文窗口有限、模型可能产生幻觉工具调用。
传统软件的容错（重试、超时）不足以覆盖这些场景。本节设计一套**多层防御体系**，从错误分类、自动恢复到降级回退，覆盖 Agent 运行时的全部故障面。

```
Agent 运行时容错层次：

┌─────────────────────────────────────────────────────┐
│  §3.12.1  错误分类与重试    ← 第一道防线              │
│  §3.12.2  断路器模式        ← 连续失败保护            │
│  §3.12.3  循环检测          ← Agent 死循环保护         │
│  §3.12.4  上下文窗口保护     ← Token 溢出保护          │
│  §3.12.5  多级超时          ← 时间预算管理             │
│  §3.12.6  工具安全与验证     ← 执行前防线              │
│  §3.12.7  降级与回退        ← 最后防线                │
│  §3.12.8  会话恢复与检查点   ← 崩溃恢复               │
└─────────────────────────────────────────────────────┘
```

#### 3.12.1 错误分类与重试策略（Error Classification & Retry）

> 灵感来源：OpenCode `model-error-classifier.ts`（错误分类）+ Codex `errors.py`（retryable detection）+
> AgentScope-Java `ExecutionConfig.RETRYABLE_ERRORS`（谓词过滤）+ LangGraph `RetryPolicy`（退避策略）+
> Spring-AI-Alibaba `ModelRetryInterceptor`（指数退避+jitter）

**核心思路**：所有错误在重试前必须先分类。可重试错误自动重试，不可重试错误直接返回给 LLM 或快速失败。

```java
/**
 * 错误分类结果
 *
 * 灵感来源：OpenCode model-error-classifier.ts 的 RETRYABLE_ERROR_NAMES / NON_RETRYABLE_ERROR_NAMES
 */
public enum ErrorCategory {
    /** 可重试：429 限流、5xx 服务端错误、网络超时、连接异常 */
    RETRYABLE,
    /** 不可重试：400 参数错误、401 认证失败、403 权限不足、ContextWindowExceeded */
    NON_RETRYABLE,
    /** 可恢复：需要特殊处理（如上下文溢出需要压缩后重试） */
    RECOVERABLE
}

/**
 * 错误分类器接口
 *
 * 灵感来源：OpenCode isRetryableModelError() + Codex is_retryable_error() + AgentScope-Java RETRYABLE_ERRORS
 */
public interface IErrorClassifier {
    /** 分类错误 */
    ErrorCategory classify(Throwable error);

    /** 默认分类器 */
    static IErrorClassifier defaultClassifier() {
        return error -> {
            // HTTP 状态码分类（参考 AgentScope-Java OpenAIException.create）
            if (error instanceof HttpException http) {
                return switch (http.statusCode()) {
                    case 429 -> ErrorCategory.RETRYABLE;         // 限流
                    case 500, 502, 503, 529 -> ErrorCategory.RETRYABLE;  // 服务端
                    case 400, 401, 403, 404 -> ErrorCategory.NON_RETRYABLE;  // 客户端
                    default -> http.statusCode() >= 500
                        ? ErrorCategory.RETRYABLE
                        : ErrorCategory.NON_RETRYABLE;
                };
            }
            // 上下文溢出 → 可恢复（压缩后重试）
            if (error instanceof ContextWindowExceededException) {
                return ErrorCategory.RECOVERABLE;
            }
            // 网络/超时 → 可重试
            if (error instanceof IOException || error instanceof TimeoutException) {
                return ErrorCategory.RETRYABLE;
            }
            // 工具逻辑错误（参数不合法等）→ 不重试，交给 LLM 自修复
            if (error instanceof IllegalArgumentException) {
                return ErrorCategory.NON_RETRYABLE;
            }
            // 未知错误 → 保守重试
            return ErrorCategory.RETRYABLE;
        };
    }
}
```

**重试策略配置**：

```java
/**
 * 重试策略配置
 *
 * 灵感来源：Codex retry_on_overload（指数退避+jitter）+
 * LangGraph RetryPolicy（initial_interval/backoff_factor/max_interval/max_attempts）+
 * Spring-AI-Alibaba ModelRetryInterceptor（指数退避+jitter±25%）
 *
 * 对比 AgentScope-Java ExecutionConfig：
 * - AgentScope: 5min timeout, 3 attempts, 2s initial, 30s max, 2.0 multiplier, 0.5 jitter
 * - Codex: 0.25s initial, 2.0s max, 0.2 jitter_ratio, 3 attempts
 * - 本设计：1s initial, 30s max, 2.0 multiplier, 0.2 jitter, 3 attempts（折中方案）
 */
public class RetryConfig {
    /** 最大重试次数（含首次调用），默认 3 */
    private int maxAttempts = 3;

    /** 初始退避间隔（毫秒），默认 1000 */
    private long initialDelayMs = 1000;

    /** 最大退避间隔（毫秒），默认 30000 */
    private long maxDelayMs = 30_000;

    /** 退避倍数，默认 2.0 */
    private double backoffMultiplier = 2.0;

    /** Jitter 比率，默认 0.2（±20%随机偏移，防止雷群效应） */
    private double jitterRatio = 0.2;

    /** 可重试的错误类型谓词，null 则使用 IErrorClassifier */
    private Predicate<Throwable> retryOn;

    /** 计算第 n 次重试的等待时间（毫秒） */
    public long computeDelay(int attempt) {
        double baseDelay = Math.min(
            initialDelayMs * Math.pow(backoffMultiplier, attempt),
            maxDelayMs
        );
        double jitter = baseDelay * jitterRatio;
        return (long) (baseDelay + (Math.random() * 2 - 1) * jitter);
    }
}
```

**分层默认配置**（参考 AgentScope-Java 的 MODEL_DEFAULTS / TOOL_DEFAULTS 分层）：

| 层级 | maxAttempts | initialDelay | maxDelay | 说明 |
|------|-------------|-------------|----------|------|
| LLM 调用 | 3 | 1s | 30s | 模型 API 不稳定，指数退避 |
| 工具执行 | 1 | — | — | 工具失败交给 AI 自修复（§3.6），不自动重试 |
| Agent 级 | 3 | 1s | 30s | call-agent 子任务可重试 |

---

#### 3.12.2 断路器模式（Circuit Breaker）

> 灵感来源：OpenCode `manager-circuit-breaker.test.ts`（后台任务断路器）+
> `runtime-fallback/fallback-state.ts`（模型冷却追踪）+ `constants.ts`（DEFAULT_CIRCUIT_BREAKER_CONSECUTIVE_THRESHOLD=20）

**核心思路**：当某个 LLM 提供商连续失败时，自动断开，切换到备用提供商。避免反复调用已知失败的服务。

```java
/**
 * 断路器状态
 *
 * 灵感来源：OpenCode manager-circuit-breaker 的 CLOSED/OPEN/HALF_OPEN 三态
 */
public enum CircuitState {
    /** 正常：请求正常通过 */
    CLOSED,
    /** 断开：所有请求被拒绝，等待冷却 */
    OPEN,
    /** 半开：允许一个探测请求，成功则恢复 CLOSED，失败则回到 OPEN */
    HALF_OPEN
}

/**
 * 断路器接口（per model/provider 维度）
 *
 * 灵感来源：OpenCode manager-circuit-breaker + fallback-state.ts
 * OpenCode 通过 BackgroundManager 管理后台任务的断路器，
 * 本设计将其泛化为 per-model 断路器。
 */
public interface ICircuitBreaker {
    /** 当前状态 */
    CircuitState getState();

    /** 记录成功调用 */
    void recordSuccess();

    /** 记录失败调用 */
    void recordFailure(Throwable error);

    /** 是否允许请求通过 */
    boolean allowRequest();

    /** 获取剩余冷却时间（毫秒），-1 表示不在冷却中 */
    long getRemainingCooldownMs();
}

/**
 * 断路器配置
 *
 * 灵感来源：OpenCode constants.ts（DEFAULT_CIRCUIT_BREAKER_CONSECUTIVE_THRESHOLD=20）
 * + fallback-state.ts（cooldown_seconds=60）
 */
public class CircuitBreakerConfig {
    /** 触发断开的连续失败次数，默认 5（比 OpenCode 的 20 更保守） */
    private int failureThreshold = 5;

    /** 断开状态持续时间（毫秒），默认 60000（60s，与 OpenCode cooldown 一致） */
    private long openDurationMs = 60_000;

    /** 半开状态下允许的探测请求数，默认 1 */
    private int halfOpenMaxRequests = 1;

    /** 滑动窗口大小（秒），默认 60 */
    private int windowSeconds = 60;

    /** 是否启用，默认 true */
    private boolean enabled = true;
}
```

**断路器与模型回退协同**：当断路器处于 OPEN 状态时，自动触发模型回退（§3.12.7），使用备用模型继续服务。

---

#### 3.12.3 循环检测（Loop Detection）

> 灵感来源：OpenCode `loop-detector.ts`（签名跟踪 + consecutiveCount）+
> Solon-AI `StopLoopInterceptor`（动作频率监控 + 柔性中断）+
> LangGraph `errors.py`（GraphRecursionError 递归限制）

**核心思路**：Agent 可能陷入死循环——反复调用同一个工具、相同的参数。通过工具调用签名检测重复，先软提示 LLM 换策略，再硬中断。

```java
/**
 * 循环检测器接口
 *
 * 灵感来源：OpenCode loop-detector.ts（createToolCallSignature + detectRepetitiveToolUse）
 * 签名 = toolName + "::" + sortedJSON(args)，确保参数顺序不影响检测。
 * Solon-AI StopLoopInterceptor 则监控滑动窗口内的动作指纹频率。
 */
public interface ILoopDetector {
    /** 记录一次工具调用，返回检测结果 */
    LoopDetectionResult recordToolCall(String toolName, Map<String, Object> args);

    /** 检测是否陷入循环 */
    LoopDetectionResult detect();

    /** 重置检测状态（新 ReAct 循环开始时） */
    void reset();

    /** 获取总工具调用次数 */
    int getTotalToolCallCount();
}

/**
 * 循环检测结果
 */
public class LoopDetectionResult {
    /** 是否触发 */
    private boolean triggered;
    /** 循环类型：CONSECUTIVE（连续重复）、PATTERN（模式重复，如 A-B-A-B） */
    private LoopType loopType;
    /** 重复的工具名 */
    private String toolName;
    /** 重复次数 */
    private int repeatedCount;
    /** 建议的处理方式：WARN（注入提示）或 ABORT（中断执行） */
    private DetectionLevel level;
}

public enum DetectionLevel {
    /** 柔性：注入提示到 LLM，建议换策略（参考 Solon-AI StopLoopInterceptor 的 soft interruption） */
    WARN,
    /** 硬性：中断 Agent 执行（参考 OpenCode circuit breaker 的 hard stop） */
    ABORT
}

/**
 * 循环检测配置
 */
public class LoopDetectorConfig {
    /**
     * 连续重复阈值，默认 5（低于 OpenCode 的 20，因为我们用两级检测）
     * 超过此数值触发 WARN 级别（注入提示）
     */
    private int consecutiveThreshold = 5;

    /**
     * 硬中断阈值，默认 15
     * 超过此数值触发 ABORT 级别（中断 Agent）
     */
    private int abortThreshold = 15;

    /**
     * 总工具调用上限，默认 200
     * LangGraph 用 recursion_limit（默认 25），DeepAgents 用 1000，
     * 本设计取中间值，用于图执行模式的递归限制
     */
    private int totalToolCallLimit = 200;

    /** 是否启用，默认 true */
    private boolean enabled = true;
}
```

**两级检测策略**：

1. **软警告（WARN）**：连续 5 次相同签名 → 注入系统提示：`"你已连续 5 次调用 {tool} 工具且参数相同，请尝试不同策略。"`
   - 参考 Solon-AI StopLoopInterceptor 的 `systemAlert` 柔性中断
2. **硬中断（ABORT）**：连续 15 次相同签名 → 中止 Agent 执行，返回错误
   - 参考 OpenCode circuit breaker 的硬中断

---

#### 3.12.4 上下文窗口保护（Context Window Protection）

> 灵感来源：OpenCode 三层保护体系：
> - `context-window-monitor.ts`（70% 警告层）
> - `preemptive-compaction.ts`（78% 预防压缩层，2分钟超时）
> - `anthropic-context-window-limit-recovery/`（溢出恢复层）
> + DeepAgents `SummarizationMiddleware`（85% 触发，10% 保留）

**核心思路**：上下文溢出是 Agent 最常见的致命故障。三层防御确保不会因上下文过大而崩溃。

```java
/**
 * 上下文窗口保护配置
 *
 * 三层保护体系，灵感完全来自 OpenCode：
 * 1. Warning 层（70%）: context-window-monitor.ts → 注入 Token 使用状态
 * 2. Preemptive 层（78%）: preemptive-compaction.ts → 触发自动压缩
 * 3. Recovery 层（溢出时）: anthropic-context-window-limit-recovery/ → 强制压缩后重试
 */
public class ContextWindowConfig {
    /** 警告阈值（占比），默认 0.70（来自 OpenCode CONTEXT_WARNING_THRESHOLD） */
    private double warningThreshold = 0.70;

    /** 预防压缩阈值（占比），默认 0.78（来自 OpenCode PREEMPTIVE_COMPACTION_THRESHOLD） */
    private double compactionThreshold = 0.78;

    /** 压缩超时（毫秒），默认 120000（来自 OpenCode PREEMPTIVE_COMPACTION_TIMEOUT_MS） */
    private long compactionTimeoutMs = 120_000;

    /** 压缩后保留的比例，默认 0.10（来自 DeepAgents SummarizationMiddleware keep fraction） */
    private double keepFraction = 0.10;

    /** 是否启用警告层，默认 true */
    private boolean warningEnabled = true;

    /** 是否启用预防压缩，默认 true */
    private boolean preemptiveCompactionEnabled = true;

    /** 是否启用溢出恢复，默认 true */
    private boolean recoveryEnabled = true;
}
```

**三层保护流程**（通过 Hook 事件模式实现，复用 §3.5 Agent Hook 系统）：

```xml
<!-- agent.xdef 配置示例：上下文窗口保护 Hook -->
<hook event="after_acting" body="
  // 第一层：70% 警告（参考 OpenCode context-window-monitor.ts）
  if (ctx.tokenUsageRatio >= 0.70 && !ctx.hasWarned) {
    ctx.hasWarned = true;
    result.output += '\\n\\n[Context Status: '
      + (ctx.tokenUsageRatio * 100).toFixed(1) + '% used, '
      + ((1 - ctx.tokenUsageRatio) * 100).toFixed(1) + '% remaining]';
  }

  // 第二层：78% 预防压缩（参考 OpenCode preemptive-compaction.ts）
  if (ctx.tokenUsageRatio >= 0.78 && !ctx.compacted) {
    ctx.compacted = true;
    ctx.triggerCompaction();  // 触发 §3.8 的 IContextCompressor
  }
" />

<!-- 溢出恢复 Hook（参考 OpenCode anthropic-context-window-limit-recovery） -->
<hook event="on_error" body="
  if (error instanceof ContextWindowExceededException) {
    // 强制压缩后重试
    ctx.forceCompaction(keepFraction: 0.10);
    ctx.retryCurrentTurn();
  }
" />
```

**Token 预算追踪**：

```java
/**
 * Token 预算追踪（per session）
 *
 * 灵感来源：OpenCode context-window-monitor.ts 的 tokenCache + resolveActualContextLimit
 * 通过 ChatStreamChunk.usage 获取实际 token 用量
 */
public class TokenBudget {
    /** 模型最大上下文长度 */
    private long maxTokens;
    /** 当前已使用 token 数（input + cache_read） */
    private long usedTokens;
    /** 上次压缩后释放的 token 数 */
    private long lastCompactionSaved;

    /** 当前使用率 */
    public double getUsageRatio() {
        return maxTokens > 0 ? (double) usedTokens / maxTokens : 0.0;
    }

    /** 更新 token 用量（从 ChatStreamChunk.usage 获取） */
    public void update(Usage usage) {
        this.usedTokens = (usage.getInputTokens() != null ? usage.getInputTokens() : 0)
            + (usage.getCacheReadTokens() != null ? usage.getCacheReadTokens() : 0);
    }
}
```

**旧工具调用参数截断**（参考 DeepAgents `SummarizationMiddleware._truncate_args`）：

```java
/**
 * 参数截断策略
 *
 * 灵感来源：DeepAgents SummarizationMiddleware._truncate_args：
 * 当上下文接近上限时，对历史工具调用中的冗长参数（如文件内容、大段代码）进行截断，
 * 保留工具名和摘要，释放 token 空间。这是压缩前的轻量级优化。
 *
 * 典型场景：Agent 多次调用 write_file/edit_file，每次工具调用包含完整的文件内容。
 * 旧调用的完整文件内容已无意义（被后续操作覆盖），截断可节省大量 token。
 */
public class ArgumentTruncationConfig {
    /** 是否启用参数截断，默认 true */
    private boolean enabled = true;

    /** 触发截断的上下文使用率阈值，默认 0.75（在压缩阈值 0.78 之前触发） */
    private double triggerThreshold = 0.75;

    /** 单个参数的最大字符数，默认 500（超出部分截断为 "... [truncated]"） */
    private int maxArgLength = 500;

    /** 需要截断的工具名列表（通常为产生大量输出的工具） */
    private Set<String> truncatableTools = Set.of("write_file", "edit_file", "shell", "http_request");
}
```

---

#### 3.12.5 多级超时（Multi-Level Timeout）

> 灵感来源：OpenCode `prompt-timeout-context.ts`（2min prompt timeout）+ `timing.ts`（30min poll timeout）+
> Codex `exec.rs`（10s command timeout）+ AgentScope-Java `ExecutionConfig`（5min model timeout）+
> OpenCode `sync-session-poller.ts`（polling with timeout）

**核心思路**：不同层级的操作需要不同的超时策略。嵌套调用共享剩余时间预算。

```java
/**
 * 多级超时配置
 *
 * 三级超时体系：
 * - Agent 级（30min）：整个 Agent 执行的超时上限
 * - LLM 调用级（2min）：单次 LLM API 调用的超时
 * - 工具执行级（可配置）：单个工具执行的超时
 *
 * 灵感来源：
 * - OpenCode prompt-timeout-context.ts（PROMPT_TIMEOUT_MS=120000，2分钟）
 * - OpenCode timing.ts（DEFAULT_POLL_TIMEOUT_MS=30*60*1000，30分钟）
 * - Codex exec.rs（DEFAULT_EXEC_COMMAND_TIMEOUT_MS=10_000，10秒）
 * - AgentScope-Java ExecutionConfig（MODEL_DEFAULTS timeout=5min）
 */
public class TimeoutConfig {
    /** Agent 总超时（毫秒），默认 1_800_000（30min，参考 OpenCode DEFAULT_POLL_TIMEOUT_MS） */
    private long agentTimeoutMs = 30 * 60 * 1000L;

    /** LLM 单次调用超时（毫秒），默认 120_000（2min，参考 OpenCode PROMPT_TIMEOUT_MS） */
    private long llmCallTimeoutMs = 120_000L;

    /** 工具执行默认超时（毫秒），默认 300_000（5min，参考 AgentScope-Java TOOL_DEFAULTS） */
    private long toolTimeoutMs = 300_000L;

    /** Per-tool 超时覆盖：toolName → timeoutMs（参考 AgentScope-Java per-tool ExecutionConfig） */
    private Map<String, Long> toolTimeoutOverrides = new HashMap<>();

    /** 获取指定工具的超时时间 */
    public long getToolTimeout(String toolName) {
        return toolTimeoutOverrides.getOrDefault(toolName, toolTimeoutMs);
    }
}
```

**超时预算级联**：

```java
/**
 * 超时预算管理器
 *
 * 嵌套调用共享父级剩余时间预算。
 * 灵感来源：OpenCode sync-session-poller.ts 的级联超时
 */
public class TimeoutBudget {
    private final long deadlineMs;

    public TimeoutBudget(long totalTimeoutMs) {
        this.deadlineMs = System.currentTimeMillis() + totalTimeoutMs;
    }

    /** 从父预算中创建子预算 */
    public TimeoutBudget createChild(long childTimeoutMs) {
        long remaining = getRemainingMs();
        // 子预算不超过剩余预算
        long actual = Math.min(childTimeoutMs, remaining);
        return new TimeoutBudget(actual);
    }

    /** 剩余时间（毫秒） */
    public long getRemainingMs() {
        return Math.max(0, deadlineMs - System.currentTimeMillis());
    }

    /** 是否已超时 */
    public boolean isExpired() {
        return System.currentTimeMillis() >= deadlineMs;
    }
}
```

**超时处理**：超时时通过 ICancelToken（§3.4 取消机制）协作式取消，确保 in-flight 工具安全完成。

---

#### 3.12.6 工具安全与验证（Tool Safety & Validation）

> 灵感来源：AgentScope-Java `ToolValidator`（JSON Schema 验证）+
> OpenCode `json-error-recovery/hook.ts`（JSON 解析错误恢复）+
> Codex `exec.rs`（输出上限 EXEC_OUTPUT_MAX_BYTES）+
> Codex `sandbox_smoketests.py`（50+ 安全测试）

**核心思路**：工具执行前验证参数、检测幻觉工具名、限制输出大小。防止无效调用浪费 Token 和时间。

```java
/**
 * 工具验证结果
 */
public class ToolValidationResult {
    /** 是否有效 */
    private boolean valid;
    /** 错误类型 */
    private ValidationErrorType errorType;
    /** 错误描述（将反馈给 LLM 以自修复） */
    private String errorMessage;
    /** 修复建议（参考 OpenCode json-error-recovery 的 JSON_ERROR_REMINDER） */
    private String fixHint;

    public static ToolValidationResult ok() {
        return new ToolValidationResult(true, null, null, null);
    }

    public static ToolValidationResult error(ValidationErrorType type, String msg, String hint) {
        return new ToolValidationResult(false, type, msg, hint);
    }
}

public enum ValidationErrorType {
    /** 工具名不存在（LLM 幻觉了工具名） */
    TOOL_NOT_FOUND,
    /** 参数不符合 JSON Schema */
    INVALID_ARGS,
    /** JSON 解析失败 */
    MALFORMED_JSON,
    /** 参数值超范围（如路径穿越、命令注入） */
    UNSAFE_ARGS
}
```

**验证流程**（参考 AgentScope-Java ToolValidator + OpenCode json-error-recovery）：

```
LLM 输出 tool_calls
    │
    ├── [1] 工具名检查：toolName 在 IToolManager 中是否存在？
    │   └── 不存在 → 返回错误提示（幻觉检测）
    │       "工具 '{name}' 不存在。可用工具：{tool_list}"
    │
    ├── [2] JSON 解析：arguments 是否为合法 JSON？
    │   └── 解析失败 → 返回修复指导（参考 OpenCode JSON_ERROR_REMINDER）
    │       "JSON 解析错误：{parse_error}。请检查括号、引号、逗号。"
    │
    ├── [3] Schema 验证：参数是否符合 tool.xdef 定义的 schema？
    │   └── 不符合 → 返回具体错误（参考 AgentScope-Java ToolValidator）
    │       "参数 '{param}' 类型错误：期望 {expected}，实际 {actual}"
    │
    └── [4] 安全检查：参数值是否安全？（参考 Codex sandbox）
        └── 不安全 → 拒绝执行
            "路径 '{path}' 超出允许范围"
```

**输出上限**（参考 Codex `EXEC_OUTPUT_MAX_BYTES`）：

```java
/**
 * 工具输出限制配置
 *
 * 灵感来源：Codex exec.rs 的 EXEC_OUTPUT_MAX_BYTES（默认 80KB）
 * 防止工具输出过大导致上下文膨胀
 */
public class ToolOutputConfig {
    /** 单个工具输出的最大字节数，默认 81920（80KB，与 Codex 一致） */
    private long maxOutputBytes = 81_920;

    /** 超出时截断并附加提示 */
    private String truncationNotice = "\n\n[Output truncated: exceeded {maxBytes} bytes limit]";
}
```

**Shell/命令执行安全**（参考 Codex `exec.rs` + `sandbox_smoketests.py`）：

```java
/**
 * Shell 命令执行安全配置
 *
 * 灵感来源：Codex exec.rs 的进程组管理：
 * - DEFAULT_EXEC_COMMAND_TIMEOUT_MS = 10_000（10秒）
 * - IO_DRAIN_TIMEOUT_MS = 2_000（进程终止后 2 秒等待 IO 排空）
 * - EXEC_OUTPUT_MAX_BYTES = 80KB
 * - 超时时杀死整个进程组（不仅仅是父进程），防止子进程泄漏
 * - sandbox_smoketests.py 包含 50+ 项安全测试（只读策略、工作区写入、符号链接竞态等）
 */
public class ShellExecutionConfig {
    /** 命令超时（毫秒），默认 10_000（参考 Codex DEFAULT_EXEC_COMMAND_TIMEOUT_MS） */
    private long commandTimeoutMs = 10_000L;

    /** 超时后 IO 排空等待时间（毫秒），默认 2_000（参考 Codex IO_DRAIN_TIMEOUT_MS） */
    private long ioDrainTimeoutMs = 2_000L;

    /** 是否杀死整个进程组（防止子进程泄漏），默认 true */
    private boolean killProcessGroup = true;

    /** 工作目录限制：仅允许在指定目录下执行（参考 Codex workspace sandbox） */
    private String allowedWorkDir;

    /** 环境变量白名单（null = 允许所有） */
    private Set<String> allowedEnvVars;
}
```

**关键安全措施**：
- **进程组清理**：超时时 `kill -9` 整个进程组（`killProcessGroup=true`），而非仅终止父进程。参考 Codex `exec.rs`。
- **工作区隔离**：命令仅允许在 `allowedWorkDir` 内执行，防止路径穿越。
- **输出流管理**：进程终止后等待 `ioDrainTimeoutMs` 确保输出完整收集。

---

#### 3.12.7 降级与回退（Degradation & Fallback）

> 灵感来源：Spring-AI-Alibaba `ModelFallbackInterceptor`（多模型回退链）+
> OpenCode `runtime-fallback/auto-retry.ts`（自动重试+回退模型）+
> OpenCode `fallback-state.ts`（per-model 冷却追踪，60s cooldown）+
> AgentScope-Java `CompositeAgentException` + `FanoutPipeline`（管道部分失败聚合）

**核心思路**：当主模型不可用时，自动切换到备用模型。当工具不可用时，返回优雅降级消息而非崩溃。

```java
/**
 * 模型回退配置
 *
 * 灵感来源：
 * - Spring-AI-Alibaba ModelFallbackInterceptor：有序模型链，逐一尝试
 * - OpenCode runtime-fallback：fallbackModels 列表 + cooldown_seconds=60
 * - OpenCode fallback-state.ts：isModelInCooldown() + findNextAvailableFallback()
 */
public class FallbackConfig {
    /** 回退模型链：primary → fallback1 → fallback2 → ... */
    private List<String> modelChain = new ArrayList<>();

    /** 每个模型的冷却时间（毫秒），默认 60000（来自 OpenCode fallback-state.ts） */
    private long cooldownMs = 60_000;

    /** 最大回退尝试次数，默认 3（来自 OpenCode DEFAULT_CONFIG.max_fallback_attempts） */
    private int maxFallbackAttempts = 3;

    /** 是否启用回退，默认 true */
    private boolean enabled = true;

    /** 回退时是否通知调用方，默认 true（来自 OpenCode notify_on_fallback） */
    private boolean notifyOnFallback = true;
}
```

**回退执行流程**（参考 OpenCode runtime-fallback + Spring-AI-Alibaba ModelFallbackInterceptor）：

```
LLM 调用失败（通过 IErrorClassifier 分类为 RETRYABLE）
    │
    ├── [1] 检查重试次数 < maxAttempts → 指数退避重试（§3.12.1）
    │
    ├── [2] 重试耗尽 → 检查断路器状态（§3.12.2）
    │   └── 断路器 OPEN → 跳过此模型
    │
    ├── [3] 查找下一个可用回退模型（参考 OpenCode findNextAvailableFallback）
    │   └── 跳过冷却中的模型
    │
    └── [4] 使用回退模型重试
        └── 成功 → 重置断路器
        └── 失败 → 标记冷却，继续查找
            └── 所有模型耗尽 → 返回错误
```

**管道部分失败**（参考 AgentScope-Java CompositeAgentException + FanoutPipeline）：

```java
/**
 * 管道聚合异常
 *
 * 灵感来源：AgentScope-Java CompositeAgentException
 * 在并行工具执行或多 Agent 管道中，部分失败时聚合所有错误
 */
public class CompositeExecutionException extends NopException {
    /** 每个失败项的详情 */
    private List<ExecutionFailure> failures;

    /** 成功项的结果（部分成功场景） */
    private List<Object> successes;
}

public class ExecutionFailure {
    private String name;      // 工具名或 Agent 名
    private Throwable error;
    private int attempt;      // 第几次尝试
}
```

---

#### 3.12.8 会话恢复与检查点（Session Recovery & Checkpointing）

> 灵感来源：LangGraph `checkpoint/base/__init__.py`（完整 Checkpoint 接口）+
> Solon-AI `RedisAgentSession`（双层缓存：内存 + Redis）+
> Spring-AI-Alibaba `RedisSaver`（分布式锁 + 线程隔离）+
> OpenCode `session-recovery/hook.ts`（会话恢复 Hook）+
> LangGraph `pregel/_loop.py`（durability 模式：sync/async/exit）

**核心思路**：在 Agent 执行的关键节点保存检查点，崩溃后可恢复到最近状态。

```java
/**
 * 检查点存储接口
 *
 * 灵感来源：
 * - LangGraph BaseCheckpointSaver：get/put/list 接口
 * - Solon-AI RedisAgentSession：双层缓存（内存 + Redis）
 * - Spring-AI-Alibaba RedisSaver：分布式锁 + 线程隔离
 */
public interface ICheckpointStore {
    /** 保存检查点 */
    void save(String sessionId, AgentCheckpoint checkpoint);

    /** 加载最近检查点 */
    AgentCheckpoint load(String sessionId);

    /** 加载指定步骤的检查点（参考 LangGraph checkpoint_id 精确恢复） */
    AgentCheckpoint load(String sessionId, int step);

    /** 列出所有检查点（参考 LangGraph list()） */
    List<AgentCheckpoint> list(String sessionId);

    /** 删除检查点 */
    void delete(String sessionId, int step);
}

/**
 * Agent 检查点
 *
 * 灵感来源：LangGraph Checkpoint TypedDict
 * 包含恢复所需的所有状态
 */
public class AgentCheckpoint {
    /** 检查点 ID */
    private String id;
    /** 会话 ID */
    private String sessionId;
    /** 步骤号（从 0 开始，-1 为初始输入） */
    private int step;
    /** 创建时间 */
    private long timestamp;
    /** 来源：input / loop / recovery（参考 LangGraph source） */
    private String source;

    /** 消息历史 */
    private List<ChatMessage> messages;
    /** 当前 Plan（如有） */
    private AgentPlan plan;
    /** 当前 Todo 列表 */
    private List<TodoItem> todos;
    /** 待处理的工具调用结果（参考 LangGraph pending_writes） */
    private List<ChatToolResponseMessage> pendingToolResults;
    /** Token 使用统计 */
    private TokenBudget tokenBudget;
}
```

**检查点触发时机**：

| 触发点 | 说明 | 来源参考 |
|--------|------|---------|
| 输入阶段 | 接收到用户消息后 | LangGraph "input" source |
| 每个 ReAct 步骤后 | reasoning + acting 完成 | LangGraph "loop" source |
| 工具执行前 | 保存当前状态（用于恢复） | LangGraph pending_writes |
| Plan 创建后 | 保存完整计划 | 本设计新增 |
| 上下文压缩后 | 保存压缩后的消息 | OpenCode compaction |

**持久化模式**（参考 LangGraph durability 配置）：

```java
public enum CheckpointDurability {
    /** 同步持久化：下一步开始前确保保存完成 */
    SYNC,
    /** 异步持久化：下一步执行期间保存 */
    ASYNC,
    /** 退出时持久化：仅在 Agent 退出时保存 */
    EXIT
}
```

**与 Nop Task 引擎的关系**：§3.10 中描述的 Task 引擎集成（GraphTaskStep/SuspendTaskStep）已内置检查点支持。对于 ReAct Agent，使用本节的 `ICheckpointStore`；对于图编排，复用 Nop Task 引擎的检查点机制。

**Hook 错误传播语义**（参考 AgentScope-Java `Hook.onEvent` + `ToolExecutor.invokeChunkCallback`）：

> Hook 错误处理遵循 AgentScope-Java 的分层策略：
> - AgentScope-Java `Hook.onEvent`：异常通过 Mono 链传播（propagate）
> - AgentScope-Java `ToolExecutor.invokeChunkCallback`：chunk callback 异常 log + swallow（不中断执行）
> - OpenCode hooks：无显式异常处理，由 agent loop 统一捕获

```
Hook 错误传播规则：

┌─────────────────────────────────────────────────────────┐
│  before_* 事件（before_reasoning, before_acting 等）     │
│  → 异常传播（propagate）：阻止后续步骤执行               │
│  → 例：before_acting 的参数验证失败 → 阻止工具执行      │
│                                                          │
│  after_* 事件（after_reasoning, after_acting 等）        │
│  → 异常记录 + 吞没（log & swallow）：不影响主流程        │
│  → 例：after_acting 的上下文监控失败 → 记录警告，继续   │
│  → 参考：AgentScope-Java ToolExecutor 的 chunk callback │
│                                                          │
│  on_error 事件                                           │
│  → 异常记录 + 吞没：错误处理 Hook 本身不能抛出异常      │
│  → 例：JSON 恢复 Hook 失败 → 记录错误，让原始异常传播   │
│                                                          │
│  工具执行回调（ToolExecutor callback）                    │
│  → 异常记录 + 吞没：参考 AgentScope-Java 的 try-catch   │
│  → 不影响其他工具的并行执行                              │
└─────────────────────────────────────────────────────────┘
```

---

## 4. 设计决策记录（带来源标注）

### 4.1 复用 ChatMessage 而非引入 ContentBlock

| 维度 | ChatMessage 平铺（现有） | ContentBlock 多态（AgentScope-Java） |
|------|-------------------------|-------------------------------------|
| 改动量 | **零** — 已有完整实现 | 需要重建整个消息体系 |
| API 对应 | 直接对应 OpenAI/Anthropic API | 需要转换层 |
| 流式支持 | ChatStreamAccumulator 已有 | 需要新建 |
| 类型安全 | 每种消息独立类 | 运行时 getBlocks(type) 查询 |
| 多模态 | ChatMessage.attachment 可扩展 | ContentBlock 天然支持 |

**决策**：复用 ChatMessage。多模态通过 ChatAttachment（已有 ChatAttachment 类）扩展，不需要 ContentBlock 体系。

### 4.2 CompletionStage 而非 Reactor Flux

| 维度 | CompletionStage + Flow.Publisher | Reactor Flux/Mono |
|------|----------------------------------|-------------------|
| Nop 平台兼容 | ✅ 标准 Java | ❌ 需要额外依赖 |
| 学习曲线 | 低（标准 JDK） | 高（Reactor 概念） |
| 灵感来源 | Solon-AI（Publisher<ChatResponse>） | AgentScope-Java |

**决策**：使用 CompletionStage + Flow.Publisher。流式用 Flow.Publisher<AgentEvent>（标准 JDK 9+ 响应式流）。

### 4.3 复用 IToolManager 而非引入 Toolkit

| 维度 | IToolManager（现有） | Toolkit（AgentScope-Java） |
|------|---------------------|---------------------------|
| 工具加载 | ✅ .tool.xml 声明式 + VirtualFileSystem | 代码注册 registerObject() |
| 工具执行 | ✅ callTool + callTools（顺序/并行） | callTools（顺序/并行 + Semaphore） |
| 拦截器 | ✅ IToolCallInterceptor | AgentScope Hook（更重） |
| 工具发现 | ✅ listTools + loadTool | toolkit.getToolSchemas() |

**决策**：直接复用 IToolManager。需要做的是桥接 ChatToolCall → AiToolCall 和 AiToolCallResult → ChatToolResponseMessage 的转换层。

### 4.4 复用 IChatService + ILlmDialect 而非引入 IChatModel

| 维度 | IChatService + ILlmDialect（现有） | IChatModel（v1 文档） |
|------|-----------------------------------|----------------------|
| Provider 支持 | ✅ OpenAI/Anthropic/Gemini/Ollama | 需要重新实现 |
| 流式 | ✅ Flow.Publisher<ChatStreamChunk> | Flux<ChatResponseChunk> |
| 方言适配 | ✅ ILlmDialect（4 种实现） | Formatter/Dialect |
| 工具 Schema | ✅ ChatToolDefinition + convertToolDefinitions | ToolSchema |

**决策**：直接复用 IChatService。ReActAgent 持有 IChatService 引用即可。

### 4.5 Hook 使用事件模式而非固定 4 方法（v3 更新）

| 维度 | 事件模式 + 便利基类（v3） | 4 方法 Hook（v2） | 8 事件类型（AgentScope-Java） |
|------|---------------------------|---------------------|---------------------------|
| 复杂度 | 中 | 低 | 高 |
| 灵活性 | **高**（任意事件） | 低（仅 4 个生命周期点） | 中（8 种预定义事件） |
| 兼容性 | ✅ 与 agent.xdef 完全一致 | ❌ 需要修改 agent.xdef | — |
| 学习成本 | 中（需要了解事件模式） | 低（4 个方法） | 高（8 种事件类型） |
| Skill 集成 | ✅ 技能可注册任意事件 | ❌ 只能注册 4 个方法 | — |

**决策**：使用事件模式 + 便利基类。Skill 可以注册任意事件（如 PlanSkill 注册 before_reasoning 和 after_acting），通过 PlanHookBase 提供类型安全的便利方法。

**灵感来源**：
- agent.xdef 定义：`event="!event-pattern-string"`（v3 关键发现）
- AgentScope-Java：Hook.onEvent(HookEvent)（8 种事件）
- Solon-AI：ChatInterceptor（简洁 before/after）

### 4.6 Tool Call 双模式（NATIVE/XML/AUTO）（v3 新增）

| 维度 | ToolCallModeAdapter（v3） | 仅 NATIVE（v2） |
|------|-------------------------|-----------------|
| LLM 覆盖率 | ✅ 高（支持原生 + XML） | ❌ 低（仅原生 LLM） |
| 灵活性 | **高**（AUTO 自动检测） | 低（固定 NATIVE） |
| 实现复杂度 | 中 | 低 |
| 灵感来源 | OpenAI/Anthropic/Gemini tool_call, DeepAgents XML calling | — |

**决策**：实现 ToolCallModeAdapter，支持 NATIVE/XML/AUTO 三种模式。
- NATIVE：使用 ILlmDialect.convertToolDefinitions()
- XML：注入 Schema + Examples 到 prompt，用 XmlResponseParser 解析
- AUTO：检测 ILlmDialect.supportToolCalls()，自动选择

### 4.7 错误处理 + AI 修复分支（v3 新增）

| 维度 | RetryAdvisor + AI 修复（v3） | 简单重试（v2） |
|------|-----------------------------|-----------------|
| 智能程度 | **高**（AI 决策重试/修复/跳过/终止） | 低（固定重试次数） |
| 灵活性 | **高**（支持 AI 修复分支） | 低（仅重试） |
| Bug 跟踪 | ✅ bug-report 工具（prompt 驱动） | ❌ 无 |
| 灵感来源 | agent-design.md (RetryAdvisor), OpenCode (Compaction), DeepAgents (Error handling) | — |

**决策**：集成 RetryAdvisor Agent + AI 修复分支 + bug-report 工具（prompt 驱动）。
- 工具失败 → 调用 RetryAdvisor via call-agent
- RetryAdvisor 返回 suggestedAction：retry/modify_params/repair/skip/abort
- repair：创建隔离分支，让 AI 修复
- abort：触发 bug-report 工具（prompt 驱动，不需要 IBugReporter 接口）
- 关键设计是触发时机（Hook 事件 + prompt），不是接口层次

### 4.8 编排复用 Nop Flow 而非自建图引擎

| 维度 | Nop Flow（现有） | 自建 GraphOrchestrator |
|------|-----------------|----------------------|
| 声明式 | ✅ flow.xdef / task.xdef | 代码构建 |
| 可视化 | ✅ 已有编辑器 | 需要自建 |
| 状态持久化 | ✅ FlowContext | 需要自建 |
| 灵感来源 | Solon-AI FlowEngine、LangGraph StateGraph | — |

**决策**：Agent 实现 IAgentNode 接口，包装为 FlowActivity 插入 Nop Flow。

### 4.9 Plan 与 Todo 独立（v3 新增）

| 维度 | 独立 Plan + Todo（v3） | Plan 包含 Todo（v2） |
|------|-----------------------|---------------------|
| 复杂度 | **低**（两个简单系统） | 高（耦合复杂） |
| 灵活性 | **高**（各自独立使用） | 低（必须一起使用） |
| 使用场景 | 清晰（复杂计划用 Plan，简单列表用 Todo） | 模糊 |
| 灵感来源 | agent-design.md (Plan), manage-todo-list.tool.xml (Todo) | — |

**决策**：Plan 和 Todo 作为独立系统。
- Plan：结构化多阶段计划，支持依赖关系
- Todo：线性任务队列，简单易用
- 可选关联：TodoItem.relatedTaskNo 引用 Plan Task

### 4.10 取消机制（v4 新增）

| 维度 | ICancelToken + 5 checkpoints（v4） | Thread.interrupt（传统方式） |
|------|----------------------------------|-----------------------------|
| 安全性 | **高**（协作式，不会意外中断） | 低（强制中断，可能导致状态不一致） |
| 可控性 | **高**（Agent 在检查点响应） | 低（立即中断，无法控制） |
| 检查点 | 5 个（loop start/before LLM/during streaming/before tool/after tool） | 1 个（随时） |
| 灵感来源 | OpenCode（AbortController）、AgentScope-Java（AtomicBoolean）、Nop（ICancelToken） | — |

**决策**：使用 ICancelToken 协作式取消，5 个检查点。
- 检查点：loop start、before LLM call、during streaming chunks、before tool execution、after tool execution
- in-flight 工具：等待完成但丢弃结果
- 不使用 Thread.interrupt，避免状态不一致

### 4.11 消息队列模式（v4 新增）

| 维度 | interrupt vs queued（v4） | 简单队列（传统方式） |
|------|--------------------------|---------------------|
| 灵活性 | **高**（两种消息语义） | 低（仅排队） |
| 紧急响应 | **高**（INTERRUPT 立即中断） | 无 |
| 非阻塞 | **高**（QUEUED 通过 CompletableFuture） | 低（可能阻塞） |
| 灵感来源 | OpenCode（Promise callback queue）、AgentScope-Java（interrupt flag）、Nop Workflow（Command Queue） | — |

**决策**：两种消息类型（interrupt vs queued）。
- INTERRUPT：立即中断当前执行，插入消息，然后恢复
- QUEUED：排队等待当前循环迭代结束后再处理
- AgentSession 维护 busy 状态和消息队列

### 4.12 并行工具执行策略（v4 新增）

| 维度 | Per-tool error isolation + JoinType（v4） | 简单并行（传统方式） |
|------|-----------------------------------------|---------------------|
| 错误隔离 | **高**（per-tool try-catch） | 低（一个失败全部失败） |
| 并发限制 | **高**（可配置 1-25） | 低（无限制） |
| JoinType | 支持（ALL_SUCCESS/ANY_SUCCESS/ALL_COMPLETED） | 无 |
| 灵感来源 | OpenCode（Promise.all + per-tool catch）、Nop Task（ParallelTaskStep JoinType）、AgentScope-Java（Semaphore） | — |

**决策**：Per-tool 错误隔离 + 可配置并发 + JoinType。
- Per-tool 状态追踪：PENDING → RUNNING → COMPLETED/ERROR
- 错误隔离：per-tool try-catch，一个失败不影响其他
- 并发限制：默认 5，最大 25（与 OpenCode 一致）
- JoinType：支持 ALL_SUCCESS/ANY_SUCCESS/ALL_COMPLETED

### 4.13 错误分类优于统一重试（v5 新增）

| 维度 | 错误分类重试（v5） | 统一重试 | 不重试 |
|------|-------------------|---------|-------|
| 效率 | **高**（可重试才重试） | 低（全部重试） | — |
| 可靠性 | **高**（不可重试快速失败） | 低（浪费重试次数） | 低（可恢复错误直接失败） |
| 灵感来源 | OpenCode `model-error-classifier.ts`、Codex `errors.py`、AgentScope-Java `ExecutionConfig.RETRYABLE_ERRORS` | — | — |

**决策**：所有错误在重试前必须分类（RETRYABLE / NON_RETRYABLE / RECOVERABLE）。可重试错误自动指数退避重试，不可重试错误直接返回给 LLM 或快速失败，可恢复错误（如上下文溢出）经特殊处理后重试。

### 4.14 三层上下文窗口保护（v5 新增）

| 维度 | 三层保护（v5） | 单层压缩 | 无保护 |
|------|--------------|---------|-------|
| 平滑性 | **高**（70%→78%→溢出） | 低（单一阈值） | — |
| 可靠性 | **高**（溢出仍可恢复） | 中（可能来不及） | 低（直接崩溃） |
| 灵感来源 | OpenCode `context-window-monitor.ts` + `preemptive-compaction.ts` + `anthropic-context-window-limit-recovery/` | DeepAgents `SummarizationMiddleware`（85%触发） | — |

**决策**：三层渐进式保护——70% 注入状态警告、78% 触发预防压缩、溢出时强制压缩后重试。每层独立可配置。

### 4.15 循环检测用签名而非语义（v5 新增）

| 维度 | 工具调用签名（v5） | 语义相似度 | 固定调用次数限制 |
|------|-------------------|-----------|---------------|
| 准确性 | **高**（精确匹配） | 中（模型依赖） | 低（无法区分不同操作） |
| 性能 | **O(1)**（哈希比较） | O(n)（embedding 计算） | O(1) |
| 灵感来源 | OpenCode `loop-detector.ts`（toolName + sortedJSON） | — | LangGraph `GraphRecursionError` |

**决策**：使用工具调用签名（toolName + sorted JSON args）进行循环检测。两级策略：连续 5 次相同签名 → 注入提示（参考 Solon-AI StopLoopInterceptor），连续 15 次 → 硬中断。

### 4.16 模型回退链而非单一模型（v5 新增）

| 维度 | 有序回退链 + 冷却（v5） | 单一模型 | 随机模型选择 |
|------|----------------------|---------|------------|
| 可靠性 | **高**（自动切换） | 低（单点故障） | 中（可能选到故障模型） |
| 可预测性 | **高**（有序链） | 高 | 低（随机） |
| 灵感来源 | Spring-AI-Alibaba `ModelFallbackInterceptor`、OpenCode `runtime-fallback/auto-retry.ts` + `fallback-state.ts` | — | — |

**决策**：支持有序模型回退链。每个模型独立冷却追踪（默认 60s，来自 OpenCode fallback-state.ts）。断路器（§3.12.2）触发时自动切换到下一个可用模型。

---

## 5. 模块演进计划

### 5.1 改动范围

```
nop-ai/
├── nop-ai-agent/              ← 主要改动
│   ├── api/
│   │   ├── IAgent.java              # 新增：Agent 核心接口
│   │   ├── ToolCallMode.java        # v3 新增：工具调用模式枚举
│   │   ├── ISkill.java             # v3 新增：技能接口
│   │   ├── AgentRequest.java        # 新增：Agent 请求
│   │   ├── AgentResponse.java       # 新增：Agent 响应
│   │   ├── AgentEvent.java          # 新增：Agent 流式事件
│   │   └── AgentHookModel.java     # 已有：Hook 配置模型
│   ├── core/
│   │   ├── ReActAgent.java          # 新增：ReAct Agent 实现
│   │   ├── ReActContext.java        # 新增：ReAct 执行上下文
│   │   ├── MessageConverter.java    # 新增：ChatMessage ↔ AiToolCall 桥接
│   │   ├── AgentMemoryImpl.java     # 新增：基于 IAiMemoryStore 的 Agent Memory
│   │   ├── ToolCallModeAdapter.java # v3 新增：工具调用模式适配器
│   │   ├── XmlResponseParser.java   # v3 新增：XML 响应解析器
│   │   ├── HookEventTrigger.java    # v3 新增：Hook 事件触发器
│   │   ├── RetryAdvisorClient.java  # v3 新增：RetryAdvisor 调用客户端
│   ├── hook/
│   │   ├── PlanHook.java            # v3 新增：Plan Hook（Ralph Loop）
│   │   ├── ContextCompressionHook.java # 新增：上下文压缩 Hook
│   │   ├── LoopDetectionHook.java   # 新增：循环检测 Hook
│   │   └── AgentHookBase.java      # v3 新增：Hook 便利基类
│   ├── skill/
│   │   ├── ISkill.java              # v3 新增：技能接口
│   │   ├── SkillManager.java        # v3 新增：技能管理器
│   │   ├── PlanSkill.java           # v3 新增：Plan 技能
│   │   └── ManagePlanExecutor.java  # v3 新增：Plan 管理工具
│   ├── todo/
│   │   ├── TodoManager.java         # v3 新增：Todo 管理器
│   │   ├── PlanTodoSync.java        # v3 新增：Plan-Todo 同步
│   │   └── TodoItem.java           # v3 新增：Todo 模型
│   └── flow/
│       ├── AgentFlowActivity.java   # 新增：Agent → FlowActivity 适配
│       └── AgentNodeAdapter.java     # 新增：IAgent → IAgentNode 适配
│
├── nop-ai-toolkit/            ← v3 扩展
│   ├── executors/
│   │   └── CallAgentExecutor.java   # v3 扩展：支持 mode/context
│   └── model/
│       ├── AiToolCalls.java          # 已有：对应 call-tools.xdef
│       └── AiToolCall.java          # 已有：对应 tool-call.xdef
│
├── nop-ai-api/                ← 不改动
├── nop-ai-core/               ← 不改动（继续清理 @Deprecated）
├── nop-ai-llm/                ← 不改动
└── nop-ai-coder/              ← 适配新 Agent 接口
```

### 5.2 依赖关系

```
IAgent（新增）
├── IChatService（已有 nop-ai-api）
├── IToolManager（已有 nop-ai-toolkit）
├── IAiMemoryStore（已有 nop-ai-agent）
├── ChatMessage 体系（已有 nop-ai-api）
├── ILlmDialect（已有 nop-ai-core）
├── AgentPlan 体系（已有 nop-ai-agent）
├── ToolCallModeAdapter（v3 新增）
├── SkillManager（v3 新增）
└── bug-report 工具（.tool.xml + markdown 触发指令）
```

### 5.3 实施优先级

**P0（核心功能）**：

1. IAgent 接口 + AgentRequest/AgentResponse/AgentEvent
2. ReActAgent 实现（不含错误处理）
3. ToolCallModeAdapter（NATIVE/XML/AUTO）
4. MessageConverter（ChatMessage ↔ AiToolCall）
5. Hook 事件触发器（基于 agent.xdef）
6. AgentFlowActivity + IAgentNode

**P1（v3 新增）**：

1. ISkill 接口 + SkillManager
2. PlanSkill + PlanHook（Ralph Loop）
3. 错误处理流程（RetryAdvisor 集成）
4. AI 修复分支
5. bug-report 工具（.tool.xml）+ 错误触发时机设计
6. call-agent 扩展（mode/context）
7. TodoManager + PlanTodoSync

**P2（完善功能）**：

1. 上下文压缩 + CompressionAdvisor
2. Session 持久化
3. 流式事件推送优化
4. 性能优化

---

## 6. 参考框架对照表（带来源标注）

| 设计要素 | 本方案 | 主要来源 | 次要来源 |
|---------|--------|---------|---------|
| Agent 接口 | IAgent.chat + chatStream | AgentScope-Java（接口分离） | Spring-AI-Alibaba（invoke/stream） |
| 消息模型 | **复用** ChatMessage 平铺 | — (已有) | — |
| LLM 调用 | **复用** IChatService + ILlmDialect | — (已有) | Solon-AI（ChatDialect 启发） |
| 工具系统 | **复用** IToolManager + IToolExecutor | — (已有) | AgentScope-Java（Toolkit 启发） |
| 工具拦截 | **复用** IToolCallInterceptor | — (已有) | AgentScope-Java（Hook 启发） |
| Agent Hook | 事件模式 + 便利基类 | agent.xdef（v3 关键发现） | Solon-AI（ChatInterceptor 简洁性） |
| ReAct 循环 | reasoning + acting + 错误处理 | AgentScope-Java（ReActAgent） | Spring-AI-Alibaba（ReactAgent） |
| Tool Call 双模式 | ToolCallModeAdapter (NATIVE/XML/AUTO) | OpenAI/Anthropic/Gemini tool_call | DeepAgents（XML calling） |
| 错误处理 | RetryAdvisor + AI 修复 + bug-report 工具（prompt 驱动） | agent-design.md（Advisor Agent） | OpenCode（Compaction） |
| 记忆系统 | **复用** IAiMemoryStore + AiMemoryConfig | — (已有) | OpenCode（compaction） |
| 上下文压缩 | IContextCompressor + CompressionAdvisor | OpenCode（CompactionAgent） | DeepAgents（SummarizationMiddleware） |
| 流式事件 | AgentEvent sealed interface + Flow.Publisher | Pi-Agent（事件类型设计） | AgentScope-Java（EventType） |
| 多 Agent 编排 | **复用** Nop Flow + IAgentNode | Solon-AI（FlowEngine） | LangGraph（StateGraph） |
| 技能系统 | ISkill + SkillManager + Hook 注册 | Solon-AI（SkillDesc + isSupported） | DeepAgents（SkillsMiddleware） |
| Plan 系统 | PlanSkill + PlanHook（Ralph Loop） | agent-design.md（Plan model） | DeepAgents（TodoListMiddleware） |
| Todo 系统 | TodoManager（独立于 Plan） | manage-todo-list.tool.xml | — |
| call-agent 扩展 | mode（sync/async/detached）+ context | call-agent.tool.xml（v3 扩展） | agent-design.md（Advisor Agent） |
| 中断机制 | interrupt() + AtomicBoolean | AgentScope-Java（interrupt） | LangGraph（interrupt_before） |
| 权限系统 | **复用** AgentPermissionModel | OpenCode（Permission.Ruleset） | — |
| 状态持久化 | FlowContext + Session | LangGraph（Checkpoint） | agent-design.md（session.json） |
| 取消机制 | ICancelToken + 5 checkpoints | OpenCode（AbortController） | AgentScope-Java（AtomicBoolean） |
| 消息队列 | interrupt vs queued | OpenCode（Promise callback queue） | Nop Workflow（Command Queue） |
| 并行工具 | Per-tool error isolation + JoinType | OpenCode（Promise.all） | Nop Task（ParallelTaskStep） |
| Memory 压缩 | 两阶段（pruning+LLM 摘要）+ 保护窗口 | OpenCode（CompactionAgent） | AgentScope-Java（AutoContextMemory） |
| Task 引擎集成 | GraphTaskStep + SuspendTaskStep + LoopTaskStep | Nop Task（已有） | LangGraph（StateGraph） |
| 错误分类重试 | ErrorClassifier + RetryConfig（三级分类） | OpenCode（model-error-classifier） | Codex（errors.py）、AgentScope-Java（RETRYABLE_ERRORS） |
| 断路器 | ICircuitBreaker（CLOSED/OPEN/HALF_OPEN） | OpenCode（manager-circuit-breaker） | OpenCode（fallback-state.ts cooldown） |
| 循环检测 | ILoopDetector（签名 + 两级检测） | OpenCode（loop-detector.ts） | Solon-AI（StopLoopInterceptor） |
| 上下文保护 | ContextWindowConfig（三层保护） | OpenCode（context-window-monitor + preemptive-compaction） | DeepAgents（SummarizationMiddleware） |
| 多级超时 | TimeoutConfig + TimeoutBudget（三级级联） | OpenCode（prompt-timeout-context） | Codex（exec.rs）、AgentScope-Java（ExecutionConfig） |
| 工具安全验证 | ToolValidationResult（四步验证流程） | AgentScope-Java（ToolValidator） | OpenCode（json-error-recovery）、Codex（sandbox） |
| 模型回退 | FallbackConfig（回退链 + 冷却追踪） | Spring-AI-Alibaba（ModelFallbackInterceptor） | OpenCode（runtime-fallback） |
| 检查点恢复 | ICheckpointStore + AgentCheckpoint | LangGraph（Checkpoint） | Solon-AI（RedisAgentSession） |
| 管道部分失败 | CompositeExecutionException | AgentScope-Java（CompositeAgentException） | — |

---

## 附录：研究来源索引

### 已分析的 ~/ai 框架（10 个）

| 框架 | 分析深度 | 本方案采纳度 |
|------|---------|-------------|
| **AgentScope-Java** | 完整源码阅读（Agent/AgentBase/ReActAgent/Tool/Model/Memory/Hook） | ★★★★☆ ReAct 循环、Hook 概念、中断机制 |
| **Solon-AI** | 完整源码阅读（ChatModel/ReActAgent/TeamAgent/Skill/Flow） | ★★★★☆ 简洁 Hook、Skill 条件激活、Flow 编排 |
| **Spring-AI-Alibaba** | 完整源码阅读（Agent/ReactAgent/AgentToolNode/Hook） | ★★★☆☆ AgentNode 概念、Hook 跳转 |
| **LangGraph** | 核心源码阅读（StateGraph/Pregel/Channel/Checkpoint） | ★★☆☆☆ 检查点概念 |
| **DeepAgents** | 核心源码阅读（graph.py/middleware） | ★★★☆☆ 中间件栈、Plan、上下文压缩 |
| **VoltAgent** | 核心源码阅读（agent.ts/workflow/chain） | ★★☆☆☆ 中间件栈概念 |
| **OpenCode** | 核心源码阅读（agent.ts/compaction/bash.ts/permission） | ★★★★☆ 上下文压缩、权限规则集 |
| **Pi-Agent** | 核心源码阅读（agent.ts/agent-loop.ts） | ★★★☆☆ 事件类型设计 |
| **Codex** | README + shell-tool-mcp | ★☆☆☆☆ MCP 沙箱概念 |
| **Open-SWE** | 核心源码阅读（server.py/middleware/tools） | ★★☆☆☆ 确定性中间件 |

### Nop AI 代码库深度分析（v3 新增）

| 对象 | 文件 | 分析深度 |
|------|------|---------|
| agent.xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef` | 完整 |
| agent-plan.xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef` | 完整 |
| call-tools.xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/call-tools.xdef` | 完整 |
| tool-call.xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/tool-call.xdef` | 完整 |
| tool.xdef | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/tool.xdef` | 完整 |
| call-agent.tool.xml | `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/call-agent.tool.xml` | 完整 |
| manage-todo-list.tool.xml | `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/manage-todo-list.tool.xml` | 完整 |
| agent-design.md | `nop-ai/nop-ai-agent/docs/agent-design.md` | 完整 |

### 关键分析文件路径

| 对象 | 文件 |
|------|------|
| nop-ai-api 消息 | `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatAssistantMessage.java` |
| nop-ai-api 流式 | `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/stream/ChatStreamAccumulator.java` |
| nop-ai-api LLM | `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/IChatService.java` |
| nop-ai-toolkit 核心 | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/api/IToolManager.java` |
| nop-ai-toolkit 实现 | `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java` |
| nop-ai-core 方言 | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/ILlmDialect.java` |
| nop-ai-agent 记忆 | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/IAiMemoryStore.java` |
| nop-ai-agent 计划 | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/AgentPlan.java` |
| AgentScope-Java Agent | `~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/Agent.java` |
| AgentScope-Java ReAct | `~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java` |
| Solon-AI ReAct | `~/ai/solon-ai/solon-ai-agent/src/main/java/org/noear/solon/ai/agent/react/ReActAgent.java` |

---

## 总结

### v3 核心创新（相比 v2）

1. **Tool Call 双模式**：NATIVE/XML/AUTO 三种模式，支持不兼容工具调用的 LLM
2. **错误处理 + AI 修复**：RetryAdvisor + 隔离分支修复 + bug-report 工具（prompt 驱动），智能决策失败处理
3. **Hook 事件模式**：基于 agent.xdef 的事件模式，提供便利基类，支持 Skill 注册任意事件
4. **Skill 系统**：ISkill 接口 + SkillManager，技能可注册工具和 Hook
5. **Plan as Skill + Ralph Loop**：PlanSkill 通过 Hook 驱动 Agent 循环，直到所有任务完成
6. **Plan vs Todo 独立**：两个独立系统，清晰分离复杂计划和简单列表
7. **call-agent 扩展**：mode（sync/async/detached）+ context（结构化数据传递）

### v4 核心创新（Phase 5 跨语言研究）

8. **取消机制**：ICancelToken 协作式取消，5 个检查点，in-flight 工具安全等待
9. **消息队列**：interrupt（立即中断）vs queued（等 loop 结束）两种消息语义
10. **并行工具执行**：per-tool 状态追踪、错误隔离、可配置并发度
11. **两阶段 Memory 压缩**：pruning（裁剪旧输出）+ LLM 摘要（结构化摘要模板），保护窗口
12. **Task 引擎集成**：复用 Nop 的 GraphTaskStep/ParallelTaskStep/SuspendTaskStep/LoopTaskStep

### v5 核心创新 — 全面容错处理体系

13. **三级错误分类**：RETRYABLE / NON_RETRYABLE / RECOVERABLE，所有错误先分类再决策。参考 OpenCode `model-error-classifier.ts` + Codex `errors.py` + AgentScope-Java `ExecutionConfig`
14. **完整断路器**：CLOSED → OPEN → HALF_OPEN 状态机，per-model 追踪，参考 OpenCode `manager-circuit-breaker`（业界独创）
15. **签名式循环检测**：toolName + sorted JSON args 签名跟踪 + 两级策略（5次软提示→15次硬中断），参考 OpenCode `loop-detector.ts` + Solon-AI `StopLoopInterceptor`
16. **三层上下文保护**：70% 警告 → 78% 预防压缩 → 溢出恢复，参考 OpenCode 三层体系（`context-window-monitor.ts` + `preemptive-compaction.ts` + `anthropic-context-window-limit-recovery/`）
17. **多级超时预算**：Agent(30min) / LLM(2min) / Tool(5min) 三级 + 预算级联共享，综合 OpenCode + Codex + AgentScope-Java 最佳实践
18. **四步工具验证**：工具名存在性 → JSON 解析 → Schema 验证 → 安全检查，参考 AgentScope-Java `ToolValidator` + OpenCode `json-error-recovery`
19. **模型回退链**：有序回退链 + per-model 冷却追踪(60s)，参考 Spring-AI-Alibaba `ModelFallbackInterceptor` + OpenCode `runtime-fallback`
20. **检查点恢复**：可插拔存储 + 5 个关键触发点 + 三种持久化模式，参考 LangGraph Checkpoint + Solon-AI RedisAgentSession

### v3 关键发现

1. **agent.xdef 使用事件模式**：不是固定的 4 方法接口，而是灵活的事件模式匹配
2. **已有 18 个工具执行器**：tool.xdef 定义的 schema + examples，Few-shot prompting
3. **ILlmDialect 已支持 native tool_call**：4 种 LLM 提供商都已实现
4. **AgentPlanError 已存在**：包含 resolution + attemptNumber，为错误处理提供基础
5. **Advisor Agent 模式**：所有决策通过 call-agent 调用专门的 Agent
6. **Todo 和 Plan 独立**：manage-todo-list.tool.xml 定义了线性 Todo，与复杂 Plan 分离

### v4 关键发现（Phase 5 跨语言研究）

7. **OpenCode 消息队列模式最完整**：busy session 入队 + Promise callback + interrupt vs queued
8. **OpenCode 压缩策略最成熟**：两阶段 + 保护窗口 + 结构化摘要模板
9. **Nop 已有成熟 Task 引擎**：GraphTaskStep/ParallelTaskStep/SuspendTaskStep，不需要从 LangGraph 借鉴

### v5 关键发现（容错处理深度调研）

10. **OpenCode 容错体系最完整**：错误分类、断路器、循环检测、三层上下文保护、JSON 恢复、会话恢复、运行时降级——远超其他框架
11. **LangGraph 检查点设计最成熟**：三种持久化模式（sync/async/exit）、精确恢复到任意步骤、pending_writes 保存
12. **Spring-AI-Alibaba 拦截器体系最灵活**：per-tool 重试配置、自定义异常谓词、指数退避 + jitter
13. **Solon-AI 两级错误区分最实用**：逻辑错误（IllegalArgumentException）不重试交给 AI 修复，物理错误（网络/超时）才重试
14. **AgentScope-Java 管道失败处理最完整**：CompositeAgentException 聚合所有错误，FanoutPipeline 继续执行不中断

### v3 设计原则

1. **复用优于重建**：ChatMessage、IToolManager、IChatService、AgentPlan 体系直接复用
2. **事件驱动 Hook**：使用 agent.xdef 定义的事件模式，提供便利基类简化实现
3. **Advisor Agent 决策**：重试、修复、压缩等通过 Advisor Agent 智能决策
4. **双模式兼容**：支持 NATIVE 和 XML 两种工具调用模式，自动检测
5. **独立可插拔**：Skill 等核心概念通过接口实现；bug 上报等行为靠 tool.xml + prompt 驱动，不需要强类型接口

### v4 设计原则（Phase 5 跨语言研究）

6. **协作式取消**：ICancelToken 在关键检查点检查，不使用 Thread.interrupt
7. **消息语义区分**：interrupt 立即中断，queued 等待完成，根据紧急程度选择

### v5 设计原则（容错处理体系）

8. **分类优于统一重试**：所有错误先分类再决策，可重试的自动重试，不可重试的快速失败
9. **多层防御**：单点故障不应导致系统崩溃，每层独立保护（断路器→循环检测→超时→降级→恢复）
10. **预算管理**：Token 和时间都是有限资源，需要追踪和级联分配
11. **优雅降级**：部分失败不应导致整体崩溃，保留成功结果，聚合失败信息

(End of v3/v4/v5 design document - total 4000+ lines)
