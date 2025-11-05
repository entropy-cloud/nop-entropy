/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.util.IOrdered;
import io.nop.api.core.util.ProcessResult;

public interface IOrmInterceptor extends IOrdered {
    default ProcessResult preSave(IOrmEntity entity) {
        return ProcessResult.CONTINUE;
    }

    default ProcessResult preUpdate(IOrmEntity entity) {
        return ProcessResult.CONTINUE;
    }

    default ProcessResult preDelete(IOrmEntity entity) {
        return ProcessResult.CONTINUE;
    }

    default void postReset(IOrmEntity entity) {

    }

    default void postSave(IOrmEntity entity) {

    }

    default void postUpdate(IOrmEntity entity) {

    }

    default void postDelete(IOrmEntity entity) {

    }

    default void postLoad(IOrmEntity entity) {

    }

    default void preFlush() {

    }

    default void postFlush(Throwable exception) {

    }
}