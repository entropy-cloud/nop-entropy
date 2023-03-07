/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.util.Guard;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Objects;

/**
 * 缓存引用包含两个部分：一个是静态的缓存名称，另一个是与具体业务数据相关的缓存key
 */
@ImmutableBean
public class CacheRef implements Serializable {
    private static final long serialVersionUID = 2889379541290649441L;

    private final String cacheName;
    private final Serializable cacheKey;
    private final int hash;

    public CacheRef(@Nonnull String cacheName, Serializable cacheKey) {
        this.cacheName = Guard.notNull(cacheName, "cacheName");
        this.cacheKey = cacheKey;
        this.hash = cacheName.hashCode() * 37 + (cacheKey == null ? 0 : cacheKey.hashCode());
    }

    public String toString() {
        return "CacheRef[cacheName=" + cacheName + ",cacheKey=" + cacheKey + "]";
    }

    public String getCacheName() {
        return cacheName;
    }

    public Object getCacheKey() {
        return cacheKey;
    }

    public int hashCode() {
        return hash;
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CacheRef))
            return false;
        CacheRef other = (CacheRef) o;
        return this.cacheName.equals(other.cacheName) && Objects.equals(cacheKey, other.cacheKey);
    }
}