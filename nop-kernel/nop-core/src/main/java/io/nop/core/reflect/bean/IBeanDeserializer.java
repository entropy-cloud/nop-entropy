/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.type.IGenericType;

import java.util.Map;

/**
 * {@link IBeanDeserializerFactory} 按名称集中管理所有的IBeanSerializer。在Bean对象的get或set方法上， 可以通过{@code @BeanDeserializer}
 * 来指定对应的反序列化器的名称
 */
public interface IBeanDeserializer {
    /**
     * 根据传入的bean结构数据，构造指定类型的对象
     *
     * @param bean       接收数据
     * @param targetType 目标类型
     * @param selection  可以选择只反序列化某些字段
     * @param objMap     用来记录对象循环引用的Map
     * @return 结果对象
     */
    Object deserialize(Object bean, IGenericType targetType, FieldSelectionBean selection, Map<Object, Object> objMap);
}
