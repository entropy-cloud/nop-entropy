/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.factory;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.orm.IOrmEntity;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.id.IEntityIdGenerator;
import io.nop.orm.id.OrmEntityIdGenerator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.persister.CollectionPersisterImpl;
import io.nop.orm.persister.EntityPersisterImpl;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_ID;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_ID_NOT_MATCH_DEF_IN_MODEL;

/**
 * 将构建PersistEnv的代码从SessionFactoryImpl中剥离出来，简化SessionFactoryImpl类的实现。
 *
 * @author canonical_entropy@163.com
 */
class PersistEnvBuilder {
    private final IOrmModel ormModel;
    private final ISequenceGenerator sequenceGenerator;
    private final SessionFactoryImpl env;

    private Map<String, IEntityPersister> entityPersisters;
    private Map<String, ICollectionPersister> collectionPersisters;

    public PersistEnvBuilder(IOrmModel ormModel, ISequenceGenerator sequenceGenerator, SessionFactoryImpl env) {
        this.ormModel = ormModel;
        this.sequenceGenerator = sequenceGenerator;
        this.env = env;
    }

    public void build() {
        this.entityPersisters = buildEntityPersisters();
        this.collectionPersisters = buildCollectionPersisters();
        this.validate();
    }

    public Map<String, IEntityPersister> getEntityPersisters() {
        return entityPersisters;
    }

    public Map<String, ICollectionPersister> getCollectionPersisters() {
        return collectionPersisters;
    }

    private void validate() {
        for (IEntityModel entityModel : ormModel.getEntityModelsInTopoOrder()) {
            validateEntity(entityModel);
        }
    }

    private void validateEntity(IEntityModel entityModel) {
        IEntityPersister persister = entityPersisters.get(entityModel.getName());
        IOrmEntity entity = persister.newEntity(null);
        for (int i = 1, n = entity.orm_propIdBound(); i < n; i++) {
            IColumnModel col = entityModel.getColumnByPropId(i, true);
            if (col != null) {
                String colName = entity.orm_propName(i);
                if (!Objects.equals(colName, col.getName()))
                    throw new OrmException(ERR_ORM_ENTITY_PROP_ID_NOT_MATCH_DEF_IN_MODEL)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, col.getName())
                            .param(ARG_ENTITY_PROP_NAME, colName).param(ARG_PROP_ID, i);
            }

            String name = entity.orm_propName(i);
            if (name != null && col == null) {
                throw new OrmException(ERR_ORM_ENTITY_PROP_ID_NOT_MATCH_DEF_IN_MODEL)
                        .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, null)
                        .param(ARG_ENTITY_PROP_NAME, name).param(ARG_PROP_ID, i);
            }
        }
    }

    IEntityPersister newEntityPersister(IEntityModel entityModel, IOrmModel ormModel) {
        EntityPersisterImpl persister = new EntityPersisterImpl();
        IEntityIdGenerator idGenerator = new OrmEntityIdGenerator(entityModel, sequenceGenerator);
        persister.init(entityModel, idGenerator, env);
        return persister;
    }

    private Map<String, IEntityPersister> buildEntityPersisters() {
        Collection<? extends IEntityModel> entityModels = ormModel.getEntityModelsInTopoOrder();
        Map<String, IEntityPersister> persisters = new HashMap<>(entityModels.size());

        for (IEntityModel entityModel : entityModels) {
            try {
                IEntityPersister persister = newEntityPersister(entityModel, ormModel);
                persisters.put(entityModel.getName(), persister);
            } catch (NopException e) {
                e.addXplStack("createEntityPersistDriver:" + entityModel.getName());
                throw e;
            }
        }
        return persisters;
    }

    ICollectionPersister newCollectionPersister(IEntityRelationModel collectionModel, IOrmModel ormModel) {
        CollectionPersisterImpl persister = new CollectionPersisterImpl();
        persister.init(collectionModel, env);
        return persister;
    }

    private Map<String, ICollectionPersister> buildCollectionPersisters() {
        Collection<? extends IEntityModel> entityModels = ormModel.getEntityModelsInTopoOrder();
        Map<String, ICollectionPersister> persisters = new HashMap<>(entityModels.size());

        for (IEntityModel entityModel : entityModels) {

            for (IEntityRelationModel relation : entityModel.getRelations()) {
                if (!relation.isToManyRelation())
                    continue;

                try {
                    ICollectionPersister persister = newCollectionPersister(relation, ormModel);

                    persisters.put(relation.getCollectionName(), persister);
                } catch (NopException e) {
                    e.addXplStack("createCollectionPersister:" + relation.getCollectionName());
                    throw e;
                }
            }
        }
        return persisters;
    }
}
