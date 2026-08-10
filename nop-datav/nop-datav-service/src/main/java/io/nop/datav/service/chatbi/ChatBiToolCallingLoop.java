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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_NO_RESULT;

/**
 * ChatBI 轻量自建 tool-calling 循环（裁定 A）。
 *
 * <p>**不复用 {@code IAgentEngine}**（ChatBI 为无状态单轮 Non-Goal，agent engine 的 session/budget/
 * guardrail/team/memory 重载不适用）。参考 {@code AgentToolDispatcher} 的类型转换逻辑（裁定 B，
 * 经 {@link ChatBiTypeConverter} 内联复制）。</p>
 *
 * <p>循环语义（裁定 D）：</p>
 * <ol>
 *   <li>构建初始 {@link ChatRequest}（system prompt + 用户问题 + 工具清单）。</li>
 *   <li>循环（最多 {@code maxIterations} 次）：
 *     <ul>
 *       <li>{@link IChatService#call} → {@link ChatResponse#outputToolCalls()}。</li>
 *       <li>为空 → LLM 给出最终文本答案 → 返回 {@link ChatBiResult}。</li>
 *       <li>非空 → 每个 {@link ChatToolCall} 转 {@link AiToolCall}（保留原引用追踪 toolCallId）→
 *           {@link IToolManager#callTool} 执行 → 结果转 {@link ChatToolResponseMessage} →
 *           {@link ChatRequest#addToolResponse} 回喂 → 继续循环。</li>
 *     </ul>
 *   </li>
 *   <li>迭代次数 ≥ {@code maxIterations} → 抛 {@code ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED}。</li>
 * </ol>
 */
public class ChatBiToolCallingLoop {

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
     * 运行 tool-calling 循环。
     *
     * @param question      用户自然语言问题
     * @param maxIterations tool-calling 轮次上限
     * @return ChatBI 结果（answer + 最后一次 query 结果的 columns/rows + 实际迭代次数）
     */
    public ChatBiResult run(String question, int maxIterations) {
        List<AiToolModel> toolModels = toolManager.listTools();
        List<ChatToolDefinition> tools = ChatBiTypeConverter.toChatToolDefinitions(toolModels);

        ChatRequest request = new ChatRequest();
        request.setSystemPrompt(ChatBiSystemPrompt.buildSystemPrompt());
        request.addMessage(new ChatUserMessage(question));
        request.setTools(tools);

        IToolExecuteContext context = new ChatBiToolExecuteContext(cancelToken);

        List<Map<String, Object>> lastQueryRows = Collections.emptyList();
        List<String> lastQueryColumns = Collections.emptyList();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            ChatResponse response = chatService.call(request, cancelToken);
            if (response == null) {
                throw new NopException(ERR_DATAV_CHATBI_NO_RESULT)
                        .param("question", question);
            }

            List<ChatToolCall> toolCalls = response.outputToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 循环终止条件 (a)：outputToolCalls 为空 → 最终答案
                ChatBiResult result = new ChatBiResult();
                result.setAnswer(response.outputText());
                result.setColumns(lastQueryColumns);
                result.setRows(lastQueryRows);
                result.setIterations(iteration);
                return result;
            }

            // 将 assistant 的 tool-call 消息加入对话（保持上下文）
            appendAssistantMessages(request, response.getMessages());

            // 执行每个 tool call，结果回喂
            for (ChatToolCall chatToolCall : toolCalls) {
                AiToolCall aiToolCall = ChatBiTypeConverter.toAiToolCall(chatToolCall);
                AiToolCallResult toolResult = toolManager.callTool(
                        chatToolCall.getName(), aiToolCall, context).join();

                ChatToolResponseMessage toolResponse =
                        ChatBiTypeConverter.toChatToolResponseMessage(chatToolCall, toolResult);

                String toolResponseContent = toolResponse.getContent();

                // 若为 query 工具且成功，解析 columns/rows 作为结果快照
                if (DatavQueryDatasetExecutor.TOOL_NAME.equals(chatToolCall.getName())
                        && "success".equals(toolResult.getStatus())
                        && toolResult.getError() == null
                        && toolResponseContent != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = JsonTool.parseNonStrict(toolResponseContent) instanceof Map
                                ? (Map<String, Object>) JsonTool.parseNonStrict(toolResponseContent)
                                : null;
                        if (parsed != null) {
                            Object cols = parsed.get("columns");
                            Object rws = parsed.get("rows");
                            if (cols instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<String> colList = (List<String>) cols;
                                lastQueryColumns = colList;
                            }
                            if (rws instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> rowList = (List<Map<String, Object>>) rws;
                                lastQueryRows = rowList;
                            }
                        }
                    } catch (Exception ignore) {
                        // 解析失败不影响循环，保留前一次快照
                    }
                }

                request.addToolResponse(chatToolCall.getId(), chatToolCall.getName(), toolResponseContent);
            }
            // 继续下一轮（回喂后让 LLM 决定是否继续 tool call 或给出最终答案）
        }

        // 循环终止条件 (b)：迭代达 maxIterations 仍无最终答案
        throw new NopException(ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED)
                .param("maxIterations", maxIterations)
                .param("question", question);
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
}
