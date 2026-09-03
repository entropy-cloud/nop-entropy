/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED;

/**
 * Factory + capability alignment tests for the {@code jdbc-2pc} connector family
 * (item 19 / P-REQ-28): construction via the fluent builder seam, single-fact-source
 * consistency alignment, planning-gate parallelism declaration, and required params.
 */
public class TestJdbcConnectorFactory {

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
    void setUp() {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + StringHelper.generateUUID()
                + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static final java.util.function.Function<Object, Object> ROW_MAPPER = row -> row;

    private StreamConnectorConfig fullConfig() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("jdbcTemplate", jdbcTemplate);
        params.put("tableName", "target_data");
        params.put("columns", List.of("id", "name"));
        params.put("recordMapper", ROW_MAPPER);
        return new StreamConnectorConfig("jdbc-2pc", params);
    }

    @Test
    public void testConstructsTwoPhaseCommitSinkWithAlignment() {
        JdbcTwoPhaseCommitSinkConnectorFactory factory = new JdbcTwoPhaseCommitSinkConnectorFactory();
        JdbcTwoPhaseCommitSink<?> sink = assertInstanceOf(JdbcTwoPhaseCommitSink.class,
                factory.createSink(fullConfig()));
        assertInstanceOf(TwoPhaseCommitSinkFunction.class, sink);
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency());

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals("jdbc-2pc", d.getTypeName());
        assertEquals(d.getSinkConsistency(), sink.getSinkConsistency());
        assertEquals(ConnectorParallelism.PLANNING_GATE_PARALLELISM_1, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS, d.getRecoverySemantic());
    }

    @Test
    public void testRequiredParamsEachFailFast() {
        JdbcTwoPhaseCommitSinkConnectorFactory factory = new JdbcTwoPhaseCommitSinkConnectorFactory();

        // jdbcTemplate missing
        StreamException ex = assertThrows(StreamException.class, () -> factory.createSink(
                new StreamConnectorConfig("jdbc-2pc", Map.of(
                        "tableName", "t", "columns", List.of("c"), "recordMapper", ROW_MAPPER))));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("jdbcTemplate", ex.getParam("paramName"));

        // tableName missing
        ex = assertThrows(StreamException.class, () -> factory.createSink(
                new StreamConnectorConfig("jdbc-2pc", Map.of(
                        "jdbcTemplate", jdbcTemplate, "columns", List.of("c"),
                        "recordMapper", ROW_MAPPER))));
        assertEquals("tableName", ex.getParam("paramName"));

        // columns missing
        ex = assertThrows(StreamException.class, () -> factory.createSink(
                new StreamConnectorConfig("jdbc-2pc", Map.of(
                        "jdbcTemplate", jdbcTemplate, "tableName", "t",
                        "recordMapper", ROW_MAPPER))));
        assertEquals("columns", ex.getParam("paramName"));

        // recordMapper missing
        ex = assertThrows(StreamException.class, () -> factory.createSink(
                new StreamConnectorConfig("jdbc-2pc", Map.of(
                        "jdbcTemplate", jdbcTemplate, "tableName", "t", "columns", List.of("c")))));
        assertEquals("recordMapper", ex.getParam("paramName"));
    }

    @Test
    public void testOptionalParamsDefaultThroughBuilder() {
        Map<String, Object> params = new LinkedHashMap<>(fullConfig().getParams());
        params.put("querySpace", "demo");
        params.put("ledgerTableName", "my_ledger");
        JdbcTwoPhaseCommitSink<?> sink = assertInstanceOf(JdbcTwoPhaseCommitSink.class,
                new JdbcTwoPhaseCommitSinkConnectorFactory().createSink(
                        new StreamConnectorConfig("jdbc-2pc", params)));
        // construction path succeeded with the optional params threaded through the builder
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency());
    }
}
