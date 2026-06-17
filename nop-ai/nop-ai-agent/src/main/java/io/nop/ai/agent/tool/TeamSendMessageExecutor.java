package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamAclDecision;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.api.core.json.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team-aware send-message tool executor (fire-and-forget). Sends an async
 * message to a <b>team member's</b> inbox topic by resolving the member's
 * bound session via the {@link ITeamManager}, then delivering through
 * {@link IAgentMessenger#send}.
 *
 * <p><b>Routing</b>: the tool resolves the caller's team via
 * {@code teamManager.getTeamBySession(callerSessionId)}, looks up the target
 * member by name, and delivers to {@code agent.{memberSessionId}.inbox}. If
 * the caller is not in a team or the target member has no bound session, the
 * tool returns an honest error result.
 *
 * <p><b>Honest no-op reporting</b> (Minimum Rules #24): if the teamManager is
 * null or a {@link NoOpTeamManager} (team functionality not enabled), the
 * tool short-circuits with an honest report that the operation was not
 * executed — it does NOT pretend success. If the messenger is a
 * {@link NoOpAgentMessenger}, the routing resolves but delivery is reported
 * as not performed.
 *
 * <p>See plan 225 (L4-8-team-tools) and vision §8.2.
 */
public class TeamSendMessageExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "team-send-message";

    private static final Logger LOG = LoggerFactory.getLogger(TeamSendMessageExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("team-send-message failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "team-send-message requires AgentToolExecuteContext (team manager not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        ITeamManager teamManager = agentCtx.getTeamManager();

        // DD#5: NoOp honest reporting — short-circuit BEFORE any routing logic.
        if (teamManager == null || teamManager instanceof NoOpTeamManager) {
            LOG.debug("team-send-message: team functionality not enabled (NoOp/null teamManager)");
            return honestNotEnabled(call.getId(),
                    "Team functionality is not enabled — message not sent. "
                            + "Wire InMemoryTeamManager via DefaultAgentEngine.setTeamManager to enable.");
        }

        String callerSessionId = agentCtx.getSessionId();
        if (callerSessionId == null || callerSessionId.isEmpty()) {
            return fail(call.getId(),
                    "team-send-message failed: caller sessionId is not available in the context");
        }

        // DD#2: resolve caller's team via session reverse-lookup.
        Optional<Team> teamOpt = teamManager.getTeamBySession(callerSessionId);
        if (teamOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-send-message failed: caller session '" + callerSessionId
                            + "' is not bound to any team");
        }
        Team team = teamOpt.get();

        // Plan 228 (L4-team-acl-enforcement): consult the ACL checker after
        // team resolution and BEFORE any argument parsing / member lookup /
        // messenger delivery. toolName=team-send-message, action=send →
        // required WRITE (allowed for both LEAD and MEMBER; denied for
        // non-members). NoOp checker returns allow(null) → zero regression.
        TeamAclDecision acl = agentCtx.getTeamAclChecker().checkAccess(
                team.getTeamId(), callerSessionId, TOOL_NAME, "send");
        if (!acl.isAllowed()) {
            LOG.debug("team-send-message: ACL denied for session={}, teamId={}, reason={}",
                    callerSessionId, team.getTeamId(), acl.getReason());
            return honestDenied(call.getId(), TOOL_NAME, "send", acl);
        }

        Map<String, Object> args = resolveArguments(call);

        String to = getStringArg(args, call, "to");
        if (to == null || to.isEmpty()) {
            return fail(call.getId(), "team-send-message failed: 'to' (target member name) is required");
        }

        String body = getStringArg(args, call, "body");
        if (body == null || body.isEmpty()) {
            body = getStringArg(args, call, "input");
        }
        if (body == null || body.isEmpty()) {
            return fail(call.getId(), "team-send-message failed: 'body' (message body) is required");
        }

        // DD#2: look up the target member and its bound session.
        Optional<TeamMember> memberOpt = teamManager.getMember(team.getTeamId(), to);
        if (memberOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-send-message failed: member '" + to + "' not found in team '"
                            + team.getSpec().getTeamName() + "' (teamId=" + team.getTeamId() + ")");
        }
        TeamMember member = memberOpt.get();
        String memberSessionId = member.getSessionId();
        if (memberSessionId == null || memberSessionId.isEmpty()) {
            return fail(call.getId(),
                    "team-send-message failed: member '" + to + "' has no bound session "
                            + "(call bindMemberSession first)");
        }

        IAgentMessenger messenger = agentCtx.getMessenger();
        if (messenger == null) {
            return fail(call.getId(),
                    "team-send-message failed: no messenger reference available in the tool execution context");
        }

        String targetTopic = AgentMessageTopics.inboxTopic(memberSessionId);
        String correlationId = UUID.randomUUID().toString();

        // Honest reporting for NoOp messenger (routing resolved, delivery not performed).
        if (messenger instanceof NoOpAgentMessenger) {
            LOG.debug("team-send-message: no messenger configured (NoOp), message not delivered: targetTopic={}",
                    targetTopic);
            return honestNotEnabled(call.getId(),
                    "No messenger configured — message not delivered. "
                            + "Target topic: " + targetTopic + ", to: " + to + ", correlationId: " + correlationId);
        }

        AgentMessageEnvelope envelope = new AgentMessageEnvelope(
                callerSessionId,
                targetTopic,
                correlationId,
                AgentMessageKind.ASYNC,
                body);

        try {
            messenger.send(envelope);
        } catch (Exception e) {
            LOG.warn("team-send-message failed: messenger.send() threw", e);
            return fail(call.getId(),
                    "team-send-message failed: messenger.send() threw an error: " + e);
        }

        LOG.debug("team-send-message delivered: to={}, targetTopic={}, correlationId={}, senderId={}",
                to, targetTopic, correlationId, callerSessionId);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody("Team message sent to member '" + to + "' at topic " + targetTopic
                + " (correlationId: " + correlationId + ")");
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveArguments(AiToolCall call) {
        Map<String, Object> args = new HashMap<>();
        if (call.getInput() != null && !call.getInput().isEmpty()) {
            try {
                Object parsed = JSON.parse(call.getInput());
                if (parsed instanceof Map) {
                    args.putAll((Map<String, Object>) parsed);
                }
            } catch (Exception e) {
                LOG.debug("team-send-message: could not parse input as JSON, treating as plain text: input={}",
                        call.getInput(), e);
                args.put("body", call.getInput());
            }
        }
        return args;
    }

    private String getStringArg(Map<String, Object> args, AiToolCall call, String key) {
        Object val = args.get(key);
        if (val != null) {
            return val.toString();
        }
        return call.attrText(key);
    }

    private static CompletableFuture<AiToolCallResult> fail(int callId, String message) {
        return CompletableFuture.completedFuture(AiToolCallResult.errorResult(callId, message));
    }

    private static CompletableFuture<AiToolCallResult> honestNotEnabled(int callId, String message) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(callId);
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(message);
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Plan 228 (L4-team-acl-enforcement): ACL-denial honest result
     * (Design Decision §6). status="success" (so the ReAct loop does NOT
     * abort as if it were a technical fault); body is a JSON object that
     * tells the LLM the operation was denied by policy and why. The store
     * / messenger is NOT touched — the executor returns BEFORE performing
     * the operation (Anti-Hollow #22).
     */
    private static CompletableFuture<AiToolCallResult> honestDenied(int callId,
                                                                    String toolName,
                                                                    String action,
                                                                    TeamAclDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("allowed", false);
        body.put("toolName", toolName);
        body.put("action", action);
        body.put("resolvedRole", decision.getResolvedRole() != null
                ? decision.getResolvedRole().name() : null);
        body.put("reason", decision.getReason());

        AiToolCallResult result = new AiToolCallResult();
        result.setId(callId);
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(JSON.stringify(body));
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }
}
