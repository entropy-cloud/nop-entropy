package io.nop.treesitter.cursor;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2: field-name lookup through the grammar's blob v2 field tables — the
 * Language field-map accessors, parse-time production-id retention and the
 * cursor field API ({@code currentFieldName}, field-directed access).
 */
public class CursorFieldTest {

    private static Language JSON;
    private static Language JAVA;

    @BeforeAll
    static void loadLanguages() {
        JSON = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        JAVA = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
    }

    @Test
    void jsonPairChildrenResolveKeyAndValueFields() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("object", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("pair", cursor.currentNode().type());
        assertNull(cursor.currentFieldName(), "a pair inside an object occupies no field slot");

        assertTrue(cursor.gotoFirstChild());
        assertEquals("string", cursor.currentNode().type());
        assertEquals("key", cursor.currentFieldName(), "the pair's first child is field 'key'");
        assertTrue(cursor.gotoNextSibling());
        assertEquals(":", cursor.currentNode().type());
        assertNull(cursor.currentFieldName(), "the anonymous ':' token occupies no field slot");
        assertTrue(cursor.gotoNextSibling());
        assertEquals("number", cursor.currentNode().type());
        assertEquals("value", cursor.currentFieldName(),
                "the pair's value child resolves through the hidden _value wrapper");
    }

    @Test
    void jsonFieldDirectedAccess() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1, \"b\": 2}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("object", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("pair", cursor.currentNode().type());

        assertTrue(cursor.gotoChildByFieldName("key"));
        assertEquals("string", cursor.currentNode().type());
        assertTrue(cursor.gotoParent());
        assertTrue(cursor.gotoChildByFieldName("value"));
        assertEquals("number", cursor.currentNode().type());

        int keyId = JSON.fieldId("key");
        assertTrue(keyId > 0);
        assertTrue(cursor.gotoParent());
        assertTrue(cursor.gotoChildByFieldId(keyId));
        assertEquals("string", cursor.currentNode().type());
    }

    @Test
    void javaMethodDeclarationChildrenResolveFields() {
        TSTree tree = TSParser.parse(JAVA, "class A {\n  public int b() {\n    int c = 5;\n  }\n}");
        TSTreeCursor cursor = tree.cursor();
        assertEquals("program", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstChild());
        assertEquals("class_declaration", cursor.currentNode().type());
        assertTrue(cursor.gotoChildByFieldName("body"));
        assertEquals("class_body", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("method_declaration", cursor.currentNode().type());

        assertEquals(5, cursor.childCount(), "method_declaration: modifiers, type, name, parameters, body");
        assertTrue(cursor.gotoFirstChild());
        assertEquals("modifiers", cursor.currentNode().type());
        assertNull(cursor.currentFieldName(), "modifiers occupies no field slot");
        assertTrue(cursor.gotoNextSibling());
        assertEquals("integral_type", cursor.currentNode().type());
        assertEquals("type", cursor.currentFieldName());
        assertTrue(cursor.gotoNextSibling());
        assertEquals("identifier", cursor.currentNode().type());
        assertEquals("name", cursor.currentFieldName());
        assertTrue(cursor.gotoNextSibling());
        assertEquals("formal_parameters", cursor.currentNode().type());
        assertEquals("parameters", cursor.currentFieldName());
        assertTrue(cursor.gotoNextSibling());
        assertEquals("block", cursor.currentNode().type());
        assertEquals("body", cursor.currentFieldName());
    }

    @Test
    void javaFieldDirectedAccessThroughInheritedEntries() {
        TSTree tree = TSParser.parse(JAVA, "class A {\n  int c = 5;\n}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("class_declaration", cursor.currentNode().type());
        assertTrue(cursor.gotoChildByFieldName("body"));
        assertEquals("class_body", cursor.currentNode().type());
        assertTrue(cursor.gotoFirstNamedChild());
        assertEquals("field_declaration", cursor.currentNode().type());

        assertTrue(cursor.gotoChildByFieldName("declarator"),
                "field 'declarator' resolves on field_declaration");
        assertEquals("variable_declarator", cursor.currentNode().type());
        assertTrue(cursor.gotoChildByFieldName("name"));
        assertEquals("identifier", cursor.currentNode().type());
        assertTrue(cursor.gotoParent());
        assertTrue(cursor.gotoChildByFieldName("value"));
        assertEquals("decimal_integer_literal", cursor.currentNode().type());
    }

    @Test
    void fieldNameReturnsNullForFieldlessNodesAndRoot() {
        TSTree tree = TSParser.parse(JSON, "1");
        TSTreeCursor cursor = tree.cursor();
        assertNull(cursor.currentFieldName(), "the cursor root occupies no field slot");
        assertTrue(cursor.gotoFirstChild());
        assertEquals("number", cursor.currentNode().type());
        assertNull(cursor.currentFieldName(), "a number inside an array-free document has no field");
    }

    @Test
    void unknownFieldAndOutOfRangeAccessAreDefined() {
        TSTree tree = TSParser.parse(JSON, "{\"a\": 1}");
        TSTreeCursor cursor = tree.cursor();
        assertTrue(cursor.gotoFirstChild());
        assertEquals("object", cursor.currentNode().type());

        assertEquals(0, JSON.fieldId("no_such_field"), "unknown field name resolves to id 0");
        assertFalse(cursor.gotoChildByFieldName("no_such_field"), "unknown field name returns false");
        assertFalse(cursor.gotoChildByFieldId(999), "out-of-range field id returns false");
        assertEquals("object", cursor.currentNode().type(), "failed field access leaves the cursor unchanged");

        assertThrows(TreeSitterException.class, () -> JSON.fieldName(999),
                "out-of-range field id raises a typed exception");
        assertThrows(TreeSitterException.class, () -> JSON.fieldMap(999_999),
                "out-of-range production id raises a typed exception");
    }

    @Test
    void languageExposesFieldTablesFromTheBlob() {
        assertEquals(2, JSON.fieldCount(), "JSON grammar declares 2 fields");
        assertEquals(40, JAVA.fieldCount(), "Java grammar declares 40 fields");
        assertEquals("key", JSON.fieldName(JSON.fieldId("key")));
        assertEquals("value", JSON.fieldName(JSON.fieldId("value")));

        Language.FieldMapEntry[] pairMap = JSON.fieldMap(1);
        assertNotNull(pairMap);
        assertEquals(2, pairMap.length);
        assertEquals(0, pairMap[0].childIndex());
        assertEquals(2, pairMap[1].childIndex());
        assertFalse(pairMap[0].inherited(), "JSON pair field entries are not inherited");
    }
}