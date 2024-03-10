/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.output;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.RawText;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.OutputXmlAttrExpression;
import io.nop.xlang.ast.OutputXmlExtAttrsExpression;
import io.nop.xlang.ast.TemplateExpression;
import io.nop.xlang.ast.XLangEscapeMode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.XLangParseBuffer;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.HashSet;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_DISALLOW_OUTPUT_INVALID_XML_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_ATTR;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrTemplateExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseContentTemplate;

public class OutputParseHelper {

    public static void outputDocType(XLangParseBuffer buf, XNode node) {
        String docType = node.getDocType();
        if (docType != null) {
            buf.append(node.getLocation(), "<!DOCTYPE " + docType + ">\n");
        }
        String instruction = node.getInstruction();
        if (instruction != null) {
            buf.append(node.getLocation(), "<?" + instruction + "?>\n");
        }
    }

    public static void outputBeginTagName(XLangParseBuffer buf, SourceLocation loc, Expression tagNameExpr,
                                          boolean newLine) {
        if (newLine)
            buf.append('\n');

        String tagName = getTagName(tagNameExpr);
        if (tagName != null) {
            buf.append(loc, "<" + tagName);
        } else {
            buf.append(loc, "<");
            buf.add(escapeExpr(XLangEscapeMode.none, tagNameExpr));
        }
    }

    public static String getTagName(Expression expr) {
        if (expr instanceof Identifier)
            return ((Identifier) expr).getName();
        if (expr instanceof Literal) {
            String value = ((Literal) expr).getStringValue();
            if (!StringHelper.isValidXmlName(value))
                throw new NopEvalException(ERR_XPL_DISALLOW_OUTPUT_INVALID_XML_NAME).source(expr).param(ARG_TAG_NAME,
                        value);
            return value;
        }
        return null;
    }

    public static void outputEndTagName(XLangParseBuffer buf, SourceLocation loc, Expression tagNameExpr) {
        String tagName = getTagName(tagNameExpr);
        if (tagName != null) {
            buf.append(loc, "</" + tagName + ">");
        } else {
            buf.append(loc, "</");
            buf.add(escapeExpr(XLangEscapeMode.none, tagNameExpr));
            buf.append(loc, ">");
        }
    }

    public static EscapeOutputExpression escapeExpr(XLangEscapeMode escapeMode, Expression expr) {
        return EscapeOutputExpression.valueOf(expr.getLocation(), escapeMode, expr.deepClone());
    }

    // public static String getTagName(XNode node, boolean xplNs, IXplCompiler cp, IXLangCompileScope scope) {
    // if (!xplNs)
    // return node.getTagName();
    // Identifier xplIs = getAttrXmlName(node, XplConstants.ATTR_XPL_IS, cp, scope);
    // if (xplIs == null)
    // return node.getTagName();
    // return xplIs.getName();
    // }

    public static Expression getTagNameExpr(XNode node, boolean xplNs, IXplCompiler cp, IXLangCompileScope scope) {
        if (!xplNs)
            return Literal.valueOf(node.getLocation(), node.getTagName());
        Expression expr = parseAttrTemplateExpr(node, XplConstants.ATTR_XPL_IS, cp, scope);
        if (expr == null)
            expr = Literal.valueOf(node.getLocation(), node.getTagName());
        return expr;
    }

    public static void outputAttrExpr(XLangParseBuffer buf, SourceLocation loc, String name, Expression expr) {
        if (expr == null)
            return;

        if (expr instanceof Literal) {
            outputAttrValue(buf, loc, name, ((Literal) expr).getValue());
        } else {
            buf.add(OutputXmlAttrExpression.valueOf(loc, name, expr));
        }
    }

    public static void outputAttrValue(XLangParseBuffer buf, SourceLocation loc, String name, Object value) {
        if (value == null)
            return;

        buf.append(" ").append(name).append('=');
        buf.append('"');
        outputValue(buf, XLangEscapeMode.xmlAttr, loc, value);
        buf.append('"');
    }

    public static void outputContent(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        outputContent(buf, node, XLangEscapeMode.xmlValue, cp, scope);
    }

    public static void outputContent(XLangParseBuffer buf, XNode node, XLangEscapeMode escapeMode, IXplCompiler cp,
                                     IXLangCompileScope scope) {
        Expression expr = parseContentTemplate(node, cp, scope);
        outputExpr(buf, escapeMode, expr);
    }

    public static void outputExpr(XLangParseBuffer buf, XLangEscapeMode escapeMode, Expression valueExpr) {
        if (valueExpr == null)
            return;

        SourceLocation loc = valueExpr.getLocation();
        if (valueExpr instanceof Literal) {
            // 如果是常量表达式
            Object value = ((Literal) valueExpr).getValue();
            outputValue(buf, escapeMode, loc, value);
        } else if (valueExpr instanceof ConcatExpression) {
            ConcatExpression concat = (ConcatExpression) valueExpr;
            for (Expression subExpr : concat.getExpressions()) {
                subExpr.setASTParent(null);
                outputExpr(buf, escapeMode, subExpr);
            }
        } else if (valueExpr instanceof TemplateExpression) {
            TemplateExpression concat = (TemplateExpression) valueExpr;
            for (Expression subExpr : concat.getExpressions()) {
                subExpr.setASTParent(null);
                outputExpr(buf, escapeMode, subExpr);
            }
        } else {
            buf.add(EscapeOutputExpression.valueOf(loc, escapeMode, valueExpr.deepClone()));
        }
    }

    public static void outputValue(XLangParseBuffer buf, XLangEscapeMode escapeMode, SourceLocation loc, Object value) {
        if (value != null) {
            if (value instanceof RawText) {
                buf.append(loc, value.toString());
            } else {
                buf.append(escape(escapeMode, value.toString()));
            }
        }
    }

    static String escape(XLangEscapeMode mode, String value) {
        switch (mode) {
            case xmlValue:
                return StringHelper.escapeXmlValue(value);
            case xmlAttr:
                return StringHelper.escapeXmlAttr(value);
            case xml:
                return StringHelper.escapeXml(value);
            case sql:
                return StringHelper.escapeSql(value, true);
        }
        return value;
    }

    public static void outputAttrs(XLangParseBuffer buf, XNode node, boolean xplNs, IXplCompiler cp,
                                   IXLangCompileScope scope) {
        try {
            node.forEachAttr((name, value) -> {
                if (xplNs && StringHelper.startsWithNamespace(name, XplConstants.XPL_NS)) {
                    if (!XplConstants.XPL_ATTRS.contains(name))
                        throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_ATTR).loc(node.attrLoc(name))
                                .param(ARG_ATTR_NAME, name).param(ARG_TAG_NAME, node.getTagName())
                                .param(ARG_ALLOWED_NAMES, XplConstants.XPL_ATTRS);
                    return;
                }

                // xpl:disableNs和xpl:enableNs总是被处理？
                if (name.equals(XplConstants.ATTR_XPL_ENABLE_NS) || name.equals(XplConstants.ATTR_XPL_DISABLE_NS))
                    return;

                if (XplParseHelper.hasExpr(value.asString())) {
                    Expression expr = XplParseHelper.parseTemplateExpr(value, cp, scope);
                    outputAttrExpr(buf, value.getLocation(), name, expr);
                } else {
                    outputAttrValue(buf, value.getLocation(), name, value.getValue());
                }
            });
        } catch (NopException e) {
            e.addXplStack(node);
            throw e;
        }

        ValueWithLocation attrs = node.attrValueLoc(XplConstants.ATTR_XPL_ATTRS);
        if (xplNs && !attrs.isNull()) {
            // if (!XplParseHelper.isExpr(attrs.asString()))
            // throw new NopEvalException(ERR_XPL_INVALID_ATTR_EXPR)
            // .loc(attrs.getLocation())
            // .param(ARG_ATTR_NAME, XplConstants.ATTR_XPL_ATTRS)
            // .param(ARG_NODE, node);
            Expression expr = XplParseHelper.parseAttrSimpleExpr(node, XplConstants.ATTR_XPL_ATTRS, cp, scope);
            Set<String> excludeNames = new HashSet<>();
            for (String name : node.getAttrNames()) {
                if (!StringHelper.startsWithNamespace(name, XplConstants.XPL_NS)) {
                    excludeNames.add(name);
                }
            }
            OutputXmlExtAttrsExpression out = OutputXmlExtAttrsExpression.valueOf(attrs.getLocation(), excludeNames,
                    expr);
            buf.add(out);
        }
    }
}