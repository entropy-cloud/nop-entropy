/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 前置验证（设计 §4.4）：多 sink 管线编译探测——2 sink 图能否编译出 ≥3 个部署顶点。
 * 这是 nop-stream component-roadmap C2 未勾选能力的 WI9 实施前置验证项；探测不通过时
 * 编排器以回退形态（每目标分片单 sink 链作业）落地。
 */
public class TestMultiSinkTopologyProbe {

    @Test
    public void testSingleSinkIsVerifiedForm() {
        MultiSinkTopologyProbe.ProbeResult result = MultiSinkTopologyProbe.probe(1);
        assertTrue(result.isCompilable());
        assertEquals(1, result.getSinkVertexCount());
    }

    @Test
    public void testTwoSinkGraphCompilesToDistinctVertices() {
        // 前置验证执行：多 sink transformation 图编译为多 JobVertex 部署图
        MultiSinkTopologyProbe.ProbeResult result = MultiSinkTopologyProbe.probe(2);
        assertTrue(result.isCompilable(),
                "two-sink graph should compile into >=3 vertices, detail: " + result.getDetail());
        assertTrue(result.getSinkVertexCount() >= 3,
                "source + 2 sink vertices expected, got: " + result.getSinkVertexCount());
    }

    @Test
    public void testFanoutRequiresExplicitOptIn() {
        // v1 拓扑裁定：探测即使通过，fan-out 也需显式 opt-in（缺省回退形态——M2 级对齐未验证）
        assertFalse(MultiSinkTopologyProbe.fanoutEnabled(3, false));
        // 单目标分片（M=1）天然是单 sink 链（已验证形态）
        assertTrue(MultiSinkTopologyProbe.fanoutEnabled(1, false));
    }

    @Test
    public void testEnvironmentBuildApiAvailable() {
        // 探测依赖的 buildJobGraph（无副作用编译入口）存在且可用
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.fromCollection(List.of(1, 2, 3)).filter(v -> true)
                .sink(value -> {
                });
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> env.buildJobGraph("probe-self-check"));
    }
}
