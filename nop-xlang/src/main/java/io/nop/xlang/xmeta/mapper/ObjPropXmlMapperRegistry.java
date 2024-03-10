/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.mapper;

public class ObjPropXmlMapperRegistry {
    static final ObjPropMapperRegistry _instance = new ObjPropMapperRegistry();

    static {
        _instance.registerMapper("xml", new NodeToXmlMapper());
        _instance.registerMapper("json", new NodeToJsonMapper());
    }

    public static ObjPropMapperRegistry instance() {
        return _instance;
    }
}