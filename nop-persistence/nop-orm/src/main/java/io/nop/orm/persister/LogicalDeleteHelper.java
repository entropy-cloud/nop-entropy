/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.dao.DaoConstants;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityModel;

public class LogicalDeleteHelper {
    // public static boolean checkLogicalDelete(IEntityModel entityModel, IOrmEntity entity) {
    // if (!entityModel.isUseLogicalDelete())
    // return false;
    //
    // int propId = entityModel.getDeleteFlagPropId();
    // if (propId <= 0)
    // return false;
    //
    // // 设置deleteFlag=1
    // entity.orm_propValue(propId, 1);
    // return true;
    // }

    public static void onSave(IEntityModel entityModel, IOrmEntity entity) {
        if (!entityModel.isUseLogicalDelete() || entity.orm_disableLogicalDelete()) {
            return;
        }

        int propId = entityModel.getDeleteFlagPropId();
        if (propId <= 0)
            return;

        // 初始化deleteFlag=0
        if (entity.orm_propValue(propId) == null)
            entity.orm_propValue(propId, DaoConstants.NO_VALUE);

        int versionPropId = entityModel.getDeleteVersionPropId();
        if (versionPropId == propId)
            return;

        if (versionPropId > 0 && entity.orm_propValue(versionPropId) == null) {
            entity.orm_propValue(versionPropId, 0);
        }
    }

    public static void onDelete(IEntityModel entityModel, IOrmEntity entity, IPersistEnv env) {
        int deleteFlagPropId = entityModel.getDeleteFlagPropId();
        if (deleteFlagPropId <= 0)
            return;

        int deleteVersionPropId = entityModel.getDeleteVersionPropId();

        if (deleteFlagPropId == deleteVersionPropId) {
            entity.orm_propValue(deleteVersionPropId, env.newDeleteVersion());
        } else {
            entity.orm_propValue(entityModel.getDeleteFlagPropId(), DaoConstants.YES_VALUE);
            if (deleteVersionPropId > 0) {
                entity.orm_propValue(deleteVersionPropId, env.newDeleteVersion());
            }
        }
    }
}
