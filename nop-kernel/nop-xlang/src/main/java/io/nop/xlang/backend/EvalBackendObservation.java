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

/**
 * 后端降级观测（命名契约，落 docs-for-ai）：
 *
 * <ul>
 * <li>WARN 日志：logger {@code io.nop.xlang.backend.EvalBackendObservation}，消息键
 * {@code nop.xlang.execution.backend-degraded}，格式 {@code backend={}, reason={}, sourceKey={}}。
 * 每次降级一条（诊断配置 force-interpreter 为静默隔离，不产生降级事件）。</li>
 * <li>指标：counter {@code nop.xlang.execution.backend-degradation}，tags {@code backend}
 * （java|truffle）与 {@code reason}（config-disabled | unavailable | unit-translation-failure |
 * generated-binding-missing）。</li>
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

    private static final Logger LOG = LoggerFactory.getLogger(EvalBackendObservation.class);

    private EvalBackendObservation() {
    }

    /** 记一次降级观测：WARN 日志 + 指标计数 */
    public static void onDegradation(String backendId, String reason, String sourceKey, SourceLocation loc) {
        if (loc != null) {
            LOG.warn("{}: backend={}, reason={}, sourceKey={}, loc={}",
                    LOG_MESSAGE_KEY, backendId, reason, sourceKey, loc);
        } else {
            LOG.warn("{}: backend={}, reason={}, sourceKey={}",
                    LOG_MESSAGE_KEY, backendId, reason, sourceKey);
        }
        GlobalMeterRegistry.instance().counter(METRIC_NAME, TAG_BACKEND, backendId, TAG_REASON, reason).increment();
    }

    /** 当前降级计数（测试与诊断用） */
    public static double degradationCount(String backendId, String reason) {
        return GlobalMeterRegistry.instance().counter(METRIC_NAME, TAG_BACKEND, backendId, TAG_REASON, reason).count();
    }
}
