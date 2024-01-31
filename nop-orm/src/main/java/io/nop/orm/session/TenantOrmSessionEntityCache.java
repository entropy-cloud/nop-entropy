/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.session;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_MISSING_TENANT_ID;

public class TenantOrmSessionEntityCache implements IOrmSessionEntityCache {
    private final Map<String, OrmSessionEntityCache> caches = new HashMap<>();
    private final OrmSessionEntityCache sharedCache;
    private final IOrmSessionImplementor session;

    public TenantOrmSessionEntityCache(IOrmSessionImplementor session) {
        this.session = session;
        this.sharedCache = new OrmSessionEntityCache(session);
    }

    @Override
    public boolean isStateless() {
        return false;
    }

    @Override
    public boolean contains(IOrmEntity entity) {
        return makeTenantCache(entity).contains(entity);
    }

    private OrmSessionEntityCache makeTenantCache(IOrmEntity entity) {
        String tenantId = makeTenantId(entity);
        return makeTenantCache(entity.orm_entityModel(), tenantId);
    }

    private OrmSessionEntityCache makeTenantCache(IEntityModel entityModel, String tenantId) {
        if (StringHelper.isEmpty(tenantId)) {
            return sharedCache;
        }

        if (entityModel.containsTenantIdInPk())
            return sharedCache;

        OrmSessionEntityCache cache = this.caches.get(tenantId);
        if (cache == null) {
            cache = new OrmSessionEntityCache(this.session);
            this.caches.put(tenantId, cache);
        }
        return cache;
    }

    private String makeTenantId(IOrmEntity entity) {
        IEntityModel entityModel = entity.orm_entityModel();
        if (entityModel.getTenantPropId() <= 0) {
            return null;
        }
        String tenantId = entity.orm_tenantId();
        if (StringHelper.isEmpty(tenantId)) {
            tenantId = ContextProvider.currentTenantId();
            if (StringHelper.isEmpty(tenantId))
                throw new NopException(ERR_ORM_MISSING_TENANT_ID)
                        .param(ARG_ENTITY_NAME, entity.orm_entityName())
                        .param(ARG_ENTITY_ID, entity.get_id())
                        .param(ARG_PROP_NAME, entityModel.getTenantColumn().getName());
            entity.orm_internalSet(entityModel.getTenantPropId(), tenantId);
        }
        return tenantId;
    }

    @Override
    public void remove(IOrmEntity entity) {
        makeTenantCache(entity).remove(entity);
    }

    @Override
    public IOrmEntity add(IOrmEntity entity) {
        return makeTenantCache(entity).add(entity);
    }

    @Override
    public IOrmEntity get(String entityName, Object id) {
        IEntityModel entityModel = session.getEntityModel(entityName);
        if (entityModel.getTenantPropId() <= 0)
            return sharedCache.get(entityName, id);

        if (entityModel.containsTenantIdInPk()) {
            return sharedCache.get(entityName, id);
        }

        String tenantId = ContextProvider.currentTenantId();
        if (StringHelper.isEmpty(tenantId))
            throw new NopException(ERR_ORM_MISSING_TENANT_ID)
                    .param(ARG_ENTITY_NAME, entityName)
                    .param(ARG_ENTITY_ID, id);
        return makeTenantCache(entityModel, tenantId).get(entityName, id);
    }

    @Override
    public void clear() {
        sharedCache.clear();
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.clear();
        }
    }

    @Override
    public void markDirty(String entityName) {
        sharedCache.markDirty(entityName);
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.markDirty(entityName);
        }
    }

    @Override
    public void clearDirty(String entityName) {
        sharedCache.clearDirty(entityName);
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.clearDirty(entityName);
        }
    }

    @Override
    public void clearDirty() {
        sharedCache.clearDirty();
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.clearDirty();
        }
    }

    @Override
    public void removeAll(String entityName) {
        sharedCache.removeAll(entityName);
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.removeAll(entityName);
        }
    }

    @Override
    public void forEachDirty(Consumer<IOrmEntity> processor) {
        sharedCache.forEachDirty(processor);
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.forEachDirty(processor);
        }
    }

    @Override
    public void forEachCurrent(Consumer<IOrmEntity> processor) {
        sharedCache.forEachCurrent(processor);
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.forEachCurrent(processor);
        }
    }

    @Override
    public void forEachCurrent(String entityName, Consumer<IOrmEntity> processor) {
        sharedCache.forEachCurrent(entityName, processor);
        for (OrmSessionEntityCache cache : caches.values()) {
            cache.forEachCurrent(entityName, processor);
        }
    }
}