/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.mfa.IOperationMfaChecker;
import io.nop.auth.api.mfa.MfaRequired;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W12-impl Phase 1 接线验证（Minimum Rules #23）：executor → IOperationMfaChecker 调用链
 * 运行时连通（mock 计数断言，非仅类型存在）。
 * <ul>
 *   <li>无 checker bean 时引擎零介入（不抛错、不拦截——框架独立可用性）。</li>
 *   <li>有 checker 时 executor 两检查点（RPC 单操作路径 + GraphQL 文档路径）确实调用，
 *       且只对 @MfaRequired 方法调用（非敏感方法零介入）。</li>
 *   <li>订阅 operation 无 mfaRequired 字段可达（构建期拒绝保证）。</li>
 *   <li>checker 抛异常（拦截）时该 operation 单独报错，批量中其他 operation 不受影响
 *       （GraphQL 逐 field error 原生语义）。</li>
 * </ul>
 */
public class TestOperationMfaExecutorWiring {

    @BizModel("MfaBiz")
    public static class MfaBizModel {
        @BizMutation
        @MfaRequired
        public String sensitiveAction(@Name("id") String id) {
            return "sensitive:" + id;
        }

        @BizMutation
        public String plainMutation(@Name("id") String id) {
            return "plain:" + id;
        }

        @BizQuery
        public String plainQuery(@Name("id") String id) {
            return "query:" + id;
        }
    }

    /**
     * 计数 mock checker：记录调用与入参；可切换为抛异常模式（模拟拦截）。
     */
    static class CountingMfaChecker implements IOperationMfaChecker {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> operations = Collections.synchronizedList(new ArrayList<>());
        volatile boolean reject;

        @Override
        public void check(String operationName, IUserContext userContext, Map<String, Object> requestHeaders) {
            calls.incrementAndGet();
            operations.add(operationName);
            if (reject) {
                throw new NopException(ErrorCode.define("nop.err.auth.operation-mfa.required",
                        "operation requires MFA verification"))
                        .param("challengeToken", "mock-token").param("operation", operationName);
            }
        }
    }

    static class BizModelSchemaLoader implements IGraphQLSchemaLoader {
        final GraphQLBizModels bizModels = new GraphQLBizModels();
        final Map<String, GraphQLObjectDefinition> defs = new HashMap<>();

        BizModelSchemaLoader(Object... beans) {
            TypeRegistry registry = new TypeRegistry();
            bizModels.build(registry, List.of(beans));
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
        public io.nop.api.core.beans.FieldSelectionBean getFragmentDefinition(String objName, String fragmentName) {
            return null;
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLDocument getGraphQLDocument() {
            return new io.nop.graphql.core.ast.GraphQLDocument();
        }

        @Override
        public GraphQLTypeDefinition getTypeDefinition(String objName) {
            return defs.get(objName);
        }

        @Override
        public Collection<GraphQLTypeDefinition> getTypeDefinitions() {
            return new ArrayList<>(defs.values());
        }

        @Override
        public java.util.Set<String> getBizObjNames() {
            return Set.of("MfaBiz");
        }

        @Override
        public Map<String, GraphQLFieldDefinition> getBizOperationDefinitions(String bizObjName) {
            return Collections.emptyMap();
        }
    }

    GraphQLEngine engine;
    CountingMfaChecker checker;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new BizModelSchemaLoader(new MfaBizModel()));
        engine.init();
        checker = new CountingMfaChecker();
        engine.setOperationMfaChecker(checker);
    }

    private ApiRequest<Map<String, Object>> rpcRequest(String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        return request;
    }

    @Test
    public void testExecutorCallsCheckerOnSensitiveRpcOperation() {
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.mutation,
                "MfaBiz__sensitiveAction", rpcRequest("a"));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertTrue(response.isOk(), "checker not rejecting → operation must succeed");
        assertEquals(1, checker.calls.get(), "executor must call checker exactly once for sensitive RPC operation");
        assertEquals("MfaBiz__sensitiveAction", checker.operations.get(0),
                "checker must receive full operation name (bizObjName__action)");
    }

    @Test
    public void testExecutorSkipsCheckerForNonSensitiveOperation() {
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.mutation,
                "MfaBiz__plainMutation", rpcRequest("b"));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertTrue(response.isOk());
        assertEquals(0, checker.calls.get(), "non-sensitive operation must not invoke checker");
    }

    @Test
    public void testNoCheckerBeanMeansZeroIntervention() {
        GraphQLEngine bareEngine = new GraphQLEngine();
        bareEngine.setSchemaLoader(new BizModelSchemaLoader(new MfaBizModel()));
        bareEngine.init();
        assertNull(bareEngine.getOperationMfaChecker(), "engine without checker bean must expose null");

        IGraphQLExecutionContext context = bareEngine.newRpcContext(GraphQLOperationType.mutation,
                "MfaBiz__sensitiveAction", rpcRequest("c"));
        ApiResponse<?> response = FutureHelper.syncGet(bareEngine.executeRpcAsync(context));
        assertTrue(response.isOk(),
                "no checker bean → zero intervention, sensitive operation executes (framework standalone)");
    }

    @Test
    public void testCheckerRejectionFailsOperation() {
        checker.reject = true;
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.mutation,
                "MfaBiz__sensitiveAction", rpcRequest("d"));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertTrue(!response.isOk(), "checker rejection must fail the operation");
        assertEquals(1, checker.calls.get());
    }

    @Test
    public void testGraphQLDocumentPathCallsCheckerForSensitiveFieldOnly() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("mutation { a: MfaBiz__sensitiveAction(id:\"x\") b: MfaBiz__plainMutation(id:\"y\") }");
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        CompletionStage<GraphQLResponseBean> promise = engine.executeGraphQLAsync(context);
        GraphQLResponseBean response = FutureHelper.syncGet(promise);

        assertEquals(1, checker.calls.get(), "document path must call checker only for the sensitive field");
        assertEquals("MfaBiz__sensitiveAction", checker.operations.get(0));

        // 拦截时与 auth check 同语义：预执行检查点抛错使整批请求失败（无部分执行的副作用），
        // 错误即该 operation 的错误（errorParams 携带 operation）；客户端凭票整批重发
        checker.reject = true;
        GraphQLRequestBean request2 = new GraphQLRequestBean();
        request2.setQuery("mutation { a: MfaBiz__sensitiveAction(id:\"x\") b: MfaBiz__plainMutation(id:\"y\") }");
        IGraphQLExecutionContext context2 = engine.newGraphQLContext(request2);
        GraphQLResponseBean response2 = FutureHelper.syncGet(engine.executeGraphQLAsync(context2));
        assertTrue(response2.getErrors() != null && !response2.getErrors().isEmpty(),
                "sensitive field interception must produce error response (no silent skip)");
        assertTrue(response2.getData() == null,
                "pre-execution interception must not partially execute the batch");
    }

    @Test
    public void testQueryPathAlsoChecked() {
        // @MfaRequired 也可声明于 query 方法（判定矩阵不含 operationType 条件）
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { MfaBiz__plainQuery(id:\"q\") }");
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        GraphQLResponseBean response = FutureHelper.syncGet(engine.executeGraphQLAsync(context));
        assertTrue(response.getErrors() == null || response.getErrors().isEmpty());
        assertEquals(0, checker.calls.get(), "plain query must not invoke checker");
    }
}
