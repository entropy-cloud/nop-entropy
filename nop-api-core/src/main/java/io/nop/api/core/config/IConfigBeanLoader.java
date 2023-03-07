/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.config;

import java.lang.reflect.Type;

/**
 * 根据配置名称装载配置文件，并把它反序列化为指定JavaBean类型
 */
public interface IConfigBeanLoader {
    default <T> T loadConfigBean(String configName, Class<T> beanType, boolean ignoreUnknown) {
        return loadConfigBeanForType(configName, beanType, ignoreUnknown);
    }

    <T> T loadConfigBeanForType(String configName, Type beanType, boolean ignoreUnknown);
}