/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.geo.dialect.h2gis.H2GisInitializer;
import io.nop.orm.initialize.DataBaseSchemaInitializer;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.orm.sql_lib.SqlLibManager;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestMultiDataSource extends BaseTestCase {

    IOrmTemplate ormTemplate;

    IJdbcTemplate jdbcTemplate;

    ITransactionTemplate transactionTemplate;

    ISqlLibManager sqlLibManager;

    H2GisInitializer h2GisInitializer;

    DataBaseSchemaInitializer initializer;

    @BeforeAll
    public static void initialize() {
        setTestConfig("nop.orm.init-database-schema",true);
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        ormTemplate = BeanContainer.getBeanByType(IOrmTemplate.class);
        jdbcTemplate = BeanContainer.getBeanByType(IJdbcTemplate.class);
        transactionTemplate = BeanContainer.getBeanByType(ITransactionTemplate.class);
        sqlLibManager = BeanContainer.getBeanByType(SqlLibManager.class);
        h2GisInitializer = BeanContainer.getBeanByType(H2GisInitializer.class);
        initializer = BeanContainer.getBeanByType(DataBaseSchemaInitializer.class);
    }

    @Test
    public void testSqlQuerySpace() {
        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> args = new HashMap<>();
        sqlLibManager.invoke("test.getAllLocations", null, args, scope);
    }

    @Test
    public void testTransaction() {
        try {
            ormTemplate.runInSession(() -> {
                transactionTemplate.runInTransaction(txn -> {
                    IOrmEntity entity = ormTemplate.newEntity("test.TestGeo");
                    entity.orm_propValueByName("sid", "123");
                    entity.orm_propValueByName("name", "test");
                    ormTemplate.save(entity);
                    ormTemplate.flushSession();

                    jdbcTemplate.existsTable(null, "test");
                    throw new IllegalStateException("test-error");
                });
            });
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }
}
