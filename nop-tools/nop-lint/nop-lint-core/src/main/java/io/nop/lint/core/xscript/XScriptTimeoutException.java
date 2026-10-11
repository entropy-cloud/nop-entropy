package io.nop.lint.core.xscript;

import io.nop.lint.core.NopLintException;

/**
 * Thrown by {@link LintDeadlineExecutor} when a lint script's match deadline
 * expires: the explicit abort signal of design 07 §3/§4 route A. Extends the
 * module exception so the eval machinery preserves the instance through
 * function-call wrapping, letting {@code RuleSetRunner} separate timeouts
 * from ordinary script failures (a timeout match is treated as non-matching
 * and never feeds the consecutive-failure disable path).
 *
 * <p>Stack traces are suppressed: expiry position is wherever the script
 * happened to be, which carries no diagnostic value — the message names the
 * rule and its budget.</p>
 */
public class XScriptTimeoutException extends NopLintException {

    private final String ruleId;
    private final int budgetMs;

    public XScriptTimeoutException(String ruleId, int budgetMs) {
        super("Rule '" + ruleId + "' xscript execution exceeded its deadline budget of " + budgetMs
                + "ms; the match is treated as non-matching and counted as a timeout");
        this.ruleId = ruleId;
        this.budgetMs = budgetMs;
    }

    /**
     * The rule whose script was aborted.
     */
    public String ruleId() {
        return ruleId;
    }

    /**
     * The budget the script exceeded, in milliseconds.
     */
    public int budgetMs() {
        return budgetMs;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
