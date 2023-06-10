package io.nop.dbtool.exp.config;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class ExportDbConfig extends AbstractDbConfig {
    private String targetDir;

    private List<String> exportFormats;

    private List<SqlTableConfig> tables;

    private String tableNamePrefix;

    public String getTargetDir() {
        return targetDir;
    }

    public void setTargetDir(String targetDir) {
        this.targetDir = targetDir;
    }

    public List<String> getExportFormats() {
        return exportFormats;
    }

    public void setExportFormats(List<String> exportFormats) {
        this.exportFormats = exportFormats;
    }

    public List<SqlTableConfig> getTables() {
        return tables;
    }

    public void setTables(List<SqlTableConfig> tables) {
        this.tables = tables;
    }

    public String getTableNamePrefix() {
        return tableNamePrefix;
    }

    public void setTableNamePrefix(String tableNamePrefix) {
        this.tableNamePrefix = tableNamePrefix;
    }
}
