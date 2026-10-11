package io.nop.jq;

import io.nop.jq.runtime.JqCancellationException;
import io.nop.jq.runtime.JqRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.3（plan 2366）：jq 资源护栏回归（P1-1.9-001）。
 * 4 类叶节点预算 + deadline/中断检查点（含 fork 传播与 try/`//` 不可捕）+ env-supplier 接缝。
 */
public class TestJqResourceGuards {

    private void applyLong(String expr) {
        IJsonQuery query = JqEngine.compile(expr);
        query.apply(null);
    }

    /** 审计探针同型：combinations 笛卡尔爆发在预算处被拦截（非 OOM）。 */
    @Test
    public void testCombinationsOutputBudget() {
        // 2^21 = 2,097,152 outputs > MAX_OUTPUT_COUNT(1M)
        JqRuntimeException e = assertThrows(JqRuntimeException.class,
                () -> applyLong("[range(21)]|map([0,1])|combinations|length"));
        assertTrue(e.getMessage().contains("Output count limit exceeded"));
    }

    /** repeatString 按追加迭代粒度受预算约束（"a"*1e9 曾 222ms OOM）。 */
    @Test
    public void testRepeatStringBudget() {
        JqRuntimeException e = assertThrows(JqRuntimeException.class,
                () -> applyLong("\"a\" * 1e9"));
        assertTrue(e.getMessage().contains("Output count limit exceeded"));
    }

    /** constructObjects 笛卡尔爆发受预算约束（10 组 key/value 各 2 值 = 4^10 > 1M）。 */
    @Test
    public void testConstructObjectsBudget() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 10; i++) {
            if (i > 0)
                sb.append(',');
            sb.append("((1,2)|tostring):(1,2)");
        }
        sb.append('}');
        JqRuntimeException e = assertThrows(JqRuntimeException.class,
                () -> applyLong(sb.toString()));
        assertTrue(e.getMessage().contains("Output count limit exceeded"));
    }

    /** interpParts 笛卡尔爆发受预算约束（20 段各 2 值 = 2^20 > 1M）。 */
    @Test
    public void testInterpPartsBudget() {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < 20; i++) {
            sb.append("\\((1,2))");
        }
        sb.append('"');
        JqRuntimeException e = assertThrows(JqRuntimeException.class,
                () -> applyLong(sb.toString()));
        assertTrue(e.getMessage().contains("Output count limit exceeded"));
    }

    /** deadline 超时抛不可捕异常；默认（0）无 deadline 不受影响。 */
    @Test
    public void testDeadlineExceeded() {
        IJsonQuery query = JqEngine.compile("[range(100)]|length");
        assertThrows(JqCancellationException.class,
                () -> query.apply(null, null, System.currentTimeMillis() - 1000));
        assertEquals(100, query.apply(null).get(0));
    }

    /** deadline 随 fork() 传播：用户函数体内检查点同样生效（否则静默失效）。 */
    @Test
    public void testDeadlinePropagatesThroughUserFunction() {
        IJsonQuery query = JqEngine.compile("def f: [range(100)]; f");
        assertThrows(JqCancellationException.class,
                () -> query.apply(null, null, System.currentTimeMillis() - 1000));
    }

    /** 中断响应：isInterrupted 非清除检查，预中断线程后首个检查点抛不可捕异常。 */
    @Test
    public void testInterruptResponse() {
        IJsonQuery query = JqEngine.compile("[range(100)]|length");
        Thread.currentThread().interrupt();
        try {
            assertThrows(JqCancellationException.class, () -> query.apply(null));
        } finally {
            // clear the flag so other tests are not polluted
            Thread.interrupted();
        }
    }

    /** 取消异常对 jq 级 try 与 `//` 不可捕：吞噬会复活失控计算。 */
    @Test
    public void testCancellationNotCatchableByTryAndAlternative() {
        IJsonQuery tryQuery = JqEngine.compile("try ([range(100)]|length) catch \"caught\"");
        assertThrows(JqCancellationException.class,
                () -> tryQuery.apply(null, null, System.currentTimeMillis() - 1000));

        IJsonQuery altQuery = JqEngine.compile("([range(100)]|length) // \"fallback\"");
        assertThrows(JqCancellationException.class,
                () -> altQuery.apply(null, null, System.currentTimeMillis() - 1000));
    }

    /** env 注入：toolkit 接缝只见沙箱变量（$ENV 与 env 内建），默认路径仍见全进程变量。 */
    @Test
    public void testEnvSupplierSeam() {
        String injected = "JQ_TEST_INJECTED_VAR";
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put(injected, "42");

        IJsonQuery query = JqEngine.compile("$ENV." + injected);
        assertEquals("42", query.apply(null, vars, 0).get(0));

        IJsonQuery envBuiltin = JqEngine.compile("env | has(\"" + injected + "\")");
        assertEquals(Boolean.TRUE, envBuiltin.apply(null, vars, 0).get(0));

        // sandboxed view must not leak process variables (PATH exists in any real env)
        IJsonQuery noPath = JqEngine.compile("$ENV | has(\"PATH\")");
        assertEquals(Boolean.FALSE, noPath.apply(null, vars, 0).get(0));

        // default path (no supplier) still sees the process environment
        IJsonQuery defaultPath = JqEngine.compile("$ENV | has(\"" + injected + "\")");
        assertFalse((Boolean) defaultPath.apply(null).get(0));
    }
}
