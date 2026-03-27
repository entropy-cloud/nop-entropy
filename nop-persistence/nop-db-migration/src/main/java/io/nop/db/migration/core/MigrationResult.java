/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import java.util.ArrayList;
import java.util.List;

public class MigrationResult {
    
    private List<MigrationRecord> records = new ArrayList<>();
    private boolean success = true;
    private String errorMessage;
    
    public MigrationResult() {
    }
    
    public MigrationResult(List<MigrationRecord> records) {
        this.records = records != null ? records : new ArrayList<>();
    }
    
    public List<MigrationRecord> getRecords() {
        return records;
    }
    
    public void setRecords(List<MigrationRecord> records) {
        this.records = records != null ? records : new ArrayList<>();
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
        this.success = false;
    }
    
    public int getExecutedCount() {
        return records.size();
    }
    
    public long getTotalExecutionTime() {
        return records.stream()
                .mapToLong(MigrationRecord::getExecutionTime)
                .sum();
    }
    
    public void addRecord(MigrationRecord record) {
        if (records == null) {
            records = new ArrayList<>();
        }
        records.add(record);
    }
}
