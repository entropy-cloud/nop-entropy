/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.handler;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializer;

/**
 * 增加复杂value结构的序列化功能。基础的CollectTextJsonHandler不能处理bean的序列化
 */
public class SerializerJsonHandler extends DelegateJsonHandler {
    private final IJsonSerializer serializer;

    public SerializerJsonHandler(IJsonHandler handler, IJsonSerializer serializer) {
        super(handler);
        this.serializer = serializer;
    }

    @Override
    public IJsonHandler value(SourceLocation loc, Object value) {
        serializer.serializeToJson(loc, value, this);
        return this;
    }
}