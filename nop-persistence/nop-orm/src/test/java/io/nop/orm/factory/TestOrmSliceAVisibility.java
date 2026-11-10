package io.nop.orm.factory;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2552 切片束 a 结构钉（承载 C10-011/012）：
 * pin-a：SessionFactoryImpl.interceptors/daoListeners 可见性形态（C1-005）；
 * pin-b：静态槽 volatile（C1-006/024，OrmTimestampHelper/DaoProvider）；
 * pin-c：OrmBatchLoadQueueImpl 队列字段 volatile + 捕获-清空同锁域（C1-007）；
 * pin-d：reloadModel 清查询计划缓存（C1-008——行为半边由 reloadModel 调用链断言）。
 * 变异红形态：去 volatile / 删 clearQueryPlanCache 调用后各 pin 红。
 */
public class TestOrmSliceAVisibility {

    private static boolean isVolatile(Class<?> clazz, String field) throws Exception {
        Field f = clazz.getDeclaredField(field);
        return Modifier.isVolatile(f.getModifiers());
    }

    @Test
    public void pin_a_factoryVisibilityForm() throws Exception {
        assertTrue(isVolatile(SessionFactoryImpl.class, "interceptors"),
                "C1-005: interceptors COW list must be volatile");
        Field daoListeners = SessionFactoryImpl.class.getDeclaredField("daoListeners");
        assertTrue(Modifier.isFinal(daoListeners.getModifiers()),
                "C1-005: resident daoListeners container must be final (always non-null)");
    }

    @Test
    public void pin_b_staticSlotsVolatile() throws Exception {
        assertTrue(isVolatile(io.nop.orm.persister.OrmTimestampHelper.class, "_instance"),
                "C1-006: OrmTimestampHelper._instance must be volatile");
        assertTrue(isVolatile(io.nop.dao.api.DaoProvider.class, "_instance"),
                "C1-024: DaoProvider._instance must be volatile");
    }

    @Test
    public void pin_c_batchQueueVisibilityAndLockDomain() throws Exception {
        assertTrue(isVolatile(io.nop.orm.loader.OrmBatchLoadQueueImpl.class, "loadQueue"),
                "C1-007: loadQueue must be volatile");
        assertTrue(isVolatile(io.nop.orm.loader.OrmBatchLoadQueueImpl.class, "afterFlushCallbacks"),
                "C1-007: afterFlushCallbacks must be volatile");
        String src = Files.readString(Paths.get(
                "src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java"));
        // 捕获-清空同锁域 + 锁外任务体（变异红：裸读捕获或锁内 task.run 后红）。
        // plan 2553 C2-009 重放循环将捕获块抽入 flushLoadsOnce()（方法体一级缩进，锁域语义不变）
        assertTrue(src.contains("synchronized (this) {\n            queue = this.loadQueue;"),
                "flush capture-clear must be in synchronized domain");
        assertTrue(src.contains("synchronized (this) {\n            tasks = this.afterFlushCallbacks;"),
                "invokeCallback capture must be lock-scoped with run outside");
    }

    @Test
    public void pin_d_reloadModelClearsQueryPlanCache() throws Exception {
        String src = Files.readString(Paths.get("src/main/java/io/nop/orm/factory/SessionFactoryImpl.java"));
        // reloadModel 体含 clearQueryPlanCache 调用（变异红：删除后红）
        int reloadStart = src.indexOf("public ILoadedOrmModel reloadModel()");
        assertTrue(reloadStart >= 0);
        String body = src.substring(reloadStart, Math.min(src.length(), reloadStart + 500));
        assertTrue(body.contains("clearQueryPlanCache()"),
                "C1-008: reloadModel must clear query plan cache");
    }
}
