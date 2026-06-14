package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;

public final class NoOpCompletionJudge implements ICompletionJudge {

    private static final NoOpCompletionJudge INSTANCE = new NoOpCompletionJudge();

    private NoOpCompletionJudge() {
    }

    public static ICompletionJudge noOp() {
        return INSTANCE;
    }

    @Override
    public CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx) {
        return CompletionDecision.Complete.instance();
    }
}
