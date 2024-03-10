/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.isNull;
import static io.nop.api.core.beans.FilterBeans.not;
import static io.nop.api.core.beans.FilterBeans.or;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEntityDaoQuery extends AbstractOrmTestCase {
    @Test
    public void testQuery() {
        insertColleges(100, 102);
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        QueryBean query = new QueryBean();
        TreeBean sqlFilter = new TreeBean();
        sqlFilter.setTagName("sql");
        sqlFilter.setAttr("value", SQL.begin().sql("o.collegeId > '103' ").end());
        query.setFilter(or(eq(SimsCollege.PROP_NAME_collegeId, "100"), sqlFilter));
        List<SimsCollege> list = dao.findPageByQuery(query);
        assertEquals(1, list.size());
    }

    @Test
    public void testSelectNext() {
        insertColleges(100, 110);

        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        SimsCollege college = dao.loadEntityById(100);
        List<SimsCollege> list = dao.findNext(college, isNull(SimsCollege.PROP_NAME_president), null, 1);
        assertEquals(1, list.size());
        assertEquals("101", list.get(0).getCollegeId());
    }

    @Test
    public void testNot() {
        insertColleges(100, 110);

        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        QueryBean query = new QueryBean();
        query.setFilter(not(and(or(eq("collegeId", 1), eq("collegeId", 2), eq("collegeId", 3)),
                or(eq("collegeId", 100), eq("collegeId", 101)))));
        dao.countByQuery(query);
    }
}
