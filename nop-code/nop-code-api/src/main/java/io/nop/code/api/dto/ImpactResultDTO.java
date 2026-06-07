package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class ImpactResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String targetSymbolId;
    private String targetQualifiedName;
    private List<ImpactedSymbolDTO> upstream;
    private List<ImpactedSymbolDTO> downstream;
    private String riskLevel;

    public String getTargetSymbolId() {
        return targetSymbolId;
    }

    public void setTargetSymbolId(String targetSymbolId) {
        this.targetSymbolId = targetSymbolId;
    }

    public String getTargetQualifiedName() {
        return targetQualifiedName;
    }

    public void setTargetQualifiedName(String targetQualifiedName) {
        this.targetQualifiedName = targetQualifiedName;
    }

    public List<ImpactedSymbolDTO> getUpstream() {
        return upstream;
    }

    public void setUpstream(List<ImpactedSymbolDTO> upstream) {
        this.upstream = upstream;
    }

    public List<ImpactedSymbolDTO> getDownstream() {
        return downstream;
    }

    public void setDownstream(List<ImpactedSymbolDTO> downstream) {
        this.downstream = downstream;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
