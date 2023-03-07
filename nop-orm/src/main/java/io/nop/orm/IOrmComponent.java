/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import java.util.Map;

public interface IOrmComponent {
    Object orm_propValueByName(String propName);

    void orm_propValueByName(String propName, Object value);

    void bindToEntity(IOrmEntity owner, Map<String, Integer> propToColPropIds);

    /**
     * 将组件对象上缓存的属性变化更新到底层的实体对象上
     */
    void flushToEntity();
}
