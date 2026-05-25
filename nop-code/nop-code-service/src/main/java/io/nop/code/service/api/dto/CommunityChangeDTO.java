package io.nop.code.service.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class CommunityChangeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private String oldCommunity;
    private String newCommunity;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getOldCommunity() {
        return oldCommunity;
    }

    public void setOldCommunity(String oldCommunity) {
        this.oldCommunity = oldCommunity;
    }

    public String getNewCommunity() {
        return newCommunity;
    }

    public void setNewCommunity(String newCommunity) {
        this.newCommunity = newCommunity;
    }
}
