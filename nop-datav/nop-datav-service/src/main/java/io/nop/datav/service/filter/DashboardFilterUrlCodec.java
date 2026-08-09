package io.nop.datav.service.filter;

import io.nop.api.core.exceptions.NopException;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG;

/**
 * 看板全局筛选 URL 参数序列化/反序列化。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/linkage-design.md} §四 URL 参数同步策略。</p>
 *
 * <p>格式与扁平 key 一致：简单参数 {@code paramName=value}，复合参数
 * {@code paramName.start=v1&paramName.end=v2}。key/value 经 URL 编解码（UTF-8）。</p>
 *
 * <p>序列化仅输出非 null 值（默认值过滤由调用方按需处理，本类只做 null 过滤）。
 * 反序列化接受完整 URL（取 {@code ?} 后部分）或纯 query string。</p>
 */
public final class DashboardFilterUrlCodec {

    private DashboardFilterUrlCodec() {
    }

    /**
     * 将扁平 key 参数值 Map 序列化为 URL query string（不含前导 {@code ?}）。
     *
     * @param values 扁平 key 参数值 Map，允许 null/空
     * @return query string（如 {@code region=East&dateRange.start=2024-01-01}）；空输入返回空串
     */
    public static String toQueryString(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(value.toString()));
            first = false;
        }
        return sb.toString();
    }

    /**
     * 从 URL 或 query string 反序列化为扁平 key 参数值 Map。
     *
     * @param url 完整 URL 或纯 query string，允许 null/空
     * @return 不可变的扁平 key 参数值 Map；空输入返回空 Map；永不为 null
     */
    public static Map<String, Object> parseQueryString(String url) {
        if (url == null || url.isEmpty()) {
            return Collections.emptyMap();
        }
        String query = extractQuery(url);
        if (query.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key;
            String value;
            if (eq < 0) {
                key = decode(pair);
                value = "";
            } else {
                key = decode(pair.substring(0, eq));
                value = decode(pair.substring(eq + 1));
            }
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String extractQuery(String url) {
        int q = url.indexOf('?');
        if (q >= 0) {
            return url.substring(q + 1);
        }
        return url;
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "URL encode failed: " + e.getMessage());
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG)
                    .param("reason", "URL decode failed: " + e.getMessage());
        }
    }
}
