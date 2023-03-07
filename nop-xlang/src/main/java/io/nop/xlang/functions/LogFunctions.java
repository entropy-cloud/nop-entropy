/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.functions;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.annotations.lang.Macro;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.LogLevel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_MIN_COUNT;
import static io.nop.xlang.XLangErrors.ARG_MSG_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_LOG_MESSAGE_NOT_ALLOW_EMPTY;
import static io.nop.xlang.XLangErrors.ERR_EXEC_LOG_MESSAGE_NOT_STATIC;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_FEW_ARGS;

/**
 * 支持EL表达式中的logInfo/logDebug等函数，第一个参数必须是静态字符串，不允许"sss"+yyy这种形式，从而也避免了log注入攻击。
 * logInfo("nop.err.invalid-name:name={}",name);
 */
public class LogFunctions {
    @Macro
    public static Expression logTrace(IXLangCompileScope scope, CallExpression expr) {
        return newLogExpression(scope, LogLevel.TRACE, expr);
    }

    @Macro
    public static Expression logDebug(IXLangCompileScope scope, CallExpression expr) {
        return newLogExpression(scope, LogLevel.DEBUG, expr);
    }

    @Macro
    public static Expression logInfo(IXLangCompileScope scope, CallExpression expr) {
        return newLogExpression(scope, LogLevel.INFO, expr);
    }

    @Macro
    public static Expression logWarn(IXLangCompileScope scope, CallExpression expr) {
        return newLogExpression(scope, LogLevel.WARN, expr);
    }

    @Macro
    public static Expression logError(IXLangCompileScope scope, CallExpression expr) {
        return newLogExpression(scope, LogLevel.ERROR, expr);
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static Expression newLogExpression(IXLangCompileScope scope, LogLevel logLevel, CallExpression expr) {
        if (expr.getArguments().isEmpty())
            throw new NopEvalException(ERR_EXEC_TOO_FEW_ARGS).param(ARG_EXPR, expr).param(ARG_MIN_COUNT, 1);

        Expression messageExpr = expr.getArguments().get(0);
        if (!(messageExpr instanceof Literal))
            throw new NopEvalException(ERR_EXEC_LOG_MESSAGE_NOT_STATIC).param(ARG_EXPR, expr).param(ARG_MSG_EXPR,
                    messageExpr);

        String message = ((Literal) messageExpr).getStringValue();
        return newLogExpression(logLevel, expr.getLocation(), message, expr.getArguments());
    }

    @NoReflection
    public static CallExpression newLogExpression(LogLevel logLevel, SourceLocation loc, String message,
                                                  List<Expression> argExprs) {
        if (StringHelper.isEmpty(message))
            throw new NopEvalException(ERR_EXEC_LOG_MESSAGE_NOT_ALLOW_EMPTY).loc(loc);

        CallExpression call = new CallExpression();
        call.setLocation(loc);

        List<Expression> args = new ArrayList<>();
        // 拼接location参数
        args.add(Literal.valueOf(loc, "({}) " + message));

        ArrayExpression array = new ArrayExpression();
        List<XLangASTNode> elements = new ArrayList<>(argExprs.size());
        elements.add(Literal.valueOf(null, loc));

        for (int i = 1, n = argExprs.size(); i < n; i++) {
            Expression arg = argExprs.get(i);
            elements.add(arg.deepClone());
        }
        array.setElements(elements);
        args.add(array);

        MemberExpression callee = newLogMethod(loc, logLevel);
        call.setCallee(callee);
        call.setArguments(args);
        return call;
    }

    private static MemberExpression newLogMethod(SourceLocation loc, LogLevel logLevel) {
        MemberExpression method = new MemberExpression();
        method.setLocation(loc);

        Identifier id = Identifier.valueOf(loc, LogHelper.class.getSimpleName());

        IClassModel classModel = ReflectionManager.instance().getClassModel(LogHelper.class);
        id.setResolvedDefinition(new ImportClassDefinition(classModel));
        id.setIdentifierKind(IdentifierKind.IMPORT_CLASS_REF);

        Identifier name = Identifier.valueOf(loc, getLogMethodName(logLevel));
        method.setObject(id);
        method.setProperty(name);
        return method;
    }

    private static String getLogMethodName(LogLevel logLevel) {
        return logLevel.name().toLowerCase();
    }
}