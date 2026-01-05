/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JArray;
import io.nop.core.lang.json.JObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.lang.json.delta.DeltaMergeHelper.buildUniqueKey;
import static io.nop.core.lang.json.delta.DeltaMergeHelper.containsUniqueKey;

/**
 * 不依赖schema，直接对任意json进行merge操作。基本规则如下： 1. Map按照key找到匹配项，然后进行合并。如果key以!为前缀，则表示直接覆盖。例如 "!xxx":yyy 表示将xxx属性覆盖为yyy 2.
 * List中的元素如果含有id/name等唯一key属性，则按照key查找到匹配项，然后进行合并，否则执行追加操作。 3. 其他情况直接覆盖
 */
public class JsonMerger {
    private static final JsonMerger _INSTANCE = new JsonMerger();

    public static JsonMerger instance() {
        return _INSTANCE;
    }

    public Object merge(Object objA, Object objB) {
        if (objA == null)
            return objB;

        if (objB == null)
            return null;

        if (objA instanceof Map) {
            if (objB instanceof Map)
                return mergeMap((Map<String, Object>) objA, (Map<String, Object>) objB);
            if (objB instanceof List<?>) {
                if (containsUniqueKey((Map<String, Object>) objA))
                    return mergeList(JArray.singleton(objA), (List<Object>) objB);
            }
            return objB;
        }

        if (objA instanceof List) {
            if (objB instanceof List) {
                return mergeList((List<Object>) objA, (List<Object>) objB);
            }
            if (objB instanceof Map) {
                if (containsUniqueKey((Map<String, Object>) objB))
                    return mergeList((List<Object>) objA, JArray.singleton(objB));
            }
            return objB;
        }

        return objB;
    }

    public Map<String, Object> mergeMap(Map<String, Object> mapA, Map<String, Object> mapB) {
        if (mapB.isEmpty())
            return mapA;

        mapB.remove(CoreConstants.ATTR_X_VIRTUAL);
        mapB.remove(CoreConstants.ATTR_X_INHERIT);

        if (mapA.isEmpty() || shouldRemove(mapA) || shouldReplace(mapB))
            return mapB;

        if (shouldRemove(mapB)) {
            return mapB;
        }

        if (mapA instanceof JObject || mapB instanceof JObject) {
            JObject ret = new JObject();
            if (mapA instanceof JObject) {
                JObject ja = (JObject) mapA;
                ret.setLocation(ja.getLocation());
                ja.forEachEntry((name, vl) -> {
                    addToMap(ret, name, vl);
                });
            } else {
                mapA.forEach((name, value) -> {
                    addToMap(ret, name, value);
                });
            }

            if (mapB instanceof JObject) {
                JObject jb = (JObject) mapB;
                ret.setLocation(jb.getLocation());
                jb.forEachEntry((name, vl) -> {
                    addToMap(ret, name, vl);
                });
            } else {
                mapB.forEach((name, value) -> {
                    addToMap(ret, name, value);
                });
            }

            return ret;
        } else {
            Map<String, Object> ret = new LinkedHashMap<>();
            mapA.forEach((name, value) -> {
                addToMap(ret, name, value);
            });

            mapB.forEach((name, value) -> {
                addToMap(ret, name, value);
            });
            return ret;
        }
    }

    private boolean shouldReplace(Map<String, Object> map) {
        return CoreConstants.OVERRIDE_REPLACE.equals(map.get(CoreConstants.ATTR_X_OVERRIDE));
    }

    private boolean shouldRemove(Map<String, Object> map) {
        return CoreConstants.OVERRIDE_REMOVE.equals(map.get(CoreConstants.ATTR_X_OVERRIDE));
    }

    private boolean isVirtual(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            return ConvertHelper.toPrimitiveBoolean(map.get(CoreConstants.ATTR_X_VIRTUAL));
        }
        return false;
    }

    private void addToMap(Map<String, Object> ret, String name, ValueWithLocation value) {
        Object v = value.getValue();
        if (v instanceof Map && shouldRemove((Map<String, Object>) v))
            return;

        if (name.startsWith("!")) {
            ret.put(name.substring(1), value);
        } else {
            Object oldValue = ret.get(name);
            ret.put(name, ValueWithLocation.of(value.getLocation(), merge(oldValue, v)));
        }
    }

    private void addToMap(Map<String, Object> ret, String name, Object value) {
        if (value instanceof Map && shouldRemove((Map<String, Object>) value))
            return;

        if (name.startsWith("!")) {
            ret.put(name.substring(1), value);
        } else {
            Object oldValue = ret.get(name);
            ret.put(name, merge(oldValue, value));
        }
    }

    public static Object mergeItems(List<Object> items) {
        if (items == null || items.isEmpty())
            return null;

        JsonMerger merger = JsonMerger.instance();
        Object item = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            item = merger.merge(item, items.get(i));
        }
        return item;
    }

    /**
     * List<Map>按照元素的唯一key进行合并
     */
    public List<Object> mergeList(List<Object> listA, List<Object> listB) {
        if (listA.isEmpty())
            return listB;

        if (listB.isEmpty())
            return listA;

        // 简化处理，要求基础列表中所有元素都必须有id，这样才能够实现稳定的合并
        if (DeltaJsonNormalizer.getListKind(listA) != DeltaListKind.ALL_WITH_ID)
            return listB;

        DeltaListKind bKind = DeltaJsonNormalizer.getListKind(listB);

        if (bKind == DeltaListKind.NONE_WITH_ID) {
            return CollectionHelper.concatList(listA, listB);
        }

        // boolean aChanged = DeltaJsonNormalizer.normalizeKeyedList(listA, "a");

        Map<Pair<String, String>, Integer> map = new HashMap<>();
        for (int i = 0, n = listA.size(); i < n; i++) {
            Object o = listA.get(i);
            if (o instanceof Map) {
                Pair<String, String> key = buildUniqueKey((Map<String, Object>) o);
                if (key != null) {
                    map.put(key, i);
                }
            }
        }

        int[] bIndexes = new int[listB.size()];
        for (int i = 0, n = listB.size(); i < n; i++) {
            Object o = listB.get(i);
            bIndexes[i] = -1;
            if (o instanceof Map) {
                Pair<String, String> key = buildUniqueKey((Map<String, Object>) o);
                if (key != null) {
                    Integer aIndex = map.get(key);
                    if (aIndex != null) {
                        bIndexes[i] = aIndex;
                    }
                }
            }
        }

        List<DeltaMergeHelper.MatchData> matchList = DeltaMergeHelper.mergeList(listA.size(), bIndexes);
        if (listA instanceof JArray && listB instanceof JArray) {
            JArray ret = new JArray(matchList.size());
            JArray ja = (JArray) listA;
            JArray jb = (JArray) listB;
            ret.setLocation(jb.getLocation());
            for (DeltaMergeHelper.MatchData matchData : matchList) {
                if (matchData.aIndex >= 0 && matchData.bIndex >= 0) {
                    ret.add(mergeVl(ja.getLocValue(matchData.aIndex), jb.getLocValue(matchData.bIndex)));
                } else if (matchData.aIndex >= 0) {
                    ret.add(ja.getLocValue(matchData.aIndex));
                } else {
                    ValueWithLocation vl = jb.getLocValue(matchData.bIndex);
                    if (!isVirtual(vl.getValue())) {
                        ret.add(vl);
                    }
                }
            }
            // if (aChanged) {
            // DeltaJsonNormalizer.removeWrap(ret);
            // }
            return ret;
        } else {
            List<Object> ret = new ArrayList<>(matchList.size());
            for (DeltaMergeHelper.MatchData matchData : matchList) {
                if (matchData.aIndex >= 0 && matchData.bIndex >= 0) {
                    ret.add(merge(listA.get(matchData.aIndex), listB.get(matchData.bIndex)));
                } else if (matchData.aIndex >= 0) {
                    ret.add(listA.get(matchData.aIndex));
                } else {
                    Object vl = listB.get(matchData.bIndex);
                    if (!isVirtual(vl)) {
                        ret.add(vl);
                    }
                }
            }
            // if (aChanged) {
            // DeltaJsonNormalizer.removeWrap(ret);
            // }
            return ret;
        }
    }

    private ValueWithLocation mergeVl(ValueWithLocation v1, ValueWithLocation v2) {
        if (v1 == null)
            return v2;
        if (v2 == null)
            return v1;
        return ValueWithLocation.of(v2.getLocation(), merge(v1.getValue(), v2.getValue()));
    }
}