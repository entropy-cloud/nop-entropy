package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.NoOpTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamAclDecision;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.api.core.json.JSON;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team-task-update tool executor. Drives the team task state machine
 * (claim / complete / abandon) on the caller's team shared task store.
 *
 * <p><b>Parameters</b>:
 * <ul>
 *   <li>{@code taskId} (required) — the task identity returned by
 *       {@code team-task-create}.</li>
 *   <li>{@code action} (required) — one of {@code claim} / {@code complete}
 *       / {@code abandon} (case-insensitive).</li>
 * </ul>
 *
 * <p><b>Routing</b> (design 裁定 5): the tool resolves the caller's team via
 * {@code teamManager.getTeamBySession(callerSessionId)}, loads the task, and
 * verifies the task belongs to the caller's team
 * ({@code task.teamId == team.teamId}). A cross-team taskId is rejected.
 *
 * <p><b>State machine</b>: each {@code action} maps to a single
 * {@link ITeamTaskStore} transition method. Legal transitions follow
 * {@link TeamTaskStatus} (CREATED→CLAIMED→COMPLETED, CLAIMED/CREATED→ABANDONED).
 *
 * <p><b>Honest no-op reporting</b> (Minimum Rules #24): if the teamManager is
 * null or a {@link NoOpTeamManager}, or the teamTaskStore is null or a
 * {@link NoOpTeamTaskStore}, the tool short-circuits with an honest report
 * that the operation was not executed — it does NOT pretend success. A CAS
 * failure (store returns empty — task in a state that disallows the action, or
 * lost a concurrent race) is converted to an honest error result that tells
 * the LLM the current status, not an exception.
 *
 * <p>See plan 227 (team-task-update), Design Decisions §2 / §5 / §6, and
 * vision §8.2.
 */
public class TeamTaskUpdateExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "team-task-update";

    private static final Logger LOG = LoggerFactory.getLogger(TeamTaskUpdateExecutor.class);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("team-task-update failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "team-task-update requires AgentToolExecuteContext (team manager not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        ITeamManager teamManager = agentCtx.getTeamManager();
        ITeamTaskStore taskStore = agentCtx.getTeamTaskStore();

        // DD#5: NoOp honest reporting — short-circuit BEFORE any routing logic.
        if (teamManager == null || teamManager instanceof NoOpTeamManager) {
            LOG.debug("team-task-update: team functionality not enabled (NoOp/null teamManager)");
            return honestNotEnabled(call.getId(),
                    "Team functionality is not enabled — task not updated. "
                            + "Wire InMemoryTeamManager via DefaultAgentEngine.setTeamManager to enable.");
        }
        if (taskStore == null || taskStore instanceof NoOpTeamTaskStore) {
            LOG.debug("team-task-update: team task store not enabled (NoOp/null taskStore)");
            return honestNotEnabled(call.getId(),
                    "Team task store is not enabled — task not updated. "
                            + "Wire InMemoryTeamTaskStore via DefaultAgentEngine.setTeamTaskStore to enable.");
        }

        String callerSessionId = agentCtx.getSessionId();
        if (callerSessionId == null || callerSessionId.isEmpty()) {
            return fail(call.getId(),
                    "team-task-update failed: caller sessionId is not available in the context");
        }

        Optional<Team> teamOpt = teamManager.getTeamBySession(callerSessionId);
        if (teamOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-task-update failed: caller session '" + callerSessionId
                            + "' is not bound to any team");
        }
        Team team = teamOpt.get();

        Map<String, Object> args = resolveArguments(call);

        String taskId = getStringArg(args, call, "taskId");
        if (taskId == null || taskId.isEmpty()) {
            return fail(call.getId(), "team-task-update failed: 'taskId' is required");
        }

        String actionRaw = getStringArg(args, call, "action");
        if (actionRaw == null || actionRaw.isEmpty()) {
            return fail(call.getId(), "team-task-update failed: 'action' is required "
                    + "(one of: claim, complete, abandon)");
        }
        String action = actionRaw.trim().toLowerCase(Locale.ROOT);

        // Load the task and verify it belongs to the caller's team.
        Optional<TeamTask> taskOpt = taskStore.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-task-update failed: task '" + taskId + "' does not exist");
        }
        TeamTask current = taskOpt.get();
        if (!team.getTeamId().equals(current.getTeamId())) {
            return fail(call.getId(),
                    "team-task-update failed: task '" + taskId
                            + "' does not belong to the caller's team");
        }

        // Plan 228 (L4-team-acl-enforcement): consult the ACL checker AFTER
        // task load + cross-team check, BEFORE the action switch. The
        // abandon action needs the current task status to decide whether
        // it is abandon-claimed (CLAIMED→ABANDONED, required EXECUTE) or
        // abandon-unclaimed (CREATED→ABANDONED, required ADMIN — the only
        // MEMBER-denied operation, Design Decision §2). claim → "claim"
        // (EXECUTE), complete → "complete" (EXECUTE). NoOp → allow(null).
        String aclAction;
        switch (action) {
            case "claim":
            case "complete":
                aclAction = action;
                break;
            case "abandon":
                if (current.getStatus() == TeamTaskStatus.CLAIMED) {
                    aclAction = "abandon-claimed";
                } else {
                    // CREATED (the only other legal source for abandon;
                    // COMPLETED/ABANDONED will fail CAS below anyway). For
                    // ACL adjudication we treat any non-CLAIMED source as
                    // abandon-unclaimed (ADMIN-only) — a MEMBER is denied
                    // before the CAS is attempted.
                    aclAction = "abandon-unclaimed";
                }
                break;
            default:
                // Unknown action will be rejected by the switch below;
                // for ACL we use the raw action string (will fail closed
                // inside the checker as an unknown tuple).
                aclAction = action;
                break;
        }
        TeamAclDecision acl = agentCtx.getTeamAclChecker().checkAccess(
                team.getTeamId(), callerSessionId, TOOL_NAME, aclAction);
        if (!acl.isAllowed()) {
            LOG.debug("team-task-update: ACL denied for session={}, teamId={}, action={}, reason={}",
                    callerSessionId, team.getTeamId(), aclAction, acl.getReason());
            return honestDenied(call.getId(), TOOL_NAME, aclAction, acl);
        }

        Optional<TeamTask> updated;
        switch (action) {
            case "claim":
                updated = taskStore.claimTask(taskId, callerSessionId);
                break;
            case "complete":
                updated = taskStore.completeTask(taskId, callerSessionId);
                break;
            case "abandon":
                updated = taskStore.abandonTask(taskId, callerSessionId);
                break;
            default:
                return fail(call.getId(),
                        "team-task-update failed: unknown action '" + actionRaw
                                + "'. Must be one of: claim, complete, abandon");
        }

        // CAS failure (illegal transition / lost race) → honest error, not exception.
        if (updated.isEmpty()) {
            LOG.debug("team-task-update: CAS failed for taskId={}, action={}, currentStatus={}",
                    taskId, action, current.getStatus());
            Map<String, Object> errJson = new LinkedHashMap<>();
            errJson.put("taskId", taskId);
            errJson.put("action", action);
            errJson.put("currentStatus", current.getStatus().name());
            errJson.put("applied", false);
            errJson.put("message", "Task '" + taskId + "' is currently "
                    + current.getStatus().name() + " — action '" + action
                    + "' is not allowed from this state");
            return honestCasResult(call.getId(), errJson);
        }

        TeamTask result = updated.get();
        LOG.debug("team-task-update: applied action={} to taskId={}, newStatus={}",
                action, taskId, result.getStatus());

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("taskId", result.getTaskId());
        resultJson.put("action", action);
        resultJson.put("status", result.getStatus().name());
        resultJson.put("claimedBy", result.getClaimedBy());
        resultJson.put("applied", true);

        return successJson(call.getId(), resultJson);
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
                LOG.debug("team-task-update: could not parse input as JSON, treating as plain text: input={}",
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
     * abort as if it were a technical fault); body is a JSON object telling
     * the LLM the operation was denied by policy and which action was
     * attempted (claim/complete/abandon-claimed/abandon-unclaimed). The
     * store is NOT touched — the executor returns BEFORE the action switch
     * is reached (Anti-Hollow #22 + Wiring #23).
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

    /**
     * CAS-failure honest result: status "success" (so the ReAct loop does not
     * abort), body is a JSON object describing that the action was not applied
     * and the current status — an honest strategy feedback for the LLM, not a
     * silent success.
     */
    private static CompletableFuture<AiToolCallResult> honestCasResult(int callId, Map<String, Object> json) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(callId);
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(JSON.stringify(json));
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }

    private static CompletableFuture<AiToolCallResult> successJson(int callId, Map<String, Object> json) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(callId);
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(JSON.stringify(json));
        result.setOutput(output);
        return CompletableFuture.completedFuture(result);
    }
}
