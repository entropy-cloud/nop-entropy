/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.rpc.model._gen._ApiMessageFieldModel;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import io.nop.xlang.xmeta.impl.SchemaNodeImpl;

import java.util.stream.Collectors;

public class ApiMessageFieldModel extends _ApiMessageFieldModel implements IWithOptions {
    public ApiMessageFieldModel() {

    }

    public IGenericType getType() {
        ISchema schema = getSchema();
        return schema == null ? null : schema.getType();
    }

    public void setType(IGenericType type) {
        ISchema schema = getSchema();
        if (schema == null) {
            schema = new SchemaImpl();
            setSchema(schema);
        }
        ((SchemaNodeImpl) schema).setType(type);
    }

    public String getCodegenJavaType() {
        ISchema schema = getSchema();
        if (schema == null) {
            return Object.class.getSimpleName();
        }

        IGenericType type = schema.getType();
        if (type != null && type.isResolved()) {
            return simplifyJavaType(buildResolvedJavaType(type));
        }

        String className = schema.getClassName();
        StdDataType stdType = StdDataType.fromStdName(className);
        if (stdType == null && className.indexOf('.') < 0) {
            stdType = StdDataType.fromStdName(className.toLowerCase());
        }
        if (stdType != null) {
            return simplifyJavaType(stdType.getJavaTypeName());
        }

        return simplifyJavaType(className);
    }

    private String buildResolvedJavaType(IGenericType type) {
        String className = simplifySourceJavaType(type.getClassName());
        if (type.getTypeParameters().isEmpty()) {
            return className;
        }

        return className + "<" + type.getTypeParameters().stream()
                .map(this::buildResolvedJavaType)
                .collect(Collectors.joining(",")) + ">";
    }

    private String simplifyJavaType(String typeName) {
        return simplifySourceJavaType(typeName);
    }

    private String simplifySourceJavaType(String typeName) {
        if (typeName == null) {
            return null;
        }

        String simplified = StringHelper.simplifyJavaType(typeName);
        if (!simplified.equals(typeName)) {
            return simplified;
        }

        if (typeName.equals("java.util.Map")) {
            return "Map";
        }
        if (typeName.equals("java.util.List")) {
            return "List";
        }
        if (typeName.equals("java.util.Set")) {
            return "Set";
        }

        return typeName;
    }
}
