package io.nop.lint.core.semantic;

/**
 * The position-keyed L4 semantic query surface a run can serve (roadmap
 * item 34, design 06 §4.5 contract names): the deep-profile {@code
 * semantic} xscript binding resolves through it. Same shape as the other
 * deep resolvers — 0-based line / UTF-16 column, named file, availability
 * probe, and the fail-closed contract: a type the backend cannot resolve is
 * an exception (skip-and-count), never a fabricated false.
 */
public interface SemanticResolver {

    /**
     * Cheap availability probe; must never start a backend.
     */
    boolean isAvailable();

    /**
     * True when the type declaration at the position implements the named
     * interface (directly, via a superclass chain, or via superinterfaces;
     * erased-name comparison with the FQN/imports/java.lang normalization).
     */
    boolean implementsInterface(String filePath, int line, int col, String interfaceName);

    /**
     * True when the method at the position cannot be overridden (final/
     * static/private modifiers or a final enclosing class).
     */
    boolean isOverridable(String filePath, int line, int col);

    /**
     * True when the call at the position resolves to a whitelisted logger
     * receiver and a log-family method name.
     */
    boolean isLoggerCall(String filePath, int line, int col);
}
