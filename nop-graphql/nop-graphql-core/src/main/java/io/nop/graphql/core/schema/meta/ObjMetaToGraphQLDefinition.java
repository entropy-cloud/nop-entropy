/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.schema.meta;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLUnionTypeDefinition;
import io.nop.graphql.core.fetcher.PropGetterFetcher;
import io.nop.graphql.core.reflection.ReflectionGraphQLTypeFactory;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.ObjPropArgModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_SUB_TYPE_OF_UNION_MUST_BE_OBJ_TYPE;

public class ObjMetaToGraphQLDefinition {
    static final Logger LOG = LoggerFactory.getLogger(ObjMetaToGraphQLDefinition.class);

    public static ObjMetaToGraphQLDefinition INSTANCE = new ObjMetaToGraphQLDefinition();

    public GraphQLObjectDefinition toObjectDefinition(IObjMeta objMeta, String objName, TypeRegistry typeRegistry) {
        GraphQLObjectDefinition objDef = new GraphQLObjectDefinition();
        objDef.setLocation(objMeta.getLocation());
        objDef.setName(objName);
        objDef.setObjMeta(objMeta);

        List<GraphQLFieldDefinition> fields = new ArrayList<>(objMeta.getProps().size());

        for (IObjPropMeta propMeta : objMeta.getProps()) {
            boolean published = propMeta.isPublished();
            if (!published)
                continue;

            if (!propMeta.isReadable())
                continue;

            // 忽略a.b.c这种复合字段
            if(propMeta.getName().indexOf('.') > 0)
                continue;

            GraphQLFieldDefinition field = toFieldDefinition(objMeta.getBizObjName(), propMeta, typeRegistry);
            if (field.getType() == null) {
                LOG.info("nop.graphql.ignore-field-without-type:objName={},propName={}", objName, field.getName());
                continue;
            }

            fields.add(field);
        }

        objDef.setFields(fields);
        return objDef;
    }

    private GraphQLFieldDefinition toFieldDefinition(String thisObjName, IObjPropMeta propMeta,
                                                     TypeRegistry typeRegistry) {
        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setLocation(propMeta.getLocation());
        field.setName(propMeta.getName());
        field.setDescription(propMeta.getDescription());
        field.setAuth(propMeta.getAuth());

        if (propMeta.getDescription() == null)
            field.setDescription(propMeta.getDisplayName());

        field.setType(toGraphQLType(thisObjName, propMeta.getSchema(), propMeta.isMandatory(), typeRegistry));

        List<GraphQLArgumentDefinition> args = toArgs(thisObjName, propMeta, typeRegistry);
        if (args != null)
            field.setArguments(args);
        field.setPropMeta(propMeta);

        if (propMeta.getGetter() != null) {
            field.setFetcher(new PropGetterFetcher(propMeta.getGetter()));
        }
        return field;
    }

    private List<GraphQLArgumentDefinition> toArgs(String thisObjName, IObjPropMeta propMeta, TypeRegistry registry) {
        List<ObjPropArgModel> propArgs = propMeta.getArgs();
        if (propArgs.isEmpty())
            return null;

        List<GraphQLArgumentDefinition> args = new ArrayList<>(propArgs.size());
        for (ObjPropArgModel propArg : propArgs) {
            String name = propArg.getName();
            GraphQLType type = toGraphQLType(thisObjName, propArg.getSchema(), propArg.isMandatory(), registry);

            GraphQLArgumentDefinition arg = new GraphQLArgumentDefinition();
            arg.setLocation(propArg.getLocation());
            arg.setName(name);
            arg.setType(type);
            arg.setDescription(propArg.getDescription());
            if (propArg.getDescription() == null)
                arg.setDescription(propArg.getDisplayName());

            args.add(arg);
        }
        return args;
    }

    public GraphQLType toGraphQLType(String thisObjName, ISchema schema, boolean mandatory, TypeRegistry typeRegistry) {
        IGenericType type = schema.getType();
        String bizObjName = schema.getBizObjName();
        if (GraphQLConstants.BIZ_OBJ_NAME_THIS_OBJ.equals(bizObjName))
            bizObjName = thisObjName;

        GraphQLType gqlType = null;

        if (schema.isListSchema()) {
            gqlType = toGraphQLType(bizObjName, schema.getItemSchema(), false, typeRegistry);
            if (gqlType != null)
                gqlType = GraphQLTypeHelper.listType(gqlType);
        } else if (schema.isUnionSchema()) {
            gqlType = buildUnionType(bizObjName, schema, typeRegistry);
        } else if (schema.isObjSchema()) {
            gqlType = buildObjType(bizObjName, schema, typeRegistry);
        } else {
            if (type == null) {
                if(bizObjName != null)
                    return GraphQLTypeHelper.namedType(bizObjName);

                StdDataType stdDataType = schema.getStdDataType();
                if (stdDataType != null)
                    gqlType = GraphQLTypeHelper.scalarType(GraphQLScalarType.fromStdDataType(stdDataType));
                if (gqlType == null)
                    gqlType = GraphQLTypeHelper.scalarType(GraphQLScalarType.String);
                return gqlType;
            }
            gqlType = ReflectionGraphQLTypeFactory.INSTANCE.buildGraphQLType(type, thisObjName, bizObjName,
                    typeRegistry);
        }

        if (mandatory) {
            gqlType = GraphQLTypeHelper.nonNullType(gqlType);
        }

        return gqlType;
    }

    private GraphQLType buildObjType(String thisObjName, ISchema schema, TypeRegistry registry) {
        GraphQLObjectDefinition def = new GraphQLObjectDefinition();
        def.setLocation(schema.getLocation());

        List<GraphQLFieldDefinition> fields = new ArrayList<>(schema.getProps().size());

        for (IObjPropMeta propMeta : schema.getProps()) {
            GraphQLFieldDefinition field = toFieldDefinition(thisObjName, propMeta, registry);
            fields.add(field);
        }
        def.setFields(fields);

        GraphQLObjectDefinition typeDef = (GraphQLObjectDefinition) registry.normalizeType(def);
        GraphQLNamedType type = GraphQLTypeHelper.namedType(typeDef.getName());
        type.setResolvedType(typeDef);
        return type;
    }

    private GraphQLType buildUnionType(String thisObjName, ISchema schema, TypeRegistry registry) {
        GraphQLUnionTypeDefinition def = new GraphQLUnionTypeDefinition();
        def.setLocation(schema.getLocation());

        List<GraphQLNamedType> types = new ArrayList<>(schema.getOneOf().size());

        for (ISchema subType : schema.getOneOf()) {
            GraphQLType type = toGraphQLType(thisObjName, subType, false, registry);
            if (!type.isNamedType())
                throw new NopException(ERR_GRAPHQL_SUB_TYPE_OF_UNION_MUST_BE_OBJ_TYPE).source(schema);
            types.add((GraphQLNamedType) type);
        }
        def.setTypes(types);

        GraphQLUnionTypeDefinition typeDef = (GraphQLUnionTypeDefinition) registry.normalizeType(def);
        GraphQLNamedType namedType = GraphQLTypeHelper.namedType(typeDef.getName());
        namedType.setResolvedType(typeDef);
        return namedType;
    }

//    private GraphQLType buildScalarType(IGenericType type) {
//        GraphQLScalarType scalarType = GraphQLScalarType.fromJavaClass(type.getRawClass());
//        if (scalarType == null)
//            scalarType = GraphQLScalarType.String;
//
//        return GraphQLTypeHelper.scalarType(scalarType);
//    }
}