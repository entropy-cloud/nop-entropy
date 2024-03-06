/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.impl;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.app.SimsClass;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestOrmTemplate extends AbstractOrmTestCase {
    @Test
    public void testCompileUpdate() {
        IOrmSessionFactory factory = orm().getSessionFactory();
        ICompiledSql sql = factory.compileSql("test",
                "update io.nop.app.SimsClass o set o.className='x'", false);

        assertEquals("update\n" +
                "  sims_class\n" +
                "set \n" +
                "  CLASS_NAME= 'x' \n", sql.getSql().getText());
    }

    @Test
    public void testCompileUpdateNoAlias() {
        IOrmSessionFactory factory = orm().getSessionFactory();
        ICompiledSql sql = factory.compileSql("test",
                "update io.nop.app.SimsClass set className='x'", false);

        assertEquals("update\n" +
                "  sims_class\n" +
                "set \n" +
                "  CLASS_NAME= 'x' \n", sql.getSql().getText());
    }

    @Test
    public void testCompileDelete() {
        IOrmSessionFactory factory = orm().getSessionFactory();
        ICompiledSql sql = factory.compileSql("test",
                "delete from io.nop.app.SimsClass o where o.className='x'", false);

        assertEquals("delete\n" +
                "from\n" +
                "  sims_class\n" +
                "where \n" +
                "  CLASS_NAME =  'x' \n", sql.getSql().getText());
    }

    @Test
    public void testCompileDeleteNoAlias() {
        IOrmSessionFactory factory = orm().getSessionFactory();
        ICompiledSql sql = factory.compileSql("test",
                "delete from io.nop.app.SimsClass where className='x'", false);

        assertEquals("delete\n" +
                "from\n" +
                "  sims_class\n" +
                "where \n" +
                "  CLASS_NAME =  'x' \n", sql.getSql().getText());
    }

    @Test
    public void testQueryError() {
        try {
            orm().runInSession(() -> {
                SQL sql = SQL.begin().sql("select o from io.nop.app.SimsClass o").end();
                orm().executeQuery(sql, ds -> {
                    throw new IllegalStateException("error");
                });
            });
            fail();
        } catch (IllegalStateException e) {
            assertEquals("error", e.getMessage());
        } catch (Exception e) {
            fail();
        }
    }


    @Test
    public void testSupports() {
        try {
            txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn -> {
                SQL sql = SQL.begin().sql("select o from io.nop.app.SimsClass o").end();
                orm().executeQuery(sql, ds -> {
                    throw new IllegalStateException("error");
                });
                return null;
            });
            fail();
        } catch (IllegalStateException e) {
            assertEquals("error", e.getMessage());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testEntityId() {
        SimsClass entity = (SimsClass) orm().newEntity(SimsClass.class.getName());
        entity.setClassId("3");
        assertEquals("3", entity.prop_get("id"));
        assertEquals("3", entity.orm_propValueByName("id"));
    }
}
