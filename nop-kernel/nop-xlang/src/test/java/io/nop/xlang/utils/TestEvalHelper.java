/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEvalHelper {

    /**
     * MINUS 必须做减法，而不是取两值较小值（MathHelper.min）
     */
    @Test
    public void testMinusBinaryOp() {
        assertEquals(-1, ((Number) EvalHelper.binaryOp(XLangOperator.MINUS, 1, 2)).intValue());
        assertEquals(0, ((Number) EvalHelper.binaryOp(XLangOperator.MINUS, 2, 2)).intValue());
        assertEquals(3, ((Number) EvalHelper.binaryOp(XLangOperator.MINUS, 5, 2)).intValue());
    }

    /**
     * const 声明的编译期常量折叠走 XplParseHelper.staticValue → EvalHelper.binaryOp，
     * 折叠结果会被内联为字面量（BuildExecutableProcessor 的 inlineVar 替换），
     * 因此 const x = 1 - 2 求值必须得到 -1：MINUS 须做减法（而非 min），
     * 且折叠传入的必须是解包后的操作数值（而非 OptionalValue 包装对象）
     */
    @Test
    public void testConstFoldMinus() {
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "const x = 1 - 2; return x");
        IEvalScope scope = XLang.newEvalScope();
        assertEquals(-1, ((Number) action.invoke(scope)).intValue());
    }

    /**
     * 同一折叠链路上 ADD 常量折叠此前把 OptionalValue 传给 MathHelper.add 得 0，
     * 解包修复后必须得到 3
     */
    @Test
    public void testConstFoldAdd() {
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileFullExpr(null, "const x = 1 + 2; return x");
        IEvalScope scope = XLang.newEvalScope();
        assertEquals(3, ((Number) action.invoke(scope)).intValue());
    }
}
