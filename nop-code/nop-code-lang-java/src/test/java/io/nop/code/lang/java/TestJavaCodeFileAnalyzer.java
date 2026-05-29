package io.nop.code.lang.java;

import io.nop.code.core.model.*;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestJavaCodeFileAnalyzer {

    private JavaFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaFileAnalyzer();
    }

    private static final String TEST_SOURCE =
            "package io.nop.test;\n" +
            "@Deprecated\n" +
            "public class Foo extends Base implements Iface {\n" +
            "    private String name;\n" +
            "    public void doStuff(String arg) { helper(); }\n" +
            "    public synchronized void syncMethod() {}\n" +
            "}\n";

    @Test
    void testAnalyzeBasicClass() {
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", TEST_SOURCE);

        assertNotNull(result);
        assertEquals(CodeLanguage.JAVA, result.getLanguage());
        assertEquals("io.nop.test", result.getPackageName());

        List<CodeSymbol> symbols = result.getSymbols();
        CodeSymbol foo = findSymbol(symbols, "Foo");
        assertNotNull(foo, "Should find Foo symbol");
        assertEquals(CodeSymbolKind.CLASS, foo.getKind());
        assertEquals("io.nop.test.Foo", foo.getQualifiedName());
        assertTrue(foo.isDeprecated(), "Foo should be deprecated");
    }

    @Test
    void testInheritances() {
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", TEST_SOURCE);
        assertNotNull(result);

        List<CodeInheritance> inheritances = result.getInheritances();
        boolean hasExtends = inheritances.stream()
                .anyMatch(i -> "Base".equals(i.getSuperTypeQualifiedName())
                        && i.getRelationType() == CodeRelationType.EXTENDS);
        boolean hasImplements = inheritances.stream()
                .anyMatch(i -> "Iface".equals(i.getSuperTypeQualifiedName())
                        && i.getRelationType() == CodeRelationType.IMPLEMENTS);

        assertTrue(hasExtends, "Should have EXTENDS Base");
        assertTrue(hasImplements, "Should have IMPLEMENTS Iface");
    }

    @Test
    void testMethodSymbol() {
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", TEST_SOURCE);
        assertNotNull(result);

        CodeSymbol doStuff = findSymbol(result.getSymbols(), "doStuff");
        assertNotNull(doStuff, "Should find doStuff method");
        assertEquals(CodeSymbolKind.METHOD, doStuff.getKind());
        assertNotNull(doStuff.getSignature(), "Method should have a signature");
        assertTrue(doStuff.getSignature().contains("doStuff"),
                "Signature should contain 'doStuff', was: " + doStuff.getSignature());
    }

    @Test
    void testFieldSymbol() {
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", TEST_SOURCE);
        assertNotNull(result);

        CodeSymbol nameField = findSymbol(result.getSymbols(), "name");
        assertNotNull(nameField, "Should find name field");
        assertEquals(CodeSymbolKind.FIELD, nameField.getKind());
        assertEquals("String", nameField.getFieldType());
    }

    @Test
    void testMethodCall() {
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", TEST_SOURCE);
        assertNotNull(result);

        List<CodeMethodCall> calls = result.getCalls();
        boolean hasHelperCall = calls.stream()
                .anyMatch(c -> "helper".equals(c.getMethodName()));
        assertTrue(hasHelperCall, "Should have a call to 'helper'");
    }

    @Test
    void testSynchronizedMethod() {
        CodeFileAnalysisResult result = analyzer.analyze("Foo.java", TEST_SOURCE);
        assertNotNull(result);

        CodeSymbol syncMethod = findSymbol(result.getSymbols(), "syncMethod");
        assertNotNull(syncMethod, "Should find syncMethod");
        assertNotNull(syncMethod.getExtData(), "syncMethod should have extData");
        assertTrue(syncMethod.getExtData().contains("synchronized"),
                "extData should contain 'synchronized', was: " + syncMethod.getExtData());
    }

    @Test
    void testEmptySourceReturnsNull() {
        assertNull(analyzer.analyze("Empty.java", ""));
        assertNull(analyzer.analyze("Empty.java", "   "));
        assertNull(analyzer.analyze("Empty.java", null));
    }

    @Test
    void testInvalidJavaReturnsNull() {
        String invalid = "this is not valid java {{{}}}";
        assertNull(analyzer.analyze("Invalid.java", invalid));
    }

    @Test
    void testSealedClassPermitsExtracted() {
        String source = "package io.nop.test;\n" +
                "public sealed class Shape permits Circle, Square, Triangle {\n" +
                "}\n" +
                "final class Circle extends Shape {}\n" +
                "final class Square extends Shape {}\n" +
                "final class Triangle extends Shape {}\n";

        CodeFileAnalysisResult result = analyzer.analyze("Shape.java", source);
        assertNotNull(result);

        CodeSymbol shape = findSymbol(result.getSymbols(), "Shape");
        assertNotNull(shape, "Should find Shape sealed class");
        assertNotNull(shape.getExtData(), "Sealed class should have extData");
        assertTrue(shape.getExtData().contains("sealed"), "extData should contain 'sealed'");
        assertTrue(shape.getExtData().contains("Circle"), "extData should contain 'Circle' in permits");
        assertTrue(shape.getExtData().contains("Square"), "extData should contain 'Square' in permits");
    }

    private CodeSymbol findSymbol(List<CodeSymbol> symbols, String name) {
        return symbols.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }
}
