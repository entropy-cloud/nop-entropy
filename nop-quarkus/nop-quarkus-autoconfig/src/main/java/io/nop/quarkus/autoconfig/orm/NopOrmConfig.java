/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.autoconfig.orm;

import io.nop.commons.cache.CacheConfig;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(prefix = "nop.orm", phase = ConfigPhase.RUN_TIME)
public class NopOrmConfig extends CacheConfig {
    private CacheConfig globalCache = CacheConfig.newConfig(1000);

    public CacheConfig getGlobalCache() {
        return globalCache;
    }

    public void setGlobalCache(CacheConfig globalCache) {
        this.globalCache = globalCache;
    }
}