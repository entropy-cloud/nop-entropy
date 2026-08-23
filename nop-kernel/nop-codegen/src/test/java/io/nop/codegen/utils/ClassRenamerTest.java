package io.nop.codegen.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassRenamerTest {
    @Test
    void testWithParameterizedConstructor() {
        String source = "public class MyClass {\n" +
                "    public MyClass(String name) {}\n" +
                "}";

        String expected = "public class MyClass_base {\n" +
                "    public MyClass_base(String name) {}\n" +
                "}";

        assertEquals(expected, ClassRenamer.renameClassAndConstructors(source));
    }

    @Test
    void testIgnoreMethodReturnTypes() {
        String source = "public class Processor {\n" +
                "    public Processor create() { return new Processor(); }\n" +
                "}";

        // 方法返回类型和内部创建的实例不应被修改
        String expected = "public class Processor_base {\n" +
                "    public Processor create() { return new Processor(); }\n" +
                "}";

        assertEquals(expected, ClassRenamer.renameClassAndConstructors(source));
    }

    @Test
    void testMultipleConstructors() {
        String source = "public class Test {\n" +
                "    public Test(){}\n" +
                "  public   Test(int a){}\n" +
                "}";

        String expected = "public class Test_base {\n" +
                "    public Test_base(){}\n" +
                "  public   Test_base(int a){}\n" +
                "}";

        assertEquals(expected, ClassRenamer.renameClassAndConstructors(source));
    }

    @Test
    void testNoModifierConstructor() {
        String source = "public class Util {\n" +
                "    public Util(){} // 无修饰符\n" +
                "}";

        String expected = "public class Util_base {\n" +
                "    public Util_base(){} // 无修饰符\n" +
                "}";

        assertEquals(expected, ClassRenamer.renameClassAndConstructors(source));
    }

    @Test
    void testConstructorWithParenAndQuoteInAnnotation() {
        // 参数中的 ) 和 " 曾使正则 ([^)]*) 提前截断并损坏参数列表
        String source = "public class Foo {\n" +
                "    public Foo(@Prop(\"a(b\") String a, @Prop(\"x\\\"y\") String b) {}\n" +
                "}";

        String result = ClassRenamer.renameClassAndConstructors(source);
        assertTrue(result.contains("public Foo_base(@Prop(\"a(b\") String a, @Prop(\"x\\\"y\") String b) {}"), result);
    }

    @Test
    void testConstructorWithDollarAndBackslashInParams() {
        // 参数含 $ 或 \ 时 appendReplacement 会误当分组引用/转义处理
        String source = "public class Foo {\n" +
                "    public Foo(String $s, String b\\c) {}\n" +
                "}";

        String result = ClassRenamer.renameClassAndConstructors(source);
        assertTrue(result.contains("public Foo_base(String $s, String b\\c) {}"), result);
    }

    @Test
    void testUnbalancedParenKeptAsIs() {
        String source = "public class Foo {\n" +
                "    public Foo(String a\n" +
                "}";

        String result = ClassRenamer.renameClassAndConstructors(source);
        // 括号不平衡的异常输入不损坏、不抛异常
        assertTrue(result.contains("public Foo(String a"), result);
        assertTrue(result.startsWith("public class Foo_base {"), result);
    }
}
