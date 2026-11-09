package io.nop.dao.jdbc.impl;

import io.nop.dao.api.DaoProvider;
import io.nop.dao.txn.impl.AbstractTransaction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2552 切片束 a（nop-dao）钉：
 * pin-a（C1-024）：DaoProvider._instance volatile（结构钉）；
 * pin-b（C1-025）：AbstractTransaction opened/rollbackOnly/error volatile（承载 C10-011）；
 * pin-c（C1-027）：查询缓存世代围栏——bump 站点三处 + miss 捕获 + 回填前比对（承载 C10-005 残面；
 * 变异红：回填去比对后 pin-c 红）。
 */
public class TestDaoSliceA {

    private static boolean isVolatile(Class<?> clazz, String field) throws Exception {
        return Modifier.isVolatile(clazz.getDeclaredField(field).getModifiers());
    }

    @Test
    public void pin_a_daoProviderVolatile() throws Exception {
        assertTrue(isVolatile(DaoProvider.class, "_instance"), "C1-024: DaoProvider._instance must be volatile");
    }

    @Test
    public void pin_b_transactionStateVolatile() throws Exception {
        assertTrue(isVolatile(AbstractTransaction.class, "opened"), "C1-025: opened must be volatile");
        assertTrue(isVolatile(AbstractTransaction.class, "rollbackOnly"), "C1-025: rollbackOnly must be volatile");
        assertTrue(isVolatile(AbstractTransaction.class, "error"), "C1-025: error must be volatile");
    }

    @Test
    public void pin_c_queryCacheGenerationFence() throws Exception {
        String src = Files.readString(Paths.get("src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java"));
        // bump 站点：三处失效入口
        long bumpSites = src.lines().filter(l -> l.contains("bumpQueryCacheEpoch")).count();
        assertEquals(4, bumpSites, // 3 调用点[含 method-ref] + 1 方法定义
                "clearQueryCache/clearQueryCacheFor/evictQueryCache must all bump the epoch");
        // miss 时点捕获（DB 读前）
        assertTrue(src.contains("final long capturedEpoch"), "captured epoch at miss time");
        // 回填前比对（变异红：删除比对块后红）
        assertTrue(src.contains("if (currentEpoch != capturedEpoch) {"),
                "saveCacheData must compare epoch before cache.put");
        assertTrue(src.contains("nop.dao.query-cache-stale-miss-abandon"),
                "stale-miss abandonment must be observable");
    }
}
