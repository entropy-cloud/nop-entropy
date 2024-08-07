package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.validator.DefaultValidationErrorCollector;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLFragmentSelection;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.xlang.xmeta.SimpleSchemaValidator;

import java.util.Map;

import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_EMPTY_ARG;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NULL_ARG;

public class GraphQLArgumentValidator {
    public static final GraphQLArgumentValidator INSTANCE = new GraphQLArgumentValidator();

    public void validate(IGraphQLExecutionContext context) {
        GraphQLOperation operation = context.getOperation();
        FieldSelectionBean selectionBean = context.getFieldSelection();

        for (GraphQLSelection field : operation.getSelectionSet().getSelections()) {
            GraphQLFieldSelection selection = (GraphQLFieldSelection) field;
            FieldSelectionBean subSelection = selectionBean.getField(selection.getAliasOrName());
            if (subSelection == null)
                continue;

            checkField(selection, subSelection, context);

            checkSelectionSet(selection.getSelectionSet(), subSelection, context);
        }
    }

    void checkSelectionSet(GraphQLSelectionSet selectionSet, FieldSelectionBean selectionBean, IGraphQLExecutionContext context) {
        if (selectionSet == null)
            return;

        for (GraphQLSelection selection : selectionSet.getSelections()) {
            if (selection instanceof GraphQLFragmentSelection) {
                GraphQLFragmentSelection fragmentSelection = (GraphQLFragmentSelection) selection;
                checkSelectionSet(fragmentSelection.getResolvedFragment().getSelectionSet(), selectionBean, context);
            } else {
                GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
                FieldSelectionBean subSelection = selectionBean.getField(fieldSelection.getAliasOrName());
                if (subSelection == null)
                    continue;

                checkField(fieldSelection, subSelection, context);

                checkSelectionSet(fieldSelection.getSelectionSet(), subSelection, context);
            }
        }
    }

    void checkField(GraphQLFieldSelection selection, FieldSelectionBean subSelection, IGraphQLExecutionContext context) {
        GraphQLFieldDefinition fieldDef = selection.getFieldDefinition();
        if (fieldDef.getArguments() == null || fieldDef.getArguments().isEmpty())
            return;

        Object args = selection.getOpRequest();
        if (args == null)
            args = subSelection.getArgs();

        for (GraphQLArgumentDefinition argDef : fieldDef.getArguments()) {
            Object value = getArg(args, argDef.getName());
            if (value == null && argDef.getType().isNonNullType()) {
                throw new NopException(ERR_GRAPHQL_FIELD_NULL_ARG)
                        .param(ARG_FIELD_NAME, fieldDef.getName())
                        .param(ARG_OPERATION_NAME, context.getOperation().getName())
                        .param(ARG_ARG_NAME, argDef.getName());
            }
            if (argDef.isMandatory() && StringHelper.isEmptyObject(value)) {
                throw new NopException(ERR_GRAPHQL_FIELD_EMPTY_ARG)
                        .param(ARG_FIELD_NAME, fieldDef.getName())
                        .param(ARG_OPERATION_NAME, context.getOperation().getName())
                        .param(ARG_ARG_NAME, argDef.getName());
            }

            if (value != null && argDef.getSchema() != null) {
                value = castValue(argDef, value, subSelection);

                SimpleSchemaValidator.INSTANCE.validate(argDef.getSchema(), argDef.getLocation(),
                        argDef.getName(), value, context.getEvalScope(),
                        DefaultValidationErrorCollector.THROW_ERROR);
            }
        }
    }

    Object getArg(Object args, String name) {
        if (args == null)
            return null;
        if (args instanceof Map)
            return ((Map<?, ?>) args).get(name);
        return BeanTool.getProperty(args, name);
    }

    Object castValue(GraphQLArgumentDefinition argDef, Object value, FieldSelectionBean subSelection) {
        IGenericType type = argDef.getJavaType();
        if (type == null)
            return value;

        if (type.isInstance(value))
            return value;

        String name = argDef.getName();
        if (value instanceof String && type.isDataBean()) {
            value = JsonTool.parse(value.toString());
            subSelection.setArg(name, value);
        }

        if (type.getStdDataType().isSimpleType()) {
            value = ConvertHelper.convertTo(type.getRawClass(), value,
                    err -> new NopException(err).source(argDef).param(ARG_FIELD_NAME, name));
            subSelection.setArg(name, value);
        }
        return value;
    }
}
