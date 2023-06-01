package io.nop.xlang.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFeatureConditionEvaluator {
    @Test
    public void testOr() {
        boolean b = new FeatureConditionEvaluator().evaluate(null, "nop.rpc.service-mesh.enabled or nop.rpc.mock-all");
        assertFalse(b);
    }
}
