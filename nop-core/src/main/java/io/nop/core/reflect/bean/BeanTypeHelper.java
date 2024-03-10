/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.core.type.IGenericType;

public class BeanTypeHelper {
    /**
     * 根据上下文类型来获取具体泛型参数，从而决定如何进行实例化
     *
     * @param contextType 上下文类型信息
     * @return 可以用返回的属性来构造一个
     */
    public static IBeanConstructor getValueConstructor(IBeanModelManager beanModelManger, IGenericType contextType,
                                                       IGenericType typeInfo) {
        typeInfo = typeInfo.refine(contextType, contextType);
        IBeanModel beanModel = beanModelManger.getBeanModelForType(typeInfo);
        return beanModel::newInstance;
    }
}
