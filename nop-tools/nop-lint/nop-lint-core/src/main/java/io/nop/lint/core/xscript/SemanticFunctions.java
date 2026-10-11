package io.nop.lint.core.xscript;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.SourcePositions;
import io.nop.lint.core.semantic.SemanticResolver;

/**
 * The {@code semantic} xscript binding (roadmap item 34): the L4 semantic
 * queries a deep-profile rule calls as {@code semantic.implementsInterface(
 * node, name)} / {@code semantic.isOverridable(node)} / {@code
 * semantic.isLoggerCall(node)}. The node argument is the match/capture
 * wrapper; its start position converts to the resolver's 0-based line /
 * UTF-16 column contract. Failures surface through the xscript failure path
 * (skip and count) — a fabricated false is never produced.
 */
final class SemanticFunctions {

    private final SemanticResolver resolver;
    private final String filePath;
    private final byte[] source;

    SemanticFunctions(SemanticResolver resolver, String filePath, byte[] source) {
        this.resolver = resolver;
        this.filePath = filePath;
        this.source = source;
    }

    public boolean implementsInterface(Object nodeArg, String interfaceName) {
        int[] at = positionOf(nodeArg);
        return resolver.implementsInterface(filePath, at[0], at[1], interfaceName);
    }

    public boolean isOverridable(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.isOverridable(filePath, at[0], at[1]);
    }

    public boolean isLoggerCall(Object nodeArg) {
        int[] at = positionOf(nodeArg);
        return resolver.isLoggerCall(filePath, at[0], at[1]);
    }

    private int[] positionOf(Object nodeArg) {
        if (!(nodeArg instanceof NodeWrapper wrapper)) {
            throw new NopLintException("semantic queries expect a node argument, got "
                    + (nodeArg == null ? "null" : nodeArg.getClass().getName()));
        }
        LintNode node = wrapper.unwrap();
        io.nop.lint.core.node.SourcePositions.LineCol pos =
                SourcePositions.lineColUtf16(source, node.range().startByte());
        return new int[]{pos.line(), pos.colUtf16()};
    }
}
