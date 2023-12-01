/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.utils;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.CoreConstants;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.WriterEvalOutput;
import io.nop.core.lang.sql.CollectSqlOutput;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectJObjectHandler;
import io.nop.core.lang.xml.handler.CollectXNodeHandler;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.Expression;

import java.io.Writer;
import java.util.Collections;
import java.util.function.Function;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_ALLOW_BOTH_RETURN_AND_OUTPUT_NODE;

public class ExprEvalHelper {
    public static void generateToWriter(Function<IEvalContext, ?> task, Writer out, IEvalContext context) {
        IEvalScope scope = context.getEvalScope();
        IEvalOutput oldOut = scope.getOut();
        try {
            scope.setOut(new WriterEvalOutput(out));
            task.apply(context);
        } finally {
            scope.setOut(oldOut);
        }
    }

    public static SQL generateSql(Function<IEvalContext, ?> task, IEvalContext context) {
        IEvalScope scope = context.getEvalScope();
        IEvalOutput oldOut = scope.getOut();
        try {
            CollectSqlOutput out = new CollectSqlOutput();
            scope.setOut(out);
            task.apply(context);
            return out.getResult().end();
        } finally {
            scope.setOut(oldOut);
        }
    }

    public static SQL.SqlBuilder generateSqlBuilder(Function<IEvalContext, ?> task, IEvalContext context) {
        IEvalScope scope = context.getEvalScope();
        IEvalOutput oldOut = scope.getOut();
        try {
            CollectSqlOutput out = new CollectSqlOutput();
            scope.setOut(out);
            task.apply(context);
            return out.getResult();
        } finally {
            scope.setOut(oldOut);
        }
    }

    public static Object generateXjson(Function<IEvalContext, ?> task, IEvalContext context) {
        IEvalScope scope = context.getEvalScope();

        IEvalOutput oldOut = scope.getOut();
        CollectJObjectHandler handler = new CollectJObjectHandler();
        try {
            scope.setOut(handler);
            Object ret = task.apply(context);

            Object result = handler.getResult();
            if (result != null) {
                if (ret != null) {
                    throw new NopEvalException(ERR_XPL_NOT_ALLOW_BOTH_RETURN_AND_OUTPUT_NODE).param(ARG_NODE, result)
                            .param(ARG_VALUE, ret);
                }
                return result;
            } else {
                return ret;
            }
        } finally {
            scope.setOut(oldOut);
        }
    }

    public static XNode generateNode(Function<IEvalContext, ?> task, IEvalContext context) {
        IEvalScope scope = context.getEvalScope();

        IEvalOutput oldOut = scope.getOut();
        CollectXNodeHandler handler = new CollectXNodeHandler();
        try {
            handler.beginNode(null, CoreConstants.DUMMY_TAG_NAME, Collections.emptyMap());
            scope.setOut(handler);
            Object ret = task.apply(context);

            handler.endNode(CoreConstants.DUMMY_TAG_NAME);

            XNode node = handler.endDoc();
            if (node.hasBody()) {
                if (ret instanceof XNode) {
                    throw new NopEvalException(ERR_XPL_NOT_ALLOW_BOTH_RETURN_AND_OUTPUT_NODE).param(ARG_NODE, ret);
                }
                return node;
            } else {
                if (ret instanceof XNode)
                    return (XNode) ret;
                return node;
            }
        } finally {
            scope.setOut(oldOut);
        }
    }

    public static Expression runMacroExpression(Expression expr, IXLangCompileScope scope, boolean dump) {
        IExecutableExpression executable = scope.getCompiler().buildExecutable(expr, false, scope);

        IEvalOutput oldOut = scope.getOut();
        CollectXNodeHandler handler = new CollectXNodeHandler();
        try {
            handler.beginNode(null, CoreConstants.DUMMY_TAG_NAME, Collections.emptyMap());
            scope.setOut(handler);
            Object ret = XLang.execute(executable, scope);

            handler.endNode(CoreConstants.DUMMY_TAG_NAME);
            XNode node = handler.endDoc();
            if (node.hasBody()) {
                if (ret instanceof XNode || ret instanceof Expression) {
                    throw new NopEvalException(ERR_XPL_NOT_ALLOW_BOTH_RETURN_AND_OUTPUT_NODE).param(ARG_NODE, ret);
                }
                if (dump)
                    node.dump("nop.macro-run-result");
                return scope.getCompiler().parseTagBody(node, scope);
            } else {
                if (ret instanceof XNode) {
                    if (dump)
                        node.dump("nop.macro-run-result");
                    return scope.getCompiler().parseTagBody((XNode) ret, scope);
                }
                if (ret instanceof Expression)
                    return (Expression) ret;
                return null;
            }
        } finally {
            scope.setOut(oldOut);
        }
    }
}
