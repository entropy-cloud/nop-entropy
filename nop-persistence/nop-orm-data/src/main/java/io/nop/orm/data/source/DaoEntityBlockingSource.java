package io.nop.orm.data.source;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.IBlockingSource;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.xlang.api.XLang;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DaoEntityBlockingSource<T extends IOrmEntity> implements IBlockingSource<T> {
    private ISqlLibManager sqlLibManager;
    private IDaoProvider daoProvider;
    private String entityName;
    private IQueryBuilder queryBuilder;

    private String acquireHostField;
    private String acquiredTimeField;
    private String acquiredStatusField;
    private int acquiredStatus;

    private long pollInterval;


    public void setQueryBuilder(IQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    public void setQuery(QueryBean query) {
        this.setQueryBuilder(ctx -> query.cloneInstance());
    }

    public IDaoProvider getDaoProvider() {
        return daoProvider;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Inject
    public void setSqlLibManager(ISqlLibManager sqlLibManager) {
        this.sqlLibManager = sqlLibManager;
    }

    public void setQuerySqlName(String querySqlName) {
        Guard.notEmpty(querySqlName, "querySqlName");
        setQueryBuilder(ctx -> {
            return sqlLibManager.buildQueryBean(querySqlName, ctx);
        });
    }

    @PostConstruct
    public void init() {
        Guard.notEmpty(entityName, "entityName");
        Guard.notNull(queryBuilder, "queryBuilder");
    }

    @Override
    public T take() throws InterruptedException {
        List<T> items = new ArrayList<>(1);
        takeMulti(items, 1);
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public T poll() {
        try {
            return poll(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw NopException.adapt(e);
        }
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        List<T> item = new ArrayList<>(1);
        long wait = unit.toMillis(timeout);
        drainTo(item, 1, wait, wait);
        return item.isEmpty() ? null : item.get(0);
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        try {
            return drainTo(c, maxElements, 0, 0);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw NopException.adapt(e);
        }
    }

    @Override
    public int takeMulti(Collection<? super T> items, int maxCount) throws InterruptedException {
        return drainTo(items, maxCount, -1L, -1L);
    }

    /**
     * 获取实体集合，并修改下次检查时间
     *
     * @param c           容纳返回结果的数据集合
     * @param maxElements 最多取出多少条数据
     * @param minWait     如果没有获取到足够多的对象，则可以继续等待一段时间。等待此时间后，如果能够获取到一些对象，则返回。
     * @param maxWait     无论是否获取到对象，超过此时间都要返回
     * @return
     * @throws InterruptedException
     */
    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait) throws InterruptedException {
        IEntityDao<T> dao = daoProvider.dao(entityName);
        if (maxWait == 0) {
            List<T> items = loadItems(dao, maxElements);
            c.addAll(items);
            return items.size();
        }

        FutureHelper.waitUntil(() -> {
            List<T> items = loadItems(dao, maxElements);
            if (items.isEmpty()) {
                return false;
            }
            dao.flushSession();
            c.addAll(items);
            return true;
        }, maxWait, minWait <= 0 ? pollInterval : Math.min(pollInterval, minWait));
        return c.size();
    }

    private List<T> loadItems(IEntityDao<T> dao, int maxCount) {
        QueryBean query = queryBuilder.buildQuery(XLang.newEvalScope());
        query.setLimit(maxCount);
        List<T> items = dao.findAllByQuery(query);

        // 获取到实体之后立刻修改其中的状态字段，通过状态过滤可以避免重复获取
        for (T item : items) {
            if (acquireHostField != null) {
                BeanTool.setProperty(item, acquireHostField, AppConfig.hostId());
            }
            if (acquiredTimeField != null) {
                BeanTool.setProperty(item, acquiredTimeField, CoreMetrics.currentTimestamp());
            }
            if (acquiredStatusField != null) {
                BeanTool.setProperty(item, acquiredStatusField, acquiredStatus);
            }
        }

        return items;
    }
}