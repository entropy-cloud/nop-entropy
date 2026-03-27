/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import java.util.Date;

public class MigrationRecord {
    
    private String version;
    private String description;
    private String type;
    private String checksum;
    private long executionTime;
    private Date installedOn;
    private String installedBy;
    private boolean success;
    private String errorMessage;
    
    public MigrationRecord() {
    }
    
    public MigrationRecord(String version, String description, String checksum, 
                          long executionTime, String installedBy) {
        this.version = version;
        this.description = description;
        this.checksum = checksum;
        this.executionTime = executionTime;
        this.installedBy = installedBy;
        this.installedOn = new Date();
        this.success = true;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getChecksum() {
        return checksum;
    }
    
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }
    
    public Date getInstalledOn() {
        return installedOn;
    }
    
    public void setInstalledOn(Date installedOn) {
        this.installedOn = installedOn;
    }
    
    public String getInstalledBy() {
        return installedBy;
    }
    
    public void setInstalledBy(String installedBy) {
        this.installedBy = installedBy;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
