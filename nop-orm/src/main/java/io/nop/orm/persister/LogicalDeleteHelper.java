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
    }
}
