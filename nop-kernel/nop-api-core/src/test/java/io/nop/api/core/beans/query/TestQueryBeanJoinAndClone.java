/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.query;

import io.nop.api.core.ApiConstants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestQueryBeanJoinAndClone {

    @Test
    public void testOrderFieldBeanCloneKeepsDirection() {
        OrderFieldBean descBean = OrderFieldBean.desc("name");
        // 克隆语义应保持排序方向不变（反转语义属于reverse()）
        assertTrue(descBean.cloneInstance().isDesc(), "clone of desc field should stay desc");

        OrderFieldBean ascBean = OrderFieldBean.asc("name");
        assertFalse(ascBean.cloneInstance().isDesc(), "clone of asc field should stay asc");

        OrderFieldBean nullsFirst = OrderFieldBean.orderBy("name", true);
        nullsFirst.setNullsFirst(true);
        assertEquals(Boolean.TRUE, nullsFirst.cloneInstance().getNullsFirst());
    }

    @Test
    public void testQueryBeanCloneKeepsOrderByDirection() {
        QueryBean query = new QueryBean();
        query.addOrderField(OrderFieldBean.desc("name"));
        query.addOrderField(OrderFieldBean.asc("age"));

        QueryBean clone = query.cloneInstance();
        assertTrue(clone.getOrderBy().get(0).isDesc());
        assertFalse(clone.getOrderBy().get(1).isDesc());
    }

    @Test
    public void testRightJoinUsesRightJoinType() {
        QueryBean query = new QueryBean();
        query.setSourceName("test");
        query.rightJoin("other", "o", "a,b", "x,y");

        QuerySourceBean join = query.getJoinByAlias("o");
        // rightJoin 不能静默降级为 leftJoin
        assertEquals(ApiConstants.JOIN_TYPE_RIGHT_JOIN, join.getJoinType());
        assertFalse(ApiConstants.JOIN_TYPE_LEFT_JOIN.equals(join.getJoinType()));
    }

    @Test
    public void testLeftAndInnerJoinTypes() {
        QueryBean query = new QueryBean();
        query.leftJoin("other", "l", "a", "x");
        assertEquals(ApiConstants.JOIN_TYPE_LEFT_JOIN, query.getJoinByAlias("l").getJoinType());

        query.innerJoin("other", "i", "a", "x");
        assertEquals(ApiConstants.JOIN_TYPE_INNER_JOIN, query.getJoinByAlias("i").getJoinType());
    }

    @Test
    public void testAddJoinDimFieldsBranch() {
        QueryBean query = new QueryBean();
        query.setSourceName("test");
        // 查询的维度字段与join的左侧字段一致时，应走dimFields结构化路径而不是退化为conditions
        query.setDimFields(Arrays.asList("a", "b"));
        query.leftJoin("other", "o", "a,b", "x,y");

        QuerySourceBean join = query.getJoinByAlias("o");
        assertEquals(Arrays.asList("x", "y"), join.getDimFields());
        assertNull(join.getConditions());
    }

    @Test
    public void testAddJoinConditionsWhenNotDimFields() {
        QueryBean query = new QueryBean();
        query.setSourceName("test");
        query.setDimFields(Arrays.asList("a"));
        query.leftJoin("other", "o", "a,b", "x,y");

        QuerySourceBean join = query.getJoinByAlias("o");
        assertNull(join.getDimFields());
        assertEquals(2, join.getConditions().size());
        assertEquals("a", join.getConditions().get(0).getLeftField());
        assertEquals("x", join.getConditions().get(0).getRightField());
    }
}
