/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.pdm;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface PdmModelErrors {
    String ARG_TABLE_NAME = "tableName";

    ErrorCode ERR_PDM_PRIMARY_KEY_NO_KEY_REF = define("nop.err.orm.pdm.primary-key-no-key-ref",
            "表[{tableName}]的<c:PrimaryKey>定义下缺少<c:Key>节点，无法确定主键引用", ARG_TABLE_NAME);
}
