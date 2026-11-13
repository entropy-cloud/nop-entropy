/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.42 R4-02 W-04 (1.1-C5-012, E2 泛在钉 — S7 结构钉): MemoryBudget is
 * RECORD-ONLY, NOT ENFORCED (adjudication B.2, declaration-downgrade form). The
 * budget object has no execution mechanism (no monitor, no submit-time check, no
 * runtime constraint — {@code getMemoryBudget()} has zero main callers); the real
 * memory envelope is set by element-count facilities (IBufferPool 65536-element
 * default, EdgeConfig.queueCapacity) and the state working view. The pin asserts
 * the {@code @Deprecated} mechanical carrier of that declaration — removing the
 * annotation (or the RECORD-ONLY contract statement it certifies) turns this pin
 * red.
 *
 * <p>Classification note (ruling B.2 / plan E2 或-branch): this is a compile-time
 * STRUCTURE pin + mutation/rollback argument — a documentation-level defect's
 * acceptance artifact IS the declaration itself; there is no runtime behavior face
 * to assert (the mutation proof: {@code git revert} of the annotation/javadoc
 * commit reintroduces the capacity-planning false guarantee and reds this pin;
 * the owner-doc envelope section is the paired evidence, checked at closure).
 */
class TestMemoryBudgetRecordOnlyDeclarationR402 {

    @Test
    void memoryBudgetCarriesTheRecordOnlyDeprecationCarrier() {
        assertTrue(MemoryBudget.class.isAnnotationPresent(Deprecated.class),
                "MemoryBudget must carry @Deprecated(since=8.42, forRemoval=false) as the "
                        + "mechanical carrier of the RECORD-ONLY, NOT ENFORCED declaration "
                        + "(CRA 8.42 R4-02 ruling B.2 — the budget value must not be used "
                        + "for capacity planning; see docs-for-ai/03-modules/nop-stream.md "
                        + "'规模与内存包络')");
    }
}
