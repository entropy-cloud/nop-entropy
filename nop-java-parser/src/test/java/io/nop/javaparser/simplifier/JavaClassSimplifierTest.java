package io.nop.javaparser.simplifier;

import com.github.javaparser.ast.CompilationUnit;
import io.nop.javaparser.utils.JavaParserHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaClassSimplifierTest {

    private String simplify(String code) {
        CompilationUnit cu = JavaParserHelper.parseJavaSource(null, code);
        JavaFileSimplifier.simplify(cu);
        return normalize(cu.toString());
    }

    private String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    @Test
    void testPublicMethodsBecomeAbstractStyle() {
        String input = "public class TestClass {" +
                "  private String field;" +
                "  public void publicMethod() {" +
                "    System.out.println(\"Hello\");" +
                "  }" +
                "  private void privateMethod() {}" +
                "}";

        String expected = "public class TestClass {" +
                " public void publicMethod();" +
                "}";

        String result = simplify(input);
        assertAll(
                () -> assertTrue(result.contains("public void publicMethod();")),
                () -> assertFalse(result.contains("private")),
                () -> assertFalse(result.contains("System.out.println"))
        );
    }

    @Test
    void testMethodsWithReturnValues() {
        String input = "public class Calculator {" +
                "  public int add(int a, int b) {" +
                "    return a + b;" +
                "  }" +
                "  private String helper() {" +
                "    return \"help\";" +
                "  }" +
                "}";

        String expected = "public class Calculator {" +
                " public int add(int a, int b);" +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testConstructorsKeepEmptyBlock() {
        String input = "public class Person {" +
                "  private String name;" +
                "  public Person() {" +
                "    this.name = \"Unknown\";" +
                "  }" +
                "  public Person(String name) {" +
                "    this.name = name;" +
                "  }" +
                "}";

        String expected = "public class Person {" +
                " public Person() {" +
                " }" +
                " public Person(String name) {" +
                " }" +
                "}";

        String result = simplify(input);
        assertAll(
                () -> assertTrue(result.contains("public Person() {")),
                () -> assertTrue(result.contains("public Person(String name) {")),
                () -> assertFalse(result.contains("this.name ="))
        );
    }

    @Test
    void testInterfaceMethodsRemainUnchanged() {
        String input = "public interface Vehicle {" +
                "  void start();" +
                "  default void stop() {" +
                "    System.out.println(\"Stopping\");" +
                "  }" +
                "}";

        String expected = "public interface Vehicle {" +
                " void start();" +
                " default void stop();" +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testAbstractClassHandling() {
        String input = "public abstract class Shape {" +
                "  private String color;" +
                "  public abstract double area();" +
                "  public void setColor(String color) {" +
                "    this.color = color;" +
                "  }" +
                "}";

        String expected = "public abstract class Shape {" +
                " public abstract double area();" +
                " public void setColor(String color);" +
                "}";

        String result = simplify(input);
        assertAll(
                () -> assertTrue(result.contains("public abstract double area();")),
                () -> assertTrue(result.contains("public void setColor(String color);")),
                () -> assertFalse(result.contains("private"))
        );
    }

    @Test
    void testStaticMethods() {
        String input = "public class MathUtils {" +
                "  public static int max(int a, int b) {" +
                "    return a > b ? a : b;" +
                "  }" +
                "  private static void log(String msg) {}" +
                "}";

        String expected = "public class MathUtils {" +
                " public static int max(int a, int b);" +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }
}