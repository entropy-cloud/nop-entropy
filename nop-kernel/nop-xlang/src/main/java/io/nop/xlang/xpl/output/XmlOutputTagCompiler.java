/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.output;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.XLangParseBuffer;
import io.nop.xlang.xpl.XplConstants;

import static io.nop.xlang.xpl.output.OutputParseHelper.getTagNameExpr;
import static io.nop.xlang.xpl.output.OutputParseHelper.outputAttrs;
import static io.nop.xlang.xpl.output.OutputParseHelper.outputBeginTagName;
import static io.nop.xlang.xpl.output.OutputParseHelper.outputContent;
import static io.nop.xlang.xpl.output.OutputParseHelper.outputDocType;
import static io.nop.xlang.xpl.output.OutputParseHelper.outputEndTagName;

public class XmlOutputTagCompiler implements IXplUnknownTagCompiler {
    public static final XmlOutputTagCompiler INSTANCE = new XmlOutputTagCompiler();

    @Override
    public void parseContent(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        outputContent(buf, node, cp, scope);
    }


    @Override
    public void parseTag(XLangParseBuffer buf, XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        if (node.isTextNode()) {
            outputContent(buf, node, cp, scope);
            return;
        }

        boolean xplNs = scope.isNsEnabled(XplConstants.XPL_NS);
        Expression tagName = getTagNameExpr(node, xplNs, cp, scope);

        outputDocType(buf, node);
        outputBeginTagName(buf, node.getLocation(), tagName, xplNs);
        outputAttrs(buf, node, xplNs, cp, scope);

        if (node.hasBody()) {
            buf.append(node.getLocation(), ">");
            if (node.hasChild()) {
                cp.parseTagBody(buf, node, scope);
            } else {
                outputContent(buf, node, cp, scope);
            }
            outputEndTagName(buf, node.getLocation(), tagName);
        } else {
            if (!isUseShortTagName(node)) {
                buf.append(">");
                outputEndTagName(buf, node.getLocation(), tagName);
            } else {
                buf.append(node.getLocation(), "/>");
            }
        }
    }

    protected boolean isUseShortTagName(XNode node) {
        return true;
    }
}
