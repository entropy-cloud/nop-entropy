package io.nop.dyn.service.codegen;

import io.nop.core.module.ModuleModel;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.orm.model.OrmModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复现并回归 getOrmModel 与 removeModule 的锁顺序：
 * 修复前 getOrmModel 在 ormModels 的 CHM bin 锁内调用 synchronized 的 genOrmModel（等待 this），
 * 而 removeModule 持 this 后调用 ormModels.remove（等待同一 bin 锁），形成 ABBA 死锁。
 */
public class TestInMemoryCodeCacheDeadlock {

    static class TestHook implements IDynCodeGenCacheHook {
        volatile CountDownLatch unloadEntered;
        volatile CountDownLatch releaseUnload;

        @Override
        public Map<String, GraphQLBizModel> prepareLoadModule(InMemoryCodeCache cache, ModuleModel module,
                                                              io.nop.core.lang.eval.IEvalScope scope) {
            return Collections.emptyMap();
        }

        @Override
        public void prepareUnloadModule(InMemoryCodeCache cache, ModuleModel module,
                                        io.nop.core.lang.eval.IEvalScope scope) {
            if (unloadEntered != null) {
                unloadEntered.countDown();
                await(releaseUnload);
            }
        }

        @Override
        public void prepareBizObject(InMemoryCodeCache cache, GraphQLBizModel bizModel,
                                     ModuleModel module, io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareOrmModel(InMemoryCodeCache cache, ModuleModel module,
                                    io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareResource(InMemoryCodeCache cache, String path, io.nop.core.lang.eval.IEvalScope scope) {
        }
    }

    static void await(CountDownLatch latch) {
        try {
            if (latch != null)
                latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    static void seedEnabledModules(InMemoryCodeCache cache, ModuleModel module) throws Exception {
        Field field = InMemoryCodeCache.class.getDeclaredField("enabledModules");
        field.setAccessible(true);
        Map<String, ModuleModel> map = (Map<String, ModuleModel>) field.get(cache);
        map.put(module.getModuleId(), module);
    }

    @Test
    public void testGetOrmModelAndRemoveModuleNoDeadlock() throws Exception {
        TestHook hook = new TestHook();
        hook.unloadEntered = new CountDownLatch(1);
        hook.releaseUnload = new CountDownLatch(1);

        final AtomicInteger genOrmModelEntered = new AtomicInteger();
        InMemoryCodeCache cache = new InMemoryCodeCache(null, "/nop/templates/dyn-gen", hook) {
            @Override
            protected synchronized OrmModel genOrmModel(boolean formatGenCode, ModuleModel module) {
                genOrmModelEntered.incrementAndGet();
                return new OrmModel();
            }
        };

        ModuleModel module = new ModuleModel();
        module.setModuleId("app/demo");
        seedEnabledModules(cache, module);

        // 线程B：removeModule 持有 this 后阻塞在 prepareUnloadModule 钩子中
        Thread remover = new Thread(() -> cache.removeModule("app/demo"), "removeModule-thread");
        remover.setDaemon(true);
        remover.start();
        assertTrue(hook.unloadEntered.await(5, TimeUnit.SECONDS), "removeModule应已持有this并进入unload钩子");

        // 线程A：getOrmModel。修复前：先取ormModels的bin锁，再等待this；修复后：先等this
        Thread loader = new Thread(() -> cache.getOrmModel(module, false), "getOrmModel-thread");
        loader.setDaemon(true);
        loader.start();

        // 等待A进入monitor等待（两种实现下都是BLOCKED状态）
        long deadline = System.currentTimeMillis() + 5000;
        while (loader.getState() != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        // 释放B：B继续执行到 ormModels.remove（修复前在bin锁上被A卡死）并释放this
        hook.releaseUnload.countDown();

        // 两个线程都必须在超时内完成，否则即死锁
        remover.join(5000);
        loader.join(5000);
        assertFalse(remover.isAlive(), "removeModule未卡死（修复前与getOrmModel形成ABBA死锁）");
        assertFalse(loader.isAlive(), "getOrmModel未卡死（修复前在bin锁内等待this被死锁）");
    }
}
