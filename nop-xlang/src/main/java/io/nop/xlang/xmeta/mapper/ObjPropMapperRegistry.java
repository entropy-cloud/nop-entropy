/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.mapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjPropMapperRegistry {

    private final Map<String, IObjPropMapper> mappers = new ConcurrentHashMap<>();

    public IObjPropMapper getMapper(String name) {
        return mappers.get(name);
    }

    public void registerMapper(String name, IObjPropMapper mapper) {
        mappers.put(name, mapper);
    }

    public void unregisterMapper(String name, IObjPropMapper mapper) {
        this.mappers.remove(name, mapper);
    }

}
