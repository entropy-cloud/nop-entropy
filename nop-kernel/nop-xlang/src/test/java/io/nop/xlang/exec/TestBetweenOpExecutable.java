package io.nop.xlang.exec;

import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：BetweenOpExecutable（WI0 快照 0% 靶点，41 行）。
 * between 谓词的求值语义：闭/开区间边界、NOT_BETWEEN 取反、display 文本、
 * 以及 passConditions 经 IEvalContext 的等价求值。
 */
public class TestBetweenOpExecutable {

    private static final io.nop.api.core.util.SourceLocation LOC =
            io.nop.api.core.util.SourceLocation.fromLine("test.x", 1);

    static class ConstExpr implements IExecutableExpression {
        private final Object value;

        ConstExpr(Object value) {
            this.value = value;
        }

        @Override
        public io.nop.api.core.util.SourceLocation getLocation() {
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

    private static BetweenOpExecutable between(Object value, Object min, Object max,
                                               boolean excludeMin, boolean excludeMax) {
        return new BetweenOpExecutable(LOC, FilterOp.BETWEEN,
                new ConstExpr(value), new ConstExpr(min), new ConstExpr(max), excludeMin, excludeMax);
    }

    private static EvalRuntime newRuntime() {
        return new EvalRuntime(new EvalScopeImpl(new LinkedHashMap<>()));
    }

    /**
     * 闭区间为缺省语义：min/max 边界值都满足。
     */
    @Test
    public void testInclusiveBounds() {
        BetweenOpExecutable exec = between(5, 1, 10, false, false);
        assertEquals(Boolean.TRUE, exec.execute(XLang.getExecutor(), newRuntime()));
        assertEquals(Boolean.TRUE, between(1, 1, 10, false, false)
                .execute(XLang.getExecutor(), newRuntime()), "闭区间下边界必须满足");
        assertEquals(Boolean.TRUE, between(10, 1, 10, false, false)
                .execute(XLang.getExecutor(), newRuntime()), "闭区间上边界必须满足");
        assertEquals(Boolean.FALSE, between(11, 1, 10, false, false)
                .execute(XLang.getExecutor(), newRuntime()));
    }

    /**
     * excludeMin/excludeMax=true 时边界值不满足（开区间）。
     */
    @Test
    public void testExclusiveBounds() {
        assertEquals(Boolean.FALSE, between(1, 1, 10, true, false)
                .execute(XLang.getExecutor(), newRuntime()), "excludeMin=true 时下边界必须不满足");
        assertEquals(Boolean.FALSE, between(10, 1, 10, false, true)
                .execute(XLang.getExecutor(), newRuntime()), "excludeMax=true 时上边界必须不满足");
        assertEquals(Boolean.TRUE, between(2, 1, 10, true, true)
                .execute(XLang.getExecutor(), newRuntime()));
    }

    /**
     * passConditions 经 IEvalContext 取 scope 后与 execute 语义一致。
     */
    @Test
    public void testPassConditionsMatchesExecute() {
        BetweenOpExecutable exec = between(5, 1, 10, false, false);
        IEvalScope scope = new EvalScopeImpl(new LinkedHashMap<>());
        TestContext ctx = new TestContext(scope);

        assertTrue(exec.passConditions(ctx), "范围内值必须满足条件");
        assertFalse(between(0, 1, 10, false, false).passConditions(ctx), "范围外值必须不满足条件");
    }

    /**
     * NOT_BETWEEN 是 BETWEEN 的取反谓词。
     */
    @Test
    public void testNotBetweenNegates() {
        BetweenOpExecutable notBetween = new BetweenOpExecutable(LOC, FilterOp.NOT_BETWEEN,
                new ConstExpr(5), new ConstExpr(1), new ConstExpr(10), false, false);
        assertEquals(Boolean.FALSE, notBetween.execute(XLang.getExecutor(), newRuntime()));
        assertEquals(FilterOp.NOT_BETWEEN, notBetween.getFilterOp());
    }

    /**
     * display 输出 "value OP min and max" 形式。
     */
    @Test
    public void testDisplay() {
        BetweenOpExecutable exec = between(5, 1, 10, false, false);
        StringBuilder sb = new StringBuilder();
        exec.display(sb);
        assertEquals("5 between 1 and 10", sb.toString(), "display 使用 filterOp 的小写名称");
    }

    /**
     * allowBreakPoint=false（谓词表达式不可设断点）。
     */
    @Test
    public void testAllowBreakPointFalse() {
        assertFalse(between(5, 1, 10, false, false).allowBreakPoint());
    }

    /**
     * value/min/max 实参不允许为 null（Guard 校验）。
     */
    @Test
    public void testGuardNullArguments() {
        assertThrows(Exception.class, () -> new BetweenOpExecutable(LOC, FilterOp.BETWEEN,
                null, new ConstExpr(1), new ConstExpr(10), false, false));
        assertThrows(Exception.class, () -> new BetweenOpExecutable(LOC, FilterOp.BETWEEN,
                new ConstExpr(5), null, new ConstExpr(10), false, false));
        assertThrows(Exception.class, () -> new BetweenOpExecutable(LOC, FilterOp.BETWEEN,
                new ConstExpr(5), new ConstExpr(1), null, false, false));
    }

    static class TestContext implements IEvalContext {
        private final IEvalScope scope;

        TestContext(IEvalScope scope) {
            this.scope = scope;
        }

        @Override
        public IEvalScope getEvalScope() {
            return scope;
        }
    }
}
