package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpCompletionJudge {

    @Test
    void decideAlwaysReturnsCompleteForEmptyMessage() {
        ICompletionJudge judge = NoOpCompletionJudge.noOp();

        ChatAssistantMessage msg = new ChatAssistantMessage();
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "s1");

        CompletionDecision result = judge.decide(msg, ctx);

        assertTrue(result.isComplete());
    }

    @Test
    void decideAlwaysReturnsCompleteForNonEmptyMessage() {
        ICompletionJudge judge = NoOpCompletionJudge.noOp();

        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("The task is done, here is the answer.");
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "s1");

        CompletionDecision result = judge.decide(msg, ctx);

        assertTrue(result.isComplete());
    }

    @Test
    void decideReturnsCompleteRegardlessOfContextState() {
        ICompletionJudge judge = NoOpCompletionJudge.noOp();

        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("partial answer");
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "s2");
        ctx.setStatus(AgentExecStatus.running);
        ctx.setMaxIterations(100);
        ctx.setTokensUsed(99999);

        CompletionDecision result = judge.decide(msg, ctx);

        assertTrue(result.isComplete());
    }

    @Test
    void decideReturnsCompleteForNullContext() {
        ICompletionJudge judge = NoOpCompletionJudge.noOp();

        ChatAssistantMessage msg = new ChatAssistantMessage();

        CompletionDecision result = judge.decide(msg, null);

        assertTrue(result.isComplete());
    }

    @Test
    void noOpFactoryReturnsSingleton() {
        ICompletionJudge a = NoOpCompletionJudge.noOp();
        ICompletionJudge b = NoOpCompletionJudge.noOp();
        assertSame(a, b);
    }

    @Test
    void decideReturnsCompleteSingletonInstance() {
        ICompletionJudge judge = NoOpCompletionJudge.noOp();

        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("anything");
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "s3");

        CompletionDecision result = judge.decide(msg, ctx);

        assertSame(CompletionDecision.Complete.instance(), result);
    }
}
