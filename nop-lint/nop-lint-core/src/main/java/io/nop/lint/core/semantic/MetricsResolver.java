package io.nop.lint.core.semantic;

/**
 * The position-keyed method-metrics query surface a run can serve (roadmap
 * item 32): the deep-profile {@code metrics} xscript binding resolves
 * through it. Same shape as {@link TypeResolver}: a core interface, a
 * language module's implementation (ServiceLoader-discovered for CLI /
 * RuleTestRunner default wiring, explicit construction for tests), and a
 * fail-closed gate — rules declaring {@code requires: METRICS} run only
 * when a live resolver serves a named file, and degrade otherwise (never a
 * faked answer).
 *
 * <p>Position contract (identical to {@link TypeResolver}): {@code line}
 * and {@code col} are 0-based, the column in UTF-16 code units — the
 * {@code SourcePositions.lineColUtf16} output the binding hands over.
 * Implementations resolve the enclosing method of that position and fail
 * loudly (never return 0) when the position has no enclosing method.</p>
 */
public interface MetricsResolver {

    /**
     * Cheap availability probe; must never start a backend (the
     * {@code TypeResolver.isAvailable()} discipline, design 11 §3).
     */
    boolean isAvailable();

    /**
     * The cyclomatic complexity (decision points + 1) of the method
     * enclosing the position.
     *
     * @throws io.nop.lint.core.NopLintException when the position has no
     *                                           enclosing method (a script
     *                                           asking for metrics of a
     *                                           non-method node is a rule
     *                                           defect, not a zero)
     */
    int cyclomatic(String filePath, int line, int col);

    /**
     * The cognitive complexity (SonarSource increment table) of the method
     * enclosing the position; same failure contract as {@link #cyclomatic}.
     */
    int cognitive(String filePath, int line, int col);

    /**
     * The NPath complexity (path-count product) of the method enclosing the
     * position; same failure contract as {@link #cyclomatic}.
     */
    long npath(String filePath, int line, int col);
}
