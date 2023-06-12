/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.deps;

public class ResourceChangeCheckResult {

    private final boolean changed;
    private final long lastModified;

    public ResourceChangeCheckResult(boolean changed, long lastModified) {
        this.changed = changed;
        this.lastModified = lastModified;
    }

    public boolean isChanged() {
        return changed;
    }

    public long getLastModified() {
        return lastModified;
    }
}