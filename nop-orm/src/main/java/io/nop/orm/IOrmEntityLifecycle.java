/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import io.nop.api.core.util.ProcessResult;

/**
 * 实体对象可以实现此接口来参与ORM引擎的实体生命周期管理
 */
public interface IOrmEntityLifecycle {

    default void orm_postLoad() {

    }

    default void orm_postSave() {
    }

    default void orm_postUpdate() {
    }

    default void orm_postDelete() {
    }

    default void orm_postReset() {
    }

    default ProcessResult orm_preSave() {
        return ProcessResult.CONTINUE;
    }

    default ProcessResult orm_preUpdate() {
        return ProcessResult.CONTINUE;
    }

    default ProcessResult orm_preDelete() {
        return ProcessResult.CONTINUE;
    }
}