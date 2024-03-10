/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.impl.AbstractResource;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Timestamp;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NO_CONTENT_PROP;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NO_UPDATE_TIME_COL;

public class DaoEntityResource extends AbstractResource {
    private final String entityName;
    private final String entityId;

    private long lastCheckTime = -1;

    private final long checkInterval;

    private long lastModified;

    private final IDaoProvider daoProvider;

    public DaoEntityResource(String entityName, String entityId, long checkInterval,
                             IDaoProvider daoProvider) {
        super(makeDaoResourcePath(entityName, entityId));
        this.entityName = Guard.notEmpty(entityName, "entityName");
        this.entityId = Guard.notEmpty(entityId, "entityId");
        this.checkInterval = checkInterval;
        this.daoProvider = daoProvider;
    }

    public static String makeDaoResourcePath(String entityName, Object entityId) {
        return "dao:" + entityName + "/" + entityId;
    }

    public static String makeDaoResourcePath(IOrmEntity entity) {
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
        if (checkInterval > 0 && lastCheckTime > 0 && now - lastCheckTime < checkInterval) {
            return;
        }

        loadEntity();
    }

    protected IOrmEntity loadEntity() {
        long now = CoreMetrics.currentTimeMillis();
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
        return entity;
    }

    private IOrmEntity requireEntity() {
        IOrmEntity entity = loadEntity();
        if (entity == null)
            throw new UnknownEntityException(entityName, entityId);

        return entity;
    }

    private Object requireContent() {
        IOrmEntity entity = requireEntity();
        IEntityModel entityModel = entity.orm_entityModel();
        IEntityPropModel propModel = entityModel.getPropByTag(OrmConstants.TAG_CONTENT);
        if (propModel == null)
            throw new NopException(ERR_ORM_ENTITY_NO_CONTENT_PROP)
                    .param(ARG_ENTITY_NAME, entityName);

        return entity.orm_propValueByName(propModel.getName());
    }

    @Override
    public Reader getReader(String encoding) {
        Object value = requireContent();
        if (value == null)
            return new StringReader("");
        if (value instanceof ByteString)
            return new StringReader(((ByteString) value).utf8());

        if (value instanceof byte[]) {
            return new StringReader(new String((byte[]) value, StringHelper.CHARSET_UTF8));
        }
        return new StringReader(value.toString());
    }

    @Override
    public String readText() {
        Object value = requireContent();
        if (value == null)
            return "";
        if (value instanceof ByteString)
            return ((ByteString) value).utf8();
        if (value instanceof byte[])
            return new String((byte[]) value, StringHelper.CHARSET_UTF8);
        return value.toString();
    }

    @Override
    public InputStream getInputStream() {
        Object value = requireContent();
        if (value == null)
            return new ByteArrayInputStream(new byte[0]);

        if (value instanceof ByteString)
            return new ByteArrayInputStream(((ByteString) value).toByteArray());

        if (value instanceof byte[])
            return new ByteArrayInputStream((byte[]) value);

        byte[] bytes = value.toString().getBytes(StringHelper.CHARSET_UTF8);
        return new ByteArrayInputStream(bytes);
    }
}
