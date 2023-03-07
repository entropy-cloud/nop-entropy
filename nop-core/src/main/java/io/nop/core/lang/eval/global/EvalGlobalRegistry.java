/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval.global;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.utils.Underscore;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ERR_EVAL_FUNCTION_NAME_MUST_STARTS_WITH_LOWER_CASE;

@GlobalInstance
public class EvalGlobalRegistry {
    static final Logger LOG = LoggerFactory.getLogger(EvalGlobalRegistry.class);
    // public static final SourceLocation global_loc = SourceLocation.fromClass(EvalGlobalRegistry.class);

    static final EvalGlobalRegistry _instance = new EvalGlobalRegistry();

    private final Map<String, IGlobalVariableDefinition> vars = new ConcurrentHashMap<>();
    private final Map<String, IFunctionModel> functions = new ConcurrentHashMap<>();

    public static EvalGlobalRegistry instance() {
        return _instance;
    }

    EvalGlobalRegistry() {
        registerVariable(CoreConstants.GLOBAL_VAR_CONTEXT, new ContextGlobalVariable());
        registerVariable(CoreConstants.GLOBAL_VAR_SCOPE, new ScopeGlobalVariable());
        registerVariable(CoreConstants.GLOBAL_VAR_OUT, new SysOutGlobalVariable());
        registerVariable(CoreConstants.GLOBAL_VAR_EVAL_HELPER, new SysEvalHelperGlobalVariable());
        registerVariable(CoreConstants.GLOBAL_VAR_JSON, new StaticClassGlobalVariableDefinition(JsonTool.class));
        registerVariable(CoreConstants.GLOBAL_VAR_MATH, new StaticClassGlobalVariableDefinition(MathHelper.class));
        registerVariable(CoreConstants.GLOBAL_VAR_DATE, new StaticClassGlobalVariableDefinition(DateHelper.class));
        registerVariable(CoreConstants.GLOBAL_VAR_UNDERSCORE,
                new StaticClassGlobalVariableDefinition(Underscore.class));
        registerVariable(CoreConstants.GLOBAL_VAR_STRING, new StaticClassGlobalVariableDefinition(StringHelper.class));
    }

    public void registerVariable(String name, IGlobalVariableDefinition varDef) {
        Guard.notEmpty(name, "name is empty");
        Guard.notNull(varDef, "varDef is null");

        Object old = vars.put(name, varDef);
        if (old != null) {
            LOG.info("nop.xlang.replace-global-variable:name={}", name);
        }
    }

    public void unregisterVariable(String name) {
        vars.remove(name);
    }

    public static boolean isGlobalVarName(String varName) {
        return varName.charAt(0) == '$';
    }

    public Map<String, IFunctionModel> getRegisteredFunctions() {
        return functions;
    }

    public Map<String, IGlobalVariableDefinition> getRegisteredVars() {
        return vars;
    }

    public IGlobalVariableDefinition getRegisteredVariable(String name) {
        return vars.get(name);
    }

    public Runnable registerFunction(String name, IFunctionModel func) {
        Guard.notEmpty(name, "name is empty");
        Guard.notNull(func, "func is null");

        if (!Character.isLowerCase(name.charAt(0)))
            throw new NopException(ERR_EVAL_FUNCTION_NAME_MUST_STARTS_WITH_LOWER_CASE).param(ARG_NAME, name);

        IFunctionModel old = functions.put(name, func);
        if (old != null) {
            LOG.info("nop.xlang.replace-global-function:name={}", name);
        }
        return () -> functions.remove(name, func);
    }

    public void unregisterFunction(String name) {
        functions.remove(name);
    }

    public IFunctionModel getRegisteredFunction(String name) {
        return functions.get(name);
    }

    /**
     * 将类上所有公开的静态函数注册为全局函数。nop-xlang模块中DefaultXLangProvider类将会自动注册XScript中支持的GlobalFunctions
     */
    public ICancellable registerStaticFunctions(Class<?> clazz) {
        Cancellable task = new Cancellable();

        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        for (IFunctionModel fn : classModel.getStaticMethods()) {
            if (fn.isPublic()) {
                task.appendOnCancelTask(registerFunction(fn.getName(), fn));
            }
        }
        return task;
    }
}