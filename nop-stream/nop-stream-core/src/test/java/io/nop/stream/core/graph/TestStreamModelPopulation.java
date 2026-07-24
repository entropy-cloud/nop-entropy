package io.nop.stream.core.graph;

import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamOperatorFactory;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.model.StreamRequirement;

class TestStreamModelPopulation {

    @Test
    void testStreamComponentsPopulatedAfterBuildStreamGraph() {
        StreamGraphGenerator generator = new StreamGraphGenerator();
        SourceTransformation<String> source = createSource("src", 1);
        SinkTransformation<String> sink = createSink(source, "snk", 1);

        List<Transformation<?>> transforms = new ArrayList<>();
        transforms.add(sink);
        StreamGraph streamGraph = generator.generate(transforms);

        StreamModel model = streamGraph.getStreamModel();
        assertNotNull(model, "StreamModel must be set on StreamGraph");

        StreamComponents components = model.getComponents();
        assertNotNull(components, "StreamComponents must be present");

        assertFalse(components.getTransforms().isEmpty(), "Transforms map must not be empty");
        assertEquals(2, components.getTransforms().size(), "Should have 2 transforms registered");

        assertTrue(components.getTransforms().containsKey(String.valueOf(source.getId())),
                "Source transform should be registered");
        assertTrue(components.getTransforms().containsKey(String.valueOf(sink.getId())),
                "Sink transform should be registered");
    }

    @Test
    void testEmptyTransformationsProducesEmptyComponents() {
        StreamGraphGenerator generator = new StreamGraphGenerator();
        StreamGraph streamGraph = generator.generate(Collections.emptyList());

        StreamModel model = streamGraph.getStreamModel();
        assertNotNull(model, "StreamModel must be set even for empty transformations");

        StreamComponents components = model.getComponents();
        assertTrue(components.getTransforms().isEmpty(), "Transforms should be empty");
        assertTrue(components.getCheckpointParticipants().isEmpty(), "Checkpoint participants should be empty");
    }

    @Test
    void testCheckpointParticipantRegistered() {
        StreamGraphGenerator generator = new StreamGraphGenerator();
        SourceTransformation<String> source = createSource("src", 1);
        SinkTransformation<String> sink = createCheckpointParticipantSink(source, "cp-sink", 1);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));

        StreamComponents components = streamGraph.getStreamModel().getComponents();
        String sinkId = String.valueOf(sink.getId());
        assertTrue(components.getCheckpointParticipants().contains(sinkId),
                "CheckpointParticipant sink should be registered");
    }

    @Test
    void testStreamModelPropagatesToStreamGraph() {
        StreamGraphGenerator generator = new StreamGraphGenerator();
        SourceTransformation<String> source = createSource("src", 1);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(source));

        assertNotNull(streamGraph.getStreamModel());
        assertFalse(streamGraph.getStreamModel().getComponents().getTransforms().isEmpty());
    }

    @Test
    void testStreamModelPropagatesToJobGraph() {
        StreamGraphGenerator generator = new StreamGraphGenerator();
        SourceTransformation<String> source = createSource("src", 1);
        SinkTransformation<String> sink = createSink(source, "snk", 1);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));
        assertNotNull(streamGraph.getStreamModel());

        JobGraphGenerator jobGraphGen = new JobGraphGenerator();
        JobGraph jobGraph = jobGraphGen.generate(streamGraph);

        assertNotNull(jobGraph.getStreamModel());
        assertEquals(streamGraph.getStreamModel().getComponents().getTransforms().size(),
                jobGraph.getStreamModel().getComponents().getTransforms().size());
    }

    private static SourceTransformation<String> createSource(String name, int parallelism) {
        TypeInformation<String> typeInfo = createStringTypeInfo();
        SourceFunction<String> fn = new TestSourceFunction();
        return new SourceTransformation<>(name, fn, typeInfo, parallelism);
    }

    private static <T> SinkTransformation<T> createSink(Transformation<T> input, String name, int parallelism) {
        TypeInformation<Void> voidInfo = createVoidTypeInfo();
        SinkFunction<T> fn = new TestSinkFunction<>();
        return new SinkTransformation<>(input, name, fn, voidInfo, parallelism);
    }

    private static <T> SinkTransformation<T> createCheckpointParticipantSink(
            Transformation<T> input, String name, int parallelism) {
        TypeInformation<Void> voidInfo = createVoidTypeInfo();
        SinkFunction<T> fn = new TestCheckpointParticipantSinkFunction<>();
        return new SinkTransformation<>(input, name, fn, voidInfo, parallelism);
    }

    private static TypeInformation<String> createStringTypeInfo() {
        return new TypeInformation<String>() {
            @Override
            public Class<String> getTypeClass() {
                return String.class;
            }
        };
    }

    private static TypeInformation<Void> createVoidTypeInfo() {
        return new TypeInformation<Void>() {
            @Override
            public Class<Void> getTypeClass() {
                return Void.class;
            }
        };
    }

    private static class TestSourceFunction implements SourceFunction<String> {
        @Override
        public void run(SourceContext<String> ctx) {
        }

        @Override
        public void cancel() {
        }
    }

    private static class TestSinkFunction<T> implements SinkFunction<T> {
        @Override
        public void consume(T value) {
        }
    }

    private static class TestCheckpointParticipantSinkFunction<T> implements SinkFunction<T>, CheckpointParticipant {
        @Override
        public void consume(T value) {
        }

        @Override
        public TaskStateSnapshot saveState(long epochId) {
            return null;
        }

        @Override
        public void prepareCommit(long epochId) {
        }

        @Override
        public void finishCommit(long epochId, boolean success) {
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
        }
    }
}
