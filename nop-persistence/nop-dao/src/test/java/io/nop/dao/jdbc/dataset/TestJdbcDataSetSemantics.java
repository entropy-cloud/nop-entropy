/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.dataset;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.JdbcTestCase;
import io.nop.dao.jdbc.impl.JdbcDataSetHelper;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IDataRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 覆盖补强：JdbcDataSet 对JDBC结果集的类型化读取/迭代/关闭语义（经生产工厂 JdbcDataSetHelper）。
 */
public class TestJdbcDataSetSemantics extends JdbcTestCase {

    private void createTypedTable() {
        jdbc().executeUpdate(new SQL(
                "create table ds_typed_entity("
                        + "id integer primary key, "
                        + "name varchar(50), "
                        + "amount decimal(10,2), "
                        + "updated_at timestamp, "
                        + "active boolean)"));
        jdbc().executeUpdate(new SQL(
                "insert into ds_typed_entity(id, name, amount, updated_at, active) values"
                        + "(1, 'first', 12.34, timestamp '2026-01-02 08:30:00', TRUE), "
                        + "(2, NULL, NULL, NULL, FALSE)"));
    }

    private IDataSet openDataSet(String sql) {
        try {
            Connection conn = getDataSource().getConnection();
            return JdbcDataSetHelper.newDataSet(conn, new SQL(sql));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Test
    public void testTypedColumnAccess() throws Exception {
        createTypedTable();
        try (IDataSet ds = openDataSet("select id, name, amount, updated_at, active from ds_typed_entity order by id")) {
            IDataSetMeta meta = ds.getMeta();
            assertEquals(5, meta.getFieldCount());

            assertTrue(ds.hasNext());
            IDataRow row = ds.next();
            assertEquals(Long.valueOf(1), row.getLong(0));
            assertEquals("first", row.getString(1));
            assertEquals(new BigDecimal("12.34"), row.getBigDecimal(2));
            assertEquals(Timestamp.valueOf("2026-01-02 08:30:00"), row.getTimestamp(3));
            assertEquals(Boolean.TRUE, row.getBoolean(4));

            // 第二行全NULL列：类型化读取返回null而不是抛错
            IDataRow nullRow = ds.next();
            assertNull(nullRow.getString(1));
            assertNull(nullRow.getBigDecimal(2));
            assertNull(nullRow.getTimestamp(3));
            assertEquals(Boolean.FALSE, nullRow.getBoolean(4));

            assertFalse(ds.hasNext());
        }
    }

    @Test
    public void testIterationCountsAndFieldNameAccess() throws Exception {
        createTypedTable();
        try (IDataSet ds = openDataSet("select id, name from ds_typed_entity order by id")) {
            assertEquals(2, ds.getMeta().getFieldCount());
            assertEquals("ID", ds.getMeta().getFieldName(0));
            assertEquals("NAME", ds.getMeta().getFieldName(1));

            int count = 0;
            while (ds.hasNext()) {
                ds.next();
                count++;
            }
            assertEquals(2, count);
            assertEquals(2, ds.getReadCount());
        }
    }

    @Test
    public void testEmptyResultHasNoRows() throws Exception {
        createTypedTable();
        try (IDataSet ds = openDataSet("select id from ds_typed_entity where id > 100")) {
            assertFalse(ds.hasNext());
            assertEquals(0, ds.getReadCount());
        }
    }

    @Test
    public void testGetColumnTableNameExposed() throws Exception {
        createTypedTable();
        try (IDataSet ds = openDataSet("select id from ds_typed_entity")) {
            // JdbcDataSet自身暴露列所属物理表名
            String tableName = ((JdbcDataSet) ds).getColumnTableName(0);
            assertTrue(tableName.toLowerCase().contains("ds_typed_entity"), tableName);
        }
    }

    @Test
    public void testCloseClosesUnderlyingResultSet() throws Exception {
        createTypedTable();
        IDialect dialect = getDialect();
        Connection conn = getDataSource().getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("select id from ds_typed_entity order by id");
        IDataSet ds = new JdbcDataSet(dialect, rs);

        assertTrue(ds.hasNext());
        assertEquals(1L, ds.next().getLong(0));

        ds.close();
        // 关闭数据集必须同步关闭底层ResultSet
        assertTrue(rs.isClosed(), "underlying ResultSet must be closed");
        conn.close();
    }

    @Test
    public void testHasNextOnClosedDataSetReturnsFalseOrThrows() throws Exception {
        createTypedTable();
        IDialect dialect = getDialect();
        Connection conn = getDataSource().getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("select id from ds_typed_entity");
        IDataSet ds = new JdbcDataSet(dialect, rs);
        ds.close();

        // 关闭后再访问：不允许返回脏数据（H2关闭的rs访问抛SQLException，包装为运行时异常）
        try {
            assertFalse(ds.hasNext());
        } catch (RuntimeException e) {
            // 预期：底层ResultSet已关闭
        }
        conn.close();
    }
}
