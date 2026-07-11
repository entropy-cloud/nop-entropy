package io.nop.graph.api;

import java.util.List;

/**
 * 通用只读有向图接口。
 *
 * 节点以 String ID 标识。算法返回的节点也是 String ID，
 * 调用者通过外部属性存储 enrich 名称、路径等信息。
 *
 * 边为通用 Edge 类型，携带 sourceId/targetId/weight/type。
 * 领域特定属性由 Edge.attrs 承载，算法不读取。
 *
 * 所有实现返回同一类型，调用者无需感知底层是
 * InMemory、Neo4j 还是 ORM。
 */
public interface IGraph {
    /**
     * 获取从指定节点出发的所有出边。
     *
     * @param nodeId 节点 ID
     * @return 出边列表，如果节点不存在返回空列表
     */
    List<Edge> getOutEdges(String nodeId);

    /**
     * 获取指向指定节点的所有入边。
     *
     * @param nodeId 节点 ID
     * @return 入边列表，如果节点不存在返回空列表
     */
    List<Edge> getInEdges(String nodeId);
}
