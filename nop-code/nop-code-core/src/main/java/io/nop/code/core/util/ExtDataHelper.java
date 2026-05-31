package io.nop.code.core.util;

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
}
