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
    ResourceChangeCheckResult checkChanged(String path, long lastModified);

    /**
     * 宽限感知的检查变体：判据值为 (lastModified, recordedAt) 二元组，recordedAt为判据值的记录时刻（宽限锚）。
     * 判据语义全仓唯一实现于 {@link DefaultResourceChangeChecker}：mtime相等比较 + 宽限期承诺双条件。
     *
     * <p>default实现委托既有2-arg入口，即宽限0档（纯mtime相等比较）——既有自定义checker实现无需改码即保持旧语义。
     *
     * @param lastModified 上次记录的文件修改时间
     * @param recordedAt   上次记录判据值时的时刻（宽限锚，取自ResourceDependencySet.getRecordedAt()）
     * @return 文件内容是否已经修改（含宽限期内保守判定），以及文件最新的修改时间
     */
    default ResourceChangeCheckResult checkChanged(String path, long lastModified, long recordedAt) {
        return checkChanged(path, lastModified);
    }
}
