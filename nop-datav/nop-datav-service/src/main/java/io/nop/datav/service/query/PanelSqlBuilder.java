package io.nop.datav.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_QUERY_FAILED;

/**
 * 将 dsText SQL 模板（含 {@code ${paramName}} 占位符）和求值后的查询参数合成为平台 {@link SQL} 对象。
 *
 * <p>仅替换出现在 paramMapping 中的参数占位符（paramMapping 已由 {@link PanelParamEvaluator} 求值）。
 * 占位符在 SQL 中被替换为顺序的 {@code ?}，对应参数值按出现顺序通过 {@link SQL.SqlBuilder#sqlWithParams(String, java.util.List)}
 * 注入，避免 SQL 注入风险。</p>
 *
 * <p>若 dsText 含 {@code ${paramName}} 占位符但参数 Map 中没有对应 key（值为 null 也会被传入），
 * 占位符仍被替换为 {@code ?}（值为 null），由 JDBC 处理 NULL 绑定。若 dsText 中含未在 paramMapping 中声明的
 * 占位符（参数 Map 中无此 key），则抛 {@code ERR_DATAV_QUERY_FAILED}（参数缺失），不静默保留原占位符文本。</p>
 */
public final class PanelSqlBuilder {

    /**
     * 匹配 {@code ${paramName}} 占位符。参数名允许字母、数字、下划线。
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    private PanelSqlBuilder() {
    }

    /**
     * 构建 SQL 对象。
     *
     * @param dsText        nop-report 数据集的 SQL 文本
     * @param params        求值后的查询参数（参数名 → 值）
     * @param panelId       面板 ID（用于错误上下文）
     */
    public static SQL build(String dsText, Map<String, Object> params, String panelId) {
        if (dsText == null || dsText.isEmpty()) {
            throw new NopException(ERR_DATAV_QUERY_FAILED)
                    .param("panelId", panelId);
        }
        Matcher matcher = PARAM_PATTERN.matcher(dsText);
        StringBuffer sb = new StringBuffer();
        List<Object> values = new ArrayList<>();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (!params.containsKey(paramName)) {
                throw new NopException(ERR_DATAV_QUERY_FAILED)
                        .param("panelId", panelId);
            }
            Object value = params.get(paramName);
            matcher.appendReplacement(sb, "?");
            values.add(value);
        }
        matcher.appendTail(sb);
        return SQL.begin().name("getPanelData").sqlWithParams(sb.toString(), values).end();
    }
}
