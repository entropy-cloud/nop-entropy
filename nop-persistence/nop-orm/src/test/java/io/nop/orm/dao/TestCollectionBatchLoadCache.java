/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.app.SimsClass;
import io.nop.api.core.context.ContextProvider;
import io.nop.app.SimsCollege;
import io.nop.commons.cache.ICache;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.factory.SessionFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归覆盖审查报告 ORM-04：集合批量加载（CollectionPersisterImpl.batchLoadCollectionAsync）完成时，
 * 集合全局缓存必须已更新——与实体路径EntityPersisterImpl.batchLoadAsync:174的修复契约保持一致。
 * 缓存更新必须链接进返回的future链，而不是独立的thenRun旁路。
 */
public class TestCollectionBatchLoadCache extends AbstractOrmTestCase {

    private static final String COLLECTION_NAME = "io.nop.app.SimsCollege@cachedClasses";

    @Test
    public void testBatchLoadCollectionFillsGlobalCacheWhenFutureCompletes() {
        orm().runInSession(session -> {
            SimsCollege college = (SimsCollege) session.get(SimsCollege.class.getName(), "1");
            IOrmEntitySet<SimsClass> coll = (IOrmEntitySet<SimsClass>) college.prop_get("cachedClasses");
            assertTrue(coll.orm_proxy(), "集合初始应为proxy状态");

            session.getBatchLoadQueue().enqueue(coll);
            session.getBatchLoadQueue().flush();

            // flush返回后（返回的future已全部完成），集合全局缓存必须已写入元素id列表
            ICache<String, Object> cache = ((SessionFactoryImpl) sessionFactory).getGlobalCache(COLLECTION_NAME);
            // SimsClass为租户实体，useTenantCache=true时缓存key=tenantId:ownerId
            String cacheKey = ContextProvider.currentTenantId() + ":" + college.orm_idString();
            assertNotNull(cache.get(cacheKey),
                    "batchLoadCollectionAsync返回future完成后，集合全局缓存应已按owner写入元素列表");
            return null;
        });
    }
}
