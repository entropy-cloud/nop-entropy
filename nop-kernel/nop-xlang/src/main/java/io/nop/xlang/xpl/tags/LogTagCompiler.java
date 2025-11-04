/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.LogLevel;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.functions.LogFunctions;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.Arrays;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_LOG_TAG_NOT_ALLOW_CHILD;
import static io.nop.xlang.xpl.XplConstants.DEBUG_NAME;
import static io.nop.xlang.xpl.XplConstants.ERROR_NAME;
import static io.nop.xlang.xpl.XplConstants.INFO_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrTemplateExpr;


public class LogTagCompiler implements IXplTagCompiler {
    public static final LogTagCompiler INSTANCE = new LogTagCompiler();

    static final List<String> ATTR_NAMES = Arrays.asList(INFO_NAME, DEBUG_NAME, ERROR_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);
        if (node.hasChild())
            throw new NopEvalException(ERR_XPL_LOG_TAG_NOT_ALLOW_CHILD).param(ARG_NODE, node);

        Expression info = parseAttrTemplateExpr(node, INFO_NAME, cp, scope);
        Expression debug = parseAttrTemplateExpr(node, DEBUG_NAME, cp, scope);
        Expression error = parseAttrTemplateExpr(node, ERROR_NAME, cp, scope);
        LogLevel level;
        List<Expression> argExprs;
        if (error != null) {
            level = LogLevel.ERROR;
            argExprs = Arrays.asList(error);
        } else if (info != null) {
            level = LogLevel.INFO;
            argExprs = Arrays.asList(info);
        } else if (debug != null) {
            level = LogLevel.DEBUG;
            argExprs = Arrays.asList(debug);
        } else {
            return null;
        }
        return LogFunctions.newLogExpression(level, node.getLocation(), "{}", argExprs);
    }
}
