package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 3 — LLM summarization (FullSummary).
 * <p>
 * Calls {@link IChatService} with a structured summary prompt (7 sections: Goal /
 * Constraints &amp; Preferences / Progress / Key Decisions / Next Steps / Critical
 * Context / Relevant Files). When a previous summary exists in the context it is
 * passed in for an incremental update rather than a full rewrite.
 * <p>
 * The result is: [head anchors: system + first user goal] + [new summary
 * message] + [tail window per {@code keepTailPercent}]. The tail preserves
 * tool_call/tool_response boundary integrity (whole turn-groups). The system
 * message and first user goal are never dropped.
 * <p>
 * <b>Graceful fallback (design §7.2 / D3 principle):</b> when
 * {@link IChatService} is absent or the summary call fails, Layer 3 degrades to
 * a Layer 2 effect (turn pruning that preserves more original messages) and logs
 * a fallback record — the agent is never failed.
 * <p>
 * Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md}
 * §7.5 (summary strategy) and §7.8 (extension point).
 */
public class Layer3FullSummaryStrategy implements ICompressionStrategy {

    public static final String NAME = "layer3-full-summary";
    public static final String SUMMARY_MARKER = "[CONTEXT SUMMARY]";

    private static final String SUMMARIZATION_SYSTEM_PROMPT =
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
        List<ChatMessage> middle = collectMiddle(groups, headEndIndex, tailStartIndex);

        String prompt = buildPrompt(middle, previousSummary);

        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatSystemMessage(SUMMARIZATION_SYSTEM_PROMPT));
        request.addMessage(new ChatUserMessage(prompt));

        ChatOptions options = new ChatOptions();
        if (!this.compressionModel.isEmpty()) {
            options.setModel(this.compressionModel);
        } else if (config.getCompressionModel() != null && !config.getCompressionModel().isEmpty()) {
            options.setModel(config.getCompressionModel());
        }
        options.disableTools();
        request.setOptions(options);

        ChatResponse response = chatService.call(request, null);

        if (response == null || !response.isSuccess() || response.getMessage() == null
                || response.getMessage().getContent() == null
                || response.getMessage().getContent().trim().isEmpty()) {
            String err = response != null ? response.getError() : "null response";
            LOG.warn("Layer 3 LLM returned unsuccessful/empty response ({}), degrading to Layer 2 effect. session={}",
                    err, ctx.getSessionId());
            return fallbackPruner.compact(ctx);
        }

        String summaryContent = response.getMessage().getContent().trim();
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

    private List<ChatMessage> collectMiddle(List<List<ChatMessage>> groups, int headEndIndex, int tailStartIndex) {
        List<ChatMessage> middle = new ArrayList<>();
        for (int i = headEndIndex; i < tailStartIndex; i++) {
            middle.addAll(groups.get(i));
        }
        return middle;
    }

    static String buildPrompt(List<ChatMessage> middle, String previousSummary) {
        StringBuilder sb = new StringBuilder();
        if (previousSummary != null && !previousSummary.isEmpty()) {
            sb.append("<previous-summary>\n").append(previousSummary).append("\n</previous-summary>\n\n");
            sb.append("Update the previous summary incrementally with the new conversation below. "
                    + "Do not rewrite from scratch — preserve still-valid information and merge new facts.\n\n");
        }
        sb.append("Conversation to summarize:\n\n");
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

    IChatService getChatService() {
        return chatService;
    }

    String getCompressionModel() {
        return compressionModel;
    }
}
