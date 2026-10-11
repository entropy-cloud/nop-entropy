/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.antlr4.common.ParseTreeResult;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Program;
import io.nop.xlang.parse.XLangASTBuildVisitor;
import io.nop.xlang.parse.XLangParseTreeParser;
import io.nop.xlang.parse.antlr.XLangParser;
import io.nop.xlang.xpl.impl.XplExprParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.ServiceLoader;

import static io.nop.xlang.XLangErrors.ERR_EXEC_COMPILER_NOT_REGISTERED;

public class XLangExprParser implements IXLangExprParser {
    private static final Logger LOG = LoggerFactory.getLogger(XLangExprParser.class);

    private static final Object s_lock = new Object();
    /** 显式注册的编译器（测试/定制覆盖入口）；null 表示未注册（回退 ServiceLoader 默认实现） */
    private static volatile IExecutableExprCompiler s_override;
    /** 显式注册标志：置位后不再回退 ServiceLoader（支持 set(null) 模拟未注册态） */
    private static volatile boolean s_explicit;
    /** ServiceLoader 解析结果缓存（null 表示已解析但 classpath 无实现） */
    private static volatile IExecutableExprCompiler s_default;

    /**
     * 注册编译器覆盖实例。传 null 可清除覆盖并进入显式未注册态（后续解析直接失败，不再回退默认实现）。
     */
    public static void registerExecutableExprCompiler(IExecutableExprCompiler compiler) {
        s_override = compiler;
        s_explicit = true;
    }

    public static IExecutableExprCompiler requireExecutableExprCompiler() {
        if (s_explicit)
            return s_override;

        IExecutableExprCompiler compiler = s_default;
        if (compiler != null)
            return compiler;

        synchronized (s_lock) {
            if (s_default == null)
                s_default = resolveDefaultCompiler();
            return s_default;
        }
    }

    private static IExecutableExprCompiler resolveDefaultCompiler() {
        Iterator<IExecutableExprCompiler> it = ServiceLoader.load(IExecutableExprCompiler.class).iterator();
        return it.hasNext() ? it.next() : null;
    }

    @Override
    public Program parseFullExpr(SourceLocation loc, String source, IXLangCompileScope scope, boolean resolveMacro) {
        ParseTreeResult result = XLangParseTreeParser.instance().parseFromText(loc, source);
        if (result == null)
            return null;
        Program program = new XLangASTBuildVisitor().visitProgram((XLangParser.ProgramContext) result.getParseTree());
        return program;
    }

    @Override
    public Expression parseSimpleExpr(SourceLocation loc, String source, IXLangCompileScope scope,
                                      boolean resolveMacro) {
        IExpressionParser parser = newExprParser(scope, resolveMacro);
        return parser.parseExpr(loc, source);
    }

    @Override
    public Expression parseTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase,
                                        IXLangCompileScope scope, boolean resolveMacro) {
        IExpressionParser parser = newExprParser(scope, resolveMacro);
        return parser.parseTemplateExpr(loc, source, singleExpr, phase);
    }

    protected IExpressionParser newExprParser(IXLangCompileScope scope, boolean resolveMacro) {
        return new XplExprParser(this, scope, resolveMacro);
    }

    @Override
    public IExecutableExpression buildExecutable(Expression expr, boolean optimize, IXLangCompileScope scope) {
        IExecutableExprCompiler compiler = requireExecutableExprCompiler();
        if (compiler == null)
            throw new NopException(ERR_EXEC_COMPILER_NOT_REGISTERED);
        return compiler.buildExecutable(expr, optimize, scope);
    }
}
