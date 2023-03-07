/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.util.Guard;

import java.util.ArrayList;
import java.util.List;

public class ChangeSubscription {
    private final String pattern;
    private final List<IConfigChangeListener> listeners = new ArrayList<>(2);

    public ChangeSubscription(String pattern) {
        this.pattern = Guard.notEmpty(pattern, "change subscription pattern");
    }

    public String getPattern() {
        return pattern;
    }

    public synchronized List<IConfigChangeListener> getListeners() {
        return new ArrayList<>(this.listeners);
    }

    public synchronized void addListener(IConfigChangeListener listener) {
        this.listeners.add(listener);
    }

    public synchronized void removeListener(IConfigChangeListener listener) {
        this.listeners.remove(listener);
    }
}
