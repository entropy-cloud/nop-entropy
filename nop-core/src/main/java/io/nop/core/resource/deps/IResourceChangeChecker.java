/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.deps;

import io.nop.api.core.resource.IResourceReference;
import io.nop.core.resource.VirtualFileSystem;

public interface IResourceChangeChecker {
    default IResourceReference resolveResource(String resourcePath) {
        if (!VirtualFileSystem.isInitialized())
            return null;
        return VirtualFileSystem.instance().getRawResource(resourcePath, true);
    }

    /**
     * 检查单个资源文件是否已经发生变化。
     *
     * @param lastModified 上次记录的文件修改时间。如果当前文件修改时间没有发生变化，则认为没有修改。否则可能要检查文件的内容是否已经发生变化
     * @return 文件内容是否已经修改，以及文件最新的修改时间
     */
    ResourceChangeCheckResult checkChanged(IResourceReference resource, long lastModified);
}