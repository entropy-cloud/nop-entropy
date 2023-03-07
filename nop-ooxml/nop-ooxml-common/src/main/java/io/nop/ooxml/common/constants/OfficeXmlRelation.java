/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common.constants;

import java.io.Serializable;

public class OfficeXmlRelation implements Serializable {
    private final String type;
    private final String rel;
    private final String defaultName;

    public OfficeXmlRelation(String type, String rel, String defaultName) {
        this.type = type;
        this.rel = rel;
        this.defaultName = defaultName;
    }

    public String getType() {
        return type;
    }

    public String getRelation() {
        return rel;
    }

    public String getDefaultName() {
        return defaultName;
    }
}
