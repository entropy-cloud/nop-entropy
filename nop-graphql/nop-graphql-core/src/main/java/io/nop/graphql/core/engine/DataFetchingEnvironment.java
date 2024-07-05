/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldSelection;

/**
 * 单个字段加载时的上下文环境
 */
public class DataFetchingEnvironment implements IDataFetchingEnvironment {
    private Object root;
    private Object source;
    private GraphQLFieldSelection selection;

    private FieldSelectionBean selectionBean;
    private IGraphQLExecutionContext context;

    private Object opRequest;

    private boolean async;

    @Override
    public Object getOpRequest() {
        return opRequest;
    }

    public void setOpRequest(Object opRequest) {
        this.opRequest = opRequest;
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    /**
     * 如果是异步执行，则当前env被异步任务占用，必须新建一个env来使用。否则可以复用当前的env
     */
    public DataFetchingEnvironment prepare() {
        if (!async) {
            setOpRequest(null);
            return this;
        }

        return copy();
    }

    public DataFetchingEnvironment copy() {
        DataFetchingEnvironment env = new DataFetchingEnvironment();
        env.setRoot(root);
        env.setExecutionContext(context);
        // env.setDepth(getDepth());
        return env;
    }

    @Override
    public Object getRoot() {
        return root;
    }

    public void setRoot(Object root) {
        this.root = root;
    }

    @Override
    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    @Override
    public FieldSelectionBean getSelectionBean() {
        return selectionBean;
    }

    public void setSelectionBean(FieldSelectionBean selectionBean) {
        this.selectionBean = selectionBean;
    }

    @Override
    public GraphQLFieldSelection getSelection() {
        return selection;
    }

    public void setSelection(GraphQLFieldSelection selection) {
        this.selection = selection;
    }

    @Override
    public IGraphQLExecutionContext getGraphQLExecutionContext() {
        return context;
    }

    public void setExecutionContext(IGraphQLExecutionContext context) {
        this.context = context;
    }

}
