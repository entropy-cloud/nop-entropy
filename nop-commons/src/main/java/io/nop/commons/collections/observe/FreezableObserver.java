/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.observe;

import io.nop.api.core.exceptions.NopException;

import static io.nop.api.core.ApiErrors.ARG_OBJ;
import static io.nop.api.core.ApiErrors.ERR_CHECK_OBJ_IS_FROZEN;

public class FreezableObserver implements ICollectionObserver {
    private boolean frozen;

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    @Override
    public void beforeModify(Object obj) {
        if (frozen)
            throw new NopException(ERR_CHECK_OBJ_IS_FROZEN).param(ARG_OBJ, obj);
    }
}
