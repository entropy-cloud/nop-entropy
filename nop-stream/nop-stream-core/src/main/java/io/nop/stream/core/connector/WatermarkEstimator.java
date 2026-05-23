/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import java.io.Serializable;

public interface WatermarkEstimator extends Serializable {

    void observe(long timestamp);

    long getCurrentWatermark();

    Object snapshotState();

    void restoreState(Object state);
}
