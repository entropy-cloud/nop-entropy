/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeutils;

import java.nio.charset.StandardCharsets;

import io.nop.core.lang.json.JsonTool;

/**
 * JSON-based {@link IStreamSerializer} for {@code @DataBean}-shaped values:
 * the payload is the value's UTF-8 JSON form ({@link JsonTool#serialize}), and
 * restore re-materializes it with {@link JsonTool#parseBeanFromText}. This is
 * the default serializer shape for state values that are plain JSON beans;
 * values with non-bean object graphs must use {@link JavaStreamSerializer}.
 */
public class JsonToolSerializer<T> implements IStreamSerializer<T> {

    private static final long serialVersionUID = 1L;

    @Override
    public byte[] serialize(T value) {
        if (value == null) return null;
        return JsonTool.serialize(value, false).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] data, Class<T> type) {
        if (data == null || data.length == 0) return null;
        String json = new String(data, StandardCharsets.UTF_8);
        return JsonTool.parseBeanFromText(json, type);
    }
}
