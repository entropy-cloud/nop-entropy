package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨类型字段解析结果 DTO（来源：{@code NopMetaTableBizModel.resolveTableFields}）。
 */
@DataBean
public class ResolveTableFieldsResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableType;
    private List<ResolvedTableFieldDTO> fields = new ArrayList<>();

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public List<ResolvedTableFieldDTO> getFields() {
        return fields;
    }

    public void setFields(List<ResolvedTableFieldDTO> fields) {
        this.fields = fields;
    }
}
