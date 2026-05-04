package io.nop.code.lang.python;

import io.nop.code.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestPythonCodeFileAnalyzer {

    private PythonCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PythonCodeFileAnalyzer();
    }

    private static final String CLASS_WITH_METHODS =
            "class MyClass(Base):\n" +
            "    def __init__(self, name):\n" +
            "        self.name = name\n" +
            "    def public_method(self):\n" +
            "        self._helper()\n" +
            "    def _helper(self):\n" +
            "        pass\n";

    @Test
    void testAnalyzeClassWithMethods() {
        CodeFileAnalysisResult result = analyzer.analyze("my_module.py", CLASS_WITH_METHODS);

        assertNotNull(result);
        assertEquals(CodeLanguage.PYTHON, result.getLanguage());

        List<CodeSymbol> symbols = result.getSymbols();

        CodeSymbol cls = findSymbol(symbols, "MyClass");
        assertNotNull(cls, "Should find MyClass symbol");
        assertEquals(CodeSymbolKind.CLASS, cls.getKind());
        assertTrue(cls.getQualifiedName().contains("MyClass"));
        assertEquals(CodeAccessModifier.PUBLIC, cls.getAccessModifier());

        CodeSymbol init = findSymbol(symbols, "__init__");
        assertNotNull(init, "Should find __init__ method");
        assertEquals(CodeSymbolKind.METHOD, init.getKind());
        assertEquals(CodeAccessModifier.PRIVATE, init.getAccessModifier());

        CodeSymbol pubMethod = findSymbol(symbols, "public_method");
        assertNotNull(pubMethod, "Should find public_method");
        assertEquals(CodeSymbolKind.METHOD, pubMethod.getKind());
        assertEquals(CodeAccessModifier.PUBLIC, pubMethod.getAccessModifier());
    }

    @Test
    void testInheritance() {
        CodeFileAnalysisResult result = analyzer.analyze("my_module.py", CLASS_WITH_METHODS);
        assertNotNull(result);

        List<CodeInheritance> inheritances = result.getInheritances();
        boolean hasExtendsBase = inheritances.stream()
                .anyMatch(i -> "Base".equals(i.getSuperTypeQualifiedName())
                        && i.getRelationType() == CodeRelationType.EXTENDS);
        assertTrue(hasExtendsBase, "Should have EXTENDS Base");
    }

    @Test
    void testStandaloneFunction() {
        String source = "def greet(name):\n" +
                "    print(f'Hello {name}')\n" +
                "    return True\n";

        CodeFileAnalysisResult result = analyzer.analyze("greeter.py", source);
        assertNotNull(result);

        CodeSymbol func = findSymbol(result.getSymbols(), "greet");
        assertNotNull(func, "Should find greet function");
        assertEquals(CodeSymbolKind.FUNCTION, func.getKind());
        assertEquals(CodeAccessModifier.PUBLIC, func.getAccessModifier());
        assertNotNull(func.getSignature(), "Function should have signature");
        assertTrue(func.getSignature().contains("greet"));
    }

    @Test
    void testPrivateFunction() {
        String source = "def _private_func():\n    pass\n";

        CodeFileAnalysisResult result = analyzer.analyze("mod.py", source);
        assertNotNull(result);

        CodeSymbol func = findSymbol(result.getSymbols(), "_private_func");
        assertNotNull(func);
        assertEquals(CodeAccessModifier.PRIVATE, func.getAccessModifier());
    }

    @Test
    void testMethodCalls() {
        CodeFileAnalysisResult result = analyzer.analyze("my_module.py", CLASS_WITH_METHODS);
        assertNotNull(result);

        List<CodeMethodCall> calls = result.getCalls();
        boolean hasHelperCall = calls.stream()
                .anyMatch(c -> "_helper".equals(c.getMethodName()));
        assertTrue(hasHelperCall, "Should have a call to _helper");
    }

    @Test
    void testEmptySourceReturnsNull() {
        assertNull(analyzer.analyze("empty.py", ""));
        assertNull(analyzer.analyze("empty.py", "   "));
        assertNull(analyzer.analyze("empty.py", null));
    }

    @Test
    void testInvalidSourceStillParsed() {
        String source = "this is not valid python {{{}}}";
        CodeFileAnalysisResult result = analyzer.analyze("invalid.py", source);
        assertNotNull(result, "tree-sitter is error-tolerant, should still return a result");
        assertEquals(CodeLanguage.PYTHON, result.getLanguage());
    }

    @Test
    void testAsyncFunction() {
        String source = "async def fetch_data(url):\n" +
                "    pass\n";

        CodeFileAnalysisResult result = analyzer.analyze("async_mod.py", source);
        assertNotNull(result);

        CodeSymbol func = findSymbol(result.getSymbols(), "fetch_data");
        assertNotNull(func);
        assertTrue(func.isAsyncFlag(), "Should detect async function");
    }

    @Test
    void testNestedClass() {
        String source = "class Outer:\n" +
                "    class Inner:\n" +
                "        pass\n";

        CodeFileAnalysisResult result = analyzer.analyze("nested.py", source);
        assertNotNull(result);

        CodeSymbol outer = findSymbol(result.getSymbols(), "Outer");
        CodeSymbol inner = findSymbol(result.getSymbols(), "Inner");
        assertNotNull(outer);
        assertNotNull(inner);
        assertEquals(CodeSymbolKind.CLASS, inner.getKind());
        assertTrue(inner.getQualifiedName().contains("Outer.Inner"),
                "Inner class qualified name should contain 'Outer.Inner', was: " + inner.getQualifiedName());
    }

    private CodeSymbol findSymbol(List<CodeSymbol> symbols, String name) {
        return symbols.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }
}
