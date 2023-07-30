package io.nop.orm.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.core.resource.impl.AbstractResource;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityModel;

import java.sql.Timestamp;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NO_UPDATE_TIME_COL;

public class DaoEntityResource extends AbstractResource {
    private final String entityName;
    private final String entityId;

    private long lastCheckTime = -1;

    private long checkInterval;

    private long lastModified;

    private IDaoProvider daoProvider;

    public DaoEntityResource(String entityName, String entityId, long checkInterval,
                             IDaoProvider daoProvider) {
        super(makeDaoResourcePath(entityName, entityId));
        this.entityName = Guard.notEmpty(entityName, "entityName");
        this.entityId = Guard.notEmpty(entityId, "entityId");
        this.checkInterval = checkInterval;
        this.daoProvider = daoProvider;
    }

    public static String makeDaoResourcePath(String entityName, String entityId) {
        return "dao:" + entityName + "/" + entityId;
    }

    public static String makeDaoResource(IOrmEntity entity) {
        return makeDaoResourcePath(entity.orm_entityName(), entity.orm_idString());
    }

    protected IEntityDao<?> dao() {
        if (daoProvider == null)
            return DaoProvider.instance().dao(entityName);
        return daoProvider.dao(entityName);
    }

    public String getEntityName() {
        return entityName;
    }

    public String getEntityId() {
        return entityId;
    }

    @Override
    public boolean exists() {
        return lastModified() > 0;
    }

    @Override
    protected Object internalObj() {
        return getPath();
    }

    @Override
    public long lastModified() {
        checkLoad();
        return lastModified;
    }

    private void checkLoad() {
        long now = CoreMetrics.currentTimeMillis();
        if (lastCheckTime > 0 && now - lastCheckTime < checkInterval) {
            return;
        }

        lastCheckTime = now;

        IEntityDao<?> dao = dao();
        IOrmEntity entity = (IOrmEntity) dao.getEntityById(entityId);
        if (entity == null) {
            lastModified = -1;
        } else {
            IEntityModel entityModel = entity.orm_entityModel();
            if (entityModel.getUpdaterPropId() < 0)
                throw new NopException(ERR_ORM_ENTITY_NO_UPDATE_TIME_COL).param(ARG_ENTITY_NAME, entityName);

            Timestamp time = ConvertHelper.toTimestamp(entity.orm_propValue(entityModel.getUpdateTimePropId()));
            if (time != null) {
                lastModified = time.getTime();
            } else {
                lastModified = now;
            }
        }
    }
}
