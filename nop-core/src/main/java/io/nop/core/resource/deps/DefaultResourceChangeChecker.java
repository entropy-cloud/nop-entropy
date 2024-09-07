/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;

public class DefaultResourceChangeChecker implements IResourceChangeChecker {
    public static final DefaultResourceChangeChecker INSTANCE = new DefaultResourceChangeChecker();

    @Override
    public ResourceChangeCheckResult checkChanged(IResourceReference resource, long lastModified) {
        if (resource == null)
            return new ResourceChangeCheckResult(lastModified != -1, -1);

        long current = resource.lastModified();
        boolean changed = lastModified != current;

        return new ResourceChangeCheckResult(changed, current);
    }
}