package io.nop.metadata.service.query;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityDimension;
import io.nop.metadata.dao.entity.NopMetaEntityMeasure;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AR-20a（plan 2026-08-06-1228-1 Phase 1）接线验证：外部聚合路径把方言从
 * {@code withConnection} lambda（safeProductName）完整传入 {@code buildOrderByClause}——
 * Anti-Hollow 调用链（execute → buildExternalAggregationSql → buildOrderByClause）。
 *
 * <p>判别性：
 * <ul>
 *   <li>metaData.getDatabaseProductName() = "MySQL" + nullsFirst=false+ASC（MySQL 无法表达）→
 *       异常经 processor/execute() 到达调用方（若方言未传入则产出非法 SQL 静默继续）；</li>
 *   <li>metaData.getDatabaseProductName() = "H2" → 子句保留且查询正常执行（keep-green）。</li>
 * </ul>
 */
public class TestAggregationOrderByDialectWiring {

    private static NopMetaEntity externalTable(String tableId, String tableName, String querySpace) {
        NopMetaEntity table = new NopMetaEntity();
        table.setMetaEntityId(tableId);
        table.setEntityKind("EXTERNAL");
        table.setTableName(tableName);
        table.setQuerySpace(querySpace);
        // buildSql 模拟 syncExternalTables 落库的列描述 JSON（MetaEntityFieldResolver.resolveExternalFields 消费）
        table.setExternalColumns("[{\"columnName\":\"AMOUNT\",\"columnType\":\"INTEGER\"}]");
        return table;
    }

    private static AggregationContext newContext(IDaoProvider daoProvider,
                                                  IMetaDataSourceConnectionProcessor connSvc,
                                                  NopMetaEntity table, List<OrderFieldBean> orderBy) {
        MetaQueryContext ctx = new MetaQueryContext(daoProvider, mock(IOrmTemplate.class), connSvc,
                new TableReferenceExecutor(mock(IMetaDataSourceConnectionProcessor.class), mock(IOrmTemplate.class)),
                new MetaDataSourceResolver(), new MetaEntityFieldResolver(), new FilterToSqlTranslator());
        AggregationContext context = mock(AggregationContext.class);
        when(context.getTable()).thenReturn(table);
        when(context.getMeasureNames()).thenReturn(List.of("total"));
        when(context.getDimensionNames()).thenReturn(Collections.emptyList());
        when(context.getOrderBy()).thenReturn(orderBy);
        when(context.getFilter()).thenReturn(null);
        when(context.getHaving()).thenReturn(null);
        when(context.getLimit()).thenReturn(null);
        when(context.getOffset()).thenReturn(null);
        when(context.ctx()).thenReturn(ctx);
        return context;
    }

    @SuppressWarnings("unchecked")
    private static IMetaDataSourceConnectionProcessor connectionSvc(Connection conn, DatabaseMetaData metaData) {
        IMetaDataSourceConnectionProcessor connSvc = mock(IMetaDataSourceConnectionProcessor.class);
        doAnswer(inv -> {
            BiConsumer<Connection, DatabaseMetaData> action = inv.getArgument(2);
            action.accept(conn, metaData);
            return null;
        }).when(connSvc).withConnection(any(), any(), any());
        return connSvc;
    }

    @SuppressWarnings("unchecked")
    private static IDaoProvider daoProvider() {
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        NopMetaDataSource dataSource = new NopMetaDataSource();
        dataSource.setDataSourceId("ds-wiring");
        dataSource.setQuerySpace("qs-wiring");
        dataSource.setDatasourceType("jdbc");
        dataSource.setStatus("ACTIVE");
        IEntityDao<NopMetaDataSource> dsDao = mock(IEntityDao.class);
        when(dsDao.findAllByQuery(any())).thenReturn(List.of(dataSource));
        when(daoProvider.daoFor(NopMetaDataSource.class)).thenReturn(dsDao);

        NopMetaEntityMeasure measure = new NopMetaEntityMeasure();
        measure.setMeasureName("total");
        measure.setEntityFieldId("AMOUNT");
        measure.setAggFunc("sum");
        IEntityDao<NopMetaEntityMeasure> measureDao = mock(IEntityDao.class);
        when(measureDao.findAllByQuery(any())).thenReturn(List.of(measure));
        when(daoProvider.daoFor(NopMetaEntityMeasure.class)).thenReturn(measureDao);

        IEntityDao<NopMetaEntityDimension> dimDao = mock(IEntityDao.class);
        when(dimDao.findAllByQuery(any())).thenReturn(Collections.emptyList());
        when(daoProvider.daoFor(NopMetaEntityDimension.class)).thenReturn(dimDao);

        IEntityDao<NopMetaEntityField> fieldDao = mock(IEntityDao.class);
        when(fieldDao.findAllByQuery(any())).thenReturn(Collections.emptyList());
        when(daoProvider.daoFor(NopMetaEntityField.class)).thenReturn(fieldDao);
        return daoProvider;
    }

    private static List<OrderFieldBean> orderByAscNullsLast() {
        OrderFieldBean f = OrderFieldBean.asc("total");
        f.setNullsFirst(false);
        return new ArrayList<>(List.of(f));
    }

    /** MySQL 无法表达组合：方言必须经 withConnection lambda → safeProductName → buildOrderByClause 到达（fail-fast）。 */
    @Test
    public void testExternalAggregationProcessorMySqlNullsLastAscFailsFast() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        IMetaDataSourceConnectionProcessor connSvc = connectionSvc(conn, metaData);
        IDaoProvider daoProvider = daoProvider();

        NopMetaEntity table = externalTable("t-wiring-mysql", "EXT_TABLE", "qs-wiring");
        AggregationContext context = newContext(daoProvider, connSvc, table, orderByAscNullsLast());

        NopException ex = assertThrows(NopException.class,
                () -> new ExternalAggregationProcessor().execute(context),
                "MySQL + nullsFirst=false+ASC must fail loudly through the processor (dialect wired), "
                        + "not silently produce illegal SQL");
        assertEquals(NopMetadataErrors.ERR_AGGR_ORDER_BY_NULLS_UNSUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("MySQL", ex.getParam("databaseProductName"));
        verify(metaData).getDatabaseProductName();
    }

    /** H2 路径：方言传入后子句保留，查询继续执行（keep-green，接线不破坏既有行为）。 */
    @Test
    public void testExternalAggregationProcessorH2KeepsNullsClauseAndExecutes() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement st = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsMeta = mock(ResultSetMetaData.class);
        when(rsMeta.getColumnCount()).thenReturn(0);
        when(rs.getMetaData()).thenReturn(rsMeta);
        when(rs.next()).thenReturn(false);
        when(st.executeQuery()).thenReturn(rs);
        when(conn.prepareStatement(any())).thenReturn(st);

        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        IMetaDataSourceConnectionProcessor connSvc = connectionSvc(conn, metaData);
        IDaoProvider daoProvider = daoProvider();

        NopMetaEntity table = externalTable("t-wiring-h2", "EXT_TABLE", "qs-wiring");
        AggregationContext context = newContext(daoProvider, connSvc, table, orderByAscNullsLast());

        List<java.util.Map<String, Object>> rows = new ExternalAggregationProcessor().execute(context);
        assertTrue(rows.isEmpty(), "H2 path must execute normally (no dialect error): " + rows);
    }
}
