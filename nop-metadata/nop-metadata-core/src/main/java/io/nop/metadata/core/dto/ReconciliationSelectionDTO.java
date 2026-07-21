package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class ReconciliationSelectionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int rowIndex;
    private String selectedEntityId;

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public String getSelectedEntityId() {
        return selectedEntityId;
    }

    public void setSelectedEntityId(String selectedEntityId) {
        this.selectedEntityId = selectedEntityId;
    }
}
