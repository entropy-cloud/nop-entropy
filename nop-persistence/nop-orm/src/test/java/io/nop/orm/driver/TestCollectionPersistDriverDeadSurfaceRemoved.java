/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.driver;

import io.nop.orm.persister.CollectionPersisterImpl;
import io.nop.orm.persister.ICollectionPersister;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 6 / V7：删除 {@code ICollectionPersistDriver.flushCollectionChange} 死接口方法。
 *
 * <p>该方法（{@code (ShardSelection, IOrmEntitySet, IOrmSessionImplementor)}）全仓零调用点，
 * 其 JDBC 实现是纯空方法体——即 plan 所说的"静默跳过"形态。
 * 已从接口与 {@code JdbcCollectionPersistDriver} 中删除。
 *
 * <p>本类钉住两件事：
 * <ol>
 *   <li>死方法确实已消失（接口与其唯一实现都不再声明它）；</li>
 *   <li><b>同名的活路径仍在</b>——{@code ICollectionPersister.flushCollectionChange}
 *       （单参版）承担真实的入队 + 全局缓存失效职责，删除不得误伤它。</li>
 * </ol>
 */
public class TestCollectionPersistDriverDeadSurfaceRemoved {

    static List<String> methodNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Method m : clazz.getMethods())
            names.add(m.getName());
        return names;
    }

    @Test
    public void testDriverInterfaceNoLongerDeclaresFlushCollectionChange() {
        // 核心回归：死接口方法已删除
        assertFalse(methodNames(ICollectionPersistDriver.class).contains("flushCollectionChange"),
                "ICollectionPersistDriver 不应再声明零调用点的 flushCollectionChange");
    }

    @Test
    public void testJdbcDriverNoLongerImplementsFlushCollectionChange() {
        assertFalse(methodNames(io.nop.orm.driver.jdbc.JdbcCollectionPersistDriver.class)
                        .contains("flushCollectionChange"),
                "JdbcCollectionPersistDriver 不应再有空的 flushCollectionChange 实现");
    }

    @Test
    public void testLivePersisterMethodStillExists() {
        // 防误伤：真正承担职责的 persister 侧同名方法必须保留
        assertTrue(methodNames(ICollectionPersister.class).contains("flushCollectionChange"),
                "ICollectionPersister.flushCollectionChange 是活路径，不得被误删");
        assertNotNull(methodNames(CollectionPersisterImpl.class).stream()
                .filter("flushCollectionChange"::equals).findFirst().orElse(null),
                "CollectionPersisterImpl 必须保留 flushCollectionChange 实现");
    }

    @Test
    public void testPersisterFlushSignatureIsTheSingleArgOne() {
        // 活路径是 (IOrmEntitySet, IOrmSessionImplementor)；死路径是三参 (ShardSelection, ...)
        boolean found = false;
        for (Method m : ICollectionPersister.class.getMethods()) {
            if ("flushCollectionChange".equals(m.getName())) {
                assertNotNull(m.getParameterTypes());
                assertTrue(m.getParameterCount() == 2,
                        "persister 侧应为两参 (IOrmEntitySet, IOrmSessionImplementor)，实际 "
                                + m.getParameterCount() + " 参");
                found = true;
            }
        }
        assertTrue(found, "未找到 ICollectionPersister.flushCollectionChange");
    }

    @Test
    public void testOtherDriverMethodsRetained() {
        // 防误伤：删除死方法不得误删接口上的其他方法
        List<String> names = methodNames(ICollectionPersistDriver.class);
        assertTrue(names.contains("loadCollection"), "loadCollection 必须保留");
        assertTrue(names.contains("batchLoadCollectionAsync"), "batchLoadCollectionAsync 必须保留");
        assertTrue(names.contains("init"), "init 必须保留");
    }
}