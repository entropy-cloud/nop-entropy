/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.serialize;

import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.bean.IBeanDeserializer;
import io.nop.core.reflect.bean.IBeanDeserializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsonDeserializerFactory implements IBeanDeserializerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(JsonDeserializerFactory.class);

    public static JsonDeserializerFactory DEFAULT_FACTORY = new JsonDeserializerFactory();

    private Map<Class, IBeanDeserializer> beanDeserializersForClass = CollectionHelper.newConcurrentWeakMap();
    private Map<String, IBeanDeserializer> beanDeserializers = new ConcurrentHashMap<>();

    public void registerDeserializerForClass(Class clazz, IBeanDeserializer deserializer) {
        Object old = beanDeserializersForClass.put(clazz, deserializer);
        if (old != null) {
            LOG.info("nop.json.replace-deserializer-for-class:className={},deserializer={}", clazz.getName(),
                    deserializer);
        }
    }

    public void unregisterDeserializerForClass(Class clazz, IBeanDeserializer deserializer) {
        beanDeserializersForClass.remove(clazz, deserializer);
    }

    public void registerDeserializer(String name, IBeanDeserializer deserializer) {
        Object old = beanDeserializers.put(name, deserializer);
        if (old != null) {
            LOG.info("nop.json.replace-deserializer:name={},deserializer={}", name, deserializer);
        }
    }

    @Override
    public IBeanDeserializer getDeserializer(String name) {
        return beanDeserializers.get(name);
    }

    @Override
    public IBeanDeserializer getDeserializerForClass(Class clazz) {
        return beanDeserializersForClass.get(clazz);
    }
}
