/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.api;

import io.nop.api.core.util.IWithIdentifier;

import java.util.Map;

public interface IDaoEntity extends IWithIdentifier {
    /**
     * 实体名称
     *
     * @return
     */
    String orm_entityName();

    /**
     * 实体的主键，如果是复合主键，则返回类型为IOrmCompositePK
     *
     * @return
     */
    Object get_id();

    Map<String, Object> orm_initedValues();

    Object orm_propValueByName(String name);

    void orm_propValueByName(String name, Object value);
}
