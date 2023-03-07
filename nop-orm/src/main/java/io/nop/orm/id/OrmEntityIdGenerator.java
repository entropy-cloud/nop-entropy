/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.id;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.util.StringHelper;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmErrors;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_TENANT_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_ID_NOT_SET;

public class OrmEntityIdGenerator implements IEntityIdGenerator {
    private final IEntityModel entityModel;
    private final ISequenceGenerator sequenceGenerator;

    public OrmEntityIdGenerator(IEntityModel entityModel, ISequenceGenerator sequenceGenerator) {
        this.entityModel = entityModel;
        this.sequenceGenerator = sequenceGenerator;
    }

    @Override
    public void generateId(IOrmEntity entity) {
        for (IColumnModel col : entityModel.getPkColumns()) {
            if (col.getPropId() == entityModel.getTenantPropId()) {
                initTenantId(col, entity);
            } else if (col.containsTag(OrmConstants.TAG_SEQ)) {
                Object value = col.getPropValue(entity);
                if (value == null) {
                    if (col.getStdDataType().isNumericType()) {
                        value = sequenceGenerator.generateLong(entityModel.getName(), true);
                    } else {
                        value = sequenceGenerator.generateString(entityModel.getName(), true);
                    }
                    col.setPropValue(entity, value);
                }
            } else {
                Object value = col.getPropValue(entity);
                if (value == null)
                    throw new OrmException(ERR_ORM_ENTITY_ID_NOT_SET).param(ARG_ENTITY_NAME, entityModel.getName())
                            .param(ARG_PROP_NAME, col.getName());
            }
        }
    }

    void initTenantId(IColumnModel col, IOrmEntity entity) {
        String current = ContextProvider.currentTenantId();
        if (current == null)
            throw new OrmException(OrmErrors.ERR_ORM_MISSING_TENANT_ID).param(ARG_ENTITY_NAME, entity.orm_entityName())
                    .param(ARG_ENTITY_ID, entity.orm_id());

        String tenantId = (String) col.getPropValue(entity);
        if (StringHelper.isEmpty(tenantId)) {
            col.setPropValue(entity, tenantId);
        } else {
            if (!current.equals(tenantId))
                throw new OrmException(OrmErrors.ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT)
                        .param(ARG_ENTITY_NAME, entity.orm_entityName()).param(ARG_ENTITY_ID, entity.orm_id())
                        .param(ARG_TENANT_ID, tenantId);
        }
    }
}
