package io.nop.code.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.core.lang.json.JsonTool;

public class ExtDataHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ExtDataHelper.class);

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
            LOG.debug("Failed to parse extData JSON", e);
        }
        return null;
    }

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
            LOG.debug("Failed to parse extData JSON for annotations", e);
        }
        return Collections.emptyList();
    }

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
            LOG.debug("Failed to parse extData JSON in parseToMap", e);
        }
        return new LinkedHashMap<>();
    }
}
