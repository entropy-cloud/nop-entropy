package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.message.IAgentMessageHandler;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.team.DefaultTeamAclChecker;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.json.JSON;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.message.core.local.LocalMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused ACL tests for {@link TeamSendMessageExecutor} (plan 228 Phase 2).
 *
 * <p>Verifies the executor consults {@link io.nop.ai.agent.team.ITeamAclChecker}
 * after team resolution and before message delivery:
 * <ul>
 *   <li>MEMBER send is allowed (WRITE) — message is actually delivered.</li>
 *   <li>A session not bound to the team is denied — messenger is NOT
 *       called (Wiring Verification #23: denial must block the operation).</li>
 *   <li>NoOp checker preserves zero-regression behaviour.</li>
 * </ul>
 */
public class TestTeamSendMessageExecutorAcl {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private InMemoryTeamManager newTeam() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("SendTeam", null, "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "worker", "worker-sess", "actor-worker");
        return mgr;
    }

    private AgentToolExecuteContext ctxWithChecker(InMemoryTeamManager mgr,
                                                    IAgentMessenger messenger,
                                                    io.nop.ai.agent.team.ITeamAclChecker checker,
                                                    String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, messenger, sessionId, "test-agent",
                null, null, null, null,
                mgr, null, checker);
    }

    /**
     * A recording messenger that flips a flag when {@link #send} is called,
     * and delegates the other two methods to {@link NoOpAgentMessenger}.
     */
    private static final class RecordingMessenger implements IAgentMessenger {
        boolean sent = false;
        AgentMessageEnvelope lastEnvelope;

        @Override
        public void send(AgentMessageEnvelope envelope) {
            sent = true;
            lastEnvelope = envelope;
        }

        @Override
        public CompletableFuture<Object> request(AgentMessageEnvelope requestEnvelope, Duration timeout) {
            return NoOpAgentMessenger.noOp().request(requestEnvelope, timeout);
        }

        @Override
        public IMessageSubscription registerHandler(String topic, IAgentMessageHandler handler) {
            return NoOpAgentMessenger.noOp().registerHandler(topic, handler);
        }
    }

    private AiToolCall call(String to, String body) {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-send-message");
        c.setId(1);
        c.setInput("{\"to\":\"" + to + "\",\"body\":\"" + body + "\"}");
        return c;
    }

    @Test
    void memberSendIsAllowedAndDelivered() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager mgr = newTeam();

        AtomicReference<AgentMessageEnvelope> received = new AtomicReference<>();
        var sub = messenger.registerHandler(
                io.nop.ai.agent.message.AgentMessageTopics.inboxTopic("lead-sess"),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        received.set(envelope);
                        return null;
                    }
                });

        // MEMBER (worker) sends to LEAD — WRITE is allowed for MEMBER.
        AgentToolExecuteContext ctx = ctxWithChecker(mgr, messenger,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamSendMessageExecutor()
                .executeAsync(call("lead", "hi from worker"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("agent.lead-sess.inbox"),
                "send should have delivered to lead inbox: " + result.getOutput().getBody());

        AgentMessageEnvelope env = received.get();
        assertNotNull(env, "MEMBER send must actually deliver the message (Wiring #23)");
        assertEquals("hi from worker", env.getPayload());
        sub.cancel();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonMemberSendIsDeniedAndMessengerNotCalled() throws Exception {
        // Use a recording messenger that fails the test if send() is called.
        RecordingMessenger recording = new RecordingMessenger();
        InMemoryTeamManager mgr = newTeam();

        // Stranger session — not bound to the team. DefaultTeamAclChecker
        // will deny (caller is not a member).
        AgentToolExecuteContext ctx = ctxWithChecker(mgr, recording,
                new DefaultTeamAclChecker(mgr), "stranger-sess");

        AiToolCallResult result = new TeamSendMessageExecutor()
                .executeAsync(call("worker", "intruder"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        // Note: actually the stranger is rejected at team resolution
        // (getTeamBySession returns empty) BEFORE ACL — that's the existing
        // behaviour. To exercise the ACL path specifically, we need a
        // member whose session IS in the team but is still denied. send
        // is WRITE which MEMBER has — so for this test we use NoOp-style
        // verification at the ACL layer via a deny-stub checker instead.
        // (See denyStubCheckerBlocksMemberSend for the explicit ACL-only path.)

        // The existing behaviour: stranger → fail at team resolution.
        assertEquals("failure", result.getStatus());
        assertFalse(recording.sent, "messenger must not be called for a stranger session");
    }

    @Test
    @SuppressWarnings("unchecked")
    void denyStubCheckerBlocksMemberSend() throws Exception {
        // Wiring Verification (#23): use a stub checker that always denies,
        // so we can prove the executor consults the checker and DOES NOT
        // call the messenger when denied — even for an in-team MEMBER.
        RecordingMessenger recording = new RecordingMessenger();
        InMemoryTeamManager mgr = newTeam();

        io.nop.ai.agent.team.ITeamAclChecker denyAll = (teamId, sess, tool, action) ->
                io.nop.ai.agent.team.TeamAclDecision.deny(MemberRole.MEMBER,
                        "stub-deny: " + tool + "/" + action);

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, recording, denyAll, "worker-sess");

        AiToolCallResult result = new TeamSendMessageExecutor()
                .executeAsync(call("lead", "should be blocked"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "ACL denial returns success (honest report, not crash)");
        assertNotNull(result.getOutput());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"),
                "body must explicitly say allowed=false");
        assertEquals("team-send-message", body.get("toolName"));
        assertEquals("send", body.get("action"));
        assertEquals("MEMBER", body.get("resolvedRole"));
        assertNotNull(body.get("reason"));
        assertFalse(recording.sent,
                "messenger.send MUST NOT be called when ACL denies (Wiring #23)");
    }

    @Test
    void noOpCheckerAllowsEverythingZeroRegression() throws Exception {
        // NoOp checker must preserve the pre-ACL behaviour.
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager mgr = newTeam();

        AtomicReference<AgentMessageEnvelope> received = new AtomicReference<>();
        var sub = messenger.registerHandler(
                io.nop.ai.agent.message.AgentMessageTopics.inboxTopic("lead-sess"),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        received.set(envelope);
                        return null;
                    }
                });

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, messenger,
                NoOpTeamAclChecker.noOp(), "worker-sess");

        AiToolCallResult result = new TeamSendMessageExecutor()
                .executeAsync(call("lead", "noOp allow"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(received.get(),
                "NoOp checker must allow MEMBER send → message delivered (zero regression)");
        assertEquals("noOp allow", received.get().getPayload());
        sub.cancel();
    }
}
