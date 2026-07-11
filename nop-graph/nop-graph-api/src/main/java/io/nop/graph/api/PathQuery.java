package io.nop.graph.api;

import java.util.function.Predicate;

/**
 * 路径表达式查询配置。
 *
 * 这是一个纯配置类型，不包含执行逻辑。
 * 执行由 nop-graph-core 的 PathQueryExecutor 完成。
 */
public class PathQuery {
    private String edgeType;
    private int minHops = 1;
    private int maxHops = 3;
    private Predicate<String> nodeFilter;

    public String getEdgeType() {
        return edgeType;
    }

    public PathQuery setEdgeType(String edgeType) {
        this.edgeType = edgeType;
        return this;
    }

    public int getMinHops() {
        return minHops;
    }

    public PathQuery setMinHops(int minHops) {
        this.minHops = minHops;
        return this;
    }

    public int getMaxHops() {
        return maxHops;
    }

    public PathQuery setMaxHops(int maxHops) {
        this.maxHops = maxHops;
        return this;
    }

    public Predicate<String> getNodeFilter() {
        return nodeFilter;
    }

    public PathQuery setNodeFilter(Predicate<String> nodeFilter) {
        this.nodeFilter = nodeFilter;
        return this;
    }

    public static PathQuery create() {
        return new PathQuery();
    }
}
