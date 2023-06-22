/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.reflect.IClassModelLoader;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@NoReflection
public class EvalScopeImpl implements IEvalScope {
    private final IEvalScope parentScope;
    private final Map<String, Object> variables;
    private Map<String, SourceLocation> locations;
    private final boolean inheritParentVars;
    private IBeanProvider beanProvider;
    private IClassModelLoader classModelLoader = ReflectionManager.instance();
    private IEvalOutput output = DisabledEvalOutput.INSTANCE;
    private IVariableScope extension;
    private EvalFrame currentFrame;
    private ExitMode exitMode;
    private IExpressionExecutor executor = EvalExprProvider.getGlobalExecutor();

    protected EvalScopeImpl(IEvalScope parentScope, Map<String, Object> variables, boolean inheritParentVars,
                            boolean inheritOutput) {
        this.parentScope = parentScope;
        this.variables = variables == null ? new HashMap<>() : variables;
        this.inheritParentVars = parentScope != null && inheritParentVars;
        if (parentScope != null) {
            this.setClassModelLoader(parentScope.getClassModelLoader());
            this.setBeanProvider(parentScope.getBeanProvider());
            this.setExpressionExecutor(parentScope.getExpressionExecutor());
            if (inheritOutput)
                this.output = parentScope.getOut();
        }
    }

    public EvalScopeImpl() {
        this(null, new HashMap<>(), false, false);
    }

    public EvalScopeImpl(Map<String, Object> variables) {
        this(null, variables, false, false);
    }

    @Override
    public IBeanProvider getBeanProvider() {
        if (beanProvider == null) {
            if (BeanContainer.isInitialized())
                return BeanContainer.instance();
            return null;
        }
        return beanProvider;
    }

    @Override
    public void setBeanProvider(IBeanProvider beanProvider) {
        this.beanProvider = beanProvider;
    }

    @Override
    public IClassModelLoader getClassModelLoader() {
        return classModelLoader;
    }

    @Override
    public void setClassModelLoader(IClassModelLoader classModelLoader) {
        this.classModelLoader = classModelLoader;
    }

    @Override
    public IEvalOutput getOut() {
        return output;
    }

    @Override
    public void setOut(IEvalOutput out) {
        Guard.notNull(out, "output");
        this.output = out;
    }

    @Override
    public void setExtension(IVariableScope extension) {
        this.extension = extension;
    }

    @Override
    public IEvalScope getParentScope() {
        return parentScope;
    }

    @Override
    public boolean isInheritParentVars() {
        return inheritParentVars;
    }

    @Override
    public IEvalScope newChildScope(boolean inheritParentVars, boolean inheritParentOut, boolean threadSafe) {
        EvalScopeImpl scope = new EvalScopeImpl(this, threadSafe ? new ConcurrentHashMap<>() : new HashMap<>(),
                inheritParentVars, inheritParentOut);
        return scope;
    }

    @Override
    public IEvalScope duplicate() {
        EvalScopeImpl scope = new EvalScopeImpl(parentScope, variables, inheritParentVars, false);
        scope.setOut(output);
        scope.setExpressionExecutor(executor);
        scope.setBeanProvider(beanProvider);
        scope.setExtension(extension);
        scope.setClassModelLoader(classModelLoader);

        if (ENABLE_EVAL_DEBUG) {
            if (locations == null) {
                locations = new HashMap<>();
            }
            scope.locations = locations;
        }
        return scope;
    }

    @Override
    public Set<String> keySet() {
        return variables.keySet();
    }

    @Override
    public int size() {
        return variables.size();
    }

    @Override
    public boolean containsLocalValue(String name) {
        return variables.containsKey(name);
    }

    @Override
    public Object getLocalValue(String name) {
        Object value = variables.get(name);
        return value;
    }

    @Override
    public ValueWithLocation recordValueLocation(String name) {
        Object value = variables.get(name);
        if (value == null) {
            if (!variables.containsKey(name))
                return ValueWithLocation.UNDEFINED_VALUE;
        }
        return ValueWithLocation.of(getLocalLocation(name), value);
    }

    public SourceLocation getLocalLocation(String name) {
        if (ENABLE_EVAL_DEBUG) {
            if (locations != null) {
                synchronized (locations) {
                    return locations.get(name);
                }
            }
        }
        return null;
    }

    @Override
    public boolean containsValue(String name) {
        // if (EvalGlobalRegistry.isGlobalVarName(name)) {
        // return EvalGlobalRegistry.instance().getRegisteredVariable(name) != null;
        // }

        if (variables.containsKey(name))
            return true;

        if (extension != null) {
            if (extension.containsValue(name))
                return true;
        }

        if (inheritParentVars)
            return parentScope.containsValue(name);
        return false;
    }

    @Override
    public Object getValue(String name) {
        // if (EvalGlobalRegistry.isGlobalVarName(name)) {
        // IGlobalVariableDefinition var = EvalGlobalRegistry.instance().getRegisteredVariable(name);
        // if (var == null)
        // throw new NopEvalException(ERR_EVAL_UNKNOWN_GLOBAL_VAR).param(ARG_NAME, name);
        // return var.getValue(this);
        // }

        Object value = getLocalValue(name);
        if (value != null)
            return value;

        if (variables.containsKey(name))
            return null;

        if (extension != null) {
            if (extension.containsValue(name))
                return extension.getValue(name);
        }

        if (inheritParentVars)
            return parentScope.getValue(name);

        return null;
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        int pos = propPath.indexOf('.');
        if (pos < 0)
            return getValue(propPath);
        Object o = getValue(propPath.substring(0, pos));
        if (o == null)
            return null;
        return BeanTool.getComplexProperty(o, propPath.substring(pos + 1));
    }

    @Override
    public void setLocalValue(SourceLocation loc, String name, Object value) {
        variables.put(name, value);
        if (ENABLE_EVAL_DEBUG) {
            if (locations == null) {
                locations = new HashMap<>();
            }
            synchronized (locations) {
                locations.put(name, loc);
            }
        }
    }

    @Override
    public void setLocalValues(SourceLocation loc, Map<String, Object> values) {
        if (values != null) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                setLocalValue(loc, entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void removeLocalValue(String name) {
        variables.remove(name);
        if (ENABLE_EVAL_DEBUG) {
            if (locations != null)
                locations.remove(name);
        }
    }

    @Override
    public void clear() {
        variables.clear();
        if (ENABLE_EVAL_DEBUG) {
            if (locations != null) {
                synchronized (locations) {
                    locations.clear();
                }
            }
        }
    }

    @Override
    public ExitMode getExitMode() {
        return exitMode;
    }

    @Override
    public void setExitMode(ExitMode exitMode) {
        this.exitMode = exitMode;
    }

    @Override
    public EvalFrame getCurrentFrame() {
        return currentFrame;
    }

    @Override
    public void pushFrame(EvalFrame frame) {
        this.currentFrame = frame;
    }

    @Override
    public void popFrame() {
        if (currentFrame != null)
            currentFrame = currentFrame.getParentFrame();
    }

    @Override
    public IExpressionExecutor getExpressionExecutor() {
        return executor;
    }

    @Override
    public void setExpressionExecutor(IExpressionExecutor executor) {
        this.executor = executor;
    }
}
