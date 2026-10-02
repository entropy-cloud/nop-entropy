package io.nop.sys.dao.lock;

import io.nop.sys.dao.entity.NopSysLock;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EntityResourceLockState 语义：IResourceLockState 视图完全由 NopSysLock 行数据驱动，
 * resourceId=lockGroup/lockName，时间列 null 时约定返回 -1（无锁时间信息）。
 */
public class TestEntityResourceLockState {

    @Test
    public void testResourceIdAndLockerFromLockRow() {
        NopSysLock entity = new NopSysLock();
        entity.setLockGroup("demo");
        entity.setLockName("resource-1");
        entity.setHolderId("holder-a");
        entity.setVersion(3L);

        EntityResourceLockState state = new EntityResourceLockState(entity);
        assertEquals("demo/resource-1", state.getResourceId(),
                "资源标识必须是 lockGroup/lockName 复合键");
        assertEquals("holder-a", state.getLockerId());
        assertEquals(3L, state.getVersion());
        assertEquals(entity, state.getEntity());
    }

    @Test
    public void testTimestampsReturnMillisOrMinusOneWhenAbsent() {
        NopSysLock entity = new NopSysLock();
        EntityResourceLockState empty = new EntityResourceLockState(entity);
        assertEquals(-1L, empty.getLockTime(), "锁时间为空时必须返回 -1");
        assertEquals(-1L, empty.getExpireTime(), "过期时间为空时必须返回 -1");
        assertEquals(-1L, empty.getCreateTime(), "创建时间为空时必须返回 -1");

        Timestamp lockTime = new Timestamp(1700000000000L);
        Timestamp expireAt = new Timestamp(1700000060000L);
        Timestamp createTime = new Timestamp(1699999999999L);
        entity.setLockTime(lockTime);
        entity.setExpireAt(expireAt);
        entity.setCreateTime(createTime);

        EntityResourceLockState state = new EntityResourceLockState(entity);
        assertEquals(1700000000000L, state.getLockTime());
        assertEquals(1700000060000L, state.getExpireTime());
        assertEquals(1699999999999L, state.getCreateTime());
    }
}
