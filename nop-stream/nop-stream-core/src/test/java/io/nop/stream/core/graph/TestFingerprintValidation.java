package io.nop.stream.core.graph;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.model.StreamRequirement;
import io.nop.stream.core.transformation.Transformation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestFingerprintValidation {

    @Test
    void testSameFingerprintIsCompatible() {
        StreamComponents components = new StreamComponents();
        Map<String, Transformation<?>> transforms = new LinkedHashMap<>();
        StreamModel model = new StreamModel(components, transforms);
        StreamModelFingerprint fp1 = model.computeFingerprint();
        StreamModelFingerprint fp2 = model.computeFingerprint();

        assertTrue(fp1.isCompatibleWith(fp2));
        assertTrue(fp2.isCompatibleWith(fp1));
    }

    @Test
    void testNullFingerprintIsIncompatible() {
        StreamComponents components = new StreamComponents();
        Map<String, Transformation<?>> transforms = new LinkedHashMap<>();
        StreamModel model = new StreamModel(components, transforms);
        StreamModelFingerprint fp = model.computeFingerprint();

        assertFalse(fp.isCompatibleWith(null));
    }

    @Test
    void testDifferentRequirementsIncompatible() {
        StreamComponents c1 = new StreamComponents();
        c1.addRequirement(StreamRequirement.STATEFUL_PROCESSING);

        StreamComponents c2 = new StreamComponents();
        c2.addRequirement(StreamRequirement.AT_LEAST_ONCE);

        Map<String, Transformation<?>> empty = new LinkedHashMap<>();
        StreamModelFingerprint fp1 = new StreamModel(c1, empty).computeFingerprint();
        StreamModelFingerprint fp2 = new StreamModel(c2, empty).computeFingerprint();

        assertFalse(fp1.isCompatibleWith(fp2));
    }

    @Test
    void testFingerprintIsDeterministic() {
        StreamComponents c = new StreamComponents();
        c.addRequirement(StreamRequirement.STATEFUL_PROCESSING);
        c.addRequirement(StreamRequirement.DISTRIBUTED_EXECUTION);

        StreamModel model1 = new StreamModel(c, new LinkedHashMap<>());
        StreamModel model2 = new StreamModel(c, new LinkedHashMap<>());

        assertEquals(model1.computeFingerprint(), model2.computeFingerprint());
        assertEquals(model1.computeFingerprint().hashCode(), model2.computeFingerprint().hashCode());
    }

    @Test
    void testPartitionedPlanGeneratorValidatesFingerprint() {
        StreamComponents components = new StreamComponents();
        components.addRequirement(StreamRequirement.STATEFUL_PROCESSING);
        Map<String, Transformation<?>> transforms = new LinkedHashMap<>();
        StreamModel model = new StreamModel(components, transforms);
        StreamModelFingerprint fp = model.computeFingerprint();

        JobGraph jobGraph = new JobGraph("test-job");
        jobGraph.setStreamModel(model);

        PartitionedPlanGenerator generator = new PartitionedPlanGenerator();
        assertDoesNotThrow(() -> generator.generate(jobGraph, fp));
    }

    @Test
    void testMismatchedFingerprintThrows() {
        StreamComponents components = new StreamComponents();
        Map<String, Transformation<?>> transforms = new LinkedHashMap<>();
        StreamModel model = new StreamModel(components, transforms);

        StreamComponents differentComponents = new StreamComponents();
        differentComponents.addRequirement(StreamRequirement.STATEFUL_PROCESSING);
        StreamModel differentModel = new StreamModel(differentComponents, transforms);

        JobGraph jobGraph = new JobGraph("test-job");
        jobGraph.setStreamModel(model);

        StreamModelFingerprint differentFp = differentModel.computeFingerprint();

        PartitionedPlanGenerator generator = new PartitionedPlanGenerator();
        assertThrows(StreamException.class, () -> generator.generate(jobGraph, differentFp));
    }
}
