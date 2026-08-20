package io.nop.xlang.truffle.lang;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.source.Source;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.eval.EvalHandoff;
import io.nop.xlang.truffle.translate.TranslationCache;
import io.nop.xlang.truffle.translate.TranslationFailureEvent;
import io.nop.xlang.truffle.translate.TranslationFailureListener;
import io.nop.xlang.truffle.translate.TranslationFailureRecorder;
import io.nop.xlang.truffle.translate.TranslatedUnit;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * XLang 的 Truffle 语言入口（设计 truffle 02 §三）。
 *
 * <p>注册形态：id {@code xl}、无 parser——{@code parse(ParsingRequest)}
 * 按合成 Source 名查翻译缓存（待翻译树经 {@link EvalHandoff} 同线程交接）。
 * <b>SHARED 终态</b>（plan I8，自 EXCLUSIVE 过渡形态切换——编译期常量变更 + 重新编译，
 * 注册结构未重构，I5 决策 D1 承诺保持）：一个语言实例服务多个同时存活的 Context
 * （池化并发经共享 Engine 显式绑定，{@code io.nop.xlang.truffle.runtime.XLangContextPool}）；
 * 非共享 Engine 的单 Context 用法在 SHARED 下合法（每 Context 独立 Engine 时语言实例不共享，
 * 行为与过渡形态一致——SHARED 为超集形态）。
 *
 * <p>{@code initializeMultipleContexts} 覆写（SHARED 要求；SL {@code SLLanguage} 覆写先例）：
 * XLangLanguage 无 single-context 假设需失效——语言实例可变数据 = 翻译缓存（monitor 守护
 * + 翻译锁外并行）与翻译失败记录器（同步集合），均已按 SHARED 并发要求处置；覆写体 =
 * 置 volatile 旗标，经 {@link #isMultipleContextsInitialized()} 暴露，作为"多 Context 共享
 * 真实激活"的接线证据（框架保证在任何共享使用前调用本方法）。
 *
 * <p>语言实例只存可共享数据：翻译缓存（resourcePath/源内容键 + 树指纹，跨 Context 复用，
 * 容量上限/LRU 淘汰已落地）与翻译失败观测（内置记录器 + 已注册消费者，无消费者时不静默）。
 */
@Registration(id = XLangLanguage.ID, name = "XLang", version = "1.0.0",
        defaultMimeType = XLangLanguage.MIME_TYPE, characterMimeTypes = {XLangLanguage.MIME_TYPE},
        contextPolicy = ContextPolicy.SHARED)
public final class XLangLanguage extends TruffleLanguage<XLangContext> {

    public static final String ID = "xl";

    public static final String MIME_TYPE = "application/x-xlang";

    private final TranslationCache translationCache = new TranslationCache();

    private final TranslationFailureRecorder translationFailures = new TranslationFailureRecorder();

    private final List<TranslationFailureListener> translationFailureListeners = new CopyOnWriteArrayList<>();

    private volatile boolean multipleContextsInitialized;

    public TranslationCache getTranslationCache() {
        return translationCache;
    }

    /**
     * 翻译失败默认记录器（内存环形保留 + 可查询；无消费者时不静默的载体）。
     */
    public TranslationFailureRecorder getTranslationFailures() {
        return translationFailures;
    }

    /**
     * 注册翻译失败消费者（I9 truffle 侧 SPI 适配器消费路径，plan I8 Phase 1 §6 裁定）。
     */
    public void addTranslationFailureListener(TranslationFailureListener listener) {
        translationFailureListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 上报翻译失败事件：内置记录器恒记 + 已注册消费者追加通知（fail-fast 主语义在
     * {@code TranslationCache} 抛错路径，事件是观测增量不是降级）。
     */
    public void reportTranslationFailure(TranslationFailureEvent event) {
        translationFailures.record(event);
        for (TranslationFailureListener listener : translationFailureListeners)
            listener.onTranslationFailure(event);
    }

    @Override
    protected void initializeMultipleContexts() {
        multipleContextsInitialized = true;
    }

    /**
     * 多 Context 共享是否已激活（框架在任何共享使用前调用 {@code initializeMultipleContexts}；
     * 测试接线断言依据——经翻译产物根节点 {@code getXLangLanguage()} 取本实例断言旗标置位）。
     */
    public boolean isMultipleContextsInitialized() {
        return multipleContextsInitialized;
    }

    @Override
    protected XLangContext createContext(Env env) {
        return new XLangContext(env);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source source = request.getSource();
        EvalHandoff.Pending pending = EvalHandoff.takePending(source.getName());
        IExecutableExpression tree = pending.getTree();
        try {
            TranslatedUnit unit = translationCache.getOrBuild(pending.getSourceKey(), tree, this);
            pending.setResolvedUnit(unit);
            return unit.getCallTarget();
        } catch (RuntimeException e) {
            // parse 期失败（如支持集外节点翻译 fail-fast）经 handoff 透传原始异常
            // （与执行期异常同一交接通道，宿主侧 facade unwrap 得到原始 NopEvalException）
            pending.captureThrown(e);
            throw e;
        }
    }

    /**
     * 节点侧 context 读取入口（programmatic 节点无 DSL 生成的 ContextReference，
     * 经基类 protected 静态查询开放给同包外节点；池归还路径亦经此取语言 context）。
     */
    public static XLangContext currentContext() {
        return getCurrentContext(XLangLanguage.class);
    }
}
