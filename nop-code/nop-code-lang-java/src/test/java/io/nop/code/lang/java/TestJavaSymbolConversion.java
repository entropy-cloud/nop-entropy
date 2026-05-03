package io.nop.code.lang.java;

import io.nop.code.core.model.*;
import io.nop.code.lang.java.analyzer.JavaFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestJavaSymbolConversion {

    private JavaFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaFileAnalyzer();
}

    @Test
    void testClassSymbolKind() {
        String source = "package io.nop.test;\npublic class MyClass {}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol cls = findSymbol(result.getSymbols(), "MyClass");
        assertNotNull(cls);
        assertEquals(CodeSymbolKind.CLASS, cls.getKind());
    }

    @Test
    void testInterfaceSymbolKind() {
        String source = "package io.nop.test;\npublic interface MyInterface { void doWork(); }\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyInterface.java", source);
        assertNotNull(result);

        CodeSymbol iface = findSymbol(result.getSymbols(), "MyInterface");
        assertNotNull(iface);
        assertEquals(CodeSymbolKind.INTERFACE, iface.getKind());
    }

    @Test
    void testEnumSymbolKind() {
        String source = "package io.nop.test;\npublic enum MyEnum { A, B }\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyEnum.java", source);
        assertNotNull(result);

        CodeSymbol en = findSymbol(result.getSymbols(), "MyEnum");
        assertNotNull(en);
        assertEquals(CodeSymbolKind.ENUM, en.getKind());
    }

    @Test
    void testEnumConstantSymbolKind() {
        String source = "package io.nop.test;\npublic enum MyEnum { ALPHA, BETA }\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyEnum.java", source);
        assertNotNull(result);

        CodeSymbol alpha = findSymbol(result.getSymbols(), "ALPHA");
        assertNotNull(alpha, "Should find enum constant ALPHA");
        assertEquals(CodeSymbolKind.CONSTANT, alpha.getKind(),
                "ENUM_CONSTANT should map to CONSTANT");
    }

    @Test
    void testAnnotationTypeSymbolKind() {
        String source = "package io.nop.test;\npublic @interface MyAnnotation { String value(); }\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyAnnotation.java", source);
        assertNotNull(result);

        CodeSymbol ann = findSymbol(result.getSymbols(), "MyAnnotation");
        assertNotNull(ann);
        assertEquals(CodeSymbolKind.ANNOTATION_TYPE, ann.getKind());
    }

    @Test
    void testMethodSymbolKind() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    public void myMethod() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol method = findSymbol(result.getSymbols(), "myMethod");
        assertNotNull(method);
        assertEquals(CodeSymbolKind.METHOD, method.getKind());
    }

    @Test
    void testConstructorSymbolKind() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    public MyClass() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol ctor = findSymbolByKind(result.getSymbols(), CodeSymbolKind.CONSTRUCTOR);
        assertNotNull(ctor, "Should find a CONSTRUCTOR symbol");
    }

    @Test
    void testFieldSymbolKind() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    private int count;\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol field = findSymbol(result.getSymbols(), "count");
        assertNotNull(field);
        assertEquals(CodeSymbolKind.FIELD, field.getKind());
        assertEquals("int", field.getFieldType());
    }

    @Test
    void testAccessModifierPublic() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    public void pub() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol pub = findSymbol(result.getSymbols(), "pub");
        assertNotNull(pub);
        assertEquals(CodeAccessModifier.PUBLIC, pub.getAccessModifier());
    }

    @Test
    void testAccessModifierPrivate() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    private void priv() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol priv = findSymbol(result.getSymbols(), "priv");
        assertNotNull(priv);
        assertEquals(CodeAccessModifier.PRIVATE, priv.getAccessModifier());
    }

    @Test
    void testAccessModifierProtected() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    protected void prot() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol prot = findSymbol(result.getSymbols(), "prot");
        assertNotNull(prot);
        assertEquals(CodeAccessModifier.PROTECTED, prot.getAccessModifier());
    }

    @Test
    void testAccessModifierPackagePrivate() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    void pkg() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol pkg = findSymbol(result.getSymbols(), "pkg");
        assertNotNull(pkg);
        assertEquals(CodeAccessModifier.PACKAGE_PRIVATE, pkg.getAccessModifier());
    }

    @Test
    void testExtDataSynchronized() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    public synchronized void syncMethod() {}\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol sync = findSymbol(result.getSymbols(), "syncMethod");
        assertNotNull(sync);
        assertNotNull(sync.getExtData());
        assertTrue(sync.getExtData().contains("synchronized"));
    }

    @Test
    void testExtDataVolatile() {
        String source = "package io.nop.test;\n" +
                "public class MyClass {\n" +
                "    private volatile int counter;\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol vol = findSymbol(result.getSymbols(), "counter");
        assertNotNull(vol);
        assertNotNull(vol.getExtData(), "volatile field should have extData");
        assertTrue(vol.getExtData().contains("volatile"),
                "extData should contain 'volatile', was: " + vol.getExtData());
    }

    @Test
    void testExtDataTransient() {
        String source = "package io.nop.test;\n" +
                "public class MyClass implements java.io.Serializable {\n" +
                "    private transient String temp;\n" +
                "}\n";
        CodeFileAnalysisResult result = analyzer.analyze("MyClass.java", source);
        assertNotNull(result);

        CodeSymbol tr = findSymbol(result.getSymbols(), "temp");
        assertNotNull(tr);
        assertNotNull(tr.getExtData(), "transient field should have extData");
        assertTrue(tr.getExtData().contains("transient"),
                "extData should contain 'transient', was: " + tr.getExtData());
    }

    private CodeSymbol findSymbol(List<CodeSymbol> symbols, String name) {
        return symbols.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }

    private CodeSymbol findSymbolByKind(List<CodeSymbol> symbols, CodeSymbolKind kind) {
        return symbols.stream()
                .filter(s -> kind.equals(s.getKind()))
                .findFirst()
                .orElse(null);
    }
}
