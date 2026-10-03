/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.model;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WI21: one normalized declarative registry entry in {@link StreamComponents}
 * (§八 11). The DSL layer's registry model objects (flow-layer aggregators /
 * joins / schemas) are normalized into these flow-free carriers so the core
 * registry can hold every declared component without a flow dependency.
 * {@code attributes} carries the declaration's semantic fields as plain
 * string values (e.g. aggregator fnId/expr, joinSpec joinType/keyExprs,
 * schema field summary) — descriptive, not executable.
 */
@DataBean
public class StreamComponentEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String registry;
    private String id;
    private Map<String, String> attributes = new LinkedHashMap<>();

    public StreamComponentEntry() {
    }

    public StreamComponentEntry(String registry, String id, Map<String, String> attributes) {
        this.registry = registry;
        this.id = id;
        if (attributes != null) {
            this.attributes = new LinkedHashMap<>(attributes);
        }
    }

    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
