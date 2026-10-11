package io.nop.lint.core.xscript;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.SourcePositions;
import io.nop.lint.core.semantic.DataflowResolver;

/**
 * The {@code dataflow} xscript binding (roadmap item 34): the L3 dataflow
 * queries a deep-profile rule calls as {@code dataflow.constantValue(node)} /
 * {@code dataflow.useCount(node)} / {@code dataflow.isSelfAssigned(node)}.
 * The node argument is the match/capture wrapper positioned on a
 * declaration's name; the constant answer is nullable (the legitimate
 * "not a constant" result). Failures surface through the xscript failure
 * path (skip and count).
 */
final class DataflowFunctions {

    private final DataflowResolver resolver;
    private final String filePath;
    private final byte[] source;

    DataflowFunctions(DataflowResolver resolver, String filePath, byte[] source) {
        this.resolver = resolver;
        this.filePath = filePath;
        this.source = source;
    }

    public String constantValue(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.constantValue(filePath, at[0], at[1]);
    }

    public long useCount(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.useCount(filePath, at[0], at[1]);
    }

    public boolean isSelfAssigned(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.isSelfAssigned(filePath, at[0], at[1]);
    }

    private int[] positionOf(Object nodeArg) {
        if (!(nodeArg instanceof NodeWrapper wrapper)) {
            throw new NopLintException("dataflow queries expect a node argument, got "
                    + (nodeArg == null ? "null" : nodeArg.getClass().getName()));
        }
        LintNode node = wrapper.unwrap();
        io.nop.lint.core.node.SourcePositions.LineCol pos =
                SourcePositions.lineColUtf16(source, node.range().startByte());
        return new int[]{pos.line(), pos.colUtf16()};
    }
}
