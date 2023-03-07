/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.ioc;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.util.ApiStringHelper;

public enum BeanContainerStartMode {
    /**
     * 自动创建所有singleton，除了那些设置了lazy-init=false的bean之外
     */
    DEFAULT,

    /**
     * 所有bean都不自动创建，全部都在访问时创建
     */
    ALL_LAZY,

    /**
     * 忽略lazy-init设置，所有singleton都自动创建
     */
    ALL_EAGER;

    @StaticFactoryMethod
    public static BeanContainerStartMode fromText(String text) {
        if (ApiStringHelper.isEmpty(text))
            return null;

        for (BeanContainerStartMode value : values()) {
            if (value.name().equals(text))
                return value;
        }
        return null;
    }
}
