/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import java.util.List;
import java.util.Map;

public interface IEntityComponentModel extends IEntityPropModel {

    String getClassName();

    List<? extends IEntityComponentPropModel> getProps();

    IEntityComponentPropModel getProp(String name);

    IEntityComponentPropModel requireProp(String name);

    /**
     * 从组件的属性名称映射到实体字段的propId
     */
    Map<String, Integer> getColumnPropIdMap();

    boolean isNeedFlush();

    default String getJavaTypeName() {
        return getClassName();
    }

    default boolean isMandatory() {
        List<? extends IEntityComponentPropModel> props = getProps();
        for (IEntityComponentPropModel prop : props) {
            IColumnModel col = prop.getColumnModel();
            if (col == null)
                return false;
            if (!col.isMandatory())
                return false;
        }
        return true;
    }
}