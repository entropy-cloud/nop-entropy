/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.xjava;

import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.xlang.xdef.impl.XDefHelper;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.IObjSchemaImpl;
import io.nop.xlang.xmeta.impl.ObjMetaImpl;
import io.nop.xlang.xmeta.impl.ObjMetaRefResolver;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.codehaus.janino.Java;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_TYPE_NAME;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_JAVA_NOT_SUPPORT_CLASS_TYPE_PARAMETER;
import static io.nop.xlang.XLangErrors.ERR_XMETA_INVALID_PROP_META_PROP_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XMETA_ONLY_ALLOW_ONE_TOP_CLASS;
import static io.nop.xlang.XLangErrors.ERR_XMETA_PACKAGE_MEMBER_MUST_BE_CLASS;

public class JavaObjMetaParser extends JaninoParser<ObjMetaImpl> {
    private Set<String> defNames = new HashSet<>();

    private boolean resolveRef = true;

    public JavaObjMetaParser resolveRef(boolean resolveRef) {
        this.resolveRef = resolveRef;
        return this;
    }

    @Override
    protected ObjMetaImpl doParse(Java.CompilationUnit cu) {
        String packageName = cu.packageDeclaration.packageName;

        if (cu.getPackageMemberTypeDeclarations().length != 1)
            throw new NopException(ERR_XMETA_ONLY_ALLOW_ONE_TOP_CLASS).loc(baseLoc);

        Java.PackageMemberTypeDeclaration top = cu.getPackageMemberTypeDeclarations()[0];
        if (!(top instanceof Java.PackageMemberClassDeclaration))
            throw new NopException(ERR_XMETA_PACKAGE_MEMBER_MUST_BE_CLASS).loc(buildLoc(top)).param(ARG_TYPE_NAME,
                    top.getClassName());

        ObjMetaImpl meta = new ObjMetaImpl();
        meta.setLocation(baseLoc);

        Java.PackageMemberClassDeclaration topClass = (Java.PackageMemberClassDeclaration) top;
        for (Java.MemberTypeDeclaration type : topClass.getMemberTypeDeclarations()) {
            IGenericType genericType = buildTypeReference(packageName, type.getName());
            addTypeAlias(type, genericType);

            if (type instanceof Java.MemberClassDeclaration && !(type instanceof Java.MemberEnumDeclaration)) {
                defNames.add(genericType.getClassName());
            }
        }

        parseClass(meta, packageName, topClass);

        KeyedList<ISchema> defs = new KeyedList<>(item -> item.getName());
        for (Java.MemberTypeDeclaration type : topClass.getMemberTypeDeclarations()) {
            // 忽略enum和interface
            if (type instanceof Java.MemberEnumDeclaration)
                continue;

            if (type instanceof Java.MemberClassDeclaration) {
                Java.MemberClassDeclaration decl = (Java.MemberClassDeclaration) type;
                defs.add(parseSchemaDefinition(packageName, decl));
            }
        }
        meta.setDefines(defs);

        if (resolveRef)
            new ObjMetaRefResolver().resolve(meta);
        return meta;
    }

    IGenericType buildTypeReference(String packageName, String name) {
        String className = packageName + '.' + name;
        return new GenericRawTypeReferenceImpl(className);
    }

    private ISchema parseSchemaDefinition(String packageName, Java.NamedClassDeclaration classDecl) {
        ISchema schema = parseClass(packageName, classDecl);
        return schema;
    }

    private SchemaImpl parseClass(String packageName, Java.NamedClassDeclaration classDecl) {
        SchemaImpl schema = new SchemaImpl();
        if (classDecl.isAbstract()) {
            schema.setAbstract(true);
        }
        parseClass(schema, packageName, classDecl);
        return schema;
    }

    private void parseClass(IObjSchemaImpl schema, String packageName, Java.NamedClassDeclaration classDecl) {
        schema.setLocation(buildLoc(classDecl));
        schema.setType(
                new GenericRawTypeReferenceImpl(packageName + '.' + StringHelper.simpleClassName(classDecl.name)));
        if (classDecl.getOptionalTypeParameters() != null && classDecl.getOptionalTypeParameters().length > 0)
            throw new NopException(ERR_JAVA_NOT_SUPPORT_CLASS_TYPE_PARAMETER).loc(buildLoc(classDecl))
                    .param(ARG_CLASS_NAME, classDecl.name);

        schema.setName(classDecl.name);
        IGenericType extendsType = buildType(classDecl.extendedType);
        List<IGenericType> implementedTypes = buildTypes(classDecl.implementedTypes);
        schema.setExtendsType(extendsType);
        schema.setImplementsTypes(implementedTypes);

        if (extendsType != null && defNames.contains(extendsType.getClassName())) {
            String refPath = XDefHelper.buildFullRefPath(getResourceStdPath(), extendsType.getSimpleClassName());
            schema.setRef(refPath);
        }

        KeyedList<ObjPropMetaImpl> props = parseProps(classDecl);
        schema.setProps(props);
    }

    private KeyedList<ObjPropMetaImpl> parseProps(Java.ClassDeclaration classDecl) {
        KeyedList<ObjPropMetaImpl> props = new KeyedList<>(item -> item.key());
        for (Java.BlockStatement member : classDecl.getVariableDeclaratorsAndInitializers()) {
            if (member instanceof Java.FieldDeclaration) {
                parseFieldDecl((Java.FieldDeclaration) member, props);
            }
        }
        return props;
    }

    private void parseFieldDecl(Java.FieldDeclaration field, KeyedList<ObjPropMetaImpl> ret) {
        IGenericType type = buildType(field.type);

        for (Java.VariableDeclarator decl : field.variableDeclarators) {
            String name = decl.name;
            IGenericType fieldType = GenericTypeHelper.buildArrayType(type, decl.brackets);
            ObjPropMetaImpl propMeta = buildPropMeta(field);
            SchemaImpl schema = new SchemaImpl();
            schema.setType(fieldType);
            propMeta.setLocation(buildLoc(decl));
            propMeta.setName(name);
            propMeta.setSchema(schema);
            ret.add(propMeta);
        }
    }

    private ObjPropMetaImpl buildPropMeta(Java.Annotatable anns) {
        ObjPropMetaImpl propMeta = new ObjPropMetaImpl();
        Java.NormalAnnotation ann = (Java.NormalAnnotation) getAnnotation(anns, PropMeta.class.getName());
        if (ann != null) {
            IBeanModel propMetaModel = ReflectionManager.instance().getBeanModelForClass(ObjPropMetaImpl.class);
            IBeanModel schemaModel = ReflectionManager.instance().getBeanModelForClass(SchemaImpl.class);

            SchemaImpl schema = null;

            for (Java.ElementValuePair pair : ann.elementValuePairs) {
                String name = pair.identifier;
                Object value = getElementValue(pair.elementValue);
                try {
                    if (propMetaModel.getPropertyModel(name) != null) {
                        propMetaModel.setProperty(propMeta, name, value);
                    } else if (schemaModel.getPropertyModel(name) != null) {
                        if (schema == null) {
                            schema = new SchemaImpl();
                        }
                        schemaModel.setProperty(schema, name, value);
                    }
                } catch (Exception e) {
                    throw new NopException(ERR_XMETA_INVALID_PROP_META_PROP_VALUE, e).param(ARG_PROP_NAME, name)
                            .param(ARG_VALUE, value);
                }
            }
            propMeta.setSchema(schema);
        }
        return propMeta;
    }

    private Object getElementValue(Java.ElementValue value) {
        if (value instanceof Java.Literal)
            return ((Java.Literal) value).value;
        if (value instanceof Java.ElementValueArrayInitializer) {
            // 仅depends需要用到数组属性，类型为Set<String>
            Java.ElementValue[] values = ((Java.ElementValueArrayInitializer) value).elementValues;
            Set<String> list = new HashSet<>();
            for (Java.ElementValue elementValue : values) {
                list.add((String) getElementValue(elementValue));
            }
            return list;
        }
        return null;
    }
}