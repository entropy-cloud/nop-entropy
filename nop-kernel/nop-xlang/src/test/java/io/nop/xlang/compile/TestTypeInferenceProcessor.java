/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.regex.IRegex;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IUnionType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.ast.*;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_TYPE_INFERENCE_ENABLED;
import static io.nop.xlang.XLangErrors.ERR_TYPE_INFER_INCOMPATIBLE_TYPES;
import static org.junit.jupiter.api.Assertions.*;

public class TestTypeInferenceProcessor extends BaseTestCase {

    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLiteralTypeInference() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        Literal stringLiteral = new Literal();
        stringLiteral.setValue("hello");
        
        ReturnTypeInfo result = processor.processLiteral(stringLiteral, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
        assertEquals(PredefinedGenericTypes.STRING_TYPE, stringLiteral.getReturnTypeInfo());

        Literal intLiteral = new Literal();
        intLiteral.setValue(123);
        
        result = processor.processLiteral(intLiteral, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());

        Literal boolLiteral = new Literal();
        boolLiteral.setValue(true);
        
        result = processor.processLiteral(boolLiteral, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());

        Literal nullLiteral = new Literal();
        nullLiteral.setValue(null);
        
        result = processor.processLiteral(nullLiteral, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.NULL_TYPE, result.getReturnType());
    }

    @Test
    public void testBinaryExpressionTypeInference() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        // 1 + 2 should be int
        BinaryExpression addExpr = new BinaryExpression();
        Literal left = new Literal();
        left.setValue(1);
        Literal right = new Literal();
        right.setValue(2);
        addExpr.setLeft(left);
        addExpr.setRight(right);
        addExpr.setOperator(XLangOperator.ADD);

        ReturnTypeInfo result = processor.processBinaryExpression(addExpr, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());

        // "a" + 1 should be string
        BinaryExpression concatExpr = new BinaryExpression();
        Literal strLeft = new Literal();
        strLeft.setValue("a");
        Literal intRight = new Literal();
        intRight.setValue(1);
        concatExpr.setLeft(strLeft);
        concatExpr.setRight(intRight);
        concatExpr.setOperator(XLangOperator.ADD);

        result = processor.processBinaryExpression(concatExpr, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());

        // 1 < 2 should be boolean
        BinaryExpression compareExpr = new BinaryExpression();
        Literal cmpLeft = new Literal();
        cmpLeft.setValue(1);
        Literal cmpRight = new Literal();
        cmpRight.setValue(2);
        compareExpr.setLeft(cmpLeft);
        compareExpr.setRight(cmpRight);
        compareExpr.setOperator(XLangOperator.LT);

        result = processor.processBinaryExpression(compareExpr, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testIdentifierTypeInference() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        state.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        Identifier id = new Identifier();
        id.setName("x");

        ReturnTypeInfo result = processor.processIdentifier(id, state);
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
        assertEquals(PredefinedGenericTypes.STRING_TYPE, id.getReturnTypeInfo());
    }

    @Test
    public void testVariableDeclaratorInference() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        VariableDeclarator decl = new VariableDeclarator();
        Identifier id = new Identifier();
        id.setName("x");
        decl.setId(id);
        
        Literal init = new Literal();
        init.setValue("hello");
        decl.setInit(init);

        processor.processVariableDeclarator(decl, state);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("x"));
    }

    @Test
    public void testTypeInferenceStateScope() {
        TypeInferenceState parent = new TypeInferenceState();
        parent.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        TypeInferenceState child = parent.newChild();
        assertNotNull(child);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, child.getVariableType("x"));
        
        child.setVariableType("y", PredefinedGenericTypes.INT_TYPE);
        assertEquals(PredefinedGenericTypes.INT_TYPE, child.getVariableType("y"));
        assertNull(parent.getVariableType("y"));

        child.setNarrowedType("x", PredefinedGenericTypes.ANY_TYPE);
        assertEquals(PredefinedGenericTypes.ANY_TYPE, child.getVariableType("x"));
    }

    @Test
    public void testUnionTypeNarrowingFromCondition() {
        TypeInferenceState state = new TypeInferenceState();
        
        state.setVariableType("x", createUnionType(
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.NULL_TYPE));

        BinaryExpression condition = new BinaryExpression();
        condition.setOperator(XLangOperator.NE);
        Identifier x = new Identifier();
        x.setName("x");
        condition.setLeft(x);
        Literal nullLit = new Literal();
        nullLit.setValue(null);
        condition.setRight(nullLit);

        Map<String, IGenericType> narrowedTypes = UnionTypeNarrower.collectNarrowedTypes(condition, true);
        
        assertNotNull(narrowedTypes);
    }

    @Test
    public void testMemberExpressionOnList() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        
        state.setVariableType("list", PredefinedGenericTypes.LIST_ANY_TYPE);

        MemberExpression member = new MemberExpression();
        Identifier list = new Identifier();
        list.setName("list");
        member.setObject(list);
        
        Identifier prop = new Identifier();
        prop.setName("length");
        member.setProperty(prop);

        ReturnTypeInfo result = processor.processMemberExpression(member, state);
        
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
    }

    @Test
    public void testMemberExpressionOnMap() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        
        state.setVariableType("map", PredefinedGenericTypes.MAP_TYPE);

        MemberExpression member = new MemberExpression();
        Identifier map = new Identifier();
        map.setName("map");
        member.setObject(map);
        
        Identifier prop = new Identifier();
        prop.setName("size");
        member.setProperty(prop);

        ReturnTypeInfo result = processor.processMemberExpression(member, state);
        
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
    }

    @Test
    public void testMemberExpressionOnString() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        
        state.setVariableType("str", PredefinedGenericTypes.STRING_TYPE);

        MemberExpression member = new MemberExpression();
        Identifier str = new Identifier();
        str.setName("str");
        member.setObject(str);
        
        Identifier prop = new Identifier();
        prop.setName("length");
        member.setProperty(prop);

        ReturnTypeInfo result = processor.processMemberExpression(member, state);
        
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
    }

    @Test
    public void testNumericTypePromotion() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        BinaryExpression expr = new BinaryExpression();
        expr.setOperator(XLangOperator.ADD);
        
        Literal left = new Literal();
        left.setValue(1L);
        expr.setLeft(left);
        
        Literal right = new Literal();
        right.setValue(2);
        expr.setRight(right);

        ReturnTypeInfo result = processor.processBinaryExpression(expr, state);
        
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.LONG_TYPE, result.getReturnType());
    }

    @Test
    public void testNumericTypePromotionDouble() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        BinaryExpression expr = new BinaryExpression();
        expr.setOperator(XLangOperator.ADD);
        
        Literal left = new Literal();
        left.setValue(1.0);
        expr.setLeft(left);
        
        Literal right = new Literal();
        right.setValue(2);
        expr.setRight(right);

        ReturnTypeInfo result = processor.processBinaryExpression(expr, state);
        
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.DOUBLE_TYPE, result.getReturnType());
    }

    @Test
    public void testComparisonOperators() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        XLangOperator[] compareOps = {XLangOperator.LT, XLangOperator.LE, XLangOperator.GT, XLangOperator.GE};
        
        for (XLangOperator op : compareOps) {
            BinaryExpression expr = new BinaryExpression();
            expr.setOperator(op);
            
            Literal left = new Literal();
            left.setValue(1);
            expr.setLeft(left);
            
            Literal right = new Literal();
            right.setValue(2);
            expr.setRight(right);

            ReturnTypeInfo result = processor.processBinaryExpression(expr, state);
            
            assertNotNull(result);
            assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
        }
    }

    @Test
    public void testArithmeticOperators() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        XLangOperator[] arithOps = {XLangOperator.MINUS, XLangOperator.MULTIPLY, XLangOperator.DIVIDE, XLangOperator.MOD};
        
        for (XLangOperator op : arithOps) {
            BinaryExpression expr = new BinaryExpression();
            expr.setOperator(op);
            
            Literal left = new Literal();
            left.setValue(10);
            expr.setLeft(left);
            
            Literal right = new Literal();
            right.setValue(2);
            expr.setRight(right);

            ReturnTypeInfo result = processor.processBinaryExpression(expr, state);
            
            assertNotNull(result);
            assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
        }
    }

    @Test
    public void testLogicalExpressionType() {
        LogicalExpression expr = new LogicalExpression();
        expr.setOperator(XLangOperator.AND);
        
        Literal left = new Literal();
        left.setValue(true);
        left.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);
        expr.setLeft(left);
        
        Literal right = new Literal();
        right.setValue(false);
        right.setReturnTypeInfo(PredefinedGenericTypes.BOOLEAN_TYPE);
        expr.setRight(right);

        Map<String, IGenericType> narrowed = UnionTypeNarrower.collectNarrowedTypes(expr, true);
        assertNotNull(narrowed);
    }

    @Test
    public void testVariableDeclaratorWithExplicitType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        VariableDeclarator decl = new VariableDeclarator();
        Identifier id = new Identifier();
        id.setName("x");
        decl.setId(id);
        
        TypeNameNode typeNode = TypeNameNode.fromTypeInfo(
                SourceLocation.fromClass(TestTypeInferenceProcessor.class),
                PredefinedGenericTypes.STRING_TYPE);
        decl.setVarType(typeNode);
        
        Literal init = new Literal();
        init.setValue(123);
        decl.setInit(init);

        processor.processVariableDeclarator(decl, state);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("x"));
    }

    @Test
    public void testUnionReturnType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        
        ReturnTypeInfo r1 = new ReturnTypeInfo();
        r1.setReturnType(PredefinedGenericTypes.STRING_TYPE);
        
        ReturnTypeInfo r2 = new ReturnTypeInfo();
        r2.setReturnType(PredefinedGenericTypes.INT_TYPE);

        ReturnTypeInfo union = processor.union(r1, r2);
        assertNotNull(union);
        assertNotNull(union.getReturnType());
        assertTrue(union.getReturnType() instanceof IUnionType);
        assertFalse(union.isOtherBranchNoReturn());
    }

    @Test
    public void testUnionSameType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        
        ReturnTypeInfo r1 = new ReturnTypeInfo();
        r1.setReturnType(PredefinedGenericTypes.STRING_TYPE);
        
        ReturnTypeInfo r2 = new ReturnTypeInfo();
        r2.setReturnType(PredefinedGenericTypes.STRING_TYPE);

        ReturnTypeInfo union = processor.union(r1, r2);
        assertNotNull(union);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, union.getReturnType());
        assertFalse(union.getReturnType() instanceof IUnionType);
    }

    @Test
    public void testUnionWithNull() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        
        ReturnTypeInfo r1 = new ReturnTypeInfo();
        r1.setReturnType(PredefinedGenericTypes.STRING_TYPE);

        ReturnTypeInfo result = processor.union(r1, null);
        
        assertNotNull(result);
        assertTrue(result.isOtherBranchNoReturn());
    }

    @Test
    public void testTypeErrorCollector() {
        TypeErrorCollector collector = new TypeErrorCollector();
        
        assertFalse(collector.hasErrors());
        assertEquals(0, collector.getErrors().size());
        
        collector.error(null, new NopException(ERR_TYPE_INFER_INCOMPATIBLE_TYPES).param("type", "test error"));
        
        assertTrue(collector.hasErrors());
        assertEquals(1, collector.getErrors().size());
    }

    @Test
    public void testCallExpressionWithNoCallee() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        CallExpression call = new CallExpression();
        Identifier callee = new Identifier();
        callee.setName("unknownFunc");
        call.setCallee(callee);
        call.setArguments(java.util.Collections.emptyList());

        ReturnTypeInfo result = processor.processCallExpression(call, state);
        
        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.ANY_TYPE, result.getReturnType());
    }

    @Test
    public void testProgramTracksSequentialAssignments() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        VariableDeclarator decl = new VariableDeclarator();
        decl.setId(Identifier.valueOf(null, "x"));
        Literal init = new Literal();
        init.setValue("s");
        decl.setInit(init);

        VariableDeclaration varDecl = new VariableDeclaration();
        varDecl.setDeclarators(java.util.Collections.singletonList(decl));

        AssignmentExpression assign = AssignmentExpression.valueOf(null,
                Identifier.valueOf(null, "x"), XLangOperator.ASSIGN, literal(1));

        Program program = new Program();
        program.setBody(java.util.Arrays.asList(varDecl, assign));

        processor.processProgram(program, state);

        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("x"));
    }

    @Test
    public void testReturnStatementChecksExpectedType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setCurrentReturnType(PredefinedGenericTypes.STRING_TYPE);

        ReturnStatement stmt = ReturnStatement.valueOf(null, literal(123));
        ReturnTypeInfo result = processor.processReturnStatement(stmt, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
        assertTrue(processor.getErrors().hasErrors());
        assertEquals(1, processor.getErrors().getErrors().size());
    }

    @Test
    public void testIfStatementMergesAssignedVariableTypes() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        AssignmentExpression assignInt = AssignmentExpression.valueOf(null,
                Identifier.valueOf(null, "x"), XLangOperator.ASSIGN, literal(1));
        AssignmentExpression assignLong = AssignmentExpression.valueOf(null,
                Identifier.valueOf(null, "x"), XLangOperator.ASSIGN, literal(2L));

        IfStatement stmt = new IfStatement();
        stmt.setTest(literal(true));
        stmt.setConsequent(assignInt);
        stmt.setAlternate(assignLong);

        processor.processIfStatement(stmt, state);

        IGenericType type = state.getVariableType("x");
        assertTrue(type instanceof IUnionType);
        assertUnionContains(type, PredefinedGenericTypes.INT_TYPE, PredefinedGenericTypes.LONG_TYPE);
    }

    @Test
    public void testIfStatementWithoutElseKeepsOriginalType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        IfStatement stmt = new IfStatement();
        stmt.setTest(literal(true));
        stmt.setConsequent(AssignmentExpression.valueOf(null,
                Identifier.valueOf(null, "x"), XLangOperator.ASSIGN, literal(1)));

        processor.processIfStatement(stmt, state);

        IGenericType type = state.getVariableType("x");
        assertTrue(type instanceof IUnionType);
        assertUnionContains(type, PredefinedGenericTypes.STRING_TYPE, PredefinedGenericTypes.INT_TYPE);
    }

    @Test
    public void testArrayExpressionInfersUnionElementType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        ReturnTypeInfo result = processor.processArrayExpression(ArrayExpression.valueOf(null,
                java.util.Arrays.asList(literal(1), literal("x"))), new TypeInferenceState());

        assertNotNull(result);
        assertTrue(result.getReturnType().isListLike());
        assertUnionContains(result.getReturnType().getComponentType(),
                PredefinedGenericTypes.INT_TYPE, PredefinedGenericTypes.STRING_TYPE);
    }

    @Test
    public void testObjectExpressionInfersMapValueType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        PropertyAssignment p1 = new PropertyAssignment();
        p1.setKey(literal("a"));
        p1.setValue(literal(1));

        PropertyAssignment p2 = new PropertyAssignment();
        p2.setKey(literal("b"));
        p2.setValue(literal("x"));

        ReturnTypeInfo result = processor.processObjectExpression(ObjectExpression.valueOf(null,
                java.util.Arrays.asList(p1, p2)), new TypeInferenceState());

        assertNotNull(result);
        assertTrue(result.getReturnType().isMapLike());
        assertUnionContains(result.getReturnType().getMapValueType(),
                PredefinedGenericTypes.INT_TYPE, PredefinedGenericTypes.STRING_TYPE);
    }

    @Test
    public void testCompoundAssignmentKeepsStringConcatenationType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        AssignmentExpression expr = AssignmentExpression.valueOf(null,
                Identifier.valueOf(null, "x"), XLangOperator.SELF_ASSIGN_ADD, literal(1));

        ReturnTypeInfo result = processor.processAssignmentExpression(expr, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("x"));
    }

    @Test
    public void testVariableDeclarationSupportsMultipleDeclarators() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        VariableDeclaration declaration = new VariableDeclaration();
        declaration.setDeclarators(java.util.Arrays.asList(
                VariableDeclarator.valueOf(null, Identifier.valueOf(null, "a"), null, literal(1)),
                VariableDeclarator.valueOf(null, Identifier.valueOf(null, "b"), null, literal("x"))));

        processor.processVariableDeclaration(declaration, state);

        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("a"));
        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("b"));
    }

    @Test
    public void testArrayBindingUsesInitializerElementType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        ArrayElementBinding e1 = new ArrayElementBinding();
        e1.setIdentifier(Identifier.valueOf(null, "a"));
        ArrayElementBinding e2 = new ArrayElementBinding();
        e2.setIdentifier(Identifier.valueOf(null, "b"));

        ArrayBinding binding = new ArrayBinding();
        binding.setElements(java.util.Arrays.asList(e1, e2));

        VariableDeclarator declarator = VariableDeclarator.valueOf(null, binding, null,
                ArrayExpression.valueOf(null, java.util.Arrays.asList(literal(1), literal(2))));

        processor.processVariableDeclarator(declarator, state);

        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("a"));
        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("b"));
    }

    @Test
    public void testObjectBindingUsesInitializerValueType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        PropertyBinding prop = new PropertyBinding();
        prop.setPropName("name");
        prop.setIdentifier(Identifier.valueOf(null, "name"));

        ObjectBinding binding = new ObjectBinding();
        binding.setProperties(java.util.Collections.singletonList(prop));

        VariableDeclarator declarator = VariableDeclarator.valueOf(null, binding, null,
                objectExpressionWithSingleProperty("name", literal("x")));

        processor.processVariableDeclarator(declarator, state);

        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("name"));
    }

    @Test
    public void testFunctionDeclarationInfersFunctionTypeFromReturn() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        ParameterDeclaration param = XLangASTBuilder.paramDecl(null, "a", PredefinedGenericTypes.STRING_TYPE);
        BlockStatement body = new BlockStatement();
        body.setBody(java.util.Collections.singletonList(
                ReturnStatement.valueOf(null, Identifier.valueOf(null, "a"))));

        FunctionDeclaration fn = new FunctionDeclaration();
        fn.setName(Identifier.valueOf(null, "echo"));
        fn.setParams(java.util.Collections.singletonList(param));
        fn.setBody(body);

        ReturnTypeInfo result = processor.processFunctionDeclaration(fn, state);

        assertNotNull(result);
        assertTrue(result.getReturnType().isFunction());
        IFunctionType fnType = (IFunctionType) result.getReturnType();
        assertEquals(PredefinedGenericTypes.STRING_TYPE, fnType.getFuncArgTypes().get(0));
        assertEquals(PredefinedGenericTypes.STRING_TYPE, fnType.getFuncReturnType());
        assertEquals(result.getReturnType(), state.getVariableType("echo"));
    }

    @Test
    public void testArrowFunctionExpressionInfersExpressionBodyType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ArrowFunctionExpression fn = new ArrowFunctionExpression();
        fn.setParams(java.util.Collections.singletonList(
                XLangASTBuilder.paramDecl(null, "x", PredefinedGenericTypes.INT_TYPE)));

        BinaryExpression body = new BinaryExpression();
        body.setLeft(Identifier.valueOf(null, "x"));
        body.setRight(literal(1));
        body.setOperator(XLangOperator.ADD);
        fn.setBody(body);

        ReturnTypeInfo result = processor.processArrowFunctionExpression(fn, new TypeInferenceState());

        assertNotNull(result);
        assertTrue(result.getReturnType().isFunction());
        IFunctionType fnType = (IFunctionType) result.getReturnType();
        assertEquals(PredefinedGenericTypes.INT_TYPE, fnType.getFuncArgTypes().get(0));
        assertEquals(PredefinedGenericTypes.INT_TYPE, fnType.getFuncReturnType());
    }

    @Test
    public void testCallExpressionAppliesGenericTypeArgumentsToReturnType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        IGenericType identityType = new io.nop.core.type.impl.GenericFunctionTypeImpl(
                java.util.Collections.singletonList(PredefinedGenericTypes.VARIABLE_T_TYPE),
                java.util.Collections.singletonList("value"),
                java.util.Collections.singletonList(PredefinedGenericTypes.VARIABLE_T_TYPE),
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        state.setVariableType("identity", identityType);

        CallExpression call = new CallExpression();
        call.setCallee(Identifier.valueOf(null, "identity"));
        call.setArguments(java.util.Collections.singletonList(literal("x")));

        ReturnTypeInfo result = processor.processCallExpression(call, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testMemberExpressionReturnsParameterizedListComponentType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("numbers", GenericTypeHelper.buildListType(PredefinedGenericTypes.INT_TYPE));

        MemberExpression member = new MemberExpression();
        member.setObject(Identifier.valueOf(null, "numbers"));
        member.setProperty(Identifier.valueOf(null, "first"));

        ReturnTypeInfo result = processor.processMemberExpression(member, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
    }

    @Test
    public void testUnaryNotReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        UnaryExpression expr = new UnaryExpression();
        expr.setOperator(XLangOperator.NOT);
        expr.setArgument(literal("x"));

        ReturnTypeInfo result = processor.processUnaryExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testUnaryMinusPreservesNumericType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        UnaryExpression expr = new UnaryExpression();
        expr.setOperator(XLangOperator.MINUS);
        expr.setArgument(literal(1L));

        ReturnTypeInfo result = processor.processUnaryExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.LONG_TYPE, result.getReturnType());
    }

    @Test
    public void testUpdateExpressionWritesBackNumericType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.INT_TYPE);

        UpdateExpression expr = new UpdateExpression();
        expr.setOperator(XLangOperator.SELF_INC);
        expr.setArgument(Identifier.valueOf(null, "x"));

        ReturnTypeInfo result = processor.processUpdateExpression(expr, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("x"));
    }

    @Test
    public void testTypeOfExpressionReturnsString() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        TypeOfExpression expr = new TypeOfExpression();
        expr.setArgument(literal(1));

        ReturnTypeInfo result = processor.processTypeOfExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testInstanceOfExpressionReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        InstanceOfExpression expr = new InstanceOfExpression();
        expr.setValue(Identifier.valueOf(null, "x"));
        expr.setRefType(XLangTypeHelper.buildTypeNode(PredefinedGenericTypes.STRING_TYPE));

        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.ANY_TYPE);

        ReturnTypeInfo result = processor.processInstanceOfExpression(expr, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testCastExpressionUsesDeclaredType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CastExpression expr = new CastExpression();
        expr.setValue(literal(1));
        expr.setAsType(XLangTypeHelper.buildTypeNode(PredefinedGenericTypes.STRING_TYPE));

        ReturnTypeInfo result = processor.processCastExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testSequenceExpressionReturnsLastType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        SequenceExpression expr = new SequenceExpression();
        expr.setExpressions(java.util.Arrays.asList(literal(1), literal("x")));

        ReturnTypeInfo result = processor.processSequenceExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testTemplateStringLiteralReturnsString() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        TemplateStringLiteral literal = new TemplateStringLiteral();
        literal.setValue("hello ${name}");

        ReturnTypeInfo result = processor.processTemplateStringLiteral(literal, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testAwaitExpressionPropagatesArgumentType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("promise", PredefinedGenericTypes.STRING_TYPE);

        AwaitExpression expr = new AwaitExpression();
        expr.setArgument(Identifier.valueOf(null, "promise"));

        ReturnTypeInfo result = processor.processAwaitExpression(expr, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testNewExpressionUsesDeclaredCalleeType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        NewExpression expr = new NewExpression();
        expr.setCallee(XLangTypeHelper.buildTypeNode(PredefinedGenericTypes.STRING_TYPE));
        expr.setArguments(java.util.Collections.singletonList(literal(1)));

        ReturnTypeInfo result = processor.processNewExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testForOfBindsLoopVariableToElementType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("items", GenericTypeHelper.buildListType(PredefinedGenericTypes.INT_TYPE));

        Identifier item = Identifier.valueOf(null, "item");

        ForOfStatement stmt = new ForOfStatement();
        stmt.setLeft(item);
        stmt.setRight(Identifier.valueOf(null, "items"));
        stmt.setBody(Identifier.valueOf(null, "item"));

        processor.processForOfStatement(stmt, state);

        assertEquals(PredefinedGenericTypes.INT_TYPE, item.getReturnTypeInfo());
    }

    @Test
    public void testForInBindsLoopVariableToString() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("map", GenericTypeHelper.buildMapType(PredefinedGenericTypes.INT_TYPE));

        Identifier key = Identifier.valueOf(null, "key");

        ForInStatement stmt = new ForInStatement();
        stmt.setLeft(key);
        stmt.setRight(Identifier.valueOf(null, "map"));
        stmt.setBody(Identifier.valueOf(null, "key"));

        processor.processForInStatement(stmt, state);

        assertEquals(PredefinedGenericTypes.STRING_TYPE, key.getReturnTypeInfo());
    }

    @Test
    public void testConcatExpressionReturnsString() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ConcatExpression expr = new ConcatExpression();
        expr.setExpressions(java.util.Arrays.asList(literal(1), literal("x")));

        ReturnTypeInfo result = processor.processConcatExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testBraceExpressionPropagatesInnerType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        BraceExpression expr = new BraceExpression();
        expr.setExpr(literal(1L));

        ReturnTypeInfo result = processor.processBraceExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.LONG_TYPE, result.getReturnType());
    }

    @Test
    public void testChainExpressionPropagatesInnerType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        ChainExpression expr = ChainExpression.valueOf(null, Identifier.valueOf(null, "x"), true);

        ReturnTypeInfo result = processor.processChainExpression(expr, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testInExpressionReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        InExpression expr = new InExpression();
        expr.setLeft(literal("name"));
        expr.setRight(objectExpressionWithSingleProperty("name", literal(1)));

        ReturnTypeInfo result = processor.processInExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testWhileStatementReturnsBodyType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.INT_TYPE);

        WhileStatement stmt = new WhileStatement();
        stmt.setTest(literal(true));
        stmt.setBody(Identifier.valueOf(null, "x"));

        ReturnTypeInfo result = processor.processWhileStatement(stmt, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
    }

    @Test
    public void testForStatementBodySeesInitVariable() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        VariableDeclarator decl = new VariableDeclarator();
        decl.setId(Identifier.valueOf(null, "i"));
        decl.setInit(literal(0));

        VariableDeclaration init = new VariableDeclaration();
        init.setDeclarators(java.util.Collections.singletonList(decl));

        ForStatement stmt = new ForStatement();
        stmt.setInit(init);
        stmt.setTest(literal(true));
        stmt.setUpdate(Identifier.valueOf(null, "i"));
        stmt.setBody(Identifier.valueOf(null, "i"));

        ReturnTypeInfo result = processor.processForStatement(stmt, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.getReturnType());
    }

    @Test
    public void testRegExpLiteralReturnsRegexType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        RegExpLiteral expr = new RegExpLiteral();
        expr.setPattern("a+");

        ReturnTypeInfo result = processor.processRegExpLiteral(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(IRegex.class.getName(), result.getReturnType().getTypeName());
    }

    @Test
    public void testDeleteStatementReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("x", PredefinedGenericTypes.STRING_TYPE);

        DeleteStatement stmt = new DeleteStatement();
        stmt.setArgument(Identifier.valueOf(null, "x"));

        ReturnTypeInfo result = processor.processDeleteStatement(stmt, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testCompareOpExpressionReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CompareOpExpression expr = new CompareOpExpression();
        expr.setOp("eq");
        expr.setLeft(literal(1));
        expr.setRight(literal(1));

        ReturnTypeInfo result = processor.processCompareOpExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testAssertOpExpressionReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        AssertOpExpression expr = new AssertOpExpression();
        expr.setOp("not-empty");
        expr.setValue(literal("x"));

        ReturnTypeInfo result = processor.processAssertOpExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testBetweenOpExpressionReturnsBoolean() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        BetweenOpExpression expr = new BetweenOpExpression();
        expr.setOp("between");
        expr.setValue(literal(2));
        expr.setMin(literal(1));
        expr.setMax(literal(3));

        ReturnTypeInfo result = processor.processBetweenOpExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.BOOLEAN_TYPE, result.getReturnType());
    }

    @Test
    public void testCatchClauseBindsExceptionVariable() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CatchClause clause = new CatchClause();
        clause.setName(Identifier.valueOf(null, "ex"));
        clause.setVarType(XLangTypeHelper.buildTypeNode(PredefinedGenericTypes.STRING_TYPE));
        clause.setBody(Identifier.valueOf(null, "ex"));

        ReturnTypeInfo result = processor.processCatchClause(clause, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testTryStatementMergesBlockCatchAndFinalizerResults() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CatchClause clause = new CatchClause();
        clause.setName(Identifier.valueOf(null, "ex"));
        clause.setBody(literal("catch"));

        TryStatement stmt = new TryStatement();
        stmt.setBlock(literal(1));
        stmt.setCatchHandler(clause);
        stmt.setFinalizer(literal(true));

        ReturnTypeInfo result = processor.processTryStatement(stmt, new TypeInferenceState());

        assertNotNull(result);
        assertTrue(result.getReturnType() instanceof IUnionType);
        assertUnionContains(result.getReturnType(),
                PredefinedGenericTypes.INT_TYPE,
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.BOOLEAN_TYPE);
    }

    @Test
    public void testSwitchCaseReturnsConsequentType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        SwitchCase switchCase = new SwitchCase();
        switchCase.setTest(literal(1));
        switchCase.setConsequent(literal("x"));

        ReturnTypeInfo result = processor.processSwitchCase(switchCase, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testSpreadElementPropagatesArgumentType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();
        state.setVariableType("items", GenericTypeHelper.buildListType(PredefinedGenericTypes.INT_TYPE));

        SpreadElement spread = new SpreadElement();
        spread.setArgument(Identifier.valueOf(null, "items"));

        ReturnTypeInfo result = processor.processSpreadElement(spread, state);

        assertNotNull(result);
        assertEquals(GenericTypeHelper.buildListType(PredefinedGenericTypes.INT_TYPE), result.getReturnType());
    }

    @Test
    public void testUsingStatementReturnsBodyType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        VariableDeclarator decl = new VariableDeclarator();
        decl.setId(Identifier.valueOf(null, "x"));
        decl.setInit(literal("hello"));

        VariableDeclaration vars = new VariableDeclaration();
        vars.setDeclarators(java.util.Collections.singletonList(decl));

        UsingStatement stmt = new UsingStatement();
        stmt.setVars(vars);
        stmt.setBody(Identifier.valueOf(null, "x"));

        ReturnTypeInfo result = processor.processUsingStatement(stmt, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testTextOutputExpressionReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        TextOutputExpression expr = new TextOutputExpression();
        expr.setText("hello");

        ReturnTypeInfo result = processor.processTextOutputExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testEscapeOutputExpressionReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        EscapeOutputExpression expr = new EscapeOutputExpression();
        expr.setEscapeMode(XLangEscapeMode.xml);
        expr.setText(literal("hello"));

        ReturnTypeInfo result = processor.processEscapeOutputExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testCollectOutputExpressionReturnsStringForTextMode() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CollectOutputExpression expr = new CollectOutputExpression();
        expr.setOutputMode(XLangOutputMode.text);
        expr.setBody(literal("hello"));

        ReturnTypeInfo result = processor.processCollectOutputExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testCollectOutputExpressionReturnsNodeForNodeMode() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CollectOutputExpression expr = new CollectOutputExpression();
        expr.setOutputMode(XLangOutputMode.node);
        expr.setBody(literal("hello"));

        ReturnTypeInfo result = processor.processCollectOutputExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(XNode.class.getName(), result.getReturnType().getTypeName());
    }

    @Test
    public void testCollectOutputExpressionReturnsSqlForSqlMode() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        CollectOutputExpression expr = new CollectOutputExpression();
        expr.setOutputMode(XLangOutputMode.sql);
        expr.setBody(literal("select 1"));

        ReturnTypeInfo result = processor.processCollectOutputExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(SQL.class.getName(), result.getReturnType().getTypeName());
    }

    @Test
    public void testGenNodeAttrExpressionReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        GenNodeAttrExpression expr = new GenNodeAttrExpression();
        expr.setName("id");
        expr.setValue(literal(1));

        ReturnTypeInfo result = processor.processGenNodeAttrExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testGenNodeExpressionReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        GenNodeAttrExpression attr = new GenNodeAttrExpression();
        attr.setName("id");
        attr.setValue(literal(1));

        GenNodeExpression expr = new GenNodeExpression();
        expr.setTagName(literal("div"));
        expr.setAttrs(java.util.Collections.singletonList(attr));
        expr.setBody(literal("hello"));

        ReturnTypeInfo result = processor.processGenNodeExpression(expr, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testEmptyStatementReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ReturnTypeInfo result = processor.processEmptyStatement(new EmptyStatement(), new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testBreakStatementReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ReturnTypeInfo result = processor.processBreakStatement(new BreakStatement(), new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testContinueStatementReturnsVoid() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ReturnTypeInfo result = processor.processContinueStatement(new ContinueStatement(), new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.VOID_TYPE, result.getReturnType());
    }

    @Test
    public void testThrowStatementReturnsArgumentType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ThrowStatement stmt = new ThrowStatement();
        stmt.setArgument(literal("boom"));

        ReturnTypeInfo result = processor.processThrowStatement(stmt, new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.getReturnType());
    }

    @Test
    public void testThisExpressionReturnsAny() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ReturnTypeInfo result = processor.processThisExpression(new ThisExpression(), new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.ANY_TYPE, result.getReturnType());
    }

    @Test
    public void testSuperExpressionReturnsAny() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ReturnTypeInfo result = processor.processSuperExpression(new SuperExpression(), new TypeInferenceState());

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.ANY_TYPE, result.getReturnType());
    }

    @Test
    public void testPropertyBindingUsesInitializerType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        PropertyBinding binding = new PropertyBinding();
        binding.setPropName("name");
        binding.setIdentifier(Identifier.valueOf(null, "name"));
        binding.setInitializer(literal("x"));

        TypeInferenceState state = new TypeInferenceState();
        ReturnTypeInfo result = processor.processPropertyBinding(binding, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("name"));
    }

    @Test
    public void testRestBindingUsesInitializerType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        RestBinding binding = new RestBinding();
        binding.setIdentifier(Identifier.valueOf(null, "rest"));
        binding.setInitializer(objectExpressionWithSingleProperty("k", literal(1)));

        TypeInferenceState state = new TypeInferenceState();
        ReturnTypeInfo result = processor.processRestBinding(binding, state);

        assertNotNull(result);
        assertEquals(state.getVariableType("rest"), result.getReturnType());
    }

    @Test
    public void testArrayElementBindingUsesInitializerType() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ArrayElementBinding binding = new ArrayElementBinding();
        binding.setIdentifier(Identifier.valueOf(null, "item"));
        binding.setInitializer(literal(1));

        TypeInferenceState state = new TypeInferenceState();
        ReturnTypeInfo result = processor.processArrayElementBinding(binding, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("item"));
    }

    @Test
    public void testObjectBindingProcessesChildren() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        PropertyBinding property = new PropertyBinding();
        property.setPropName("name");
        property.setIdentifier(Identifier.valueOf(null, "name"));
        property.setInitializer(literal("x"));

        ObjectBinding binding = new ObjectBinding();
        binding.setProperties(java.util.Collections.singletonList(property));

        TypeInferenceState state = new TypeInferenceState();
        ReturnTypeInfo result = processor.processObjectBinding(binding, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.STRING_TYPE, state.getVariableType("name"));
    }

    @Test
    public void testArrayBindingProcessesChildren() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();

        ArrayElementBinding element = new ArrayElementBinding();
        element.setIdentifier(Identifier.valueOf(null, "item"));
        element.setInitializer(literal(1));

        ArrayBinding binding = new ArrayBinding();
        binding.setElements(java.util.Collections.singletonList(element));

        TypeInferenceState state = new TypeInferenceState();
        ReturnTypeInfo result = processor.processArrayBinding(binding, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.INT_TYPE, state.getVariableType("item"));
    }

    @Test
    public void testImportDefaultSpecifierRegistersLocalAsAny() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        ImportDefaultSpecifier spec = new ImportDefaultSpecifier();
        spec.setLocal(Identifier.valueOf(null, "foo"));

        ReturnTypeInfo result = processor.processImportDefaultSpecifier(spec, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.ANY_TYPE, result.getReturnType());
        assertEquals(PredefinedGenericTypes.ANY_TYPE, state.getVariableType("foo"));
    }

    @Test
    public void testImportNamespaceSpecifierRegistersLocalAsAny() {
        TypeInferenceProcessor processor = new TypeInferenceProcessor();
        TypeInferenceState state = new TypeInferenceState();

        ImportNamespaceSpecifier spec = new ImportNamespaceSpecifier();
        spec.setLocal(Identifier.valueOf(null, "ns"));

        ReturnTypeInfo result = processor.processImportNamespaceSpecifier(spec, state);

        assertNotNull(result);
        assertEquals(PredefinedGenericTypes.ANY_TYPE, result.getReturnType());
        assertEquals(PredefinedGenericTypes.ANY_TYPE, state.getVariableType("ns"));
    }

    private Literal literal(Object value) {
        Literal literal = new Literal();
        literal.setValue(value);
        return literal;
    }

    private ObjectExpression objectExpressionWithSingleProperty(String key, Expression value) {
        PropertyAssignment property = new PropertyAssignment();
        property.setKey(literal(key));
        property.setValue(value);
        return ObjectExpression.valueOf(null, java.util.Collections.singletonList(property));
    }

    private void assertUnionContains(IGenericType type, IGenericType... expectedTypes) {
        assertTrue(type instanceof IUnionType);
        java.util.List<IGenericType> subTypes = ((IUnionType) type).getSubTypes();
        for (IGenericType expectedType : expectedTypes) {
            assertTrue(subTypes.stream().anyMatch(t -> t.getTypeName().equals(expectedType.getTypeName())));
        }
    }

    private IGenericType createUnionType(IGenericType... types) {
        return new io.nop.core.type.impl.GenericUnionTypeImpl(java.util.Arrays.asList(types));
    }

    @Test
    public void testConfigDisabled() {
        boolean originalValue = CFG_XLANG_TYPE_INFERENCE_ENABLED.get();
        try {
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_TYPE_INFERENCE_ENABLED, false);
            assertFalse(CFG_XLANG_TYPE_INFERENCE_ENABLED.get());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_TYPE_INFERENCE_ENABLED, originalValue);
        }
    }

    @Test
    public void testConfigEnabled() {
        boolean originalValue = CFG_XLANG_TYPE_INFERENCE_ENABLED.get();
        try {
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_TYPE_INFERENCE_ENABLED, true);
            assertTrue(CFG_XLANG_TYPE_INFERENCE_ENABLED.get());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_TYPE_INFERENCE_ENABLED, originalValue);
        }
    }
}
