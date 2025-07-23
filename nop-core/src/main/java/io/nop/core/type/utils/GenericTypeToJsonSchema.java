package io.nop.core.type.utils;

import io.nop.api.core.json.JsonSchema;
import io.nop.commons.type.StdDataType;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GenericTypeToJsonSchema {

    public static JsonSchema buildOutputSchema(IFunctionModel func) {
        return buildSchemaForType(func.getReturnType());
    }

    public static JsonSchema buildInputSchema(IFunctionModel func) {
        JsonSchema schema = new JsonSchema();
        schema.setType("object");
        Map<String, JsonSchema> props = new LinkedHashMap<>();

        Set<String> required = new LinkedHashSet<>();
        for (IFunctionArgument arg : func.getArgs()) {
            JsonSchema argSchema = buildSchemaForType(arg.getType());
            argSchema.setDescription(arg.getDescription());
            props.put(arg.getName(), argSchema);
            if (!arg.isNullable()) {
                required.add(arg.getName());
            }
        }
        schema.setProperties(props);
        if (!required.isEmpty()) {
            schema.setRequired(required);
        }
        return schema;
    }

    public static JsonSchema buildSchemaForType(IGenericType type) {
        JsonSchema schema = new JsonSchema();
        if (type.isCollectionLike()) {
            schema.setType("array");
            JsonSchema itemSchema = buildSchemaForType(type.getComponentType());
            schema.setItems(itemSchema);
        } else {
            StdDataType dataType = type.getStdDataType();
            if (dataType.isSimpleType()) {
                schema.setType(dataType.getJsonType());
            } else {
                schema.setType("object");
                IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(type.getRawClass());
                Map<String, JsonSchema> props = new LinkedHashMap<>();
                for (IBeanPropertyModel propModel : beanModel.getPropertyModels().values()) {
                    if (propModel.isReadable())
                        props.put(propModel.getName(), buildSchemaForType(propModel.getType()));
                }

                if (!props.isEmpty()) {
                    schema.setProperties(props);
                }
            }
        }
        return schema;
    }
}
