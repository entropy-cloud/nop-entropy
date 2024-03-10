/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.component.AbstractFreezable;

import java.util.IdentityHashMap;
import java.util.Map;

public class BeanCopyOptions extends AbstractFreezable {
    public static final BeanCopyOptions DEFAULT = new BeanCopyOptions().markFreeze();

    private IBeanModelManager beanModelManager = ReflectionManager.instance();

    private IBeanDeserializerFactory deserializerFactory;

    /**
     * 可以选择只复制部分字段
     */
    private FieldSelectionBean selection;

    /**
     * 是否忽略所有标记了@JsonIgnore的字段
     */
    private boolean onlySerializableSource = true;

    private boolean onlySerializableTarget = true;

    /**
     * 是否允许循环引用，如果允许，则使用objMap来管理对象引用，避免为同一源对象创建多个目标对象
     */
    private boolean allowLoop;
    private boolean ignoreUnknownProp;
    private boolean allowBuilderMethod;
    /**
     * 对于Immutable的对象，是否直接复用
     */
    private boolean reuseImmutable = true;

    /**
     * 是否识别Map的继承类上的getXXX方法构成的bean属性
     */
    private boolean allowMapExt;

    /**
     * 是否识别List的继承类上的getXXX方法构成的bean属性
     */
    private boolean allowListExt;

    private Map<Object, Object> objMap;
    private IEvalScope evalScope = DisabledEvalScope.INSTANCE;

    BeanCopyOptions markFreeze() {
        freeze(true);
        return this;
    }

    public BeanCopyOptions onlySerializable(boolean b) {
        this.onlySerializableSource = b;
        return this;
    }

    public boolean isAllowMapExt() {
        return allowMapExt;
    }

    public void setAllowMapExt(boolean allowMapExt) {
        checkAllowChange();
        this.allowMapExt = allowMapExt;
    }

    public boolean isAllowListExt() {
        return allowListExt;
    }

    public void setAllowListExt(boolean allowListExt) {
        checkAllowChange();
        this.allowListExt = allowListExt;
    }

    public boolean isReuseImmutable() {
        return reuseImmutable;
    }

    public void setReuseImmutable(boolean reuseImmutable) {
        checkAllowChange();
        this.reuseImmutable = reuseImmutable;
    }

    public IBeanModelManager getBeanModelManager() {
        return beanModelManager;
    }

    public void setBeanModelManager(IBeanModelManager beanModelManager) {
        checkAllowChange();
        this.beanModelManager = beanModelManager;
    }

    public IBeanDeserializerFactory getDeserializerFactory() {
        return deserializerFactory;
    }

    public void setDeserializerFactory(IBeanDeserializerFactory deserializerFactory) {
        checkAllowChange();
        this.deserializerFactory = deserializerFactory;
    }

    /**
     * 复制bean时可以选择只复制部分属性
     */
    public FieldSelectionBean getSelection() {
        return selection;
    }

    public void setSelection(FieldSelectionBean selection) {
        checkAllowChange();
        this.selection = selection;
    }

    /**
     * 是否允许对象循环引用，缺省为false
     */
    public boolean isAllowLoop() {
        return allowLoop;
    }

    public void setAllowLoop(boolean allowLoop) {
        checkAllowChange();
        this.allowLoop = allowLoop;
    }

    public boolean isOnlySerializableSource() {
        return onlySerializableSource;
    }

    /**
     * 是否忽略所有标记了@JsonIgnore的属性
     */
    public void setOnlySerializableSource(boolean onlySerializableSource) {
        checkAllowChange();
        this.onlySerializableSource = onlySerializableSource;
    }

    public boolean isOnlySerializableTarget() {
        return onlySerializableTarget;
    }

    public void setOnlySerializableTarget(boolean onlySerializableTarget) {
        checkAllowChange();
        this.onlySerializableTarget = onlySerializableTarget;
    }

    public Map<Object, Object> makeObjMap() {
        checkAllowChange();
        if (objMap == null)
            objMap = new IdentityHashMap<>();
        return objMap;
    }

    public Object getMappedObj(Object src) {
        if (objMap == null)
            return null;
        return objMap.get(src);
    }

    public IEvalScope getEvalScope() {
        return evalScope;
    }

    public void setEvalScope(IEvalScope scope) {
        checkAllowChange();
        this.evalScope = scope;
    }

    public void addMappedObj(Object src, Object target) {
        makeObjMap().put(src, target);
    }

    /**
     * 是否忽略未知的属性，缺省为false。如果设置为true, 则发现反射模型上未定义的属性时抛出异常
     */
    public boolean isIgnoreUnknownProp() {
        return ignoreUnknownProp;
    }

    public void setIgnoreUnknownProp(boolean ignoreUnknownProp) {
        checkAllowChange();
        this.ignoreUnknownProp = ignoreUnknownProp;
    }

    /**
     * 是否自动识别withXXX(value), addXXX(value)这种builder模式常用的构造函数，缺省为false
     */
    public boolean isAllowBuilderMethod() {
        return allowBuilderMethod;
    }

    public void setAllowBuilderMethod(boolean allowBuilderMethod) {
        checkAllowChange();
        this.allowBuilderMethod = allowBuilderMethod;
    }

    public Map<Object, Object> getObjMap() {
        return objMap;
    }

    public void setObjMap(Map<Object, Object> objMap) {
        checkAllowChange();
        this.objMap = objMap;
    }
}