/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.CatchClause;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_TYPE_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_INVALID_TYPE_REF;
import static io.nop.xlang.ast.XLangASTBuilder.catchClause;
import static io.nop.xlang.ast.XLangASTBuilder.tryStatement;
import static io.nop.xlang.ast.XLangASTBuilder.typeName;
import static io.nop.xlang.xpl.XplConstants.BODY_NAME;
import static io.nop.xlang.xpl.XplConstants.CATCH_NAME;
import static io.nop.xlang.xpl.XplConstants.FINALLY_NAME;
import static io.nop.xlang.xpl.XplConstants.NAME_NAME;
import static io.nop.xlang.xpl.XplConstants.TYPE_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkSlotNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrIdentifier;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrLiteral;
import static io.nop.xlang.xpl.utils.XplParseHelper.getSlot;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireSlot;
import static java.util.Arrays.asList;

public class TryTagCompiler implements IXplTagCompiler {
    public static TryTagCompiler INSTANCE = new TryTagCompiler();

    static final List<String> SLOT_NAMES = asList(BODY_NAME, CATCH_NAME, FINALLY_NAME);
    static final List<String> CATCH_ATTR_NAMES = asList(NAME_NAME, TYPE_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkSlotNames(node, SLOT_NAMES);

        XNode bodyNode = requireSlot(node, BODY_NAME);
        XNode catchNode = node.childByTag(CATCH_NAME);
        XNode finallyNode = getSlot(node, FINALLY_NAME);

        Expression body = cp.parseTagBody(bodyNode, scope);

        CatchClause handler = compileCatch(catchNode, cp, scope);

        Expression finalizer = null;
        if (finallyNode != null)
            finalizer = cp.parseTagBody(finallyNode, scope);
        return tryStatement(node.getLocation(), body, handler, finalizer);
    }

    CatchClause compileCatch(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, CATCH_ATTR_NAMES);
        Identifier name = getAttrIdentifier(node, NAME_NAME, cp, scope);
        NamedTypeNode type = name == null ? null : typeName(name.getLocation(), name.getName());
        Expression body = cp.parseTagBody(node, scope);
        return catchClause(node.getLocation(), name, type, body);
    }

    List<NamedTypeNode> getTypes(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        Literal typesLiteral = getAttrLiteral(node, TYPE_NAME, cp, scope);
        List<String> types = ConvertHelper.toCsvList(typesLiteral.getStringValue(), NopEvalException::new);
        if (types == null || types.isEmpty())
            return null;
        SourceLocation loc = typesLiteral.getLocation();
        List<NamedTypeNode> ret = new ArrayList<>();
        for (String type : types) {
            if (!StringHelper.isValidClassName(type))
                throw new NopEvalException(ERR_XPL_INVALID_TYPE_REF).param(ARG_TYPE_NAME, type).param(ARG_NODE, node);
            ret.add(typeName(loc, type));
        }
        return ret;
    }
}