/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table completeness gate (I1, 门禁基座): the change-type method table in
 * {@code ai-dev/audits/nop-stream-invariants/gate-inventory.json} must be in two-way exact
 * equality with live reflection for every class in the gate scope of this module
 * (nop-stream-core). A new change-type method not added to the table is red; a table entry
 * pointing at a method that no longer exists is red.
 *
 * <p>The classifier is the mechanical I0 §4.1 form shared with the mjs scanner
 * ({@code ai-dev/tools/check-nop-stream-invariants.mjs}) and the runtime/cep module tests.
 * Positive/negative fixtures are exercised via {@link #testDirectionA_ghostTableEntryIsRed()}
 * and {@link #testDirectionB_liveMethodNotInTableIsRed()}.
 */
public class TestInvariantTableCompleteness {

    static final String MODULE = "nop-stream-core";

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

    @Test
    void testDirectionA_ghostTableEntryIsRed() {
        Set<String> table = Set.of("realMethod", "ghostMethod");
        Set<String> live = Set.of("realMethod");
        List<String> violations = InvariantTableCompleteness.findViolations("fixture.A", table, live);
        assertTrue(violations.stream().anyMatch(v -> v.contains("ghostMethod") && v.contains("ghost")),
                "ghost table entry must be reported as red, got: " + violations);
    }

    @Test
    void testDirectionB_liveMethodNotInTableIsRed() {
        Set<String> table = Set.of("realMethod");
        Set<String> live = Set.of("realMethod", "newMethod");
        List<String> violations = InvariantTableCompleteness.findViolations("fixture.B", table, live);
        assertTrue(violations.stream().anyMatch(v -> v.contains("newMethod") && v.contains("missing from table")),
                "live method missing from table must be reported as red, got: " + violations);
    }

    @Test
    void testEmptyDiffIsGreen() {
        Set<String> table = Set.of("a", "b");
        Set<String> live = Set.of("a", "b");
        List<String> violations = InvariantTableCompleteness.findViolations("fixture.C", table, live);
        assertTrue(violations.isEmpty(), "identical sets must be green, got: " + violations);
    }
}
