package io.nop.code.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeSymbol {

    @Test
    void testDefaultBooleanValuesAreFalse() {
        CodeSymbol symbol = new CodeSymbol();
        assertFalse(symbol.isDeprecated());
        assertFalse(symbol.isAbstractFlag());
        assertFalse(symbol.isFinalFlag());
        assertFalse(symbol.isStaticFlag());
        assertFalse(symbol.isAsyncFlag());
        assertFalse(symbol.isReadonlyFlag());
    }

    @Test
    void testQualifiedNameAndKindFormIdentity() {
        CodeSymbol a = new CodeSymbol();
        a.setId("com.example.User");
        a.setQualifiedName("com.example.User");
        a.setName("User");
        a.setKind(CodeSymbolKind.CLASS);

        assertEquals("com.example.User", a.getQualifiedName());
        assertEquals("User", a.getName());
        assertEquals(CodeSymbolKind.CLASS, a.getKind());
    }
}
