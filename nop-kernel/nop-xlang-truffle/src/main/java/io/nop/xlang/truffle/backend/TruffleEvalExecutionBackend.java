/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.truffle.backend;

import io.nop.xlang.backend.EvalBackendCapability;
import io.nop.xlang.backend.EvalBackendDynamicOutcome;
import io.nop.xlang.backend.EvalBackendDynamicRequest;
import io.nop.xlang.backend.IEvalDynamicBackend;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import io.nop.xlang.truffle.runtime.XLangTruffleEngine;
import io.nop.xlang.truffle.translate.TranslationFailureEvent;
import io.nop.xlang.truffle.translate.TranslationFailureListener;
import org.graalvm.polyglot.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * truffle 执行后端 SPI 适配器（动态翻译形态）：统一决策树动态路径的执行体。
 *
 * <p>池运行时接入：惰性打开 {@link XLangContextPool}（共享 Engine + SHARED Context 池，
 * I8 租借协议），Engine/池初始化失败 = 不可用条目判定信号（sticky，保留原因不阻断启动）。
 *
 * <p>第三分支（单元级翻译失败）接线：池打开时经 {@code pool.getLanguage()} 注册
 * {@link TranslationFailureListener} 消费者（I8 观测事件接口的真实消费），求值返回异常且
 * <b>per-request 翻译失败标志</b>置位（parse 期 {@code TranslationCache.getOrBuild} 抛错
 * 路径，经 {@code TranslatedEval} 携带）= 单元级翻译失败 → 返回 fallback（裁决入口改走
 * 解释器 + 降级观测）；未置位的异常是真实求值错误，原样重抛（fail-fast 保持）。
 * 事件 map（按 sourceKey）仅作降级细节的事件载荷来源，不参与关联判定（check2 P1 修复：
 * 并发下同 sourceKey 多树求值的键碰撞会误配——真实求值错误被降级重放、翻译失败被硬抛）。
 */
public class TruffleEvalExecutionBackend implements IEvalDynamicBackend, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TruffleEvalExecutionBackend.class);

    public static final String BACKEND_ID = "truffle";

    private static final int RECENT_FAILURES_MAX = 256;

    private static final TruffleEvalExecutionBackend INSTANCE =
            new TruffleEvalExecutionBackend(XLangTruffleEngine::sharedEngine);

    public static TruffleEvalExecutionBackend instance() {
        return INSTANCE;
    }

    private final Supplier<Engine> engineProvider;

    private final TranslationFailureListener failureListener = this::onTranslationFailure;

    private volatile XLangContextPool pool;

    private volatile String unavailableReason;

    private final LinkedHashMap<String, TranslationFailureEvent> recentTranslationFailures =
            new LinkedHashMap<String, TranslationFailureEvent>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TranslationFailureEvent> eldest) {
                    return size() > RECENT_FAILURES_MAX;
                }
            };

    /** 缺省引擎供给 = 共享 Engine 单例（创建失败显式 IllegalStateException = 不可用信号） */
    public TruffleEvalExecutionBackend() {
        this(XLangTruffleEngine::sharedEngine);
    }

    /** 注入式引擎供给（初始化失败注入的测试缝与嵌入场景接入缝） */
    public TruffleEvalExecutionBackend(Supplier<Engine> engineProvider) {
        this.engineProvider = engineProvider;
    }

    /**
     * 注册时初始化探测：探测引擎可用性，失败 → sticky 不可用（保留原因，不抛出、不阻断启动）。
     * 由模块初始化器调用（也可跳过探测，首次求值时惰性探测——探测结果等价）。
     */
    public void probeInitialization() {
        try {
            engineProvider.get();
        } catch (RuntimeException e) {
            LOG.warn("nop.xlang.execution.backend-init-failed: backend={}", BACKEND_ID, e);
            unavailableReason = "engine-init-failed: " + e;
        }
    }

    @Override
    public String getBackendId() {
        return BACKEND_ID;
    }

    @Override
    public Set<EvalBackendCapability> getCapabilities() {
        return Collections.unmodifiableSet(EnumSet.of(EvalBackendCapability.DYNAMIC_TRANSLATION));
    }

    @Override
    public boolean isAvailable() {
        return unavailableReason == null;
    }

    @Override
    public String getUnavailableReason() {
        return unavailableReason;
    }

    @Override
    public EvalBackendDynamicOutcome executeDynamic(EvalBackendDynamicRequest request) {
        String sourceKey = deriveSourceKey(request);
        XLangContextPool currentPool = pool();
        if (currentPool == null)
            return EvalBackendDynamicOutcome.fallback(EvalBackendDynamicOutcome.FALLBACK_BACKEND_UNAVAILABLE,
                    unavailableReason);

        try (XLangContextPool.Lease lease = currentPool.lease()) {
            XLangTruffleEval.TranslatedEval result = lease.eval(sourceKey, request.getTree(),
                    request.getRuntime().getScope(), request.getRuntime().getOut());
            Throwable thrown = result.getThrown();
            if (thrown == null)
                return EvalBackendDynamicOutcome.ofValue(result.getReturnValue(),
                        result.getUnit() == null ? null : result.getUnit().getRootNode());
            // 异常关联判定（check2 P1 修复）：以 per-request 翻译失败标志为准（parse 期
            // getOrBuild 抛错路径置位）——命中 = 单元级降级（第三分支）；否则真实求值错误
            // 重抛。不再按 sourceKey 从共享 map 关联（并发下同 sourceKey 的多树求值会
            // 误配：真实求值错误误降级重放 / 翻译失败被硬抛）。事件 map 仅在标志命中时
            // 取本次求值的事件作降级细节（取不到时以原始异常为细节）。
            if (result.isTranslationFailed()) {
                TranslationFailureEvent event = takeRecentFailure(sourceKey);
                return EvalBackendDynamicOutcome.fallback(
                        EvalBackendDynamicOutcome.FALLBACK_UNIT_TRANSLATION_FAILURE,
                        event != null ? event : thrown);
            }
            if (thrown instanceof RuntimeException)
                throw (RuntimeException) thrown;
            if (thrown instanceof Error)
                throw (Error) thrown;
            throw new io.nop.api.core.exceptions.NopEvalException(
                    "truffle-eval-failed: " + sourceKey, null, thrown);
        }
        // 池租借/求值协议异常不参与翻译失败关联（check2 P1 修复）：协议违约（lease/handoff
        // IllegalStateException 族）是接线缺陷红灯，原样上抛；此前按 sourceKey 关联共享
        // map 在并发陈旧事件存在时会把协议异常误吞成降级，掩盖缺陷。
    }

    /** 关闭后端（测试与嵌入场景）：关闭池并反注册消费者（SHARED 语言实例长于池，防泄漏） */
    @Override
    public synchronized void close() {
        // 与pool()同锁（check2 P2 修复）：不持锁时close()可能在pool()的open()预热期间读到
        // pool==null直接返回，随后pool()把新建池赋给字段——已关闭的后端泄漏整个Context池
        XLangContextPool currentPool = this.pool;
        this.pool = null;
        if (currentPool != null) {
            currentPool.getLanguage().removeTranslationFailureListener(failureListener);
            currentPool.close();
        }
    }

    private synchronized XLangContextPool pool() {
        if (unavailableReason != null)
            return null;
        if (pool == null) {
            try {
                // 引擎创建失败/池打开失败（含预热接线自检失败）= sticky 不可用条目
                engineProvider.get();
                XLangContextPool opened = XLangContextPool.open();
                opened.getLanguage().addTranslationFailureListener(failureListener);
                pool = opened;
            } catch (RuntimeException e) {
                LOG.warn("nop.xlang.execution.backend-init-failed: backend={}", BACKEND_ID, e);
                unavailableReason = "pool-init-failed: " + e;
                return null;
            }
        }
        return pool;
    }

    /** 翻译失败事件记录（package-private：测试注入陈旧事件的种入通道，正常消费经监听器）。 */
    void onTranslationFailure(TranslationFailureEvent event) {
        synchronized (recentTranslationFailures) {
            recentTranslationFailures.put(event.getSourceKey(), event);
        }
    }

    private TranslationFailureEvent takeRecentFailure(String sourceKey) {
        synchronized (recentTranslationFailures) {
            return recentTranslationFailures.remove(sourceKey);
        }
    }

    /**
     * sourceKey 口径：resourcePath 单元 = resourcePath；无 resourcePath 动态树 = 树实例身份键
     * （同一 action 复用同一树实例 → 稳定命中翻译缓存；键语义与 I8 动态源内容哈希键并存不冲突）。
     */
    static String deriveSourceKey(EvalBackendDynamicRequest request) {
        String resourcePath = request.getResourcePath();
        if (resourcePath != null)
            return resourcePath;
        return "xl-route-dyn/" + Integer.toHexString(System.identityHashCode(request.getTree()));
    }
}
