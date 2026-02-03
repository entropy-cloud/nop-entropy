package io.nop.xlang.expr.simple;

import io.nop.xlang.ast.ObjectTypeDef;
import io.nop.xlang.ast.PropertyTypeDef;
import io.nop.xlang.ast.TypeAliasDeclaration;
import io.nop.xlang.ast.TypeNameNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTypeDefinitionParser {

    @Test
    public void testSimpleTypeDefinition() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type A = {a:int,b:string}";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        assertNotNull(declaration);
        assertEquals("A", declaration.getTypeName().getName());

        ObjectTypeDef objectTypeDef = (ObjectTypeDef) declaration.getDefType();
        assertNotNull(objectTypeDef);
        assertEquals(2, objectTypeDef.getTypes().size());

        PropertyTypeDef propA = objectTypeDef.getTypes().get(0);
        assertEquals("a", propA.getName());
        assertFalse(propA.getOptional());
        assertEquals("int", ((TypeNameNode) propA.getValueType()).getTypeName());

        PropertyTypeDef propB = objectTypeDef.getTypes().get(1);
        assertEquals("b", propB.getName());
        assertFalse(propB.getOptional());
        assertEquals("string", ((TypeNameNode) propB.getValueType()).getTypeName());
    }

    @Test
    public void testOptionalProperties() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type B = {x?:number,y?:string}";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ObjectTypeDef objectTypeDef = (ObjectTypeDef) declaration.getDefType();
        PropertyTypeDef propX = objectTypeDef.getTypes().get(0);
        assertTrue(propX.getOptional());
        PropertyTypeDef propY = objectTypeDef.getTypes().get(1);
        assertTrue(propY.getOptional());
    }

    @Test
    public void testWithLeadingComment() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "// Type A comment\ntype A = {a:int,b:string}";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        assertEquals("Type A comment", declaration.getLeadingComment());
    }

    @Test
    public void testWithPropertyComments() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type A = {\n" +
                "  // Property a comment\n" +
                "  a:int,\n" +
                "  // Property b comment\n" +
                "  b:string\n" +
                "}";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ObjectTypeDef objectTypeDef = (ObjectTypeDef) declaration.getDefType();
        PropertyTypeDef propA = objectTypeDef.getTypes().get(0);
        assertEquals("Property a comment", propA.getLeadingComment());

        PropertyTypeDef propB = objectTypeDef.getTypes().get(1);
        assertEquals("Property b comment", propB.getLeadingComment());
    }

    @Test
    public void testWithBlockComment() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "/* Block comment for type A */\ntype A = {a:int}";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        assertEquals("Block comment for type A", declaration.getLeadingComment());
    }

    @Test
    public void testWithoutTypeAnnotation() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type A = {a,b}";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ObjectTypeDef objectTypeDef = (ObjectTypeDef) declaration.getDefType();
        PropertyTypeDef propA = objectTypeDef.getTypes().get(0);
        assertEquals("a", propA.getName());
        assertEquals("any", ((TypeNameNode) propA.getValueType()).getTypeName());

        PropertyTypeDef propB = objectTypeDef.getTypes().get(1);
        assertEquals("b", propB.getName());
        assertEquals("any", ((TypeNameNode) propB.getValueType()).getTypeName());
    }
}
