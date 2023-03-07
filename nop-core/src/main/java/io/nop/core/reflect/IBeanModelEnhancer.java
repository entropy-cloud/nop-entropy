/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect;

import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.BeanPropertyModel;

import java.util.Map;

public interface IBeanModelEnhancer {
    boolean isForClass(Class<?> clazz);

    void enhance(BeanModel beanModel, IClassModel classModel,
                 Map<String, BeanPropertyModel> props, Map<String, String> propAliases);
}
