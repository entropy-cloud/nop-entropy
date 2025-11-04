package io.nop.commons.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提供JavaScript中的Map相关函数
 */
public class MapFunctions {
    
    /**
     * 在JavaScript的Map对象中设置一个键值对
     *
     * @param jsMap JavaScript的Map对象
     * @param key   键
     * @param value 值
     */
    public static <T> void set(Map<T, Object> jsMap, T key, Object value) {
        jsMap.put(key, value);
    }

    /**
     * 从JavaScript的Map对象中删除一个键值对
     *
     * @param jsMap JavaScript的Map对象
     * @param key   键
     */
    public static <T> void delete(Map<T, ?> jsMap, T key) {
        jsMap.remove(key);
    }

    /**
     * 检查JavaScript的Map对象中是否包含某个键
     *
     * @param jsMap JavaScript的Map对象
     * @param key   键
     * @return 如果包含则返回true，否则返回false
     */
    public static <T> boolean has(Map<T, ?> jsMap, T key) {
        return jsMap.containsKey(key);
    }

    /**
     * 获取JavaScript的Map对象中的所有键
     *
     * @param jsMap JavaScript的Map对象
     * @return 所有键的集合
     */
    public static <T> List<T> keys(Map<T, ?> jsMap) {
        return new ArrayList<>(jsMap.keySet());
    }

    /**
     * 获取JavaScript的Map对象中的所有键值对
     *
     * @param jsMap JavaScript的Map对象
     * @return 所有键值对的集合
     */
    public static <T, R> List<Map.Entry<T, R>> entries(Map<T, R> jsMap) {
        return new ArrayList<>(jsMap.entrySet());
    }
}