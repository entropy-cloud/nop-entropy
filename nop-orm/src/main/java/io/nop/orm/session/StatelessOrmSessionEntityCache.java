/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.session;

import io.nop.orm.IOrmEntity;

import java.util.function.Consumer;

/**
 * 无状态的session
 */
public class StatelessOrmSessionEntityCache implements IOrmSessionEntityCache {
    private final IOrmSessionImplementor session;

    public StatelessOrmSessionEntityCache(IOrmSessionImplementor session) {
        this.session = session;
    }

    @Override
    public boolean isStateless() {
        return true;
    }

    @Override
    public boolean contains(IOrmEntity entity) {
        return entity.orm_enhancer() == session;
    }

    @Override
    public void remove(IOrmEntity entity) {
        entity.orm_detach();
    }

    @Override
    public IOrmEntity add(IOrmEntity entity) {
        entity.orm_attach(session);
        return null;
    }

    @Override
    public IOrmEntity get(String entityName, Object id) {
        return null;
    }

    @Override
    public void clear() {

    }

    @Override
    public void markDirty(String entityName) {

    }

    @Override
    public void clearDirty(String entityName) {

    }

    @Override
    public void clearDirty() {

    }

    @Override
    public void removeAll(String entityName) {

    }

    @Override
    public void forEachDirty(Consumer<IOrmEntity> processor) {

    }

    @Override
    public void forEachCurrent(Consumer<IOrmEntity> processor) {

    }

    @Override
    public void forEachCurrent(String entityName, Consumer<IOrmEntity> processor) {

    }
}
