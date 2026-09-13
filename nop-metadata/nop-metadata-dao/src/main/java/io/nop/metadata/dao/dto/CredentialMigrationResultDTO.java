package io.nop.metadata.dao.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;

/**
 * 数据源凭证批量迁移摘要（审计 MD-3：替代 Map&lt;String,Object&gt; 返回；字段名与原 Map 键一致，GraphQL 面不变）。
 */
@DataBean
public class CredentialMigrationResultDTO {
    private int migratedCount;
    private int skippedCount;
    private int failedCount;
    private List<Map<String, Object>> failures;

    public int getMigratedCount() {
        return migratedCount;
    }

    public void setMigratedCount(int migratedCount) {
        this.migratedCount = migratedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<Map<String, Object>> getFailures() {
        return failures;
    }

    public void setFailures(List<Map<String, Object>> failures) {
        this.failures = failures;
    }
}
