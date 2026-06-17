package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.NoOpTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamAclDecision;
import io.nop.ai.agent.team.TeamTask;
import io.nop.api.core.json.JSON;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Team-task-create tool executor. Creates a new task in the caller's team
 * shared task store and returns the generated {@code taskId}.
 *
 * <p><b>Parameters</b>:
 * <ul>
 *   <li>{@code subject} (required) — short task title.</li>
 *   <li>{@code description} (optional) — longer description.</li>
 *   <li>{@code blockedBy} (optional) — comma-separated list of dependency
 *       task IDs (stored verbatim, not resolved in this slice).</li>
 * </ul>
 *
 * <p><b>Routing</b>: the tool resolves the caller's team via
 * {@code teamManager.getTeamBySession(callerSessionId)}, then creates the
 * task in the team's store with {@code createdBy = callerSessionId}.
 *
 * <p><b>Honest no-op reporting</b> (Minimum Rules #24): if the teamManager is
 * null or a {@link NoOpTeamManager}, or the teamTaskStore is null or a
 * {@link NoOpTeamTaskStore}, the tool short-circuits with an honest report
 * that the operation was not executed — it does NOT pretend success.
 *
 * <p>See plan 225 (L4-8-team-tools), Design Decisions §4 / §5 / §7, and
 * vision §8.2.
 */
public class TeamTaskCreateExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "team-task-create";

    private static final Logger LOG = LoggerFactory.getLogger(TeamTaskCreateExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("team-task-create failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "team-task-create requires AgentToolExecuteContext (team manager not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        ITeamManager teamManager = agentCtx.getTeamManager();
        ITeamTaskStore taskStore = agentCtx.getTeamTaskStore();

        // DD#5: NoOp honest reporting — short-circuit BEFORE any routing logic.
        if (teamManager == null || teamManager instanceof NoOpTeamManager) {
            LOG.debug("team-task-create: team functionality not enabled (NoOp/null teamManager)");
            return honestNotEnabled(call.getId(),
                    "Team functionality is not enabled — task not created. "
                            + "Wire InMemoryTeamManager via DefaultAgentEngine.setTeamManager to enable.");
        }
        if (taskStore == null || taskStore instanceof NoOpTeamTaskStore) {
            LOG.debug("team-task-create: team task store not enabled (NoOp/null taskStore)");
            return honestNotEnabled(call.getId(),
                    "Team task store is not enabled — task not created. "
                            + "Wire InMemoryTeamTaskStore via DefaultAgentEngine.setTeamTaskStore to enable.");
        }

        String callerSessionId = agentCtx.getSessionId();
        if (callerSessionId == null || callerSessionId.isEmpty()) {
            return fail(call.getId(),
                    "team-task-create failed: caller sessionId is not available in the context");
        }

        Optional<Team> teamOpt = teamManager.getTeamBySession(callerSessionId);
        if (teamOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-task-create failed: caller session '" + callerSessionId
                            + "' is not bound to any team");
        }
        Team team = teamOpt.get();

        // Plan 228 (L4-team-acl-enforcement): consult the ACL checker after
        // team resolution and BEFORE argument parsing / task creation.
        // toolName=team-task-create, action=create → required WRITE (allowed
        // for LEAD and MEMBER; denied for non-members). NoOp → allow(null).
        TeamAclDecision acl = agentCtx.getTeamAclChecker().checkAccess(
                team.getTeamId(), callerSessionId, TOOL_NAME, "create");
        if (!acl.isAllowed()) {
            LOG.debug("team-task-create: ACL denied for session={}, teamId={}, reason={}",
                    callerSessionId, team.getTeamId(), acl.getReason());
            return honestDenied(call.getId(), TOOL_NAME, "create", acl);
        }

        Map<String, Object> args = resolveArguments(call);

        String subject = getStringArg(args, call, "subject");
        if (subject == null || subject.isEmpty()) {
            return fail(call.getId(), "team-task-create failed: 'subject' is required");
        }

        String description = getStringArg(args, call, "description");

        // DD#4: blockedBy is a comma-separated task ID list (optional).
        List<String> blockedBy = parseBlockedBy(getStringArg(args, call, "blockedBy"));

        TeamTask task = taskStore.createTask(
                team.getTeamId(), subject, description, blockedBy, callerSessionId);

        LOG.debug("team-task-create: created taskId={}, teamId={}, subject='{}'",
                task.getTaskId(), team.getTeamId(), subject);

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("taskId", task.getTaskId());
        resultJson.put("status", task.getStatus().name());

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(JSON.stringify(resultJson));
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    static List<String> parseBlockedBy(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
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
                LOG.debug("team-task-create: could not parse input as JSON, treating as plain text: input={}",
                        call.getInput(), e);
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
     * abort); body is a JSON object describing the policy denial. The store
     * is NOT touched — createTask is NOT called.
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
