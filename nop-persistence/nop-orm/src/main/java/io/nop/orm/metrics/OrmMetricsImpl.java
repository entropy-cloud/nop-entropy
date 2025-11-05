/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.util.Arrays;

public class OrmMetricsImpl implements IOrmMetrics {
    private final MeterRegistry registry;
    private final String prefix;

    private final Counter sessionsOpen;
    private final Counter sessionsClosed;
    private final Counter sessionsFlush;

    private final Counter entitiesLoad;
    private final Counter entitiesDelete;
    private final Counter entitiesUpdate;
    private final Counter entitiesSave;


    public OrmMetricsImpl(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = prefix;

        sessionsOpen = createCounter("nop.orm.sessions.open");
        sessionsClosed = createCounter("nop.orm.sessions.closed");
        sessionsFlush = createCounter("nop.orm.sessions.flush");

        entitiesDelete = createCounter("nop.orm.entities.delete");
        entitiesLoad = createCounter("nop.orm.entities.load");
        entitiesUpdate = createCounter("nop.orm.entities.update");
        entitiesSave = createCounter("nop.orm.entities.save");
    }

    Counter createCounter(String name, Tag... tags) {
        return registry.counter(meterName(name), Arrays.asList(tags));
    }

    String meterName(String name) {
        if (prefix == null)
            return name;
        return prefix + '.' + name;
    }

    @Override
    public void onSessionOpen() {
        sessionsOpen.increment();
    }

    @Override
    public void onSessionClosed() {
        sessionsClosed.increment();
    }

    @Override
    public void onFlush() {
        sessionsFlush.increment();
    }

    @Override
    public void onLoadEntity(String entityName) {
        entitiesLoad.increment();
    }

    @Override
    public void onFlushDeleteEntity(String entityName) {
        entitiesDelete.increment();
    }

    @Override
    public void onFlushUpdateEntity(String entityName) {
        entitiesUpdate.increment();
    }

    @Override
    public void onFlushSaveEntity(String entityName) {
        entitiesSave.increment();
    }
}
