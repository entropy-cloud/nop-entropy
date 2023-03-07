/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.serialize;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.json.IJsonSerializer;
import io.nop.core.lang.json.IJsonSerializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsonSerializerFactory implements IJsonSerializerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(JsonSerializerFactory.class);

    public static JsonSerializerFactory DEFAULT_FACTORY = new JsonSerializerFactory();

    private Map<String, IJsonSerializer> serializers = new ConcurrentHashMap<>();
    private Map<Class, IJsonSerializer> serializersForClass = CollectionHelper.newConcurrentWeakMap();

    public void registerSerializer(String serializerName, IJsonSerializer serializer) {
        Object old = serializers.put(serializerName, serializer);
        if (old != null) {
            LOG.info("nop.json.replace-serializer:name={},serializer={}", serializerName, serializer);
        }
    }

    public void unregisterSerializer(String serializerName, IJsonSerializer serializer) {
        serializers.remove(serializerName, serializer);
    }

    public void registerSerializerForClass(Class clazz, IJsonSerializer serializer) {
        IJsonSerializer old = serializersForClass.put(clazz, serializer);
        if (old != null) {
            LOG.info("nop.json.replace-serializer-for-class:className={},serializer={}", clazz.getName(), serializer);
        }
    }

    public void unregisterSerializerForClass(Class clazz, IJsonSerializer serializer) {
        serializersForClass.remove(clazz, serializer);
    }

    @Override
    public IJsonSerializer getSerializer(String serializerName) {
        return serializers.get(serializerName);
    }

    @Override
    public IJsonSerializer getSerializerForClass(Class clazz) {
        return serializersForClass.get(clazz);
    }
}
