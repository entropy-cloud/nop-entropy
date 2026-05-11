package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 端到端集成测试：验证 EQL 中集合属性作为 FROM 子句表源的功能。
 *
 * <p>典型语法：{@code from o.simsClasses c where c.className = '...'}，
 * 由 {@code CollectionTableSourceHelper} 负责将集合属性表源转换为真实实体表 + JOIN 条件。</p>
 */
public class TestCollectionTableSource extends AbstractOrmTestCase {

    @Test
    public void testCollectionAsTableSource() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o "
                        + "where exists (select 1 from o.simsClasses c where c.className = 'test')")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }

    @Test
    public void testCollectionTableSourceCount() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o "
                        + "where o.simsClasses._some.className = 'x'")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }

    @Test
    public void testCollectionTableSourceWithExplicitJoin() {
        SQL sql = SQL.begin().sql(
                "select count(*) from SimsCollege o "
                        + "where exists (select 1 from o.simsClasses c "
                        + "where c.collegeId = o.collegeId and c.className = 'test')")
                .end();
        long value = orm().findLong(sql, 0L);
        Assertions.assertEquals(0, value);
    }
}
