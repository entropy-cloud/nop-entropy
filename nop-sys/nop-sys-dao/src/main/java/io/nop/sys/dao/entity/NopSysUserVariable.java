/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.sys.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.commons.type.StdDataType;
import io.nop.sys.dao.entity._gen.NopSysUserVariablePkBuilder;
import io.nop.sys.dao.entity._gen._NopSysUserVariable;


@BizObjName("NopSysUserVariable")
public class NopSysUserVariable extends _NopSysUserVariable {
    public NopSysUserVariable() {
    }


    public static NopSysUserVariablePkBuilder newPk() {
        return new NopSysUserVariablePkBuilder();
    }

    public Object getNormalizedValue() {
        String value = getVarValue();
        StdDataType dataType = StdDataType.fromStdName(getVarType());
        if (dataType != null) {
            return dataType.convert(value);
        }
        return value;
    }
}
