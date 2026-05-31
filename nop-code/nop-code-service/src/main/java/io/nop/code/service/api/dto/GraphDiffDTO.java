package io.nop.code.service.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class GraphDiffDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Set<String> addedNodes;
    private Set<String> removedNodes;
    private Set<EdgeKeyDTO> addedEdges;
    private Set<EdgeKeyDTO> removedEdges;
    private List<CommunityChangeDTO> communityChanges;

    public Set<String> getAddedNodes() {
        return addedNodes;
    }

    public void setAddedNodes(Set<String> addedNodes) {
        this.addedNodes = addedNodes;
    }

    public Set<String> getRemovedNodes() {
        return removedNodes;
    }

    public void setRemovedNodes(Set<String> removedNodes) {
        this.removedNodes = removedNodes;
    }

    public Set<EdgeKeyDTO> getAddedEdges() {
        return addedEdges;
    }

    public void setAddedEdges(Set<EdgeKeyDTO> addedEdges) {
        this.addedEdges = addedEdges;
    }

    public Set<EdgeKeyDTO> getRemovedEdges() {
        return removedEdges;
    }

    public void setRemovedEdges(Set<EdgeKeyDTO> removedEdges) {
        this.removedEdges = removedEdges;
    }

    public List<CommunityChangeDTO> getCommunityChanges() {
        return communityChanges;
    }

    public void setCommunityChanges(List<CommunityChangeDTO> communityChanges) {
        this.communityChanges = communityChanges;
    }
}
