/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.time.CoreMetrics;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IEntityModel;

import java.sql.Timestamp;

import static io.nop.orm.OrmConfigs.CFG_ORM_SYS_USER_NAME;

public class OrmTimestampHelper {
    public static void onCreate(IEntityModel entityModel, IOrmEntity entity) {
        if (entity.orm_disableAutoStamp())
            return;

        if (entityModel.getCreaterPropId() > 0 || entityModel.getUpdaterPropId() > 0) {
            String user = getCurrentUser();
            if (user != null) {
                if (entityModel.getCreaterPropId() > 0)
                    entity.orm_propValue(entityModel.getCreaterPropId(), user);
                if (entityModel.getUpdaterPropId() > 0)
                    entity.orm_propValue(entityModel.getUpdaterPropId(), user);
            } else {
                if (entityModel.getCreaterPropId() > 0 && entity.orm_propValue(entityModel.getCreaterPropId()) == null)
                    entity.orm_propValue(entityModel.getCreaterPropId(), CFG_ORM_SYS_USER_NAME.get());
                if (entityModel.getUpdaterPropId() > 0 && entity.orm_propValue(entityModel.getUpdaterPropId()) == null)
                    entity.orm_propValue(entityModel.getUpdaterPropId(), CFG_ORM_SYS_USER_NAME.get());
            }
        }

        if (entityModel.getUpdateTimePropId() > 0 || entityModel.getCreateTimePropId() > 0) {
            Timestamp current = new Timestamp(CoreMetrics.currentTimeMillis());
            if (entityModel.getCreateTimePropId() > 0)
                entity.orm_propValue(entityModel.getCreateTimePropId(), current);

            if (entityModel.getUpdateTimePropId() > 0) {
                entity.orm_propValue(entityModel.getUpdateTimePropId(), current);
            }
        }
    }

    public static void onUpdate(IEntityModel entityModel, IOrmEntity entity) {
        if (entity.orm_disableAutoStamp())
            return;

        Timestamp current = new Timestamp(CoreMetrics.currentTimeMillis());

        if (entityModel.getCreaterPropId() > 0) {
            if (entity.orm_propValue(entityModel.getCreaterPropId()) == null) {
                String user = getCurrentUser();
                if (user != null) {
                    user = CFG_ORM_SYS_USER_NAME.get();
                }
                entity.orm_propValue(entityModel.getCreaterPropId(), user);
            }
        }

        if (entityModel.getCreaterPropId() > 0) {
            if (entity.orm_propValue(entityModel.getCreateTimePropId()) == null)
                entity.orm_propValue(entityModel.getCreateTimePropId(), current);
        }

        if (entityModel.getUpdaterPropId() > 0) {
            String user = getCurrentUser();
            if (user != null) {
                entity.orm_propValue(entityModel.getUpdaterPropId(), user);
            } else {
                entity.orm_propValue(entityModel.getUpdaterPropId(), CFG_ORM_SYS_USER_NAME.get());
            }
        }

        if (entityModel.getUpdateTimePropId() > 0) {
            entity.orm_propValue(entityModel.getUpdateTimePropId(), current);
        }
    }

    private static String getCurrentUser() {
        return ContextProvider.currentUserRefNo();
    }
}