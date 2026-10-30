package io.nop.tcc.dao.store;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccStatus;
import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.dao.test.AbstractTccTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static io.nop.tcc.dao.entity.NopTccRecord.PROP_NAME_txnId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C10-007 收口门 tcc 半边·store 行为面（plan 2515 红⑪，C7-005）：
 * 耗尽行（retryTimes>=maxRetryCount 且过期且非终态）前置终态化为 KILLED 后入既有 finished
 * 清理谓词；removeCompletedRecords 有界批删；nop_tcc_record 索引申报落地（orm 模型 + 迁移脚本
 * + 库内索引真实生效）。缺陷态：耗尽行被恢复扫描与清理谓词双重永久排除、全表零索引——全红。
 */
public class TestTccLedgerHygiene_C7_005 extends AbstractTccTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITransactionTemplate txnTemplate;

    private static Object invoke(Object target, String methodName, Class<?>[] argTypes, Object... args) throws Exception {
        Method method = target.getClass().getMethod(methodName, argTypes);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw (e.getCause() instanceof Exception ? (Exception) e.getCause() : e);
        }
    }

    private ITccRecord insertRecord(String txnId, TccStatus status, int retryTimes, long beginTimeOffsetMs,
                                    long expireOffsetMs) {
        NopTccRecord record = (NopTccRecord) tccRecordStore.newTccRecord("hygiene-group");
        record.setTxnId(txnId);
        record.setStatus(status.getCode());
        record.setRetryTimes(retryTimes);
        long now = System.currentTimeMillis();
        record.setBeginTime(new Timestamp(now + beginTimeOffsetMs));
        record.setExpireTime(new Timestamp(now + expireOffsetMs));
        daoProvider.daoFor(NopTccRecord.class).saveEntityDirectly(record);
        return record;
    }

    private NopTccRecord loadByTxnId(String txnId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(PROP_NAME_txnId, txnId));
        java.util.List<NopTccRecord> list = daoProvider.daoFor(NopTccRecord.class).findAllByQuery(query);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 耗尽行前置终态化：retryTimes 超限且过期的非终态行 → status=KILLED + endTime 落账 */
    @Test
    public void C7_005_killExhaustedRows_beforeCleanupPredicate() throws Exception {
        tccRecordStore = new TccRecordStore();
        tccRecordStore.setDaoProvider(daoProvider);
        tccRecordStore.setDefaultMaxRetryTimes(100);

        insertRecord("txn-exhausted-kill", TccStatus.TRYING, 200, -40 * 24 * 3600_000L, -1000);
        insertRecord("txn-live-keep", TccStatus.TRYING, 5, -40 * 24 * 3600_000L, -1000);

        int killed;
        try {
            killed = (Integer) invoke(tccRecordStore, "killExhaustedRecords",
                    new Class<?>[]{int.class, int.class}, 100, 1000);
        } catch (NoSuchMethodException e) {
            fail("store must expose killExhaustedRecords (exhausted-row terminalization pre-step): " + e);
            return;
        }

        assertEquals(1, killed, "exactly the exhausted row must be terminalized");
        NopTccRecord killedRow = loadByTxnId("txn-exhausted-kill");
        assertNotNull(killedRow);
        assertEquals(TccStatus.KILLED, TccStatus.fromCode(killedRow.getStatus()),
                "exhausted row must be terminalized to KILLED (C7-006 seam: terminal field = status)");
        assertNotNull(killedRow.getEndTime(), "terminalized row must have endTime recorded");
        assertEquals(200, killedRow.getRetryTimes(), "terminalization must not reset the retry counter");

        NopTccRecord liveRow = loadByTxnId("txn-live-keep");
        assertNotNull(liveRow);
        assertEquals(TccStatus.TRYING, TccStatus.fromCode(liveRow.getStatus()),
                "non-exhausted row must not be terminalized");
    }

    /** 有界清理：KILLED 终态行进入既有 finished 谓词被批删，非终态行保留 */
    @Test
    public void C7_005_boundedRemoveCompletedRecords_killedRowCleaned() throws Exception {
        tccRecordStore = new TccRecordStore();
        tccRecordStore.setDaoProvider(daoProvider);
        tccRecordStore.setDefaultMaxRetryTimes(100);

        insertRecord("txn-dead-killed", TccStatus.KILLED, 200, -40 * 24 * 3600_000L, -1000);
        insertRecord("txn-stuck-keep", TccStatus.TRYING, 200, -40 * 24 * 3600_000L, -1000);

        int removed;
        try {
            removed = (Integer) invoke(tccRecordStore, "removeCompletedRecords",
                    new Class<?>[]{long.class, boolean.class, int.class},
                    30 * 24 * 3600_000L, true, 1000);
        } catch (NoSuchMethodException e) {
            fail("store must expose bounded removeCompletedRecords(retention, onlyCompleted, batchSize): " + e);
            return;
        }

        assertEquals(1, removed, "only the finished (KILLED) old row may be removed");
        assertNull(loadByTxnId("txn-dead-killed"), "terminalized old row must be cleaned");
        assertNotNull(loadByTxnId("txn-stuck-keep"), "non-terminal old row must be kept");
    }

    /** 索引申报落地三面：orm 模型 <indexes> 段 + 非 _ 前缀迁移脚本 + 库内索引真实生效 */
    @Test
    public void C7_005_indexesDeclared_ormModelMigrationScriptAndDbEffective() throws Exception {
        // (a) orm 源模型申报（裁定⑧ §3.8(c)：nop-tcc.orm.xml 为源模型）
        String ormXml = Files.readString(Paths.get("../model/nop-tcc.orm.xml"));
        assertTrue(ormXml.contains("idx_tcc_record_expire"),
                "orm model must declare the expireTime+status+retryTimes composite index");
        assertTrue(ormXml.contains("idx_tcc_record_begin_time"),
                "orm model must declare the beginTime ordering index");

        // (b) 非 _ 前缀迁移脚本（裁定⑧ §3.8(b)：postgresql 基准方言）
        assertTrue(Files.exists(Paths.get("../deploy/sql/postgresql/2026-10-add-tcc-record-indexes.sql")),
                "migration script must exist under nop-tcc/deploy/sql/postgresql/ (non-underscore)");

        // (c) 库内索引真实生效（schema 由 orm 模型生成 → 索引必须真实存在，非仅 xml 声明）
        try (Connection conn = txnTemplate.openConnection(null)) {
            DatabaseMetaData meta = conn.getMetaData();
            boolean foundExpireIndex = false;
            try (ResultSet rs = meta.getIndexInfo(null, null, "NOP_TCC_RECORD", false, false)) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    if (name != null && name.toUpperCase().contains("TCC_RECORD_EXPIRE"))
                        foundExpireIndex = true;
                }
            }
            assertTrue(foundExpireIndex,
                    "expire composite index must exist in the database (query-plan face, not xml-only)");
        }
    }
}
