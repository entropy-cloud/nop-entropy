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

        // 如果文件修改时间变了，但是缓存内容并没有变，则仍然标记为未改变
//        if (changed && resource instanceof IResource) {
//            String text = ResourceContentCache.instance().getCachedText((IResource) resource, false);
//            if (text != null) {
//                String resText = ((IResource) resource).readText();
//                if (text.equals(resText)) {
//                    changed = false;
//                }
//            }
//        }
        return new ResourceChangeCheckResult(changed, current);
    }
}