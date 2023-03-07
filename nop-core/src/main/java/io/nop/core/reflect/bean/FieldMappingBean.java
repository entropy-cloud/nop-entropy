/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.bean;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.lang.eval.IEvalFunction;

public class FieldMappingBean extends FieldSelectionBean {
    private IEvalFunction getter;
    private IEvalFunction setter;

    @Override
    protected FieldSelectionBean newFieldSelectionBean() {
        FieldMappingBean bean = new FieldMappingBean();
        bean.setSetter(setter);
        bean.setGetter(getter);
        return bean;
    }

    public IEvalFunction getGetter() {
        return getter;
    }

    public void setGetter(IEvalFunction getter) {
        checkAllowChange();
        this.getter = getter;
    }

    public IEvalFunction getSetter() {
        return setter;
    }

    public void setSetter(IEvalFunction setter) {
        checkAllowChange();
        this.setter = setter;
    }
}
