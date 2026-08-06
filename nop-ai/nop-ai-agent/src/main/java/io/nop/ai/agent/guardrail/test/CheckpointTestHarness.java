package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.conflict.FailFastStrategy;
import io.nop.ai.agent.conflict.InMemoryWriteIntentRegistry;
import io.nop.ai.agent.conflict.WriteIntent;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentHookInvoker;
import io.nop.ai.agent.engine.AgentSecurityConsultation;
import io.nop.ai.agent.engine.AgentToolPlanResolver;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.DefaultApprovalGate;
import io.nop.ai.agent.security.DefaultDenialLedger;
import io.nop.ai.agent.security.DefaultPathAccessChecker;
import io.nop.ai.agent.security.DefaultPermissionMatrix;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.DefaultSecurityLevelResolver;
import io.nop.ai.agent.security.DefaultToolAccessChecker;
import io.nop.ai.agent.security.SecurityCheckpoint;
import io.nop.ai.agent.security.SecurityCheckpointChain;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Data-driven harness that drives the full 7-checkpoint
 * {@link SecurityCheckpointChain} for a single declared
 * {@link CheckpointTestCase} (design {@code guardrail-contract.md} §增量 4,
 * 裁定 C).
 *
 * <p>For each case the harness:
 * <ol>
 *   <li>builds a <b>fresh</b> {@link AgentSecurityConsultation} with the shipped
 *       {@code Default*} components (matching the {@code DefaultAgentEngine}
 *       defaults) plus a {@link CollectingAuditLogger} side channel — so every
 *       case starts from a clean ledger / post-denial-guard / registry state
 *       (no cross-case state leakage);</li>
 *   <li>constructs a minimal {@link AgentModel} (workDir only) +
 *       {@link AgentExecutionContext} (channelKind + principal) +
 *       {@link ChatToolCall} from the case;</li>
 *   <li>calls the <b>real</b> {@code buildCheckpointChain()} then
 *       {@code chain.evaluate(ctx)} (no mocking);</li>
 *   <li>recovers the denying checkpoint's {@code matchedRule} from the
 *       collecting audit logger and produces a {@link CheckpointTestResult}.</li>
 * </ol>
 *
 * <p><b>No silent no-op</b> (Minimum Rules #24): if {@code buildCheckpointChain()}
 * returns null or throws, the harness fails loud (a wiring bug must be fixed,
 * not buried). If {@code evaluate()} throws, the case is recorded as a
 * <em>failed</em> result carrying the exception message — never silently
 * skipped.
 *
 * <p>This is a <b>test-time</b> component: it never invokes a real LLM or tool
 * executor. The checkpoint chain is pure decision logic over a tool call + static
 * security configuration.
 */
public class CheckpointTestHarness {

    private static final Logger LOG = LoggerFactory.getLogger(CheckpointTestHarness.class);

    private static final String DEFAULT_AGENT_NAME = "checkpoint-test-agent";

    private final IToolManager stubToolManager;

    public CheckpointTestHarness() {
        this(new NoOpToolManager());
    }

    public CheckpointTestHarness(IToolManager stubToolManager) {
        this.stubToolManager = stubToolManager != null ? stubToolManager : new NoOpToolManager();
    }

    /**
     * Run a single case through a freshly-built consultation + chain.
     *
     * @return the per-case result (never {@code null})
     * @throws NopAiAgentException if the consultation/chain cannot be assembled
     *         (a wiring bug — fail fast rather than producing a misleading result)
     */
    public CheckpointTestResult runCase(CheckpointTestCase testCase) {
        if (testCase == null) {
            throw new NopAiAgentException("CheckpointTestHarness.runCase: testCase must not be null");
        }
        CollectingAuditLogger auditLogger = new CollectingAuditLogger();
        InMemoryWriteIntentRegistry registry = new InMemoryWriteIntentRegistry();

        AgentModel agentModel = new AgentModel();
        if (testCase.getWorkDir() != null && !testCase.getWorkDir().trim().isEmpty()) {
            agentModel.setWorkDir(testCase.getWorkDir());
        }

        // Seed the write-intent registry BEFORE building the consultation so the
        // conflict checkpoint observes the pre-populated cross-session intent.
        if (testCase.hasConflictSeed()) {
            seedConflict(registry, testCase, agentModel);
        }

        AgentSecurityConsultation consultation = buildDefaultConsultation(registry, auditLogger);

        SecurityCheckpointChain chain;
        try {
            chain = consultation.buildCheckpointChain();
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "CheckpointTestHarness: buildCheckpointChain() failed for case "
                            + testCase.getId() + " (consultation wiring error)", e);
        }
        if (chain == null) {
            throw new NopAiAgentException(
                    "CheckpointTestHarness: buildCheckpointChain() returned null for case " + testCase.getId());
        }

        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, testCase.getSessionId());
        if (testCase.getChannelKind() != null) {
            ctx.setChannelKind(testCase.getChannelKind());
        }
        if (testCase.getPrincipal() != null) {
            ctx.setPrincipal(testCase.getPrincipal());
        }

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId(testCase.getId());
        toolCall.setName(testCase.getToolName());
        toolCall.setArguments(testCase.getArgs());

        String fingerprintWorkDir = resolveFingerprintWorkDir(testCase);

        SecurityCheckpoint.CheckContext checkCtx = SecurityCheckpoint.CheckContext.create(
                testCase.getSessionId(), DEFAULT_AGENT_NAME, toolCall, ctx,
                fingerprintWorkDir, agentModel);

        SecurityCheckpoint.Decision actualDecision;
        try {
            actualDecision = chain.evaluate(checkCtx);
        } catch (Exception e) {
            // No silent skip: record the case as a failed result carrying the
            // exception summary so the report surfaces it, and log the full
            // exception (with stack trace) so the failure is debuggable
            // without aborting the whole batch. Use e.toString() (not
            // getMessage) for the report line; the stack trace goes to the log.
            LOG.error("CheckpointTestHarness: evaluate() threw for case {}", testCase.getId(), e);
            return new CheckpointTestResult(testCase, null, null, false,
                    "evaluate() threw: " + e.toString());
        }

        String actualMatchedRule = auditLogger.firstDenyMatchedRule();
        return evaluate(testCase, actualDecision, actualMatchedRule);
    }

    /**
     * Assemble an {@link AgentSecurityConsultation} with the shipped
     * {@code Default*} components (裁定 C). The {@code registry} and
     * {@code auditLogger} are injected so each case gets fresh, isolated
     * instances and the harness can read the captured audit events.
     */
    public AgentSecurityConsultation buildDefaultConsultation(
            InMemoryWriteIntentRegistry registry, CollectingAuditLogger auditLogger) {
        if (registry == null) {
            throw new NopAiAgentException("CheckpointTestHarness: registry must not be null");
        }
        if (auditLogger == null) {
            throw new NopAiAgentException("CheckpointTestHarness: auditLogger must not be null");
        }
        return new AgentSecurityConsultation(
                new DefaultPostDenialGuard(),
                auditLogger,
                new DefaultToolAccessChecker(),
                new AllowAllPermissionProvider(),
                new DefaultPathAccessChecker(),
                new DefaultSecurityLevelResolver(),
                new DefaultPermissionMatrix(),
                new DefaultApprovalGate(),
                new DefaultDenialLedger(),
                FailFastStrategy.failFast(),
                registry,
                new AgentToolPlanResolver(stubToolManager),
                new AgentHookInvoker(new DefaultHookRegistry(), null));
    }

    private void seedConflict(InMemoryWriteIntentRegistry registry, CheckpointTestCase testCase,
                              AgentModel agentModel) {
        // Compute the normalized registry key the exact same way the
        // consultation's checkWriteConflict will look it up, so the seeded
        // intent is observed as a real cross-session conflict.
        AgentToolPlanResolver resolver = new AgentToolPlanResolver(stubToolManager);
        File agentWorkDir = resolver.resolveWorkDir(agentModel);
        File baseDir = agentWorkDir != null ? agentWorkDir : new File(".").getAbsoluteFile();
        String absolute = AgentSecurityConsultation.resolveAbsolute(testCase.getPrePopConflictPath(), baseDir);
        String normalized = DefaultPathAccessChecker.normalizePathStatic(absolute);
        if (normalized == null) {
            // Fall back to the absolute path so a non-normalizable seed still
            // registers (the conflict check skips non-normalizable paths, so
            // such a case would simply not conflict — surfaced by the result).
            normalized = absolute;
        }
        String otherSession = testCase.getPrePopConflictSession() != null
                ? testCase.getPrePopConflictSession()
                : "other-session";
        registry.registerAndGetConflicting(new WriteIntent(
                otherSession, "other-agent", normalized, testCase.getToolName(),
                System.currentTimeMillis()));
    }

    private static String resolveFingerprintWorkDir(CheckpointTestCase testCase) {
        String wd = testCase.getWorkDir();
        return (wd != null && !wd.trim().isEmpty()) ? wd : null;
    }

    private static CheckpointTestResult evaluate(CheckpointTestCase testCase,
                                                 SecurityCheckpoint.Decision actualDecision,
                                                 String actualMatchedRule) {
        SecurityCheckpoint.Decision expected = testCase.getExpectedDecision();
        boolean decisionMatches = actualDecision == expected;
        String expectedRule = testCase.getExpectedMatchedRule();
        boolean ruleMatches = expectedRule == null || expectedRule.equals(actualMatchedRule);
        boolean passed = decisionMatches && ruleMatches;

        if (passed) {
            return new CheckpointTestResult(testCase, actualDecision, actualMatchedRule, true, null);
        }
        StringBuilder sb = new StringBuilder();
        if (!decisionMatches) {
            sb.append("decision mismatch: expected ").append(expected)
                    .append(" but got ").append(actualDecision);
        }
        if (!ruleMatches) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("matchedRule mismatch: expected '").append(expectedRule)
                    .append("' but got '").append(actualMatchedRule).append("'");
        }
        return new CheckpointTestResult(testCase, actualDecision, actualMatchedRule, false, sb.toString());
    }

    /**
     * Minimal {@link IToolManager} stub: the checkpoint chain never executes a
     * tool (it only decides ALLOW/DENY), so the stub just returns empty
     * discovery and a no-op execute result. Matches the stub pattern in
     * {@code TestConflictDetectionDispatchPath.stubToolManager()} and
     * {@code TestEngineExtractedSecurityAndDispatch.StubToolManager}.
     */
    static final class NoOpToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "noop"));
        }

        @Override
        public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls,
                                                                IToolExecuteContext context) {
            return CompletableFuture.completedFuture(new AiToolCallsResponse());
        }

        @Override
        public List<AiToolModel> listTools() {
            return Collections.emptyList();
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return null;
        }
    }
}
