/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.jpath;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import io.nop.core.reflect.bean.BeanTool;

public class BeanMappingProvider implements MappingProvider {
    public static final BeanMappingProvider INSTANCE = new BeanMappingProvider();

    @Override
    public <T> T map(Object source, Class<T> targetType, Configuration configuration) {
        return BeanTool.castBeanToType(source, targetType);
    }

    @Override
    public <T> T map(Object source, TypeRef<T> targetType, Configuration configuration) {
        return BeanTool.castBeanToType(source, targetType.getType());
    }
}
