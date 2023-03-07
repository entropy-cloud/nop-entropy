/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component.parse;

import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

public interface IResourceParser<T> {
    T parseFromResource(IResource resource, boolean ignoreUnknown);

    default T parseFromResource(IResource resource) {
        return parseFromResource(resource, false);
    }

    default T parseFromVirtualPath(String path, boolean ignoreUnknown) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return parseFromResource(resource, ignoreUnknown);
    }

    default T parseFromVirtualPath(String path) {
        return parseFromVirtualPath(path, false);
    }
}