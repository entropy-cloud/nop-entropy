/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
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
