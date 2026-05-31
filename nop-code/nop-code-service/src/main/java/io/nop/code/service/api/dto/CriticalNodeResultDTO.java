package io.nop.code.service.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class CriticalNodeResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<CriticalNodeScoreDTO> hubNodes;
    private List<CriticalNodeScoreDTO> bridgeNodes;
    private int totalNodes;
    private int topN;

    public List<CriticalNodeScoreDTO> getHubNodes() {
        return hubNodes;
    }

    public void setHubNodes(List<CriticalNodeScoreDTO> hubNodes) {
        this.hubNodes = hubNodes;
    }

    public List<CriticalNodeScoreDTO> getBridgeNodes() {
        return bridgeNodes;
    }

    public void setBridgeNodes(List<CriticalNodeScoreDTO> bridgeNodes) {
        this.bridgeNodes = bridgeNodes;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }
}
