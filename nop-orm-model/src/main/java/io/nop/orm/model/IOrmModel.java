/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.hook.IPropGetMissingHook;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.nop.orm.model.OrmModelErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME;
import static io.nop.orm.model.OrmModelErrors.ARG_TABLE_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLLECTION_NAME;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_ENTITY_MODEL_FOR_TABLE;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_ENTITY_NAME;

public interface IOrmModel extends IPropGetMissingHook {
    /**
     * 是否有实体启用了tenant支持
     */
    boolean isAnyEntityUseTenant();

    List<IEntityModel> getEntityModelInTopoOrder(Collection<String> entityNames);

    List<IEntityModel> sortEntityModelInTopoOrder(Collection<IEntityModel> entityModels);

    Collection<? extends IEntityModel> getEntityModelsInTopoOrder();

    List<? extends IEntityModel> getEntityModels();

    Set<String> getEntityNames();

    IEntityModel getEntityModel(String entityName);

    IEntityModel getEntityModelByTableName(String tableName);

    IEntityModel getEntityModelBySnakeCaseName(String name);

    default IEntityModel requireEntityModelBySnakeCaseName(String name) {
        IEntityModel entityModel = getEntityModelBySnakeCaseName(name);
        if (entityModel == null)
            throw new NopException(ERR_ORM_UNKNOWN_ENTITY_MODEL_FOR_TABLE).param(ARG_TABLE_NAME, name);
        return entityModel;
    }

    default IEntityModel requireEntityModelByTableName(String tableName) {
        IEntityModel entityModel = getEntityModelByTableName(tableName);
        if (entityModel == null)
            throw new NopException(ERR_ORM_UNKNOWN_ENTITY_MODEL_FOR_TABLE).param(ARG_TABLE_NAME, tableName);
        return entityModel;
    }

    default IEntityModel requireEntityModel(String entityName) {
        IEntityModel entityModel = getEntityModel(entityName);
        if (entityModel == null)
            throw new NopException(ERR_ORM_UNKNOWN_ENTITY_NAME).param(ARG_ENTITY_NAME, entityName);
        return entityModel;
    }

    IEntityRelationModel getCollectionModel(String collectionName);

    default IEntityRelationModel requireCollectionModel(String collectionName) {
        IEntityRelationModel collectionModel = getCollectionModel(collectionName);
        if (collectionModel == null)
            throw new NopException(ERR_ORM_UNKNOWN_COLLECTION_NAME).param(ARG_COLLECTION_NAME, collectionName);
        return collectionModel;
    }

    List<DictBean> getDicts();

    DictBean getDict(String name);
}