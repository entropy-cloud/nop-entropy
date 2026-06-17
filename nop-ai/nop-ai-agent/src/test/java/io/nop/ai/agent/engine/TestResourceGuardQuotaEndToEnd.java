package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.quota.CountingResourceGuard;
import io.nop.ai.agent.quota.DefaultResourceGuard;
import io.nop.ai.agent.quota.QuotaConfig;
import io.nop.ai.agent.quota.QuotaDimension;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 234 Phase 3 end-to-end test (Anti-Hollow #22 + Wiring #23): proves the
 * {@link io.nop.ai.agent.quota.IResourceGuard} quota gateway is wired into the
 * full engine execution path, not just the component-level managers.
 *
 * <p>Full path verified:
 * <pre>
 *   engine.execute(lead)
 *     → loadAgentModel → precheck → supplyAsync
 *     → autoBindTeam → autoBindLead
 *     → teamManager.createTeam        (TEAM_MEMBERS quota check)
 *     → teamManager.bindMemberSession (TEAM_PARALLEL_BOUND_MEMBERS quota check, lead=1)
 *   engine.execute(member-a)
 *     → autoBindTeam → autoBindMember
 *     → teamManager.bindMemberSession (quota check, member-a=2, within limit 2)
 *   engine.execute(member-b)
 *     → autoBindTeam → autoBindMember
 *     → teamManager.bindMemberSession (quota check, member-b=3, EXCEEDS limit 2)
 *     → NopAiAgentException → CompletableFuture fails
 * </pre>
 *
 * <p>The lead declares {@code <team maxParallelMembers="2">} with 3 members.
 * With a functional {@link DefaultResourceGuard} wired into the
 * {@link InMemoryTeamManager}, the 3rd concurrent binding (member-b) is denied
 * and the engine.execute future fails. The bound count remains at 2 (lead +
 * member-a), proving the denial actually prevented the over-limit binding
 * (Anti-Hollow — state unchanged on denial).
 */
public class TestResourceGuardQuotaEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void maxParallelMembersQuotaDenialPropagatesThroughEngineExecute() throws Exception {
        // Wire a CountingResourceGuard (delegating to DefaultResourceGuard with
        // vision-default teamMaxMembers=8 so createTeam passes, but
        // maxParallelMembers=2 is the per-team override declared in the XML).
        CountingResourceGuard guard = new CountingResourceGuard(
                new DefaultResourceGuard(new QuotaConfig(8, 10)));
        InMemoryTeamManager mgr = new InMemoryTeamManager(guard);
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        DefaultAgentEngine engine = newEngine(mgr, taskStore);

        String leadSession = "quota-e2e-lead-sess";
        String memberASession = "quota-e2e-member-a-sess";
        String memberBSession = "quota-e2e-member-b-sess";

        // 1. Lead executes → createTeam (3 members, TEAM_MEMBERS check OK) +
        //    bind lead (parallel-bound = 1, within limit 2).
        AgentExecutionResult leadResult = engine.execute(
                new AgentMessageRequest("test-quota-lead", "lead starts", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, leadResult.getStatus(),
                "lead should complete. Messages: " + leadResult.getMessages());

        Team team = mgr.getTeamBySession(leadSession).orElse(null);
        assertNotNull(team, "team must exist after lead execution");
        String teamId = team.getTeamId();
        assertEquals(2, team.getSpec().getMaxParallelMembers(),
                "team maxParallelMembers must be 2 (from XML declaration)");

        // 2. Member A executes → bind member-a (parallel-bound = 2, within limit 2).
        AgentExecutionResult memberAResult = engine.execute(
                new AgentMessageRequest("test-quota-member-a", "member A joins", memberASession, null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, memberAResult.getStatus(),
                "member A (2nd bound) should complete within limit 2. Messages: "
                        + memberAResult.getMessages());

        // 3. Member B executes → bind member-b DENIED (parallel-bound would be 3
        //    > limit 2). engine.execute future must fail with the quota denial.
        CompletableFuture<AgentExecutionResult> memberBFuture = engine.execute(
                new AgentMessageRequest("test-quota-member-b", "member B joins", memberBSession, null));

        ExecutionException ex = assertFutureFails(memberBFuture);
        Throwable cause = ex.getCause();
        assertNotNull(cause, "engine.execute future must fail with a cause");
        // The denial surfaces as NopAiAgentException wrapping the quota reason.
        assertTrue(containsNopAiAgentException(cause),
                "future failure must be (or wrap) NopAiAgentException. cause=" + cause);
        assertTrue(causeChainContains(cause, "TEAM_PARALLEL_BOUND_MEMBERS"),
                "failure cause must mention TEAM_PARALLEL_BOUND_MEMBERS. cause=" + cause);

        // 4. Anti-Hollow: the bound count is still 2 (lead + member-a); member-b
        //    was NOT bound (denial prevented the over-limit binding).
        long boundCount = mgr.getTeam(teamId).get().getMembers().values().stream()
                .filter(TeamMember::isBound).count();
        assertEquals(2, boundCount,
                "bound count must remain 2 after the denied 3rd binding");
        TeamMember memberB = mgr.getMember(teamId, "quota-member-b").orElse(null);
        assertNotNull(memberB);
        assertFalse(memberB.isBound(),
                "member-b must remain unbound (denial prevented binding)");

        // 5. Wiring verification (Minimum Rules #23): the guard was actually
        //    invoked on the engine path (createTeam + multiple binds), and at
        //    least one denial was returned.
        assertTrue(guard.wasCalled(),
                "IResourceGuard.checkConcurrent must be invoked on the engine path");
        assertTrue(guard.getDimensions().contains(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS),
                "bindMemberSession must invoke the TEAM_PARALLEL_BOUND_MEMBERS dimension");
        assertTrue(guard.getDimensions().contains(QuotaDimension.TEAM_MEMBERS),
                "createTeam must invoke the TEAM_MEMBERS dimension");
        assertTrue(guard.getDecisions().stream().anyMatch(d -> !d.isAllowed()),
                "at least one denial must have been returned (member-b binding)");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static <T> ExecutionException assertFutureFails(CompletableFuture<T> future)
            throws InterruptedException, TimeoutException {
        try {
            future.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException ee) {
            return ee;
        }
        throw new AssertionError("future was expected to fail but completed successfully");
    }

    private static boolean containsNopAiAgentException(Throwable t) {
        return causeChainContains(t, "NopAiAgentException");
    }

    private static boolean causeChainContains(Throwable t, String needle) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(needle)) {
                return true;
            }
            if (cur.getClass().getSimpleName().contains(needle)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private DefaultAgentEngine newEngine(InMemoryTeamManager mgr, InMemoryTeamTaskStore taskStore) {
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(finalResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return finalResponse();
            }

            private ChatResponse finalResponse() {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager tools = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools);
        engine.setTeamManager(mgr);
        engine.setTeamTaskStore(taskStore);
        return engine;
    }
}
