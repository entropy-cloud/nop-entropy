package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.List;

@DataBean
public class DepGraphDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<DepNodeDTO> nodes;
    private List<DepEdgeDTO> edges;

    public List<DepNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<DepNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<DepEdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<DepEdgeDTO> edges) {
        this.edges = edges;
    }
}
