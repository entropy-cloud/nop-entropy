/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

public interface ICacheFactory {
    <K, V> ICache<K, V> newCache(String name, CacheConfig config, ICacheLoader<K, V> loader);

    default <K, V> ICache<K, V> newCache(String name, CacheConfig config) {
        return newCache(name, config, null);
    }
}