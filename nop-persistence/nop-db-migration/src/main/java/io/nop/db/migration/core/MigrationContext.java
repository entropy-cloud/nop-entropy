/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.dialect.IDialect;
import io.nop.db.migration.model.DbMigrationModel;

import java.util.ArrayList;
import java.util.List;

public class MigrationContext {
    
    private List<String> migrationPaths = new ArrayList<>();
    private List<DbMigrationModel> migrations;
    private String installedBy;
    private String context;
    private List<String> labels = new ArrayList<>();
    private IJdbcTemplate jdbcTemplate;
    private IDialect dialect;
    private String querySpace = "default";
    private boolean validateChecksum = true;
    private boolean failFast = true;
    
    public List<String> getMigrationPaths() {
        return migrationPaths;
    }
    
    public void setMigrationPaths(List<String> migrationPaths) {
        this.migrationPaths = migrationPaths;
    }
    
    public List<DbMigrationModel> getMigrations() {
        return migrations;
    }
    
    public void setMigrations(List<DbMigrationModel> migrations) {
        this.migrations = migrations;
    }
    
    public String getInstalledBy() {
        return installedBy;
    }
    
    public void setInstalledBy(String installedBy) {
        this.installedBy = installedBy;
    }
    
    public String getContext() {
        return context;
    }
    
    public void setContext(String context) {
        this.context = context;
    }
    
    public List<String> getLabels() {
        return labels;
    }
    
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
    
    public IJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
    
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    public IDialect getDialect() {
        return dialect;
    }
    
    public void setDialect(IDialect dialect) {
        this.dialect = dialect;
    }
    
    public String getQuerySpace() {
        return querySpace;
    }
    
    public void setQuerySpace(String querySpace) {
        this.querySpace = querySpace;
    }
    
    public boolean isValidateChecksum() {
        return validateChecksum;
    }
    
    public void setValidateChecksum(boolean validateChecksum) {
        this.validateChecksum = validateChecksum;
    }
    
    public boolean isFailFast() {
        return failFast;
    }
    
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }
    
    public boolean hasLabel(String label) {
        return labels != null && labels.contains(label);
    }
    
    public boolean matchesContext(String targetContext) {
        if (context == null || targetContext == null) {
            return true;
        }
        return context.equals(targetContext);
    }
}
