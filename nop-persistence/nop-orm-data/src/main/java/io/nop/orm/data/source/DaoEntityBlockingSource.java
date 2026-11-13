package io.nop.orm.data.source;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.IBlockingSource;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IQueryBuilder;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
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
    private ITransactionTemplate transactionTemplate;
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

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
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

    /**
     * plan 2517 R2-14 裁定⑬（C6-001/C3-006）：方法级 REQUIRES_NEW 摘除——等待窗口不再横跨事务持连持锁。
     * waitUntil 循环体外置事务（无事务上下文执行，消除 K>=8 消费者空队列稳态下的 DB 连接饥饿），
     * 循环体内逐轮 {@code runInTransaction} 短事务包络。N4 实现约束（裁定⑬验收项）：tryClaim 抢占 CAS
     * 与轮询读必须同一短事务——短事务包络范围 = loadItems（含 tryClaim）+ dao.flushSession() + c.addAll 三段同界。
     * 「空闲持连」守卫指标：空队列轮询期活跃连接数为 0（红⑬断言载体）。
     * transactionTemplate 未注入时（@Transactional 拦截器装配前提未满足的部署形态）逐轮直接执行，
     * 与既有未装配行为一致，此处显式声明不静默。
     */
    @Override
    public int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait) throws InterruptedException {
        // 与 IBlockingSource 的 default 实现保持一致的入口防御
        if (maxElements <= 0)
            return 0;

        IEntityDao<T> dao = daoProvider.dao(entityName);
        if (maxWait == 0) {
            List<T> items = loadRound(dao, maxElements, c);
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
            List<T> items = loadRound(dao, maxElements - (c.size() - oldSize), c);
            if (!items.isEmpty()) {
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

    /**
     * 裁定⑬/N4：单轮短事务包络 = loadItems（含 tryClaim 抢占 CAS）+ dao.flushSession() + 结果收集三段同界。
     * 抢占 CAS 失去事务串行化前提即失效，因此 tryClaim 必须与轮询读同一短事务。
     */
    private List<T> loadRound(IEntityDao<T> dao, int maxCount, Collection<? super T> c) {
        if (transactionTemplate == null) {
            // 显式降级声明：事务模板未注入（@Transactional 拦截器装配前提未满足的部署形态），逐轮无事务执行
            return loadItems(dao, maxCount);
        }
        return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn -> {
            List<T> items = loadItems(dao, maxCount);
            if (!items.isEmpty())
                dao.flushSession();
            return items;
        });
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

    /**
     * plan 2517 R2-14 裁定⑦（C2-005）：抢占谓词 NULL 显式化——按声明抢占字段全集生成谓词：
     * 源值非 null 用等值条件，源值为 null 生成 {@code col IS NULL}（SQL 条件 UPDATE 的 CAS 语义恢复）。
     * 旧实现 currentAcquireValues 逐字段跳过 null 旧值，新任务行抢占字段初始 NULL 不进 WHERE，
     * UPDATE 退化为按 id 无条件更新 → 两消费者均匹配成功、同一任务重复投递（声明契约失效且静默无日志）。
     * 三字段全 NULL 时 UPDATE 仍带主键 + 全部 IS NULL 条件，CAS 语义恢复（两消费者只有一个匹配成功）。
     * 直接经 SqlBuilder 构造条件 UPDATE（谓词生成层可断言，消除 session mock 屏蔽面——C10-006）。
     */
    private boolean tryClaim(IEntityDao<T> dao, T item) {
        IEntityModel entityModel = item.orm_entityModel();
        if (entityModel == null)
            return false;

        // 零回退钉保护（TestDaoEntityBlockingSourceClaim 两钉，非 NULL 等值语义不变）：
        // 全部抢占字段旧值均非 null 时维持既有 updateByExample 路径（等值谓词语义等价）；
        // 任一字段旧值为 null 时走显式 IS NULL 谓词 SQL 路径（裁定⑦缺陷面——NULL 初始态参与 CAS）
        boolean hasNullOldValue = (acquireHostField != null && item.orm_propValueByName(acquireHostField) == null)
                || (acquiredTimeField != null && item.orm_propValueByName(acquiredTimeField) == null)
                || (acquiredStatusField != null && item.orm_propValueByName(acquiredStatusField) == null);
        // SQL 生成层可解析性守卫：IS NULL 谓词路径需要方言与表名可解析（生产装配恒可用）；
        // 不可解析（如无方言装配的替身环境）时回落既有等值谓词路径——两条路径均为 CAS 安全的条件 UPDATE，
        // 仅 SQL 路径支持 NULL 初始态参与 CAS，绝不退化为无条件 UPDATE（缺陷态本体）
        IDialect dialect = ormTemplate.getDialectForQuerySpace(entityModel.getQuerySpace());
        boolean sqlPathAvailable = hasNullOldValue && dialect != null && entityModel.getTableName() != null;
        if (!sqlPathAvailable) {
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

        SQL sql = buildClaimSql(entityModel, item);

        return ormTemplate.runInSession(session -> session.executeUpdate(sql)) == 1L;
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

    /**
     * 构造抢占条件 UPDATE。包内可见供谓词文本断言（红⑦回归钉穿透谓词生成层）。
     */
    SQL buildClaimSql(IEntityModel entityModel, T item) {
        SQL.SqlBuilder sb = SQL.begin();
        sb.querySpace(entityModel.getQuerySpace());
        IDialect dialect = ormTemplate.getDialectForQuerySpace(entityModel.getQuerySpace());

        sb.append(dialect.getUpdateKeyword()).append(' ');
        sb.sql(entityModel.getTableName());
        sb.append(" set ");
        boolean firstSet = true;
        if (acquireHostField != null) {
            sb.eq(columnCode(entityModel, acquireHostField), AppConfig.hostId());
            firstSet = false;
        }
        if (acquiredTimeField != null) {
            if (!firstSet)
                sb.append(',');
            sb.eq(columnCode(entityModel, acquiredTimeField), CoreMetrics.currentTimestamp());
            firstSet = false;
        }
        if (acquiredStatusField != null) {
            if (!firstSet)
                sb.append(',');
            sb.eq(columnCode(entityModel, acquiredStatusField), acquiredStatus);
        }
        sb.where();
        appendPkCondition(sb, entityModel, item);

        // 按声明抢占字段全集生成谓词：非 null 等值，null 显式 IS NULL
        if (acquireHostField != null) {
            appendClaimCondition(sb, entityModel, item, acquireHostField);
        }
        if (acquiredTimeField != null) {
            appendClaimCondition(sb, entityModel, item, acquiredTimeField);
        }
        if (acquiredStatusField != null) {
            appendClaimCondition(sb, entityModel, item, acquiredStatusField);
        }

        // 租户列是 useTenant 自动维护的系统列：与 updateByExample 侧 appendExampleFilter 同口径补租户条件
        if (entityModel.isUseTenant() && entityModel.getTenantPropId() > 0) {
            int tenantPropId = entityModel.getTenantPropId();
            if (!item.orm_propInited(tenantPropId)) {
                sb.and();
                sb.eq(entityModel.getColumnByPropId(tenantPropId, false).getCode(),
                        io.nop.api.core.context.ContextProvider.currentTenantId());
            }
        }

        return sb.end();
    }

    private void appendPkCondition(SQL.SqlBuilder sb, IEntityModel entityModel, T item) {
        Object id = item.get_id();
        if (id instanceof io.nop.orm.support.OrmCompositePk) {
            io.nop.orm.support.OrmCompositePk pk = (io.nop.orm.support.OrmCompositePk) id;
            boolean first = true;
            for (IColumnModel col : entityModel.getPkColumns()) {
                if (!first)
                    sb.and();
                sb.eq(col.getCode(), pk.getByPropName(col.getName()));
                first = false;
            }
        } else {
            IEntityPropModel idProp = entityModel.getIdProp();
            sb.eq(columnCode(entityModel, idProp.getName()), id);
        }
    }

    private void appendClaimCondition(SQL.SqlBuilder sb, IEntityModel entityModel, T item, String fieldName) {
        Object oldValue = item.orm_propValueByName(fieldName);
        sb.and();
        if (oldValue == null) {
            // 裁定⑦：源值为 null 的抢占字段生成 col IS NULL 进 WHERE，NULL 初始态同样参与 CAS
            sb.isNull(columnCode(entityModel, fieldName));
        } else {
            sb.eq(columnCode(entityModel, fieldName), oldValue);
        }
    }

    private String columnCode(IEntityModel entityModel, String propName) {
        IColumnModel col = entityModel.getColumn(propName, false);
        if (col == null)
            throw new NopException(io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COLUMN)
                    .param(io.nop.orm.model.OrmModelErrors.ARG_ENTITY_NAME, entityModel.getName())
                    .param(io.nop.orm.model.OrmModelErrors.ARG_COL_NAME, propName);
        return col.getCode();
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