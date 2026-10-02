package io.nop.core.reflect.aop;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAopAnnotationsLoader extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseClassNamesFiltersByPostfixAndSplitsLines() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("aop-annotations-test").toFile();
        try {
            File registry = new File(dir, "my-module.annotations");
            Files.write(registry.toPath(),
                    "io.nop.Ann1\nio.nop.Ann2\n\nio.nop.Ann1\n".getBytes(StandardCharsets.UTF_8));

            // 非 .annotations 后缀的文件应被忽略
            File other = new File(dir, "ignored.txt");
            Files.write(other.toPath(), "io.nop.ShouldNotAppear".getBytes(StandardCharsets.UTF_8));

            List<IResource> resources = Arrays.asList(new FileResource(registry.toURI().toString(), registry),
                    new FileResource(other.toURI().toString(), other));

            Set<String> names = AopAnnotationsLoader.parseClassNames(resources);
            // splitToLines 保留空行，空行也会进入集合（加载阶段被 safeLoadClass 忽略）
            assertEquals(new HashSet<>(Arrays.asList("io.nop.Ann1", "io.nop.Ann2", "")), names,
                    "should parse only .annotations files and deduplicate lines");
        } finally {
            registryDelete(dir);
        }
    }

    private static void registryDelete(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }

    @Test
    public void testParseClassNamesHandlesNullAndEmpty() {
        assertTrue(AopAnnotationsLoader.parseClassNames(null).isEmpty());
        assertTrue(AopAnnotationsLoader.parseClassNames(Collections.emptyList()).isEmpty());
    }

    @Test
    public void testLoadAnnotationClassesFromVfs() {
        // /nop/aop 下 nop-api-core.annotations 注册的注解应被加载为 Class 对象
        List<Class<?>> classes = AopAnnotationsLoader.getAnnotationClasses();
        assertNotNull(classes);
        assertTrue(classes.contains(io.nop.api.core.annotations.txn.Transactional.class),
                "registry should load Transactional annotation, got: " + classes);
        assertTrue(classes.contains(io.nop.api.core.annotations.cache.Cache.class),
                "registry should load Cache annotation");

        // 二次调用返回缓存结果（同一列表实例）
        org.junit.jupiter.api.Assertions.assertSame(classes, AopAnnotationsLoader.getAnnotationClasses(),
                "getAnnotationClasses should cache loaded classes");
    }
}
