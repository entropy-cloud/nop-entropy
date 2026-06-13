package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MicroCompressionCompactor implements IContextCompactor, ICompressionStrategy {

    public static final String NAME = "layer1-micro-compression";

    public static final Set<String> COMPRESSIBLE_TOOLS = Set.of(
            "read_file", "bash", "grep", "search", "list_directory",
            "cat", "head", "tail", "glob", "find", "rg", "ls",
            "type", "dir", "get-content", "select-string"
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        if (messages.isEmpty()) {
            return new CompactionResult(ctx.getSessionId(), 0, 0, 0, null, null);
        }

        CompactConfig config = ctx.getCompactConfig();
        int maxRecentToolResults = config != null ? config.getMaxRecentToolResults() : CompactConfig.DEFAULT_MAX_RECENT_TOOL_RESULTS;

        Set<String> recentToolCallIds = collectRecentToolCallIds(messages, maxRecentToolResults);

        ITokenEstimator estimator = NoOpContextCompactor.resolveEstimator(ctx);
        long tokensBefore = estimator.estimateTokens(messages);

        List<ChatMessage> compactedMessages = new ArrayList<>(messages.size());
        boolean firstUserMessage = true;
        boolean anyCompressed = false;

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);

            if (msg instanceof ChatSystemMessage) {
                compactedMessages.add(msg);
                continue;
            }

            if (msg instanceof ChatUserMessage && firstUserMessage) {
                compactedMessages.add(msg);
                firstUserMessage = false;
                continue;
            }

            if (msg instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage toolResp = (ChatToolResponseMessage) msg;
                String toolCallId = toolResp.getToolCallId();

                if (recentToolCallIds.contains(toolCallId)) {
                    compactedMessages.add(msg);
                    continue;
                }

                String toolName = toolResp.getName();
                if (toolName != null && COMPRESSIBLE_TOOLS.contains(toolName)) {
                    ChatToolResponseMessage compacted = compressToolResponse(toolResp);
                    compactedMessages.add(compacted);
                    anyCompressed = true;
                    continue;
                }
            }

            compactedMessages.add(msg);
        }

        if (!anyCompressed) {
            return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensBefore,
                    messages.size(), null, null);
        }

        long tokensAfter = estimator.estimateTokens(compactedMessages);
        return new CompactionResult(ctx.getSessionId(), tokensBefore, tokensAfter,
                compactedMessages.size(), null, compactedMessages);
    }

    private Set<String> collectRecentToolCallIds(List<ChatMessage> messages, int maxRecent) {
        List<String> recentIds = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && recentIds.size() < maxRecent; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage toolResp = (ChatToolResponseMessage) msg;
                String toolCallId = toolResp.getToolCallId();
                if (toolCallId != null) {
                    recentIds.add(toolCallId);
                }
            }
        }
        return new HashSet<>(recentIds);
    }

    private ChatToolResponseMessage compressToolResponse(ChatToolResponseMessage original) {
        String originalContent = original.getContent();
        int originalLength = originalContent != null ? originalContent.length() : 0;
        String placeholder = "[COMPRESSED " + original.getName()
                + " result, toolCallId=" + original.getToolCallId()
                + ", originalLength=" + originalLength + " chars]";

        ChatToolResponseMessage compressed = original.copy();
        compressed.setContent(placeholder);
        return compressed;
    }
}
