package io.nop.lint.core.xscript;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.lint.core.NopLintException;

import java.util.Objects;

/**
 * Route A deadline wrapper (design 07 §4): the global
 * {@link IExpressionExecutor} delegate installed through the public
 * {@link EvalExprProvider#registerGlobalExecutor(IExpressionExecutor)}
 * extension point — zero platform change. Because every XLang executable
 * node (including each {@code while} loop back-edge and lambda body) executes
 * through the executor handed down from the evaluation root, installing this
 * wrapper yields a per-node interception point for lint scripts without any
 * compile-time instrumentation.
 *
 * <p>Semantics (design 07 §4, plan item 15):</p>
 * <ul>
 * <li><b>no deadline in scope</b> — verbatim passthrough to the executor
 * captured at install time: non-lint XLang evaluations keep their exact
 * pre-wrapper behavior, including exception semantics</li>
 * <li><b>deadline expired</b> — {@link XScriptTimeoutException}, aborting the
 * whole match (the runner treats it as a non-matching, separately counted
 * timeout)</li>
 * <li><b>evaluation depth</b> — nested executor entries within a deadline
 * scope are counted per thread; beyond {@value #MAX_EVAL_DEPTH} the script
 * aborts with an explicit failure (runaway recursion guard, design 07 §3).
 * Non-lint evaluations are never depth-limited.</li>
 * </ul>
 *
 * <p>Installation is idempotent: {@link #install()} wraps whatever executor
 * is current at call time (never hardwires
 * {@code DefaultExpressionExecutor}), so it composes with other wrappers
 * (e.g. the debugger executor) instead of replacing them.</p>
 */
public final class LintDeadlineExecutor implements IExpressionExecutor {

    /**
     * The maximum nesting depth of executor entries allowed inside one lint
     * script execution (design 07 §3 "调用深度上限 32").
     */
    public static final int MAX_EVAL_DEPTH = 32;

    private static final ThreadLocal<int[]> EVAL_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private final IExpressionExecutor delegate;

    LintDeadlineExecutor(IExpressionExecutor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * The executor this wrapper captured at install time and defers to.
     */
    public IExpressionExecutor delegate() {
        return delegate;
    }

    /**
     * Idempotently installs the wrapper as the global executor, delegating to
     * whatever executor is current at installation time. Safe to call before
     * every lint run: a second call observes the wrapper already installed
     * and does nothing (no wrapper stacking).
     */
    public static synchronized void install() {
        IExpressionExecutor current = EvalExprProvider.getGlobalExecutor();
        if (current instanceof LintDeadlineExecutor) {
            return;
        }
        EvalExprProvider.registerGlobalExecutor(new LintDeadlineExecutor(current));
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        IEvalScope scope = rt.getScope();
        Object deadline = scope.getValue(XScriptDeadline.VAR_DEADLINE_NANOS);
        if (deadline == null) {
            // No lint deadline in scope: verbatim passthrough. The delegate
            // threads itself down the expression tree exactly as the
            // pre-wrapper executor would, so non-lint evaluations keep their
            // exact semantics and are never depth-counted.
            return delegate.execute(expr, rt);
        }
        if (System.nanoTime() > ((Number) deadline).longValue()) {
            throw new XScriptTimeoutException(ruleIdOf(scope), budgetMsOf(scope));
        }
        int[] depth = EVAL_DEPTH.get();
        if (depth[0] >= MAX_EVAL_DEPTH) {
            throw new NopLintException("Rule '" + ruleIdOf(scope)
                    + "' xscript evaluation depth exceeded the limit of " + MAX_EVAL_DEPTH
                    + " nested executor calls (runaway recursion or expression nesting; fail-closed abort)");
        }
        depth[0]++;
        try {
            // Thread THIS wrapper down the expression tree (the platform
            // executor convention, cf. DefaultExpressionExecutor /
            // DebugExpressionExecutor): every nested executor.execute entry —
            // including each while-loop back edge — re-enters this check.
            return expr.execute(this, rt);
        } finally {
            depth[0]--;
        }
    }

    static String ruleIdOf(IEvalScope scope) {
        Object value = scope.getValue(XScriptDeadline.VAR_RULE_ID);
        return value instanceof String id ? id : "unknown";
    }

    static int budgetMsOf(IEvalScope scope) {
        Object value = scope.getValue(XScriptDeadline.VAR_BUDGET_MS);
        return value instanceof Number number ? number.intValue() : -1;
    }
}
