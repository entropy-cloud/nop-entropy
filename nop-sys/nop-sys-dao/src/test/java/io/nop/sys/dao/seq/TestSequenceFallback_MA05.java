package io.nop.sys.dao.seq;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.entity.NopSysSequence;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-05（C2-005 主 + C1-007，plan 2525 裁定⑥）：SEQ_DEFAULT 回退路径治理。
 *
 * <p>缺陷①（C2-005）：回退结果直接缓存共享的 SEQ_DEFAULT item——多序列名共用同一锁对象，
 * synchronized(item) 内 REQUIRES_NEW DB 往返（含 session.lock 行锁等待）使一个冷门序列的 DB 慢
 * 拖住全部默认序列取号。修复 = 回退项按 seqName 各建独立 item（配置快照同源、取值流仍来自
 * SEQ_DEFAULT 行）。
 *
 * <p>缺陷②（C1-007）：defaultCache 只装不清——管理端回拨 nextValue 后 clearCache/removeCache
 * 不穿透回退项，最长 TTL 60s 窗口内对回退序列名不生效。修复 = 两入口同步 defaultCache 失效。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSequenceFallback_MA05 extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    SysSequenceGenerator generator;

    @BeforeEach
    public void setUpDefaultSequence() {
        // SEQ_DEFAULT 行存在性保证（重复运行幂等）：回退路径的取值流来源
        IEntityDao<NopSysSequence> dao = daoProvider.daoFor(NopSysSequence.class);
        NopSysSequence example = new NopSysSequence();
        example.setSeqName(NopSysDaoConstants.SEQ_DEFAULT);
        if (dao.findFirstByExample(example) == null) {
            NopSysSequence seq = new NopSysSequence();
            seq.setSeqName(NopSysDaoConstants.SEQ_DEFAULT);
            seq.setNextValue(1000L);
            seq.setCacheSize(100);
            seq.setSeqType("default");
            seq.setIsUuid(DaoConstants.NO_VALUE);
            seq.setStepSize(1);
            dao.saveEntity(seq);
        }
    }

    private SysSequenceGenerator newGenerator() {
        return generator;
    }

    @SuppressWarnings("unchecked")
    private SysSequenceGenerator.SeqItem fallbackItem(SysSequenceGenerator generator, String seqName)
            throws Exception {
        Field field = SysSequenceGenerator.class.getDeclaredField("defaultCache");
        field.setAccessible(true);
        io.nop.commons.cache.ICache<String, SysSequenceGenerator.SeqItem> defaultCache =
                (io.nop.commons.cache.ICache<String, SysSequenceGenerator.SeqItem>) field.get(generator);
        generator.generateLong(seqName, true);
        return defaultCache.get(seqName);
    }

    /**
     * 缺陷①钉（当前即红）：两个未知序列名的回退项必须是独立 item（独立锁对象），
     * 不得共享 SEQ_DEFAULT 实例。
     */
    @Test
    public void testFallbackItemsArePerSeqName() throws Exception {
        SysSequenceGenerator generator = newGenerator();

        SysSequenceGenerator.SeqItem itemA = fallbackItem(generator, "unknown-seq-a");
        SysSequenceGenerator.SeqItem itemB = fallbackItem(generator, "unknown-seq-b");

        assertNotNull(itemA);
        assertNotNull(itemB);
        assertNotSame(itemA, itemB,
                "C2-005: fallback items must be per-seqName — a shared SEQ_DEFAULT item serializes"
                        + " all default-path draws behind one lock including in-lock DB round trips");
    }

    /**
     * 取号功能钉：并发同序列名取号无重复值（现有 testSequence 仅单线程）。
     */
    @Test
    public void testConcurrentGenerationNoDuplicates() throws Exception {
        SysSequenceGenerator generator = newGenerator();
        int threads = 4;
        int perThread = 25;
        Set<Long> values = ConcurrentHashMap.newKeySet();
        AtomicInteger collisions = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        long v = generator.generateLong("concurrent-seq", true);
                        if (!values.add(v))
                            collisions.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            }, "seq-gen-" + t).start();
        }
        assertTrue(done.await(java.util.concurrent.TimeUnit.SECONDS.toSeconds(30), java.util.concurrent.TimeUnit.SECONDS),
                "generation threads must finish");
        assertEquals(0, collisions.get(), "concurrent draws on the same sequence must never repeat a value");
        assertEquals(threads * perThread, values.size());
    }

    /**
     * 缺陷②钉（当前即红）：removeCache/clearCache 必须同步失效 defaultCache（回退项缓存）。
     */
    @Test
    public void testRemoveCacheInvalidatesDefaultCache() throws Exception {
        SysSequenceGenerator generator = newGenerator();

        // populate cache + defaultCache via fallback draws
        generator.generateLong("invalidated-seq-a", true);
        generator.generateLong("invalidated-seq-b", true);

        generator.removeCache("invalidated-seq-a");
        assertFallbackAbsent(generator, "invalidated-seq-a",
                "C1-007: removeCache must also invalidate the fallback (defaultCache) entry —"
                        + " otherwise an admin nextValue rollback stays invisible to the fallback"
                        + " name for up to the defaultCache TTL");

        generator.clearCache();
        assertFallbackAbsent(generator, "invalidated-seq-b",
                "C1-007: clearCache must also clear the fallback (defaultCache)");
    }

    @SuppressWarnings("unchecked")
    private void assertFallbackAbsent(SysSequenceGenerator generator, String seqName, String message)
            throws Exception {
        Field field = SysSequenceGenerator.class.getDeclaredField("defaultCache");
        field.setAccessible(true);
        io.nop.commons.cache.ICache<String, SysSequenceGenerator.SeqItem> defaultCache =
                (io.nop.commons.cache.ICache<String, SysSequenceGenerator.SeqItem>) field.get(generator);
        assertTrue(defaultCache.get(seqName) == null, message);
    }

    /**
     * 结构钉：取号 DB 环节超时上限经上下文 callExpireTime 传导（runWithContext + setCallExpireTime）。
     * 行为级红不可构造——H2 内存库无法确定性产生 &gt;1s 语句（queryTimeout 秒级粒度），
     * 沿结构钉先例承载（2523/2524）。
     */
    @Test
    public void testSyncDbTimeoutBoundedViaContext() throws Exception {
        Path source = Path.of("src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java");
        String code = Files.readString(source);
        assertTrue(code.contains("provider.attachContext(bounded)"),
                "MA-05: the in-lock DB round trip must run under a bounded context so the"
                        + " statement timeout inherits the sync-db-timeout budget");
        assertTrue(code.contains("getCallExpireTime()"),
                "MA-05: the bounded proxy context must override callExpireTime —"
                        + " JdbcHelper.getQueryTimeout takes the min of it and the global statement timeout");
        assertTrue(code.contains("provider.attachContext(prev)"),
                "MA-05: the bounded context attach must be restored (no ambient context poisoning)");
    }
}
