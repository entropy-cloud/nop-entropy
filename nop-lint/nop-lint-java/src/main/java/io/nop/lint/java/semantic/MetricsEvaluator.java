package io.nop.lint.java.semantic;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import io.nop.lint.core.NopLintException;

import java.util.Optional;

/**
 * The method-level complexity metrics evaluator (roadmap item 32, design 01
 * §6 contract names): cyclomatic = decision-point count + 1, cognitive =
 * the SonarSource increment table (plan Decision 3, whitepaper v1.7
 * Appendix B), NPath = the product of per-decision path counts (plan
 * Decision 4 — explicitly NOT an AST-depth computation). Complexity is a
 * path property, never a depth property.
 *
 * <p>Cognitive v1 口径 (plan Decision 3): if/else if/else each +1 flat (the
 * else-if chain is flattened — no nesting multiplier on the clause itself)
 * but all three raise the nesting level for their contents; a switch and
 * all its cases combined incur a single +1 (plus nesting) and raise
 * nesting; loops and catch clauses +1 + nesting and raise nesting; the
 * ternary +1 + nesting and raises nesting; a lambda carries no increment of
 * its own but raises nesting; each new logical operator sequence (a run of
 * the same short-circuit operator) +1 flat. The recursion increment and the
 * labeled-jump increments are v1 residuals (plan Deferred). NPath v1 口径
 * (plan Decision 4): if/ternary/loop ×2, a switch with n case labels
 * ×(n+1), each catch clause ×2, each short-circuit operator ×2.
 *
 * <p>All three metrics traverse the method's entire subtree, so local or
 * anonymous classes declared inside the method contribute to its numbers —
 * the documented v1 shape (the caller's match unit is the method).</p>
 */
public final class MetricsEvaluator {

    /**
     * Cyclomatic complexity = decision points + 1 (design 01 §6): if, for,
     * while, do, each case label, catch, ternary, and each short-circuit
     * operator count one each.
     */
    public int cyclomaticComplexity(MethodDeclaration method) {
        requireMethod(method);
        Counter counter = new Counter();
        method.walk(node -> {
            if (node instanceof IfStmt || node instanceof ForStmt || node instanceof ForEachStmt
                    || node instanceof WhileStmt || node instanceof DoStmt
                    || node instanceof CatchClause || node instanceof ConditionalExpr) {
                counter.value++;
            } else if (node instanceof SwitchEntry entry && !entry.isDefault()) {
                counter.value++;
            } else if (node instanceof BinaryExpr binary
                    && isShortCircuit(binary.getOperator())) {
                counter.value++;
            }
        });
        return counter.value + 1;
    }

    /**
     * Cognitive complexity per the plan Decision 3 increment table; see the
     * class javadoc. Nesting starts at 0 for the method body.
     */
    public int cognitiveComplexity(MethodDeclaration method) {
        requireMethod(method);
        return cognitiveOf(method, 0);
    }

    /**
     * NPath complexity = the product of the per-decision path counts (plan
     * Decision 4); at least 1, saturating at {@link Long#MAX_VALUE} instead
     * of overflowing.
     */
    public long nPathComplexity(MethodDeclaration method) {
        requireMethod(method);
        return Math.max(1, nPathOf(method));
    }

    /**
     * The if-chain walk: the if clause is +1 + nesting; every else / else-if
     * link in the chain is a flat +1 (the whitepaper's flattening), while
     * everything below a clause (condition included — the uniform v1 rule: a
     * structure's entire subtree lives one level deeper) is walked at
     * nesting + 1.
     */
    private int cognitiveIf(IfStmt ifStmt, int nesting) {
        int total = 1 + nesting;
        total += cognitiveOf(ifStmt.getCondition(), nesting + 1);
        total += cognitiveOf(ifStmt.getThenStmt(), nesting + 1);
        Optional<Statement> elseStmt = ifStmt.getElseStmt();
        while (elseStmt.isPresent()) {
            Statement elseNode = elseStmt.get();
            total += 1;
            if (elseNode instanceof IfStmt elseIf) {
                total += cognitiveOf(elseIf.getCondition(), nesting + 1);
                total += cognitiveOf(elseIf.getThenStmt(), nesting + 1);
                elseStmt = elseIf.getElseStmt();
            } else {
                total += cognitiveOf(elseNode, nesting + 1);
                break;
            }
        }
        return total;
    }

    private int cognitiveOf(com.github.javaparser.ast.Node node, int nesting) {
        int total = 0;
        int childNesting = nesting;
        if (node instanceof IfStmt ifStmt) {
            return cognitiveIf(ifStmt, nesting);
        } else if (node instanceof SwitchStmt || node instanceof ForStmt
                || node instanceof ForEachStmt || node instanceof WhileStmt
                || node instanceof DoStmt || node instanceof CatchClause
                || node instanceof ConditionalExpr) {
            total += 1 + nesting;
            childNesting = nesting + 1;
        } else if (node instanceof LambdaExpr) {
            // a lambda carries no increment but raises nesting for its body
            childNesting = nesting + 1;
        }
        for (com.github.javaparser.ast.Node child : node.getChildNodes()) {
            total += cognitiveOf(child, childNesting);
        }
        total += logicalSequenceIncrement(node);
        return total;
    }

    /**
     * The logical-operator sequence rule: each run of the same short-circuit
     * operator is one flat +1 — a new sequence starts where the parent is
     * not a logical expression of the same operator.
     */
    private int logicalSequenceIncrement(com.github.javaparser.ast.Node node) {
        if (!(node instanceof BinaryExpr binary) || !isShortCircuit(binary.getOperator())) {
            return 0;
        }
        var parent = node.getParentNode().orElse(null);
        if (parent instanceof BinaryExpr parentBinary
                && parentBinary.getOperator() == binary.getOperator()) {
            return 0;
        }
        return 1;
    }

    private long nPathOf(com.github.javaparser.ast.Node node) {
        long product = 1;
        for (com.github.javaparser.ast.Node child : node.getChildNodes()) {
            product = saturatingMultiply(product, nPathOf(child));
            if (child instanceof IfStmt || child instanceof ForStmt || child instanceof ForEachStmt
                    || child instanceof WhileStmt || child instanceof DoStmt
                    || child instanceof ConditionalExpr) {
                product = saturatingMultiply(product, 2);
            } else if (child instanceof SwitchStmt switchStmt) {
                long caseLabels = switchStmt.getEntries().stream()
                        .filter(entry -> !entry.isDefault()).count();
                product = saturatingMultiply(product, caseLabels + 1);
            } else if (child instanceof CatchClause) {
                product = saturatingMultiply(product, 2);
            } else if (child instanceof BinaryExpr binary
                    && isShortCircuit(binary.getOperator())) {
                product = saturatingMultiply(product, 2);
            }
        }
        return product;
    }

    private static long saturatingMultiply(long a, long b) {
        if (b != 0 && a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    private static boolean isShortCircuit(BinaryExpr.Operator operator) {
        return operator == BinaryExpr.Operator.AND || operator == BinaryExpr.Operator.OR;
    }

    private static void requireMethod(MethodDeclaration method) {
        if (method == null) {
            throw new NopLintException("a metrics query requires a method declaration");
        }
    }

    private static final class Counter {
        int value;
    }
}
