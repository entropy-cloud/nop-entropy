/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.geo.dialect.h2gis.H2GisInitializer;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@NopTestConfig(localDb = true, initDatabaseSchema = true, beanContainerStartMode = BeanContainerStartMode.DEFAULT)
public class TestMultiDataSource extends JunitBaseTestCase {

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    ISqlLibManager sqlLibManager;

    @Inject
    H2GisInitializer h2GisInitializer;

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
                    entity.orm_propValueByName("sid","123");
                    entity.orm_propValueByName("name", "test");
                    ormTemplate.save(entity);
                    ormTemplate.flushSession();

                    jdbcTemplate.existsTable(null,"test");
                    throw new IllegalStateException("test-error");
                });
            });
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }
}
