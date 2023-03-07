/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.GenNodeAttrExpression;
import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_OUTPUT_MODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_ALLOW_OUTPUT;

public class PrintTagCompiler implements IXplTagCompiler {
    public static final PrintTagCompiler INSTANCE = new PrintTagCompiler();

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        switch (scope.getOutputMode()) {
            case node:
                return bodyToExpression(node);
            case html:
                String html = node.innerHtml();
                return TextOutputExpression.valueOf(node.content().getLocation(), html);
            case xml:
            case text:
                String xml = node.innerXml();
                return TextOutputExpression.valueOf(node.content().getLocation(), xml);
            // case json:
            // if (node.getChildCount() == 1)
            // return nodeToJson(node.child(0));
            // return childrenToJson(node);
            default:
                throw new NopEvalException(ERR_XPL_NOT_ALLOW_OUTPUT).param(ARG_OUTPUT_MODE, scope.getOutputMode())
                        .param(ARG_NODE, node);
        }
    }

    Expression nodeToJson(XNode node) {
        String json = node.jsonText();
        return TextOutputExpression.valueOf(node.getLocation(), json);
    }

    Expression childrenToJson(XNode node) {
        String json = node.childrenToJson("  ");
        return TextOutputExpression.valueOf(node.getLocation(), json);
    }

    Expression bodyToExpression(XNode node) {
        if (!node.hasChild()) {
            if (!node.hasContent())
                return null;
            Expression expr = valueToExpr(node.content());
            return GenNodeExpression.genTextNode(expr);
        }
        return childrenToExpr(node);
    }

    Expression childrenToExpr(XNode node) {
        if (node.getChildCount() == 1) {
            return nodeToExpression(node.child(0));
        }

        List<Expression> list = new ArrayList<>(node.getChildCount());
        for (XNode child : node.getChildren()) {
            list.add(nodeToExpression(child));
        }
        return SequenceExpression.valueOf(node.getLocation(), list);
    }

    GenNodeExpression nodeToExpression(XNode node) {
        if (node.isTextNode()) {
            return GenNodeExpression.genTextNode(valueToExpr(node.content()));
        }
        GenNodeExpression ret = new GenNodeExpression();
        ret.setLocation(node.getLocation());
        ret.setTagName(Literal.valueOf(node.getLocation(), node.getTagName()));

        if (node.getAttrCount() > 0) {
            List<GenNodeAttrExpression> attrs = new ArrayList<>(node.getAttrCount());
            node.forEachAttr((name, value) -> {
                GenNodeAttrExpression expr = new GenNodeAttrExpression();
                expr.setLocation(value.getLocation());
                expr.setValue(valueToExpr(value));
                attrs.add(expr);
            });
            ret.setAttrs(attrs);
        } else {
            ret.setAttrs(Collections.emptyList());
        }

        if (!node.hasChild()) {
            if (node.hasContent()) {
                ret.setBody(valueToExpr(node.content()));
            }
        } else {
            if (node.getChildCount() == 1) {
                ret.setBody(nodeToExpression(node.child(0)));
            } else {
                List<Expression> list = new ArrayList<>(node.getChildCount());
                for (XNode child : node.getChildren()) {
                    list.add(nodeToExpression(child));
                }
                ret.setBody(SequenceExpression.valueOf(null, list));
            }
        }

        return ret;
    }

    Expression valueToExpr(ValueWithLocation value) {
        Object v = value.getValue();
        if (v == null) {
            return Literal.nullValue(value.getLocation());
        }

        if (v instanceof Expression)
            return (Expression) v;

        return Literal.valueOf(value.getLocation(), value.getValue());
    }
}