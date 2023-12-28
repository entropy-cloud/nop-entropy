/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.api;

import java.util.Set;

/**
 * 统一管理所有的Dao。每个表对应一个Dao对象
 */
public interface IDaoProvider {
    Set<String> getEntityNames();

    String normalizeEntityName(String entityName);

    <T extends IDaoEntity> IEntityDao<T> dao(String entityName);

    default <T extends IDaoEntity> IEntityDao<T> daoFor(Class<T> entityClass) {
        return dao(entityClass.getName());
    }

    <T extends IDaoEntity> IEntityDao<T> daoForTable(String tableName);

    default <T extends IDaoEntity> T newEntity(Class<T> entityClass) {
        return daoFor(entityClass).newEntity();
    }
}