/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.graphql.core.GraphQLConfigs;
import io.nop.graphql.core.GraphQLErrors;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLHook;
import io.nop.graphql.core.ParsedGraphQLRequest;
import io.nop.graphql.core.ast.GraphQLDirectiveDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLVariableDefinition;
import io.nop.graphql.core.parse.GraphQLDocumentParser;
import io.nop.graphql.core.rpc.RpcServiceOnGraphQL;
import io.nop.graphql.core.schema.BuiltinSchemaLoader;
import io.nop.graphql.core.schema.GraphQLSchema;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_DEPTH;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED;
import static io.nop.graphql.core.GraphQLErrors.ARG_EXPECTED_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DOC_OPERATION_SIZE_NOT_ONE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_INTROSPECTION_NOT_ENABLED;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_BUILTIN_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_OPERATION;

public class GraphQLEngine implements IGraphQLEngine {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLEngine.class);

    private final LocalCache<String, GraphQLDocument> documentCache;

    private GraphQLSchema builtinSchema;

    private IGraphQLSchemaLoader schemaLoader;

    private IAsyncFunctionInvoker executionInvoker;
    private IAsyncFunctionInvoker operationInvoker;

    private IGraphQLHook graphQLHook;

    private IDataAuthChecker dataAuthChecker;

    private IActionAuthChecker actionAuthChecker;

    private IFlowControlRunner flowControlRunner;

    private final CancelTokenManager cancelTokenManager = new CancelTokenManager();

    private boolean enableActionAuth;
    private boolean enableDataAuth;

    public GraphQLEngine() {
        this.documentCache = LocalCache.newCache(
                "graphql-parse-cache", newConfig(GraphQLConfigs.CFG_GRAPHQL_QUERY_PARSE_CACHE_SIZE.get()).useMetrics()
                        .expireAfterWrite(GraphQLConfigs.CFG_GRAPHQL_QUERY_PARSE_CACHE_TIMEOUT.get()),
                this::parseOperationFromText);
    }

    public void setBuiltinSchema(GraphQLSchema schema) {
        this.builtinSchema = schema;
    }

    @Inject
    @Nullable
    public void setDataAuthChecker(IDataAuthChecker dataAuthChecker) {
        this.dataAuthChecker = dataAuthChecker;
    }

    @Inject
    @Nullable
    public void setActionAuthChecker(IActionAuthChecker actionAuthChecker) {
        this.actionAuthChecker = actionAuthChecker;
    }

    @Inject
    @Nullable
    public void setFlowControlRunner(IFlowControlRunner flowControlRunner) {
        this.flowControlRunner = flowControlRunner;
    }

    public LocalCache<String, GraphQLDocument> getDocumentCache() {
        return documentCache;
    }

    public boolean isEnableActionAuth() {
        return enableActionAuth;
    }

    public void setEnableActionAuth(boolean enableActionAuth) {
        this.enableActionAuth = enableActionAuth;
    }

    public boolean isEnableDataAuth() {
        return enableDataAuth;
    }

    public void setEnableDataAuth(boolean enableDataAuth) {
        this.enableDataAuth = enableDataAuth;
    }

    public void setGraphQLHook(IGraphQLHook graphQLHook) {
        this.graphQLHook = graphQLHook;
    }

    @Inject
    public void setSchemaLoader(IGraphQLSchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    public IGraphQLSchemaLoader getSchemaLoader() {
        return schemaLoader;
    }

    /**
     * 一般是SingleSessionFunctionInvoker
     */
    public void setExecutionInvoker(IAsyncFunctionInvoker invoker) {
        this.executionInvoker = invoker;
    }

    /**
     * 一般是DefaultOperationFunctionInvoker
     */
    public void setOperationInvoker(IAsyncFunctionInvoker invoker) {
        this.operationInvoker = invoker;
    }

    @PostConstruct
    public void init() {
        // 装载系统内置的schema定义
        this.builtinSchema = new BuiltinSchemaLoader(schemaLoader, CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED.get())
                .load();
    }

    private GraphQLDocument parseOperationFromText(String text) {
        GraphQLDocument doc = new GraphQLDocumentParser().parseFromText(SourceLocation.fromClass(GraphQLDocument.class),
                text);
        if (!doc.isOperationQuery())
            throw new NopException(ERR_GRAPHQL_DOC_OPERATION_SIZE_NOT_ONE);

        doc.init();
        GraphQLOperation op = doc.getOperation();
        int maxDepth = CFG_GRAPHQL_QUERY_MAX_DEPTH.get();
        // graphql-ui的__schema查询深度为7
        if (isIntrospection(op)) {
            maxDepth = 10;
        }

        resolveSelections(doc, maxDepth);
        doc.freeze(true);
        return doc;
    }

    void resolveSelections(GraphQLDocument doc, int maxDepth) {
        new GraphQLSelectionResolver(this, maxDepth).resolveSelection(doc);

        doc.setResolved(true);
    }

    boolean isIntrospection(GraphQLOperation op) {
        if (!"IntrospectionQuery".equals(op.getName()))
            return false;

        for (GraphQLSelection field : op.getSelectionSet().getSelections()) {
            if (!(field instanceof GraphQLFieldSelection))
                return false;

            GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) field;
            if (!fieldSelection.getName().startsWith("__"))
                return false;
        }
        return true;
    }

    @Override
    public GraphQLDocument parseOperation(String query, boolean skipCache) {
        if (StringHelper.isBlank(query))
            throw new NopException(GraphQLErrors.ERR_GRAPHQL_PARSE_EMPTY_STRING);
        if (query.length() > GraphQLConfigs.CFG_GRAPHQL_QUERY_PARSE_MAX_LENGTH.get())
            throw new NopException(GraphQLErrors.ERR_GRAPHQL_PARSE_EXCEED_MAX_LENGTH);
        if (skipCache)
            return parseOperationFromText(query);
        return documentCache.get(query);
    }

    @Override
    public GraphQLDirectiveDefinition getDirective(String name) {
        return builtinSchema.getDirective(name);
    }

    @Override
    public void resolveSelection(String objName, GraphQLSelectionSet selectionSet,
                                 Map<String, GraphQLVariableDefinition> vars) {
        new GraphQLSelectionResolver(this, CFG_GRAPHQL_QUERY_MAX_DEPTH.get()).resolveSelections(null, objName,
                selectionSet, vars, 0);
    }

    @Override
    public FieldSelectionBean buildSelectionBean(String name, GraphQLSelectionSet selectionSet,
                                                 Map<String, Object> vars) {
        return new SelectionBeanBuilder(builtinSchema == null ? null : builtinSchema.getDirectives())
                .buildSelectionBean(name, selectionSet, vars);
    }

    @Override
    public GraphQLTypeDefinition getTypeDefinition(String typeName) {
        if (builtinSchema != null) {
            GraphQLTypeDefinition type = builtinSchema.getType(typeName);
            if (type != null)
                return type;
        }
        return schemaLoader.getTypeDefinition(typeName);
    }

    @Override
    public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) {
        if (opType == GraphQLOperationType.query && builtinSchema != null) {
            GraphQLObjectDefinition def = builtinSchema.getObjectType("Query");
            if (def != null) {
                GraphQLFieldDefinition field = def.getField(name);
                if (field != null)
                    return field;
            }
        }

        if (name.startsWith("__")) {
            if (!CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED.get()) {
                throw new NopException(ERR_GRAPHQL_INTROSPECTION_NOT_ENABLED).param(ARG_OPERATION_NAME, name);
            } else {
                throw new NopException(ERR_GRAPHQL_UNKNOWN_BUILTIN_TYPE).param(ARG_TYPE_NAME, name);
            }
        }
        return schemaLoader.getOperationDefinition(opType, name);
    }

    @Override
    public IGraphQLExecutionContext newGraphQLContext() {
        GraphQLExecutionContext context = new GraphQLExecutionContext();
        if (enableActionAuth)
            context.setActionAuthChecker(actionAuthChecker);
        if (enableDataAuth)
            context.setDataAuthChecker(dataAuthChecker);
        return context;
    }

    public void initGraphQLContext(IGraphQLExecutionContext context, ParsedGraphQLRequest request) {
        GraphQLDocument doc = request.getDocument();
        if (!doc.isResolved()) {
            resolveSelections(doc, CFG_GRAPHQL_QUERY_MAX_DEPTH.get());
        }

        Map<String, Object> vars = request.getVariables();
        GraphQLOperation op = (GraphQLOperation) doc.getDefinitions().get(0);
        context.setOperation(op);
        context.setExecutionId(request.getOperationId());
        context.setFieldSelection(buildSelectionBean(op.getName(), op.getSelectionSet(), vars));
    }

    public void initRpcContext(IGraphQLExecutionContext context, GraphQLOperationType expectedOpType,
                               String operationName, ApiRequest<?> request) {

        GraphQLFieldDefinition action = schemaLoader.getOperationDefinition(null, operationName);
        if (action == null)
            throw new NopException(ERR_GRAPHQL_UNKNOWN_OPERATION).param(ARG_OPERATION_NAME, operationName);

        GraphQLOperationType opType = action.getOperationType();
        if (expectedOpType != null && opType != expectedOpType) {
            throw new NopException(ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE).param(ARG_OPERATION_NAME, operationName)
                    .param(ARG_OPERATION_TYPE, opType).param(ARG_EXPECTED_OPERATION_TYPE, expectedOpType);
        }

        context.setRequestHeaders(request.getHeaders());
        GraphQLDocument doc = new GraphQLDocument();
        GraphQLFieldSelection selection = doc.addOperation(action.getOperationType(), operationName, request.getData());

        GraphQLSelectionSet selectionSet = new RpcSelectionSetBuilder(this.builtinSchema, schemaLoader,
                CFG_GRAPHQL_QUERY_MAX_DEPTH.get()).buildForType(action.getType(), request.getSelection());
        selection.setSelectionSet(selectionSet);
        selection.setFieldDefinition(action);

        ParsedGraphQLRequest req = new ParsedGraphQLRequest();
        req.setDocument(doc);
        context.setRequest(req);
        context.setOperation(doc.getOperation());

        GraphQLOperation op = (GraphQLOperation) doc.getDefinitions().get(0);
        FieldSelectionBean fieldSelection = buildSelectionBean(operationName, op.getSelectionSet(), Collections.emptyMap());

        context.setFieldSelection(fieldSelection);
    }

    @Override
    public CompletionStage<ApiResponse<?>> executeRpcAsync(IGraphQLExecutionContext context) {
        IGraphQLExecutor executor = new GraphQLExecutor(operationInvoker, graphQLHook, flowControlRunner, this);
        IAsyncFunctionInvoker executionInvoker = getExecutionInvoker(context);
        if (executionInvoker != null)
            return executionInvoker.invokeAsync(executor::executeOneAsync, context);

        return executor.executeOneAsync(context);
    }

    @Override
    public CompletionStage<GraphQLResponseBean> executeGraphQLAsync(IGraphQLExecutionContext context) {
        IGraphQLExecutor executor = new GraphQLExecutor(operationInvoker, graphQLHook, flowControlRunner, this);
        IAsyncFunctionInvoker executionInvoker = getExecutionInvoker(context);
        if (executionInvoker != null)
            return executionInvoker.invokeAsync(executor::executeAsync, context);
        return executor.executeAsync(context);
    }

    @Override
    public GraphQLResponseBean buildGraphQLResponse(Object result, Throwable err, IGraphQLExecutionContext context) {
        if (err != null) {
            LOG.error("nop.graphql.execute-fail", err);
            String locale = ContextProvider.currentLocale();
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(locale, err, false, true);
            GraphQLResponseBean ret = new GraphQLResponseBean();
            ret.addError(errorBean);
            return ret;
        }

        if (result instanceof GraphQLResponseBean)
            return (GraphQLResponseBean) result;

        GraphQLResponseBean ret = new GraphQLResponseBean();
        ret.setData(result);
        return ret;
    }

    @Override
    public ApiResponse<?> buildRpcResponse(Object result, Throwable err, IGraphQLExecutionContext context) {
        ApiResponse<?> res;
        if (err != null) {
            NopException.logIfNotTraced(LOG, "nop.graphql.rest-execute-fail", err);
            String locale = ContextProvider.currentLocale();
            res = ErrorMessageManager.instance().buildResponse(locale, err);
        } else if (result instanceof ApiResponse<?>) {
            res = (ApiResponse<?>) result;
        } else {
            res = ApiResponse.buildSuccess(result);
        }

        if (context != null && context.getResponseHeaders() != null) {
            context.getResponseHeaders().forEach(res::setHeader);
        }
        return res;
    }

    protected IAsyncFunctionInvoker getExecutionInvoker(IGraphQLExecutionContext context) {
        IAsyncFunctionInvoker executionInvoker = cancelTokenManager.wrap(this.executionInvoker, context);
        if (flowControlRunner == null)
            return executionInvoker;
        return new GraphQLFlowControlInvoker(flowControlRunner, executionInvoker);
    }

    @Override
    public boolean cancel(String requestId) {
        return cancelTokenManager.cancel(requestId);
    }


    @Override
    public Flow.Publisher<GraphQLResponseBean> subscribeGraphQL(IGraphQLExecutionContext context) {
        return null;
    }

    @Override
    public <T> T makeRpcProxy(Class<T> rpcClass) {
        BizModel bizModel = rpcClass.getAnnotation(BizModel.class);
        String serviceName;
        if (bizModel != null) {
            serviceName = bizModel.value();
        } else {
            serviceName = rpcClass.getSimpleName();
        }
        RpcServiceOnGraphQL service = new RpcServiceOnGraphQL(this, serviceName, Collections.emptyList());
        return service.asProxy(rpcClass);
    }
}