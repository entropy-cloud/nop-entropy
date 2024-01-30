/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.accessor.MethodPropertyGetter;
import io.nop.core.reflect.bean.BeanModel;
import io.nop.core.reflect.bean.BeanPropertyModel;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class AnnotationBeanModelBuilder {

    public IBeanModel buildFromClassModel(IClassModel classModel) {
        BeanModel beanModel = new BeanModel();
        beanModel.setType(classModel.getType());
        beanModel.setDescription(classModel.getDescription());
        beanModel.setImmutable(true);

        buildProps(beanModel, classModel);
        return beanModel;
    }

    private void buildProps(BeanModel beanModel, IClassModel classModel) {
        Map<String, IBeanPropertyModel> props = new TreeMap<>();

        for (IFunctionModel method : classModel.getMethods()) {
            if (method.isPublic() && method.getArgCount() == 0) {
                if (!isObjectMethod(method)) {
                    IBeanPropertyModel propModel = buildPropModel(method);
                    props.put(propModel.getName(), propModel);
                }
            }
        }
        beanModel.setPropertyModels(CollectionHelper.immutableSortedMap(props));
        beanModel.setPropAliases(Collections.emptyMap());
    }

    private boolean isObjectMethod(IFunctionModel method) {
        String name = method.getName();
        return name.equals("hashCode") || name.equals("toString");
    }

    private IBeanPropertyModel buildPropModel(IFunctionModel method) {
        BeanPropertyModel propModel = new BeanPropertyModel();
        propModel.setName(method.getName());
        propModel.setType(method.getReturnType());
        propModel.setGetter(new MethodPropertyGetter(method));
        return propModel;
    }
}