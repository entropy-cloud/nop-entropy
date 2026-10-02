/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.commons.collections.KeyedList;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReflectConfigGenerator extends BaseTestCase {

    static class SampleBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static KeyedList<ReflectClass> classes(ReflectConfig config) {
        return (KeyedList<ReflectClass>) config.getClassList();
    }

    @BeforeAll
    public static void setUp() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void tearDown() {
        CoreInitialization.destroy();
    }

    private static ReflectClass clazz(String name) {
        ReflectClass clazz = new ReflectClass();
        clazz.setName(name);
        ReflectField field = new ReflectField();
        field.setName("value");
        field.setAllowWrite(true);
        clazz.addField(field);
        ReflectMethod method = new ReflectMethod();
        method.setName("run");
        method.setParameterTypes(List.of("java.lang.String"));
        clazz.addMethod(method);
        return clazz;
    }

    @Test
    public void testLoadConfigOfMissingResourceIsEmpty() {
        ReflectConfigGenerator gen = ReflectConfigGenerator.instance();
        FileResource missing = new FileResource("/virtual/missing-reflect-config.json",
                getTargetFile("no-such-reflect-config.json"));
        ReflectConfig config = gen.loadConfig(missing);
        assertNotNull(config);
        assertTrue(config.getClassList().isEmpty());
    }

    @Test
    public void testSaveConfigWritesSortedJsonAndRoundTrips() {
        ReflectConfigGenerator gen = ReflectConfigGenerator.instance();

        ReflectConfig config = new ReflectConfig();
        config.addClass(clazz("io.test.Zeta"));
        config.addClass(clazz("io.test.Alpha"));
        // 构造乱序：Zeta 先加入，保存时应按类名排序
        ReflectClass unsafe = clazz("io.test.Mid");
        unsafe.setUnsafeAllocated(true);
        config.addClass(unsafe);

        FileResource target = new FileResource("/virtual/reflect-config.json",
                getTargetFile("graalvm/reflect-config.json"));
        gen.saveConfig(target, config);

        String text = ResourceHelper.readText(target);
        // 类名按字典序输出
        assertTrue(text.indexOf("io.test.Alpha") >= 0);
        assertTrue(text.indexOf("io.test.Alpha") < text.indexOf("io.test.Mid"));
        assertTrue(text.indexOf("io.test.Mid") < text.indexOf("io.test.Zeta"));

        // JSON 可以无损读回，字段/方法/标志保留
        ReflectConfig loaded = gen.loadConfig(target);
        assertEquals(3, loaded.getClassList().size());
        ReflectClass alpha = classes(loaded).getByKey("io.test.Alpha");
        assertEquals("value", alpha.getFields().get(0).getName());
        assertTrue(alpha.getFields().get(0).isAllowWrite());
        assertEquals("run(java.lang.String)", alpha.getMethods().get(0).getSignature());
        assertFalse(alpha.isUnsafeAllocated());
        assertTrue(classes(loaded).getByKey("io.test.Mid").isUnsafeAllocated());
    }

    @Test
    public void testGenerateIncludesLoggedReflectClasses() {
        // 只有开启 recordForNativeImage 后 logReflectClass 才会登记
        ReflectionManager.instance().setRecordForNativeImage(true);
        try {
            ReflectionManager.instance().logReflectClass(SampleBean.class);
            ReflectConfig config = ReflectConfigGenerator.instance().generate();

            String sampleName = SampleBean.class.getName();
            ReflectClass reflectClass = classes(config).getByKey(sampleName);
            assertNotNull(reflectClass);
            // 生成器为登记类开启全部 public 反射开关
            assertTrue(reflectClass.isAllPublicConstructors());
            assertTrue(reflectClass.isAllPublicMethods());
            assertTrue(reflectClass.isAllPublicFields());
        } finally {
            // 恢复缺省关闭状态（同时清空登记集合，避免污染其他测试）
            ReflectionManager.instance().setRecordForNativeImage(false);
        }
    }

    @Test
    public void testGenerateDeltaToResourceMergesExistingAndExcludesDefaultConfig() {
        FileResource target = new FileResource("/virtual/reflect-config-delta.json",
                getTargetFile("graalvm/reflect-config-delta.json"));
        // 预置旧文件内容：其中一个类已存在于缺省 classpath 配置中
        ReflectClass existing = clazz("io.test.Preserved");
        ReflectConfig oldConfig = new ReflectConfig();
        oldConfig.addClass(existing);
        ResourceHelper.writeText(target, JsonTool.serialize(oldConfig.getClassList(), false));

        ReflectConfigGenerator gen = ReflectConfigGenerator.instance();
        gen.generateDeltaToResource(target);

        ReflectConfig after = gen.loadConfig(target);
        // 旧文件中的条目被合并保留
        assertNotNull(classes(after).getByKey("io.test.Preserved"));
    }
}
