/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.id;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmErrors;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.utils.OrmModelHelper;
import io.nop.orm.support.OrmEntityHelper;

import java.util.List;

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
            int propId = col.getPropId();
            if (propId == entityModel.getTenantPropId()) {
                initTenantId(col, entity);
            } else if (col.containsTag(OrmConstants.TAG_SEQ_DEFAULT)) {
                genSeq(entity, col, true);
            } else if (col.containsTag(OrmConstants.TAG_SEQ)) {
                genSeq(entity, col, false);
            } else {
                Object value = entity.orm_propValue(col.getPropId());
                if (value == null) {
                    if (propId == entityModel.getDeleteVersionPropId()) {
                        value = 0;
                        entity.orm_propValue(propId, value);
                    } else if (col.getPropId() == entityModel.getDeleteFlagPropId()) {
                        value = DaoConstants.NO_VALUE;
                        entity.orm_propValue(propId, value);
                    } else {
                        value = forceGenRef(entity, col);
                    }
                }
                if (value == null)
                    throw new OrmException(ERR_ORM_ENTITY_ID_NOT_SET).param(ARG_ENTITY_NAME, entityModel.getName())
                            .param(ARG_PROP_NAME, col.getName());
            }
        }
    }

    Object forceGenRef(IOrmEntity entity, IColumnModel col) {
        List<IEntityRelationModel> refs = col.getColumnRefs();
        if (refs == null || refs.isEmpty())
            return null;
        for (IEntityRelationModel ref : refs) {
            if (ref.isToOneRelation() && !ref.isReverseDepends()) {
                IOrmEntity refEntity = entity.orm_refEntity(ref.getName());
                if (refEntity != null) {
                    entity.orm_enhancer().initEntityId(refEntity);
                    return entity.orm_propValue(col.getPropId());
                }
            }
        }
        return null;
    }

    void genSeq(IOrmEntity entity, IColumnModel col, boolean useDefault) {
        Object value = OrmEntityHelper.getPropValue(col, entity);
        if (value == null) {
            String key = OrmModelHelper.buildEntityPropKey(col);
            if (col.getStdDataType().isNumericType()) {
                value = sequenceGenerator.generateLong(key, useDefault);
                // 如果主键是Integer类型，这里生成long再转换为integer就可能出现负数
                value = MathHelper.abs(col.getStdDataType().convert(value));
            } else {
                value = sequenceGenerator.generateString(key, useDefault);
            }
            entity.orm_propValue(col.getColumnPropId(), value);
        }
    }

    void initTenantId(IColumnModel col, IOrmEntity entity) {
        String current = ContextProvider.currentTenantId();
        if (current == null)
            throw new OrmException(OrmErrors.ERR_ORM_MISSING_TENANT_ID).param(ARG_ENTITY_NAME, entity.orm_entityName())
                    .param(ARG_ENTITY_ID, entity.orm_id());

        String tenantId = (String) OrmEntityHelper.getPropValue(col, entity);
        if (StringHelper.isEmpty(tenantId)) {
            OrmEntityHelper.setPropValue(col, entity, tenantId);
        } else {
            if (!current.equals(tenantId))
                throw new OrmException(OrmErrors.ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT)
                        .param(ARG_ENTITY_NAME, entity.orm_entityName()).param(ARG_ENTITY_ID, entity.orm_id())
                        .param(ARG_TENANT_ID, tenantId);
        }
    }
}
