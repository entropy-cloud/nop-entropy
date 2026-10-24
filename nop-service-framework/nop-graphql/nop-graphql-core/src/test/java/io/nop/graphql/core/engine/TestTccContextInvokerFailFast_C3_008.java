package io.nop.graphql.core.engine;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.context.TccContext;
import io.nop.graphql.core.IDataFetchingEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CRA 2.3-C3-008（plan 2499 Phase 3，裁定③）：TccContextInvoker 入口 fail-fast + 恢复逻辑
 * 对捕获的 IContext 对象属性写回。
 * <p>
 * 缺陷：TCC set/restore 依赖「completion 与调用同线程」假设——无线附着线程发起时
 * TccContext.setCurrent 经 getOrCreateContext 永久附着新建 context，跨任务残留
 * txnId/branchId → TCC 分支错配（条件化，与 C3-007 复合）。
 * <p>
 * 判别口径：(1) fail-fast——无线附着时 invokeAsync 抛语义化错误
 * ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT 且不附着任何 context（修复前静默 setCurrent 附着，红）；
 * (2) 恢复写回——完成后捕获 context 对象的 TccContext 属性恢复原值/清除，无残留。
 */
public class TestTccContextInvokerFailFast_C3_008 {

    static final String TCC_ATTR = TccContext.class.getName();

    @BeforeEach
    public void setUp() {
        BaseContextProvider.clear();
    }

    @AfterEach
    public void tearDown() {
        BaseContextProvider.clear();
    }

    /**
     * 构建携带 TCC 事务头的执行环境。注记：ServiceContextImpl 字段初始化
     * （getOrCreateContext）会在测试线程上附着一个兜底 context——构建后立即清除线程附着，
     * 使各用例从「无线附着」前置状态出发（C3-008 fail-fast 判别面）。
     */
    private IDataFetchingEnvironment envWithTxnHeaders() {
        ServiceContextImpl svc = new ServiceContextImpl();
        BaseContextProvider.clear();
        svc.setRequestHeaders(Map.of(
                ApiConstants.HEADER_TXN_ID, "txn-1",
                ApiConstants.HEADER_TXN_GROUP, "grp-1",
                ApiConstants.HEADER_TXN_BRANCH_ID, "br-1"));
        GraphQLExecutionContext execCtx = new GraphQLExecutionContext(svc);
        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(execCtx);
        return env;
    }

    /**
     * 判别用例（fail-fast）：无线附着线程发起 TCC 调用——修复前静默经 setCurrent 的
     * getOrCreateContext 新建并永久附着（currentContext()!=null 可观测，红）；修复后抛
     * ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT 语义化错误，线程无任何附着。
     */
    @Test
    public void testInvokeAsyncWithoutBoundContextFailsFast() {
        assertNull(ContextProvider.currentContext(), "test precondition: no bound context");

        TccContextInvoker invoker = new TccContextInvoker(null);

        NopException ex = assertThrows(NopException.class,
                () -> invoker.invokeAsync(req -> FutureHelper.success("ok"), envWithTxnHeaders()));
        assertEquals(ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT.getErrorCode(), ex.getErrorCode());

        // fail-fast 非静默附着：线程保持无线附着（无 getOrCreateContext 泄漏）
        assertNull(ContextProvider.currentContext(),
                "fail-fast must not silently attach a context to the calling thread");
    }

    /**
     * 恢复写回（无旧值形态）：完成后捕获 context 对象的 TccContext 属性清除，无
     * txnId/branchId 残留。
     */
    @Test
    public void testRestoreClearsTccAttributeOnCapturedContext() throws Exception {
        IDataFetchingEnvironment env = envWithTxnHeaders();
        IContext bound = ContextProvider.newContext(true);
        try {
            TccContextInvoker invoker = new TccContextInvoker(null);

            CompletionStage<String> result = invoker.invokeAsync(
                    req -> FutureHelper.success("done"), env);

            assertEquals("done", result.toCompletableFuture().get(2, TimeUnit.SECONDS));

            // 无旧值：恢复逻辑清除 TCC 属性（对捕获对象写回，非完成线程 currentContext 读点）
            assertNull(bound.getAttribute(TCC_ATTR), "no TCC residue may remain on the bound context");
            assertSame(bound, ContextProvider.currentContext());
        } finally {
            bound.close();
        }
    }

    /**
     * 恢复写回（有旧值形态）：完成后旧 TccContext 原值写回捕获 context。
     */
    @Test
    public void testRestoreWritesBackOldTccAttribute() throws Exception {
        IDataFetchingEnvironment env = envWithTxnHeaders();
        IContext bound = ContextProvider.newContext(true);
        try {
            TccContext oldTcc = new TccContext();
            oldTcc.setTxnId("old-txn");
            bound.setAttribute(TCC_ATTR, oldTcc);

            TccContextInvoker invoker = new TccContextInvoker(null);

            CompletionStage<String> result = invoker.invokeAsync(
                    req -> FutureHelper.success("done"), env);

            assertEquals("done", result.toCompletableFuture().get(2, TimeUnit.SECONDS));

            Object restored = bound.getAttribute(TCC_ATTR);
            assertSame(oldTcc, restored, "old TccContext must be written back to the captured context");
        } finally {
            bound.close();
        }
    }

    /**
     * 失败路径恢复：任务体异常完成时恢复逻辑同样执行（whenComplete 语义），且异常传递
     * 给调用方——rule 24 失败可见非静默吞。
     */
    @Test
    public void testRestoreRunsOnTaskFailureAndPropagatesError() {
        IDataFetchingEnvironment env = envWithTxnHeaders();
        IContext bound = ContextProvider.newContext(true);
        try {
            TccContextInvoker invoker = new TccContextInvoker(null);

            CompletionStage<String> result = invoker.invokeAsync(
                    req -> FutureHelper.reject(new IllegalStateException("task-body-fail")),
                    env);

            Throwable err = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> result.toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause();
            assertEquals("task-body-fail", err.getMessage());

            assertNull(bound.getAttribute(TCC_ATTR),
                    "restore must also run on the failure completion path");
        } finally {
            bound.close();
        }
    }

    /**
     * 无 TCC 头（buildFromServiceContext 返回 null）：直接透传执行，零介入零附着。
     */
    @Test
    public void testNoTxnHeadersPassesThroughWithoutAttachment() throws Exception {
        ServiceContextImpl svc = new ServiceContextImpl();
        BaseContextProvider.clear();
        svc.setRequestHeaders(Map.of("x-other", "1"));
        GraphQLExecutionContext execCtx = new GraphQLExecutionContext(svc);
        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(execCtx);

        TccContextInvoker invoker = new TccContextInvoker(null);

        CompletableFuture<String> task = new CompletableFuture<>();
        CompletionStage<String> result = invoker.invokeAsync(req -> task, env);

        assertNull(ContextProvider.currentContext(), "pass-through must not attach anything");
        task.complete("passthrough");
        assertEquals("passthrough", result.toCompletableFuture().get(2, TimeUnit.SECONDS));
    }
}
