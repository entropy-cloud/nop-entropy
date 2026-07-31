package io.nop.ai.agent.engine;

import io.nop.ai.agent.conflict.ConflictResult;
import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.IConflictStrategy;
import io.nop.ai.agent.conflict.IWriteIntentRegistry;
import io.nop.ai.agent.conflict.WriteIntent;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.ApprovalDecision;
import io.nop.ai.agent.security.AuditDecision;
import io.nop.ai.agent.security.AuditEvent;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DenialLayerSource;
import io.nop.ai.agent.security.DenialRecord;
import io.nop.ai.agent.security.DenialRecordOutcome;
import io.nop.ai.agent.security.DenialResult;
import io.nop.ai.agent.security.IApprovalGate;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.IDenialLedger;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IPermissionMatrix;
import io.nop.ai.agent.security.IPermissionProvider;
import io.nop.ai.agent.security.IPostDenialGuard;
import io.nop.ai.agent.security.ISecurityLevelResolver;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.LevelHints;
import io.nop.ai.agent.security.MatrixDecision;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.PathAccessResult;
import io.nop.ai.agent.security.Permission;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.ai.agent.security.SecurityCheckpointChain;
import io.nop.ai.agent.security.SecurityLevel;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.ToolPathArgKeys;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dispatch-path security consultation for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Assembles the 7-checkpoint
 * {@link SecurityCheckpointChain} and implements the Layer 1 path-access /
 * Layer 2 security-level + permission-matrix / Layer 3 approval-gate /
 * Layer 2 conflict-strategy consultations plus the denial-ledger threshold
 * handling shared by every deny path. Events are published through the
 * injected {@link AgentHookInvoker}.
 */
public class AgentSecurityConsultation {
    private final IPostDenialGuard postDenialGuard;
    private final IAuditLogger auditLogger;
    private final IToolAccessChecker toolAccessChecker;
    private final IPermissionProvider permissionProvider;
    private final IPathAccessChecker pathAccessChecker;
    private final ISecurityLevelResolver securityLevelResolver;
    private final IPermissionMatrix permissionMatrix;
    private final IApprovalGate approvalGate;
    private final IDenialLedger denialLedger;
    private final IConflictStrategy conflictStrategy;
    private final IWriteIntentRegistry writeIntentRegistry;
    private final AgentToolPlanResolver toolPlanResolver;
    private final AgentHookInvoker hookInvoker;

    public AgentSecurityConsultation(IPostDenialGuard postDenialGuard,
                                     IAuditLogger auditLogger,
                                     IToolAccessChecker toolAccessChecker,
                                     IPermissionProvider permissionProvider,
                                     IPathAccessChecker pathAccessChecker,
                                     ISecurityLevelResolver securityLevelResolver,
                                     IPermissionMatrix permissionMatrix,
                                     IApprovalGate approvalGate,
                                     IDenialLedger denialLedger,
                                     IConflictStrategy conflictStrategy,
                                     IWriteIntentRegistry writeIntentRegistry,
                                     AgentToolPlanResolver toolPlanResolver,
                                     AgentHookInvoker hookInvoker) {
        this.postDenialGuard = postDenialGuard;
        this.auditLogger = auditLogger;
        this.toolAccessChecker = toolAccessChecker;
        this.permissionProvider = permissionProvider;
        this.pathAccessChecker = pathAccessChecker;
        this.securityLevelResolver = securityLevelResolver;
        this.permissionMatrix = permissionMatrix;
        this.approvalGate = approvalGate;
        this.denialLedger = denialLedger;
        this.conflictStrategy = conflictStrategy;
        this.writeIntentRegistry = writeIntentRegistry;
        this.toolPlanResolver = toolPlanResolver;
        this.hookInvoker = hookInvoker;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    /**
     * Build the SecurityCheckpoint chain that replaces the 7 inline deny
     * paths in the dispatch loop. Each checkpoint implements one deny path,
     * following the same pattern: check → if denied → audit + event + error
     * response + handleDenialAndCheckThreshold → DENY or DENY_AND_BREAK.
     * The chain is built once at construction time.
     */
    public SecurityCheckpointChain buildCheckpointChain() {
        // Mutable holder so Layer 2 can pass the resolved SecurityLevel to Layer 3
        java.util.concurrent.atomic.AtomicReference<SecurityLevel> resolvedLevelRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        SecurityCheckpoint postDenialCheckpoint = ctx -> {
            DenialResult postDenialResult = postDenialGuard.checkBeforeDispatch(
                    ctx.sessionId(), ctx.toolName(),
                    ctx.chatToolCall().getArguments(), ctx.fingerprintWorkDir());
            if (postDenialResult != null) {
                String denyMessage = postDenialResult.getMessage() != null
                        ? postDenialResult.getMessage()
                        : "Repeated same denied action";
                auditLogger.log(new AuditEvent(ctx.sessionId(), ctx.agentName(), null,
                        ctx.toolName(), AuditDecision.DENY, denyMessage,
                        "layer3_post_denial_guard",
                        postDenialResult.getActionFingerprint(), System.currentTimeMillis()));
                hookInvoker.publishEvent(AgentEventType.TOOL_CALL_DENIED, ctx.sessionId(), ctx.agentName(),
                        Map.of("toolName", ctx.toolName() != null ? ctx.toolName() : "",
                                "reason", denyMessage,
                                "denialReason", postDenialResult.getReason().name(),
                                "suggestedNextStep", postDenialResult.getSuggestedNextStep().name()));
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        denyMessage + " (suggested: " + postDenialResult.getSuggestedNextStep() + ")"));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER3_POST_DENIAL_GUARD, denyMessage,
                        "layer3_post_denial_guard", ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            return SecurityCheckpoint.Decision.ALLOW;
        };

        SecurityCheckpoint toolAccessCheckpoint = ctx -> {
            ToolAccessResult accessResult = toolAccessChecker.checkAccess(
                    ctx.toolName(), ctx.executionContext());
            auditLogger.log(new AuditEvent(ctx.sessionId(), ctx.agentName(), null,
                    ctx.toolName(),
                    accessResult.isAllowed() ? AuditDecision.ALLOW : AuditDecision.DENY,
                    accessResult.getReason(), accessResult.getMatchedRule(), null,
                    System.currentTimeMillis()));
            if (!accessResult.isAllowed()) {
                hookInvoker.publishEvent(AgentEventType.TOOL_CALL_DENIED, ctx.sessionId(), ctx.agentName(),
                        Map.of("toolName", ctx.toolName(),
                                "reason", accessResult.getReason() != null ? accessResult.getReason() : ""));
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        "Access denied: " + (accessResult.getReason() != null ? accessResult.getReason() : "hardcoded deny")));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER1_TOOL_ACCESS, accessResult.getReason(),
                        accessResult.getMatchedRule(), ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            return SecurityCheckpoint.Decision.ALLOW;
        };

        SecurityCheckpoint permissionCheckpoint = ctx -> {
            Permission perm = permissionProvider.resolve(
                    ctx.toolName(), ctx.agentName(), ctx.sessionId());
            auditLogger.log(new AuditEvent(ctx.sessionId(), ctx.agentName(), null,
                    ctx.toolName(),
                    perm.isAllowed() ? AuditDecision.ALLOW : AuditDecision.DENY,
                    perm.getReason(), perm.getMatchedRuleId(), null,
                    System.currentTimeMillis()));
            if (!perm.isAllowed()) {
                hookInvoker.publishEvent(AgentEventType.TOOL_CALL_DENIED, ctx.sessionId(), ctx.agentName(),
                        Map.of("toolName", ctx.toolName(),
                                "reason", perm.getReason() != null ? perm.getReason() : ""));
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        "Permission denied: " + (perm.getReason() != null ? perm.getReason() : "access denied")));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER1_PERMISSION, perm.getReason(),
                        perm.getMatchedRuleId(), ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            return SecurityCheckpoint.Decision.ALLOW;
        };

        SecurityCheckpoint pathAccessCheckpoint = ctx -> {
            String pathDenied = checkPathAccess(ctx.chatToolCall(),
                    ctx.executionContext(), ctx.sessionId(), ctx.agentName());
            if (pathDenied != null) {
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        "Path access denied: " + pathDenied));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER1_PATH_ACCESS, pathDenied,
                        "path_access_checker", ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            return SecurityCheckpoint.Decision.ALLOW;
        };

        SecurityCheckpoint layer2Checkpoint = ctx -> {
            SecurityConsultationOutcome layer2 = checkLayer2Consultation(
                    ctx.chatToolCall(), ctx.executionContext(),
                    ctx.sessionId(), ctx.agentName(), ctx.agentModel());
            if (layer2.isDenied()) {
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        "Security policy denied: " + layer2.getDenialReason()));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER2_SECURITY_POLICY, layer2.getDenialReason(),
                        "layer2_permission_matrix", ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            resolvedLevelRef.set(layer2.getResolvedLevel());
            return SecurityCheckpoint.Decision.ALLOW;
        };

        SecurityCheckpoint layer3Checkpoint = ctx -> {
            String layer3Denied = checkLayer3Approval(
                    resolvedLevelRef.get(), ctx.toolName(),
                    ctx.executionContext(), ctx.sessionId(), ctx.agentName());
            if (layer3Denied != null) {
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        "Approval denied: " + layer3Denied));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER3_APPROVAL_GATE, layer3Denied,
                        "layer3_approval_gate", ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            return SecurityCheckpoint.Decision.ALLOW;
        };

        SecurityCheckpoint conflictCheckpoint = ctx -> {
            String conflictDenied = checkWriteConflict(
                    ctx.chatToolCall(), ctx.executionContext(),
                    ctx.sessionId(), ctx.agentName(), ctx.agentModel());
            if (conflictDenied != null) {
                ctx.executionContext().addMessage(ChatToolResponseMessage.error(
                        ctx.chatToolCall().getId(), ctx.toolName(),
                        "Conflict denied: " + conflictDenied));
                if (handleDenialAndCheckThreshold(ctx.sessionId(), ctx.toolName(),
                        DenialLayerSource.LAYER2_CONFLICT_STRATEGY, conflictDenied,
                        "layer2_conflict_strategy", ctx.executionContext(), ctx.agentName(),
                        ctx.chatToolCall(), ctx.fingerprintWorkDir())) {
                    return SecurityCheckpoint.Decision.DENY_AND_BREAK;
                }
                return SecurityCheckpoint.Decision.DENY;
            }
            return SecurityCheckpoint.Decision.ALLOW;
        };

        return SecurityCheckpointChain.builder()
                .add(postDenialCheckpoint)
                .add(toolAccessCheckpoint)
                .add(permissionCheckpoint)
                .add(pathAccessCheckpoint)
                .add(layer2Checkpoint)
                .add(layer3Checkpoint)
                .add(conflictCheckpoint)
                .build();
    }
    /**
     * Layer 3 denial-ledger dispatch-path integration (design §6.2 / §6.3 / §8).
     * Called at every deny checkpoint (Layer 1 / 2 / 3 + post-denial-guard
     * consultation — six deny paths) after the existing audit + event + error
     * response. Records the denial into the ledger, records the denied
     * action's fingerprint into the {@link IPostDenialGuard} (design §6.3, so
     * a subsequent blind retry is detectable by the pre-Layer-1
     * consultation), then inspects the returned ledger outcome to decide
     * whether the session has reached the denial threshold.
     *
     * <p>On threshold exceeded: marks the session as {@link AgentExecStatus#paused},
     * records an {@link AuditEvent} (DENY + reason {@code "denial threshold exceeded"}
     * + matched rule {@code "layer3_denial_ledger"}), and publishes a
     * {@link AgentEventType#SESSION_PAUSED} event. The caller then
     * {@code break}s out of the dispatch for-loop.
     *
     * <p>The fingerprint-guard recording forms a closed loop with the
     * pre-Layer-1 consultation: a guard-deny is itself recorded back to the
     * guard, preventing "retry the guard-deny result" loops (design §6.3
     * recording-after-every-deny, including the guard's own deny).
     *
     * <p>With the shipped {@link NoOpDenialLedger} /
     * {@link PassThroughPostDenialGuard} defaults this is a no-op
     * pass-through that always returns {@code false} (backward compatible —
     * no spurious pauses, no fingerprint tracking).
     *
     * @param chatToolCall   the denied tool call (used to extract arguments
     *                       for the fingerprint); never null
     * @param fingerprintWorkDir the workDir string used for fingerprint
     *                       computation; may be null
     * @return {@code true} if the denial threshold has been reached and the
     *         dispatch loop should abort; {@code false} to continue with the
     *         next tool call
     */
    public boolean handleDenialAndCheckThreshold(String sessionId, String toolName,
                                                  DenialLayerSource layerSource, String reason,
                                                  String matchedRule, AgentExecutionContext ctx,
                                                  String agentName,
                                                  ChatToolCall chatToolCall, String fingerprintWorkDir) {
        // Record the denied action's fingerprint into the post-denial guard
        // (design §6.3) so a subsequent blind retry is detectable by the
        // pre-Layer-1 consultation. With the PassThroughPostDenialGuard
        // default this is a no-op (0 overhead).
        postDenialGuard.recordDeniedAction(sessionId, toolName,
                extractArguments(chatToolCall), fingerprintWorkDir);

        DenialRecord record = DenialRecord.of(
                sessionId, toolName, layerSource, reason, matchedRule,
                System.currentTimeMillis());
        DenialRecordOutcome outcome = denialLedger.recordDenial(record);
        if (!outcome.isThresholdExceeded()) {
            return false;
        }
        ctx.setStatus(AgentExecStatus.paused);
        auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                AuditDecision.DENY, "denial threshold exceeded (count=" + outcome.getCount() + ")",
                "layer3_denial_ledger", null, System.currentTimeMillis()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("toolName", toolName != null ? toolName : "");
        payload.put("layerSource", layerSource.name());
        payload.put("denialCount", outcome.getCount());
        payload.put("reason", reason != null ? reason : "");
        hookInvoker.publishEvent(AgentEventType.SESSION_PAUSED, sessionId, agentName, payload);
        return true;
    }
    /**
     * Extract the arguments map from a tool call for fingerprint computation.
     * Returns an empty map when the tool call carries no arguments.
     */
    public static Map<String, Object> extractArguments(ChatToolCall chatToolCall) {
        Map<String, Object> args = chatToolCall.getArguments();
        return args != null ? args : Collections.emptyMap();
    }

    /**
     * Resolve the workDir as a String for action-fingerprint computation
     * (design §6.3). Returns null when the agent model declares no workDir.
     */
    public static String resolveWorkDirString(AgentModel agentModel) {
        String workDir = agentModel.getWorkDir();
        return (workDir != null && !workDir.trim().isEmpty()) ? workDir : null;
    }
    public String checkPathAccess(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                   String sessionId, String agentName) {
        Map<String, Object> arguments = chatToolCall.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String pathValue = (String) value;
            if (pathValue == null || pathValue.trim().isEmpty()) {
                continue;
            }

            PathAccessResult pathResult = pathAccessChecker.checkAccess(pathValue, ctx);
            if (!pathResult.isAllowed()) {
                auditLogger.log(new AuditEvent(sessionId, agentName, null, chatToolCall.getName(),
                        AuditDecision.DENY, pathResult.getReason(), pathResult.getMatchedRule(),
                        pathValue, System.currentTimeMillis()));
                hookInvoker.publishEvent(AgentEventType.PATH_ACCESS_DENIED, sessionId, agentName,
                        Map.of("path", pathValue,
                                "reason", pathResult.getReason() != null ? pathResult.getReason() : ""));
                return pathResult.getReason() != null ? pathResult.getReason() : "path access denied";
            }
        }

        return null;
    }
    /**
     * Layer 2 dispatch-path consultation (design §5.1/§5.3/§8). Produces the
     * {@link LevelHints} for the tool call, resolves the {@link SecurityLevel}
     * via {@link ISecurityLevelResolver}, and consults the channel × level
     * {@link IPermissionMatrix} using the context's {@code channelKind} and
     * {@code principal}.
     *
     * <p>On denial: records an {@link AuditEvent} (DENY + reason + matched rule
     * {@code "layer2_permission_matrix"}) and publishes a
     * {@code TOOL_CALL_DENIED} event, then carries the auditable reason in the
     * returned outcome — mirroring the Layer 1 deny path. The caller turns a
     * denied outcome into a {@code ChatToolResponseMessage.error(...)} and
     * skips the call.
     *
     * <p>On allow: the outcome carries a null denial reason and the tool call
     * proceeds. With the shipped {@link NoOpSecurityLevelResolver} /
     * {@link PassThroughPermissionMatrix} defaults this always allows
     * (backward compatible — no spurious denials).
     *
     * <p>The resolved {@link SecurityLevel} is always carried in the outcome so
     * the Layer 3 consultation can reuse it without resolving twice.
     *
     * @return the consultation outcome (resolved level + optional denial
     *         reason); never null
     */
    public SecurityConsultationOutcome checkLayer2Consultation(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                                                String sessionId, String agentName, AgentModel agentModel) {
        String toolName = chatToolCall.getName();

        File workDir = toolPlanResolver.resolveWorkDir(agentModel);
        LevelHints hints = securityLevelResolver.produce(toolName, chatToolCall.getArguments(), workDir, ctx);
        SecurityLevel level = securityLevelResolver.resolve(toolName, hints);

        ChannelKind channel = ctx.getChannelKind();
        Principal principal = ctx.getPrincipal();
        MatrixDecision decision = permissionMatrix.check(channel, principal, level);
        if (decision.isDenied()) {
            String reason = decision.getReason() != null
                    ? decision.getReason()
                    : "security level " + level + " denied for channel " + channel;
            String auditContext = "channel=" + (channel != null ? channel.name() : "unknown")
                    + ",level=" + level.name();
            auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                    AuditDecision.DENY, reason, "layer2_permission_matrix",
                    auditContext, System.currentTimeMillis()));
            hookInvoker.publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                    Map.of("toolName", toolName,
                            "reason", decision.getReason() != null ? decision.getReason() : "",
                            "securityLevel", level.name(),
                            "channel", channel != null ? channel.name() : "unknown"));
            return SecurityConsultationOutcome.denied(level, reason);
        }
        return SecurityConsultationOutcome.allowed(level);
    }
    /**
     * Layer 3 dispatch-path consultation (design §6.1/§8). After the Layer 2
     * matrix allows, consults the {@link IApprovalGate} with the resolved
     * {@link SecurityLevel} and the tool-call context.
     *
     * <p>On denial: records an {@link AuditEvent} (DENY + reason + matched rule
     * {@code "layer3_approval_gate"}) and publishes a
     * {@code TOOL_CALL_DENIED} event, then returns the auditable reason —
     * mirroring the Layer 1/2 deny paths. The caller turns the non-null return
     * into a {@code ChatToolResponseMessage.error(...)} and skips the call.
     *
     * <p>On approval: returns {@code null} and the tool call proceeds. With the
     * shipped {@link DefaultApprovalGate} default this approves
     * STANDARD/ELEVATED and defense-in-depth denies RESTRICTED (plan 199).
     *
     * @param level the security level already resolved during the Layer 2
     *              consultation (reused to avoid a second resolve)
     * @return the denial reason, or {@code null} when the call is approved
     */
    public String checkLayer3Approval(SecurityLevel level, String toolName,
                                       AgentExecutionContext ctx, String sessionId, String agentName) {
        ChannelKind channel = ctx.getChannelKind();
        Principal principal = ctx.getPrincipal();
        ApprovalDecision decision = approvalGate.requestApproval(
                level, toolName, channel, principal, sessionId, agentName);
        if (decision.isDenied()) {
            String reason = decision.getReason() != null
                    ? decision.getReason()
                    : "approval denied (kind=" + decision.getDenialKind() + ") for level " + level;
            String auditContext = "channel=" + (channel != null ? channel.name() : "unknown")
                    + ",level=" + level.name()
                    + ",kind=" + decision.getDenialKind();
            auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                    AuditDecision.DENY, reason, "layer3_approval_gate",
                    auditContext, System.currentTimeMillis()));
            hookInvoker.publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                    Map.of("toolName", toolName,
                            "reason", decision.getReason() != null ? decision.getReason() : "",
                            "securityLevel", level.name(),
                            "denialKind", decision.getDenialKind() != null ? decision.getDenialKind().name() : "",
                            "channel", channel != null ? channel.name() : "unknown"));
            return reason;
        }
        return null;
    }
    /**
     * Plan 214 (L2-13a): Layer 2 conflict-strategy consultation (design
     * {@code nop-ai-agent-multi-agent.md} §4.4). Called from the dispatch
     * loop after the Layer 3 approval gate allows and before
     * {@code allowedCalls.add(...)}. Extracts the path arguments from the
     * current tool call, normalizes each to an absolute path (reusing the
     * same normalization as the Layer 1 path-access check and the
     * LevelHints producer), and registers a {@link WriteIntent} per path
     * into the {@link IWriteIntentRegistry}. If any registration returns a
     * non-empty conflict set (another session has an active intent on the
     * same file), the {@link IConflictStrategy} decides ALLOW or DENY.
     *
     * <p><b>Path normalization rule</b> (design裁定): relative paths are
     * resolved against the agent's declared {@code workDir} (or JVM CWD
     * when no workDir is declared, matching
     * {@code DefaultLevelHintsProducer.evaluateWritesOutside}). The
     * resulting absolute path is normalized via
     * {@link DefaultPathAccessChecker#normalizePathStatic(String)} so the
     * registry key is identical to the key used by the Layer 1 path checker
     * — two intents on the same physical file compare equal by {@code filePath}.
     *
     * <p><b>Multi-path handling</b>: a tool call may carry multiple
     * {@code ToolPathArgKeys} hits (e.g. {@code copy-file} with
     * {@code source} + {@code destination}). Each hit produces an
     * independent {@link WriteIntent}; the call is denied if any one path
     * is denied (conservative — never partially register a multi-path call).
     *
     * <p><b>Known Phase-1 limitation</b>: a path-arg hit on a non-write
     * tool (e.g. {@code read-file}) also registers an intent. Under the
     * shipped {@link FailFastStrategy} this is a conservative false-positive
     * (宁可误拒); tool-name-level write-tool classification is a successor.
     *
     * @return the denial reason, or {@code null} when the call has no path
     *         arguments or every conflict was allowed by the strategy. When
     *         non-null, the caller must take the standard deny path (error
     *         response + audit + denial-ledger recording); the denied intent
     *         remains registered and is reclaimed by {@code releaseSession}
     *         when the session terminates.
     */
    public String checkWriteConflict(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                      String sessionId, String agentName, AgentModel agentModel) {
        Map<String, Object> arguments = chatToolCall.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        // Resolve the base directory for relative-path resolution. Mirrors
        // DefaultLevelHintsProducer: agent workDir when declared, JVM CWD
        // otherwise. Reuse toolPlanResolver.resolveWorkDir(agentModel) for the agent-workDir
        // case so the File instance is identical to the one used elsewhere
        // in this executor.
        File agentWorkDir = toolPlanResolver.resolveWorkDir(agentModel);
        File baseDir = agentWorkDir != null ? agentWorkDir : new File(".").getAbsoluteFile();

        String toolName = chatToolCall.getName();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String rawPath = (String) value;
            if (rawPath == null || rawPath.trim().isEmpty()) {
                continue;
            }

            String absolutePath = resolveAbsolute(rawPath, baseDir);
            String normalized = DefaultPathAccessChecker.normalizePathStatic(absolutePath);
            if (normalized == null) {
                // Normalization failed (invalid traversal, no home dir for
                // tilde, etc.). Skip conflict detection for this path — the
                // Layer 1 path-access check is the authoritative guard for
                // malformed paths, and we must not throw here (the call may
                // still be denied by Layer 1 on its own merits).
                continue;
            }

            WriteIntent intent = new WriteIntent(
                    sessionId, agentName, normalized, toolName, now);
            Set<WriteIntent> conflicting =
                    writeIntentRegistry.registerAndGetConflicting(intent);

            // Always consult the strategy — even with an empty conflict set.
            // The FailFastStrategy default returns ALLOW immediately on an
            // empty set (zero overhead), but always calling resolve() lets a
            // future CoordinationBusStrategy observe/broadcast every write
            // intent (not just conflicting ones) and keeps the
            // dispatch-path → strategy wiring verifiable without
            // pre-population (Minimum Rules #23).
            ConflictResult result = conflictStrategy.resolve(intent, conflicting);
            auditLogger.log(new AuditEvent(sessionId, agentName, null, toolName,
                    result.isDenied() ? AuditDecision.DENY : AuditDecision.ALLOW,
                    result.getReason(), "layer2_conflict_strategy",
                    normalized, now));
            if (result.isDenied()) {
                hookInvoker.publishEvent(AgentEventType.TOOL_CALL_DENIED, sessionId, agentName,
                        Map.of("toolName", toolName != null ? toolName : "",
                                "reason", result.getReason() != null ? result.getReason() : "",
                                "conflictPath", normalized,
                                "conflictStrategy", result.getStrategyName() != null
                                        ? result.getStrategyName() : "",
                                "conflictingSessions", conflicting.stream()
                                        .map(WriteIntent::getSessionId)
                                        .findFirst().orElse("")));
                return result.getReason() != null
                        ? result.getReason()
                        : "write conflict on path " + normalized;
            }
            // ALLOW (with or without conflict): the intent is already
            // registered; proceed to the next path argument.
        }
        return null;
    }
    /**
     * Resolve a (possibly relative) path string against the given base
     * directory and return the absolute form. Mirrors
     * {@code DefaultLevelHintsProducer.isOutsideBase}'s relative→absolute
     * resolution so both layers agree on what "absolute" means for the
     * same raw path.
     */
    public static String resolveAbsolute(String rawPath, File baseDir) {
        File resolved = new File(rawPath);
        if (!resolved.isAbsolute()) {
            resolved = new File(baseDir, rawPath);
        }
        return resolved.getAbsolutePath();
    }
    /**
     * Immutable result of the Layer 2 security consultation. Carries the
     * resolved {@link SecurityLevel} (always set, so the Layer 3 approval
     * consultation can reuse it without a second resolve) and an optional
     * denial reason (non-null when the Layer 2 matrix denied the call).
     */
    static final class SecurityConsultationOutcome {
        private final SecurityLevel resolvedLevel;
        private final String denialReason;

        private SecurityConsultationOutcome(SecurityLevel resolvedLevel, String denialReason) {
            this.resolvedLevel = resolvedLevel;
            this.denialReason = denialReason;
        }

        static SecurityConsultationOutcome allowed(SecurityLevel level) {
            return new SecurityConsultationOutcome(level, null);
        }

        static SecurityConsultationOutcome denied(SecurityLevel level, String reason) {
            return new SecurityConsultationOutcome(level, reason);
        }

        SecurityLevel getResolvedLevel() {
            return resolvedLevel;
        }

        String getDenialReason() {
            return denialReason;
        }

        boolean isDenied() {
            return denialReason != null;
        }
    }
}

