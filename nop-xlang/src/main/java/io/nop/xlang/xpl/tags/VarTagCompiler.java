/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.List;

import static io.nop.xlang.xpl.XplConstants.NAME_NAME;
import static io.nop.xlang.xpl.XplConstants.TYPE_NAME;
import static io.nop.xlang.xpl.XplConstants.VALUE_NAME;
import static java.util.Arrays.asList;

public class VarTagCompiler implements IXplTagCompiler {
    public static final VarTagCompiler INSTANCE = new VarTagCompiler();

    static final List<String> ATTR_NAMES = asList(NAME_NAME, TYPE_NAME, VALUE_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        XplParseHelper.checkArgNames(node, ATTR_NAMES);
        Identifier varName = XplParseHelper.getAttrIdentifier(node, NAME_NAME, cp, scope);
        Expression valueExpr = XplParseHelper.parseAttrExpr(node, VALUE_NAME, cp, scope);
        NamedTypeNode typeNode = null;
        return XLangASTBuilder.let(varName.getLocation(), varName, typeNode, valueExpr);
    }
}
