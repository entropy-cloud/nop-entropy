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

    String ARG_TABLE_NAME = "tableName";

    String ARG_DEFAULT_VALUE = "defaultValue";

    String ARG_MIDDLE_ENTITY_NAME = "middleEntityName";

    String ARG_RELATION_NAME = "relationName";

    ErrorCode ERR_DYN_UNKNOWN_STD_SQL_TYPE =
            define("nop.err.dyn.unknown-std-sql-type", "未知的标准SQL类型:{stdSqlType}", ARG_STD_SQL_TYPE);

    ErrorCode ERR_DYN_VIRTUAL_ENTITY_PK_NOT_SID =
            define("nop.err.dyn.virtual-entity-pk-not-sid", "虚拟实体[{entityName}]的主键字段不是sid", ARG_ENTITY_NAME);

    ErrorCode ERR_DYN_VIRTUAL_ENTITY_PROP_MAPPING_NOT_VALID =
            define("nop.err.dyn.virtual-entity-prop-mapping-not-valid", "虚拟实体[{entityName}]的动态属性[{propName}]指定了propMapping映射，但是指定映射的字段[{propMapping}]不是NopDynEntity实体的属性",
                    ARG_ENTITY_NAME, ARG_PROP_NAME, ARG_PROP_MAPPING);

    ErrorCode ERR_DYN_ENTITY_NO_PROP =
            define("nop.err.dyn.entity-no-prop", "实体[{entityName}]没有定义属性[{propName}]",
                    ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_DYN_INVALID_TABLE_NAME =
            define("nop.err.dyn.invalid-table-name", "实体[{entityName}]的表名[{tableName}]不合法，表名只允许字母开头的字母、数字、下划线组合",
                    ARG_ENTITY_NAME, ARG_TABLE_NAME);

    ErrorCode ERR_DYN_INVALID_PROP_NAME =
            define("nop.err.dyn.invalid-prop-name", "实体[{entityName}]的属性名[{propName}]不合法，属性名只允许字母开头的字母、数字、下划线组合",
                    ARG_ENTITY_NAME, ARG_PROP_NAME);

    ErrorCode ERR_DYN_INVALID_DEFAULT_VALUE =
            define("nop.err.dyn.invalid-default-value", "实体[{entityName}]的属性[{propName}]的缺省值[{defaultValue}]不合法，缺省值不允许包含换行、分号或SQL注释",
                    ARG_ENTITY_NAME, ARG_PROP_NAME, ARG_DEFAULT_VALUE);

    ErrorCode ERR_DYN_MIDDLE_ENTITY_CONFLICT =
            define("nop.err.dyn.middle-entity-conflict", "中间表[{middleEntityName}]已经被多于两个多对多关系使用，发生冲突的关系:[{relationName}]",
                    ARG_MIDDLE_ENTITY_NAME, ARG_RELATION_NAME);
}
