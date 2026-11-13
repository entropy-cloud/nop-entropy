package io.nop.graphql.core.fetcher;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.engine.DataFetchingEnvironment;
import io.nop.graphql.core.engine.GraphQLExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * CRA 2.3-C3-015（plan 2499 Phase 3，裁定③与 C3-002 同批同验收，A4）：fetcher 层异步出口
 * 统一挂接 thenOnContext。
 * <p>
 * 缺陷：TransformFetcher/EvalActionTransformFetcher 的 thenCompose 续行（transformer 用户
 * 表达式，可携带 env/EvalScope）在上游 future 完成线程执行——线程上下文读到空/残留，
 * EvalScope 线程亲和未声明。
 * <p>
 * 判别口径：上游 future 在 ForkJoinPool 完成线程交付，transformer 内观测
 * currentContext() 即请求自身 context（assertSame）。修复前 transformer 在 ForkJoinPool
 * 线程执行、观测为 null（先红）；修复后经 thenOnContext 在请求 context 附着状态下执行（绿）。
 */
public class TestFetcherThenOnContext_C3_015 {

    @BeforeEach
    public void setUp() {
        BaseContextProvider.clear();
    }

    @AfterEach
    public void tearDown() {
        BaseContextProvider.clear();
    }

    private IDataFetchingEnvironment newEnv(IContext requestContext) {
        GraphQLExecutionContext execCtx = new GraphQLExecutionContext();
        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(execCtx);
        return env;
    }

    @Test
    public void testTransformFetcherRunsTransformerOnRequestContext() throws Exception {
        IContext requestContext = ContextProvider.newContext(true);
        requestContext.setTenantId("t-c3-015");
        try {
            IDataFetchingEnvironment env = newEnv(requestContext);
            AtomicReference<IContext> observed = new AtomicReference<>();

            // 上游在 fetcher.get() 注册完成之后才由外部线程交付——确定性复现「transformer
            // 落到上游完成线程」的缺陷形态（注册即完成的竞态会掩盖缺陷）
            CompletableFuture<String> upstream = new CompletableFuture<>();
            TransformFetcher fetcher = new TransformFetcher(
                    e -> upstream,
                    (value, e) -> {
                        observed.set(ContextProvider.currentContext());
                        return value + "!";
                    });

            Object result = fetcher.get(env);
            CompletableFuture.runAsync(() -> upstream.complete("v"));

            assertEquals("v!", ((CompletionStage<?>) result).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS));

            assertSame(requestContext, observed.get(),
                    "transformer must run with the request context attached (fetcher-level thenOnContext)");
        } finally {
            requestContext.close();
        }
    }

    @Test
    public void testEvalActionTransformFetcherRunsTransformOnRequestContext() throws Exception {
        IContext requestContext = ContextProvider.newContext(true);
        requestContext.setTenantId("t-c3-015-eval");
        try {
            IDataFetchingEnvironment env = newEnv(requestContext);
            AtomicReference<IContext> observed = new AtomicReference<>();

            IEvalAction transformAction = new IEvalAction() {
                @Override
                public Object invoke(IEvalContext ctx) {
                    observed.set(ContextProvider.currentContext());
                    return "eval-ok";
                }
            };

            CompletableFuture<String> upstream = new CompletableFuture<>();
            EvalActionTransformFetcher fetcher = new EvalActionTransformFetcher(
                    e -> upstream, transformAction);

            Object result = fetcher.get(env);
            CompletableFuture.runAsync(() -> upstream.complete("raw"));

            assertEquals("eval-ok", ((CompletionStage<?>) result).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS));

            assertSame(requestContext, observed.get(),
                    "XPL transform must run with the request context attached");
        } finally {
            requestContext.close();
        }
    }

    /**
     * 同步出口零回归：上游非 CompletionStage 时 transform 仍内联在调用线程执行（挂接只
     * 作用于异步出口）。
     */
    @Test
    public void testSyncOutletKeepsInlineTransform() throws Exception {
        IContext requestContext = ContextProvider.newContext(true);
        try {
            IDataFetchingEnvironment env = newEnv(requestContext);
            AtomicReference<IContext> observed = new AtomicReference<>();

            TransformFetcher fetcher = new TransformFetcher(
                    e -> "raw",
                    (value, e) -> {
                        observed.set(ContextProvider.currentContext());
                        return value + "!";
                    });

            Object result = fetcher.get(env);
            assertEquals("raw!", result);
            assertSame(requestContext, observed.get());
        } finally {
            requestContext.close();
        }
    }
}
