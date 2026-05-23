package io.nop.stream.core.model;

import io.nop.stream.core.transformation.Transformation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamModelFingerprint {

    @Test
    void testSameModelProducesSameFingerprint() {
        StreamComponents components = new StreamComponents();
        Map<String, Transformation<?>> transforms = new LinkedHashMap<>();
        StreamModel model1 = new StreamModel(components, transforms);
        StreamModel model2 = new StreamModel(components, transforms);

        StreamModelFingerprint fp1 = model1.computeFingerprint();
        StreamModelFingerprint fp2 = model2.computeFingerprint();

        assertEquals(fp1, fp2);
        assertEquals(fp1.hashCode(), fp2.hashCode());
    }

    @Test
    void testDifferentRequirementsProduceDifferentFingerprint() {
        StreamComponents c1 = new StreamComponents();
        c1.addRequirement(StreamRequirement.STATEFUL_PROCESSING);

        StreamComponents c2 = new StreamComponents();
        c2.addRequirement(StreamRequirement.DISTRIBUTED_EXECUTION);

        Map<String, Transformation<?>> empty = new LinkedHashMap<>();
        StreamModelFingerprint fp1 = new StreamModel(c1, empty).computeFingerprint();
        StreamModelFingerprint fp2 = new StreamModel(c2, empty).computeFingerprint();

        assertNotEquals(fp1, fp2);
    }

    @Test
    void testIsCompatibleWithSameVersion() {
        StreamModel model = new StreamModel(new StreamComponents(), new LinkedHashMap<>());
        StreamModelFingerprint fp1 = model.computeFingerprint();
        StreamModelFingerprint fp2 = model.computeFingerprint();

        assertTrue(fp1.isCompatibleWith(fp2));
    }

    @Test
    void testIsNotCompatibleWithDifferentDag() {
        StreamComponents c = new StreamComponents();
        Map<String, Transformation<?>> t1 = new LinkedHashMap<>();
        t1.put("a", null);

        Map<String, Transformation<?>> t2 = new LinkedHashMap<>();
        t2.put("b", null);

        StreamModelFingerprint fp1 = new StreamModel(c, t1).computeFingerprint();
        StreamModelFingerprint fp2 = new StreamModel(c, t2).computeFingerprint();

        assertFalse(fp1.isCompatibleWith(fp2));
    }
}
