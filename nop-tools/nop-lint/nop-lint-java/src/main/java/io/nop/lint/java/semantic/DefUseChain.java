package io.nop.lint.java.semantic;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The intra-procedural definition-use chain of one method-shaped body
 * (roadmap item 30, design 06 §4.4: {@code DataFlowAnalyzer.buildDefUseChain}).
 * Flow-insensitive by v1 adjudication: every definition site and every use
 * site is collected from the method's subtree and aggregated per variable
 * declaration — no control-flow graph is built.
 *
 * <p>Definition sites: every {@code VariableDeclarator} node (including
 * declarations without an initializer), every {@code Parameter} (including
 * catch parameters), the target subtree of every {@code AssignExpr}
 * (including EnclosedExpr-wrapped targets), and the operand of every
 * increment/decrement {@code UnaryExpr} (x++ is NOT an AssignExpr — the
 * round-1 review's F1 blocker).</p>
 *
 * <p>Use sites: every {@code NameExpr} that is not itself a definition
 * target. References inside lambda bodies participate in the enclosing
 * method's scope; anonymous/local class bodies are barriers — their own
 * fields and re-declared variables do not enter the method-level chains
 * (plan F3 class-body barrier).</p>
 *
 * <p>Self-assignment (v1 scope): local/parameter {@code x = x} identity
 * only — {@code x = this.x} is a legal field-to-local copy on the field
 * successor surface.</p>
 */
public final class DefUseChain {

    /**
     * The aggregated chain of one variable declaration (identity-keyed by
     * the declaration node, so shadowing inner re-declarations build
     * independent chains).
     */
    public record Record(String variableName, Node declarationNode,
                         List<Node> definitionSites, List<Node> useSites,
                         boolean isSelfAssigned) {

        public boolean isUsed() {
            return !useSites.isEmpty();
        }
    }

    private final List<Record> chains;

    private DefUseChain(List<Record> chains) {
        this.chains = List.copyOf(chains);
    }

    /**
     * Builds the definition-use chains of one method body.
     */
    public static DefUseChain build(MethodDeclaration method) {
        return build((Node) method);
    }

    /**
     * Builds the definition-use chains of one constructor body.
     */
    public static DefUseChain build(com.github.javaparser.ast.body.ConstructorDeclaration constructor) {
        return build((Node) constructor);
    }

    private static DefUseChain build(Node root) {
        // per-variable state, identity-keyed by the declaration node
        Set<Node> declaredVars = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Node, String> nameByVar = new IdentityHashMap<>();
        Map<Node, List<Node>> defSitesByVar = new IdentityHashMap<>();
        Map<Node, List<Node>> useSitesByVar = new IdentityHashMap<>();
        Map<Node, Boolean> selfAssignedByVar = new IdentityHashMap<>();
        List<Node> order = new ArrayList<>();

        // pass 1: collect declarations (in source order)
        for (Node node : root.findAll(Node.class)) {
            String name = null;
            if (node instanceof VariableDeclarator declarator && inSurface(node, root)) {
                name = declarator.getNameAsString();
            } else if (node instanceof Parameter parameter && inSurface(node, root)
                    && !isLambdaParameter(parameter, root)) {
                name = parameter.getNameAsString();
            }
            if (name != null && !declaredVars.contains(node)) {
                declaredVars.add(node);
                nameByVar.put(node, name);
                List<Node> defSites = new ArrayList<>();
                defSites.add(node); // the declaration itself is a definition site
                defSitesByVar.put(node, defSites);
                useSitesByVar.put(node, new ArrayList<>());
                selfAssignedByVar.put(node, false);
                order.add(node);
            }
        }

        // pass 2: collect definition targets and self-assignments
        for (Node node : root.findAll(Node.class)) {
            if (node instanceof AssignExpr assign && assign.getOperator() == AssignExpr.Operator.ASSIGN) {
                List<NameExpr> targets = nameTargets(assign.getTarget());
                if (targets.size() == 1) {
                    NameExpr target = targets.get(0);
                    Node var = findVar(target, declaredVars, nameByVar, root);
                    if (var != null) {
                        defSitesByVar.get(var).add(target);
                        // self-assignment: the value is also a NameExpr with the same name
                        if (assign.getValue() instanceof NameExpr value
                                && value.getNameAsString().equals(target.getNameAsString())) {
                            selfAssignedByVar.put(var, true);
                        }
                    }
                }
            } else if (node instanceof UnaryExpr unary && isIncrementOrDecrement(unary)) {
                NameExpr operand = innerName(unary.getExpression());
                if (operand != null) {
                    Node var = findVar(operand, declaredVars, nameByVar, root);
                    if (var != null) {
                        defSitesByVar.get(var).add(operand);
                    }
                }
            }
        }

        // pass 3: collect use sites (NameExpr that are not definition targets
        // and are not shadowed by a lambda parameter of the same name)
        for (Node node : root.findAll(Node.class)) {
            if (!(node instanceof NameExpr name)) {
                continue;
            }
            if (isDefTarget(name, root)) {
                continue;
            }
            if (shadowedByLambdaParam(name, root)) {
                continue;
            }
            Node var = findVar(name, declaredVars, nameByVar, root);
            if (var != null) {
                useSitesByVar.get(var).add(name);
            }
        }

        List<Record> records = new ArrayList<>(order.size());
        for (Node var : order) {
            records.add(new Record(
                    nameByVar.get(var), var,
                    List.copyOf(defSitesByVar.get(var)),
                    List.copyOf(useSitesByVar.get(var)),
                    selfAssignedByVar.get(var)));
        }
        return new DefUseChain(records);
    }

    /**
     * The variable records in declaration order.
     */
    public List<Record> records() {
        return chains;
    }

    /**
     * The record of a variable by name — the first declaration with that
     * name.
     */
    public Optional<Record> recordOf(String variableName) {
        return chains.stream()
                .filter(record -> record.variableName().equals(variableName))
                .findFirst();
    }

    // ==================== internals ====================

    private static boolean isIncrementOrDecrement(UnaryExpr unary) {
        return unary.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT
                || unary.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT
                || unary.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                || unary.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT;
    }

    private static NameExpr innerName(Expression expr) {
        if (expr instanceof NameExpr name) {
            return name;
        }
        if (expr instanceof EnclosedExpr enclosed && enclosed.getInner() instanceof NameExpr name) {
            return name;
        }
        return null;
    }

    /**
     * The NameExpr targets of an assignment (direct or EnclosedExpr-wrapped,
     * plan F6).
     */
    private static List<NameExpr> nameTargets(Expression target) {
        List<NameExpr> result = new ArrayList<>();
        if (target instanceof NameExpr name) {
            result.add(name);
        } else if (target instanceof EnclosedExpr enclosed
                && enclosed.getInner() instanceof NameExpr name) {
            result.add(name);
        }
        return result;
    }

    /**
     * True when this NameExpr is the target of an enclosing assignment or
     * the operand of an increment/decrement.
     */
    private static boolean isDefTarget(NameExpr name, Node root) {
        Node current = name;
        Node parent = current.getParentNode().orElse(null);
        while (parent != null && parent != root) {
            if (parent instanceof AssignExpr assign) {
                List<NameExpr> targets = nameTargets(assign.getTarget());
                if (targets.contains(current)) {
                    return true;
                }
            }
            if (parent instanceof UnaryExpr unary && isIncrementOrDecrement(unary)
                    && unary.getExpression() == current) {
                return true;
            }
            // escape the expression tree: a BlockStmt boundary means the
            // name is no longer an assignment target or increment operand
            if (parent instanceof BlockStmt) {
                return false;
            }
            current = parent;
            parent = current.getParentNode().orElse(null);
        }
        return false;
    }

    /**
     * Resolves a NameExpr to the tracked variable declaration it references:
     * the innermost declaration with a matching name whose scope encloses
     * the reference (the lexical-scope adjudication, plan F3).
     *
     * <p>Resolution keys (plan 11, audit finding C1 — the former single
     * "largest declaration line" key mis-bound references whenever an outer
     * declaration textually followed an inner block): first the declaration
     * must PRECEDE the reference (a later declaration can never be in
     * scope); among the survivors the DEEPEST scope boundary wins; ties are
     * broken by the latest declaration line. This converges block shadowing
     * AND same-depth sibling reuse (two catch params of one name, re-used
     * for-loop variables) without any resolver dependency.</p>
     */
    private static Node findVar(NameExpr name, Set<Node> declaredVars,
                                Map<Node, String> nameByVar, Node root) {
        int refLine = name.getRange().map(r -> r.begin.line).orElse(-1);
        Node best = null;
        int bestDepth = -1;
        int bestLine = -1;
        for (Node var : declaredVars) {
            if (!nameByVar.get(var).equals(name.getNameAsString())) {
                continue;
            }
            int varLine = var.getRange().map(r -> r.begin.line).orElse(-1);
            // a declaration after the reference point is never in scope
            if (varLine > refLine) {
                continue;
            }
            if (!scopeEncloses(var, name)) {
                continue;
            }
            int depth = scopeDepth(var);
            if (depth > bestDepth || (depth == bestDepth && varLine >= bestLine)) {
                best = var;
                bestDepth = depth;
                bestLine = varLine;
            }
        }
        return best;
    }

    /**
     * The nesting depth of the declaration's scope boundary: a parameter's
     * method-wide scope is the shallowest (0); a local's nearest enclosing
     * block counts one per containing block, so an inner block's local
     * outranks an outer block's — which is exactly the shadowing order.
     */
    private static int scopeDepth(Node var) {
        if (var instanceof Parameter) {
            return 0;
        }
        if (var instanceof VariableDeclarator declarator) {
            Optional<BlockStmt> scope = declarator.findAncestor(BlockStmt.class);
            if (scope.isEmpty()) {
                // class-field initializer context: shallowest, like a param
                return 0;
            }
            int depth = 0;
            Node current = scope.get();
            while (current != null) {
                if (current instanceof BlockStmt) {
                    depth++;
                }
                current = current.getParentNode().orElse(null);
            }
            return depth;
        }
        return 0;
    }

    /**
     * True when the declaration's scope encloses the reference. Parameters
     * are method-wide; local variables are scoped to their nearest enclosing
     * BlockStmt; anonymous/local class bodies are barriers.
     */
    private static boolean scopeEncloses(Node var, NameExpr reference) {
        if (var instanceof Parameter parameter && isLambdaParameter(parameter, null)) {
            return false; // lambda params are a separate scope (F3)
        }
        if (var instanceof Parameter) {
            return true; // method-wide
        }
        if (var instanceof VariableDeclarator declarator) {
            Optional<BlockStmt> scope = declarator.findAncestor(BlockStmt.class);
            if (scope.isEmpty()) {
                return true;
            }
            // the reference must be inside the same block
            Node current = reference;
            while (current != null) {
                if (current == scope.get()) {
                    return true;
                }
                // a nested class body is a barrier
                if (current instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
                        || current instanceof com.github.javaparser.ast.body.EnumDeclaration) {
                    return false;
                }
                current = current.getParentNode().orElse(null);
            }
            return false;
        }
        return false;
    }

    /**
     * True when the NameExpr is inside a lambda body AND that lambda has a
     * parameter with the same name (plan F3: the lambda param shadows the
     * method-level variable inside the lambda body).
     */
    private static boolean shadowedByLambdaParam(NameExpr name, Node root) {
        Node current = name;
        while (current != null && current != root) {
            if (current instanceof com.github.javaparser.ast.expr.LambdaExpr lambda) {
                for (Parameter param : lambda.getParameters()) {
                    if (param.getNameAsString().equals(name.getNameAsString())) {
                        return true;
                    }
                }
                // the innermost lambda has no matching param — look no further
                // (the lambda is a new scope; outer lambdas' params don't shadow
                // through this lambda's param list)
                break;
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }

    /**
     * True when this node lives on the method's own variable surface (not
     * inside an anonymous or local class body, which is the plan's F3
     * class-body barrier).
     */
    private static boolean inSurface(Node node, Node root) {
        Node current = node;
        while (current != null && current != root) {
            if (current instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
                    || current instanceof com.github.javaparser.ast.body.EnumDeclaration) {
                return false;
            }
            // anonymous class body: ObjectCreationExpr with a class body is
            // a barrier (plan F3 / round-1 S5b — anonymous fields must not
            // pollute the method surface)
            if (current instanceof com.github.javaparser.ast.expr.ObjectCreationExpr creation
                    && creation.getAnonymousClassBody().isPresent()) {
                return false;
            }
            current = current.getParentNode().orElse(null);
        }
        return true;
    }

    /**
     * True when this Parameter is a lambda parameter (its parent chain
     * reaches a LambdaExpr before the method root). Lambda parameters are
     * a separate scope from the method's own variables (plan F3 adjudication).
     */
    private static boolean isLambdaParameter(Node parameter, Node root) {
        Node current = parameter;
        while (current != null && current != root) {
            if (current instanceof com.github.javaparser.ast.expr.LambdaExpr) {
                return true;
            }
            current = current.getParentNode().orElse(null);
        }
        return false;
    }
}
