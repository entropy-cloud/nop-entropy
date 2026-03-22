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
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IUnionType;
import io.nop.core.type.PredefinedGenericTypes;
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
