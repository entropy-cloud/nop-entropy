/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.auth.dao.entity.NopAuthGroup;
import io.nop.auth.service.biz.TestService;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTransaction extends JunitAutoTestCase {
    @Inject
    TestService testService;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testRequiresNew() {
        try {
            testService.methodA();
            fail();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void testRollback() {

        try {
            ormTemplate.runInSession(() -> {
                transactionTemplate.runInTransaction(txn -> {
                    NopAuthGroup group = new NopAuthGroup();
                    group.setName("aaa");
                    ormTemplate.save(group);
                    ormTemplate.flushSession();
                    throw new IllegalArgumentException("e");
                });
            });
        } catch (IllegalArgumentException e) {
            // ignore
        }

        assertEquals(0, daoProvider.daoFor(NopAuthGroup.class).findAll().size());
    }
}