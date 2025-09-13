package io.nop.javaparser.simplifier;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaFileSimplifierTest {

    private String simplify(String code) {
        CompilationUnit cu = new JavaParser().parse(new StringReader(code)).getResult().orElseThrow();
        new JavaFileSimplifier().simplify(cu);
        return cu.toString();
    }

    // 标准化字符串：移除多余空格和换行
    private String normalize(String s) {
        return s.replaceAll("\\s+", "").trim();
    }

    @Test
    void testClassWithPublicMethods() {
        String input = "public class Sample {" +
                "  private int count;" +
                "  public void increment() { count++; }" +
                "  public int getCount() { return count; }" +
                "  private void reset() { count = 0; }" +
                "}";

        String expected = "public class Sample { " +
                "public void increment(); " +
                "public int getCount(); " +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testClassWithConstructor() {
        String input = "public class Person {" +
                "  private String name;" +
                "  public Person() {}" +
                "  public Person(String name) { this.name = name; }" +
                "}";

        String expected = "public class Person { " +
                "public Person() { } " +
                "public Person(String name) { } " +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testInterfaceSimplification() {
        String input = "public interface Calculator {" +
                "  int add(int a, int b);" +
                "  default int multiply(int a, int b) {" +
                "    return a * b;" +
                "  }" +
                "}";

        String expected = "public interface Calculator { " +
                "int add(int a, int b); " +
                "default int multiply(int a, int b); " +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testEnumSimplification() {
        String input = "public enum Direction {" +
                "  NORTH(0), EAST(90), SOUTH(180), WEST(270);" +
                "  private int degrees;" +
                "  Direction(int degrees) { this.degrees = degrees; }" +
                "  public int getDegrees() { return degrees; }" +
                "}";

        String expected = "public enum Direction {\n" +
                "\n" +
                "    NORTH(0), EAST(90), SOUTH(180), WEST(270);\n" +
                "\n" +
                "    public int getDegrees();\n" +
                "}\n";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testClassWithAnnotations() {
        String input = "@Service " +
                "public class UserService {" +
                "  @Autowired " +
                "  private UserRepository repository;" +
                "  @Transactional " +
                "  public void save(User user) {" +
                "    repository.save(user);" +
                "  }" +
                "}";

        String expected = "@Service " +
                "public class UserService { " +
                "@Transactional " +
                "public void save(User user); " +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testAbstractClass() {
        String input = "public abstract class Shape {" +
                "  private String color;" +
                "  public abstract double area();" +
                "  public void setColor(String color) {" +
                "    this.color = color;" +
                "  }" +
                "}";

        String expected = "public abstract class Shape { " +
                "public abstract double area(); " +
                "public void setColor(String color); " +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }

    @Test
    void testStaticMethods() {
        String input = "public class MathUtils {" +
                "  public static int max(int a, int b) {" +
                "    return a > b ? a : b;" +
                "  }" +
                "  private static void log(String msg) {}" +
                "}";

        String expected = "public class MathUtils { " +
                "public static int max(int a, int b); " +
                "}";

        assertEquals(normalize(expected), normalize(simplify(input)));
    }
}