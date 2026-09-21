package io.nop.lint.core.xscript;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.type.DeclTypeResolver;
import io.nop.xlang.api.XLang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Executes one rule's compiled xscript for a single pattern match (design 07
 * §1, §2). The script was compiled once by {@link XScriptCompiler}; each
 * match gets a fresh child eval scope with the v1 context bindings:
 * {@code node} (the matched node), {@code captures} (single nodes and
 * sequences from the match environment), {@code report} (the diagnostic
 * callback) and {@code declType} (the L1 declaration-type query, design 06
 * §5.2). A script that never calls {@code report} yields no diagnostic for
 * that match — xscript doubles as a post-match filter.
 *
 * <p>Resource bounds (design 07 §3): the per-match diagnostic count is
 * capped — the cap aborts the script while keeping the diagnostics already
 * reported, and the abort surfaces to the caller through
 * {@link MatchOutcome#capped()} for counting. Script failures are not
 * handled here: they propagate to the rule runner, which owns the skip,
 * warning-count and consecutive-failure-disable semantics.</p>
 *
 * <p>Deadline enforcement (design 07 §3, §4 route A) is injected per match:
 * when the caller supplies an {@link XScriptDeadline}, its values are written
 * into the match scope before the script runs and the globally installed
 * {@link LintDeadlineExecutor} aborts an expired match with
 * {@link XScriptTimeoutException}, which propagates to the runner exactly
 * like any other script failure — the runner owns the timeout-vs-failure
 * distinction. Matches executed without a deadline are unbounded, exactly as
 * before.</p>
 */
public final class XScriptEngine {

    /**
     * The default per-match diagnostic cap (design 07 §3).
     */
    public static final int DEFAULT_MAX_DIAGNOSTICS_PER_MATCH = 100;

    private static final Set<String> SEVERITIES = Set.of("error", "warning", "info", "hint");

    private final String ruleId;
    private final String severity;
    private final IEvalAction action;
    private final int maxDiagnosticsPerMatch;
    private final DeclTypeResolver declTypeResolver = new DeclTypeResolver();

    /**
     * Creates the per-rule executor.
     *
     * @param ruleId   the owning rule's id (carried on every diagnostic)
     * @param severity the rule's declared severity (report() default)
     * @param action   the compiled xscript body
     */
    public XScriptEngine(String ruleId, String severity, IEvalAction action) {
        this(ruleId, severity, action, DEFAULT_MAX_DIAGNOSTICS_PER_MATCH);
    }

    /**
     * Creates the per-rule executor with an explicit per-match diagnostic
     * cap (the test seam for the resource bound).
     */
    public XScriptEngine(String ruleId, String severity, IEvalAction action, int maxDiagnosticsPerMatch) {
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        if (maxDiagnosticsPerMatch <= 0) {
            throw new NopLintException("Rule '" + ruleId
                    + "' has a non-positive xscript diagnostic limit: " + maxDiagnosticsPerMatch);
        }
        this.maxDiagnosticsPerMatch = maxDiagnosticsPerMatch;
    }

    /**
     * The owning rule's id.
     */
    public String ruleId() {
        return ruleId;
    }

    /**
     * The per-match diagnostic cap this engine enforces.
     */
    public int maxDiagnosticsPerMatch() {
        return maxDiagnosticsPerMatch;
    }

    /**
     * Runs the script for one match without a deadline (the direct-engine
     * entry used by tests and callers that own their own bounds).
     */
    public MatchOutcome executeMatch(LintNode matchNode, MetaVarEnv env, SourceMap sourceMap) {
        return executeMatch(matchNode, env, sourceMap, null);
    }

    /**
     * Runs the script for one match under an explicit deadline. The deadline
     * is injected as scope-local values (host-side keys that are not in the
     * compile whitelist, so scripts cannot read or alter them); expiry aborts
     * through the installed {@link LintDeadlineExecutor} with
     * {@link XScriptTimeoutException}.
     *
     * @param deadline the match's deadline budget, or null for no enforcement
     * @throws NopException            when the script fails (bad report
     *                                 arguments, null navigation, runtime
     *                                 faults) — the caller applies the
     *                                 skip-and-count semantics
     * @throws XScriptTimeoutException when the deadline expired before or
     *                                 during the script run
     */
    public MatchOutcome executeMatch(LintNode matchNode, MetaVarEnv env, SourceMap sourceMap, XScriptDeadline deadline) {
        Objects.requireNonNull(matchNode, "matchNode must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(sourceMap, "sourceMap must not be null");

        NodeWrapper nodeWrapper = new NodeWrapper(matchNode, sourceMap);
        Map<String, Object> captures = buildCaptures(env, sourceMap);
        ReportFunction report = new ReportFunction(nodeWrapper, captures);

        IEvalScope rootScope = XLang.newEvalScope();
        IEvalScope scope = rootScope.newChildScope();
        scope.setLocalValue(XScriptCompiler.VAR_NODE, nodeWrapper);
        scope.setLocalValue(XScriptCompiler.VAR_CAPTURES, captures);
        scope.setLocalValue(XScriptCompiler.VAR_REPORT, report);
        scope.setLocalValue(XScriptCompiler.VAR_DECL_TYPE,
                (Function<Object, Object>) this::resolveDeclType);
        if (deadline != null) {
            XScriptDeadline.inject(scope, deadline);
        }

        try {
            action.invoke(scope);
        } catch (Exception e) {
            if (!report.isCapped()) {
                // The eval machinery wraps script failures before they leave
                // the call boundary — hand the original failure back to the
                // runner (which owns the skip-and-count semantics).
                if (e instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new NopLintException("Rule '" + ruleId + "' xscript failed: " + e.getMessage(), e);
            }
            // The per-match diagnostic cap aborted the script (possibly after
            // being wrapped by the eval machinery): the already reported
            // diagnostics stand, and the abort is surfaced through
            // MatchOutcome.capped() for counting.
        }
        return new MatchOutcome(report.diagnostics(), report.isCapped());
    }

    /**
     * The capture bindings for one match: single captures wrap as one
     * {@link NodeWrapper}, sequence captures as an ordered list. A name bound
     * both ways resolves to the sequence (the list carries strictly more
     * information; design 04 §2 capture shapes).
     */
    private Map<String, Object> buildCaptures(MetaVarEnv env, SourceMap sourceMap) {
        Map<String, Object> captures = new LinkedHashMap<>();
        for (Map.Entry<String, LintNode> entry : env.singleCaptures().entrySet()) {
            captures.put(entry.getKey(), new NodeWrapper(entry.getValue(), sourceMap));
        }
        for (Map.Entry<String, List<LintNode>> entry : env.multiCaptures().entrySet()) {
            List<NodeWrapper> wrappers = new ArrayList<>(entry.getValue().size());
            for (LintNode captured : entry.getValue()) {
                wrappers.add(new NodeWrapper(captured, sourceMap));
            }
            captures.put(entry.getKey(), wrappers);
        }
        return captures;
    }

    private Object resolveDeclType(Object argument) {
        if (argument == null) {
            return null;
        }
        if (!(argument instanceof NodeWrapper wrapper)) {
            throw new NopLintException("Rule '" + ruleId + "': declType() expects a node argument, got "
                    + argument.getClass().getName());
        }
        return declTypeResolver.resolveDeclType(wrapper.unwrap());
    }

    /**
     * One match's script result: the reported diagnostics plus whether the
     * diagnostic cap aborted the script.
     */
    public record MatchOutcome(List<Diagnostic> diagnostics, boolean capped) {

        public MatchOutcome {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /**
     * The {@code report(diag)} binding: collects one diagnostic per call
     * under the design 07 §2.3 argument contract, and aborts the script once
     * the per-match diagnostic cap is reached. Every contract violation
     * throws — report() never silently drops a diagnostic.
     */
    private final class ReportFunction implements Consumer<Object> {

        private final NodeWrapper matchWrapper;
        private final Map<String, Object> captures;
        private final List<Diagnostic> reported = new ArrayList<>();
        private boolean capped;

        ReportFunction(NodeWrapper matchWrapper, Map<String, Object> captures) {
            this.matchWrapper = matchWrapper;
            this.captures = captures;
        }

        List<Diagnostic> diagnostics() {
            return reported;
        }

        boolean isCapped() {
            return capped;
        }

        @Override
        public void accept(Object argument) {
            if (!(argument instanceof Map<?, ?> spec)) {
                throw new NopLintException("Rule '" + ruleId + "': report() requires an object "
                        + "argument with a 'message' field");
            }
            if (spec.get("fix") != null) {
                throw new NopLintException("Rule '" + ruleId + "': report({fix}) is not supported in this "
                        + "engine version (deferred to roadmap item 25); the field is rejected instead of "
                        + "being ignored");
            }
            if (!(spec.get("message") instanceof String message) || message.isBlank()) {
                throw new NopLintException("Rule '" + ruleId
                        + "': report() requires a non-blank 'message' string");
            }
            String diagnosticSeverity = resolveSeverity(spec);
            LintNode target = resolveTarget(spec);
            reported.add(new Diagnostic(ruleId, diagnosticSeverity, message, target.range()));
            if (reported.size() >= maxDiagnosticsPerMatch) {
                capped = true;
                throw new DiagnosticCapReached();
            }
        }

        private String resolveSeverity(Map<?, ?> spec) {
            Object value = spec.get("severity");
            if (value == null) {
                return severity;
            }
            if (!(value instanceof String candidate) || !SEVERITIES.contains(candidate)) {
                throw new NopLintException("Rule '" + ruleId + "': report() severity must be one of "
                        + "error|warning|info|hint, got: " + value);
            }
            return candidate;
        }

        private LintNode resolveTarget(Map<?, ?> spec) {
            // design 07 §2.3: an explicit capture overrides the node target
            Object capture = spec.get("capture");
            if (capture != null) {
                return resolveCaptureTarget(capture);
            }
            Object node = spec.get("node");
            if (node instanceof NodeWrapper wrapper) {
                return wrapper.unwrap();
            }
            if (node != null) {
                throw new NopLintException("Rule '" + ruleId + "': report() 'node' must be a node "
                        + "(script nodes arrive as NodeWrapper values)");
            }
            return matchWrapper.unwrap();
        }

        private LintNode resolveCaptureTarget(Object capture) {
            if (!(capture instanceof String name)) {
                throw new NopLintException("Rule '" + ruleId
                        + "': report() 'capture' must be a capture name string");
            }
            Object bound = captures.get(name);
            if (bound == null) {
                throw new NopLintException("Rule '" + ruleId + "': report() references unknown capture '"
                        + name + "'");
            }
            if (bound instanceof NodeWrapper wrapper) {
                return wrapper.unwrap();
            }
            List<?> sequence = (List<?>) bound;
            if (sequence.isEmpty()) {
                throw new NopLintException("Rule '" + ruleId + "': report() capture '" + name
                        + "' matched zero nodes, so it cannot be a diagnostic target");
            }
            return ((NodeWrapper) sequence.get(0)).unwrap();
        }
    }

    private static final class DiagnosticCapReached extends RuntimeException {
        private DiagnosticCapReached() {
            super("diagnostic cap reached");
            super.setStackTrace(new StackTraceElement[0]);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
