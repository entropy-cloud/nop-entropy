/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import java.io.Serializable;

import io.nop.api.core.annotations.core.Internal;

/**
 * Reserved for future FLIP-27 style connector framework.
 *
 * @apiNote Reserved for future FLIP-27 style connector framework. Not yet used.
 */
@Internal
public interface RestrictionTracker<R> extends Serializable {

    boolean tryClaim(R restriction);

    R getRestriction();

    Object getProgress();

    Object snapshotWatermarkEstimatorState();
}
