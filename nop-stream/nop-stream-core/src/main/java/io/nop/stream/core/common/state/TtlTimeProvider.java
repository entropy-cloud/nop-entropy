/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;

/**
 * Processing-time source used by TTL bookkeeping. Abstracting the clock allows tests to
 * advance time deterministically instead of sleeping. The first TTL version is
 * processing-time only (see plan Non-Goals: event-time/watermark-based TTL is future).
 */
@FunctionalInterface
public interface TtlTimeProvider extends Serializable {
    long currentTimeMillis();
}
