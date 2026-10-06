/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;

import java.util.List;

/**
 * M1 前置验证（设计 §4.4）：nop-stream 多 sink 管线是否可编译为部署图。
 *
 * <p>设计裁定：M&gt;1 fan-out 是未验证能力（component-roadmap C2 检查单"多 sink 正确编译/
 * 跨 Task 数据交换"未勾选），列为 WI9 实施前置验证项；验证不通过的回退形态 = 每目标分片一个
 * 单 sink 链作业。本探测验证<b>编译面</b>（多 sink transformation → 部署顶点）；每顶点
 * checkpoint/2PC 对齐仍属未验证面，因此编排器 v1 缺省采用回退形态，fan-out 仅在探测通过
 * 且配置显式 opt-in 时启用。</p>
 */
public final class MultiSinkTopologyProbe {
    private static final Logger LOG = LoggerFactory.getLogger(MultiSinkTopologyProbe.class);

    private MultiSinkTopologyProbe() {
    }

    /**
     * 编译探测结果。
     */
    public static final class ProbeResult {
        private final boolean compilable;
        private final int sinkVertexCount;
        private final String detail;

        ProbeResult(boolean compilable, int sinkVertexCount, String detail) {
            this.compilable = compilable;
            this.sinkVertexCount = sinkVertexCount;
            this.detail = detail;
        }

        public boolean isCompilable() {
            return compilable;
        }

        public int getSinkVertexCount() {
            return sinkVertexCount;
        }

        public String getDetail() {
            return detail;
        }
    }

    /**
     * 构造一个包含 source + N 个 sink 的图并编译为 JobGraph（不执行），返回每个 sink 是否
     * 成为独立部署顶点。任何编译异常 = 探测不通过（回退形态接管），不向上抛。
     */
    public static ProbeResult probe(int sinkCount) {
        if (sinkCount <= 1)
            return new ProbeResult(true, sinkCount, "single-sink topology (verified form)");
        try {
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            io.nop.stream.core.datastream.DataStreamSource<Integer> source =
                    env.fromCollection(List.of(1, 2, 3));
            CountingSinkFunction[] sinks = new CountingSinkFunction[sinkCount];
            for (int i = 0; i < sinkCount; i++) {
                sinks[i] = new CountingSinkFunction();
                source.filter(v -> true).sink(sinks[i]);
            }
            io.nop.stream.core.jobgraph.JobGraph graph = env.buildJobGraph("migration-multisink-probe");
            int vertices = graph.getVertices().size();
            return new ProbeResult(vertices >= sinkCount + 1, vertices,
                    "compiled vertices=" + vertices + " for sinkCount=" + sinkCount);
        } catch (Exception e) {
            LOG.warn("nop.orm.migration.multisink-probe-failed:compiling fan-out topology failed", e);
            return new ProbeResult(false, 0, "compile failed (see nop.orm.migration.multisink-probe-failed log for stack trace)");
        }
    }

    /**
     * 判定 M&gt;1 时能否走 fan-out 形态（编译探测通过 + 显式 opt-in 缺省关闭）。
     */
    public static boolean fanoutEnabled(int targetCount, boolean explicitOptIn) {
        if (targetCount <= 1)
            return true;
        return explicitOptIn && probe(targetCount).isCompilable();
    }

    private static final class CountingSinkFunction implements SinkFunction<Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Integer value) {
            // probe-only sink：不产生任何外部副作用
        }
    }
}
