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
        volatile CountDownLatch entered;
        volatile CountDownLatch release;

        @Override
        InMemoryCodeCache initTenantCache(String tenantId) {
            initCount.incrementAndGet();
            entered.countDown();
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
        // 两个线程都"进入init"才算双双执行（修复后第二个线程在锁上等待，不会进入）
        codeGen.entered = new CountDownLatch(2);
        codeGen.release = new CountDownLatch(1);

        AtomicReference<InMemoryCodeCache> refA = new AtomicReference<>();
        AtomicReference<InMemoryCodeCache> refB = new AtomicReference<>();
        Thread threadA = new Thread(() -> refA.set(codeGen.getTenantCodeCache("t1")), "tenant-init-a");
        Thread threadB = new Thread(() -> refB.set(codeGen.getTenantCodeCache("t1")), "tenant-init-b");

        threadA.start();
        // A已进入init并阻塞在latch上，此时B发起同一租户的首次访问
        Thread.sleep(100);
        threadB.start();

        boolean bothEntered = codeGen.entered.await(1, TimeUnit.SECONDS);

        codeGen.release.countDown();
        threadA.join(5000);
        threadB.join(5000);

        assertEquals(1, codeGen.initCount.get(),
                "并发首次访问时initTenantCache必须只执行一次，修复前两个线程都读到null会各自执行一遍");
        assertSame(refA.get(), refB.get(), "竞争线程必须复用先完成初始化的缓存实例");
        // bothEntered为true说明修复前行为：两个线程都重复执行了init
        assertEquals(false, bothEntered, "第二个线程不应进入init，而是在锁上等待后直接复用");
    }
}
