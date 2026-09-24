package io.nop.lint.java.semantic;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;

/**
 * The position-keyed L3 dataflow queries over one method (roadmap item 34,
 * design 06 §4.4 consumer surface): the constant classification, use count,
 * and self-assignment flag of the local/parameter declared at a position.
 * The identity-matched lookup (plan R1 B-1) pins the exact declaration
 * node — {@link ConstantPropagation}'s by-name table would silently
 * collapse shadowing re-declarations.
 *
 * <p>Fail-closed: a position that is not a local/parameter declaration
 * inside a method (fields and method-outside declarations are the v1
 * not-answered surface, plan Decision 3) throws — never a fabricated zero
 * or "not constant". Positions are 1-based (line, column) JavaParser
 * coordinates; the resolver layer converts the engine's byte contract.</p>
 */
public final class DataflowQueries {

    private final DataFlowAnalyzer analyzer = new DataFlowAnalyzer();

    /**
     * The compile-time constant value of the variable declared at the
     * position, or null when it is not a constant ({@code NotConstant} and
     * re-assigned forms alike — the legitimate "no constant" answer).
     */
    public String constantValue(MethodDeclaration method, int line, int column) {
        VariableDeclaration declaration = declarationAt(method, line, column);
        ConstantPropagation propagation = ConstantPropagation.build(method);
        ConstantPropagation.ConstantResult result = propagation.constantValueOf(declaration.record());
        if (result instanceof ConstantPropagation.Constant constant) {
            return constant.value();
        }
        return null;
    }

    /**
     * The number of use sites of the variable declared at the position.
     */
    public long useCount(MethodDeclaration method, int line, int column) {
        return declarationAt(method, line, column).record().useSites().size();
    }

    /**
     * The self-assignment flag of the variable declared at the position
     * (the v1 {@code x = x} identity form).
     */
    public boolean isSelfAssigned(MethodDeclaration method, int line, int column) {
        return declarationAt(method, line, column).record().isSelfAssigned();
    }

    private VariableDeclaration declarationAt(MethodDeclaration method, int line, int column) {
        // name-anchored with one tolerance: the query may sit anywhere on
        // the declaration (a tree-sitter variable_declarator starts at the
        // type keyword), so a non-reference position anchors to the
        // declaration's name; a position on a REFERENCE name throws —
        // accepting it would silently answer the wrong variable
        Node position = minimalAt(method, line, column);
        SimpleName name = anchorDeclaration(position);
        Node declarator = name.getParentNode().orElse(null);
        if (hasNoEnclosingMethod(declarator)) {
            throw new IllegalArgumentException("the declaration at " + line + ":" + column
                    + " is outside any method (field initializers are not the v1 dataflow"
                    + " surface)");
        }
        DefUseChain.Record record = matchingRecord(method, declarator);
        return new VariableDeclaration(record);
    }

    /**
     * The declaration name the position anchors to: the name itself when the
     * position sits on it; otherwise the name of the declaration the
     * position falls inside (the type keyword of the same declaration). A
     * position on a reference name (a {@code NameExpr}) is not a declaration
     * position and throws.
     */
    private SimpleName anchorDeclaration(Node position) {
        if (position instanceof SimpleName name) {
            Node parent = name.getParentNode().orElse(null);
            if (parent instanceof VariableDeclarator || parent instanceof Parameter) {
                return name;
            }
            if (parent instanceof com.github.javaparser.ast.expr.NameExpr) {
                throw new IllegalArgumentException("the queried position is a reference,"
                        + " not a declaration name (a dataflow query anchors to the"
                        + " declaration's own name)");
            }
        }
        Node current = position;
        while (current != null) {
            if (current instanceof VariableDeclarator declarator) {
                return declarator.getName();
            }
            if (current instanceof Parameter parameter) {
                return parameter.getName();
            }
            if (current instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr expr
                    && !expr.getVariables().isEmpty()) {
                return expr.getVariable(0).getName();
            }
            current = current.getParentNode().orElse(null);
        }
        throw new IllegalArgumentException("no local/parameter declaration at the queried"
                + " position (fields and method-outside declarations are not the v1"
                + " dataflow surface)");
    }

    private static boolean hasNoEnclosingMethod(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof MethodDeclaration || current instanceof ConstructorDeclaration) {
                return false;
            }
            current = current.getParentNode().orElse(null);
        }
        return true;
    }

    private DefUseChain.Record matchingRecord(MethodDeclaration method, Node declarationNode) {
        DefUseChain chain = analyzer.buildDefUseChain(method);
        for (DefUseChain.Record record : chain.records()) {
            if (record.declarationNode() == declarationNode) {
                return record;
            }
        }
        throw new IllegalArgumentException("the declaration at the queried position has no"
                + " dataflow record (a catch parameter or field form the v1 chain does not"
                + " cover) — fail-closed");
    }

    private static Node minimalAt(Node root, int line, int column) {
        Node best = null;
        for (Node node : root.findAll(Node.class)) {
            if (node.getRange().isEmpty()) {
                continue;
            }
            com.github.javaparser.Position begin = node.getRange().get().begin;
            com.github.javaparser.Position end = node.getRange().get().end;
            boolean contains = line >= begin.line && line <= end.line
                    && !(line == begin.line && column < begin.column)
                    && !(line == end.line && column > end.column);
            if (contains) {
                best = node;
            }
        }
        return best;
    }

    private record VariableDeclaration(DefUseChain.Record record) {
    }
}
