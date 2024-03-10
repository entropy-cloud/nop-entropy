/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.commons.collections.IntArray;
import io.nop.dao.api.IDaoEntity;
import io.nop.orm.model.IEntityModel;

public interface IEqlQueryContext {
    Object internalReadId(Object[] values, int fromIndex, IEntityModel entityModel);

    Object castId(IEntityModel entityModel, Object id);

    IDaoEntity internalMakeEntity(String entityName, Object id, Object[] propValues, IntArray propIds);

    IDaoEntity internalLoad(String entityName, Object id);
}
