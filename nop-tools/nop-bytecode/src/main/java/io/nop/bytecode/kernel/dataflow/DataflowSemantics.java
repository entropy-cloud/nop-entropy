package io.nop.bytecode.kernel.dataflow;

import org.objectweb.asm.tree.MethodNode;

/**
 * Lattice + transfer semantics owned by a concrete dataflow analysis (the kernel framework
 * understands no lattice values — per plan 03 callback contract). Implementations:
 *
 * <ul>
 *   <li>{@link #initialFrame} — client-built initial frame (this = its own semantics, reference
 *       params, cat2 slot widths); the solver only consumes it.</li>
 *   <li>{@link #transfer} — abstract interpretation of one instruction (pops/pushes, records
 *       findings through client state); must leave the frame at post-instruction state.</li>
 *   <li>{@link #edgeFrame} — per-successor out-frame: this is where branch-sensitive refinement
 *       happens (e.g. IFNULL/IFNONNULL refining a local). The default handler-entry shape
 *       (stack cleared to base + pushed exception reference) is also client knowledge and is
 *       signalled via {@code targetIsHandlerHead}.</li>
 *   <li>{@link #merge} / {@link #topValue} — lattice algebra (merge(null, x) = x by convention
 *       for first-touch joins).</li>
 * </ul>
 */
public interface DataflowSemantics {

    Frame initialFrame(MethodNode mn);

    /** Abstract interpretation of one instruction; may record findings via client state. */
    void transfer(Frame frame, int insnIndex);

    /**
     * Per-successor out-frame after {@link #transfer}: implementations mutate {@code edgeFrame}
     * in place (branch refinement; handler-entry shape when {@code targetIsHandlerHead}).
     * Returns whether the edge is reachable — {@code false} drops the edge (used by the
     * nullness analysis to model "assertions always enabled": the $assertionsDisabled bypass
     * branch is dropped so assert guards hold regardless of runtime instrumentation).
     */
    boolean edgeFrame(Frame edgeFrame, int insnIndex, int target, boolean targetIsHandlerHead);

    /** Lattice merge; null means "no information yet" (first touch). */
    Object merge(Object a, Object b);

    /** Second-slot placeholder value for category-2 widths. */
    Object topValue();
}
