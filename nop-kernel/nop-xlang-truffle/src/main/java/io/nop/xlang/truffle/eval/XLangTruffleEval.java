package io.nop.xlang.truffle.eval;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.translate.TranslatedUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

import java.util.concurrent.atomic.AtomicLong;


/**
 * XLang truffle 后端宿主侧求值入口（测试域 driver：I5 对拍 truffle 列/接线冒烟的执行通路；
 * 生产代码路径的路由接入归 I9；多线程池化运行时 = {@code io.nop.xlang.truffle.runtime.XLangContextPool}，
 * 其 Lease 复用本类的 {@link #evalOnContext} 通用求值协议）。
 *
 * <p>执行通路：注册求值 handoff（同线程）→ {@code Context.eval(合成 Source)} →
 * {@link XLangLanguage#parse} 查翻译缓存返回 CallTarget → 引擎执行该 CallTarget →
 * 根节点在求值窗口内绑定 scope/输出缓冲。语言异常经 handoff 原样回传（原始 NopException，
 * 三层断言语料），宿主值经 host access 直取。
 *
 * <p>sourceKey 口径（决策 D2）：resourcePath 单元 = resourcePath；无 resourcePath 动态源
 * = {@code dyn:} + 源内容哈希（源内容即键的防串用形态）。本实例持独立 Context（未绑定
 * 共享 Engine），单线程串行使用。
 */
public final class XLangTruffleEval implements AutoCloseable {

    private static final String DYNAMIC_PREFIX = "dyn:";

    /**
     * 全局单调求值序号：Source 内容必须<b>全局</b>逐次唯一——SHARED + 共享 Engine 下
     * parse 缓存按语言实例共享，同内容 Source 会命中缓存跳过 parse（plan I8 发现并修复：
     * 逐 Context 计数会在第二个 Context 上碰撞缓存，绕过翻译缓存查找链路）。
     */
    private static final AtomicLong GLOBAL_EVAL_SEQ = new AtomicLong();

    private final Context context;

    public XLangTruffleEval() {
        context = Context.newBuilder(XLangLanguage.ID).allowHostAccess(HostAccess.ALL).build();
    }

    /**
     * 无 resourcePath 动态源的翻译缓存键（源内容哈希键，决策 D2）。
     */
    public static String dynamicSourceKey(String source) {
        return DYNAMIC_PREFIX + StringHelper.sha256Hash(source, null);
    }

    public TranslatedEval eval(String sourceKey, IExecutableExpression tree, IEvalScope scope,
                               IEvalOutput output) {
        return evalOnContext(context, sourceKey, tree, scope, output);
    }

    /**
     * 通用求值协议（宿主侧 facade 与池租借 Lease 共用，plan I8 §3 复用裁定）：注册求值
     * handoff（同线程）→ {@code Context.eval(合成 Source)} → {@link XLangLanguage#parse} 查
     * 翻译缓存返回 CallTarget → 引擎执行 → 根节点在求值窗口内绑定 scope/输出缓冲。语言异常
     * 经 handoff 原样回传（原始 NopException，三层断言语料），宿主值经 host access 直取。
     */
    public static TranslatedEval evalOnContext(Context context, String sourceKey,
                                               IExecutableExpression tree, IEvalScope scope,
                                               IEvalOutput output) {
        EvalHandoff.Pending pending = new EvalHandoff.Pending(sourceKey, tree, scope, output);
        EvalHandoff.begin(pending);
        try {
            Source source;
            try {
                // 内容全局逐次唯一：引擎按 Source 内容缓存 parse 结果，唯一化保证每次 eval 都经
                // parse → 翻译缓存查找链路（内容不被解析消费，XLang 无 parser）
                source = Source.newBuilder(XLangLanguage.ID, "eval-" + GLOBAL_EVAL_SEQ.incrementAndGet(),
                        sourceKey).build();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("build synthetic xl source failed: " + sourceKey, e);
            }
            context.eval(source);
            // 原始返回值经 handoff 同线程交接（polyglot Value 转换会丢失精确 Java 类型）
            return new TranslatedEval(pending.getResolvedUnit(), pending.getReturned(), null,
                    pending.isTranslationFailed());
        } catch (PolyglotException e) {
            return new TranslatedEval(pending.getResolvedUnit(), null, unwrap(e, pending),
                    pending.isTranslationFailed());
        } finally {
            EvalHandoff.end();
        }
    }

    private static Throwable unwrap(PolyglotException e, EvalHandoff.Pending pending) {
        Throwable captured = pending.getThrown();
        if (captured != null)
            return captured;
        if (e.isHostException()) {
            Throwable host = e.asHostException();
            if (host != null)
                return host;
        }
        // parse 期 fail-fast（如支持集外节点翻译失败）：NopEvalException 以 cause 链透出
        if (e.getCause() instanceof NopEvalException)
            return e.getCause();
        return e;
    }

    public Context getContext() {
        return context;
    }

    @Override
    public void close() {
        context.close();
    }

    /**
     * 单次求值结果：返回值/异常 + 实际执行的翻译产物（身份断言证据来源）。
     */
    public static final class TranslatedEval {

        private final TranslatedUnit unit;

        private final Object returnValue;

        private final Throwable thrown;

        /**
         * parse 期翻译失败标记（check2 P1 修复）：本次求值的异常是否出自
         * {@code TranslationCache.getOrBuild} 抛错路径（per-request 精确信号——消费方以
         * 本标志判定单元级降级，不依赖跨线程共享事件的 sourceKey 关联）。
         */
        private final boolean translationFailed;

        TranslatedEval(TranslatedUnit unit, Object returnValue, Throwable thrown, boolean translationFailed) {
            this.unit = unit;
            this.returnValue = returnValue;
            this.thrown = thrown;
            this.translationFailed = translationFailed;
        }

        public TranslatedUnit getUnit() {
            return unit;
        }

        public Object getReturnValue() {
            return returnValue;
        }

        public Throwable getThrown() {
            return thrown;
        }

        public boolean isTranslationFailed() {
            return translationFailed;
        }
    }
}
