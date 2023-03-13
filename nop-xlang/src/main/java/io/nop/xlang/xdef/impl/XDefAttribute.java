/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.impl;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.component.AbstractFreezable;
import io.nop.xlang.xdef.IXDefAttribute;
import io.nop.xlang.xdef.XDefTypeDecl;

@DataBean
public class XDefAttribute extends AbstractFreezable implements IXDefAttribute {
    private String name;
    private XDefTypeDecl type;
    private String propName;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkAllowChange();
        this.name = name;
    }

    @Override
    public XDefTypeDecl getType() {
        return type;
    }

    public void setType(XDefTypeDecl type) {
        checkAllowChange();
        this.type = type;
    }

    @Override
    public String getPropName() {
        return propName;
    }

    public void setPropName(String propName) {
        checkAllowChange();
        this.propName = propName;
    }
}
