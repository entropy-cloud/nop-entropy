/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.jpath;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import io.nop.commons.cache.LocalCache;

import java.util.List;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_JPATH_CACHE_SIZE;

public class JPath {
    public static Configuration DEFAULT_CONFIG = Configuration.builder().jsonProvider(BeanJsonProvider.INSTANCE)
            .mappingProvider(BeanMappingProvider.INSTANCE).build();

    private final Configuration config;
    private final JsonPath path;

    static LocalCache<String, JPath> cache = LocalCache.newCache("jpath-compile-cache",
            newConfig(CFG_JPATH_CACHE_SIZE.get()), JPath::compile);

    public JPath(JsonPath path, Configuration config) {
        this.config = config;
        this.path = path;
    }

    public static JPath compile(String path) {
        return new JPath(JsonPath.compile(path), DEFAULT_CONFIG);
    }

    public static JPath jpath(String path) {
        return compileWithCache(path);
    }

    public static JPath compileWithCache(String path) {
        return cache.get(path);
    }

    public Object get(Object bean) {
        return path.read(bean, config);
    }

    public Object getOne(Object bean) {
        Object ret = get(bean);
        if (ret instanceof List) {
            List<?> list = (List<?>) ret;
            if (list.isEmpty())
                return null;
            return list.get(0);
        }
        return ret;
    }

    public void get(Object bean, Object value) {
        path.set(bean, value, config);
    }

    public void delete(Object bean) {
        path.delete(bean, config);
    }

    public static Object get(Object bean, String path) {
        return compileWithCache(path).get(bean);
    }

    public static void get(Object bean, String path, Object value) {
        compileWithCache(path).get(bean, value);
    }

    public static void delete(Object bean, String path) {
        compileWithCache(path).delete(bean);
    }
}