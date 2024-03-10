/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.utils;


import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.http.api.client.IHttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 辅助构建GraphQL请求
 */
public class GraphQLRequestBuilder {
    public static final String OPERATION_TYPE_QUERY = "query";
    public static final String OPERATION_TYPE_MUTATION = "mutation";
    public static final String OPERATION_TYPE_SUBSCRIPTION = "subscription";

    private final String operationType;

    private final List<OperationBuilder> operations = new ArrayList<>();

    private static class OperationArg {
        final String name;
        final String type;
        final Object value;

        public OperationArg(String name, String type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public String getVarName(int index) {
            return "$" + name + "_" + index;
        }
    }

    public static class OperationBuilder {
        private final String operationName;
        private final String alias;
        private final int index;

        private final List<OperationArg> args = new ArrayList<>();
        private FieldSelectionBean selection;
        private Consumer<Object> onSuccess;
        private Consumer<Throwable> onFailure;

        public OperationBuilder(String operationName, int index) {
            this.operationName = operationName;
            this.index = index;
            this.alias = operationName + "_" + index;
        }

        public int getIndex() {
            return index;
        }

        public String getAlias() {
            return alias;
        }

        public String getOperationName() {
            return operationName;
        }

        public List<OperationArg> getArgs() {
            return args;
        }

        public FieldSelectionBean getSelection() {
            return selection;
        }

        public OperationBuilder addArg(String name, String type, Object value) {
            args.add(new OperationArg(name, type, value));
            return this;
        }

        public OperationBuilder selection(FieldSelectionBean selection) {
            this.selection = selection;
            return this;
        }

        public OperationBuilder onSuccess(Consumer<Object> onSuccess) {
            this.onSuccess = onSuccess;
            return this;
        }

        public OperationBuilder onFailure(Consumer<Throwable> onFailure){
            this.onFailure = onFailure;
            return this;
        }

        public void complete(Object result) {
            if (onSuccess != null)
                onSuccess.accept(result);
        }

        public void fail(Exception e) {
            if (onFailure != null)
                onFailure.accept(e);
        }
    }

    public GraphQLRequestBuilder(String operationType) {
        this.operationType = operationType;
    }

    public static GraphQLRequestBuilder query() {
        return new GraphQLRequestBuilder(OPERATION_TYPE_QUERY);
    }

    public static GraphQLRequestBuilder mutation() {
        return new GraphQLRequestBuilder(OPERATION_TYPE_MUTATION);
    }

    public static GraphQLRequestBuilder subscription() {
        return new GraphQLRequestBuilder(OPERATION_TYPE_SUBSCRIPTION);
    }

    public OperationBuilder addOperation(String operationName) {
        OperationBuilder operation = new OperationBuilder(operationName, operations.size());
        operations.add(operation);
        return operation;
    }

    public GraphQLRequestBean build() {
        GraphQLRequestBean req = new GraphQLRequestBean();
        req.setVariables(buildVariables());

        StringBuilder sb = new StringBuilder();
        beginQuery(sb, !req.getVariables().isEmpty());
        for (OperationBuilder operation : operations) {
            sb.append(operation.getAlias()).append(':');
            sb.append(operation.getOperationName());
            if (!operation.getArgs().isEmpty()) {
                sb.append('(');
                for (int i = 0, n = operation.getArgs().size(); i < n; i++) {
                    if (i != 0)
                        sb.append(',');
                    OperationArg arg = operation.getArgs().get(i);
                    sb.append(arg.name).append(':').append(arg.getVarName(operation.getIndex()));
                }
                sb.append(')');
            }

            if (operation.getSelection() != null) {
                sb.append('{');
                operation.getSelection().printTo(sb, false);
                sb.append('}');
            }

            sb.append('\n');
        }
        endQuery(sb);

        req.setQuery(sb.toString());

        return req;
    }

    private void beginQuery(StringBuilder sb, boolean hasArgs) {
        sb.append(operationType);
        if (hasArgs) {
            sb.append("(");
            int argCount = 0;
            for (OperationBuilder operation : operations) {
                for (OperationArg arg : operation.getArgs()) {
                    if (argCount != 0) {
                        sb.append(',');
                    }
                    sb.append(arg.getVarName(operation.getIndex()));
                    sb.append(':').append(arg.type);
                    argCount++;
                }
            }
            sb.append(")");
        }
        sb.append("{\n");
    }

    public void endQuery(StringBuilder sb) {
        sb.append("\n}");
    }

    public void handleResponse(IHttpResponse response) {
        GraphQLResponseBean bean = response.getBodyAsBean(GraphQLResponseBean.class);
        if (bean.hasError()) {
            RuntimeException e = NopRebuildException.rebuild(bean.toErrorBean());
            for (OperationBuilder operation : operations) {
                operation.fail(e);
            }
            throw e;
        }

        Map<String, Object> map = (Map<String, Object>) bean.getData();
        for (OperationBuilder operation : operations) {
            String alias = operation.getAlias();
            Object value = map.get(alias);
            operation.complete(value);
        }
    }

    private Map<String, Object> buildVariables() {
        Map<String, Object> vars = new TreeMap<>();
        for (int i = 0, n = operations.size(); i < n; i++) {
            OperationBuilder operation = operations.get(i);

            for (OperationArg arg : operation.getArgs()) {
                String varName = arg.name + "_" + operation.getIndex();
                vars.put(varName, arg.value);
            }
        }
        return vars;
    }
}