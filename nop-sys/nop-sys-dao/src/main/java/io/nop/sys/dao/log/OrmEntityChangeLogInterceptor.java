package io.nop.sys.dao.log;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.time.CoreMetrics;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysChangeLog;

public class OrmEntityChangeLogInterceptor implements IOrmInterceptor {

    @Override
    public void postSave(IOrmEntity entity) {
        IEntityModel entityModel = entity.orm_entityModel();
        if (!entityModel.containsTag(OrmModelConstants.TAG_AUDIT_SAVE))
            return;

        if (entityModel.getName().equals(NopSysChangeLog.class.getName()))
            return;

        IEntityDao<NopSysChangeLog> dao = dao();
        NopSysChangeLog changeLog = dao.newEntity();
        initChangeLog(changeLog, NopSysDaoConstants.OPERATION_SAVE, entity);

        entityModel.getColumns().forEach(col -> {
            int propId = col.getPropId();
            if (propId == entityModel.getVersionPropId())
                return;

            if (col.containsTag(OrmModelConstants.TAG_NO_AUDIT))
                return;

            NopSysChangeLog log = changeLog.cloneInstance();
            log.setPropName(col.getName());
            log.setNewValue(ConvertHelper.toString(entity.orm_propValue(propId)));
            dao.saveEntity(log);
        });
    }

    @Override
    public void postUpdate(IOrmEntity entity) {
        IEntityModel entityModel = entity.orm_entityModel();
        if (!entityModel.containsTag(OrmModelConstants.TAG_AUDIT))
            return;

        if (entityModel.getName().equals(NopSysChangeLog.class.getName()))
            return;

        IEntityDao<NopSysChangeLog> dao = dao();
        NopSysChangeLog changeLog = dao.newEntity();
        initChangeLog(changeLog, NopSysDaoConstants.OPERATION_UPDATE, entity);

        entity.orm_forEachDirtyProp((value, propId) -> {
            NopSysChangeLog log = changeLog.cloneInstance();
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            log.setPropName(col.getName());
            log.setOldValue(ConvertHelper.toString(value));
            log.setNewValue(ConvertHelper.toString(entity.orm_propValue(propId)));
            dao.saveEntity(log);
        });
    }

    @Override
    public void postDelete(IOrmEntity entity) {
        IEntityModel entityModel = entity.orm_entityModel();
        if (!entityModel.containsTag(OrmModelConstants.TAG_AUDIT))
            return;

        if (entityModel.getName().equals(NopSysChangeLog.class.getName()))
            return;

        IEntityDao<NopSysChangeLog> dao = dao();
        NopSysChangeLog changeLog = dao.newEntity();
        initChangeLog(changeLog, NopSysDaoConstants.OPERATION_DELETE, entity);
        changeLog.setPropName(NopSysDaoConstants.PROP_DELETED);
        changeLog.setOldValue("0");
        changeLog.setNewValue("1");
        dao.saveEntity(changeLog);
    }

    protected IEntityDao<NopSysChangeLog> dao() {
        return DaoProvider.instance().daoFor(NopSysChangeLog.class);
    }

    protected void initChangeLog(NopSysChangeLog changeLog, String defaultOpName,
                                 IOrmEntity entity) {
        IEntityModel entityModel = entity.orm_entityModel();
        String bizKeyProp = (String) entityModel.prop_get(OrmModelConstants.ORM_BIZ_KEY_PROP);
        String approverIdProp = (String) entityModel.prop_get(OrmModelConstants.ORM_APPROVER_ID_PROP);

        changeLog.setBizObjName(entityModel.getShortName());
        changeLog.setObjId(entity.orm_idString());
        changeLog.setChangeTime(CoreMetrics.currentTimestamp());
        IContext context = ContextProvider.currentContext();
        String appId = context.getDynAppId();
        if (appId == null)
            appId = AppConfig.appName();

        changeLog.setAppId(appId);
        if (bizKeyProp != null) {
            changeLog.setBizKey((String) entity.orm_propValueByName(bizKeyProp));
        }
        if (approverIdProp != null) {
            changeLog.setApproverId((String) entity.orm_propValueByName(approverIdProp));
        }

        changeLog.setOperationName(context.getCallOperationName());
        changeLog.setOperatorId(context.getUserId());

        if (changeLog.getOperationName() == null)
            changeLog.setOperationName(defaultOpName);
    }
}