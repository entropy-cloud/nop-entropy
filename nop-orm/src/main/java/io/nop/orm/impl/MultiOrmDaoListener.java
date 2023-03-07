/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.impl;

import io.nop.orm.IOrmDaoListener;
import io.nop.orm.model.IEntityModel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MultiOrmDaoListener implements IOrmDaoListener {
    private List<IOrmDaoListener> daoListeners = new CopyOnWriteArrayList<>();

    public void addDaoListener(IOrmDaoListener listener) {
        this.daoListeners.add(listener);
    }

    public void removeDaoListener(IOrmDaoListener listener) {
        this.daoListeners.remove(listener);
    }

    @Override
    public void onRead(IEntityModel entityModel) {
        for (IOrmDaoListener listener : daoListeners) {
            listener.onRead(entityModel);
        }
    }

    @Override
    public void onUpdate(IEntityModel entityModel) {
        for (IOrmDaoListener listener : daoListeners) {
            listener.onUpdate(entityModel);
        }
    }

    @Override
    public void onDelete(IEntityModel entityModel) {
        for (IOrmDaoListener listener : daoListeners) {
            listener.onDelete(entityModel);
        }
    }

    @Override
    public void onSave(IEntityModel entityModel) {
        for (IOrmDaoListener listener : daoListeners) {
            listener.onSave(entityModel);
        }
    }
}