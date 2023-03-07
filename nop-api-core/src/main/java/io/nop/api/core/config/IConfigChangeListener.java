/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import java.util.Map;

public interface IConfigChangeListener {

    /**
     * 配置变化时回调此函数。通过provider获取当前值，可以通过oldValues来获知配置修改之前的值
     *
     * @param provider
     * @param oldValues 配置变化之前的值。key为varName
     */
    void onConfigChange(IConfigProvider provider, Map<String, Object> oldValues);
}