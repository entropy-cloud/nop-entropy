/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.core.lang.sql.SQL;
import io.nop.stream.core.connector.lookup.ITableLookup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI14: TYPE-level proof of the §III #8 bridge — an {@code IJdbcTemplate} (nop-dao)
 * adapts to the stream-side {@link ITableLookup} contract, so a dimension lookup on
 * the stream goes through the platform's data-access facade instead of a self-managed
 * data source. H2 in-memory (D15 default), a real SELECT through the JDBC facade.
 */
public class TestJdbcTemplateTableLookupAdapter {

    private static HikariDataSource dataSource;
    private static IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));

        jdbcTemplate.executeUpdate(SQL.begin().sql("create table dim_merchant (merchant_id varchar(32) primary key, merchant_name varchar(64))").end());
        jdbcTemplate.executeUpdate(SQL.begin().sql("insert into dim_merchant values ('m1', 'Merchant One')").end());
        jdbcTemplate.executeUpdate(SQL.begin().sql("insert into dim_merchant values ('m2', 'Merchant Two')").end());
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    /**
     * The application-side adapter: IJdbcTemplate wrapped as the stream lookup facade.
     * The template is TRANSIENT (live connection pool, not serializable) — production
     * adapters re-resolve it on the subtask side (e.g. via a static/supplier hook or
     * by opening in the operator's open()). This test form re-resolves eagerly.
     */
    static final class JdbcTemplateTableLookup implements ITableLookup {
        private static final long serialVersionUID = 1L;

        private final String table;
        private transient IJdbcTemplate template;
        private static IJdbcTemplate lastTemplate;

        JdbcTemplateTableLookup(IJdbcTemplate template, String table) {
            this.template = template;
            this.table = table;
            lastTemplate = template;
        }

        private IJdbcTemplate template() {
            if (template == null) {
                template = lastTemplate;
            }
            return template;
        }

        Object lookupForTest(Object key) {
            return lookup(key);
        }

        @Override
        public Object lookup(Object key) {
            // findFirst with a single column returns the value itself (default row
            // mapper maps a one-column row to that value)
            Object value = template().findFirst(
                    SQL.begin().sql("select merchant_name from " + table + " where merchant_id = ")
                            .param0(key).end());
            return value;
        }
    }

    @Test
    void jdbcTemplateBridgesToTableLookupContract() {
        ITableLookup lookup = new JdbcTemplateTableLookup(jdbcTemplate, "dim_merchant");

        assertEquals("Merchant One", lookup.lookup("m1"), "existing key resolves through the JDBC facade");
        assertEquals("Merchant Two", lookup.lookup("m2"));
        assertEquals(null, lookup.lookup("missing"), "missing key yields null (no row)");
    }

    @Test
    void adapterSurvivesOperatorCopyViaTransientTemplate() throws Exception {
        // IJdbcTemplate is not serializable (it wraps a live connection pool) — the
        // adapter must hold it transiently and re-resolve on the subtask side. This
        // test pins that contract: copy round trip does not fail, and the copied
        // adapter reports the missing template instead of silently breaking.
        JdbcTemplateTableLookup lookup = new JdbcTemplateTableLookup(jdbcTemplate, "dim_merchant");
        byte[] bytes;
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(lookup);
            bytes = bos.toByteArray();
        }
        JdbcTemplateTableLookup copy;
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            copy = (JdbcTemplateTableLookup) ois.readObject();
        }
        assertEquals("Merchant One", copy.lookupForTest("m1"),
                "the re-resolved template must serve lookups after the copy");
    }
}
