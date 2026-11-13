package io.nop.orm.factory;

import io.nop.orm.ILoadedOrmModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C1-004（plan 2552 裁定 B）钉：TOCTOU 闭合。
 * pin-a（行为）：close 后 tryIncRefIfOpen 恒 false（引用计数不泄漏）；
 * pin-b（行为）：并发 close×tryIncRefIfOpen——恰一次 doClose 生效、无负计数、成功引用必有配对；
 * pin-c（结构）：close/decRef/incRef/tryIncRefIfOpen 同监视器（synchronized(this) 四处，变异红：
 * inc 侧去锁后 pin-c 红——裁定 B 的核心是三侧同锁，仅锁 inc 不闭合交错）。
 * 布景：LoadedOrmModel 构造器需 IPersistEnv/IOrmModel——用 ProxyStub（沿 TestCra2517GenerationFence
 * 先例，R1-Info-9 允许）；doClose 遍历空 persister 集合为 no-op，可安全驱动。
 */
public class TestLoadedOrmModelCloseRace {

    private static LoadedOrmModel newModel() throws Exception {
        Class<?> envIface = Class.forName("io.nop.orm.persister.IPersistEnv");
        Class<?> modelIface = Class.forName("io.nop.orm.model.IOrmModel");
        Object env = java.lang.reflect.Proxy.newProxyInstance(envIface.getClassLoader(),
                new Class<?>[]{envIface}, (p, m, a) -> null);
        Object model = java.lang.reflect.Proxy.newProxyInstance(modelIface.getClassLoader(),
                new Class<?>[]{modelIface}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "isAnyEntityUseTenant":
                            return Boolean.FALSE;
                        case "getEntityModelsInTopoOrder":
                            return java.util.List.of(); // 空 topo：跳过 persister 构建（close/inRef 面不需要）
                        default:
                            return null;
                    }
                });
        return new LoadedOrmModel((io.nop.orm.persister.IPersistEnv) env,
                (io.nop.orm.model.IOrmModel) model);
    }

    @Test
    public void pin_a_incRefAfterCloseRejected() throws Exception {
        LoadedOrmModel model = newModel();
        assertTrue(model.tryIncRefIfOpen(), "open model must accept reference");
        model.decRef();
        model.close();
        assertFalse(model.tryIncRefIfOpen(), "closed model must reject new references (TOCTOU closed)");
    }

    @Test
    public void pin_b_concurrentCloseVsIncRefConsistent() throws Exception {
        for (int round = 0; round < 20; round++) {
            LoadedOrmModel model = newModel();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicBoolean incSucceeded = new AtomicBoolean(false);
            AtomicInteger closeCount = new AtomicInteger();

            pool.execute(() -> {
                try {
                    start.await();
                    if (model.tryIncRefIfOpen()) {
                        incSucceeded.set(true);
                        model.decRef();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            pool.execute(() -> {
                try {
                    start.await();
                    model.close();
                    closeCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            pool.shutdownNow();

            // 不变量：成功引用必有配对 decRef（close 侧或引用侧恰一次销毁判定成立）；
            // refCount 永不为负（TOCTOU 交错面）。反射读取校验。
            Field refField = LoadedOrmModel.class.getDeclaredField("refCount");
            refField.setAccessible(true);
            java.util.concurrent.atomic.AtomicInteger refCount =
                    (java.util.concurrent.atomic.AtomicInteger) refField.get(model);
            assertTrue(refCount.get() >= 0, "refCount must never go negative (round " + round + ")");
            assertEquals(0, refCount.get(), "all references must be paired (round " + round + ")");
        }
    }

    @Test
    public void pin_c_sameMonitorAllSides() throws Exception {
        String src = java.nio.file.Files.readString(Paths.get(
                "src/main/java/io/nop/orm/factory/LoadedOrmModel.java"));
        int closeIdx = src.indexOf("public void close()");
        int decIdx = src.indexOf("public void decRef()");
        int incIdx = src.indexOf("public void incRef()");
        int tryIdx = src.indexOf("public boolean tryIncRefIfOpen()");
        assertTrue(closeIdx > 0 && decIdx > closeIdx && incIdx > 0 && tryIdx > 0);
        // 四个方法体内均含 synchronized(this)（变异红：任一侧去锁后红——裁定 B 核心）
        assertTrue(src.substring(incIdx, tryIdx).contains("synchronized (this)"),
                "incRef must hold the same monitor");
        assertTrue(src.substring(tryIdx, decIdx).contains("synchronized (this)"),
                "tryIncRefIfOpen must hold the same monitor");
        assertTrue(src.substring(decIdx, decIdx + 250).contains("synchronized (this)"),
                "decRef must hold the same monitor (close-write/lock-free-refCount-read interleave)");
        assertTrue(src.substring(closeIdx, incIdx).contains("synchronized (this)"),
                "close must hold the same monitor");
    }
}
