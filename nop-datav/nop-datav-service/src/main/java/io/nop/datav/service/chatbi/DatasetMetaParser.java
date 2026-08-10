package io.nop.datav.service.chatbi;

import io.nop.core.lang.json.JsonTool;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * dsMeta 解析共享 helper（裁定 N）。
 *
 * <p>从 {@link NopReportDataset#getDsMeta()} JSON 文本中提取字段名集合，供 generate-dashboard executor
 * 的 fieldMapping 校验与 describe executor 的字段展示共用。避免两处各写一份解析逻辑。</p>
 *
 * <p><b>字段名 key 约定</b>（dsMeta 三种形式，字段名提取规则）：
 * <ol>
 *   <li>{@code {"fields": [{"name": "region", ...}, ...]}} → 取 {@code fields[].name}。</li>
 *   <li>{@code {"columns": [{"name": ...}, ...]}} → 取 {@code columns[].name}（兼容形式）。</li>
 *   <li>{@code [{...}, ...]}（数组根）→ 取每个元素（若有 {@code name}）。</li>
 * </ol>
 * </p>
 *
 * <p>仅当字段对象含 {@code name} key 时纳入字段名集合；不含 {@code name} 的字段对象跳过（非报错）。
 * dsMeta 解析失败或为空时返回空集合（非 null）。</p>
 */
public final class DatasetMetaParser {

    private static final String FIELDS_KEY = "fields";
    private static final String COLUMNS_KEY = "columns";
    private static final String NAME_KEY = "name";

    private DatasetMetaParser() {
    }

    /**
     * 从 dsMeta JSON 文本中提取字段名集合。
     *
     * @param dsMeta JSON 文本，允许 null/空
     * @return 字段名集合（不可变；解析失败或无字段时为空集合）
     */
    public static Set<String> parseFieldNames(String dsMeta) {
        if (dsMeta == null || dsMeta.isEmpty() || "{}".equals(dsMeta.trim())) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<>();
        try {
            Object parsed = JsonTool.parseNonStrict(dsMeta);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) parsed;
                collectFromList(meta.get(FIELDS_KEY), names);
                collectFromList(meta.get(COLUMNS_KEY), names);
            } else if (parsed instanceof List) {
                collectFromList(parsed, names);
            }
        } catch (Exception ignore) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(names);
    }

    @SuppressWarnings("unchecked")
    private static void collectFromList(Object value, Set<String> names) {
        if (!(value instanceof List)) {
            return;
        }
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) {
                continue;
            }
            Object name = ((Map<String, Object>) item).get(NAME_KEY);
            if (name != null && !name.toString().isEmpty()) {
                names.add(name.toString());
            }
        }
    }
}
