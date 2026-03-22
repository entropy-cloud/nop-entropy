/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestGenericTypeInferencer extends BaseTestCase {

    @BeforeAll
    public static void beforeAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void afterAll() {
        CoreInitialization.destroy();
    }

    @Test
    public void testTypeVarBindings() {
        GenericTypeInferencer.TypeVarBindings bindings = new GenericTypeInferencer.TypeVarBindings();
        
        // Test simple binding
        assertTrue(bindings.bind("T", PredefinedGenericTypes.STRING_TYPE, null));
        assertEquals(PredefinedGenericTypes.STRING_TYPE, bindings.getBindings().get("T"));
        
        // Test compatible type binding (subtype)
        assertTrue(bindings.bind("T", PredefinedGenericTypes.ANY_TYPE, null));
    }

    @Test
    public void testInferTypeArguments() {
        // Test case: identity<T>(x: T): T called with string
        // typeParams = [T], paramTypes = [T], argTypes = [string]
        // Expected: {T: string}
        
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.List<IGenericType> paramTypes = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.List<IGenericType> argTypes = java.util.Collections.singletonList(
                PredefinedGenericTypes.STRING_TYPE);
        
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArguments(
                typeParams, paramTypes, argTypes, null);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.get("T"));
    }

    @Test
    public void testApplyTypeArguments() {
        // Test applying type arguments to a type variable
        Map<String, IGenericType> typeArgs = new java.util.HashMap<>();
        typeArgs.put("T", PredefinedGenericTypes.STRING_TYPE);
        
        IGenericType result = GenericTypeInferencer.applyTypeArguments(
                PredefinedGenericTypes.VARIABLE_T_TYPE, typeArgs);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result);
    }

    @Test
    public void testMultipleTypeParams() {
        // Test case: map<K, V>(k: K, v: V): Map<K, V> called with (string, int)
        // typeParams = [K, V], paramTypes = [K, V], argTypes = [string, int]
        // Expected: {K: string, V: int}
        
        java.util.List<IGenericType> typeParams = java.util.Arrays.asList(
                PredefinedGenericTypes.VARIABLE_K_TYPE,
                PredefinedGenericTypes.VARIABLE_V_TYPE);
        
        java.util.List<IGenericType> paramTypes = java.util.Arrays.asList(
                PredefinedGenericTypes.VARIABLE_K_TYPE,
                PredefinedGenericTypes.VARIABLE_V_TYPE);
        
        java.util.List<IGenericType> argTypes = java.util.Arrays.asList(
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.INT_TYPE);
        
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArguments(
                typeParams, paramTypes, argTypes, null);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.get("K"));
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.get("V"));
    }

    @Test
    public void testEmptyTypeParams() {
        // Test case: no type parameters
        java.util.List<IGenericType> typeParams = java.util.Collections.emptyList();
        java.util.List<IGenericType> paramTypes = java.util.Collections.singletonList(
                PredefinedGenericTypes.STRING_TYPE);
        java.util.List<IGenericType> argTypes = java.util.Collections.singletonList(
                PredefinedGenericTypes.STRING_TYPE);
        
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArguments(
                typeParams, paramTypes, argTypes, null);
        
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNullTypeParams() {
        // Test case: null type parameters
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArguments(
                null, null, null, null);
        
        assertTrue(result.isEmpty());
    }

    @Test
    public void testTypeConflict() {
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.List<IGenericType> paramTypes = java.util.Arrays.asList(
                PredefinedGenericTypes.VARIABLE_T_TYPE,
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.List<IGenericType> argTypes = java.util.Arrays.asList(
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.INT_TYPE);
        
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArguments(
                typeParams, paramTypes, argTypes, null);
        
        IGenericType tResult = result.get("T");
        assertNotNull(tResult);
    }

    @Test
    public void testApplyEmptyTypeArgs() {
        // Test applying empty type arguments
        IGenericType original = PredefinedGenericTypes.STRING_TYPE;
        
        IGenericType result = GenericTypeInferencer.applyTypeArguments(original, null);
        assertEquals(original, result);
        
        result = GenericTypeInferencer.applyTypeArguments(original, java.util.Collections.emptyMap());
        assertEquals(original, result);
    }

    @Test
    public void testApplyTypeArgsToNonGenericType() {
        // Test applying type arguments to a type without type variables
        Map<String, IGenericType> typeArgs = new java.util.HashMap<>();
        typeArgs.put("T", PredefinedGenericTypes.STRING_TYPE);
        
        IGenericType result = GenericTypeInferencer.applyTypeArguments(
                PredefinedGenericTypes.INT_TYPE, typeArgs);
        
        // Should return the original type since it has no type variables
        assertEquals(PredefinedGenericTypes.INT_TYPE, result);
    }

    @Test
    public void testTypeVarBindingsWithAnyType() {
        GenericTypeInferencer.TypeVarBindings bindings = new GenericTypeInferencer.TypeVarBindings();
        
        // any type should be compatible with any other type
        assertTrue(bindings.bind("T", PredefinedGenericTypes.ANY_TYPE, null));
        assertEquals(PredefinedGenericTypes.ANY_TYPE, bindings.getBindings().get("T"));
        
        // Binding a concrete type after any should work
        assertTrue(bindings.bind("T", PredefinedGenericTypes.STRING_TYPE, null));
    }

    @Test
    public void testFewerArgsThanParams() {
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.List<IGenericType> paramTypes = java.util.Arrays.asList(
                PredefinedGenericTypes.VARIABLE_T_TYPE,
                PredefinedGenericTypes.STRING_TYPE);
        
        java.util.List<IGenericType> argTypes = java.util.Collections.singletonList(
                PredefinedGenericTypes.INT_TYPE);
        
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArguments(
                typeParams, paramTypes, argTypes, null);
        
        assertEquals(PredefinedGenericTypes.INT_TYPE, result.get("T"));
    }

    @Test
    public void testGetTypeVariableNames() {
        java.util.Set<String> names = GenericTypeInferencer.getTypeVariableNames(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        assertTrue(names.contains("T"));
    }

    @Test
    public void testGetTypeVariableNamesFromComplexType() {
        IGenericType listT = io.nop.core.type.utils.GenericTypeHelper.buildListType(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.Set<String> names = GenericTypeInferencer.getTypeVariableNames(listT);
        
        assertTrue(names.contains("T"));
    }

    @Test
    public void testInferTypeArgumentsWithReturn() {
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        java.util.List<IGenericType> paramTypes = java.util.Collections.emptyList();
        java.util.List<IGenericType> argTypes = java.util.Collections.emptyList();
        
        Map<String, IGenericType> result = GenericTypeInferencer.inferTypeArgumentsWithReturn(
                typeParams, paramTypes, argTypes, 
                PredefinedGenericTypes.STRING_TYPE,
                PredefinedGenericTypes.VARIABLE_T_TYPE,
                null);
        
        assertEquals(PredefinedGenericTypes.STRING_TYPE, result.get("T"));
    }

    @Test
    public void testValidateTypeBounds() {
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        Map<String, IGenericType> typeArgs = new java.util.HashMap<>();
        typeArgs.put("T", PredefinedGenericTypes.STRING_TYPE);
        
        boolean valid = GenericTypeInferencer.validateTypeBounds(typeParams, typeArgs, null);
        assertTrue(valid);
    }

    @Test
    public void testValidateTypeBoundsWithNullArgs() {
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        boolean valid = GenericTypeInferencer.validateTypeBounds(typeParams, null, null);
        assertTrue(valid);
    }

    @Test
    public void testValidateTypeBoundsWithEmptyArgs() {
        java.util.List<IGenericType> typeParams = java.util.Collections.singletonList(
                PredefinedGenericTypes.VARIABLE_T_TYPE);
        
        boolean valid = GenericTypeInferencer.validateTypeBounds(typeParams, 
                java.util.Collections.emptyMap(), null);
        assertTrue(valid);
    }
}
