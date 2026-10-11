/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.compile.DefaultExecutableExprCompiler;
import io.nop.xlang.exec.LiteralExecutable;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ERR_EXEC_COMPILER_NOT_REGISTERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * R1.1（plan 2364）：expr→compile 单边割离后的接缝回归。
 * 覆盖四点：①ServiceLoader 默认实现端到端委托连通；②显式注册覆盖生效；③未注册 fail-loud；④默认实现来源正确。
 */
public class TestExecutableExprCompilerSeam {

    private Expression parse(String source) {
        return io.nop.xlang.expr.simple.SimpleExprParser.newDefault().parseExpr(null, source);
    }

    @Test
    public void testDefaultCompilerDelegatesThroughSeam() {
        Expression expr = parse("1 + 2");
        IExecutableExpression executable = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .buildExecutable(expr);
        assertNotNull(executable);
        assertEquals(3, XLang.execute(executable, new EvalRuntime(XLang.newEvalScope())));
    }

    @Test
    public void testRegisteredOverrideIsHonored() {
        IExecutableExpression marker = LiteralExecutable.build(SourceLocation.UNKNOWN, 42);
        IExecutableExprCompiler stub = (expr, optimize, scope) -> marker;

        IExecutableExprCompiler saved = XLangExprParser.requireExecutableExprCompiler();
        try {
            XLangExprParser.registerExecutableExprCompiler(stub);
            IExecutableExpression executable = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                    .buildExecutable(parse("1 + 2"));
            assertSame(marker, executable);
        } finally {
            XLangExprParser.registerExecutableExprCompiler(saved);
        }
    }

    @Test
    public void testUnregisteredCompilerFailsLoud() {
        IExecutableExprCompiler saved = XLangExprParser.requireExecutableExprCompiler();
        try {
            XLangExprParser.registerExecutableExprCompiler(null);
            assertThrows(NopException.class,
                    () -> XLang.newCompileTool().allowUnregisteredScopeVar(true).buildExecutable(parse("1 + 2")));
            try {
                XLang.newCompileTool().allowUnregisteredScopeVar(true).buildExecutable(parse("1 + 2"));
                fail("expect ERR_EXEC_COMPILER_NOT_REGISTERED when compiler unregistered");
            } catch (NopException e) {
                assertEquals(ERR_EXEC_COMPILER_NOT_REGISTERED.getErrorCode(), e.getCode());
            }
        } finally {
            XLangExprParser.registerExecutableExprCompiler(saved);
        }
    }

    @Test
    public void testServiceLoaderDefaultIsCompilePackageImpl() {
        IExecutableExprCompiler compiler = XLangExprParser.requireExecutableExprCompiler();
        assertNotNull(compiler);
        assertSame(DefaultExecutableExprCompiler.class, compiler.getClass());
    }
}
