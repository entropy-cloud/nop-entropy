/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.lazy;

import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLazyLoadOrmModel {

    private static IEntityModel dynModel(String name, String tableName) {
        IEntityModel model = mock(IEntityModel.class);
        lenient().when(model.getName()).thenReturn(name);
        lenient().when(model.getTableName()).thenReturn(tableName);
        lenient().when(model.getShortName()).thenReturn(name);
        lenient().when(model.isRegisterShortName()).thenReturn(false);
        return model;
    }

    @Test
    public void testEntityModelByTableNameCaseInsensitive() {
        LazyLoadOrmModel model = new LazyLoadOrmModel(null, (name, m) -> null);
        model.addEntityModel(dynModel("DynA", "DYN_TABLE"));

        // 与静态 OrmModel 的 CaseInsensitiveMap 索引保持一致；修复前大小写敏感返回 null
        assertNotNull(model.getEntityModelByTableName("dyn_table"));
        assertNotNull(model.getEntityModelByTableName("DYN_TABLE"));
    }

    @Test
    public void testConcurrentAddAndTopoRebuildKeepsAllEntities() throws InterruptedException {
        final int rounds = 50;
        final int addersPerRound = 8;

        for (int round = 0; round < rounds; round++) {
            LazyLoadOrmModel model = new LazyLoadOrmModel(null, (name, m) -> null);
            // 先触发一次拓扑重建，使 topoEntryInited=true
            model.getEntityNames();

            Set<String> expected = new HashSet<>();
            CountDownLatch start = new CountDownLatch(1);
            Thread[] adders = new Thread[addersPerRound];
            for (int i = 0; i < addersPerRound; i++) {
                String entityName = "Dyn" + round + "_" + i;
                expected.add(entityName);
                adders[i] = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    model.addEntityModel(dynModel(entityName, "TBL_" + entityName));
                });
                adders[i].start();
            }

            // 并发读驱动重建与 put 交错
            Thread reader = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    model.getEntityNames();
                }
            });
            reader.start();
            start.countDown();
            for (Thread adder : adders) {
                adder.join(5000);
            }
            reader.interrupt();
            reader.join(5000);

            // 修复前 put 与重建之间无 happens-before，新增实体可能在拓扑表中永久缺失
            Set<String> names = model.getEntityNames();
            for (String name : expected) {
                assertTrue(names.contains(name), "entity " + name + " missing after round " + round);
            }
        }
    }
}
