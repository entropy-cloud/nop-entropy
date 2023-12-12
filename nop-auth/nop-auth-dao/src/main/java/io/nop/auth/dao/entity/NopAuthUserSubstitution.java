/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.auth.dao.entity._gen._NopAuthUserSubstitution;

import java.time.LocalDateTime;

@BizObjName("NopAuthUserSubstitution")
public class NopAuthUserSubstitution extends _NopAuthUserSubstitution {
    public NopAuthUserSubstitution() {
    }

    public boolean isValid(LocalDateTime now) {
        LocalDateTime start = getStartTime();
        if (start != null && start.isAfter(now))
            return false;

        LocalDateTime end = getEndTime();
        if (end != null) {
            return !end.isBefore(now);
        }
        return true;
    }
}
