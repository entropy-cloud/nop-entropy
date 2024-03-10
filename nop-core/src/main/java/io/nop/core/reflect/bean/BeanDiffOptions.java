/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.functional.IEqualsChecker;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.ReflectionManager;

public class BeanDiffOptions {
    private IBeanModelManager beanModelManager = ReflectionManager.instance();
    private FieldSelectionBean selection;
    private IEvalScope scope = DisabledEvalScope.INSTANCE;
    private boolean includeSame;
    private boolean onlySerializable;

    private IEqualsChecker equalsChecker = IEqualsChecker.OBJECT_EQUALS;

    public IEqualsChecker getEqualsChecker() {
        return equalsChecker;
    }

    public void setEqualsChecker(IEqualsChecker equalsChecker) {
        this.equalsChecker = equalsChecker;
    }

    public IBeanModelManager getBeanModelManager() {
        return beanModelManager;
    }

    public void setBeanModelManager(IBeanModelManager beanModelManager) {
        this.beanModelManager = beanModelManager;
    }

    public boolean isOnlySerializable() {
        return onlySerializable;
    }

    public void setOnlySerializable(boolean onlySerializable) {
        this.onlySerializable = onlySerializable;
    }

    public FieldSelectionBean getSelection() {
        return selection;
    }

    public void setSelection(FieldSelectionBean selection) {
        this.selection = selection;
    }

    public boolean isIncludeSame() {
        return includeSame;
    }

    public void setIncludeSame(boolean includeSame) {
        this.includeSame = includeSame;
    }

    public IEvalScope getScope() {
        return scope;
    }

    public void setScope(IEvalScope scope) {
        this.scope = scope;
    }
}
