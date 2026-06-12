package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIAgentExecutor {

    @Test
    public void testMockExecutorReturnsResultFromContext() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.setStatus(AgentExecStatus.completed);
        ctx.setCurrentIteration(5);
        ctx.setTokensUsed(1000L);

        IAgentExecutor executor = c -> {
            AgentExecutionResult result = AgentExecutionResult.fromContext(c);
            return CompletableFuture.completedFuture(result);
        };

        CompletionStage<AgentExecutionResult> stage = executor.execute(ctx);
        AgentExecutionResult result = stage.toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(5, result.getTotalIterations());
        assertEquals(1000L, result.getTotalTokensUsed());
        assertNotNull(result.getMessages());
    }

    @Test
    public void testMockExecutorCompletesAsync() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-2");
        ctx.setStatus(AgentExecStatus.completed);

        IAgentExecutor executor = c -> CompletableFuture.completedFuture(
                AgentExecutionResult.fromContext(c));

        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();

        assertTrue(future.isDone());
        AgentExecutionResult result = future.join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
    }

    @Test
    public void testLambdaImplementation() {
        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-3");
        ctx.setStatus(AgentExecStatus.failed);
        ctx.setLastError("test error");

        AgentExecutionResult expectedResult = AgentExecutionResult.fromContext(ctx);

        IAgentExecutor executor = c -> CompletableFuture.completedFuture(expectedResult);

        AgentExecutionResult actualResult = executor.execute(ctx)
                .toCompletableFuture()
                .join();

        assertSame(expectedResult, actualResult);
        assertEquals(AgentExecStatus.failed, actualResult.getStatus());
        assertEquals("test error", actualResult.getError());
    }
}
