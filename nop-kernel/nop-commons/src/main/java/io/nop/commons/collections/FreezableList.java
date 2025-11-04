/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.IFreezable;
import io.nop.commons.collections.observe.FreezableObserver;
import io.nop.commons.collections.observe.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class FreezableList<E> extends ObservableList<E> implements IFreezable {
    public FreezableList() {
        this(new ArrayList<>());
    }

    public FreezableList(List<E> collection) {
        super(collection, new FreezableObserver());
    }

    public String toString() {
        return getClass().getSimpleName() + '(' + size() + ')';
    }

    protected FreezableObserver getObserver() {
        return (FreezableObserver) super.getObserver();
    }

    @Override
    public boolean frozen() {
        return getObserver().isFrozen();
    }

    @Override
    public void freeze(boolean cascade) {
        getObserver().setFrozen(true);
        if (cascade) {
            FreezeHelper.deepFreezeObjects(this);
        }
    }
}