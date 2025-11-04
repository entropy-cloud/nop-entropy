/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.common;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.CoreConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.nop.codegen.CodeGenErrors.ARG_LOC_1;
import static io.nop.codegen.CodeGenErrors.ARG_METHOD_NAME_1;
import static io.nop.codegen.CodeGenErrors.ARG_METHOD_NAME_2;
import static io.nop.codegen.CodeGenErrors.ERR_CODE_GEN_METHOD_DECL_CONFLICTED;

public class AbstractGenCode {
    private int nextVarIndex = 0;

    private Map<String, MethodBlock> methods = new TreeMap<>();

    /**
     * 生成不重复的变量名
     */
    public String genVar() {
        return CoreConstants.GEN_VAR_PREFIX + (nextVarIndex++);
    }

    public String genVar(String prefix) {
        return CoreConstants.GEN_VAR_PREFIX + prefix + (nextVarIndex++);
    }

    public List<MethodBlock> getMethods() {
        return new ArrayList<>(methods.values());
    }

    public MethodBlock makeMethod(SourceLocation loc, String methodName) {
        return makeMethod(loc, methodName, -1);
    }

    public MethodBlock makeMethod(SourceLocation loc, String methodName, int argCount) {
        MethodBlock method = methods.get(getMethodKey(methodName, argCount));
        if (method == null)
            return addMethod(loc, methodName, argCount);
        return method;
    }

    public MethodBlock addMethod(SourceLocation loc, String methodName) {
        return addMethod(loc, methodName, -1);
    }

    public MethodBlock addMethod(SourceLocation loc, String methodName, int argCount) {
        MethodBlock method = new MethodBlock(loc, methodName);
        String key = getMethodKey(methodName, argCount);

        MethodBlock oldMethod = methods.put(key, method);
        if (oldMethod != null) {
            throw new NopEvalException(ERR_CODE_GEN_METHOD_DECL_CONFLICTED)
                    .param(ARG_METHOD_NAME_1, oldMethod.getMethodName()).param(ARG_METHOD_NAME_2, methodName)
                    .param(ARG_LOC_1, oldMethod.getLocation()).loc(loc);
        }
        return method;
    }

    private String getMethodKey(String methodName, int argCount) {
        if (argCount >= 0) {
            return methodName + "-" + argCount;
        }
        return methodName;
    }
}
