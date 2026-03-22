/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.ast.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestUnionTypeNarrower extends BaseTestCase {

    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @Test
    public void testRemoveFromUnion() {
        IGenericType result = UnionTypeNarrower.removeFromUnion(
                PredefinedGenericTypes.STRING_TYPE, 
                PredefinedGenericTypes.INT_TYPE);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result);
    }

    @Test
    public void testRemoveFromUnionType() {
        IGenericType unionType = createUnionType(
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.INT_TYPE,
                PredefinedGenericTypes.NULL_TYPE);

        IGenericType result = UnionTypeNarrower.removeFromUnion(unionType, PredefinedGenericTypes.NULL_TYPE);
        
        assertNotNull(result);
        assertTrue(result.isUnion());
        assertEquals(2, result.getSubTypes().size());
    }

    @Test
    public void testRemoveAllFromUnion() {
        IGenericType unionType = createUnionType(
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.INT_TYPE);

        IGenericType result = UnionTypeNarrower.removeFromUnion(unionType, PredefinedGenericTypes.STRING_TYPE);
        assertEquals(PredefinedGenericTypes.INT_TYPE, result);
    }

    @Test
    public void testRemoveOnlyElementFromUnion() {
        IGenericType result = UnionTypeNarrower.removeFromUnion(
                PredefinedGenericTypes.STRING_TYPE, 
                PredefinedGenericTypes.STRING_TYPE);
        
        assertEquals(PredefinedGenericTypes.NEVER_TYPE, result);
    }

    @Test
    public void testCollectNarrowedTypesNull() {
        Map<String, IGenericType> result = UnionTypeNarrower.collectNarrowedTypes(null, true);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCollectNarrowedTypesWithIdentifier() {
        Identifier id = new Identifier();
        id.setName("x");
        id.setReturnTypeInfo(createUnionType(
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.NULL_TYPE));

        Map<String, IGenericType> result = UnionTypeNarrower.collectNarrowedTypes(id, true);
        
        assertTrue(result.containsKey("x"));
        IGenericType narrowed = result.get("x");
        assertNotNull(narrowed);
    }

    @Test
    public void testCollectNarrowedTypesWithInstanceOf() {
        InstanceOfExpression expr = new InstanceOfExpression();
        
        Identifier value = new Identifier();
        value.setName("x");
        expr.setValue(value);
        
        TypeNameNode refType = TypeNameNode.fromTypeInfo(SourceLocation.fromClass(TestUnionTypeNarrower.class), 
                PredefinedGenericTypes.STRING_TYPE);
        expr.setRefType(refType);

        Map<String, IGenericType> result = UnionTypeNarrower.collectNarrowedTypes(expr, true);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.get("x"));
    }

    @Test
    public void testCollectNarrowedTypesWithBinaryEqNull() {
        BinaryExpression expr = new BinaryExpression();
        expr.setOperator(XLangOperator.EQ);
        
        Identifier left = new Identifier();
        left.setName("x");
        expr.setLeft(left);
        
        Literal right = new Literal();
        right.setValue(null);
        expr.setRight(right);

        Map<String, IGenericType> resultTrue = UnionTypeNarrower.collectNarrowedTypes(expr, true);
        assertEquals(PredefinedGenericTypes.NULL_TYPE, resultTrue.get("x"));
    }

    @Test
    public void testCollectNarrowedTypesWithLogicalAnd() {
        LogicalExpression andExpr = new LogicalExpression();
        andExpr.setOperator(XLangOperator.AND);

        BinaryExpression eq1 = new BinaryExpression();
        eq1.setOperator(XLangOperator.NE);
        Identifier x1 = new Identifier();
        x1.setName("x");
        eq1.setLeft(x1);
        Literal nullLit1 = new Literal();
        nullLit1.setValue(null);
        eq1.setRight(nullLit1);

        BinaryExpression eq2 = new BinaryExpression();
        eq2.setOperator(XLangOperator.NE);
        Identifier x2 = new Identifier();
        x2.setName("y");
        eq2.setLeft(x2);
        Literal nullLit2 = new Literal();
        nullLit2.setValue(null);
        eq2.setRight(nullLit2);

        andExpr.setLeft(eq1);
        andExpr.setRight(eq2);

        Map<String, IGenericType> result = UnionTypeNarrower.collectNarrowedTypes(andExpr, true);
        
        assertNotNull(result);
    }

    @Test
    public void testCollectNarrowedTypesWithUnaryNot() {
        UnaryExpression notExpr = new UnaryExpression();
        notExpr.setOperator(XLangOperator.NOT);

        BinaryExpression inner = new BinaryExpression();
        inner.setOperator(XLangOperator.EQ);
        Identifier x = new Identifier();
        x.setName("x");
        inner.setLeft(x);
        Literal nullLit = new Literal();
        nullLit.setValue(null);
        inner.setRight(nullLit);
        
        notExpr.setArgument(inner);

        Map<String, IGenericType> result = UnionTypeNarrower.collectNarrowedTypes(notExpr, true);
        
        assertNotNull(result);
    }

    private IGenericType createUnionType(IGenericType... types) {
        return new io.nop.core.type.impl.GenericUnionTypeImpl(java.util.Arrays.asList(types));
    }
}
