/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.utils;

import io.nop.api.core.annotations.graphql.GraphQLInput;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.commons.util.StringHelper;
import io.nop.graphql.core.GraphQLConstants;

public class GraphQLNameHelper {

    public static String getPermission(String bizObjName, String bizAction) {
        return bizObjName + ':' + bizAction;
    }

    public static String getOperationName(String bizObj, String bizAction) {
        if (GraphQLConstants.BIZ_OBJ_NAME_ROOT.equals(bizObj) || StringHelper.isEmpty(bizObj))
            return bizAction;

        return bizObj + GraphQLConstants.OBJ_ACTION_SEPARATOR + bizAction;
    }

    public static String getResultTypeName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(GraphQLObject.class))
            return clazz.getSimpleName();
        return "g_" + clazz.getName().replace('.', '_');
    }

    public static String getInputTypeName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(GraphQLInput.class)) {
            return clazz.getSimpleName();
        }
        return "i_" + clazz.getName().replace('.', '_');
    }

    public static boolean isGeneratedTypeName(String typeName) {
        return typeName.startsWith("g_");
    }

    public static String getBaseObjName(String bizObjName) {
        // biz文件不存在，尝试删除bizExtType装载基础模型文件
        int pos = bizObjName.lastIndexOf('_');
        if (pos < 0) {
            return bizObjName;
        }

        String baseName = bizObjName.substring(0, pos);
        return baseName;
    }

    public static String getFetchAction(String objType, String field) {
        return objType + '.' + field;
    }
}
