package io.nop.lint.core.xscript;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.lint.core.NopLintException;

import java.util.concurrent.TimeUnit;

/**
 * One match's deadline budget (design 07 §3, §4 route A): the immutable
 * value the rule runner computes before a script runs, injected by
 * {@link XScriptEngine} as scope-local values and checked by
 * {@link LintDeadlineExecutor} at every expression-node entry.
 *
 * <p>The scope-local keys are host-side injection channels, deliberately
 * <em>not</em> registered in the {@link XScriptCompiler} compile whitelist:
 * a script referencing them fails compilation with an unresolved identifier,
 * so script code can neither read nor tamper with its own deadline.</p>
 *
 * @param ruleId        the owning rule's id (carried into timeout reporting)
 * @param budgetMs      the per-match budget in milliseconds (profile-scaled,
 *                      always >= 1)
 * @param deadlineNanos {@code System.nanoTime()} value at which the match is
 *                      aborted
 */
public record XScriptDeadline(String ruleId, int budgetMs, long deadlineNanos) {

    /**
     * Scope-local key holding the absolute {@link System#nanoTime()} deadline.
     */
    public static final String VAR_DEADLINE_NANOS = "__lintDeadlineNanos";

    /**
     * Scope-local key holding the owning rule's id (timeout reporting only).
     */
    public static final String VAR_RULE_ID = "__lintRuleId";

    /**
     * Scope-local key holding the budget in milliseconds (reporting only).
     */
    public static final String VAR_BUDGET_MS = "__lintBudgetMs";

    public XScriptDeadline {
        if (ruleId == null || ruleId.isBlank())
            throw new NopLintException("An xscript deadline requires the owning rule's id");
        if (budgetMs <= 0)
            throw new NopLintException("Rule '" + ruleId + "' has a non-positive xscript deadline budget: "
                    + budgetMs + "ms (a budget below 1ms can never run)");
    }

    /**
     * A deadline for a match starting now.
     *
     * @param ruleId   the owning rule's id
     * @param budgetMs the per-match budget in milliseconds (>= 1)
     */
    public static XScriptDeadline startNow(String ruleId, int budgetMs) {
        return new XScriptDeadline(ruleId, budgetMs,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs));
    }

    /**
     * Writes the deadline into the match's eval scope (host-side, after the
     * scope is built and before the compiled action is invoked).
     */
    static void inject(IEvalScope scope, XScriptDeadline deadline) {
        scope.setLocalValue(VAR_DEADLINE_NANOS, deadline.deadlineNanos());
        scope.setLocalValue(VAR_RULE_ID, deadline.ruleId());
        scope.setLocalValue(VAR_BUDGET_MS, deadline.budgetMs());
    }
}
