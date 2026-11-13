/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.store;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.retry.dao.entity.NopRetryRecord;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static io.nop.retry.api.NopRetryApiConstants.*;
import static io.nop.retry.dao._NopRetryDaoConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.6-C7-012（plan 2515 裁定⑨ / C10-006 namespace 半）：扫描器覆盖面修复——
 * fetchPendingRecords 移除单值 namespaceId 硬过滤后，非 default namespace 的
 * PENDING 记录必须进入扫描拾取面（缺陷态：永远不可见，重试承诺静默违约）。
 * 缺陷态红 → 修复后绿（先红后绿证据见 _tmp/red-green-2515-retry-c7-012-*.log）。
 */
@NopTestConfig(
        localDb = true,
        initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestRetry2515Phase2_C7_012_NamespaceScan extends JunitAutoTestCase {

    @Inject
    IRetryRecordStore recordStore;

    /**
     * 红⑫：非 default namespace 记录失败进入 PENDING 后必须被扫描器拾取（job 桥接零改码：
     * 提交侧 withNamespaceId 透传照旧，行为闭合落 fetch 放宽）。
     */
    @Test
    void testC7_012_fetchPendingRecords_coversNonDefaultNamespace() {
        NopRetryRecord defaultRecord = createRecord("rec-ns-default", "default", "idem-ns-default");
        defaultRecord.setNextTriggerTime(new Timestamp(System.currentTimeMillis() - 1000));
        recordStore.saveRecord(defaultRecord);

        NopRetryRecord tenantRecord = createRecord("rec-ns-tenant-x", "tenant-x", "idem-ns-tenant-x");
        tenantRecord.setNextTriggerTime(new Timestamp(System.currentTimeMillis() - 1000));
        recordStore.saveRecord(tenantRecord);

        List<NopRetryRecord> fetched = recordStore.fetchPendingRecords(100, null);

        assertTrue(containsSid(fetched, defaultRecord.getSid()),
                "default namespace pending record must be scannable");
        assertTrue(containsSid(fetched, tenantRecord.getSid()),
                "non-default namespace pending record must be scannable"
                        + " (C7-012: single-namespace hard filter silently broke the retry promise)");
    }

    private boolean containsSid(List<NopRetryRecord> records, String sid) {
        return records.stream().anyMatch(r -> sid.equals(r.getSid()));
    }

    private NopRetryRecord createRecord(String sid, String namespaceId, String idempotentId) {
        NopRetryRecord record = new NopRetryRecord();
        record.setSid(sid);
        record.setNamespaceId(namespaceId);
        record.setGroupId("default");
        record.setPolicyId("policy-ns-scan");
        record.setIdempotentId(idempotentId);
        record.setStatus(RETRY_RECORD_STATUS_PENDING);
        record.setRetryCount(0);
        record.setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT);
        record.setServiceName("svc");
        record.setServiceMethod("method");
        return record;
    }
}
