/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.seq;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.DaoErrors;
import io.nop.dao.seq.ISequenceGenerator;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.sys.dao.entity.NopSysSequence;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.nop.sys.dao.NopSysDaoConfigs.CFG_SYS_INIT_DEFAULT_SEQUENCE;
import static io.nop.sys.dao.NopSysDaoConstants.SEQ_DEFAULT;

public class SysSequenceGenerator implements ISequenceGenerator {
    static final Logger LOG = LoggerFactory.getLogger(SysSequenceGenerator.class);

    private IOrmTemplate ormTemplate;
    private ITransactionTemplate transactionTemplate;

    private final Map<String, SeqItem> cache = new ConcurrentHashMap<>();

    private final LocalCache<String, SeqItem> defaultCache = LocalCache.newCache("default-seq-cache",
            CacheConfig.newConfig(500, 60 * 1000));

    static class SeqItem {
        String name; // 对象类型, 如果没有找到匹配的对象类型，则使用default类型
        String dbSeq; // 是否有对应的数据库sequence, 如果有则使用数据库sequence配置，而忽略nextValue设置
        int cacheSize; // 每次获取的sequence值是否代表着一批可以使用的值。例如cacheSize=100,
        // 获取到nextValue=1000之后表示1000-1100的值都已获取。
        int stepSize; // nextValue的步长，缺省为1，下次获取则
        long usedCount; // 当cacheSize大于0时，这里的值表示已经使用了多少缓存的nextValue, 当usedCount
        // >= cacheSize时，需要获取新的nextValue
        long nextValue; // 下一个可用的sequence值
        boolean useUuid; // 是否使用UUID来生成随机id

        SeqItem(NopSysSequence seq) {
            this.name = seq.getSeqName();
            this.useUuid = StringHelper.isYes(seq.getIsUuid());
            this.update(seq);
        }

        SeqItem(String name) {
            this.name = name;
            this.useUuid = true;
        }

        public void update(NopSysSequence seq) {
            this.useUuid = StringHelper.isYes(seq.getIsUuid());
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
    }

    public void removeCache(String cacheKey) {
        cache.remove(cacheKey);
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
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
                        entity.setNextValue(1L);
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
            if (item.useUuid)
                return MathHelper.secureRandom().nextLong();

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
        return runLocal(session -> {
            NopSysSequence seq = (NopSysSequence) session.load(NopSysSequence.class.getName(), item.name);
            session.lock(seq);

            item.update(seq);

            item.cacheSize = seq.getCacheSize();
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
                    return defaultItem;
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
                return task.apply(session);
            });
        });
    }
}
