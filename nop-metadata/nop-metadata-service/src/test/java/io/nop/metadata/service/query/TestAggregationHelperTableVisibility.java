package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-06 判别性测试（R6.4）：checkTableExists 把 getTables 的 SQLException 归类为"表不可见"的修复验证。
 *
 * <p>语义钉死：
 * <ul>
 *   <li>getTables 抛 SQLException（真实故障：权限缺失/元数据面异常）→ 抛
 *       {@code ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED}（含原始 cause），不再返回 false 伪装"表不存在"；</li>
 *   <li>空结果（表确实不存在）→ 仍返回 false（业务语义保持）；</li>
 *   <li>有行 → 返回 true；</li>
 *   <li>{@code isEntityTableVisible} 首个探测抛错即 fail-fast（不重试大小写变体——探测本身异常意味着
 *       元数据面不可用）；</li>
 *   <li>接线验证：{@code MixedSameDbJoinAggregationProcessor.execute} → withConnection lambda →
 *       checkTableExists 抛出的异常向调用方完整传播（不吞异常、不落到 ERR_FIELD_RESOLVE_NO_FIELDS）。</li>
 * </ul>
 */
public class TestAggregationHelperTableVisibility {

    @Test
    public void testCheckTableExistsMetaDataFailureThrows() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException cause = new SQLException("metadata lookup denied");
        when(metaData.getTables(isNull(), eq("SCH"), eq("EMP"), isNull())).thenThrow(cause);

        NopException ex = assertThrows(NopMetadataException.class,
                () -> AggregationHelper.checkTableExists(metaData, "SCH", "EMP"));
        assertEquals(NopMetadataErrors.ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED.getErrorCode(), ex.getErrorCode());
        assertSame(cause, ex.getCause(), "original SQLException must be preserved as cause");
        assertEquals("EMP", ex.getParam(NopMetadataErrors.ARG_TABLE_NAME));
        assertEquals("SCH", ex.getParam(NopMetadataErrors.ARG_SCHEMA));
    }

    @Test
    public void testCheckTableExistsEmptyResultReturnsFalse() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getTables(isNull(), eq("SCH"), eq("EMP"), isNull())).thenReturn(mock(ResultSet.class));

        assertFalse(AggregationHelper.checkTableExists(metaData, "SCH", "EMP"),
                "empty metadata result (table truly missing) must stay false, not throw");
    }

    @Test
    public void testCheckTableExistsFoundReturnsTrue() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(metaData.getTables(isNull(), eq("SCH"), eq("EMP"), isNull())).thenReturn(rs);

        assertTrue(AggregationHelper.checkTableExists(metaData, "SCH", "EMP"));
    }

    @Test
    public void testIsEntityTableVisibleFailsFastOnFirstProbeError() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException cause = new SQLException("metadata plane unavailable");
        when(metaData.getTables(isNull(), isNull(), eq("EMP"), isNull())).thenThrow(cause);

        NopException ex = assertThrows(NopMetadataException.class,
                () -> AggregationHelper.isEntityTableVisible(metaData, null, "EMP"));
        assertEquals(NopMetadataErrors.ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED.getErrorCode(), ex.getErrorCode());
        // fail-fast：首个探测抛错即上抛，不再尝试大小写变体（重试无意义）
        verify(metaData, times(1)).getTables(any(), any(), any(), any());
    }

    /** 接线验证：execute() → checkEntityTableVisible → withConnection lambda 内异常向调用方完整传播。 */
    @Test
    public void testExecutePropagatesVisibilityCheckFailure() throws Exception {
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        IEntityDao<NopMetaDataSource> dsDao = mock(IEntityDao.class);
        when(daoProvider.daoFor(NopMetaDataSource.class)).thenReturn(dsDao);
        NopMetaDataSource dataSource = new NopMetaDataSource();
        dataSource.setDataSourceId("ds-visibility");
        dataSource.setQuerySpace("qs-visibility");
        dataSource.setDatasourceType("jdbc");
        dataSource.setStatus("ACTIVE");
        when(dsDao.findAllByQuery(any())).thenReturn(List.of(dataSource));

        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        SQLException cause = new SQLException("getTables metadata failure");
        when(metaData.getTables(isNull(), isNull(), eq("EMP"), isNull())).thenThrow(cause);
        IMetaDataSourceConnectionProcessor connSvc = mock(IMetaDataSourceConnectionProcessor.class);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            BiConsumer<Connection, DatabaseMetaData> action = inv.getArgument(2);
            action.accept(conn, metaData);
            return null;
        }).when(connSvc).withConnection(any(), any(), any());

        MetaQueryContext ctx = new MetaQueryContext(daoProvider, mock(IOrmTemplate.class), connSvc,
                new TableReferenceExecutor(mock(IMetaDataSourceConnectionProcessor.class), mock(IOrmTemplate.class)),
                new MetaDataSourceResolver(), new MetaTableFieldResolver(), new FilterToSqlTranslator());

        AggregationContext context = mock(AggregationContext.class);
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t-visibility");
        table.setQuerySpace("qs-visibility");
        table.setTableType("external");
        NopMetaEntity entity = new NopMetaEntity();
        entity.setTableName("EMP");
        when(context.getTable()).thenReturn(table);
        when(context.getLeftEndpoint()).thenReturn(MetaJoinExecutor.Endpoint.table(table));
        when(context.getRightEndpoint()).thenReturn(MetaJoinExecutor.Endpoint.entity(entity));
        when(context.getMeasureNames()).thenReturn(Collections.emptyList());
        when(context.getDimensionNames()).thenReturn(Collections.emptyList());
        when(context.getOrderBy()).thenReturn(Collections.emptyList());
        when(context.getJoinId()).thenReturn("j-visibility");
        when(context.ctx()).thenReturn(ctx);

        MixedSameDbJoinAggregationProcessor processor = new MixedSameDbJoinAggregationProcessor();
        NopException ex = assertThrows(NopMetadataException.class, () -> processor.execute(context));
        assertEquals(NopMetadataErrors.ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED.getErrorCode(), ex.getErrorCode());
        assertSame(cause, ex.getCause(),
                "SQLException from getTables must reach the caller of execute() with cause preserved");
        verify(connSvc).withConnection(any(), any(), any());
    }
}
