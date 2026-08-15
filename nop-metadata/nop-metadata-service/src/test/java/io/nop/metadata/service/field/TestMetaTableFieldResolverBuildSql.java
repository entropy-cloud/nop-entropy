package io.nop.metadata.service.field;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-18b（plan 2026-08-06-0914-3 Phase 1）：external 表 buildSql 反序列化逐元素类型化校验——
 * 元素非 Map（或为 null）时显式抛 {@link NopMetadataErrors#ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID}
 * （含元素下标参数），不再裸 ClassCastException。
 *
 * <p>判别性：修复前 {@code (List<Map<String,Object>>) parsed} 未类型化，元素为 Integer 时
 * {@code col.get(...)} 裸 ClassCastException（非显式错误码）——red 实测。
 */
public class TestMetaTableFieldResolverBuildSql {

    private final MetaTableFieldResolver resolver = new MetaTableFieldResolver();

    private static NopMetaTable externalTable(String buildSql) {
        NopMetaTable t = new NopMetaTable();
        t.setMetaTableId("meta-table-buildsql");
        t.setTableType("external");
        t.setBuildSql(buildSql);
        return t;
    }

    @Test
    public void testMixedTypeElementsFailWithExplicitErrorCode() {
        // [123, {...}] —— 元素 0 非 Map：显式错误码（修复前裸 ClassCastException）
        NopMetaTable table = externalTable("[123, {\"columnName\":\"AMOUNT\",\"dataType\":\"INTEGER\"}]");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID.getErrorCode(),
                ex.getErrorCode(), "non-map element must map to the explicit error code, not bare CCE");
        assertEquals(0, ex.getParam("elementIndex"), "element index must identify the offending element");
    }

    @Test
    public void testNullElementFailsWithExplicitErrorCode() {
        NopMetaTable table = externalTable("[{\"columnName\":\"A\"}, null]");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID.getErrorCode(),
                ex.getErrorCode());
        assertEquals(1, ex.getParam("elementIndex"), "null element at index 1 must be identified");
    }

    /**
     * 整段 JSON 非数组（值语义无元素下标）→ P1-6 轨 3 换码 ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_UNPARSEABLE
     * （plan 2026-08-15-1913-3：原码 {elementIndex} 在该分支无值可传，元素级点位传齐禁削占位符）。
     */
    @Test
    public void testNonListJsonFailsWithExplicitErrorCode() {
        NopMetaTable table = externalTable("{\"columnName\":\"A\"}");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_UNPARSEABLE.getErrorCode(),
                ex.getErrorCode());
        assertEquals("meta-table-buildsql", ex.getParam("metaTableId"),
                "identity param must render (P1-6): " + ex.getParam("metaTableId"));
    }

    /** JSON 解析失败（值语义无元素下标）→ 同上换码 UNPARSEABLE + metaTableId 识别性参数 + cause 保留。 */
    @Test
    public void testMalformedJsonFailsWithExplicitErrorCode() {
        NopMetaTable table = externalTable("[{\"columnName\":\"A\"");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_UNPARSEABLE.getErrorCode(),
                ex.getErrorCode());
        assertEquals("meta-table-buildsql", ex.getParam("metaTableId"),
                "identity param must render (P1-6)");
        assertNotNull(ex.getCause(), "original parse exception must be preserved in cause chain");
    }

    @Test
    public void testValidBuildSqlResolvesFieldNames() {
        NopMetaTable table = externalTable(
                "[{\"columnName\":\"AMOUNT\",\"dataType\":\"INTEGER\"},{\"columnName\":\"CAT_NAME\",\"dataType\":\"VARCHAR\"}]");
        Set<String> names = resolver.resolveFieldNames(table, null);
        assertEquals(2, names.size());
        assertTrue(names.contains("AMOUNT"), "valid buildSql must resolve column names: " + names);
        assertTrue(names.contains("CAT_NAME"), "valid buildSql must resolve column names: " + names);
    }

    /**
     * P1-6 代表点（plan 2026-08-15-1913-3，原 :383）：元素缺 columnName 时 elementIndex 真实渲染
     * ——修复前该 throw 缺 elementIndex 参数，渲染消息含字面 {@code {elementIndex}}（身份丢失）。
     */
    @Test
    public void testMissingColumnNameRendersRealElementIndex() {
        NopMetaTable table = externalTable(
                "[{\"columnName\":\"A\"},{\"dataType\":\"INTEGER\"}]");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID.getErrorCode(),
                ex.getErrorCode());
        assertEquals(1, ex.getParam("elementIndex"),
                "offending element index must be passed (P1-6): " + ex.getParam("elementIndex"));
        assertTrue(ex.getMessage().contains("elementIndex=1"),
                "rendered message must contain the real elementIndex value, got: " + ex.getMessage());
        assertTrue(!ex.getMessage().contains("{elementIndex}"),
                "no literal {elementIndex} placeholder may remain, got: " + ex.getMessage());
    }
}
