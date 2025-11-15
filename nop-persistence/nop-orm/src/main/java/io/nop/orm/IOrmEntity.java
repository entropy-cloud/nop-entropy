/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.util.ICloneable;
import io.nop.commons.collections.IntArray;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoEntity;
import io.nop.orm.model.IEntityModel;

import java.util.Map;
import java.util.function.ObjIntConsumer;

/**
 * 实体是一个有状态对象，它具有唯一id，通过状态变量来跟踪与数据库之间的同步关系。 实体内部记录了哪些属性已经设置了值，那些属性已经被修改，修改前的值和修改后的值分别是什么，因此可以通过orm_reset()在内存中实现回滚。
 */
public interface IOrmEntity extends IDaoEntity, IOrmObject, ICloneable, IOrmEntityLifecycle {
    String toString();

    IOrmEntity cloneInstance();

    /**
     * 实体名称
     */
    String orm_entityName();

    String orm_tenantId();

    /**
     * 实体的主键，如果是复合主键，则返回类型为{@link IOrmCompositePk}
     *
     * @return
     */
    default Object get_id() {
        return orm_id();
    }

    default String orm_idString() {
        return StringHelper.toString(get_id(), null);
    }

    default boolean orm_hasId() {
        return orm_id() != null;
    }

    /**
     * 对于复合主键而言，如果存在某个字段为null，则返回null
     *
     * @return 返回非空值时表示所有主键字段都非空
     */
    Object orm_id();

    default String get_entityName() {
        return orm_entityName();
    }

    /**
     * 实体当前的状态
     */
    OrmEntityState orm_state();

    @Internal
    void orm_state(OrmEntityState state);

    /**
     * 新建实体保存到session中后会调用此函数，标记所有属性都已经加载。这样在迁移到MANAGED状态之后不会导致加载未设置的属性的情况。
     */
    @Internal
    void orm_markFullyLoaded();

    /**
     * 是否所有属性都已经加载，没有需要延迟加载的属性
     */
    boolean orm_fullyLoaded();

    /**
     * 使用oldEntity中的值作为当前实体的oldValue
     *
     * @param oldEntity
     */
    @Internal
    void orm_useOldValues(IOrmEntity oldEntity);

    /**
     * 如果当前实体状态为managed/deleted/missing，则修改为proxy状态，放弃当前对象上的修改，允许重新从数据库中加载
     */
    @Internal
    void orm_unload();

    /**
     * 所有属于本实体的数据列具有唯一的编号，这里返回最大编号+1。propId从1开始，0被保留。并且propId不保证连续性，有可能会出现空缺的情况
     */
    int orm_propIdBound();

    boolean orm_dirty();

    /**
     * 是否关联对象集合为dirty
     */
    boolean orm_extDirty();

    void orm_extDirty(boolean dirty);

    boolean orm_readonly();

    void orm_readonly(boolean readonly);

    boolean orm_locked();

    @Internal
    void orm_locked(boolean locked);

    /**
     * 判断指定实体属性是否已经被修改
     *
     * @param propId 属性Id, 只有列属性具有propId，关联的集合对象和引用对象等通过propName来访问，它们没有propId属性。
     */
    boolean orm_propDirty(int propId);

    default boolean orm_propDirtyByName(String propName) {
        return orm_propDirty(orm_propId(propName));
    }

    /**
     * 清空dirty标识。
     */
    void orm_clearDirty();

    /**
     * 判断指定属性的值是否已经被设置。
     *
     * @param propId 属性id
     */
    boolean orm_propInited(int propId);

    /**
     * 放弃所有当前修改，将字段值重置为修改前的值
     */
    void orm_reset();

    /**
     * 得到指定属性的属性名
     *
     * @param propId
     */
    String orm_propName(int propId);

    /**
     * 根据属性名得到属性id。如果未找到对应属性，则返回-1
     *
     * @param propName 属性名
     */
    int orm_propId(String propName);

    /**
     * 根据属性名称获取值
     *
     * @param propName 属性名称
     */
    Object orm_propValueByName(String propName);

    void orm_propValueByName(String propName, Object value);

    default void orm_propValues(Map<String, Object> values) {
        values.forEach(this::orm_propValueByName);
    }

    /**
     * 得到指定属性的值
     *
     * @param propId 属性id
     */
    Object orm_propValue(int propId);

    /**
     * 设置指定属性的值
     *
     * @param propId 属性id
     * @param value  要设置的值
     */
    void orm_propValue(int propId, Object value);

    /**
     * 由orm引擎内部使用，设置指定属性的值，跳过延迟加载检查等处理过程，直接设置对象字段值。
     *
     * @param propId 属性id
     * @param value  属性值 类型必须与属性类型一致，因为是由ORM引擎内部负责调用此函数，因此不会进行类型转换。
     */
    @Internal
    void orm_internalSet(int propId, Object value);

    /**
     * 属性修改前的值
     *
     * @param propId 属性id
     */
    Object orm_propOldValue(int propId);

    default Object orm_propOldValueByName(String propName) {
        return orm_propOldValue(orm_propId(propName));
    }

    /**
     * 得到修改前的属性值，key为propName
     */
    Map<String, Object> orm_dirtyOldValues();

    Map<String, Object> orm_dirtyNewValues();

    void orm_forEachDirtyProp(ObjIntConsumer<Object> consumer);

    /**
     * 当前实体对象的属性是否已经被设置。如果任何属性都没有被设置，则返回false
     */
    boolean orm_inited();

    /**
     * 得到当前实体上已经设置的属性值。如果一个实体具有延迟加载的属性，则initedValues只包含已经被加载的属性。
     */
    Map<String, Object> orm_initedValues();

    void orm_forEachInitedProp(ObjIntConsumer<Object> consumer);

    /**
     * 判断指定属性是否已经被加载
     *
     * @param propId 属性id
     */
    boolean orm_propLoaded(int propId);

    /**
     * 是否与session绑定
     *
     * @return
     */
    boolean orm_attached();

    /**
     * 与session绑定。
     *
     * @param enhancer session提供的延迟加载功能
     */
    @Internal
    void orm_attach(IOrmEntityEnhancer enhancer);

    @Internal
    void orm_entityModel(IEntityModel entityModel);

    IEntityModel orm_entityModel();

    @Internal
    void orm_detach();

    boolean orm_proxy();

    IntArray orm_dirtyPropIds();

    /**
     * session的flush的处理过程中标记为visiting，避免循环处理
     */
    @Internal
    boolean orm_flushVisiting();

    @Internal
    void orm_flushVisiting(boolean flushVisiting);

    default <T extends IOrmEntity> IOrmEntitySet<T> orm_refEntitySet(String propName) {
        return (IOrmEntitySet<T>) orm_propValueByName(propName);
    }

    default IOrmEntity orm_refEntity(String propName) {
        return (IOrmEntity) orm_propValueByName(propName);
    }

    boolean orm_refLoaded(String propName);

    void orm_unsetRef(String propName);

    Object orm_computed(String propName, Map<String, Object> args);

    /**
     * 判断字段是否主键
     */
    boolean orm_isPrimary(int propId);

    /**
     * 如果实体的主键尚未初始化，则注册监听器，否则立刻触发监听器
     */
    @Internal
    void orm_addPkWatcher(Runnable watcher);

    void orm_flushComponent();

    /**
     * 跳过createdBy/createTime/updatedBy/updateTime等簿记字段的自动处理
     */
    boolean orm_disableAutoStamp();

    void orm_disableAutoStamp(boolean value);

    boolean orm_disableLogicalDelete();

    void orm_disableLogicalDelete(boolean value);

    boolean orm_disableVersionCheckError();

    void orm_disableVersionCheckError(boolean value);

    boolean orm_logicalDeleted();

    /**
     * 如果是proxy状态，则强制加载实体。如果加载后发现实体不存在，则抛出异常
     *
     * @param <T> 当前实体的实际类型
     * @return 返回当前实体
     */
    <T extends IOrmEntity> T orm_requireEntity();

    void orm_forceLoad();
}