package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.NoOpTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamAclDecision;
import io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator;
import io.nop.ai.agent.team.flow.TeamTaskFlowResult;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.api.core.json.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team-execute-flow tool executor (plan 239 / {@code L4-team-execute-flow-llm-tool}).
 *
 * <p>The top Layer 4 LLM entry point that lets an LLM agent (e.g. a LEAD or
 * MEMBER inside a ReAct loop) drive plan 233's
 * {@link TeamTaskFlowOrchestrator} to execute its own team's task DAG via a
 * tool call. The orchestrator consumes the auto-spawn capability delivered by
 * plans 237/238 (the injected {@link IMemberSpawner}), so an unbound-member
 * team can be orchestrated end to end with no manual {@code bindMemberSession}.
 *
 * <p><b>Parameters</b>: none. The caller's team is resolved from the session
 * (see DD#2).
 *
 * <p><b>Routing</b>: the tool resolves the caller's team via
 * {@code teamManager.getTeamBySession(callerSessionId)} — identical to the
 * other team tools. It does NOT expose a {@code teamId} parameter, so an LLM
 * can only ever orchestrate its own team (ACL-safe).
 *
 * <p><b>Scheduling strategy = async (plan 241 wiring)</b>: the tool calls the
 * orchestrator's non-blocking {@code executeAsync(teamId)} and chains the
 * honest result mapping onto the returned {@link CompletableFuture}. This
 * replaces the pre-241 "wrap sync {@code execute} result in
 * {@code completedFuture}" hollow pattern with a real async consumption of
 * the orchestrator's async entry point. The tool's future now completes
 * when the orchestrator's DAG future completes (not when the synchronous
 * call returns), so the LLM tool dispatcher is not blocked on the entire
 * DAG. Structural fast-failures (empty task set / unknown team / cyclic
 * {@code blockedBy}) still throw synchronously out of {@code executeAsync}
 * before the future is created; they are mapped to {@link AiToolCallResult#errorResult}
 * here (a technical fault, distinct from a DAG outcome). cross-process /
 * multi-team orchestration remains an explicit Non-Goal successor.
 *
 * <p><b>Per-invocation orchestrator construction</b> (DD#3): on each call the
 * tool reads {@code engine} / {@code taskStore} / {@code teamManager} from the
 * context and combines them with the wire-at-consumer-injected
 * {@link IMemberSpawner} to construct a fresh
 * {@link TeamTaskFlowOrchestrator}. The orchestrator is cheap to build and
 * its services come from the (per-invocation) context, while the spawner is a
 * shared singleton (wire-at-consumer). No new field on
 * {@link AgentToolExecuteContext} is needed.
 *
 * <p><b>Honest result mapping</b> (DD#4) — never silent success:
 * <ul>
 *   <li>Orchestrator success ({@code isSuccess()==true}) → {@code status="success"}
 *       with body JSON {@code {success:true, completedTaskIds, startOrder,
 *       completionOrder}}.</li>
 *   <li>Orchestrator failure ({@code isSuccess()==false}, e.g. a node failure
 *       or a NoOp spawner declining to spawn an unbound-member node) →
 *       {@code status="success"} with body JSON {@code {success:false,
 *       failedTaskId, skippedTaskIds, completedTaskIds}}. The
 *       {@code status="success"} keeps the ReAct loop running so the LLM can
 *       read the {@code success:false} body and decide to retry / give up;
 *       the body is honest, not a silent success.</li>
 *   <li>Structural fast-failure (empty task set / unknown team / cyclic
 *       {@code blockedBy} — {@link io.nop.ai.agent.engine.NopAiAgentException}
 *       thrown by the orchestrator) → {@link AiToolCallResult#errorResult}
 *       (technical fault, not a DAG outcome).</li>
 * </ul>
 *
 * <p><b>Honest no-op reporting</b> (Minimum Rules #24): NoOp/null
 * {@code teamManager} or {@code taskStore} short-circuits with an honest "not
 * enabled" report; a caller not bound to a team returns an honest error
 * result; an ACL denial returns an honest denied body (and the orchestrator is
 * never invoked).
 *
 * <p><b>ACL</b> (DD#5): action {@code execute} → required {@code WRITE}
 * (LEAD/MEMBER allowed, non-members denied — registered in
 * {@link io.nop.ai.agent.team.DefaultTeamAclChecker#buildRequiredActions}). The
 * NoOp shipped default ({@link io.nop.ai.agent.team.NoOpTeamAclChecker}) allows
 * everything (zero regression).
 *
 * <p>See plan 239 ({@code L4-team-execute-flow-llm-tool}).
 */
public class TeamExecuteFlowExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "team-execute-flow";

    /**
     * ACL action used by this tool. Registered as
     * {@code ("team-execute-flow", "execute") → WRITE} in
     * {@link io.nop.ai.agent.team.DefaultTeamAclChecker}.
     */
    public static final String ACL_ACTION = "execute";

    private static final Logger LOG = LoggerFactory.getLogger(TeamExecuteFlowExecutor.class);

    /**
     * Wire-at-consumer-injected member spawner (DD#3). Null-safe →
     * {@link NoOpMemberSpawner} shipped default (an unbound-member node
     * honestly fails at run time, mirroring plan 238/237 wiring). A functional
     * spawner (e.g. {@link io.nop.ai.agent.team.DefaultMemberSpawner}) opts the
     * tool's per-invocation orchestrator into run-time auto-spawn.
     */
    private IMemberSpawner memberSpawner = NoOpMemberSpawner.noOp();

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    /**
     * @return the wire-at-consumer member spawner (never {@code null} — the
     *         shipped default is {@link NoOpMemberSpawner}).
     */
    public IMemberSpawner getMemberSpawner() {
        return memberSpawner;
    }

    /**
     * Wire (or re-wire) the member spawner (DD#3, wire-at-consumer). Passing
     * {@code null} resets to the shipped {@link NoOpMemberSpawner} default.
     *
     * @param memberSpawner the spawner to wire; {@code null} falls back to
     *                      {@link NoOpMemberSpawner#noOp()}
     */
    public void setMemberSpawner(IMemberSpawner memberSpawner) {
        this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("team-execute-flow failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "team-execute-flow requires AgentToolExecuteContext (team manager not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        ITeamManager teamManager = agentCtx.getTeamManager();
        ITeamTaskStore taskStore = agentCtx.getTeamTaskStore();

        // DD#5 + Minimum Rules #24: NoOp honest reporting — short-circuit
        // BEFORE any routing logic.
        if (teamManager == null || teamManager instanceof NoOpTeamManager) {
            LOG.debug("team-execute-flow: team functionality not enabled (NoOp/null teamManager)");
            return honestNotEnabled(call.getId(),
                    "Team functionality is not enabled — flow not executed. "
                            + "Wire InMemoryTeamManager via DefaultAgentEngine.setTeamManager to enable.");
        }
        if (taskStore == null || taskStore instanceof NoOpTeamTaskStore) {
            LOG.debug("team-execute-flow: team task store not enabled (NoOp/null taskStore)");
            return honestNotEnabled(call.getId(),
                    "Team task store is not enabled — flow not executed. "
                            + "Wire InMemoryTeamTaskStore via DefaultAgentEngine.setTeamTaskStore to enable.");
        }

        String callerSessionId = agentCtx.getSessionId();
        if (callerSessionId == null || callerSessionId.isEmpty()) {
            return fail(call.getId(),
                    "team-execute-flow failed: caller sessionId is not available in the context");
        }

        // DD#2: resolve caller's team via session reverse-lookup — no teamId
        // parameter is exposed, so an LLM can only orchestrate its own team.
        Optional<Team> teamOpt = teamManager.getTeamBySession(callerSessionId);
        if (teamOpt.isEmpty()) {
            return fail(call.getId(),
                    "team-execute-flow failed: caller session '" + callerSessionId
                            + "' is not bound to any team");
        }
        Team team = teamOpt.get();

        // DD#5: consult the ACL checker after team resolution and BEFORE the
        // orchestrator is constructed / invoked. toolName=team-execute-flow,
        // action=execute → required WRITE (LEAD/MEMBER allowed; non-members
        // denied). NoOp checker → allow(null) → zero regression. The store is
        // NOT touched and the orchestrator is NOT called on denial.
        TeamAclDecision acl = agentCtx.getTeamAclChecker().checkAccess(
                team.getTeamId(), callerSessionId, TOOL_NAME, ACL_ACTION);
        if (!acl.isAllowed()) {
            LOG.debug("team-execute-flow: ACL denied for session={}, teamId={}, reason={}",
                    callerSessionId, team.getTeamId(), acl.getReason());
            return honestDenied(call.getId(), TOOL_NAME, ACL_ACTION, acl);
        }

        // DD#3: per-invocation orchestrator construction. The orchestrator's
        // services (engine/taskStore/teamManager) come from the per-invocation
        // context; the spawner is the wire-at-consumer shared singleton.
        // taskFlowManager is passed null so the orchestrator falls back to its
        // internal TaskFlowManagerImpl (default nop-task runtime).
        IAgentEngine engine = agentCtx.getEngine();
        if (engine == null) {
            return fail(call.getId(),
                    "team-execute-flow failed: no IAgentEngine available in the tool execution context "
                            + "(needed by the orchestrator to execute member agents)");
        }

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
                engine, taskStore, teamManager, null, memberSpawner);

        // Plan 241 wiring: consume the orchestrator's real async entry point.
        // The returned future completes when the DAG future completes (not when
        // the synchronous call returns), eliminating the pre-241 "wrap sync
        // result in completedFuture" hollow pattern. Structural fast-failures
        // (empty task set / unknown team / cyclic blockedBy) throw
        // synchronously out of executeAsync before the future is created; they
        // are mapped here to errorResult (a technical fault distinct from a
        // DAG outcome). Node-level failures are converted by the orchestrator
        // into an honest TeamTaskFlowResult{success=false} (never propagated
        // as a future exception), and mapResult maps that to the honest
        // failure body (status="success" + success:false).
        CompletableFuture<TeamTaskFlowResult> flowFuture;
        try {
            flowFuture = orchestrator.executeAsync(team.getTeamId());
        } catch (io.nop.ai.agent.engine.NopAiAgentException structural) {
            LOG.debug("team-execute-flow structural failure for teamId={}",
                    team.getTeamId(), structural);
            return fail(call.getId(),
                    "team-execute-flow structural failure: " + structural.toString());
        }

        return flowFuture.thenApply(result -> mapResult(call.getId(), result));
    }

    /**
     * DD#4: honest {@link TeamTaskFlowResult} → {@link AiToolCallResult}
     * mapping. Success and node-failure both return {@code status="success"}
     * (so a ReAct loop does not abort on a DAG outcome); the body JSON carries
     * the honest {@code success:true/false} flag and the relevant task ids.
     */
    private static AiToolCallResult mapResult(int callId, TeamTaskFlowResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<String> completedTaskIds = result.getCompletedTaskIds();
        body.put("success", result.isSuccess());
        body.put("completedTaskIds", completedTaskIds);

        if (result.isSuccess()) {
            body.put("startOrder", result.getStartOrder());
            body.put("completionOrder", result.getCompletionOrder());
            LOG.debug("team-execute-flow succeeded: completedTaskIds={}", completedTaskIds);
        } else {
            // Honest failure body (not silent success): expose the failed /
            // skipped task ids so the LLM can reason about the DAG outcome.
            body.put("failedTaskId", result.getFailedTaskId());
            body.put("skippedTaskIds", result.getSkippedTaskIds());
            LOG.debug("team-execute-flow DAG failed: failedTaskId={}, skippedTaskIds={}, completedTaskIds={}",
                    result.getFailedTaskId(), result.getSkippedTaskIds(), completedTaskIds);
        }

        AiToolCallResult out = new AiToolCallResult();
        out.setId(callId);
        out.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(JSON.stringify(body));
        out.setOutput(output);
        return out;
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
     * ACL-denial honest result (DD#5). {@code status="success"} so the ReAct
     * loop does NOT abort as if it were a technical fault; the body is a JSON
     * object describing the policy denial. The orchestrator is NOT invoked.
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
