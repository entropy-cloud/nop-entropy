package io.nop.graphql.orm;

import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.fetcher.ServiceActionFetcher;
import io.nop.graphql.core.parse.GraphQLDocumentHelper;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.orm.fetcher.OrmBatchLoader;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.impl.OrmSessionRegistry;
import org.dataloader.DataLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA plan 2499 Phase 3 端到端验证（rule 22）：异步 biz action + 嵌套 ORM 批量加载完整链路——
 * <p>
 * 入口 context（tenant + 请求级 ORM 会话注册在 context 绑定 registry）→ 异步 biz action
 * （C3-007 受管提交形态：提交线程快照调用方 context，任务体经 executeWithContext 在 worker
 * 窗口内执行）→ 出口泵/递归泵挂接（C3-002）→ 真实 {@link OrmBatchLoader} 在泵执行窗口内
 * requireSession 读到入口请求的会话（非 NOT_IN_SESSION、非 worker 残留会话）。
 * 覆盖 C3-002/C3-007 修复组合（2.3-C3-002 的 C10-001 传播网端到端面）。
 */
public class TestAsyncBizActionOrmChain_P3_E2E {

    /** 入口请求打开的 ORM 会话探针（requireSession 判别面） */
    static final AtomicReference<IOrmSession> ENTRY_SESSION = new AtomicReference<>();
    static final AtomicReference<String> ASYNC_BODY_TENANT = new AtomicReference<>();

    /** 伪造 sessionFactory 键（判别面只需 registry 按 key 取会话语义） */
    static final IOrmSessionFactory FAKE_FACTORY = (IOrmSessionFactory) Proxy.newProxyInstance(
            TestAsyncBizActionOrmChain_P3_E2E.class.getClassLoader(),
            new Class[]{IOrmSessionFactory.class}, (p, m, a) -> defaultFor(m.getReturnType()));

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
    public static class E2eBizModel {

        static Tree tree(String id) {
            Tree t = new Tree();
            t.setId(id);
            t.setName("name-" + id);
            return t;
        }

        /**
         * 异步 biz action（C3-007 受管提交形态的最小复现）：提交线程快照调用方 context，
         * 任务体经 executeWithContext 在 worker 线程窗口内执行后交付——模拟
         * BizActionService.callActionAsync 的修复形态。
         */
        @BizQuery("asyncOrmGet")
        @GraphQLReturn(bizObjName = "Tree")
        public CompletionStage<Tree> asyncOrmGet(@Name("id") String id) {
            IContext callerContext = ContextProvider.currentContext();
            CompletableFuture<Tree> promise = new CompletableFuture<>();
            WORKER.submit(() -> {
                try {
                    // 确定性延迟：保证引擎侧 waitAll 注册时 op future 仍 pending，游走链由
                    // op 完成线程驱动（消除 worker 先行完成对缺陷形态的竞态掩盖）
                    java.util.concurrent.locks.LockSupport.parkNanos(
                            TimeUnit.MILLISECONDS.toNanos(100));
                    if (callerContext != null) {
                        callerContext.executeWithContext(() -> {
                            // C3-007 判别面：worker 窗口内读到的 tenant 即调用方 context 的
                            ASYNC_BODY_TENANT.set(ContextProvider.currentTenantId());
                            return tree(id);
                        });
                    } else {
                        ASYNC_BODY_TENANT.set(ContextProvider.currentTenantId());
                    }
                    promise.complete(tree(id));
                } catch (Throwable e) {
                    promise.completeExceptionally(e);
                }
            });
            return promise;
        }
    }

    /**
     * ORM 读点探针模板：requireSession 语义与 OrmTemplateImpl 同构——从当前线程 context
     * 绑定的 OrmSessionRegistry 按 key 取会话，取不到即 NOT_IN_SESSION（缺陷形态的复现面）。
     */
    static IOrmTemplate e2eOrmTemplate() {
        return (IOrmTemplate) Proxy.newProxyInstance(
                TestAsyncBizActionOrmChain_P3_E2E.class.getClassLoader(),
                new Class[]{IOrmTemplate.class}, (p, m, a) -> {
                    if ("requireSession".equals(m.getName())) {
                        IOrmSession session = OrmSessionRegistry.instance().get(FAKE_FACTORY);
                        if (session == null)
                            throw new IllegalStateException(
                                    "nop.err.orm.not-in-session: batch loader ran without the "
                                            + "request-bound ORM session (context-window leak)");
                        return session;
                    }
                    return defaultFor(m.getReturnType());
                });
    }

    /** 探针会话：getBatchLoadQueue 返回 no-op 队列（OrmBatchLoader.load 的 flush 接触面） */
    static IOrmSession probeSession() {
        Object queue = Proxy.newProxyInstance(TestAsyncBizActionOrmChain_P3_E2E.class.getClassLoader(),
                new Class[]{io.nop.orm.IOrmBatchLoadQueue.class}, (p, m, a) -> defaultFor(m.getReturnType()));
        return (IOrmSession) Proxy.newProxyInstance(TestAsyncBizActionOrmChain_P3_E2E.class.getClassLoader(),
                new Class[]{IOrmSession.class}, (p, m, a) -> {
                    if ("getBatchLoadQueue".equals(m.getName()))
                        return queue;
                    return defaultFor(m.getReturnType());
                });
    }

    static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }

    static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "e2e-biz-worker");
        t.setDaemon(true);
        return t;
    });

    GraphQLEngineHolder holder;

    /** 最小 schema loader（结构与 nop-graphql-core 测试 MockGraphQLSchemaLoader 同构） */
    static class E2eSchemaLoader implements IGraphQLSchemaLoader {
        private final Map<String, GraphQLObjectDefinition> defs;
        private final TypeRegistry typeRegistry = new TypeRegistry();
        private final GraphQLBizModels bizModels = new GraphQLBizModels();

        E2eSchemaLoader(List<Object> beans) {
            IResource resource = new ClassPathResource("classpath:io/nop/graphql/orm/e2e.graphql");
            defs = GraphQLDocumentHelper.parseObjectDefinitions(resource);
            bizModels.build(typeRegistry, beans);
            for (GraphQLObjectDefinition def : defs.values()) {
                bizModels.customize(def);
            }
            for (GraphQLObjectDefinition objDef : defs.values()) {
                objDef.init();
                for (GraphQLFieldDefinition fieldDef : objDef.getFields()) {
                    if (fieldDef.getFetcher() == null) {
                        if (fieldDef.getServiceAction() != null) {
                            fieldDef.setFetcher(new ServiceActionFetcher(fieldDef.getServiceAction()));
                        } else {
                            fieldDef.setFetcher(BeanPropertyFetcher.INSTANCE);
                        }
                    }
                }
            }

            // batchName 字段挂真实 OrmBatchLoader：每 source 投递一个 load 键，
            // DataLoader 由 executor 的出口泵/递归泵在回合出口统一 dispatch
            for (GraphQLFieldDefinition fieldDef : defs.get("Tree").getFields()) {
                if ("batchName".equals(fieldDef.getName())) {
                    fieldDef.setFetcher(this::ormBatchFetch);
                }
            }
        }

        Object ormBatchFetch(IDataFetchingEnvironment env) {
            Tree source = (Tree) env.getSource();
            DataLoader<Supplier<Object>, Object> loader = OrmBatchLoader.makeDataLoader(
                    e2eOrmTemplate(), env.getGraphQLExecutionContext());
            return loader.load(() -> (Object) ("sess-" + source.getId()));
        }

        @Override
        public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) {
            return bizModels.getOperationDefinition(opType, name);
        }

        @Override
        public GraphQLObjectDefinition getObjectTypeDefinition(String objName) {
            return defs.get(objName);
        }

        @Override
        public GraphQLObjectDefinition resolveTypeDefinition(GraphQLType type) {
            return defs.get(type.getNamedTypeName());
        }

        @Override
        public List<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType) {
            return Collections.emptyList();
        }

        @Override
        public GraphQLTypeDefinition getTypeDefinition(String objName) {
            return getObjectTypeDefinition(objName);
        }

        @Override
        public io.nop.api.core.beans.FieldSelectionBean getFragmentDefinition(String objName, String fragmentName) {
            return null;
        }

        @Override
        public Collection<GraphQLTypeDefinition> getTypeDefinitions() {
            return new ArrayList<>(defs.values());
        }

        @Override
        public GraphQLDocument getGraphQLDocument() {
            GraphQLDocument doc = new GraphQLDocument();
            List<GraphQLDefinition> list = new ArrayList<>();
            list.addAll(getTypeDefinitions().stream().map(def -> def.deepClone()).collect(Collectors.toList()));
            doc.setDefinitions(list);
            return doc;
        }

        @Override
        public Set<String> getBizObjNames() {
            return defs.keySet();
        }

        @Override
        public Map<String, GraphQLFieldDefinition> getBizOperationDefinitions(String bizObjName) {
            return Collections.emptyMap();
        }
    }

    static class GraphQLEngineHolder {
        final io.nop.graphql.core.engine.GraphQLEngine engine;

        GraphQLEngineHolder() {
            engine = new io.nop.graphql.core.engine.GraphQLEngine();
            engine.setSchemaLoader(new E2eSchemaLoader(List.of(new E2eBizModel())));
            engine.init();
        }
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
        WORKER.shutdownNow();
    }

    @BeforeEach
    public void setUp() {
        BaseContextProvider.clear();
        ENTRY_SESSION.set(null);
        ASYNC_BODY_TENANT.set(null);
        holder = new GraphQLEngineHolder();
    }

    @AfterEach
    public void tearDown() {
        BaseContextProvider.clear();
    }

    /**
     * 判别用例（rule 22 完整链路）：入口 context + 请求级 ORM 会话 → 异步 biz action 在
     * worker 窗口内执行（tenant 传播断言）→ 出口泵/递归泵挂接 → OrmBatchLoader 的
     * requireSession 读到入口请求会话。修复前批量加载在完成线程上落空 registry
     * （NOT_IN_SESSION 判别失败，红）。
     */
    @Test
    public void testAsyncBizActionToOrmBatchLoadRunsInRequestContextWindow() throws Exception {
        // 入口 context：tenant + 请求级 ORM 会话（HTTP 入口的 service 层等价物）
        IContext entryContext = ContextProvider.newContext(true);
        entryContext.setTenantId("tenant-e2e");
        IOrmSession requestSession = probeSession();
        ENTRY_SESSION.set(requestSession);
        OrmSessionRegistry.instance().put(FAKE_FACTORY, requestSession);

        try {
            GraphQLRequestBean req = new GraphQLRequestBean();
            req.setQuery("query { Tree__asyncOrmGet(id: \"7\") { batchName } }");
            IGraphQLExecutionContext context = holder.engine.newGraphQLContext(req);

            GraphQLResponseBean response = holder.engine.executeGraphQLAsync(context)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertTrue(response.getErrors() == null || response.getErrors().isEmpty(),
                    "unexpected errors: " + io.nop.core.lang.json.JsonTool.serialize(response.getErrors(), true));

            // C3-007 判别面：异步 biz action 体在 worker 窗口内读到调用方 tenant
            assertEquals("tenant-e2e", ASYNC_BODY_TENANT.get(),
                    "async biz action body must run with the caller's context (managed submit)");

            // C3-002 判别面：OrmBatchLoader.load 在泵窗口内执行，requireSession 读到
            // 入口请求的会话（registry 键命中）
            Map<String, Object> data = (Map<String, Object>) response.getData();
            Map<String, Object> tree = (Map<String, Object>) data.get("Tree__asyncOrmGet");
            assertEquals("sess-7", tree.get("batchName"),
                    "batch load must resolve through the request-bound ORM session");

            IOrmSession seen = OrmSessionRegistry.instance().get(FAKE_FACTORY);
            assertSame(requestSession, seen,
                    "the pump window must carry the entry request context (session identity)");
        } finally {
            entryContext.close();
        }
    }
}
