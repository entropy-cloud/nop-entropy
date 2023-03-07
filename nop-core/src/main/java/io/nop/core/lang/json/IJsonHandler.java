/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json;

import io.nop.api.core.annotations.core.ReturnSelf;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;

public interface IJsonHandler {

    default void beginDoc(String encoding) {
    }

    Object endDoc();

    @ReturnSelf
    IJsonHandler comment(String comment);

    /**
     * 开始输出一个对象
     *
     * @param loc 源码位置
     */
    @ReturnSelf
    IJsonHandler beginObject(SourceLocation loc);

    @ReturnSelf
    IJsonHandler endObject();

    @ReturnSelf
    IJsonHandler key(String name);

    @ReturnSelf
    IJsonHandler value(SourceLocation loc, Object value);

    @ReturnSelf
    default IJsonHandler valueLoc(ValueWithLocation vl) {
        return value(vl.getLocation(), vl.getValue());
    }

    @ReturnSelf
    default IJsonHandler put(String name, Object value) {
        return key(name).value(null, value);
    }

    @ReturnSelf
    default IJsonHandler stringValue(SourceLocation loc, String value) {
        return value(loc, value);
    }

    @ReturnSelf
    default IJsonHandler numberValue(SourceLocation loc, Number value) {
        return value(loc, value);
    }

    @ReturnSelf
    default IJsonHandler booleanValue(SourceLocation loc, Boolean b) {
        return value(loc, b);
    }

    /**
     * 对于SerializerJsonHandler,这里rawValue对应直接调用低层的handler.rawValue函数，跳过serializer处理
     */
    @ReturnSelf
    default IJsonHandler rawValue(SourceLocation loc, Object value) {
        return value(loc, value);
    }

    @ReturnSelf
    IJsonHandler beginArray(SourceLocation loc);

    @ReturnSelf
    IJsonHandler endArray();
}