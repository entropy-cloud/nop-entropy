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
import io.nop.commons.collections.observe.ObservableMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class FreezableMap<K, V> extends ObservableMap<K, V> implements IFreezable {

    public FreezableMap() {
        this(new LinkedHashMap<>());
    }

    public FreezableMap(Map<K, V> map) {
        super(map, new FreezableObserver());
    }

    protected FreezableObserver getObserver() {
        return (FreezableObserver) super.getObserver();
    }

    public String toString() {
        return getClass().getSimpleName() + super.keySet();
    }

    @Override
    public boolean frozen() {
        return getObserver().isFrozen();
    }

    @Override
    public void freeze(boolean cascade) {
        getObserver().setFrozen(true);
        if (cascade) {
            FreezeHelper.deepFreezeObjects(this.values());
        }
    }

    @Override
    public Object clone() {
        return new FreezableMap<>(new LinkedHashMap<>(this));
    }
}