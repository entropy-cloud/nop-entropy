package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class TestCollectionOperator extends AbstractOrmTestCase {
    @Test
    public void testSome() {
        SQL sql = SQL.begin().sql("select count(*) from SimsCollege o where o.simsClasses._some.simsCollege.shortName = 'a' " +
                "or o.simsClasses._some.className = 'c'").end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }
}
