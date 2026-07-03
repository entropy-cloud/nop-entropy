package io.nop.job.dao.helper;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared query-building utilities for nop-job store layer.
 */
public final class JobQueryHelper {

    private JobQueryHelper() {
    }

    /**
     * Append a partition-range filter to a {@link QueryBean}. If {@code partitions} is null or
     * empty, no filter is added. Each {@link IntRangeBean} in the set becomes a
     * {@code between} predicate on {@code partitionIndex}, combined with OR.
     *
     * @param query      the query to augment (must not be null)
     * @param partitions the partition range set; null or empty means no filtering
     * @param partitionProp the property name for the partition index column
     */
    public static void addPartitionFilter(QueryBean query, IntRangeSet partitions, String partitionProp) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }

        List<TreeBean> rangeFilters = new ArrayList<>();
        for (IntRangeBean range : partitions.getRanges()) {
            rangeFilters.add(FilterBeans.between(partitionProp, range.getOffset(), range.getLast()));
        }
        query.addFilter(FilterBeans.or(rangeFilters));
    }
}
