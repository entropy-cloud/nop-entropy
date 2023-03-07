/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.delta;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreConstants.ATTR_VALUE;
import static io.nop.core.CoreConstants.ATTR_X_ID;
import static io.nop.core.CoreConstants.ATTR_X_OVERRIDE;
import static io.nop.core.CoreConstants.DUMMY_TAG_NAME;
import static io.nop.core.CoreConstants.OVERRIDE_REMOVE;
import static io.nop.core.CoreConstants.PROP_TYPE;
import static io.nop.core.lang.json.delta.DeltaMergeHelper.containsUniqueKey;

/**
 * 为了精确标记节点的删除关系，需要所有结构都转换为Map，并且为其增加唯一表示，例如x:name
 */
public class DeltaJsonNormalizer {
    private static final List<String> VALUE_ATTRS = Arrays.asList(PROP_TYPE, ATTR_VALUE, ATTR_X_ID);

    /**
     * 列表中的元素如果不是Map，则需要包装为Map结构。type属性为 _, 并且只有x:name属性和value属性
     */
    public static boolean isWrapValueMap(Map<String, Object> map) {
        if (map.size() != 3)
            return false;

        if (!DUMMY_TAG_NAME.equals(map.get(PROP_TYPE)))
            return false;

        if (!VALUE_ATTRS.containsAll(map.keySet()))
            return false;

        return true;
    }

    /**
     * 是否所有元素都是Map，且所有节点都具有id
     */
    public static DeltaListKind getListKind(List<Object> list) {
        int idCount = 0;
        for (Object item : list) {
            if (!(item instanceof Map))
                continue;

            if (containsUniqueKey((Map<String, Object>) item))
                idCount++;
        }

        if (idCount == 0)
            return DeltaListKind.NONE_WITH_ID;
        return idCount == list.size() ? DeltaListKind.ALL_WITH_ID : DeltaListKind.SOME_WITH_ID;
    }

    static final String GEN_KEY_PREFIX = "$i$";

    /**
     * 确保每个元素都具有唯一id
     */
    public static boolean normalizeKeyedList(List<Object> list, String key) {
        boolean bChanged = false;
        for (int i = 0, n = list.size(); i < n; i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                if (!containsUniqueKey(map)) {
                    map.put(ATTR_X_ID, GEN_KEY_PREFIX + key + i);
                    bChanged = true;
                }
            } else {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put(ATTR_X_ID, GEN_KEY_PREFIX + key + i);
                map.put(PROP_TYPE, DUMMY_TAG_NAME);
                map.put(ATTR_VALUE, item);
                list.set(i, map);
                bChanged = true;
            }
        }
        return bChanged;
    }

    public static Map<String, Object> buildRemove(int index) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(ATTR_X_ID, GEN_KEY_PREFIX + "a" + index);
        map.put(ATTR_X_OVERRIDE, OVERRIDE_REMOVE);
        return map;
    }

    public static void removeWrap(List<Object> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            Object item = list.get(i);
            if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                if (isWrapValueMap(map)) {
                    list.set(i, map.get(ATTR_VALUE));
                }
            }
        }
    }
}