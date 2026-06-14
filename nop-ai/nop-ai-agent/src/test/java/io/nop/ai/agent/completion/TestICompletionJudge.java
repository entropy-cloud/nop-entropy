package io.nop.ai.agent.completion;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestICompletionJudge {

    @Test
    void interfaceContractCanBeImplemented() {
        ICompletionJudge judge = new ICompletionJudge() {
            @Override
            public CompletionDecision decide(ChatAssistantMessage assistantMessage, AgentExecutionContext ctx) {
                return CompletionDecision.Complete.instance();
            }
        };

        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");

        CompletionDecision result = judge.decide(msg, ctx);

        assertTrue(result instanceof CompletionDecision);
        assertTrue(result.isComplete());
    }

    @Test
    void interfaceIsAssignableFromNoOp() {
        assertTrue(ICompletionJudge.class.isAssignableFrom(NoOpCompletionJudge.class));
    }

    @Test
    void interfaceCanReturnAllOutcomes() {
        ICompletionJudge continueJudge = (msg, ctx) -> new CompletionDecision.Continue("keep going");
        ICompletionJudge escalateJudge = (msg, ctx) -> new CompletionDecision.Escalate("stuck");
        ICompletionJudge completeJudge = (msg, ctx) -> CompletionDecision.Complete.instance();

        ChatAssistantMessage msg = new ChatAssistantMessage();
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "s1");

        assertTrue(continueJudge.decide(msg, ctx).isContinue());
        assertTrue(escalateJudge.decide(msg, ctx).isEscalate());
        assertTrue(completeJudge.decide(msg, ctx).isComplete());
    }

    @Test
    void interfaceReceivesAssistantMessageAndContext() {
        ChatAssistantMessage passed = new ChatAssistantMessage();
        passed.setContent("hello");

        AgentExecutionContext passedCtx = AgentExecutionContext.create(new AgentModel(), "ctx-session");

        java.util.concurrent.atomic.AtomicReference<ChatAssistantMessage> capturedMsg = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<AgentExecutionContext> capturedCtx = new java.util.concurrent.atomic.AtomicReference<>();

        ICompletionJudge judge = (msg, ctx) -> {
            capturedMsg.set(msg);
            capturedCtx.set(ctx);
            return CompletionDecision.Complete.instance();
        };

        judge.decide(passed, passedCtx);

        assertSame(passed, capturedMsg.get());
        assertSame(passedCtx, capturedCtx.get());
    }
}
