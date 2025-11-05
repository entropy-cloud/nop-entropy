/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.boot;

import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;

public class NopBanner {
    // private static final Logger LOG = LoggerFactory.getLogger(NopBanner.class);

    private String resourcePath;

    public NopBanner(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public void print() {
        IResource resource = new ClassPathResource(resourcePath);
        String text = ResourceHelper.readText(resource, "UTF-8");
        System.out.println(text);
    }
}
