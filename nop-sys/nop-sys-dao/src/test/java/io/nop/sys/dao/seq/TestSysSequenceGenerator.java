package io.nop.sys.dao.seq;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.sys.dao.NopSysErrors;
import io.nop.sys.dao.entity.NopSysSequence;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysSequenceGenerator extends JunitBaseTestCase {
    static final Logger LOG = LoggerFactory.getLogger(TestSysSequenceGenerator.class);

    @Inject
    IDaoProvider daoProvider;

    @Inject
    SysSequenceGenerator generator;

    @Test
    public void testSequence() {
        NopSysSequence seq = new NopSysSequence();
        seq.setNextValue(100L);
        seq.setSeqName("test");
        seq.setCacheSize(200);
        seq.setSeqType("default");
        seq.setIsUuid(DaoConstants.NO_VALUE);
        seq.setStepSize(1);

        daoProvider.daoFor(NopSysSequence.class).saveEntity(seq);

        String value = generator.generateString("test", true);
        assertEquals("100", value);

        LOG.info("before-generate");
        value = generator.generateString("test", true);
        assertEquals("101", value);

        value = generator.generateString("test", true);
        assertEquals("102", value);
        LOG.info("after-generate");

        NopSysSequence entity = daoProvider.daoFor(NopSysSequence.class).getEntityById(seq.orm_id());
        assertEquals(300, entity.getNextValue());
    }

    /**
     * check 审计 [P1]：CACHE_SIZE 列无 mandatory/defaultValue，管理端可不填。
     * 修复前 syncFromDb 对 null 拆箱抛 NPE，该序列完全不可用；修复后按 0 处理（每次取号同步DB）。
     */
    @Test
    public void testCacheSizeNullSequenceUsable() {
        NopSysSequence seq = new NopSysSequence();
        seq.setNextValue(500L);
        seq.setSeqName("test-null-cache-size");
        seq.setSeqType("default");
        seq.setIsUuid(DaoConstants.NO_VALUE);
        seq.setStepSize(1);
        // cacheSize 不设置（null）

        daoProvider.daoFor(NopSysSequence.class).saveEntity(seq);

        String v1 = generator.generateString("test-null-cache-size", true);
        assertEquals("500", v1);
        String v2 = generator.generateString("test-null-cache-size", true);
        assertEquals("501", v2);
    }

    /**
     * check2 审计 [P3]：ERR_SYS_NO_SEQ 定义后从未使用。序列行被并发删除时 syncFromDb
     * 的懒加载代理抛通用ORM错误；修复后确认行确实缺失时转译为语义化错误码便于排障。
     */
    @Test
    public void testSyncFromDbThrowsSemanticErrorWhenSeqRowDeleted() {
        NopSysSequence seq = new NopSysSequence();
        seq.setSeqName("test-concurrently-deleted");
        seq.setNextValue(700L);
        seq.setCacheSize(0); // 每次取号都走syncFromDb
        seq.setSeqType("default");
        seq.setIsUuid(DaoConstants.NO_VALUE);
        seq.setStepSize(1);

        daoProvider.daoFor(NopSysSequence.class).saveEntity(seq);

        assertEquals("700", generator.generateString("test-concurrently-deleted", true));

        // 直接按查询删除（deleteEntity 对已脱离session的实体会抛 entity-not-in-session）
        QueryBean deleteQuery = new QueryBean();
        deleteQuery.setFilter(FilterBeans.eq(NopSysSequence.PROP_NAME_seqName, "test-concurrently-deleted"));
        daoProvider.daoFor(NopSysSequence.class).deleteByQuery(deleteQuery);

        NopException ex = assertThrows(NopException.class,
                () -> generator.generateString("test-concurrently-deleted", true));
        assertEquals(NopSysErrors.ERR_SYS_NO_SEQ.getErrorCode(), ex.getErrorCode(),
                "missing sequence row must surface the semantic no-seq error code");
        assertEquals("test-concurrently-deleted", ex.getParam(NopSysErrors.ARG_SEQ_NAME));
    }
}
