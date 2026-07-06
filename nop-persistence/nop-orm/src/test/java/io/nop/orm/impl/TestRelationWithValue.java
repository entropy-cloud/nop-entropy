package io.nop.orm.impl;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

public class TestRelationWithValue extends AbstractOrmTestCase {
    @Test
    public void testLeftValueToOne() {
        orm().runInSession(()->{
            SQL sql = SQL.begin().sql("select o.testRef.fieldName  from SimsCollege o").end();
            orm().findAll(sql);
        });
    }
}
