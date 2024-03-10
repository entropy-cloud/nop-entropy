/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.api.core.json.IJsonProvider;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;

import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * 提供对JSON的解析和序列化工具。一般应用代码不会直接使用JsonParser等接口。可以使用jackson库的实现来代替平台缺省实现
 */
public interface IJsonTool extends IJsonProvider {
    Object parseFromResource(IResource resource, JsonParseOptions options);

    Object parseFromText(SourceLocation loc, String text, JsonParseOptions options);

    boolean saveToResource(IResource resource, Object json, JsonSaveOptions options);

    void serialize(Object obj, String indent, Writer out);

    void serializeTo(Object obj, IJsonHandler handler);

    <T> T loadDeltaBean(IResource resource, Type targetType, DeltaJsonOptions options);

    <T> T buildDeltaBean(Map<String, Object> obj, Type targetType, DeltaJsonOptions options);

}