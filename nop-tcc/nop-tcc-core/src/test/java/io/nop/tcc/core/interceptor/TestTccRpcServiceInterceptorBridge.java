package io.nop.tcc.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.context.TccContext;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccEngine;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.core.meta.TccMethodMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-3.8-005（plan 2388）：TccRpcServiceInterceptor 参与方 inbound TccContext 框架级桥接——
 * 服务端收到携带 txn 头的请求时从请求头建立上下文，嵌套 TCC 调用 join 入站事务而非静默 fork。
 * 钉定点：①桥接可见性+完成恢复；②无头不建上下文；③outbound 不桥接；
 * ④thread-local 赢（不覆盖已有上下文——与网关相反的优先级）；⑤参与方不 startBranch（no-fork）。
 */
public class TestTccRpcServiceInterceptorBridge {

    private static final String TXN_ID = "txn-bridge-1";
    private static final String TXN_GROUP = "grp-a";

    static TccMethodMeta methodMeta(String txnGroup) {
        return new TccMethodMeta(txnGroup, "tryMethod", "confirmMethod", "cancelMethod");
    }

    /** 捕获引擎回调内的 thread-local 快照；isInitiator 可编程（参与方=false）。 */
    static class CapturingEngine implements ITccEngine {
        final AtomicReference<TccContext> contextInsideCallback = new AtomicReference<>();
        final AtomicReference<String> txnIdParam = new AtomicReference<>();
        final AtomicInteger taskApplications = new AtomicInteger();
        boolean initiator = false;

        @Override
        public <T> CompletionStage<T> runInTransactionAsync(String txnGroup, String txnId,
                                                            Function<ITccTransaction, CompletionStage<T>> task) {
            this.txnIdParam.set(txnId);
            this.contextInsideCallback.set(TccContext.getCurrent());
            this.taskApplications.incrementAndGet();
            ITccTransaction txn = new ITccTransaction() {
                @Override
                public boolean isInitiator() {
                    return initiator;
                }

                @Override
                public ITccRecord getTccRecord() {
                    throw new UnsupportedOperationException("not expected in bridge test");
                }

                @Override
                public CompletionStage<Void> beginAsync() {
                    throw new UnsupportedOperationException("not expected in bridge test");
                }

                @Override
                public CompletionStage<Void> endAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
                    throw new UnsupportedOperationException("not expected in bridge test");
                }
            };
            return task.apply(txn);
        }

        @Override
        public <T> CompletionStage<T> runInTransactionAsync(String txnGroup,
                                                            Function<ITccTransaction, CompletionStage<T>> task) {
            return runInTransactionAsync(txnGroup, null, task);
        }

        // 其余引擎面不被拦截器触达：显式 not-implemented（快速失败优于静默）
        @Override
        public ITccTransaction newTransaction(String txnGroup) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public ITccTransaction getCurrentTransaction(String txnGroup) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public ITccBranchTransaction newBranchTransaction(ITccTransaction txn, TccBranchRequest request) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public CompletionStage<ITccTransaction> loadTransactionAsync(String txnGroup, String txnId) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public CompletionStage<List<ITccBranchTransaction>> loadBranchTransactionsAsync(ITccTransaction txn) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public <T> CompletionStage<T> runBranchTransactionAsync(ITccTransaction txn,
                                                                TccBranchRequest branchRequest,
                                                                Function<ITccBranchTransaction, CompletionStage<T>> task) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public void checkExpiredTransactions(long expireGap, int maxRetryCount, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public void cleanCompletedTransactions(long retentionTime) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public <T> T runInTransaction(String txnGroup, Function<ITccTransaction, T> task) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public <T> T runInTransaction(String txnGroup, String txnId, Function<ITccTransaction, T> task) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }

        @Override
        public <T> T runBranchTransaction(ITccTransaction txn, TccBranchRequest branchRequest,
                                          Function<ITccBranchTransaction, T> task) {
            throw new UnsupportedOperationException("not expected in bridge test");
        }
    }

    static IRpcServiceInvocation invocation(boolean inbound, ApiRequest<?> request) {
        return new IRpcServiceInvocation() {
            @Override
            public String getServiceName() {
                return "svcA";
            }

            @Override
            public String getServiceMethod() {
                return "tryMethod";
            }

            @Override
            public ApiRequest<?> getRequest() {
                return request;
            }

            @Override
            public boolean isInbound() {
                return inbound;
            }

            @Override
            public ICancelToken getCancelToken() {
                return null;
            }

            @Override
            public CompletionStage<ApiResponse<?>> proceedAsync() {
                // 捕获 proceed 时刻的 thread-local（业务方法可见的上下文）
                bridgeProbe.set(TccContext.getCurrent());
                return CompletableFuture.completedFuture(ApiResponse.success("ok"));
            }
        };
    }

    static final AtomicReference<TccContext> bridgeProbe = new AtomicReference<>();

    @Test
    public void testInboundHeadersBridgeContextIntoCallbackAndProceed() throws Exception {
        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setTxnId(request, TXN_ID);
        ApiHeaders.setTxnGroup(request, TXN_GROUP);
        ApiHeaders.setTxnBranchId(request, "br-1");
        ApiHeaders.setTxnBranchNo(request, 2);

        CapturingEngine engine = new CapturingEngine();
        TccRpcServiceInterceptor interceptor = new TccRpcServiceInterceptor(engine,
                (svc, method, req) -> methodMeta(TXN_GROUP));

        interceptor.interceptAsync(invocation(true, request)).toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals(TXN_ID, engine.txnIdParam.get(),
                "bridged txnId must reach runInTransactionAsync (join, not fork)");
        TccContext ctx = engine.contextInsideCallback.get();
        assertEquals(TXN_ID, ctx.getTxnId(), "engine callback sees bridged context");
        assertEquals(TXN_GROUP, ctx.getTxnGroup());
        assertEquals("br-1", ctx.getBranchId());
        assertEquals(2, ctx.getBranchNo());
        assertEquals(TXN_ID, bridgeProbe.get().getTxnId(),
                "business method (proceedAsync) sees the bridged context");
        assertNull(TccContext.getCurrent(),
                "thread-local must be restored after the invocation completes");
    }

    @Test
    public void testNoHeadersNoBridge() throws Exception {
        ApiRequest<?> request = new ApiRequest<>();
        CapturingEngine engine = new CapturingEngine();
        TccRpcServiceInterceptor interceptor = new TccRpcServiceInterceptor(engine,
                (svc, method, req) -> methodMeta(TXN_GROUP));

        interceptor.interceptAsync(invocation(true, request)).toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertNull(engine.txnIdParam.get(), "no txn headers → no bridge → txnId stays null");
        assertNull(engine.contextInsideCallback.get(), "no context must be fabricated from nothing");
        assertNull(TccContext.getCurrent());
    }

    @Test
    public void testOutboundNotBridged() throws Exception {
        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setTxnId(request, TXN_ID);

        // outbound + TCC 方法会走 startBranch（客户端负责记录 branch）——内联覆盖分支桩
        AtomicReference<TccContext> contextAtBranch = new AtomicReference<>();
        CapturingEngine engine = new CapturingEngine() {
            @Override
            public <T> CompletionStage<T> runBranchTransactionAsync(ITccTransaction txn,
                                                                    TccBranchRequest branchRequest,
                                                                    Function<ITccBranchTransaction, CompletionStage<T>> task) {
                contextAtBranch.set(TccContext.getCurrent());
                return CompletableFuture.completedFuture(null);
            }
        };
        TccRpcServiceInterceptor interceptor = new TccRpcServiceInterceptor(engine,
                (svc, method, req) -> methodMeta(TXN_GROUP));

        interceptor.interceptAsync(invocation(false, request)).toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertNull(engine.contextInsideCallback.get(),
                "outbound invocations must not build context from request headers");
        assertNull(contextAtBranch.get(),
                "branch-side business execution must not see a fabricated context either");
    }

    @Test
    public void testExistingThreadLocalWinsOverHeaders() throws Exception {
        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setTxnId(request, "txn-from-header");

        CapturingEngine engine = new CapturingEngine();
        TccRpcServiceInterceptor interceptor = new TccRpcServiceInterceptor(engine,
                (svc, method, req) -> methodMeta(TXN_GROUP));

        TccContext existing = new TccContext();
        existing.setTxnId("txn-thread-local");
        existing.setTxnGroup(TXN_GROUP);
        TccContext.setCurrent(existing);
        try {
            interceptor.interceptAsync(invocation(true, request)).toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertEquals("txn-thread-local", engine.contextInsideCallback.get().getTxnId(),
                    "existing thread-local context must win (not overridden by headers)");
        } finally {
            TccContext.removeCurrent(existing);
        }
    }

    @Test
    public void testParticipantDoesNotStartBranch() throws Exception {
        ApiRequest<?> request = new ApiRequest<>();
        ApiHeaders.setTxnId(request, TXN_ID);
        ApiHeaders.setTxnGroup(request, TXN_GROUP);

        CapturingEngine engine = new CapturingEngine();
        engine.initiator = false; // 参与方：join 的事务不是本服务发起
        AtomicInteger proceedCount = new AtomicInteger();
        TccRpcServiceInterceptor interceptor = new TccRpcServiceInterceptor(engine,
                (svc, method, req) -> methodMeta(TXN_GROUP)) {
            @Override
            protected boolean shouldStartBranch(boolean inbound, boolean txnInitiator,
                                                TccMethodMeta methodMeta) {
                boolean should = super.shouldStartBranch(inbound, txnInitiator, methodMeta);
                if (should)
                    proceedCount.incrementAndGet();
                return should;
            }
        };

        ApiResponse<?> resp = interceptor.interceptAsync(invocation(true, request))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(resp.isOk());
        assertEquals(0, proceedCount.get(),
                "participant (non-initiator, inbound) must not start a branch — the caller records it");
        assertEquals(1, engine.taskApplications.get());
    }
}
