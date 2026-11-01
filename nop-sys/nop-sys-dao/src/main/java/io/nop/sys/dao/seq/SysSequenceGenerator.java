/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.seq;

import io.nop.api.core.annotations.ioc.IgnoreDepends;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.DelegateContext;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.IContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.DaoErrors;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.seq.SnowflakeSequenceGenerator;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.NopSysDaoConstants;
import io.nop.sys.dao.NopSysErrors;
import io.nop.sys.dao.entity.NopSysSequence;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.api.core.ApiConfigs.CFG_HOST_ID;
import static io.nop.sys.dao.NopSysDaoConfigs.CFG_SYS_INIT_DEFAULT_SEQUENCE;
import static io.nop.sys.dao.NopSysDaoConstants.SEQ_DEFAULT;

public class SysSequenceGenerator implements ISequenceGenerator {
    static final Logger LOG = LoggerFactory.getLogger(SysSequenceGenerator.class);

    private IOrmTemplate ormTemplate;
    private ITransactionTemplate transactionTemplate;

    private long workerId = 0L;

    private long defaultSeqInitNextValue = 1L;

    /**
     * 取号 DB 环节超时上限（毫秒，MA-05）：锁内 REQUIRES_NEW 往返（含 session.lock 行锁等待）
     * 不再依赖全局 10 分钟语句上限兜底——经上下文 callExpireTime 传导到每个语句的
     * queryTimeout（JdbcHelper 取两者最小值），超时按取号失败抛出，禁静默重试空转。
     */
    private long syncDbTimeout = 10000;

    @InjectValue("@cfg:nop.sys.seq.sync-db-timeout|10000")
    public void setSyncDbTimeout(long syncDbTimeout) {
        this.syncDbTimeout = syncDbTimeout;
    }

    private ISequenceGenerator snowflakeGenerator;
    private final Map<String, SeqItem> cache = new ConcurrentHashMap<>();

    private final LocalCache<String, SeqItem> defaultCache = LocalCache.newCache("default-seq-cache",
            CacheConfig.newConfig(500, 60 * 1000L));

    static class SeqItem {
        String name; // 对象类型, 如果没有找到匹配的对象类型，则使用default类型
        String dbSeq; // 是否有对应的数据库sequence, 如果有则使用数据库sequence配置，而忽略nextValue设置
        // DB行装载名（MA-05）：SEQ_DEFAULT回退项按seqName独立建item后，取值流仍来自SEQ_DEFAULT行，
        // loadName指向真正的DB装载行；普通item恒等于name
        String loadName;
        int cacheSize; // 每次获取的sequence值是否代表着一批可以使用的值。例如cacheSize=100,
        // 获取到nextValue=1000之后表示1000-1100的值都已获取。
        int stepSize; // nextValue的步长，缺省为1，下次获取则
        long usedCount; // 当cacheSize大于0时，这里的值表示已经使用了多少缓存的nextValue, 当usedCount
        // >= cacheSize时，需要获取新的nextValue
        long nextValue; // 下一个可用的sequence值
        boolean useUuid; // 是否使用UUID来生成随机id

        boolean snowflake;

        SeqItem(NopSysSequence seq) {
            this.name = seq.getSeqName();
            this.loadName = this.name;
            this.useUuid = StringHelper.isYes(seq.getIsUuid());
            this.update(seq);
        }

        SeqItem(String name) {
            this.name = name;
            this.loadName = name;
            this.useUuid = true;
        }

        /**
         * SEQ_DEFAULT 回退项的配置快照（MA-05）：锁对象独立但取值流同源——cacheSize 置 0
         * 强制首次取号走 DB 装载（自 SEQ_DEFAULT 行带行锁取段，值流无快照碰撞），
         * loadName 继承源 item 的 DB 装载行。
         */
        void copyConfigFrom(SeqItem other) {
            this.dbSeq = other.dbSeq;
            this.useUuid = other.useUuid;
            this.snowflake = other.snowflake;
            this.stepSize = other.stepSize;
            this.loadName = other.loadName;
            this.cacheSize = 0;
            this.usedCount = 0;
            this.nextValue = 0;
        }

        public void update(NopSysSequence seq) {
            this.useUuid = StringHelper.isYes(seq.getIsUuid());
            this.snowflake = NopSysDaoConstants.SEQ_TYPE_SNOWFLAKE.equals(seq.getSeqType());
            this.cacheSize = 0;
            this.stepSize = seq.getStepSize() != null ? seq.getStepSize() : 1;
            if (this.stepSize <= 0) {
                this.stepSize = 1;
            }
            this.nextValue = seq.getNextValue() == null ? 0 : seq.getNextValue();
        }
    }

    public void clearCache() {
        cache.clear();
        // defaultCache同步失效（MA-05/C1-007）：回退项按seqName独立后，管理端回拨nextValue
        // 必须同时穿透回退项缓存，否则最长TTL窗口内对回退序列名不生效
        defaultCache.clear();
    }

    public void removeCache(String cacheKey) {
        cache.remove(cacheKey);
        defaultCache.remove(cacheKey);
    }

    @InjectValue("@cfg:nop.sys.seq.snowflake-worker-id|0")
    public void setSnowflakeWorkerId(long snowflakeWorkerId) {
        this.workerId = snowflakeWorkerId;
    }

    @InjectValue("@cfg:nop.sys.seq.default-seq-init-next-value|1")
    public void setDefaultSeqInitNextValue(long defaultSeqInitNextValue) {
        this.defaultSeqInitNextValue = defaultSeqInitNextValue;
    }

    @Inject
    @IgnoreDepends
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    @IgnoreDepends
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void afterPropertiesSet() {
        // 如果没有设置workerId，则尝试根据唯一指定的hostId来动态生成
        if (workerId == 0) {
            String hostId = CFG_HOST_ID.get();
            if (StringHelper.isEmpty(hostId))
                hostId = NetHelper.findLocalIp();
            workerId = Math.abs(HashHelper.murmur3_32(hostId) % 1024);
            // 哈希到1024槽位存在碰撞概率（约24节点时>20%）：同workerId的节点同毫秒同序号会生成
            // 完全相同的ID。多节点部署必须显式配置nop.sys.seq.snowflake-worker-id
            LOG.warn("nop.sys.seq.snowflake-worker-id-derived-from-host:workerId={},hostId={}", workerId, hostId);
        }
        this.snowflakeGenerator = new SnowflakeSequenceGenerator(workerId);
    }

    public void lazyInit() {
        if (!CFG_SYS_INIT_DEFAULT_SEQUENCE.get())
            return;

        if (BaseTestCase.isTestRunning()) {
            BaseTestCase.addLazyAction(this::addDefaultSequence);
        } else {
            addDefaultSequence();
        }
    }

    public void addDefaultSequence() {
        ContextProvider.runWithTenant("0", () -> {
            SQL sql = SQL.begin().sql("select o.id from ").sql(NopSysSequence.class.getName())
                    .sql(" o").end();
            boolean exists = ormTemplate.exists(sql);
            if (!exists) {
                // 如果sequence表为空，则初始化一条缺省记录
                try {
                    runLocal(session -> {
                        NopSysSequence entity = (NopSysSequence) ormTemplate.newEntity(NopSysSequence.class.getName());
                        entity.setCacheSize(100);
                        entity.setStepSize(1);
                        entity.setNextValue(defaultSeqInitNextValue);
                        entity.setSeqType("seq");
                        entity.setSeqName(SEQ_DEFAULT);
                        session.save(entity);
                        return null;
                    });
                } catch (NopException e) {
                    // 如果多个应用同时执行初始化操作，则可能出现键值冲突异常
                    if (!DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(e.getErrorCode())) {
                        LOG.warn("nop.err.sys.init-default-sequence-fail", e);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public long generateLong(String seqName, boolean useDefault) {
        SeqItem item = this.findSeqItem(seqName, useDefault);
        synchronized (item) {
            if (item.snowflake) {
                return snowflakeGenerator.generateLong(seqName, useDefault);
            }
            if (item.useUuid)
                return MathHelper.randomPositiveLong();

            if (item.cacheSize > 0 && item.usedCount < item.cacheSize) {
                long value = item.nextValue;
                item.usedCount++;
                item.nextValue += item.stepSize;
                return value;
            }

            return syncFromDb(item);
        }
    }

    @Override
    public String generateString(String seqName, boolean useDefault) {
        // 基本处理逻辑:
        // 1. 从数据库装载配置到SeqItem中
        // 2. 从SeqItem中获取缓存的nextValue值
        // 3. 如果没有缓存的值，则从数据库中重新装载SeqItem, 获取nextValue,
        // 并更新数据库中的nextValue配置
        SeqItem item = this.findSeqItem(seqName, useDefault);
        synchronized (item) {
            if (item.snowflake)
                return snowflakeGenerator.generateString(seqName, useDefault);

            if (item.useUuid) {
                return StringHelper.generateUUID();
            }

            if (item.cacheSize > 0 && item.usedCount < item.cacheSize) {
                long value = item.nextValue;
                item.usedCount++;
                item.nextValue += item.stepSize;
                return String.valueOf(value);
            }

            return String.valueOf(syncFromDb(item));
        }
    }

    long syncFromDb(SeqItem item) {
        try {
            return doSyncFromDb(item);
        } catch (RuntimeException e) {
            // 序列行被并发删除时懒加载代理抛出的是通用ORM错误（如lock-entity-fail），
            // 确认行确实缺失时转译为语义化错误码便于排障；其他异常（含DB环节超时）原样抛出
            if (loadCacheItemFromDb(item.loadName) == null)
                throw new NopException(NopSysErrors.ERR_SYS_NO_SEQ)
                        .param(NopSysErrors.ARG_SEQ_NAME, item.name);
            throw e;
        }
    }

    private long doSyncFromDb(SeqItem item) {
        return runLocal(session -> {
            NopSysSequence seq = (NopSysSequence) session.load(NopSysSequence.class.getName(), item.loadName);
            session.lock(seq);

            item.update(seq);

            // CACHE_SIZE列无mandatory/defaultValue，管理端可不填；null拆箱会使该序列完全不可用
            Integer cacheSize = seq.getCacheSize();
            item.cacheSize = cacheSize == null ? 0 : cacheSize;
            item.useUuid = StringHelper.isYes(seq.getIsUuid());

            long ret = seq.getNextValue();

            long next = item.nextValue + (item.cacheSize > 0 ? (long) item.cacheSize * item.stepSize : item.stepSize);
            seq.setNextValue(next);

            LOG.debug("dao.next_sequence:name={},value={}", item.name, next);

            item.usedCount = 1;
            item.nextValue += item.stepSize;
            return ret;
        });
    }

    SeqItem findSeqItem(String seqName, boolean useDefault) {
        SeqItem item = cache.get(seqName);
        if (item != null)
            return item;
        synchronized (this) {
            item = cache.get(seqName);
            if (item != null)
                return item;

            item = defaultCache.get(seqName);
            if (item != null)
                return item;
            NopSysSequence seq = loadCacheItemFromDb(seqName);
            if (seq == null) {
                if (useDefault && !SEQ_DEFAULT.equals(seqName)) {
                    SeqItem defaultItem = findSeqItem(SEQ_DEFAULT, false);
                    // 回退项按seqName各建独立item（MA-05/C2-005）：不再共享SEQ_DEFAULT锁对象——
                    // 否则synchronized(item)内REQUIRES_NEW DB往返（含session.lock行锁等待）使
                    // 一个冷门序列的DB慢拖住全部默认序列取号。配置快照同源、取值流仍来自
                    // SEQ_DEFAULT行（copyConfigFrom强制首次DB装载），锁对象独立
                    SeqItem fallback = new SeqItem(seqName);
                    fallback.copyConfigFrom(defaultItem);
                    // 回退结果按名缓存，避免未知序列名每次取号都查一次DB（defaultCache带TTL）
                    defaultCache.put(seqName, fallback);
                    return fallback;
                }

                item = new SeqItem(seqName);
                defaultCache.put(seqName, item);
                return item;
            }
            item = new SeqItem(seq);
            LOG.debug("dao.load_item_from_db:next={}", item.nextValue);
            cache.put(seqName, item);
        }
        return item;
    }

    protected NopSysSequence loadCacheItemFromDb(String seqName) {
        NopSysSequence seq = runLocal(session -> {
            NopSysSequence example = new NopSysSequence();
            example.setSeqName(seqName);
            return session.findFirstByExample(example);
        });
        return seq;
    }

    protected <T> T runLocal(Function<IOrmSession, T> task) {
        return ormTemplate.runInNewSession(session -> {
            return transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn -> {
                // 取号DB环节超时上限（MA-05）：以代理上下文携带callExpireTime预算——语句超时取
                // 其与全局上限的最小值（JdbcHelper.getQueryTimeout），超时抛NopTimeoutException
                // （取号失败显式化）。attach/restore严格配对，不污染环境上下文
                IContextProvider provider = ContextProvider.instance();
                IContext prev = provider.currentContext();
                // 无环境上下文时代理到未附加的空上下文（DelegateContext不支持null delegate）
                IContext base = prev != null ? prev : ContextProvider.newContext(false);
                IContext bounded = new BoundedCallContext(base, CoreMetrics.currentTimeMillis() + syncDbTimeout);
                provider.attachContext(bounded);
                try {
                    return task.apply(session);
                } finally {
                    provider.attachContext(prev);
                }
            });
        });
    }

    /** 仅覆盖callExpireTime的代理上下文（其余语义透传环境上下文，可为null时按空上下文兜底）。 */
    private static final class BoundedCallContext extends DelegateContext {
        private final long expireTime;

        BoundedCallContext(IContext delegate, long expireTime) {
            super(delegate);
            this.expireTime = expireTime;
        }

        @Override
        public long getCallExpireTime() {
            return expireTime;
        }
    }
}
