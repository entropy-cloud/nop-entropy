/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.assigner;

import io.nop.api.core.beans.IntRangeBean;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestPartitionAssigner {
    @Test
    public void testAssign() {
        List<ServiceInstance> servers = new ArrayList<>();
        servers.add(newServer("a", 100));
        servers.add(newServer("b", 50));
        servers.add(newServer("c", 100));

        WeightedPartitionAssigner assigner = new WeightedPartitionAssigner();
        List<IntRangeBean> partitions = assigner.assignPartitions(IntRangeBean.shortRange(), servers);
        System.out.println(StringHelper.join(partitions, "\n"));
        assertEquals(32767, partitions.stream().reduce(0, (a, b) -> a + b.getLimit(), Integer::sum));
        assertEquals("0,13107|13107,6553|19660,13107", StringHelper.join(partitions, "|"));
        partitions = assigner.assignPartitions(IntRangeBean.intRange(1, 5), servers);
        System.out.println(StringHelper.join(partitions, "\n"));
        assertEquals(5, partitions.stream().reduce(0, (a, b) -> a + b.getLimit(), Integer::sum));
        assertEquals("1,2|3,1|4,2", StringHelper.join(partitions, "|"));
    }

    ServiceInstance newServer(String name, int weight) {
        ServiceInstance server = new ServiceInstance();
        server.setServiceName(name);
        server.setWeight(weight);
        return server;
    }
}
