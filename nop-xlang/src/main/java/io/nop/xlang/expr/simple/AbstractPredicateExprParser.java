/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr.simple;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.ast.XLangOperator;

import static io.nop.xlang.XLangErrors.ERR_EXPR_UNEXPECTED_CHAR;

public abstract class AbstractPredicateExprParser<E> extends AbstractExprParser<E> {
    protected E inclusiveOrExpr(TextScanner sc) {
        return equalityExpr(sc);
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
        if (sc.cur == '\'' || sc.cur == '\"') {
            E x = stringExpr(sc);
            return x;
        }

        SourceLocation loc = sc.location();
        if (sc.cur == '(') {
            E x = orExpr(sc);
            if (x == null)
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            sc.match(')');
            return newBraceExpr(loc, x);
        }

        if (StringHelper.isDigit(sc.cur)) {
            E x = numberExpr(sc);
            if (sc.cur == '[' || sc.cur == '(' || sc.cur == '.')
                throw sc.newError(ERR_EXPR_UNEXPECTED_CHAR);
            return x;
        }

        if (Character.isJavaIdentifierStart(sc.cur)) {
            if (sc.tryMatchToken("true"))
                return newLiteralExpr(loc, true);

            if (sc.tryMatchToken("false")) {
                return newLiteralExpr(loc, false);
            }

            if (sc.tryMatchToken("null")) {
                return newLiteralExpr(loc, null);
            }

            return tokenExpr(sc);
        }

        return defaultFactorExpr(sc);
    }

    protected abstract E newUnaryExpr(SourceLocation loc, XLangOperator op, E x);

    protected E newBraceExpr(SourceLocation loc, E x) {
        return x;
    }

    protected abstract E tokenExpr(TextScanner sc);
}