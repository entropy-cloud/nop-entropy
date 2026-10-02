package io.nop.xlang.exec;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI3 补强：VarExecutableFunction（WI0 快照 0% 靶点，35 行）。
 * 变量持有的函数值调用语义：实参表达式求值、可选调用（?.）对 null 函数透传 null、
 * 强制调用 null 函数报 nop.err.xlang.exec.call-null-function。
 */
public class TestVarExecutableFunction {

    private static final SourceLocation LOC = SourceLocation.fromLine("test.x", 1);

    /**
     * 常量可执行表达式，测试用桩。
     */
    static class ConstExpr implements IExecutableExpression {
        private final Object value;

        ConstExpr(Object value) {
            this.value = value;
        }

        @Override
        public SourceLocation getLocation() {
            return LOC;
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append(value);
        }

        @Override
        public boolean containsReturnStatement() {
            return false;
        }

        @Override
        public boolean containsBreakStatement() {
            return false;
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            return value;
        }

        @Override
        public void visit(IExecutableExpressionVisitor visitor) {
            // 常量无子节点
        }
    }

    /**
     * 函数表达式桩：display 输出可控的函数名。
     */
    static class FuncExpr extends ConstExpr {
        private final String displayName;

        FuncExpr(Object value, String displayName) {
            super(value);
            this.displayName = displayName;
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append(displayName);
        }
    }

    private static ExecutableFunction newFunc(String name, int argCount) {
        String[] slots = new String[argCount];
        for (int i = 0; i < argCount; i++) {
            slots[i] = "arg" + i;
        }
        return new ExecutableFunction(LOC, LOC, name, argCount, argCount, slots,
                IExecutableExpression.EMPTY_EXPRS, new ConstExpr("result"));
    }

    private EvalRuntime newRuntime() {
        IEvalScope scope = XLang.newEvalScope();
        return new EvalRuntime(scope);
    }

    /**
     * 非空函数值：以实参表达式调用并返回函数体结果。
     */
    @Test
    public void testInvokeFunctionValue() {
        VarExecutableFunction call = new VarExecutableFunction(LOC, new ConstExpr(newFunc("f", 1)),
                false, new IExecutableExpression[]{new ConstExpr(1)});

        assertEquals("result", call.execute(XLang.getExecutor(), newRuntime()));
    }

    /**
     * optional=true（?. 调用）且函数值为 null：透传 null，不抛异常。
     */
    @Test
    public void testOptionalCallOnNullFunctionReturnsNull() {
        VarExecutableFunction call = new VarExecutableFunction(LOC, new ConstExpr(null),
                true, new IExecutableExpression[]{new ConstExpr(1)});

        assertNull(call.execute(XLang.getExecutor(), newRuntime()));
    }

    /**
     * optional=false 且函数值为 null：抛 nop.err.xlang.exec.call-null-function，
     * 错误参数 funcExpr 携带被调表达式的显示形式。
     */
    @Test
    public void testRequiredCallOnNullFunctionThrows() {
        VarExecutableFunction call = new VarExecutableFunction(LOC, new ConstExpr(null),
                false, new IExecutableExpression[0]);

        NopEvalException e = assertThrows(NopEvalException.class,
                () -> call.execute(XLang.getExecutor(), newRuntime()));
        assertEquals(XLangErrors.ERR_EXEC_CALL_NULL_FUNCTION.getErrorCode(), e.getErrorCode());
    }

    /**
     * display 输出：普通调用为 fn(args)，optional 调用带 "?." 前缀，参数逗号分隔。
     */
    @Test
    public void testDisplay() {
        VarExecutableFunction call = new VarExecutableFunction(LOC, new FuncExpr(newFunc("f", 2), "f"),
                false, new IExecutableExpression[]{new ConstExpr(1), new ConstExpr("a")});
        StringBuilder sb = new StringBuilder();
        call.display(sb);
        assertEquals("f(1,a)", sb.toString(), "display 为 函数表达式(实参,...) 形式，参数逗号分隔");

        VarExecutableFunction optionalCall = new VarExecutableFunction(LOC, new FuncExpr(null, "f"),
                true, new IExecutableExpression[0]);
        StringBuilder sb2 = new StringBuilder();
        optionalCall.display(sb2);
        assertEquals("f?.()", sb2.toString(), "optional 调用在函数表达式与括号之间插入 ?.");
    }
}
