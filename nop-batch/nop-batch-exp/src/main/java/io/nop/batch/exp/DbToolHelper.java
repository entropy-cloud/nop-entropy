package io.nop.batch.exp;

import io.nop.core.lang.json.JsonTool;

import java.util.Map;

/**
 * ExportDbTool/ImportDbTool在启动时会把完整config序列化后输出到INFO日志，
 * 其中包含jdbcConnection的明文password。序列化前必须脱敏，避免数据库口令落入日志文件。
 */
final class DbToolHelper {
    private static final String PASSWORD_MASK = "***";

    private DbToolHelper() {
    }

    /**
     * 序列化config用于日志输出，将jdbcConnection.password替换为掩码。
     * 掩码作用于序列化副本，原config对象保持真实密码供buildDataSource使用。
     */
    static String toMaskedConfigJson(Object config) {
        Object json = JsonTool.beanToJsonObject(config);
        if (json instanceof Map)
            maskJdbcPassword((Map<String, Object>) json);
        return JsonTool.serialize(json, true);
    }

    @SuppressWarnings("unchecked")
    private static void maskJdbcPassword(Map<String, Object> configMap) {
        Object conn = configMap.get("jdbcConnection");
        if (conn instanceof Map)
            ((Map<String, Object>) conn).put("password", PASSWORD_MASK);
    }
}
