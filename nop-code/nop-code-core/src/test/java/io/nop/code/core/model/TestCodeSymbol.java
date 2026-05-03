package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeSymbol {
    @Test
    void testGettersAndSetters() {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId("test-id");
        symbol.setKind(CodeSymbolKind.CLASS);
        symbol.setName("TestClass");
        symbol.setQualifiedName("com.example.TestClass");
        symbol.setAccessModifier(CodeAccessModifier.PUBLIC);
        symbol.setDocumentation("Test doc");
        symbol.setLine(10);
        symbol.setColumn(5);
        symbol.setEndLine(50);
        symbol.setEndColumn(1);
        symbol.setParentId("parent-id");
        symbol.setDeclaringSymbolId("decl-id");
        symbol.setSuperClassName("BaseClass");
        symbol.setSignature("testMethod(String)");
        symbol.setReturnType("void");
        symbol.setFieldType("String");
        symbol.setExtData("{\"custom\":true}");

        assertEquals("test-id", symbol.getId());
        assertEquals(CodeSymbolKind.CLASS, symbol.getKind());
        assertEquals("TestClass", symbol.getName());
        assertEquals("com.example.TestClass", symbol.getQualifiedName());
        assertEquals(CodeAccessModifier.PUBLIC, symbol.getAccessModifier());
        assertEquals("Test doc", symbol.getDocumentation());
        assertEquals(10, symbol.getLine());
        assertEquals(5, symbol.getColumn());
        assertEquals(50, symbol.getEndLine());
        assertEquals(1, symbol.getEndColumn());
        assertEquals("parent-id", symbol.getParentId());
        assertEquals("decl-id", symbol.getDeclaringSymbolId());
        assertEquals("BaseClass", symbol.getSuperClassName());
        assertEquals("testMethod(String)", symbol.getSignature());
        assertEquals("void", symbol.getReturnType());
        assertEquals("String", symbol.getFieldType());
        assertEquals("{\"custom\":true}", symbol.getExtData());
    }

    @Test
    void testDefaultBooleanValues() {
        CodeSymbol symbol = new CodeSymbol();
        assertFalse(symbol.isDeprecated());
        assertFalse(symbol.isAbstractFlag());
        assertFalse(symbol.isFinalFlag());
        assertFalse(symbol.isStaticFlag());
        assertFalse(symbol.isAsyncFlag());
        assertFalse(symbol.isReadonlyFlag());
    }

    @Test
    void testExtDataCanHoldJson() {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setExtData("{\"synchronized\":true,\"native\":false}");
        assertTrue(symbol.getExtData().contains("synchronized"));
    }
}
