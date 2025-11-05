/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.api.core.util.Guard;
import io.nop.orm.IOrmEntity;
import io.nop.orm.exceptions.OrmException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.orm.OrmErrors.ERR_ORM_VISIT_LOOP_COUNT_EXCEED_LIMIT;

/**
 * 一级缓存。加入到缓存中时与session绑定，移除时自动与session解除绑定
 */
public class OrmSessionEntityCache implements IOrmSessionEntityCache {

    private static final int MAX_LOOP_COUNT = 100;

    private final IOrmSessionImplementor session;

    private Map<String, EntityCache> entityCaches = new HashMap<>();

    // visiting过程中使用tempEntityCaches和tempRemoves来记录对cache的增删操作
    private Map<String, EntityCache> tempEntityCaches = null;
    private Set<IOrmEntity> tempRemoves = null;

    // 是否正在调用forEach函数遍历对象
    private boolean visiting;

    public OrmSessionEntityCache(IOrmSessionImplementor session) {
        this.session = session;
    }

    static class EntityCache {
        /**
         * 如果所有实体的dirty和extDirty都为false，则这里为false，从而减少遍历只读的数据
         */
        boolean dirty;

        // 尽量保证实体的处理顺序，避免执行过程中的随机性，便于基于录制回放机制实现自动化测试
        final Map<Object /* id */, IOrmEntity> idToEntities = new LinkedHashMap<>();

        // 是否某个实体被修改了
        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean containsKey(Object id) {
            return idToEntities.containsKey(id);
        }

        public Collection<IOrmEntity> entities() {
            return idToEntities.values();
        }

        public void clear() {
            for (IOrmEntity entity : idToEntities.values()) {
                entity.orm_detach();
            }
            this.idToEntities.clear();
        }

        public void putAll(EntityCache map) {
            this.idToEntities.putAll(map.idToEntities);
        }

        public IOrmEntity add(IOrmEntity entity) {
            Guard.notNull(entity.get_id(), "entity._id");
            dirty = true;
            return this.idToEntities.put(entity.get_id(), entity);
        }

        public IOrmEntity get(Object id) {
            return this.idToEntities.get(id);
        }

        public void remove(IOrmEntity entity) {
            this.idToEntities.remove(entity.get_id(), entity);
        }
    }

    @Override
    public boolean isStateless() {
        return false;
    }

    @Override
    public boolean contains(IOrmEntity entity) {
        return this.get(entity.orm_entityName(), entity.get_id()) == entity;
    }

    @Override
    public void remove(IOrmEntity entity) {
        entity.orm_detach();
        if (this.visiting) {
            if (this.tempRemoves == null)
                this.tempRemoves = new HashSet<>();
            tempRemoves.add(entity);
            if (tempEntityCaches != null) {
                _remove(tempEntityCaches, entity);
            }
        } else {
            _remove(entityCaches, entity);
        }
    }

    private void _remove(Map<String, EntityCache> caches, IOrmEntity entity) {
        EntityCache cache = caches.get(entity.orm_entityName());
        if (cache != null) {
            cache.remove(entity);
        }
    }

    @Override
    public IOrmEntity add(IOrmEntity entity) {
        entity.orm_attach(session);
        return makeEntityCache(entity).add(entity);
    }

    @Override
    public IOrmEntity get(String entityName, Object id) {
        if (id == null)
            return null;

        if (visiting && tempEntityCaches != null) {
            IOrmEntity entity = _get(this.tempEntityCaches, entityName, id);
            if (entity != null)
                return entity;
        }

        return _get(this.entityCaches, entityName, id);
    }

    private IOrmEntity _get(Map<String, EntityCache> caches, String entityName, Object id) {
        EntityCache cache = caches.get(entityName);
        if (cache == null)
            return null;
        return cache.get(id);
    }

//    private Map<String, EntityCache> makeCaches(LongHashMap<Map<String, EntityCache>> cacheMap, long hisVersion) {
//        Map<String, EntityCache> caches = cacheMap.get(hisVersion);
//        if (caches == null) {
//            caches = new HashMap<>();
//            cacheMap.put(hisVersion, caches);
//        }
//        return caches;
//    }

    private Map<String, EntityCache> makeCaches(IOrmEntity entity) {

        Map<String, EntityCache> caches;

        if (this.visiting) {
            if (this.tempRemoves != null)
                this.tempRemoves.remove(entity);
            if (this.tempEntityCaches == null)
                this.tempEntityCaches = new HashMap<>();

            caches = tempEntityCaches;
        } else {
            caches = entityCaches;
        }
        return caches;
    }

    private EntityCache makeEntityCache(IOrmEntity entity) {
        Map<String, EntityCache> caches = makeCaches(entity);
        String entityName = entity.orm_entityName();
        EntityCache cache = caches.get(entityName);
        if (cache == null) {
            cache = new EntityCache();
            caches.put(entityName, cache);
        }
        return cache;
    }

    @Override
    public void markDirty(String entityName) {
        EntityCache cache = entityCaches.get(entityName);
        if (cache != null)
            cache.setDirty(true);
    }

    @Override
    public void clearDirty(String entityName) {
        EntityCache cache = entityCaches.get(entityName);
        if (cache != null)
            cache.setDirty(false);
    }

    @Override
    public void clearDirty() {
        for (EntityCache cache : entityCaches.values()) {
            cache.setDirty(false);
        }
    }

    @Override
    public void clear() {
        if (this.visiting) {
            this.tempEntityCaches = null;
            this.tempRemoves = null;
            this.entityCaches = new HashMap<>();
        } else {
            for (EntityCache cache : entityCaches.values()) {
                cache.clear();
            }
            this.entityCaches.clear();
        }
    }

    @Override
    public void removeAll(String entityName) {
        if (this.visiting) {
            if (this.tempEntityCaches != null) {
                tempEntityCaches.remove(entityName);
            }
        }

        EntityCache cache = this.entityCaches.get(entityName);
        if (cache != null)
            cache.clear();
    }

    @Override
    public void forEachCurrent(String entityName, Consumer<IOrmEntity> processor) {
        Map<String, EntityCache> caches = this.entityCaches;
        this.visiting = true;
        try {
            do {

                EntityCache cache = caches.get(entityName);
                if (cache != null) {
                    for (IOrmEntity entity : cache.entities()) {
                        processor.accept(entity);
                    }
                }

                if (this.tempRemoves != null) {
                    for (IOrmEntity entity : tempRemoves) {
                        _remove(entityCaches, entity);
                    }
                    this.tempRemoves = null;
                }

                caches = this.tempEntityCaches;
                if (caches != null) {
                    this.tempEntityCaches = null;
                    this.mergeTempCaches(caches);
                }
            } while (caches != null);
        } finally {
            visiting = false;
        }
    }

    @Override
    public void forEachCurrent(Consumer<IOrmEntity> processor) {
        Map<String, EntityCache> caches = this.entityCaches;
        this.visiting = true;
        try {
            do {
                for (EntityCache cache : caches.values()) {
                    for (IOrmEntity entity : cache.entities()) {
                        processor.accept(entity);
                    }
                }

                if (this.tempRemoves != null) {
                    for (IOrmEntity entity : tempRemoves) {
                        _remove(entityCaches, entity);
                    }
                    this.tempRemoves = null;
                }

                caches = this.tempEntityCaches;
                if (caches != null) {
                    this.tempEntityCaches = null;
                    this.mergeTempCaches(caches);
                }
            } while (caches != null);
        } finally {
            visiting = false;
        }
    }

    @Override
    public void forEachDirty(Consumer<IOrmEntity> processor) {
        Map<String, EntityCache> caches = this.entityCaches;
        this.visiting = true;
        int count = 0;
        try {
            do {
                if (count > MAX_LOOP_COUNT)
                    throw new OrmException(ERR_ORM_VISIT_LOOP_COUNT_EXCEED_LIMIT);

                for (EntityCache cache : caches.values()) {
                    if (!cache.isDirty())
                        continue;

                    for (IOrmEntity entity : cache.entities()) {
                        processor.accept(entity);
                    }
                }

                if (this.tempRemoves != null) {
                    for (IOrmEntity entity : tempRemoves) {
                        _remove(entityCaches, entity);
                    }
                    this.tempRemoves = null;
                }

                caches = this.tempEntityCaches;
                if (caches != null) {
                    this.tempEntityCaches = null;
                    this.mergeTempCaches(caches);
                }

            } while (caches != null);
        } finally {
            visiting = false;
        }
    }

    void mergeTempCaches(Map<String, EntityCache> caches) {
        for (Map.Entry<String, EntityCache> entry : caches.entrySet()) {
            EntityCache cache = entityCaches.get(entry.getKey());
            if (cache == null) {
                entry.getValue().setDirty(true);
                entityCaches.put(entry.getKey(), entry.getValue());
            } else {
                cache.setDirty(true);
                cache.putAll(entry.getValue());
            }
        }
    }
}