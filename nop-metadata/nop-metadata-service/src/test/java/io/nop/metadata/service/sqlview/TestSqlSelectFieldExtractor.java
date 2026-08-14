package io.nop.metadata.service.sqlview;

import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SqlSelectFieldExtractor 单元测试（纯语法解析，不依赖 ORM session / IoC）。
 *
 * <p>AR-14d：验证错误异常的 {@code sql} param 包含真实 SQL 文本（非 Java 类名或其它误导性字符串），
 * 与文件内其它错误点（{@code .param("sql", sql)}）保持一致。
 */
public class TestSqlSelectFieldExtractor {

    private final SqlSelectFieldExtractor extractor = new SqlSelectFieldExtractor();

    /** 非 SELECT 语句（DELETE）→ ERR_SQL_VIEW_NOT_SELECT，sql param 为真实 SQL 文本。 */
    @Test
    public void nonSelectErrorParamContainsRealSql() {
        String sql = "DELETE FROM my_table WHERE id = 1";
        NopMetadataException ex = assertThrows(NopMetadataException.class, () -> extractor.extract(sql));
        assertEquals(NopMetadataErrors.ERR_SQL_VIEW_NOT_SELECT.getErrorCode(), ex.getErrorCode());
        assertEquals(sql, ex.getParam("sql"), "error 'sql' param must contain real SQL text, not a class name");
    }

    /** 多语句（{@code ;} 分隔）→ ERR_SQL_VIEW_MULTI_STATEMENT，sql param 为真实 SQL 文本。 */
    @Test
    public void multiStatementErrorParamContainsRealSql() {
        String sql = "SELECT a FROM t1; SELECT b FROM t2";
        NopMetadataException ex = assertThrows(NopMetadataException.class, () -> extractor.extract(sql));
        assertEquals(NopMetadataErrors.ERR_SQL_VIEW_MULTI_STATEMENT.getErrorCode(), ex.getErrorCode());
        assertEquals(sql, ex.getParam("sql"), "error 'sql' param must contain real SQL text");
    }

    /** 不可解析 SQL → ERR_SQL_VIEW_PARSE_FAILED，sql param 为真实 SQL 文本。 */
    @Test
    public void parseFailedErrorParamContainsRealSql() {
        String sql = "SELECT FROM";
        NopMetadataException ex = assertThrows(NopMetadataException.class, () -> extractor.extract(sql));
        assertEquals(NopMetadataErrors.ERR_SQL_VIEW_PARSE_FAILED.getErrorCode(), ex.getErrorCode());
        assertEquals(sql, ex.getParam("sql"), "error 'sql' param must contain real SQL text");
    }
}
