package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P2-09（plan 2026-08-16-0226-2 Phase 2）代表点回归：`-- {error}` 附注族 throw 点
 * 补齐 `.param(ARG_ERROR, …)` 后，异常消息渲染真实 error 值、无字面 `{error}` 残留。
 *
 * <p>代表点 = {@code AggregationHelper.checkTableExists}（P2-06 显式抛错路径，原 :543）：
 * 已建连元数据查询抛 SQLException（P2-08 已建连面裁定——消息不含连接 URL，无脱敏新增面）。
 * 其余 6 处补参点（AutoClassificationProcessor / LineageTagPropagationProcessor /
 * NopMetaModuleBizModel×2 / NopMetaTagLabelBizModel / MetaModelChangedEventPublisher）由
 * INV-ERROR-PARAM 门禁（`{error}` 豁免已收口）静态守护 + 代表点渲染模式保证；
 * MetaModelChangedEventPublisher 可触发路径的 focused 断言见
 * {@code TestNopMetaModelChangedEvent#testP209SerializationFailureRendersRealErrorParam}。
 */
public class TestAggregationHelperErrorParam {

    /** getTables 抛 SQLException → ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED 渲染真实 error 值。 */
    @Test
    public void testCheckTableExistsMetadataFailureRendersRealError() throws SQLException {
        SQLException cause = new SQLException("getTables metadata lookup boom");
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getTables(isNull(), anyString(), anyString(), any())).thenThrow(cause);

        NopException ex = assertThrows(NopException.class,
                () -> AggregationHelper.checkTableExists(metaData, "myschema", "mytable"),
                "metadata lookup failure must fail-loud with ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED");

        assertEquals(NopMetadataErrors.ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED.getErrorCode(), ex.getErrorCode());
        assertSame(cause, ex.getCause(), "original SQLException cause must be preserved");
        assertEquals("getTables metadata lookup boom", ex.getParam("error"),
                "error param must carry the real exception message (messageOf form)");
        assertTrue(ex.getMessage().contains("getTables metadata lookup boom"),
                "rendered message must contain the real error value, got: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("{error}"),
                "no literal {error} placeholder may remain (P2-09), got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("myschema") && ex.getMessage().contains("mytable"),
                "schema/tableName identities must still render, got: " + ex.getMessage());
    }
}
