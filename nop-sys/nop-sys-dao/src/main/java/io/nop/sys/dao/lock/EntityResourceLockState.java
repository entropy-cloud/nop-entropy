package io.nop.sys.dao.lock;

import io.nop.commons.concurrent.lock.IResourceLockState;
import io.nop.sys.dao.entity.NopSysLock;

import java.sql.Timestamp;

public class EntityResourceLockState implements IResourceLockState {
    private final NopSysLock entity;

    public EntityResourceLockState(NopSysLock entity) {
        this.entity = entity;
    }

    public NopSysLock getEntity() {
        return entity;
    }

    @Override
    public String getResourceId() {
        return entity.getLockGroup() + "/" + entity.getLockName();
    }

    @Override
    public String getLockerId() {
        return entity.getHolderId();
    }

    @Override
    public long getLockTime() {
        Timestamp time = entity.getLockTime();
        return time == null ? -1 : time.getTime();
    }

    @Override
    public long getExpireTime() {
        Timestamp time = entity.getExpireAt();
        return time == null ? -1 : time.getTime();
    }

    @Override
    public long getCreateTime() {
        Timestamp time = entity.getCreateTime();
        return time == null ? -1 : time.getTime();
    }

    @Override
    public String getLockReason() {
        return entity.getLockReason();
    }

    @Override
    public int getVersion() {
        return entity.getVersion();
    }
}
