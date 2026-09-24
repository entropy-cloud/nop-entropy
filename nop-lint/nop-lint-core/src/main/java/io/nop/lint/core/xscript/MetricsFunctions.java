package io.nop.lint.core.xscript;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.SourcePositions;
import io.nop.lint.core.semantic.MetricsResolver;

/**
 * The {@code metrics} xscript binding (roadmap item 32): the three
 * method-metrics queries a deep-profile rule calls as
 * {@code metrics.cyclomatic(node)} / {@code metrics.cognitive(node)} /
 * {@code metrics.npath(node)}. The node argument is the match/capture
 * wrapper; its start position converts to the resolver's 0-based line /
 * UTF-16 column contract and the enclosing method's metrics come back.
 *
 * <p>Fail-closed: a query for a position with no enclosing method (the rule
 * matched a non-method node) throws — the runner's xscript failure path
 * skips the match and counts it, a zero is never faked. A null resolver
 * (the binding compiled but the run serves no provider — only reachable by
 * bypassing the engine's gate) surfaces the same way.</p>
 */
final class MetricsFunctions {

    private final MetricsResolver resolver;
    private final String filePath;
    private final byte[] source;

    MetricsFunctions(MetricsResolver resolver, String filePath, byte[] source) {
        this.resolver = resolver;
        this.filePath = filePath;
        this.source = source;
    }

    public int cyclomatic(Object nodeArg) {
        return resolver.cyclomatic(filePath, lineOf(nodeArg), colOf(nodeArg));
    }

    public int cognitive(Object nodeArg) {
        return resolver.cognitive(filePath, lineOf(nodeArg), colOf(nodeArg));
    }

    public long npath(Object nodeArg) {
        return resolver.npath(filePath, lineOf(nodeArg), colOf(nodeArg));
    }

    private int lineOf(Object nodeArg) {
        return positionOf(nodeArg).line();
    }

    private int colOf(Object nodeArg) {
        return positionOf(nodeArg).colUtf16();
    }

    private SourcePositions.LineCol positionOf(Object nodeArg) {
        if (!(nodeArg instanceof NodeWrapper wrapper)) {
            throw new NopLintException("metrics queries expect a node argument, got "
                    + (nodeArg == null ? "null" : nodeArg.getClass().getName()));
        }
        LintNode node = wrapper.unwrap();
        return SourcePositions.lineColUtf16(source, node.range().startByte());
    }
}
