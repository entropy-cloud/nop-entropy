package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team-status tool executor. Queries the caller's team status (team info +
 * member list with binding state + task count) and returns a structured JSON
 * string.
 *
 * <p><b>Routing</b>: the tool resolves the caller's team via
 * {@code teamManager.getTeamBySession(callerSessionId)}. If the caller is not
 * in a team, the tool returns an honest error result.
 *
 * <p><b>Honest no-op reporting</b> (Minimum Rules #24): if the teamManager is
 * null or a {@link NoOpTeamManager} (team functionality not enabled), the
 * tool short-circuits with an honest report that the operation was not
 * executed. The task count is sourced from the
 * {@link ITeamTaskStore} (0 when NoOp) — {@code team-status} does NOT
 * short-circuit on a NoOp task store because the team + member info is still
 * meaningful.
 *
 * <p>See plan 225 (L4-8-team-tools), Design Decisions §3, and vision §8.2.
 */
public class TeamStatusExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "team-status";

    private static final Logger LOG = LoggerFactory.getLogger(TeamStatusExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("team-status failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "team-status requires AgentToolExecuteContext (team manager not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        ITeamManager teamManager = agentCtx.getTeamManager();

        // DD#5: NoOp honest reporting — short-circuit BEFORE any query logic.
        if (teamManager == null || teamManager instanceof NoOpTeamManager) {
            LOG.debug("team-status: team functionality not enabled (NoOp/null teamManager)");
            return honestNotEnabled(call.getId(),
                    "Team functionality is not enabled — status not returned. "
                            + "Wire InMemoryTeamManager via DefaultAgentEngine.setTeamManager to enable.");
        }

        String callerSessionId = agentCtx.getSessionId();
        if (callerSessionId == null || callerSessionId.isEmpty()) {
            return fail(call.getId(),
                    "team-status failed: caller sessionId is not available in the context");
        }

        Optional<Team> teamOpt = teamManager.getTeamBySession(callerSessionId);
        if (teamOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-status failed: caller session '" + callerSessionId
                            + "' is not bound to any team");
        }
        Team team = teamOpt.get();

        // Plan 228 (L4-team-acl-enforcement): consult the ACL checker after
        // team resolution and BEFORE any taskStore query / status build.
        // toolName=team-status, action=view → required READ (allowed for
        // LEAD and MEMBER; denied for non-members). NoOp → allow(null).
        TeamAclDecision acl = agentCtx.getTeamAclChecker().checkAccess(
                team.getTeamId(), callerSessionId, TOOL_NAME, "view");
        if (!acl.isAllowed()) {
            LOG.debug("team-status: ACL denied for session={}, teamId={}, reason={}",
                    callerSessionId, team.getTeamId(), acl.getReason());
            return honestDenied(call.getId(), TOOL_NAME, "view", acl);
        }

        // DD#3: build structured JSON output.
        ITeamTaskStore taskStore = agentCtx.getTeamTaskStore();
        int taskCount = 0;
        if (taskStore != null) {
            taskCount = taskStore.getTasksByTeam(team.getTeamId()).size();
        }

        List<Map<String, Object>> membersJson = new ArrayList<>();
        for (TeamMember member : team.getMembers().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("memberName", member.getMemberName());
            m.put("role", member.getRole().name());
            m.put("sessionId", member.getSessionId());
            m.put("actorId", member.getActorId());
            m.put("bound", member.isBound());
            membersJson.add(m);
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("teamId", team.getTeamId());
        status.put("teamName", team.getSpec().getTeamName());
        status.put("status", team.getStatus().name());
        status.put("members", membersJson);
        status.put("taskCount", taskCount);

        String json = JSON.stringify(status);

        LOG.debug("team-status returned: teamId={}, memberCount={}, taskCount={}",
                team.getTeamId(), membersJson.size(), taskCount);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(json);
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
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
     * abort); body is a JSON object describing the policy denial. The
     * taskStore is NOT queried.
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
