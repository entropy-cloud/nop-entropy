/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.lock;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.time.IEstimatedClock;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.lock.IResourceLock;
import io.nop.commons.concurrent.lock.IResourceLockManager;
import io.nop.commons.concurrent.lock.IResourceLockState;
import io.nop.commons.concurrent.lock.impl.IResourceLockManagerImplementor;
import io.nop.commons.concurrent.lock.impl.ResourceLock;
import io.nop.commons.io.net.IServerAddrFinder;
import io.nop.commons.util.NetHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.OrmConstants;
import io.nop.orm.dao.AbstractDaoHandler;
import io.nop.orm.support.OrmCompositePk;
import io.nop.sys.dao.entity.NopSysLock;
import io.nop.sys.dao.entity._gen.NopSysLockPkBuilder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

import static io.nop.sys.dao.NopSysDaoConstants.RESOURCE_GROUP_DEFAULT;

public class SysDaoResourceLockManager extends AbstractDaoHandler implements IResourceLockManager, IResourceLockManagerImplementor {
    static final Logger LOG = LoggerFactory.getLogger(SysDaoResourceLockManager.class);

    private long defaultWaitTime = 1000; // 1s
    private long defaultLeaseTime = 10000; // 10s
    private IServerAddrFinder addrFinder;

    public void setDefaultWaitTime(long defaultWaitTime) {
        this.defaultWaitTime = defaultWaitTime;
    }

    public void setDefaultLeaseTime(long defaultLeaseTime) {
        this.defaultLeaseTime = defaultLeaseTime;
    }

    @Inject
    public void setAddrFinder(@Nullable IServerAddrFinder addrFinder) {
        this.addrFinder = addrFinder;
    }

    @Override
    public long getDefaultWaitTime() {
        return defaultWaitTime;
    }

    @Override
    public long getDefaultLeaseTime() {
        return defaultLeaseTime;
    }

    @Override
    public IResourceLock getLock(@Nonnull String resourceId, String holderId) {
        return new ResourceLock(this, resourceId, holderId, getDefaultWaitTime(), getDefaultLeaseTime());
    }

    OrmCompositePk castId(String resourceId) {
        int pos = resourceId.indexOf('/');
        String resourceGroup, resourceName;
        if (pos >= 0) {
            resourceGroup = resourceId.substring(0, pos);
            resourceName = resourceId.substring(pos + 1);
            if (StringHelper.isEmpty(resourceGroup))
                resourceGroup = RESOURCE_GROUP_DEFAULT;
        } else {
            resourceGroup = RESOURCE_GROUP_DEFAULT;
            resourceName = resourceId;
        }

        NopSysLockPkBuilder builder = new NopSysLockPkBuilder();
        builder.setLockName(resourceName);
        builder.setLockGroup(resourceGroup);
        return builder.build();
    }

    @Override
    public IResourceLockState getLockState(@Nonnull String resourceId) {
        OrmCompositePk pk = castId(resourceId);
        NopSysLock lock = runInNewSession(session -> {
            return (NopSysLock) session.get(NopSysLock.class.getName(), pk);
        });

        if (lock == null)
            return null;
        return new EntityResourceLockState(lock);
    }

    @Override
    public boolean forceUnlock(String resourceId) {
        OrmCompositePk pk = castId(resourceId);

        return orm().deleteById(NopSysLock.class.getName(), pk) > 0;
    }

    @Override
    public IResourceLockState tryLockWithLease(String resourceId, String lockerId,
                                               long waitTime, long leaseTime, String lockReason) {
        IEstimatedClock clock = orm().getDbEstimatedClock(null);

        OrmCompositePk pk = castId(resourceId);

        long beginTime = CoreMetrics.currentTimeMillis();

        try {
            NopSysLock entity = saveNew(resourceId, lockerId, leaseTime, lockReason, clock.getMaxCurrentTimeMillis());
            return new EntityResourceLockState(entity);
        } catch (Exception e) {
            // ignore data base error
            LOG.trace("nop.lock.sys.save-lock-failed:resourceId={}", resourceId, e);
        }
        do {
            long leftWait = waitTime - (CoreMetrics.currentTimeMillis() - beginTime);
            if (leftWait <= 0) {
                LOG.debug("nop.lock.try-lock-fail:resourceId={},lockerId={},waitTime={}", resourceId, lockerId, waitTime);
                return null;
            }

            try {
                NopSysLock entity = saveNew(resourceId, lockerId, leaseTime, lockReason, clock.getMaxCurrentTimeMillis());
                return new EntityResourceLockState(entity);
            } catch (Exception e) {
                // ignore data base error
                LOG.trace("nop.lock.sys.save-lock-failed:resourceId={}", resourceId, e);
            }

            NopSysLock entity = runInNewSession(session -> {
                NopSysLock existing = (NopSysLock) session.get(NopSysLock.class.getName(), pk);
                if (existing != null) {
                    // 如果已过期，则尝试删除
                    if (isExpired(existing, clock)) {
                        if (orm().tryDelete(existing))
                            return null;
                    }
                }
                return existing;
            });

            if (entity != null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e);
                }
            } else {
                // 如果数据库记录已经被删除，则重试锁定
                try {
                    entity = saveNew(resourceId, lockerId, leaseTime, lockReason, clock.getMaxCurrentTimeMillis());
                    return new EntityResourceLockState(entity);
                } catch (Exception e) {
                    // ignore data base error
                    LOG.trace("nop.lock.sys.save-lock-failed:resourceId={}", resourceId, e);
                }
            }
        } while (true);
    }

    protected boolean isExpired(NopSysLock entity, IEstimatedClock clock) {
        return entity.getExpireAt().getTime() >= clock.getMaxCurrentTimeMillis();
    }

    NopSysLock saveNew(String resourceId, String lockId, long leaseTime, String lockReason, long currentTime) {
        Guard.notEmpty(lockId, "lockerId");
        Guard.notEmpty(resourceId, "resourceId");

        OrmCompositePk pk = castId(resourceId);

        return runLocal(session -> {
            NopSysLock entity = (NopSysLock) session.newEntity(NopSysLock.class.getName());
            entity.setLockGroup((String) pk.getByPropName(NopSysLock.PROP_NAME_lockGroup));
            entity.setLockName((String) pk.getByPropName(NopSysLock.PROP_NAME_lockName));
            entity.setAppId(AppConfig.appName());
            String addr = addrFinder == null ? NetHelper.findLocalIp() : addrFinder.findAddr();
            entity.setHolderAdder(addr);
            entity.setHolderId(lockId);
            entity.setLockReason(lockReason);
            entity.setLockTime(new Timestamp(currentTime));
            entity.setExpireAt(new Timestamp(currentTime + leaseTime));
            session.saveDirectly(entity);
            return entity;
        });
    }

    @Override
    public boolean tryResetLease(IResourceLockState lock, long leaseTime) {
        NopSysLock entity = ((EntityResourceLockState) lock).getEntity();

        return runLocal(session -> {
            IEstimatedClock clock = orm().getDbEstimatedClock(null);
            SQL sql = SQL.begin().update(NopSysLock.class.getName())
                    .set()
                    .eq(NopSysLock.PROP_NAME_version, entity.getVersion() + 1)
                    .eq(NopSysLock.PROP_NAME_expireAt, new Timestamp(clock.getMaxCurrentTimeMillis() + leaseTime))
                    .where().eq(OrmConstants.PROP_ID, entity.orm_id())
                    .and().eq(NopSysLock.PROP_NAME_version, entity.getVersion()).end();
            return session.executeUpdate(sql) == 1;
        });
    }

    @Override
    public boolean isHoldingLock(IResourceLockState lock) {
        IResourceLockState state = getLockState(lock.getResourceId());
        if (state == null)
            return false;

        if (!state.getLockerId().equals(lock.getLockerId()))
            return false;

        return state.getVersion() == lock.getVersion();
    }

    @Override
    public void releaseLock(IResourceLockState lock) {
        NopSysLock entity = ((EntityResourceLockState) lock).getEntity();
        runLocal(session -> {
            session.deleteDirectly(entity);
//            SQL sql = SQL.begin().deleteFrom().append(NopSysLock.class.getName())
//                    .where().eq(NopSysLock.PROP_NAME_lockGroup, entity.getLockGroup())
//                    .and().eq(NopSysLock.PROP_NAME_lockName, entity.getLockName())
//                    .and().eq(NopSysLock.PROP_NAME_version, entity.getVersion()).end();
//            ormTemplate.executeUpdate(sql);
            return null;
        });
    }
}
