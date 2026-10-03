/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.transformation;

import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6: unit tests for the {@link UnionTransformation} and the {@code DataStream.union}
 * API, plus the StreamGraph-level union branch (union node + one StreamEdge per input)
 * and the duplicate stream-id disambiguation for self-union topologies.
 */
public class TestUnionTransformation {

    @Test
    public void testUnionApiBuildsUnionTransformationWithAllInputs() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        DataStream<Integer> s1 = env.fromElements(1, 2);
        DataStream<Integer> s2 = env.fromElements(3, 4);

        DataStream<Integer> merged = s1.union(s2);

        Transformation<?> transform = ((DataStreamImpl<Integer>) merged).getTransformation();
        assertTrue(transform instanceof UnionTransformation,
                "union() must produce a UnionTransformation, got " + transform.getClass().getName());
        UnionTransformation<?> union = (UnionTransformation<?>) transform;
        assertEquals(2, union.getUnionInputs().size());
        assertEquals(2, union.getInputs().size());
        assertSame(((DataStreamImpl<Integer>) s1).getTransformation(), union.getUnionInputs().get(0));
        assertSame(((DataStreamImpl<Integer>) s2).getTransformation(), union.getUnionInputs().get(1));
    }

    @Test
    public void testUnionWithNoArgumentsFailsFast() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        // The varargs form accepts an empty array — must be rejected, not merged as zero.
        StreamException e = assertThrows(StreamException.class,
                () -> env.<Integer>fromElements(1).union(new DataStream[0]));
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testUnionWithNullElementFailsFast() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        StreamException e = assertThrows(StreamException.class,
                () -> env.<Integer>fromElements(1).union((DataStream<Integer>) null));
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testStreamGraphUnionNodeWithPerInputEdges() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        DataStream<Integer> merged = env.fromElements(1, 2).union(env.fromElements(3, 4));
        UnionTransformation<?> union = (UnionTransformation<?>)
                ((DataStreamImpl<Integer>) merged).getTransformation();

        StreamGraph graph = new StreamGraphGenerator()
                .generate(Collections.singletonList(union));

        // exactly one union node, registered as union
        List<Integer> unionIds = graph.getUnionIDs();
        assertEquals(1, unionIds.size(), "exactly one union node expected");

        // one StreamEdge per input into the union node
        long edgesIntoUnion = graph.getAllStreamEdges().values().stream()
                .flatMap(List::stream)
                .filter(e -> e.getTargetId() == unionIds.get(0).intValue()).count();
        assertEquals(2, edgesIntoUnion, "union node must have one StreamEdge per input");
    }

    @Test
    public void testSelfUnionStreamIdsDisambiguated() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        DataStream<Integer> single = env.fromElements(1, 2);

        DataStream<Integer> merged = single.union(single);
        UnionTransformation<?> union = (UnionTransformation<?>)
                ((DataStreamImpl<Integer>) merged).getTransformation();

        StreamGraph graph = new StreamGraphGenerator()
                .generate(Collections.singletonList(union));

        // The two parallel edges must not collapse into one registry entry: both are
        // registered and the duplicate carries an ordinal suffix.
        Map<String, ?> streams = graph.getStreamModel().getComponents().getStreams();
        Set<String> streamIds = streams.keySet();
        assertEquals(2, streamIds.size(),
                "self-union must register two distinct stream entries, ids=" + streamIds);
        assertTrue(streamIds.stream().anyMatch(id -> id.matches(".*#2")),
                "the duplicated edge key must carry a #2 ordinal suffix, ids=" + streamIds);
    }
}
