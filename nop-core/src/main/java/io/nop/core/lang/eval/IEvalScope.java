/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.reflect.IClassModelLoader;

import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreConfigs.CFG_EVAL_SCOPE_DEBUG_ENABLED;

/**
 * 为了支持调试功能和副作用输出，需要显式定义scope
 */
@NoReflection
public interface IEvalScope extends IVariableScope, IEvalContext {
    boolean ENABLE_EVAL_DEBUG = CFG_EVAL_SCOPE_DEBUG_ENABLED.get();

    default IEvalScope getEvalScope() {
        return this;
    }

    IBeanProvider getBeanProvider();

    void setBeanProvider(IBeanProvider beanProvider);

    IClassModelLoader getClassModelLoader();

    void setClassModelLoader(IClassModelLoader classModelLoader);

    IEvalOutput getOut();

    void setOut(IEvalOutput out);

    void setExtension(IVariableScope extension);

    /**
     * 当变量在scope中找不到时，会自动到parentScope中查找
     *
     * @return
     */
    IEvalScope getParentScope();

    boolean isInheritParentVars();

    /**
     * 共享同样的变量集合，但是使用不同的EvalFrame，一般用于并行执行
     *
     * @return 新建的scope
     */
    IEvalScope duplicate();

    /**
     * 新建一个空的变量scope。为了便于维持scope内在限制，这里并没有传入vars参数
     *
     * @param inheritParentVars 新scope在查找变量未找到的时候，是否到父scope中查找
     * @param inheritParentOut  新scope的out和outputStream是否继承父scope的
     * @return
     */
    IEvalScope newChildScope(boolean inheritParentVars, boolean inheritParentOut, boolean threadSafe);

    default IEvalScope newChildScope() {
        return newChildScope(true, true, false);
    }

    IEvalScope newChildScope(Map<String,Object> childVars);

    Set<String> keySet();

    int size();

    /**
     * 只在当前变量集合中查找，不查找$defaults集合和全局变量集合
     *
     * @param name
     * @return
     */
    boolean containsLocalValue(String name);

    boolean containsValue(String name);

    /**
     * 获取当前变量集合中的变量值，不查找$defaults集合和全局变量集合
     *
     * @param name
     * @return
     */
    Object getLocalValue(String name);

    /**
     * 记录下当前scope中某个变量的值。当前变量集合中不存在此变量，则返回UNDEFINED_VALUE
     *
     * @param name
     * @return
     */
    ValueWithLocation recordValueLocation(String name);

    default void restoreValueLocation(String name, ValueWithLocation vl) {
        if (vl.isUndefined()) {
            removeLocalValue(name);
        } else {
            setLocalValue(vl.getLocation(), name, vl.getValue());
        }
    }

    /**
     * 首先判断是否是全局变量，如果是则返回全局变量，否则从局部变量集合中查找，如果未找到，在$defaults变量集合中找
     *
     * @param name
     * @return
     */
    Object getValue(String name);

    /**
     * 设置变量值
     *
     * @param name
     * @param value
     */
    void setLocalValue(SourceLocation loc, String name, Object value);

    default void setLocalValue(String name, Object value) {
        setLocalValue(null, name, value);
    }


    void setLocalValues(SourceLocation loc, Map<String, Object> values);

    default void setLocalValues(Map<String, Object> values) {
        setLocalValue(null, values);
    }

    /**
     * 从当前变量集合中删除变量
     *
     * @param name
     */
    void removeLocalValue(String name);

    /**
     * 清空所有变量
     */
    void clear();

    ExitMode getExitMode();

    void setExitMode(ExitMode exitMode);

    IExpressionExecutor getExpressionExecutor();

    void setExpressionExecutor(IExpressionExecutor executor);

    EvalFrame getCurrentFrame();

    default EvalFrame getFrame(int frameIndex) {
        if (frameIndex <= 0)
            return getCurrentFrame();

        EvalFrame frame = getCurrentFrame();
        for (int i = 0; i < frameIndex; i++) {
            frame = frame.getParentFrame();
            if (frame == null)
                return null;
        }
        return frame;
    }

    void pushFrame(EvalFrame frame);

    void popFrame();
}