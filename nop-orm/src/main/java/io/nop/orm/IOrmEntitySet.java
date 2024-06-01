/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.annotations.core.Internal;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.reflect.hook.IPropMakeMissingHook;
import io.nop.core.reflect.hook.IPropSetMissingHook;
import jakarta.annotation.Nonnull;

import java.util.Comparator;
import java.util.Set;

/**
 * 对于主外键关联，在主表一侧可以通过集合对象来访问从表。在从表一侧可以通过ownerProp对应的属性来访问主表
 */
public interface IOrmEntitySet<T extends IOrmEntity>
        extends Set<T>, IOrmObject, IPropGetMissingHook, IPropSetMissingHook, IPropMakeMissingHook {
    /**
     * 每个集合必须具有一个owner，且不能改变owner。
     */
    @Nonnull
    IOrmEntity orm_owner();

    /**
     * 得到集合中的第一个元素
     */
    default T get__first() {
        return CollectionHelper.first(this);
    }

    /**
     * 本集合对应owner对象上的哪个属性。例如dept.children，owner为dept, propName为children
     */
    String orm_propName();

    String orm_refPropName();

    /**
     * 集合的名称，格式为 owner.entityName + '@' + propName
     */
    String orm_collectionName();

    T orm_newItem();

    /**
     * 集合是否是proxy对象。如果是，则第一次访问集合元素时会从数据库中实际加载数据
     *
     * @return
     */
    boolean orm_proxy();

    /**
     * 集合是否允许被修改
     */
    boolean orm_readonly();

    void orm_readonly(boolean readonly);

    /**
     * 判断集合是否已经发生改变。当集合没有发生插入删除操作，仅仅是集合元素的属性被修改时，集合本身不会被标记为dirty
     */
    boolean orm_dirty();

    void orm_clearDirty();

    //void orm_onFlush();

    /**
     * 返回被删除的元素，可能为null
     *
     * @return
     */
    Set<T> orm_removed();

    /**
     * 返回新增的元素，可能为null
     */
    Set<T> orm_added();

    void orm_sort(Comparator<? super T> comparator);

    @Internal
    void orm_proxy(boolean proxy);

    @Internal
    void orm_unload();

    @Internal
    void orm_beginLoad();

    @Internal
    void orm_internalAdd(T entity);

    @Internal
    void orm_endLoad();

    void orm_forceLoad();

    void orm_tenantId(String tenantId);
}