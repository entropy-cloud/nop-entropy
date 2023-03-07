/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.deps;

public interface IResourceDependsPersister {
    /**
     * 资源文件的依赖关系经过分析后可能被缓存下来，避免多次分析
     */
    ResourceDependencySet loadDepends(String resourcePath);

    void saveDepends(String resourcePath, ResourceDependencySet deps);
}