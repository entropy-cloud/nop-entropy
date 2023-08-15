/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.impl;

import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOrmTemplate extends AbstractOrmTestCase {
    @Test
    public void testCompileUpdate() {
        IOrmSessionFactory factory = orm().getSessionFactory();
        ICompiledSql sql = factory.compileSql("test",
                "update io.nop.app.SimsClass o set o.className='x'", false);

        assertEquals("update\n" +
                "  sims_class\n" +
                "set \n" +
                "  CLASS_NAME= 'x' \n",sql.getSql().getText());
    }

    @Test
    public void testCompileUpdateNoAlias() {
        IOrmSessionFactory factory = orm().getSessionFactory();
        ICompiledSql sql = factory.compileSql("test",
                "update io.nop.app.SimsClass set className='x'", false);

        assertEquals("update\n" +
                "  sims_class\n" +
                "set \n" +
                "  CLASS_NAME= 'x' \n",sql.getSql().getText());
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
                "  CLASS_NAME =  'x' \n",sql.getSql().getText());
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
                "  CLASS_NAME =  'x' \n",sql.getSql().getText());
    }
}
