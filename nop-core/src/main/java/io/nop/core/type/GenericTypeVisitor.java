/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.type;

import io.nop.api.core.util.ProcessResult;

import java.util.List;

public class GenericTypeVisitor<T> {
    public T visit(IGenericType type) {
        switch (type.getKind()) {
            case RAW_TYPE:
                return visitRawType((IRawType) type);
            case RAW_TYPE_REF:
                return visitRawTypeReference((IRawTypeReference) type);
            case PARAMETERIZED_TYPE:
                return visitParameterizedType((IParameterizedType) type);
            case FUNCTION:
                return visitFunctionType((IFunctionType) type);
            case TYPE_VARIABLE:
                return visitTypeVariable((ITypeVariable) type);
            case TYPE_VARIABLE_BOUND:
                return visitTypeVariableBound((ITypeVariableBound) type);
            case WILDCARD:
                return visitWildcardType((IWildcardType) type);
            case ARRAY:
                return visitArrayType((IArrayType) type);
            case TUPLE:
                return visitTupleType((IArrayType) type);
            case UNION:
                return visitUnionType((IUnionType) type);
            case INTERSECTION:
                return visitIntersectionType((IIntersectionType) type);
            default:
                throw new IllegalArgumentException("invalid GenericTypeKind: " + type.getKind());
        }
    }

    protected T doVisit(IGenericType type) {
        return visitChildren(type);
    }

    protected boolean shouldStop(T ret) {
        return ret == ProcessResult.STOP;
    }

    public T visitChildren(IGenericType type) {
        T ret = visitTypes(type.getTypeParameters());
        if (shouldStop(ret))
            return ret;

        switch (type.getKind()) {
            case ARRAY: {
                ret = visit(type.getComponentType());
                break;
            }
            case UNION:
            case TUPLE:
            case INTERSECTION: {
                ret = visitTypes(type.getSubTypes());
                break;
            }
            case FUNCTION: {
                IFunctionType fn = (IFunctionType) type;
                ret = visitTypes(fn.getFuncArgTypes());
                if (shouldStop(ret))
                    return ret;
                if (fn.getFuncReturnType() != null)
                    ret = visit(fn.getFuncReturnType());
                break;
            }
            case WILDCARD:
            case TYPE_VARIABLE_BOUND: {
                ITypeWithBound bound = (ITypeWithBound) type;
                ret = visit(bound.getUpperBound());
                if (shouldStop(ret))
                    return ret;
                if (bound.getLowerBound() != null) {
                    ret = visit(bound.getLowerBound());
                }
                break;
            }
        }
        return ret;
    }

    public T visitTypes(List<IGenericType> types) {
        T ret = null;
        if (!types.isEmpty()) {
            for (IGenericType type : types) {
                ret = visit(type);
                if (shouldStop(ret))
                    return ret;
            }
        }
        return ret;
    }

    public T visitIntersectionType(IIntersectionType type) {
        return doVisit(type);
    }

    public T visitUnionType(IUnionType type) {
        return doVisit(type);
    }

    public T visitTupleType(IArrayType type) {
        return doVisit(type);
    }

    public T visitArrayType(IArrayType type) {
        return doVisit(type);
    }

    public T visitWildcardType(IWildcardType type) {
        return doVisit(type);
    }

    public T visitTypeVariableBound(ITypeVariableBound type) {
        return doVisit(type);
    }

    public T visitTypeVariable(ITypeVariable type) {
        return doVisit(type);
    }

    public T visitFunctionType(IFunctionType type) {
        return doVisit(type);
    }

    public T visitParameterizedType(IParameterizedType type) {
        return doVisit(type);
    }

    public T visitRawTypeReference(IRawTypeReference type) {
        return doVisit(type);
    }

    public T visitRawType(IRawType type) {
        return doVisit(type);
    }
}