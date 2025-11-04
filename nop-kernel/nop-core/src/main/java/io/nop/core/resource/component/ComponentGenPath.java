/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component;

import java.io.Serializable;

/**
 * 参见{@link IComponentGenPathStrategy}
 */
public class ComponentGenPath implements Serializable {
    private static final long serialVersionUID = 8001235044941973702L;

    private final String modelPath;
    private final String genFormat;

    public ComponentGenPath(String modelPath, String genFormat) {
        this.modelPath = modelPath;
        this.genFormat = genFormat;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getGenFormat() {
        return genFormat;
    }

}