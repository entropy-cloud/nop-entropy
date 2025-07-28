package io.nop.javaparser.delta;

import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeltaJavaMergerTest {
    private final DeltaJavaMerger merger = new DeltaJavaMerger();

    @Test
    void testMergeClasses() {
        String sourceA = "package com.example;\n" +
                "public class A {\n" +
                "    void methodA() {}\n" +
                "}";

        String sourceB = "package com.example;\n" +
                "public class A {\n" +
                "    void methodB() {}\n" +
                "}";

        String merged = merger.merge(sourceA, sourceB);
        assertTrue(merged.contains("methodA()"));
        assertTrue(merged.contains("methodB()"));
        assertTrue(merged.contains("package com.example"));
    }

    @Test
    void testMergeWithDifferentTypes() {
        String sourceA = "public class A {\n" +
                "    private String field;\n" +
                "}";

        String sourceB = "public interface A {\n" +
                "    void method();\n" +
                "}";

        String merged = merger.merge(sourceA, sourceB);
        assertTrue(merged.contains("interface A"));
        assertFalse(merged.contains("field"));
    }

    @Test
    void testMergeAnnotations() {
        String sourceA = "@Deprecated\n" +
                "public @interface MyAnnotation {\n" +
                "    String value();\n" +
                "    String desc() default \"\";\n" +
                "}";

        String sourceB = "@Retention(RUNTIME)\n" +
                "public @interface MyAnnotation {\n" +
                "    int priority() default 0;\n" +
                "    String desc() default \"default\";\n" +
                "}";

        String merged = merger.merge(sourceA, sourceB);

        assertAll(
                () -> assertTrue(merged.contains("@Deprecated")),
                () -> assertTrue(merged.contains("@Retention(RUNTIME)")),
                () -> assertTrue(merged.contains("String value()")),
                () -> assertTrue(merged.contains("int priority() default 0")),
                () -> assertTrue(merged.contains("String desc() default \"default\""))
        );
    }

    @Test
    void testMergeEnums() {
        String sourceA = "public enum Color {\n" +
                "    RED, GREEN\n" +
                "}";

        String sourceB = "public enum Color {\n" +
                "    RED, BLUE\n" +
                "}";

        String merged = merger.merge(sourceA, sourceB);

        assertAll(
                () -> assertEquals(1, StringHelper.countMatches(merged, "RED")),
                () -> assertTrue(merged.contains("GREEN")),
                () -> assertTrue(merged.contains("BLUE"))
        );
    }

    @Test
    void testMergePackageAndImports() {
        // 使用数组和String.join构造多行字符串
        String[] sourceALines = {
                "package com.old;",
                "import java.util.List;",
                "public class A {}"
        };
        String sourceA = String.join("\n", sourceALines);

        String[] sourceBLines = {
                "package com.new_pkg;",
                "import java.util.Set;",
                "public class A {}"
        };
        String sourceB = String.join("\n", sourceBLines);

        String merged = merger.merge(sourceA, sourceB);

        assertAll(
                () -> assertTrue(merged.contains("package com.new_pkg")),
                () -> assertTrue(merged.contains("import java.util.List")),
                () -> assertTrue(merged.contains("import java.util.Set"))
        );
    }

    @Test
    void testMergeWhenSourceAEmpty() {
        String sourceA = "";
        String sourceB = "public class B {\n" +
                "    void onlyInB() {}\n" +
                "}";

        String merged = merger.merge(sourceA, sourceB);
        assertTrue(merged.contains("onlyInB()"));
    }

    @Test
    void testMergeWhenSourceBEmpty() {
        String sourceA = "public class C {\n" +
                "    void onlyInA() {}\n" +
                "}";
        String sourceB = "";

        String merged = merger.merge(sourceA, sourceB);
        assertTrue(merged.contains("onlyInA()"));
    }
}