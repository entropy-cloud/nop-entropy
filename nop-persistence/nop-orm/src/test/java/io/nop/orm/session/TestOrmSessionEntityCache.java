/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.app.SimsCollege;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestOrmSessionEntityCache extends AbstractOrmTestCase {

    /**
     * 遍历租户缓存过程中新建了其他租户的缓存，不允许抛ConcurrentModificationException
     */
    @Test
    public void testForEachDirtyNewTenantDuringIteration() {
        orm().runInSession(session -> {
            IOrmSessionImplementor impl = (IOrmSessionImplementor) session;
            TenantOrmSessionEntityCache cache = new TenantOrmSessionEntityCache(impl);
            // 预置两个租户缓存，保证外层迭代器在processor执行后仍需继续前进，CME必然复现
            cache.add(newExtField(impl, "1", "123"));
            cache.add(newExtField(impl, "2", "200"));

            List<IOrmEntity> visited = new ArrayList<>();
            // processor中保存了尚未建缓存租户的新实体，会新建租户缓存条目
            cache.forEachDirty(entity -> {
                visited.add(entity);
                cache.add(newExtField(impl, "3", "999"));
            });
            assertEquals(2, visited.size());
            return null;
        });
    }

    /**
     * visiting状态下调用removeAll不允许直接clear正在被遍历的主缓存
     */
    @Test
    public void testRemoveAllDuringVisiting() {
        orm().runInSession(session -> {
            IOrmSessionImplementor impl = (IOrmSessionImplementor) session;
            OrmSessionEntityCache cache = new OrmSessionEntityCache(impl);
            IOrmEntity a = newExtField(impl, "1", "123");
            IOrmEntity b = newExtField(impl, "2", "123");
            cache.add(a);
            cache.add(b);

            List<IOrmEntity> visited = new ArrayList<>();
            cache.forEachCurrent(entity -> {
                visited.add(entity);
                // 遍历中evict所有同类型实体，修复前直接clear正在迭代的LinkedHashMap导致CME
                cache.removeAll(SimsCollege.class.getName());
                cache.removeAll(a.orm_entityName());
            });
            assertEquals(2, visited.size());

            // 遍历结束后统一清除
            assertFalse(cache.contains(a));
            assertFalse(cache.contains(b));
            return null;
        });
    }

    private IOrmEntity newExtField(IOrmSessionImplementor session, String entityId, String tenantId) {
        IOrmEntity entity = session.newEntity("io.nop.app.SimsExtField");
        entity.orm_propValueByName("entityName", "TestOrmSessionEntityCache");
        entity.orm_propValueByName("entityId", entityId);
        entity.orm_propValueByName("fieldName", "f");
        entity.orm_propValueByName("nopTenantId", tenantId);
        return entity;
    }
}
