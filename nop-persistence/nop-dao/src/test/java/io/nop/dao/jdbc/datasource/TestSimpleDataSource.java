/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoErrors.ERR_DAO_MISSING_DRIVER_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * driverClassName漏配时应抛出带错误码的NopException，而不是不带上下文的裸NPE
 */
public class TestSimpleDataSource {

    @Test
    public void testNullDriverClassNameFailsWithErrorCode() {
        SimpleDataSource ds = new SimpleDataSource();
        NopException e = assertThrows(NopException.class, () -> ds.setDriverClassName(null));
        assertEquals(ERR_DAO_MISSING_DRIVER_CLASS_NAME.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testBlankDriverClassNameFailsWithErrorCode() {
        SimpleDataSource ds = new SimpleDataSource();
        assertThrows(NopException.class, () -> ds.setDriverClassName("  "));
    }

    @Test
    public void testValidDriverClassNameIsTrimmedAndLoaded() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName(" org.h2.Driver ");
        assertEquals("org.h2.Driver", ds.getDriverClassName());
    }

    /**
     * check2 P2：setCatalog/setSchema 抛异常时已打开的物理连接必须被关闭。
     * 验证手法：H2 命名内存库在【最后一个】连接关闭时丢弃内容——若泄漏了连接，
     * 后续"建表→关连接→重开"后表仍在；修复后重开时表已随库丢弃。
     */
    @Test
    public void testConnectionClosedWhenSetSchemaFails() throws Exception {
        String url = "jdbc:h2:mem:simple_ds_leak_test_" + System.nanoTime(); // 无 DB_CLOSE_DELAY

        SimpleDataSource ds = new SimpleDataSource();
        ds.setUrl(url);
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setSchema("NO_SUCH_SCHEMA"); // H2 对未知 schema 抛 JdbcSQLSyntaxErrorException

        assertThrows(java.sql.SQLException.class, ds::getConnection);

        // 无泄漏 → 该库此刻没有任何打开的连接；建表并关闭后库被丢弃，重开时表不存在
        try (java.sql.Connection c1 = java.sql.DriverManager.getConnection(url, "sa", "")) {
            try (java.sql.Statement st = c1.createStatement()) {
                st.execute("create table LEAK_PROBE(id int)");
            }
        }
        try (java.sql.Connection c2 = java.sql.DriverManager.getConnection(url, "sa", "")) {
            java.sql.DatabaseMetaData meta = c2.getMetaData();
            try (java.sql.ResultSet rs = meta.getTables(null, null, "LEAK_PROBE", null)) {
                assertEquals(false, rs.next(),
                        "leaked connection keeps the in-memory DB alive across close/reopen");
            }
        }
    }
}
