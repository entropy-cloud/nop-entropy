/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import io.nop.core.resource.IResourceObjectLoader;

public class ComponentModelLoader {
    private final String modelType;
    private final IResourceObjectLoader<Object> loader;

    public ComponentModelLoader(String modelType, IResourceObjectLoader<Object> loader) {
        this.modelType = modelType;
        this.loader = loader;
    }

    public String getModelType() {
        return modelType;
    }

    public IResourceObjectLoader<Object> getLoader() {
        return loader;
    }
}
