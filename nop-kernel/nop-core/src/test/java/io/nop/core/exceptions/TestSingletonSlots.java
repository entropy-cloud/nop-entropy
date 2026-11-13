/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.core.exceptions;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C1-009）载体钉：单例槽位 volatile + ErrorMessageManager this-escape 消除。
 */
public class TestSingletonSlots {

    /** pin0109：本模块内 4 个可替换单例槽位必须 `static volatile`。 */
    @Test
    public void pin0109_slotsAreVolatile() throws Exception {
        Class<?>[] classes = {
                io.nop.core.unittest.VarCollector.class,
                io.nop.core.module.ModuleManager.class,
                io.nop.core.dict.DictProvider.class,
                io.nop.core.resource.VirtualFileSystem.class
        };
        for (Class<?> c : classes) {
            Field f = c.getDeclaredField("_instance");
            int mod = f.getModifiers();
            assertTrue(Modifier.isStatic(mod), c.getSimpleName() + "._instance 必须 static");
            assertTrue(Modifier.isVolatile(mod),
                    c.getSimpleName() + "._instance 必须 volatile（跨线程重注册可见性）");
        }
    }

    /** pin0109b：`ErrorMessageManager` 构造器不再 this-escape（构造新实例不改变全局注册）。 */
    @Test
    public void pin0109b_errorMessageManagerConstructorHasNoSideEffect() {
        ErrorMessageManager global = ErrorMessageManager.instance();
        assertNotNull(global);

        // 构造一个独立实例——不得注册为全局（this-escape 已消除）
        new ErrorMessageManager();

        assertEquals(global, ErrorMessageManager.instance(),
                "new ErrorMessageManager() 不得改变全局实例（this-escape 已消除）");
    }
}
