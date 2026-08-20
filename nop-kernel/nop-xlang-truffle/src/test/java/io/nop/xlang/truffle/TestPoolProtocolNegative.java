package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.compare.RecordingEvalOutput;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 池租借协议负测试（roadmap I8 验收第二项：注入故意残留 → 残留检测机制红灯，红/绿对照——
 * I1 差异注入自检先例）。
 *
 * <p><b>红灯</b>：绕过归还清空路径构造残留 context 状态（租约窗口内经公共 API
 * {@code XLangLanguage.currentContext()} 直接 {@code bindEvaluation}——模拟借用方泄漏
 * 求值现场，如根节点 finally 被跳过或借用方私接状态）→ {@code Lease.close()} 红灯
 * （IllegalStateException + 该 Context 从池中退役不再出借）。
 * <b>绿灯</b>：正常求值归还与异常抛出路径归还 → 检测通过（池复用无残留）。
 */
public class TestPoolProtocolNegative {

    private static final SourceLocation LOC = SourceLocation.fromPath("/pool-negative-test.xpl");

    private XLangContextPool pool;

    @AfterEach
    public void tearDown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    /**
     * 注入通道自证：负测试的注入路径（currentContext + bindEvaluation）与池归还检测路径
     * 是同一通道——注入可达即检测可达。
     */
    @Test
    public void testInjectionChannelReachesTheLanguageContext() {
        pool = XLangContextPool.open(1);
        try (XLangContextPool.Lease lease = pool.lease()) {
            lease.getContext().enter();
            try {
                XLangContext langContext = XLangLanguage.currentContext();
                langContext.bindEvaluation(new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
                assertTrue(langContext.hasEvaluationResidue(), "injected residue must be observable");
                langContext.clearEvaluation();
            } finally {
                lease.getContext().leave();
            }
        }
    }

    /**
     * 红灯：注入残留 → 归还检测红灯 + Context 退役（不再出借）。
     */
    @Test
    public void testInjectedResidueRedFlagsAtReturnAndRetiresTheContext() {
        pool = XLangContextPool.open(2);
        XLangContextPool.Lease lease = pool.lease();
        IExecutableExpression tree = LiteralExecutable.build(LOC, 1);
        lease.eval("/pool-negative-red.xpl", tree, new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);

        // 绕过归还清空路径注入残留（模拟借用方泄漏求值现场）
        lease.getContext().enter();
        try {
            XLangLanguage.currentContext().bindEvaluation(new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        } finally {
            lease.getContext().leave();
        }

        IllegalStateException e = assertThrows(IllegalStateException.class, lease::close,
                "residue detection must red-flag at return");
        assertTrue(e.getMessage().contains("residue"), "violation must name the residue: " + e.getMessage());
        assertEquals(0, pool.leasedCount(), "counter must be decremented even on violation");
        assertEquals(1, pool.availableCount(), "retired context must not return to the idle pool");
    }

    /**
     * 绿灯对照：正常求值归还与异常抛出路径归还均无残留（检测通过，Context 复用）。
     */
    @Test
    public void testNormalAndExceptionPathsReturnClean() {
        pool = XLangContextPool.open(1);
        // 正常路径
        try (XLangContextPool.Lease lease = pool.lease()) {
            Object value = lease.eval("/pool-negative-green.xpl", LiteralExecutable.build(LOC, 5),
                    new EvalScopeImpl(), DisabledEvalOutput.INSTANCE).getReturnValue();
            assertEquals(5, value);
        }
        // 异常抛出路径
        IExecutableExpression thrower = new GuardNotNullExecutable(LOC, NullExecutable.NULL);
        try (XLangContextPool.Lease lease = pool.lease()) {
            Object thrown = lease.eval("/pool-negative-green-ex.xpl", thrower,
                    new EvalScopeImpl(), DisabledEvalOutput.INSTANCE).getThrown();
            assertTrue(thrown != null, "thrower must throw");
        }
        // 复用无残留（同一池条目连续通过三轮租借）
        for (int i = 0; i < 3; i++) {
            try (XLangContextPool.Lease lease = pool.lease()) {
                lease.getContext().enter();
                try {
                    assertTrue(!XLangLanguage.currentContext().hasEvaluationResidue(),
                            "re-leased context must be residue-free: cycle=" + i);
                } finally {
                    lease.getContext().leave();
                }
            }
        }
        assertEquals(1, pool.availableCount(), "green path keeps the context in the pool");
    }

    /**
     * 退役后池行为：剩余条目照常服务（退役是隔离不是池失效）。
     */
    @Test
    public void testPoolServesWithRemainingEntriesAfterRetirement() {
        pool = XLangContextPool.open(2);
        XLangContextPool.Lease lease = pool.lease();
        lease.eval("/pool-retire-first.xpl", LiteralExecutable.build(LOC, 1), new EvalScopeImpl(),
                DisabledEvalOutput.INSTANCE);
        lease.getContext().enter();
        try {
            XLangLanguage.currentContext().bindEvaluation(new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        } finally {
            lease.getContext().leave();
        }
        assertThrows(IllegalStateException.class, lease::close);

        // 另一条目照常出借且求值正确
        try (XLangContextPool.Lease other = pool.lease()) {
            Object value = other.eval("/pool-retire-second.xpl", LiteralExecutable.build(LOC, 9),
                    new EvalScopeImpl(), DisabledEvalOutput.INSTANCE).getReturnValue();
            assertEquals(9, value);
        }
        assertEquals(1, pool.availableCount());
    }

    /**
     * 残留检测的对象敏感性：注入的是"这一次求值"的 scope/输出缓冲（IEvalScope/IEvalOutput），
     * 检测红灯信息不依赖注入对象身份。
     */
    @Test
    public void testResidueInjectionViaOutputBufferOnlyAlsoRedFlags() {
        pool = XLangContextPool.open(1);
        XLangContextPool.Lease lease = pool.lease();
        lease.eval("/pool-residue-output.xpl", LiteralExecutable.build(LOC, 1), new EvalScopeImpl(),
                DisabledEvalOutput.INSTANCE);
        IEvalScope scopeOnly = new EvalScopeImpl();
        lease.getContext().enter();
        try {
            // 仅输出缓冲残留（换缓冲协议未恢复的形态）
            XLangContext ctx = XLangLanguage.currentContext();
            ctx.bindEvaluation(scopeOnly, new RecordingEvalOutput());
        } finally {
            lease.getContext().leave();
        }
        assertThrows(IllegalStateException.class, lease::close, "output-buffer residue must red-flag too");
        assertEquals(0, pool.leasedCount());
    }
}
