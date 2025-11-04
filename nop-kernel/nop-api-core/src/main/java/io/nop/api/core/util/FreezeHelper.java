/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.exceptions.NopException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import static io.nop.api.core.ApiErrors.ARG_OBJ;
import static io.nop.api.core.ApiErrors.ERR_CHECK_OBJ_IS_FROZEN;

public class FreezeHelper {
    public static void checkNotFrozen(IFreezable obj) {
        if (obj.frozen())
            throw new NopException(ERR_CHECK_OBJ_IS_FROZEN).param(ARG_OBJ, obj);
    }

    public static <T> List<T> freezeList(List<T> list, boolean cascade) {
        if (list == null || list == CloneHelper.EMPTY_LIST)
            return list;
        if (cascade) {
            _deepFreezeList(list);
        }
        return Collections.unmodifiableList(list);
    }

    private static <T> void _deepFreezeList(List<T> items) {
        for (int i = 0, n = items.size(); i < n; i++) {
            Object item = items.get(i);
            Object o = _deepFreeze(item);
            if (item != o) {
                items.set(i, (T) o);
            }
        }
    }

    private static <T> void _deepFreezeItems(Collection<T> items) {
        for (T item : items) {
            _deepFreeze(item);
        }
    }

    private static <K, V> void _deepFreezeMap(Map<K, V> map) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            V value = entry.getValue();
            Object o = _deepFreeze(value);
            if (o != value)
                entry.setValue((V) o);
        }
    }

    public static <T> T deepFreeze(T o) {
        return (T) _deepFreeze(o);
    }

    private static Object _deepFreeze(Object o) {
        if (o == null)
            return null;

        if (o instanceof IFreezable) {
            ((IFreezable) o).freeze(true);
            return o;
        } else if (o instanceof List) {
            return freezeList((List) o, true);
        } else if (o instanceof SortedMap) {
            return freezeSortedMap((SortedMap) o, true);
        } else if (o instanceof Map) {
            return freezeMap((Map) o, true);
        } else if (o instanceof SortedSet) {
            return freezeSortedSet((SortedSet) o, true);
        } else if (o instanceof Set) {
            return freezeSet((Set) o, true);
        } else {
            return o;
        }
    }

    public static void freezeObj(IFreezable obj, boolean cascade) {
        if (obj != null)
            obj.freeze(cascade);
    }

    public static <T extends IFreezable> void freezeItems(Collection<T> items, boolean cascade) {
        if (items == null)
            return;
        for (IFreezable item : items) {
            item.freeze(cascade);
        }
    }

    public static void deepFreezeObjects(Collection<?> items) {
        if (items == null)
            return;
        for (Object item : items) {
            _deepFreeze(item);
        }
    }

    public static <T> Set<T> freezeSet(Set<T> set, boolean cascade) {
        if (set == null || set == CloneHelper.EMPTY_SET)
            return set;
        if (cascade)
            _deepFreezeItems(set);
        return Collections.unmodifiableSet(set);
    }

    public static <T> SortedSet<T> freezeSortedSet(SortedSet<T> set, boolean cascade) {
        if (set == null || set == CloneHelper.EMPTY_SET)
            return set;
        if (cascade)
            _deepFreezeItems(set);
        return Collections.unmodifiableSortedSet(set);
    }

    public static <K, V> Map<K, V> freezeMap(Map<K, V> map, boolean cascade) {
        if (map == null || map == CloneHelper.EMPTY_MAP)
            return map;
        if (cascade)
            _deepFreezeMap(map);
        return Collections.unmodifiableMap(map);
    }

    public static <K, V> SortedMap<K, V> freezeSortedMap(SortedMap<K, V> map, boolean cascade) {
        if (map == null || map == CloneHelper.EMPTY_MAP)
            return map;
        if (cascade)
            _deepFreezeMap(map);
        return Collections.unmodifiableSortedMap(map);
    }
}