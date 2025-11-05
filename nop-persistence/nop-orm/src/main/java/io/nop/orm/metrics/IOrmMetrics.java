/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.metrics;

public interface IOrmMetrics {
    /**
     * Counter: nop.orm.sessions.open
     */
    void onSessionOpen();

    /**
     * Counter: nop.orm.sessions.closed
     */
    void onSessionClosed();

    /**
     * Counter: nop.orm.sessions.flush
     */
    void onFlush();

    /**
     * Counter: nop.orm.entities.load
     */
    void onLoadEntity(String entityName);

    /**
     * Counter: nop.orm.entities.delete
     */
    void onFlushDeleteEntity(String entityName);

    /**
     * Counter: nop.orm.entities.update
     */
    void onFlushUpdateEntity(String entityName);

    /**
     * Counter: nop.orm.entities.save
     */
    void onFlushSaveEntity(String entityName);
}
