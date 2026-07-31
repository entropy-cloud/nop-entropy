package io.nop.ai.agent.engine;

import io.nop.ai.agent.budget.IBudgetProvider;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.compact.IContextCompactor;
import io.nop.ai.agent.compact.Layer2TurnPruningStrategy;
import io.nop.ai.agent.compact.Layer3FullSummaryStrategy;
import io.nop.ai.agent.compact.MicroCompressionCompactor;
import io.nop.ai.agent.compact.PipelineCompactor;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.AutoApproveGate;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.IAuditLogger;
import io.nop.ai.agent.security.NoOpAuditLogger;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.NoOpSecurityLevelResolver;
import io.nop.ai.agent.security.PassThroughPermissionMatrix;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.Slf4jAuditLogger;
import io.nop.ai.agent.usage.NoOpUsageRecorder;
import io.nop.ai.api.chat.IChatService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup security-default warnings for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05). Emits WARN/INFO lines when the
 * wired components are insecure or pass-through NoOp defaults, so security
 * downgrades stay visible rather than silent. Also owns the one-shot
 * NoOpUsageRecorder warning flag.
 */
public class AgentStartupWarnings {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
    private boolean usageRecorderNoOpWarned;

    public void resetUsageRecorderNoOpWarned() {
        this.usageRecorderNoOpWarned = false;
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    public static IContextCompactor defaultPipelineCompactor(IChatService chatService) {
        return new PipelineCompactor(
                new MicroCompressionCompactor(),
                new Layer2TurnPruningStrategy(),
                new Layer3FullSummaryStrategy(chatService)
        );
    }
    public void warnIfInsecureDefaults(DefaultAgentEngineConfig config) {
        // --- Always-checked: components with secure defaults ---
        if (config.getToolAccessChecker() instanceof AllowAllToolAccessChecker) {
            LOG.warn("DefaultAgentEngine constructed with AllowAllToolAccessChecker: "
                    + "dangerous tools (bash/write-file/delete-file/move-file/patch-file/apply-delta/"
                    + "http-request/graphql-query) are NOT blocked. This is an insecure default. "
                    + "To restore secure-by-default behaviour, do not pass an AllowAllToolAccessChecker "
                    + "to the constructor — the default already uses DefaultToolAccessChecker.");
        }
        if (config.getPathAccessChecker() instanceof AllowAllPathAccessChecker) {
            LOG.warn("DefaultAgentEngine constructed with AllowAllPathAccessChecker: "
                    + "sensitive paths (~/.ssh/, ~/.aws/, /etc/, .env, id_rsa, ...) are NOT blocked. "
                    + "This is an insecure default. To restore secure-by-default behaviour, do not pass "
                    + "an AllowAllPathAccessChecker to the constructor — the default already uses "
                    + "DefaultPathAccessChecker.");
        }
        if (config.getAuditLogger() instanceof NoOpAuditLogger) {
            LOG.warn("DefaultAgentEngine constructed with NoOpAuditLogger: "
                    + "audit events are being DISCARDED — tool decisions (deny/approve/override) "
                    + "leave NO record. This is an insecure downgrade of the audit trail. "
                    + "To restore secure-by-default behaviour, do not pass a NoOpAuditLogger "
                    + "to setAuditLogger — the default already uses Slf4jAuditLogger. "
                    + "For a custom audit sink (e.g. database), supply your own IAuditLogger.");
        }
        if (config.getApprovalGate() instanceof AutoApproveGate) {
            LOG.warn("DefaultAgentEngine wired with AutoApproveGate: "
                    + "ALL operations including RESTRICTED are unconditionally auto-approved — "
                    + "the defense-in-depth RESTRICTED deny provided by the default "
                    + "DefaultApprovalGate is bypassed. This is an insecure downgrade of the "
                    + "Layer 3 approval gate. To restore secure-by-default behaviour, do not pass "
                    + "an AutoApproveGate to setApprovalGate — the default already uses "
                    + "DefaultApprovalGate (denies RESTRICTED, approves STANDARD/ELEVATED).");
        }

        // --- Layer 2/3 NoOp/PassThrough: always-checked (plan 200 migrated to Default* defaults) ---
        if (config.getSecurityLevelResolver() instanceof NoOpSecurityLevelResolver) {
            LOG.warn("DefaultAgentEngine wired with NoOpSecurityLevelResolver: "
                    + "all operations resolve to STANDARD — no security-level classification is "
                    + "performed. RESTRICTED/ELEVATED levels are never produced, so the approval "
                    + "gate's defense-in-depth deny and the permission matrix's level checks are "
                    + "ineffective. To restore secure-by-default behaviour, do not pass a "
                    + "NoOpSecurityLevelResolver to setSecurityLevelResolver — the default already "
                    + "uses DefaultSecurityLevelResolver.");
        }
        if (config.getPermissionMatrix() instanceof PassThroughPermissionMatrix) {
            LOG.warn("DefaultAgentEngine wired with PassThroughPermissionMatrix: "
                    + "all channels allow all security levels — no channel-based permission "
                    + "restrictions are enforced. To restore secure-by-default behaviour, do not "
                    + "pass a PassThroughPermissionMatrix to setPermissionMatrix — the default "
                    + "already uses DefaultPermissionMatrix.");
        }
        if (config.getDenialLedger() instanceof NoOpDenialLedger) {
            LOG.warn("DefaultAgentEngine wired with NoOpDenialLedger: "
                    + "denials are not counted and no sessions are paused on threshold — "
                    + "repeated security denials do not trigger autonomous-execution pause. "
                    + "To restore secure-by-default behaviour, do not pass a NoOpDenialLedger "
                    + "to setDenialLedger — the default already uses DefaultDenialLedger.");
        }
        if (config.getPostDenialGuard() instanceof PassThroughPostDenialGuard) {
            LOG.warn("DefaultAgentEngine wired with PassThroughPostDenialGuard: "
                    + "blind retries of denied actions are not detected or blocked — "
                    + "the agent can repeatedly attempt the same denied operation. "
                    + "To restore secure-by-default behaviour, do not pass a "
                    + "PassThroughPostDenialGuard to setPostDenialGuard — the default already "
                    + "uses DefaultPostDenialGuard.");
        }

        if (config.getContentGuardrail() instanceof NoOpContentGuardrail) {
            LOG.warn("DefaultAgentEngine constructed with NoOpContentGuardrail: "
                    + "No production implementation available for IContentGuardrail — "
                    + "content safety is not enforced. Provide a custom implementation "
                    + "via setContentGuardrail() for production use.");
        }
        if (config.getBudgetProvider() instanceof NoOpBudgetProvider) {
            LOG.info("DefaultAgentEngine constructed with NoOpBudgetProvider: "
                    + "No production implementation available for IBudgetProvider — "
                    + "execution budget is unlimited. Provide a custom implementation "
                    + "via setBudgetProvider() for production use.");
        }
    }
    public void warnIfNoOpUsageRecorder(DefaultAgentEngineConfig config) {
        if (!usageRecorderNoOpWarned && config.getUsageRecorder() instanceof NoOpUsageRecorder) {
            usageRecorderNoOpWarned = true;
            LOG.warn("DefaultAgentEngine wired with NoOpUsageRecorder: per-LLM-call usage data "
                    + "(token metering) is being discarded — usage is not observable. "
                    + "To make usage observable, wire a functional recorder via setUsageRecorder() "
                    + "(e.g. SimpleUsageRecorder for structured logs, DbUsageRecorder for persistence).");
        }
    }
}

