/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.connector.lookup.ITableLookup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** In-memory dimension table stub for the DSL-level E2E. */
public class InMemoryTableLookup implements ITableLookup {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> rows = new HashMap<>();

    public InMemoryTableLookup() {
        rows.put("k1", "merchant-a");
        rows.put("k2", "merchant-b");
    }

    @Override
    public Object lookup(Object key) {
        return rows.get(key);
    }
}
