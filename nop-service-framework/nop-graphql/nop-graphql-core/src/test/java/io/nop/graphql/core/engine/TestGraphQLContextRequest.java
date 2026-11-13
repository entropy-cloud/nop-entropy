package io.nop.graphql.core.engine;

import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.ParsedGraphQLRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C1-004（plan 2550 裁定 A）钉：setRequest/getRequest 监视器一致。
 * pin4：并发 setRequest × getRequest 后，读侧最终观测到最新写入值（同一请求实例），
 * 全程无异常（监视器失配下读侧可能读到撕裂的透传中间态）。
 */
public class TestGraphQLContextRequest extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    @Test
    public void pin4_concurrentSetGetRequestConsistent() throws Exception {
        GraphQLExecutionContext ctx = new GraphQLExecutionContext(new ServiceContextImpl());
        ParsedGraphQLRequest finalRequest = new ParsedGraphQLRequest();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int w = 0; w < 3; w++) {
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        ctx.setRequest(new ParsedGraphQLRequest());
                    }
                } catch (Throwable t) {
                    failed.set(true);
                } finally {
                    done.countDown();
                }
            });
        }
        pool.execute(() -> {
            try {
                start.await();
                for (int i = 0; i < 500; i++) {
                    Object r = ctx.getRequest(); // 读侧不得抛异常/撕裂
                }
            } catch (Throwable t) {
                failed.set(true);
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        // 收敛后写入最终值，读侧必得同实例（监视器一致 + 透传正确）
        ctx.setRequest(finalRequest);
        assertFalse(failed.get(), "concurrent setRequest/getRequest must not fail");
        assertSame(finalRequest, ctx.getRequest(), "getRequest must observe latest setRequest");
    }
}
