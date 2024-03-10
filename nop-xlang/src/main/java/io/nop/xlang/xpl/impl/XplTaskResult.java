/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.impl;

import io.nop.core.resource.component.AbstractComponentModel;

/**
 * 后缀为xtask的文件实际上是xpl文件，但是它的装载结果实际上是作为xpl运行后的返回结果
 */
public class XplTaskResult extends AbstractComponentModel {
    private Object returnValue;

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        checkAllowChange();
        this.returnValue = returnValue;
    }
}
