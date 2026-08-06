package io.nop.metadata.service.field;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    public void testNonListJsonFailsWithExplicitErrorCode() {
        NopMetaTable table = externalTable("{\"columnName\":\"A\"}");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID.getErrorCode(),
                ex.getErrorCode());
    }

    @Test
    public void testMalformedJsonFailsWithExplicitErrorCode() {
        NopMetaTable table = externalTable("[{\"columnName\":\"A\"");
        NopException ex = assertThrows(NopException.class, () -> resolver.resolve(table, null));
        assertEquals(NopMetadataErrors.ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID.getErrorCode(),
                ex.getErrorCode());
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
}
