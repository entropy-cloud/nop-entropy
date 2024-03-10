/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.dao.api.IDaoComponent;

import java.util.Map;

public interface IOrmComponent extends IDaoComponent {
    Object orm_propValueByName(String propName);

    void orm_propValueByName(String propName, Object value);

    void bindToEntity(IOrmEntity owner, Map<String, Integer> propToColPropIds);

    /**
     * 将组件对象上缓存的属性变化更新到底层的实体对象上
     */
    void flushToEntity();

    default void onEntityFlush() {
        flushToEntity();
    }

    default void onEntityDelete(boolean logicalDelete) {

    }
}
