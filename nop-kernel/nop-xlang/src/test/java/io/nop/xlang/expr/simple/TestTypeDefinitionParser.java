package io.nop.xlang.expr.simple;

import io.nop.xlang.ast.ArrayTypeNode;
import io.nop.xlang.ast.FunctionArgTypeDef;
import io.nop.xlang.ast.FunctionTypeDef;
import io.nop.xlang.ast.IntersectionTypeDef;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.ObjectTypeDef;
import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.PropertyTypeDef;
import io.nop.xlang.ast.TupleTypeDef;
import io.nop.xlang.ast.TypeAliasDeclaration;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.UnionTypeDef;

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

    // ==================== Union Types ====================

    @Test
    public void testSimpleUnionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type StringOrNumber = string | number";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        assertNotNull(declaration);
        assertEquals("StringOrNumber", declaration.getTypeName().getName());

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertNotNull(unionTypeDef);
        assertEquals(2, unionTypeDef.getTypes().size());

        TypeNameNode type1 = (TypeNameNode) unionTypeDef.getTypes().get(0);
        assertEquals("string", type1.getTypeName());

        TypeNameNode type2 = (TypeNameNode) unionTypeDef.getTypes().get(1);
        assertEquals("number", type2.getTypeName());
    }

    @Test
    public void testMultipleUnionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type MultipleTypes = string | number | boolean";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertEquals(3, unionTypeDef.getTypes().size());

        assertEquals("string", ((TypeNameNode) unionTypeDef.getTypes().get(0)).getTypeName());
        assertEquals("number", ((TypeNameNode) unionTypeDef.getTypes().get(1)).getTypeName());
        assertEquals("boolean", ((TypeNameNode) unionTypeDef.getTypes().get(2)).getTypeName());
    }

    @Test
    public void testUnionTypeWithCustomTypes() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Result = Success | Error | Warning";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertEquals(3, unionTypeDef.getTypes().size());

        assertEquals("Success", ((TypeNameNode) unionTypeDef.getTypes().get(0)).getTypeName());
        assertEquals("Error", ((TypeNameNode) unionTypeDef.getTypes().get(1)).getTypeName());
        assertEquals("Warning", ((TypeNameNode) unionTypeDef.getTypes().get(2)).getTypeName());
    }

    @Test
    public void testUnionTypeWithObjectTypes() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type MixedType = TypeA | TypeB";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertEquals(2, unionTypeDef.getTypes().size());

        TypeNameNode type1 = (TypeNameNode) unionTypeDef.getTypes().get(0);
        assertEquals("TypeA", type1.getTypeName());

        TypeNameNode type2 = (TypeNameNode) unionTypeDef.getTypes().get(1);
        assertEquals("TypeB", type2.getTypeName());
    }

    // ==================== Intersection Types ====================

    @Test
    public void testSimpleIntersectionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Named = TypeA & TypeB";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        assertNotNull(declaration);
        assertEquals("Named", declaration.getTypeName().getName());

        IntersectionTypeDef intersectionTypeDef = (IntersectionTypeDef) declaration.getDefType();
        assertNotNull(intersectionTypeDef);
        assertEquals(2, intersectionTypeDef.getTypes().size());

        TypeNameNode type1 = (TypeNameNode) intersectionTypeDef.getTypes().get(0);
        assertEquals("TypeA", type1.getTypeName());

        TypeNameNode type2 = (TypeNameNode) intersectionTypeDef.getTypes().get(1);
        assertEquals("TypeB", type2.getTypeName());
    }

    @Test
    public void testMultipleIntersectionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type TypeC = Type1 & Type2 & Type3";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        IntersectionTypeDef intersectionTypeDef = (IntersectionTypeDef) declaration.getDefType();
        assertEquals(3, intersectionTypeDef.getTypes().size());
    }

    @Test
    public void testIntersectionWithNamedTypes() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type TypeD = TypeX & TypeY & TypeZ";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        IntersectionTypeDef intersectionTypeDef = (IntersectionTypeDef) declaration.getDefType();
        assertEquals(3, intersectionTypeDef.getTypes().size());

        assertEquals("TypeX", ((TypeNameNode) intersectionTypeDef.getTypes().get(0)).getTypeName());
        assertEquals("TypeY", ((TypeNameNode) intersectionTypeDef.getTypes().get(1)).getTypeName());
        assertEquals("TypeZ", ((TypeNameNode) intersectionTypeDef.getTypes().get(2)).getTypeName());
    }

    // ==================== Array Types ====================

    @Test
    public void testSimpleArrayType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Numbers = int[]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ArrayTypeNode arrayType = (ArrayTypeNode) declaration.getDefType();
        assertNotNull(arrayType);

        TypeNameNode componentType = (TypeNameNode) arrayType.getComponentType();
        assertEquals("int", componentType.getTypeName());
    }

    @Test
    public void testNestedArrayType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Matrix = int[][]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ArrayTypeNode outerArray = (ArrayTypeNode) declaration.getDefType();
        ArrayTypeNode innerArray = (ArrayTypeNode) outerArray.getComponentType();

        TypeNameNode componentType = (TypeNameNode) innerArray.getComponentType();
        assertEquals("int", componentType.getTypeName());
    }

    @Test
    public void testArrayOfCustomType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type UserArray = User[]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ArrayTypeNode arrayType = (ArrayTypeNode) declaration.getDefType();
        TypeNameNode componentType = (TypeNameNode) arrayType.getComponentType();
        assertEquals("User", componentType.getTypeName());
    }

    @Test
    public void testUnionOfArrayTypes() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type MixedArray = int[] | string[]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertEquals(2, unionTypeDef.getTypes().size());

        ArrayTypeNode array1 = (ArrayTypeNode) unionTypeDef.getTypes().get(0);
        assertEquals("int", ((TypeNameNode) array1.getComponentType()).getTypeName());

        ArrayTypeNode array2 = (ArrayTypeNode) unionTypeDef.getTypes().get(1);
        assertEquals("string", ((TypeNameNode) array2.getComponentType()).getTypeName());
    }

    // ==================== Tuple Types ====================

    @Test
    public void testSimpleTupleType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Point = [number, number]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        TupleTypeDef tupleTypeDef = (TupleTypeDef) declaration.getDefType();
        assertNotNull(tupleTypeDef);
        assertEquals(2, tupleTypeDef.getTypes().size());

        TypeNameNode type1 = (TypeNameNode) tupleTypeDef.getTypes().get(0);
        assertEquals("number", type1.getTypeName());

        TypeNameNode type2 = (TypeNameNode) tupleTypeDef.getTypes().get(1);
        assertEquals("number", type2.getTypeName());
    }

    @Test
    public void testMultiElementTuple() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Record = [string, number, boolean, any]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        TupleTypeDef tupleTypeDef = (TupleTypeDef) declaration.getDefType();
        assertEquals(4, tupleTypeDef.getTypes().size());

        assertEquals("string", ((TypeNameNode) tupleTypeDef.getTypes().get(0)).getTypeName());
        assertEquals("number", ((TypeNameNode) tupleTypeDef.getTypes().get(1)).getTypeName());
        assertEquals("boolean", ((TypeNameNode) tupleTypeDef.getTypes().get(2)).getTypeName());
        assertEquals("any", ((TypeNameNode) tupleTypeDef.getTypes().get(3)).getTypeName());
    }

    @Test
    public void testTupleWithCustomTypes() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type UserInfo = [User, Profile]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        TupleTypeDef tupleTypeDef = (TupleTypeDef) declaration.getDefType();
        assertEquals(2, tupleTypeDef.getTypes().size());

        assertEquals("User", ((TypeNameNode) tupleTypeDef.getTypes().get(0)).getTypeName());
        assertEquals("Profile", ((TypeNameNode) tupleTypeDef.getTypes().get(1)).getTypeName());
    }

    // ==================== Function Types ====================

    @Test
    public void testSimpleFunctionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Callback = (value:string) => void";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        FunctionTypeDef functionTypeDef = (FunctionTypeDef) declaration.getDefType();
        assertNotNull(functionTypeDef);
        assertEquals(1, functionTypeDef.getArgs().size());

        FunctionArgTypeDef arg = functionTypeDef.getArgs().get(0);
        assertEquals("string", ((TypeNameNode) arg.getArgType()).getTypeName());

        TypeNameNode returnType = (TypeNameNode) functionTypeDef.getReturnType();
        assertEquals("void", returnType.getTypeName());
    }

    @Test
    public void testMultiArgFunctionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Validator = (name:string, age:number) => boolean";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        FunctionTypeDef functionTypeDef = (FunctionTypeDef) declaration.getDefType();
        assertEquals(2, functionTypeDef.getArgs().size());

        FunctionArgTypeDef arg1 = functionTypeDef.getArgs().get(0);
        assertEquals("name", arg1.getArgName().getName());
        assertEquals("string", ((TypeNameNode) arg1.getArgType()).getTypeName());

        FunctionArgTypeDef arg2 = functionTypeDef.getArgs().get(1);
        assertEquals("age", arg2.getArgName().getName());
        assertEquals("number", ((TypeNameNode) arg2.getArgType()).getTypeName());

        TypeNameNode returnType = (TypeNameNode) functionTypeDef.getReturnType();
        assertEquals("boolean", returnType.getTypeName());
    }

    @Test
    public void testFunctionTypeWithoutArgNames() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Predicate = (string) => boolean";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        FunctionTypeDef functionTypeDef = (FunctionTypeDef) declaration.getDefType();
        assertEquals(1, functionTypeDef.getArgs().size());

        FunctionArgTypeDef arg = functionTypeDef.getArgs().get(0);
        assertNull(arg.getArgName());
        assertEquals("string", ((TypeNameNode) arg.getArgType()).getTypeName());

        TypeNameNode returnType = (TypeNameNode) functionTypeDef.getReturnType();
        assertEquals("boolean", returnType.getTypeName());
    }

    // ==================== Parameterized Types ====================

    @Test
    public void testParameterizedType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type StringList = List<string>";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ParameterizedTypeNode paramType = (ParameterizedTypeNode) declaration.getDefType();
        assertNotNull(paramType);
        assertEquals("List", paramType.getTypeName());
        assertEquals(1, paramType.getTypeArgs().size());

        TypeNameNode typeArg = (TypeNameNode) paramType.getTypeArgs().get(0);
        assertEquals("string", typeArg.getTypeName());
    }

    @Test
    public void testMultiTypeParameterizedType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type StringNumberMap = Map<string, number>";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ParameterizedTypeNode paramType = (ParameterizedTypeNode) declaration.getDefType();
        assertEquals("Map", paramType.getTypeName());
        assertEquals(2, paramType.getTypeArgs().size());

        TypeNameNode typeArg1 = (TypeNameNode) paramType.getTypeArgs().get(0);
        assertEquals("string", typeArg1.getTypeName());

        TypeNameNode typeArg2 = (TypeNameNode) paramType.getTypeArgs().get(1);
        assertEquals("number", typeArg2.getTypeName());
    }

    @Test
    public void testNestedParameterizedType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type ListOfLists = List<List<string>>";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ParameterizedTypeNode outerParam = (ParameterizedTypeNode) declaration.getDefType();
        assertEquals("List", outerParam.getTypeName());
        assertEquals(1, outerParam.getTypeArgs().size());

        ParameterizedTypeNode innerParam = (ParameterizedTypeNode) outerParam.getTypeArgs().get(0);
        assertEquals("List", innerParam.getTypeName());
        assertEquals(1, innerParam.getTypeArgs().size());

        TypeNameNode typeArg = (TypeNameNode) innerParam.getTypeArgs().get(0);
        assertEquals("string", typeArg.getTypeName());
    }

    // ==================== Complex Combined Types ====================

    @Test
    public void testComplexTypeWithUnion() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Complex = TypeA | TypeB";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertEquals(2, unionTypeDef.getTypes().size());

        TypeNameNode type1 = (TypeNameNode) unionTypeDef.getTypes().get(0);
        assertEquals("TypeA", type1.getTypeName());

        TypeNameNode type2 = (TypeNameNode) unionTypeDef.getTypes().get(1);
        assertEquals("TypeB", type2.getTypeName());
    }

    @Test
    public void testUnionWithParameterizedTypes() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type Collection = List<string> | Map<string, number>";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        UnionTypeDef unionTypeDef = (UnionTypeDef) declaration.getDefType();
        assertEquals(2, unionTypeDef.getTypes().size());

        ParameterizedTypeNode type1 = (ParameterizedTypeNode) unionTypeDef.getTypes().get(0);
        assertEquals("List", type1.getTypeName());

        ParameterizedTypeNode type2 = (ParameterizedTypeNode) unionTypeDef.getTypes().get(1);
        assertEquals("Map", type2.getTypeName());
    }

    @Test
    public void testArrayWithParameterizedType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type UserLists = User[]";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        ArrayTypeNode arrayType = (ArrayTypeNode) declaration.getDefType();
        TypeNameNode componentType = (TypeNameNode) arrayType.getComponentType();
        assertEquals("User", componentType.getTypeName());
    }

    @Test
    public void testFunctionReturningUnionType() {
        TypeDefinitionParser parser = new TypeDefinitionParser();
        String source = "type AsyncResult = (value: number) => string";

        TypeAliasDeclaration declaration = parser.parseTypeDefinition(source);

        FunctionTypeDef functionTypeDef = (FunctionTypeDef) declaration.getDefType();
        assertEquals(1, functionTypeDef.getArgs().size());

        TypeNameNode returnType = (TypeNameNode) functionTypeDef.getReturnType();
        assertEquals("string", returnType.getTypeName());
    }
}
