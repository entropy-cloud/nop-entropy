/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.resource.cache.ResourceCacheEntryWithLoader;
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
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLVariableDefinition;
import io.nop.graphql.core.parse.GraphQLDocumentParser;
import io.nop.graphql.core.reflection.IGraphQLArgsNormalizer;
import io.nop.graphql.core.rpc.RpcServiceOnGraphQL;
import io.nop.graphql.core.schema.BuiltinSchemaLoader;
import io.nop.graphql.core.schema.GraphQLSchema;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAX_DIRECTIVE_PER_REQUEST;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_PARSE_CACHE_CHECK_CHANGED;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_DEPTH;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED;
import static io.nop.graphql.core.GraphQLConstants.SYS_OPERATION_FETCH_RESULTS;
import static io.nop.graphql.core.GraphQLErrors.ARG_ALLOWED_NAMES;
import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_COUNT;
import static io.nop.graphql.core.GraphQLErrors.ARG_EXPECTED_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_MAX_COUNT;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DOC_OPERATION_SIZE_NOT_ONE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_EXCEED_MAX_DIRECTIVE_PER_REQUEST;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_INTROSPECTION_NOT_ENABLED;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_QUERY_EXCEED_MAX_OPERATION_COUNT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_QUERY_NO_OPERATION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_BUILTIN_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_OPERATION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_OPERATION_ARG;

public class GraphQLEngine implements IGraphQLEngine {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLEngine.class);

    // 不能直接缓存GraphQLDocument。因为xbiz文件有可能动态更新，所以缓存需要监听资源文件的变化
    private final LocalCache<String, ResourceCacheEntryWithLoader<GraphQLDocument>> documentCache;

    private boolean registerCache;

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
                this::parseDocumentWithLoader);
    }

    public void setRegisterCache(boolean registerCache) {
        this.registerCache = registerCache;
    }

    public void clearCache() {
        documentCache.clear();
    }

    public void setBuiltinSchema(GraphQLSchema schema) {
        this.builtinSchema = schema;
    }

    @Inject
    public void setDataAuthChecker(@Nullable IDataAuthChecker dataAuthChecker) {
        this.dataAuthChecker = dataAuthChecker;
    }

    @Inject
    public void setActionAuthChecker(@Nullable IActionAuthChecker actionAuthChecker) {
        this.actionAuthChecker = actionAuthChecker;
    }

    @Inject
    public void setFlowControlRunner(@Nullable IFlowControlRunner flowControlRunner) {
        this.flowControlRunner = flowControlRunner;
    }

    @Inject
    public void setGraphQLHook(@Nullable IGraphQLHook graphQLHook) {
        this.graphQLHook = graphQLHook;
    }

    @Override
    public ICancelTokenManger getCancelTokenManager() {
        return cancelTokenManager;
    }

    public LocalCache<String, ResourceCacheEntryWithLoader<GraphQLDocument>> getDocumentCache() {
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
        if (registerCache)
            GlobalCacheRegistry.instance().register(documentCache);

        // 装载系统内置的schema定义
        this.builtinSchema = new BuiltinSchemaLoader(schemaLoader, CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED.get())
                .load();
        LOG.info("graphql-builtin-types:types={},introspection={}", builtinSchema.getTypes().keySet(), CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED.get());
    }

    @PreDestroy
    public void destroy() {
        if (registerCache)
            GlobalCacheRegistry.instance().unregister(documentCache);
    }

    private GraphQLDocument parseOperationFromText(String text) {
        GraphQLDocument doc = new GraphQLDocumentParser().parseFromText(SourceLocation.fromClass(GraphQLDocument.class),
                text);
        if (!doc.isOperationQuery())
            throw new NopException(ERR_GRAPHQL_DOC_OPERATION_SIZE_NOT_ONE);

        initDocument(doc);
        return doc;
    }

    private void initDocument(GraphQLDocument doc) {
        doc.init();
        GraphQLOperation op = doc.getOperation();
        int maxDepth = CFG_GRAPHQL_QUERY_MAX_DEPTH.get();
        // graphql-ui的__schema查询深度为7
        if (isIntrospection(op)) {
            maxDepth = 10;
        }

        resolveSelections(doc, maxDepth);
        doc.freeze(true);
    }

    private ResourceCacheEntryWithLoader<GraphQLDocument> parseDocumentWithLoader(String text) {
        ResourceCacheEntryWithLoader<GraphQLDocument> entry = new ResourceCacheEntryWithLoader<>("graphql-document-cache-item", k -> {
            GraphQLDocument newDoc = parseOperationFromText(text);
            return newDoc;
        });
        entry.getObject(false);
        return entry;
    }

    void resolveSelections(GraphQLDocument doc, int maxDepth) {
        new GraphQLSelectionResolver(this, builtinSchema, maxDepth).resolveSelection(doc);

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
        return documentCache.get(query).getObject(CFG_GRAPHQL_PARSE_CACHE_CHECK_CHANGED.get());
    }

    @Override
    public GraphQLDirectiveDefinition getDirective(String name) {
        return builtinSchema.getDirective(name);
    }

    @Override
    public void resolveSelection(String objName, GraphQLSelectionSet selectionSet,
                                 Map<String, GraphQLVariableDefinition> vars) {
        new GraphQLSelectionResolver(this, builtinSchema, CFG_GRAPHQL_QUERY_MAX_DEPTH.get()).resolveSelections(null, objName,
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
        if ((opType == null || opType == GraphQLOperationType.query) && builtinSchema != null) {
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
    public IGraphQLExecutionContext newGraphQLContextFromContext(IServiceContext ctx) {
        GraphQLExecutionContext context = new GraphQLExecutionContext(ctx);

        if (enableActionAuth) {
            if (context.getActionAuthChecker() == null) {
                context.setActionAuthChecker(actionAuthChecker);
            }
        }

        if (enableDataAuth) {
            if (context.getDataAuthChecker() == null) {
                context.setDataAuthChecker(dataAuthChecker);
            }
        }
        return context;
    }

    @Override
    public void initGraphQLContext(IGraphQLExecutionContext context, ParsedGraphQLRequest request) {
        GraphQLDocument doc = request.getDocument();
        if (!doc.isResolved()) {
            resolveSelections(doc, CFG_GRAPHQL_QUERY_MAX_DEPTH.get());
        }
        validateDocument(doc);

        GraphQLOperation op = doc.getOperation();
        if (op.getSelectionSet() == null || op.getSelectionSet().isEmpty())
            throw new NopException(ERR_GRAPHQL_QUERY_NO_OPERATION);

        if (op.getSelectionSet().size() > CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get())
            throw new NopException(ERR_GRAPHQL_QUERY_EXCEED_MAX_OPERATION_COUNT)
                    .param(ARG_OPERATION_NAME, op.getName());

        context.setOperation(op);
        context.setExecutionId(request.getOperationId());

        Map<String, Object> vars = request.getVariables();
        FieldSelectionBean selectionBean = buildSelectionBean(op.getName(), op.getSelectionSet(), vars);
        context.setFieldSelection(selectionBean);
    }

    private void validateDocument(GraphQLDocument doc) {
        int maxCount = CFG_GRAPHQL_MAX_DIRECTIVE_PER_REQUEST.get();
        int count = doc.getAllDirectiveCount();
        if (count > maxCount)
            throw new NopException(ERR_GRAPHQL_EXCEED_MAX_DIRECTIVE_PER_REQUEST)
                    .param(ARG_MAX_COUNT, maxCount)
                    .param(ARG_COUNT, count);
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

        if (request.getSelection() != null) {
            validateSelection(request.getSelection());
        }

        context.setRequestHeaders(request.getHeaders());

        if (request.getData() instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) request.getData();
            if (action.getArgsNormalizer() != null) {

                IGraphQLArgsNormalizer argsNormalizer = action.getArgsNormalizer();
                Map<String, Object> data = argsNormalizer.normalize(map, context);
                ((ApiRequest<Map<String, Object>>) request).setData(data);
                map = data;
            }

            checkOperationArgs(action, map);
        }

        GraphQLFieldSelection field = initForReturnType(context, opType, operationName,
                request.getData(), action.getType(), request.getSelection());
        field.setFieldDefinition(action);
    }

    public GraphQLFieldSelection initForReturnType(IGraphQLExecutionContext context,
                                                   GraphQLOperationType operationType, String operationName, Object request,
                                                   GraphQLType returnType, FieldSelectionBean selectionBean) {
        GraphQLDocument doc = new GraphQLDocument();
        GraphQLFieldSelection selection = doc.addOperation(operationType, operationName, request);
        if (operationName.equals(SYS_OPERATION_FETCH_RESULTS)) {
            GraphQLFieldDefinition mockField = new GraphQLFieldDefinition();
            mockField.setName(operationName);
            mockField.setType(returnType);
            selection.setFieldDefinition(mockField);
        }

        GraphQLSelectionSet selectionSet = new RpcSelectionSetBuilder(this.builtinSchema, schemaLoader,
                CFG_GRAPHQL_QUERY_MAX_DEPTH.get()).buildForType(returnType, selectionBean);

        selection.setSelectionSet(selectionSet);
        resolveSelections(doc, CFG_GRAPHQL_QUERY_MAX_DEPTH.get());

        ParsedGraphQLRequest req = new ParsedGraphQLRequest();
        req.setDocument(doc);
        context.setRequest(req);


        GraphQLOperation op = doc.getOperation();
        context.setOperation(op);

        FieldSelectionBean fieldSelection = buildSelectionBean(operationName, op.getSelectionSet(), Collections.emptyMap());

        context.setFieldSelection(fieldSelection);
        return selection;
    }

    private void validateSelection(FieldSelectionBean selection) {
        int count = selection.getAllDirectiveCount();
        int maxCount = CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get();
        if (count > maxCount) {
            throw new NopException(ERR_GRAPHQL_EXCEED_MAX_DIRECTIVE_PER_REQUEST)
                    .param(ARG_MAX_COUNT, maxCount)
                    .param(ARG_COUNT, count);
        }
    }

    FieldSelectionBean buildSelectionBean(String operationName, FieldSelectionBean selectionBean) {
        FieldSelectionBean ret = new FieldSelectionBean();
        ret.addField(operationName, selectionBean);
        return ret;
    }

    void checkOperationArgs(GraphQLFieldDefinition field, Map<String, Object> data) {
        for (String name : data.keySet()) {
            // 忽略以_为前缀的属性。AMIS的schemaApi会自动添加_replace=1
            if (name.startsWith("_"))
                continue;

            if (field.getArg(name) == null)
                throw new NopException(ERR_GRAPHQL_UNKNOWN_OPERATION_ARG)
                        .param(ARG_OPERATION_NAME, field.getOperationName())
                        .param(ARG_ARG_NAME, name)
                        .param(ARG_ALLOWED_NAMES, field.getArgNames());
        }
    }

    @Override
    public CompletionStage<ApiResponse<?>> executeRpcAsync(IGraphQLExecutionContext gqlCtx) {
        IGraphQLExecutor executor = newGraphQLExecutor();
        IAsyncFunctionInvoker executionInvoker = getExecutionInvoker(gqlCtx);

        Supplier<CompletionStage<Object>> task = () -> {
            CompletionStage<Object> future;
            if (executionInvoker != null) {
                future = executionInvoker.invokeAsync(executor::executeOneAsync, gqlCtx);
            } else {
                future = executor.executeOneAsync(gqlCtx);
            }

            return future;
        };

        return gqlCtx.getServiceContext().invokeWithBindingCtx(() -> toRpcResponse(task, gqlCtx));
    }

    CompletionStage<ApiResponse<?>> toRpcResponse(Supplier<CompletionStage<Object>> task, IGraphQLExecutionContext context) {
        try {
            CompletionStage<?> future = task.get();
            return (CompletionStage) future.thenApply(v -> {
                ApiResponse<?> ret = buildRpcResponse(v, null, context);
                return ret;
            }).exceptionally(err -> {
                ApiResponse<?> ret = buildRpcResponse(null, err, context);
                return (ApiResponse) ret;
            });
        } catch (Exception e) {
            ApiResponse<?> ret = buildRpcResponse(null, e, context);
            return FutureHelper.toCompletionStage(ret);
        }
    }

    @Override
    public CompletionStage<GraphQLResponseBean> executeGraphQLAsync(IGraphQLExecutionContext gqlCtx) {
        IGraphQLExecutor executor = newGraphQLExecutor();
        IAsyncFunctionInvoker executionInvoker = getExecutionInvoker(gqlCtx);

        Supplier<CompletionStage<Object>> task = () -> {
            CompletionStage<Object> future;
            if (executionInvoker != null) {
                future = executionInvoker.invokeAsync(executor::executeAsync, gqlCtx);
            } else {
                future = executor.executeAsync(gqlCtx);
            }

            return future;
        };

        return gqlCtx.getServiceContext().invokeWithBindingCtx(() -> toGraphQLResponse(task, gqlCtx));
    }

    CompletionStage<GraphQLResponseBean> toGraphQLResponse(Supplier<CompletionStage<Object>> task, IGraphQLExecutionContext context) {
        try {
            CompletionStage<?> future = task.get();
            return future.thenApply(v -> {
                GraphQLResponseBean ret = buildGraphQLResponse(v, null, context);
                return ret;
            }).exceptionally(err -> {
                GraphQLResponseBean ret = buildGraphQLResponse(null, err, context);
                return ret;
            });
        } catch (Exception e) {
            GraphQLResponseBean ret = buildGraphQLResponse(null, e, context);
            return FutureHelper.toCompletionStage(ret);
        }
    }

    @Override
    public GraphQLResponseBean buildGraphQLResponse(Object result, Throwable err, IGraphQLExecutionContext context) {
        GraphQLResponseBean ret;
        if (err != null) {
            LOG.error("nop.graphql.execute-fail", err);
            String locale = ContextProvider.currentLocale();
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(locale, err, false, true);
            ret = new GraphQLResponseBean();
            ret.addError(errorBean);
        } else {
            if (result instanceof GraphQLResponseBean) {
                ret = (GraphQLResponseBean) result;
            } else {
                ret = new GraphQLResponseBean();
                ret.setData(result);
                return ret;
            }
        }

        if (context != null) {
            if (err != null) {
                context.completeExceptionally(err);
            } else {
                context.complete();
            }
        }
        return ret;
    }

    @Override
    public ApiResponse<?> buildRpcResponse(Object result, Throwable err, IGraphQLExecutionContext context) {
        ApiResponse<?> res;
        if (err != null) {
            NopException.logIfNotTraced(LOG, "nop.graphql.rest-execute-fail", err);
            String locale = ContextProvider.currentLocale();
            res = ErrorMessageManager.instance().buildResponseForException(locale, err);
        } else if (result instanceof ApiResponse<?>) {
            res = (ApiResponse<?>) result;
        } else {
            res = ApiResponse.success(result);
        }

        if (context != null) {
            if (context.getResponseHeaders() != null)
                context.getResponseHeaders().forEach(res::setHeader);

            if (err != null) {
                context.completeExceptionally(err);
            } else {
                context.complete();
            }
        }
        return res;
    }

    protected IAsyncFunctionInvoker getExecutionInvoker(IGraphQLExecutionContext context) {
        IAsyncFunctionInvoker invoker = this.executionInvoker;
        invoker = new TccContextInvoker(invoker);

        if (flowControlRunner != null)
            invoker = new GraphQLFlowControlInvoker(flowControlRunner, invoker);

        invoker = cancelTokenManager.wrap(invoker, context.getServiceContext());

        return invoker;
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

    @Override
    public CompletionStage<Object> fetchResultWithSelection(Object result, String resultType,
                                                            FieldSelectionBean selectionBean, IServiceContext ctx) {
        IGraphQLExecutor executor = newGraphQLExecutor();

        GraphQLType gqlType = new GraphQLDocumentParser().parseType(null, resultType);
        if (result instanceof Collection) {
            if (!gqlType.isListType()) {
                gqlType = GraphQLTypeHelper.listType(gqlType);
            }
        }
        IGraphQLExecutionContext context = newGraphQLContextFromContext(ctx);

        initForReturnType(context, GraphQLOperationType.query, SYS_OPERATION_FETCH_RESULTS, result, gqlType, selectionBean);

        return executor.fetchResult(result, context);
    }

    protected IGraphQLExecutor newGraphQLExecutor() {
        return new GraphQLExecutor(operationInvoker, graphQLHook, flowControlRunner, this);
    }
}
