package io.nop.code.lang.python;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTreeSitterByteOffset {

    private PythonCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PythonCodeFileAnalyzer();
    }

    @Test
    void testChineseCommentDoesNotCorruptSymbolExtraction() {
        String source = "# \u8fd9\u662f\u4e2d\u6587\u6ce8\u91ca\n" +
                "class MyClass:\n" +
                "    def hello(self):\n" +
                "        pass\n";

        CodeFileAnalysisResult result = analyzer.analyze("test.py", source);
        assertNotNull(result);

        CodeSymbol cls = findSymbol(result.getSymbols(), "MyClass");
        assertNotNull(cls, "Should find MyClass");
        assertEquals(CodeSymbolKind.CLASS, cls.getKind());
        assertEquals("MyClass", cls.getName());
    }

    @Test
    void testMultibyteStringBeforeClass() {
        String source = "# \u65e5\u672c\u8a9e\u30b3\u30e1\u30f3\u30c8\n" +
                "# \u20ac symbol\n" +
                "class Foo:\n" +
                "    def bar(self):\n" +
                "        pass\n";

        CodeFileAnalysisResult result = analyzer.analyze("test.py", source);
        assertNotNull(result);

        CodeSymbol foo = findSymbol(result.getSymbols(), "Foo");
        assertNotNull(foo, "Should find Foo");
        assertEquals("Foo", foo.getName());

        CodeSymbol bar = findSymbol(result.getSymbols(), "bar");
        assertNotNull(bar, "Should find bar method");
    }

    @Test
    void testEmojiInSource() {
        String source = "# emoji: \ud83d\ude00\ud83d\ude01\n" +
                "class Hello:\n" +
                "    def world(self):\n" +
                "        pass\n";

        CodeFileAnalysisResult result = analyzer.analyze("test.py", source);
        assertNotNull(result);

        CodeSymbol hello = findSymbol(result.getSymbols(), "Hello");
        assertNotNull(hello, "Should find Hello class even with emoji");
        assertEquals("Hello", hello.getName());
    }

    private CodeSymbol findSymbol(List<CodeSymbol> symbols, String name) {
        return symbols.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }
}
