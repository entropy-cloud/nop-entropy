package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 2.3-C3-002（plan 2499 Phase 3，裁定③形态 A）：出口泵/递归泵挂接泵级 thenOnContext。
 * <p>
 * 缺陷：异步顶层 operation 完成后 fetchNext 游走与 dispatchAll 调度整体在 future 完成线程
 * （CompletableFuture.supplyAsync 的 ForkJoinPool 线程）执行——批量 loader 的线程上下文读点
 * （真实场景 = OrmBatchLoader.requireSession 按 currentContext 取 ORM 会话）读到空/残留
 * context：NOT_IN_SESSION 或落入 worker 残留会话（错会话脏数据）。
 * <p>
 * 判别口径（rule 23 接线验证）：真实 GraphQLExecutor 链上，批量 loader 执行时可观测
 * currentContext 即请求自身的 context 载体（assertSame + tenant 断言）。修复前 probe 记录
 * 为 null（ForkJoinPool 完成线程无线附着）→ 先红；修复后经泵级 thenOnContext 在请求
 * context 附着状态下执行 → 绿。
 * <p>
 * 递归泵（dispatchAll 批次完成续轮）与出口泵共用同一挂接原语（GraphQLExecutor.dispatchAll
 * 单点），嵌套异步字段的调度生命周期由既有 TestGraphQLDataLoaderDispatchLifecycle 4 条
 * 零回改守护。
 */
public class TestGraphQLExecutorContextPump_C3_002 {

    /** 批量 loader 执行时的 [观察到的 IContext, 线程名] 记录 */
    static final List<Object[]> BATCH_PROBES = new CopyOnWriteArrayList<>();

    public static class ProbeTree {
        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BizModel("Tree")
    public static class PumpProbeBizModel {

        static ProbeTree tree(String id) {
            ProbeTree t = new ProbeTree();
            t.setId(id);
            t.setName("name-" + id);
            return t;
        }

        /** 异步顶层 operation：在 ForkJoinPool 完成线程上交付（缺陷形态的完成线程错位源） */
        @BizQuery("pumpGetAsync")
        @GraphQLReturn(bizObjName = "Tree")
        public CompletionStage<ProbeTree> pumpGetAsync(@Name("id") String id) {
            return CompletableFuture.supplyAsync(() -> {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                return tree(id);
            });
        }

        /** 同步顶层 operation（游走内联先于调度点，零回归对照面） */
        @BizQuery("pumpGet")
        @GraphQLReturn(bizObjName = "Tree")
        public ProbeTree pumpGet(@Name("id") String id) {
            return tree(id);
        }

        /** 批量 loader：context 敏感读点（真实场景 = OrmBatchLoader.requireSession） */
        @BizLoader("batchName")
        @GraphQLReturn(bizObjName = "Tree")
        public List<String> batchName(@ContextSource List<ProbeTree> trees) {
            BATCH_PROBES.add(new Object[]{ContextProvider.currentContext(),
                    Thread.currentThread().getName()});
            return trees.stream().map(t -> "batch:" + t.getId()).toList();
        }
    }

    GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    @BeforeEach
    public void setUp() {
        BaseContextProvider.clear();
        BATCH_PROBES.clear();
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(List.of(new PumpProbeBizModel())));
        engine.init();
    }

    @AfterEach
    public void tearDown() {
        // 防御性清理：测试失败路径也可能残留附着 context
        BaseContextProvider.clear();
    }

    private GraphQLResponseBean execute(String query) throws Exception {
        GraphQLRequestBean req = new GraphQLRequestBean();
        req.setQuery(query);
        IGraphQLExecutionContext context = engine.newGraphQLContext(req);
        return engine.executeGraphQLAsync(context).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    /**
     * 判别用例（C3-002 出口泵）：异步顶层 operation + 批量 loader 字段。修复前 loader 在
     * ForkJoinPool 完成线程上执行，currentContext()==null（先红）；修复后出口泵挂接
     * thenOnContext，loader 在请求 context 附着状态下执行（assertSame + tenant 断言）。
     */
    @Test
    public void testAsyncOpBatchLoaderRunsOnRequestContext() throws Exception {
        IContext requestContext = ContextProvider.newContext(true);
        requestContext.setTenantId("t-c3-002");
        try {
            GraphQLResponseBean response = execute("query { Tree__pumpGetAsync(id:\"1\") { batchName } }");
            assertTrue(response.getErrors() == null || response.getErrors().isEmpty(),
                    "unexpected errors: " + response.getErrors());

            assertEquals(1, BATCH_PROBES.size(), "batch loader must run exactly once");
            IContext observed = (IContext) BATCH_PROBES.get(0)[0];
            // 判别断言（rule 23）：loader 执行线程可观测的 context 即请求 context 载体
            assertSame(requestContext, observed,
                    "batch loader must observe the request context (pump-level thenOnContext); "
                            + "observed=" + observed + " thread=" + BATCH_PROBES.get(0)[1]);
            assertEquals("t-c3-002", observed.getTenantId(), "tenant must propagate to loader");
        } finally {
            requestContext.close();
        }
    }

    /**
     * 同步路径零回归：同步顶层 operation 的游走内联在调用线程（本就持有 context），
     * 出口泵快路径（isRunningOnContext）不改变其行为。
     */
    @Test
    public void testSyncOpBatchLoaderStillRunsOnRequestContext() throws Exception {
        IContext requestContext = ContextProvider.newContext(true);
        requestContext.setTenantId("t-c3-002-sync");
        try {
            GraphQLResponseBean response = execute("query { Tree__pumpGet(id:\"1\") { batchName } }");
            assertTrue(response.getErrors() == null || response.getErrors().isEmpty(),
                    "unexpected errors: " + response.getErrors());

            assertEquals(1, BATCH_PROBES.size());
            IContext observed = (IContext) BATCH_PROBES.get(0)[0];
            assertSame(requestContext, observed, "sync walk keeps request context binding");
            assertEquals("t-c3-002-sync", observed.getTenantId());
        } finally {
            requestContext.close();
        }
    }

    /**
     * 无请求 context 时（无 HTTP filter 链的裸调用）loader 读点仍可完成（getOrCreateContext
     * 兜底）——pump 挂接的 thenOnContext 无参形态不进入本路径，裸调用保持既有宽容语义，
     * 但 probe 不得因挂接而抛错。
     */
    @Test
    public void testAsyncOpWithoutCallerContextStillCompletes() throws Exception {
        GraphQLResponseBean response = execute("query { Tree__pumpGetAsync(id:\"2\") { batchName } }");
        assertTrue(response.getErrors() == null || response.getErrors().isEmpty(),
                "unexpected errors: " + response.getErrors());
        assertEquals(1, BATCH_PROBES.size());
        // 裸调用无请求 context 载体：loader 观察到 getOrCreateContext 兜底新建的 context
        assertNotNull(BATCH_PROBES.get(0)[0], "engine must provide a fallback context for bare calls");
    }
}
