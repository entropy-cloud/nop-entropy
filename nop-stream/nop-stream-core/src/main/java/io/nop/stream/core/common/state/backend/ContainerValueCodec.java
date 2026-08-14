/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.util.ClassNameValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1-21-01: recursive element-type-aware encoding/decoding for MapState container
 * values (List/Map, nested included) across the JSON checkpoint storage layer.
 *
 * <p>JSON persistence round-trips a {@code List<Event>} value as a JSON array whose
 * elements come back as {@code LinkedHashMap}s: restore previously kept them (the raw
 * {@code List.class} declared type short-circuits the type check) and CEP crashed on
 * the first typed access (ClassCastException / silently wrong condition evaluation).
 * The restore side has no element-type source (raw value class, type-less JSON, null
 * input serializer), so the type source is established at the SNAPSHOT side
 * (Decision B: type-aware JSON via a recursive wrapper) which both the Memory and the
 * RocksDB backends share.
 *
 * <p><b>Encoding</b> (Memory snapshot / RocksDB put): container values are wrapped as
 * {@code {nopContainerType: list|map, nopElementType?, nopItems|nopEntries}} with the
 * first element's class recorded per level; nested containers are wrapped recursively.
 * Empty containers carry no element type (nothing to re-materialize). Non-container
 * values pass through unchanged — the stored JSON format is unchanged for them.
 *
 * <p><b>Decoding</b> (Memory restore / RocksDB get/restore): wrapped values are
 * re-materialized element by element (JSON-native maps → beans via
 * {@code JsonTool.parseBeanFromText}, scalars re-typed, nested wrappers recursed).
 * Unwrapped legacy containers carry no element type info — per No-Silent-No-Op
 * (guide rule #24) the degradation is logged with {@code LOG.warn} instead of
 * silently returning a corrupt container.
 */
public final class ContainerValueCodec {

    private static final Logger LOG = LoggerFactory.getLogger(ContainerValueCodec.class);

    public static final String CONTAINER_TYPE_KEY = "nopContainerType";
    public static final String ELEMENT_TYPE_KEY = "nopElementType";
    public static final String KEY_ELEMENT_TYPE_KEY = "nopKeyElementType";
    public static final String VALUE_ELEMENT_TYPE_KEY = "nopValueElementType";
    public static final String ITEMS_KEY = "nopItems";
    public static final String ENTRIES_KEY = "nopEntries";

    private static final String LIST_CONTAINER = "list";
    private static final String MAP_CONTAINER = "map";

    private ContainerValueCodec() {
    }

    /**
     * Whether the declared value type is a container (List/Map/Collection) whose
     * inner elements need type-aware handling on the JSON storage layer.
     */
    public static boolean isContainerType(Class<?> type) {
        return type != null && (List.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type));
    }

    /**
     * Wraps a container value (List/Map, nested included) with per-level element type
     * info so the JSON storage layer can restore inner element types. The element type
     * source is the live container's first element class. Non-container values pass
     * through unchanged.
     */
    @SuppressWarnings("unchecked")
    public static Object encode(Object value) {
        if (value instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put(CONTAINER_TYPE_KEY, MAP_CONTAINER);
            List<Object> entries = new ArrayList<>(map.size());
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                List<Object> pair = new ArrayList<>(2);
                pair.add(e.getKey());
                pair.add(encode(e.getValue()));
                entries.add(pair);
            }
            if (!entries.isEmpty()) {
                Map.Entry<Object, Object> first = map.entrySet().iterator().next();
                if (first.getKey() != null) {
                    wrapper.put(KEY_ELEMENT_TYPE_KEY, first.getKey().getClass().getName());
                }
                if (first.getValue() != null) {
                    wrapper.put(VALUE_ELEMENT_TYPE_KEY, first.getValue().getClass().getName());
                }
            }
            wrapper.put(ENTRIES_KEY, entries);
            return wrapper;
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put(CONTAINER_TYPE_KEY, LIST_CONTAINER);
            List<Object> items = new ArrayList<>(list.size());
            for (Object item : list) {
                items.add(encode(item));
            }
            if (!items.isEmpty()) {
                wrapper.put(ELEMENT_TYPE_KEY, list.get(0).getClass().getName());
            }
            wrapper.put(ITEMS_KEY, items);
            return wrapper;
        }
        return value;
    }

    /**
     * Decodes a container value into its typed form. Wrapped values (produced by
     * {@link #encode}) are re-materialized recursively from the recorded element
     * types; unwrapped legacy containers carry no element type info and degrade with
     * a {@code LOG.warn} (observability over silent corruption, guide rule #24).
     * Non-container values pass through unchanged.
     *
     * @param obj         the value as it appears after the storage-layer round trip
     *                    (live objects when no JSON round trip happened, JSON-native
     *                    maps/lists/scalars otherwise)
     * @param declaredType the state descriptor's declared value type
     * @param logContext  state name / backend context for warning messages
     */
    @SuppressWarnings("unchecked")
    public static Object decode(Object obj, Class<?> declaredType, String logContext) {
        if (isWrapperMap(obj)) {
            return decodeWrapper((Map<String, Object>) obj, logContext);
        }
        if (obj instanceof List || obj instanceof Map) {
            // Unwrapped container: legacy snapshot (or a value that never went through
            // encode). JSON-native inner elements mean the type info was lost — log the
            // degradation instead of silently returning a corrupt container.
            if (hasJsonNativeContainerElements(obj)) {
                LOG.warn("Restored container value for {} has JSON-native elements without element type "
                        + "info; inner element types cannot be recovered (legacy snapshot?)", logContext);
            }
            return obj;
        }
        return obj;
    }

    private static boolean hasJsonNativeContainerElements(Object obj) {
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty()) {
                return false;
            }
            Object first = list.get(0);
            return first instanceof Map || first instanceof List;
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.isEmpty()) {
                return false;
            }
            Object firstValue = map.values().iterator().next();
            return firstValue instanceof Map || firstValue instanceof List;
        }
        return false;
    }

    private static boolean isWrapperMap(Object obj) {
        if (!(obj instanceof Map)) {
            return false;
        }
        Map<?, ?> map = (Map<?, ?>) obj;
        return LIST_CONTAINER.equals(map.get(CONTAINER_TYPE_KEY))
                || MAP_CONTAINER.equals(map.get(CONTAINER_TYPE_KEY));
    }

    @SuppressWarnings("unchecked")
    private static Object decodeWrapper(Map<String, Object> wrapper, String logContext) {
        String containerType = (String) wrapper.get(CONTAINER_TYPE_KEY);
        if (LIST_CONTAINER.equals(containerType)) {
            Class<?> elementType = resolveType((String) wrapper.get(ELEMENT_TYPE_KEY));
            Object itemsObj = wrapper.get(ITEMS_KEY);
            if (itemsObj instanceof List) {
                List<Object> result = new ArrayList<>();
                for (Object item : (List<?>) itemsObj) {
                    result.add(rematerializeElement(item, elementType, logContext));
                }
                return result;
            }
            LOG.warn("Container value for {} has a malformed '{}' wrapper (missing {}); keeping JSON form",
                    logContext, LIST_CONTAINER, ITEMS_KEY);
            return wrapper;
        }
        if (MAP_CONTAINER.equals(containerType)) {
            Class<?> keyElementType = resolveType((String) wrapper.get(KEY_ELEMENT_TYPE_KEY));
            Class<?> valueElementType = resolveType((String) wrapper.get(VALUE_ELEMENT_TYPE_KEY));
            Object entriesObj = wrapper.get(ENTRIES_KEY);
            if (entriesObj instanceof List) {
                Map<Object, Object> result = new LinkedHashMap<>();
                for (Object entryObj : (List<?>) entriesObj) {
                    if (!(entryObj instanceof List)) {
                        continue;
                    }
                    List<?> pair = (List<?>) entryObj;
                    if (pair.size() < 2) {
                        continue;
                    }
                    Object k = rematerializeElement(pair.get(0), keyElementType, logContext);
                    Object v = rematerializeElement(pair.get(1), valueElementType, logContext);
                    result.put(k, v);
                }
                return result;
            }
            LOG.warn("Container value for {} has a malformed '{}' wrapper (missing {}); keeping JSON form",
                    logContext, MAP_CONTAINER, ENTRIES_KEY);
            return wrapper;
        }
        LOG.warn("Container value for {} has an unknown wrapper type '{}'; keeping JSON form",
                logContext, containerType);
        return wrapper;
    }

    /**
     * Re-materializes one container element to the recorded element type. Nested
     * wrappers recurse; JSON-native containers/scalars are re-typed via
     * {@code JsonTool.parseBeanFromText}; values already of the recorded type pass
     * through. Failures degrade with a warning (best-effort, observable) instead of
     * silently keeping a wrong-typed element.
     */
    @SuppressWarnings("unchecked")
    private static Object rematerializeElement(Object item, Class<?> elementType, String logContext) {
        if (item == null) {
            return null;
        }
        if (isWrapperMap(item)) {
            return decodeWrapper((Map<String, Object>) item, logContext);
        }
        if (elementType == null || elementType == Object.class) {
            if (item instanceof Map || item instanceof List) {
                LOG.warn("Container element for {} has no element type info; keeping JSON-native form",
                        logContext);
            }
            return item;
        }
        if (elementType.isInstance(item)) {
            return item;
        }
        String json = JsonTool.serialize(item, false);
        try {
            Object rematerialized = JsonTool.parseBeanFromText(json, elementType);
            if (rematerialized != null && elementType.isInstance(rematerialized)) {
                return rematerialized;
            }
            LOG.warn("Re-materialization of container element {} as {} for {} produced {}; keeping JSON-native form",
                    json, elementType.getName(), logContext,
                    rematerialized != null ? rematerialized.getClass().getName() : "null");
        } catch (Exception e) {
            LOG.warn("Failed to re-materialize container element {} as {} for {}; keeping JSON-native form",
                    json, elementType.getName(), logContext, e);
        }
        return item;
    }

    private static Class<?> resolveType(String typeName) {
        if (typeName == null) {
            return null;
        }
        try {
            ClassNameValidator.validateClassName(typeName);
            return Class.forName(typeName);
        } catch (Exception e) {
            LOG.warn("Container element type class not found: {}", typeName);
            return null;
        }
    }
}
