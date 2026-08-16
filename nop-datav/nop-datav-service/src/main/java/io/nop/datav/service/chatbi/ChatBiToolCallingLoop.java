package io.nop.datav.service.chatbi;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.json.JsonTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_TOOL_NAME;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_NO_RESULT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED;

/**
 * ChatBI 轻量自建 tool-calling 循环（裁定 A + 裁定 L 泛化）。
 *
 * <p>**不复用 {@code IAgentEngine}**（ChatBI 为无状态单轮 Non-Goal，agent engine 的 session/budget/
 * guardrail/team/memory 重载不适用）。参考 {@code AgentToolDispatcher} 的类型转换逻辑（裁定 B，
 * 经 {@link ChatBiTypeConverter} 内联复制）。</p>
 *
 * <p><b>D6-1b 泛化（裁定 L）</b>：循环从 D6-1 查询专用泛化为查询 + 看板生成两路径共享。4 个泛化点：
 * <ol>
 *   <li><b>system prompt</b>：从硬编码改为 {@code run()} 入参（查询传查询 prompt，生成传生成 prompt）。</li>
 *   <li><b>结果提取</b>：从硬编码 query-columns/rows 解析改为可插拔 {@link ToolResultHandler} 回调。</li>
 *   <li><b>返回类型</b>：泛化 {@link ChatBiResult}（新增 {@code createdEntityId} 字段，裁定 K）。</li>
 *   <li><b>context 构建</b>：{@code run()} 接受 {@code operator} 入参，构建携带 operator 的
 *       {@link ChatBiToolExecuteContext}（裁定 G）。</li>
 * </ol>
 * </p>
 *
 * <p>循环语义（裁定 D）：
 * <ol>
 *   <li>构建初始 {@link ChatRequest}（system prompt + 用户消息 + 工具清单）+
 *       {@link ChatBiToolExecuteContext}（携带 operator）。</li>
 *   <li>循环（最多 {@code maxIterations} 次）：
 *     <ul>
 *       <li>{@link IChatService#call} → {@link ChatResponse#outputToolCalls()}。</li>
 *       <li>为空 → LLM 给出最终文本答案 → handler 收尾 → 返回 {@link ChatBiResult}。</li>
 *       <li>非空 → 每个 {@link ChatToolCall} 转 {@link AiToolCall}（保留原引用追踪 toolCallId）→
 *           {@link IToolManager#callTool} 执行 → 结果转 {@link ChatToolResponseMessage} →
 *           {@link ToolResultHandler#handle} 累加字段 → {@link ChatRequest#addToolResponse} 回喂 →
 *           继续循环。</li>
 *     </ul>
 *   </li>
 *   <li>迭代次数 ≥ {@code maxIterations} → 抛 {@code ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED}。</li>
 * </ol>
 * </p>
 */
public class ChatBiToolCallingLoop {

    private static final Logger LOG = LoggerFactory.getLogger(ChatBiToolCallingLoop.class);

    private final IChatService chatService;
    private final IToolManager toolManager;
    private final ICancelToken cancelToken;

    public ChatBiToolCallingLoop(IChatService chatService, IToolManager toolManager) {
        this(chatService, toolManager, null);
    }

    public ChatBiToolCallingLoop(IChatService chatService, IToolManager toolManager,
                                  ICancelToken cancelToken) {
        this.chatService = chatService;
        this.toolManager = toolManager;
        this.cancelToken = cancelToken;
    }

    /**
     * D6-1 查询路径入口（回归兼容）：使用查询 system prompt + 无 operator + 查询结果提取 handler。
     *
     * <p>迁移到泛化循环后行为不变（回归测试保护）。</p>
     *
     * @param question      用户自然语言问题
     * @param maxIterations tool-calling 轮次上限
     * @return ChatBI 结果（answer + 最后一次 query 结果的 columns/rows + 实际迭代次数）
     */
    public ChatBiResult run(String question, int maxIterations) {
        return run(question, ChatBiSystemPrompt.buildSystemPrompt(), null, maxIterations,
                ChatBiQueryResultHandlers.QUERY_HANDLER, null);
    }

    /**
     * 泛化循环入口（5 参重载，回归兼容）：等价于 6 参入口传入 null 历史（单轮）。
     */
    public ChatBiResult run(String userMessage, String systemPrompt, String operator,
                              int maxIterations, ToolResultHandler resultHandler) {
        return run(userMessage, systemPrompt, operator, maxIterations, resultHandler, null);
    }

    /**
     * 泛化循环入口（6 参重载，回归兼容）：等价于 7 参入口传入 admin=false（生成路径不消费 admin）。
     */
    public ChatBiResult run(String userMessage, String systemPrompt, String operator,
                              int maxIterations, ToolResultHandler resultHandler,
                              List<ChatMessage> historyMessages) {
        return run(userMessage, systemPrompt, operator, false, maxIterations, resultHandler, historyMessages);
    }

    /**
     * 泛化循环入口（6 参 + admin，P1-03 裁定 D4）：等价于 7 参入口传入 null 历史（单轮）。
     * 生成路径（chatToDashboard/chatToScreen）使用本重载传递身份。
     */
    public ChatBiResult run(String userMessage, String systemPrompt, String operator, boolean admin,
                              int maxIterations, ToolResultHandler resultHandler) {
        return run(userMessage, systemPrompt, operator, admin, maxIterations, resultHandler, null);
    }

    /**
     * 泛化循环入口（裁定 L + P1-03 裁定 D4）。供查询路径与生成路径共用。
     *
     * <p>多轮会话历史注入点（裁定 S2）：{@code historyMessages} 为 null 时行为与既有单轮完全一致
     * （回归兼容）；非 null 时在 system prompt 之后、本轮用户消息之前按序注入历史消息。</p>
     *
     * @param userMessage     用户消息（查询路径为问题，生成路径为看板描述）
     * @param systemPrompt    system prompt（注入式，裁定 L 泛化点 1）
     * @param operator        当前调用者身份（查询路径可 null，生成路径必传，裁定 L 泛化点 4 + 裁定 G）
     * @param admin           当前调用者是否 admin 角色（P1-03 裁定 D4 选项 B：随 context 传递给
     *                        datav-list/describe/query executor 做数据集可见性判定）
     * @param maxIterations   tool-calling 轮次上限
     * @param resultHandler   可插拔结果提取回调（裁定 L 泛化点 2）
     * @param historyMessages 多轮会话历史消息（null = 单轮，不注入；裁定 S2）
     * @return ChatBI 结果（answer + handler 累加字段 + iterations）
     */
    public ChatBiResult run(String userMessage, String systemPrompt, String operator, boolean admin,
                              int maxIterations, ToolResultHandler resultHandler,
                              List<ChatMessage> historyMessages) {
        List<AiToolModel> toolModels = toolManager.listTools();
        List<ChatToolDefinition> tools = ChatBiTypeConverter.toChatToolDefinitions(toolModels);

        ChatRequest request = new ChatRequest();
        request.setSystemPrompt(systemPrompt);
        // 裁定 S2：历史消息插在 system prompt 与本轮用户消息之间（null 时逐字节等价于既有单轮构造）
        if (historyMessages != null) {
            for (ChatMessage msg : historyMessages) {
                if (msg != null && !(msg instanceof ChatSystemMessage)) {
                    request.addMessage(msg);
                }
            }
        }
        request.addMessage(new ChatUserMessage(userMessage));
        request.setTools(tools);

        // 裁定 L 泛化点 4 + 裁定 G + P1-03 裁定 D4：构建携带 operator + admin 的 context
        IToolExecuteContext context = new ChatBiToolExecuteContext(cancelToken, operator, admin);

        ChatBiResult accumulator = new ChatBiResult();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            ChatResponse response = chatService.call(request, cancelToken);
            if (response == null) {
                throw new NopException(ERR_DATAV_CHATBI_NO_RESULT)
                        .param("question", userMessage);
            }

            List<ChatToolCall> toolCalls = response.outputToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 循环终止条件 (a)：outputToolCalls 为空 → 最终答案
                accumulator.setAnswer(response.outputText());
                accumulator.setIterations(iteration);
                return accumulator;
            }

            // 将 assistant 的 tool-call 消息加入对话（保持上下文）
            appendAssistantMessages(request, response.getMessages());

            // 执行每个 tool call，结果回喂
            for (ChatToolCall chatToolCall : toolCalls) {
                AiToolCall aiToolCall = ChatBiTypeConverter.toAiToolCall(chatToolCall);
                AiToolCallResult toolResult;
                try {
                    toolResult = toolManager.callTool(
                            chatToolCall.getName(), aiToolCall, context).join();
                } catch (CompletionException | CancellationException e) {
                    // AR-6: .join() 把 executor 失败包成 CompletionException，取消则抛 CancellationException。
                    // 解包到首个非 CompletionException cause，抛结构化 NopException（不再让原始异常泄漏到
                    // 公共 GraphQL action chatToQuery/chatToDashboard/chatToScreen）。
                    Throwable underlying = e;
                    while (underlying instanceof CompletionException && underlying.getCause() != null) {
                        underlying = underlying.getCause();
                    }
                    String reason = underlying.getMessage() != null
                            ? underlying.getMessage() : underlying.getClass().getName();
                    throw new NopException(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED)
                            .param(ARG_TOOL_NAME, chatToolCall.getName())
                            .param(ARG_REASON, reason)
                            .cause(underlying);
                }

                ChatToolResponseMessage toolResponse =
                        ChatBiTypeConverter.toChatToolResponseMessage(chatToolCall, toolResult);

                String toolResponseContent = toolResponse.getContent();

                // 裁定 L 泛化点 2：可插拔 handler 累加结果字段
                if (resultHandler != null) {
                    try {
                        resultHandler.handle(chatToolCall.getName(), toolResult, toolResponseContent, accumulator);
                    } catch (Exception ignore) {
                        // handler 失败不影响循环（与 D6-1 query 解析失败容忍一致），但记录 DEBUG 以便追踪
                        LOG.debug("nop.datav.chatbi.tool-handler-failed: tool={}", chatToolCall.getName(), ignore);
                    }
                }

                request.addToolResponse(chatToolCall.getId(), chatToolCall.getName(), toolResponseContent);
            }
            // 继续下一轮（回喂后让 LLM 决定是否继续 tool call 或给出最终答案）
        }

        // 循环终止条件 (b)：迭代达 maxIterations 仍无最终答案
        throw new NopException(ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED)
                .param("maxIterations", maxIterations)
                .param("question", userMessage);
    }

    /**
     * 将 ChatResponse 的 messages 追加到 ChatRequest（保留 assistant 文本/工具调用的对话上下文）。
     * 避免重复追加 system 消息。
     */
    @SuppressWarnings("unchecked")
    private void appendAssistantMessages(ChatRequest request, List<ChatMessage> responseMessages) {
        if (responseMessages == null) {
            return;
        }
        for (ChatMessage msg : responseMessages) {
            if (msg == null) {
                continue;
            }
            // 只追加非 system 消息（system prompt 已在开头设置）
            if (!(msg instanceof ChatSystemMessage)) {
                request.addMessage(msg);
            }
        }
    }

    // ==================== 查询路径内置 handler（D6-1 回归兼容） ====================

    /**
     * 解析 query 工具结果为 columns/rows 的内置 handler（D6-1 原硬编码逻辑提取，行为不变）。
     * 供查询路径 {@code chatToQuery} 复用，保证迁移到泛化循环后既有查询行为不变（回归保护）。
     */
    public static final class ChatBiQueryResultHandlers {
        public static final ToolResultHandler QUERY_HANDLER = (toolName, result, content, accumulator) -> {
            if (!DatavQueryDatasetExecutor.TOOL_NAME.equals(toolName)) {
                return;
            }
            if (!"success".equals(result.getStatus()) || result.getError() != null || content == null) {
                return;
            }
            try {
                Object parsedObj = JsonTool.parseNonStrict(content);
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = parsedObj instanceof Map
                        ? (Map<String, Object>) parsedObj
                        : null;
                if (parsed == null) {
                    return;
                }
                Object cols = parsed.get("columns");
                Object rws = parsed.get("rows");
                if (cols instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> colList = (List<String>) cols;
                    accumulator.setColumns(colList);
                }
                if (rws instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rowList = (List<Map<String, Object>>) rws;
                    accumulator.setRows(rowList);
                }
            } catch (Exception ignore) {
                // 解析失败不影响循环，保留前一次快照；记录 DEBUG 以便追踪
                LOG.debug("nop.datav.chatbi.query-result-parse-failed: tool={}", toolName, ignore);
            }
        };

        private ChatBiQueryResultHandlers() {
        }
    }
}
