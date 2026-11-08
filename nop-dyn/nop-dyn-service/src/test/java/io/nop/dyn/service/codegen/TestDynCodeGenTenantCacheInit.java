package io.nop.dyn.service.codegen;

import io.nop.core.module.ModuleModel;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户缓存并发首次访问时 initTenantCache（全模块DB查询+代码生成）必须只执行一次：
 * 竞争线程在容器引用上等待初始化完成后直接复用，而不是各自完整执行一遍后丢弃多余结果
 */
public class TestDynCodeGenTenantCacheInit {

    static class NoopHook implements IDynCodeGenCacheHook {
        final Map<String, GraphQLBizModel> loadResult = new HashMap<>();

        @Override
        public Map<String, GraphQLBizModel> prepareLoadModule(InMemoryCodeCache cache, ModuleModel module,
                                                              io.nop.core.lang.eval.IEvalScope scope) {
            return loadResult;
        }

        @Override
        public void prepareUnloadModule(InMemoryCodeCache cache, ModuleModel module,
                                        io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareBizObject(InMemoryCodeCache cache, GraphQLBizModel bizModel, ModuleModel module,
                                      io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareOrmModel(InMemoryCodeCache cache, ModuleModel module,
                                     io.nop.core.lang.eval.IEvalScope scope) {
        }

        @Override
        public void prepareResource(InMemoryCodeCache cache, String path,
                                    io.nop.core.lang.eval.IEvalScope scope) {
        }
    }

    static class CountingInitDynCodeGen extends DynCodeGen {
        final NoopHook hook = new NoopHook();
        final AtomicInteger initCount = new AtomicInteger();
        volatile CountDownLatch aEntered;
        volatile CountDownLatch release;

        @Override
        InMemoryCodeCache initTenantCache(String tenantId) {
            initCount.incrementAndGet();
            aEntered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new InMemoryCodeCache(tenantId, "/nop/templates/dyn-gen", hook);
        }
    }

    @Test
    public void testConcurrentTenantInitExecutedOnce() throws Exception {
        CountingInitDynCodeGen codeGen = new CountingInitDynCodeGen();
        // A 进入 init 后即发信号（修复后第二个线程在锁上等待，不会进入）
        codeGen.aEntered = new CountDownLatch(1);
        codeGen.release = new CountDownLatch(1);

        AtomicReference<InMemoryCodeCache> refA = new AtomicReference<>();
        AtomicReference<InMemoryCodeCache> refB = new AtomicReference<>();
        Thread threadA = new Thread(() -> refA.set(codeGen.getTenantCodeCache("t1")), "tenant-init-a");
        Thread threadB = new Thread(() -> refB.set(codeGen.getTenantCodeCache("t1")), "tenant-init-b");

        threadA.start();
        // A已进入init并阻塞在latch上，此时B发起同一租户的首次访问（显式同步，不依赖时间等待）
        assertTrue(codeGen.aEntered.await(5, TimeUnit.SECONDS), "A必须先进入init");
        threadB.start();

        codeGen.release.countDown();
        threadA.join(5000);
        threadB.join(5000);

        assertEquals(1, codeGen.initCount.get(),
                "并发首次访问时initTenantCache必须只执行一次，修复前两个线程都读到null会各自执行一遍");
        assertSame(refA.get(), refB.get(), "竞争线程必须复用先完成初始化的缓存实例");
    }
}
