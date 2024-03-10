/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.Objects;

import static io.nop.orm.OrmErrors.*;

public class OrmRevisionHelper {
    public static void onRevSave(IEntityModel entityModel, IOrmEntity entity, EntityPersisterImpl persister,
                                 IOrmSessionImplementor session) {
        IOrmEntity oldEntity = persister.findLatest(entity, session);
        if (oldEntity != null && Objects.equals(OrmConstants.REV_TYPE_DELETE,
                oldEntity.orm_propValue(entityModel.getNopRevTypePropId())))
            throw newError(entityModel, ERR_ORM_ENTITY_ALREADY_EXISTS, entity);

        entity.orm_internalSet(entityModel.getNopRevTypePropId(), OrmConstants.REV_TYPE_SAVE);

        long ver = session.getSessionRevVersion();

        int beginVerPropId = entityModel.getNopRevBeginVerPropId();
        if (beginVerPropId > 0) {
            if (oldEntity != null) {
                long beginVer = (Long) oldEntity.orm_propValue(beginVerPropId);
                if (beginVer <= ver)
                    throw newError(entityModel, ERR_ORM_ENTITY_REV_VER_IS_LESS_THAN_HIS_VER, oldEntity).param(ARG_REV_VER,
                            ver);
            }
            entity.orm_internalSet(beginVerPropId, ver);
        }

        int endVerPropId = entityModel.getNopRevEndVarPropId();
        if (endVerPropId > 0) {
            entity.orm_internalSet(endVerPropId, OrmConstants.NOP_VER_MAX_VALUE);
            if (oldEntity != null)
                oldEntity.orm_propValue(endVerPropId, ver);
        }

        if (oldEntity != null)
            persister.queueUpdate(oldEntity, session);

        persister.queueSave(entity, session);
    }

    public static void onRevUpdate(IEntityModel entityModel, IOrmEntity entity, EntityPersisterImpl persister,
                                   IOrmSessionImplementor session) {
        checkCurrentRev(entityModel, entity);
        IOrmEntity revEntity = newRevEntity(OrmConstants.REV_TYPE_UPDATE, entityModel, entity, session);
        persister.queueUpdate(entity, session);
        persister.queueSave(revEntity, session);
    }

    public static void onRevDelete(IEntityModel entityModel, IOrmEntity entity, EntityPersisterImpl persister,
                                   IOrmSessionImplementor session) {
        checkCurrentRev(entityModel, entity);
        IOrmEntity revEntity = newRevEntity(OrmConstants.REV_TYPE_DELETE, entityModel, entity, session);
        persister.queueUpdate(entity, session);
        persister.queueSave(revEntity, session);
    }

    static void checkCurrentRev(IEntityModel entityModel, IOrmEntity entity) {
        if (!isCurrentRev(entityModel, entity))
            throw newError(entityModel, ERR_ORM_ENTITY_NOT_CURRENT_REVISION, entity).param(ARG_REV_END_VER,
                    entity.orm_propValue(entityModel.getNopRevEndVarPropId()));
    }

    static NopException newError(IEntityModel entityModel, ErrorCode errorCode, IOrmEntity entity) {
        Object beginVer = -1L;
        if (entityModel.getNopRevBeginVerPropId() > 0) {
            beginVer = entity.orm_propValue(entityModel.getNopRevBeginVerPropId());
        }
        return OrmException.newError(ERR_ORM_ENTITY_NOT_CURRENT_REVISION, entity).param(ARG_REV_BEGIN_VER, beginVer);
    }

    public static IOrmEntity newRevEntity(byte revType, IEntityModel entityModel, IOrmEntity entity,
                                          IOrmSessionImplementor session) {
        IOrmEntity revEntity = entity.cloneInstance();

        revEntity.orm_internalSet(entityModel.getNopRevTypePropId(), revType);

        long ver = session.getSessionRevVersion();

        int beginVerPropId = entityModel.getNopRevBeginVerPropId();
        if (beginVerPropId > 0) {
            long beginVer = (Long) entity.orm_propValue(beginVerPropId);
            if (beginVer <= ver)
                throw newError(entityModel, ERR_ORM_ENTITY_REV_VER_IS_LESS_THAN_HIS_VER, entity).param(ARG_REV_VER,
                        ver);
            revEntity.orm_internalSet(beginVerPropId, ver);
        }

        int extChangePropId = entityModel.getNopRevExtChangePropId();
        if (extChangePropId > 0) {
            if (entity.orm_propDirty(entityModel.getNopRevExtChangePropId())) {
                Object value = entity.orm_propValue(entityModel.getNopRevExtChangePropId());
                revEntity.orm_propValue(extChangePropId, value);
            } else {
                revEntity.orm_propValue(extChangePropId, 0);
            }
        }

        // 仅修改前一条记录的revEndVer字段，其他字段都恢复原值。
        entity.orm_clearDirty();
        int endVerPropId = entityModel.getNopRevEndVarPropId();
        if (endVerPropId > 0) {
            entity.orm_internalSet(endVerPropId, ver);
            revEntity.orm_internalSet(endVerPropId, OrmConstants.NOP_VER_MAX_VALUE);
        }

        return revEntity;
    }

    /**
     * 检查实体是否是最新的版本。最新版本的revEndVer为Long.MAX_VALUE
     */
    public static boolean isCurrentRev(IEntityModel entityModel, IOrmEntity entity) {
        int endVerPropId = entityModel.getNopRevEndVarPropId();
        if (endVerPropId > 0) {
            Object value = entity.orm_propValue(endVerPropId);
            return Objects.equals(Long.MAX_VALUE, value);
        } else {
            return true;
        }
    }
}
