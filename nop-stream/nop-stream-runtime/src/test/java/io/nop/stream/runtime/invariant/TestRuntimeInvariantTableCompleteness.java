/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.invariant;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.nop.stream.core.test.InvariantTableCompleteness;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table completeness gate for the nop-stream-runtime module (I1, 门禁基座): change-type
 * method table in {@code gate-inventory.json} vs live reflection, two-way exact equality.
 * Same classifier as {@code TestInvariantTableCompleteness} in nop-stream-core.
 */
public class TestRuntimeInvariantTableCompleteness {

    static final String MODULE = "nop-stream-runtime";

    static List<String> inventoryClassNames() {
        Map<String, Object> inventory = InvariantTableCompleteness.loadInventory();
        Map<String, Object> classes = InvariantTableCompleteness.moduleClasses(inventory, MODULE);
        return classes.keySet().stream().sorted().collect(java.util.stream.Collectors.toList());
    }

    @ParameterizedTest
    @MethodSource("inventoryClassNames")
    void testTableMatchesLiveCode(String fqcn) throws Exception {
        Map<String, Object> inventory = InvariantTableCompleteness.loadInventory();
        Map<String, Object> classes = InvariantTableCompleteness.moduleClasses(inventory, MODULE);
        List<String> table = InvariantTableCompleteness.tableMethods((Map<String, Object>) classes.get(fqcn));

        Class<?> clazz = Class.forName(fqcn);
        Set<String> live = InvariantTableCompleteness.changeTypeMethodNames(clazz);
        List<String> violations = InvariantTableCompleteness.findViolations(fqcn, Set.copyOf(table), live);
        assertTrue(violations.isEmpty(), "table/live mismatch for " + fqcn + ":\n  "
                + String.join("\n  ", violations));
    }
}
