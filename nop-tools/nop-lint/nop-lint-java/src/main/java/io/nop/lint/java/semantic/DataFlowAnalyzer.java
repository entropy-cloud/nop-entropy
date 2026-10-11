package io.nop.lint.java.semantic;

import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * The dataflow analysis facade (design 06 §4.4 contract names, roadmap item
 * 30): intra-procedural definition-use chains and constant propagation over
 * a method's JavaParser AST. Flow-insensitive by v1 adjudication — the
 * v1 surface covers local-variable and parameter analysis; field-level,
 * path-sensitive and cross-procedural analysis are the successor surface.
 */
public final class DataFlowAnalyzer {

    /**
     * Design 06 §4.4: builds the definition-use chain of a method.
     */
    public DefUseChain buildDefUseChain(MethodDeclaration method) {
        return DefUseChain.build(method);
    }

    /**
     * Design 06 §4.4: propagates compile-time constants through a method's
     * local variables.
     */
    public ConstantPropagation propagateConstants(MethodDeclaration method) {
        return ConstantPropagation.build(method);
    }
}
