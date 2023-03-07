/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.xjava;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.resource.component.parse.AbstractTextResourceParser;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.xlang.xdsl.XDslParseHelper;
import org.codehaus.janino.Java;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_TYPE_NAME;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_JAVA_TYPE_ALIAS_CONFLICTED;
import static io.nop.xlang.XLangErrors.ERR_JAVA_UNSUPPORTED_ELEMENT_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XMETA_UNKNOWN_TYPE;

public abstract class JaninoParser<T> extends AbstractTextResourceParser<T> {
    private IRawTypeResolver rawTypeResolver;
    protected SourceLocation baseLoc;
    protected Map<String, IGenericType> typeAliases = new HashMap<>();
    protected String packageName;

    protected Map<String, IGenericType> typeCache = new HashMap<>();

    public void setRawTypeResolver(IRawTypeResolver rawTypeResolver) {
        this.rawTypeResolver = rawTypeResolver;
    }

    @Override
    protected T doParseText(SourceLocation loc, String text) {
        this.baseLoc = loc;
        Java.CompilationUnit cu = JaninoHelper.parseJavaSource(loc, text);

        parseImports(cu.importDeclarations);
        this.packageName = cu.packageDeclaration.packageName;

        return doParse(cu);
    }

    public void addTypeAlias(Java.Locatable node, IGenericType type) {
        IGenericType oldType = typeAliases.put(StringHelper.simpleClassName(type.getClassName()), type);
        if (oldType != null && !oldType.equals(type)) {
            throw new NopException(ERR_JAVA_TYPE_ALIAS_CONFLICTED).loc(buildLoc(node)).param(ARG_TYPE_NAME,
                    type.getTypeName());
        }
    }

    protected abstract T doParse(Java.CompilationUnit cu);

    protected Java.Annotation getAnnotation(Java.Annotatable annotatable, String annClass) {
        Java.Annotation[] anns = annotatable.getAnnotations();
        if (anns == null || anns.length == 0)
            return null;

        for (Java.Annotation ann : anns) {
            String typeName = getTypeName(ann.getType());
            if (typeName.equals(annClass))
                return ann;
        }
        return null;
    }

    private String getTypeName(Java.Type type) {
        String name = type.toString();
        if (name.indexOf('.') > 0) {
            return name;
        }

        IGenericType genericType = typeAliases.get(name);
        if (genericType != null)
            return genericType.getTypeName();

        throw new NopException(ERR_XMETA_UNKNOWN_TYPE).loc(buildLoc(type)).param(ARG_TYPE_NAME, name);
    }

    private void parseImports(Java.AbstractCompilationUnit.ImportDeclaration[] imports) {
        if (imports != null) {
            for (Java.AbstractCompilationUnit.ImportDeclaration decl : imports) {
                if (decl instanceof Java.AbstractCompilationUnit.SingleTypeImportDeclaration) {
                    Java.AbstractCompilationUnit.SingleTypeImportDeclaration single = (Java.AbstractCompilationUnit.SingleTypeImportDeclaration) decl;
                    String className = StringHelper.joinArray(single.identifiers, ".");
                    IGenericType type = buildClassType(decl, className);
                    addTypeAlias(decl, type);
                }
            }
        }
    }

    protected SourceLocation buildLoc(Java.Locatable node) {
        return JaninoHelper.buildLoc(baseLoc, node.getLocation());
    }

    protected IGenericType buildClassType(Java.Locatable node, String className) {
        return withCache(XDslParseHelper.parseGenericType(buildLoc(node), className, rawTypeResolver));
    }

    private IGenericType withCache(IGenericType type) {
        IGenericType oldType = typeCache.putIfAbsent(type.getTypeName(), type);
        if (oldType != null)
            return oldType;
        return type;
    }

    protected List<IGenericType> buildTypes(Java.Type[] types) {
        if (types == null || types.length == 0)
            return null;
        IGenericType[] ret = new IGenericType[types.length];
        for (int i = 0, n = types.length; i < n; i++) {
            ret[i] = buildType(types[i]);
        }
        return CollectionHelper.buildImmutableList(ret);
    }

    protected IGenericType buildType(Java.Type type) {
        if (type == null)
            return null;

        if (type instanceof Java.PrimitiveType) {
            Java.Primitive primitive = ((Java.PrimitiveType) type).primitive;
            return withCache(PredefinedGenericTypes.getPredefinedType(primitive.name().toLowerCase()));
        } else if (type instanceof Java.ArrayType) {
            Java.ArrayType arrayType = (Java.ArrayType) type;
            return withCache(GenericTypeHelper.buildArrayType(buildType(arrayType.componentType)));
        } else if (type instanceof Java.ReferenceType) {
            Java.ReferenceType referenceType = (Java.ReferenceType) type;
            return buildReferenceType(referenceType);
        } else {
            throw new IllegalStateException("nop.err.janino.not-supported-type:" + type);
        }
    }

    IGenericType resolveAlias(Java.Located node, String typeName) {
        IGenericType type = typeAliases.get(typeName);
        if (type == null) {
            type = PredefinedGenericTypes.getPredefinedType(typeName);
        }
        if (type == null)
            throw new NopException(ERR_XMETA_UNKNOWN_TYPE).loc(buildLoc(node)).param(ARG_TYPE_NAME, typeName);
        return type;
    }

    IGenericType buildReferenceType(Java.ReferenceType referenceType) {
        IGenericType rawType;
        if (referenceType.identifiers.length == 1) {
            rawType = resolveAlias(referenceType, referenceType.identifiers[0]);
        } else {
            String typeName = StringHelper.joinArray(referenceType.identifiers, ",");
            rawType = buildClassType(referenceType, typeName);
        }

        if (referenceType.typeArguments == null || referenceType.typeArguments.length == 0)
            return rawType;

        List<IGenericType> typeArgs = resolveTypeArguments(referenceType.typeArguments);
        return GenericTypeHelper.buildParameterizedType(rawType, typeArgs);
    }

    private List<IGenericType> resolveTypeArguments(Java.TypeArgument[] args) {
        IGenericType[] typeArgs = new IGenericType[args.length];
        for (int i = 0, n = args.length; i < n; i++) {
            typeArgs[i] = buildTypeArgument(args[i]);
        }
        return CollectionHelper.buildImmutableList(typeArgs);
    }

    private IGenericType buildTypeArgument(Java.TypeArgument arg) {
        if (arg instanceof Java.Type)
            return buildType((Java.Type) arg);
        Java.Wildcard wildcard = (Java.Wildcard) arg;
        if (wildcard.referenceType == null)
            return PredefinedGenericTypes.NO_BOUND_WILDCARD_TYPE;
        IGenericType bound = buildReferenceType(wildcard.referenceType);
        boolean upperBound = wildcard.bounds == Java.Wildcard.BOUNDS_EXTENDS;
        return GenericTypeHelper.buildWildcardType(upperBound ? bound : null, upperBound ? null : bound);
    }

    protected void parseAnnotationValue(Java.Annotation ann, Annotation obj) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(obj.getClass());
        if (ann instanceof Java.SingleElementAnnotation) {
            Object value = getValue(((Java.SingleElementAnnotation) ann).elementValue);
            beanModel.setProperty(obj, "value", value);
        } else if (ann instanceof Java.NormalAnnotation) {
            Java.ElementValuePair[] pairs = ((Java.NormalAnnotation) ann).elementValuePairs;
            for (Java.ElementValuePair pair : pairs) {
                String name = pair.identifier;
                Object value = getValue(pair.elementValue);
                beanModel.setProperty(obj, name, value);
            }
        }
    }

    private Object getValue(Java.ElementValue value) {
        if (value instanceof Java.Literal) {
            return ((Java.Literal) value).value;
        }
        throw new NopException(ERR_JAVA_UNSUPPORTED_ELEMENT_VALUE).param(ARG_VALUE, value);
    }
}