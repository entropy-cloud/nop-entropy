/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.output;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.GenNodeAttrExpression;
import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.XLangParseBuffer;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.xpl.output.OutputParseHelper.getTagNameExpr;

public class NodeOutputTagCompiler implements IXplUnknownTagCompiler {
    public static final NodeOutputTagCompiler INSTANCE = new NodeOutputTagCompiler();

    @Override
    public void parseContent(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        Expression expr = XplParseHelper.parseContentTemplate(node, cp, scope);
        buf.add(GenNodeExpression.genTextNode(expr));
    }

    @Override
    public void parseTag(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        if (node.isTextNode()) {
            parseContent(buf, node, cp, scope);
            return;
        }

        boolean xplNs = scope.isNsEnabled(XplConstants.XPL_NS);
        Expression tagName = getTagNameExpr(node, xplNs, cp, scope);
        List<GenNodeAttrExpression> attrExprs = genAttrExprs(xplNs, node, cp, scope);
        Expression extAttrExpr = parseExtAttrs(node, cp, scope);
        Expression bodyExpr = cp.parseTagBody(node, scope);

        buf.add(GenNodeExpression.valueOf(node.getLocation(), tagName, attrExprs, extAttrExpr, bodyExpr));
    }

    private Expression parseExtAttrs(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        ValueWithLocation attrs = node.attrValueLoc(XplConstants.ATTR_XPL_ATTRS);
        if (attrs.isNull())
            return null;

        return XplParseHelper.parseAttrSimpleExpr(node, XplConstants.ATTR_XPL_ATTRS, cp, scope);
    }

    private List<GenNodeAttrExpression> genAttrExprs(boolean xplNs, XNode node, IXplCompiler cp,
                                                     IXLangCompileScope scope) {
        List<GenNodeAttrExpression> ret = new ArrayList<>(node.getAttrCount());
        node.forEachAttr((name, value) -> {
            if (xplNs) {
                if (StringHelper.startsWithNamespace(name, XplConstants.XPL_NS))
                    return;
            }
            Expression expr = XplParseHelper.parseAttrTemplateExpr(node, name, cp, scope);
            ret.add(GenNodeAttrExpression.valueOf(value.getLocation(), name, expr));
        });
        return ret;
    }
}