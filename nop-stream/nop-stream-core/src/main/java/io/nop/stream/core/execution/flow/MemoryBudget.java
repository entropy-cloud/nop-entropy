/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.flow;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.api.core.annotations.data.DataBean;

/**
 * RECORD-ONLY, NOT ENFORCED (CRA 8.42 R4-02, ruling B.2 — 1.1-C5-012).
 *
 * <p>This budget object currently has NO execution mechanism of any kind: there
 * is no MemoryBudgetMonitor, no submit-time budget check, and no runtime
 * enforcement hook ({@code DeploymentPlan#getMemoryBudget()} has zero production
 * callers). The real memory envelope of a job is determined by element-count
 * facilities ({@code IBufferPool}'s 65536-element default, EdgeConfig.queueCapacity)
 * and the state working view resident on the heap — byte totals derived from this
 * object can diverge from the real envelope by orders of magnitude.
 *
 * <p><b>Do not use for capacity planning.</b> The authoritative envelope
 * declaration lives in {@code docs-for-ai/03-modules/nop-stream.md} (section
 * 「规模与内存包络」). The {@code @Deprecated} marker is the mechanical carrier of
 * this declaration; it can be lifted once the state-management design §9.2
 * executor (MemoryBudgetMonitor + three-tier overflow policy) actually lands.
 */
@Deprecated(since = "8.42", forRemoval = false)
@DataBean
public class MemoryBudget implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long totalBytes;
    private final Map<String, Long> componentAllocations;

    public MemoryBudget(long totalBytes, Map<String, Long> componentAllocations) {
        this.totalBytes = totalBytes;
        this.componentAllocations = componentAllocations != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(componentAllocations))
                : Collections.emptyMap();
    }

    public MemoryBudget() {
        this(0, null);
    }

    public static MemoryBudget defaultLocalBudget(long totalBytes) {
        Map<String, Long> allocations = new LinkedHashMap<>();
        allocations.put("stateBackend", (long) (totalBytes * 0.5));
        allocations.put("edgeQueues", (long) (totalBytes * 0.3));
        allocations.put("networkBuffers", (long) (totalBytes * 0.2));
        return new MemoryBudget(totalBytes, allocations);
    }

    /**
     * Returns the declared allocation of one component. RECORD-ONLY (ruling B.2):
     * the returned number is a declaration with no enforcement behind it — never
     * use it as a memory bound or for capacity planning (see the class javadoc).
     */
    public long getAllocation(String component) {
        return componentAllocations.getOrDefault(component, 0L);
    }
}
