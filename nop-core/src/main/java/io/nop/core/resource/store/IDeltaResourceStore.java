/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.store;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;

import java.util.Set;

/**
 * DefaultVirtualFileSystem内部实现时使用的接口。
 */
public interface IDeltaResourceStore extends IResourceStore {
    Set<String> getClassPathFiles();

    IResource getSuperResource(String path, boolean returnNullIfNotExists);

    IResource getRawResource(String path);

    void updateInMemoryLayer(IResourceStore resourceStore);

    IResourceStore getInMemoryLayer();
}
