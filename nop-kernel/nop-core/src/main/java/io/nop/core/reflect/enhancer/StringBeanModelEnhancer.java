/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.enhancer;

import io.nop.core.reflect.IBeanModelEnhancer;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.accessor.FunctionSpecializedPropertyGetter;
import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.BeanPropertyModel;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.Map;

public class StringBeanModelEnhancer implements IBeanModelEnhancer {
    @Override
    public boolean isForClass(Class<?> clazz) {
        return clazz == String.class;
    }

    @Override
    public void enhance(BeanModel beanModel, IClassModel classModel,
                        Map<String, BeanPropertyModel> props, Map<String, String> propAliases) {
        if (beanModel.getPropertyModel("length") == null) {
            BeanPropertyModel prop = new BeanPropertyModel();
            prop.setName("length");
            prop.setType(PredefinedGenericTypes.INT_TYPE);
            prop.setGetter(new FunctionSpecializedPropertyGetter(classModel.getMethod("length", 0).getInvoker()));
            props.put(prop.getName(), prop);
        }
    }
}