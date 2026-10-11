package io.nop.bytecode.kernel.cfg;

import java.util.List;

/**
 * Immutable instruction-level CFG for one method. Nodes are instruction indices into the
 * ASM {@code MethodNode.instructions} list (labels, line numbers and stack-map frames are
 * pass-through pseudo nodes that occupy indices too).
 *
 * <p>Edges: linear fallthrough, jump/switch targets, and exception-handler edges restricted
 * to the lexical try range of each handler (per design/nop-bytecode 00-overview §3.1 kernel
 * contract). JSR/RET are not supported (v61+ corpus never contains them); encountering them
 * is a loud {@link IllegalArgumentException} from the builder.
 */
public final class MethodCfg {
    private final int instructionCount;
    private final List<List<Integer>> successors;
    private final boolean[] handlerHead;

    public MethodCfg(int instructionCount, List<List<Integer>> successors, boolean[] handlerHead) {
        this.instructionCount = instructionCount;
        this.successors = successors;
        this.handlerHead = handlerHead;
    }

    public int instructionCount() {
        return instructionCount;
    }

    /** Successor instruction indices of the instruction at {@code index} (includes handler edges). */
    public List<Integer> successors(int index) {
        return successors.get(index);
    }

    /** Whether the instruction at {@code index} is an exception-handler entry. */
    public boolean isHandlerHead(int index) {
        return handlerHead[index];
    }
}
