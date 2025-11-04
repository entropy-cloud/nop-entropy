/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.serialize;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializer;
import io.nop.core.lang.json.IJsonSerializerFactory;
import io.nop.core.lang.json.handler.BuildObjectJsonHandler;
import io.nop.core.lang.json.handler.CollectTextJsonHandler;
import io.nop.core.lang.json.handler.SerializerJsonHandler;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModelManager;

import static io.nop.core.CoreConfigs.CFG_JSON_SERIALIZE_ONLY_DATA_BEAN;

public class JsonSerializeHelper {

    public static void serialize(IJsonSerializer serializer, SourceLocation loc, Object o, IJsonHandler out) {
        serializer.serializeToJson(loc, o, new SerializerJsonHandler(out, serializer));
    }

    public static void serialize(SourceLocation loc, Object o, IJsonHandler out) {
        IBeanModelManager beanModelManager = ReflectionManager.instance();
        IJsonSerializerFactory factory = JsonSerializerFactory.DEFAULT_FACTORY;
        JsonSerializer serializer = new JsonSerializer(beanModelManager, CFG_JSON_SERIALIZE_ONLY_DATA_BEAN.get(),
                factory, JsonWhitelistChecker.instance(), DisabledEvalScope.INSTANCE);
        serialize(serializer, loc, o, out);
    }

    public static String serializeToJsonString(IJsonSerializer serializer, SourceLocation loc, Object o,
                                               String indent) {
        StringBuilder sb = new StringBuilder();
        CollectTextJsonHandler handler = new CollectTextJsonHandler(sb);
        handler.indent(indent);
        serializer.serializeToJson(loc, o, new SerializerJsonHandler(handler, serializer));
        return sb.toString();
    }

    public static Object serializeToObject(IJsonSerializer serializer, Object o) {
        BuildObjectJsonHandler handler = new BuildObjectJsonHandler();
        serializer.serializeToJson(null, o, new SerializerJsonHandler(handler, serializer));
        return handler.getResult();
    }
}
