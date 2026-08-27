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
import io.nop.orm.dao.IOrmEntityDao;
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
            saveDirectly(dao, log);
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
            // 乐观锁更新version必为脏属性，与postSave一致跳过，避免每次更新多写一条
            // propName=version的噪声记录
            if (propId == entityModel.getVersionPropId())
                return;

            NopSysChangeLog log = changeLog.cloneInstance();
            IColumnModel col = entityModel.getColumnByPropId(propId, false);
            log.setPropName(col.getName());
            log.setOldValue(ConvertHelper.toString(value));
            log.setNewValue(ConvertHelper.toString(entity.orm_propValue(propId)));
            saveDirectly(dao, log);
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
        saveDirectly(dao, changeLog);
    }

    protected IEntityDao<NopSysChangeLog> dao() {
        return DaoProvider.instance().daoFor(NopSysChangeLog.class);
    }

    /**
     * 拦截器在外层会话的flush回调内触发：入队式saveEntity会随会话关闭被丢弃（internalSave
     * 已入队但INSERT从未执行）；saveEntityDirectly复用环境会话又与进行中的flush相互重入
     * （同一动作重复入队，duplicate-key）。必须在独立新会话中直接落库。
     */
    @SuppressWarnings("unchecked")
    private void saveDirectly(IEntityDao<NopSysChangeLog> dao, NopSysChangeLog log) {
        IOrmEntityDao<NopSysChangeLog> ormDao = (IOrmEntityDao<NopSysChangeLog>) dao;
        ormDao.getOrmTemplate().runInNewSession(session -> session.saveDirectly(log));
    }

    protected void initChangeLog(NopSysChangeLog changeLog, String defaultOpName,
                                 IOrmEntity entity) {
        IEntityModel entityModel = entity.orm_entityModel();
        String bizKeyProp = (String) entityModel.prop_get(OrmModelConstants.ORM_BIZ_KEY_PROP);
        String approverIdProp = (String) entityModel.prop_get(OrmModelConstants.ORM_APPROVER_ID_PROP);

        changeLog.setBizObjName(entityModel.getShortName());
        changeLog.setObjId(entity.orm_idString());
        changeLog.setChangeTime(CoreMetrics.currentTimestamp());

        // 无上下文线程（消息消费分发/后台定时任务的flush阶段）currentContext()返回null，
        // 直接取值会NPE：appId回退应用名，operationName回退默认操作名，operatorId置空
        IContext context = ContextProvider.currentContext();
        String appId = context == null ? null : context.getDynAppId();
        if (appId == null)
            appId = AppConfig.appName();

        changeLog.setAppId(appId);
        if (bizKeyProp != null) {
            changeLog.setBizKey((String) entity.orm_propValueByName(bizKeyProp));
        }
        if (approverIdProp != null) {
            changeLog.setApproverId((String) entity.orm_propValueByName(approverIdProp));
        }

        changeLog.setOperationName(context == null ? null : context.getCallOperationName());
        // OPERATOR_ID列mandatory：无上下文/无登录用户时回退"system"，避免审计写入因非空约束失败
        String operatorId = context == null ? null : context.getUserId();
        changeLog.setOperatorId(operatorId == null ? "system" : operatorId);

        if (changeLog.getOperationName() == null)
            changeLog.setOperationName(defaultOpName);
    }
}
