package io.nop.datav.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_QUERY_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-12（plan 2026-08-15-2146-3 Phase 5）{@link PanelSqlBuilder} 注入屏障纯 JUnit 单测
 * （无容器依赖）。
 *
 * <p>此前 3 个集成测试仅间接锚定参数化主干，两个错误分支（空 SQL / 未声明占位符）零覆盖、
 * 无恶意参数值绑定回归断言。本测试直接锚定：</p>
 * <ul>
 *   <li>分支 A：空 SQL（null / ""）→ {@code NopException} + {@code ERR_DATAV_QUERY_FAILED}
 *       + {@code panelId} param（显式失败非静默）；</li>
 *   <li>分支 B：SQL 含未在参数 Map 声明的 {@code ${占位符}} → 同上显式失败；</li>
 *   <li>多占位符按出现顺序绑定 {@code ?} 与参数值；</li>
 *   <li><b>注入回归锚点</b>：恶意参数值（{@code north' OR '1'='1}）进入 {@code getParams()}
 *       绑定通道、不出现在 SQL 文本（占位符替换为 {@code ?}，无字符串拼接）。</li>
 * </ul>
 */
public class TestPanelSqlBuilder {

    // ==================== 分支 A：空 SQL 显式失败 ====================

    @Test
    public void testNullSqlRejectedExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> PanelSqlBuilder.build(null, Map.of(), "panel-a1"));
        assertEquals(ERR_DATAV_QUERY_FAILED.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-a1", ex.getParam(ARG_PANEL_ID));
    }

    @Test
    public void testEmptySqlRejectedExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> PanelSqlBuilder.build("", Map.of(), "panel-a2"));
        assertEquals(ERR_DATAV_QUERY_FAILED.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-a2", ex.getParam(ARG_PANEL_ID));
    }

    // ==================== 分支 B：未声明占位符显式失败 ====================

    @Test
    public void testUndeclaredPlaceholderRejectedExplicitly() {
        NopException ex = assertThrows(NopException.class,
                () -> PanelSqlBuilder.build(
                        "select * from t where region = ${undeclared}", Map.of(), "panel-b1"));
        assertEquals(ERR_DATAV_QUERY_FAILED.getErrorCode(), ex.getErrorCode());
        assertEquals("panel-b1", ex.getParam(ARG_PANEL_ID));
    }

    /** 占位符出现在 params 且值为 null → 仍替换为 ?（JDBC NULL 绑定），不抛错。 */
    @Test
    public void testDeclaredNullValueBindsAsPlaceholder() {
        SQL sql = PanelSqlBuilder.build(
                "select * from t where region = ${region}",
                params("region", null), "panel-b2");
        assertEquals("select * from t where region = ?", sql.getText());
        assertEquals(1, sql.getParams().size(), "null value still bound as parameter");
    }

    // ==================== 多占位符顺序绑定 ====================

    @Test
    public void testMultiplePlaceholdersBoundInOrder() {
        SQL sql = PanelSqlBuilder.build(
                "select * from t where a = ${p1} and b = ${p2} and a = ${p1}",
                params("p1", "v1", "p2", 42), "panel-c1");

        assertEquals("select * from t where a = ? and b = ? and a = ?", sql.getText(),
                "placeholders replaced with ? (no inlined values)");
        List<Object> params = sql.getParams();
        assertEquals(3, params.size(), "one bound value per placeholder occurrence");
        assertEquals("v1", params.get(0));
        assertEquals(42, params.get(1));
        assertEquals("v1", params.get(2), "repeated placeholder bound again (positional)");
    }

    // ==================== 注入回归锚点 ====================

    @Test
    public void testMaliciousParamValueBoundNotInlined() {
        String payload = "north' OR '1'='1";
        SQL sql = PanelSqlBuilder.build(
                "select * from t where region = ${region}",
                params("region", payload), "panel-d1");

        assertEquals("select * from t where region = ?", sql.getText(),
                "SQL text contains only the ? placeholder");
        assertFalse(sql.getText().contains(payload), "payload must not be inlined into SQL text");
        assertFalse(sql.getText().contains("'1'='1'"), "no injection fragment in SQL text");
        assertEquals(1, sql.getParams().size());
        assertEquals(payload, sql.getParams().get(0), "payload travels via parameter binding channel");
        assertTrue(sql.getParams().get(0) instanceof String);
    }

    /** SQL 文本本身含引号/特殊字符但无占位符 → 原样保留（非占位符部分不转义，不拼接参数）。 */
    @Test
    public void testStaticSqlWithoutPlaceholdersPassedThrough() {
        SQL sql = PanelSqlBuilder.build("select 'literal' from t", Map.of(), "panel-e1");
        assertEquals("select 'literal' from t", sql.getText());
        assertTrue(sql.getParams().isEmpty(), "no params bound");
    }

    // ==================== helpers ====================

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
