package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.TokenEstimators;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

public class NoOpContextCompactor implements IContextCompactor {

    public static final NoOpContextCompactor INSTANCE = new NoOpContextCompactor();

    private final ITokenEstimator defaultEstimator;

    public NoOpContextCompactor() {
        this.defaultEstimator = TokenEstimators.defaultEstimator();
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        long estimatedTokens = resolveEstimator(ctx).estimateTokens(messages);
        int messageCount = messages.size();
        // NoOp = no compaction: snapshotId stays null (no archive consulted)
        // and both message-count dimensions equal the original size (design
        // §8.3 Decision G — NoOp keeps null snapshotId for backward compat).
        return new CompactionResult(
                ctx.getSessionId(),
                estimatedTokens,
                estimatedTokens,
                messageCount,
                null,
                null,
                messageCount,
                messageCount
        );
    }

    static ITokenEstimator resolveEstimator(CompactionContext ctx) {
        ITokenEstimator estimator = ctx.getTokenEstimator();
        if (estimator != null) {
            return estimator;
        }
        return TokenEstimators.defaultEstimator();
    }
}
