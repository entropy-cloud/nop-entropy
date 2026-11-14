package io.nop.core.lang.xml;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2572 Phase 1 结构钉：_instance 必须是 volatile——
 * 启动线程（XLangCoreInitializer.registerInstance）写、工作线程（XNodeSplitter/xdef 编译链）读，
 * 非 volatile 是 unsafe publication（半构造暴露：cache 字段未发布即被读取）。
 * 2515 W-11 SymbolTable volatile 同族先例；全仓 mutable+non-final+non-volatile 静态实例 holder 唯一漏网。
 */
public class TestXPathProviderVolatilePin {
    @Test
    public void pin2572_instanceFieldMustBeVolatile() throws Exception {
        Field f = XPathProvider.class.getDeclaredField("_instance");
        int mods = f.getModifiers();
        assertTrue(Modifier.isStatic(mods) && Modifier.isVolatile(mods),
                "_instance must be static volatile: " + Modifier.toString(mods));
        assertNotNull(f.getType());
    }
}
