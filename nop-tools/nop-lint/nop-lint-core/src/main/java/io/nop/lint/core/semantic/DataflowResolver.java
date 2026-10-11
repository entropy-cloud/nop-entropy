package io.nop.lint.core.semantic;

/**
 * The position-keyed L3 dataflow query surface a run can serve (roadmap
 * item 34, design 06 §4.4 consumer surface): the deep-profile {@code
 * dataflow} xscript binding resolves through it. Same shape as the other
 * deep resolvers; the query position must sit on a local/parameter
 * declaration's name (fields and method-outside declarations are not the v1
 * surface — they throw), and "not a constant" is the legitimate null
 * answer, never a failure.
 */
public interface DataflowResolver {

    /**
     * Cheap availability probe; must never start a backend.
     */
    boolean isAvailable();

    /**
     * The compile-time constant value of the variable declared at the
     * position, or null when it is not a constant.
     */
    String constantValue(String filePath, int line, int col);

    /**
     * The number of use sites of the variable declared at the position.
     */
    long useCount(String filePath, int line, int col);

    /**
     * True when the variable declared at the position has the v1
     * {@code x = x} self-assignment form.
     */
    boolean isSelfAssigned(String filePath, int line, int col);
}
