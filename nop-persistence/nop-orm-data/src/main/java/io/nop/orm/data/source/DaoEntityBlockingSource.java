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
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IQueryBuilder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.orm.support.OrmEntityHelper;
import io.nop.xlang.api.XLang;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DaoEntityBlockingSource<T extends IOrmEntity> implements IBlockingSource<T> {
    private static final Logger LOG = LoggerFactory.getLogger(DaoEntityBlockingSource.class);

    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 100L;

    private ISqlLibManager sqlLibManager;
    private IDaoProvider daoProvider;
    private IOrmTemplate ormTemplate;
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

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
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

    public void setAcquireHostField(String acquireHostField) {
        this.acquireHostField = acquireHostField;
    }

    public void setAcquiredTimeField(String acquiredTimeField) {
        this.acquiredTimeField = acquiredTimeField;
    }

    public void setAcquiredStatusField(String acquiredStatusField) {
        this.acquiredStatusField = acquiredStatusField;
    }

    public void setAcquiredStatus(int acquiredStatus) {
        this.acquiredStatus = acquiredStatus;
    }

    public void setPollInterval(long pollInterval) {
        this.pollInterval = pollInterval;
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
        Guard.notNull(ormTemplate, "ormTemplate");
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
     * 获取实体集合，并修改下次检查时间。按 {@link IBlockingSource} 的攒批契约实现：
     * 拿满 maxElements，或已取到部分数据且 minWait 攒批窗口用尽时返回；maxWait 到时无论是否拿满都返回。
     *
     * @param c           容纳返回结果的数据集合
     * @param maxElements 最多取出多少条数据
     * @param minWait     如果没有获取到足够多的对象，则可以继续等待一段时间。等待此时间后，如果能够获取到一些对象，则返回。
     * @param maxWait     无论是否获取到对象，超过此时间都要返回。maxWait < 0 表示无限等待
     * @return 本次实际转移到集合 c 中的元素个数（不包含调用前 c 中已有的元素）
     * @throws InterruptedException 等待过程中线程被中断且没有取到任何数据时抛出
     */
    @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
    @Override
    public int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait) throws InterruptedException {
        // 与 IBlockingSource 的 default 实现保持一致的入口防御
        if (maxElements <= 0)
            return 0;

        IEntityDao<T> dao = daoProvider.dao(entityName);
        if (maxWait == 0) {
            List<T> items = loadItems(dao, maxElements);
            c.addAll(items);
            return items.size();
        }

        // maxWait < 0 表示无限等待。FutureHelper.waitUntil 要求 timeout 为正数，
        // 因此换成一个足够大的值（避免 now + timeout 溢出）
        long timeout = maxWait > 0 ? maxWait : Long.MAX_VALUE / 2;
        long interval = minWait <= 0 ? pollInterval : Math.min(pollInterval, minWait);
        // 未配置 pollInterval 时避免以 0 间隔密集轮询数据库（无限等待场景下尤其危险）
        if (interval <= 0)
            interval = DEFAULT_POLL_INTERVAL_MILLIS;

        int oldSize = c.size();
        long begin = CoreMetrics.currentTimeMillis();
        boolean found = FutureHelper.waitUntil(() -> {
            List<T> items = loadItems(dao, maxElements - (c.size() - oldSize));
            if (!items.isEmpty()) {
                dao.flushSession();
                c.addAll(items);
            }
            // 拿满 maxElements 立即返回；已取到部分数据且 minWait 攒批窗口用尽也返回
            if (c.size() - oldSize >= maxElements)
                return true;
            return c.size() > oldSize && CoreMetrics.currentTimeMillis() - begin >= minWait;
        }, timeout, interval);

        // waitUntil 在线程被中断时复位中断标志并返回 false，这里恢复 InterruptedException 语义。
        // 已取到数据时优先返回数据
        if (!found && c.size() == oldSize && Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        return c.size() - oldSize;
    }

    private List<T> loadItems(IEntityDao<T> dao, int maxCount) {
        QueryBean query = queryBuilder.buildQuery(XLang.newEvalScope());
        query.setLimit(maxCount);
        List<T> items = dao.findAllByQuery(query);

        if (items.isEmpty() || !hasAcquireFields())
            return items;

        // 原子抢占：以UPDATE...WHERE 主键 AND 抢占字段保持被选中时的旧值 的方式写占用标记。
        // 两个消费者SELECT到同一批记录时，只有一个消费者的条件UPDATE能匹配成功，
        // 抢占失败的记录被跳过，避免同一任务被重复投递处理
        List<T> claimed = new ArrayList<>(items.size());
        for (T item : items) {
            if (tryClaim(dao, item)) {
                applyAcquireValues(item);
                claimed.add(item);
            } else {
                LOG.info("nop.orm.data.claim-conflict-skip:entityName={},id={}", entityName, item.get_id());
            }
        }
        return claimed;
    }

    private boolean hasAcquireFields() {
        return acquireHostField != null || acquiredTimeField != null || acquiredStatusField != null;
    }

    private boolean tryClaim(IEntityDao<T> dao, T item) {
        IEntityModel entityModel = item.orm_entityModel();
        if (entityModel == null)
            return false;

        Map<String, Object> oldValues = currentAcquireValues(item);

        T example = dao.newEntity();
        T updated = dao.newEntity();
        OrmEntityHelper.setId(entityModel, example, item.get_id());
        OrmEntityHelper.setId(entityModel, updated, item.get_id());
        // 修改前的占用字段值（非null项）作为更新条件，实现乐观抢占
        for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
            example.orm_propValueByName(entry.getKey(), entry.getValue());
        }
        applyAcquireValues(updated);

        return ormTemplate.runInSession(session -> session.updateByExample(example, updated)) == 1L;
    }

    private Map<String, Object> currentAcquireValues(IOrmEntity item) {
        Map<String, Object> ret = new HashMap<>();
        if (acquireHostField != null) {
            Object v = item.orm_propValueByName(acquireHostField);
            if (v != null)
                ret.put(acquireHostField, v);
        }
        if (acquiredTimeField != null) {
            Object v = item.orm_propValueByName(acquiredTimeField);
            if (v != null)
                ret.put(acquiredTimeField, v);
        }
        if (acquiredStatusField != null) {
            Object v = item.orm_propValueByName(acquiredStatusField);
            if (v != null)
                ret.put(acquiredStatusField, v);
        }
        return ret;
    }

    private void applyAcquireValues(IOrmEntity item) {
        if (acquireHostField != null) {
            item.orm_propValueByName(acquireHostField, AppConfig.hostId());
        }
        if (acquiredTimeField != null) {
            item.orm_propValueByName(acquiredTimeField, CoreMetrics.currentTimestamp());
        }
        if (acquiredStatusField != null) {
            item.orm_propValueByName(acquiredStatusField, acquiredStatus);
        }
    }
}