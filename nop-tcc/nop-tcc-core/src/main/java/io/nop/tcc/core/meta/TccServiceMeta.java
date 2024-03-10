/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.meta;

import java.util.HashMap;
import java.util.Map;

public class TccServiceMeta implements ITccServiceMeta {
    private final String serviceName;
    private final Map<String, TccMethodMeta> methodMetas;

    public TccServiceMeta(String serviceName, Map<String, TccMethodMeta> methodMetas) {
        this.serviceName = serviceName;
        this.methodMetas = methodMetas;
    }

    public TccServiceMeta merge(TccServiceMeta serviceMeta) {
        Map<String, TccMethodMeta> merged = new HashMap<>();
        merged.putAll(methodMetas);
        merged.putAll(serviceMeta.methodMetas);
        return new TccServiceMeta(serviceName, merged);
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public TccMethodMeta getMethodMeta(String serviceAction) {
        return methodMetas.get(serviceAction);
    }
}
