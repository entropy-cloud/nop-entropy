/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.windowing;

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

    public WindowingStrategy(String strategyId, String windowFnId, String triggerId,
                             long allowedLateness, AccumulationMode accumulationMode) {
        this.strategyId = strategyId;
        this.windowFnId = windowFnId;
        this.triggerId = triggerId;
        this.allowedLateness = allowedLateness;
        this.accumulationMode = accumulationMode != null ? accumulationMode : AccumulationMode.DISCARDING;
    }

    public WindowingStrategy() {
        this(null, null, null, 0, AccumulationMode.DISCARDING);
    }

    public String getStrategyId() { return strategyId; }
    public String getWindowFnId() { return windowFnId; }
    public String getTriggerId() { return triggerId; }
    public long getAllowedLateness() { return allowedLateness; }
    public AccumulationMode getAccumulationMode() { return accumulationMode; }
}
