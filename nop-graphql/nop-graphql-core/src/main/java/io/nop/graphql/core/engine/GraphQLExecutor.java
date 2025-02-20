/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLHook;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLFragmentSelection;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static io.nop.api.core.util.FutureHelper.tryResolve;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;

public class GraphQLExecutor implements IGraphQLExecutor {
    static Logger LOG = LoggerFactory.getLogger(GraphQLExecutor.class);

    private final IAsyncFunctionInvoker operationInvoker;
    private final IGraphQLHook graphQLHook;
    private final IGraphQLEngine engine;
    private final IFlowControlRunner runner;

    private final Object resultLock = new Object();

    public GraphQLExecutor(IAsyncFunctionInvoker operationInvoker, IGraphQLHook graphQLHook,
                           IFlowControlRunner runner,
                           IGraphQLEngine engine) {
        this.operationInvoker = operationInvoker;
        this.graphQLHook = graphQLHook;
        this.engine = engine;
        this.runner = runner;
        if (operationInvoker == null) {
            LOG.info("nop.graphql.executor_operation_invoker_is_null");
        }
    }

    @Override
    public CompletionStage<Object> executeOneAsync(IGraphQLExecutionContext context) {
        GraphQLActionAuthChecker.INSTANCE.check(context);
        GraphQLArgumentValidator.INSTANCE.validate(context);

        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(context);
        env.setSource(null);
        env.setRoot(null);

        GraphQLFieldSelection operation = context.getOperation().getFieldSelection();
        env.setSelection(operation);
        env.setOpRequest(operation.getOpRequest());
        env.setSelectionBean(context.getFieldSelection().getField(operation.getAliasOrName()));
        env.setOperationName(operation.getName());

        Object meter = graphQLHook == null ? null : graphQLHook.beginExecute(context);


        CompletionStage<Object> future = invokeOperation(env);

        if (graphQLHook != null) {
            future = future.whenComplete((ret, err) -> {
                graphQLHook.endExecute(meter, err, context);
            });
        }

        dispatchAll(context);

        return future;
    }


    @Override
    public CompletionStage<Object> fetchResult(Object result, IGraphQLExecutionContext context) {
        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(context);
        env.setSource(result);
        env.setRoot(result);

        GraphQLFieldSelection operation = context.getOperation().getFieldSelection();
        env.setSelection(operation);
        env.setOpRequest(operation.getOpRequest());
        env.setSelectionBean(context.getFieldSelection().getField(operation.getAliasOrName()));

        CompletionStage<Object> future = FutureHelper.toCompletionStage(fetchNext(result, env));

        dispatchAll(context);
        return future;
    }

    private CompletionStage<Object> invokeOperation(DataFetchingEnvironment env) {
        CompletionStage<OperationResult> future;

        if (operationInvoker != null) {
            future = operationInvoker.invokeAsync(r -> invokeOperationOrTry(env), env);
        } else {
            future = invokeOperationOrTry(env);
        }

        return future.thenCompose(opResult -> {
            if (opResult.isUseTry()) {
                return FutureHelper.toCompletionStage(opResult.getValue());
            }
            env.setRoot(opResult.getValue());
            return FutureHelper.toCompletionStage(fetchNext(opResult.getValue(), env));
        });
    }

    private CompletionStage<Object> thenFetchNext(CompletionStage<?> future, DataFetchingEnvironment env) {
        // thenOnContext确保所有fetcher都在同一个异步context上执行
        return ContextProvider.thenOnContext(future, env.getContext()).thenCompose(v -> {
            return FutureHelper.toCompletionStage(fetchNext(v, env));
        });
    }

    public CompletionStage<Object> executeAsync(IGraphQLExecutionContext context) {
        Guard.notNull(context.getFieldSelection(), "fieldSelection");


        GraphQLActionAuthChecker.INSTANCE.check(context);
        GraphQLArgumentValidator.INSTANCE.validate(context);

        Map<String, Object> response = new LinkedHashMap<>();
        context.setResponse(response);

        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setExecutionContext(context);
        env.setSource(null);
        env.setRoot(null);
        env.setSelectionBean(context.getFieldSelection());

        CompletionStage<Object> future = invokeOperations(env);

        dispatchAll(context);
        return future;
    }

    void dispatchAll(IGraphQLExecutionContext context) {
        CompletionStage<Void> promise = context.dispatchAll();
        if (promise != null) {
            promise.thenAccept(v -> this.dispatchAll(context));
        }
    }

    private CompletionStage<Object> invokeOperations(DataFetchingEnvironment env) {
        Map<String, Object> ret = new LinkedHashMap<>();

        // 先执行所有的operation，如果全部成功再调用fetchNext，减少数据库事务的持有时间，并避免
        List<Supplier<CompletionStage<Object>>> actions = new ArrayList<>();

        CompletionStage<?> future;
        if (operationInvoker != null) {
            future = operationInvoker.invokeAsync(r -> _invokeOperations(env, ret, actions), env);
        } else {
            future = _invokeOperations(env, ret, actions);
        }

        if (actions.isEmpty())
            return future.thenApply(v -> ret);

        return future.thenCompose(v -> {
            List<CompletionStage<?>> promises = new ArrayList<>(actions.size());
            for (Supplier<CompletionStage<Object>> action : actions) {
                promises.add(action.get());
            }
            return FutureHelper.waitAll(promises);
        }).thenApply(v -> ret);
    }

    private CompletionStage<OperationResult> invokeOperationOrTry(DataFetchingEnvironment env) {
        Object meter = graphQLHook == null ? null : graphQLHook.beginInvoke(env);

        CompletionStage<OperationResult> future = null;
        if (env.getGraphQLExecutionContext().isMakerCheckerEnabled()) {
            GraphQLFieldSelection operation = env.getGraphQLExecutionContext().getOperation().getFieldSelection();
            GraphQLFieldDefinition fieldDef = operation.getFieldDefinition();
            if (fieldDef.getTryAction() != null) {
                Object request = env.getOpRequest();
                FieldSelectionBean selection = env.getSelectionBean();
                future = FutureHelper.futureCall(() -> {
                    return withFlowControl(v -> fieldDef.getTryAction().invoke(request, selection, env.getGraphQLExecutionContext().getServiceContext())).get(env);
                }).thenApply(v -> {
                    return new OperationResult(v, true);
                });
            }
        }

        if (future == null) {
            GraphQLFieldSelection selection = env.getSelection();
            GraphQLFieldDefinition fieldDef = selection.getFieldDefinition();
            IDataFetcher fetcher = fieldDef.getFetcher();
            if (fetcher == null)
                throw new IllegalStateException("nop.graphql.null-operation-fetcher:" + fieldDef.getName());

            future = FutureHelper.toCompletionStage(withFlowControl(fetcher).get(env)).thenApply(v -> {
                v = normalizeValue(v, selection);
                return new OperationResult(v, false);
            });
        }

        CompletionStage<OperationResult> opFuture = future.whenComplete((v, err) -> {
            if (graphQLHook != null)
                graphQLHook.endInvoke(meter, err, env);
        });

        return opFuture;
    }

    private IDataFetcher withFlowControl(IDataFetcher fetcher) {
        if (runner == null)
            return fetcher;
        return new FlowControlFetcher(runner, fetcher);
    }

    private CompletionStage<Void> _invokeOperations(DataFetchingEnvironment baseEnv, Map<String, Object> result,
                                                    List<Supplier<CompletionStage<Object>>> actions) {
        FieldSelectionBean sourceSelection = baseEnv.getSelectionBean();
        GraphQLSelectionSet selectionSet = baseEnv.getGraphQLExecutionContext().getOperation().getSelectionSet();

        List<CompletionStage<?>> promises = new ArrayList<>();

        // int depth = env.getDepth();
        for (GraphQLSelection selection : selectionSet.getSelections()) {
            // 因为通过action延迟执行导致env会在多处复用，因此这里需要复制一份
            DataFetchingEnvironment opEnv = baseEnv.copy();

            // env.setDepth(depth + 1);
            opEnv.setSource(null);
            GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
            opEnv.setSelection(fieldSelection);

            // selection的key为返回的变量名
            String alias = fieldSelection.getAliasOrName();
            FieldSelectionBean selectionBean = sourceSelection.getField(alias);
            if (selectionBean == null) {
                // 没有选择表示通过@skip或者@include方式跳过
                continue;
            }
            opEnv.setSelectionBean(selectionBean);
            opEnv.setOperationName(fieldSelection.getName());

            CompletionStage<OperationResult> future = invokeOperationOrTry(opEnv);
            actions.add(() -> {
                return thenFetchNext(future.thenApply(r -> {
                    opEnv.setRoot(r.getValue());
                    return r.getValue();
                }), opEnv).thenApply(v -> {
                    v = normalizeValue(v, fieldSelection);
                    synchronized (resultLock) {
                        result.put(alias, v);
                    }
                    return v;
                });
            });
            FutureHelper.collectWaiting(future, promises);
        }
        return FutureHelper.waitAll(promises);
    }

    protected Object fetchSelections(Object source, GraphQLSelectionSet selectionSet, DataFetchingEnvironment env) {
        Map<String, Object> ret = new LinkedHashMap<>();

        List<CompletionStage<?>> promises = _fetchSelections(null, ret, source, selectionSet, env);

        if (promises == null) {
            return ret;
        }

        return FutureHelper.waitAll(promises).thenApply(v -> ret);
    }

    private List<CompletionStage<?>> _fetchSelections(List<CompletionStage<?>> promises, Map<String, Object> ret,
                                                      Object source, GraphQLSelectionSet selectionSet, DataFetchingEnvironment env) {
        FieldSelectionBean sourceSelection = env.getSelectionBean();

        // int depth = env.getDepth();
        for (GraphQLSelection selection : selectionSet.getSelections()) {
            env = env.prepare();
            // env.setDepth(depth + 1);
            env.setSource(source);

            if (selection instanceof GraphQLFieldSelection) {
                GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
                env.setSelection(fieldSelection);

                // selection的key为返回的变量名
                String alias = fieldSelection.getAliasOrName();
                FieldSelectionBean selectionBean = sourceSelection.getField(alias);
                if (selectionBean == null) {
                    continue;
                }

                env.setSelectionBean(selectionBean);

                Object value = fetchSelection(env);
                if (value instanceof CompletionStage) {
                    env.setAsync(true);
                    if (promises == null)
                        promises = new ArrayList<>();
                    CompletionStage<?> promise = (CompletionStage<?>) value;
                    promise = promise.thenAccept(v -> {
                        Object v2 = normalizeValue(v, fieldSelection);
                        synchronized (resultLock) {
                            ret.put(alias, v2);
                        }
                    });
                    promises.add(promise);
                } else {
                    value = normalizeValue(value, fieldSelection);
                    synchronized (resultLock) {
                        ret.put(alias, value);
                    }
                }
            } else if (selection instanceof GraphQLFragmentSelection) {
                GraphQLFragmentSelection fragmentSelection = (GraphQLFragmentSelection) selection;
                GraphQLSelectionSet fragmentSelectionSet = fragmentSelection.getResolvedFragment().getSelectionSet();
                env.setSelectionBean(sourceSelection);
                promises = _fetchSelections(promises, ret, source, fragmentSelectionSet, env);
            }
        }
        return promises;
    }

    // 确保返回值类型与GraphQL定义中的类型一致
    private Object normalizeValue(Object value, GraphQLFieldSelection selection) {
        GraphQLFieldDefinition field = selection.getFieldDefinition();
        return field.getTypeConverter().convert(value, err -> new NopException(err).param(ARG_FIELD_NAME, selection.getName()));
    }

    /**
     * 返回的是selection最终装载完毕的结果
     */
    private Object fetchSelection(DataFetchingEnvironment env) {
        GraphQLFieldSelection selection = env.getSelection();
        GraphQLFieldDefinition fieldDef = selection.getFieldDefinition();

        IDataFetcher fetcher = fieldDef.getFetcher();
        Object result = tryResolve(hookFetch(fetcher, env));
        if (result instanceof CompletionStage) {
            // thenOnContext确保所有fetcher都在同一个异步context上执行
            result = thenFetchNext((CompletionStage<?>) result, env);
        } else {
            result = fetchNext(result, env);
        }

        return result;
    }

    Object hookFetch(IDataFetcher fetcher, DataFetchingEnvironment env) {
        if (fetcher == null)
            fetcher = BeanPropertyFetcher.INSTANCE;

        if (graphQLHook == null)
            return fetcher.get(env);

        Object meter = graphQLHook.beginDataFetch(env);
        try {
            Object ret = fetcher.get(env);
            if (ret instanceof CompletionStage<?>) {
                return ((CompletionStage<Object>) ret).whenComplete((v, err) -> {
                    graphQLHook.endDataFetch(meter, err, env);
                });
            } else {
                graphQLHook.endDataFetch(meter, null, env);
            }
            return ret;
        } catch (Exception err) {
            graphQLHook.endDataFetch(meter, err, env);
            throw NopException.adapt(err);
        }
    }

    //
    // DataFetchingEnvironment buildNextEnv(DataFetchingEnvironment env, Object result, boolean root) {
    // if (!root)
    // return env;
    // env = env.prepare();
    // env.setRoot(result);
    // return env;
    // }

    protected Object fetchNext(Object value, DataFetchingEnvironment env) {
        if (isEmpty(value))
            return value;

        GraphQLSelectionSet selectionSet = env.getSelection().getSelectionSet();
        if (selectionSet == null)
            return value;

        GraphQLFieldDefinition fieldDef = env.getSelection().getFieldDefinition();
        if (fieldDef.getType().isListType()) {
            return fetchList((Collection<?>) value, selectionSet, env);
        } else {
            return fetchSelections(value, selectionSet, env);
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null)
            return true;
        if (value instanceof Collection)
            return ((Collection) value).isEmpty();
        return false;
    }

    protected Object fetchList(Collection<?> c, GraphQLSelectionSet selectionSet, DataFetchingEnvironment env) {
        List<Object> list = new ArrayList<>(c.size());
        List<CompletionStage<?>> promises = null;

        // 仅在必要的时候使用异步获取
        FieldSelectionBean sourceSelection = env.getSelectionBean();
        for (Object o : c) {
            if (o == null) {
                list.add(o);
                continue;
            }
            env = env.prepare();
            env.setSource(o);
            env.setSelectionBean(sourceSelection);
            Object value = fetchSelections(o, selectionSet, env);
            list.add(value);
            if (value instanceof CompletionStage) {
                if (promises == null)
                    promises = new ArrayList<>();
                promises.add((CompletionStage<?>) value);

                env.setAsync(true);
            }
        }

        if (promises == null) {
            // 如果都已经通过同步方式获取到数据，则直接返回
            return list;
        } else {
            return FutureHelper.waitAll(promises).thenApply(r -> {
                List<Object> values = new ArrayList<>(list.size());
                for (Object value : list) {
                    if (value instanceof CompletionStage) {
                        value = FutureHelper.syncGet((CompletionStage<? extends Object>) value);
                    }
                    values.add(value);
                }
                return values;
            });
        }
    }
}