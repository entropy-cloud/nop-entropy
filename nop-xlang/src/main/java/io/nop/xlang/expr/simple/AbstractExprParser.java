/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.XLangOperator;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ARG_PEEK_OP;
import static io.nop.xlang.XLangErrors.ERR_EXPR_BINARY_OP_NO_LEFT_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXPR_BINARY_OP_NO_RIGHT_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXPR_MISSING_FACTOR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_NOT_EXPECTED_OP;
import static io.nop.xlang.XLangErrors.ERR_EXPR_NULL_FACTOR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_UNARY_OP_NO_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_UNEXPECTED_CHAR;
import static io.nop.xlang.XLangErrors.ERR_EXPR_UNSUPPORTED_OP;

public abstract class AbstractExprParser<E> {

    private boolean useEvalException;
    private int features;

    public void setFeatures(int features) {
        this.features = features;
    }

    public int getFeatures() {
        return features;
    }

    public void addFeatures(int features) {
        this.features |= features;
    }

    public boolean supportFeature(int feature) {
        return (features & feature) == feature;
    }

    public boolean isUseEvalException() {
        return useEvalException;
    }

    public void setUseEvalException(boolean useEvalException) {
        this.useEvalException = useEvalException;
    }

    public E parseExpr(SourceLocation loc, String s) {
        if (StringHelper.isEmpty(s))
            return null;
        TextScanner sc = TextScanner.fromString(loc, s);
        sc.useEvalException = this.isUseEvalException();

        sc.skipBlank();
        E expr = simpleExpr(sc);
        if (!sc.isEnd())
            throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
        return makeCompileResult(expr);
    }

    public E parseExpr(TextScanner sc) {
        return makeCompileResult(simpleExpr(sc));
    }

    protected E makeCompileResult(E result) {
        return result;
    }

    protected E simpleExpr(TextScanner sc) {
        return orExpr(sc);
    }

    protected E orExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        E x = andExpr(sc);
        List<E> exprs = null;
        while (consumeOrOp(sc)) {
            if (exprs == null) {
                checkLeftValue(sc, XLangOperator.OR.name(), x);
                exprs = new ArrayList<>();
                exprs.add(x);
            }
            E y = andExpr(sc);
            checkRightValue(sc, XLangOperator.OR.name(), y);
            exprs.add(y);
        }
        if (exprs != null) {
            return newLogicExpr(loc, XLangOperator.OR, exprs);
        }
        return x;
    }

    /**
     * <pre>
     *   ConditionalAndE :=
     *     InclusiveOrE { '&amp;&amp;' InclusiveOrE }
     * </pre>
     */
    protected E andExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        E x = inclusiveOrExpr(sc);
        List<E> exprs = null;
        while (consumeAndOp(sc)) {
            if (exprs == null) {
                checkLeftValue(sc, XLangOperator.AND.name(), x);
                exprs = new ArrayList<>();
                exprs.add(x);
            }
            E y = inclusiveOrExpr(sc);
            checkRightValue(sc, XLangOperator.AND.name(), y);
            exprs.add(y);
        }
        if (exprs != null)
            return newLogicExpr(loc, XLangOperator.AND, exprs);
        return x;
    }

    protected E equalityExpr(TextScanner sc) {
        E x = relationalExpr(sc);
        SourceLocation loc = sc.location();
        if (mayMatch(sc, XLangOperator.EQ)) {
            E y = relationalExpr(sc);
            return newBinaryExpr(loc, XLangOperator.EQ, x, y);
        } else if (mayMatch(sc, XLangOperator.NE)) {
            E y = relationalExpr(sc);
            return newBinaryExpr(loc, XLangOperator.NE, x, y);
        }
        return x;
    }

    /**
     * <pre>
     *   RelationalExpression :=
     *     ShiftExpression {
     *       'instanceof' ReferenceType
     *       | '&lt;' ShiftExpression [ { ',' TypeArgument } '&gt;' ]
     *       | '&lt;' TypeArgument [ { ',' TypeArgument } '&gt;' ]
     *       | ( '&gt;' | '&lt;=' | '&gt;=' ) ShiftExpression
     *     }
     * </pre>
     */
    protected E relationalExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        E x = shiftExpr(sc);
        E x2 = relationalExprEx(sc, x);
        if (x2 != null)
            return x2;

        XLangOperator op = peekOperator(sc);
        if (op != null) {
            if (op.isCompareOp()) {
                checkLeftValue(sc, op.name(), x);
                skipOp(sc);
                E y = shiftExpr(sc);
                checkRightValue(sc, op.name(), y);
                return newBinaryExpr(loc, op, x, y);
            }
        }
        return x;
    }

    protected E relationalExprEx(TextScanner sc, E x) {
        return null;
    }

    protected E stringExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        String s = sc.nextJavaString();
        sc.skipBlank();
        return newLiteralExpr(loc, s);
    }

    protected E numberExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        Number num = sc.nextNumber();
        sc.skipBlank();
        return newLiteralExpr(loc, num);
    }

    protected abstract E inclusiveOrExpr(TextScanner sc);

    protected abstract E shiftExpr(TextScanner sc);

    protected abstract E newLogicExpr(SourceLocation loc, XLangOperator op, List<E> exprs);

    protected abstract E newBinaryExpr(SourceLocation loc, XLangOperator op, E x, E y);

    protected abstract E newLiteralExpr(SourceLocation loc, Object value);

    protected E defaultFactorExpr(TextScanner sc) {
        throw sc.newError(ERR_EXPR_MISSING_FACTOR).param(ARG_PEEK_OP, sc.peekToken);
    }

    protected boolean mayMatch(TextScanner sc, XLangOperator op) {
        if (peekOperator(sc) == op) {
            skipOp(sc);
            return true;
        }
        return false;
    }

    protected void skipOp(TextScanner sc) {
        sc.consumePeekToken();
        sc.skipBlank();
    }

    public void mustMatch(TextScanner sc, XLangOperator op) {
        if (!mayMatch(sc, op))
            throw sc.newError(ERR_EXPR_NOT_EXPECTED_OP).param(ARG_EXPECTED, op);
    }

    protected XLangOperator peekOperator(TextScanner sc) {
        if (sc.peekToken != null) {
            return (XLangOperator) sc.peekToken;
        }

        switch (sc.cur) {
            case '>':
                _peekGt(sc);
                break;
            case '<':
                _peekLt(sc);
                break;
            case '=':
                _peekEq(sc);
                break;
            case '!':
                _peekNot(sc);
                break;
            case '+':
                _peekPlus(sc);
                break;
            case '-':
                _peekMinus(sc);
                break;
            case '*':
                _peekMultiply(sc);
                break;
            case '/':
                _peekDivide(sc);
                break;
            case '%':
                _peekMod(sc);
                break;
            case '&':
                _peekAnd(sc);
                break;
            case '|':
                _peekOr(sc);
                break;
            case '^':
                _peekXor(sc);
                break;
            case '~':
                _peekBitNot(sc);
                break;
            case '?':
                _peekQuestion(sc);
                break;
            default:
                return null;
        }
        return (XLangOperator) sc.peekToken;
    }

    private void _peekGt(TextScanner sc) {
        int next = sc.peek();
        if (next == '>') {
            int next2 = sc.peek(2);
            if (next2 == '>') {
                if (sc.peek(3) == '=') {
                    sc.setPeekToken(XLangOperator.SELF_ASSIGN_UNSIGNED_RIGHT_SHIFT, 4);
                } else {
                    // >>>
                    sc.setPeekToken(XLangOperator.BIT_UNSIGNED_RIGHT_SHIFT, 3);
                }
            } else if (next2 == '=') {
                // >>=
                sc.setPeekToken(XLangOperator.SELF_ASSIGN_RIGHT_SHIFT, 3);
            } else {
                // >>
                sc.setPeekToken(XLangOperator.BIT_RIGHT_SHIFT, 2);
            }
        } else if (next == '=') {
            // >=
            sc.setPeekToken(XLangOperator.GE, 2);
        } else {
            sc.setPeekToken(XLangOperator.GT, 1);
        }
    }

    protected void _peekLt(TextScanner sc) {
        int next = sc.peek();
        if (next == '<') {
            int next2 = sc.peek(2);
            if (next2 == '=') {
                // <<=
                sc.setPeekToken(XLangOperator.SELF_ASSIGN_LEFT_SHIFT, 3);
            } else if (next2 == '<') {
                // <<<
                throw sc.newError(ERR_EXPR_UNSUPPORTED_OP).param(ARG_OP, "<<<");
            } else {
                // <<
                sc.setPeekToken(XLangOperator.BIT_LEFT_SHIFT, 2);
            }
        } else if (next == '=') {
            // <=
            sc.setPeekToken(XLangOperator.LE, 2);
        } else {
            sc.setPeekToken(XLangOperator.LT, 1);
        }
    }

    protected void _peekEq(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            if (sc.peek(2) == '=')
                throw sc.newError(ERR_EXPR_UNSUPPORTED_OP).param(ARG_OP, "===");
            sc.setPeekToken(XLangOperator.EQ, 2);
        } else if (next == '>') {
            sc.setPeekToken(XLangOperator.ARROW, 2);
        } else {
            sc.setPeekToken(XLangOperator.ASSIGN, 1);
        }
    }

    protected void _peekNot(TextScanner sc) {
        if (sc.peek() == '=') {
            sc.setPeekToken(XLangOperator.NE, 2);
        } else {
            sc.setPeekToken(XLangOperator.NOT, 1);
        }
    }

    protected void _peekAnd(TextScanner sc) {
        int next = sc.peek();
        if (next == '&') {
            // &&
            sc.setPeekToken(XLangOperator.AND, 2);
        } else if (next == '=') {
            // &=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_BIT_AND, 2);
        } else {
            sc.setPeekToken(XLangOperator.BIT_AND, 1);
        }
    }

    protected void _peekOr(TextScanner sc) {
        int next = sc.peek();
        if (next == '|') {
            // ||
            sc.setPeekToken(XLangOperator.OR, 2);
        } else if (next == '=') {
            // |=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_BIT_OR, 2);
        } else {
            sc.setPeekToken(XLangOperator.BIT_OR, 1);
        }
    }

    protected void _peekXor(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // ^=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_BIT_XOR, 2);
        } else {
            sc.setPeekToken(XLangOperator.BIT_XOR, 1);
        }
    }

    protected void _peekPlus(TextScanner sc) {
        int next = sc.peek();
        if (next == '+') {
            // ++
            sc.setPeekToken(XLangOperator.SELF_INC, 2);
        } else if (next == '=') {
            // +=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_ADD, 2);
        } else {
            sc.setPeekToken(XLangOperator.ADD, 1);
        }
    }

    protected void _peekMinus(TextScanner sc) {
        int next = sc.peek();
        if (next == '-') {
            // --
            sc.setPeekToken(XLangOperator.SELF_DEC, 2);
        } else if (next == '=') {
            // -=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_MINUS, 2);
        } else {
            sc.setPeekToken(XLangOperator.MINUS, 1);
        }
    }

    protected void _peekMultiply(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // *=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_MULTI, 2);
        } else {
            sc.setPeekToken(XLangOperator.MULTIPLY, 1);
        }
    }

    protected void _peekDivide(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // /=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_DIV, 2);
        } else {
            sc.setPeekToken(XLangOperator.DIVIDE, 1);
        }
    }

    protected void _peekMod(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // %=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_MOD, 2);
        } else {
            sc.setPeekToken(XLangOperator.MOD, 1);
        }
    }

    protected void _peekBitNot(TextScanner sc) {
        int next = sc.peek();
        if (next == '=') {
            // ~=
            sc.setPeekToken(XLangOperator.SELF_ASSIGN_BIT_NOT, 2);
        } else {
            sc.setPeekToken(XLangOperator.BIT_NOT, 1);
        }
    }

    protected void _peekQuestion(TextScanner sc) {
        int next = sc.peek();
        if (next == '?') {
            // ~=
            sc.setPeekToken(XLangOperator.NULL_COALESCE, 2);
        } else {
            sc.setPeekToken(XLangOperator.QUESTION, 1);
        }
    }

    protected void checkLeftValue(TextScanner sc, String op, E x) {
        if (x == null) {
            throw sc.newError(ERR_EXPR_BINARY_OP_NO_LEFT_VALUE).param(ARG_OP, op);
        }
    }

    protected void checkFactor(TextScanner sc, String op, E x) {
        if (x == null)
            throw sc.newError(ERR_EXPR_NULL_FACTOR).param(ARG_OP, op);
    }

    protected void checkRightValue(TextScanner sc, String op, E y) {
        if (y == null) {
            throw sc.newError(ERR_EXPR_BINARY_OP_NO_RIGHT_VALUE).param(ARG_OP, op);
        }
    }

    protected void checkUnaryExpr(TextScanner sc, XLangOperator op, E expr) {
        if (expr == null)
            throw sc.newError(ERR_EXPR_UNARY_OP_NO_EXPR).param(ARG_OP, op.name());
    }

    protected void checkCondition(TextScanner sc, boolean b) {
        if (!b)
            throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
    }

    protected boolean consumeOrOp(TextScanner sc) {
        if (sc.tryMatchToken("or")) {
            return true;
        }
        return mayMatch(sc, XLangOperator.OR);
    }

    protected boolean consumeAndOp(TextScanner sc) {
        if (sc.tryMatchToken("and")) {
            return true;
        }
        return mayMatch(sc, XLangOperator.AND);
    }

}
