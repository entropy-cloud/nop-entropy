/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.spring.autoconfig.orm;

import io.nop.commons.cache.CacheConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nop.orm")
public class NopOrmConfig extends CacheConfig {
    private CacheConfig globalCache = CacheConfig.newConfig(1000);

    public CacheConfig getGlobalCache() {
        return globalCache;
    }

    public void setGlobalCache(CacheConfig globalCache) {
        this.globalCache = globalCache;
    }
}