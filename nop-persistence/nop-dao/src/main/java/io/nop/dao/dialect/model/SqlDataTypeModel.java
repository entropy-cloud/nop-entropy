/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.model;

import io.nop.dao.dialect.model._gen._SqlDataTypeModel;

public class SqlDataTypeModel extends _SqlDataTypeModel {
    public SqlDataTypeModel() {

    }

    public String getCodeOrName() {
        String code = getCode();
        if (code != null)
            return code;
        return getName();
    }

    /**
     * 数据长度在本数据类型允许的范围之内
     *
     * @param precision 数据长度
     */
    public boolean isAllowPrecision(int precision) {
        // 如果定义没有限制数据长度，则允许任意长度。例如CLOB
        if (this.getPrecision() == null)
            return true;

        if (getPrecision() == precision)
            return true;

        if (precision <= 0)
            return false;

        return precision <= getPrecision();
    }
}
