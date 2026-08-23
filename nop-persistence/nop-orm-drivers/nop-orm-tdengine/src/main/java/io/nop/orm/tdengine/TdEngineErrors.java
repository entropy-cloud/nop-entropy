/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface TdEngineErrors {
    String ARG_ENTITY_NAME = "entityName";
    String ARG_PROP_NAME = "propName";
    String ARG_TABLE_NAME = "tableName";

    ErrorCode ERR_TDENGINE_INVALID_SUB_TABLE_NAME = define("nop.err.orm.tdengine.invalid-sub-table-name",
            "Invalid TDengine sub table name [{tableName}] generated from property [{propName}]: only letters, digits and underscores are allowed",
            ARG_TABLE_NAME, ARG_PROP_NAME);

    ErrorCode ERR_TDENGINE_UPDATE_NOT_SUPPORTED = define("nop.err.orm.tdengine.update-not-supported",
            "TDengine driver does not support updating entity [{entityName}]: TDengine has no UPDATE statement, re-inserting a row with the same primary key overwrites it",
            ARG_ENTITY_NAME);
}
