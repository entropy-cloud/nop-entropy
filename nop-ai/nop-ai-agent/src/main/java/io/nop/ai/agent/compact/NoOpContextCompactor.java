package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.CalibratedTokenEstimator;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

public class NoOpContextCompactor implements IContextCompactor {

    public static final NoOpContextCompactor INSTANCE = new NoOpContextCompactor();

    private final ITokenEstimator defaultEstimator;

    public NoOpContextCompactor() {
        this.defaultEstimator = CalibratedTokenEstimator.defaultInstance();
    }

    @Override
    public CompactionResult compact(CompactionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        long estimatedTokens = resolveEstimator(ctx).estimateTokens(messages);
        return new CompactionResult(
                ctx.getSessionId(),
                estimatedTokens,
                estimatedTokens,
                messages.size(),
                null,
                null
        );
    }

    static ITokenEstimator resolveEstimator(CompactionContext ctx) {
        ITokenEstimator estimator = ctx.getTokenEstimator();
        if (estimator != null) {
            return estimator;
        }
        return CalibratedTokenEstimator.defaultInstance();
    }
}
