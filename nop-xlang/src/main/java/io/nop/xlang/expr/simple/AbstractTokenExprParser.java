package io.nop.xlang.expr.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.xlang.ast.XLangOperator;

import static io.nop.xlang.XLangErrors.ERR_EXPR_UNEXPECTED_CHAR;

public abstract class AbstractTokenExprParser<E> extends AbstractExprParser<E> {

    protected E inclusiveOrExpr(TextScanner sc) {
        return unaryExpr(sc);
    }

    @Override
    protected E shiftExpr(TextScanner sc) {
        return unaryExpr(sc);
    }

    protected E unaryExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        if (mayMatch(sc, XLangOperator.NOT)) {
            E x = unaryExpr(sc);
            checkUnaryExpr(sc, XLangOperator.NOT, x);
            return newUnaryExpr(loc, XLangOperator.NOT, x);
        } else {
            E x = factorExpr(sc);
            return x;
        }
    }

    protected E factorExpr(TextScanner sc) {
        SourceLocation loc = sc.location();
        if (sc.cur == '(') {
            E x = orExpr(sc);
            if (x == null)
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            sc.match(')');
            return newBraceExpr(loc, x);
        }

        if (Character.isJavaIdentifierStart(sc.cur)) {
            return tokenExpr(sc);
        }

        return defaultFactorExpr(sc);
    }

    protected E newBraceExpr(SourceLocation loc, E x) {
        return x;
    }

    protected abstract E tokenExpr(TextScanner sc);

    @Override
    protected E newBinaryExpr(SourceLocation loc, XLangOperator op, E x, E y) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected E newLiteralExpr(SourceLocation loc, Object value) {
        throw new UnsupportedOperationException();
    }

    protected abstract E newUnaryExpr(SourceLocation loc, XLangOperator op, E x);
}