/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath;

import io.nop.api.core.util.IVariableScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.adapter.IXNodeAdapter;
import io.nop.xlang.XLangConstants;

public class DefaultXPathContext<E> implements IXPathContext<E>, IVariableScope {
    private final IEvalScope scope;
    private final E root;
    private final IXNodeAdapter<E> adapter;
    private E thisNode;

    public DefaultXPathContext(E root, IXNodeAdapter<E> adapter, IEvalScope scope) {
        this.scope = scope;
        this.root = root;
        this.adapter = adapter;
        this.scope.setExtension(this);
    }

    @Override
    public boolean containsValue(String name) {
        return getValue(name) != null;
    }

    @Override
    public Object getValue(String name) {
        if (XLangConstants.XPATH_VAR_THIS_NODE.equals(name))
            return thisNode;
        if (XLangConstants.XPATH_VAR_ROOT.equals(name))
            return root;
        return null;
    }

    @Override
    public Object getValueByPropPath(String propPath) {
        return getValue(propPath);
    }

    public void setThisNode(E thisNode) {
        this.thisNode = thisNode;
    }

    public E getThisNode() {
        return thisNode;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public E root() {
        return root;
    }

    @Override
    public IXNodeAdapter<E> adapter() {
        return adapter;
    }
}