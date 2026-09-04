/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.api.core.util.IMapLike;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.ReflectionManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript Object 全局对象兼容：XScript 中裸名 {@code Object.keys(o)} 映射到本类静态方法。
 * 支持 Map / IMapLike（DynamicObject）/ 普通 Bean 三种输入类型。
 */
public class JsObject {

    public static List<String> keys(Object o) {
        Map<String, Object> map = toMap(o);
        return new ArrayList<>(map.keySet());
    }

    public static List<Object> values(Object o) {
        Map<String, Object> map = toMap(o);
        return new ArrayList<>(map.values());
    }

    public static List<List<Object>> entries(Object o) {
        Map<String, Object> map = toMap(o);
        List<List<Object>> ret = new ArrayList<>(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            List<Object> pair = new ArrayList<>(2);
            pair.add(entry.getKey());
            pair.add(entry.getValue());
            ret.add(pair);
        }
        return ret;
    }

    /**
     * Object.assign(target, ...sources)：浅合并，返回 target。
     */
    public static Object assign(Object target, Object... sources) {
        Map<String, Object> targetMap = toMap(target);
        if (sources != null) {
            for (Object source : sources) {
                if (source == null)
                    continue;
                targetMap.putAll(toMap(source));
            }
        }
        return target;
    }

    public static boolean hasOwn(Object o, String key) {
        Map<String, Object> map = toMap(o);
        return map.containsKey(key);
    }

    public static boolean isEmpty(Object o) {
        if (o == null)
            return true;
        return toMap(o).isEmpty();
    }

    private static Map<String, Object> toMap(Object o) {
        if (o == null)
            return new LinkedHashMap<>();
        if (o instanceof Map)
            return (Map<String, Object>) o;
        if (o instanceof IMapLike)
            return ((IMapLike) o).toMap();
        // 普通 Bean：反射可读属性名 → 值
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(o.getClass());
        Map<String, Object> ret = new LinkedHashMap<>();
        beanModel.forEachReadableProp(prop -> {
            ret.put(prop.getName(), beanModel.getProperty(o, prop.getName()));
        });
        return ret;
    }
}