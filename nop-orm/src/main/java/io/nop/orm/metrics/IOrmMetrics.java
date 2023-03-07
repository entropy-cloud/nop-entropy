/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.metrics;

public interface IOrmMetrics {
    void onSessionOpen();

    void onSessionClosed();

    void onFlush();

    /**
     * Counter: nop.orm.entities.load
     */
    void onLogicalLoadEntity(String entityName);

    /**
     * Counter: nop.orm.entities.deletes
     */
    void onLogicalDeleteEntity(String entityName);

    /**
     * Counter: nop.orm.entities.updates
     */
    void onLogicalUpdateEntity(String entityName);

    /**
     * Counter: nop.orm.entities.saves
     */
    void onLogicalSaveEntity(String entityName);
}
