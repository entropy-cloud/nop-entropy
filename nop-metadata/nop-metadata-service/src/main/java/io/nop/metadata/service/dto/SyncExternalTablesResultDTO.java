package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.dao.dto.ErrorDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步外部表结果 DTO（来源：{@code NopMetaDataSourceBizModel.syncExternalTables}）。
 */
@DataBean
public class SyncExternalTablesResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int syncedTableCount;
    private List<ErrorDTO> errors = new ArrayList<>();

    public int getSyncedTableCount() {
        return syncedTableCount;
    }

    public void setSyncedTableCount(int syncedTableCount) {
        this.syncedTableCount = syncedTableCount;
    }

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }
}
