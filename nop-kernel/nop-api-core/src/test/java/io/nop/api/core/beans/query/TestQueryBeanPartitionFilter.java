/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestQueryBeanPartitionFilter {

    @Test
    public void testNullPartitionsAddsNoFilter() {
        QueryBean query = new QueryBean();
        query.addPartitionFilter(null, "partitionIndex");
        assertNull(query.getFilter());
    }

    @Test
    public void testEmptyPartitionsAddsNoFilter() {
        QueryBean query = new QueryBean();
        query.addPartitionFilter(IntRangeSet.parse(""), "partitionIndex");
        assertNull(query.getFilter());
    }

    @Test
    public void testSingleRangeAddsBetweenFilter() {
        QueryBean query = new QueryBean();
        query.addPartitionFilter(IntRangeSet.parse("10,20"), "partitionIndex");

        TreeBean filter = query.getFilter();
        assertNotNull(filter);
        assertEquals(FilterBeanConstants.FILTER_OP_BETWEEN, filter.getTagName());
        assertEquals("partitionIndex", filter.getAttr(FilterBeanConstants.FILTER_ATTR_NAME));
        assertEquals(10, filter.getAttr(FilterBeanConstants.FILTER_ATTR_MIN));
        assertEquals(29, filter.getAttr(FilterBeanConstants.FILTER_ATTR_MAX));
    }

    @Test
    public void testMultipleRangesAddsOrFilter() {
        QueryBean query = new QueryBean();
        query.addPartitionFilter(IntRangeSet.parse("10,20|50,5"), "partitionIndex");

        TreeBean filter = query.getFilter();
        assertNotNull(filter);
        assertEquals(FilterBeanConstants.FILTER_OP_OR, filter.getTagName());
        assertEquals(2, filter.getChildren().size());
        for (TreeBean child : filter.getChildren()) {
            assertEquals(FilterBeanConstants.FILTER_OP_BETWEEN, child.getTagName());
            assertEquals("partitionIndex", child.getAttr(FilterBeanConstants.FILTER_ATTR_NAME));
        }
    }

    @Test
    public void testExistingFilterIsAppended() {
        QueryBean query = new QueryBean();
        query.addFilter(new TreeBean(FilterBeanConstants.FILTER_OP_GT).attr(FilterBeanConstants.FILTER_ATTR_NAME, "status")
                .attr(FilterBeanConstants.FILTER_ATTR_VALUE, 1));
        query.addPartitionFilter(IntRangeSet.parse("10,20"), "partitionIndex");

        TreeBean filter = query.getFilter();
        assertNotNull(filter);
        assertEquals(FilterBeanConstants.FILTER_OP_AND, filter.getTagName());
        assertEquals(2, filter.getChildren().size());
    }
}
