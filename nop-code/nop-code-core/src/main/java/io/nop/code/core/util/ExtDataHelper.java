package io.nop.code.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.core.lang.json.JsonTool;
public class ExtDataHelper {

    public static String extractFilePath(String extData) {
        if (extData == null || extData.isEmpty()) {
            return null;
        }
        if (!extData.contains("filePath")) {
            return null;
        }
        try {
            Object parsed = JsonTool.parseNonStrict(extData);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                Object filePath = map.get("filePath");
                if (filePath != null) {
                    return filePath.toString();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Retrieve annotation short names stored in extData under the "annotations" key.
     *
     * @param extData JSON string, may be null
     * @return list of annotation short names, never null
     */
    @SuppressWarnings("unchecked")
    public static List<String> getAnnotations(String extData) {
        if (extData == null || extData.isEmpty() || !extData.contains("annotations")) {
            return Collections.emptyList();
        }
        try {
            Object parsed = JsonTool.parseNonStrict(extData);
            if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;
                Object annotations = map.get("annotations");
                if (annotations instanceof List) {
                    List<String> result = new ArrayList<>();
                    for (Object item : (List<?>) annotations) {
                        if (item != null) {
                            result.add(item.toString());
                        }
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return Collections.emptyList();
    }

    /**
     * Store annotation short names in extData under the "annotations" key.
     * Preserves existing keys in extData.
     *
     * @param extData     existing JSON string, may be null
     * @param annotations list of annotation short names to store
     * @return updated JSON string
     */
    public static String setAnnotations(String extData, List<String> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return extData;
        }
        Map<String, Object> map = parseToMap(extData);
        map.put("annotations", annotations);
        return JsonTool.stringify(map);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseToMap(String extData) {
        if (extData == null || extData.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = JsonTool.parseNonStrict(extData);
            if (parsed instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) parsed);
            }
        } catch (Exception e) {
            // fall through
        }
        return new LinkedHashMap<>();
    }
}
