/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.exp.config;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class ExportDbConfig extends AbstractDbConfig {
    private String outputDir;

    private List<String> exportFormats;

    private List<ExportTableConfig> tables;

    private String tableNamePrefix;

    private List<String> excludeTableNames;

    private boolean exportAllTables;

    private String dialect;

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public boolean isExportAllTables() {
        return exportAllTables;
    }

    public void setExportAllTables(boolean exportAllTables) {
        this.exportAllTables = exportAllTables;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public List<String> getExportFormats() {
        return exportFormats;
    }

    public void setExportFormats(List<String> exportFormats) {
        this.exportFormats = exportFormats;
    }

    public List<ExportTableConfig> getTables() {
        return tables;
    }

    public void setTables(List<ExportTableConfig> tables) {
        this.tables = tables;
    }

    public String getTableNamePrefix() {
        return tableNamePrefix;
    }

    public void setTableNamePrefix(String tableNamePrefix) {
        this.tableNamePrefix = tableNamePrefix;
    }

    public List<String> getExcludeTableNames() {
        return excludeTableNames;
    }

    public void setExcludeTableNames(List<String> excludeTableNames) {
        this.excludeTableNames = excludeTableNames;
    }
}
