/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.jdbc;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI4 plan 2413 防回归钉：dmlMode=INSERT 全路径在**无 nop-message-debezium** 的运行时
 * classpath 下必须可用（本类在 surefire execution insert-no-debezium 中以
 * classpathDependencyExcludes 排除该 jar 运行）。
 *
 * <p>本类不得 import io.nop.message.debezium 下的任何类型——测试自身必须对 CDC 信封零链接，
 * 与被钉的生产行为（INSERT 纯记录路径零链接）同构。
 *
 * <p>历史缺陷：JdbcTwoPhaseCommitSink.toDmlOp 的 INSERT 分支曾直接
 * {@code value instanceof ChangeEvent}，JVM 对 instanceof 的类解析使纯 INSERT 部署
 * 首条记录即 NoClassDefFoundError（pom 注释「INSERT never touches these types」假设不成立）。
 */
class TestJdbcTwoPhaseCommitSinkInsertNoDebezium {

    private HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName()
                + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE cdc_target (id BIGINT PRIMARY KEY, name VARCHAR(100))")) {
                ps.execute();
            }
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private static Map<String, Object> row(Object id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private String nameOf(Object id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM cdc_target WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Test
    void testInsertFullPathWithoutDebeziumOnClasspath() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .columns("id", "name")
                .dmlMode(JdbcTwoPhaseCommitSink.DmlMode.INSERT)
                .recordMapper(row -> row)
                .build();
        sink.initializeLedgerTable();

        // 2PC 全路径：begin → invoke 多条 → saveState → preCommit → commit
        sink.beginTransaction();
        sink.invoke(row(1, "a"));
        sink.invoke(row(2, "b"));
        sink.saveState(1L);
        sink.preCommit(1L);
        sink.commit(1L);

        assertEquals("a", nameOf(1), "first INSERT record must land");
        assertEquals("b", nameOf(2), "second INSERT record must land");
    }
}
