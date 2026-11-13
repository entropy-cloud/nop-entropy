/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-2.6-004（引擎形态判别）：DataLoader 调度生命周期由 GraphQLExecutor 的游走回合边界
 * 统一驱动——发起时点名（GraphQLExecutor :98/:120/:179）+ 游走回合出口泵
 * （invokeOperation.thenCompose / thenFetchNext）+ 批次完成递归。
 * <p>
 * 缺陷：executor 的调度点全部位于 operation 发起时；异步 operation 完成后才发生的
 * fetchNext 游走（注册批量 loader）错过全部调度点且递归链未武装 → load future 永不
 * 完成 → 静默挂死（探针 _tmp/fcr26-probe/DataLoaderDispatchProbe.java B 用例实证）。
 * <p>
 * 判别口径：带超时的端到端引擎请求。修复前本文件三个用例全部 TimeoutException；
 * 修复后全部在超时内完成，且同步/异步回合各自恰好一批（回合出口泵不抢跑游走）。
 */
public class TestGraphQLDataLoaderDispatchLifecycle {

    /** 批量调用的 keys 记录（每批一条），由 batchName biz 方法写入 */
    static final List<List<Object>> BATCH_CALLS = new CopyOnWriteArrayList<>();

    public static class Tree {
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
    public static class LifecycleBizModel {

        static Tree tree(String id) {
            Tree t = new Tree();
            t.setId(id);
            t.setName("name-" + id);
            return t;
        }

        @BizQuery("get")
        @GraphQLReturn(bizObjName = "Tree")
        public Tree get(@Name("id") String id) {
            return tree(id);
        }

        /** 异步顶层 operation：延迟完成后返回，模拟 LoginApiBizModel.loginAsync 形态 */
        @BizQuery("asyncGet")
        @GraphQLReturn(bizObjName = "Tree")
        public CompletionStage<Tree> asyncGet(@Name("id") String id) {
            return CompletableFuture.supplyAsync(() -> {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                return tree(id);
            });
        }

        /** 同步返回列表：多个 source 命中同一批量 loader（确定性单批形态） */
        @BizQuery("getIds")
        @GraphQLReturn(bizObjName = "Tree")
        public List<Tree> getIds() {
            List<Tree> ret = new ArrayList<>();
            for (int i = 0; i < 3; i++)
                ret.add(tree("id-" + i));
            return ret;
        }

        /** 异步返回列表：游走发生在异步 body 完成之后（缺陷形态的端到端复现） */
        @BizQuery("getIdsAsync")
        @GraphQLReturn(bizObjName = "Tree")
        public CompletionStage<List<Tree>> getIdsAsync() {
            return CompletableFuture.supplyAsync(() -> {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
                List<Tree> ret = new ArrayList<>();
                for (int i = 0; i < 3; i++)
                    ret.add(tree("id-" + i));
                return ret;
            });
        }

        /** 异步字段：其内部 selection 的游走发生在 thenFetchNext 续行回合内 */
        @BizLoader("asyncChild")
        @GraphQLReturn(bizObjName = "Tree")
        public CompletionStage<Tree> asyncChild(@ContextSource Tree tree) {
            return CompletableFuture.supplyAsync(() -> {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                return tree("child-of-" + tree.getId());
            });
        }

        /** 批量形态：@ContextSource 为集合 → BeanMethodBatchFetcher（真正走 DataLoader） */
        @BizLoader("batchName")
        @GraphQLReturn(bizObjName = "Tree")
        public List<String> batchName(@ContextSource List<Tree> trees) {
            BATCH_CALLS.add(trees.stream().map(Tree::getId).collect(Collectors.toList()));
            return trees.stream().map(t -> "batch:" + t.getId()).collect(Collectors.toList());
        }
    }

    GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    @BeforeEach
    public void setUp() {
        BATCH_CALLS.clear();
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader(List.of(new LifecycleBizModel())));
        engine.init();
    }

    private GraphQLResponseBean execute(String query) throws Exception {
        GraphQLRequestBean req = new GraphQLRequestBean();
        req.setQuery(query);
        IGraphQLExecutionContext context = engine.newGraphQLContext(req);
        // 带超时断言：缺陷形态下 load future 永不完成，请求静默挂死——超时即判别失败
        return engine.executeGraphQLAsync(context).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dataList(GraphQLResponseBean response, String opAlias) {
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (List<Map<String, Object>>) data.get(opAlias);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataMap(GraphQLResponseBean response, String opAlias) {
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return (Map<String, Object>) data.get(opAlias);
    }

    private static void assertNoErrors(GraphQLResponseBean response) {
        assertTrue(response.getErrors() == null || response.getErrors().isEmpty(),
                "unexpected errors: " + response.getErrors());
    }

    /**
     * 判别用例（挂死消除）：异步顶层 operation + 列表结果 + 批量 loader 字段。游走发生在
     * 异步 body 完成（invokeOperation.thenCompose 回合）内——修复前所有调度点已空跑、
     * 递归链未武装 → 3 秒超时挂死；修复后回合出口泵在同一线程 dispatch。
     * 回合内注册完整落入同一批：恰 1 批、键全覆盖、值正确。
     */
    @Test
    public void testAsyncListBatchLoaderCompletes() throws Exception {
        GraphQLResponseBean response = execute(
                "query { Tree__getIdsAsync { batchName } }");
        assertNoErrors(response);

        List<Map<String, Object>> items = dataList(response, "Tree__getIdsAsync");
        assertEquals(3, items.size());
        for (int i = 0; i < 3; i++)
            assertEquals("batch:id-" + i, items.get(i).get("batchName"));

        assertEquals(1, BATCH_CALLS.size(), "one walk turn must aggregate into exactly one batch");
        assertEquals(List.of("id-0", "id-1", "id-2"), BATCH_CALLS.get(0), "all keys in a single batch");
    }

    /**
     * 同步路径零回归：同步 operation + 列表 + 批量 loader——游走内联先于发起时调度点，
     * 确定性单批语义与回合出口泵一致。
     */
    @Test
    public void testSyncListSingleBatch() throws Exception {
        GraphQLResponseBean response = execute("query { Tree__getIds { batchName } }");
        assertNoErrors(response);

        List<Map<String, Object>> items = dataList(response, "Tree__getIds");
        assertEquals(3, items.size());
        for (int i = 0; i < 3; i++)
            assertEquals("batch:id-" + i, items.get(i).get("batchName"));

        assertEquals(1, BATCH_CALLS.size(), "sync walk must aggregate into exactly one batch");
        assertEquals(List.of("id-0", "id-1", "id-2"), BATCH_CALLS.get(0));
    }

    /**
     * 判别用例（嵌套异步字段）：批量 loader 字段位于异步字段内部——内部游走发生在
     * thenFetchNext 续行回合内，由回合出口泵承接。修复前挂死，修复后完成。
     */
    @Test
    public void testNestedAsyncFieldBatchLoaderCompletes() throws Exception {
        GraphQLResponseBean response = execute(
                "query { Tree__get(id:\"1\") { asyncChild { batchName } } }");
        assertNoErrors(response);

        Map<String, Object> root = dataMap(response, "Tree__get");
        Map<String, Object> child = (Map<String, Object>) root.get("asyncChild");
        assertEquals("batch:child-of-1", child.get("batchName"));

        assertEquals(1, BATCH_CALLS.size());
        assertEquals(List.of("child-of-1"), BATCH_CALLS.get(0));
    }

    /**
     * 异步单对象 operation + 批量 loader（探针 B 用例的回归化）：最简挂死形态。
     */
    @Test
    public void testAsyncSingleOpBatchLoaderCompletes() throws Exception {
        GraphQLResponseBean response = execute("query { Tree__asyncGet(id:\"1\") { batchName } }");
        assertNoErrors(response);

        Map<String, Object> root = dataMap(response, "Tree__asyncGet");
        assertEquals("batch:1", root.get("batchName"));

        assertEquals(1, BATCH_CALLS.size());
        assertEquals(List.of("1"), BATCH_CALLS.get(0));
    }
}
