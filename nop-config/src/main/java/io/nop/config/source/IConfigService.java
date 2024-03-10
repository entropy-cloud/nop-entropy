/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.source;

import io.nop.api.core.annotations.core.Internal;

/**
 * 通过配置服务获取远程配置，并实现配置自动更新。 配置系统会使用{@link java.util.ServiceLoader}机制来查找{@link IConfigService}接口的实现类。
 */
@Internal
public interface IConfigService {
    default String getName() {
        return getClass().getName();
    }

    void start();

    void stop();

    /**
     * 获取配置信息，并注册监听器。当配置发生变化时会触发监听器
     */
    IConfigSource getConfigSource(IConfigSource baseSource, String dataId);
}
