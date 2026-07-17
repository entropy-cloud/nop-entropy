package io.nop.table.validator.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.xml.XNode;

import java.util.List;

@DataBean
public class TableValidatorModel {
    private String description;
    private List<TableColumnMeta> columns;
    private List<RowValidatorDef> rowValidators;
    private List<TableStatCheckModel> statChecks;
    private List<TableGlobalCheckModel> tableChecks;
    private XNode beforeValidate;
    private XNode afterValidate;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<TableColumnMeta> getColumns() {
        return columns;
    }

    public void setColumns(List<TableColumnMeta> columns) {
        this.columns = columns;
    }

    public List<RowValidatorDef> getRowValidators() {
        return rowValidators;
    }

    public void setRowValidators(List<RowValidatorDef> rowValidators) {
        this.rowValidators = rowValidators;
    }

    public List<TableStatCheckModel> getStatChecks() {
        return statChecks;
    }

    public void setStatChecks(List<TableStatCheckModel> statChecks) {
        this.statChecks = statChecks;
    }

    public List<TableGlobalCheckModel> getTableChecks() {
        return tableChecks;
    }

    public void setTableChecks(List<TableGlobalCheckModel> tableChecks) {
        this.tableChecks = tableChecks;
    }

    public XNode getBeforeValidate() {
        return beforeValidate;
    }

    public void setBeforeValidate(XNode beforeValidate) {
        this.beforeValidate = beforeValidate;
    }

    public XNode getAfterValidate() {
        return afterValidate;
    }

    public void setAfterValidate(XNode afterValidate) {
        this.afterValidate = afterValidate;
    }
}
