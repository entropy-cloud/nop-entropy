/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.service;

public enum ServiceStatus {
    CREATED,

    STARTING,

    ACTIVE,

    STOPPING,

    STOPPED;

    public boolean isAllowStart() {
        return this == CREATED;
    }

    public boolean isAllowStop() {
        return this == STARTING || this == ACTIVE;
    }
}