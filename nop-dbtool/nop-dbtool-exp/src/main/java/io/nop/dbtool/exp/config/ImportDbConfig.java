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
public class ImportDbConfig extends AbstractDbConfig {
    private String inputDir;
    private List<ImportTableConfig> tables;

    private boolean importAllTables;

    private List<String> excludeTableNames;

    private boolean ignoreDuplicate = true;

    public boolean isIgnoreDuplicate() {
        return ignoreDuplicate;
    }

    public void setIgnoreDuplicate(boolean ignoreDuplicate) {
        this.ignoreDuplicate = ignoreDuplicate;
    }

    public List<String> getExcludeTableNames() {
        return excludeTableNames;
    }

    public void setExcludeTableNames(List<String> excludeTableNames) {
        this.excludeTableNames = excludeTableNames;
    }

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }

    public List<ImportTableConfig> getTables() {
        return tables;
    }

    public void setTables(List<ImportTableConfig> tables) {
        this.tables = tables;
    }

    public boolean isImportAllTables() {
        return importAllTables;
    }

    public void setImportAllTables(boolean importAllTables) {
        this.importAllTables = importAllTables;
    }
}
