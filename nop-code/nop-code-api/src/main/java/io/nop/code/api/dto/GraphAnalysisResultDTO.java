package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class GraphAnalysisResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<GodNodeDTO> godNodes;
    private CohesionBreakdownDTO cohesionBreakdown;
    private List<String> isolatedSymbols;

    public List<GodNodeDTO> getGodNodes() {
        return godNodes;
    }

    public void setGodNodes(List<GodNodeDTO> godNodes) {
        this.godNodes = godNodes;
    }

    public CohesionBreakdownDTO getCohesionBreakdown() {
        return cohesionBreakdown;
    }

    public void setCohesionBreakdown(CohesionBreakdownDTO cohesionBreakdown) {
        this.cohesionBreakdown = cohesionBreakdown;
    }

    public List<String> getIsolatedSymbols() {
        return isolatedSymbols;
    }

    public void setIsolatedSymbols(List<String> isolatedSymbols) {
        this.isolatedSymbols = isolatedSymbols;
    }
}
