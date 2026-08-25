/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.initialize;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class TestCoreInitialization {

    /**
     * reinitialize 独立调用（或 destroy 后再调用）时 bootstrapConfig 为 null，
     * loadInitializers 不应抛 NPE
     */
    @Test
    public void testLoadInitializersWithoutBootstrapConfig() throws Exception {
        Field field = CoreInitialization.class.getDeclaredField("bootstrapConfig");
        field.setAccessible(true);
        Object oldConfig = field.get(null);
        field.set(null, null);
        try {
            List<ICoreInitializer> list = CoreInitialization.loadInitializers();
            assertNotNull(list);
        } catch (NullPointerException e) {
            fail("loadInitializers should not throw NPE when bootstrapConfig is not loaded");
        } finally {
            field.set(null, oldConfig);
        }
    }

    /**
     * 未初始化状态下调用reinitialize应把loadInitializers的结果回写到initializers静态字段，
     * 否则后续destroy()会因为initializers==null而整体跳过清理
     */
    @Test
    public void testReinitializeWritesBackInitializers() throws Exception {
        Field field = CoreInitialization.class.getDeclaredField("initializers");
        field.setAccessible(true);

        CoreInitialization.destroy();
        assertNull(field.get(null));

        CoreInitialization.reinitialize();
        assertNotNull(field.get(null), "reinitialize should write back loaded initializers");

        // 回写之后destroy能正常执行清理并复位
        CoreInitialization.destroy();
        assertNull(field.get(null));
    }
}
