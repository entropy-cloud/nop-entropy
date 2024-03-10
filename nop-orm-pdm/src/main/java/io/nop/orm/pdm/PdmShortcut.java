/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.pdm;

public class PdmShortcut {
    private String code;
    private PdmElementType elementType;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public PdmElementType getElementType() {
        return elementType;
    }

    public void setElementType(PdmElementType elementType) {
        this.elementType = elementType;
    }
}
