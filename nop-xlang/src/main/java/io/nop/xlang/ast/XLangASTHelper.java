/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.MathHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ERR_XLANG_EXPR_NOT_JSON_VALUE;

public class XLangASTHelper {
    public static boolean allowMandatoryChain(Expression x) {
        switch (x.getASTKind()) {
            case Identifier:
            case MemberExpression:
            case CallExpression:
            case BraceExpression:
                return true;
        }
        return false;
    }

    /**
     * 是否允许作为MemberExpression的obj部分
     */
    public static boolean allowMember(Expression x) {
        switch (x.getASTKind()) {
            case Identifier:
            case Literal:
            case MemberExpression:
            case CallExpression:
            case ChainExpression:
            case BraceExpression:
            case CustomExpression:
                return true;
            default:
        }
        return false;
    }

    public static boolean allowCall(Expression x) {
        switch (x.getASTKind()) {
            case Identifier:
            case MemberExpression:
            case CallExpression:
            case ChainExpression:
                return true;
            default:
        }
        return false;
    }

    public static boolean isQualifiedName(Expression expr) {
        XLangASTKind kind = expr.getASTKind();
        if (kind == XLangASTKind.Identifier)
            return true;

        if (kind == XLangASTKind.MemberExpression) {
            MemberExpression member = (MemberExpression) expr;
            if (member.getComputed())
                return false;

            if (!isQualifiedName(member.getObject()))
                return false;

            return isQualifiedName(member.getProperty());
        }

        return false;
    }

    public static boolean isJsonValue(XLangASTNode expr) {
        XLangASTKind kind = expr.getASTKind();
        if (kind == XLangASTKind.Literal)
            return true;

        if (kind == XLangASTKind.UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            if (unary.getOperator() != XLangOperator.MINUS)
                return false;
            return isJsonValue(unary.getArgument());
        }

        if (kind == XLangASTKind.ArrayExpression) {
            ArrayExpression array = (ArrayExpression) expr;
            for (XLangASTNode elm : array.getElements()) {
                if (!isJsonValue(elm))
                    return false;
            }
            return true;
        }

        if (kind == XLangASTKind.ObjectExpression) {
            ObjectExpression obj = (ObjectExpression) expr;
            for (XLangASTNode elm : obj.getProperties()) {
                if (elm.getASTKind() == XLangASTKind.PropertyAssignment) {
                    PropertyAssignment prop = (PropertyAssignment) elm;
                    if (prop.getComputed())
                        return false;

                    if (prop.getKey().getASTKind() != XLangASTKind.Literal)
                        return false;
                    return isJsonValue(prop.getValue());
                } else {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    public static Object toJsonValue(XLangASTNode expr) {
        XLangASTKind kind = expr.getASTKind();
        if (kind == XLangASTKind.Literal)
            return ((Literal) expr).getValue();

        if (kind == XLangASTKind.UnaryExpression) {
            UnaryExpression unary = (UnaryExpression) expr;
            if (unary.getOperator() != XLangOperator.MINUS)
                throw new NopException(ERR_XLANG_EXPR_NOT_JSON_VALUE).source(expr);

            return MathHelper.neg(toJsonValue(unary.getArgument()));
        }

        if (kind == XLangASTKind.ArrayExpression) {
            ArrayExpression array = (ArrayExpression) expr;

            List<Object> ret = new ArrayList<>(array.getElements().size());

            for (XLangASTNode elm : array.getElements()) {
                ret.add(toJsonValue(elm));
            }
            return ret;
        }

        if (kind == XLangASTKind.ObjectExpression) {
            ObjectExpression obj = (ObjectExpression) expr;
            Map<String, Object> ret = new LinkedHashMap<>();
            for (XLangASTNode elm : obj.getProperties()) {
                if (elm.getASTKind() == XLangASTKind.PropertyAssignment) {
                    PropertyAssignment prop = (PropertyAssignment) elm;
                    if (prop.getComputed())
                        throw new NopException(ERR_XLANG_EXPR_NOT_JSON_VALUE).source(prop);

                    if (prop.getKey().getASTKind() != XLangASTKind.Literal) {
                        throw new NopException(ERR_XLANG_EXPR_NOT_JSON_VALUE).source(prop);
                    }

                    String name = ((Literal) prop.getKey()).getStringValue();
                    Object value = toJsonValue(prop.getValue());
                    ret.put(name, value);
                } else {
                    throw new NopException(ERR_XLANG_EXPR_NOT_JSON_VALUE).source(elm);
                }
            }
            return ret;
        }

        throw new NopException(ERR_XLANG_EXPR_NOT_JSON_VALUE).source(expr);
    }
}