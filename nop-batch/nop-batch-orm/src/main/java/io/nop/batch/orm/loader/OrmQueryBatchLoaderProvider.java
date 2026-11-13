/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.orm.loader;

import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nop.dao.api.IEntityDao;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.sql_lib.ISqlLibManager;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.List;

public class OrmQueryBatchLoaderProvider<S extends IDaoEntity> implements IBatchLoaderProvider<S> {
    static final Logger LOG = LoggerFactory.getLogger(OrmQueryBatchLoaderProvider.class);

    private IQueryBuilder queryBuilder;

    private ISqlLibManager sqlLibManager;

    private IDaoProvider daoProvider;

    private List<String> batchLoadProps;

    private String entityName;

    private String partitionIndexField;

    static class LoaderState<S extends IDaoEntity> {
        /**
         * 1.7-C7-007（plan 2546 W-03）：游标锚点改为**排序键值快照实体**，不再持有上一页的活实体引用。
         * 活实体引用在「orderBy 列被本任务就地变更」时会漂移（findNext 按实体当前排序值构造排他下界，
         * 变更后剩余 WAITING 行永久落在窗口外 → 静默丢行）。此实体在 newLoaderState 创建一次并复用，
         * 每次 load 仅覆盖快照值；状态为 TRANSIENT（不入 cache、不 flush、不触发 DB 装载）。
         */
        S cursor;
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

    public String getPartitionIndexField() {
        return partitionIndexField;
    }

    public void setPartitionIndexField(String partitionIndexField) {
        this.partitionIndexField = partitionIndexField;
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
        return (batchSize, ctx) -> {
            synchronized (state) {
                return load(batchSize, state);
            }
        };
    }

    LoaderState<S> newLoaderState(IBatchTaskContext context) {
        LoaderState<S> state = new LoaderState<>();
        if (queryBuilder == null) {
            // P1-3.3-001（plan 2381）：全表加载兜底必须可观测——显式无过滤读全表是合法用途，
            // 但静默兜底会让「声明了过滤条件却被丢弃」的配置缺陷不可见（init() Guard 仅覆盖 bean 形态）
            LOG.warn("nop.batch.orm-reader-no-query:entityName={},fallback=full-table-scan", entityName);
        }
        state.query = queryBuilder == null ? new QueryBean() : queryBuilder.buildQuery(context);
        IntRangeSet partitions = context.getPartitionRange();

        // 自动增加分区条件
        if (partitionIndexField != null && partitions != null) {
            state.query.addPartitionFilter(partitions, partitionIndexField);
        }

        String entityName = this.entityName;
        if (state.query.getSourceName() != null)
            entityName = state.query.getSourceName();
        state.dao = daoProvider.dao(entityName);

        // 1.7-C7-007（plan 2546 W-03）：快照实体创建一次并复用（避免每 chunk newEntity 的开销与置脏）。
        state.cursor = null;
        return state;
    }

    List<S> load(int batchSize, LoaderState<S> state) {
        IEntityDao<S> dao = state.dao;
        List<S> list = dao.findNext(state.cursor, state.query.getFilter(), state.query.getOrderBy(), batchSize);

        if (list.isEmpty()) {
            return list;
        }

        if (batchLoadProps != null)
            dao.batchLoadProps(list, batchLoadProps);

        // batchLoadProps有可能报错，这里将状态更新放到最后
        snapshotCursor(state, list.get(list.size() - 1));
        return list;
    }

    /**
     * 1.7-C7-007（plan 2546 W-03）：把锚点的**排序键值**拷入快照实体，而非保留活实体引用。
     * <p>
     * 快照列 = orderBy 列 + 主键列（{@code dao.getPkColumnNames()}，返回 prop 名，与
     * {@code DaoQueryHelper.appendCursorCmp} 的 {@code field.getName()} 同域）。
     * 快照实体经 {@code dao.newEntity()} 创建（TRANSIENT、不入 cache、不 flush）。
     */
    @SuppressWarnings("unchecked")
    static <S extends IDaoEntity> void snapshotCursor(LoaderState<S> state, S lastEntity) {
        IEntityDao<S> dao = state.dao;
        if (state.cursor == null) {
            state.cursor = dao.newEntity();
        }
        S cursor = state.cursor;

        if (state.query.getOrderBy() != null) {
            for (OrderFieldBean field : state.query.getOrderBy()) {
                String name = field.getName();
                if (StringHelper.isEmpty(name))
                    continue;
                cursor.orm_propValueByName(name, lastEntity.orm_propValueByName(name));
            }
        }
        for (String pkName : dao.getPkColumnNames()) {
            cursor.orm_propValueByName(pkName, lastEntity.orm_propValueByName(pkName));
        }
    }
}