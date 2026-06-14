package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;

public interface ICompletionJudge {

    CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx);
}
