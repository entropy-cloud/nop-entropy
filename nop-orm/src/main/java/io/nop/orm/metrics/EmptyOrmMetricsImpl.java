/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.metrics;

public class EmptyOrmMetricsImpl implements IOrmMetrics {
    @Override
    public void onSessionOpen() {

    }

    @Override
    public void onSessionClosed() {

    }

    @Override
    public void onFlush() {

    }

    @Override
    public void onLoadEntity(String entityName) {

    }

    @Override
    public void onFlushDeleteEntity(String entityName) {

    }

    @Override
    public void onFlushUpdateEntity(String entityName) {

    }

    @Override
    public void onFlushSaveEntity(String entityName) {

    }
}
