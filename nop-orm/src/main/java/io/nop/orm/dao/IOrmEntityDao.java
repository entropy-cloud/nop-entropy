/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public interface IOrmEntityDao<T extends IOrmEntity> extends IEntityDao<T> {
    IEntityModel getEntityModel();

    IOrmTemplate getOrmTemplate();

    /**
     * 尝试更新实体。如果成功通过乐观锁校验，则返回更新记录
     *
     * @param entities 待更新实体
     * @return 所有更新后的实体
     */
    default List<T> tryUpdateManyWithVersionCheck(Collection<T> entities) {
        if (entities == null || entities.isEmpty())
            return Collections.emptyList();

        entities.forEach(entity -> entity.orm_disableVersionCheckError(true));
        batchUpdateEntities(entities);
        flushSession();
        return entities.stream().filter(entity -> !entity.orm_readonly()).collect(Collectors.toList());
    }
}
