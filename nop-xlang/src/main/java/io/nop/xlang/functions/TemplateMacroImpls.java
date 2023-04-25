/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.functions;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.jpath.JPath;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.query.OrderBySqlParser;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.TemplateStringLiteral;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.xpath.XPathHelper;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.xlib.XplLibTagCompiler;
import io.nop.xlang.xpl.xlib.XplTag;

import java.util.Collections;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_MAX_COUNT;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_MACRO_FUNC_ARG_MUST_BE_TEMPLATE_LITERAL_OR_STRING_LITERAL;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_CUSTOM_TAG_FUNC;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_FUNC_TOO_MAY_ARGS;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_NAME;

public class TemplateMacroImpls {
    public static String getTemplateLiteralArg(CallExpression expr) {
        if (expr.getArguments().size() != 1) {
            throw new NopEvalException(ERR_MACRO_FUNC_ARG_MUST_BE_TEMPLATE_LITERAL_OR_STRING_LITERAL).param(ARG_EXPR,
                    expr);
        }
        Expression arg = expr.getArgument(0);
        if (arg instanceof TemplateStringLiteral)
            return ((TemplateStringLiteral) arg).getStringValue();
        if (arg instanceof Literal) {
            Object value = ((Literal) arg).getValue();
            if (value instanceof String)
                return value.toString();
        }
        throw new NopEvalException(ERR_MACRO_FUNC_ARG_MUST_BE_TEMPLATE_LITERAL_OR_STRING_LITERAL).param(ARG_EXPR, expr);
    }

    public static Expression xpl(IXLangCompileScope scope, CallExpression expr) {
        if (expr.getArguments().size() >= 1) {
            Expression arg = expr.getArgument(0);
            if (arg instanceof Literal) {
                String name = ((Literal) arg).getStringValue();
                // 如果第一个参数是合法的标签名，则认为是标签调用
                if (StringHelper.isValidXmlName(name)) {
                    return callTag(name, scope, expr);
                }
            }
        }
        String tpl = getTemplateLiteralArg(expr);
        if (StringHelper.isBlank(tpl))
            return Literal.nullValue(expr.getLocation());

        XNode node = XNodeParser.instance().forFragments(true).parseFromText(expr.getArgument(0).getLocation(), tpl);
        return scope.getCompiler().parseTagBody(node, scope);
    }

    private static Expression callTag(String tagName, IXLangCompileScope scope, CallExpression expr) {
        IXplTagCompiler tagCompiler = scope.getTagCompiler(tagName);
        if (tagCompiler == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_NAME).source(expr).param(ARG_TAG_NAME, tagName);
        if (!(tagCompiler instanceof XplLibTagCompiler))
            throw new NopEvalException(ERR_XPL_NOT_CUSTOM_TAG_FUNC).source(expr).param(ARG_TAG_NAME, tagName);

        XplLibTagCompiler libTag = (XplLibTagCompiler) tagCompiler;
        XNode node = buildTagNode(libTag.getTag(), tagName, expr);
        return libTag.parseTag(node, scope.getCompiler(), scope);
    }

    private static XNode buildTagNode(XplTag tag, String tagName, CallExpression expr) {
        XNode node = XNode.make(tagName);
        node.setLocation(expr.getLocation());

        if (expr.getArguments().size() == 2) {
            Expression argExpr = expr.getArgument(1);
            if (argExpr.getASTKind() == XLangASTKind.ObjectExpression) {
                ObjectExpression objExpr = (ObjectExpression) argExpr;
                if (objExpr.isPropMap()) {
                    for (XLangASTNode prop : objExpr.getProperties()) {
                        PropertyAssignment assign = (PropertyAssignment) prop;
                        String name = ((Literal) assign.getKey()).getStringValue();
                        node.setAttr(prop.getLocation(), name, assign.getValue());
                    }
                    return node;
                }
            }
        }

        if (tag.getAttrs().size() < expr.getArguments().size() - 1) {
            throw new NopEvalException(ERR_XPL_TAG_FUNC_TOO_MAY_ARGS).source(expr).param(ARG_TAG_NAME, tagName)
                    .param(ARG_MAX_COUNT, tag.getAttrs().size());
        }
        for (int i = 1, n = expr.getArguments().size(); i < n; i++) {
            Expression argExpr = expr.getArgument(i);
            node.setAttr(argExpr.getLocation(), tag.getAttrs().get(i - 1).getName(), argExpr);
        }
        return node;
    }

    public static Expression tpl(IXLangCompileScope scope, CallExpression expr) {
        String tpl = getTemplateLiteralArg(expr);
        return scope.getCompiler().parseTemplateExpr(expr.getArgument(0).getLocation(), tpl, false, ExprPhase.eval,
                scope);
    }

    public static Expression xml(IXLangCompileScope scope, CallExpression expr) {
        String tpl = getTemplateLiteralArg(expr);
        Expression arg = expr.getArgument(0);
        XNode node = XNodeParser.instance().parseFromText(arg.getLocation(), tpl);
        node.freeze(true);
        return Literal.valueOf(arg.getLocation(), node);
    }

    public static Expression jpath(IXLangCompileScope scope, CallExpression expr) {
        String tpl = getTemplateLiteralArg(expr);
        if (StringHelper.isEmpty(tpl))
            return Literal.nullValue(expr.getLocation());
        return Literal.valueOf(expr.getArgument(0).getLocation(), JPath.compileWithCache(tpl));
    }

    public static Expression xpath(IXLangCompileScope scope, CallExpression expr) {
        String tpl = getTemplateLiteralArg(expr);
        if (StringHelper.isEmpty(tpl))
            return Literal.nullValue(expr.getLocation());
        return Literal.valueOf(expr.getArgument(0).getLocation(), XPathHelper.parseXSelector(tpl));
    }

    public static Expression selection(IXLangCompileScope scope, CallExpression expr) {
        String tpl = getTemplateLiteralArg(expr);
        if (StringHelper.isEmpty(tpl))
            return Literal.nullValue(expr.getLocation());
        SourceLocation loc = expr.getArgument(0).getLocation();
        FieldSelectionBean selection = new FieldSelectionBeanParser().parseFromText(loc, tpl);
        selection.freeze(true);
        return Literal.valueOf(loc, selection);
    }

    public static Expression order_by(IXLangCompileScope scope, CallExpression expr) {
        String tpl = getTemplateLiteralArg(expr);
        if (StringHelper.isEmpty(tpl))
            return Literal.nullValue(expr.getLocation());
        SourceLocation loc = expr.getArgument(0).getLocation();
        List<OrderFieldBean> orderBy = new OrderBySqlParser().parseFromText(loc, tpl);
        return Literal.valueOf(loc, Collections.unmodifiableList(orderBy));
    }
}
