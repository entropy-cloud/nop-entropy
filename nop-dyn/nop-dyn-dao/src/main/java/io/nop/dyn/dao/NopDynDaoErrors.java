/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopDynDaoErrors {
    String ARG_STD_SQL_TYPE = "stdSqlType";

    String ARG_ENTITY_NAME = "entityName";

    String ARG_PROP_NAME = "propName";

    String ARG_PROP_MAPPING = "propMapping";

    ErrorCode ERR_DYN_UNKNOWN_STD_SQL_TYPE =
            define("nop.err.dyn.unknown-std-sql-type", "未知的标准SQL类型:{stdSqlType}", ARG_STD_SQL_TYPE);

    ErrorCode ERR_DYN_VIRTUAL_ENTITY_PK_NOT_SID =
            define("nop.err.dyn.virtual-entity-pk-not-sid", "虚拟实体[{entityName}]的主键字段不是sid", ARG_ENTITY_NAME);

    ErrorCode ERR_DYN_VIRTUAL_ENTITY_PROP_MAPPING_NOT_VALID =
            define("nop.err.dyn.virtual-entity-prop-mapping-not-valid", "虚拟实体[{entityName}]的动态属性[{propName}]指定了propMapping映射，但是指定映射的字段[{propMapping}]不是NopDynEntity实体的属性",
                    ARG_ENTITY_NAME, ARG_PROP_NAME, ARG_PROP_MAPPING);
}
