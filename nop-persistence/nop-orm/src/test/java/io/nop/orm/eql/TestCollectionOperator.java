package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestCollectionOperator extends AbstractOrmTestCase {

    @Test
    public void testSome() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o where o.simsClasses._some.className = 'c'")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }

    @Test
    public void testSomeOrCondition() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o where o.simsClasses._some.className = 'a' "
                        + "or o.simsClasses._some.className = 'c'")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }

    @Test
    public void testAllCondition() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o where o.simsClasses._all.collegeId = '1'")
                .end();
        long value = orm().findLong(sql, 0L);
        // _all 对空集合返回 true，所以空表时 count=1（每个 college 都满足"所有 class 的 collegeId=1"）
        Assertions.assertEquals(1, value);
    }

    @Test
    public void testSomeAndAllMixed() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o where o.simsClasses._some.className = 'a' "
                        + "and o.simsClasses._all.collegeId = '1'")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }

    @Test
    public void testSomeWithRelation() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o where o.simsClasses._some.simsCollege.collegeName = 'a'")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }
}
