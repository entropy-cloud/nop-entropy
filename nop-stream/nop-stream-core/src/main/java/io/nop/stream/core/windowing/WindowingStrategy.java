/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class WindowingStrategy implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String strategyId;
    private final String windowFnId;
    private final String triggerId;
    private final long allowedLateness;
    private final AccumulationMode accumulationMode;
    /**
     * WI8 (plan 2332): max-delay fallback window (ms, past window end, PROCESSING
     * time). 0 = disabled. Event-time windows only — a processing-time window's
     * cleanup timer fires at window end, strictly before any maxDelay deadline, so
     * combining the two is a construction error.
     */
    private final long maxDelay;

    public WindowingStrategy(String strategyId, String windowFnId, String triggerId,
                             long allowedLateness, AccumulationMode accumulationMode) {
        this(strategyId, windowFnId, triggerId, allowedLateness, accumulationMode, 0L);
    }

    public WindowingStrategy(String strategyId, String windowFnId, String triggerId,
                             long allowedLateness, AccumulationMode accumulationMode, long maxDelay) {
        if (allowedLateness < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "allowedLateness must be >= 0, but was: " + allowedLateness);
        }
        if (maxDelay < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "maxDelay must be >= 0 (0 = disabled), but was: " + maxDelay);
        }
        this.strategyId = strategyId;
        this.windowFnId = windowFnId;
        this.triggerId = triggerId;
        this.allowedLateness = allowedLateness;
        this.accumulationMode = accumulationMode != null ? accumulationMode : AccumulationMode.DISCARDING;
        this.maxDelay = maxDelay;
    }

    /** WI8: max-delay fallback window in ms past window end (0 = disabled). */
    public long getMaxDelay() { return maxDelay; }

    public WindowingStrategy() {
        this(null, null, null, 0, AccumulationMode.DISCARDING);
    }

    public String getStrategyId() { return strategyId; }
    public String getWindowFnId() { return windowFnId; }
    public String getTriggerId() { return triggerId; }
    public long getAllowedLateness() { return allowedLateness; }
    public AccumulationMode getAccumulationMode() { return accumulationMode; }
}
