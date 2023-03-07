/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.Guard;
import io.nop.xlang.xdef.XDefConstants;

import java.io.Serializable;

@DataBean
public class SchemaDefType implements Serializable, IJsonString {
    private final String defType;
    private final String options;

    public SchemaDefType(String defType, String options) {
        this.defType = Guard.notEmpty(defType, "defType");
        this.options = options;
    }

    public String toString() {
        if (options == null)
            return defType;
        return defType + XDefConstants.XDEF_TYPE_PREFIX_OPTIONS + options;
    }

    public String getDefType() {
        return defType;
    }

    public String getOptions() {
        return options;
    }
}