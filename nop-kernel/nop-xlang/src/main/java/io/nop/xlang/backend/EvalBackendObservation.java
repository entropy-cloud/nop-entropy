/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.metrics.GlobalMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后端降级观测（命名契约，落 docs-for-ai）：
 *
 * <ul>
 * <li>WARN 日志：logger {@code io.nop.xlang.backend.EvalBackendObservation}，消息键
 * {@code nop.xlang.execution.backend-degraded}，格式 {@code backend={}, reason={}, sourceKey={}}。
 * 每次降级一条（诊断配置 force-interpreter 为静默隔离，不产生降级事件）。</li>
 * <li>指标：counter {@code nop.xlang.execution.backend-degradation}，tags {@code backend}
 * （java|truffle）与 {@code reason}（config-disabled | unavailable | unit-translation-failure |
 * generated-binding-missing | generated-fingerprint-mismatch | tenant-divergent-tree |
 * codegen-pipeline-missed）。
 * 分级语义（I10）：generated-binding-missing / generated-fingerprint-mismatch = stale 缺陷
 * （每次 WARN）；tenant-divergent-tree = 租户差异化树预期稳态（WARN 按 sourceKey 去重，
 * 指标计数不衰减）。codegen-pipeline-missed（I11）= 构建管线漏跑缺陷（初始化装载时全局
 * 一次 WARN + java 后端不可用条目，sourceKey = 清单文件探测路径）。</li>
 * <li>不可用条目查询：{@link EvalBackendRegistry#getUnavailableBackends()}。</li>
 * </ul>
 */
public final class EvalBackendObservation {

    public static final String METRIC_NAME = "nop.xlang.execution.backend-degradation";

    public static final String LOG_MESSAGE_KEY = "nop.xlang.execution.backend-degraded";

    public static final String TAG_BACKEND = "backend";

    public static final String TAG_REASON = "reason";

    public static final String REASON_CONFIG_DISABLED = "config-disabled";

    public static final String REASON_UNAVAILABLE = "unavailable";

    public static final String REASON_UNIT_TRANSLATION_FAILURE = "unit-translation-failure";

    public static final String REASON_GENERATED_BINDING_MISSING = "generated-binding-missing";

    /** 清单条目在、树指纹失配、绑定发生时无租户上下文 = stale 缺陷（基树变更未重生成） */
    public static final String REASON_GENERATED_FINGERPRINT_MISMATCH = "generated-fingerprint-mismatch";

    /** 清单条目在、树指纹失配、绑定发生时租户上下文活跃 = 租户差异化树预期稳态（非缺陷） */
    public static final String REASON_TENANT_DIVERGENT_TREE = "tenant-divergent-tree";

    /**
     * java 后端启用且部署声明应有生成产物（require-manifest=true）但 classpath 无清单文件 =
     * 构建管线漏跑缺陷（I11 漏跑判别子：全局一次性 WARN + 不可用条目，全部资源走动态路径/解释器）。
     */
    public static final String REASON_CODEGEN_PIPELINE_MISSED = "codegen-pipeline-missed";

    /** 稳态去重集上界：超界回退每次 WARN（宁噪声不无界内存） */
    static final int STEADY_STATE_DEDUP_MAX_KEYS = 1024;

    private static final Set<String> STEADY_STATE_WARNED_KEYS = ConcurrentHashMap.newKeySet();

    private static final Logger LOG = LoggerFactory.getLogger(EvalBackendObservation.class);

    private EvalBackendObservation() {
    }

    /** 记一次降级观测：WARN 日志 + 指标计数 */
    public static void onDegradation(String backendId, String reason, String sourceKey, SourceLocation loc) {
        warn(backendId, reason, sourceKey, loc);
        count(backendId, reason);
    }

    /**
     * 记一次稳态降级观测（I10 分级：租户差异化树）：WARN 按 sourceKey 去重（once-per-path，
     * 有界），指标计数不衰减（每次降级都计数）。
     */
    public static void onSteadyStateDegradation(String backendId, String reason, String sourceKey,
                                                SourceLocation loc) {
        if (sourceKey == null || STEADY_STATE_WARNED_KEYS.size() >= STEADY_STATE_DEDUP_MAX_KEYS
                || STEADY_STATE_WARNED_KEYS.add(sourceKey)) {
            warn(backendId, reason, sourceKey, loc);
        }
        count(backendId, reason);
    }

    /** 测试隔离用：清空稳态去重集 */
    public static void clearSteadyStateDedup() {
        STEADY_STATE_WARNED_KEYS.clear();
    }

    private static void warn(String backendId, String reason, String sourceKey, SourceLocation loc) {
        if (loc != null) {
            LOG.warn("{}: backend={}, reason={}, sourceKey={}, loc={}",
                    LOG_MESSAGE_KEY, backendId, reason, sourceKey, loc);
        } else {
            LOG.warn("{}: backend={}, reason={}, sourceKey={}",
                    LOG_MESSAGE_KEY, backendId, reason, sourceKey);
        }
    }

    private static void count(String backendId, String reason) {
        GlobalMeterRegistry.instance().counter(METRIC_NAME, TAG_BACKEND, backendId, TAG_REASON, reason).increment();
    }

    /** 当前降级计数（测试与诊断用） */
    public static double degradationCount(String backendId, String reason) {
        return GlobalMeterRegistry.instance().counter(METRIC_NAME, TAG_BACKEND, backendId, TAG_REASON, reason).count();
    }
}
