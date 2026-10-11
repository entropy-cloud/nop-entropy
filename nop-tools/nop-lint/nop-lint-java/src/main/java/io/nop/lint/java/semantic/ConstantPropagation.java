package io.nop.lint.java.semantic;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The intra-procedural constant propagation of one method-shaped body
 * (roadmap item 30, design 06 §4.4: {@code DataFlowAnalyzer.propagateConstants}).
 * Flow-insensitive v1: a variable is a known constant when its declaration
 * initialiser is a compile-time constant AND it is never reassigned
 * (reassignment includes increment/decrement forms per the plan's F1
 * blocker — {@code c++} is NOT an AssignExpr but IS a reassignment).
 *
 * <p>Six literal forms: String, int, long, boolean, char, null. The v1
 * query surface is single-literal only: a String literal returns its
 * unwrapped value, numeric and boolean forms return the expression's source
 * text (so {@code 0x10} reads back as written, not as 16) — an expression
 * like {@code "a" + "b"} is NOT folded in v1 and reads back as its source
 * text (plan 11: the javadoc previously claimed folding the implementation
 * never had). The query returns a sealed {@link ConstantResult} — three
 * states: {@code Constant} (with the value string), {@code NotConstant}
 * (reassigned or non-constant expression), {@code Unknown} (variable name
 * not declared in the method).
 */
public final class ConstantPropagation {

    /**
     * The three-state query result (plan F2: Optional is two-state and
     * cannot express the known/not-known/absent semantics).
     */
    public sealed interface ConstantResult permits Constant, NotConstant, Unknown {
    }

    /**
     * A known compile-time constant value.
     */
    public static final record Constant(String value) implements ConstantResult {
    }

    /**
     * The variable exists but is not a compile-time constant.
     */
    public static final record NotConstant() implements ConstantResult {
    }

    /**
     * The variable name is not declared in the method.
     */
    public static final record Unknown() implements ConstantResult {
    }

    private final Map<String, ConstantResult> byName;

    private ConstantPropagation(Map<String, ConstantResult> byName) {
        this.byName = Map.copyOf(byName);
    }

    /**
     * Builds the constant bindings from one method body. A variable is a
     * known constant when it has exactly one definition site (the
     * declarator) whose initializer is a compile-time constant and no use
     * site is a reassignment target. A variable with an increment/decrement
     * or additional assignment is NotConstant.
     */
    public static ConstantPropagation build(MethodDeclaration method) {
        return build(method, DefUseChain.build(method));
    }

    /**
     * The chain-sharing form (plan 12 audit m1): a caller that already built
     * the method's def-use chain passes it in — the same chain serves both
     * the record lookup and the constant classification instead of walking
     * the tree twice.
     */
    public static ConstantPropagation build(MethodDeclaration method, DefUseChain chain) {
        Map<String, ConstantResult> byName = new HashMap<>();
        for (DefUseChain.Record record : chain.records()) {
            byName.put(record.variableName(), classify(record, method));
        }
        return new ConstantPropagation(byName);
    }

    private static ConstantResult classify(DefUseChain.Record record, MethodDeclaration method) {
        // exactly one definition site (the declarator) and it must carry an
        // initialiser; the v1 no-initialiser form has no constant to bind
        if (record.definitionSites().size() != 1) {
            return new NotConstant();
        }
        Node defSite = record.definitionSites().get(0);
        if (!(defSite instanceof VariableDeclarator declarator)) {
            return new NotConstant();
        }
        if (declarator.getInitializer().isEmpty()) {
            return new NotConstant();
        }
        Expression init = declarator.getInitializer().get();
        if (!isConstantExpression(init)) {
            return new NotConstant();
        }
        return new Constant(constantValue(init));
    }

    /**
     * The constant query for a variable name (design 06 §4.4). Position-keyed
     * consumers (roadmap item 34) must NOT use this overload for
     * shadow-sensitive answers — the by-name table collapses same-name
     * re-declarations; use {@link #constantValueOf(Record)} instead.
     */
    public ConstantResult constantValueOf(String variableName) {
        ConstantResult result = byName.get(variableName);
        return result == null ? new Unknown() : result;
    }

    /**
     * The constant classification of one declaration record, identity-matched
     * (roadmap item 34): the record pins the exact declaration node, so
     * shadowing re-declarations keep independent answers.
     */
    public ConstantResult constantValueOf(DefUseChain.Record record) {
        return classify(record, null);
    }

    /**
     * True when the expression is a compile-time constant: a String/int/
     * long/boolean/char/null literal, a compile-time string concatenation
     * of constant parts, or a parenthesised constant.
     */
    private static boolean isConstantExpression(Expression expr) {
        if (expr instanceof StringLiteralExpr || expr instanceof NullLiteralExpr
                || expr instanceof CharLiteralExpr) {
            return true;
        }
        if (expr instanceof com.github.javaparser.ast.expr.IntegerLiteralExpr
                || expr instanceof com.github.javaparser.ast.expr.LongLiteralExpr
                || expr instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr) {
            return true;
        }
        if (expr instanceof UnaryExpr unary
                && (unary.getOperator() == UnaryExpr.Operator.MINUS
                || unary.getOperator() == UnaryExpr.Operator.PLUS)) {
            return isConstantExpression(unary.getExpression());
        }
        if (expr instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            return isConstantExpression(binary.getLeft()) && isConstantExpression(binary.getRight());
        }
        if (expr instanceof com.github.javaparser.ast.expr.EnclosedExpr enclosed) {
            return isConstantExpression(enclosed.getInner());
        }
        return false;
    }

    /**
     * The constant value of a compile-time constant expression (the
     * unwrapped literal text for String/char, the raw text for numeric and
     * boolean forms).
     */
    private static String constantValue(Expression expr) {
        if (expr instanceof StringLiteralExpr str) {
            return str.getValue();
        }
        if (expr instanceof CharLiteralExpr chr) {
            return chr.getValue();
        }
        return expr.toString();
    }
}
