/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.flow;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class EdgeConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private final FlowControlPolicy flowControlPolicy;
    private final int queueCapacity;
    private final int receiveWindow;
    private final int packetSize;

    public EdgeConfig(FlowControlPolicy flowControlPolicy, int queueCapacity,
                      int receiveWindow, int packetSize) {
        this.flowControlPolicy = flowControlPolicy != null ? flowControlPolicy : FlowControlPolicy.BLOCKING_QUEUE;
        this.queueCapacity = queueCapacity;
        this.receiveWindow = receiveWindow;
        this.packetSize = packetSize;
    }

    public EdgeConfig() {
        this(FlowControlPolicy.BLOCKING_QUEUE, 1024, 1024, 4096);
    }

    public static EdgeConfig defaultConfig() {
        return new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 1024, 1024, 4096);
    }

    public FlowControlPolicy getFlowControlPolicy() { return flowControlPolicy; }
    public int getQueueCapacity() { return queueCapacity; }
    public int getReceiveWindow() { return receiveWindow; }
    public int getPacketSize() { return packetSize; }
}
