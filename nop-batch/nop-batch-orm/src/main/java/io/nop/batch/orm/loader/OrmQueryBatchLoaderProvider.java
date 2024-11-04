/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.orm.loader;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.sql_lib.ISqlLibManager;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.List;

public class OrmQueryBatchLoaderProvider<S extends IDaoEntity> implements IBatchLoaderProvider<S> {

    private IQueryBuilder queryBuilder;

    private ISqlLibManager sqlLibManager;

    private IDaoProvider daoProvider;

    private List<String> batchLoadProps;

    private String entityName;

    static class LoaderState<S extends IDaoEntity> {
        S lastEntity;
        QueryBean query;
        IEntityDao<S> dao;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Inject
    public void setSqlLibManager(ISqlLibManager sqlLibManager) {
        this.sqlLibManager = sqlLibManager;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setQueryBuilder(IQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public void setQuery(QueryBean query) {
        Guard.notNull(query, "query");
        setQueryBuilder(ctx -> query.cloneInstance());
    }

    public List<String> getBatchLoadProps() {
        return batchLoadProps;
    }

    public void setBatchLoadProps(List<String> batchLoadProps) {
        this.batchLoadProps = batchLoadProps;
    }

    public void setSqlName(String sqlName) {
        setQueryBuilder(ctx -> {
            return sqlLibManager.buildQueryBean(sqlName, ctx);
        });
    }

    @PostConstruct
    public void init() {
        Guard.notNull(queryBuilder, "queryBuilder");
    }

    @Override
    public IBatchLoader<S> setup(IBatchTaskContext context) {
        LoaderState<S> state = newLoaderState(context);
        return (batchSize, ctx) -> load(batchSize, state);
    }

    LoaderState<S> newLoaderState(IBatchTaskContext context) {
        LoaderState<S> state = new LoaderState<>();
        state.query = queryBuilder.buildQuery(context);
        state.lastEntity = null;
        String entityName = this.entityName;
        if (state.query.getSourceName() != null)
            entityName = state.query.getSourceName();
        state.dao = daoProvider.dao(entityName);
        return state;
    }

    synchronized List<S> load(int batchSize, LoaderState<S> state) {
        IEntityDao<S> dao = state.dao;
        List<S> list = dao.findNext(state.lastEntity, state.query.getFilter(), state.query.getOrderBy(), batchSize);

        if (list.isEmpty()) {
            return list;
        }

        state.lastEntity = list.get(list.size() - 1);

        if (batchLoadProps != null)
            dao.batchLoadProps(list, batchLoadProps);
        return list;
    }
}