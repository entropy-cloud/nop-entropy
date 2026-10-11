package io.nop.lint.core.semantic;

import java.nio.file.Path;

/**
 * The L2 type-resolution contract a profile's analyzer provides (design 06
 * §5.3 方案 A, roadmap item 20): program binding plus node type queries.
 * Implementations are lazy by contract (design 11 §3) —
 * {@link #isAvailable()} only probes the environment without starting
 * anything, and the real backend starts on the first query — and they fail
 * visibly: every failed query throws {@link TypeResolutionException}, never
 * a silent null or a syntactic guess (roadmap hard constraint: L2 is never
 * faked with L1 results).
 *
 * <p>Line and column follow the TypeScript wire convention documented in
 * design 06 §5.3: 0-based line and 0-based column in UTF-16 code units.</p>
 */
public interface TypeResolver {

    /**
     * The environment probe (design 11 §3 lazy principle): true when a query
     * could plausibly succeed. Must not start processes or do meaningful
     * work; the engine calls it per rule gate, so it must be cheap.
     */
    boolean isAvailable();

    /**
     * Binds (or refreshes) the project the queries run against. The
     * implementation rebuilds only when the project definition changed
     * (design 11 §4 tsconfig-hash invalidation).
     *
     * @throws TypeResolutionException when the project cannot be loaded
     */
    void initProject(Path tsConfigPath);

    /**
     * True when the node at the position (0-based line and column) has a
     * type assignable to {@code expectedType}, resolved in the project's own
     * checker context (the {@code isTypeAssignableTo} contract of design 06
     * §5.3).
     *
     * @throws TypeResolutionException when the query cannot be answered
     *                                 (unavailable backend, deadline, bad
     *                                 position) — the engine degrades the
     *                                 rule instead of guessing
     */
    boolean isAssignableTo(String filePath, int line, int col, String expectedType);

    /**
     * The rendered type of the node at the position (the {@code
     * getTypeAtLocation} contract of design 06 §5.3); a diagnostic-facing
     * convenience alongside {@link #isAssignableTo}.
     *
     * @throws TypeResolutionException when the query cannot be answered
     */
    String typeNameAt(String filePath, int line, int col);
}
