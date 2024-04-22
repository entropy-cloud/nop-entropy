/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.support;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.collections.ImmutableIntArray;
import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.IntArrayMap;
import io.nop.commons.collections.MapOfInt;
import io.nop.commons.collections.bit.IBitSet;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntityEnhancer;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmEntityState;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IEntityModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ObjIntConsumer;

import static io.nop.api.core.util.Guard.notNull;
import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_ID;
import static io.nop.orm.OrmErrors.ARG_PROP_ID_BOUND;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_TENANT_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_IS_READONLY;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NOT_ATTACHED;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_NOT_ALLOW_SET;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_TYPE_CONVERSION_FAIL;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_UNKNOWN_PROP;
import static io.nop.orm.OrmErrors.ERR_ORM_INVALID_PROP_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_NULL_ENTITY_MODEL;
import static io.nop.orm.OrmErrors.ERR_ORM_SESSION_CLOSED;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN_PROP_ID;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_PROP;

public abstract class OrmEntity implements IOrmEntity {
    private IOrmEntityEnhancer enhancer;
    private OrmEntityState state = OrmEntityState.TRANSIENT;
    private IEntityModel entityModel;

    private boolean readonly;
    private boolean extDirty;
    private boolean locked;
    private boolean fullyLoaded;
    private boolean flushVisiting;
    private boolean disableAutoStamp;
    private boolean disableLogicalDelete;

    /**
     * 记录哪些属性被读取过
     */
    private IBitSet accessedProps;

    /**
     * 标记所有已经被设置了值的字段
     */
    private IBitSet initedProps;

    private MapOfInt<Object> oldValues;

    /**
     * id一旦被初始化，就不会被修改。
     */
    private Object id;

    private List<Runnable> pkWatchers;

    /**
     * 允许在实体对象上保存一些临时属性，实现类似缓存的作用。
     */
    private Map<String, Object> _t;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[entityName=").append(orm_entityName());
        sb.append(",id=").append(get_id());
        sb.append(",status=").append(state);
        if (orm_tenantId() != null) {
            sb.append(",tenantId=").append(orm_tenantId());
        }
        if (orm_dirty()) {
            sb.append(",dirty");
        }
        if (orm_readonly()) {
            sb.append(",readonly");
        }
        if (orm_fullyLoaded()) {
            sb.append(",fullyLoaded");
        }
        if (orm_locked()) {
            sb.append(",locked");
        }
        sb.append(']');
        return sb.toString();
    }

    public String orm_entityName() {
        if (entityModel != null)
            return entityModel.getName();
        return getClass().getName();
    }

    public void orm_entityModel(IEntityModel entityModel) {
        this.entityModel = entityModel;
    }

    public IEntityModel orm_entityModel() {
        return entityModel;
    }

    protected IEntityModel requireEntityModel() {
        if (entityModel == null)
            throw newError(ERR_ORM_NULL_ENTITY_MODEL);
        return entityModel;
    }

    @Override
    public IOrmEntityEnhancer orm_enhancer() {
        return enhancer;
    }

    @Override
    public boolean orm_inited() {
        if (state == OrmEntityState.PROXY)
            return false;
        return initedProps != null && !initedProps.isEmpty();
    }

    @Override
    public OrmEntityState orm_state() {
        return state;
    }

    @Override
    public void orm_state(OrmEntityState status) {
        this.state = notNull(status, "state");
    }

    @Override
    public void orm_markFullyLoaded() {
        Guard.checkState(initedProps != null);
        this.fullyLoaded = true;
    }

    @Override
    public boolean orm_fullyLoaded() {
        return fullyLoaded;
    }

    @Override
    public void orm_useOldValues(IOrmEntity oldEntity) {
        if (oldValues == null)
            this.oldValues = newValuesMap();

        oldEntity.orm_forEachInitedProp((v, propId) -> {
            oldValues.put(propId, oldEntity.orm_propOldValue(propId));
        });
    }

    @Override
    public void orm_unload() {
        switch (state) {
            case MANAGED:
            case DELETED:
            case MISSING: {
                state = OrmEntityState.PROXY;
                if (initedProps != null)
                    initedProps.clear();
                this._t = null;
                orm_clearDirty();
                return;
            }
        }
    }

    @Override
    public boolean orm_dirty() {
        return oldValues != null && !oldValues.isEmpty();
    }

    @Override
    public boolean orm_readonly() {
        return readonly;
    }

    @Override
    public void orm_readonly(boolean readonly) {
        this.readonly = true;
    }

    @Override
    public boolean orm_locked() {
        return locked;
    }

    @Override
    public void orm_locked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public boolean orm_extDirty() {
        return extDirty;
    }

    public void orm_extDirty(boolean dirty) {
        this.extDirty = dirty;
        if (enhancer != null) {
            if (dirty) {
                enhancer.internalMarkExtDirty(this);
            } else {
                enhancer.internalClearExtDirty(this);
            }

            int propId = nopRevChildChangePropId();
            if (propId > 0) {
                // 如果是数据库中已经存在的记录，则先设置为0再设置为1会强制标记实体为dirty
                if (orm_state().isManaged()) {
                    orm_propValue(propId, 0);
                }
                orm_propValue(propId, 1);
            }
        }
    }

    protected int nopRevChildChangePropId() {
        return -1;
    }

    @Override
    public boolean orm_propDirty(int propId) {
        if (oldValues == null || oldValues.isEmpty())
            return false;
        return oldValues.containsKey(propId);
    }

    @Override
    public boolean orm_propInited(int propId) {
        if (initedProps == null)
            return false;
        return initedProps.get(propId);
    }

    @Override
    public void orm_clearDirty() {
        if (oldValues != null)
            oldValues.clear();
        if (enhancer != null)
            enhancer.internalClearDirty(this);
    }

    @Override
    public boolean orm_flushVisiting() {
        return flushVisiting;
    }

    @Override
    public void orm_flushVisiting(boolean flushVisiting) {
        this.flushVisiting = flushVisiting;
    }

    @Override
    public void orm_reset() {
        if (oldValues != null) {
            oldValues.forEachEntry((value, propId) -> {
                orm_propValue(propId, value);
            });
            oldValues.clear();
        }
        this._t = null;
        if (enhancer != null)
            enhancer.internalClearDirty(this);
    }

    @Override
    public Object orm_propOldValue(int propId) {
        if (oldValues != null) {
            Object value = oldValues.get(propId);
            if (value != null || oldValues.containsKey(propId))
                return value;
        }
        // 如果没有被修改过，则返回当前值
        return orm_propValue(propId);
    }

    @Override
    public Map<String, Object> orm_dirtyOldValues() {
        if (oldValues == null || oldValues.isEmpty())
            return Collections.emptyMap();
        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(oldValues.size());
        oldValues.forEachEntry((value, index) -> {
            String propName = orm_propName(index);
            ret.put(propName, value);
        });
        return ret;
    }

    @Override
    public Map<String, Object> orm_dirtyNewValues() {
        if (oldValues == null || oldValues.isEmpty())
            return Collections.emptyMap();
        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(oldValues.size());
        oldValues.forEachEntry((value, index) -> {
            String propName = orm_propName(index);
            Object newValue = orm_propValue(index);
            ret.put(propName, newValue);
        });
        return ret;
    }

    @Override
    public Map<String, Object> orm_initedValues() {
        if (initedProps == null)
            return Collections.emptyMap();

        Map<String, Object> ret = CollectionHelper.newLinkedHashMap(initedProps.cardinality());
        initedProps.forEach(propId -> {
            String propName = orm_propName(propId);
            Object propValue = orm_propValue(propId);
            ret.put(propName, propValue);
        });

        return ret;
    }

    @Override
    public boolean orm_propLoaded(int propId) {
        if (fullyLoaded)
            return true;

        // 主键字段不会延迟加载
        if (orm_isPrimary(propId))
            return true;

        if (initedProps == null)
            return false;
        return initedProps.get(propId);
    }

    @Override
    public boolean orm_attached() {
        return enhancer != null;
    }

    @Override
    public void orm_attach(IOrmEntityEnhancer enhancer) {
        this.enhancer = enhancer;
    }

    @Override
    public void orm_detach() {
        this.enhancer = null;
        // 与session脱离之后也不再保存locked状态。lock对应于数据库中的锁，脱离session后就不再有意义。
        this.locked = false;
    }

    @Override
    public boolean orm_proxy() {
        return state == OrmEntityState.PROXY;
    }

    @Override
    public IntArray orm_dirtyPropIds() {
        if (oldValues == null || oldValues.isEmpty())
            return ImmutableIntArray.EMPTY;
        return oldValues.keySet();
    }

    protected IOrmEntityEnhancer requireEnhancer() {
        if (enhancer == null) {
            if (state != OrmEntityState.TRANSIENT)
                throw newError(ERR_ORM_SESSION_CLOSED);
            throw newError(ERR_ORM_ENTITY_NOT_ATTACHED);
        }
        return enhancer;
    }

    @Override
    public Object orm_computed(String propName, Map<String, Object> args) {
        return requireEnhancer().internalCompute(this, propName, args);
    }

    @Override
    public Object orm_propValueByName(String propName) {
        if (OrmConstants.PROP_ID.equals(propName))
            return orm_id();
        Object value = BeanTool.instance().getProperty(this, propName);
        return value;
    }

    @Override
    public void orm_propValueByName(String propName, Object value) {
        BeanTool.instance().setProperty(this, propName, value);
    }

    protected void markPropAccessed(int propId) {
        if (accessedProps == null) {
            accessedProps = newBitSet();
        }
        accessedProps.set(propId);
    }

    protected void forcePropLoaded(int propId) {
        // 如果是新建对象，尚未保存到数据库中，则直接返回
        if (state == OrmEntityState.TRANSIENT || state == OrmEntityState.SAVING || state == OrmEntityState.MISSING)
            return;

        if (orm_propLoaded(propId))
            return;

        requireEnhancer().internalLoadProperty(this, propId);
    }

    private boolean isNeedLoad() {
        return state == OrmEntityState.MANAGED || state == OrmEntityState.PROXY;
    }

    /**
     * 标记字段值已修改，并记录第一次修改前的值。如果多次修改，则oldValues中保存的是初次初始化的值。
     */
    protected boolean markPropDirty(int propId, Object value) {
        checkReadonly();
        Object oldValue = orm_propValue(propId);
        if (Objects.equals(oldValue, value))
            return false;

        if (this.oldValues == null)
            this.oldValues = newValuesMap();

        if (!oldValues.containsKey(propId)) {
            oldValues.put(propId, oldValue);
        }

        IOrmEntityEnhancer enhancer = orm_enhancer();
        if (enhancer != null)
            enhancer.internalMarkDirty(this);
        return true;
    }

    protected void checkReadonly() {
        if (readonly)
            throw newError(ERR_ORM_ENTITY_IS_READONLY);
    }

    @Override
    public void orm_forEachDirtyProp(ObjIntConsumer<Object> consumer) {
        if (oldValues == null)
            return;

        oldValues.forEachEntry(consumer);
    }

    @Override
    public void orm_forEachInitedProp(ObjIntConsumer<Object> consumer) {
        if (initedProps == null)
            return;
        initedProps.forEach(propId -> {
            Object value = orm_propValue(propId);
            consumer.accept(value, propId);
        });
    }

    public String orm_tenantId() {
        if (entityModel != null) {
            int propId = entityModel.getTenantPropId();
            if (propId > 0) {
                if (orm_propInited(propId))
                    return StringHelper.toString(orm_propValue(propId), null);
                return null;
            }
        }
        return null;
    }

    /**
     * 返回一个可扩展的临时属性集合
     *
     * @return
     */
    public Map<String, Object> get_t() {
        return _t;
    }

    public Map<String, Object> make_t() {
        if (_t == null)
            _t = new HashMap<>();
        return _t;
    }

    protected NopException newError(ErrorCode errorCode) {
        NopException e = new OrmException(errorCode).param(ARG_ENTITY_NAME, orm_entityName()).param(ARG_ENTITY_ID,
                orm_id());
        String tenantId = orm_tenantId();
        if (tenantId != null)
            e.param(ARG_TENANT_ID, orm_tenantId());
        return e;
    }

    protected NopException newTypeConversionError(String propName) {
        return newError(ERR_ORM_ENTITY_PROP_TYPE_CONVERSION_FAIL).param(ARG_PROP_NAME, propName);
    }

    protected void onPropGet(int propId) {
        markPropAccessed(propId);
        forcePropLoaded(propId);
    }

    protected void checkPropIdRange(int propId) {
        if (propId >= orm_propIdBound() || propId <= 0)
            throw newError(ERR_ORM_INVALID_PROP_ID).param(ARG_PROP_ID_BOUND, orm_propIdBound()).param(ARG_PROP_ID,
                    propId);
    }

    protected boolean onPropSet(int propId, Object value) {
        if (isNeedLoad()) {
            forcePropLoaded(propId);
        }
        boolean changed = markPropDirty(propId, value);
        onInitProp(propId);
        return changed;
    }

    protected void onInitProp(int propId) {
        if (initedProps == null)
            initedProps = newBitSet();

        initedProps.set(propId);
    }

    protected IBitSet newBitSet() {
        return CollectionHelper.newFixedBitSet(orm_propIdBound());
    }

    protected MapOfInt<Object> newValuesMap() {
        return new IntArrayMap<>(orm_propIdBound());
    }

    public <T extends IOrmEntity> T newOrmEntity(Class<T> clazz, boolean initId) {
        IOrmEntityEnhancer enhancer = requireEnhancer();
        T entity = (T) enhancer.newEntity(clazz.getName());
        if (initId)
            enhancer.initEntityId(entity);
        return entity;
    }

    @Override
    public void orm_addPkWatcher(Runnable watcher) {
        if (id != null) {
            watcher.run();
            return;
        }

        if (pkWatchers == null)
            pkWatchers = new ArrayList<>();
        pkWatchers.add(watcher);
    }

    protected Object buildSimpleId(int propId) {
        if (id != null)
            return id;

        Object value = orm_propValue(propId);
        if (value != null) {
            id = value;
            internalNotifyPkWatcher();
        }
        return value;
    }

    protected Object buildCompositeId(List<String> propNames, int[] propIds) {
        if (id != null)
            return id;

        Object[] idValues = new Object[propIds.length];
        for (int i = 0, n = propIds.length; i < n; i++) {
            Object value = orm_propValue(propIds[i]);
            if (value == null)
                return null;
            idValues[i] = value;
        }
        id = new OrmCompositePk(propNames, idValues);
        internalNotifyPkWatcher();
        return id;
    }

    /**
     * 当主键第一次设置值时调用此函数。主键值不允许修改，因此执行完回调后就可以清除。
     */
    protected void internalNotifyPkWatcher() {
        List<Runnable> watchers = this.pkWatchers;
        if (watchers != null) {
            for (Runnable watcher : watchers) {
                watcher.run();
            }
            this.pkWatchers = null;
        }
    }

    protected Object defaultGetProp(String propName) {
        throw newError(ERR_ORM_ENTITY_UNKNOWN_PROP).param(ARG_PROP_NAME, propName);
    }

    protected void defaultSetProp(String propName, Object value) {
        throw newError(ERR_ORM_ENTITY_PROP_NOT_ALLOW_SET).param(ARG_PROP_NAME, propName);
    }

    // ================ 以下方法在派生类中会被覆盖 ==================
    protected void internalClearRefs(int propId) {

    }

    @Override
    public Object orm_propValue(int propId) {
        throw newError(ERR_ORM_UNKNOWN_COLUMN_PROP_ID).param(ARG_PROP_ID, propId);
    }

    @Override
    public void orm_propValue(int propId, Object value) {
        throw newError(ERR_ORM_UNKNOWN_COLUMN_PROP_ID).param(ARG_PROP_ID, propId);
    }

    @Override
    public String orm_propName(int propId) {
        // throw newError(ERR_ORM_UNKNOWN_COLUMN_PROP_ID).param(ARG_PROP_ID, propId);
        return null;
    }

    @Override
    public int orm_propId(String propName) {
        throw newError(ERR_ORM_UNKNOWN_PROP).param(ARG_PROP_NAME, propName);
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        throw newError(ERR_ORM_UNKNOWN_COLUMN_PROP_ID).param(ARG_PROP_ID, propId);
    }

    @Override
    public void orm_flushComponent() {
        IEntityModel entityModel = orm_entityModel();
        for (IEntityComponentModel compM : entityModel.getComponents()) {
            if (compM.isNeedFlush()) {
                Object comp = orm_propValueByName(compM.getName());
                if (comp instanceof IOrmComponent)
                    ((IOrmComponent) comp).onEntityFlush();
            }
        }
    }

    @Override
    public boolean orm_disableAutoStamp() {
        return disableAutoStamp;
    }

    public void orm_disableAutoStamp(boolean value) {
        this.disableAutoStamp = value;
    }

    public Object orm_getBean(String name) {
        return orm_enhancer().getBeanProvider().getBean(name);
    }

    @Override
    public boolean orm_disableLogicalDelete() {
        return disableLogicalDelete;
    }

    @Override
    public void orm_disableLogicalDelete(boolean value) {
        this.disableLogicalDelete = value;
    }

    @Override
    public <T extends IOrmEntity> T orm_requireEntity() {
        OrmEntityState state = orm_state();
        if (state.isProxy())
            orm_enhancer().internalLoad(this);


        if (state.isGone())
            throw new UnknownEntityException(get_entityName(), get_id());
        return (T) this;
    }

    @Override
    public void orm_forceLoad() {
        if (orm_proxy())
            orm_enhancer().internalLoad(this);
    }
}