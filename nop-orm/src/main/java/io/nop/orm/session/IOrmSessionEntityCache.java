/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.orm.IOrmEntity;

import java.util.function.Consumer;

/**
 * ORM引擎的一级缓存
 */
public interface IOrmSessionEntityCache {

    boolean isStateless();

    boolean contains(IOrmEntity entity);

    void remove(IOrmEntity entity);

    /**
     * 将实体保存到一级缓存中，返回此前与该主键对应的实体
     *
     * @param entity
     * @return
     */
    IOrmEntity add(IOrmEntity entity);

    IOrmEntity get(String entityName, Object id);

    void clear();

    void markDirty(String entityName);

    void clearDirty(String entityName);

    void clearDirty();

    void removeAll(String entityName);

    void forEachDirty(Consumer<IOrmEntity> processor);

    void forEachCurrent(Consumer<IOrmEntity> processor);

    /**
     * 遍历所有缓存中的实体的当前版本。实体对象的历史版本不会被此函数访问
     */
    void forEachCurrent(String entityName, Consumer<IOrmEntity> processor);
}