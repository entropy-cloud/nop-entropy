/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.reflect.IClassModelLoader;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreErrors.ERR_EVAL_DISABLED_EVAL_SCOPE;

public final class DisabledEvalScope implements IEvalScope {
    public static final DisabledEvalScope INSTANCE = new DisabledEvalScope();

    @Override
    public EvalFrame getCurrentFrame() {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public IBeanProvider getBeanProvider() {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void setBeanProvider(IBeanProvider beanProvider) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public IClassModelLoader getClassModelLoader() {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void setClassModelLoader(IClassModelLoader loader) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public IEvalOutput getOut() {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void setOut(IEvalOutput out) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void setExtension(IVariableScope extension) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public IEvalScope getParentScope() {
        return null;
    }

    @Override
    public boolean isInheritParentVars() {
        return false;
    }

    @Override
    public IEvalScope newChildScope(boolean inheritParentVars, boolean inheritParentOut, boolean threadSafe) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public IEvalScope newChildScope(Map<String, Object> childVars) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public Set<String> keySet() {
        return Collections.emptySet();
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean containsLocalValue(String name) {
        return false;
    }

    @Override
    public boolean containsValue(String name) {
        return false;
    }

    @Override
    public Object getLocalValue(String name) {
        return null;
    }

    @Override
    public ValueWithLocation recordValueLocation(String name) {
        return null;
    }

    @Override
    public Object getValue(String name) {
        return null;
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        return null;
    }

    @Override
    public void setLocalValue(SourceLocation loc, String name, Object value) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void setLocalValues(SourceLocation loc, Map<String, Object> values) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public IEvalScope duplicate() {
        return this;
    }

    @Override
    public void removeLocalValue(String name) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void clear() {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public ExitMode getExitMode() {
        return null;
    }

    @Override
    public void setExitMode(ExitMode exitMode) {
        throw new NopEvalException(ERR_EVAL_DISABLED_EVAL_SCOPE);
    }

    @Override
    public void pushFrame(EvalFrame frame) {

    }

    @Override
    public void popFrame() {

    }

    @Override
    public IExpressionExecutor getExpressionExecutor() {
        return DefaultExpressionExecutor.INSTANCE;
    }

    @Override
    public void setExpressionExecutor(IExpressionExecutor executor) {

    }
}