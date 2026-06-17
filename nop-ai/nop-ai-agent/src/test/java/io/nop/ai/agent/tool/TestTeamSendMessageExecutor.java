package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.IAgentMessageHandler;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.message.core.local.LocalMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link TeamSendMessageExecutor}: NoOp honest
 * reporting, functional delivery to correct inbox topic, unbound-member
 * error, and caller-not-in-team error.
 *
 * <p>See plan 225 (L4-8-team-tools) Phase 1.
 */
public class TestTeamSendMessageExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentToolExecuteContext createContext(ITeamManager teamManager,
                                                   io.nop.ai.agent.message.IAgentMessenger messenger,
                                                   String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, messenger, sessionId, "test-agent",
                null, null, null, null,
                teamManager, null);
    }

    private AiToolCall createCall(String to, String body) {
        AiToolCall call = new AiToolCall();
        call.setToolName("team-send-message");
        call.setId(1);
        call.setInput("{\"to\":\"" + to + "\",\"body\":\"" + body + "\"}");
        return call;
    }

    private ITeamManager createTeamWithMembers(String leadSessionId, String memberSessionId) {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("TestTeam", null, "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", leadSessionId, "actor-lead");
        if (memberSessionId != null) {
            mgr.bindMemberSession(team.getTeamId(), "worker", memberSessionId, "actor-worker");
        }
        return mgr;
    }

    @Test
    void noOpTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpTeamManager.noOp(),
                NoOpAgentMessenger.noOp(), "sess-1");
        AiToolCall call = createCall("worker", "hello");

        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "team-send-message with NoOp teamManager should return success (honest report, not crash)");
        assertNotNull(result.getOutput());
        String body = result.getOutput().getBody();
        assertTrue(body.contains("not enabled") || body.contains("not sent"),
                "NoOp teamManager result must honestly report not-enabled: " + body);
    }

    @Test
    void nullTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(null, NoOpAgentMessenger.noOp(), "sess-1");
        AiToolCall call = createCall("worker", "hello");

        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("not enabled"));
    }

    @Test
    void functionalDeliveryToCorrectInboxTopic() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);

        ITeamManager teamManager = createTeamWithMembers("lead-sess", "worker-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, messenger, "lead-sess");

        AtomicReference<AgentMessageEnvelope> received = new AtomicReference<>();
        IMessageSubscription sub = messenger.registerHandler(
                io.nop.ai.agent.message.AgentMessageTopics.inboxTopic("worker-sess"),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        received.set(envelope);
                        return null;
                    }
                });

        AiToolCall call = createCall("worker", "hello from lead");
        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("agent.worker-sess.inbox"));

        AgentMessageEnvelope envelope = received.get();
        assertNotNull(envelope, "Handler should have received the envelope");
        assertEquals("hello from lead", envelope.getPayload());
        assertEquals(io.nop.ai.agent.message.AgentMessageKind.ASYNC, envelope.getKind());
        assertEquals("lead-sess", envelope.getSenderId());
        assertEquals("agent.worker-sess.inbox", envelope.getTargetTopic());
        assertNotNull(envelope.getCorrelationId());

        sub.cancel();
    }

    @Test
    void unboundMemberReturnsError() throws Exception {
        // Create team but do NOT bind the worker session.
        ITeamManager teamManager = createTeamWithMembers("lead-sess", null);
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpAgentMessenger.noOp(), "lead-sess");

        AiToolCall call = createCall("worker", "hello");
        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no bound session"),
                "Error should mention unbound session: " + result.getError().getBody());
    }

    @Test
    void callerNotInTeamReturnsError() throws Exception {
        ITeamManager teamManager = createTeamWithMembers("lead-sess", "worker-sess");
        // Use a session that is NOT bound to any team.
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpAgentMessenger.noOp(), "stranger-sess");

        AiToolCall call = createCall("worker", "hello");
        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"),
                "Error should mention caller not in team: " + result.getError().getBody());
    }

    @Test
    void unknownMemberReturnsError() throws Exception {
        ITeamManager teamManager = createTeamWithMembers("lead-sess", "worker-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpAgentMessenger.noOp(), "lead-sess");

        AiToolCall call = createCall("ghost", "hello");
        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not found"),
                "Error should mention unknown member: " + result.getError().getBody());
    }

    @Test
    void missingToReturnsError() throws Exception {
        ITeamManager teamManager = createTeamWithMembers("lead-sess", "worker-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpAgentMessenger.noOp(), "lead-sess");

        AiToolCall call = new AiToolCall();
        call.setToolName("team-send-message");
        call.setId(1);
        call.setInput("{\"body\":\"hello\"}");

        TeamSendMessageExecutor executor = new TeamSendMessageExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("'to'"));
    }
}
