package io.nop.code.api.dto;

import java.io.Serializable;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
@DataBean
public class KnowledgeGapResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<IsolatedSymbolDTO> isolatedSymbols;
    private List<WeakCommunityDTO> weakCommunities;

    public List<IsolatedSymbolDTO> getIsolatedSymbols() {
        return isolatedSymbols;
    }

    public void setIsolatedSymbols(List<IsolatedSymbolDTO> isolatedSymbols) {
        this.isolatedSymbols = isolatedSymbols;
    }

    public List<WeakCommunityDTO> getWeakCommunities() {
        return weakCommunities;
    }

    public void setWeakCommunities(List<WeakCommunityDTO> weakCommunities) {
        this.weakCommunities = weakCommunities;
    }
}
