package io.nop.xlang.truffle.eval;

import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.translate.TranslatedUnit;

/**
 * 求值 handoff（宿主侧 facade → 语言侧 parse/根节点的同线程交接）。
 *
 * <p>facade 在 {@code Context.eval} 前注册本记录：parse 依据 sourceKey 取待翻译树并回填解析产物，
 * 根节点在执行窗口读取求值现场（scope + 输出缓冲）并记录逃逸的原始异常。同线程交接是
 * 确定性的（SHARED 形态下并发由多 Context 承担，各自线程各自 ThreadLocal 天然隔离）；
 * 嵌套注册与 sourceKey 失配均 fail-fast，无静默回退。池化批求值 = 多次 handoff 循环
 * （一个 Lease 多次 eval，不嵌套——plan I8 §3 裁定，现 fail-fast 语义保持）。
 */
public final class EvalHandoff {

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private EvalHandoff() {
    }

    public static void begin(Pending pending) {
        if (PENDING.get() != null)
            throw new IllegalStateException("nested eval handoff is not supported: " + PENDING.get().getSourceKey());
        PENDING.set(pending);
    }

    public static void end() {
        PENDING.remove();
    }

    /**
     * parse 出口：按合成 Source 名（= sourceKey）取待翻译树；失配或缺失即 fail-fast。
     */
    public static Pending takePending(String sourceName) {
        Pending pending = PENDING.get();
        if (pending == null)
            throw new IllegalStateException(
                    "no pending tree registered for xl source (XLang has no parser; register via XLangTruffleEval): "
                            + sourceName);
        if (!pending.getSourceKey().equals(sourceName))
            throw new IllegalStateException("pending tree sourceKey mismatch: pending=" + pending.getSourceKey()
                    + ", source=" + sourceName);
        return pending;
    }

    /**
     * 根节点执行窗口入口：求值现场必须在同线程求值窗口内。
     */
    public static Pending requireActive(String sourceKey) {
        Pending pending = PENDING.get();
        if (pending == null)
            throw new IllegalStateException("no active evaluation window for xl root: " + sourceKey);
        return pending;
    }

    /**
     * 单次求值的交接记录。
     */
    public static final class Pending {

        private final String sourceKey;

        private final IExecutableExpression tree;

        private final IEvalScope scope;

        private final IEvalOutput output;

        private TranslatedUnit resolvedUnit;

        private Throwable thrown;

        private Object returned;

        public Pending(String sourceKey, IExecutableExpression tree, IEvalScope scope, IEvalOutput output) {
            this.sourceKey = sourceKey;
            this.tree = tree;
            this.scope = scope;
            this.output = output;
        }

        public String getSourceKey() {
            return sourceKey;
        }

        public IExecutableExpression getTree() {
            return tree;
        }

        public IEvalScope getScope() {
            return scope;
        }

        public IEvalOutput getOutput() {
            return output;
        }

        public TranslatedUnit getResolvedUnit() {
            return resolvedUnit;
        }

        public void setResolvedUnit(TranslatedUnit resolvedUnit) {
            this.resolvedUnit = resolvedUnit;
        }

        public Throwable getThrown() {
            return thrown;
        }

        public void captureThrown(Throwable thrown) {
            this.thrown = thrown;
        }

        /**
         * 根节点记录的原始返回值（同线程确定性交接，无 polyglot Value 转换损耗——
         * Integer/Long 等精确类型保真，typedEquals 断言依赖）。
         */
        public Object getReturned() {
            return returned;
        }

        public void captureReturned(Object returned) {
            this.returned = returned;
        }
    }
}
