/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import io.nop.commons.metrics.IWithStats;
import jakarta.annotation.Nonnull;

public interface ICacheManagement<K> extends IWithStats<CacheStats> {
    String getName();

    void remove(@Nonnull K key);

    void clear();

    default void clearForTenant(String tenantId) {

    }
}