/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLArgument;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLArrayValue;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDirective;
import io.nop.graphql.core.ast.GraphQLDirectiveDefinition;
import io.nop.graphql.core.ast.GraphQLDirectiveLocation;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLFragment;
import io.nop.graphql.core.ast.GraphQLFragmentSelection;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLObjectValue;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLPropertyValue;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLValue;
import io.nop.graphql.core.ast.GraphQLVariable;
import io.nop.graphql.core.ast.GraphQLVariableDefinition;
import io.nop.graphql.core.fetcher.FixedValueFetcher;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.GraphQLSchema;
import io.nop.graphql.core.utils.GraphQLTypeHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_DEPTH;
import static io.nop.graphql.core.GraphQLConstants.BIZ_OBJ_NAME_ROOT;
import static io.nop.graphql.core.GraphQLConstants.OBJ_ACTION_SEPARATOR;
import static io.nop.graphql.core.GraphQLErrors.ARG_ALLOWED_NAMES;
import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_AST_NODE;
import static io.nop.graphql.core.GraphQLErrors.ARG_DIRECTIVE;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FRAGMENT_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_LEVEL;
import static io.nop.graphql.core.GraphQLErrors.ARG_LOCATION;
import static io.nop.graphql.core.GraphQLErrors.ARG_MAX;
import static io.nop.graphql.core.GraphQLErrors.ARG_MAX_DEPTH;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_SELECTION_SET;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_VAR_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_ARG_MAX_MUST_BE_POSITIVE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_COMPLEX_TYPE_NO_SELECTION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NOT_ALLOW_SELECTION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_INVALID_FRAGMENT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_NOT_ALLOW_DIRECTIVE_AT_LOCATION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_NOT_OBJ_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_FIELD;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_FIELD_ARG;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_OBJECT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_OPERATION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_ARG_FOR_DIRECTIVE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_DIRECTIVE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_FRAGMENT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_VAR;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNSUPPORTED_AST;

/**
 * 验证GraphQLSelectionSet中所有字段都是模型中已经定义的字段，且参数类型与所引用的变量的类型相同
 */
public class GraphQLSelectionResolver {
    private final IGraphQLEngine engine;
    private final GraphQLSchema builtinSchema;
    private final int maxDepth;
    private boolean hasTreeChildren;

    public GraphQLSelectionResolver(IGraphQLEngine engine, GraphQLSchema builtinSchema, int maxDepth) {
        this.engine = engine;
        this.builtinSchema = builtinSchema;
        this.maxDepth = maxDepth;
    }

    public void resolveSelection(GraphQLDocument doc) {
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLFragment) {
                resolveFragment(doc, (GraphQLFragment) def, 0);
            }
        }

        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLOperation) {
                resolveOperation(doc, (GraphQLOperation) def);
            }
        }
    }

    private void resolveFragment(GraphQLDocument doc, GraphQLFragment fragment, int level) {
        if (fragment.isResolved())
            return;
        fragment.setResolved(true);
        resolveSelections(doc, fragment.getOnType(), fragment.getSelectionSet(), new HashMap<>(), level - 1);
    }

    private void resolveOperation(GraphQLDocument doc, GraphQLOperation op) {
        for (GraphQLSelection selection : op.getSelectionSet().getSelections()) {
            if (selection instanceof GraphQLFieldSelection) {
                GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
                GraphQLFieldDefinition fieldDef = fieldSelection.getFieldDefinition();
                if (fieldDef == null)
                    fieldDef = engine.getOperationDefinition(op.getOperationType(),
                            fieldSelection.getName());
                if (fieldDef == null) {
                    throw new NopException(ERR_GRAPHQL_UNDEFINED_OPERATION).source(op)
                            .param(ARG_OPERATION_NAME, fieldSelection.getName())
                            .param(ARG_OPERATION_TYPE, op.getOperationType());
                }
                int index = fieldSelection.getName().indexOf(OBJ_ACTION_SEPARATOR);
                String objName = index < 0 ? BIZ_OBJ_NAME_ROOT : fieldSelection.getName().substring(0, index);
                resolveFieldSelection(doc, objName, fieldDef,
                        fieldSelection, op.getVars(), 0);
            } else {
                throw new NopException(ERR_GRAPHQL_UNSUPPORTED_AST).param(ARG_AST_NODE, selection);
            }
        }
    }

    public void resolveSelections(GraphQLDocument doc, String objName, GraphQLSelectionSet selectionSet,
                                  Map<String, GraphQLVariableDefinition> vars, int level) {
        if (selectionSet != null) {
            GraphQLTypeDefinition typeDef = engine.getTypeDefinition(objName);
            if (typeDef == null)
                throw new NopException(ERR_GRAPHQL_UNDEFINED_OBJECT).source(selectionSet).param(ARG_OBJ_NAME, objName);

            if (!(typeDef instanceof GraphQLObjectDefinition)) {
                throw new NopException(ERR_GRAPHQL_NOT_OBJ_TYPE).source(selectionSet).param(ARG_TYPE, objName)
                        .param(ARG_SELECTION_SET, selectionSet.toSource());
            }

            if (level > maxDepth) {
                throw new NopException(ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH).source(selectionSet).param(ARG_TYPE, objName)
                        .param(ARG_SELECTION_SET, selectionSet.toSource()).param(ARG_MAX_DEPTH, maxDepth)
                        .param(ARG_LEVEL, level);
            }

            GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) typeDef;
            selectionSet.setObjectDefinition(objDef);

            for (GraphQLSelection selection : selectionSet.getSelections()) {
                if (selection instanceof GraphQLFieldSelection) {
                    GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
                    resolveFieldSelection(doc, objDef, fieldSelection, vars, level + 1);
                } else if (selection instanceof GraphQLFragmentSelection) {
                    GraphQLFragmentSelection fragmentSelection = (GraphQLFragmentSelection) selection;
                    resolveFragmentSelection(doc, objDef, fragmentSelection, level);
                } else {
                    throw new NopException(ERR_GRAPHQL_UNSUPPORTED_AST).param(ARG_AST_NODE, selection);
                }
            }

            if (hasTreeChildren) {
                for (GraphQLSelection selection : selectionSet.getSelections()) {
                    if (selection instanceof GraphQLFieldSelection) {
                        GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
                        if (fieldSelection.getSelectionSet() == null || !fieldSelection.hasSelection()) {
                            GraphQLDirective directive = fieldSelection
                                    .getDirective(GraphQLConstants.DIRECTIVE_TREE_CHILDREN);
                            if (directive != null) {
                                int maxLevel = ConvertHelper.toPrimitiveInt(directive
                                                .getArgValue(GraphQLConstants.DIRECTIVE_ARG_MAX, Collections.emptyMap()), 0,
                                        NopException::new);
                                if (maxLevel <= 0)
                                    throw new NopException(ERR_GRAPHQL_ARG_MAX_MUST_BE_POSITIVE).source(directive)
                                            .param(ARG_MAX, maxLevel).param(ARG_SELECTION_SET, selectionSet.toSource());

                                if (level + maxLevel > CFG_GRAPHQL_QUERY_MAX_DEPTH.get())
                                    throw new NopException(ERR_GRAPHQL_QUERY_EXCEED_MAX_DEPTH).source(selectionSet)
                                            .param(ARG_TYPE, objName).param(ARG_SELECTION_SET, selectionSet.toSource())
                                            .param(ARG_MAX_DEPTH, CFG_GRAPHQL_QUERY_MAX_DEPTH.get());

                                fieldSelection.getDirectives().remove(directive);
                                expandTreeChildren(selectionSet, fieldSelection, maxLevel);
                            }
                        }
                    }
                }
            }
        }
    }

    private void expandTreeChildren(GraphQLSelectionSet selectionSet, GraphQLFieldSelection fieldSelection,
                                    int maxLevel) {
        if (maxLevel == 0) {
            selectionSet.removeChild(fieldSelection);
        } else {
            GraphQLSelectionSet copy = selectionSet.deepClone();
            fieldSelection.setSelectionSet(copy);
            GraphQLSelection selection = copy.getSelection(fieldSelection.getAliasOrName());
            expandTreeChildren(copy, (GraphQLFieldSelection) selection, maxLevel - 1);
        }
    }

    private void resolveFragmentSelection(GraphQLDocument doc, GraphQLObjectDefinition objDef,
                                          GraphQLFragmentSelection fragmentSelection, int level) {
        String name = fragmentSelection.getFragmentName();
        if (name.startsWith(GraphQLConstants.FRAGMENT_SELECTION_PREFIX)) {
            // 预定义的fragment selection, 从engine获取
            FieldSelectionBean selection = engine.getSchemaLoader().getFragmentDefinition(objDef.getName(), name);
            GraphQLFragment fragment = new GraphQLFragment();
            GraphQLSelectionSet selectionSet = new GraphQLSelectionSet();
            selectionSet.setObjectDefinition(objDef);
            fragment.setSelectionSet(selectionSet);
            fragment.setName(name);
            fragmentSelection.setResolvedFragment(fragment);
            new RpcSelectionSetBuilder(builtinSchema, this.engine.getSchemaLoader(), maxDepth)
                    .addNonLazyFields(fragment.getSelectionSet(), objDef, level, selection);
            fragment.setResolved(true);
            return;
        }

        GraphQLFragment fragment = doc == null ? null : doc.getFragment(fragmentSelection.getFragmentName());
        if (fragment == null)
            throw new NopException(ERR_GRAPHQL_UNKNOWN_FRAGMENT).param(ARG_AST_NODE, fragmentSelection);
        fragmentSelection.setResolvedFragment(fragment);

        if (!objDef.getName().equals(fragment.getOnType()))
            throw new NopException(ERR_GRAPHQL_INVALID_FRAGMENT).param(ARG_AST_NODE, fragmentSelection)
                    .param(ARG_FRAGMENT_NAME, fragment.getName());

        resolveFragment(doc, fragment, level);
    }

    // private void resolveTypeSelection(GraphQLType type, GraphQLSelectionSet selectionSet,
    // Map<String, GraphQLVariableDefinition> vars) {
    // if (selectionSet != null) {
    // GraphQLObjectDefinition objDef = schemaLoader.resolveTypeDefinition(type);
    // if (objDef == null)
    // throw new NopException(ERR_GRAPHQL_UNDEFINED_OBJECT)
    // .param(ARG_OBJ_NAME, type.getObjTypeName());
    //
    // selectionSet.setObjectDefinition(objDef);
    //
    // for (GraphQLSelection selection : selectionSet.getSelections()) {
    // if (selection instanceof GraphQLFieldSelection) {
    // GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
    // resolveFieldSelection(objDef, fieldSelection, vars);
    // } else {
    // throw new NopException(ERR_GRAPHQL_UNSUPPORTED_AST)
    // .param(ARG_AST_NODE, selection);
    // }
    // }
    // }
    // }

    private void resolveFieldSelection(GraphQLDocument doc, GraphQLObjectDefinition objDef,
                                       GraphQLFieldSelection selection, Map<String, GraphQLVariableDefinition> vars, int level) {
        if (selection.getName().equals(GraphQLConstants.PROP___TYPENAME)) {
            GraphQLFieldDefinition fieldDef = new GraphQLFieldDefinition();
            fieldDef.setName(GraphQLConstants.PROP___TYPENAME);
            fieldDef.setType(GraphQLTypeHelper.scalarType(GraphQLScalarType.String));
            fieldDef.setFetcher(new FixedValueFetcher(objDef.getName()));
            resolveFieldSelection(doc, objDef.getName(), fieldDef, selection, vars, level);
            return;
        }

        GraphQLFieldDefinition fieldDef = objDef.getField(selection.getName());
        if (fieldDef == null)
            throw new NopException(ERR_GRAPHQL_UNDEFINED_FIELD).source(selection).param(ARG_OBJ_NAME, objDef.getName())
                    .param(ARG_FIELD_NAME, selection.getName()).param(ARG_ALLOWED_NAMES, objDef.getFieldNames());
        resolveFieldSelection(doc, objDef.getName(), fieldDef, selection, vars, level);
    }

    private void resolveFieldSelection(GraphQLDocument doc, String objName, GraphQLFieldDefinition fieldDef,
                                       GraphQLFieldSelection selection, Map<String, GraphQLVariableDefinition> vars, int level) {
        selection.setFieldDefinition(fieldDef);

        if (selection.getArguments() != null) {
            for (GraphQLArgument arg : selection.getArguments()) {
                GraphQLArgumentDefinition argDef = fieldDef.getArg(arg.getName());
                if (argDef == null)
                    throw new NopException(ERR_GRAPHQL_UNDEFINED_FIELD_ARG)
                            .source(selection).param(ARG_OBJ_NAME, objName)
                            .param(ARG_FIELD_NAME, fieldDef.getName())
                            .param(ARG_ARG_NAME, arg.getName());
                arg.setArgDefinition(argDef);
                resolveArg(arg, vars);
            }
        }

        if (fieldDef.getArguments() != null) {
            for (GraphQLArgumentDefinition argDef : fieldDef.getArguments()) {
                if (selection.getArg(argDef.getName()) == null) {
                    if (argDef.getDefaultValue() != null) {
                        // 没有传入参数，但是具有缺省值，则直接补充参数
                        GraphQLArgument arg = new GraphQLArgument();
                        arg.setName(argDef.getName());
                        arg.setLocation(argDef.getLocation());
                        arg.setValue(argDef.getDefaultValue());
                        arg.setArgDefinition(argDef);
                        selection.addArg(arg);
//                    } else {
//                        throw new NopException(ERR_GRAPHQL_MISSING_FIELD_ARG)
//                                .source(selection).param(ARG_OBJ_NAME, objName)
//                                .param(ARG_FIELD_NAME, fieldDef.getName())
//                                .param(ARG_ARG_NAME, argDef.getName());
                    }
                }
            }
        }

        resolveDirectives(selection.getDirectives(), GraphQLDirectiveLocation.FIELD, vars);

        if (selection.getSelectionSet() != null) {
            String typeName = fieldDef.getType().getNamedTypeName();
            if (typeName == null)
                throw new NopException(ERR_GRAPHQL_FIELD_NOT_ALLOW_SELECTION).source(selection)
                        .param(ARG_OBJ_NAME, objName).param(ARG_FIELD_NAME, fieldDef.getName());

            // 标记了TreeChildren则由GraphQL引擎负责展开
            if (selection.getSelectionSet().isEmpty() && selection.getDirective(GraphQLConstants.DIRECTIVE_TREE_CHILDREN) != null) {
                hasTreeChildren = true;
                return;
            }

            resolveSelections(doc, typeName, selection.getSelectionSet(), vars, level);
        } else {
            if (fieldDef.getType().needFieldSelection()) {
                if (selection.getDirective(GraphQLConstants.DIRECTIVE_TREE_CHILDREN) != null) {
                    hasTreeChildren = true;
                    return;
                }

                String typeName = fieldDef.getType().getNamedTypeName();
                if (typeName != null) {
                    GraphQLTypeDefinition typeDef = engine.getTypeDefinition(objName);
                    if (typeDef == null)
                        throw new NopException(ERR_GRAPHQL_UNDEFINED_OBJECT).source(selection).param(ARG_OBJ_NAME, objName);
                    if (typeDef.isObjectDefinition() && ((GraphQLObjectDefinition) typeDef).isGraphqlBean())
                        return;
                }

                throw new NopException(ERR_GRAPHQL_FIELD_COMPLEX_TYPE_NO_SELECTION).source(selection)
                        .param(ARG_OBJ_NAME, objName).param(ARG_FIELD_NAME, selection.getName())
                        .param(ARG_TYPE, fieldDef.getType());
            }
        }
    }
//
//    boolean isEnumType(GraphQLType type) {
//        if (type.isEnumType())
//            return true;
//        if (type.isObjectType())
//            return false;
//
//        String typeName = type.getNamedTypeName();
//        if (typeName == null)
//            return false;
//
//        return engine.getTypeDefinition(typeName) instanceof GraphQLEnumDefinition;
//    }

    private void resolveArg(GraphQLArgument arg, Map<String, GraphQLVariableDefinition> vars) {
        resolveValue(arg.getValue(), vars);
    }

    private void resolveValue(GraphQLValue value, Map<String, GraphQLVariableDefinition> vars) {
        if (value instanceof GraphQLArrayValue) {
            GraphQLArrayValue array = (GraphQLArrayValue) value;
            for (GraphQLValue item : array.getItems()) {
                resolveValue(item, vars);
            }
        } else if (value instanceof GraphQLObjectValue) {
            GraphQLObjectValue obj = (GraphQLObjectValue) value;
            if (obj.getProperties() != null) {
                for (GraphQLPropertyValue prop : obj.getProperties()) {
                    resolveValue(prop.getValue(), vars);
                }
            }
        } else if (value instanceof GraphQLVariable) {
            String varName = ((GraphQLVariable) value).getName();
            if (!vars.containsKey(varName))
                throw new NopException(ERR_GRAPHQL_UNKNOWN_VAR).source(value).param(ARG_VAR_NAME, varName);
        }
    }

    private void resolveDirectives(List<GraphQLDirective> directives, GraphQLDirectiveLocation loc,
                                   Map<String, GraphQLVariableDefinition> vars) {
        if (directives == null || directives.isEmpty())
            return;

        for (GraphQLDirective directive : directives) {
            String name = directive.getName();
            GraphQLDirectiveDefinition directiveType = engine.getDirective(directive.getName());
            if (directiveType == null)
                throw new NopException(ERR_GRAPHQL_UNKNOWN_DIRECTIVE).param(ARG_DIRECTIVE, name).source(directive);

            if (!directiveType.getLocations().contains(loc)) {
                throw new NopException(ERR_GRAPHQL_NOT_ALLOW_DIRECTIVE_AT_LOCATION).param(ARG_DIRECTIVE, name)
                        .param(ARG_LOCATION, loc).source(directive);
            }

            if (directive.getArguments() != null) {
                for (GraphQLArgument arg : directive.getArguments()) {
                    GraphQLArgumentDefinition argDef = directiveType.getArg(arg.getName());
                    if (argDef == null)
                        throw new NopException(ERR_GRAPHQL_UNKNOWN_ARG_FOR_DIRECTIVE).source(directive)
                                .param(ARG_DIRECTIVE, name).param(ARG_ARG_NAME, arg.getName());

                    resolveArg(arg, vars);
                }
            }
        }
    }
}