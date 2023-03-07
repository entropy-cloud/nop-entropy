/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.lang.Undefined;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.utils.JsonMatchHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nop.core.lang.json.delta.DeltaMergeHelper.buildUniqueKey;

/**
 * 返回 delta = a - b 的结果，即 a = b + delta，可以根据返回值delta和基础对象b重建出对象a
 */
public class JsonDiffer {
    private static final JsonDiffer INSTANCE = new JsonDiffer();

    public static JsonDiffer instance() {
        return INSTANCE;
    }

    /**
     * a是最终合并的结果，从a中除去基础对象b中已经具有的部分
     *
     * @param a 最终合并的结果
     * @param b 基础对象，例如x:gen-extends生成的部分
     * @return 差量部分. delta = a - b
     */
    public Map<String, Object> diffMap(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> ret = new LinkedHashMap<>();
        Pair<String, String> key = buildUniqueKey(b);

        boolean bVirtual = false;

        for (Map.Entry<String, Object> entry : a.entrySet()) {
            String name = entry.getKey();
            if (name.equals(CoreConstants.ATTR_X_VIRTUAL))
                continue;

            // 先保留key
            if (key != null && key.getFirst().equals(name)) {
                ret.put(name, key.getValue());
                continue;
            }

            Object aValue = entry.getValue();
            Object bValue = b.get(name);

            Object value = diffValue(aValue, bValue);
            if (value != Undefined.undefined && !isIgnorableForMapValue(bValue, value)) {
                ret.put(name, value);
            } else {
                bVirtual = true;
            }
        }

        for (Map.Entry<String, Object> entry : b.entrySet()) {
            String name = entry.getKey();

            // b中有，而合并后的结果a没有，则差量只能是null
            if (!a.containsKey(name))
                ret.put(name, null);
        }

        // virtual表示忽略了a中存在的值
        if (bVirtual && !ret.containsKey(CoreConstants.ATTR_X_INHERIT)) {
            ret.put(CoreConstants.ATTR_X_VIRTUAL, true);
        }

        return ret;
    }

    Object diffValue(Object aValue, Object bValue) {
        if (bValue instanceof Map<?, ?>) {
            Map<String, Object> bMap = (Map<String, Object>) bValue;
            if (aValue instanceof Map<?, ?>) {
                return diffMap((Map<String, Object>) aValue, bMap);
            }

            Pair<String, String> bKey = buildUniqueKey(bMap);
            if (bKey == null) {
                // 没有key设置，且合并后的结果也不是Map，则必然是被覆盖
                return aValue;
            }

            if (aValue instanceof List<?>) {
                List<?> aList = (List<?>) aValue;
                List<Pair<String, String>> aKeys = getListKeys(aList);
                // 合并后为List，原先为Map。先查找List中是否有相同的ey
                int index = aKeys.indexOf(bKey);
                if (index < 0) {
                    // 没有找到，则直接覆盖
                    return aValue;
                } else {
                    // 合并匹配节点，保留其他节点
                    List<Object> list = new ArrayList<>(aKeys.size());
                    for (int i = 0, n = aKeys.size(); i < n; i++) {
                        if (i != index) {
                            list.add(aList.get(i));
                        } else {
                            Object diff = diffMap((Map<String, Object>) aList.get(i), bMap);
                            if (diff == Undefined.undefined || isKeyMap(diff))
                                return Undefined.undefined;
                            list.add(diff);
                        }
                    }
                    return list;
                }
            } else {
                // aValue不是Map或者List，则直接覆盖
                return aValue;
            }
        } else if (bValue instanceof List<?>) {
            List<?> bList = (List<?>) bValue;

            if (aValue instanceof List<?>) {
                List<?> aList = (List<?>) aValue;
                return diffList(aList, bList);
            } else if (aValue instanceof Map<?, ?>) {
                // 合并结果为Map，而基础对象为List
                Map<String, Object> aMap = (Map<String, Object>) aValue;
                Pair<String, String> aKey = buildUniqueKey(aMap);
                if (aKey == null) {
                    // 没有key，则表示覆盖
                    aMap.put(CoreConstants.ATTR_X_OVERRIDE, CoreConstants.OVERRIDE_REPLACE);
                    return aMap;
                } else {
                    List<Pair<String, String>> bKeys = getListKeys(bList);
                    if (bKeys.contains(null))
                        return aMap;

                    // 如果合并后的key不在原列表中，则实际为覆盖
                    int index = bKeys.indexOf(aKey);
                    if (index < 0) {
                        aMap.put(CoreConstants.ATTR_X_OVERRIDE, CoreConstants.OVERRIDE_REPLACE);
                        return aMap;
                    } else {
                        // 删除其他元素，计算匹配元素的差量
                        List<Object> list = new ArrayList<>(bKeys.size());
                        for (int i = 0, n = bKeys.size(); i < n; i++) {
                            if (i == index) {
                                list.add(diffMap(aMap, (Map<String, Object>) bList.get(i)));
                            } else {
                                list.add(buildRemove(bKeys, i));
                            }
                        }
                        return list;
                    }
                }
            } else {
                return aValue;
            }
        } else {
            // 忽略a中已经存在的属性
            if (Objects.equals(aValue, bValue)) {
                return Undefined.undefined;
            } else {
                return aValue;
            }
        }
    }

    public Object diffList(List<?> aList, List<?> bList) {
        if (aList.size() == bList.size()) {
            // 如果完全相等，则取差后为undefined，表示没有差异
            if (JsonMatchHelper.deepEqualsList(aList, bList, Objects::equals)) {
                return Undefined.undefined;
            }
        }

        List<Pair<String, String>> bKeys = getListKeys(bList);
        List<Pair<String, String>> aKeys = getListKeys(aList);

        // 如果不能完全按照key进行匹配，则认为是覆盖
        if (bKeys.contains(null))
            return aList;

        Map<Pair<String, String>, Integer> bIndexMap = buildIndexMap(bKeys);

        int[] aIndexes = new int[aList.size()];

        for (int i = 0, n = aKeys.size(); i < n; i++) {
            Pair<String, String> aKey = aKeys.get(i);
            if (aKey != null) {
                Integer bIndex = bIndexMap.get(aKey);
                if (bIndex != null) {
                    aIndexes[i] = bIndex;
                } else {
                    aIndexes[i] = -2;
                }
            } else {
                aIndexes[i] = -1;
            }
        }

        // bIndexes为基础对象下表，而aIndexes为合并后对象下表
        List<DeltaMergeHelper.MatchData> matchedList = DeltaMergeHelper.diff(aIndexes, bKeys.size());
        List<Object> ret = new ArrayList<>();
        for (DeltaMergeHelper.MatchData matched : matchedList) {
            if (matched.aIndex >= 0 && matched.bIndex >= 0) {
                Object value = diffValue(aList.get(matched.aIndex), bList.get(matched.bIndex));
                if (value != Undefined.undefined) {
                    ret.add(value);
                }
            } else if (matched.aIndex >= 0) {
                // 在最终结果中存在，在基础节点中不存在
                ret.add(aList.get(matched.aIndex));
            } else if (matched.bIndex >= 0) {
                // 在基础节点中存在，在最终结果中不存在
                ret.add(buildRemove(bKeys.get(matched.bIndex)));
            }
        }
        simplify(matchedList, ret);

        if (isAllIgnorable(ret)) {
            return Undefined.undefined;
        }
        return ret;
    }

    // 如果合并后出现连续的仅起占位作用的b的元素，则可以只保留第一个和最后一个。因为合并规则是以b的元素为定位基点，插入到定位元素的后面
    void simplify(List<DeltaMergeHelper.MatchData> matchedList, List<Object> ret) {
        if (ret.size() <= 2)
            return;

        DeltaMergeHelper.MatchData prevMatched = null;
        int removed = 0;
        boolean first = true;
        for (int i = 0, n = ret.size(); i < n; i++) {
            DeltaMergeHelper.MatchData matched = matchedList.get(i + removed);
            boolean b = matched.aIndex >= 0 && matched.bIndex >= 0;
            if (b) {
                if (prevMatched != null) {
                    if (prevMatched.bIndex == matched.bIndex - 1) {
                        if (first && prevMatched.bIndex != 0) {
                            prevMatched = matched;
                            first = false;
                            continue;
                        }
                        first = false;
                        ret.remove(i - 1);
                        i--;
                        n--;
                        removed++;
                    }
                }
                prevMatched = matched;
                continue;
            }
            prevMatched = null;
        }
    }

    boolean isAllIgnorable(List<?> list) {
        for (Object o : list) {
            if (!isKeyMap(o))
                return false;
        }
        return true;
    }

    boolean isKeyMap(Object item) {
        if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            return map.size() == 1 || map.size() == 2 && map.containsKey(CoreConstants.ATTR_X_VIRTUAL);
        }
        return false;
    }

    Map<String, Object> buildRemove(List<Pair<String, String>> keys, int index) {
        Pair<String, String> key = keys.get(index);
        if (key != null) {
            return buildRemove(key);
        } else {
            // 当仅在a中出现，不在b中出现时，需要具有key才能被删除
            Map<String, Object> map = DeltaJsonNormalizer.buildRemove(index);
            return map;
        }
    }

    Map<Pair<String, String>, Integer> buildIndexMap(List<Pair<String, String>> keys) {
        Map<Pair<String, String>, Integer> ret = CollectionHelper.newHashMap(keys.size());
        for (int i = 0, n = keys.size(); i < n; i++) {
            Pair<String, String> key = keys.get(i);
            if (key != null) {
                ret.put(key, i);
            }
        }
        return ret;
    }

    boolean isIgnorableForMapValue(Object bValue, Object diffValue) {
        if (bValue instanceof Map) {
            if (diffValue instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) diffValue;
                if (map.isEmpty())
                    return true;
                if (map.size() == 1) {
                    if (map.containsKey(CoreConstants.ATTR_X_VIRTUAL))
                        return true;
                    Pair<String, String> key = buildUniqueKey(map);
                    if (key != null) {
                        Map<String, Object> bMap = (Map<String, Object>) bValue;
                        return stringEquals(bMap.get(key.getKey()), key.getValue());
                    }
                } else if (map.size() == 2 && ConvertHelper.toPrimitiveBoolean(map.get(CoreConstants.ATTR_X_VIRTUAL))) {
                    Pair<String, String> key = buildUniqueKey(map);
                    if (key != null) {
                        Map<String, Object> bMap = (Map<String, Object>) bValue;
                        return stringEquals(bMap.get(key.getKey()), key.getValue());
                    }
                }
            }
        }
        return false;
    }

    boolean stringEquals(Object v1, Object v2) {
        if (Objects.equals(v1, v2))
            return true;
        if (v1 == null || v2 == null)
            return false;
        return v2.toString().equals(v1.toString());
    }

    Map<String, Object> buildRemove(Pair<String, String> key) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key.getKey(), key.getValue());
        map.put(CoreConstants.ATTR_X_OVERRIDE, CoreConstants.OVERRIDE_REMOVE);
        return map;
    }

    List<Pair<String, String>> getListKeys(List<?> list) {
        List<Pair<String, String>> keys = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                Map<String, Object> map = (Map<String, Object>) item;
                Pair<String, String> key = buildUniqueKey(map);
                keys.add(key);
            } else {
                keys.add(null);
            }
        }
        return keys;
    }
}