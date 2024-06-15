/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

/**
 * 用于跟踪对象相关的源码位置，类似SourceMap机制
 */
public interface ISourceLocationGetter {
    SourceLocation getLocation();

    default String resourcePath() {
        SourceLocation loc = getLocation();
        return loc == null ? null : loc.getPath();
    }

    default String resourceStdPath() {
        String path = resourcePath();
        if (path == null)
            return null;

        return ApiStringHelper.getStdPath(path);
    }
}
