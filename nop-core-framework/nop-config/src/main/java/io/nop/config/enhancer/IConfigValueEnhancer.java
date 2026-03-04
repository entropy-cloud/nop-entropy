/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.enhancer;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigValue;

/**
 * 配置值增强器接口，用于对原始配置值进行处理转换
 */
public interface IConfigValueEnhancer {

    /**
     * 增强配置值
     *
     * @param value       原始配置值
     * @param targetClass 目标类型
     * @return 增强后的配置值
     */
    <T> IConfigValue<T> enhance(Object value, Class<T> targetClass);

    /**
     * 增强配置值，支持通过 ConfigProvider 解析 ${...} 表达式
     *
     * <p>实现类应该支持 Spring 风格的配置变量引用：
     * <ul>
     *   <li>${prop.name} - 引用其他配置变量</li>
     *   <li>${prop.name:default} - 带默认值的引用</li>
     * </ul>
     *
     * @param value          原始配置值
     * @param targetClass    目标类型
     * @param configProvider 配置提供者，用于解析表达式中的变量引用
     * @return 增强后的配置值
     */
    default <T> IConfigValue<T> enhance(Object value, Class<T> targetClass,
                                        IConfigProvider configProvider) {
        // 默认实现：忽略 configProvider，调用原有方法（向后兼容）
        return enhance(value, targetClass);
    }
}