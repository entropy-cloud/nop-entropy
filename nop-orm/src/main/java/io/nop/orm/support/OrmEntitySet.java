/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.support;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntityEnhancer;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmKeyValueTable;
import io.nop.orm.OrmEntityState;
import io.nop.orm.exceptions.OrmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY;
import static io.nop.orm.OrmErrors.ARG_OWNER;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ADD_NULL_ELEMENT_TO_COLLECTION;
import static io.nop.orm.OrmErrors.ERR_ORM_COLLECTION_ELEMENT_NOT_ALLOW_MULTIPLE_OWNER;
import static io.nop.orm.OrmErrors.ERR_ORM_COLLECTION_IS_READONLY;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NOT_ATTACHED;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_SET_ELEMENT_NOT_KV_TABLE;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_SET_NO_KEY_PROP;
import static io.nop.orm.OrmErrors.ERR_ORM_SESSION_CLOSED;

/**
 * @author canonical_entropy@163.com
 */
public class OrmEntitySet<T extends IOrmEntity> implements IOrmEntitySet<T> {
    private final IOrmEntity owner;
    private final String propName;
    /**
     * ownerPropName是集合的成员对象上反向引用owner的属性名称。例如 A --> Set<B>, 类型B可以具有属性a，它指向类型为A的owner。 一般情况下ownerPropName对应于外键字段
     */
    private final String refPropName;
    private final String collectionName;

    /**
     * 如果keyProp不为空，则集合中的元素具有唯一属性keyProp
     */
    private final String keyProp;

    /**
     * 通过LinkedHashSet来保持集合元素的顺序与加入顺序一致
     */
    private final Set<T> entities = new LinkedHashSet<>();

    private final Class<? extends IOrmEntity> refEntityClass;

    private Map<String, T> keyToEntityMap;

    /**
     * 从数据库中加载的原始记录。orm_reset将恢复entities集合为initialEntities。
     */
    private Set<T> initialEntities;

    private Set<T> removedEntities;

    private boolean proxy;
    private boolean dirty;
    private boolean readonly;

    private boolean kvTable;

    public OrmEntitySet(IOrmEntity owner, String propName, String refPropName, String keyProp,
                        Class<? extends IOrmEntity> refEntityClass) {
        this.owner = owner;
        this.collectionName = OrmEntityHelper.buildCollectionName(owner.orm_entityName(), propName);
        this.propName = propName;
        this.refPropName = refPropName;
        this.keyProp = keyProp;
        this.refEntityClass = refEntityClass;
        this.kvTable = refEntityClass != null && IOrmKeyValueTable.class.isAssignableFrom(refEntityClass);
    }

    public OrmEntitySet(IOrmEntity owner, String propName, String refPropName, String keyProp) {
        this(owner, propName, refPropName, keyProp, null);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OrmEntitySet[collectionName=").append(orm_collectionName()).append(",owner=").append(orm_owner());
        if (orm_proxy()) {
            sb.append(",proxy");
        }
        if (orm_dirty()) {
            sb.append(",dirty");
        }
        if (orm_readonly()) {
            sb.append(",readonly");
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public Object prop_get(String propName) {
        if (keyProp == null)
            throw newError(ERR_ORM_ENTITY_SET_NO_KEY_PROP).param(ARG_PROP_NAME, propName);
        checkLoaded();
        IOrmEntity entity = makeKeyToEntityMap().get(propName);
        return entity;
    }

    @Override
    public Object prop_make(String propName) {
        Object value = prop_get(propName);
        if (value == null) {
            IOrmEntity entity = newElement();
            entity.orm_propValueByName(keyProp, propName);
            add((T) entity);
            value = entity;
        }
        return value;
    }

    protected Map<String, T> makeKeyToEntityMap() {
        if (keyToEntityMap == null) {
            keyToEntityMap = new HashMap<>();
            for (T entity : entities) {
                String key = getKey(entity);
                keyToEntityMap.put(key, entity);
            }
        }
        return keyToEntityMap;
    }

    private String getKey(IOrmEntity entity) {
        Object value = entity.orm_propValueByName(keyProp);
        return ConvertHelper.toString(value, "");
    }

    @Override
    public boolean prop_has(String propName) {
        return makeKeyToEntityMap().containsKey(propName);
    }

    @Override
    public void prop_set(String propName, Object value) {
        checkReadonly();
        checkLoaded();

        if (value == null) {
            if (keyToEntityMap == null)
                return;
            T entity = keyToEntityMap.remove(propName);
            if (entity != null)
                remove(entity);
            return;
        }

        if (keyProp == null)
            throw newError(ERR_ORM_ENTITY_SET_NO_KEY_PROP);

        if (value instanceof IOrmEntity) {
            T entity = (T) value;
            entity.orm_propValueByName(keyProp, propName);
            add(entity);
        } else {
            if (!kvTable)
                throw newError(ERR_ORM_ENTITY_SET_ELEMENT_NOT_KV_TABLE);

            IOrmKeyValueTable entity = (IOrmKeyValueTable) makeKeyToEntityMap().get(propName);
            if (entity != null) {
                entity.setValue(value);
            } else {
                entity = (IOrmKeyValueTable) newElement();
                entity.setFieldName(propName);
                entity.setValue(value);
                add((T) entity);
            }
        }
    }

    private IOrmEntity newElement() {
        return (IOrmEntity) ClassHelper.newInstance(refEntityClass);
    }

    @Override
    public boolean orm_readonly() {
        return readonly;
    }

    @Override
    public void orm_readonly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public IOrmEntity orm_owner() {
        return owner;
    }

    @Override
    public String orm_refPropName() {
        return refPropName;
    }

    @Override
    public void orm_sort(Comparator<? super T> comparator) {
        checkLoaded();
        checkReadonly();

        if (isEmpty())
            return;

        beginModify();

        List<T> list = new ArrayList<>(entities);
        Collections.sort(list, comparator);
        entities.clear();
        entities.addAll(list);
    }

    void beginModify() {
        if (entities == initialEntities) {
            initialEntities = new LinkedHashSet<>(entities);
        }
    }

    @Override
    public String orm_propName() {
        return propName;
    }

    @Override
    public boolean orm_proxy() {
        // initialEntities如果不为null，则表示已经从数据库中加载数据，当前对象已经不再是proxy对象
        // 但是为了支持orm_unload操作，仍然保留proxy标记。
        return proxy && initialEntities == null;
    }

    public void orm_proxy(boolean proxy) {
        this.proxy = proxy;
        if (proxy)
            this.initialEntities = null;
    }

    @Override
    public void orm_reset() {
        this.entities.clear();
        if (this.initialEntities != null) {
            this.entities.addAll(initialEntities);
        }
        this.orm_clearDirty();
    }

    @Override
    public boolean orm_attached() {
        return owner.orm_attached();
    }

    @Override
    public IOrmEntityEnhancer orm_enhancer() {
        return owner.orm_enhancer();
    }

    private void checkLoaded() {
        if (orm_proxy()) {
            requireEnhancer().internalLoadCollection(this);
        }
    }

    private IOrmEntityEnhancer requireEnhancer() {
        IOrmEntityEnhancer enhancer = owner.orm_enhancer();
        if (enhancer == null) {
            if (owner.orm_state() != OrmEntityState.TRANSIENT)
                throw newError(ERR_ORM_SESSION_CLOSED);
            throw newError(ERR_ORM_ENTITY_NOT_ATTACHED);
        }
        return enhancer;
    }

    protected NopException newError(ErrorCode errorCode) {
        return new OrmException(errorCode).param(ARG_COLLECTION_NAME, collectionName).param(ARG_OWNER, owner);
    }

    public void orm_markDirty() {
        checkReadonly();

        this.dirty = true;
        owner.orm_extDirty(true);
    }

    private void checkReadonly() {
        if (readonly)
            throw newError(ERR_ORM_COLLECTION_IS_READONLY);
    }

    @Override
    public int size() {
        checkLoaded();
        return this.entities.size();
    }

    @Override
    public boolean isEmpty() {
        checkLoaded();
        return this.entities.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        checkLoaded();
        return this.entities.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        checkLoaded();
        return new IteratorView(this.entities.iterator());
    }

    @Override
    public Object[] toArray() {
        checkLoaded();
        return entities.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        checkLoaded();
        return entities.toArray(a);
    }

    @Override
    public boolean add(T e) {
        if (e == null)
            throw newError(ERR_ORM_ADD_NULL_ELEMENT_TO_COLLECTION);

        checkLoaded();
        checkReadonly();
        beginModify();

        // 绑定owner属性
        if (refPropName != null) {
            IOrmEntity elmOwner = e.orm_refEntity(refPropName);
            if (elmOwner == null) {
                e.orm_propValueByName(refPropName, owner);
            } else if (elmOwner != owner) {
                throw newError(ERR_ORM_COLLECTION_ELEMENT_NOT_ALLOW_MULTIPLE_OWNER).param(ARG_ENTITY, e);
            }
        }

        if (this.removedEntities != null) {
            this.removedEntities.remove(e);
        }

        boolean b = entities.add(e);
        if (b) {
            if (keyProp != null) {
                String key = getKey(e);
                makeKeyToEntityMap().put(key, e);
            }
            this.orm_markDirty();
        }
        return b;
    }

    @Override
    public boolean remove(Object o) {
        checkLoaded();
        checkReadonly();
        beginModify();

        boolean b = entities.remove(o);
        if (b) {
            T entity = (T) o;
            doRemove(entity);
        }
        return b;
    }

    private void doRemove(T entity) {
        this.makeRemovedEntities().add(entity);
        if (keyProp != null && keyToEntityMap != null) {
            String key = getKey(entity);
            keyToEntityMap.remove(key);
        }
        orm_markDirty();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        checkLoaded();
        return entities.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        checkLoaded();

        boolean ret = false;
        for (T entity : c) {
            boolean b = add(entity);
            if (b)
                ret = true;
        }
        return ret;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        checkLoaded();

        boolean ret = false;
        for (Object entity : c) {
            boolean b = remove(entity);
            if (b)
                ret = true;
        }
        return ret;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        checkLoaded();
        checkReadonly();

        Set<IOrmEntity> toRemoveElms = new LinkedHashSet<>();
        for (IOrmEntity entity : entities) {
            if (!c.contains(entity))
                toRemoveElms.add(entity);
        }
        if (toRemoveElms.isEmpty())
            return false;
        return removeAll(toRemoveElms);
    }

    @Override
    public void clear() {
        checkLoaded();
        checkReadonly();
        beginModify();

        if (!isEmpty()) {
            // 这里考虑了先删除几个元素，然后再清空的情况
            this.makeRemovedEntities().addAll(this.entities);
            this.entities.clear();
            this.keyToEntityMap = null;
            this.orm_markDirty();
        }
    }

    @Override
    public String orm_collectionName() {
        return collectionName;
    }

    @Override
    public boolean orm_dirty() {
        return dirty;
    }

    @Override
    public void orm_clearDirty() {
        this.dirty = false;
        // 这里没有清空removeEntities,
        // 因为它可能已经传递到外部使用。例如BatchActions.CollectionBatchAction。
        this.removedEntities = null;
        this.initialEntities = this.entities;
    }

    @Override
    public void orm_unload() {
        if (proxy)
            return;

        this.proxy = true;
        this.entities.clear();
        this.orm_clearDirty();
        this.initialEntities = null;
        this.keyToEntityMap = null;
    }

    @Override
    public void orm_beginLoad() {
        this.dirty = false;
        this.entities.clear();
        this.removedEntities = null;
        this.initialEntities = new LinkedHashSet<>();
        this.keyToEntityMap = null;
    }

    @Override
    public void orm_endLoad() {

    }

    @Override
    public Set<T> orm_removed() {
        if (removedEntities == null)
            return null;
        return new LinkedHashSet<>(removedEntities);
    }

    @Override
    public Set<T> orm_added() {
        if (initialEntities == entities)
            return Collections.emptySet();

        if (initialEntities == null)
            return new LinkedHashSet<>(entities);

        Set<T> ret = new LinkedHashSet<>(entities);
        ret.removeAll(initialEntities);
        return ret;
    }

    @Override
    public void orm_add(T o) {
        initialEntities.add(o);
        entities.add(o);
        this.keyToEntityMap = null;
    }

    Set<T> makeRemovedEntities() {
        if (removedEntities == null)
            removedEntities = new LinkedHashSet<>();
        return removedEntities;
    }

    final class IteratorView implements Iterator<T> {
        private final Iterator<T> it;
        private T current;

        IteratorView(Iterator<T> it) {
            this.it = it;
        }

        public boolean hasNext() {
            return it.hasNext();
        }

        public T next() {
            current = it.next();
            return current;
        }

        public void remove() {
            doRemove(current);
            it.remove();
        }
    }
}