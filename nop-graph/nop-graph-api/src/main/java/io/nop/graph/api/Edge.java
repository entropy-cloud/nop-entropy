package io.nop.graph.api;

import java.util.Collections;
import java.util.Map;

/**
 * 通用边。所有图实现返回同一类型。
 *
 * 只携带拓扑信息（sourceId/targetId）和算法需要的属性
 *（weight/type）。领域特定属性通过 attrs 扩展，算法不读取。
 *
 * attrs 是 mutable 的，允许适配层在构造后注入领域属性。
 */
public class Edge {
    private final String sourceId;
    private final String targetId;
    private final double weight;
    private final String type;
    private Map<String, Object> attrs;

    public Edge(String sourceId, String targetId) {
        this(sourceId, targetId, 1.0, null);
    }

    public Edge(String sourceId, String targetId, double weight, String type) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.weight = weight;
        this.type = type;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetId() {
        return targetId;
    }

    public double getWeight() {
        return weight;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getAttrs() {
        return attrs != null ? attrs : Collections.emptyMap();
    }

    public void setAttrs(Map<String, Object> attrs) {
        this.attrs = attrs;
    }
}
