package io.nop.xlang.truffle.eval;

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


/**
 * XLang truffle 后端宿主侧求值入口（测试域 driver：I5 对拍 truffle 列/接线冒烟的执行通路；
 * 生产代码路径的路由接入归 I9）。
 *
 * <p>执行通路：注册求值 handoff（同线程）→ {@code Context.eval(合成 Source)} →
 * {@link XLangLanguage#parse} 查翻译缓存返回 CallTarget → 引擎执行该 CallTarget →
 * 根节点在求值窗口内绑定 scope/输出缓冲。语言异常经 handoff 原样回传（原始 NopException，
 * 三层断言语料），宿主值经 host access 直取。
 *
 * <p>sourceKey 口径（决策 D2）：resourcePath 单元 = resourcePath；无 resourcePath 动态源
 * = {@code dyn:} + 源内容哈希（源内容即键的防串用形态）。
 */
public final class XLangTruffleEval implements AutoCloseable {

    private static final String DYNAMIC_PREFIX = "dyn:";

    private final Context context;

    private long evalCounter;

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
        EvalHandoff.Pending pending = new EvalHandoff.Pending(sourceKey, tree, scope, output);
        EvalHandoff.begin(pending);
        try {
            Source source;
            try {
                // 内容逐次唯一：引擎按 Source 内容缓存 parse 结果，唯一化保证每次 eval 都经
                // parse → 翻译缓存查找链路（内容不被解析消费，XLang 无 parser）
                source = Source.newBuilder(XLangLanguage.ID, "eval-" + (++evalCounter), sourceKey).build();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("build synthetic xl source failed: " + sourceKey, e);
            }
            context.eval(source);
            // 原始返回值经 handoff 同线程交接（polyglot Value 转换会丢失精确 Java 类型）
            return new TranslatedEval(pending.getResolvedUnit(), pending.getReturned(), null);
        } catch (PolyglotException e) {
            return new TranslatedEval(pending.getResolvedUnit(), null, unwrap(e, pending));
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

        TranslatedEval(TranslatedUnit unit, Object returnValue, Throwable thrown) {
            this.unit = unit;
            this.returnValue = returnValue;
            this.thrown = thrown;
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
    }
}
