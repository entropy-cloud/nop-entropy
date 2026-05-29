package io.nop.code.core.graph;

import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSymbolTable {
    private CodeSymbol createSymbol(String id, String qualifiedName) {
        CodeSymbol s = new CodeSymbol();
        s.setId(id);
        s.setQualifiedName(qualifiedName);
        s.setKind(CodeSymbolKind.CLASS);
        return s;
    }

    @Test
    void testAddAndGetByQualifiedName() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("id1", "com.example.Foo"));
        assertNotNull(table.getByQualifiedName("com.example.Foo"));
    }

    @Test
    void testGetById() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("id1", "com.example.Foo"));
        assertNotNull(table.getById("id1"));
        assertEquals("com.example.Foo", table.getById("id1").getQualifiedName());
    }

    @Test
    void testGetAllReturnsAll() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("id1", "com.example.Foo"));
        table.add(createSymbol("id2", "com.example.Bar"));
        assertEquals(2, table.getAll().size());
    }

    @Test
    void testDuplicateQualifiedNameOverwrites() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("id1", "com.example.Foo"));
        table.add(createSymbol("id2", "com.example.Foo"));
        assertEquals(2, table.getAll().size());
        assertEquals("id2", table.getByQualifiedName("com.example.Foo").getId());
        assertNotNull(table.getById("id1"));
        assertNotNull(table.getById("id2"));
    }

    @Test
    void testGetAllIncludesSymbolsWithoutQualifiedName() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("id1", "com.example.Foo"));

        CodeSymbol anon = new CodeSymbol();
        anon.setId("id2");
        anon.setKind(CodeSymbolKind.CLASS);
        table.add(anon);

        assertEquals(2, table.getAll().size());
        assertEquals(2, table.size());
        assertNotNull(table.getById("id2"));
    }
}
