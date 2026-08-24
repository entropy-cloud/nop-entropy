/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.aggregator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestAverageAggregator {

    @Test
    public void testAverageIgnoresNull() {
        AverageAggregator agg = new AverageAggregator();
        agg.update(1);
        agg.update(null);
        agg.update(3);
        // null不计入分母：AVG(1,null,3)=2，与SQL语义一致
        assertEquals(2.0, agg.getValue().doubleValue(), 0.0);
    }

    @Test
    public void testAverageAllNull() {
        AverageAggregator agg = new AverageAggregator();
        agg.update(null);
        agg.update(null);
        // 全null输入返回null，与MinAggregator/MaxAggregator的行为一致
        assertNull(agg.getValue());
    }

    @Test
    public void testAverageReset() {
        AverageAggregator agg = new AverageAggregator();
        agg.update(2);
        agg.update(4);
        assertEquals(3.0, agg.getValue().doubleValue(), 0.0);
        agg.reset();
        assertNull(agg.getValue());
    }
}
