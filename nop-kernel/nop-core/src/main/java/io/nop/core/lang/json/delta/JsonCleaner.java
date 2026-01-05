/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.objects.Pair;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.utils.JsonVisitState;
import io.nop.core.lang.json.utils.SourceLocationHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static io.nop.core.CoreConstants.ATTR_VALUE;
import static io.nop.core.CoreErrors.ARG_JSON_PATH;
import static io.nop.core.CoreErrors.ERR_DELTA_MERGE_NODE_NOT_INHERIT;

public class JsonCleaner {
    private static final JsonCleaner _INSTANCE = new JsonCleaner();

    public static JsonCleaner instance() {
        return _INSTANCE;
    }

    public void clean(Object obj, BiPredicate<String, Object> predicate) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                String name = entry.getKey();
                Object value = entry.getValue();

                // 如果是map或者list，则先处理子节点
                if (value != null) {
                    clean(value, predicate);
                }

                // 空的Map和null值都可能被删除
                if (predicate.test(name, value)) {
                    it.remove();
                }
            }
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0, n = list.size(); i < n; i++) {
                Object v = list.get(i);
                clean(v, predicate);
            }
        }
    }

    public void cleanDelta(Object obj) {
        cleanDelta(obj, new JsonVisitState(obj));
    }

    /**
     * 删除所有x名字空间的属性，确保最后返回的json结构中不包含为差量计算额外添加的信息。
     */
    private void cleanDelta(Object obj, JsonVisitState state) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            List<Pair<String, Object>> replacedItems = null;

            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                String name = entry.getKey();
                Object value = entry.getValue();

                state.enter(name);
                try {
                    if (CoreConstants.ATTR_X_INHERIT.equals(name))
                        throw new NopException(ERR_DELTA_MERGE_NODE_NOT_INHERIT)
                                .loc(SourceLocationHelper.getPropLocation(obj, name))
                                .param(ARG_JSON_PATH, state.getJsonPath());

                    // 如果标记了x:abstract，则表示此节点没有被继承，因此需要被删除
                    if (shouldRemove(value)) {
                        it.remove();
                        continue;
                    }

                    if (name.startsWith(CoreConstants.NAMESPACE_X_PREFIX)) {
                        it.remove();
                        continue;
                    }

                    if (name.startsWith("!")) {
                        cleanDelta(value, state);
                        // "!xxx": "sss" 表示将强制覆盖 xxx属性的值为sss
                        if (replacedItems == null)
                            replacedItems = new ArrayList<>();
                        replacedItems.add(Pair.of(name.substring(1), value));
                        it.remove();
                    } else {
                        cleanDelta(value, state);
                    }
                } finally {
                    state.leave();
                }
            }

            if (replacedItems != null) {
                for (Pair<String, Object> pair : replacedItems) {
                    map.put(pair.getKey(), pair.getValue());
                }
            }
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0, n = list.size(); i < n; i++) {
                Object v = list.get(i);
                state.enter(i);
                try {
                    if (shouldRemove(v)) {
                        list.remove(i);
                        i--;
                        n--;
                    } else if (v instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) v;
                        if (DeltaJsonNormalizer.isWrapValueMap(map)) {
                            v = map.get(ATTR_VALUE);
                            list.set(i, v);
                        }
                        cleanDelta(v);
                    } else {
                        cleanDelta(v);
                    }
                } finally {
                    state.leave();
                }
            }
        }
    }

    public static boolean shouldRemove(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (ConvertHelper.toPrimitiveBoolean(map.get(CoreConstants.ATTR_X_ABSTRACT)))
                return true;

            if (ConvertHelper.toPrimitiveBoolean(map.get(CoreConstants.ATTR_X_VIRTUAL)))
                return true;

            if (CoreConstants.OVERRIDE_REMOVE.equals(map.get(CoreConstants.ATTR_X_OVERRIDE)))
                return true;
        }
        return false;
    }

    public static void changeNamePrefix(Object obj, String oldPrefix, String newPrefix) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();

            Map<String, Object> changed = null;

            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                String name = entry.getKey();
                Object value = entry.getValue();

                if (name.startsWith(oldPrefix)) {
                    if (changed == null) {
                        changed = new LinkedHashMap<>();
                    }
                    changed.put(newPrefix + name.substring(oldPrefix.length()), value);
                    it.remove();
                }

                // 如果是map或者list，则先处理子节点
                if (value != null) {
                    changeNamePrefix(value, oldPrefix, newPrefix);
                }
            }

            if (changed != null)
                map.putAll(changed);
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0, n = list.size(); i < n; i++) {
                Object v = list.get(i);
                changeNamePrefix(v, oldPrefix, newPrefix);
            }
        }
    }
}
