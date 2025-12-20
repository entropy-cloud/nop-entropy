/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.type.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.impl.DefaultClassResolver;
import io.nop.core.type.IFunctionType;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericArrayTypeImpl;
import io.nop.core.type.impl.GenericFunctionTypeImpl;
import io.nop.core.type.impl.GenericIntersectionTypeImpl;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.core.type.impl.GenericWildcardTypeImpl;
import io.nop.core.type.impl.PredefinedGenericType;
import io.nop.core.type.impl.PredefinedWildcardType;
import io.nop.core.type.utils.GenericTypeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_START_LOC;
import static io.nop.core.CoreErrors.ARG_TYPE_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_TYPE_INVALID_ARG;
import static io.nop.core.CoreErrors.ERR_TYPE_NOT_ALLOW_FUNCTION_TYPE_ARRAY;

public class GenericTypeParser implements IGenericTypeParser {
    private boolean intern;

    private IRawTypeResolver resolver = DefaultClassResolver.INSTANCE;

    private Map<String, IGenericType> unresolved;

    @Override
    public IGenericTypeParser intern(boolean intern) {
        this.intern = intern;
        return this;
    }

    public IGenericTypeParser rawTypeResolver(IRawTypeResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public Map<String, IGenericType> getUnresolved() {
        return unresolved;
    }

    private String intern(String text) {
        if (intern)
            return text.intern();
        return text;
    }

    public List<IGenericType> parseGenericTypeList(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        List<IGenericType> ret = new ArrayList<>();
        do {
            IGenericType type = parseGenericType(sc);
            ret.add(type);
            sc.skipBlank();
        } while (sc.tryConsume(','));
        return ret;
    }

    public IGenericType parseGenericType(TextScanner sc) {
        // 目前仅允许顶层类型为函数类型
        if (sc.cur == '(')
            return parseFunctionType(sc);

        IGenericType type = parseBaseType(sc);
        while (consumeBracket(sc)) {
            if (type.isPredefined()) {
                IGenericType predefined = PredefinedGenericTypes.getPredefinedArrayType((PredefinedGenericType) type);
                if (predefined != null) {
                    type = predefined;
                    continue;
                }
            }
            if (type instanceof IFunctionType)
                throw sc.newError(ERR_TYPE_NOT_ALLOW_FUNCTION_TYPE_ARRAY).param(ARG_TYPE_NAME, type.getTypeName());

            type = new GenericArrayTypeImpl(type);
        }
        return type;
    }

    boolean consumeBracket(TextScanner sc) {
        if (sc.tryConsume('[')) {
            sc.consume(']');
            return true;
        }
        return false;
    }

    IGenericType parseBaseType(TextScanner sc) {
        String typeName = intern(sc.nextJavaPropPath());
        IGenericType type = buildType(typeName);

        sc.skipBlank();

        List<IGenericType> argTypes = parseArguments(sc);
        if (argTypes != null) {
            type = GenericTypeHelper.buildParameterizedType(type, argTypes);
        }

        return type;
    }

    public IFunctionType parseFunctionTypeFromText(SourceLocation loc, String text) {
        return parseFunctionType(TextScanner.fromString(loc, text));
    }

    public IFunctionType parseFunctionType(TextScanner sc) {
        sc.skipBlank();

        sc.match('(');
        List<String> argNames = Collections.emptyList();
        List<IGenericType> argTypes = Collections.emptyList();
        if (!sc.tryMatch(')')) {
            do {
                String argName = sc.nextJavaVar();
                IGenericType argType = PredefinedGenericTypes.ANY_TYPE;
                if (sc.tryMatch(':')) {
                    argType = parseGenericType(sc);
                }
                if (argNames.isEmpty()) {
                    argNames = new ArrayList<>();
                    argTypes = new ArrayList<>();
                }
                argNames.add(argName);
                argTypes.add(argType);
            } while (sc.tryMatch(','));
            sc.match(')');
        }
        matchFunctionArrow(sc);

        IGenericType returnType = parseGenericType(sc);
        return new GenericFunctionTypeImpl(argNames, argTypes, returnType);
    }

    IGenericType buildType(String typeName) {
        IGenericType type = PredefinedGenericTypes.getPredefinedType(typeName);
        if (type != null)
            return type;

        if (unresolved != null) {
            type = unresolved.get(typeName);
            if (type != null)
                return type;
        }

        type = resolver.resolveRawType(typeName);
        if (type == null) {
            if (unresolved == null) {
                unresolved = new HashMap<>();
            }
            type = new GenericRawTypeReferenceImpl(typeName);
            unresolved.put(typeName, type);
        }
        return type;
    }

    void checkTypeArgMatch(IGenericType type, List<IGenericType> argTypes, TextScanner sc, SourceLocation loc) {
        if (type.isCollectionLike()) {
            if (argTypes.size() != 1) {
                throw sc.newError(ERR_REFLECT_TYPE_INVALID_ARG).param(ARG_START_LOC, loc).param(ARG_TYPE_NAME,
                        type.getTypeName());
            }
        } else if (type.isMapLike()) {
            if (argTypes.size() != 2) {
                throw sc.newError(ERR_REFLECT_TYPE_INVALID_ARG).param(ARG_START_LOC, loc).param(ARG_TYPE_NAME,
                        type.getTypeName());
            }
        }
    }

    List<IGenericType> parseArguments(TextScanner sc) {
        if (!tryMatchLessThan(sc)) {
            return null;
        }

        List<IGenericType> ret = new ArrayList<>();
        do {
            IGenericType argType = parseArgument(sc);
            ret.add(argType);
        } while (sc.tryMatch(','));
        matchGreaterThan(sc);

        return CollectionHelper.immutableList(ret);
    }

    IGenericType parseArgument(TextScanner sc) {
        if (sc.tryMatch('?')) {
            if (sc.tryMatch("extends")) {
                sc.checkBlankSkipped();
                IGenericType type = parseUpperBound(sc);
                return GenericWildcardTypeImpl.valueOf(type, null);
            } else if (sc.tryMatch("super")) {
                sc.checkBlankSkipped();
                IGenericType type = parseGenericType(sc);
                return GenericWildcardTypeImpl.valueOf(PredefinedGenericTypes.ANY_TYPE, type);
            } else {
                return PredefinedWildcardType.NO_BOUND_WILDCARD_TYPE;
            }
        } else {
            return parseGenericType(sc);
        }
    }

    IGenericType parseUpperBound(TextScanner sc) {
        IGenericType type = parseGenericType(sc);
        List<IGenericType> types = null;
        while (tryMatchAmpersand(sc)) {
            IGenericType type2 = parseGenericType(sc);
            if (types == null) {
                types = new ArrayList<>();
                types.add(type);
            }
            types.add(type2);
        }
        if (types != null)
            return new GenericIntersectionTypeImpl(CollectionHelper.immutableList(types));
        return type;
    }

    // ///////////// 支持对已转义 xml 字符串的解析 ////////////

    /** 尝试匹配小于号（<） */
    boolean tryMatchLessThan(TextScanner sc) {
        return sc.tryMatch("&lt;") || sc.tryMatch('<');
    }

    /** 匹配大于号（>） */
    void matchGreaterThan(TextScanner sc) {
        if (!sc.tryMatch("&gt;")) {
            sc.match('>');
        }
    }

    /** 尝试匹配和号（&） */
    boolean tryMatchAmpersand(TextScanner sc) {
        return sc.tryMatch("&amp;") || sc.tryMatch('&');
    }

    /** 匹配函数箭头（=>） */
    void matchFunctionArrow(TextScanner sc) {
        if (!sc.tryMatch("=&gt;")) {
            sc.match("=>");
        }
    }
}
