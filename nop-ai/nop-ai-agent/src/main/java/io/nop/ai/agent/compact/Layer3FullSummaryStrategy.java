package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 3 — LLM summarization (FullSummary).
 * <p>
 * Calls {@link IChatService} with a KV prefix-preserving prompt (design
 * {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md}
 * §3.1): the head anchors (system + first user goal) are prepended verbatim and
 * object-reused, then a single summarization instruction user message (7
 * sections: Goal / Constraints &amp; Preferences / Progress / Key Decisions /
 * Next Steps / Critical Context / Relevant Files, with the previous summary
 * embedded for incremental updates), then the middle window as one user
 * message. Head and middle share the optional compression prompt budget 1:1
 * (message-level trimming, whole turn-groups), which keeps the shared prefix
 * token-cost capped while preserving KV-cache reuse for the head anchors.
 * <p>
 * The result is: [head anchors: system + first user goal] + [new summary
 * message] + [tail window per {@code keepTailPercent}]. The tail preserves
 * tool_call/tool_response boundary integrity (whole turn-groups). The system
 * message and first user goal are never dropped.
 * <p>
 * <b>Graceful fallback (design §7.2 / D3 principle):</b> when
 * {@link IChatService} is absent, the summary call fails, or the middle window
 * cannot fit the compression prompt budget, Layer 3 degrades to a Layer 2
 * effect (turn pruning that preserves more original messages) and logs a
 * fallback record — the agent is never failed.
 * <p>
 * Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md}
 * §7.5 (summary strategy) and §7.8 (extension point).
 */
public class Layer3FullSummaryStrategy implements ICompressionStrategy {

    public static final String NAME = "layer3-full-summary";
    public static final String SUMMARY_MARKER = "[CONTEXT SUMMARY]";

    /**
     * Summarization instruction (rendered as the last user message of the
     * summary prompt, after the head anchor messages). The instruction carries
     * the section template; when a previous summary exists it is embedded in
     * the same message for an incremental update (design
     * {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md}
     * §3.1 — the prompt must stay single-user so OpenAI-compatible providers
     * with a single-user-prompt constraint accept it).
     */
    private static final String SUMMARIZATION_INSTRUCTION =
            "You are a conversation summarizer. Read the conversation and produce a concise structured summary "
                    + "with exactly these sections:\n"
                    + "## Goal\n## Constraints & Preferences\n## Progress\n## Key Decisions\n"
                    + "## Next Steps\n## Critical Context\n## Relevant Files\n"
                    + "Keep it factual and information-dense. Do not add commentary.";

    private static final Logger LOG = LoggerFactory.getLogger(Layer3FullSummaryStrategy.class);

    private final IChatService chatService;
    private final String compressionModel;
    private final Layer2TurnPruningStrategy fallbackPruner;

    public Layer3FullSummaryStrategy() {
        this(null, null, new Layer2TurnPruningStrategy());
    }

    public Layer3FullSummaryStrategy(IChatService chatService) {
        this(chatService, null, new Layer2TurnPruningStrategy());
    }

    public Layer3FullSummaryStrategy(IChatService chatService, String compressionModel) {
        this(chatService, compressionModel, new Layer2TurnPruningStrategy());
    }

    public Layer3FullSummaryStrategy(IChatService chatService, String compressionModel,
                                     Layer2TurnPruningStrategy fallbackPruner) {
        this.chatService = chatService;
        this.compressionModel = compressionModel != null ? compressionModel : "";
        this.fallbackPruner = fallbackPruner != null ? fallbackPruner : new Layer2TurnPruningStrategy();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        if (messages == null || messages.isEmpty()) {
            return new CompactionResult(ctx.getSessionId(), 0, 0, 0, null, null);
        }

        if (chatService == null) {
            LOG.warn("Layer 3 fallback: no IChatService configured, degrading to Layer 2 effect (turn pruning). "
                    + "session={}", ctx.getSessionId());
            return fallbackPruner.compact(ctx);
        }

        try {
            return summarize(ctx, messages);
        } catch (Exception e) {
            LOG.warn("Layer 3 LLM summarization failed, degrading to Layer 2 effect (turn pruning). session={}",
                    ctx.getSessionId(), e);
            return fallbackPruner.compact(ctx);
        }
    }

    private CompactionResult summarize(CompactionContext ctx, List<ChatMessage> messages) {
        ITokenEstimator estimator = NoOpContextCompactor.resolveEstimator(ctx);
        long tokensBefore = estimator.estimateTokens(messages);

        CompactConfig config = ctx.getCompactConfig() != null
                ? ctx.getCompactConfig()
                : CompactConfig.defaults();

        List<List<ChatMessage>> groups = Layer2TurnPruningStrategy.groupIntoTurns(messages);
        int headEndIndex = Layer2TurnPruningStrategy.computeHeadEndGroupIndex(groups);
        long keepTailMessages = Math.max(1, Math.round(Math.ceil(messages.size() * config.getKeepTailPercent())));
        int tailStartIndex = Layer2TurnPruningStrategy.computeTailStartGroupIndex(groups, keepTailMessages);

        if (tailStartIndex <= headEndIndex) {
            LOG.info("Layer 3 skip: head/tail windows overlap, too few messages to summarize. session={}",
                    ctx.getSessionId());
            return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                    messages.size(), null, null);
        }

        String previousSummary = findPreviousSummary(messages);
        List<ChatMessage> head = collectHead(groups, headEndIndex);
        List<ChatMessage> middle = collectMiddle(groups, headEndIndex, tailStartIndex);

        long budget = resolveCompressionPromptBudget(ctx, config);
        if (budget > 0) {
            List<List<ChatMessage>> headGroups = groups.subList(0, headEndIndex);
            List<List<ChatMessage>> middleGroups = groups.subList(headEndIndex, tailStartIndex);
            long headBudget = budget / 2;
            head = trimGroupsToBudget(headGroups, estimator, headBudget, true, ctx);
            long instructionTokens = estimator.estimateTokens(
                    List.of(new ChatUserMessage(buildSummaryInstruction(previousSummary))));
            middle = trimGroupsToBudget(middleGroups, estimator, budget - headBudget - instructionTokens, false, ctx);
            if (middle.isEmpty()) {
                LOG.info("Layer 3 skip: middle window exceeds compression prompt budget, "
                        + "degrading to Layer 2 effect. session={}", ctx.getSessionId());
                return fallbackPruner.compact(ctx);
            }
        }

        ChatRequest request = new ChatRequest();
        for (ChatMessage msg : head) {
            request.addMessage(msg);
        }
        request.addMessage(new ChatUserMessage(buildSummaryInstruction(previousSummary)));
        request.addMessage(new ChatUserMessage(buildMiddleContent(middle)));

        ChatOptions options = new ChatOptions();
        if (!this.compressionModel.isEmpty()) {
            options.setModel(this.compressionModel);
        } else if (config.getCompressionModel() != null && !config.getCompressionModel().isEmpty()) {
            options.setModel(config.getCompressionModel());
        }
        options.disableTools();
        request.setOptions(options);

        ChatResponse response = chatService.call(request, null);

        if (response == null || !response.isSuccess() || response.outputText() == null
                || response.outputText().trim().isEmpty()) {
            String err = response != null ? response.getError() : "null response";
            LOG.warn("Layer 3 LLM returned unsuccessful/empty response ({}), degrading to Layer 2 effect. session={}",
                    err, ctx.getSessionId());
            return fallbackPruner.compact(ctx);
        }

        String summaryContent = response.outputText().trim();
        ChatUserMessage summaryMessage = new ChatUserMessage(SUMMARY_MARKER + "\n" + summaryContent);

        List<ChatMessage> result = new ArrayList<>();
        for (int i = 0; i < headEndIndex; i++) {
            result.addAll(groups.get(i));
        }
        result.add(summaryMessage);
        for (int i = tailStartIndex; i < groups.size(); i++) {
            result.addAll(groups.get(i));
        }

        Layer2TurnPruningStrategy.assertBoundaryIntegrity(result);

        long tokensAfter = estimator.estimateTokens(result);
        boolean incremental = previousSummary != null;
        LOG.info("Layer 3 {} summary produced: tokens {} -> {}, messages {} -> {}. session={}",
                incremental ? "incremental" : "initial", tokensBefore, tokensAfter,
                messages.size(), result.size(), ctx.getSessionId());

        return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensAfter,
                result.size(), null, result);
    }

    static String findPreviousSummary(List<ChatMessage> messages) {
        for (ChatMessage msg : messages) {
            if (msg instanceof ChatUserMessage && msg.getContent() != null
                    && msg.getContent().startsWith(SUMMARY_MARKER)) {
                return msg.getContent().substring(SUMMARY_MARKER.length()).trim();
            }
        }
        return null;
    }

    private List<ChatMessage> collectHead(List<List<ChatMessage>> groups, int headEndIndex) {
        List<ChatMessage> head = new ArrayList<>();
        for (int i = 0; i < headEndIndex; i++) {
            head.addAll(groups.get(i));
        }
        return head;
    }

    private List<ChatMessage> collectMiddle(List<List<ChatMessage>> groups, int headEndIndex, int tailStartIndex) {
        List<ChatMessage> middle = new ArrayList<>();
        for (int i = headEndIndex; i < tailStartIndex; i++) {
            middle.addAll(groups.get(i));
        }
        return middle;
    }

    /**
     * Trims whole turn-groups from the end of the given window until the group
     * count fits the budget (head and middle share the compression prompt
     * budget 1:1, see design §3.1). Head groups are never dropped below one
     * group so the shared prefix stays meaningful; boundary integrity is kept
     * because groups are complete turns.
     */
    private List<ChatMessage> trimGroupsToBudget(List<List<ChatMessage>> groups, ITokenEstimator estimator,
                                                 long budget, boolean keepAtLeastOne, CompactionContext ctx) {
        List<List<ChatMessage>> remaining = new ArrayList<>(groups);
        while (remaining.size() > 1 || (!keepAtLeastOne && remaining.size() > 0)) {
            long tokens = estimator.estimateTokens(flatten(remaining));
            if (tokens <= budget) {
                break;
            }
            remaining.remove(remaining.size() - 1);
        }
        LOG.debug("Layer 3 prompt budget: budget={}, kept {} of {} turn-groups. session={}",
                budget, remaining.size(), groups.size(), ctx.getSessionId());
        return flatten(remaining);
    }

    private static List<ChatMessage> flatten(List<List<ChatMessage>> groups) {
        List<ChatMessage> flat = new ArrayList<>();
        for (List<ChatMessage> group : groups) {
            flat.addAll(group);
        }
        return flat;
    }

    /**
     * Summarization instruction for the final user message of the summary
     * prompt. The previous summary (when present) is embedded here so the LLM
     * performs an incremental update instead of a rewrite.
     */
    static String buildSummaryInstruction(String previousSummary) {
        StringBuilder sb = new StringBuilder(SUMMARIZATION_INSTRUCTION);
        sb.append("\n\n");
        if (previousSummary != null && !previousSummary.isEmpty()) {
            sb.append("<previous-summary>\n").append(previousSummary).append("\n</previous-summary>\n\n");
            sb.append("Update the previous summary incrementally with the new conversation below. "
                    + "Do not rewrite from scratch — preserve still-valid information and merge new facts.\n\n");
        }
        sb.append("Return ONLY the summary sections, no preamble.");
        return sb.toString();
    }

    static String buildMiddleContent(List<ChatMessage> middle) {
        StringBuilder sb = new StringBuilder("Conversation to summarize:\n\n");
        for (ChatMessage msg : middle) {
            String role = msg instanceof ChatAssistantMessage ? "assistant"
                    : msg instanceof ChatToolResponseMessage ? "tool"
                    : msg.getRole();
            String content = msg.getContent();
            if (content == null) {
                content = "";
            }
            sb.append("[").append(role).append("] ").append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * Dynamic budget when {@code compressionPromptBudget} is unconfigured
     * (sentinel -1): half of the compression trigger watermark. Mirrors
     * {@link PipelineCompactor#resolveMaxContextTokens} semantics — an
     * explicit {@code maxTokens} on the execution chat options wins, otherwise
     * the engine default.
     */
    private long resolveCompressionPromptBudget(CompactionContext ctx, CompactConfig config) {
        int budget = config.getCompressionPromptBudget();
        if (budget >= 0) {
            return budget;
        }
        long maxContextTokens = ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS;
        if (ctx.getExecutionContext() != null && ctx.getExecutionContext().getChatOptions() != null
                && ctx.getExecutionContext().getChatOptions().getMaxTokens() != null) {
            maxContextTokens = ctx.getExecutionContext().getChatOptions().getMaxTokens();
        }
        long watermark = (long) (maxContextTokens * config.getTriggerTokenPercent());
        return watermark / 2;
    }

    IChatService getChatService() {
        return chatService;
    }

    String getCompressionModel() {
        return compressionModel;
    }
}
