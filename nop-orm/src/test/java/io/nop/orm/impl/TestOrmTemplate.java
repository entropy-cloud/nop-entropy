/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

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
    public void testParamValueType() {
        txn().runInTransaction(null, TransactionPropagation.SUPPORTS, txn -> {
            SQL sql = SQL.begin().sql("select o,2 from io.nop.app.SimsClass o where 1=? and date(o.createdTime) > ?", 3,"2002-01-03").end();
            orm().findAll(sql);
            return null;
        });
    }

    @Test
    public void testEntityId() {
        SimsClass entity = (SimsClass) orm().newEntity(SimsClass.class.getName());
        entity.setClassId("3");
        assertEquals("3", entity.prop_get("id"));
        assertEquals("3", entity.orm_propValueByName("id"));
    }

    @Test
    public void testJoinOnNonPkColumn() {
        SimsClass simsClass = (SimsClass) orm().newEntity(SimsClass.class.getName());
        simsClass.setClassId("333");
        simsClass.setClassName("test_join");
        orm().save(simsClass);

        SimsCollege simsCollege = (SimsCollege) orm().newEntity(SimsCollege.class.getName());
        simsCollege.setId("456");
        simsCollege.setCollegeName("test_join");
        orm().save(simsCollege);

        orm().runInSession(() -> {
            SimsClass entity = (SimsClass) orm().get(SimsClass.class.getName(), "333");
            assertEquals("test_join", entity.getClassName());
            SimsCollege refCollege = (SimsCollege) entity.prop_get("refByName");
            assertEquals("456", refCollege.getId());

            IOrmEntitySet<SimsClass> refClasses = (IOrmEntitySet) refCollege.prop_get("refClasses");
            assertEquals(1, refClasses.size());
            assertEquals(entity, refClasses.get__first());
        });

        orm().runInSession(() -> {
            SimsClass entity = (SimsClass) orm().load(SimsClass.class.getName(), "333");
            orm().batchLoadProps(Collections.singleton(entity), Arrays.asList("refByName", "refByName.refClasses"));

            assertEquals("test_join", entity.getClassName());
            SimsCollege refCollege = (SimsCollege) entity.prop_get("refByName");
            assertEquals("456", refCollege.getId());

            IOrmEntitySet<SimsClass> refClasses = (IOrmEntitySet) refCollege.prop_get("refClasses");
            assertEquals(1, refClasses.size());
            assertEquals(entity, refClasses.get__first());
        });

        orm().runInSession(() -> {
            SQL sql = SQL.begin().sql("select o.refByName.collegeName from SimsClass o").end();
            orm().findAll(sql);
        });
    }
}
