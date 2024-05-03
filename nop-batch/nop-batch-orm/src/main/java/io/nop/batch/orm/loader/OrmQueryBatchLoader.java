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
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoader;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.sql_lib.ISqlLibManager;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.List;

public class OrmQueryBatchLoader<S extends IDaoEntity> implements IBatchLoader<S, IBatchChunkContext>, IBatchTaskListener {

    private IQueryBuilder queryBuilder;

    private ISqlLibManager sqlLibManager;

    private IDaoProvider daoProvider;

    private List<String> batchLoadProps;

    private String entityName;


    private S lastEntity;
    private QueryBean query;
    private IEntityDao<S> dao;

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
    public void onTaskBegin(IBatchTaskContext context) {
        query = queryBuilder.buildQuery(context);
        lastEntity = null;
        String entityName = this.entityName;
        if (query.getSourceName() != null)
            entityName = query.getSourceName();
        dao = daoProvider.dao(entityName);
    }

    @Override
    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        query = null;
        lastEntity = null;
    }

    @Override
    public synchronized List<S> load(int batchSize, IBatchChunkContext context) {
        List<S> list = dao.findNext(lastEntity, query.getFilter(), query.getOrderBy(), batchSize);

        if (list.isEmpty()) {
            return list;
        }

        lastEntity = list.get(list.size() - 1);

        if (batchLoadProps != null)
            dao.batchLoadProps(list, batchLoadProps);
        return list;
    }
}