package io.nop.lint.core.semantic;

import java.util.List;

/**
 * The position-keyed scope-analysis query surface a run can serve (roadmap
 * item 33, design 05 §2 v1 面): the deep-profile {@code scope} xscript
 * binding resolves through it. Same shape as {@link MetricsResolver} — a
 * core interface, a language module's ServiceLoader implementation,
 * fail-closed gating ({@code requires: SCOPE} rules run only when a live
 * resolver serves a named file, and degrade otherwise).
 *
 * <p>Position contract (identical to {@link MetricsResolver}): 0-based line
 * and UTF-16 column. The no-definition answer is legitimate — {@code
 * definitionOf} returns -1 for a name the single unit does not declare;
 * only backend failures throw (the binding's failure path skips and counts,
 * a fabricated answer is never produced).</p>
 */
public interface ScopeResolver {

    /**
     * Cheap availability probe; must never start a backend.
     */
    boolean isAvailable();

    /**
     * The byte offset of the defining name for the identifier at the
     * position, or -1 when nothing in the unit declares it (fields count
     * order-independently; locals/params from their declaration point).
     */
    long definitionOf(String filePath, int line, int col);

    /**
     * The names declared directly in the innermost scope containing the
     * position.
     */
    List<String> declaredNames(String filePath, int line, int col);

    /**
     * The innermost scope kind at the position (class/method/block/catch/
     * lambda/for/switch/top).
     */
    String scopeKind(String filePath, int line, int col);

    /**
     * True when the declaration at the position shares its name with a
     * visible declaration in an enclosing scope (the shadowing judgment
     * does not presuppose legal Java).
     */
    boolean shadows(String filePath, int line, int col);
}
