/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.ThrowStatement;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.Collections;
import java.util.List;

import static io.nop.xlang.xpl.XplConstants.BIZ_FATAL_NAME;
import static io.nop.xlang.xpl.XplConstants.CAUSE_NAME;
import static io.nop.xlang.xpl.XplConstants.ERROR_CODE_NAME;
import static io.nop.xlang.xpl.XplConstants.ERROR_DESCRIPTION_NAME;
import static io.nop.xlang.xpl.XplConstants.ERROR_STATUS_NAME;
import static io.nop.xlang.xpl.XplConstants.PARAMS_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrBool;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrExprOrInt;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrTemplateExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrLiteral;
import static java.util.Arrays.asList;

public class ThrowTagCompiler implements IXplTagCompiler {
    public static final ThrowTagCompiler INSTANCE = new ThrowTagCompiler();

    static final List<String> ATTR_NAMES = asList(ERROR_CODE_NAME, PARAMS_NAME, CAUSE_NAME,
            ERROR_DESCRIPTION_NAME, BIZ_FATAL_NAME, ERROR_STATUS_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);

        Literal code = requireAttrLiteral(node, ERROR_CODE_NAME, cp, scope);
        Expression params = parseAttrExpr(node, PARAMS_NAME, cp, scope);
        if (params == null) {
            params = Literal.nullValue(node.getLocation());
        }

        Expression cause = parseAttrExpr(node, CAUSE_NAME, cp, scope);
        if (cause == null)
            cause = Literal.nullValue(node.getLocation());

        Expression errorDescription = parseAttrTemplateExpr(node, ERROR_DESCRIPTION_NAME, cp, scope);
        Boolean bizFatal = getAttrBool(node, BIZ_FATAL_NAME);
        Expression errorStatus = parseAttrExprOrInt(node, ERROR_STATUS_NAME, cp, scope);

        ThrowStatement stm = new ThrowStatement();
        stm.setLocation(node.getLocation());
        NewExpression errorExpr = new NewExpression();
        errorExpr.setLocation(code.getLocation());
        errorExpr.setCallee(XLangASTBuilder.typeName(node.getLocation(), NopEvalException.class));
        errorExpr.setArguments(asList(code, params, cause));

        SourceLocation loc = node.getLocation();
        Expression argExpr = errorExpr;
        if (errorDescription != null) {
            argExpr = addCall(argExpr, loc, ERROR_DESCRIPTION_NAME, errorDescription);
        }
        if (bizFatal != null && bizFatal) {
            argExpr = addCall(argExpr, loc, BIZ_FATAL_NAME, Literal.booleanValue(loc, bizFatal));
        }
        if (errorStatus != null) {
            argExpr = addCall(argExpr, loc, ERROR_STATUS_NAME, errorStatus);
        }
        stm.setArgument(argExpr);
        return stm;
    }

    private Expression addCall(Expression objExpr, SourceLocation loc, String name, Expression value) {
        MemberExpression fn = new MemberExpression();
        fn.setLocation(loc);
        fn.setObject(objExpr);
        fn.setProperty(Identifier.valueOf(loc, name));

        CallExpression call = new CallExpression();
        call.setLocation(loc);
        call.setCallee(fn);
        call.setArguments(Collections.singletonList(value));
        return call;
    }
}
