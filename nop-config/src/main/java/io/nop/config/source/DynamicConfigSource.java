/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.source;

import io.nop.commons.util.objects.ValueWithLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class DynamicConfigSource implements IConfigSource {
    private final String name;
    private volatile Map<String, ValueWithLocation> configVars; //NOSONAR
    private Runnable onClose;

    private final List<Runnable> onChanges = new CopyOnWriteArrayList<>();

    public DynamicConfigSource(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, ValueWithLocation> getConfigValues() {
        return configVars;
    }

    public void setConfigVars(Map<String, ValueWithLocation> configVars) {
        synchronized (this) {
            this.configVars = configVars;
        }
        triggerOnChange();
    }

    public void triggerOnChange() {
        for (Runnable onChange : onChanges) {
            onChange.run();
        }
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public void addOnChange(Runnable callback) {
        onChanges.add(callback);
    }

    @Override
    public void close() throws Exception {
        if (onClose != null)
            onClose.run();
    }
}
