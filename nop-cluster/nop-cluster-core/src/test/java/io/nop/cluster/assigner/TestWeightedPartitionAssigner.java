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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestWeightedPartitionAssigner {

    private WeightedPartitionAssigner assigner = new WeightedPartitionAssigner();

    private ServiceInstance server(String instanceId, int weight) {
        ServiceInstance instance = new ServiceInstance();
        instance.setInstanceId(instanceId);
        instance.setWeight(weight);
        return instance;
    }

    @Test
    public void testEmptyServers() {
        List<ServiceInstance> servers = new ArrayList<>();
        List<IntRangeBean> ranges = assigner.assignPartitions(IntRangeBean.intRange(0, 100), servers);
        assertTrue(ranges.isEmpty());
    }

    @Test
    public void testSingleServer() {
        List<ServiceInstance> servers = List.of(server("s1", 100));
        IntRangeBean range = IntRangeBean.intRange(0, 100);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(1, ranges.size());
        assertEquals(0, ranges.get(0).getOffset());
        assertEquals(100, ranges.get(0).getLimit());
    }

    @Test
    public void testTwoServersEqualWeight() {
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 100);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(2, ranges.size());
        // s1: [0, 50)
        assertEquals(0, ranges.get(0).getOffset());
        assertEquals(50, ranges.get(0).getLimit());
        // s2: [50, 100)
        assertEquals(50, ranges.get(1).getOffset());
        assertEquals(50, ranges.get(1).getLimit());

        // 验证覆盖完整范围
        assertCoverage(ranges, range);
    }

    @Test
    public void testThreeServersEqualWeight() {
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100),
                server("s3", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 100);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(3, ranges.size());
        printRanges("3 servers, 100 partitions", ranges);

        // 验证覆盖完整范围
        assertCoverage(ranges, range);
    }

    @Test
    public void testShortHashRange() {
        // 模拟 SHORT_HASH_RANGE: [0, 32767)
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100),
                server("s3", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, Short.MAX_VALUE); // [0, 32767)
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(3, ranges.size());
        printRanges("3 servers, SHORT_HASH_RANGE", ranges);

        // 验证覆盖完整范围
        assertCoverage(ranges, range);
    }

    @Test
    public void testSinglePartition() {
        // 只有一个分区，但有多个服务器
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 1); // 只有 [0]
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(2, ranges.size());
        printRanges("2 servers, 1 partition", ranges);

        // 第一个服务器得到唯一分区
        assertEquals(0, ranges.get(0).getOffset());
        assertEquals(1, ranges.get(0).getLimit());

        // 第二个服务器得到空分区 - 检查 offset 是否合理
        System.out.println("Empty range offset: " + ranges.get(1).getOffset());
        // 问题：range.getEnd() - 1 = 0，这个 offset 是合理的
    }

    @Test
    public void testMoreServersThanPartitions() {
        // 服务器数量 > 分区数量
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100),
                server("s3", 100),
                server("s4", 100),
                server("s5", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 2); // 只有 2 个分区
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(5, ranges.size());
        printRanges("5 servers, 2 partitions", ranges);

        // 检查 offset >= end 的情况
        int end = range.getEnd();
        for (int i = 0; i < ranges.size(); i++) {
            IntRangeBean r = ranges.get(i);
            System.out.println("Server " + i + ": offset=" + r.getOffset() + ", limit=" + r.getLimit() + ", end=" + (r.getOffset() + r.getLimit()));

            // 如果 offset > end，这是问题
            if (r.getOffset() > end && r.getLimit() == 0) {
                System.out.println("WARNING: offset " + r.getOffset() + " > end " + end);
            }
        }
    }

    @Test
    public void testWeightedDistribution() {
        // 不同权重
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 200),
                server("s3", 300)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 600);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(3, ranges.size());
        printRanges("3 servers, weights 100/200/300", ranges);

        // s1: 100/600 * 600 = 100
        // s2: 200/600 * 600 = 200
        // s3: 300/600 * 600 = 300
        assertEquals(100, ranges.get(0).getLimit());
        assertEquals(200, ranges.get(1).getLimit());
        assertEquals(300, ranges.get(2).getLimit());

        assertCoverage(ranges, range);
    }

    @Test
    public void testSmallRangeWithManyServers() {
        // 小范围 + 多服务器，可能导致某些服务器得到 0 个分区
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100),
                server("s3", 100),
                server("s4", 100),
                server("s5", 100),
                server("s6", 100),
                server("s7", 100),
                server("s8", 100),
                server("s9", 100),
                server("s10", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 3); // 只有 3 个分区
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(10, ranges.size());
        printRanges("10 servers, 3 partitions", ranges);

        // 统计有多少服务器得到 0 个分区
        int zeroCount = 0;
        for (IntRangeBean r : ranges) {
            if (r.getLimit() == 0) {
                zeroCount++;
            }
        }
        System.out.println("Servers with 0 partitions: " + zeroCount);

        assertCoverage(ranges, range);
    }

    @Test
    public void testEmptyRange() {
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 0);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(2, ranges.size());
        // 都是空区间
        assertTrue(ranges.get(0).isEmpty());
        assertTrue(ranges.get(1).isEmpty());
    }

    @Test
    public void testNonZeroOffset() {
        // 非零起始偏移
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(100, 100); // [100, 200)
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(2, ranges.size());
        printRanges("2 servers, [100,200)", ranges);

        // s1: [100, 150)
        assertEquals(100, ranges.get(0).getOffset());
        assertEquals(50, ranges.get(0).getLimit());
        // s2: [150, 200)
        assertEquals(150, ranges.get(1).getOffset());
        assertEquals(50, ranges.get(1).getLimit());

        assertCoverage(ranges, range);
    }

    @Test
    public void testZeroAndNegativeWeight() {
        // 零权重和负权重应该使用默认权重
        List<ServiceInstance> servers = List.of(
                server("s1", 0),    // 使用默认权重 100
                server("s2", -50),  // 使用默认权重 100
                server("s3", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 300);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        assertEquals(3, ranges.size());
        printRanges("3 servers, weights 0/-50/100", ranges);

        // 所有服务器应该各得 100
        assertEquals(100, ranges.get(0).getLimit());
        assertEquals(100, ranges.get(1).getLimit());
        assertEquals(100, ranges.get(2).getLimit());

        assertCoverage(ranges, range);
    }

    @Test
    public void testNoOverlap() {
        // 验证区间不重叠
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 200),
                server("s3", 300)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 600);
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        // 验证非空区间不重叠
        for (int i = 0; i < ranges.size(); i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                IntRangeBean r1 = ranges.get(i);
                IntRangeBean r2 = ranges.get(j);
                if (!r1.isEmpty() && !r2.isEmpty()) {
                    // r1.end <= r2.offset 或 r2.end <= r1.offset
                    boolean noOverlap = r1.getEnd() <= r2.getOffset() || r2.getEnd() <= r1.getOffset();
                    assertTrue(noOverlap,
                            "Ranges should not overlap: [" + r1.getOffset() + "," + r1.getEnd() + ") and [" + r2.getOffset() + "," + r2.getEnd() + ")");
                }
            }
        }
    }

    @Test
    public void testOffsetNotExceedEnd() {
        // 验证分区耗尽后 offset 不超过 end
        List<ServiceInstance> servers = List.of(
                server("s1", 100),
                server("s2", 100),
                server("s3", 100),
                server("s4", 100),
                server("s5", 100)
        );
        IntRangeBean range = IntRangeBean.intRange(0, 2); // 只有 2 个分区
        List<IntRangeBean> ranges = assigner.assignPartitions(range, servers);

        int end = range.getEnd();
        for (IntRangeBean r : ranges) {
            // 空区间的 offset 不应该超过 end
            if (r.isEmpty()) {
                assertTrue(r.getOffset() <= end,
                        "Empty range offset " + r.getOffset() + " should not exceed end " + end);
            }
        }
    }

    private void printRanges(String title, List<IntRangeBean> ranges) {
        System.out.println("=== " + title + " ===");
        for (int i = 0; i < ranges.size(); i++) {
            IntRangeBean r = ranges.get(i);
            System.out.println("  [" + i + "] " + r.getOffset() + "," + r.getLimit() + " => [" + r.getOffset() + ", " + r.getEnd() + ")");
        }
    }

    private void assertCoverage(List<IntRangeBean> ranges, IntRangeBean expected) {
        // 验证所有非空区间覆盖完整范围
        int covered = 0;
        for (IntRangeBean r : ranges) {
            if (!r.isEmpty()) {
                covered += r.getLimit();
            }
        }
        assertEquals(expected.getLimit(), covered,
                "Ranges should cover the full range");
    }
}
