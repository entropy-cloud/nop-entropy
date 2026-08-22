/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmErrors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.isNull;
import static io.nop.api.core.beans.FilterBeans.not;
import static io.nop.api.core.beans.FilterBeans.or;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void testPropGt() {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.propGt("f1", "f2"));
        SQL sql = DaoQueryHelper.queryToSelectObjectSql("Test", query);
        assertEquals("select o from Test as o \n" +
                " where o.f1 > o.f2", sql.getText());
    }

    /**
     * select字段的owner与字段名必须与group by/order by一样经过合法性校验
     */
    @Test
    public void testQueryToSelectFieldsSqlRejectsInvalidField() {
        QueryBean query = new QueryBean();
        query.setSourceName("io.nop.app.SimsCollege");
        query.addField(QueryFieldBean.forField("bad field!"));

        NopException err = assertThrows(NopException.class, () -> DaoQueryHelper.queryToSelectFieldsSql(query, null));
        assertEquals(OrmErrors.ERR_ORM_INVALID_FIELD_NAME.getErrorCode(), err.getErrorCode());

        QueryBean query2 = new QueryBean();
        query2.setSourceName("io.nop.app.SimsCollege");
        query2.addField(QueryFieldBean.subField("bad owner!", "collegeId"));
        NopException err2 = assertThrows(NopException.class, () -> DaoQueryHelper.queryToSelectFieldsSql(query2, null));
        assertEquals(OrmErrors.ERR_ORM_INVALID_OWNER_NAME.getErrorCode(), err2.getErrorCode());
    }

    private PageBean<SimsCollege> findPageByCursor(String cursor, int limit, boolean findPrev, TreeBean filter) {
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        QueryBean query = new QueryBean();
        query.setLimit(limit);
        query.setCursor(cursor);
        query.setFindPrev(findPrev);
        query.setFilter(filter);
        PageBean<SimsCollege> page = new PageBean<>();
        dao.findPageAndReturnCursor(query, page);
        return page;
    }

    // 注意: DaoQueryHelper.queryToFindPrevSql 在 filter 为空时会漏生成 where 关键字(独立缺陷)，
    // 因此 findPrev 场景统一附加 filter 以聚焦 findPageAndReturnCursor 自身的逻辑
    private static final TreeBean COLLEGE_ID_GE_100 = FilterBeans.ge(SimsCollege.PROP_NAME_collegeId, "100");

    @Test
    public void testFindPageCursorFullPage() {
        insertColleges(100, 110);

        PageBean<SimsCollege> page = findPageByCursor("102", 3, false, null);

        assertEquals(3, page.getItems().size());
        assertEquals("103", page.getItems().get(0).getCollegeId());
        assertEquals("105", page.getItems().get(2).getCollegeId());
        assertTrue(page.getHasNext());
        assertTrue(page.getHasPrev());
        assertEquals("105", page.getNextCursor());
        assertEquals("103", page.getPrevCursor());
    }

    @Test
    public void testFindPageCursorLastPageKeepsAllItems() {
        insertColleges(100, 110);

        // 游标后只剩 3 条（不足 limit+1），不能丢掉最后一条
        PageBean<SimsCollege> page = findPageByCursor("107", 3, false, null);

        assertEquals(3, page.getItems().size());
        assertEquals("108", page.getItems().get(0).getCollegeId());
        assertEquals("110", page.getItems().get(2).getCollegeId());
        assertFalse(page.getHasNext());
        assertTrue(page.getHasPrev());
        assertEquals(OrmConstants.ID_NULL, page.getNextCursor());
        assertEquals("108", page.getPrevCursor());
    }

    @Test
    public void testFindPageCursorPastEndReturnsEmptyPage() {
        insertColleges(100, 110);

        // 游标之后无记录：应返回空页而不是抛 IndexOutOfBoundsException
        PageBean<SimsCollege> page = findPageByCursor("110", 3, false, null);

        assertTrue(page.getItems().isEmpty());
        assertFalse(page.getHasNext());
        assertTrue(page.getHasPrev());
        assertEquals(OrmConstants.ID_NULL, page.getNextCursor());
        assertEquals(OrmConstants.ID_NULL, page.getPrevCursor());
    }

    @Test
    public void testFindPageCursorFindPrevFullPage() {
        insertColleges(100, 110);

        PageBean<SimsCollege> page = findPageByCursor("104", 3, true, COLLEGE_ID_GE_100);

        assertEquals(3, page.getItems().size());
        assertEquals("101", page.getItems().get(0).getCollegeId());
        assertEquals("103", page.getItems().get(2).getCollegeId());
        assertTrue(page.getHasPrev());
        assertTrue(page.getHasNext());
        assertEquals("101", page.getPrevCursor());
        assertEquals("103", page.getNextCursor());
    }

    @Test
    public void testFindPageCursorFindPrevShortPage() {
        insertColleges(100, 110);

        // 游标前只有 '100','101' 两条（不足 limit+1），不能丢数据
        PageBean<SimsCollege> page = findPageByCursor("102", 3, true, COLLEGE_ID_GE_100);

        assertEquals(2, page.getItems().size());
        assertEquals("100", page.getItems().get(0).getCollegeId());
        assertEquals("101", page.getItems().get(1).getCollegeId());
        assertFalse(page.getHasPrev());
        assertTrue(page.getHasNext());
        assertEquals(OrmConstants.ID_NULL, page.getPrevCursor());
        assertEquals("101", page.getNextCursor());
    }
}
