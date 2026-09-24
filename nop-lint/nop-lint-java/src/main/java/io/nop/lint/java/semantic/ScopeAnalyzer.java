package io.nop.lint.java.semantic;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Java scope-analysis kernel (roadmap item 33, design 05 §2 v1 面,
 * plan 2026-09-24-1130-1): scope kinds, the declarations of one scope,
 * reference → definition resolution, and the shadowing judgment — all over
 * a single compilation unit via the position-aware lexical walk (plan
 * Decision 6; no symbol solver, no L2 dependency).
 *
 * <p>Visibility is per scope kind (plan Decision 5): class fields are
 * visible across the whole class body regardless of declaration order;
 * locals and parameters are visible from their declaration point on. The
 * shadowing judgment does not presuppose legal Java (nested local-local
 * same-names are a compile error yet still judged), and the direction
 * special cases hold: lambda/catch/for-init variables shadow fields but
 * never an enclosing local (that form stays a compile error — still judged
 * as shadowing, tests pin it). The switch block is ONE scope: case labels
 * are kind tags only, declarations are visible across cases (plan D2).
 *
 * <p>Reuse boundary (plan Decision 6 注记): the item-30 DefUseChain is a
 * method-internal device — its class-body barrier and lambda flattening are
 * deliberately NOT carried over here; the class field scope is a new
 * surface and the lambda body is its own scope whose parent is the
 * enclosing scope. Only the lexical-walk mechanics carry over.</p>
 *
 * <p>All positions are 1-based JavaParser (line, column) pairs internally;
 * the resolver layer converts to/from the engine's byte-offset contract.
 * A reference that resolves to nothing (cross-class names, external names)
 * yields an empty answer — a legitimate result, never a failure; failures
 * (malformed queries) throw.</p>
 */
public final class ScopeAnalyzer {

    /**
     * The scope kinds the v1 surface distinguishes (the {@code scope.kind}
     * binding answers these strings).
     */
    public static final String KIND_CLASS = "class";
    public static final String KIND_METHOD = "method";
    public static final String KIND_CATCH = "catch";
    public static final String KIND_LAMBDA = "lambda";
    public static final String KIND_FOR = "for";
    public static final String KIND_SWITCH = "switch";
    public static final String KIND_BLOCK = "block";
    public static final String KIND_TOP = "top";

    /**
     * One resolved declaration: the declaring name's 1-based position (the
     * resolver converts to the engine's byte contract) and the variable
     * name.
     */
    public record Definition(String name, int line, int column) {
    }

    /**
     * The declaration enclosing {@code bytePos}-derived position resolves
     * to: the innermost visible same-name declaration walking outward
     * (fields order-independently, locals/params from their declaration
     * point), or null when nothing in the unit declares it.
     */
    public Definition definitionOf(CompilationUnit unit, int line, int column) {
        Node reference = identifierAt(unit, line, column);
        String name = reference instanceof SimpleName simple ? simple.getIdentifier()
                : reference.toString();
        return resolve(unit, name, line, column, reference);
    }

    /**
     * The names declared directly in the innermost scope containing the
     * position (locals/params/fields per the scope kind).
     */
    public List<String> declaredNames(CompilationUnit unit, int line, int column) {
        Node position = nodeAt(unit, line, column);
        Node scope = innermostScopeOf(position, unit);
        Map<String, Boolean> names = new LinkedHashMap<>();
        for (Declaration declaration : directDeclarations(scope)) {
            names.putIfAbsent(declaration.name(), Boolean.TRUE);
        }
        return new ArrayList<>(names.keySet());
    }

    /**
     * The kind string of the innermost scope containing the position (see
     * the KIND_ constants; {@code top} for positions outside any scope
     * boundary).
     */
    public String scopeKind(CompilationUnit unit, int line, int column) {
        Node position = nodeAt(unit, line, column);
        Node scope = innermostScopeOf(position, unit);
        return kindOf(scope);
    }

    /**
     * True when the declaration at the position shares its name with a
     * visible declaration in an ENCLOSING scope (fields order-independently,
     * enclosing locals/params before the declaration point). Same-scope
     * duplicates are not shadowing.
     */
    public boolean shadows(CompilationUnit unit, int line, int column) {
        Node position = identifierAt(unit, line, column);
        String name = position instanceof SimpleName simple ? simple.getIdentifier()
                : position.toString();
        // walk the scope boundaries strictly outside the declaration's own
        // innermost scope: the declaration's parent chain starts at its
        // scope boundary, which we skip
        Node scope = innermostScopeOf(position, unit);
        Node ancestor = scope.getParentNode().orElse(null);
        while (ancestor != null) {
            if (isRealScopeBoundary(ancestor)) {
                Definition hit = visibleDeclarationIn(ancestor, name, line, column);
                if (hit != null) {
                    return true;
                }
            }
            ancestor = ancestor.getParentNode().orElse(null);
        }
        return false;
    }

    // ==================== resolution core ====================

    private Definition resolve(CompilationUnit unit, String name, int line, int column,
                               Node reference) {
        Node current = reference;
        while (current != null) {
            if (isRealScopeBoundary(current)) {
                Definition hit = visibleDeclarationIn(current, name, line, column);
                if (hit != null) {
                    return hit;
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }

    /**
     * A scope boundary that owns declarations in the outward walk — the
     * compilation unit is excluded (the class boundary already checked its
     * fields; letting the unit re-descend would double-count them as
     * outer-level and mis-judge same-scope duplicates).
     */
    private static boolean isRealScopeBoundary(Node node) {
        return isScopeBoundary(node) && !(node instanceof CompilationUnit);
    }

    /**
     * The same-name declaration one scope boundary makes visible at
     * (line, column), or null. Field visibility ignores order; every other
     * declaration is visible from its point on. Lambda and catch parameters
     * and for-init variables span their whole statement (plan Decision 5
     * special cases).
     */
    private Definition visibleDeclarationIn(Node scope, String name, int line, int column) {
        for (Declaration declaration : directDeclarations(scope)) {
            if (!declaration.name().equals(name)) {
                continue;
            }
            if (declaration.orderIndependent()) {
                return definitionOf(declaration);
            }
            if (!after(declaration.line(), declaration.column(), line, column)) {
                return definitionOf(declaration);
            }
        }
        return null;
    }

    /**
     * The declarations made directly by one scope boundary — stopping at
     * nested boundaries (an if-branch's block belongs to the branch, not
     * the enclosing block). Fields order-independent; try resources belong
     * to the try's inner block; switch entries' declarators belong to the
     * switch scope (single scope, order-independent across cases).
     */
    private List<Declaration> directDeclarations(Node scope) {
        List<Declaration> declarations = new ArrayList<>();
        boolean orderIndependent = kindOf(scope).equals(KIND_CLASS)
                || kindOf(scope).equals(KIND_SWITCH);
        for (Node child : scope.getChildNodes()) {
            collectDeclarations(child, declarations, orderIndependent);
        }
        return declarations;
    }

    private void collectDeclarations(Node node, List<Declaration> out, boolean orderIndependent) {
        if (node instanceof VariableDeclarator declarator) {
            out.add(new Declaration(declarator.getName().getIdentifier(),
                    declarator.getName().getRange().get().begin.line,
                    declarator.getName().getRange().get().begin.column,
                    orderIndependent));
            return;
        }
        if (node instanceof Parameter parameter) {
            out.add(new Declaration(parameter.getName().getIdentifier(),
                    parameter.getName().getRange().get().begin.line,
                    parameter.getName().getRange().get().begin.column,
                    orderIndependent));
            return;
        }
        if (node instanceof FieldDeclaration field) {
            for (VariableDeclarator declarator : field.getVariables()) {
                out.add(new Declaration(declarator.getName().getIdentifier(),
                        declarator.getName().getRange().get().begin.line,
                        declarator.getName().getRange().get().begin.column,
                        true));
            }
            return;
        }
        if (isScopeBoundary(node)) {
            return;
        }
        for (Node child : node.getChildNodes()) {
            collectDeclarations(child, out, orderIndependent);
        }
    }

    /**
     * The innermost scope boundary containing the node (the node itself
     * counts — a block IS the scope its direct declarations live in).
     */
    private Node innermostScopeOf(Node node, CompilationUnit unit) {
        Node current = node;
        while (current != null) {
            if (isScopeBoundary(current) && !(current instanceof CompilationUnit)) {
                return current;
            }
            current = current.getParentNode().orElse(null);
        }
        return unit;
    }

    private static boolean isScopeBoundary(Node node) {
        return node instanceof BlockStmt || node instanceof MethodDeclaration
                || node instanceof ClassOrInterfaceDeclaration || node instanceof CatchClause
                || node instanceof LambdaExpr || node instanceof ForStmt
                || node instanceof ForEachStmt || node instanceof SwitchStmt
                || node instanceof TryStmt
                || node instanceof CompilationUnit;
    }

    private static String kindOf(Node scope) {
        if (scope instanceof ClassOrInterfaceDeclaration) {
            return KIND_CLASS;
        }
        if (scope instanceof MethodDeclaration) {
            return KIND_METHOD;
        }
        if (scope instanceof CatchClause) {
            return KIND_CATCH;
        }
        if (scope instanceof LambdaExpr) {
            return KIND_LAMBDA;
        }
        if (scope instanceof ForStmt || scope instanceof ForEachStmt) {
            return KIND_FOR;
        }
        if (scope instanceof SwitchStmt) {
            return KIND_SWITCH;
        }
        if (scope instanceof BlockStmt) {
            return KIND_BLOCK;
        }
        return KIND_TOP;
    }

    private Definition definitionOf(Declaration declaration) {
        return new Definition(declaration.name(), declaration.line(), declaration.column());
    }

    /**
     * True when the declaration position is strictly before the reference
     * position (locals/params are visible from their point on).
     */
    private static boolean after(int declLine, int declColumn, int line, int column) {
        if (declLine != line) {
            return declLine > line;
        }
        return declColumn > column;
    }

    private Node identifierAt(CompilationUnit unit, int line, int column) {
        Node found = nodeAt(unit, line, column);
        if (found instanceof NameExpr nameExpr) {
            return nameExpr.getName();
        }
        if (found instanceof VariableDeclarator declarator) {
            return declarator.getName();
        }
        return found;
    }

    private Node nodeAt(CompilationUnit unit, int line, int column) {
        Node found = minimalAt(unit, line, column);
        if (found == null) {
            throw new IllegalArgumentException("no node at " + line + ":" + column
                    + " (a scope query must name a position inside the unit)");
        }
        return found;
    }

    /**
     * The deepest node whose range contains the (1-based) position: a
     * pre-order walk keeping the last containing node (containment is
     * nested, pre-order visits outer before inner, so the deepest
     * containing node wins).
     */
    private Node minimalAt(CompilationUnit unit, int line, int column) {
        Node best = null;
        for (Node node : unit.findAll(Node.class)) {
            if (node.getRange().isEmpty()) {
                continue;
            }
            com.github.javaparser.Position begin = node.getRange().get().begin;
            com.github.javaparser.Position end = node.getRange().get().end;
            if (positionIn(line, column, begin, end)) {
                best = node;
            }
        }
        return best;
    }

    private static boolean positionIn(int line, int column,
                                      com.github.javaparser.Position begin,
                                      com.github.javaparser.Position end) {
        if (line < begin.line || line > end.line) {
            return false;
        }
        if (line == begin.line && column < begin.column) {
            return false;
        }
        return !(line == end.line && column > end.column);
    }

    private record Declaration(String name, int line, int column, boolean orderIndependent) {
    }
}
