package io.nop.codegen.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
