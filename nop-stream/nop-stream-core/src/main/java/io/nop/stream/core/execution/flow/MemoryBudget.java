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

    public long getTotalBytes() { return totalBytes; }
    public Map<String, Long> getComponentAllocations() { return componentAllocations; }

    public long getAllocation(String component) {
        return componentAllocations.getOrDefault(component, 0L);
    }
}
